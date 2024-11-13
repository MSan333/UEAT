package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 提交秒杀优惠券订单
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. 判断优惠券抢购时间是否合法
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束！");
        }
        // 3. 判断库存是否充足
        if (voucher.getStock()<1) {
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 锁加在这里能保证事务能够及时提交
            // 因此先获取锁，提交事务，再释放锁，才能确保线程安全
            // 但是这里拿到的是事务的非代理对象this，而事务是由它的代理对象来进行管理的，因此直接用this会使得*事务失效*
            // 因此需要拿到事务的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // synchronized加在方法上，则锁的对象是this，那么肯定是线程安全的，锁的范围是整个方法，任何用户都要加锁，是串行执行
        // 7. 一人一单
        Long userId = UserHolder.getUser().getId();
        // 用同步代码块来实现同一个用户用同一把锁，这样可以增加锁的粒度，不需要所有用户用同一把锁
        // 用userId的string对象来作为锁，但是toString底层调用的是new，就算是同一个userId获取到的锁也不一样，因此要再用intern方法
        // 来获取串池中的对象引用（值一样，锁就一样）
        // 但是如果是锁同步代码块，锁释放后其他线程就会进入函数方法，但是此时事务还未提交（由spring管理），也就是数据库还未更新
        // 因此锁应该加载方法外面
        // 7.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 7.2 判断是否存在(判断新增，而不是判断是否修改，因此不能用乐观锁)
        if (count > 0) {
            // 至少下过一单
            return Result.fail("已经购买过一次！");
        }

        // 4. 扣减库存
        boolean success = seckillVoucherService.update() //乐观锁 CAS法
                .setSql("stock = stock - 1") // set stock = stock - 1
                // where id=? and stock=? 修改为 where id=? and stock>0 避免失败率太高的问题
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足！");
        }

        // 5. 创建订单
        VoucherOrder order = new VoucherOrder();
        // 6. 订单id 用户id 代金券id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        save(order);
        return Result.ok(orderId);

    }
}
