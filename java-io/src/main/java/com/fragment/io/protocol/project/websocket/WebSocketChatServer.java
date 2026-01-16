package com.fragment.io.protocol.project.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket聊天服务器
 * 
 * 功能特性：
 * 1. 多用户实时聊天
 * 2. 用户上线/下线通知
 * 3. 设置用户昵称
 * 4. 查看在线用户列表
 * 5. 私聊功能
 * 6. 群发消息
 * 7. 心跳检测
 * 
 * @author fragment
 */
public class WebSocketChatServer {
    
    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    // 所有连接的Channel
    private static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    
    // Channel到用户名的映射
    private static final Map<Channel, String> channelUserMap = new ConcurrentHashMap<>();
    
    // 用户名到Channel的映射
    private static final Map<String, Channel> userChannelMap = new ConcurrentHashMap<>();
    
    public WebSocketChatServer(int port) {
        this.port = port;
    }
    
    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        // HTTP编解码器
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new ChunkedWriteHandler());
                        
                        // WebSocket处理器
                        pipeline.addLast(new WebSocketServerHandler());
                    }
                });
            
            System.out.println("WebSocket聊天服务器启动成功");
            System.out.println("访问地址: http://localhost:" + port);
            System.out.println("\n支持的命令:");
            System.out.println("  /name <昵称>    - 设置昵称");
            System.out.println("  /list           - 查看在线用户");
            System.out.println("  /to <用户> <消息> - 私聊");
            System.out.println("  其他消息        - 群发消息");
            System.out.println();
            
            ChannelFuture future = bootstrap.bind(port).sync();
            future.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }
    
    public void shutdown() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
    
    /**
     * WebSocket服务器处理器
     */
    static class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {
        
        private WebSocketServerHandshaker handshaker;
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest) {
                handleHttpRequest(ctx, (FullHttpRequest) msg);
            } else if (msg instanceof WebSocketFrame) {
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            }
        }
        
        /**
         * 处理HTTP请求（WebSocket握手）
         */
        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
            // 如果不是WebSocket升级请求，返回首页
            if (!request.decoderResult().isSuccess() || 
                !"websocket".equals(request.headers().get("Upgrade"))) {
                
                if (request.uri().equals("/")) {
                    sendHttpResponse(ctx, request, createHtmlResponse());
                } else {
                    sendHttpResponse(ctx, request, new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
                }
                return;
            }
            
            // 进行WebSocket握手
            WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                "ws://localhost:8080/ws", null, true);
            handshaker = factory.newHandshaker(request);
            
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            } else {
                handshaker.handshake(ctx.channel(), request);
                
                // 添加到Channel组
                channels.add(ctx.channel());
                
                // 分配默认用户名
                String username = "用户" + ctx.channel().id().asShortText();
                channelUserMap.put(ctx.channel(), username);
                userChannelMap.put(username, ctx.channel());
                
                // 通知所有人有新用户加入
                String message = formatSystemMessage(username + " 加入了聊天室");
                broadcastMessage(message, null);
                
                // 发送欢迎消息
                sendMessage(ctx.channel(), formatSystemMessage(
                    "欢迎来到聊天室！你的昵称是: " + username + "\n" +
                    "使用 /name <昵称> 可以修改昵称\n" +
                    "使用 /list 查看在线用户\n" +
                    "使用 /to <用户> <消息> 发送私聊"));
                
                System.out.println("[系统] " + username + " 加入聊天室");
            }
        }
        
        /**
         * 处理WebSocket帧
         */
        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            // 关闭帧
            if (frame instanceof CloseWebSocketFrame) {
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
                return;
            }
            
            // Ping帧
            if (frame instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
                return;
            }
            
            // 只支持文本帧
            if (!(frame instanceof TextWebSocketFrame)) {
                throw new UnsupportedOperationException("不支持的帧类型");
            }
            
            // 处理文本消息
            String message = ((TextWebSocketFrame) frame).text();
            String username = channelUserMap.get(ctx.channel());
            
            System.out.println("[" + username + "] " + message);
            
            // 处理命令
            if (message.startsWith("/")) {
                handleCommand(ctx.channel(), username, message);
            } else {
                // 群发消息
                String formattedMessage = formatUserMessage(username, message);
                broadcastMessage(formattedMessage, ctx.channel());
            }
        }
        
        /**
         * 处理命令
         */
        private void handleCommand(Channel channel, String username, String command) {
            String[] parts = command.split("\\s+", 3);
            String cmd = parts[0];
            
            switch (cmd) {
                case "/name":
                    // 修改昵称
                    if (parts.length < 2) {
                        sendMessage(channel, formatSystemMessage("用法: /name <昵称>"));
                        return;
                    }
                    
                    String newName = parts[1];
                    
                    // 检查昵称是否已被使用
                    if (userChannelMap.containsKey(newName)) {
                        sendMessage(channel, formatSystemMessage("昵称 " + newName + " 已被使用"));
                        return;
                    }
                    
                    // 更新昵称
                    userChannelMap.remove(username);
                    channelUserMap.put(channel, newName);
                    userChannelMap.put(newName, channel);
                    
                    // 通知所有人
                    String message = formatSystemMessage(username + " 改名为 " + newName);
                    broadcastMessage(message, null);
                    
                    System.out.println("[系统] " + username + " 改名为 " + newName);
                    break;
                    
                case "/list":
                    // 查看在线用户列表
                    StringBuilder userList = new StringBuilder("在线用户 (" + channelUserMap.size() + "):\n");
                    for (String user : userChannelMap.keySet()) {
                        userList.append("  - ").append(user).append("\n");
                    }
                    sendMessage(channel, formatSystemMessage(userList.toString()));
                    break;
                    
                case "/to":
                    // 私聊
                    if (parts.length < 3) {
                        sendMessage(channel, formatSystemMessage("用法: /to <用户> <消息>"));
                        return;
                    }
                    
                    String targetUser = parts[1];
                    String privateMessage = parts[2];
                    
                    Channel targetChannel = userChannelMap.get(targetUser);
                    if (targetChannel == null) {
                        sendMessage(channel, formatSystemMessage("用户 " + targetUser + " 不存在"));
                        return;
                    }
                    
                    // 发送私聊消息
                    String formattedPrivateMsg = formatPrivateMessage(username, targetUser, privateMessage);
                    sendMessage(targetChannel, formattedPrivateMsg);
                    sendMessage(channel, formatPrivateMessage(username, targetUser, privateMessage));
                    
                    System.out.println("[私聊] " + username + " -> " + targetUser + ": " + privateMessage);
                    break;
                    
                default:
                    sendMessage(channel, formatSystemMessage("未知命令: " + cmd));
                    break;
            }
        }
        
        /**
         * 广播消息
         */
        private void broadcastMessage(String message, Channel excludeChannel) {
            for (Channel channel : channels) {
                if (channel != excludeChannel && channel.isActive()) {
                    sendMessage(channel, message);
                }
            }
        }
        
        /**
         * 发送消息给指定Channel
         */
        private void sendMessage(Channel channel, String message) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(message));
            }
        }
        
        /**
         * 格式化用户消息
         */
        private String formatUserMessage(String username, String message) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return String.format("[%s] %s: %s", sdf.format(new Date()), username, message);
        }
        
        /**
         * 格式化系统消息
         */
        private String formatSystemMessage(String message) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return String.format("[%s] [系统] %s", sdf.format(new Date()), message);
        }
        
        /**
         * 格式化私聊消息
         */
        private String formatPrivateMessage(String from, String to, String message) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return String.format("[%s] [私聊] %s -> %s: %s", 
                sdf.format(new Date()), from, to, message);
        }
        
        /**
         * 发送HTTP响应
         */
        private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, 
                                      FullHttpResponse response) {
            if (response.status().code() != 200) {
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 
                    response.content().readableBytes());
            }
            
            ChannelFuture future = ctx.writeAndFlush(response);
            if (!HttpUtil.isKeepAlive(request) || response.status().code() != 200) {
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
        
        /**
         * 创建HTML响应
         */
        private FullHttpResponse createHtmlResponse() {
            String html = getChatHtml();
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                io.netty.buffer.Unpooled.copiedBuffer(html, io.netty.util.CharsetUtil.UTF_8));
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            
            return response;
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // 用户离开
            String username = channelUserMap.remove(ctx.channel());
            if (username != null) {
                userChannelMap.remove(username);
                channels.remove(ctx.channel());
                
                // 通知所有人
                String message = formatSystemMessage(username + " 离开了聊天室");
                broadcastMessage(message, null);
                
                System.out.println("[系统] " + username + " 离开聊天室");
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
        
        /**
         * 获取聊天页面HTML
         */
        private String getChatHtml() {
            return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>WebSocket聊天室</title>\n" +
                "    <style>\n" +
                "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "        body { font-family: Arial, sans-serif; height: 100vh; display: flex; flex-direction: column; }\n" +
                "        .header { background: #4CAF50; color: white; padding: 15px; text-align: center; }\n" +
                "        .container { flex: 1; display: flex; overflow: hidden; }\n" +
                "        .chat-area { flex: 1; display: flex; flex-direction: column; }\n" +
                "        #messages { flex: 1; overflow-y: auto; padding: 20px; background: #f5f5f5; }\n" +
                "        .message { margin: 10px 0; padding: 10px; background: white; border-radius: 5px; }\n" +
                "        .system { background: #fff3cd; border-left: 3px solid #ffc107; }\n" +
                "        .private { background: #d1ecf1; border-left: 3px solid #17a2b8; }\n" +
                "        .input-area { padding: 20px; background: white; border-top: 1px solid #ddd; }\n" +
                "        .input-group { display: flex; gap: 10px; }\n" +
                "        #messageInput { flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 5px; }\n" +
                "        button { padding: 10px 20px; background: #4CAF50; color: white; border: none; border-radius: 5px; cursor: pointer; }\n" +
                "        button:hover { background: #45a049; }\n" +
                "        .status { padding: 10px; background: #e9ecef; text-align: center; }\n" +
                "        .connected { color: #28a745; }\n" +
                "        .disconnected { color: #dc3545; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <h1>WebSocket聊天室</h1>\n" +
                "    </div>\n" +
                "    <div class=\"status\" id=\"status\">\n" +
                "        <span class=\"disconnected\">未连接</span>\n" +
                "    </div>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"chat-area\">\n" +
                "            <div id=\"messages\"></div>\n" +
                "            <div class=\"input-area\">\n" +
                "                <div class=\"input-group\">\n" +
                "                    <input type=\"text\" id=\"messageInput\" placeholder=\"输入消息...\" onkeypress=\"if(event.keyCode==13)sendMessage()\">\n" +
                "                    <button onclick=\"sendMessage()\">发送</button>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <script>\n" +
                "        let ws;\n" +
                "        \n" +
                "        function connect() {\n" +
                "            ws = new WebSocket('ws://' + window.location.host + '/ws');\n" +
                "            \n" +
                "            ws.onopen = function() {\n" +
                "                document.getElementById('status').innerHTML = '<span class=\"connected\">已连接</span>';\n" +
                "                console.log('WebSocket连接已建立');\n" +
                "            };\n" +
                "            \n" +
                "            ws.onmessage = function(event) {\n" +
                "                displayMessage(event.data);\n" +
                "            };\n" +
                "            \n" +
                "            ws.onclose = function() {\n" +
                "                document.getElementById('status').innerHTML = '<span class=\"disconnected\">连接已断开</span>';\n" +
                "                console.log('WebSocket连接已关闭');\n" +
                "                setTimeout(connect, 3000);\n" +
                "            };\n" +
                "            \n" +
                "            ws.onerror = function(error) {\n" +
                "                console.error('WebSocket错误:', error);\n" +
                "            };\n" +
                "        }\n" +
                "        \n" +
                "        function sendMessage() {\n" +
                "            const input = document.getElementById('messageInput');\n" +
                "            const message = input.value.trim();\n" +
                "            \n" +
                "            if (message && ws && ws.readyState === WebSocket.OPEN) {\n" +
                "                ws.send(message);\n" +
                "                input.value = '';\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        function displayMessage(message) {\n" +
                "            const messagesDiv = document.getElementById('messages');\n" +
                "            const messageDiv = document.createElement('div');\n" +
                "            messageDiv.className = 'message';\n" +
                "            \n" +
                "            if (message.includes('[系统]')) {\n" +
                "                messageDiv.classList.add('system');\n" +
                "            } else if (message.includes('[私聊]')) {\n" +
                "                messageDiv.classList.add('private');\n" +
                "            }\n" +
                "            \n" +
                "            messageDiv.textContent = message;\n" +
                "            messagesDiv.appendChild(messageDiv);\n" +
                "            messagesDiv.scrollTop = messagesDiv.scrollHeight;\n" +
                "        }\n" +
                "        \n" +
                "        // 页面加载时连接\n" +
                "        connect();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
        }
    }
    
    public static void main(String[] args) throws Exception {
        int port = 8080;
        WebSocketChatServer server = new WebSocketChatServer(port);
        server.start();
    }
}
