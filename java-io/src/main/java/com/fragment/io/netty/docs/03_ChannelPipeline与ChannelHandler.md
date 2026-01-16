# 03_ChannelPipeline与ChannelHandler

> **核心问题**：ChannelPipeline的执行流程是什么？入站和出站事件如何传播？如何正确使用Handler？

---

## 一、ChannelPipeline的工作原理

### 1.1 Pipeline是什么？

**ChannelPipeline的定义**：
- 一个双向链表，管理ChannelHandler
- 负责事件的传播和处理
- 每个Channel都有一个Pipeline

**为什么需要Pipeline？**

**问题**：如果不用Pipeline，直接在一个类中处理所有逻辑？
```java
// 不使用Pipeline的问题
public class AllInOneHandler {
    public void handleData(ByteBuf data) {
        // 1. 解码
        String message = decode(data);
        
        // 2. 业务处理
        String result = processBusinessLogic(message);
        
        // 3. 编码
        ByteBuf response = encode(result);
        
        // 4. 发送
        channel.write(response);
    }
}

// 问题：
// 1. 代码耦合：解码、业务、编码混在一起
// 2. 难以复用：无法复用解码器和编码器
// 3. 难以测试：无法单独测试某个环节
// 4. 难以扩展：添加新功能需要修改整个类
```

**解决方案**：责任链模式
```java
// 使用Pipeline的优势
pipeline.addLast("decoder", new StringDecoder());      // 解码器
pipeline.addLast("business", new BusinessHandler());   // 业务处理
pipeline.addLast("encoder", new StringEncoder());      // 编码器

// 优势：
// 1. 职责分离：每个Handler只负责一件事
// 2. 灵活组合：可以自由组合不同的Handler
// 3. 易于复用：解码器和编码器可以复用
// 4. 易于扩展：添加新Handler即可
```

### 1.2 Pipeline的结构

```
Pipeline的双向链表结构：
┌────────────────────────────────────────────────────────────┐
│                     ChannelPipeline                        │
│                                                            │
│  Head ←→ Handler1 ←→ Handler2 ←→ Handler3 ←→ ... ←→ Tail  │
│   ↑                                                    ↑   │
│   │                                                    │   │
│  入站事件从Head开始                      出站事件从Tail开始  │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**核心组件**：
- **Head**：Pipeline的头节点，负责I/O操作
- **Tail**：Pipeline的尾节点，负责异常处理
- **ChannelHandlerContext**：Handler的上下文，包装Handler

**ChannelHandlerContext的作用**：
```java
// Context包装Handler，提供额外功能
public interface ChannelHandlerContext {
    Channel channel();              // 获取Channel
    EventLoop eventLoop();          // 获取EventLoop
    ChannelPipeline pipeline();     // 获取Pipeline
    
    // 事件传播
    ChannelHandlerContext fireChannelRead(Object msg);
    ChannelHandlerContext fireChannelActive();
    
    // I/O操作
    ChannelFuture write(Object msg);
    ChannelFuture writeAndFlush(Object msg);
}
```

### 1.3 入站事件和出站事件

#### 1.3.1 入站事件（Inbound）

**定义**：从外部（网络）到内部（应用）的事件

**常见入站事件**：
```java
// 1. channelRegistered：Channel注册到EventLoop
void channelRegistered(ChannelHandlerContext ctx);

// 2. channelActive：Channel激活，连接建立
void channelActive(ChannelHandlerContext ctx);

// 3. channelRead：读取到数据
void channelRead(ChannelHandlerContext ctx, Object msg);

// 4. channelReadComplete：读取完成
void channelReadComplete(ChannelHandlerContext ctx);

// 5. channelInactive：Channel失活，连接断开
void channelInactive(ChannelHandlerContext ctx);

// 6. channelUnregistered：Channel从EventLoop注销
void channelUnregistered(ChannelHandlerContext ctx);

// 7. exceptionCaught：异常捕获
void exceptionCaught(ChannelHandlerContext ctx, Throwable cause);
```

**入站事件的传播方向**：
```
入站事件传播：Head → Handler1 → Handler2 → Handler3 → Tail
                ↓         ↓         ↓         ↓
            fireXxx()  fireXxx()  fireXxx()  fireXxx()
```

#### 1.3.2 出站事件（Outbound）

**定义**：从内部（应用）到外部（网络）的事件

**常见出站事件**：
```java
// 1. bind：绑定端口
void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise);

