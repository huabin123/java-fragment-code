# 第四章：BIO 最佳实践与适用边界

## 4.1 BIO 的正确适用场景

```
✅ 适合用 BIO 的场景：
  1. 连接数少（< 100 个并发连接）
  2. 短连接为主（请求-响应后即关闭）
  3. 代码简单优先（工具脚本、内部小服务）
  4. 对话式协议（FTP 命令通道、Telnet）
  5. 阻塞本身有业务意义（如等待用户输入的 CLI 工具）

❌ 不适合用 BIO 的场景：
  1. 高并发（> 1000 并发连接）
  2. 长连接（IM、游戏服务器、推送服务）
  3. 高性能 RPC 框架
  4. 实时数据推送
```

---

## 4.2 生产 BIO 服务必须配置项

```java
// ThreadPoolServerDemo.java → ProductionBioServer

public class ProductionBioServer {

    // ✅ 配置1：有界线程池（防止任务积压撑爆内存）
    private static final ExecutorService POOL = new ThreadPoolExecutor(
        20, 200,
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(500),       // 有界队列！不要用 LinkedBlockingQueue()
        r -> new Thread(r, "bio-worker-" + THREAD_COUNT.incrementAndGet()),
        (r, e) -> log.warn("请求被拒绝，线程池已满")
    );

    public void start() throws IOException {
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);        // ✅ 配置2：地址复用（快速重启）
        server.bind(new InetSocketAddress(8080), 256);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket client = server.accept();
                POOL.submit(() -> handle(client));
            } catch (SocketTimeoutException e) {
                // 超时是正常的，继续等待
            }
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(30_000);     // ✅ 配置3：读超时（防止线程永久阻塞）
            socket.setTcpNoDelay(true);      // ✅ 配置4：禁用 Nagle（降低延迟）
            socket.setKeepAlive(true);       // ✅ 配置5：TCP 保活

            // 用 BufferedStream 减少系统调用
            try (BufferedInputStream  in  = new BufferedInputStream(socket.getInputStream(), 8192);
                 BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream(), 8192)) {
                processRequest(in, out);
            }
        } catch (SocketTimeoutException e) {
            log.warn("客户端 {} 读超时，关闭连接", socket.getRemoteSocketAddress());
        } catch (IOException e) {
            log.error("连接处理异常", e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
```

---

## 4.3 BIO vs NIO vs Netty 选型

```
并发连接数     推荐方案        理由
────────────────────────────────────────────────────────────────
< 50         BIO（线程池）   代码简单，维护成本低
50 ~ 1000    NIO（自实现）   可接受的复杂度，无额外依赖
1000 ~ 10万  Netty           成熟框架，内置优化，生产验证
> 10万       Netty + 优化    水平扩展，集群化
```

---

## 4.4 常见陷阱总结

| 陷阱 | 症状 | 解决方案 |
|------|------|---------|
| 未设置 `SoTimeout` | 线程永久阻塞，线程池满，服务无响应 | `socket.setSoTimeout(30000)` |
| 无界任务队列 | 任务积压，内存 OOM | `ArrayBlockingQueue(500)` 有界队列 |
| 未设置 `ReuseAddress` | 服务重启失败，报 "Address already in use" | `server.setReuseAddress(true)` |
| 未用 `BufferedStream` | 频繁系统调用，CPU 高，性能差 | `new BufferedOutputStream(out, 8192)` |
| 未关闭 Socket | 文件描述符泄漏，最终 "Too many open files" | `try-with-resources` |
| 在主线程 accept + 处理 | 串行服务，只能处理一个客户端 | 提交到线程池 |

---

## 4.5 本章总结

- **BIO 有其合理场景**：低并发、短连接、代码简单优先，不要因为"BIO 落后"就一律排斥
- **生产 BIO 五件套**：有界线程池 + ReuseAddress + SoTimeout + TcpNoDelay + BufferedStream
- **选型原则**：根据并发连接数决定；< 50 连接用 BIO 完全够用，不必引入 Netty 的复杂度
- **陷阱预防**：SoTimeout 和有界队列是最常见的遗漏点，务必在代码模板中固化

> **本章对应演示代码**：`ThreadPoolServerDemo.java`（完整生产级 BIO 服务、压测、参数调优）

**返回导航**：[README.md](../README.md) | **下一模块**：[NIO 模块](../../nio/docs-v2/README.md)
