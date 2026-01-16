package com.fragment.io.netty.project.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket聊天室服务器
 * 
 * 功能：
 * 1. 支持多人在线聊天
 * 2. 支持用户名设置
 * 3. 支持查看在线用户
 * 4. 支持私聊
 * 5. 消息广播
 * 
 * 使用方式：
 * 1. 启动服务器：运行main方法
 * 2. 浏览器访问：http://localhost:8080/chat.html
 * 3. 设置用户名：/name 用户名
 * 4. 查看在线用户：/list
 * 5. 私聊：/to 用户名 消息内容
 * 6. 群聊：直接输入消息
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class WebSocketChatServer {
    
    private static final int PORT = 8080;
    
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
                            // HTTP编解码
                            .addLast(new HttpServerCodec())
                            .addLast(new HttpObjectAggregator(65536))
                            .addLast(new ChunkedWriteHandler())
                            // WebSocket支持
                            .addLast(new WebSocketServerProtocolHandler("/ws"))
                            // 聊天处理器
                            .addLast(new ChatServerHandler());
                    }
                });
            
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("=== WebSocket聊天室启动成功 ===");
            System.out.println("访问地址: http://localhost:" + PORT + "/chat.html");
            System.out.println("WebSocket地址: ws://localhost:" + PORT + "/ws");
            System.out.println("=============================\n");
            
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * 聊天处理器
     */
    static class ChatServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        
        // 存储所有连接的Channel
        private static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        
        // 存储用户名和Channel的映射
        private static final Map<String, Channel> userChannelMap = new ConcurrentHashMap<>();
        private static final Map<Channel, String> channelUserMap = new ConcurrentHashMap<>();
        
        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            Channel channel = ctx.channel();
            String channelId = channel.id().asShortText();
            
            // 通知所有用户有新用户加入
            String message = String.format("[系统] 新用户加入：%s (在线人数：%d)", 
                channelId, channels.size() + 1);
            channels.writeAndFlush(new TextWebSocketFrame(message));
            
            // 添加到Channel组
            channels.add(channel);
            
            System.out.println(message);
        }
        
        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            Channel channel = ctx.channel();
            String username = channelUserMap.remove(channel);
            
            if (username != null) {
                userChannelMap.remove(username);
            }
            
            // 通知所有用户有用户离开
            String displayName = username != null ? username : channel.id().asShortText();
            String message = String.format("[系统] 用户离开：%s (在线人数：%d)", 
                displayName, channels.size());
            channels.writeAndFlush(new TextWebSocketFrame(message));
            
            System.out.println(message);
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
            String text = msg.text().trim();
            Channel channel = ctx.channel();
            
            if (text.isEmpty()) {
                return;
            }
            
            // 处理命令
            if (text.startsWith("/")) {
                handleCommand(ctx, text);
            } else {
                // 群聊消息
                String username = getUserName(channel);
                String message = String.format("[%s] %s", username, text);
                channels.writeAndFlush(new TextWebSocketFrame(message));
                System.out.println(message);
            }
        }
        
        /**
         * 处理命令
         */
        private void handleCommand(ChannelHandlerContext ctx, String text) {
            Channel channel = ctx.channel();
            
            if (text.startsWith("/name ")) {
                // 设置用户名
                String newUsername = text.substring(6).trim();
                
                if (newUsername.isEmpty()) {
                    ctx.writeAndFlush(new TextWebSocketFrame("[系统] 用户名不能为空"));
                    return;
                }
                
                if (userChannelMap.containsKey(newUsername)) {
                    ctx.writeAndFlush(new TextWebSocketFrame("[系统] 用户名已存在"));
                    return;
                }
                
                // 移除旧用户名
                String oldUsername = channelUserMap.get(channel);
                if (oldUsername != null) {
                    userChannelMap.remove(oldUsername);
                }
                
                // 设置新用户名
                userChannelMap.put(newUsername, channel);
                channelUserMap.put(channel, newUsername);
                
                ctx.writeAndFlush(new TextWebSocketFrame("[系统] 用户名设置为：" + newUsername));
                
                // 通知其他用户
                String message = String.format("[系统] %s 加入聊天室", newUsername);
                channels.stream()
                    .filter(ch -> ch != channel)
                    .forEach(ch -> ch.writeAndFlush(new TextWebSocketFrame(message)));
                
                System.out.println(message);
                
            } else if (text.equals("/list")) {
                // 列出在线用户
                StringBuilder sb = new StringBuilder("[系统] 在线用户列表：\n");
                int index = 1;
                for (String username : userChannelMap.keySet()) {
                    sb.append(index++).append(". ").append(username).append("\n");
                }
                sb.append("总计：").append(userChannelMap.size()).append(" 人");
                ctx.writeAndFlush(new TextWebSocketFrame(sb.toString()));
                
            } else if (text.startsWith("/to ")) {
                // 私聊
                String[] parts = text.substring(4).split(" ", 2);
                if (parts.length < 2) {
                    ctx.writeAndFlush(new TextWebSocketFrame("[系统] 格式错误，使用：/to 用户名 消息内容"));
                    return;
                }
                
                String targetUser = parts[0].trim();
                String message = parts[1].trim();
                
                Channel targetChannel = userChannelMap.get(targetUser);
                if (targetChannel == null) {
                    ctx.writeAndFlush(new TextWebSocketFrame("[系统] 用户不存在：" + targetUser));
                    return;
                }
                
                String username = getUserName(channel);
                String privateMsg = String.format("[私聊] %s 对你说：%s", username, message);
                targetChannel.writeAndFlush(new TextWebSocketFrame(privateMsg));
                
                ctx.writeAndFlush(new TextWebSocketFrame(
                    String.format("[私聊] 你对 %s 说：%s", targetUser, message)));
                
                System.out.println(String.format("[私聊] %s -> %s: %s", username, targetUser, message));
                
            } else {
                ctx.writeAndFlush(new TextWebSocketFrame(
                    "[系统] 未知命令。可用命令：\n" +
                    "/name 用户名 - 设置用户名\n" +
                    "/list - 查看在线用户\n" +
                    "/to 用户名 消息 - 私聊"));
            }
        }
        
        /**
         * 获取用户名
         */
        private String getUserName(Channel channel) {
            String username = channelUserMap.get(channel);
            return username != null ? username : channel.id().asShortText();
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[异常] " + cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
    }
}
