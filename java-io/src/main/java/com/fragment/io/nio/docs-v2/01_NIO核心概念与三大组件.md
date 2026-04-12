# 第一章：NIO 核心概念与三大组件

## 1.1 为什么需要 NIO？

BIO 的根本问题：**一个连接必须占用一个线程**，即使该连接 99% 的时间都在等待数据。

NIO 的核心思想：**用一个线程监控多个连接，只有当某连接真正有数据时才处理它**。

```
BIO 模型（1000 个连接 = 1000 个线程）：
  线程1 ─── [等待数据........][处理][等待数据......]
  线程2 ─── [等待数据.....][处理][等待数据.........]
  线程3 ─── [等待数据..][处理][等待数据............]
  ... × 1000  （997 个线程大部分时间在空等）

NIO 模型（1000 个连接 = 1 个 Selector 线程）：
  Selector ─── 监控 1000 个 Channel
              → 发现 Channel3 有数据 → 处理 Channel3
              → 发现 Channel7 有数据 → 处理 Channel7
              → ...（只处理就绪的 Channel，无需等待）
```

---

## 1.2 NIO 三大核心组件

```
┌─────────────────────────────────────────────────────────┐
│                   NIO 三大核心组件                        │
│                                                         │
│  ┌───────────┐    ┌───────────┐    ┌───────────────┐   │
│  │  Channel   │←→ │  Buffer   │    │   Selector    │   │
│  │  (通道)    │    │  (缓冲区)  │    │  (多路复用器) │   │
│  └───────────┘    └───────────┘    └───────────────┘   │
│       ↑                                    ↑            │
│   双向数据流                           监控多个 Channel   │
│   （BIO 是单向流）                   （就绪时通知处理）   │
└─────────────────────────────────────────────────────────┘

Channel（通道）：
  - 既可读又可写（BIO 的 InputStream/OutputStream 是单向的）
  - 异步就绪通知（配合 Selector）
  - 支持零拷贝（FileChannel.transferTo）
  常见类型：FileChannel、SocketChannel、ServerSocketChannel、DatagramChannel

Buffer（缓冲区）：
  - Channel 读写数据的中间容器（Channel 不能直接读写基本类型）
  - 有 position、limit、capacity 三个指针
  - 核心操作：flip()（写→读切换）、clear()（重置）、compact()（保留未读数据）

Selector（选择器）：
  - 一个 Selector 可注册多个 Channel
  - select() 阻塞直到有 Channel 就绪（I/O 多路复用）
  - 底层：Linux 用 epoll，macOS 用 kqueue，Windows 用 select
```

---

## 1.3 NIO 与 BIO 的对比

| 特性 | BIO | NIO |
|------|-----|-----|
| I/O 方式 | 流（Stream）| 块（Buffer）|
| 数据流向 | 单向（InputStream/OutputStream）| 双向（Channel）|
| 线程模型 | 一连接一线程 | 一线程多连接（Selector）|
| 阻塞性 | 同步阻塞 | 同步非阻塞 |
| 适合场景 | 低并发、代码简单 | 高并发、长连接 |
| 代码复杂度 | 低 | 高（推荐用 Netty 封装）|

---

## 1.4 快速体验 NIO

`SelectorDemo.java` 演示了用 NIO 实现的 Echo 服务器骨架：

```java
// NIO 服务器的核心骨架（SelectorDemo.java → EchoServer）
Selector selector = Selector.open();

ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);           // 非阻塞模式（必须设置！）
serverChannel.bind(new InetSocketAddress(8080));
serverChannel.register(selector, SelectionKey.OP_ACCEPT);  // 注册 ACCEPT 事件

while (true) {
    selector.select();  // 阻塞直到有 Channel 就绪
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    Iterator<SelectionKey> it = selectedKeys.iterator();

    while (it.hasNext()) {
        SelectionKey key = it.next();
        it.remove();   // ❗必须手动移除，否则下次还会处理

        if (key.isAcceptable()) {
            // 有新连接
            SocketChannel client = serverChannel.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {
            // 有数据可读
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            int n = client.read(buf);
            if (n > 0) {
                buf.flip();
                client.write(buf);   // Echo 回去
            } else if (n < 0) {
                key.cancel();
                client.close();
            }
        }
    }
}
```

---

## 1.5 本章总结

- **NIO 解决的核心问题**：一线程管理多连接，消除"等待数据"时对线程的占用
- **三大组件**：Channel（双向通道）+ Buffer（读写缓冲区）+ Selector（就绪事件多路复用）
- **非阻塞模式**：`channel.configureBlocking(false)` 是 NIO 的前提，必须设置
- **selectedKeys 必须手动 remove**：否则下轮循环会重复处理，是新手最常见的 Bug

> **本章对应演示代码**：`SelectorDemo.java`（NIO Echo 服务器、非阻塞演示）

**继续阅读**：[02_Buffer深度解析.md](./02_Buffer深度解析.md)
