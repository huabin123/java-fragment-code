# 05_ByteBuf与内存管理

> **核心问题**：ByteBuf比ByteBuffer好在哪里？引用计数如何避免内存泄漏？Netty的零拷贝体现在哪些方面？

---

## 一、ByteBuf vs ByteBuffer

### 1.1 ByteBuffer的问题

**问题1：需要手动flip()**
```java
// JDK ByteBuffer
ByteBuffer buffer = ByteBuffer.allocate(1024);
buffer.put("Hello".getBytes());
buffer.flip();  // 必须手动flip，容易忘记
byte[] data = new byte[buffer.remaining()];
buffer.get(data);
```

**问题2：容量固定，无法动态扩展**
```java
ByteBuffer buffer = ByteBuffer.allocate(10);
buffer.put("Hello".getBytes());  // 5字节，OK
buffer.put("World!".getBytes()); // 6字节，BufferOverflowException
```

**问题3：只有一个position指针**
```java
ByteBuffer buffer = ByteBuffer.allocate(1024);
buffer.put("Hello".getBytes());
buffer.flip();
buffer.get();  // 读取'H'
// 如果想继续写入，需要compact()或clear()，操作复杂
```

### 1.2 ByteBuf的优势

**优势1：双指针，不需要flip()**
```java
// Netty ByteBuf
ByteBuf buffer = Unpooled.buffer(1024);
buffer.writeBytes("Hello".getBytes());  // writerIndex自动增加
byte[] data = new byte[buffer.readableBytes()];
buffer.readBytes(data);  // readerIndex自动增加
// 不需要flip()
```

**优势2：容量可动态扩展**
```java
ByteBuf buffer = Unpooled.buffer(10);
buffer.writeBytes("Hello".getBytes());   // 5字节
buffer.writeBytes("World!".getBytes());  // 6字节
// 自动扩容，不会抛出异常
```

**优势3：零拷贝操作**
```java
// slice：切片，不拷贝数据
ByteBuf buffer = Unpooled.buffer(100);
buffer.writeBytes("Hello World".getBytes());
ByteBuf slice = buffer.slice(0, 5);  // "Hello"，零拷贝

// duplicate：复制，不拷贝数据
ByteBuf duplicate = buffer.duplicate();  // 共享数据，独立指针

// composite：组合，不拷贝数据
ByteBuf header = Unpooled.buffer().writeBytes("Header".getBytes());
ByteBuf body = Unpooled.buffer().writeBytes("Body".getBytes());
CompositeByteBuf composite = Unpooled.compositeBuffer();
composite.addComponents(true, header, body);  // 逻辑组合，零拷贝
```

**优势4：引用计数，自动管理内存**
```java
ByteBuf buffer = ctx.alloc().buffer();
buffer.retain();   // 引用计数+1
buffer.release();  // 引用计数-1
buffer.release();  // 引用计数=0，自动释放内存
```

**优势5：池化，减少GC**
```java
// 从池中获取ByteBuf
ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer();
try {
    // 使用buffer
} finally {
    buffer.release();  // 归还到池
}
```

---

## 二、ByteBuf的结构

### 2.1 ByteBuf的内部结构

```
ByteBuf的内部结构：
┌───────────────────────────────────────────────────────────┐
│                      ByteBuf                              │
│                                                           │
│  ┌─────────────┬─────────────────┬──────────────────┐    │
│  │  discarded  │    readable     │    writable      │    │
│  │   bytes     │     bytes       │     bytes        │    │
│  └─────────────┴─────────────────┴──────────────────┘    │
│  0         readerIndex       writerIndex        capacity  │
│                                                           │
│  - discarded bytes: 已读取的字节，可以通过discardReadBytes()丢弃 │
│  - readable bytes: 可读取的字节，长度=writerIndex-readerIndex │
│  - writable bytes: 可写入的字节，长度=capacity-writerIndex    │
└───────────────────────────────────────────────────────────┘
```

