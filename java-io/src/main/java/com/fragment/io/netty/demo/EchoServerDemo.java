package com.fragment.io.netty.demo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;

/**
 * Echo服务器演示
 * 
 * 功能：
 * 1. 演示Netty的基本使用
 * 2. 演示Bootstrap的配置和启动
 * 3. 演示Channel的生命周期
 * 4. 演示简单的业务处理
 * 
 * 运行方式：
 * 1. 启动服务端：运行main方法
 * 2. 使用telnet测试：telnet localhost 8080
 * 3. 输入任意内容，服务端会回显
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class EchoServerDemo {
    
    private static final int PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        // 1. 创建EventLoopGroup
        // BossGroup：负责接收连接
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // WorkerGroup：负责处理I/O
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            // 2. 创建ServerBootstrap
            ServerBootstrap bootstrap = new ServerBootstrap();
            
            // 3. 配置Bootstrap
            bootstrap.group(bossGroup, workerGroup)
                // 指定Channel类型
                .channel(NioServerSocketChannel.class)
                // 配置ServerChannel选项
                .option(ChannelOption.SO_BACKLOG, 128)
                // 配置客户端Channel选项
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                // 配置客户端Channel的Handler
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 添加Handler到Pipeline
                        ch.pipeline().addLast(new EchoServerHandler());
                    }
                });
            
            // 4. 绑定端口并启动
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("Echo服务器启动成功，端口：" + PORT);
            System.out.println("使用telnet测试：telnet localhost " + PORT);
            
            // 5. 等待服务器关闭
            future.channel().closeFuture().sync();
        } finally {
            // 6. 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * Echo服务器处理器
     */
    static class EchoServerHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            System.out.println("[生命周期] channelRegistered: Channel注册到EventLoop");
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("[生命周期] channelActive: Channel激活，连接建立");
            System.out.println("[连接信息] 客户端地址: " + ctx.channel().remoteAddress());
            
            // 发送欢迎消息
            String welcome = "欢迎使用Echo服务器！\n";
            ctx.writeAndFlush(Unpooled.copiedBuffer(welcome, CharsetUtil.UTF_8));
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                // 读取客户端发送的数据
                String received = buf.toString(CharsetUtil.UTF_8);
                System.out.println("[接收数据] " + received.trim());
                
                // 回显数据
                String echo = "Echo: " + received;
                ctx.writeAndFlush(Unpooled.copiedBuffer(echo, CharsetUtil.UTF_8));
            } finally {
                // 释放ByteBuf
                buf.release();
            }
        }
        
        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            System.out.println("[生命周期] channelReadComplete: 读取完成");
            // 刷新缓冲区
            ctx.flush();
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[生命周期] channelInactive: Channel失活，连接断开");
        }
        
        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            System.out.println("[生命周期] channelUnregistered: Channel从EventLoop注销");
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[异常处理] 发生异常: " + cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
    }
}
