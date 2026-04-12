# 第三章：Selector 与多路复用

## 3.1 epoll：Selector 的底层原理

Java Selector 在 Linux 上基于 `epoll`，理解 epoll 才能理解 Selector 的性能：

```
select（旧，O(n)）：
  传入 fd 集合 → 内核遍历所有 fd 检查就绪状态 → 返回就绪 fd 数量
  每次调用都要：用户空间 → 内核空间拷贝全部 fd，O(n) 遍历
  上限：1024 个 fd

poll（稍好，O(n)）：
  去掉了 1024 限制，但仍然 O(n) 遍历

epoll（现代，O(1)）：
  epoll_create()  → 创建事件表（内核维护一棵红黑树）
  epoll_ctl()     → 注册/修改/删除 fd（O(logN)）
  epoll_wait()    → 阻塞等待，仅返回就绪的 fd（内核维护就绪链表，O(1)）

关键优势：
  1. 无 fd 数量限制（受系统 open files 限制，通常几十万）
  2. 就绪通知 O(1)：不需要遍历所有 fd，只看就绪链表
  3. 无每次拷贝开销：fd 在内核常驻，用户空间不需要每次传入
```

---

## 3.2 SelectionKey 的四种事件

```java
// SelectorDemo.java → demonstrateSelectionKeys()

// 注册时指定感兴趣的事件（可以用 | 组合多个）
channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

// 四种事件：
SelectionKey.OP_ACCEPT  = 16  // ServerSocketChannel：有新连接到来
SelectionKey.OP_CONNECT = 8   // SocketChannel（客户端）：连接建立完成
SelectionKey.OP_READ    = 1   // SocketChannel：有数据可读
SelectionKey.OP_WRITE   = 4   // SocketChannel：写缓冲区可写

// ⚠️ 关于 OP_WRITE 的陷阱：
// 只要 Socket 发送缓冲区未满，OP_WRITE 就一直就绪
// 如果一直注册 OP_WRITE，select() 会一直返回，CPU 100%！

// ✅ 正确用法：只在有数据要写时才注册 OP_WRITE，写完立即取消
// 需要写数据时：
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
// 写完数据后：
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

---

## 3.3 SelectionKey 的附加数据（Attachment）

```java
// SelectorDemo.java → demonstrateAttachment()
// 将每个 Channel 的状态附加到 SelectionKey 上

// 定义连接状态对象
static class ConnectionState {
    ByteBuffer readBuffer  = ByteBuffer.allocate(4096);
    ByteBuffer writeBuffer = null;
    int messageCount = 0;
}

// 注册时附加状态
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
key.attach(new ConnectionState());  // 附加状态对象

// 处理时取出状态
ConnectionState state = (ConnectionState) key.attachment();
state.messageCount++;
int n = channel.read(state.readBuffer);

// 优势：避免了用 HashMap<Channel, State> 来维护连接状态
// 直接从 SelectionKey 取，无需 hash 查找，性能更好
```

---

## 3.4 select()、selectNow()、select(timeout) 的区别

```java
// SelectorDemo.java → demonstrateSelectModes()

// select()：阻塞直到至少有一个 Channel 就绪
int readyCount = selector.select();

// select(timeout)：最多阻塞 timeout 毫秒，超时返回 0
int readyCount2 = selector.select(1000);

// selectNow()：非阻塞，立即返回（无论是否有就绪 Channel）
int readyCount3 = selector.selectNow();

// wakeup()：让阻塞中的 select() 立即返回
// 场景：另一个线程想往 selector 注册新 Channel（需要先唤醒 select）
selector.wakeup();

// 典型事件循环写法：
while (!Thread.currentThread().isInterrupted()) {
    if (selector.select(100) == 0) {
        // 100ms 内无事件，执行其他定时任务（如心跳检查）
        checkIdleConnections();
        continue;
    }
    Set<SelectionKey> keys = selector.selectedKeys();
    // ... 处理就绪事件 ...
    keys.clear();  // ✅ 等价于逐个 it.remove()，更简洁
}
```

---

## 3.5 完整 NIO 服务器实现要点

```java
// SelectorDemo.java → FullNioServer（完整实现）

// 要点1：selectedKeys 必须手动移除（否则下次重复处理）
Iterator<SelectionKey> it = selector.selectedKeys().iterator();
while (it.hasNext()) {
    SelectionKey key = it.next();
    it.remove();  // ❗必须！
    // 或事后 selector.selectedKeys().clear();
}

// 要点2：read() 返回 -1 时关闭连接
int n = channel.read(buf);
if (n == -1) {
    key.cancel();     // 从 selector 注销
    channel.close();  // 关闭 channel（自动关闭底层 socket）
}

// 要点3：OP_WRITE 不要一直注册（见 3.2 节）

// 要点4：异常时取消 key，防止 selector 反复报错
try {
    // 处理 key
} catch (IOException e) {
    key.cancel();
    key.channel().close();
}

// 要点5：Selector 注册必须在 configureBlocking(false) 之后
channel.configureBlocking(false);  // 必须先设置非阻塞
channel.register(selector, SelectionKey.OP_READ);
```

---

## 3.6 本章总结

- **epoll 的优势**：O(1) 就绪通知 + 无 fd 数量上限 + 无每次 fd 拷贝，是高性能 Selector 的基础
- **OP_WRITE 陷阱**：发送缓冲区未满时永远就绪，只在有数据要写时注册，写完立即取消
- **Attachment**：用 `key.attach()` 把连接状态挂在 key 上，避免额外 HashMap 查找
- **三种 select()**：`select()`（阻塞）、`select(timeout)`（超时）、`selectNow()`（非阻塞）
- **两个必须**：`selectedKeys` 处理后必须 remove；Channel 注册前必须 `configureBlocking(false)`

> **本章对应演示代码**：`SelectorDemo.java`（epoll 工作原理、SelectionKey 四种事件、OP_WRITE 正确用法、完整 NIO 服务器）

**继续阅读**：[04_Channel与FileChannel.md](./04_Channel与FileChannel.md)
