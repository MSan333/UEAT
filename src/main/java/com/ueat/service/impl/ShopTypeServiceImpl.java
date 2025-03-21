package com.ueat.service.impl;

import cn.hutool.json.JSONUtil;
import com.ueat.dto.Result;
import com.ueat.entity.ShopType;
import com.ueat.mapper.ShopTypeMapper;
import com.ueat.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ueat.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService typeService;

    @Override
    public Result queryShopType() {
        // List<ShopType> typeList = typeService
        //         .query().orderByAsc("sort").list();

        // 1. 从redis中查询类型列表缓存
        String listJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_LIST);
        // 2. 查到则直接返回
        if (listJson != null) {
            return Result.ok(JSONUtil.toList(listJson, ShopType.class));
        }
        // 3. 没查到
        // 4. 查询数据库
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        // 5. 查到后写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_LIST, JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}
