# 第二章：HTTP 协议演进

## 2.1 HTTP/1.0：短连接的代价

```
HTTP/1.0 模型：每次请求都建立新 TCP 连接

GET /index.html HTTP/1.0
─── 三次握手 ───────────────────  约 1 RTT
─── HTTP 请求 ──────────────────
─── HTTP 响应 ──────────────────  约 1 RTT
─── 四次挥手 ──────────────────── 约 1 RTT

一个 HTML 页面（1 个 HTML + 10 个资源）需要 11 次 TCP 连接！
每次连接：3 RTT 握手+挥手 overhead + 数据传输时间
低延迟线路（10ms RTT）：每次连接额外消耗 30ms，11 个连接 = 330ms overhead
```

---

## 2.2 HTTP/1.1：持久连接与管道化

```
HTTP/1.1 改进1：持久连接（Keep-Alive，默认开启）
  一个 TCP 连接复用多个 HTTP 请求，减少握手开销

HTTP/1.1 改进2：管道化（Pipelining）
  不等前一个响应，直接发下一个请求
  但：响应必须按请求顺序返回（队头阻塞，Head-of-Line Blocking）

问题：队头阻塞（HOL Blocking）
  请求1 处理中（慢）
  请求2 已就绪（快）
  请求3 已就绪（快）
  → 请求2、3 必须等请求1 完成后才能响应
  → 浏览器通常开 6 个并发 TCP 连接绕过此问题
```

```java
// HttpProtocolDemo.java → demonstrateHttp11()

// Java 11+ HttpClient 默认使用 HTTP/1.1 持久连接
HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.ofSeconds(10))
    .build();

// 复用同一个 HttpClient 实例，连接会被自动复用
HttpRequest req1 = HttpRequest.newBuilder(URI.create("http://example.com/api/1")).build();
HttpRequest req2 = HttpRequest.newBuilder(URI.create("http://example.com/api/2")).build();

HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
// 两个请求复用同一个 TCP 连接
```

---

## 2.3 HTTP/2：多路复用

```
HTTP/2 核心改进：二进制分帧 + 多路复用

HTTP/1.1（文本）：
  GET /api/data HTTP/1.1\r\n
  Host: example.com\r\n
  \r\n

HTTP/2（二进制帧）：
  ┌──────────┬──────┬──────────────────────┐
  │ Length(3)│Type(1)│ Flags(1)│Stream ID(4)│ Payload(N) │
  └──────────┴──────┴──────────────────────┘

多路复用：
  Stream 1: GET /api/user
  Stream 3: GET /api/orders    ← 同一个 TCP 连接
  Stream 5: GET /api/products  ← 并发发送，无序返回
  → 彻底解决 HTTP/1.1 的队头阻塞问题（应用层面）

其他改进：
  - Header 压缩（HPACK）：减少重复 Header 的传输
  - 服务端推送（Server Push）：主动推送客户端可能需要的资源
  - 流优先级：重要资源先传
```

```java
// HttpProtocolDemo.java → demonstrateHttp2()

// Java 11+ HttpClient 支持 HTTP/2
HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .build();

// 并发发送多个请求（复用同一 TCP 连接，HTTP/2 多路复用）
List<HttpRequest> requests = List.of(
    HttpRequest.newBuilder(URI.create("https://example.com/api/1")).build(),
    HttpRequest.newBuilder(URI.create("https://example.com/api/2")).build(),
    HttpRequest.newBuilder(URI.create("https://example.com/api/3")).build()
);

List<CompletableFuture<HttpResponse<String>>> futures = requests.stream()
    .map(req -> client.sendAsync(req, HttpResponse.BodyHandlers.ofString()))
    .collect(Collectors.toList());

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
// 三个请求并行发出，共用一个 TCP 连接，无队头阻塞
```

---

## 2.4 HTTP/3：基于 QUIC

```
HTTP/2 未解决的问题：TCP 层的队头阻塞
  HTTP/2 在应用层解决了 HOL，但 TCP 层仍有：
  一个 TCP 数据包丢失 → 所有 HTTP/2 流都停顿等待重传

HTTP/3 解决方案：用 QUIC（UDP + 可靠传输）替代 TCP
  QUIC（Quick UDP Internet Connections）：
  - 基于 UDP，自行实现可靠传输、拥塞控制
  - 独立的流：某个流的包丢失，只阻塞该流，不影响其他流
  - 0-RTT 握手：首次连接 1-RTT，复用连接 0-RTT（比 TLS 1.3 + TCP 快）
  - 连接迁移：切换网络（WiFi→4G）不断连，IP 地址变了 QUIC 连接仍然有效

Java 现状（2024）：
  标准库：暂无内置 HTTP/3 支持
  第三方：Netty 提供 quiche（基于 BoringSSL）
  最简单方式：Armeria 框架内置 HTTP/3 支持
```

---

## 2.5 HTTP 版本对比速查

| 特性 | HTTP/1.0 | HTTP/1.1 | HTTP/2 | HTTP/3 |
|------|---------|---------|--------|--------|
| 连接复用 | ❌ | ✅ Keep-Alive | ✅ | ✅ |
| 多路复用 | ❌ | ❌（有 HOL）| ✅ | ✅ |
| 头部压缩 | ❌ | ❌ | ✅ HPACK | ✅ QPACK |
| 传输协议 | TCP | TCP | TCP | UDP（QUIC）|
| 服务端推送 | ❌ | ❌ | ✅ | ✅ |
| 0-RTT | ❌ | ❌ | ❌ | ✅ |

---

## 2.6 本章总结

- **HTTP/1.0**：短连接，每次请求三次握手，高延迟
- **HTTP/1.1**：持久连接（默认），但有应用层队头阻塞（HOL）；浏览器用 6 个并发连接绕过
- **HTTP/2**：二进制分帧 + 多路复用，彻底解决应用层 HOL；Header 压缩（HPACK）减小体积
- **HTTP/3**：基于 QUIC（UDP），解决 TCP 层 HOL；0-RTT 握手；连接迁移
- **Java 选型**：HTTP/1.1/2 用 `java.net.http.HttpClient`（Java 11+）；HTTP/3 用 Armeria 或 Netty QUIC

> **本章对应演示代码**：`HttpProtocolDemo.java`（HTTP/1.1 持久连接、HTTP/2 多路复用并发请求、性能对比）

**继续阅读**：[03_WebSocket协议详解.md](./03_WebSocket协议详解.md)
