# 第4章：零拷贝与Reactor模式深度剖析

> **本章目标**：深入理解零拷贝技术的实现原理、掌握Reactor模式的三种形态、为学习Netty打下基础

---

## 一、零拷贝技术深度解析

### 1.1 问题：什么是零拷贝？为什么需要零拷贝？

#### 传统I/O的数据拷贝过程：

```
场景：从文件读取数据并通过Socket发送

传统方式（read + write）：
┌─────────────────────────────────────────────────┐
│                                                 │
│  应用程序代码：                                  │
│  byte[] buffer = new byte[4096];                │
│  int len = fileInputStream.read(buffer);        │
│  socketOutputStream.write(buffer, 0, len);      │
│                                                 │
└─────────────────────────────────────────────────┘

数据流转过程（4次拷贝 + 4次上下文切换）：

用户空间                    内核空间
┌──────────┐              ┌──────────┐
│          │              │          │
│  应用    │              │  内核    │
│  程序    │              │          │
│          │              │          │
└──────────┘              └──────────┘

第1步：read()系统调用
┌──────────────────────────────────────────┐
│ 1. 上下文切换：用户态 → 内核态            │
│ 2. DMA拷贝：磁盘 → 内核缓冲区（Page Cache）│
│ 3. CPU拷贝：内核缓冲区 → 用户缓冲区       │
│ 4. 上下文切换：内核态 → 用户态            │
└──────────────────────────────────────────┘

磁盘 ─DMA拷贝→ 内核缓冲区 ─CPU拷贝→ 用户缓冲区
                  ↑                    ↑
              (拷贝1)              (拷贝2)

第2步：write()系统调用
┌──────────────────────────────────────────┐
│ 1. 上下文切换：用户态 → 内核态            │
│ 2. CPU拷贝：用户缓冲区 → Socket缓冲区     │
│ 3. DMA拷贝：Socket缓冲区 → 网卡           │
│ 4. 上下文切换：内核态 → 用户态            │
└──────────────────────────────────────────┘

用户缓冲区 ─CPU拷贝→ Socket缓冲区 ─DMA拷贝→ 网卡
              ↑                    ↑
          (拷贝3)              (拷贝4)

总结：
- 4次拷贝：2次DMA拷贝 + 2次CPU拷贝
- 4次上下文切换：用户态 ↔ 内核态
- 数据在用户空间和内核空间之间来回拷贝
- CPU参与了数据拷贝，浪费CPU资源
```

#### 零拷贝的目标：

```
目标1：减少拷贝次数
- 减少CPU拷贝（CPU可以做更重要的事）
- 减少内存带宽占用

目标2：减少上下文切换
- 减少用户态和内核态的切换
- 降低系统开销

目标3：避免数据在用户空间和内核空间之间拷贝
- 数据直接在内核空间传输
- 提高传输效率
```

### 1.2 零拷贝技术1：mmap + write

#### mmap（内存映射）的原理：

```
mmap的工作原理：

传统read()：
┌─────────────────────────────────────────┐
│ 磁盘 → 内核缓冲区 → 用户缓冲区          │
│        (DMA拷贝)    (CPU拷贝)           │
└─────────────────────────────────────────┘

mmap()：
┌─────────────────────────────────────────┐
│ 磁盘 → 内核缓冲区（Page Cache）         │
│        (DMA拷贝)                        │
│          ↓                              │
│        映射到用户空间（共享内存）        │
│        （无需拷贝）                      │
└─────────────────────────────────────────┘

内存布局：
┌─────────────────────────────────────────┐
│          用户空间                        │
│  ┌─────────────────┐                    │
│  │  mmap映射区域   │ ←─┐                │
│  └─────────────────┘   │ 映射            │
│                        │                │
├────────────────────────┼────────────────┤
│          内核空间       │                │
│  ┌─────────────────┐   │                │
│  │  Page Cache     │ ←─┘                │
│  └─────────────────┘                    │
└─────────────────────────────────────────┘

优势：
- 用户空间和内核空间共享同一块物理内存
- 避免了内核缓冲区到用户缓冲区的CPU拷贝
```

