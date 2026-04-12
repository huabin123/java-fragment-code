# 第一章：TCP 协议深度解析

## 1.1 TCP 三次握手

```
客户端                          服务端
   │                               │
   │──── SYN (seq=x) ────────────>│  LISTEN 状态
   │                               │  服务端收到 SYN → SYN_RCVD 状态
   │<─── SYN-ACK (seq=y,ack=x+1) ─│
   │  客户端收到 → ESTABLISHED 状态│
   │──── ACK (ack=y+1) ──────────>│  服务端收到 → ESTABLISHED 状态
   │                               │
   │         数据传输               │

为什么必须三次，不能两次？
  两次握手：服务端发 SYN-ACK 后就认为连接建立
  → 旧的重复 SYN 包可能让服务端以为是新连接（资源浪费，攻击面）
  → 无法确认客户端的接收能力（服务端发的 SYN-ACK 客户端是否收到？）
  三次握手：双方都确认了对方的发送和接收能力，缺一不可
```

**Java 代码视角**：
```java
// TcpProtocolDemo.java → demonstrateHandshake()

// 三次握手发生在 connect() 内部，对应用透明
// 客户端：
SocketChannel client = SocketChannel.open();
client.connect(new InetSocketAddress("host", 8080));  // 发送 SYN，等待 SYN-ACK，发送 ACK

// 服务端：
ServerSocketChannel server = ServerSocketChannel.open().bind(new InetSocketAddress(8080));
SocketChannel conn = server.accept();  // 从 accept 队列（已完成三次握手的连接）取出

// 半连接队列（SYN Queue）：收到 SYN，三次握手未完成
// 全连接队列（Accept Queue）：三次握手完成，等待 accept()
// SYN Flood 攻击：伪造大量 SYN，填满半连接队列，让合法连接无法建立
// 防御：SYN Cookie（不存储半连接状态，在 SYN-ACK 中编码信息）
```

---

## 1.2 TCP 四次挥手

```
主动关闭方               被动关闭方
    │                        │
    │──── FIN ─────────────>│  被动方收到 → CLOSE_WAIT
    │<─── ACK ──────────────│  主动方收到 → FIN_WAIT_2
    │                        │  被动方处理完剩余数据...
    │<─── FIN ──────────────│  被动方发 FIN → LAST_ACK
    │──── ACK ─────────────>│
    │  主动方等待 2MSL...      │  被动方收到 ACK → CLOSED
    │  TIME_WAIT 状态         │
    │  2MSL 后 → CLOSED       │

为什么是四次，不是三次？
  TCP 是全双工：两个方向的数据流独立关闭
  收到对方 FIN 只表示"对方不再发数据"
  我方可能还有数据要发，所以不能立即回 FIN
  ACK 和 FIN 分两次发送 → 四次挥手

TIME_WAIT 状态（2MSL ≈ 60~120 秒）：
  作用1：确保最后的 ACK 对方收到（如果 ACK 丢失，对方会重发 FIN，TIME_WAIT 期间可以再 ACK）
  作用2：让网络中残留的数据包消散（防止被下一个连接误收）
  问题：服务器重启后端口被 TIME_WAIT 占用，报 "Address already in use"
  解决：SO_REUSEADDR = true
```

---

## 1.3 TCP 可靠传输机制

```java
// TcpProtocolDemo.java → demonstrateReliability()

// 1. 序列号与确认号：每个字节都有序号，接收方确认到哪个字节
// 2. 超时重传：发送方等待 ACK，超时未收到则重传
// 3. 滑动窗口：控制发送速率，防止接收方缓冲区溢出
// 4. 拥塞控制：防止发送方淹没网络

// 滑动窗口对 Java 编程的影响：
// socket.setReceiveBufferSize() 直接影响 TCP 接收窗口大小
// 接收缓冲区太小 → TCP 窗口小 → 对方被迫降速 → 吞吐量下降

// 验证：大文件传输时，调大 Socket 缓冲区可显著提升吞吐
socket.setReceiveBufferSize(4 * 1024 * 1024);  // 4MB（高带宽长距离传输）
socket.setSendBufferSize(4 * 1024 * 1024);
// 带宽时延积（BDP）= 带宽 × 往返延迟
// 例：100Mbps 带宽，50ms RTT：BDP = 100Mbps × 0.05s = 625KB
// 缓冲区应 ≥ BDP，才能跑满带宽
```

---

## 1.4 TCP 常见问题

### Nagle 算法

```java
// Nagle 算法：将小包合并，减少网络中的小数据包数量
// 默认开启：当有未 ACK 的数据时，新的小数据等待（合并后再发）

// 问题：写一个 100 字节的请求头，再写 200 字节的请求体
// 第一次 write(header) → 立即发送（无未 ACK 数据）
// 第二次 write(body)   → 等待 header 的 ACK（因 Nagle 延迟发送）
// 等待期间：接收方 ACK 延迟（默认 200ms 延迟 ACK）
// 结果：写完 300ms 后才能真正发出，延迟大！

// ✅ 低延迟场景（RPC、游戏、实时通信）必须禁用 Nagle：
socket.setTcpNoDelay(true);
// 或在 Netty 中：.childOption(ChannelOption.TCP_NODELAY, true)
```

### keepAlive

```java
// TCP keepAlive（OS 级别）vs 应用层心跳

// TCP keepAlive：
socket.setKeepAlive(true);
// Linux 默认：7200 秒（2小时）无数据才发探测包，间隔 75 秒，探测 9 次
// 时间太长，无法快速发现死连接
// 修改方式：sysctl net.ipv4.tcp_keepalive_time=60

// 应用层心跳（推荐，可控性强）：
// 每 30 秒发一次 PING，60 秒未收到 PONG 则主动断开
// 见 Netty 模块的 HeartbeatDemo.java
```

---

## 1.5 本章总结

- **三次握手**：确保双方收发能力正常；半连接/全连接队列区别；`backlog` 控制全连接队列大小
- **四次挥手**：全双工关闭，双方独立关闭各自方向；`TIME_WAIT` 必须等 2MSL
- **TIME_WAIT 处理**：`SO_REUSEADDR=true` 解决重启问题，不要轻易关闭 TIME_WAIT（有存在意义）
- **Socket 缓冲区**：高吞吐场景设置 ≥ BDP（带宽×延迟），低延迟场景不要设太大
- **TCP_NODELAY**：低延迟服务必须设 `true`，禁用 Nagle；高吞吐文件传输可保持默认

> **本章对应演示代码**：`TcpProtocolDemo.java`（三次握手/四次挥手状态机、Nagle 延迟实测、keepAlive 配置）

**继续阅读**：[02_HTTP协议演进.md](./02_HTTP协议演进.md)
