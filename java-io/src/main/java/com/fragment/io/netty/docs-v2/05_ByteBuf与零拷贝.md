# 第五章：ByteBuf 与零拷贝

## 5.1 ByteBuffer 的三大缺陷

JDK 的 `ByteBuffer` 有三个让人头疼的问题：

```java
// 缺陷1：读写切换必须手动 flip()
ByteBuffer buf = ByteBuffer.allocate(1024);
buf.put("Hello".getBytes());   // 写入
// 忘记 flip() → 读到空数据！
buf.flip();                    // 必须手动切换到读模式
byte[] data = new byte[buf.remaining()];
buf.get(data);

// 缺陷2：容量固定，无法扩容
ByteBuffer buf2 = ByteBuffer.allocate(4);
buf2.put("Hello".getBytes());  // 5字节，直接 BufferOverflowException

// 缺陷3：只有一个 position 指针，读写不能同时进行
// 想在读了一部分后继续写入 → 必须调用 compact()，逻辑复杂
```

---

## 5.2 ByteBuf 的优势

`ByteBuf` 用**两个独立指针**（`readerIndex` 和 `writerIndex`）彻底解决上述问题：

```
ByteBuf 内部结构：

0           readerIndex       writerIndex        capacity
│           │                 │                  │
▼           ▼                 ▼                  ▼
┌───────────┬─────────────────┬──────────────────┐
│ 已读区域   │  可读区域        │   可写区域         │
│（可丢弃）  │ readableBytes() │  writableBytes()  │
└───────────┴─────────────────┴──────────────────┘

写数据：writerIndex 向右移动，readerIndex 不动
读数据：readerIndex 向右移动，writerIndex 不动
完全不需要 flip()！
```

```java
// ByteBufDemo.java → demonstrateBasicOperations()

ByteBuf buf = Unpooled.buffer(16);

// 写入数据（writerIndex 增加）
buf.writeInt(100);        // writerIndex: 0 → 4
buf.writeBytes("Hello".getBytes()); // writerIndex: 4 → 9

// 读取数据（readerIndex 增加，不影响 writerIndex）
int num = buf.readInt();                     // readerIndex: 0 → 4，num = 100
byte[] bytes = new byte[buf.readableBytes()]; // 可读 = writerIndex(9) - readerIndex(4) = 5
buf.readBytes(bytes);                        // "Hello"

// 继续写入（不需要 flip，直接写）
buf.writeBytes(" World".getBytes()); // writerIndex: 9 → 15
```

---

## 5.3 ByteBuf 的三种类型

```
按内存位置分：
  HeapByteBuf（堆内存）
    → 在 JVM 堆上分配，受 GC 管理
    → 读写时需要拷贝到 OS 缓冲区，有一次额外拷贝
    → 适合：消息体较小，对象生命周期短的场景

  DirectByteBuf（直接内存 / 堆外内存）
    → 在操作系统内存上分配，不受 GC 管理
    → 直接与 Socket 交互，省去一次堆内存→OS 的拷贝（零拷贝的基础）
    → 分配/释放代价高，需要对象池
    → 适合：网络 I/O 的最终读写缓冲区

  CompositeByteBuf（复合缓冲区）
    → 将多个 ByteBuf 组合为一个逻辑视图，不复制数据（零拷贝）
    → 见 5.4 节

按是否池化分：
  PooledByteBuf（池化，默认）
    → 对象池管理，复用内存，大幅减少 GC 和内存碎片
    → Netty 默认使用池化直接内存（PooledDirectByteBuf）

  UnpooledByteBuf（非池化）
    → 每次 alloc/release 都申请/释放内存
    → 适合少量临时数据
```

---

## 5.4 零拷贝：三种实现方式

### slice()：切片，不复制数据

```java
// ByteBufDemo.java → demonstrateZeroCopySlice()

ByteBuf original = Unpooled.copiedBuffer("Hello World Netty", CharsetUtil.UTF_8);

// 切出 "Hello"，底层共享 original 的内存
ByteBuf hello = original.slice(0, 5);
// 切出 "World"
ByteBuf world = original.slice(6, 5);

// 修改 hello 的内容 → original 也被修改（共享同一块内存）
hello.setByte(0, 'h');
System.out.println(original.toString(CharsetUtil.UTF_8));  // "hello World Netty"

// 注意：slice 不会增加引用计数！
// 如果 original 被 release，hello 和 world 变成悬挂引用
// 需要先 hello.retain() 来保持引用
```

### CompositeByteBuf：合并不复制