#### mmap + write的数据流转：

```
数据流转过程（3次拷贝 + 4次上下文切换）：

第1步：mmap()系统调用
┌──────────────────────────────────────────┐
│ 1. 上下文切换：用户态 → 内核态            │
│ 2. 建立内存映射（虚拟内存 → 物理内存）   │
│ 3. 上下文切换：内核态 → 用户态            │
└──────────────────────────────────────────┘

第2步：访问映射内存（触发缺页中断）
┌──────────────────────────────────────────┐
│ 1. 缺页中断：用户态 → 内核态              │
│ 2. DMA拷贝：磁盘 → Page Cache             │
│ 3. 返回用户态                             │
└──────────────────────────────────────────┘

磁盘 ─DMA拷贝→ Page Cache（映射到用户空间）
                  ↑
              (拷贝1)

第3步：write()系统调用
┌──────────────────────────────────────────┐
│ 1. 上下文切换：用户态 → 内核态            │
│ 2. CPU拷贝：Page Cache → Socket缓冲区     │
│ 3. DMA拷贝：Socket缓冲区 → 网卡           │
│ 4. 上下文切换：内核态 → 用户态            │
└──────────────────────────────────────────┘

Page Cache ─CPU拷贝→ Socket缓冲区 ─DMA拷贝→ 网卡
              ↑                    ↑
          (拷贝2)              (拷贝3)

总结：
- 3次拷贝：2次DMA拷贝 + 1次CPU拷贝
- 4次上下文切换
- 减少了1次CPU拷贝（内核缓冲区 → 用户缓冲区）
```

#### Java中使用mmap：

```java
// 使用MappedByteBuffer
public class MmapExample {
    
    public static void sendFileWithMmap(String filePath, SocketChannel socketChannel) 
            throws IOException {
        // 1. 打开文件
        RandomAccessFile file = new RandomAccessFile(filePath, "r");
        FileChannel fileChannel = file.getChannel();
        
        long fileSize = fileChannel.size();
        
        // 2. 创建内存映射
        MappedByteBuffer mappedBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            0,              // 起始位置
            fileSize        // 映射大小
        );
        
        // 3. 直接从映射内存写入Socket
        socketChannel.write(mappedBuffer);
        
        fileChannel.close();
        file.close();
    }
}
```

### 1.3 零拷贝技术2：sendfile（最优方案）

#### sendfile的原理：

```
sendfile系统调用：
int sendfile(
    int out_fd,     // 输出文件描述符（Socket）
    int in_fd,      // 输入文件描述符（File）
    off_t *offset,  // 起始偏移
    size_t count    // 传输字节数
);

核心思想：
- 数据完全在内核空间传输
- 不经过用户空间
- 减少拷贝和上下文切换
```

#### sendfile的数据流转：

```
数据流转过程（2次拷贝 + 2次上下文切换）：

第1步：sendfile()系统调用
┌──────────────────────────────────────────┐
│ 1. 上下文切换：用户态 → 内核态            │
│ 2. DMA拷贝：磁盘 → Page Cache             │
│ 3. DMA拷贝：Page Cache → 网卡（直接）     │
│ 4. 上下文切换：内核态 → 用户态            │
└──────────────────────────────────────────┘

磁盘 ─DMA拷贝→ Page Cache ─DMA拷贝→ 网卡
        ↑                    ↑
    (拷贝1)              (拷贝2)

注意：
- 没有CPU拷贝！
- 数据不经过用户空间
- 只有2次上下文切换

进一步优化（Scatter-Gather DMA）：
┌──────────────────────────────────────────┐
│ 1. DMA拷贝：磁盘 → Page Cache             │
│ 2. 将Page Cache的地址和长度发送给网卡     │
│ 3. 网卡直接从Page Cache读取数据（DMA）    │
└──────────────────────────────────────────┘

磁盘 ─DMA拷贝→ Page Cache
        ↑            ↓
    (拷贝1)    (网卡直接读取，无需拷贝)

真正的零拷贝：
- 只有1次DMA拷贝（磁盘 → Page Cache）
- 网卡直接从Page Cache读取
- 需要硬件支持（Scatter-Gather DMA）
```

