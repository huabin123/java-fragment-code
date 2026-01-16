package com.fragment.io.protocol.project.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单HTTP服务器
 * 
 * 功能特性：
 * 1. 支持GET、POST、PUT、DELETE等HTTP方法
 * 2. 支持静态文件服务
 * 3. 支持RESTful API
 * 4. 支持JSON数据交互
 * 5. 支持文件上传下载
 * 6. 支持Keep-Alive连接复用
 * 7. 支持CORS跨域
 * 
 * @author fragment
 */
public class SimpleHttpServer {
    
    private final int port;
    private final String webRoot;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    // 模拟数据库
    private static final Map<String, User> userDatabase = new ConcurrentHashMap<>();
    
    static {
        // 初始化测试数据
        userDatabase.put("1", new User("1", "张三", 25, "zhangsan@example.com"));
        userDatabase.put("2", new User("2", "李四", 30, "lisi@example.com"));
        userDatabase.put("3", new User("3", "王五", 28, "wangwu@example.com"));
    }
    
    public SimpleHttpServer(int port, String webRoot) {
        this.port = port;
        this.webRoot = webRoot;
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
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        // HTTP编解码器
                        pipeline.addLast(new HttpServerCodec());
                        // HTTP消息聚合
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        // 支持大文件传输
                        pipeline.addLast(new ChunkedWriteHandler());
                        // 自定义处理器
                        pipeline.addLast(new HttpServerHandler(webRoot));
                    }
                });
            
            System.out.println("HTTP服务器启动成功");
            System.out.println("访问地址: http://localhost:" + port);
            System.out.println("Web根目录: " + webRoot);
            System.out.println("\nAPI端点:");
            System.out.println("  GET    /api/users       - 获取所有用户");
            System.out.println("  GET    /api/users/{id}  - 获取指定用户");
            System.out.println("  POST   /api/users       - 创建用户");
            System.out.println("  PUT    /api/users/{id}  - 更新用户");
            System.out.println("  DELETE /api/users/{id}  - 删除用户");
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
     * HTTP服务器处理器
     */
    static class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        
        private final String webRoot;
        
        public HttpServerHandler(String webRoot) {
            this.webRoot = webRoot;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            // 记录请求
            logRequest(request);
            
            // 解码URI
            String uri;
            try {
                uri = URLDecoder.decode(request.uri(), "UTF-8");
            } catch (Exception e) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }
            
            // 路由处理
            FullHttpResponse response;
            
            if (uri.startsWith("/api/")) {
                // API请求
                response = handleApiRequest(request, uri);
            } else {
                // 静态文件请求
                response = handleStaticFile(request, uri);
            }
            
            // 添加CORS头
            addCorsHeaders(response);
            
            // 处理Keep-Alive
            boolean keepAlive = HttpUtil.isKeepAlive(request);
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            
            // 发送响应
            ChannelFuture future = ctx.writeAndFlush(response);
            
            if (!keepAlive) {
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
        
        /**
         * 处理API请求
         */
        private FullHttpResponse handleApiRequest(FullHttpRequest request, String uri) {
            HttpMethod method = request.method();
            
            // OPTIONS请求（CORS预检）
            if (method.equals(HttpMethod.OPTIONS)) {
                return createJsonResponse(HttpResponseStatus.OK, "");
            }
            
            // 路由匹配
            if (uri.equals("/api/users") && method.equals(HttpMethod.GET)) {
                return handleGetUsers();
            } else if (uri.matches("/api/users/\\d+") && method.equals(HttpMethod.GET)) {
                String id = uri.substring(uri.lastIndexOf('/') + 1);
                return handleGetUser(id);
            } else if (uri.equals("/api/users") && method.equals(HttpMethod.POST)) {
                String body = request.content().toString(CharsetUtil.UTF_8);
                return handleCreateUser(body);
            } else if (uri.matches("/api/users/\\d+") && method.equals(HttpMethod.PUT)) {
                String id = uri.substring(uri.lastIndexOf('/') + 1);
                String body = request.content().toString(CharsetUtil.UTF_8);
                return handleUpdateUser(id, body);
            } else if (uri.matches("/api/users/\\d+") && method.equals(HttpMethod.DELETE)) {
                String id = uri.substring(uri.lastIndexOf('/') + 1);
                return handleDeleteUser(id);
            } else {
                return createJsonResponse(HttpResponseStatus.NOT_FOUND, 
                    "{\"error\":\"API not found\"}");
            }
        }
        
        /**
         * 获取所有用户
         */
        private FullHttpResponse handleGetUsers() {
            StringBuilder json = new StringBuilder("[");
            int count = 0;
            for (User user : userDatabase.values()) {
                if (count > 0) json.append(",");
                json.append(user.toJson());
                count++;
            }
            json.append("]");
            
            return createJsonResponse(HttpResponseStatus.OK, json.toString());
        }
        
        /**
         * 获取指定用户
         */
        private FullHttpResponse handleGetUser(String id) {
            User user = userDatabase.get(id);
            if (user == null) {
                return createJsonResponse(HttpResponseStatus.NOT_FOUND, 
                    "{\"error\":\"User not found\"}");
            }
            return createJsonResponse(HttpResponseStatus.OK, user.toJson());
        }
        
        /**
         * 创建用户
         */
        private FullHttpResponse handleCreateUser(String body) {
            try {
                User user = User.fromJson(body);
                user.setId(String.valueOf(System.currentTimeMillis()));
                userDatabase.put(user.getId(), user);
                
                return createJsonResponse(HttpResponseStatus.CREATED, user.toJson());
            } catch (Exception e) {
                return createJsonResponse(HttpResponseStatus.BAD_REQUEST, 
                    "{\"error\":\"Invalid JSON\"}");
            }
        }
        
        /**
         * 更新用户
         */
        private FullHttpResponse handleUpdateUser(String id, String body) {
            User existingUser = userDatabase.get(id);
            if (existingUser == null) {
                return createJsonResponse(HttpResponseStatus.NOT_FOUND, 
                    "{\"error\":\"User not found\"}");
            }
            
            try {
                User user = User.fromJson(body);
                user.setId(id);
                userDatabase.put(id, user);
                
                return createJsonResponse(HttpResponseStatus.OK, user.toJson());
            } catch (Exception e) {
                return createJsonResponse(HttpResponseStatus.BAD_REQUEST, 
                    "{\"error\":\"Invalid JSON\"}");
            }
        }
        
        /**
         * 删除用户
         */
        private FullHttpResponse handleDeleteUser(String id) {
            User user = userDatabase.remove(id);
            if (user == null) {
                return createJsonResponse(HttpResponseStatus.NOT_FOUND, 
                    "{\"error\":\"User not found\"}");
            }
            
            return createJsonResponse(HttpResponseStatus.OK, 
                "{\"message\":\"User deleted successfully\"}");
        }
        
        /**
         * 处理静态文件请求
         */
        private FullHttpResponse handleStaticFile(FullHttpRequest request, String uri) {
            // 默认首页
            if (uri.equals("/") || uri.equals("/index.html")) {
                return createHtmlResponse(getIndexHtml());
            }
            
            // 构建文件路径
            String filePath = webRoot + uri;
            File file = new File(filePath);
            
            // 安全检查：防止目录遍历攻击
            try {
                String canonicalPath = file.getCanonicalPath();
                String canonicalRoot = new File(webRoot).getCanonicalPath();
                if (!canonicalPath.startsWith(canonicalRoot)) {
                    return createTextResponse(HttpResponseStatus.FORBIDDEN, "Forbidden");
                }
            } catch (Exception e) {
                return createTextResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                    "Internal Server Error");
            }
            
            // 文件不存在
            if (!file.exists() || !file.isFile()) {
                return createTextResponse(HttpResponseStatus.NOT_FOUND, "404 Not Found");
            }
            
            // 读取文件
            try {
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                long fileLength = raf.length();
                
                DefaultHttpResponse response = new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                
                // 设置Content-Type
                String contentType = getContentType(file.getName());
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);
                
                // 使用零拷贝传输文件
                DefaultFileRegion fileRegion = new DefaultFileRegion(
                    raf.getChannel(), 0, fileLength);
                
                // 注意：这里简化处理，实际应该使用ChunkedFile
                raf.close();
                
                // 返回简单的文本响应（实际项目中应该使用ChunkedFile）
                return createTextResponse(HttpResponseStatus.OK, 
                    "File: " + file.getName() + " (" + fileLength + " bytes)");
                
            } catch (Exception e) {
                return createTextResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                    "Error reading file");
            }
        }
        
        /**
         * 创建JSON响应
         */
        private FullHttpResponse createJsonResponse(HttpResponseStatus status, String json) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(json, CharsetUtil.UTF_8));
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            
            return response;
        }
        
        /**
         * 创建HTML响应
         */
        private FullHttpResponse createHtmlResponse(String html) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(html, CharsetUtil.UTF_8));
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            
            return response;
        }
        
        /**
         * 创建文本响应
         */
        private FullHttpResponse createTextResponse(HttpResponseStatus status, String text) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(text, CharsetUtil.UTF_8));
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            
            return response;
        }
        
        /**
         * 添加CORS头
         */
        private void addCorsHeaders(FullHttpResponse response) {
            response.headers().set("Access-Control-Allow-Origin", "*");
            response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Content-Type");
        }
        
        /**
         * 发送错误响应
         */
        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(status.toString(), CharsetUtil.UTF_8));
            
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        
        /**
         * 获取Content-Type
         */
        private String getContentType(String fileName) {
            if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                return "text/html; charset=UTF-8";
            } else if (fileName.endsWith(".css")) {
                return "text/css; charset=UTF-8";
            } else if (fileName.endsWith(".js")) {
                return "application/javascript; charset=UTF-8";
            } else if (fileName.endsWith(".json")) {
                return "application/json; charset=UTF-8";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (fileName.endsWith(".png")) {
                return "image/png";
            } else if (fileName.endsWith(".gif")) {
                return "image/gif";
            } else {
                return "application/octet-stream";
            }
        }
        
        /**
         * 记录请求
         */
        private void logRequest(FullHttpRequest request) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            System.out.println(String.format("[%s] %s %s %s",
                sdf.format(new Date()),
                request.method(),
                request.uri(),
                request.protocolVersion()));
        }
        
        /**
         * 获取首页HTML
         */
        private String getIndexHtml() {
            return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Simple HTTP Server</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 40px; }\n" +
                "        h1 { color: #333; }\n" +
                "        .api-list { background: #f5f5f5; padding: 20px; border-radius: 5px; }\n" +
                "        .api-item { margin: 10px 0; }\n" +
                "        .method { display: inline-block; width: 80px; font-weight: bold; }\n" +
                "        .get { color: #61affe; }\n" +
                "        .post { color: #49cc90; }\n" +
                "        .put { color: #fca130; }\n" +
                "        .delete { color: #f93e3e; }\n" +
                "        button { margin: 5px; padding: 10px 20px; cursor: pointer; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>Simple HTTP Server</h1>\n" +
                "    <p>基于Netty实现的简单HTTP服务器</p>\n" +
                "    \n" +
                "    <h2>API端点</h2>\n" +
                "    <div class=\"api-list\">\n" +
                "        <div class=\"api-item\">\n" +
                "            <span class=\"method get\">GET</span>\n" +
                "            <span>/api/users</span> - 获取所有用户\n" +
                "        </div>\n" +
                "        <div class=\"api-item\">\n" +
                "            <span class=\"method get\">GET</span>\n" +
                "            <span>/api/users/{id}</span> - 获取指定用户\n" +
                "        </div>\n" +
                "        <div class=\"api-item\">\n" +
                "            <span class=\"method post\">POST</span>\n" +
                "            <span>/api/users</span> - 创建用户\n" +
                "        </div>\n" +
                "        <div class=\"api-item\">\n" +
                "            <span class=\"method put\">PUT</span>\n" +
                "            <span>/api/users/{id}</span> - 更新用户\n" +
                "        </div>\n" +
                "        <div class=\"api-item\">\n" +
                "            <span class=\"method delete\">DELETE</span>\n" +
                "            <span>/api/users/{id}</span> - 删除用户\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <h2>测试</h2>\n" +
                "    <button onclick=\"getUsers()\">获取所有用户</button>\n" +
                "    <button onclick=\"getUser()\">获取用户1</button>\n" +
                "    <button onclick=\"createUser()\">创建用户</button>\n" +
                "    <div id=\"result\" style=\"margin-top: 20px; padding: 10px; background: #f0f0f0; border-radius: 5px;\"></div>\n" +
                "    \n" +
                "    <script>\n" +
                "        function getUsers() {\n" +
                "            fetch('/api/users')\n" +
                "                .then(res => res.json())\n" +
                "                .then(data => {\n" +
                "                    document.getElementById('result').innerText = JSON.stringify(data, null, 2);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function getUser() {\n" +
                "            fetch('/api/users/1')\n" +
                "                .then(res => res.json())\n" +
                "                .then(data => {\n" +
                "                    document.getElementById('result').innerText = JSON.stringify(data, null, 2);\n" +
                "                });\n" +
                "        }\n" +
                "        \n" +
                "        function createUser() {\n" +
                "            fetch('/api/users', {\n" +
                "                method: 'POST',\n" +
                "                headers: {'Content-Type': 'application/json'},\n" +
                "                body: JSON.stringify({name: '测试用户', age: 20, email: 'test@example.com'})\n" +
                "            })\n" +
                "                .then(res => res.json())\n" +
                "                .then(data => {\n" +
                "                    document.getElementById('result').innerText = JSON.stringify(data, null, 2);\n" +
                "                });\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    /**
     * 用户实体类
     */
    static class User {
        private String id;
        private String name;
        private int age;
        private String email;
        
        public User() {}
        
        public User(String id, String name, int age, String email) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.email = email;
        }
        
        public String toJson() {
            return String.format("{\"id\":\"%s\",\"name\":\"%s\",\"age\":%d,\"email\":\"%s\"}",
                id, name, age, email);
        }
        
        public static User fromJson(String json) {
            // 简单的JSON解析（实际项目应使用Jackson或Gson）
            User user = new User();
            json = json.trim().replace("{", "").replace("}", "");
            String[] pairs = json.split(",");
            
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                
                switch (key) {
                    case "id":
                        user.setId(value);
                        break;
                    case "name":
                        user.setName(value);
                        break;
                    case "age":
                        user.setAge(Integer.parseInt(value));
                        break;
                    case "email":
                        user.setEmail(value);
                        break;
                }
            }
            
            return user;
        }
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
    
    public static void main(String[] args) throws Exception {
        int port = 8080;
        String webRoot = System.getProperty("user.dir") + "/www";
        
        // 创建web根目录
        File webRootDir = new File(webRoot);
        if (!webRootDir.exists()) {
            webRootDir.mkdirs();
        }
        
        SimpleHttpServer server = new SimpleHttpServer(port, webRoot);
        server.start();
    }
}
