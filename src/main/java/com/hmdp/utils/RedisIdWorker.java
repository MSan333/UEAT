package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 从 2024/1/1/0/0/0 开始的时间戳
    private static final long BEGIN_TIMESTAMP = 1704067200L;
    // 序列号长度（ == 时间戳左移的位移）
    private static final long COUNT_BITS = 32;

    /**
     *  基于 redis 自增长器实现自增ID
     * @param keyPrefix 业务前缀
     * @return 基本数据类型的单号（不用String等是因为基本数据类型占空间更小）
     */
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号
        // redis自增的范围到2^64，而全局ID生成器中设计的：记录序列号的比特位只有32位，因此哪怕同一个业务也不能一直使用同一个key，不然可能超过上限
        // 采取按天来对业务进行细分，同一天使用同一个key，保证不溢出的同时也有统计的能力
        // 2.1 获取当前日期 精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长，使用基本数据类型来接收（后续要运算）
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 拼接并返回
        // 同一秒生成的id序列号前缀是一样的（timestamp一样）
        return timeStamp<<COUNT_BITS | count;
    }

}
