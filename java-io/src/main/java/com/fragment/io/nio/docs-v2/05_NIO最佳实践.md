# 第五章：NIO 最佳实践

## 5.1 生产级 NIO 服务器的七个要点

```java
// ReactorDemo.java → ProductionNioServer（完整单线程 Reactor）

// ① 必须 configureBlocking(false)
serverChannel.configureBlocking(false);

// ② selectedKeys 必须手动 remove
Iterator<SelectionKey> it = selector.selectedKeys().iterator();
while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove();    // ← 不 remove，下次循环重复处理相同事件
    process(key);
}

// ③ OP_WRITE 只在有数据时注册，写完立即取消
// 有数据要写：
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
// 写完后：
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

// ④ read() 返回 -1 时关闭连接
int n = channel.read(buf);
if (n == -1) {
    key.cancel();
    channel.close();
}

// ⑤ 异常时取消 key，防止 Selector 反复触发
try {
    handle(key);
} catch (IOException e) {
    key.cancel();
    try { key.channel().close(); } catch (IOException ignored) {}
}

// ⑥ write() 可能未写完，需要循环
ByteBuffer resp = prepareResponse();
while (resp.hasRemaining()) {
    int written = channel.write(resp);
    if (written == 0) {
        // 发送缓冲区满，注册 OP_WRITE 等待
        key.attach(resp);
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        break;
    }
}

// ⑦ 向 Selector 注册新 Channel 前先 wakeup()
// （如果 select() 正在阻塞，register() 会阻塞等待）
selector.wakeup();
newChannel.register(selector, SelectionKey.OP_READ);
```

---

## 5.2 Reactor 模式：NIO 的最佳线程模型

`ReactorDemo.java` 演示了三种 Reactor 变体：

```
单线程 Reactor（适合低并发）：
  Reactor 线程：accept + read + decode + business + encode + write
  问题：business 耗时会阻塞后续 I/O 处理

单线程 Reactor + 业务线程池（中等并发）：
  Reactor 线程：accept + read + write（纯 I/O，不阻塞）
  业务线程池：decode + business + encode（耗时任务）
  问题：Reactor 单线程，accept + read 仍是瓶颈

主从多线程 Reactor（高并发，Netty 默认模型）：
  Boss Reactor：只处理 accept
  Worker Reactor（多个）：read + write
  业务线程池：decode + business + encode
```

---

## 5.3 Direct Buffer 的正确用法

```java
// ❌ 错误：每次请求都 allocateDirect
ByteBuffer buf = ByteBuffer.allocateDirect(4096);  // 系统调用，代价高
// 处理完后没有复用 → Direct Buffer 堆外内存泄漏（GC 不主动回收）

// ✅ 正确1：使用 ThreadLocal 复用（单线程模型）
private static final ThreadLocal<ByteBuffer> DIRECT_BUF =
    ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(64 * 1024));

ByteBuffer buf = DIRECT_BUF.get();
buf.clear();  // 重置指针（不清除数据，直接覆盖写）
channel.read(buf);

// ✅ 正确2：对象池（多线程模型，如 Netty 的 PooledByteBufAllocator）

// ✅ 正确3：小数据用 Heap Buffer（分配快，GC 管理，无泄漏风险）
ByteBuffer heap = ByteBuffer.allocate(256);  // 适合小消息
```

---

## 5.4 常见 Bug 速查

| Bug | 症状 | 根本原因 | 解决方案 |
|-----|------|---------|---------|
| 忘记 `remove` selectedKey | 相同事件被重复处理，数据重复 | Selector 不自动清理已处理 key | `it.remove()` 或 `keys.clear()` |
| 忘记 `configureBlocking(false)` | `register()` 抛 `IllegalBlockingModeException` | 阻塞模式 Channel 不能用 Selector | 在 `register()` 前设置 |
| OP_WRITE 常驻注册 | CPU 100% | 发送缓冲区空时 OP_WRITE 永远就绪 | 只在有数据写时注册，写完取消 |
| read() 返回 -1 未处理 | 内存泄漏，fd 耗尽 | 未检测连接关闭 | 返回 -1 时 `key.cancel()` + `close()` |
| write() 未循环 | 大数据包只发了一部分 | write() 可能返回 < 要写的字节数 | `while (buf.hasRemaining())` 循环写 |
| 多线程同时 register | `register()` 与 `select()` 死锁 | 两者都需要 Selector 的锁 | 注册前 `wakeup()`，或在 Reactor 线程中注册 |

---

## 5.5 NIO vs BIO vs Netty 选型

```
推荐场景：
  BIO（线程池）：< 100 并发连接，代码简单优先，内部工具
  NIO（自实现）：100~1000 并发连接，不想引入依赖，对 NIO 熟悉
  Netty：> 1000 并发连接，生产服务，推荐默认选择

不建议手写 NIO 服务器用于生产的原因：
  1. JDK Epoll Bug（空轮询 CPU 100%）需要自行检测和处理
  2. 粘包拆包需要自行实现
  3. SSL/TLS 需要自行集成
  4. 连接管理、心跳检测、重连等都需要自行实现
  Netty 已经解决了以上所有问题，且经过大规模生产验证
```

---

## 5.6 本章总结

**NIO 开发七要点**：
1. `configureBlocking(false)` 在 register 前设置
2. `selectedKeys` 每次处理后必须 remove
3. `OP_WRITE` 只在有数据时注册，写完立即取消
4. `read() == -1` 时 cancel key 并 close channel
5. `write()` 要循环直到 `hasRemaining() == false`
6. 多线程 register 前先 `wakeup()`
7. Direct Buffer 必须复用（ThreadLocal 或对象池）

> **本章对应演示代码**：`ReactorDemo.java`（单线程/多线程 Reactor、生产级 NIO 服务器）、`ZeroCopyDemo.java`（Direct Buffer 最佳实践）

**返回导航**：[README.md](../README.md) | **下一模块**：[AIO 模块](../../aio/docs-v2/README.md)
