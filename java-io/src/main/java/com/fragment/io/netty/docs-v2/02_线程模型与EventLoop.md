# 第二章：线程模型与 EventLoop

## 2.1 Reactor 线程模型

Netty 的线程模型基于 **Reactor 模式**，有三种变体：

```
单 Reactor 单线程（不用）
┌──────────────────────────────┐
│  Reactor（Selector）          │
│  Accept + Read/Write + 业务  │  ← 全部单线程，业务阻塞会影响 I/O
└──────────────────────────────┘

单 Reactor 多线程（Netty 客户端模式）
┌─────────────────┐    ┌─────────────────────┐
│ Reactor（单线程）│→→→ │  业务线程池（多线程）  │
│ Accept+Read/Write│    │  处理业务逻辑         │
└─────────────────┘    └─────────────────────┘

主从 Reactor 多线程（Netty 服务端默认，最佳实践）
┌─────────────────┐    ┌─────────────────────┐    ┌──────────────┐
│ Boss Reactor     │→→→│ Worker Reactor（多）  │→→→│ 业务线程池    │
│ 只负责 Accept    │    │ 负责 Read/Write       │    │ 处理耗时业务  │
└─────────────────┘    └─────────────────────┘    └──────────────┘
```

---

## 2.2 EventLoop：Netty 的核心引擎

**一个 EventLoop = 一个线程 + 一个 Selector**，负责：

```
1. 监听 I/O 事件（通过 Selector.select()）
2. 处理 I/O 事件（读/写/连接/断开）
3. 执行提交到本 EventLoop 的任务（runAllTasks()）
4. 执行定时任务
```

**关键约束**：**Channel 绑定的 EventLoop 永不改变**。Channel 上的所有 I/O 操作都由同一个线程执行，天然无锁。

```java
// EchoServerDemo.java 中的线程模型配置
NioEventLoopGroup boss   = new NioEventLoopGroup(1);   // Boss：1个线程，只处理 Accept
NioEventLoopGroup worker = new NioEventLoopGroup();     // Worker：默认 CPU*2 个线程

// 当一个连接建立时：
// 1. Boss EventLoop 的 Selector 检测到 OP_ACCEPT 事件
// 2. Boss 创建 SocketChannel，从 Worker 组中挑选一个 EventLoop
// 3. 将 SocketChannel 注册到该 Worker EventLoop 的 Selector
// 4. 此后该 Channel 的所有 I/O 事件都由这个 Worker EventLoop 处理（永久绑定）
```

---

## 2.3 EventLoop 的执行流程

```java
// EventLoop 的核心循环（简化版）
while (!terminated) {
    // 阶段1：I/O 轮询
    int selectedKeys = selector.select(calculateStrategy());

    // 阶段2：处理 I/O 事件
    if (selectedKeys > 0) {
        processSelectedKeys();   // 触发 Pipeline 的 channelRead 等事件
    }

    // 阶段3：执行任务队列中的任务
    runAllTasks(ioRatio);
    // ioRatio 控制 I/O 时间和任务时间的比例（默认 50：50）
}
```

**ioRatio 参数**（`-Dio.netty.eventloop.maxTaskExecutionTime`，默认 50）：
- `50` → I/O 处理时间 : 任务处理时间 = 1:1
- `100` → 每次循环尽量处理完所有任务（适合任务密集型场景）

---

## 2.4 Handler 中不能做耗时操作

```java
// ❌ 错误：在 EventLoop 线程中执行数据库查询（阻塞整个 EventLoop）
public class BadHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 这会阻塞 EventLoop 线程！同一个 EventLoop 上的其他 Channel 全部卡住！
        String result = database.query(msg.toString());  // 可能耗时 100ms
        ctx.writeAndFlush(result);
    }
}

// ✅ 正确：将耗时操作提交到业务线程池
public class GoodHandler extends ChannelInboundHandlerAdapter {
    private static final EventExecutorGroup BUSINESS_POOL =
        new DefaultEventExecutorGroup(16);  // 独立的业务线程池

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        BUSINESS_POOL.submit(() -> {
            String result = database.query(msg.toString());  // 在业务线程执行
            ctx.writeAndFlush(result);   // writeAndFlush 是线程安全的，可从任意线程调用
        });
    }
}

// 或者在 Pipeline 注册时为 Handler 指定执行器
pipeline.addLast(BUSINESS_POOL, "dbHandler", new DatabaseHandler());
```

---

## 2.5 ChannelFuture：异步操作的句柄

Netty 所有 I/O 操作都是异步的，返回 `ChannelFuture`：

```java
// ❌ 错误：同步等待（在 EventLoop 线程中调用 sync() 会死锁！）
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ctx.writeAndFlush(msg).sync();  // 死锁！EventLoop 线程在等待自己完成写操作
}

// ✅ 正确：添加监听器（回调方式，非阻塞）
ctx.writeAndFlush(msg).addListener(future -> {
    if (future.isSuccess()) {
        System.out.println("发送成功");
    } else {
        System.out.println("发送失败: " + future.cause().getMessage());
        ctx.close();
    }
});

// ✅ 在非 EventLoop 线程中可以 sync()（如 main 线程等待服务器关闭）
ChannelFuture f = bootstrap.bind(8080).sync();  // main 线程等待绑定完成
f.channel().closeFuture().sync();               // main 线程等待服务器关闭
```

---

## 2.6 线程安全规则

```
规则1：Channel 的所有 I/O 操作，Netty 会自动确保在绑定的 EventLoop 线程上执行
       → write/read/close 等操作可以从任意线程调用，Netty 内部会转移到正确的线程

规则2：Handler 的 channelRead、channelActive 等回调方法，总是在 EventLoop 线程中执行
       → 不需要加锁保护 Handler 的状态（只要 Handler 不是 @Sharable 或正确实现共享）

规则3：@Sharable 的 Handler 可以被多个 Channel 共享
       → 必须确保 Handler 是无状态的，或对共享状态正确加锁

规则4：耗时业务逻辑必须放入独立线程池，不能阻塞 EventLoop
```

---

## 2.7 本章总结

- **主从 Reactor**：Boss（1线程，Accept）+ Worker（CPU×2线程，I/O）是服务端标准配置
- **EventLoop = 线程 + Selector**：Channel 绑定后永不改变，天然无锁
- **执行流程**：select 轮询 → 处理 I/O 事件 → 执行任务队列，三阶段循环
- **黄金法则**：Handler 中不做耗时操作；耗时任务提交到独立业务线程池
- **ChannelFuture**：所有 I/O 异步，用 `addListener` 回调而非在 EventLoop 中调用 `sync()`

> **本章对应演示代码**：`EchoServerDemo.java`（Boss/Worker 线程组、ChannelFuture 监听器）、`HeartbeatDemo.java`（定时任务在 EventLoop 中执行）

**继续阅读**：[03_ChannelPipeline责任链.md](./03_ChannelPipeline责任链.md)