// 2. connect：连接服务器
void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, ChannelPromise promise);

// 3. write：写数据
void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise);

// 4. flush：刷新缓冲区
void flush(ChannelHandlerContext ctx);

// 5. read：读取数据
void read(ChannelHandlerContext ctx);

// 6. disconnect：断开连接
void disconnect(ChannelHandlerContext ctx, ChannelPromise promise);

// 7. close：关闭连接
void close(ChannelHandlerContext ctx, ChannelPromise promise);
```

**出站事件的传播方向**：
```
出站事件传播：Tail ← Handler3 ← Handler2 ← Handler1 ← Head
                ↑         ↑         ↑         ↑
              write()   write()   write()   write()
```

### 1.4 完整的事件传播流程

```
完整的Pipeline事件流：
┌──────────────────────────────────────────────────────────────┐
│                        Pipeline                              │
│                                                              │
│  ┌──────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────┐│
│  │ Head │→ │ Decoder  │→ │ Business │→ │ Encoder  │← │Tail││
│  │      │← │ (Inbound)│  │ (Inbound)│  │(Outbound)│  │    ││
│  └──────┘  └──────────┘  └──────────┘  └──────────┘  └────┘│
│     ↑                                                    ↓   │
│     │                                                    │   │
│  Socket                                              Socket  │
│  (读取)                                              (写入)  │
└──────────────────────────────────────────────────────────────┘

入站流程（读取数据）：
1. Socket读取数据 → Head
2. Head → Decoder（解码）
3. Decoder → Business（业务处理）
4. Business → Tail（结束）

出站流程（写入数据）：
1. Business调用ctx.write() → Encoder
2. Encoder（编码）→ Head
3. Head → Socket写入数据
```

**为什么入站和出站方向相反？**

**设计理由**：
- **入站**：数据从网络进来，需要先解码再处理
- **出站**：数据要发送到网络，需要先处理再编码
- **分离关注点**：解码器只关心入站，编码器只关心出站

---

## 二、ChannelHandler详解

### 2.1 Handler的类型

#### 2.1.1 ChannelInboundHandler（入站处理器）

**作用**：处理入站事件

**接口定义**：
```java
public interface ChannelInboundHandler extends ChannelHandler {
    void channelRegistered(ChannelHandlerContext ctx);
    void channelUnregistered(ChannelHandlerContext ctx);
    void channelActive(ChannelHandlerContext ctx);
    void channelInactive(ChannelHandlerContext ctx);
    void channelRead(ChannelHandlerContext ctx, Object msg);
    void channelReadComplete(ChannelHandlerContext ctx);
    void userEventTriggered(ChannelHandlerContext ctx, Object evt);
    void channelWritabilityChanged(ChannelHandlerContext ctx);
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause);
}
```

**常用实现**：
```java
// 1. ChannelInboundHandlerAdapter：适配器，提供默认实现
public class MyInboundHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 处理数据
        System.out.println("接收到数据: " + msg);
        
        // 传递给下一个Handler
        ctx.fireChannelRead(msg);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 处理异常
        cause.printStackTrace();
        ctx.close();
    }
}

