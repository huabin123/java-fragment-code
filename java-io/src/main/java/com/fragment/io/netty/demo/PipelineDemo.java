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
 * Pipeline执行流程演示
 * 
 * 功能：
 * 1. 演示入站Handler的执行顺序
 * 2. 演示出站Handler的执行顺序
 * 3. 演示事件的传播机制
 * 4. 演示ctx.write() vs channel.write()的区别
 * 
 * 关键点：
 * - 入站事件：从Head开始，依次经过InboundHandler
 * - 出站事件：从调用位置开始，向前查找OutboundHandler
 * 
 * 运行方式：
 * 1. 启动服务端：运行main方法
 * 2. 使用telnet测试：telnet localhost 8081
 * 3. 输入任意内容，观察Handler的执行顺序
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class PipelineDemo {
    
    private static final int PORT = 8081;
    
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
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        // 添加Handler到Pipeline
                        // 注意：添加顺序很重要
                        
                        // 入站Handler
                        pipeline.addLast("inbound1", new InboundHandler1());
                        pipeline.addLast("inbound2", new InboundHandler2());
                        pipeline.addLast("inbound3", new InboundHandler3());
                        
                        // 出站Handler
                        pipeline.addLast("outbound1", new OutboundHandler1());
                        pipeline.addLast("outbound2", new OutboundHandler2());
                        pipeline.addLast("outbound3", new OutboundHandler3());
                        
                        System.out.println("\n=== Pipeline配置完成 ===");
                        System.out.println("入站Handler顺序: inbound1 → inbound2 → inbound3");
                        System.out.println("出站Handler顺序: outbound3 → outbound2 → outbound1");
                        System.out.println("========================\n");
                    }
                });
            
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("Pipeline演示服务器启动，端口：" + PORT);
            System.out.println("使用telnet测试：telnet localhost " + PORT);
            
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * 入站Handler 1
     */
    static class InboundHandler1 extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            System.out.println("→ InboundHandler1.channelRead()");
            // 传递给下一个InboundHandler
            ctx.fireChannelRead(msg);
        }
    }
    
    /**
     * 入站Handler 2
     */
    static class InboundHandler2 extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            System.out.println("→ InboundHandler2.channelRead()");
            // 传递给下一个InboundHandler
            ctx.fireChannelRead(msg);
        }
    }
    
    /**
     * 入站Handler 3
     * 在这里触发出站事件
     */
    static class InboundHandler3 extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            System.out.println("→ InboundHandler3.channelRead()");
            
            ByteBuf buf = (ByteBuf) msg;
            try {
                String received = buf.toString(CharsetUtil.UTF_8);
                System.out.println("\n[接收数据] " + received.trim());
                
                // 触发出站事件
                System.out.println("\n=== 开始出站流程 ===");
                String response = "Echo: " + received;
                
                // 方式1：从当前Handler开始，向前查找OutboundHandler
                // 会经过：outbound3 → outbound2 → outbound1
                ctx.writeAndFlush(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8));
                
                // 方式2：从Tail开始，向前查找OutboundHandler
                // 也会经过：outbound3 → outbound2 → outbound1
                // ctx.channel().writeAndFlush(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8));
                
            } finally {
                buf.release();
            }
        }
    }
    
    /**
     * 出站Handler 1
     */
    static class OutboundHandler1 extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            System.out.println("← OutboundHandler1.write()");
            // 传递给下一个OutboundHandler（向Head方向）
            ctx.write(msg, promise);
        }
    }
    
    /**
     * 出站Handler 2
     */
    static class OutboundHandler2 extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            System.out.println("← OutboundHandler2.write()");
            // 传递给下一个OutboundHandler
            ctx.write(msg, promise);
        }
    }
    
    /**
     * 出站Handler 3
     */
    static class OutboundHandler3 extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            System.out.println("← OutboundHandler3.write()");
            // 传递给下一个OutboundHandler
            ctx.write(msg, promise);
        }
        
        @Override
        public void flush(ChannelHandlerContext ctx) {
            System.out.println("← OutboundHandler3.flush()");
            System.out.println("=== 出站流程结束 ===\n");
            ctx.flush();
        }
    }
}
