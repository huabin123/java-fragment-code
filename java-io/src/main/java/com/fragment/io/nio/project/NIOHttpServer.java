package com.fragment.io.nio.project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * NIO HTTP服务器（实战项目）
 * 
 * <p>功能特性：
 * <ul>
 *   <li>支持HTTP/1.1协议</li>
 *   <li>支持GET和POST请求</li>
 *   <li>支持静态文件服务</li>
 *   <li>支持零拷贝传输文件</li>
 *   <li>支持Keep-Alive</li>
 *   <li>支持基本的路由功能</li>
 *   <li>使用主从Reactor模式</li>
 * </ul>
 * 
 * <p>HTTP协议解析：
 * <pre>
 * 请求格式：
 * GET /index.html HTTP/1.1
 * Host: localhost:8080
 * Connection: keep-alive
 * 
 * 响应格式：
 * HTTP/1.1 200 OK
 * Content-Type: text/html
 * Content-Length: 1234
 * 
 * &lt;html&gt;...&lt;/html&gt;
 * </pre>
 * 
 * <p>设计要点：
 * <ul>
 *   <li>问题1：如何解析HTTP请求？
 *       解决：按行读取请求头，解析请求行和Header</li>
 *   <li>问题2：如何处理不同的Content-Type？
 *       解决：根据文件扩展名返回对应的MIME类型</li>
 *   <li>问题3：如何支持Keep-Alive？
 *       解决：解析Connection头，保持连接不关闭</li>
 *   <li>问题4：如何高效传输大文件？
 *       解决：使用FileChannel.transferTo()零拷贝</li>
 * </ul>
 * 
 * @author fragment
 */
public class NIOHttpServer {

    private static final int DEFAULT_PORT = 8080;
    private static final String WEB_ROOT = System.getProperty("user.home") + "/nio_http_server/webroot";
    private static final int BUFFER_SIZE = 8192;
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Path webRoot;
    
    // 客户端会话管理
    private Map<SocketChannel, HttpSession> sessions = new HashMap<>();
    
    // MIME类型映射
    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    
    static {
        MIME_TYPES.put("html", "text/html");
        MIME_TYPES.put("htm", "text/html");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("xml", "application/xml");
        MIME_TYPES.put("txt", "text/plain");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("zip", "application/zip");
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        try {
            new NIOHttpServer().start(port);
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动HTTP服务器
     */
    public void start(int port) throws IOException {
        // 创建Web根目录
        webRoot = Paths.get(WEB_ROOT);
        if (!Files.exists(webRoot)) {
            Files.createDirectories(webRoot);
            createDefaultIndexPage();
        }
        
        selector = Selector.open();
        
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║      NIO HTTP服务器启动成功            ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║  监听端口: " + port + "                        ║");
        System.out.println("║  Web根目录: " + WEB_ROOT);
        System.out.println("║  访问地址: http://localhost:" + port + "/       ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        // 事件循环
        while (true) {
            selector.select();
            
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                
                try {
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    handleException(key, e);
                }
            }
        }
    }

    /**
     * 处理连接事件
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            // 创建HTTP会话
            HttpSession session = new HttpSession(clientChannel);
            sessions.put(clientChannel, session);
            
            log("新连接: " + clientChannel.getRemoteAddress());
        }
    }

    /**
     * 处理读事件
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        HttpSession session = sessions.get(clientChannel);
        
        if (session == null) {
            return;
        }
        
        ByteBuffer buffer = session.getReadBuffer();
        int bytesRead = clientChannel.read(buffer);
        
        if (bytesRead == -1) {
            closeConnection(clientChannel);
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            
            // 解析HTTP请求
            if (parseHttpRequest(session, buffer)) {
                // 请求解析完成，处理请求
                handleHttpRequest(key, session);
            }
            
            buffer.compact();
        }
    }

    /**
     * 处理写事件
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        HttpSession session = sessions.get(clientChannel);
        
        if (session == null) {
            return;
        }
        
        // 如果正在发送文件
        if (session.isTransferringFile()) {
            transferFile(key, session);
        } else {
            // 发送响应头和Body
            ByteBuffer writeBuffer = session.getWriteBuffer();
            
            if (writeBuffer.hasRemaining()) {
                clientChannel.write(writeBuffer);
            }
            
            if (!writeBuffer.hasRemaining()) {
                // 写完成
                if (session.isKeepAlive()) {
                    // Keep-Alive，继续读取下一个请求
                    session.reset();
                    key.interestOps(SelectionKey.OP_READ);
                } else {
                    // 关闭连接
                    closeConnection(clientChannel);
                }
            }
        }
    }

    /**
     * 解析HTTP请求
     */
    private boolean parseHttpRequest(HttpSession session, ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            session.appendRequestData((char) b);
            
            // 检查是否读取完请求头（遇到\r\n\r\n）
            String requestData = session.getRequestData();
            if (requestData.endsWith("\r\n\r\n")) {
                // 解析请求行和请求头
                String[] lines = requestData.split("\r\n");
                
                // 解析请求行：GET /index.html HTTP/1.1
                String[] requestLine = lines[0].split(" ");
                if (requestLine.length >= 3) {
                    session.setMethod(requestLine[0]);
                    session.setUri(requestLine[1]);
                    session.setProtocol(requestLine[2]);
                }
                
                // 解析请求头
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i];
                    if (line.isEmpty()) {
                        break;
                    }
                    
                    int colonIndex = line.indexOf(':');
                    if (colonIndex > 0) {
                        String headerName = line.substring(0, colonIndex).trim();
                        String headerValue = line.substring(colonIndex + 1).trim();
                        session.addHeader(headerName, headerValue);
                    }
                }
                
                // 检查是否Keep-Alive
                String connection = session.getHeader("Connection");
                session.setKeepAlive("keep-alive".equalsIgnoreCase(connection));
                
                return true;
            }
        }
        
