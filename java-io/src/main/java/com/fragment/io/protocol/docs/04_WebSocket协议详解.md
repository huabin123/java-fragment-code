# 04_WebSocket协议详解

> **核心问题**：WebSocket如何实现全双工通信？为什么比HTTP轮询更高效？如何实现心跳保活？

---

## 一、为什么需要WebSocket？

### 1.1 HTTP的局限性

**问题场景**：实现实时聊天功能

```
使用HTTP轮询：
客户端                                    服务器
  │                                         │
  │  请求：有新消息吗？                      │
  │─────────────────────────────────────→ │
  │  响应：没有                             │
  │←───────────────────────────────────── │
  │                                         │
  │  请求：有新消息吗？                      │
  │─────────────────────────────────────→ │
  │  响应：没有                             │
  │←───────────────────────────────────── │
  │                                         │
  │  请求：有新消息吗？                      │
  │─────────────────────────────────────→ │
  │  响应：有！这是消息                      │
  │←───────────────────────────────────── │

问题：
❌ 大量无效请求（99%的请求都是"没有"）
❌ 延迟高（最多1秒延迟）
❌ 服务器压力大
❌ 浪费带宽
```

**HTTP长轮询**：
```
客户端                                    服务器
  │                                         │
  │  请求：有新消息吗？                      │
  │─────────────────────────────────────→ │
  │                                         │
  │  （服务器保持连接，等待新消息）          │
  │                                         │
  │  （30秒后，有新消息）                   │
  │  响应：有！这是消息                      │
  │←───────────────────────────────────── │
  │                                         │
  │  请求：有新消息吗？                      │
  │─────────────────────────────────────→ │

改进：
✓ 减少无效请求
✓ 延迟低

但仍有问题：
❌ 服务器无法主动推送
❌ 每次都要重新建立连接
❌ HTTP头部开销大
```

### 1.2 WebSocket的解决方案

```
WebSocket：
客户端                                    服务器
  │                                         │
  │  1. HTTP握手（升级到WebSocket）         │
  │─────────────────────────────────────→ │
  │  2. 握手成功                            │
  │←───────────────────────────────────── │
  │                                         │
  │  3. 建立持久连接                        │
  │←─────────────────────────────────────→│
  │                                         │
  │  4. 双向通信（随时发送消息）             │
  │←─────────────────────────────────────→│
  │  服务器可以主动推送                      │
  │←───────────────────────────────────── │
  │  客户端可以随时发送                      │
  │─────────────────────────────────────→ │

优势：
✓ 全双工通信
✓ 低延迟（实时）
✓ 低开销（无HTTP头部）
✓ 服务器主动推送
```

---

## 二、WebSocket握手过程

### 2.1 握手请求

```
客户端发起握手：

GET /chat HTTP/1.1
Host: server.example.com
Upgrade: websocket                    ← 升级到WebSocket
Connection: Upgrade                   ← 连接升级
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==  ← 随机密钥
Sec-WebSocket-Version: 13             ← WebSocket版本
Origin: http://example.com

关键字段：
- Upgrade: websocket          表示要升级到WebSocket
- Connection: Upgrade         表示连接要升级
- Sec-WebSocket-Key          客户端生成的随机密钥（Base64编码）
- Sec-WebSocket-Version      WebSocket协议版本（13）
```

### 2.2 握手响应

```
服务器响应握手：

HTTP/1.1 101 Switching Protocols      ← 状态码101表示协议切换
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=  ← 服务器计算的密钥

Sec-WebSocket-Accept的计算：
1. 将Sec-WebSocket-Key与固定字符串拼接
   key = "dGhlIHNhbXBsZSBub25jZQ==" + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
   
2. 计算SHA-1哈希
   hash = SHA1(key)
   
3. Base64编码
   accept = Base64(hash)
   
作用：防止缓存代理返回错误的响应
```

### 2.3 握手完整流程

```
WebSocket握手流程：

客户端                                    服务器
  │                                         │
  │  1. HTTP GET请求                        │
  │  Upgrade: websocket                    │
  │  Sec-WebSocket-Key: xxx                │
  │─────────────────────────────────────→ │
  │                                         │
  │                                         │  验证请求
  │                                         │  计算Accept
  │                                         │
  │  2. HTTP 101响应                        │
  │  Upgrade: websocket                    │
  │  Sec-WebSocket-Accept: yyy             │
  │←───────────────────────────────────── │
  │                                         │
  │  验证Accept                             │
  │                                         │
  │  3. WebSocket连接建立                   │
  │←─────────────────────────────────────→│
  │                                         │
  │  4. 开始传输WebSocket帧                 │
  │←─────────────────────────────────────→│
```

---

## 三、WebSocket帧格式

### 3.1 帧结构