// 2. SimpleChannelInboundHandler：自动释放消息
public class MySimpleHandler extends SimpleChannelInboundHandler<String> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 处理数据
        System.out.println("接收到数据: " + msg);
        // 不需要手动释放msg，框架会自动释放
    }
}
```

**SimpleChannelInboundHandler vs ChannelInboundHandlerAdapter**：

| 对比项 | ChannelInboundHandlerAdapter | SimpleChannelInboundHandler |
|-------|----------------------------|---------------------------|
| **消息释放** | 需要手动释放 | 自动释放 |
| **类型检查** | 无 | 有（泛型） |
| **适用场景** | 需要传递消息给下一个Handler | 消息在此Handler处理完毕 |

#### 2.1.2 ChannelOutboundHandler（出站处理器）

**作用**：处理出站事件

**接口定义**：
```java
public interface ChannelOutboundHandler extends ChannelHandler {
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise);
    void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, 
                 SocketAddress localAddress, ChannelPromise promise);
    void disconnect(ChannelHandlerContext ctx, ChannelPromise promise);
    void close(ChannelHandlerContext ctx, ChannelPromise promise);
    void deregister(ChannelHandlerContext ctx, ChannelPromise promise);
    void read(ChannelHandlerContext ctx);
    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise);
    void flush(ChannelHandlerContext ctx);
}
```

**常用实现**：
```java
// ChannelOutboundHandlerAdapter：适配器
public class MyOutboundHandler extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // 处理写操作
        System.out.println("写入数据: " + msg);
        
        // 传递给下一个Handler
        ctx.write(msg, promise);
    }
}
```

#### 2.1.3 ChannelDuplexHandler（双向处理器）

**作用**：同时处理入站和出站事件

**使用场景**：需要同时处理读和写的Handler

```java
public class MyDuplexHandler extends ChannelDuplexHandler {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 处理入站事件
        System.out.println("读取数据: " + msg);
        ctx.fireChannelRead(msg);
    }
    
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // 处理出站事件
        System.out.println("写入数据: " + msg);
        ctx.write(msg, promise);
    }
}
```

### 2.2 Handler的执行顺序

#### 2.2.1 入站Handler的执行顺序

```java
// 添加Handler
pipeline.addLast("decoder", new StringDecoder());      // Handler1
pipeline.addLast("handler1", new MyHandler1());        // Handler2
pipeline.addLast("handler2", new MyHandler2());        // Handler3
pipeline.addLast("encoder", new StringEncoder());      // Handler4

// 入站事件执行顺序：
// 1. StringDecoder（解码）
// 2. MyHandler1（业务处理1）
// 3. MyHandler2（业务处理2）
// 4. StringEncoder（不执行，因为是OutboundHandler）
```

**关键点**：
- 入站事件只会经过InboundHandler
- 按照addLast的顺序执行
- 如果不调用fireChannelRead()，事件不会传播

#### 2.2.2 出站Handler的执行顺序

```java
// 添加Handler（同上）
pipeline.addLast("decoder", new StringDecoder());      // Handler1
pipeline.addLast("handler1", new MyHandler1());        // Handler2
pipeline.addLast("handler2", new MyHandler2());        // Handler3
pipeline.addLast("encoder", new StringEncoder());      // Handler4

// 出站事件执行顺序（从调用write的位置开始，向前查找）：
// 假设在MyHandler2中调用ctx.write()
// 1. MyHandler2 → 向前查找OutboundHandler
// 2. StringEncoder（编码）
// 3. Head（写入Socket）

// StringDecoder、MyHandler1不会执行，因为是InboundHandler
```

**关键点**：
- 出站事件只会经过OutboundHandler
- 从调用write的位置开始，向前查找
- 按照addLast的逆序执行

#### 2.2.3 完整示例

```java
public class PipelineOrderDemo {
    public static void main(String[] args) {
        ChannelPipeline pipeline = channel.pipeline();
        
        // 添加Handler
        pipeline.addLast("h1", new InboundHandler1());   // 入站
        pipeline.addLast("h2", new OutboundHandler1());  // 出站
        pipeline.addLast("h3", new InboundHandler2());   // 入站
        pipeline.addLast("h4", new OutboundHandler2());  // 出站
        
        // 入站事件执行顺序：h1 → h3
        // 出站事件执行顺序：h4 → h2
    }
}

class InboundHandler1 extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("InboundHandler1.channelRead");
        ctx.fireChannelRead(msg);  // 传递给下一个InboundHandler
    }
}

class InboundHandler2 extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("InboundHandler2.channelRead");
        
        // 调用write，触发出站事件
        ctx.writeAndFlush(msg);
    }
}

class OutboundHandler1 extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        System.out.println("OutboundHandler1.write");
        ctx.write(msg, promise);  // 传递给下一个OutboundHandler
    }
}

class OutboundHandler2 extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        System.out.println("OutboundHandler2.write");
        ctx.write(msg, promise);  // 传递给下一个OutboundHandler
    }
}

// 输出：
// InboundHandler1.channelRead
// InboundHandler2.channelRead
// OutboundHandler2.write
// OutboundHandler1.write
```

### 2.3 ctx.write() vs channel.write()

**关键区别**：
```java
public class MyHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 方式1：从当前Handler开始，向前查找OutboundHandler
        ctx.write(msg);
        
        // 方式2：从Tail开始，向前查找OutboundHandler
        ctx.channel().write(msg);
    }
}
```

**示例**：
```java
pipeline.addLast("encoder1", new Encoder1());  // 出站
pipeline.addLast("handler", new MyHandler());  // 入站
pipeline.addLast("encoder2", new Encoder2());  // 出站

