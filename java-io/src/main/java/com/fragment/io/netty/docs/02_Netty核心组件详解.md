# 02_Netty核心组件详解

> **核心问题**：Bootstrap如何启动Netty应用？EventLoop的线程模型是怎样的？Channel的生命周期如何管理？

---

## 一、Bootstrap启动器

### 1.1 Bootstrap的作用

**Bootstrap是什么？**
- Netty应用的启动引导类
- 负责配置和初始化Netty的各个组件
- 提供流畅的Builder API

**为什么需要Bootstrap？**

**问题**：如果不用Bootstrap，直接创建Channel会怎样？
```java
// 不使用Bootstrap的问题
NioServerSocketChannel channel = new NioServerSocketChannel();
channel.bind(new InetSocketAddress(8080));  // 缺少EventLoop
// 问题：Channel没有绑定EventLoop，无法处理I/O事件
// 问题：没有配置Pipeline，无法处理业务逻辑
// 问题：配置分散，难以管理
```

**解决方案**：使用Bootstrap统一配置
```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(new MyHandler());
        }
    })
    .bind(8080);
```

### 1.2 Bootstrap的类型

#### 1.2.1 ServerBootstrap（服务端启动器）

**用途**：启动服务端应用，监听客户端连接

**核心配置**：
```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap
    // 1. 配置线程组
    .group(bossGroup, workerGroup)
    
    // 2. 配置Channel类型
    .channel(NioServerSocketChannel.class)
    
    // 3. 配置ServerChannel选项
    .option(ChannelOption.SO_BACKLOG, 128)
    .option(ChannelOption.SO_REUSEADDR, true)
    
    // 4. 配置客户端Channel选项
    .childOption(ChannelOption.SO_KEEPALIVE, true)
    .childOption(ChannelOption.TCP_NODELAY, true)
    
    // 5. 配置ServerChannel处理器
    .handler(new LoggingHandler(LogLevel.INFO))
    
    // 6. 配置客户端Channel处理器
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline()
                .addLast(new StringDecoder())
                .addLast(new StringEncoder())
                .addLast(new MyServerHandler());
        }
    });

// 7. 绑定端口并启动
ChannelFuture future = bootstrap.bind(8080).sync();
```

**配置项详解**：

| 配置项 | 作用对象 | 说明 |
|-------|---------|------|
| **group(bossGroup, workerGroup)** | 线程组 | bossGroup处理连接，workerGroup处理I/O |
| **channel()** | ServerChannel | 指定ServerChannel的实现类 |
| **option()** | ServerChannel | 配置ServerChannel的TCP参数 |
| **childOption()** | 客户端Channel | 配置客户端Channel的TCP参数 |
| **handler()** | ServerChannel | ServerChannel的处理器 |
| **childHandler()** | 客户端Channel | 客户端Channel的处理器 |

**为什么要区分option和childOption？**

```
ServerChannel和客户端Channel的关系：
┌─────────────────────────────────────────┐
│       ServerChannel (监听8080)           │
│       - option配置                       │
│       - handler处理                      │
│                                         │
│  ┌─────────────┐  ┌─────────────┐      │
│  │  Client 1   │  │  Client 2   │      │
│  │  Channel    │  │  Channel    │      │
│  │  childOption│  │  childOption│      │
│  │  childHandler│  │  childHandler│     │
│  └─────────────┘  └─────────────┘      │
└─────────────────────────────────────────┘
```

- **option**：配置ServerChannel，影响连接的接收
- **childOption**：配置客户端Channel，影响数据的传输

#### 1.2.2 Bootstrap（客户端启动器）

**用途**：启动客户端应用，连接服务端