### 2.2 核心指针

**readerIndex（读指针）**：
- 标记下一个要读取的字节位置
- 初始值为0
- 调用readXxx()方法会增加readerIndex

**writerIndex（写指针）**：
- 标记下一个要写入的字节位置
- 初始值为0
- 调用writeXxx()方法会增加writerIndex

**capacity（容量）**：
- ByteBuf的总容量
- 可以通过capacity()方法动态调整

**maxCapacity（最大容量）**：
- ByteBuf的最大容量
- 超过会抛出异常

### 2.3 核心方法

#### 2.3.1 读操作

```java
ByteBuf buffer = Unpooled.buffer();
buffer.writeBytes("Hello World".getBytes());

// 读取单个字节
byte b = buffer.readByte();  // 'H'，readerIndex+1

// 读取多个字节
byte[] data = new byte[5];
buffer.readBytes(data);  // "ello"，readerIndex+5

// 读取但不移动指针
byte b2 = buffer.getByte(0);  // 'H'，readerIndex不变

// 检查可读字节数
int readable = buffer.readableBytes();  // writerIndex - readerIndex

// 检查是否可读
boolean isReadable = buffer.isReadable();  // readerIndex < writerIndex
```

#### 2.3.2 写操作

```java
ByteBuf buffer = Unpooled.buffer();

// 写入单个字节
buffer.writeByte('H');  // writerIndex+1

// 写入多个字节
buffer.writeBytes("ello".getBytes());  // writerIndex+4

// 写入但不移动指针
buffer.setByte(0, 'h');  // writerIndex不变

// 检查可写字节数
int writable = buffer.writableBytes();  // capacity - writerIndex

// 检查是否可写
boolean isWritable = buffer.isWritable();  // writerIndex < capacity
```

#### 2.3.3 指针操作

```java
ByteBuf buffer = Unpooled.buffer();
buffer.writeBytes("Hello World".getBytes());

// 标记读指针
buffer.markReaderIndex();
buffer.readBytes(new byte[5]);  // 读取5字节

// 重置读指针
buffer.resetReaderIndex();  // 回到标记位置

// 标记写指针
buffer.markWriterIndex();
buffer.writeBytes("!!!".getBytes());

// 重置写指针
buffer.resetWriterIndex();  // 回到标记位置

// 清空（重置指针，不清除数据）
buffer.clear();  // readerIndex=0, writerIndex=0

// 丢弃已读字节
buffer.discardReadBytes();  // 将未读数据移到开头，readerIndex=0
```

---

## 三、ByteBuf的类型

### 3.1 按内存分配方式分类

#### 3.1.1 HeapByteBuf（堆内存）

**特点**：
- 数据存储在JVM堆内存
- 分配和释放快
- 可以直接访问底层数组

**创建**：
```java
ByteBuf buffer = Unpooled.buffer(1024);  // 默认是HeapByteBuf
```

**优点**：
- ✅ 分配快速
- ✅ 可以直接访问数组：`buffer.array()`
- ✅ 不需要额外的内存管理

**缺点**：
- ❌ I/O操作需要拷贝到直接内存
- ❌ GC压力大

**适用场景**：
- 内存操作多，I/O操作少
- 需要频繁访问底层数组

#### 3.1.2 DirectByteBuf（直接内存）

**特点**：
- 数据存储在JVM堆外内存
- 分配和释放慢
- I/O操作零拷贝

**创建**：
```java
ByteBuf buffer = Unpooled.directBuffer(1024);
```

**优点**：
- ✅ I/O操作零拷贝（不需要从堆拷贝到直接内存）
- ✅ 不占用JVM堆内存
- ✅ 不受GC影响

**缺点**：
- ❌ 分配和释放慢
- ❌ 不能直接访问数组
- ❌ 需要手动管理内存

**适用场景**：
- I/O操作多，内存操作少
- 大文件传输
- 高性能网络通信