// 在MyHandler中：
// ctx.write(msg)：只会经过encoder1（向前查找）
// ctx.channel().write(msg)：会经过encoder2和encoder1（从Tail开始）
```

**如何选择？**

| 场景 | 使用 | 说明 |
|------|------|------|
| **传递给下一个Handler** | ctx.fireChannelRead() | 入站事件 |
| **传递给下一个Handler** | ctx.write() | 出站事件 |
| **从头开始处理** | ctx.channel().write() | 需要经过所有Handler |
| **性能优化** | ctx.write() | 减少Handler遍历 |

---

## 三、Pipeline的动态管理

### 3.1 添加Handler

```java
// 1. addLast：添加到末尾
pipeline.addLast("handler1", new MyHandler1());

// 2. addFirst：添加到开头
pipeline.addFirst("handler0", new MyHandler0());

// 3. addBefore：添加到指定Handler之前
pipeline.addBefore("handler1", "handler0.5", new MyHandler());

// 4. addAfter：添加到指定Handler之后
pipeline.addAfter("handler1", "handler1.5", new MyHandler());

// 5. 指定EventExecutorGroup
EventExecutorGroup group = new DefaultEventExecutorGroup(4);
pipeline.addLast(group, "handler", new MyHandler());
// Handler在独立的线程池中执行，不阻塞EventLoop
```

### 3.2 删除Handler

```java
// 1. remove：删除指定Handler
pipeline.remove("handler1");
pipeline.remove(MyHandler.class);

// 2. removeFirst：删除第一个Handler
pipeline.removeFirst();

// 3. removeLast：删除最后一个Handler
pipeline.removeLast();

// 4. 在Handler中删除自己
public class OneTimeHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 处理一次后删除自己
        processMessage(msg);
        ctx.pipeline().remove(this);
    }
}
```

### 3.3 替换Handler

```java
// replace：替换Handler
pipeline.replace("oldHandler", "newHandler", new NewHandler());
pipeline.replace(OldHandler.class, "newHandler", new NewHandler());

// 应用场景：协议升级
public class ProtocolUpgradeHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (isUpgradeRequest(msg)) {
            // 替换为新协议的Handler
            ctx.pipeline().replace(this, "newProtocol", new NewProtocolHandler());
        }
    }
}
```

### 3.4 查询Handler

```java
// 1. get：获取Handler
ChannelHandler handler = pipeline.get("handler1");
MyHandler handler = (MyHandler) pipeline.get(MyHandler.class);

// 2. context：获取Context
ChannelHandlerContext ctx = pipeline.context("handler1");
ChannelHandlerContext ctx = pipeline.context(MyHandler.class);

// 3. first/last：获取第一个/最后一个Handler
ChannelHandler first = pipeline.first();
ChannelHandler last = pipeline.last();

// 4. names：获取所有Handler的名称
List<String> names = pipeline.names();
```

---

## 四、Handler的最佳实践

### 4.1 资源释放

#### 4.1.1 ByteBuf的释放

**问题**：忘记释放ByteBuf导致内存泄漏
```java
// ❌ 错误：忘记释放
public class BadHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        // 处理数据
        processData(buf);
        // 忘记释放，导致内存泄漏
    }
}
```

**解决方案1：手动释放**
```java
// ✅ 正确：手动释放
public class GoodHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            // 处理数据
            processData(buf);
        } finally {
            // 释放ByteBuf
            ReferenceCountUtil.release(msg);
        }
    }
}
```

**解决方案2：使用SimpleChannelInboundHandler**
```java
// ✅ 更好：自动释放
public class BetterHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // 处理数据
        processData(msg);
        // 框架自动释放msg
    }
}
```

**解决方案3：传递给下一个Handler**
```java
// ✅ 正确：传递给下一个Handler
public class PassThroughHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 处理数据
        processData((ByteBuf) msg);
        
        // 传递给下一个Handler，由下一个Handler负责释放
        ctx.fireChannelRead(msg);
    }
}
```

**释放规则**：
- **谁最后使用，谁负责释放**
- **如果传递给下一个Handler，不要释放**
- **如果不传递，必须释放**

#### 4.1.2 内存泄漏检测

**启用内存泄漏检测**：
```java
// 在JVM启动参数中添加
-Dio.netty.leakDetection.level=ADVANCED

