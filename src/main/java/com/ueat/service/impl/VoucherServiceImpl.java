package com.ueat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ueat.dto.Result;
import com.ueat.entity.Voucher;
import com.ueat.mapper.VoucherMapper;
import com.ueat.entity.SeckillVoucher;
import com.ueat.service.ISeckillVoucherService;
import com.ueat.service.IVoucherService;
import com.ueat.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 保存秒杀优惠券到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
    }

    // 用户发起秒杀请求
    @Transactional
    public Result seckillVoucher(Long voucherId, Long userId) {
        // 1. 检查 Redis 中的库存
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        if (stockStr == null || Integer.parseInt(stockStr) <= 0) {
            return Result.fail("库存不足或秒杀已结束");
        }

        // 2. 扣减 Redis 库存
        Long stock = stringRedisTemplate.opsForValue().decrement(stockKey);
        if (stock < 0) {
            stringRedisTemplate.opsForValue().increment(stockKey); // 回滚
            return Result.fail("库存不足");
        }

        try {
            // 3. 创建订单（异步或同步）
            createOrder(userId, voucherId); // 假设订单逻辑
            return Result.ok("秒杀成功");
        } catch (Exception e) {
            // 4. 如果订单创建失败，回滚 Redis 库存
            stringRedisTemplate.opsForValue().increment(stockKey);
            return Result.fail("订单创建失败");
        }
    }

    private void createOrder(Long userId, Long voucherId) {
        // 这里可以调用订单服务或异步写入消息队列
        // 最终将订单信息和库存变化持久化到数据库
    }
}