```
WebSocket帧格式：

 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+

字段说明：
- FIN (1 bit)：是否是最后一个分片
- RSV1-3 (3 bits)：保留位，必须为0
- opcode (4 bits)：操作码，表示帧类型
- MASK (1 bit)：是否使用掩码（客户端必须为1）
- Payload len (7 bits)：数据长度
- Masking-key (32 bits)：掩码密钥（如果MASK=1）
- Payload Data：实际数据
```

### 3.2 操作码（opcode）

```
操作码类型：

0x0：继续帧（Continuation Frame）
0x1：文本帧（Text Frame）
0x2：二进制帧（Binary Frame）
0x8：关闭帧（Close Frame）
0x9：Ping帧
0xA：Pong帧

示例：
- 发送文本消息：opcode=0x1
- 发送二进制数据：opcode=0x2
- 心跳检测：opcode=0x9（Ping）
- 心跳响应：opcode=0xA（Pong）
- 关闭连接：opcode=0x8
```

### 3.3 掩码（Masking）

```
为什么需要掩码？
- 防止缓存污染攻击
- 客户端发送的数据必须掩码
- 服务器发送的数据不需要掩码

掩码算法：
masked_data[i] = original_data[i] XOR masking_key[i % 4]

示例：
原始数据：Hello
掩码密钥：0x12345678

H (0x48) XOR 0x12 = 0x5A
e (0x65) XOR 0x34 = 0x51
l (0x6C) XOR 0x56 = 0x3A
l (0x6C) XOR 0x78 = 0x14
o (0x6F) XOR 0x12 = 0x7D

掩码后：0x5A 0x51 0x3A 0x14 0x7D
```

---

## 四、WebSocket心跳保活

### 4.1 为什么需要心跳？

```
问题场景：
客户端                                    服务器
  │                                         │
  │  WebSocket连接建立                      │
  │←─────────────────────────────────────→│
  │                                         │
  │  （长时间无数据传输）                    │
  │                                         │
  │  （中间设备认为连接已断开）              │
  │  X                                      │
  │                                         │
  │  发送消息                                │
  │─────────────────────────────────────→ │  X 无法送达
  │                                         │

问题：
- 连接空闲时间过长
- 中间设备（防火墙、NAT）可能关闭连接
- 双方无法感知连接已断开
```

### 4.2 心跳机制

```
心跳保活：

客户端                                    服务器
  │                                         │
  │  （每30秒发送Ping）                     │
  │  Ping                                  │
  │─────────────────────────────────────→ │
  │                                         │
  │  Pong                                  │
  │←───────────────────────────────────── │
  │                                         │
  │  （30秒后）                             │
  │  Ping                                  │
  │─────────────────────────────────────→ │
  │                                         │
  │  Pong                                  │
  │←───────────────────────────────────── │

如果超时未收到Pong：
- 认为连接已断开
- 关闭连接
- 尝试重连
```

### 4.3 心跳实现

**客户端心跳**：
```java
WebSocket ws = new WebSocket("ws://localhost:8080/chat");

// 启动心跳定时器
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    if (ws.isOpen()) {
        ws.sendPing();  // 发送Ping
    }
}, 0, 30, TimeUnit.SECONDS);  // 每30秒发送一次

// 处理Pong响应
ws.addListener(new WebSocketAdapter() {
    @Override
    public void onPongReceived(WebSocket websocket, byte[] message) {
        System.out.println("收到Pong，连接正常");
    }
});
```

**服务器心跳检测**：
```java
// 使用Netty的IdleStateHandler
pipeline.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
pipeline.addLast(new WebSocketServerHandler());

class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 60秒未收到数据，关闭连接
                System.out.println("心跳超时，关闭连接");
                ctx.close();
            }
        }
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof PingWebSocketFrame) {
            // 收到Ping，回复Pong
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        }
    }
}
```

---

## 五、WebSocket vs HTTP

### 5.1 性能对比

```
场景：实时聊天，每秒发送1条消息

HTTP短轮询（每秒轮询1次）：
- 每次请求：500字节头部 + 数据
- 每秒请求数：1次
- 每秒流量：500字节 × 1 = 500字节
- 延迟：平均500ms

HTTP长轮询：
- 每次请求：500字节头部 + 数据
- 每秒请求数：1次（有消息时）
- 每秒流量：500字节
- 延迟：<100ms

WebSocket：
- 首次握手：500字节
- 每次消息：2-6字节头部 + 数据
- 每秒流量：6字节 × 1 = 6字节
- 延迟：<10ms

性能对比：
- 流量：WebSocket节省99%
- 延迟：WebSocket降低90%
```

### 5.2 适用场景

```
使用WebSocket的场景：
✓ 实时聊天
✓ 在线游戏
✓ 实时协作（如Google Docs）
✓ 股票行情推送
✓ 实时监控
✓ 视频弹幕

使用HTTP的场景：
✓ 普通网页浏览
✓ RESTful API
✓ 文件下载
✓ 不需要实时性的场景
```

