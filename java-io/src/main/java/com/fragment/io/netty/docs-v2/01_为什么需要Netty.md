# 第一章：为什么需要 Netty？

## 1.1 直接用 NIO 的五大痛点

Java NIO 已经是非阻塞的了，为什么还需要 Netty？先看直接写 NIO 服务器时的代码：

```java
// 直接用 NIO 实现一个 Echo 服务器（只是骨架，省略了粘包处理）
Selector selector = Selector.open();
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);
serverChannel.bind(new InetSocketAddress(8080));
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select();                               // ← JDK Bug：Linux 上可能空轮询，CPU 100%
    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();                                 // ← 容易忘记，导致重复处理

        if (key.isAcceptable()) {
            SocketChannel ch = serverChannel.accept();
            ch.configureBlocking(false);
            ch.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {
            SocketChannel ch = (SocketChannel) key.channel();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            int n = ch.read(buf);
            if (n > 0) {
                buf.flip();                          // ← 容易忘记 flip，导致读到空数据
                ch.write(buf);
            } else if (n < 0) {
                ch.close();
            }
            // ← 没有处理粘包/拆包，实际收到的不一定是完整消息
        }
    }
}
```

**五大问题**：

| 痛点 | 具体表现 |
|------|---------|
| **编程复杂** | `flip()`/`clear()`/`compact()` 状态管理易出错，粘包拆包需自行处理 |
| **JDK Epoll Bug** | Linux 上 `Selector.select()` 可能空返回，导致 CPU 100%，需自行检测重建 Selector |
| **线程模型缺失** | 没有内置的 Boss/Worker 线程分离，I/O 线程与业务线程混用 |
| **功能不完整** | 没有编解码器、心跳检测、SSL/TLS、流量整形 |
| **跨平台差异** | Linux epoll / macOS kqueue / Windows select，需针对平台适配 |

---

## 1.2 Netty 是什么？

```
Netty = 异步事件驱动的网络应用框架
      + 封装了 NIO 所有痛点
      + 提供高性能、高可用的开箱即用组件
```

**同样的 Echo 服务器，用 Netty 实现**（`EchoServerDemo.java`）：

```java
EventLoopGroup boss   = new NioEventLoopGroup(1);     // 接受连接（1个线程够了）
EventLoopGroup worker = new NioEventLoopGroup();       // 处理 I/O（默认 CPU×2 个线程）
try {
    new ServerBootstrap()
        .group(boss, worker)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new EchoServerHandler());
            }
        })
        .bind(8080).sync()
        .channel().closeFuture().sync();
} finally {
    boss.shutdownGracefully();
    worker.shutdownGracefully();
}
```

代码量减少一半，且：Epoll Bug 已修复、线程模型开箱即用、可随时插拔编解码器。

---

## 1.3 Netty 的整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Netty 架构                               │
│                                                                 │
│  ┌──────────┐   ┌────────────────────────────────────────────┐  │
│  │          │   │                 Transport                  │  │
│  │  你的代码  │   │  NIO  │  OIO  │  Local  │  Embedded      │  │
│  │(Handler) │   └────────────────────────────────────────────┘  │
│  │          │   ┌────────────────────────────────────────────┐  │
│  │          │   │              Protocol Support              │  │
│  │          │   │  HTTP  │ WebSocket │ SSL/TLS │ Protobuf   │  │
│  └──────────┘   └────────────────────────────────────────────┘  │
│                 ┌────────────────────────────────────────────┐  │
│                 │               Core（核心）                  │  │
│                 │  EventLoop │ ByteBuf │ Pipeline │ Bootstrap │  │
│                 └────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**五个核心概念**（后续章节逐一深入）：

| 概念 | 一句话描述 |
|------|----------|
| `Channel` | 网络连接的抽象，对应一个 Socket |
| `EventLoop` | 驱动 Channel 的 I/O 线程，负责所有事件处理 |
| `ChannelPipeline` | 处理器链，入站/出站事件依次经过各个 Handler |
| `ByteBuf` | 替代 `ByteBuffer`，双指针、动态扩容、零拷贝 |
| `Bootstrap` | 启动引导类，配置和启动整个 Netty 应用 |

---

## 1.4 Netty 解决 JDK Epoll Bug 的方式

```java
// Netty 内部的空轮询检测逻辑（简化版）
long time = System.nanoTime();
int selectedKeys = selector.select(timeoutMillis);

if (selectedKeys == 0) {
    long timeBlocked = System.nanoTime() - time;
    if (timeBlocked < TimeUnit.MILLISECONDS.toNanos(timeoutMillis) / 2) {
        // select 没有阻塞足够时间就返回了 → 很可能是空轮询 Bug
        selectCnt++;
    }
}

if (selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
    // 连续空轮询超过阈值（默认512次）→ 重建 Selector
    rebuildSelector();   // 把旧 Selector 上的所有 key 迁移到新 Selector
    selectCnt = 0;
}
```

---

## 1.5 Netty 适用场景

| 场景 | 典型案例 |
|------|---------|
| 高性能 RPC 框架 | Dubbo、gRPC（Java）|
| 实时消息推送 | 游戏服务器、即时聊天 |
| HTTP/WebSocket 服务 | API 网关、实时数据推送 |
| 自定义二进制协议 | 金融交易系统、物联网设备通信 |

> **本章对应演示代码**：`EchoServerDemo.java`（Netty vs NIO 对比实现）

**继续阅读**：[02_线程模型与EventLoop.md](./02_线程模型与EventLoop.md)
