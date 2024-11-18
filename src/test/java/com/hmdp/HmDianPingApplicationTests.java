package com.hmdp;

import cn.hutool.cache.Cache;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        // 线程池是异步执行 所以需要借助countDownLatch来计时
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            // 生成 100 个id 并且打印
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            latch.countDown();
        };
        // 循环提交任务
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        // shopService.saveShop2Redis(1L,10L);
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop,10L, TimeUnit.SECONDS);
    }


    @Test
    void loadShopData() {
        // search shop info
        List<Shop> list = shopService.list();
        // category shop by typeId, same in the same list
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // save by batch in redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // type
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            // list
            List<Shop> value = entry.getValue();
            // in redis GEOADD KEY LON LAT MEMBER
//            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
//            }
            // 使用迭代器来新增，而不是批量新增，降低io
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                // shop->location
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }
            // 批量写入redis
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        // 统计
        Long hl2 = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(hl2);
    }
}