**核心配置**：
```java
Bootstrap bootstrap = new Bootstrap();
bootstrap
    // 1. 配置线程组（客户端只需要一个）
    .group(workerGroup)
    
    // 2. 配置Channel类型
    .channel(NioSocketChannel.class)
    
    // 3. 配置Channel选项
    .option(ChannelOption.SO_KEEPALIVE, true)
    .option(ChannelOption.TCP_NODELAY, true)
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
    
    // 4. 配置处理器
    .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline()
                .addLast(new StringDecoder())
                .addLast(new StringEncoder())
                .addLast(new MyClientHandler());
        }
    });

// 5. 连接服务端
ChannelFuture future = bootstrap.connect("localhost", 8080).sync();
```

**ServerBootstrap vs Bootstrap**：

| 对比项 | ServerBootstrap | Bootstrap |
|-------|----------------|-----------|
| **线程组** | 两个（boss+worker） | 一个（worker） |
| **Channel类型** | NioServerSocketChannel | NioSocketChannel |
| **配置项** | option + childOption | option |
| **处理器** | handler + childHandler | handler |
| **启动方法** | bind() | connect() |

### 1.3 Bootstrap的启动流程

#### 1.3.1 服务端启动流程

```
ServerBootstrap启动流程：
┌─────────────────────────────────────────────────────────┐
│ 1. 创建ServerBootstrap                                  │
│    ServerBootstrap bootstrap = new ServerBootstrap()    │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. 配置参数                                              │
│    - group(bossGroup, workerGroup)                      │
│    - channel(NioServerSocketChannel.class)              │
│    - option/childOption                                 │
│    - handler/childHandler                               │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 调用bind()方法                                        │
│    ChannelFuture future = bootstrap.bind(8080)          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 创建ServerChannel                                     │
│    - 通过反射创建NioServerSocketChannel实例              │
│    - 初始化ServerChannel的Pipeline                       │
│    - 添加ServerBootstrapAcceptor到Pipeline               │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. 注册ServerChannel到BossGroup                          │
│    - 选择一个EventLoop                                   │
│    - 将ServerChannel注册到EventLoop的Selector            │
│    - 触发channelRegistered事件                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 6. 绑定端口                                              │
│    - 调用ServerChannel.bind(8080)                        │
│    - 底层调用ServerSocketChannel.bind()                  │
│    - 触发channelActive事件                               │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 7. 注册OP_ACCEPT事件                                     │
│    - ServerChannel开始监听连接                           │
│    - 等待客户端连接                                      │
└─────────────────────────────────────────────────────────┘
```

**关键步骤详解**：

**步骤4：ServerBootstrapAcceptor是什么？**
```java
// ServerBootstrapAcceptor是一个特殊的Handler
// 作用：接收新连接，并将其注册到WorkerGroup
private static class ServerBootstrapAcceptor extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // msg是新接收的SocketChannel
        Channel child = (Channel) msg;
        
        // 配置客户端Channel
        child.pipeline().addLast(childHandler);
        setChannelOptions(child, childOptions);
        
        // 注册到WorkerGroup
        childGroup.register(child);
    }
}
```

**步骤5：为什么要注册到Selector？**
- **多路复用**：一个线程可以管理多个连接
- **事件驱动**：当有连接到达时，Selector会通知EventLoop
- **非阻塞**：不需要阻塞等待连接

#### 1.3.2 客户端启动流程

```
Bootstrap启动流程：
┌─────────────────────────────────────────────────────────┐
│ 1. 创建Bootstrap                                         │
│    Bootstrap bootstrap = new Bootstrap()                 │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. 配置参数                                              │
│    - group(workerGroup)                                 │
│    - channel(NioSocketChannel.class)                    │
│    - option                                             │
│    - handler                                            │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 调用connect()方法                                     │
│    ChannelFuture future = bootstrap.connect(host, port) │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 创建SocketChannel                                     │
│    - 通过反射创建NioSocketChannel实例                    │
│    - 初始化SocketChannel的Pipeline                       │
│    - 添加用户配置的Handler                               │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. 注册SocketChannel到WorkerGroup                        │
│    - 选择一个EventLoop                                   │
│    - 将SocketChannel注册到EventLoop的Selector            │
│    - 触发channelRegistered事件                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 6. 发起连接                                              │
│    - 调用SocketChannel.connect(address)                  │
│    - 底层调用SocketChannel.connect()                     │
│    - 注册OP_CONNECT事件                                  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 7. 等待连接完成                                          │
│    - EventLoop监听OP_CONNECT事件                         │
│    - 连接完成后触发channelActive事件                      │
│    - 注册OP_READ事件                                     │
└─────────────────────────────────────────────────────────┘
```

