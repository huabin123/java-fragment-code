package com.fragment.juc.async.project;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 异步HTTP客户端
 * 
 * 功能：
 * 1. 异步HTTP请求
 * 2. 批量并行请求
 * 3. 超时控制
 * 4. 重试机制
 * 5. 降级处理
 * 
 * @author huabin
 */
public class AsyncHttpClient {

    private final ExecutorService executor;
    private final long defaultTimeout;
    private final int maxRetries;

    public AsyncHttpClient() {
        this(Executors.newFixedThreadPool(50), 5000, 3);
    }

    public AsyncHttpClient(ExecutorService executor, long defaultTimeout, int maxRetries) {
        this.executor = executor;
        this.defaultTimeout = defaultTimeout;
        this.maxRetries = maxRetries;
    }

    /**
     * 异步GET请求
     */
    public CompletableFuture<Response> getAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("  [GET] " + url);
            return executeRequest("GET", url, null);
        }, executor);
    }

    /**
     * 异步POST请求
     */
    public CompletableFuture<Response> postAsync(String url, String body) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("  [POST] " + url);
            return executeRequest("POST", url, body);
        }, executor);
    }

    /**
     * 带超时的请求
     */
    public CompletableFuture<Response> getWithTimeout(String url, long timeout) {
        CompletableFuture<Response> request = getAsync(url);
        
        CompletableFuture<Response> timeoutFuture = new CompletableFuture<>();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            timeoutFuture.completeExceptionally(new TimeoutException("请求超时"));
        }, timeout, TimeUnit.MILLISECONDS);

        return request.applyToEither(timeoutFuture, r -> r)
            .whenComplete((r, ex) -> scheduler.shutdown());
    }

    /**
     * 带重试的请求
     */
    public CompletableFuture<Response> getWithRetry(String url) {
        return getWithRetry(url, maxRetries, 0);
    }

    private CompletableFuture<Response> getWithRetry(String url, int maxRetries, int attempt) {
        return getAsync(url).exceptionally(ex -> {
            if (attempt < maxRetries) {
                System.out.println("  [重试] " + url + " (第" + (attempt + 1) + "次)");
                sleep(1000 * (attempt + 1)); // 指数退避
                return getWithRetry(url, maxRetries, attempt + 1).join();
            } else {
                System.err.println("  [失败] " + url + " 已达最大重试次数");
                throw new RuntimeException("请求失败", ex);
            }
        });
    }

    /**
     * 批量并行请求
     */
    public CompletableFuture<List<Response>> batchGet(List<String> urls) {
        List<CompletableFuture<Response>> futures = urls.stream()
            .map(this::getAsync)
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
            );
    }

    /**
     * 批量请求（部分失败不影响整体）
     */
    public CompletableFuture<List<Response>> batchGetWithFallback(List<String> urls) {
        List<CompletableFuture<Response>> futures = urls.stream()
            .map(url -> getAsync(url).exceptionally(ex -> {
                System.err.println("  [降级] " + url + " 失败，返回空响应");
                return Response.empty();
            }))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toList())
            );
    }

    /**
     * 聚合多个API
     */
    public CompletableFuture<AggregatedData> aggregateAPIs(String userId) {
        CompletableFuture<Response> userFuture = 
            getAsync("/api/user/" + userId);

        CompletableFuture<Response> ordersFuture = 
            getAsync("/api/orders/" + userId);

        CompletableFuture<Response> profileFuture = 
            getAsync("/api/profile/" + userId);

        return CompletableFuture.allOf(userFuture, ordersFuture, profileFuture)
            .thenApply(v -> new AggregatedData(
                userFuture.join(),
                ordersFuture.join(),
                profileFuture.join()
            ));
    }

    /**
     * 竞速请求（多个数据源）
     */
    public CompletableFuture<Response> raceRequest(List<String> urls) {
        CompletableFuture<Response>[] futures = urls.stream()
            .map(this::getAsync)
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.anyOf(futures)
            .thenApply(result -> (Response) result);
    }

    // ========== 模拟HTTP请求 ==========

    private Response executeRequest(String method, String url, String body) {
        try {
            // 模拟网络延迟
            Thread.sleep((long) (Math.random() * 1000));

            // 模拟随机失败
            if (Math.random() < 0.1) {
                throw new RuntimeException("网络错误");
            }

            return new Response(200, "Success: " + method + " " + url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("请求被中断", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== 响应类 ==========

    public static class Response {
        private final int statusCode;
        private final String body;

        public Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public static Response empty() {
            return new Response(0, "");
        }

        public boolean isEmpty() {
            return statusCode == 0;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        @Override
        public String toString() {
            return "Response{status=" + statusCode + ", body='" + body + "'}";
        }
    }

    public static class AggregatedData {
        private final Response user;
        private final Response orders;
        private final Response profile;

        public AggregatedData(Response user, Response orders, Response profile) {
            this.user = user;
            this.orders = orders;
            this.profile = profile;
        }

        @Override
        public String toString() {
            return "AggregatedData{user=" + user.statusCode + 
                   ", orders=" + orders.statusCode + 
                   ", profile=" + profile.statusCode + "}";
        }
    }

    // ========== 测试 ==========

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            异步HTTP客户端演示                               ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        AsyncHttpClient client = new AsyncHttpClient();

        // 测试1：基本请求
        System.out.println("\n========== 测试1：基本异步请求 ==========\n");
        CompletableFuture<Response> future1 = client.getAsync("/api/users");
        System.out.println("请求已发送，继续其他工作...");
        Response response1 = future1.get();
        System.out.println("响应: " + response1);

        // 测试2：超时控制
        System.out.println("\n========== 测试2：超时控制 ==========\n");
        client.getWithTimeout("/api/slow", 2000)
            .thenAccept(r -> System.out.println("成功: " + r))
            .exceptionally(ex -> {
                System.err.println("超时: " + ex.getMessage());
                return null;
            })
            .get();

        // 测试3：重试机制
        System.out.println("\n========== 测试3：重试机制 ==========\n");
        client.getWithRetry("/api/unstable")
            .thenAccept(r -> System.out.println("最终成功: " + r))
            .exceptionally(ex -> {
                System.err.println("最终失败: " + ex.getMessage());
                return null;
            })
            .get();

        // 测试4：批量请求
        System.out.println("\n========== 测试4：批量并行请求 ==========\n");
        List<String> urls = Arrays.asList(
            "/api/user/1",
            "/api/user/2",
            "/api/user/3"
        );
        List<Response> responses = client.batchGet(urls).get();
        System.out.println("批量请求完成，成功: " + responses.size());

        // 测试5：聚合API
        System.out.println("\n========== 测试5：聚合多个API ==========\n");
        AggregatedData data = client.aggregateAPIs("user123").get();
        System.out.println("聚合数据: " + data);

        // 测试6：竞速请求
        System.out.println("\n========== 测试6：竞速请求 ==========\n");
        List<String> sources = Arrays.asList(
            "/api/source1",
            "/api/source2",
            "/api/source3"
        );
        Response fastest = client.raceRequest(sources).get();
        System.out.println("最快响应: " + fastest);

        System.out.println("\n===========================");
        System.out.println("✅ 异步HTTP客户端演示完成");
        System.out.println("===========================");

        System.exit(0);
    }
}
