# 第三章：WebSocket 协议详解

## 3.1 为什么需要 WebSocket？

HTTP 是请求-响应模式，服务端无法主动推送数据：

```
轮询方式（HTTP）：
  客户端：有新消息吗？→ 服务端：没有
  客户端：有新消息吗？→ 服务端：没有
  客户端：有新消息吗？→ 服务端：有！返回消息
  问题：大量无效请求，延迟高（轮询间隔），服务器压力大

长轮询（HTTP long-polling）：
  客户端发请求 → 服务端挂起（不立即响应）→ 有数据时才响应 → 客户端立即再次请求
  改进：减少无效请求；但每次都要建立新 HTTP 连接，Header 开销大

WebSocket：
  一次 HTTP 升级握手 → 建立全双工 TCP 连接
  服务端可以随时主动推送，客户端也可以随时发送
  → 真正的实时双向通信
```

---

## 3.2 WebSocket 握手过程

```
1. 客户端发起 HTTP Upgrade 请求（WebSocket 握手）：
   GET /ws HTTP/1.1
   Host: example.com
   Upgrade: websocket                     ← 请求升级协议
   Connection: Upgrade
   Sec-WebSocket-Key: dGhlIHNhbXBsZQ==   ← 随机 Base64 key
   Sec-WebSocket-Version: 13

2. 服务端返回 101 Switching Protocols：
   HTTP/1.1 101 Switching Protocols
   Upgrade: websocket
   Connection: Upgrade
   Sec-WebSocket-Accept: s3pPLMBiT...    ← SHA1(key + GUID) 的 Base64

3. 握手完成，HTTP 连接"升级"为 WebSocket 帧传输
   之后的数据以 WebSocket 帧格式传输，而非 HTTP 格式
```

```java
// WebSocketProtocolDemo.java → demonstrateHandshake()

// 用 Java 11 HttpClient 建立 WebSocket 连接
HttpClient client = HttpClient.newHttpClient();
WebSocket ws = client.newWebSocketBuilder()
    .buildAsync(URI.create("ws://localhost:8080/ws"),
        new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                System.out.println("收到消息: " + data);
                ws.request(1);  // 继续接收（流控）
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                System.out.println("连接关闭: " + statusCode + " " + reason);
                return null;
            }
        })
    .join();

ws.sendText("Hello Server", true);  // 发送文本帧，true = 最后一帧（完整消息）
```

---

## 3.3 WebSocket 帧格式

```
WebSocket 帧结构：
  bit:  0       7 8      15 16     23 24    31
       ┌──────────┬──────────┬──────────┬──────────┐
       │FIN RSV|OPC│MASK|PayLen│         │          │
       └──────────┴──────────┴──────────┴──────────┘
  FIN(1bit):  1=最后一帧（可分多帧传大消息）
  RSV(3bit):  扩展用，通常 000
  Opcode(4bit):
    0x0 = 继续帧
    0x1 = 文本帧（UTF-8）
    0x2 = 二进制帧
    0x8 = 关闭帧
    0x9 = Ping 帧（心跳）
    0xA = Pong 帧（心跳回复）
  MASK(1bit):  客户端→服务端必须掩码，服务端→客户端不掩码
  PayloadLen:  7bit（< 126）/ 16bit（< 65536）/ 64bit（更大）

关键特性：
  - 客户端发送的帧必须掩码（防止代理缓存污染攻击）
  - 帧头最小 2 字节，比 HTTP 报头轻量得多
```

---

## 3.4 Netty 实现 WebSocket 服务端

```java
// WebSocketProtocolDemo.java → WebSocketServer（基于 Netty）

// Pipeline 配置
pipeline.addLast(new HttpServerCodec());           // HTTP 编解码
pipeline.addLast(new HttpObjectAggregator(65536));  // 聚合 HTTP 报文
pipeline.addLast(new WebSocketServerProtocolHandler("/ws",  // 升级路径
    null, true, 65536));  // 最大帧大小
pipeline.addLast(new TextWebSocketFrameHandler()); // 业务处理（仅处理升级后的 WS 帧）

// 业务 Handler（只需处理 WebSocket 帧，握手由 Netty 自动处理）
public class TextWebSocketFrameHandler
        extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ChannelGroup clients =
        new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clients.add(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        System.out.println("收到来自 " + ctx.channel().remoteAddress() + ": " + text);

        // 广播给所有在线客户端
        clients.writeAndFlush(new TextWebSocketFrame("[广播] " + text));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        clients.remove(ctx.channel());
    }
}
```

---

## 3.5 心跳保活

```java
// WebSocket 心跳：Ping/Pong 帧（协议原生支持）

// 服务端：空闲时发 Ping
pipeline.addLast(new IdleStateHandler(0, 30, 0));  // 30s 无写操作
pipeline.addLast(new ChannelInboundHandlerAdapter() {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            ctx.writeAndFlush(new PingWebSocketFrame());  // 发 Ping 帧
        }
    }
});

// 客户端：Netty 自动响应 Pong（WebSocketServerProtocolHandler 内置）
// 也可以在应用层发送文本心跳（更通用，浏览器 JS 也可以发）
ws.sendPing(ByteBuffer.wrap("PING".getBytes()));
```

---

## 3.6 本章总结

- **WebSocket 的价值**：一次握手后全双工通信，服务端主动推送，帧头轻量，适合实时应用
- **握手流程**：HTTP Upgrade 请求 → 101 响应 → 连接升级为 WebSocket 帧传输
- **帧类型**：文本(0x1)、二进制(0x2)、关闭(0x8)、Ping/Pong(0x9/0xA)
- **客户端掩码**：浏览器发出的帧必须掩码，服务端发出的不需要
- **Netty 集成**：`WebSocketServerProtocolHandler` 自动处理握手、Ping/Pong，业务只需处理数据帧

> **本章对应演示代码**：`WebSocketProtocolDemo.java`（Java 11 WebSocket 客户端、Netty WebSocket 服务端、群聊广播）

**继续阅读**：[04_自定义协议设计.md](./04_自定义协议设计.md)