### 1.4 常用配置选项

#### 1.4.1 ChannelOption详解

**服务端常用选项**：

```java
// SO_BACKLOG：连接队列的大小
.option(ChannelOption.SO_BACKLOG, 128)
// 说明：当连接请求到达时，如果服务端来不及处理，会放入队列
// 队列满了之后，新的连接会被拒绝
// 默认值：Linux上是128，Windows上是200

// SO_REUSEADDR：地址重用
.option(ChannelOption.SO_REUSEADDR, true)
// 说明：允许在TIME_WAIT状态下重新绑定端口
// 用途：服务重启时可以立即绑定端口，不需要等待2MSL

// SO_RCVBUF：接收缓冲区大小
.option(ChannelOption.SO_RCVBUF, 32 * 1024)
// 说明：TCP接收缓冲区的大小
// 影响：影响TCP的滑动窗口大小，进而影响吞吐量
```

**客户端Channel常用选项**：

```java
// SO_KEEPALIVE：TCP保活
.childOption(ChannelOption.SO_KEEPALIVE, true)
// 说明：定期发送保活探测包，检测连接是否存活
// 默认：2小时无数据传输时发送探测包
// 用途：检测死连接，及时释放资源

// TCP_NODELAY：禁用Nagle算法
.childOption(ChannelOption.TCP_NODELAY, true)
// 说明：Nagle算法会合并小包发送，减少网络开销
// 禁用后：立即发送数据，降低延迟
// 适用场景：对延迟敏感的应用（游戏、实时通信）

// SO_SNDBUF：发送缓冲区大小
.childOption(ChannelOption.SO_SNDBUF, 32 * 1024)
// 说明：TCP发送缓冲区的大小
// 影响：影响TCP的滑动窗口大小

// SO_LINGER：关闭时的行为
.childOption(ChannelOption.SO_LINGER, 0)
// 说明：关闭连接时，如果缓冲区还有数据
// 0：立即关闭，丢弃数据
// >0：等待指定秒数，尝试发送数据
// -1：默认行为，优雅关闭
```

**Netty特有选项**：

```java
// ALLOCATOR：ByteBuf分配器
.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
// 说明：使用池化的ByteBuf，减少GC
// 默认：Netty 4.1+默认使用池化

// RCVBUF_ALLOCATOR：接收缓冲区分配器
.childOption(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator())
// 说明：自适应调整接收缓冲区大小
// 优势：根据实际接收的数据量动态调整

// WRITE_BUFFER_WATER_MARK：写缓冲区水位
.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
    new WriteBufferWaterMark(32 * 1024, 64 * 1024))
// 说明：控制写缓冲区的大小
// 低水位：32KB，高水位：64KB
// 超过高水位：Channel变为不可写，触发channelWritabilityChanged事件
// 低于低水位：Channel变为可写
```

#### 1.4.2 配置选项的最佳实践

**场景1：高并发服务器**
```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    // 增大连接队列
    .option(ChannelOption.SO_BACKLOG, 1024)
    // 地址重用
    .option(ChannelOption.SO_REUSEADDR, true)
    // 禁用Nagle算法，降低延迟
    .childOption(ChannelOption.TCP_NODELAY, true)
    // 启用TCP保活
    .childOption(ChannelOption.SO_KEEPALIVE, true)
    // 使用池化ByteBuf
    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
```

