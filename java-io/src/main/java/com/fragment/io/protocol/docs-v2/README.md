# protocol 模块文档（v2）

> 本目录是对 `docs/` 目录的重组优化版本，原 `docs/` 目录保持不变。

## 文档结构

| 章节 | 文件 | 核心内容 |
|------|------|---------|
| 第一章 | [01_TCP协议深度解析.md](./01_TCP协议深度解析.md) | 三次握手/四次挥手原理、TIME_WAIT 处理、Nagle 算法、Socket 缓冲区调优 |
| 第二章 | [02_HTTP协议演进.md](./02_HTTP协议演进.md) | HTTP/1.0→1.1→2→3 演进、队头阻塞问题、多路复用、Java HttpClient 示例 |
| 第三章 | [03_WebSocket协议详解.md](./03_WebSocket协议详解.md) | 握手流程、帧格式、Netty 实现、心跳保活 |
| 第四章 | [04_自定义协议设计.md](./04_自定义协议设计.md) | 协议五要素、RPC 协议完整实现、异步请求-响应匹配、序列化选型 |
| 第五章 | [05_协议最佳实践.md](./05_协议最佳实践.md) | 协议设计七原则、安全防护、版本兼容策略、常见 Bug 排查 |

## 快速入口

**TCP 连接延迟高**：→ 第一章 1.3（TCP_NODELAY=true 禁用 Nagle 算法）

**服务重启端口被占用**：→ 第一章 1.2（TIME_WAIT 和 SO_REUSEADDR）

**HTTP 并发请求慢**：→ 第二章 2.3（HTTP/2 多路复用，用 HttpClient 并发 sendAsync）

**WebSocket 连接断开无感知**：→ 第三章 3.5（IdleStateHandler 心跳检测）

**自定义协议粘包拆包**：→ 第四章 4.3（LengthFieldBasedFrameDecoder 配置）

**RPC pending Map 内存泄漏**：→ 第五章 5.4（超时清理 + channelInactive 清理）

**攻击者发送超大消息 OOM**：→ 第五章 5.2（maxFrameLength 限制 + 魔数快速断连）

## 与 Demo 代码对应

```
demo/
├── TcpProtocolDemo.java        ← 第一章（三次握手状态机、Nagle 实测、keepAlive）
├── HttpProtocolDemo.java       ← 第二章（HTTP/1.1 持久连接、HTTP/2 多路复用）
├── WebSocketProtocolDemo.java  ← 第三章（WebSocket 握手、Netty 服务端、群聊）
└── CustomProtocolDemo.java     ← 第四章（RPC 协议完整实现）、第五章（安全防护）
```

## 与原文档的差异

| 原问题 | 优化后 |
|-------|--------|
| TCP 章节未说明对 Java 编程的实际影响 | 第一章每个知识点都附有对应 Java 代码示例 |
| HTTP/3 完全未提及 | 第二章 2.4 新增 HTTP/3 / QUIC 原理和 Java 现状 |
| WebSocket 帧格式只有图，无字段说明 | 第三章 3.3 补充每个字段的含义和取值 |
| 自定义协议无异步请求-响应匹配实现 | 第四章 4.4 新增 CompletableFuture + pending Map 完整实现 |
| 协议安全防护未提及 | 第五章 5.2 新增魔数校验、消息大小限制、版本范围校验 |
| pending Map 泄漏问题未提及 | 第五章 5.4 新增超时 + channelInactive 双重清理方案 |
