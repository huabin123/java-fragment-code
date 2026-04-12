# 第一章：BIO 原理与 I/O 模型

## 1.1 五种 I/O 模型总览

理解 BIO 首先要放在操作系统的五种 I/O 模型中对比：

```
五种 I/O 模型（以 read() 读数据为例）

① 同步阻塞 I/O（BIO）
   应用线程                   内核
   │─── read() ─────────────>│
   │      等待数据到达         │← 数据从网卡 DMA 到内核缓冲区
   │      等待数据拷贝         │← 数据从内核缓冲区拷贝到用户空间
   │<────── 返回数据 ──────────│
   特点：两个阶段都阻塞

② 同步非阻塞 I/O（NIO polling）
   应用线程                   内核
   │─── read() ─────────────>│ 数据未就绪，立即返回 EAGAIN
   │─── read() ─────────────>│ 数据未就绪，立即返回 EAGAIN
   │─── read() ─────────────>│ 数据就绪
   │      等待数据拷贝         │← 拷贝阶段仍阻塞
   │<────── 返回数据 ──────────│
   特点：第一阶段非阻塞（轮询），第二阶段阻塞

③ I/O 多路复用（NIO + Selector）
   应用线程                   内核
   │─── select()/epoll() ───>│ 监听多个 fd，阻塞直到有 fd 就绪
   │<──── 通知有 fd 就绪 ──────│
   │─── read() ─────────────>│ 就绪 fd 的数据
   │<────── 返回数据 ──────────│
   特点：用一个线程监控多个连接，select 阶段阻塞，拷贝阶段阻塞

④ 信号驱动 I/O
   注册 SIGIO 信号处理器，数据就绪时内核发信号，应用发起 read()
   特点：第一阶段非阻塞，第二阶段阻塞；实际很少使用

⑤ 异步 I/O（AIO）
   应用线程                   内核
   │─── aio_read() ─────────>│ 立即返回
   │    (继续干其他事)         │← 数据就绪后内核自动拷贝到用户空间
   │<──── 通知完成（回调）──────│
   特点：两个阶段都不阻塞，真正的异步
```

**Java 中的对应关系**：
| I/O 模型 | Java 实现 | 包 |
|---------|---------|-----|
| 同步阻塞 | `Socket` / `ServerSocket` / `InputStream` | `java.net` / `java.io` |
| I/O 多路复用 | `Selector` + `Channel` | `java.nio` |
| 异步 I/O | `AsynchronousSocketChannel` | `java.nio.channels` |

---

## 1.2 BIO 的两个阻塞点

```java
// BasicSocketDemo.java → SimpleEchoServer

ServerSocket serverSocket = new ServerSocket(8080);

while (true) {
    // 阻塞点1：等待客户端建立 TCP 连接（三次握手完成后才返回）
    Socket socket = serverSocket.accept();

    InputStream in = socket.getInputStream();
    byte[] buffer = new byte[1024];

    // 阻塞点2：等待客户端发送数据（内核缓冲区有数据后拷贝到 buffer 才返回）
    int len = in.read(buffer);

    // 处理数据...
    socket.getOutputStream().write(process(buffer, len));
}
```

**阻塞的本质**：
```
内核视角：
  accept() → 进程加入 accept 等待队列 → 内核唤醒时返回
  read()   → 进程加入 socket 等待队列 → 网卡数据 DMA 到内核缓冲区后唤醒

用户视角：
  线程被 OS 挂起（状态 WAITING），不占 CPU
  但占用：线程栈内存（默认 512KB~1MB）+ 内核数据结构
```

---

## 1.3 BIO 的代价：为什么无法支撑高并发？

```
BIO 的一连接一线程模型：

连接1 → 线程1（大部分时间阻塞在 read()，等数据）
连接2 → 线程2（大部分时间阻塞在 read()，等数据）
连接3 → 线程3（大部分时间阻塞在 read()，等数据）
...
连接N → 线程N

代价：
  假设 10000 并发连接：
  - 内存：10000 × 1MB（线程栈）= 10GB（仅线程栈！）
  - CPU：频繁在 10000 个线程间上下文切换
  - 实际 I/O 活跃率：通常 < 5%，95% 的线程在白白等待

为什么还会用 BIO：
  - 连接数少（< 100），开发简单
  - 短连接场景（HTTP 1.0，连接用完即关）
  - 对延迟不敏感，对代码可读性要求高
```

---

## 1.4 本章总结

- **BIO = 同步阻塞**：`accept()` 等连接，`read()` 等数据，两个阶段都挂起线程
- **五种 I/O 模型**：BIO → NIO polling → I/O 多路复用 → 信号驱动 → AIO，逐步减少阻塞
- **BIO 的根本限制**：一连接一线程，高并发下线程数爆炸，内存耗尽
- **适用场景**：低并发（< 100 连接）、代码简单优先、短连接服务

> **本章对应演示代码**：`BasicSocketDemo.java`（单线程 BIO 服务器、客户端、阻塞行为演示）

**继续阅读**：[02_BIO线程模型演进.md](./02_BIO线程模型演进.md)