**场景2：大文件传输**
```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    // 增大接收缓冲区
    .childOption(ChannelOption.SO_RCVBUF, 128 * 1024)
    // 增大发送缓冲区
    .childOption(ChannelOption.SO_SNDBUF, 128 * 1024)
    // 增大写缓冲区水位
    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
        new WriteBufferWaterMark(64 * 1024, 128 * 1024));
```

**场景3：低延迟应用**
```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    // 禁用Nagle算法
    .childOption(ChannelOption.TCP_NODELAY, true)
    // 减小接收缓冲区，快速处理
    .childOption(ChannelOption.SO_RCVBUF, 8 * 1024)
    // 使用直接内存
    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
```

---

## 二、EventLoop事件循环

### 2.1 EventLoop的作用

**EventLoop是什么？**
- Netty的核心组件，负责处理I/O事件和任务
- 本质是一个单线程的事件循环
- 类似于Reactor模式中的Reactor

**EventLoop的职责**：
1. **处理I/O事件**：监听和处理Channel的读写事件
2. **执行任务**：执行提交到EventLoop的任务
3. **执行定时任务**：执行定时任务和周期性任务

### 2.2 EventLoop的线程模型

#### 2.2.1 单线程EventLoop

```
EventLoop的工作模型：
┌───────────────────────────────────────────────────────┐
│              EventLoop (单线程)                        │
│                                                       │
│  while (true) {                                       │
│    ┌─────────────────────────────────────────┐       │
│    │ 1. 执行I/O操作 (50%-70%时间)             │       │
│    │    - selector.select()                  │       │
│    │    - 处理OP_ACCEPT                      │       │
│    │    - 处理OP_READ                        │       │
│    │    - 处理OP_WRITE                       │       │
│    └─────────────────────────────────────────┘       │
│                     ↓                                 │
│    ┌─────────────────────────────────────────┐       │
│    │ 2. 执行任务队列中的任务 (30%-50%时间)     │       │
│    │    - 普通任务                            │       │
│    │    - 定时任务                            │       │
│    │    - 尾部任务                            │       │
│    └─────────────────────────────────────────┘       │
│  }                                                    │
└───────────────────────────────────────────────────────┘
```

**为什么用单线程？**

**问题**：如果EventLoop用多线程，会有什么问题？
```java
// 假设EventLoop用多线程
// 线程1：处理Channel A的读事件
// 线程2：处理Channel A的写事件
// 问题：需要加锁保证线程安全
synchronized (channel) {
    channel.write(data);
}
// 问题：锁竞争导致性能下降
```

**解决方案**：单线程EventLoop
- **无锁化**：每个Channel只在一个EventLoop中处理，不需要加锁
- **顺序性**：事件按顺序处理，不会出现并发问题
- **简单性**：代码简单，易于理解和维护

**单线程如何支持高并发？**
```
一个EventLoop处理多个Channel：
┌─────────────────────────────────────────┐
│         EventLoop (单线程)               │
│                                         │
│  ┌────────┐  ┌────────┐  ┌────────┐   │
│  │Channel1│  │Channel2│  │Channel3│   │
│  └────────┘  └────────┘  └────────┘   │
│       ↓           ↓           ↓        │
│  ┌────────────────────────────────┐   │
│  │        Selector                │   │
│  │  - 监听多个Channel的事件        │   │
│  │  - 有事件时通知EventLoop        │   │
│  └────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

#### 2.2.2 EventLoopGroup

**EventLoopGroup是什么？**
- EventLoop的集合
- 负责管理多个EventLoop
- 负责将Channel分配给EventLoop

**EventLoopGroup的类型**：
```java
// NioEventLoopGroup：基于NIO的实现
EventLoopGroup group = new NioEventLoopGroup(4);  // 4个EventLoop

// EpollEventLoopGroup：基于Linux epoll的实现（性能更好）
EventLoopGroup group = new EpollEventLoopGroup(4);

// DefaultEventLoopGroup：用于执行非I/O任务
EventLoopGroup group = new DefaultEventLoopGroup(4);
```

**EventLoopGroup的大小如何确定？**

```java
// 默认值：CPU核心数 * 2
int defaultThreads = NettyRuntime.availableProcessors() * 2;

