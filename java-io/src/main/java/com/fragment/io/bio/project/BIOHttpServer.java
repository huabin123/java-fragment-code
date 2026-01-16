package com.fragment.io.bio.project;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BIO简易HTTP服务器
 * 
 * <p>功能特性：
 * <ul>
 *   <li>支持GET/POST请求</li>
 *   <li>静态文件服务</li>
 *   <li>请求参数解析</li>
 *   <li>响应头设置</li>
 *   <li>简单的路由功能</li>
 *   <li>访问日志</li>
 * </ul>
 * 
 * @author fragment
 */
public class BIOHttpServer {

    private static final int PORT = 9002;
    private static final String WEB_ROOT = "./webroot/";
    private static final AtomicLong requestCount = new AtomicLong(0);

    public static void main(String[] args) {
        System.out.println("========== BIO HTTP服务器 ==========");
        System.out.println("端口: " + PORT);
        System.out.println("根目录: " + WEB_ROOT);
        System.out.println("===================================\n");

        // 创建Web根目录
        createWebRoot();

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(100);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("HTTP服务器启动成功");
            System.out.println("访问: http://localhost:" + PORT + "\n");

            while (true) {
                Socket socket = serverSocket.accept();
                executor.execute(new HttpHandler(socket));
            }

        } catch (IOException e) {
            System.err.println("服务器异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * HTTP请求处理器
     */
    static class HttpHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private OutputStream outputStream;

        public HttpHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(10000);  // 10秒超时

                reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                outputStream = socket.getOutputStream();
                writer = new PrintWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

                // 解析HTTP请求
                HttpRequest request = parseRequest();
                if (request == null) {
                    return;
                }

                // 记录日志
                logRequest(request);

                // 处理请求
                handleRequest(request);

            } catch (IOException e) {
                System.err.println("处理请求异常: " + e.getMessage());
            } finally {
                closeQuietly(reader);
                closeQuietly(writer);
                closeQuietly(socket);
            }
        }

        /**
         * 解析HTTP请求
         */
        private HttpRequest parseRequest() throws IOException {
            // 读取请求行
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return null;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                sendError(400, "Bad Request");
                return null;
            }

            String method = parts[0];
            String uri = parts[1];
            String version = parts[2];

