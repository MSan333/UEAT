//package com.hmdp.config;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.connection.stream.ReadOffset;
//import org.springframework.data.redis.connection.stream.StreamOffset;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.PostConstruct;
//import java.util.Collections;
//
//@Component
//public class RedisStreamInitializer {
//
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @PostConstruct
//    public void initStream() {
//        String streamKey = "stream.orders";
//        String groupName = "g1";
//
//        try {
//            // 检查流是否存在
//            stringRedisTemplate.opsForStream().info(streamKey);
//        } catch (Exception e) {
//            if (e.getMessage().contains("ERR The stream does not exist")) {
//                // 流不存在，添加初始消息以创建流
//                stringRedisTemplate.opsForStream().add(streamKey, Collections.singletonMap("init", "init"));
//                System.out.println("流 '" + streamKey + "' 已创建");
//            }
//        }
//
//        try {
//            // 创建消费者组
//            stringRedisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("$"), groupName);
//            System.out.println("消费者组 '" + groupName + "' 已创建");
//        } catch (Exception e) {
//            if (!e.getMessage().contains("BUSYGROUP")) { // 如果组已存在，会抛出 BUSYGROUP 异常
//                System.err.println("创建消费者组失败: " + e.getMessage());
//            }
//        }
//    }
//}