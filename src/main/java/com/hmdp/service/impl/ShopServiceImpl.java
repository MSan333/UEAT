package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);
        // 缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存空值实现 - 缓存穿透
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        // 1. 从redis查缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2. 存在 直接返回 （isNotBlank 空字符串也是空值）
        if (StrUtil.isNotBlank(shopJson)) {
            // 反序列化
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 3. 不存在
        // 命中redis的是空值 （不是null就一定是空字符串）
        if (shopJson != null) {
            return null;
        }
        // 4. 根据id查数据库
        Shop shop = getById(id);
        // 5. 不存在 返回错误
        if (shop == null) {
            // 缓存穿透：将空值“”写入redis
            stringRedisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6. 存在 写入redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7. 返回
        return shop;
    }

    /**
     * 互斥锁实现 - 缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        // 1. 从redis查缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2. 存在 直接返回 （isNotBlank 空字符串也是空值）
        if (StrUtil.isNotBlank(shopJson)) {
            // 反序列化
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 3. 不存在
        // 命中redis的是空值 （不是null就一定是空字符串）
        if (shopJson != null) {
            return null;
        }
        // 没命中redis（包括命中了空值），则查数据库
        // -------------实现缓存重建------------
        // 获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        // 由于无论是否执行成功，都要释放锁，因此获取锁，释放锁放在同一个try-catch-finally里实现
        try {
            boolean isLock = tryLock(lockKey);
            // 判断是否获取成功
            // 失败则休眠并重试
            if (!isLock) {
                Thread.sleep(50);
                // 递归重试
                return queryWithMutex(id);
            }
            // 成功 检测缓存是否存在（避免重试的thread重新构建缓存）
            // 查询数据库
            // 4. 根据id查数据库
            shop = getById(id);
            // 模拟重建缓存的延时
            Thread.sleep(200);
            // 5. 不存在 返回错误
            if (shop == null) {
                // 缓存穿透：将空值“”写入redis
                stringRedisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6. 存在 写入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放互斥锁
            unlock(lockKey);
        }
        // 7. 返回
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("商店ID不能为空");
        }
        String shopKey = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(shopKey);
        return Result.ok();
    }

    /**
     * 基于setnx自定义互斥锁，采用互斥锁方案解决缓存击穿(热点key问题)
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        // flag是包装类，可能有空值，return会做拆箱转基本数据类型，如果为空值则会报空指针
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
