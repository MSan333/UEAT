package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 单线程线程池，因为处理时效要求没有很高
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct // 在当前类初始化完毕后执行
    private void init() {
        // 提交任务到线程池
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    // 任务，需要在类初始化时就加载，因为随时都有可能会发生抢购
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true) { // 死循环
                try {
                    // 获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();// 阻塞方法，获取队列中的头部元素，如果队列为空，则阻塞等待
                    // 创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /**
     * 异步下单
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 异步处理 加锁只为了兜底 以防redis没有判断成功(虽然理论上不可能)
        // 1. 获取用户
        Long userId = voucherOrder.getUserId();
        // 2. 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3. 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败
            log.error("不允许重复下单");
            return;
        }
        try{
            // 获取代理对象（子线程拿不到proxy） 只能在主线程获取
            // IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            proxy.createVoucherOrderByVoucherOrderId(voucherOrder);
        }finally{
            // 释放锁
            lock.unlock();
        }
    }


    private IVoucherOrderService proxy;

    /**
     * 基于Lua脚本实现秒杀逻辑优化，把查询秒杀库存和保存订单的业务挪到redis中先进行
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2. 判断结果
        // 2.1 不为0，没有购买资格
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不允许重复下单");
        }
        // 2.2 为0，有购买资格

        // 2.3 返回订单id
        long orderId = redisIdWorker.nextId("order");
        // TODO 保存到阻塞队列 把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        // 2.4 创建阻塞队列
        orderTasks.add(voucherOrder);
        // 获取代理对象，让子线程可以拿到代理对象（放在初始化中）
        proxy =(IVoucherOrderService)AopContext.currentProxy();
        return Result.ok(orderId);
    }


    /**
     * 提交秒杀优惠券订单
     * @return
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1. 查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2. 判断优惠券抢购时间是否合法
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束！");
//        }
//        // 3. 判断库存是否充足
//        if (voucher.getStock()<1) {
//            return Result.fail("库存不足！");
//        }
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()) {
////            // 锁加在这里能保证事务能够及时提交
////            // 因此先获取锁，提交事务，再释放锁，才能确保线程安全
////            // 但是这里拿到的是事务的非代理对象this，而事务是由它的代理对象来进行管理的，因此直接用this会使得*事务失效*
////            // 因此需要拿到事务的代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        // 使用redis分布式锁解决集群问题
//        // 创建锁对象 (锁定范围为userId)
//        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁
//        // boolean isLock = lock.tryLock(1200);
//        // 无参：失败不等待（不重试）
//        boolean isLock = lock.tryLock();
//        // 判断是否成功
//        if (!isLock) {
//            // 获取锁失败，返回错误（或者重试）
//            return Result.fail("不允许重复下单！");
//        }
//        // 获取锁成功
//        try {
//            // 事务的代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {;
//            // 手动释放
//            lock.unlock();
//        }
//    }



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

    /**
     * 异步处理保存订单业务，不需要再传递信息到前端
     * @param voucherOrder
     */
    @Transactional
    public void createVoucherOrderByVoucherOrderId(VoucherOrder voucherOrder) {
        // 一人一单 异步子线程只能从voucherOrder中取，而不是从线程中获得（区别）
        Long userId = voucherOrder.getId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 判断是否存在(判断新增，而不是判断是否修改，因此不能用乐观锁)
        if (count > 0) {
            // 至少下过一单
            log.error("用户已经下过订单了");
            return;
        }
        // 扣减库存
        boolean success = seckillVoucherService.update() //乐观锁 CAS法
                .setSql("stock = stock - 1") // set stock = stock - 1
                // where id=? and stock=? 修改为 where id=? and stock>0 避免失败率太高的问题
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足！");
            return;
        }
        save(voucherOrder);
    }
}