#### Java中使用sendfile：

```java
// 使用FileChannel.transferTo()
public class SendfileExample {
    
    public static void sendFileWithTransferTo(String filePath, SocketChannel socketChannel) 
            throws IOException {
        // 1. 打开文件
        FileChannel fileChannel = FileChannel.open(
            Paths.get(filePath),
            StandardOpenOption.READ
        );
        
        long fileSize = fileChannel.size();
        long position = 0;
        
        // 2. 使用transferTo传输（底层调用sendfile）
        while (position < fileSize) {
            long transferred = fileChannel.transferTo(
                position,
                fileSize - position,
                socketChannel
            );
            
            position += transferred;
        }
        
        fileChannel.close();
    }
    
    // transferTo的限制
    // - 单次传输最大2GB（某些系统）
    // - 需要分块传输大文件
}
```

### 1.4 零拷贝技术3：Splice（Linux特有）

```
splice系统调用：
ssize_t splice(
    int fd_in,          // 输入文件描述符
    loff_t *off_in,     // 输入偏移
    int fd_out,         // 输出文件描述符
    loff_t *off_out,    // 输出偏移
    size_t len,         // 传输长度
    unsigned int flags  // 标志
);

特点：
- 在两个文件描述符之间移动数据
- 数据在内核空间的管道缓冲区中传输
- 不需要拷贝到Page Cache
- 适合流式传输

数据流转：
┌──────────────────────────────────────────┐
│ 文件 → 管道缓冲区 → Socket                │
│      (零拷贝)    (零拷贝)                 │
└──────────────────────────────────────────┘

Java不直接支持splice，但Netty有封装
```

### 1.5 零拷贝技术对比

```
┌──────────┬──────┬──────┬──────┬──────────┐
│  技术    │拷贝次│上下文│CPU参│  适用场景 │
│          │  数  │切换  │  与  │          │
├──────────┼──────┼──────┼──────┼──────────┤
│传统I/O   │  4   │  4   │  是  │ 通用     │
│(read+write)│    │      │      │          │
├──────────┼──────┼──────┼──────┼──────────┤
│mmap+write│  3   │  4   │  是  │ 需要处理 │
│          │      │      │      │ 数据     │
├──────────┼──────┼──────┼──────┼──────────┤
│sendfile  │  2   │  2   │  否  │ 文件传输 │
│          │      │      │      │ (推荐)   │
├──────────┼──────┼──────┼──────┼──────────┤
│sendfile  │  1   │  2   │  否  │ 需要硬件 │
│+SG-DMA   │      │      │      │ 支持     │
├──────────┼──────┼──────┼──────┼──────────┤
│splice    │  0   │  2   │  否  │ 管道传输 │
└──────────┴──────┴──────┴──────┴──────────┘

性能测试（传输1GB文件）：
- 传统I/O：5000ms
- mmap+write：3500ms（提升30%）
- sendfile：1000ms（提升80%）
```

### 1.6 问题：零拷贝的局限性是什么？

```
局限1：不能处理数据
┌─────────────────────────────────────────┐
│ - 数据直接从文件传输到Socket             │
│ - 无法在中间修改数据                     │
│ - 不适合需要加密、压缩等场景             │
└─────────────────────────────────────────┘

局限2：文件必须支持DMA
┌─────────────────────────────────────────┐
│ - 某些文件系统不支持DMA                  │
│ - 网络文件系统可能不支持                 │
└─────────────────────────────────────────┘

局限3：平台限制
┌─────────────────────────────────────────┐
│ - sendfile是Linux特有                   │
│ - Windows需要使用TransmitFile           │
│ - 跨平台需要适配                         │
└─────────────────────────────────────────┘

局限4：大文件限制
┌─────────────────────────────────────────┐
│ - 单次transferTo最多2GB                  │
│ - 需要分块传输                           │
└─────────────────────────────────────────┘
```