// 自定义大小
EventLoopGroup bossGroup = new NioEventLoopGroup(1);      // Boss只需要1个
EventLoopGroup workerGroup = new NioEventLoopGroup(4);    // Worker根据负载调整
```

**如何选择EventLoopGroup的大小？**

| 场景 | BossGroup大小 | WorkerGroup大小 | 说明 |
|------|--------------|----------------|------|
| **单端口服务** | 1 | CPU核心数 * 2 | Boss只需要1个 |
| **多端口服务** | 端口数 | CPU核心数 * 2 | 每个端口1个Boss |
| **CPU密集型** | 1 | CPU核心数 | 避免过多线程切换 |
| **I/O密集型** | 1 | CPU核心数 * 2 | 充分利用I/O等待时间 |

#### 2.2.3 主从Reactor模型

```
主从Reactor模型：
┌─────────────────────────────────────────────────────────┐
│                    BossGroup (主Reactor)                 │
│  ┌──────────────────────────────────────────────────┐   │
│  │  EventLoop (单线程)                               │   │
│  │  - 监听ServerChannel的OP_ACCEPT事件               │   │
│  │  - 接收新连接                                     │   │
│  │  - 将新连接注册到WorkerGroup                      │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                          ↓ (新连接)
┌─────────────────────────────────────────────────────────┐
│                   WorkerGroup (从Reactor)                │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐        │
│  │ EventLoop1 │  │ EventLoop2 │  │ EventLoop3 │        │
│  │  Channel1  │  │  Channel3  │  │  Channel5  │        │
│  │  Channel2  │  │  Channel4  │  │  Channel6  │        │
│  └────────────┘  └────────────┘  └────────────┘        │
│  - 处理OP_READ/OP_WRITE事件                             │
│  - 执行业务逻辑                                         │
└─────────────────────────────────────────────────────────┘
```

**为什么要分BossGroup和WorkerGroup？**

**问题**：如果只用一个EventLoopGroup会怎样？
```java
// 只用一个EventLoopGroup
EventLoopGroup group = new NioEventLoopGroup(1);
bootstrap.group(group, group);  // Boss和Worker用同一个

// 问题：
// 1. 接收连接和处理I/O在同一个线程
// 2. 如果I/O处理慢，会影响接收新连接
// 3. 无法充分利用多核CPU
```

**解决方案**：主从Reactor模型
- **职责分离**：Boss专注接收连接，Worker专注处理I/O
- **并行处理**：多个Worker并行处理I/O，提高吞吐量
- **负载均衡**：新连接均匀分配到Worker

### 2.3 EventLoop的任务执行

#### 2.3.1 提交任务到EventLoop

**为什么需要提交任务？**

**场景1：在Handler中执行耗时操作**
```java
public class MyHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 问题：如果直接执行耗时操作，会阻塞EventLoop
        // 导致其他Channel无法处理
        doSlowOperation();  // 耗时操作
        
        ctx.fireChannelRead(msg);
    }
}
```

**解决方案1：提交到EventLoop的任务队列**
```java
public class MyHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 提交到EventLoop的任务队列
        ctx.channel().eventLoop().execute(() -> {
            doSlowOperation();  // 在EventLoop线程中执行
        });
        
        ctx.fireChannelRead(msg);
    }
}
```

**解决方案2：提交到业务线程池**
```java
public class MyHandler extends ChannelInboundHandlerAdapter {
    private ExecutorService executor = Executors.newFixedThreadPool(10);
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 提交到业务线程池
        executor.submit(() -> {
            doSlowOperation();  // 在业务线程中执行
            
            // 执行完后，提交回EventLoop
            ctx.channel().eventLoop().execute(() -> {
                ctx.writeAndFlush(response);
            });
        });
        
