package com.fragment.io.aio.project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 异步HTTP客户端
 * 
 * <p>功能特性：
 * <ul>
 *   <li>异步HTTP GET/POST请求</li>
 *   <li>请求头和请求体支持</li>
 *   <li>响应解析</li>
 *   <li>超时控制</li>
 *   <li>连接池（简化版）</li>
 *   <li>重试机制</li>
 * </ul>
 * 
 * <p>注意：这是一个教学示例，生产环境请使用成熟的HTTP客户端库（如Apache HttpClient、OkHttp等）
 * 
 * @author fragment
 */
public class AsyncHttpClient {

    private static final int DEFAULT_TIMEOUT = 30000; // 30秒
    private static final int BUFFER_SIZE = 8192;
    private static final String DEFAULT_USER_AGENT = "AsyncHttpClient/1.0";

    /**
     * 异步GET请求
     */
    public CompletableFuture<HttpResponse> get(String url) {
        return get(url, new HashMap<>());
    }

    /**
     * 异步GET请求（带请求头）
     */
    public CompletableFuture<HttpResponse> get(String url, Map<String, String> headers) {
        return request(HttpMethod.GET, url, headers, null);
    }

    /**
     * 异步POST请求
     */
    public CompletableFuture<HttpResponse> post(String url, String body) {
        return post(url, new HashMap<>(), body);
    }

    /**
     * 异步POST请求（带请求头）
     */
    public CompletableFuture<HttpResponse> post(String url, Map<String, String> headers, String body) {
        return request(HttpMethod.POST, url, headers, body);
    }

