package com.fragment.io.protocol.demo;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.net.URI;

/**
 * WebSocket协议演示
 * 
 * 演示内容：
 * 1. WebSocket握手过程（HTTP升级）
 * 2. WebSocket帧类型（Text、Binary、Ping、Pong、Close）
 * 3. WebSocket全双工通信
 * 4. WebSocket心跳机制
 * 5. WebSocket连接关闭
 * 
 * @author fragment
 */
public class WebSocketProtocolDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== WebSocket协议演示 ===\n");
        
        // 启动WebSocket服务器
        WebSocketServer server = new WebSocketServer(8082);
        new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
        // 等待服务器启动
        Thread.sleep(1000);
        
        // 启动WebSocket客户端
        WebSocketClient client = new WebSocketClient("ws://localhost:8082/ws");
        client.connect();
        
        // 等待连接建立
        Thread.sleep(1000);
        
        // 发送文本消息
        System.out.println("\n--- 发送文本消息 ---");
        client.sendText("Hello WebSocket!");
        Thread.sleep(500);
        
        client.sendText("这是第二条消息");
        Thread.sleep(500);
        
        // 发送Ping帧
        System.out.println("\n--- 发送Ping帧（心跳） ---");
        client.sendPing();
        Thread.sleep(500);
        
        // 发送二进制消息
        System.out.println("\n--- 发送二进制消息 ---");
        client.sendBinary(new byte[]{1, 2, 3, 4, 5});
        Thread.sleep(500);
        
        // 关闭连接
        System.out.println("\n--- 关闭WebSocket连接 ---");
        client.close();
        
        Thread.sleep(1000);
        server.stop();
    }
    
    /**
     * WebSocket服务器
     */
    static class WebSocketServer {
        private final int port;
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;
        private Channel serverChannel;
        
        public WebSocketServer(int port) {
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
                            
                            // WebSocket服务器处理器
                            pipeline.addLast(new WebSocketServerHandler());
                        }
                    });
                
                System.out.println("WebSocket服务器启动在端口: " + port);
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
     * WebSocket服务器处理器
     */
    static class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {
        private WebSocketServerHandshaker handshaker;
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest) {
                // HTTP握手请求
                handleHttpRequest(ctx, (FullHttpRequest) msg);
            } else if (msg instanceof WebSocketFrame) {
                // WebSocket帧
                handleWebSocketFrame(ctx, (WebSocketFrame) msg);
            }
        }
        
        /**
         * 处理HTTP握手请求
         */
        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
            System.out.println("\n[服务器] 收到WebSocket握手请求:");
            System.out.println("  URI: " + request.uri());
            System.out.println("  请求头:");
            request.headers().forEach(header -> 
                System.out.println("    " + header.getKey() + ": " + header.getValue()));
            
            // 检查是否是WebSocket升级请求
            if (!request.decoderResult().isSuccess() || 
                !"websocket".equals(request.headers().get("Upgrade"))) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
                return;
            }
            
            // 进行WebSocket握手
            WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                "ws://localhost:8082/ws", null, true);
            handshaker = factory.newHandshaker(request);
            
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            } else {
                System.out.println("[服务器] WebSocket握手成功");
                handshaker.handshake(ctx.channel(), request);
            }
        }
        
        /**
         * 处理WebSocket帧
         */
        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            // 关闭帧
            if (frame instanceof CloseWebSocketFrame) {
                System.out.println("[服务器] 收到关闭帧");
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
                return;
            }
            
            // Ping帧
            if (frame instanceof PingWebSocketFrame) {
                System.out.println("[服务器] 收到Ping帧，回复Pong帧");
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
                return;
            }
            
            // Pong帧
            if (frame instanceof PongWebSocketFrame) {
                System.out.println("[服务器] 收到Pong帧");
                return;
            }
            
            // 文本帧
            if (frame instanceof TextWebSocketFrame) {
                String text = ((TextWebSocketFrame) frame).text();
                System.out.println("[服务器] 收到文本消息: " + text);
                
                // 回复消息
                String response = "服务器回复: " + text;
                ctx.writeAndFlush(new TextWebSocketFrame(response));
                return;
            }
            
            // 二进制帧
            if (frame instanceof BinaryWebSocketFrame) {
                byte[] data = new byte[frame.content().readableBytes()];
                frame.content().readBytes(data);
                System.out.println("[服务器] 收到二进制消息，长度: " + data.length);
                
                // 回复消息
                ctx.writeAndFlush(new TextWebSocketFrame("收到二进制数据，长度: " + data.length));
            }
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
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[服务器] WebSocket连接断开");
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[服务器] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
    
    /**
     * WebSocket客户端
     */
    static class WebSocketClient {
        private final String url;
        private EventLoopGroup group;
        private Channel channel;
        private WebSocketClientHandshaker handshaker;
        
        public WebSocketClient(String url) {
            this.url = url;
        }
        
        public void connect() throws Exception {
            group = new NioEventLoopGroup();
            
            URI uri = new URI(url);
            handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, null);
            
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new WebSocketClientHandler(handshaker));
                    }
                });
            
            System.out.println("[客户端] 连接WebSocket服务器: " + url);
            channel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
            
            // 等待握手完成
            handshaker.handshake(channel).sync();
        }
        
        public void sendText(String text) {
            if (channel != null && channel.isActive()) {
                System.out.println("[客户端] 发送文本消息: " + text);
                channel.writeAndFlush(new TextWebSocketFrame(text));
            }
        }
        
        public void sendBinary(byte[] data) {
            if (channel != null && channel.isActive()) {
                System.out.println("[客户端] 发送二进制消息，长度: " + data.length);
                channel.writeAndFlush(new BinaryWebSocketFrame(
                    io.netty.buffer.Unpooled.wrappedBuffer(data)));
            }
        }
        
        public void sendPing() {
            if (channel != null && channel.isActive()) {
                System.out.println("[客户端] 发送Ping帧");
                channel.writeAndFlush(new PingWebSocketFrame());
            }
        }
        
        public void close() {
            if (channel != null && channel.isActive()) {
                System.out.println("[客户端] 发送关闭帧");
                channel.writeAndFlush(new CloseWebSocketFrame());
            }
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }
    
    /**
     * WebSocket客户端处理器
     */
    static class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        
        public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("[客户端] 连接建立，开始WebSocket握手");
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            Channel ch = ctx.channel();
            
            if (!handshaker.isHandshakeComplete()) {
                // 完成握手
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                System.out.println("[客户端] WebSocket握手完成");
                return;
            }
            
            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                    "意外的HTTP响应: " + response.status());
            }
            
            WebSocketFrame frame = (WebSocketFrame) msg;
            
            if (frame instanceof TextWebSocketFrame) {
                String text = ((TextWebSocketFrame) frame).text();
                System.out.println("[客户端] 收到文本消息: " + text);
            } else if (frame instanceof PongWebSocketFrame) {
                System.out.println("[客户端] 收到Pong帧");
            } else if (frame instanceof CloseWebSocketFrame) {
                System.out.println("[客户端] 收到关闭帧");
                ch.close();
            }
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[客户端] WebSocket连接断开");
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[客户端] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