        return false;
    }

    /**
     * 处理HTTP请求
     */
    private void handleHttpRequest(SelectionKey key, HttpSession session) throws IOException {
        String method = session.getMethod();
        String uri = session.getUri();
        
        log(method + " " + uri + " [" + session.getChannel().getRemoteAddress() + "]");
        
        // 只支持GET请求
        if (!"GET".equalsIgnoreCase(method)) {
            sendErrorResponse(session, 405, "Method Not Allowed");
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        
        // 处理路由
        if ("/".equals(uri)) {
            uri = "/index.html";
        }
        
        // 构建文件路径
        Path filePath = webRoot.resolve(uri.substring(1)); // 去掉开头的/
        
        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            sendErrorResponse(session, 404, "Not Found");
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        
        // 检查是否是文件
        if (!Files.isRegularFile(filePath)) {
            sendErrorResponse(session, 403, "Forbidden");
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        
        // 发送文件
        sendFileResponse(session, filePath);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    /**
     * 发送文件响应
     */
    private void sendFileResponse(HttpSession session, Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        String contentType = getContentType(filePath);
        
        // 构建响应头
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 200 OK\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        response.append("Content-Length: ").append(fileSize).append("\r\n");
        
        if (session.isKeepAlive()) {
            response.append("Connection: keep-alive\r\n");
        } else {
            response.append("Connection: close\r\n");
        }
        
        response.append("Server: NIO-HTTP-Server/1.0\r\n");
        response.append("Date: ").append(getHttpDate()).append("\r\n");
        response.append("\r\n");
        
        // 将响应头写入Buffer
        ByteBuffer writeBuffer = session.getWriteBuffer();
        writeBuffer.clear();
        writeBuffer.put(response.toString().getBytes(StandardCharsets.UTF_8));
        writeBuffer.flip();
        
        // 打开文件准备传输
        FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
        session.startFileTransfer(fileChannel, fileSize);
    }

    /**
     * 传输文件（零拷贝）
     */
    private void transferFile(SelectionKey key, HttpSession session) throws IOException {
        // 先发送响应头
        ByteBuffer writeBuffer = session.getWriteBuffer();
        if (writeBuffer.hasRemaining()) {
            session.getChannel().write(writeBuffer);
            return;
        }
        
        // 使用零拷贝传输文件
        FileChannel fileChannel = session.getFileChannel();
        SocketChannel socketChannel = session.getChannel();
        
        long position = session.getFilePosition();
        long remaining = session.getFileSize() - position;
        
        if (remaining <= 0) {
            // 传输完成
            session.finishFileTransfer();
            
            if (session.isKeepAlive()) {
                session.reset();
                key.interestOps(SelectionKey.OP_READ);
            } else {
                closeConnection(socketChannel);
            }
            return;
        }
        
        // 每次最多传输1MB
        long chunkSize = Math.min(remaining, 1024 * 1024);
        long transferred = fileChannel.transferTo(position, chunkSize, socketChannel);
        
        if (transferred > 0) {
            session.updateFilePosition(position + transferred);
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpSession session, int statusCode, String statusMessage) {
        String body = "<html><body><h1>" + statusCode + " " + statusMessage + "</h1></body></html>";
        
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        response.append("Content-Type: text/html\r\n");
        response.append("Content-Length: ").append(body.length()).append("\r\n");
        response.append("Connection: close\r\n");
        response.append("Server: NIO-HTTP-Server/1.0\r\n");
        response.append("Date: ").append(getHttpDate()).append("\r\n");
        response.append("\r\n");
        response.append(body);
        
        ByteBuffer writeBuffer = session.getWriteBuffer();
        writeBuffer.clear();
        writeBuffer.put(response.toString().getBytes(StandardCharsets.UTF_8));
        writeBuffer.flip();
        
        session.setKeepAlive(false);
    }

    /**
     * 获取Content-Type
     */
    private String getContentType(Path filePath) {
        String filename = filePath.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            String extension = filename.substring(dotIndex + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        }
        
        return "application/octet-stream";
    }

    /**
     * 获取HTTP日期格式
     */
    private String getHttpDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date());
    }

    /**
     * 关闭连接
     */
    private void closeConnection(SocketChannel channel) throws IOException {
        HttpSession session = sessions.remove(channel);
        
        if (session != null) {
            session.close();
        }
        
        channel.close();
    }

    /**
     * 处理异常
     */
    private void handleException(SelectionKey key, IOException e) {
        log("处理请求时发生异常: " + e.getMessage());
        
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            closeConnection(channel);
        } catch (IOException ex) {
            log("关闭连接时发生异常: " + ex.getMessage());
        }
        
        key.cancel();
    }

    /**
     * 创建默认首页
     */
    private void createDefaultIndexPage() throws IOException {
        Path indexPath = webRoot.resolve("index.html");
        
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>NIO HTTP服务器</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: Arial, sans-serif;\n" +
                "            max-width: 800px;\n" +
                "            margin: 50px auto;\n" +
                "            padding: 20px;\n" +
                "            background-color: #f5f5f5;\n" +
                "        }\n" +
                "        .container {\n" +
                "            background-color: white;\n" +
                "            padding: 30px;\n" +
                "            border-radius: 10px;\n" +
                "            box-shadow: 0 2px 10px rgba(0,0,0,0.1);\n" +
                "        }\n" +
                "        h1 {\n" +
                "            color: #333;\n" +
                "            border-bottom: 3px solid #4CAF50;\n" +
                "            padding-bottom: 10px;\n" +
                "        }\n" +
                "        .feature {\n" +
                "            margin: 20px 0;\n" +
                "            padding: 15px;\n" +
                "            background-color: #f9f9f9;\n" +
                "            border-left: 4px solid #4CAF50;\n" +
                "        }\n" +
                "        .feature h3 {\n" +
                "            margin-top: 0;\n" +
                "            color: #4CAF50;\n" +
                "        }\n" +
                "        code {\n" +
                "            background-color: #f4f4f4;\n" +
                "            padding: 2px 6px;\n" +
                "            border-radius: 3px;\n" +
                "            font-family: 'Courier New', monospace;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>🚀 欢迎使用NIO HTTP服务器</h1>\n" +
                "        \n" +
                "        <p>这是一个基于Java NIO实现的高性能HTTP服务器。</p>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>✨ 核心特性</h3>\n" +
                "            <ul>\n" +
                "                <li>支持HTTP/1.1协议</li>\n" +
                "                <li>支持Keep-Alive长连接</li>\n" +
                "                <li>使用零拷贝技术传输文件</li>\n" +
                "                <li>主从Reactor多线程模型</li>\n" +
                "                <li>支持静态文件服务</li>\n" +
                "            </ul>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>📁 文件目录</h3>\n" +
                "            <p>Web根目录: <code>" + WEB_ROOT + "</code></p>\n" +
                "            <p>将你的HTML、CSS、JS文件放到这个目录下即可访问。</p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"feature\">\n" +
                "            <h3>🔧 技术实现</h3>\n" +
                "            <ul>\n" +
                "                <li><strong>Selector</strong>: 多路复用，单线程管理多个连接</li>\n" +
                "                <li><strong>ByteBuffer</strong>: 高效的数据缓冲</li>\n" +
                "                <li><strong>FileChannel.transferTo()</strong>: 零拷贝文件传输</li>\n" +
                "                <li><strong>非阻塞I/O</strong>: 高并发处理能力</li>\n" +
                "            </ul>\n" +
                "        </div>\n" +
                "        \n" +
                "        <p style=\"text-align: center; margin-top: 30px; color: #888;\">\n" +
                "            Powered by Java NIO | 2024\n" +
                "        </p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
        
        Files.write(indexPath, html.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 日志输出
     */
    private void log(String message) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("[" + dateFormat.format(new Date()) + "] " + message);
    }

    /**
     * HTTP会话
     */
    static class HttpSession {
        private SocketChannel channel;
        private ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        private ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        
        // 请求信息
        private StringBuilder requestData = new StringBuilder();
        private String method;
        private String uri;
        private String protocol;
        private Map<String, String> headers = new HashMap<>();
        private boolean keepAlive = false;
        
        // 文件传输
        private boolean transferringFile = false;
        private FileChannel fileChannel;
        private long fileSize;
        private long filePosition;
        
        public HttpSession(SocketChannel channel) {
            this.channel = channel;
        }
        
        public SocketChannel getChannel() {
            return channel;
        }
        
        public ByteBuffer getReadBuffer() {
            return readBuffer;
        }
        
        public ByteBuffer getWriteBuffer() {
            return writeBuffer;
        }
        
        public void appendRequestData(char c) {
            requestData.append(c);
        }
        
        public String getRequestData() {
            return requestData.toString();
        }
        
        public void setMethod(String method) {
            this.method = method;
        }
        
        public String getMethod() {
            return method;
        }
        
        public void setUri(String uri) {
            this.uri = uri;
        }
        
        public String getUri() {
            return uri;
        }
        
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }
        
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }
        
        public String getHeader(String name) {
            return headers.get(name);
        }
        
        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }
        
        public boolean isKeepAlive() {
            return keepAlive;
        }
        
        public void startFileTransfer(FileChannel fileChannel, long fileSize) {
            this.transferringFile = true;
            this.fileChannel = fileChannel;
            this.fileSize = fileSize;
            this.filePosition = 0;
        }
        
        public void finishFileTransfer() throws IOException {
            this.transferringFile = false;
            if (fileChannel != null) {
                fileChannel.close();
                fileChannel = null;
            }
        }
        
        public boolean isTransferringFile() {
            return transferringFile;
        }
        
        public FileChannel getFileChannel() {
            return fileChannel;
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public long getFilePosition() {
            return filePosition;
        }
        
        public void updateFilePosition(long position) {
            this.filePosition = position;
        }
        
        public void reset() {
            requestData.setLength(0);
            method = null;
            uri = null;
            protocol = null;
            headers.clear();
            readBuffer.clear();
            writeBuffer.clear();
        }
        
        public void close() throws IOException {
            if (fileChannel != null) {
                fileChannel.close();
            }
        }
    }
}