**对比**：
```java
// HeapByteBuf的I/O操作
HeapByteBuf heapBuf = ...;
channel.write(heapBuf);
// 内部流程：
// 1. 分配DirectByteBuf
// 2. 拷贝数据：HeapByteBuf → DirectByteBuf
// 3. 写入Socket：DirectByteBuf → 内核缓冲区
// 4. 释放DirectByteBuf

// DirectByteBuf的I/O操作
DirectByteBuf directBuf = ...;
channel.write(directBuf);
// 内部流程：
// 1. 写入Socket：DirectByteBuf → 内核缓冲区（零拷贝）
```

### 3.2 按是否池化分类

#### 3.2.1 UnpooledByteBuf（非池化）

**特点**：
- 每次都分配新的内存
- 使用完后由GC回收

**创建**：
```java
ByteBuf buffer = Unpooled.buffer(1024);
```

**优点**：
- ✅ 简单，不需要手动释放
- ✅ 适合小对象

**缺点**：
- ❌ 频繁分配导致性能下降
- ❌ GC压力大

#### 3.2.2 PooledByteBuf（池化）

**特点**：
- 从内存池中获取
- 使用完后归还到池

**创建**：
```java
ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(1024);
```

**优点**：
- ✅ 减少内存分配
- ✅ 减少GC
- ✅ 提高性能

**缺点**：
- ❌ 必须手动释放（调用release()）
- ❌ 内存泄漏风险

**Netty 4.1+默认使用池化**：
```java
// 默认分配器
ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;

// 在Bootstrap中配置
bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
```

---

## 四、引用计数机制

### 4.1 为什么需要引用计数？

**问题**：直接内存不受GC管理，如何释放？

```java
// 问题场景
ByteBuf buffer = ctx.alloc().directBuffer(1024);
// 使用buffer
// 忘记释放，导致内存泄漏
```

**解决方案**：引用计数
```java
ByteBuf buffer = ctx.alloc().buffer();  // refCnt=1
buffer.retain();   // refCnt=2
buffer.release();  // refCnt=1
buffer.release();  // refCnt=0，自动释放内存
```

### 4.2 引用计数的工作原理

```
引用计数的生命周期：
┌─────────────────────────────────────────────────────────┐
│ 1. 创建ByteBuf                                          │
│    ByteBuf buf = ctx.alloc().buffer();                  │
│    refCnt = 1                                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. 增加引用                                             │
│    buf.retain();                                        │
│    refCnt = 2                                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 释放引用                                             │
│    buf.release();                                       │
│    refCnt = 1                                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 最后释放                                             │
│    buf.release();                                       │
│    refCnt = 0，释放内存                                  │
└─────────────────────────────────────────────────────────┘
```

### 4.3 引用计数的规则

**规则1：谁创建，谁释放**
```java
public void method1() {
    ByteBuf buf = ctx.alloc().buffer();  // 创建
    try {
        // 使用buf
    } finally {
        buf.release();  // 释放
    }
}
```

**规则2：谁持有，谁释放**
```java
public void method2(ByteBuf buf) {
    buf.retain();  // 持有
    try {
        // 使用buf
    } finally {
        buf.release();  // 释放
    }
}
```

**规则3：传递给下一个Handler，不释放**
```java
public class Handler1 extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        // 处理数据
        processData(buf);
        
        // 传递给下一个Handler，不释放
        ctx.fireChannelRead(msg);
    }
}
```

**规则4：不传递，必须释放**
```java
public class Handler2 extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            // 处理数据
            processData(buf);
            // 不传递给下一个Handler
        } finally {
            buf.release();  // 必须释放
        }
    }
}
```

### 4.4 内存泄漏检测

**启用内存泄漏检测**：
```java
// 方式1：JVM参数
-Dio.netty.leakDetection.level=ADVANCED

// 方式2：代码设置
ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
```

