package com.hmdp.netty;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class NettyRedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        // 生成时间戳部分
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.BASIC_ISO_DATE); // 如 20250308
        // 使用 Redis 自增生成序列号
        String key = keyPrefix + ":" + timestamp;
        return stringRedisTemplate.opsForValue().increment(key);
    }
}