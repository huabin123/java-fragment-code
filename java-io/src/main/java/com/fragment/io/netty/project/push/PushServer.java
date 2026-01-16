package com.fragment.io.netty.project.push;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.concurrent.TimeUnit;

/**
 * TCP长连接推送服务器
 * 
 * 功能：
 * 1. 维持长连接
 * 2. 心跳检测
 * 3. 消息推送
 * 4. 自动重连
 * 
 * 使用方式：
 * 1. 启动服务器
 * 2. 客户端连接
 * 3. 服务器定期推送消息
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class PushServer {
    
    private static final int PORT = 9999;
    private static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    
    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                            // 60秒读空闲检测
                            .addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
                            .addLast(new StringDecoder(CharsetUtil.UTF_8))
                            .addLast(new StringEncoder(CharsetUtil.UTF_8))
                            .addLast(new PushServerHandler());
                    }
                });
            
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("=== TCP推送服务器启动成功 ===");
            System.out.println("端口: " + PORT);
            System.out.println("===========================\n");
            
            // 启动推送任务
            startPushTask();
            
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * 启动推送任务
     */
    private static void startPushTask() {
        new Thread(() -> {
            int count = 0;
            while (true) {
                try {
                    Thread.sleep(10000);  // 每10秒推送一次
                    
                    if (channels.size() > 0) {
                        String message = String.format("[推送] 这是第 %d 条推送消息", ++count);
                        channels.writeAndFlush(message + "\n");
                        System.out.println(message + " (在线: " + channels.size() + ")");
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
    
    /**
     * 推送服务器处理器
     */
    static class PushServerHandler extends SimpleChannelInboundHandler<String> {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channels.add(ctx.channel());
            System.out.println("[连接] 客户端连接: " + ctx.channel().remoteAddress() 
                + " (在线: " + channels.size() + ")");
            
            ctx.writeAndFlush("[系统] 欢迎连接到推送服务器！\n");
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[断开] 客户端断开: " + ctx.channel().remoteAddress() 
                + " (在线: " + channels.size() + ")");
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            String text = msg.trim();
            
            if ("PING".equals(text)) {
                // 心跳响应
                ctx.writeAndFlush("PONG\n");
            } else {
                System.out.println("[消息] " + ctx.channel().remoteAddress() + ": " + text);
                ctx.writeAndFlush("[回复] 收到消息: " + text + "\n");
            }
        }
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                
                if (event.state() == IdleState.READER_IDLE) {
                    System.out.println("[超时] 60秒未收到心跳，关闭连接: " 
                        + ctx.channel().remoteAddress());
                    ctx.close();
                }
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[异常] " + cause.getMessage());
            ctx.close();
        }
    }
}
