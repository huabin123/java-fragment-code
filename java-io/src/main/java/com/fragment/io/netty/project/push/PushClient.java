package com.fragment.io.netty.project.push;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

/**
 * TCP推送客户端
 * 
 * 功能：
 * 1. 连接服务器
 * 2. 发送心跳
 * 3. 接收推送
 * 4. 自动重连
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class PushClient {
    
    private static final String HOST = "localhost";
    private static final int PORT = 9999;
    private EventLoopGroup workerGroup;
    private Channel channel;
    
    /**
     * 连接服务器
     */
    public void connect() {
        workerGroup = new NioEventLoopGroup();
        
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        // 30秒写空闲检测
                        .addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS))
                        .addLast(new StringDecoder(CharsetUtil.UTF_8))
                        .addLast(new StringEncoder(CharsetUtil.UTF_8))
                        .addLast(new PushClientHandler(PushClient.this));
                }
            });
        
        doConnect(bootstrap);
    }
    
    /**
     * 执行连接
     */
    private void doConnect(Bootstrap bootstrap) {
        try {
            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            channel = future.channel();
            System.out.println("[客户端] 连接成功: " + HOST + ":" + PORT);
            
            channel.closeFuture().sync();
        } catch (Exception e) {
            System.err.println("[客户端] 连接失败，5秒后重连...");
            
            // 5秒后重连
            workerGroup.schedule(() -> {
                System.out.println("[客户端] 尝试重连...");
                doConnect(bootstrap);
            }, 5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * 推送客户端处理器
     */
    static class PushClientHandler extends SimpleChannelInboundHandler<String> {
        
        private final PushClient client;
        
        public PushClientHandler(PushClient client) {
            this.client = client;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            System.out.println("[接收] " + msg.trim());
        }
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                
                if (event.state() == IdleState.WRITER_IDLE) {
                    // 30秒未发送消息，发送心跳
                    System.out.println("[客户端] 发送心跳");
                    ctx.writeAndFlush("PING\n");
                }
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
    
    /**
     * 测试
     */
    public static void main(String[] args) {
        PushClient client = new PushClient();
        client.connect();
    }
}