        ctx.fireChannelRead(msg);
    }
}
```

**如何选择？**

| 场景 | 方案 | 说明 |
|------|------|------|
| **轻量级任务** | EventLoop任务队列 | 不阻塞EventLoop |
| **耗时操作** | 业务线程池 | 避免阻塞EventLoop |
| **需要顺序性** | EventLoop任务队列 | 保证顺序执行 |
| **高并发** | 业务线程池 | 充分利用CPU |

#### 2.3.2 定时任务

**提交定时任务**：
```java
// 延迟执行
ctx.channel().eventLoop().schedule(() -> {
    System.out.println("延迟5秒执行");
}, 5, TimeUnit.SECONDS);

// 周期性执行
ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
    System.out.println("每隔10秒执行一次");
}, 0, 10, TimeUnit.SECONDS);
```

**应用场景**：
- **心跳检测**：定期发送心跳包
- **超时检测**：检测连接是否超时
- **定时清理**：清理过期数据

### 2.4 EventLoop的最佳实践

#### 2.4.1 不要阻塞EventLoop

**错误示例**：
```java
public class BadHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // ❌ 错误：阻塞EventLoop
        Thread.sleep(1000);  // 阻塞1秒
        
        // ❌ 错误：执行数据库查询
        String result = database.query("SELECT ...");
        
        // ❌ 错误：执行HTTP请求
        String response = httpClient.get("http://...");
    }
}
```

**正确示例**：
```java
public class GoodHandler extends ChannelInboundHandlerAdapter {
    private ExecutorService executor = Executors.newFixedThreadPool(10);
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // ✅ 正确：提交到业务线程池
        executor.submit(() -> {
            // 执行耗时操作
            String result = database.query("SELECT ...");
            
            // 回到EventLoop线程
            ctx.channel().eventLoop().execute(() -> {
                ctx.writeAndFlush(result);
            });
        });
    }
}
```

#### 2.4.2 合理配置EventLoopGroup大小

**经验公式**：
```java
// I/O密集型应用
int ioThreads = Runtime.getRuntime().availableProcessors() * 2;

// CPU密集型应用
int cpuThreads = Runtime.getRuntime().availableProcessors();

// 混合型应用
int mixedThreads = Runtime.getRuntime().availableProcessors() * 1.5;
```

**压测调优**：
```java
// 从小到大逐步调整
EventLoopGroup workerGroup = new NioEventLoopGroup(4);   // 初始值
// 压测，观察CPU使用率和QPS
// 如果CPU未满，增加线程数
// 如果CPU已满，减少线程数或优化代码
```

---

## 三、Channel通道

### 3.1 Channel的作用

**Channel是什么？**
- Netty对网络I/O操作的抽象
- 代表一个网络连接
- 提供异步的I/O操作

**Channel vs Socket**：

| 对比项 | Socket | Channel |
|-------|--------|---------|
| **抽象层次** | 低（操作系统层面） | 高（应用层面） |
| **I/O模型** | 阻塞/非阻塞 | 异步 |
| **API风格** | 同步 | 异步（返回Future） |
| **功能** | 基础I/O | I/O + Pipeline + EventLoop |

### 3.2 Channel的类型

#### 3.2.1 服务端Channel

**NioServerSocketChannel**：
```java
// 用于监听客户端连接
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.channel(NioServerSocketChannel.class);  // 指定Channel类型

// 底层封装了ServerSocketChannel
// 监听OP_ACCEPT事件
// 接收新连接后，创建NioSocketChannel
```

**特点**：
- 只负责接收连接，不传输数据
- 绑定到BossGroup的EventLoop
- 每个端口对应一个NioServerSocketChannel

#### 3.2.2 客户端Channel

**NioSocketChannel**：
```java
// 用于数据传输
Bootstrap bootstrap = new Bootstrap();
bootstrap.channel(NioSocketChannel.class);  // 指定Channel类型