// 检测级别：
// DISABLED：禁用
// SIMPLE：默认，抽样检测1%
// ADVANCED：抽样检测1%，提供详细信息
// PARANOID：检测所有对象，性能影响大
```

**检测到泄漏的输出**：
```
LEAK: ByteBuf.release() was not called before it's garbage-collected.
Recent access records:
#1:
	io.netty.buffer.AdvancedLeakAwareByteBuf.writeBytes(AdvancedLeakAwareByteBuf.java:589)
	com.example.MyHandler.channelRead(MyHandler.java:25)
```

### 4.2 异常处理

#### 4.2.1 入站异常处理

**异常传播规则**：
- 入站异常会沿着Pipeline向后传播
- 如果没有Handler处理，最终到达Tail
- Tail会打印警告日志

**示例**：
```java
public class Handler1 extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 抛出异常
        throw new RuntimeException("Handler1异常");
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 不处理，传递给下一个Handler
        ctx.fireExceptionCaught(cause);
    }
}

public class Handler2 extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 处理异常
        System.err.println("捕获异常: " + cause.getMessage());
        ctx.close();  // 关闭连接
    }
}
```

**最佳实践**：在Pipeline末尾添加异常处理Handler
```java
pipeline.addLast("decoder", new StringDecoder());
pipeline.addLast("handler", new BusinessHandler());
pipeline.addLast("encoder", new StringEncoder());
// 在末尾添加异常处理器
pipeline.addLast("exceptionHandler", new ExceptionHandler());

public class ExceptionHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 统一处理异常
        if (cause instanceof IOException) {
            System.err.println("网络异常: " + cause.getMessage());
        } else {
            System.err.println("未知异常: " + cause.getMessage());
            cause.printStackTrace();
        }
        ctx.close();
    }
}
```

#### 4.2.2 出站异常处理

**出站异常处理方式**：
```java
// 方式1：通过ChannelFuture处理
ChannelFuture future = ctx.writeAndFlush(msg);
future.addListener(new ChannelFutureListener() {
    @Override
    public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
            // 处理写入失败
            Throwable cause = future.cause();
            System.err.println("写入失败: " + cause.getMessage());
        }
    }
});

// 方式2：通过ChannelPromise处理
ChannelPromise promise = ctx.newPromise();
promise.addListener(new ChannelFutureListener() {
    @Override
    public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
            // 处理写入失败
            System.err.println("写入失败: " + future.cause().getMessage());
        }
    }
});
ctx.writeAndFlush(msg, promise);
```

### 4.3 Handler的共享

#### 4.3.1 @Sharable注解

**默认情况**：每个Channel都有独立的Handler实例
```java
// 每个连接都创建新的Handler实例
pipeline.addLast(new MyHandler());
```

**共享Handler**：多个Channel共享同一个Handler实例
```java
@Sharable
public class SharedHandler extends ChannelInboundHandlerAdapter {
    private AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 统计所有连接的消息数
        int count = counter.incrementAndGet();
        System.out.println("总消息数: " + count);
        ctx.fireChannelRead(msg);
    }
}

// 创建一个实例，所有Channel共享
SharedHandler sharedHandler = new SharedHandler();
pipeline.addLast(sharedHandler);
```

**注意事项**：
- **线程安全**：共享Handler必须是线程安全的
- **无状态**：避免使用实例变量存储Channel相关的状态
- **性能**：减少对象创建，提高性能

**何时使用共享Handler？**

| 场景 | 是否共享 | 说明 |
|------|---------|------|
| **无状态Handler** | 共享 | 如编解码器 |
| **统计信息** | 共享 | 如计数器、监控 |
| **有状态Handler** | 不共享 | 如会话管理 |
| **需要Channel上下文** | 不共享 | 如连接管理 |

### 4.4 Handler的执行线程

#### 4.4.1 默认在EventLoop线程执行

```java
// 默认情况：Handler在EventLoop线程中执行
pipeline.addLast("handler", new MyHandler());

// 优点：无锁，性能好
// 缺点：不能执行耗时操作，会阻塞EventLoop
```

#### 4.4.2 在独立线程池执行

```java
// 创建独立的线程池
EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(10);

// Handler在独立线程池中执行
pipeline.addLast(businessGroup, "handler", new BusinessHandler());

