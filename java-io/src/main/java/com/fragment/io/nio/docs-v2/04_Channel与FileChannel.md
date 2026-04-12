# 第四章：Channel 与 FileChannel

## 4.1 Channel 类型体系

```
Channel（接口）
  ├── ReadableByteChannel     ← 可读
  ├── WritableByteChannel     ← 可写
  ├── FileChannel             ← 文件读写、内存映射、零拷贝
  ├── SocketChannel           ← TCP 客户端
  ├── ServerSocketChannel     ← TCP 服务端（监听连接）
  ├── DatagramChannel         ← UDP
  └── Pipe.SinkChannel / SourceChannel  ← 进程内管道
```

---

## 4.2 FileChannel：高性能文件 I/O

### 基本读写

```java
// ChannelDemo.java → demonstrateFileChannel()

// 读文件
try (FileChannel fc = FileChannel.open(Paths.get("data.txt"), StandardOpenOption.READ)) {
    ByteBuffer buf = ByteBuffer.allocate(4096);
    while (fc.read(buf) != -1) {
        buf.flip();
        // 处理 buf 中的数据
        buf.clear();
    }
}

// 写文件
try (FileChannel fc = FileChannel.open(Paths.get("out.txt"),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
    ByteBuffer buf = ByteBuffer.wrap("Hello FileChannel\n".getBytes());
    while (buf.hasRemaining()) {
        fc.write(buf);  // write 可能不会一次写完，需要循环
    }
}

// 随机访问（FileChannel 支持，BIO 的 InputStream 不支持）
fc.position(1024);        // 跳到偏移量 1024
fc.read(buf);             // 从 1024 开始读
fc.position(fc.size());   // 跳到文件末尾（追加写）
```

### 内存映射（MappedByteBuffer）

```java
// ChannelDemo.java → demonstrateMappedBuffer()

// 将文件映射到内存，像访问数组一样访问文件
// 底层：mmap() 系统调用，文件数据按需加载到物理内存（缺页中断），无需 read() 系统调用
try (FileChannel fc = FileChannel.open(Paths.get("large.bin"), StandardOpenOption.READ)) {
    MappedByteBuffer mmap = fc.map(
        FileChannel.MapMode.READ_ONLY,  // 只读映射
        0,                              // 从文件开头
        fc.size()                       // 映射整个文件
    );

    // 直接访问，无需 read() 系统调用
    byte firstByte = mmap.get(0);       // 读取偏移量 0 的字节
    int  intValue  = mmap.getInt(100);  // 读取偏移量 100 的 int

    // 适合：频繁随机访问大文件（如数据库文件、日志索引）
    // 注意：映射不会立即加载整个文件，按页（通常 4KB）按需加载
}
```

### transferTo / transferFrom：零拷贝文件传输

```java
// ChannelDemo.java → demonstrateZeroCopyTransfer()
// ZeroCopyDemo.java → demonstrateFileTransfer()

// ❌ 传统方式（两次系统调用 + 两次拷贝）：
try (FileInputStream fis = new FileInputStream("source.bin");
     FileOutputStream fos = new FileOutputStream("dest.bin")) {
    byte[] buf = new byte[8192];
    int n;
    while ((n = fis.read(buf)) != -1) {  // 内核→用户空间
        fos.write(buf, 0, n);             // 用户空间→内核
    }
}

// ✅ FileChannel.transferTo()（一次系统调用，底层 sendfile()）：
try (FileChannel src  = FileChannel.open(Paths.get("source.bin"), StandardOpenOption.READ);
     FileChannel dest = FileChannel.open(Paths.get("dest.bin"),
             StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
    long transferred = 0;
    long size = src.size();
    while (transferred < size) {
        transferred += src.transferTo(transferred, size - transferred, dest);
    }
    // 数据流：磁盘 → 内核缓冲区 → 目标文件（无需经过用户空间！）
}

// 传输到 Socket（HTTP 文件下载场景）：
try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
     SocketChannel socketChannel = (SocketChannel) key.channel()) {
    fileChannel.transferTo(0, fileChannel.size(), socketChannel);
    // 数据流：磁盘 → 内核缓冲区 → Socket 缓冲区 → 网卡（完全绕过用户空间）
}
```

---

## 4.3 Scatter / Gather：分散读与聚集写

```java
// ChannelDemo.java → demonstrateScatterGather()

// 场景：自定义协议 = 固定头（16字节）+ 可变长消息体
// 一次 read 分别填入两个 Buffer，无需手动分割

ByteBuffer header = ByteBuffer.allocate(16);
ByteBuffer body   = ByteBuffer.allocate(4096);

// 分散读（Scatter Read）：一次读，填入多个 Buffer
channel.read(new ByteBuffer[]{header, body});
// header 读满后，自动开始填 body

// 聚集写（Gather Write）：多个 Buffer 一次写出
header.flip();
body.flip();
channel.write(new ByteBuffer[]{header, body});
// 先写 header，再写 body，合并为一次系统调用

// 优势：避免手动从大 Buffer 切割，减少代码量和拷贝
```

---

## 4.4 SocketChannel 客户端连接

```java
// ChannelDemo.java → demonstrateSocketChannelClient()

// 同步连接（阻塞模式）
SocketChannel channel = SocketChannel.open();
channel.connect(new InetSocketAddress("localhost", 8080));  // 阻塞直到连接建立

// 异步连接（非阻塞模式）
SocketChannel channel2 = SocketChannel.open();
channel2.configureBlocking(false);
boolean connected = channel2.connect(new InetSocketAddress("localhost", 8080));
if (!connected) {
    // 连接尚未完成，注册 OP_CONNECT 等待
    channel2.register(selector, SelectionKey.OP_CONNECT);
}

// 处理 OP_CONNECT 事件
if (key.isConnectable()) {
    SocketChannel ch = (SocketChannel) key.channel();
    if (ch.finishConnect()) {  // 完成连接握手
        // 连接建立成功
        key.interestOps(SelectionKey.OP_READ);
    }
}
```

---

## 4.5 本章总结

- **FileChannel**：支持随机访问（`position()`）、内存映射（`mmap`）、零拷贝传输（`transferTo`）
- **MappedByteBuffer**：像访问数组一样访问文件，适合频繁随机读取大文件
- **transferTo**：底层 `sendfile()`，文件→Socket 无需经过用户空间，大文件下载性能关键
- **Scatter/Gather**：一次系统调用读/写多个 Buffer，适合固定头 + 可变体的协议格式
- **非阻塞连接**：客户端 `configureBlocking(false)` 后 `connect()` 可能未完成，需用 `finishConnect()` 确认

> **本章对应演示代码**：`ChannelDemo.java`（FileChannel 读写、内存映射、Scatter/Gather）、`ZeroCopyDemo.java`（transferTo 零拷贝传输）

**继续阅读**：[05_NIO最佳实践.md](./05_NIO最佳实践.md)