---

## 二、Reactor模式深度剖析

### 2.1 问题：什么是Reactor模式？为什么需要它？

#### Reactor模式的起源：

```
问题：如何高效处理大量并发连接？

传统方案（多线程）：
┌─────────────────────────────────────────┐
│ 连接1 → 线程1                            │
│ 连接2 → 线程2                            │
│ 连接3 → 线程3                            │
│ ...                                     │
│ 连接N → 线程N                            │
│                                         │
│ 问题：                                   │
│ - 线程数量受限                           │
│ - 上下文切换开销大                       │
│ - 内存消耗高                             │
└─────────────────────────────────────────┘

Reactor方案（事件驱动）：
┌─────────────────────────────────────────┐
│          Reactor（事件分发器）           │
│                 ↓                       │
│         监听所有连接的事件               │
│                 ↓                       │
│    有事件就绪 → 分发给Handler处理        │
│                                         │
│ 优势：                                   │
│ - 少量线程处理大量连接                   │
│ - 事件驱动，高效                         │
│ - 资源利用率高                           │
└─────────────────────────────────────────┘
```

#### Reactor模式的核心组件：

```
Reactor模式的角色：

1. Reactor（反应器）
┌─────────────────────────────────────────┐
│ - 负责监听和分发事件                     │
│ - 使用Selector实现                       │
│ - 事件循环的核心                         │
└─────────────────────────────────────────┘

2. Handler（处理器）
┌─────────────────────────────────────────┐
│ - 处理具体的I/O事件                      │
│ - 读取数据、业务处理、写入响应           │
│ - 每个连接对应一个Handler                │
└─────────────────────────────────────────┘

3. Acceptor（接收器）
┌─────────────────────────────────────────┐
│ - 处理连接事件                           │
│ - 接受新连接                             │
│ - 创建Handler并注册到Reactor             │
└─────────────────────────────────────────┘

协作流程：
┌─────────────────────────────────────────┐
│ 1. Reactor监听事件                       │
│    ↓                                    │
│ 2. 有连接事件 → Acceptor处理             │
│    ↓                                    │
│ 3. 创建Handler并注册                     │
│    ↓                                    │
│ 4. 有读写事件 → Handler处理              │
└─────────────────────────────────────────┘
```

### 2.2 Reactor模式的三种形态

#### 形态1：单Reactor单线程

```
架构图：
┌─────────────────────────────────────────────┐
│              单线程                          │
│  ┌─────────────────────────────────────┐   │
│  │          Reactor                    │   │
│  │  ┌──────────────────────┐           │   │
│  │  │     Selector         │           │   │
│  │  └──────────────────────┘           │   │
│  │           ↓                         │   │
│  │  ┌──────────────────────┐           │   │
│  │  │     Acceptor         │           │   │
│  │  └──────────────────────┘           │   │
│  │           ↓                         │   │
│  │  ┌──────────────────────┐           │   │
│  │  │   Handler1           │           │   │
│  │  │   Handler2           │           │   │
│  │  │   Handler3           │           │   │
│  │  └──────────────────────┘           │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘

工作流程：
1. Reactor在单线程中运行
2. 监听所有事件（连接、读、写）
3. 有事件就绪 → 分发给对应Handler
4. Handler在同一线程中处理

优点：
✓ 模型简单，易于实现
✓ 没有多线程竞争，无需同步
✓ 适合小规模应用

缺点：
✗ 单线程，无法利用多核CPU
✗ Handler处理慢会阻塞其他连接
✗ 不适合计算密集型任务
```

**单Reactor单线程实现**：

