# 第三章：AIO 最佳实践与陷阱

## 3.1 五个常见陷阱

### 陷阱1：忘记在 accept 完成后重新调用 accept()

```java
// ❌ 错误：accept 完成后没有重新注册，只能接受第一个连接
server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
    @Override
    public void completed(AsynchronousSocketChannel client, Void att) {
        handleConnection(client);
        // 忘记 server.accept(null, this)！后续连接全部无法进入
    }
    // ...
});

// ✅ 正确：在 completed 的最开始就重新注册
server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
    @Override
    public void completed(AsynchronousSocketChannel client, Void att) {
        server.accept(null, this);  // 立即重新接受，不影响当前连接处理
        handleConnection(client);
    }
    // ...
});
```

### 陷阱2：在回调中执行耗时操作阻塞 AIO 线程池

```java
// ❌ 错误：AIO 线程池线程被数据库操作占用，无法处理其他 I/O 完成通知
public void completed(Integer bytesRead, ByteBuffer buf) {
    String sql = parseQuery(buf);
    List<Row> rows = database.execute(sql);  // 可能耗时 100ms，阻塞 AIO 线程！
    channel.write(encode(rows), ...);
}

// ✅ 正确：转交业务线程池
public void completed(Integer bytesRead, ByteBuffer buf) {
    String query = parseQuery(buf);
    BUSINESS_POOL.submit(() -> {
        List<Row> rows = database.execute(query);  // 在业务线程执行
        channel.write(encode(rows), ...);
    });
}
```

### 陷阱3：ByteBuffer 被多个操作并发使用

```java
// ❌ 错误：同一个 ByteBuffer 同时用于读和写
ByteBuffer buf = ByteBuffer.allocate(4096);
channel.read(buf, buf, readHandler);
// 如果读尚未完成，又开始了写操作 → 数据损坏

// ✅ 正确：每次操作使用独立的 ByteBuffer，或确保严格串行
// 读完后翻转，处理，再准备写 buffer
```

### 陷阱4：异常未处理导致连接泄漏

```java
// ❌ 错误：failed() 中没有关闭 channel
public void failed(Throwable exc, ByteBuffer buf) {
    exc.printStackTrace();  // 只打印，没有关闭 channel
    // channel 永远占用资源，文件描述符泄漏
}

// ✅ 正确：failed() 中必须关闭资源
public void failed(Throwable exc, ByteBuffer buf) {
    if (!(exc instanceof AsynchronousCloseException)) {
        log.error("AIO 操作失败", exc);
    }
    try { channel.close(); } catch (IOException ignored) {}
}
```

### 陷阱5：主线程退出导致 AIO 任务未完成

```java
// ❌ 错误：主线程没有等待，JVM 直接退出
server.accept(null, handler);
// main() 方法结束，JVM 退出，AIO 线程池被销毁

// ✅ 正确：主线程等待
CountDownLatch latch = new CountDownLatch(1);
server.accept(null, handler);
latch.await();  // 或 Thread.currentThread().join()
```

---

## 3.2 AIO 与 NIO 的性能对比

```
基准测试结论（Linux，Java 17）：

并发连接数   NIO (Netty)    AIO         说明
────────────────────────────────────────────────────────
1000        ✅ 高吞吐      ≈ 相当       Linux 上差异不大
10000       ✅ 更稳定      ⚠️ 抖动多    AIO 线程池调度开销
大文件传输   ✅ sendfile   ✅ 也有优化  取决于场景
Windows     一般           ✅ IOCP 优  真正异步

结论：Linux 上 NIO（Netty）通常优于或等于 AIO
      Windows 上 AIO（IOCP）有优势但 Java 服务很少跑 Windows
      异步文件 I/O（AsynchronousFileChannel）是 AIO 最有价值的使用场景
```

---

## 3.3 何时应该选 AIO

```
✅ 推荐使用 AIO 的场景：
  1. 异步读写大文件（AsynchronousFileChannel）
  2. Windows 部署的 Java 服务（IOCP 真异步）
  3. 学习异步编程模型（为理解 Reactive/CompletableFuture 打基础）

❌ 不推荐的场景：
  1. Linux 上的高性能网络服务 → 用 Netty（NIO + epoll）
  2. 需要大量第三方生态支持 → Netty 生态完善，AIO 较孤立
  3. 团队对异步编程不熟悉 → BIO + 线程池更简单可控
```

---

## 3.4 本章总结

**五个陷阱**：
1. **accept 必须循环注册**：完成后立即重新调用 `accept()`
2. **回调不做耗时操作**：业务逻辑提交到独立线程池
3. **ByteBuffer 不共享**：并发操作用独立 Buffer
4. **failed() 必须关闭资源**：否则文件描述符泄漏
5. **主线程不能退出**：用 `latch.await()` 或 `join()` 保持 JVM 存活

**选型建议**：
- Linux 网络编程 → Netty
- 异步文件 I/O → `AsynchronousFileChannel`
- Windows 服务 → AIO（IOCP）
- 学习目的 → `CompletionHandler` + `CompletableFuture` 组合是很好的异步编程练习

> **本章对应演示代码**：`CompletionHandlerDemo.java`（陷阱演示、CompletableFuture 包装、性能对比）

**返回导航**：[README.md](../README.md) | **下一模块**：[optimization 模块](../../optimization/docs-v2/README.md)