```java
// ByteBufDemo.java → demonstrateZeroCopyComposite()

// 场景：HTTP 响应 = 固定头 + 动态消息体，不需要把两块内存合并为一块

ByteBuf header = Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.UTF_8);
ByteBuf body   = Unpooled.copiedBuffer("{\"code\":200}", CharsetUtil.UTF_8);

// ❌ 传统做法：复制两次内存
ByteBuf merged = Unpooled.buffer(header.readableBytes() + body.readableBytes());
merged.writeBytes(header);
merged.writeBytes(body);

// ✅ 零拷贝：CompositeByteBuf 只持有两个缓冲区的引用，不复制数据
CompositeByteBuf composite = Unpooled.compositeBuffer();
composite.addComponents(true, header, body);  // true = 自动调整 writerIndex
System.out.println(composite.readableBytes()); // header + body 的总长度
```

### FileRegion：文件零拷贝传输

```java
// ByteBufDemo.java → demonstrateFileRegionTransfer()

// 利用操作系统的 sendfile() 系统调用，文件数据直接从磁盘→Socket，不经过 JVM 堆
// HTTP 文件服务器传输大文件时性能关键

RandomAccessFile file = new RandomAccessFile("/path/to/file.mp4", "r");
FileRegion region = new DefaultFileRegion(file.getChannel(), 0, file.length());
ctx.writeAndFlush(region).addListener(future -> file.close());

// 传统方式（有额外拷贝）：
// 磁盘 → 内核缓冲区 → 用户空间（JVM 堆） → 内核 Socket 缓冲区 → 网卡
// FileRegion 方式（零拷贝）：
// 磁盘 → 内核缓冲区 → 内核 Socket 缓冲区 → 网卡（完全绕过用户空间）
```

---

## 5.5 引用计数与内存泄漏

Netty 用**引用计数**管理 `ByteBuf` 的内存（特别是堆外内存）：

```java
// ByteBuf 创建时引用计数 = 1
ByteBuf buf = ctx.alloc().buffer();  // refCnt = 1

buf.retain();   // refCnt = 2（主动声明"我还要用它"）
buf.release();  // refCnt = 1（我用完了一次）
buf.release();  // refCnt = 0 → 内存归还到对象池（或释放堆外内存）

// ❌ 常见内存泄漏：忘记 release()
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    // ... 处理 buf ...
    // 忘记 buf.release() → 堆外内存永远不释放 → Direct buffer memory OOM
}

// ✅ 使用 SimpleChannelInboundHandler（自动 release）
// 或者手动 try-finally
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    try {
        // ... 处理 buf ...
    } finally {
        buf.release();  // 确保释放
    }
}

// ✅ 如果把 buf 传给下一个 Handler（fireChannelRead），不要 release
// 责任转移给下一个 Handler
ctx.fireChannelRead(buf);  // 注意：不要在此之后再 release buf
```

**开启泄漏检测**：
```bash
# 开发环境：-Dio.netty.leakDetection.level=advanced（详细报告，性能有影响）
# 生产环境：-Dio.netty.leakDetection.level=simple（每 128 次采样一次，默认）
java -Dio.netty.leakDetection.level=advanced -jar app.jar
```

---

## 5.6 ByteBuf 常用 API 速查

```java
ByteBuf buf = ctx.alloc().ioBuffer(256);  // 分配适合 I/O 的缓冲区（通常是 Direct）

// 写入
buf.writeByte(0xFF);
buf.writeShort(1000);
buf.writeInt(Integer.MAX_VALUE);
buf.writeLong(System.currentTimeMillis());
buf.writeBytes("Hello".getBytes());

// 读取（移动 readerIndex）
byte  b = buf.readByte();
short s = buf.readShort();
int   i = buf.readInt();

// 随机访问（不移动 readerIndex）
int peek = buf.getInt(0);  // 从偏移量0读取，不移动 readerIndex

// 查询
buf.readableBytes();  // 可读字节数 = writerIndex - readerIndex
buf.writableBytes();  // 可写字节数 = capacity - writerIndex
buf.capacity();       // 当前容量
buf.maxCapacity();    // 最大容量（自动扩容上限）

// 标记与重置
buf.markReaderIndex();
buf.resetReaderIndex();  // 回退到上一次 mark 的位置

// 释放
buf.release();
ReferenceCountUtil.release(msg);  // 通用工具，对非 ByteBuf 对象也安全
```

---

## 5.7 本章总结

- **ByteBuf 优于 ByteBuffer**：双指针（无需 flip）、动态扩容、零拷贝操作
- **三种类型**：Heap（GC 管理）、Direct（堆外，高效 I/O）、Composite（逻辑合并，不复制）
- **默认 PooledDirect**：池化直接内存，减少 GC 压力和内存分配开销
- **三种零拷贝**：`slice()`（切片共享）、`CompositeByteBuf`（逻辑合并）、`FileRegion`（sendfile 系统调用）
- **引用计数**：`retain()` + `release()` 管理生命周期；用 `SimpleChannelInboundHandler` 自动释放；开启泄漏检测辅助排查

> **本章对应演示代码**：`ByteBufDemo.java`（双指针读写、slice/composite 零拷贝、引用计数、泄漏检测）

**继续阅读**：[06_生产实践与最佳实践.md](./06_生产实践与最佳实践.md)
