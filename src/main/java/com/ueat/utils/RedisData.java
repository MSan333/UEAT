package com.ueat.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // 热点数据需要提前导入（缓存预热）
    private LocalDateTime expireTime;
    // 泛型存储 不需要让原本数据进行继承
    private Object data;
}