```java
/**
 * 单Reactor单线程模型
 */
public class SingleThreadReactor {
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    
    public void start(int port) throws IOException {
        selector = Selector.open();
        
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        
        // 注册Acceptor
        serverChannel.register(selector, SelectionKey.OP_ACCEPT, new Acceptor());
        
        System.out.println("单Reactor单线程服务器启动: " + port);
        
        // 事件循环（单线程）
        while (true) {
            selector.select();
            
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                
                // 分发事件
                dispatch(key);
            }
        }
    }
    
    private void dispatch(SelectionKey key) {
        Runnable handler = (Runnable) key.attachment();
        if (handler != null) {
            handler.run();  // 在当前线程执行
        }
    }
    
    // Acceptor：处理连接事件
    class Acceptor implements Runnable {
        @Override
        public void run() {
            try {
                SocketChannel channel = serverChannel.accept();
                if (channel != null) {
                    new Handler(selector, channel);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Handler：处理读写事件
    class Handler implements Runnable {
        private SocketChannel channel;
        private SelectionKey key;
        private ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        public Handler(Selector selector, SocketChannel channel) throws IOException {
            this.channel = channel;
            channel.configureBlocking(false);
            
            // 注册读事件
            key = channel.register(selector, SelectionKey.OP_READ);
            key.attach(this);
            
            selector.wakeup();
        }
        
        @Override
        public void run() {
            try {
                if (key.isReadable()) {
                    read();
                } else if (key.isWritable()) {
                    write();
                }
            } catch (IOException e) {
                close();
            }
        }
        
        private void read() throws IOException {
            buffer.clear();
            int len = channel.read(buffer);
            
            if (len == -1) {
                close();
                return;
            }
            
            if (len > 0) {
                // 处理数据（在当前线程）
                process();
            }
        }
        
        private void process() {
            buffer.flip();
            // 业务处理...
            
            // 切换到写模式
            key.interestOps(SelectionKey.OP_WRITE);
        }
        
        private void write() throws IOException {
            channel.write(buffer);
            
            if (!buffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
        
        private void close() {
            try {
                key.cancel();
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

#### 形态2：单Reactor多线程

```
架构图：
┌─────────────────────────────────────────────┐
│          主线程（Reactor）                   │
│  ┌─────────────────────────────────────┐   │
│  │          Reactor                    │   │
│  │  ┌──────────────────────┐           │   │
│  │  │     Selector         │           │   │
│  │  └──────────────────────┘           │   │
│  │           ↓                         │   │
│  │  ┌──────────────────────┐           │   │
│  │  │     Acceptor         │           │   │
│  │  └──────────────────────┘           │   │
│  └─────────────────────────────────────┘   │
│           ↓                                 │
│  ┌─────────────────────────────────────┐   │
│  │      工作线程池                      │   │
│  │  ┌──────────────────────┐           │   │
│  │  │   Handler1 (Thread1) │           │   │
│  │  │   Handler2 (Thread2) │           │   │
│  │  │   Handler3 (Thread3) │           │   │
│  │  └──────────────────────┘           │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘

工作流程：
1. Reactor在主线程中运行
2. 监听所有I/O事件
3. 有读事件 → 读取数据
4. 将业务处理提交到线程池
5. 处理完成 → 写回响应

优点：
✓ 充分利用多核CPU
✓ 业务处理不阻塞I/O
✓ 适合计算密集型任务

缺点：
✗ 单Reactor处理所有I/O事件，高并发时成为瓶颈
✗ 多线程需要同步，复杂度增加
```

**单Reactor多线程实现**：

```java
/**
 * 单Reactor多线程模型
 */
public class MultiThreadReactor {
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private ExecutorService threadPool;
    
    public void start(int port) throws IOException {
        selector = Selector.open();
        
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        
        serverChannel.register(selector, SelectionKey.OP_ACCEPT, new Acceptor());
        
        // 创建线程池
        threadPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        );
        
        System.out.println("单Reactor多线程服务器启动: " + port);
        
