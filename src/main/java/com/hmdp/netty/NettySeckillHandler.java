package com.hmdp.netty;

import com.hmdp.utils.RedisIdWorker;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Sharable
public class NettySeckillHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private DataSource dataSource;

    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(10);
    private static final DefaultRedisScript<Long> STOCK_SCRIPT;

    static {
        // 初始化Lua脚本
        STOCK_SCRIPT = new DefaultRedisScript<>();
        STOCK_SCRIPT.setResultType(Long.class);
        STOCK_SCRIPT.setScriptText("if redis.call('exists', KEYS[1]) == 1 then " +
                "local stock = redis.call('get', KEYS[1]); " +
                "if tonumber(stock) > 0 then " +
                "redis.call('decr', KEYS[1]); " +
                "return stock - 1; " +
                "end; " +
                "return -1; " +
                "end; " +
                "return -1;");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);
        String request = new String(bytes);
        System.out.println("Received request: " + request);

        String[] parts = request.split(":");
        if (parts.length == 3 && "seckill".equals(parts[0])) {
            String voucherId = parts[1];
            String userId = parts[2];
            String response = processSeckill(voucherId, userId);
            ByteBuf responseBuf = ctx.alloc().buffer();
            responseBuf.writeBytes(response.getBytes());
            ctx.writeAndFlush(responseBuf);
        } else {
            ctx.writeAndFlush(ctx.alloc().buffer().writeBytes("Invalid request".getBytes()));
        }
    }

    private String processSeckill(String voucherId, String userId) {
        String stockKey = "seckill:stock:" + voucherId;
        String orderKey = "seckill:order:" + userId + ":" + voucherId;

        // 检查是否重复下单
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(orderKey))) {
            return "Error: User " + userId + " has already participated in this seckill.";
        }

        // 使用 Lua 脚本检查并减少库存
        Long remainingStock = stringRedisTemplate.execute(STOCK_SCRIPT, Collections.singletonList(stockKey));
        if (remainingStock == null || remainingStock < 0) {
            return "Error: Stock for voucher " + voucherId + " is depleted.";
        }

        long orderId = redisIdWorker.nextId("seckill:order");
        String orderIdStr = String.valueOf(orderId);

        // 异步写入数据库
        dbExecutor.submit(() -> {
            try {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                String sql = "INSERT INTO seckill_orders (order_id, voucher_id, user_id, status) VALUES (?, ?, ?, ?)";
                jdbcTemplate.update(sql, orderIdStr, voucherId, userId, "SUCCESS");
            } catch (Exception e) {
                e.printStackTrace();
                // 回滚库存
                redissonClient.getAtomicLong(stockKey).incrementAndGet();
                System.err.println("Failed to save order for " + orderIdStr + ", stock rolled back.");
            }
        });

        stringRedisTemplate.opsForValue().set(orderKey, orderIdStr);
        System.out.println("Seckill Order ID: " + orderIdStr + ", Remaining Stock: " + remainingStock);
        return "OrderID:" + orderIdStr + ",RemainingStock:" + remainingStock;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    public void initStock(String voucherId, int stock) {
        redissonClient.getAtomicLong("seckill:stock:" + voucherId).set(stock);
    }

    public void shutdown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        dbExecutor.shutdown();
    }
}