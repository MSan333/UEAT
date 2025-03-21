package com.ueat.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ueat.dto.Result;
import com.ueat.entity.Shop;
import com.ueat.mapper.ShopMapper;
import com.ueat.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ueat.utils.CacheClient;
import com.ueat.utils.RedisConstants;
import com.ueat.utils.RedisData;
import com.ueat.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);
        // lambda表达式 id2 -> getById(id2) 简写如下
        //Shop shop = cacheClient.
        //        queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        Shop shop = cacheClient.
                queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS); // 设置逻辑过期时间为20s，方便测试

        // 缓存击穿 - 互斥锁
        // Shop shop = queryWithMutex(id);
        // 缓存击穿 - 过期
        // Shop shop = queryWithLogicalExpire(id);
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

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存击穿 - 逻辑过期实现
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 从redis查缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2. 不存在/未命中，直接返回null(说明访问的数据不在预热范围内)
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // Redis命中，判断过期时间，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 反序列化 object 类型变量的时候，由于没有指定具体类型，做反序列化时会变成JSONObject类
        JSONObject data = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 再判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 未过期 直接返回店铺信息
            return shop;
        }
        // 已过期 需要缓存重建
        // 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 获取锁成功 开启独立线程执行缓存重建 同样也返回过期店铺信息 释放互斥锁
        if (isLock) {
            // 获取成功应该再次检测redis是否过期 DoubleCheck 存在则无需缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 调用缓存预热代码来更新缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }

        // 获取锁失败 直接返回过期的店铺信息
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
            // 成功 检测缓存是否存在（避免上一个thread已经完成重构了，接下来的thread还要重新构建缓存，double check）
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1 判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 按照原始根据类型分页数据库查询
            Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 2 计算分页参数（页面大小)
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3 按照距离排序查询redis分页。结果：shopId, dist
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // geosearch bylonlat x y byradius 10 withdistance
                .search(key, GeoReference.fromCoordinate(x, y), new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        // 4 解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() < from) {
            // 没有下一页了直接结束
            return Result.ok(Collections.emptyList());
        }
        // 截取 from ~ end 的数据（默认从0开始）
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distances = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result->{
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            Distance distance = result.getDistance();
            distances.put(shopId.toString(), distance);
        });
        // 5 查询店铺
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("Order By Field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distances.get(shop.getId().toString()).getValue());
        }
        // 6 返回
        return Result.ok(shops);
    }

    /**
     * 利用单元测试来实现缓存预热（暂时没有工具平台来做这坚守）
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 查询店铺数据
        Shop shop = getById(id);
        // 模拟缓存重建的延迟
        Thread.sleep(50);
        // 封装过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        // 设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入redis(永久有效，不设置实际过期时间)
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
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
