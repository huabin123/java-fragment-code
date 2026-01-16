package com.fragment.io.protocol.demo;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.nio.charset.StandardCharsets;

/**
 * TCP协议演示
 * 
 * 演示内容：
 * 1. TCP三次握手和四次挥手的观察
 * 2. TCP粘包拆包问题
 * 3. 基于长度字段的协议设计
 * 4. TCP连接状态管理
 * 
 * @author fragment
 */
public class TcpProtocolDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== TCP协议演示 ===\n");
        
        // 启动服务器
        TcpServer server = new TcpServer(8080);
        new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
        // 等待服务器启动
        Thread.sleep(1000);
        
        // 启动客户端
        TcpClient client = new TcpClient("localhost", 8080);
        client.connect();
        
        // 发送多条消息，观察粘包拆包处理
        System.out.println("\n--- 客户端发送消息 ---");
        client.sendMessage("第一条消息");
        Thread.sleep(100);
        client.sendMessage("第二条消息");
        Thread.sleep(100);
        client.sendMessage("这是一条比较长的消息，用于测试TCP协议的数据传输能力");
        
        // 等待消息处理
        Thread.sleep(2000);
        
        // 关闭连接，观察四次挥手
        System.out.println("\n--- 关闭连接 ---");
        client.close();
        server.stop();
    }
    
    /**
     * TCP服务器
     */
    static class TcpServer {
        private final int port;
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;
        private Channel serverChannel;
        
        public TcpServer(int port) {
            this.port = port;
        }
        
        public void start() throws Exception {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 使用长度字段解决粘包拆包问题
                            // maxFrameLength: 最大帧长度
                            // lengthFieldOffset: 长度字段偏移量
                            // lengthFieldLength: 长度字段长度
                            // lengthAdjustment: 长度调整值
                            // initialBytesToStrip: 跳过的字节数
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(
                                1024, 0, 4, 0, 4));
                            pipeline.addLast(new LengthFieldPrepender(4));
                            
                            pipeline.addLast(new TcpServerHandler());
                        }
                    });
                
                System.out.println("TCP服务器启动在端口: " + port);
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannel = future.channel();
                
                serverChannel.closeFuture().sync();
            } finally {
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        }
        
        public void stop() {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
    }
    
    /**
     * TCP服务器处理器
     */
    static class TcpServerHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("[服务器] 客户端连接建立: " + ctx.channel().remoteAddress());
            System.out.println("        (TCP三次握手完成)");
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            String message = buf.toString(StandardCharsets.UTF_8);
            System.out.println("[服务器] 收到消息: " + message);
            
            // 回复消息
            String response = "服务器已收到: " + message;
            ByteBuf responseBuf = Unpooled.copiedBuffer(response, StandardCharsets.UTF_8);
            ctx.writeAndFlush(responseBuf);
            
            buf.release();
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[服务器] 客户端连接断开: " + ctx.channel().remoteAddress());
            System.out.println("        (TCP四次挥手完成)");
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[服务器] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
    
    /**
     * TCP客户端
     */
    static class TcpClient {
        private final String host;
        private final int port;
        private EventLoopGroup group;
        private Channel channel;
        
        public TcpClient(String host, int port) {
            this.host = host;
            this.port = port;
        }
        
        public void connect() throws Exception {
            group = new NioEventLoopGroup();
            
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        // 使用长度字段解决粘包拆包问题
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(
                            1024, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));
                        
                        pipeline.addLast(new TcpClientHandler());
                    }
                });
            
            System.out.println("[客户端] 开始连接服务器: " + host + ":" + port);
            System.out.println("        (发起TCP三次握手)");
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
        }
        
        public void sendMessage(String message) {
            if (channel != null && channel.isActive()) {
                ByteBuf buf = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
                channel.writeAndFlush(buf);
                System.out.println("[客户端] 发送消息: " + message);
            }
        }
        
        public void close() {
            if (channel != null) {
                System.out.println("[客户端] 关闭连接");
                System.out.println("        (发起TCP四次挥手)");
                channel.close();
            }
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }
    
    /**
     * TCP客户端处理器
     */
    static class TcpClientHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("[客户端] 连接建立成功");
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            String message = buf.toString(StandardCharsets.UTF_8);
            System.out.println("[客户端] 收到回复: " + message);
            buf.release();
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[客户端] 连接已断开");
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[客户端] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