**检测级别**：
```java
// DISABLED：禁用检测
ResourceLeakDetector.Level.DISABLED

// SIMPLE：默认，抽样检测1%，性能影响小
ResourceLeakDetector.Level.SIMPLE

// ADVANCED：抽样检测1%，提供详细的泄漏信息
ResourceLeakDetector.Level.ADVANCED

// PARANOID：检测所有对象，性能影响大，仅用于调试
ResourceLeakDetector.Level.PARANOID
```

**泄漏检测输出**：
```
LEAK: ByteBuf.release() was not called before it's garbage-collected.
See http://netty.io/wiki/reference-counted-objects.html for more information.
Recent access records:
#1:
	io.netty.buffer.AdvancedLeakAwareByteBuf.writeBytes(AdvancedLeakAwareByteBuf.java:589)
	com.example.MyHandler.channelRead(MyHandler.java:25)
	io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:362)
```

### 4.5 避免内存泄漏的最佳实践

**实践1：使用try-finally**
```java
ByteBuf buf = ctx.alloc().buffer();
try {
    // 使用buf
} finally {
    buf.release();  // 确保释放
}
```

**实践2：使用SimpleChannelInboundHandler**
```java
// 自动释放msg
public class MyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // 处理数据
        processData(msg);
        // 框架自动释放msg
    }
}
```

**实践3：使用ReferenceCountUtil**
```java
import io.netty.util.ReferenceCountUtil;

public void method(Object msg) {
    try {
        // 处理消息
    } finally {
        ReferenceCountUtil.release(msg);  // 安全释放
    }
}
```

**实践4：启用内存泄漏检测**
```java
// 开发环境：PARANOID
-Dio.netty.leakDetection.level=PARANOID

// 测试环境：ADVANCED
-Dio.netty.leakDetection.level=ADVANCED

// 生产环境：SIMPLE
-Dio.netty.leakDetection.level=SIMPLE
```

---

## 五、Netty的零拷贝

### 5.1 什么是零拷贝？

**传统拷贝**：
```
传统文件传输（4次拷贝，4次上下文切换）：
┌─────────┐                                    ┌─────────┐
│  磁盘   │                                    │  网卡   │
└─────────┘                                    └─────────┘
     ↓ 1. DMA拷贝                                   ↑ 4. DMA拷贝
┌─────────────────┐                        ┌─────────────────┐
│  内核缓冲区      │                        │  Socket缓冲区    │
└─────────────────┘                        └─────────────────┘
     ↓ 2. CPU拷贝                                ↑ 3. CPU拷贝
┌─────────────────┐                        ┌─────────────────┐
│  应用缓冲区      │ ────────────────────→  │  应用缓冲区      │
└─────────────────┘                        └─────────────────┘
```

**零拷贝**：
```
零拷贝文件传输（2次拷贝，2次上下文切换）：
┌─────────┐                                    ┌─────────┐
│  磁盘   │                                    │  网卡   │
└─────────┘                                    └─────────┘
     ↓ 1. DMA拷贝                                   ↑ 2. DMA拷贝
┌─────────────────┐                        ┌─────────────────┐
│  内核缓冲区      │ ────────────────────→  │  Socket缓冲区    │
└─────────────────┘                        └─────────────────┘
                    （不经过应用缓冲区）
```

### 5.2 Netty的零拷贝实现

#### 5.2.1 CompositeByteBuf（组合缓冲区）

**问题**：合并多个ByteBuf需要拷贝
```java
// 传统方式：需要拷贝
ByteBuf header = ...;
ByteBuf body = ...;
ByteBuf combined = Unpooled.buffer(header.readableBytes() + body.readableBytes());
combined.writeBytes(header);  // 拷贝1
combined.writeBytes(body);    // 拷贝2
```

**解决方案**：CompositeByteBuf逻辑组合，零拷贝
```java
CompositeByteBuf composite = Unpooled.compositeBuffer();
composite.addComponents(true, header, body);  // 零拷贝，逻辑组合
// header和body的数据不会被拷贝，只是逻辑上组合在一起
```