        // 事件循环（主线程）
        while (true) {
            selector.select();
            
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                
                dispatch(key);
            }
        }
    }
    
    private void dispatch(SelectionKey key) {
        Runnable handler = (Runnable) key.attachment();
        if (handler != null) {
            handler.run();
        }
    }
    
    class Acceptor implements Runnable {
        @Override
        public void run() {
            try {
                SocketChannel channel = serverChannel.accept();
                if (channel != null) {
                    new MultiThreadHandler(selector, channel, threadPool);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Handler：I/O在主线程，业务处理在工作线程
    class MultiThreadHandler implements Runnable {
        private SocketChannel channel;
        private SelectionKey key;
        private ByteBuffer buffer = ByteBuffer.allocate(1024);
        private ExecutorService threadPool;
        
        private static final int READING = 0;
        private static final int PROCESSING = 1;
        private static final int WRITING = 2;
        private int state = READING;
        
        public MultiThreadHandler(Selector selector, SocketChannel channel, 
                                 ExecutorService threadPool) throws IOException {
            this.channel = channel;
            this.threadPool = threadPool;
            
            channel.configureBlocking(false);
            key = channel.register(selector, SelectionKey.OP_READ);
            key.attach(this);
            
            selector.wakeup();
        }
        
        @Override
        public void run() {
            try {
                if (state == READING) {
                    read();
                } else if (state == WRITING) {
                    write();
                }
            } catch (IOException e) {
                close();
            }
        }
        
        private void read() throws IOException {
            buffer.clear();
            int len = channel.read(buffer);
            
            if (len == -1) {
                close();
                return;
            }
            
            if (len > 0) {
                state = PROCESSING;
                
                // 提交到线程池处理（异步）
                threadPool.submit(() -> {
                    process();
                });
            }
        }
        
        private void process() {
            // 业务处理（在工作线程）
            buffer.flip();
            
            // 模拟耗时操作
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // 处理完成，切换到写模式
            state = WRITING;
            key.interestOps(SelectionKey.OP_WRITE);
            key.selector().wakeup();
        }
        
        private void write() throws IOException {
            channel.write(buffer);
            
            if (!buffer.hasRemaining()) {
                state = READING;
                key.interestOps(SelectionKey.OP_READ);
            }
        }
        
        private void close() {
            try {
                key.cancel();
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

#### 形态3：主从Reactor多线程（Netty的模型）⭐⭐⭐

```
架构图：
┌─────────────────────────────────────────────────────┐
│              主Reactor线程                           │
│  ┌─────────────────────────────────────────────┐   │
│  │          MainReactor                        │   │
│  │  ┌──────────────────────┐                   │   │
│  │  │     Selector         │                   │   │
│  │  └──────────────────────┘                   │   │
│  │           ↓                                 │   │
│  │  ┌──────────────────────┐                   │   │
│  │  │     Acceptor         │                   │   │
│  │  └──────────────────────┘                   │   │
│  └─────────────────────────────────────────────┘   │
│           ↓                                         │
│  分发连接到SubReactor                               │
│           ↓                                         │
│  ┌─────────────────────────────────────────────┐   │
│  │        从Reactor线程池                       │   │
│  │  ┌─────────────────────────────────────┐   │   │
│  │  │  SubReactor1 (Thread1)              │   │   │
│  │  │  ┌────────────┐                     │   │   │
│  │  │  │  Selector  │                     │   │   │
│  │  │  └────────────┘                     │   │   │
│  │  │  Handler1, Handler2...              │   │   │
│  │  └─────────────────────────────────────┘   │   │
│  │  ┌─────────────────────────────────────┐   │   │
│  │  │  SubReactor2 (Thread2)              │   │   │
│  │  │  ┌────────────┐                     │   │   │
│  │  │  │  Selector  │                     │   │   │
│  │  │  └────────────┘                     │   │   │
│  │  │  Handler3, Handler4...              │   │   │
│  │  └─────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────┘   │
│           ↓                                         │
│  ┌─────────────────────────────────────────────┐   │
│  │        业务线程池（可选）                    │   │
│  │  Thread1, Thread2, Thread3...               │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘

工作流程：
1. MainReactor只负责接收连接
2. 接收到连接后，分发给SubReactor
3. SubReactor负责该连接的所有I/O事件
4. 业务处理可以在SubReactor线程或单独的业务线程池

优点：
✓ MainReactor专注于接收连接，高效
✓ 多个SubReactor处理I/O，充分利用多核
✓ 连接分散到多个SubReactor，负载均衡
✓ 这是Netty的线程模型！

缺点：
✗ 实现复杂
✗ 需要考虑线程安全
```

**主从Reactor多线程实现**：

```java
/**
 * 主从Reactor多线程模型（Netty模型）
 */
public class MasterSlaveReactor {
    
    private Selector mainSelector;      // 主Reactor的Selector
    private ServerSocketChannel serverChannel;
    private SubReactor[] subReactors;   // 从Reactor数组
    private int next = 0;               // 轮询索引
    
    public void start(int port) throws IOException {
        // 1. 创建主Reactor
        mainSelector = Selector.open();
        
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        
        serverChannel.register(mainSelector, SelectionKey.OP_ACCEPT);
        
        // 2. 创建从Reactor线程池
        int subReactorCount = Runtime.getRuntime().availableProcessors();
        subReactors = new SubReactor[subReactorCount];
        
        for (int i = 0; i < subReactorCount; i++) {
            subReactors[i] = new SubReactor();
            new Thread(subReactors[i], "SubReactor-" + i).start();
        }
        
        System.out.println("主从Reactor服务器启动: " + port);
        System.out.println("SubReactor数量: " + subReactorCount);
        
        // 3. 主Reactor事件循环
        while (true) {
            mainSelector.select();
            
            Set<SelectionKey> keys = mainSelector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                
                if (key.isAcceptable()) {
                    acceptConnection();
                }
            }
        }
    }
    
    // 接收连接并分发到SubReactor
    private void acceptConnection() throws IOException {
        SocketChannel channel = serverChannel.accept();
        if (channel != null) {
            System.out.println("接受连接: " + channel.getRemoteAddress());
            
            // 轮询分发到SubReactor
            SubReactor subReactor = subReactors[next];
            next = (next + 1) % subReactors.length;
            
            subReactor.registerChannel(channel);
        }
    }
    
    // 从Reactor：处理I/O事件
    static class SubReactor implements Runnable {
        private Selector selector;
        private Queue<SocketChannel> pendingChannels = new ConcurrentLinkedQueue<>();
        
        public SubReactor() throws IOException {
            selector = Selector.open();
        }
        
        // 注册Channel（由主Reactor调用）
        public void registerChannel(SocketChannel channel) {
            pendingChannels.offer(channel);
            selector.wakeup();  // 唤醒selector
        }
        
        @Override
        public void run() {
            while (true) {
                try {
                    // 处理待注册的Channel
                    registerPendingChannels();
                    
                    // 等待I/O事件
                    selector.select();
                    
                    // 处理就绪事件
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();
                    
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        
                        Handler handler = (Handler) key.attachment();
                        if (handler != null) {
                            handler.handle(key);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        private void registerPendingChannels() throws IOException {
            SocketChannel channel;
            while ((channel = pendingChannels.poll()) != null) {
                channel.configureBlocking(false);
                SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
                key.attach(new Handler(channel));
            }
        }
    }
    
    // Handler：处理读写事件
    static class Handler {
        private SocketChannel channel;
        private ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        public Handler(SocketChannel channel) {
            this.channel = channel;
        }
        
        public void handle(SelectionKey key) {
            try {
                if (key.isReadable()) {
                    read(key);
                } else if (key.isWritable()) {
                    write(key);
                }
            } catch (IOException e) {
                close(key);
            }
        }
        
        private void read(SelectionKey key) throws IOException {
            buffer.clear();
            int len = channel.read(buffer);
            
            if (len == -1) {
                close(key);
                return;
            }
            
            if (len > 0) {
                // 处理数据
                buffer.flip();
                
                // 切换到写模式
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
        
        private void write(SelectionKey key) throws IOException {
            channel.write(buffer);
            
            if (!buffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
        
        private void close(SelectionKey key) {
            try {
                key.cancel();
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

### 2.3 Reactor模式对比总结

```
┌────────────┬──────────┬──────────┬──────────┐
│  模式      │ 单Reactor│ 单Reactor│主从Reactor│
│            │ 单线程   │ 多线程   │ 多线程   │
├────────────┼──────────┼──────────┼──────────┤
│ I/O处理    │ 单线程   │ 单线程   │ 多线程   │
├────────────┼──────────┼──────────┼──────────┤
│ 业务处理   │ 单线程   │ 线程池   │ 线程池   │
├────────────┼──────────┼──────────┼──────────┤
│ 并发能力   │ 低       │ 中       │ 高       │
├────────────┼──────────┼──────────┼──────────┤
│ 实现复杂度 │ 简单     │ 中等     │ 复杂     │
├────────────┼──────────┼──────────┼──────────┤
│ 适用场景   │ 小规模   │ 中等规模 │ 大规模   │
│            │ 原型开发 │ 业务系统 │ 高并发   │
├────────────┼──────────┼──────────┼──────────┤
│ 典型应用   │ Redis    │ Tomcat   │ Netty    │
│            │ (6.0前)  │ (NIO模式)│          │
└────────────┴──────────┴──────────┴──────────┘

选择建议：
1. 学习和原型 → 单Reactor单线程
2. 一般业务系统 → 单Reactor多线程
3. 高并发系统 → 主从Reactor多线程
4. 生产环境 → 使用Netty（已经实现了主从Reactor）
```

---

## 三、NIO的最佳实践

### 3.1 性能优化建议

```java
// 1. 使用DirectBuffer进行I/O
ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

// 2. 使用对象池复用Buffer
class BufferPool {
    private Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(8192);
        }
        buffer.clear();
        return buffer;
    }
    
    public void release(ByteBuffer buffer) {
        pool.offer(buffer);
    }
}

// 3. 合理设置Buffer大小
// - 太小：频繁系统调用
// - 太大：浪费内存
// - 推荐：8KB - 64KB

// 4. 使用零拷贝传输文件
fileChannel.transferTo(0, size, socketChannel);

// 5. 批量注册事件
channel.register(selector, OP_READ | OP_WRITE);

// 6. 使用Scatter/Gather减少系统调用
ByteBuffer[] buffers = {header, body};
channel.write(buffers);
```

### 3.2 常见陷阱和解决方案

```java
// 陷阱1：忘记flip()
// 解决：养成习惯，写入后立即flip()

// 陷阱2：忘记remove() SelectionKey
// 解决：使用Iterator.remove()

// 陷阱3：一直注册OP_WRITE
// 解决：按需注册，写完立即取消

// 陷阱4：空轮询Bug
// 解决：检测并重建Selector

// 陷阱5：DirectBuffer泄漏
// 解决：使用对象池管理

// 陷阱6：并发访问Buffer
// 解决：每个线程独立Buffer或使用ThreadLocal
```

---

## 四、总结：从NIO到Netty

### NIO的优势：

```
1. 高并发：一个线程管理多个连接
2. 高性能：事件驱动，零拷贝
3. 低资源：减少线程和内存开销
4. 可扩展：轻松支持万级并发
```

### NIO的问题：

```
1. 编程复杂：需要处理很多细节
2. 容易出错：Buffer管理、事件处理
3. 调试困难：异步编程难以追踪
4. 跨平台：不同系统实现不同
5. Bug多：空轮询等JDK Bug
```

### 为什么需要Netty？

```
Netty解决了NIO的所有痛点：
1. 简化编程模型（Pipeline、Handler）
2. 解决JDK Bug（空轮询）
3. 提供丰富的编解码器
4. 内存管理优化（ByteBuf、对象池）
5. 完善的线程模型（主从Reactor）
6. 跨平台支持
7. 生产级稳定性
```

---

**学习建议**：
1. 深入理解NIO的原理和Reactor模式
2. 手写简单的NIO服务器加深理解
3. 学习Netty，看看框架如何优雅地解决这些问题
4. 阅读Netty源码，学习优秀的设计和实现

**下一步**：开始学习Netty框架，它是NIO的最佳实践！
