# 第一章：AIO 原理与适用场景

## 1.1 从 NIO 到 AIO：最后一步

```
BIO：read() 阻塞直到数据到达并拷贝完毕（两阶段都阻塞）
NIO：select() 等到数据就绪后，再调 read() 拷贝（第一阶段阻塞，第二阶段也阻塞）
AIO：注册回调，内核数据就绪后自动拷贝，拷贝完才通知应用（两阶段都不阻塞）

           等待数据就绪        数据拷贝到用户空间
BIO        ████阻塞████        ████阻塞████
NIO        ████阻塞████（select）  阻塞（read）
AIO        ────不阻塞────       ────不阻塞────（内核自动完成后回调）
```

**AIO 的核心区别**：
- NIO：你等内核告诉你"数据准备好了，你来取"（就绪通知）
- AIO：你告诉内核"数据准备好后帮我搬到这里，搬完叫我"（完成通知）

---

## 1.2 AIO 的底层实现

```
Linux：io_uring（Linux 5.1+）或 epoll 模拟（内核线程池）
  Java AIO 在 Linux 上底层用线程池模拟异步（并非真正的内核异步 I/O）
  实际性能有时不如 epoll + 单线程 NIO

Windows：IOCP（I/O Completion Port）
  真正的内核级异步 I/O，性能优秀
  Java AIO 在 Windows 上直接使用 IOCP，是真正的异步

macOS：kqueue 模拟
  类似 Linux，线程池模拟

结论：
  Java AIO 在 Linux 上并非真正异步，性能不一定优于 NIO
  这也是 Netty 放弃 AIO，坚持基于 epoll NIO 的原因之一
```

---

## 1.3 AIO 的两种编程模型

```java
// AIO 有两种通知方式：

// 方式1：CompletionHandler 回调（推荐）
channel.read(buffer, attachment, new CompletionHandler<Integer, Object>() {
    @Override
    public void completed(Integer bytesRead, Object attachment) {
        // I/O 完成，bytesRead 是实际读取的字节数
        process(buffer, bytesRead);
    }

    @Override
    public void failed(Throwable exc, Object attachment) {
        // I/O 失败
        exc.printStackTrace();
    }
});
// 特点：注册即返回，不阻塞；完成后由 AIO 线程池调用回调

// 方式2：Future 轮询（适合简单场景）
Future<Integer> future = channel.read(buffer);
// 可以做其他事情...
int bytesRead = future.get();  // 阻塞等待完成（失去异步意义，但代码简单）
```

---

## 1.4 AIO 适用场景分析

```
✅ AIO 的优势场景：
  1. 大文件异步读写（磁盘 I/O 延迟高，AIO 不阻塞线程）
  2. Windows 服务器（IOCP 性能优秀）
  3. 连接数极高但每次 I/O 数据量大的场景

❌ AIO 的局限：
  1. Linux 上底层是线程池模拟，非真正异步
  2. 回调地狱：多层嵌套 CompletionHandler 代码可读性差
  3. 调试困难：回调在 AIO 线程池中执行，堆栈追踪困难
  4. Java 生态支持有限（Netty 不支持 AIO，Spring WebFlux 基于 NIO）

实际建议：
  网络编程 → 优先 Netty（基于 NIO）
  异步文件 I/O → AIO 的 AsynchronousFileChannel 有价值
  如果坚持用 AIO 网络编程 → 推荐配合 CompletableFuture 组合异步操作
```

---

## 1.5 本章总结

- **AIO 本质**：完成通知（内核完成 I/O 后通知），NIO 是就绪通知（数据就绪后通知你来取）
- **两阶段都不阻塞**：等待数据 + 数据拷贝都由内核完成，应用线程全程不阻塞
- **Linux 现实**：Java AIO 在 Linux 上是线程池模拟，非真正异步；Windows 上是 IOCP 真异步
- **两种编程模型**：`CompletionHandler` 回调（推荐）+ `Future.get()` 阻塞（简单场景）
- **选型建议**：网络编程选 Netty；异步文件 I/O 可用 `AsynchronousFileChannel`

> **本章对应演示代码**：`AsynchronousSocketChannelDemo.java`（AIO 服务端/客户端对比）

**继续阅读**：[02_核心组件详解.md](./02_核心组件详解.md)