**工作原理**：
```
CompositeByteBuf的结构：
┌────────────────────────────────────────────┐
│         CompositeByteBuf                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │Component1│  │Component2│  │Component3│ │
│  │(header)  │  │(body)    │  │(footer)  │ │
│  └──────────┘  └──────────┘  └──────────┘ │
│       ↓              ↓              ↓      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ ByteBuf1 │  │ ByteBuf2 │  │ ByteBuf3 │ │
│  └──────────┘  └──────────┘  └──────────┘ │
└────────────────────────────────────────────┘
（数据不拷贝，只是逻辑组合）
```

#### 5.2.2 slice（切片）

**问题**：提取ByteBuf的一部分需要拷贝
```java
// 传统方式：需要拷贝
ByteBuf buffer = ...;
ByteBuf part = Unpooled.buffer(5);
buffer.readBytes(part, 5);  // 拷贝5字节
```

**解决方案**：slice零拷贝
```java
ByteBuf buffer = Unpooled.buffer();
buffer.writeBytes("Hello World".getBytes());

ByteBuf slice = buffer.slice(0, 5);  // "Hello"，零拷贝
// slice和buffer共享底层数据，修改slice会影响buffer
```

**注意事项**：
```java
ByteBuf buffer = Unpooled.buffer();
buffer.writeBytes("Hello World".getBytes());

ByteBuf slice = buffer.slice(0, 5);
slice.setByte(0, 'h');  // 修改slice

System.out.println(buffer.toString(CharsetUtil.UTF_8));  // "hello World"
// buffer也被修改了，因为共享数据
```

#### 5.2.3 wrap（包装）

**问题**：将byte[]转换为ByteBuf需要拷贝
```java
// 传统方式：需要拷贝
byte[] data = "Hello".getBytes();
ByteBuf buffer = Unpooled.buffer(data.length);
buffer.writeBytes(data);  // 拷贝数据
```

**解决方案**：wrap零拷贝
```java
byte[] data = "Hello".getBytes();
ByteBuf buffer = Unpooled.wrappedBuffer(data);  // 零拷贝，直接包装
// buffer和data共享数据
```

#### 5.2.4 FileRegion（文件传输）

**问题**：传统文件传输需要多次拷贝
```java
// 传统方式：4次拷贝
FileInputStream in = new FileInputStream("file.txt");
byte[] buffer = new byte[4096];
int read;
while ((read = in.read(buffer)) != -1) {
    channel.write(ByteBuffer.wrap(buffer, 0, read));
}
```

**解决方案**：FileRegion零拷贝
```java
// 使用FileRegion：2次拷贝（使用sendfile系统调用）
FileChannel fileChannel = new FileInputStream("file.txt").getChannel();
DefaultFileRegion fileRegion = new DefaultFileRegion(fileChannel, 0, fileChannel.size());
channel.writeAndFlush(fileRegion);
```

**工作原理**：
```
FileRegion使用sendfile系统调用：
┌─────────┐                    ┌─────────┐
│  磁盘   │ ──DMA拷贝──→       │  网卡   │
└─────────┘                    └─────────┘
     ↓                              ↑
┌─────────────────┐          ┌─────────────────┐
│  内核缓冲区      │ ──────→  │  Socket缓冲区    │
└─────────────────┘          └─────────────────┘
（不经过应用层，零拷贝）
```

---

## 六、ByteBuf最佳实践

### 6.1 选择合适的ByteBuf类型

**场景1：频繁的I/O操作**
```java
// 使用DirectByteBuf
ByteBuf buffer = ctx.alloc().directBuffer(1024);
```

**场景2：频繁的内存操作**
```java
// 使用HeapByteBuf
ByteBuf buffer = ctx.alloc().heapBuffer(1024);
```

**场景3：高性能场景**
```java
// 使用PooledByteBuf
ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(1024);
```

### 6.2 正确释放ByteBuf

**模式1：try-finally**
```java
ByteBuf buf = ctx.alloc().buffer();
try {
    // 使用buf
} finally {
    buf.release();
}
```