---

## 六、WebSocket最佳实践

### 6.1 断线重连

```java
public class ReconnectWebSocket {
    private WebSocket ws;
    private String url;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    
    public void connect() {
        try {
            ws = new WebSocket(url);
            
            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onDisconnected(WebSocket websocket, 
                                          WebSocketFrame serverCloseFrame,
                                          WebSocketFrame clientCloseFrame,
                                          boolean closedByServer) {
                    // 连接断开，尝试重连
                    reconnect();
                }
            });
            
            reconnectAttempts = 0;  // 重置重连次数
        } catch (Exception e) {
            reconnect();
        }
    }
    
    private void reconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            System.out.println("达到最大重连次数，放弃重连");
            return;
        }
        
        reconnectAttempts++;
        int delay = Math.min(30, (int) Math.pow(2, reconnectAttempts));  // 指数退避
        
        System.out.println("将在" + delay + "秒后重连（第" + reconnectAttempts + "次）");
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            connect();
        }, delay, TimeUnit.SECONDS);
    }
}
```

### 6.2 消息确认机制

```java
public class ReliableWebSocket {
    private Map<String, Message> pendingMessages = new ConcurrentHashMap<>();
    
    public void sendMessage(String content) {
        String messageId = UUID.randomUUID().toString();
        Message message = new Message(messageId, content);
        
        // 保存待确认的消息
        pendingMessages.put(messageId, message);
        
        // 发送消息
        ws.sendText(JSON.toJSONString(message));
        
        // 启动超时检测
        scheduler.schedule(() -> {
            if (pendingMessages.containsKey(messageId)) {
                // 超时未确认，重发
                System.out.println("消息" + messageId + "超时，重发");
                sendMessage(content);
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    public void onMessage(String text) {
        JSONObject json = JSON.parseObject(text);
        
        if (json.containsKey("ack")) {
            // 收到确认
            String messageId = json.getString("ack");
            pendingMessages.remove(messageId);
            System.out.println("消息" + messageId + "已确认");
        } else {
            // 收到新消息，发送确认
            String messageId = json.getString("id");
            ws.sendText("{\"ack\":\"" + messageId + "\"}");
            
            // 处理消息
            handleMessage(json);
        }
    }
}
```

### 6.3 流量控制

```java
public class ThrottledWebSocket {
    private static final int MAX_MESSAGES_PER_SECOND = 10;
    private Queue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private AtomicInteger messageCount = new AtomicInteger(0);
    
    public void sendMessage(String message) {
        if (messageCount.get() < MAX_MESSAGES_PER_SECOND) {
            // 未达到限制，直接发送
            ws.sendText(message);
            messageCount.incrementAndGet();
        } else {
            // 达到限制，加入队列
            messageQueue.offer(message);
        }
    }
    
    // 每秒重置计数器并发送队列中的消息
    scheduler.scheduleAtFixedRate(() -> {
        messageCount.set(0);
        
        while (!messageQueue.isEmpty() && 
               messageCount.get() < MAX_MESSAGES_PER_SECOND) {
            String message = messageQueue.poll();
            ws.sendText(message);
            messageCount.incrementAndGet();
        }
    }, 0, 1, TimeUnit.SECONDS);
}
```

---

## 七、核心问题总结

### Q1：WebSocket和HTTP有什么区别？

**答**：
1. **连接方式**：WebSocket是持久连接，HTTP是请求-响应
2. **通信方式**：WebSocket是全双工，HTTP是半双工
3. **开销**：WebSocket头部小（2-6字节），HTTP头部大（500+字节）
4. **推送**：WebSocket支持服务器主动推送，HTTP不支持

### Q2：WebSocket的握手过程是怎样的？

**答**：
1. 客户端发送HTTP GET请求，包含Upgrade头
2. 服务器返回101状态码，表示协议切换
3. 连接升级为WebSocket
4. 开始传输WebSocket帧

### Q3：如何实现WebSocket心跳？

**答**：
1. 客户端定期发送Ping帧
2. 服务器收到Ping后回复Pong帧
3. 如果超时未收到Pong，认为连接断开
4. 服务器也可以检测读空闲，超时关闭连接

### Q4：WebSocket适合什么场景？

**答**：
- ✅ 实时性要求高的场景（聊天、游戏）
- ✅ 需要服务器主动推送的场景
- ✅ 消息频繁的场景
- ❌ 普通的HTTP请求（用HTTP更简单）

---

## 八、下一步学习

在理解WebSocket协议后，下一章我们将学习：

**第5章：自定义协议设计**
- 协议设计的基本原则
- 魔数、版本号、长度字段的作用
- 序列化方案的选择
- 完整的RPC协议设计

---

**继续学习**：[05_自定义协议设计](./05_自定义协议设计.md)