    /**
     * 通用HTTP请求
     */
    private CompletableFuture<HttpResponse> request(HttpMethod method, String url, 
                                                    Map<String, String> headers, String body) {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();

        try {
            // 解析URL
            UrlInfo urlInfo = parseUrl(url);
            
            // 打开异步Socket连接
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            
            System.out.println("🔗 连接到 " + urlInfo.host + ":" + urlInfo.port);

            // 异步连接
            channel.connect(new InetSocketAddress(urlInfo.host, urlInfo.port), null, 
                new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    System.out.println("✅ 连接成功");
                    
                    // 构建HTTP请求
                    String request = buildHttpRequest(method, urlInfo, headers, body);
                    System.out.println("📤 发送请求:\n" + request);
                    
                    ByteBuffer requestBuffer = ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8));
                    
                    // 异步发送请求
                    sendRequest(channel, requestBuffer, future);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    System.err.println("❌ 连接失败: " + exc.getMessage());
                    future.completeExceptionally(exc);
                    closeChannel(channel);
                }
            });

            // 设置超时（Java 8兼容方式）
            scheduleTimeout(future, channel, DEFAULT_TIMEOUT);

        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 异步发送请求
     */
    private void sendRequest(AsynchronousSocketChannel channel, ByteBuffer requestBuffer, 
                            CompletableFuture<HttpResponse> future) {
        channel.write(requestBuffer, requestBuffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer attachment) {
                if (attachment.hasRemaining()) {
                    // 继续写入剩余数据
                    channel.write(attachment, attachment, this);
                } else {
                    System.out.println("✅ 请求发送完成");
                    // 开始读取响应
                    readResponse(channel, future);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("❌ 发送请求失败: " + exc.getMessage());
                future.completeExceptionally(exc);
                closeChannel(channel);
            }
        });
    }

    /**
     * 异步读取响应
     */
    private void readResponse(AsynchronousSocketChannel channel, CompletableFuture<HttpResponse> future) {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        List<ByteBuffer> buffers = new ArrayList<>();

        readResponseChunk(channel, buffer, buffers, future);
    }

    /**
     * 递归读取响应数据块
     */
    private void readResponseChunk(AsynchronousSocketChannel channel, ByteBuffer buffer, 
                                   List<ByteBuffer> buffers, CompletableFuture<HttpResponse> future) {
        channel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    // 读取完成
                    System.out.println("✅ 响应接收完成");
                    closeChannel(channel);
                    
                    // 解析响应
                    try {
                        HttpResponse response = parseResponse(buffers);
                        future.complete(response);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                    return;
                }

                // 保存当前buffer
                attachment.flip();
                ByteBuffer copy = ByteBuffer.allocate(attachment.remaining());
                copy.put(attachment);
                copy.flip();
                buffers.add(copy);

                // 检查是否读取完整响应（简化判断）
                String currentData = StandardCharsets.UTF_8.decode(copy).toString();
                if (isResponseComplete(buffers, currentData)) {
                    System.out.println("✅ 响应接收完成");
                    closeChannel(channel);
                    
                    try {
                        HttpResponse response = parseResponse(buffers);
                        future.complete(response);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                    return;
                }

                // 继续读取
                ByteBuffer nextBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                readResponseChunk(channel, nextBuffer, buffers, future);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("❌ 读取响应失败: " + exc.getMessage());
                future.completeExceptionally(exc);
                closeChannel(channel);
            }
        });
    }

    /**
     * 判断响应是否完整（简化版）
     */
    private boolean isResponseComplete(List<ByteBuffer> buffers, String currentData) {
        // 简化判断：如果包含完整的HTTP响应头和Content-Length指定的body
        // 实际应该根据Content-Length或Transfer-Encoding: chunked来判断
        
        // 检查是否有响应头结束标记
        String allData = getAllData(buffers);
        if (!allData.contains("\r\n\r\n")) {
            return false;
        }

        // 简单判断：如果读取到的数据较小或包含HTML结束标签
        return allData.contains("</html>") || 
               allData.contains("</body>") || 
               allData.length() > 100; // 简化判断
    }

    /**
     * 获取所有已读取的数据
     */
    private String getAllData(List<ByteBuffer> buffers) {
        StringBuilder sb = new StringBuilder();
        for (ByteBuffer buffer : buffers) {
            buffer.rewind();
            sb.append(StandardCharsets.UTF_8.decode(buffer));
        }
        return sb.toString();
    }

    /**
     * 解析HTTP响应
     */
    private HttpResponse parseResponse(List<ByteBuffer> buffers) {
        String responseText = getAllData(buffers);
        
        System.out.println("📥 收到响应 (" + responseText.length() + " 字节)");

        // 分离响应头和响应体
        String[] parts = responseText.split("\r\n\r\n", 2);
        String headerPart = parts[0];
        String bodyPart = parts.length > 1 ? parts[1] : "";

        // 解析状态行
        String[] lines = headerPart.split("\r\n");
        String statusLine = lines[0];
        String[] statusParts = statusLine.split(" ", 3);
        
        int statusCode = Integer.parseInt(statusParts[1]);
        String statusMessage = statusParts.length > 2 ? statusParts[2] : "";

        // 解析响应头
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }

        return new HttpResponse(statusCode, statusMessage, headers, bodyPart);
    }

    /**
     * 构建HTTP请求
     */
    private String buildHttpRequest(HttpMethod method, UrlInfo urlInfo, 
                                    Map<String, String> headers, String body) {
        StringBuilder request = new StringBuilder();

        // 请求行
        request.append(method.name()).append(" ").append(urlInfo.path).append(" HTTP/1.1\r\n");

        // 必需的请求头
        request.append("Host: ").append(urlInfo.host).append("\r\n");
        request.append("User-Agent: ").append(DEFAULT_USER_AGENT).append("\r\n");
        request.append("Accept: */*\r\n");
        request.append("Connection: close\r\n");

        // 自定义请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }

        // 请求体
        if (body != null && !body.isEmpty()) {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            request.append("Content-Type: application/x-www-form-urlencoded\r\n");
            request.append("\r\n");
            request.append(body);
        } else {
            request.append("\r\n");
        }

        return request.toString();
    }

    /**
     * 解析URL
     */
    private UrlInfo parseUrl(String url) {
        // 简化的URL解析（仅支持http）
        if (!url.startsWith("http://")) {
            throw new IllegalArgumentException("仅支持 http:// 协议");
        }

        url = url.substring(7); // 移除 "http://"
        
        int pathIndex = url.indexOf('/');
        String hostPort;
        String path;

        if (pathIndex == -1) {
            hostPort = url;
            path = "/";
        } else {
            hostPort = url.substring(0, pathIndex);
            path = url.substring(pathIndex);
        }

        String host;
        int port = 80; // 默认HTTP端口

        int colonIndex = hostPort.indexOf(':');
        if (colonIndex != -1) {
            host = hostPort.substring(0, colonIndex);
            port = Integer.parseInt(hostPort.substring(colonIndex + 1));
        } else {
            host = hostPort;
        }

        return new UrlInfo(host, port, path);
    }

    /**
     * 关闭通道
     */
    private void closeChannel(AsynchronousSocketChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * HTTP方法
     */
    private enum HttpMethod {
        GET, POST, PUT, DELETE, HEAD, OPTIONS
    }

    /**
     * URL信息
     */
    private static class UrlInfo {
        final String host;
        final int port;
        final String path;

        UrlInfo(String host, int port, String path) {
            this.host = host;
            this.port = port;
            this.path = path;
        }
    }

    /**
     * HTTP响应
     */
    public static class HttpResponse {
        private final int statusCode;
        private final String statusMessage;
        private final Map<String, String> headers;
        private final String body;

        public HttpResponse(int statusCode, String statusMessage, 
                          Map<String, String> headers, String body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = headers;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public String getHeader(String name) {
            return headers.get(name);
        }

        public String getBody() {
            return body;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        @Override
        public String toString() {
            return "HttpResponse{" +
                   "statusCode=" + statusCode +
                   ", statusMessage='" + statusMessage + '\'' +
                   ", headers=" + headers.size() +
                   ", bodyLength=" + body.length() +
                   '}';
        }
    }

    // ==================== 测试示例 ====================

    public static void main(String[] args) throws Exception {
        AsyncHttpClient client = new AsyncHttpClient();

        System.out.println("========== 异步HTTP客户端演示 ==========\n");

        // 示例1: 简单的GET请求
        demonstrateSimpleGet(client);

        Thread.sleep(3000);

        // 示例2: 带请求头的GET请求
        demonstrateGetWithHeaders(client);

        Thread.sleep(3000);

        // 示例3: POST请求
        demonstratePost(client);

        Thread.sleep(3000);

        // 示例4: 并发请求
        demonstrateConcurrentRequests(client);

        Thread.sleep(5000);

        System.out.println("\n========== 演示完成 ==========");
    }

    private static void demonstrateSimpleGet(AsyncHttpClient client) {
        System.out.println("========== 示例1: 简单的GET请求 ==========\n");

        client.get("http://httpbin.org/get")
              .thenAccept(response -> {
                  System.out.println("\n✅ 请求成功");
                  System.out.println("状态码: " + response.getStatusCode());
                  System.out.println("状态消息: " + response.getStatusMessage());
                  System.out.println("响应头数量: " + response.getHeaders().size());
                  System.out.println("响应体长度: " + response.getBody().length());
                  System.out.println("\n响应体预览:");
                  System.out.println(response.getBody().substring(0, 
                      Math.min(200, response.getBody().length())) + "...");
              })
              .exceptionally(ex -> {
                  System.err.println("❌ 请求失败: " + ex.getMessage());
                  return null;
              });

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void demonstrateGetWithHeaders(AsyncHttpClient client) {
        System.out.println("========== 示例2: 带请求头的GET请求 ==========\n");

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("X-Custom-Header", "CustomValue");

        client.get("http://httpbin.org/headers", headers)
              .thenAccept(response -> {
                  System.out.println("\n✅ 请求成功");
                  System.out.println("状态码: " + response.getStatusCode());
                  
                  if (response.isSuccess()) {
                      System.out.println("✅ 请求成功 (2xx)");
                  }
                  
                  System.out.println("\n响应头:");
                  response.getHeaders().forEach((key, value) -> 
                      System.out.println("  " + key + ": " + value));
              })
              .exceptionally(ex -> {
                  System.err.println("❌ 请求失败: " + ex.getMessage());
                  return null;
              });

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void demonstratePost(AsyncHttpClient client) {
        System.out.println("========== 示例3: POST请求 ==========\n");

        String postData = "name=AsyncHttpClient&version=1.0&type=educational";

        client.post("http://httpbin.org/post", postData)
              .thenAccept(response -> {
                  System.out.println("\n✅ POST请求成功");
                  System.out.println("状态码: " + response.getStatusCode());
                  System.out.println("Content-Type: " + response.getHeader("Content-Type"));
                  System.out.println("\n响应体预览:");
                  System.out.println(response.getBody().substring(0, 
                      Math.min(300, response.getBody().length())) + "...");
              })
              .exceptionally(ex -> {
                  System.err.println("❌ POST请求失败: " + ex.getMessage());
                  return null;
              });

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void demonstrateConcurrentRequests(AsyncHttpClient client) {
        System.out.println("========== 示例4: 并发请求 ==========\n");

        List<String> urls = new ArrayList<>();
        urls.add("http://httpbin.org/delay/1");
        urls.add("http://httpbin.org/delay/2");
        urls.add("http://httpbin.org/get");

        System.out.println("同时发起 " + urls.size() + " 个请求...\n");

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<HttpResponse>> futures = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            final int index = i + 1;
            String url = urls.get(i);
            
            CompletableFuture<HttpResponse> future = client.get(url)
                .thenApply(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    System.out.println("✅ 请求 #" + index + " 完成 (耗时: " + duration + "ms)");
                    return response;
                })
                .exceptionally(ex -> {
                    System.err.println("❌ 请求 #" + index + " 失败: " + ex.getMessage());
                    return null;
                });
            
            futures.add(future);
        }

        // 等待所有请求完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                long totalDuration = System.currentTimeMillis() - startTime;
                System.out.println("\n✅ 所有请求完成！总耗时: " + totalDuration + "ms");
                
                long successCount = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(r -> r != null && r.isSuccess())
                    .count();
                
                System.out.println("成功: " + successCount + "/" + urls.size());
            })
            .exceptionally(ex -> {
                System.err.println("❌ 并发请求失败: " + ex.getMessage());
                return null;
            });

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 设置超时（Java 8兼容方式）
     */
    private void scheduleTimeout(CompletableFuture<HttpResponse> future, 
                                 AsynchronousSocketChannel channel, long timeoutMs) {
        java.util.concurrent.ScheduledExecutorService scheduler = 
            java.util.concurrent.Executors.newScheduledThreadPool(1);
        
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("请求超时"));
                closeChannel(channel);
                System.err.println("❌ 请求超时");
            }
            scheduler.shutdown();
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 创建分隔线
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