// 底层封装了SocketChannel
// 监听OP_READ/OP_WRITE事件
// 负责数据的读写
```

**特点**：
- 负责数据传输
- 绑定到WorkerGroup的EventLoop
- 每个连接对应一个NioSocketChannel

#### 3.2.3 其他Channel类型

```java
// OioServerSocketChannel：基于BIO的实现（已废弃）
bootstrap.channel(OioServerSocketChannel.class);

// EpollServerSocketChannel：基于Linux epoll的实现（性能更好）
bootstrap.channel(EpollServerSocketChannel.class);

// KQueueServerSocketChannel：基于BSD kqueue的实现
bootstrap.channel(KQueueServerSocketChannel.class);

// LocalServerChannel：用于JVM内部通信
bootstrap.channel(LocalServerChannel.class);
```

**如何选择Channel类型？**

| 平台 | 推荐Channel | 说明 |
|------|------------|------|
| **Linux** | EpollServerSocketChannel | 性能最好 |
| **BSD/macOS** | KQueueServerSocketChannel | 性能较好 |
| **Windows** | NioServerSocketChannel | 跨平台 |
| **跨平台** | NioServerSocketChannel | 兼容性好 |

### 3.3 Channel的生命周期

```
Channel的生命周期：
┌─────────────────────────────────────────────────────────┐
│ 1. ChannelRegistered                                    │
│    - Channel注册到EventLoop                             │
│    - 触发channelRegistered事件                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. ChannelActive                                        │
│    - Channel激活（连接建立或绑定端口）                   │
│    - 触发channelActive事件                              │
│    - 可以开始读写数据                                    │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. ChannelRead                                          │
│    - Channel可读（有数据到达）                           │
│    - 触发channelRead事件                                │
│    - 读取数据并处理                                      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. ChannelInactive                                      │
│    - Channel失活（连接断开）                             │
│    - 触发channelInactive事件                            │
│    - 不能再读写数据                                      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. ChannelUnregistered                                  │
│    - Channel从EventLoop注销                             │
│    - 触发channelUnregistered事件                        │
│    - 生命周期结束                                        │
└─────────────────────────────────────────────────────────┘
```

**生命周期事件的处理**：
```java
public class LifecycleHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        System.out.println("Channel注册到EventLoop");
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("Channel激活，可以开始通信");
        // 发送欢迎消息
        ctx.writeAndFlush("Welcome!");
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("接收到数据: " + msg);
        // 处理数据
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("Channel失活，连接断开");
        // 清理资源
    }
    
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        System.out.println("Channel从EventLoop注销");
    }
}
```

### 3.4 Channel的核心方法

#### 3.4.1 读写操作

```java
// 写数据（不刷新）
ChannelFuture future = channel.write(msg);
// 数据写入到缓冲区，不会立即发送

// 刷新缓冲区
channel.flush();
// 将缓冲区的数据发送到网络

// 写并刷新（常用）
ChannelFuture future = channel.writeAndFlush(msg);
// 等价于 write() + flush()
```

**为什么要分write和flush？**

**场景**：批量发送多条消息
```java
// ❌ 低效：每次都刷新
for (int i = 0; i < 100; i++) {
    channel.writeAndFlush(msg);  // 100次系统调用
}

// ✅ 高效：批量刷新
for (int i = 0; i < 100; i++) {
    channel.write(msg);  // 写入缓冲区
}
channel.flush();  // 一次系统调用
```

#### 3.4.2 关闭操作

```java
// 关闭Channel
ChannelFuture future = channel.close();
// 立即关闭，不等待数据发送完成

// 优雅关闭
ChannelFuture future = channel.writeAndFlush(msg)
    .addListener(ChannelFutureListener.CLOSE);
// 等待数据发送完成后关闭

// 关闭并等待
channel.close().sync();
// 阻塞等待关闭完成
```

#### 3.4.3 状态查询

```java
// 是否打开
boolean isOpen = channel.isOpen();

// 是否激活
boolean isActive = channel.isActive();

// 是否可写
boolean isWritable = channel.isWritable();
// 当写缓冲区超过高水位时，返回false