// 优点：不阻塞EventLoop
// 缺点：线程切换开销，需要考虑线程安全
```

**应用场景**：
```java
// 场景1：数据库查询
EventExecutorGroup dbGroup = new DefaultEventExecutorGroup(10);
pipeline.addLast(dbGroup, "dbHandler", new DatabaseHandler());

// 场景2：HTTP请求
EventExecutorGroup httpGroup = new DefaultEventExecutorGroup(10);
pipeline.addLast(httpGroup, "httpHandler", new HttpClientHandler());

// 场景3：复杂计算
EventExecutorGroup computeGroup = new DefaultEventExecutorGroup(4);
pipeline.addLast(computeGroup, "computeHandler", new ComputeHandler());
```

**注意事项**：
```java
public class BusinessHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 在业务线程池中执行
        System.out.println("业务线程: " + Thread.currentThread().getName());
        
        // 执行耗时操作
        String result = doSlowOperation();
        
        // 写回数据（会切换到EventLoop线程）
        ctx.writeAndFlush(result);
    }
}
```

---

## 五、常见使用模式

### 5.1 编解码模式

```java
// 标准的编解码Pipeline
pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4));
pipeline.addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
pipeline.addLast("handler", new BusinessHandler());
pipeline.addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));
pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));

// 流程：
// 入站：frameDecoder → decoder → handler
// 出站：handler → encoder → frameEncoder
```

### 5.2 业务处理模式

```java
// 分层处理
pipeline.addLast("decoder", new MessageDecoder());
pipeline.addLast("auth", new AuthHandler());           // 认证
pipeline.addLast("validate", new ValidateHandler());   // 验证
pipeline.addLast("business", new BusinessHandler());   // 业务
pipeline.addLast("encoder", new MessageEncoder());

// 每个Handler负责一个职责
```

### 5.3 协议切换模式

```java
// 初始Pipeline
pipeline.addLast("httpDecoder", new HttpRequestDecoder());
pipeline.addLast("httpHandler", new HttpServerHandler());

// 在HttpServerHandler中检测WebSocket升级请求
public class HttpServerHandler extends SimpleChannelInboundHandler<HttpRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {
        if (isWebSocketUpgrade(msg)) {
            // 切换到WebSocket协议
            ctx.pipeline().remove(HttpRequestDecoder.class);
            ctx.pipeline().remove(this);
            ctx.pipeline().addLast(new WebSocketServerProtocolHandler("/ws"));
            ctx.pipeline().addLast(new WebSocketHandler());
        }
    }
}
```

---

## 六、核心问题总结

### Q1：ChannelPipeline的执行流程是什么？

**答**：
1. **入站事件**：从Head开始，依次经过InboundHandler，到达Tail
2. **出站事件**：从调用write的位置开始，向前查找OutboundHandler，到达Head
3. **事件传播**：通过fireXxx()或ctx.write()传播事件

### Q2：入站和出站Handler的执行顺序是什么？

**答**：
- **入站**：按照addLast的顺序执行
- **出站**：按照addLast的逆序执行
- **关键**：入站只经过InboundHandler，出站只经过OutboundHandler

### Q3：ctx.write() 和 channel.write() 有什么区别？

**答**：
- **ctx.write()**：从当前Handler开始，向前查找OutboundHandler
- **channel.write()**：从Tail开始，向前查找OutboundHandler
- **选择**：通常使用ctx.write()，性能更好

### Q4：如何正确释放ByteBuf？

**答**：
1. **手动释放**：使用ReferenceCountUtil.release()
2. **自动释放**：使用SimpleChannelInboundHandler
3. **传递释放**：传递给下一个Handler，由下一个Handler释放
4. **规则**：谁最后使用，谁负责释放

### Q5：如何处理异常？

**答**：
1. **入站异常**：在exceptionCaught()中处理，或传递给下一个Handler
2. **出站异常**：通过ChannelFuture或ChannelPromise处理
3. **最佳实践**：在Pipeline末尾添加统一的异常处理Handler

---

## 七、下一步学习

在掌握了Pipeline和Handler的核心概念后，下一章我们将学习：

**第4章：编解码器与粘包拆包问题**
- 什么是粘包拆包问题
- Netty内置的编解码器
- 如何自定义编解码器

**实践任务**：
1. 实现一个完整的Pipeline，包含解码、业务处理、编码
2. 观察入站和出站Handler的执行顺序
3. 实现一个共享的统计Handler

---

**继续学习**：[04_编解码器与粘包拆包问题](./04_编解码器与粘包拆包问题.md)
