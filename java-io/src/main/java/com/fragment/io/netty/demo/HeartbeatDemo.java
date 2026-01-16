package com.fragment.io.netty.demo;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

/**
 * 心跳检测演示
 * 
 * 功能：
 * 1. 演示IdleStateHandler的使用
 * 2. 演示服务端空闲检测
 * 3. 演示客户端心跳发送
 * 4. 演示完整的心跳机制
 * 
 * 机制：
 * - 客户端：每30秒发送心跳
 * - 服务端：60秒未收到消息则关闭连接
 * 
 * 运行方式：
 * 1. 先运行服务端：HeartbeatServer.main()
 * 2. 再运行客户端：HeartbeatClient.main()
 * 3. 观察心跳日志
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class HeartbeatDemo {
    
    private static final int PORT = 8083;
    private static final String HEARTBEAT_MSG = "PING";
    
    /**
     * 心跳服务端
     */
    public static class HeartbeatServer {
        
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
                                // 空闲检测：60秒读空闲
                                .addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
                                // 空闲处理器
                                .addLast(new ServerIdleHandler())
                                // 字符串编解码
                                .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                // 业务处理器
                                .addLast(new ServerBusinessHandler());
                        }
                    });
                
                ChannelFuture future = bootstrap.bind(PORT).sync();
                System.out.println("=== 心跳服务端启动 ===");
                System.out.println("端口: " + PORT);
                System.out.println("空闲超时: 60秒");
                System.out.println("====================\n");
                
                future.channel().closeFuture().sync();
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }
        
        /**
         * 服务端空闲处理器
         */
        static class ServerIdleHandler extends ChannelInboundHandlerAdapter {
            
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof IdleStateEvent) {
                    IdleStateEvent event = (IdleStateEvent) evt;
                    
                    if (event.state() == IdleState.READER_IDLE) {
                        // 60秒未收到客户端消息
                        System.out.println("[服务端] 60秒未收到客户端消息，关闭连接: " 
                            + ctx.channel().remoteAddress());
                        ctx.close();
                    }
                } else {
                    ctx.fireUserEventTriggered(evt);
                }
            }
        }
        
        /**
         * 服务端业务处理器
         */
        static class ServerBusinessHandler extends SimpleChannelInboundHandler<String> {
            
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                System.out.println("[服务端] 客户端连接: " + ctx.channel().remoteAddress());
            }
            
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                if (HEARTBEAT_MSG.equals(msg)) {
                    // 收到心跳
                    System.out.println("[服务端] 收到心跳: " + ctx.channel().remoteAddress());
                    // 回复心跳
                    ctx.writeAndFlush("PONG");
                } else {
                    // 业务消息
                    System.out.println("[服务端] 收到消息: " + msg);
                    ctx.writeAndFlush("Echo: " + msg);
                }
            }
            
            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                System.out.println("[服务端] 客户端断开: " + ctx.channel().remoteAddress());
            }
            
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                System.err.println("[服务端] 异常: " + cause.getMessage());
                ctx.close();
            }
        }
    }
    
    /**
     * 心跳客户端
     */
    public static class HeartbeatClient {
        
        public static void main(String[] args) throws Exception {
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                // 空闲检测：30秒写空闲
                                .addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS))
                                // 空闲处理器
                                .addLast(new ClientIdleHandler())
                                // 字符串编解码
                                .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                // 业务处理器
                                .addLast(new ClientBusinessHandler());
                        }
                    });
                
                ChannelFuture future = bootstrap.connect("localhost", PORT).sync();
                System.out.println("=== 心跳客户端启动 ===");
                System.out.println("连接服务器: localhost:" + PORT);
                System.out.println("心跳间隔: 30秒");
                System.out.println("====================\n");
                
                future.channel().closeFuture().sync();
            } finally {
                workerGroup.shutdownGracefully();
            }
        }
        
        /**
         * 客户端空闲处理器
         */
        static class ClientIdleHandler extends ChannelInboundHandlerAdapter {
            
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof IdleStateEvent) {
                    IdleStateEvent event = (IdleStateEvent) evt;
                    
                    if (event.state() == IdleState.WRITER_IDLE) {
                        // 30秒未发送消息，发送心跳
                        System.out.println("[客户端] 发送心跳");
                        ctx.writeAndFlush(HEARTBEAT_MSG);
                    }
                } else {
                    ctx.fireUserEventTriggered(evt);
                }
            }
        }
        
        /**
         * 客户端业务处理器
         */
        static class ClientBusinessHandler extends SimpleChannelInboundHandler<String> {
            
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                System.out.println("[客户端] 连接成功: " + ctx.channel().localAddress());
                
                // 发送测试消息
                ctx.writeAndFlush("Hello Server");
            }
            
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                if ("PONG".equals(msg)) {
                    // 收到心跳响应
                    System.out.println("[客户端] 收到心跳响应");
                } else {
                    // 业务消息
                    System.out.println("[客户端] 收到消息: " + msg);
                }
            }
            
            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                System.out.println("[客户端] 连接断开");
            }
            
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                System.err.println("[客户端] 异常: " + cause.getMessage());
                ctx.close();
            }
        }
    }
    
    /**
     * 测试入口：同时启动服务端和客户端
     */
    public static void main(String[] args) throws Exception {
        // 启动服务端
        new Thread(() -> {
            try {
                HeartbeatServer.main(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
        // 等待服务端启动
        Thread.sleep(1000);
        
        // 启动客户端
        HeartbeatClient.main(null);
    }
}
