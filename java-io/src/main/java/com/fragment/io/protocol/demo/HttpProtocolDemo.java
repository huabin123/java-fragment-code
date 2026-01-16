package com.fragment.io.protocol.demo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP协议演示
 * 
 * 演示内容：
 * 1. HTTP/1.1协议的请求响应模型
 * 2. HTTP请求方法（GET、POST）
 * 3. HTTP请求头和响应头
 * 4. HTTP状态码
 * 5. Keep-Alive连接复用
 * 6. Content-Type和Content-Length
 * 
 * @author fragment
 */
public class HttpProtocolDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== HTTP协议演示 ===\n");
        
        // 启动HTTP服务器
        HttpServer server = new HttpServer(8081);
        new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
        // 等待服务器启动
        Thread.sleep(1000);
        
        // 发送HTTP请求
        System.out.println("\n--- 发送HTTP GET请求 ---");
        sendHttpRequest("http://localhost:8081/hello", "GET");
        
        Thread.sleep(500);
        
        System.out.println("\n--- 发送HTTP POST请求 ---");
        sendHttpRequest("http://localhost:8081/data", "POST");
        
        Thread.sleep(500);
        
        System.out.println("\n--- 测试Keep-Alive连接复用 ---");
        testKeepAlive();
        
        Thread.sleep(2000);
        server.stop();
    }
    
    /**
     * 发送HTTP请求
     */
    private static void sendHttpRequest(String urlString, String method) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("User-Agent", "HttpProtocolDemo/1.0");
            conn.setRequestProperty("Accept", "text/plain");
            
            if ("POST".equals(method)) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                String jsonData = "{\"name\":\"张三\",\"age\":25}";
                conn.getOutputStream().write(jsonData.getBytes(CharsetUtil.UTF_8));
            }
            
            int responseCode = conn.getResponseCode();
            System.out.println("响应状态码: " + responseCode + " " + conn.getResponseMessage());
            System.out.println("响应头:");
            conn.getHeaderFields().forEach((key, values) -> {
                if (key != null) {
                    System.out.println("  " + key + ": " + String.join(", ", values));
                }
            });
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), CharsetUtil.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            System.out.println("响应体: " + response.toString());
            
        } catch (Exception e) {
            System.err.println("请求失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试Keep-Alive连接复用
     */
    private static void testKeepAlive() {
        try {
            URL url = new URL("http://localhost:8081/hello");
            
            // 第一次请求
            HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
            conn1.setRequestProperty("Connection", "keep-alive");
            System.out.println("第一次请求 - 响应码: " + conn1.getResponseCode());
            conn1.getInputStream().close();
            
            // 第二次请求（复用连接）
            HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
            conn2.setRequestProperty("Connection", "keep-alive");
            System.out.println("第二次请求 - 响应码: " + conn2.getResponseCode());
            System.out.println("(连接复用，无需重新建立TCP连接)");
            conn2.getInputStream().close();
            
        } catch (Exception e) {
            System.err.println("Keep-Alive测试失败: " + e.getMessage());
        }
    }
    
    /**
     * HTTP服务器
     */
    static class HttpServer {
        private final int port;
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;
        private Channel serverChannel;
        
        public HttpServer(int port) {
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
                            // HTTP消息聚合器，将多个HTTP消息聚合成一个完整的FullHttpRequest
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            // 自定义处理器
                            pipeline.addLast(new HttpServerHandler());
                        }
                    });
                
                System.out.println("HTTP服务器启动在端口: " + port);
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
     * HTTP服务器处理器
     */
    static class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            System.out.println("\n[服务器] 收到HTTP请求:");
            System.out.println("  方法: " + request.method());
            System.out.println("  URI: " + request.uri());
            System.out.println("  版本: " + request.protocolVersion());
            System.out.println("  请求头:");
            request.headers().forEach(header -> 
                System.out.println("    " + header.getKey() + ": " + header.getValue()));
            
            if (request.content().readableBytes() > 0) {
                String body = request.content().toString(CharsetUtil.UTF_8);
                System.out.println("  请求体: " + body);
            }
            
            // 根据URI路由
            String uri = request.uri();
            FullHttpResponse response;
            
            if (uri.startsWith("/hello")) {
                response = handleHello(request);
            } else if (uri.startsWith("/data")) {
                response = handleData(request);
            } else {
                response = handle404();
            }
            
            // 处理Keep-Alive
            boolean keepAlive = HttpUtil.isKeepAlive(request);
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            
            // 发送响应
            ChannelFuture future = ctx.writeAndFlush(response);
            
            // 如果不是Keep-Alive，关闭连接
            if (!keepAlive) {
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
        
        /**
         * 处理/hello请求
         */
        private FullHttpResponse handleHello(FullHttpRequest request) {
            String content = "Hello, HTTP Protocol Demo!";
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
            );
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.SERVER, "Netty/4.1");
            
            return response;
        }
        
        /**
         * 处理/data请求
         */
        private FullHttpResponse handleData(FullHttpRequest request) {
            String requestBody = request.content().toString(CharsetUtil.UTF_8);
            String content = "收到数据: " + requestBody;
            
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
            );
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            
            return response;
        }
        
        /**
         * 处理404
         */
        private FullHttpResponse handle404() {
            String content = "404 Not Found";
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_FOUND,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
            );
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            
            return response;
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[服务器] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
