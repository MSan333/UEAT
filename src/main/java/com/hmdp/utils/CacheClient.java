package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component // spring维护这个bean
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 设置 缓存击穿(逻辑过期预热部分)
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) throws InterruptedException {
        RedisData redisData = new RedisData();
        Thread.sleep(50);
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 查询 缓存穿透（基于存储空值的解决方案）
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 查询 Redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. redis中存在
        // 2.1 命中的不是空值：直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 2.2 命中的是空值：返回null
        if (json != null) {
            return null;
        }
        // 3. redis中不存在
        // 4. 查询数据库
        R r = dbFallback.apply(id);
        // 4.1 数据库中不存在，redis存储空值，返回null
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4.2 数据库中存在，写入redis并返回
        this.set(key, JSONUtil.toJsonStr(r), time, unit);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 查询 缓存击穿（基于逻辑过期的解决方案）
    public <R, ID> R queryWithLogicalExpire
                    (String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 查询 redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 不存在直接返回null(缓存预热，redis中不存在说明数据非热点数据，不需要继续查询数据库)
            return null;
        }
        // 2. 存在，反序列化为bean(以redisData类存储)
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        // 3. 判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        // 3.1 没过期，直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 4. 过期
        // 4.1 获取互斥锁(这里还是用shop的lock来指定)
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 4.2 锁没被获取 开启独立线程实现缓存更新
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库 获取最新值
                    R r1 = dbFallback.apply(id);
                    // 调用缓存预热方法实现缓存更新（逻辑过期）
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 4.3 锁已被获取/还未被获取 最后都直接返回旧数据
        return r;
    }

    private boolean tryLock(String key) {
        // value任意
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


}