            // 读取请求头
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    headers.put(key.toLowerCase(), value);
                }
            }

            // 读取请求体（POST）
            String body = null;
            if ("POST".equalsIgnoreCase(method)) {
                String contentLength = headers.get("content-length");
                if (contentLength != null) {
                    int length = Integer.parseInt(contentLength);
                    char[] buffer = new char[length];
                    reader.read(buffer, 0, length);
                    body = new String(buffer);
                }
            }

            return new HttpRequest(method, uri, version, headers, body);
        }

        /**
         * 处理HTTP请求
         */
        private void handleRequest(HttpRequest request) throws IOException {
            String uri = request.getUri();

            // 路由处理
            if ("/".equals(uri)) {
                serveIndexPage();
            } else if ("/api/hello".equals(uri)) {
                serveApiHello(request);
            } else if ("/api/echo".equals(uri)) {
                serveApiEcho(request);
            } else if ("/api/stats".equals(uri)) {
                serveApiStats();
            } else {
                serveStaticFile(uri);
            }
        }

        /**
         * 服务首页
         */
        private void serveIndexPage() {
            String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <title>BIO HTTP服务器</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 50px; }\n" +
                "        h1 { color: #333; }\n" +
                "        .info { background: #f0f0f0; padding: 20px; border-radius: 5px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>欢迎使用BIO HTTP服务器</h1>\n" +
                "    <div class='info'>\n" +
                "        <p>这是一个基于BIO实现的简易HTTP服务器</p>\n" +
                "        <p>当前请求数: " + requestCount.get() + "</p>\n" +
                "        <h3>API列表:</h3>\n" +
                "        <ul>\n" +
                "            <li><a href='/api/hello'>/api/hello</a> - Hello API</li>\n" +
                "            <li><a href='/api/stats'>/api/stats</a> - 服务器统计</li>\n" +
                "            <li>/api/echo - Echo API (POST)</li>\n" +
                "        </ul>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

            sendResponse(200, "OK", "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Hello API
         */
        private void serveApiHello(HttpRequest request) {
            String name = request.getParameter("name");
            if (name == null) {
                name = "World";
            }

            String json = "{\"message\": \"Hello, " + name + "!\", \"timestamp\": " + 
                         System.currentTimeMillis() + "}";

            sendResponse(200, "OK", "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Echo API
         */
        private void serveApiEcho(HttpRequest request) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                sendError(405, "Method Not Allowed");
                return;
            }

            String body = request.getBody();
            if (body == null) {
                body = "";
            }

            String json = "{\"echo\": \"" + body + "\", \"length\": " + body.length() + "}";
            sendResponse(200, "OK", "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 统计API
         */
        private void serveApiStats() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

            String json = "{\n" +
                "  \"requestCount\": " + requestCount.get() + ",\n" +
                "  \"activeThreads\": " + Thread.activeCount() + ",\n" +
                "  \"usedMemoryMB\": " + usedMemory + ",\n" +
                "  \"timestamp\": " + System.currentTimeMillis() + "\n" +
                "}";

            sendResponse(200, "OK", "application/json", json.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 服务静态文件
         */
        private void serveStaticFile(String uri) throws IOException {
            Path filePath = Paths.get(WEB_ROOT + uri);

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                sendError(404, "Not Found");
                return;
            }

            byte[] content = Files.readAllBytes(filePath);
            String contentType = getContentType(uri);

            sendResponse(200, "OK", contentType, content);
        }

        /**
         * 发送HTTP响应
         */
        private void sendResponse(int statusCode, String statusMessage, 
                                 String contentType, byte[] content) {
            try {
                // 状态行
                writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);

                // 响应头
                writer.println("Content-Type: " + contentType);
                writer.println("Content-Length: " + content.length);
                writer.println("Connection: close");
                writer.println("Server: BIO-HTTP-Server/1.0");
                writer.println();
                writer.flush();

                // 响应体
                outputStream.write(content);
                outputStream.flush();

            } catch (IOException e) {
                System.err.println("发送响应异常: " + e.getMessage());
            }
        }

        /**
         * 发送错误响应
         */
        private void sendError(int statusCode, String message) {
            String html = "<html><body><h1>" + statusCode + " " + message + "</h1></body></html>";
            sendResponse(statusCode, message, "text/html", html.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 获取Content-Type
         */
        private String getContentType(String uri) {
            if (uri.endsWith(".html") || uri.endsWith(".htm")) {
                return "text/html; charset=UTF-8";
            } else if (uri.endsWith(".css")) {
                return "text/css";
            } else if (uri.endsWith(".js")) {
                return "application/javascript";
            } else if (uri.endsWith(".json")) {
                return "application/json";
            } else if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (uri.endsWith(".png")) {
                return "image/png";
            } else if (uri.endsWith(".gif")) {
                return "image/gif";
            } else {
                return "application/octet-stream";
            }
        }

        /**
         * 记录请求日志
         */
        private void logRequest(HttpRequest request) {
            long count = requestCount.incrementAndGet();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            
            System.out.printf("[%s] #%d %s %s %s\n",
                timestamp, count, request.getMethod(), request.getUri(),
                socket.getRemoteSocketAddress());
        }

        private void closeQuietly(Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * HTTP请求对象
     */
    static class HttpRequest {
        private String method;
        private String uri;
        private String version;
        private Map<String, String> headers;
        private String body;
        private Map<String, String> parameters;

        public HttpRequest(String method, String uri, String version,
                          Map<String, String> headers, String body) {
            this.method = method;
            this.version = version;
            this.headers = headers;
            this.body = body;

            // 解析URI和参数
            int questionIndex = uri.indexOf('?');
            if (questionIndex != -1) {
                this.uri = uri.substring(0, questionIndex);
                parseParameters(uri.substring(questionIndex + 1));
            } else {
                this.uri = uri;
                this.parameters = new HashMap<>();
            }
        }

        private void parseParameters(String queryString) {
            parameters = new HashMap<>();
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                int equalsIndex = pair.indexOf('=');
                if (equalsIndex > 0) {
                    String key = pair.substring(0, equalsIndex);
                    String value = pair.substring(equalsIndex + 1);
                    parameters.put(key, value);
                }
            }
        }

        public String getMethod() { return method; }
        public String getUri() { return uri; }
        public String getVersion() { return version; }
        public Map<String, String> getHeaders() { return headers; }
        public String getBody() { return body; }
        public String getParameter(String name) { return parameters.get(name); }
    }

    /**
     * 创建Web根目录和示例文件
     */
    private static void createWebRoot() {
        File dir = new File(WEB_ROOT);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 创建示例HTML文件
        try {
            String exampleHtml = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><title>Example Page</title></head>\n" +
                "<body><h1>This is an example page</h1></body>\n" +
                "</html>";
            
            Files.write(Paths.get(WEB_ROOT + "example.html"), 
                       exampleHtml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 忽略
        }
    }
}