// 是否已注册
boolean isRegistered = channel.isRegistered();
```

### 3.5 ChannelFuture异步结果

**为什么需要ChannelFuture？**

**问题**：Netty的I/O操作都是异步的
```java
// write()立即返回，不等待数据发送完成
ChannelFuture future = channel.writeAndFlush(msg);
// 此时数据可能还没发送完成
```

**解决方案**：通过ChannelFuture获取异步结果

#### 3.5.1 添加监听器

```java
// 方式1：添加监听器
channel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
    @Override
    public void operationComplete(ChannelFuture future) {
        if (future.isSuccess()) {
            System.out.println("发送成功");
        } else {
            System.out.println("发送失败: " + future.cause());
        }
    }
});

// 方式2：使用Lambda
channel.writeAndFlush(msg).addListener(future -> {
    if (future.isSuccess()) {
        System.out.println("发送成功");
    }
});

// 方式3：使用内置监听器
channel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
// 发送完成后关闭连接

channel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
// 发送失败后关闭连接
```

#### 3.5.2 同步等待

```java
// 等待操作完成
ChannelFuture future = channel.writeAndFlush(msg);
future.sync();  // 阻塞等待
if (future.isSuccess()) {
    System.out.println("发送成功");
}

// 等待操作完成（带超时）
future.await(5, TimeUnit.SECONDS);
if (future.isDone()) {
    System.out.println("操作完成");
}
```

**注意**：不要在EventLoop线程中调用sync()或await()
```java
// ❌ 错误：在EventLoop线程中调用sync()
ctx.channel().eventLoop().execute(() -> {
    ChannelFuture future = ctx.writeAndFlush(msg);
    future.sync();  // 死锁！EventLoop等待自己
});

// ✅ 正确：使用监听器
ctx.writeAndFlush(msg).addListener(future -> {
    if (future.isSuccess()) {
        System.out.println("发送成功");
    }
});
```

---

## 四、核心问题总结

### Q1：Bootstrap的作用是什么？

**答**：
1. **配置Netty应用**：统一配置线程组、Channel类型、选项、处理器
2. **启动应用**：bind()启动服务端，connect()连接服务端
3. **简化开发**：提供流畅的Builder API，减少样板代码

### Q2：EventLoop的线程模型是怎样的？

**答**：
1. **单线程EventLoop**：每个EventLoop绑定一个线程
2. **多Channel复用**：一个EventLoop可以处理多个Channel
3. **主从Reactor**：BossGroup接收连接，WorkerGroup处理I/O
4. **无锁化设计**：每个Channel只在一个EventLoop中处理

### Q3：为什么EventLoop要用单线程？

**答**：
1. **无锁化**：避免锁竞争，提高性能
2. **顺序性**：事件按顺序处理，不会出现并发问题
3. **简单性**：代码简单，易于理解和维护

### Q4：Channel的生命周期有哪些阶段？

**答**：
1. **ChannelRegistered**：注册到EventLoop
2. **ChannelActive**：激活，可以开始通信
3. **ChannelRead**：读取数据
4. **ChannelInactive**：失活，连接断开
5. **ChannelUnregistered**：从EventLoop注销

### Q5：如何正确使用ChannelFuture？

**答**：
1. **添加监听器**：通过addListener()处理异步结果
2. **避免阻塞**：不要在EventLoop线程中调用sync()或await()
3. **使用内置监听器**：CLOSE、CLOSE_ON_FAILURE等

---

## 五、下一步学习

在掌握了Bootstrap、EventLoop、Channel的核心概念后，下一章我们将学习：

**第3章：ChannelPipeline与ChannelHandler**
- Pipeline的工作原理
- Handler的类型和执行顺序
- 如何正确使用Pipeline和Handler

**实践任务**：
1. 实现一个简单的Echo服务器
2. 观察Channel的生命周期事件
3. 尝试提交任务到EventLoop

---

**继续学习**：[03_ChannelPipeline与ChannelHandler](./03_ChannelPipeline与ChannelHandler.md)
