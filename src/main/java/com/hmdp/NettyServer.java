package com.hmdp;

import com.hmdp.netty.NettySeckillHandler; // 同一包下，无需修改
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class NettyServer {

    public static void main(String[] args) {
        // 启动 Spring 容器
        ApplicationContext context = SpringApplication.run(NettyServer.class, args);

        // 获取 SeckillHandler Bean
        NettySeckillHandler seckillHandler = context.getBean(NettySeckillHandler.class);

        // 初始化库存（示例）
        seckillHandler.initStock("10", 100);

        // 启动 Netty 服务器
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(seckillHandler);
                        }
                    });

            ChannelFuture future = bootstrap.bind(8080).sync();
            System.out.println("Netty Server started on port 8080");
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}