**模式2：SimpleChannelInboundHandler**
```java
public class MyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // 使用msg，框架自动释放
    }
}
```

**模式3：ReferenceCountUtil**
```java
public void method(Object msg) {
    try {
        // 处理消息
    } finally {
        ReferenceCountUtil.release(msg);
    }
}
```

### 6.3 避免内存拷贝

**使用CompositeByteBuf**：
```java
// ❌ 错误：拷贝数据
ByteBuf combined = Unpooled.buffer();
combined.writeBytes(header);
combined.writeBytes(body);

// ✅ 正确：零拷贝
CompositeByteBuf composite = Unpooled.compositeBuffer();
composite.addComponents(true, header, body);
```

**使用slice**：
```java
// ❌ 错误：拷贝数据
ByteBuf part = Unpooled.buffer(5);
buffer.readBytes(part, 5);

// ✅ 正确：零拷贝
ByteBuf slice = buffer.slice(0, 5);
```

**使用wrap**：
```java
// ❌ 错误：拷贝数据
ByteBuf buffer = Unpooled.buffer(data.length);
buffer.writeBytes(data);

// ✅ 正确：零拷贝
ByteBuf buffer = Unpooled.wrappedBuffer(data);
```

### 6.4 性能优化

**预估容量**：
```java
// ❌ 错误：频繁扩容
ByteBuf buffer = Unpooled.buffer(10);
for (int i = 0; i < 1000; i++) {
    buffer.writeInt(i);  // 频繁扩容
}

// ✅ 正确：预估容量
ByteBuf buffer = Unpooled.buffer(4000);  // 1000 * 4字节
for (int i = 0; i < 1000; i++) {
    buffer.writeInt(i);  // 不需要扩容
}
```

**使用池化**：
```java
// ❌ 错误：频繁分配
for (int i = 0; i < 10000; i++) {
    ByteBuf buffer = Unpooled.buffer(1024);
    // 使用buffer
    buffer.release();
}

// ✅ 正确：使用池化
ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
for (int i = 0; i < 10000; i++) {
    ByteBuf buffer = allocator.buffer(1024);
    // 使用buffer
    buffer.release();  // 归还到池
}
```

---

## 七、核心问题总结

### Q1：ByteBuf比ByteBuffer好在哪里？

**答**：
1. **双指针**：不需要flip()
2. **动态扩容**：容量可以自动扩展
3. **零拷贝**：slice、duplicate、composite等操作
4. **引用计数**：自动管理内存
5. **池化**：减少GC

### Q2：引用计数如何避免内存泄漏？

**答**：
1. **创建时refCnt=1**
2. **retain()增加引用**
3. **release()减少引用**
4. **refCnt=0时自动释放**
5. **规则**：谁最后使用，谁负责释放

### Q3：Netty的零拷贝体现在哪些方面？

**答**：
1. **CompositeByteBuf**：逻辑组合，不拷贝数据
2. **slice**：切片，共享数据
3. **wrap**：包装，共享数据
4. **FileRegion**：使用sendfile，减少拷贝

### Q4：如何选择ByteBuf类型？

**答**：
- **I/O密集**：DirectByteBuf
- **内存密集**：HeapByteBuf
- **高性能**：PooledByteBuf

### Q5：如何避免内存泄漏？

**答**：
1. **使用try-finally**
2. **使用SimpleChannelInboundHandler**
3. **启用内存泄漏检测**
4. **遵循释放规则**

---

## 八、下一步学习

在掌握了ByteBuf和内存管理后，下一章我们将学习：

**第6章：高级特性**
- 心跳检测
- 空闲检测
- 流量整形
- SSL/TLS支持

**实践任务**：
1. 对比ByteBuf和ByteBuffer的性能
2. 实现一个使用CompositeByteBuf的编解码器
3. 测试内存泄漏检测

---

**继续学习**：[06_高级特性](./06_高级特性.md)
