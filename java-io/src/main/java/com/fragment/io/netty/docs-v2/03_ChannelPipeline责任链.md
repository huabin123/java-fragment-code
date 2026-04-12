# 第三章：ChannelPipeline 责任链

## 3.1 为什么需要 Pipeline？

网络数据从接收到响应，需要经过多个独立步骤：解码 → 业务处理 → 编码 → 发送。如果所有逻辑写在一个类里：

```java
// ❌ 没有 Pipeline 的写法：所有逻辑混在一起
public class AllInOneHandler {
    public void handleData(byte[] rawData) {
        String message = decode(rawData);      // 解码
        authenticate(message);                 // 鉴权
        String result = processLogic(message); // 业务
        byte[] response = encode(result);      // 编码
        channel.write(response);               // 发送
    }
}
// 问题：解码器无法复用、业务与协议耦合、无法单独测试某一步
```

Pipeline 用**责任链模式**解耦各层：

```java
// ✅ 使用 Pipeline，每个 Handler 只做一件事
pipeline.addLast("frameDecoder",   new LengthFieldBasedFrameDecoder(...)); // 拆包
pipeline.addLast("protobufDecoder",new ProtobufDecoder(...));               // 解码
pipeline.addLast("authHandler",    new AuthHandler());                      // 鉴权
pipeline.addLast("businessHandler",new BusinessHandler());                  // 业务
pipeline.addLast("protobufEncoder",new ProtobufEncoder());                  // 编码
```

---

## 3.2 Pipeline 的结构与事件流向

```
                         Channel Pipeline
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│    入站事件（Inbound）：数据从网络 → 应用                        │
│    ──────────────────────────────────────────────────────→    │
│    Socket │ Head │ Decoder │ AuthHandler │ BusinessHandler │ Tail │
│           │      │         │             │                 │      │
│    ←────────────────────────────────────────────────────────    │
│    出站事件（Outbound）：数据从应用 → 网络                        │
│    write/flush 从 Tail 向 Head 方向传播                          │
└────────────────────────────────────────────────────────────────┘

Head：最靠近 Socket，负责实际的字节读写
Tail ：最靠近业务，负责未处理消息的兜底（如释放 ByteBuf 防泄漏）
```

**`PipelineDemo.java`** 演示了事件在 Pipeline 中的完整传播过程。

---

## 3.3 ChannelHandler 的两种类型

### ChannelInboundHandler（入站处理器）

处理来自网络的数据和连接状态变更事件：

```java
public class MyInboundHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 连接建立时触发（在 channelRead 之前）
        System.out.println("连接建立: " + ctx.channel().remoteAddress());
        ctx.fireChannelActive();  // 继续向后传播（不调用则链条在此中断）
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 收到数据时触发
        // msg 的类型取决于前一个 Handler 的输出类型
        String text = (String) msg;  // 假设前面有 StringDecoder
        System.out.println("收到: " + text);
        ctx.fireChannelRead("处理后的" + text);  // 传给下一个 InboundHandler
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 连接断开时触发
        System.out.println("连接断开");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 异常处理（Pipeline 中应有且仅有一个兜底的异常处理 Handler）
        cause.printStackTrace();
        ctx.close();
    }
}
```

### ChannelOutboundHandler（出站处理器）

处理写操作：

```java
public class MyOutboundHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // 拦截写操作，可以在这里做编码、加密、限流等
        String text = (String) msg;
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        ctx.write(buf, promise);  // 继续向前（向 Head 方向）传播
    }
}
```

---

## 3.4 SimpleChannelInboundHandler：自动释放 msg

使用 `ChannelInboundHandlerAdapter` 时，必须手动释放 `msg`（如果是 `ByteBuf`）：

```java
// ❌ 容易忘记 release，导致内存泄漏
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ByteBuf buf = (ByteBuf) msg;
    // ... 处理 buf ...
    // 忘记 buf.release() → 堆外内存泄漏
}

// ✅ 使用 SimpleChannelInboundHandler，自动释放 msg
public class AutoReleaseHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // msg 在此方法返回后自动 release()，无需手动释放
        System.out.println("收到 " + msg.readableBytes() + " 字节");
    }
}

// 注意：SimpleChannelInboundHandler 会自动 release，如果你在此之后还需要用 msg
// （如传给另一个线程），需要先 msg.retain() 增加引用计数
```

---

## 3.5 Handler 的共享与状态

```java
// @Sharable：Handler 可以被多个 Pipeline 共享（节省内存）
// 前提：Handler 必须是无状态的（或对状态正确加锁）

// ✅ 无状态 Handler，可以共享
@ChannelHandler.Sharable
public class StatelessHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 只使用 ctx 中的 Channel 信息，不持有任何实例状态
        System.out.println("从 " + ctx.channel().remoteAddress() + " 收到消息");
        ctx.fireChannelRead(msg);
    }
}

// ❌ 有状态的 Handler，不能共享（每个 Channel 需要独立实例）
// 这样的 Handler 不要加 @Sharable
public class StatefulHandler extends ChannelInboundHandlerAdapter {
    private int messageCount = 0;  // 每个连接独立的计数器

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        messageCount++;  // 如果共享，多个 Channel 同时读会有竞态条件
        ctx.fireChannelRead(msg);
    }
}
```

---

## 3.6 ChannelHandlerContext vs Channel

```java
// ctx.write()     → 从当前 Handler 位置开始向 Head 传播（跳过当前Handler之后的出站Handler）
// ctx.channel().write() → 从 Tail 开始向 Head 传播（经过所有出站Handler）

// 大多数情况用 ctx.writeAndFlush()（性能更好，因为不需要完整遍历）
ctx.writeAndFlush(response);

// 只有需要经过后续所有出站 Handler 处理时，才用 channel.writeAndFlush()
ctx.channel().writeAndFlush(response);
```

---

## 3.7 本章总结

- **Pipeline = 责任链**：入站事件从 Head → Tail，出站事件从 Tail → Head
- **事件继续传播**：调用 `ctx.fireChannelRead()` 向后传入站，调用 `ctx.write()` 向前传出站；不调用则链断
- **SimpleChannelInboundHandler**：自动释放 `msg`，适合终结处理的业务 Handler
- **@Sharable**：无状态 Handler 加此注解可跨 Channel 共享，有状态必须每 Channel 独立实例
- **ctx.write vs channel.write**：前者跳过已经过的出站 Handler，后者从头走完整链

> **本章对应演示代码**：`PipelineDemo.java`（入站/出站事件传播、Handler 添加/删除、异常处理链）

**继续阅读**：[04_粘包拆包与编解码.md](./04_粘包拆包与编解码.md)
