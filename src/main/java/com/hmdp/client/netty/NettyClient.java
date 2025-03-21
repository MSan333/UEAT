package com.hmdp.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyClient {

    // 记录成功和失败的用户
    private static final List<String> successUsers = new ArrayList<>();
    private static final List<String> failedUsers = new ArrayList<>();
    private static final Map<String, Long> userStockMap = new HashMap<>(); // 记录每个用户的剩余库存
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failedCount = new AtomicInteger(0);


    public static void main(String[] args) throws InterruptedException {
        int clientCount = 200; // 模拟 200 个并发客户端
        CountDownLatch latch = new CountDownLatch(clientCount);
        NioEventLoopGroup group = new NioEventLoopGroup();

        try {
            for (int i = 0; i < clientCount; i++) {
                String userId = "user" + i;
                new Thread(() -> {
                    try {
                        Bootstrap b = new Bootstrap();
                        b.group(group)
                                .channel(NioSocketChannel.class)
                                .handler(new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel ch) {
                                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                            @Override
                                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                                byte[] bytes = new byte[msg.readableBytes()];
                                                msg.readBytes(bytes);
                                                String response = new String(bytes);
                                                System.out.println("Response for " + userId + ": " + response);

                                                // 判断是否成功获得秒杀券
                                                if (response.contains("OrderID")) {
                                                    synchronized (successUsers) {
                                                        successUsers.add(userId);
                                                        successCount.incrementAndGet();
                                                        // 提取剩余库存
                                                        String remainingStockStr = response.split(",RemainingStock:")[1];
                                                        long remainingStock = Long.parseLong(remainingStockStr);
                                                        userStockMap.put(userId, remainingStock);
                                                        System.out.println(userId + " 获得了秒杀券，剩余库存: " + remainingStock);
                                                    }
                                                } else {
                                                    synchronized (failedUsers) {
                                                        failedUsers.add(userId);
                                                        failedCount.incrementAndGet();
                                                    }
                                                }
                                                ctx.close();
                                            }

                                            @Override
                                            public void channelActive(ChannelHandlerContext ctx) {
                                                String request = "seckill:10:" + userId;
                                                ctx.writeAndFlush(Unpooled.copiedBuffer(request.getBytes()));
                                            }

                                            @Override
                                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                                cause.printStackTrace();
                                                synchronized (failedUsers) {
                                                    failedUsers.add(userId);
                                                    failedCount.incrementAndGet();
                                                }
                                                ctx.close();
                                            }
                                        });
                                    }
                                });

                        ChannelFuture f = b.connect("localhost", 8080).sync();
                        f.channel().closeFuture().sync();
                    } catch (Exception e) {
                        e.printStackTrace();
                        synchronized (failedUsers) {
                            failedUsers.add(userId);
                            failedCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await(); // 等待所有客户端完成

            // 打印总结果
            System.out.println("\n=== 秒杀结果统计 ===");
            System.out.println("成功获得秒杀券的用户数: " + successCount.get());
            System.out.println("成功用户及剩余库存: ");
            for (String user : successUsers) {
                System.out.println(user + " - 剩余库存: " + userStockMap.get(user));
            }
            System.out.println("未获得秒杀券的用户数: " + failedCount.get());
            System.out.println("失败用户列表: " + failedUsers);

        } finally {
            group.shutdownGracefully();
        }
    }
}