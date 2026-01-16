# 02_TCP协议深度解析

> **核心问题**：TCP是如何保证可靠传输的？为什么需要三次握手和四次挥手？如何优化TCP性能？

---

## 一、为什么需要TCP协议？

### 1.1 网络通信的挑战

**问题场景**：两台计算机通过网络通信

```
计算机A ────────────────→ 计算机B
           网络（不可靠）

网络的特点：
❌ 数据包可能丢失
❌ 数据包可能乱序
❌ 数据包可能重复
❌ 数据包可能损坏
❌ 网络可能拥塞
```

**如果没有TCP会怎样？**

```java
// 使用UDP发送数据（不可靠）
DatagramSocket socket = new DatagramSocket();
byte[] data = "Hello".getBytes();
DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
socket.send(packet);  // 发送后就不管了

// 问题：
// 1. 数据包可能丢失 → 接收方收不到
// 2. 数据包可能乱序 → 接收方顺序错误
// 3. 没有流量控制 → 接收方可能处理不过来
```

**TCP解决了什么问题？**

```
TCP的核心功能：
┌─────────────────────────────────────────────────────────┐
│ 1. 可靠传输                                              │
│    - 确认应答（ACK）                                     │
│    - 超时重传                                            │
│    - 序列号保证顺序                                      │
├─────────────────────────────────────────────────────────┤
│ 2. 流量控制                                              │
│    - 滑动窗口                                            │
│    - 防止发送方发送过快                                  │
├─────────────────────────────────────────────────────────┤
│ 3. 拥塞控制                                              │
│    - 慢启动                                              │
│    - 拥塞避免                                            │
│    - 快速重传                                            │
├─────────────────────────────────────────────────────────┤
│ 4. 连接管理                                              │
│    - 三次握手建立连接                                    │
│    - 四次挥手关闭连接                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 二、TCP三次握手：为什么是三次？

### 2.1 三次握手的过程

```
TCP三次握手详细过程：

客户端                                    服务器
  │                                         │
  │  1. SYN (seq=x)                        │
  │─────────────────────────────────────→ │  LISTEN
  │                                         │
  │                                         │  收到SYN
  │                                         │  分配资源
  │                                         │
  │  2. SYN-ACK (seq=y, ack=x+1)           │
  │←───────────────────────────────────── │  SYN_RCVD
  │                                         │
  │  收到SYN-ACK                            │
  │  分配资源                               │
  │                                         │
  │  3. ACK (ack=y+1)                      │
  │─────────────────────────────────────→ │
  │                                         │
  │                                         │  收到ACK
  │                                         │
ESTABLISHED                           ESTABLISHED
  │                                         │
  │  可以开始传输数据                        │
  │←─────────────────────────────────────→│
```

**详细解释**：

**第一次握手（SYN）**：
```
客户端 → 服务器：SYN=1, seq=x

含义：
- SYN=1：表示这是一个连接请求
- seq=x：客户端的初始序列号

客户端状态：SYN_SENT
服务器状态：LISTEN → SYN_RCVD
```

**第二次握手（SYN-ACK）**：
```
服务器 → 客户端：SYN=1, ACK=1, seq=y, ack=x+1

含义：
- SYN=1：服务器也要发起连接
- ACK=1：确认收到客户端的SYN
- seq=y：服务器的初始序列号
- ack=x+1：期望收到客户端的下一个序列号

客户端状态：SYN_SENT
服务器状态：SYN_RCVD
```

**第三次握手（ACK）**：
```
客户端 → 服务器：ACK=1, ack=y+1

含义：
- ACK=1：确认收到服务器的SYN-ACK
- ack=y+1：期望收到服务器的下一个序列号

客户端状态：ESTABLISHED
服务器状态：ESTABLISHED
```

### 2.2 为什么需要三次握手？两次不行吗？

**问题1：两次握手会有什么问题？**

```
假设只有两次握手：

客户端                                    服务器
  │                                         │
  │  1. SYN (seq=x)                        │
  │─────────────────────────────────────→ │
  │                                         │
  │  2. SYN-ACK (seq=y, ack=x+1)           │
  │←───────────────────────────────────── │
  │                                         │
ESTABLISHED                           ESTABLISHED
```

**问题场景**：旧的SYN包延迟到达

```
时间线：
T1: 客户端发送SYN1（seq=100）
T2: SYN1在网络中丢失
T3: 客户端超时，重发SYN2（seq=200）
T4: 服务器收到SYN2，发送SYN-ACK2
T5: 连接建立，正常通信
T6: 连接关闭
T7: 旧的SYN1延迟到达服务器

如果只有两次握手：
- 服务器收到SYN1，发送SYN-ACK1
- 服务器认为连接已建立，分配资源
- 但客户端已经关闭，不会响应
- 服务器的资源被浪费！

如果有三次握手：
- 服务器收到SYN1，发送SYN-ACK1
- 客户端不会发送第三次ACK（因为已关闭）
- 服务器超时后释放资源
- 避免了资源浪费！
```

**问题2：为什么不是四次握手？**

```
三次握手已经足够：
1. 客户端告诉服务器：我要连接你（SYN）
2. 服务器告诉客户端：我收到了，我也要连接你（SYN-ACK）
3. 客户端告诉服务器：我收到了（ACK）

双方都确认了对方的存在，可以开始通信了！

四次握手是多余的：
- 第二次握手已经包含了服务器的SYN和对客户端的ACK
- 没有必要分开发送
```

### 2.3 三次握手的实际抓包分析

**使用Wireshark抓包**：

```
No.  Time     Source          Destination     Protocol  Info
1    0.000000 192.168.1.100   192.168.1.200   TCP       [SYN] Seq=0 Win=65535 Len=0
2    0.000234 192.168.1.200   192.168.1.100   TCP       [SYN, ACK] Seq=0 Ack=1 Win=65535 Len=0
3    0.000456 192.168.1.100   192.168.1.200   TCP       [ACK] Seq=1 Ack=1 Win=65535 Len=0

详细分析：
第1个包（SYN）：
- Flags: SYN
- Sequence number: 0
- Window size: 65535
- 含义：客户端请求建立连接，初始序列号为0，接收窗口为65535字节

第2个包（SYN-ACK）：
- Flags: SYN, ACK
- Sequence number: 0
- Acknowledgment number: 1
- Window size: 65535
- 含义：服务器同意连接，初始序列号为0，确认收到客户端的序列号0，期望下一个是1

第3个包（ACK）：
- Flags: ACK
- Sequence number: 1
- Acknowledgment number: 1
- 含义：客户端确认收到服务器的SYN，连接建立完成
```

---

## 三、TCP四次挥手：为什么是四次？

### 3.1 四次挥手的过程

```
TCP四次挥手详细过程：

客户端                                    服务器
  │                                         │
  │  1. FIN (seq=u)                        │
  │─────────────────────────────────────→ │  ESTABLISHED
  │                                         │
  │                                         │  收到FIN
  │                                         │  应用层可能还有数据要发
  │                                         │
  │  2. ACK (ack=u+1)                      │
  │←───────────────────────────────────── │  CLOSE_WAIT
  │                                         │
FIN_WAIT_1                                  │
  │                                         │
FIN_WAIT_2                                  │  应用层处理完毕
  │                                         │
  │  3. FIN (seq=v)                        │
  │←───────────────────────────────────── │  LAST_ACK
  │                                         │
  │  收到FIN                                │
  │                                         │
  │  4. ACK (ack=v+1)                      │
  │─────────────────────────────────────→ │
  │                                         │
TIME_WAIT                                   │  收到ACK
  │                                         │
  │  等待2MSL                               │  CLOSED
  │                                         │
CLOSED                                      │
```

**详细解释**：

**第一次挥手（FIN）**：
```
客户端 → 服务器：FIN=1, seq=u

含义：
- FIN=1：客户端请求关闭连接
- seq=u：客户端的序列号

客户端状态：FIN_WAIT_1
服务器状态：CLOSE_WAIT
```

**第二次挥手（ACK）**：
```
服务器 → 客户端：ACK=1, ack=u+1

含义：
- ACK=1：确认收到客户端的FIN
- ack=u+1：期望收到客户端的下一个序列号

客户端状态：FIN_WAIT_2
服务器状态：CLOSE_WAIT（半关闭状态）

注意：此时服务器还可以发送数据！
```

**第三次挥手（FIN）**：
```
服务器 → 客户端：FIN=1, seq=v

含义：
- FIN=1：服务器也请求关闭连接
- seq=v：服务器的序列号

客户端状态：TIME_WAIT
服务器状态：LAST_ACK
```

**第四次挥手（ACK）**：
```
客户端 → 服务器：ACK=1, ack=v+1

含义：
- ACK=1：确认收到服务器的FIN
- ack=v+1：期望收到服务器的下一个序列号

客户端状态：TIME_WAIT（等待2MSL）
服务器状态：CLOSED
```

### 3.2 为什么需要四次挥手？三次不行吗？

**关键点：TCP是全双工通信**

```
TCP连接 = 两个单向通道：
┌─────────────────────────────────────────┐
│  客户端 → 服务器（通道1）                 │
│  客户端 ← 服务器（通道2）                 │
└─────────────────────────────────────────┘

关闭连接需要关闭两个通道：
1. 客户端关闭通道1（FIN）
2. 服务器确认通道1关闭（ACK）
3. 服务器关闭通道2（FIN）
4. 客户端确认通道2关闭（ACK）
```

**为什么不能合并成三次？**

```
不能合并的原因：

第二次和第三次不能合并：
┌─────────────────────────────────────────┐
│ 客户端发送FIN后：                        │
│ - 客户端不再发送数据                     │
│ - 但服务器可能还有数据要发送！            │
│                                         │
│ 例如：                                  │
│ 1. 客户端发送FIN（我不发了）             │
│ 2. 服务器ACK（我知道了）                 │
│ 3. 服务器继续发送剩余数据                │
│ 4. 服务器发送完毕，发送FIN（我也不发了）  │
│ 5. 客户端ACK（我知道了）                 │
└─────────────────────────────────────────┘

如果合并：
- 服务器收到FIN后立即发送FIN+ACK
- 但服务器可能还有数据没发送完
- 数据会丢失！
```

**特殊情况：可以是三次挥手**

```
如果服务器没有数据要发送：
┌─────────────────────────────────────────┐
│ 1. 客户端：FIN                           │
│ 2. 服务器：FIN+ACK（合并）               │
│ 3. 客户端：ACK                           │
└─────────────────────────────────────────┘

这种情况下，第二次和第三次挥手合并了！
```

### 3.3 TIME_WAIT状态：为什么要等待2MSL？

**什么是MSL？**
```
MSL (Maximum Segment Lifetime)：最大报文生存时间
- 一个TCP报文在网络中能存活的最长时间
- Linux默认：30秒
- 2MSL = 60秒
```

**为什么要等待2MSL？**

**原因1：确保最后的ACK能够到达**
```
场景：最后的ACK丢失

客户端                                    服务器
  │                                         │
  │  FIN                                   │
  │←───────────────────────────────────── │
  │                                         │
  │  ACK                                   │
  │─────────────────────────────────────→ │  X（丢失）
  │                                         │
  │                                         │  超时
  │  FIN（重传）                            │
  │←───────────────────────────────────── │
  │                                         │
  │  ACK（重传）                            │
  │─────────────────────────────────────→ │
  │                                         │

如果客户端立即关闭：
- 服务器重传的FIN到达时，客户端已经关闭
- 客户端会发送RST，导致服务器异常

如果客户端等待2MSL：
- 服务器重传的FIN到达时，客户端还在等待
- 客户端可以重传ACK
- 确保连接正常关闭
```

**原因2：防止旧连接的数据包干扰新连接**
```
场景：端口复用

时间线：
T1: 连接1建立（客户端:1234 → 服务器:80）
T2: 连接1关闭
T3: 连接2建立（客户端:1234 → 服务器:80）← 复用了端口1234
T4: 连接1的旧数据包延迟到达

如果没有TIME_WAIT：
- 旧数据包可能被连接2接收
- 导致数据混乱

如果有TIME_WAIT（2MSL）：
- 等待2MSL后，旧数据包已经在网络中消失
- 新连接不会收到旧数据包
```

---

## 四、TCP状态机

### 4.1 完整的TCP状态转换图

```
TCP状态机（11个状态）：

                    ┌─────────┐
                    │ CLOSED  │
                    └─────────┘
                         │
                         │ 主动打开/发送SYN
                         ↓
                    ┌─────────┐
             ┌─────→│SYN_SENT │
             │      └─────────┘
             │           │
             │           │ 收到SYN+ACK/发送ACK
             │           ↓
    被动打开  │      ┌─────────────┐
    /监听    │      │ ESTABLISHED │←─────┐
             │      └─────────────┘      │
             │           │               │
        ┌────────┐       │ 主动关闭      │ 数据传输
        │ LISTEN │       │ /发送FIN      │
        └────────┘       ↓               │
             │      ┌──────────┐         │
             │      │FIN_WAIT_1│         │
             │      └──────────┘         │
             │           │               │
             │           │ 收到ACK       │
             │           ↓               │
             │      ┌──────────┐         │
             │      │FIN_WAIT_2│         │
             │      └──────────┘         │
             │           │               │
             │           │ 收到FIN       │
             │           │ /发送ACK      │
             │           ↓               │
             │      ┌──────────┐         │
             │      │TIME_WAIT │         │
             │      └──────────┘         │
             │           │               │
             │           │ 2MSL超时      │
             │           ↓               │
             │      ┌─────────┐          │
             └─────→│ CLOSED  │←─────────┘
                    └─────────┘
```

### 4.2 各状态的含义

| 状态 | 含义 | 何时进入 |
|------|------|---------|
| **CLOSED** | 关闭状态 | 初始状态或连接关闭后 |
| **LISTEN** | 监听状态 | 服务器等待连接 |
| **SYN_SENT** | SYN已发送 | 客户端发送SYN后 |
| **SYN_RCVD** | SYN已接收 | 服务器收到SYN后 |
| **ESTABLISHED** | 连接已建立 | 三次握手完成后 |
| **FIN_WAIT_1** | FIN已发送 | 主动关闭方发送FIN后 |
| **FIN_WAIT_2** | FIN已确认 | 收到对方的ACK后 |
| **CLOSE_WAIT** | 等待关闭 | 被动关闭方收到FIN后 |
| **LAST_ACK** | 最后确认 | 被动关闭方发送FIN后 |
| **TIME_WAIT** | 时间等待 | 主动关闭方收到FIN后 |
| **CLOSING** | 同时关闭 | 双方同时发送FIN |

### 4.3 使用netstat查看TCP状态

```bash
# 查看所有TCP连接的状态
netstat -ant

# 输出示例：
Proto Recv-Q Send-Q Local Address           Foreign Address         State
tcp        0      0 0.0.0.0:8080            0.0.0.0:*               LISTEN
tcp        0      0 192.168.1.100:45678     192.168.1.200:8080      ESTABLISHED
tcp        0      0 192.168.1.100:45679     192.168.1.200:8080      TIME_WAIT
tcp        0      0 192.168.1.100:45680     192.168.1.200:8080      FIN_WAIT_2

# 统计各状态的连接数
netstat -ant | awk '{print $6}' | sort | uniq -c
     10 ESTABLISHED
    100 TIME_WAIT
      5 FIN_WAIT_2
      1 LISTEN
```

---

## 五、TCP参数调优

### 5.1 常见的TCP参数

**1. backlog（连接队列大小）**

```java
// Java中设置backlog
ServerSocket serverSocket = new ServerSocket(8080, 128);  // backlog=128

// 含义：
// - 完成三次握手但未被accept的连接队列大小
// - 如果队列满了，新的SYN会被丢弃
```

**系统参数**：
```bash
# Linux查看backlog
cat /proc/sys/net/core/somaxconn
# 默认：128

# 修改backlog
echo 1024 > /proc/sys/net/core/somaxconn
```

**问题场景**：高并发下连接被拒绝
```
现象：
- 客户端报错：Connection refused
- 服务器日志：无错误

原因：
- backlog队列满了
- 新的SYN被丢弃

解决：
- 增大backlog参数
- 加快accept速度
```

**2. SO_KEEPALIVE（TCP保活）**

```java
// 启用TCP保活
socket.setKeepAlive(true);

// 含义：
// - 定期发送保活探测包
// - 检测对方是否还活着
```

**系统参数**：
```bash
# Linux的保活参数
cat /proc/sys/net/ipv4/tcp_keepalive_time    # 7200秒（2小时）
cat /proc/sys/net/ipv4/tcp_keepalive_intvl   # 75秒
cat /proc/sys/net/ipv4/tcp_keepalive_probes  # 9次

# 含义：
# - 2小时无数据传输后，开始发送保活探测
# - 每75秒发送一次
# - 连续9次无响应，认为连接断开
```

**问题**：保活时间太长
```
问题：
- 默认2小时才开始探测
- 连接断开后，2小时内无法发现

解决：
- 应用层实现心跳（推荐）
- 修改系统参数（不推荐）
```

**3. TCP_NODELAY（禁用Nagle算法）**

```java
// 禁用Nagle算法
socket.setTcpNoDelay(true);

// Nagle算法：
// - 将小的数据包合并成大的数据包再发送
// - 减少网络中的小包数量
// - 但会增加延迟

// 适用场景：
// - 启用Nagle：大量小数据传输（如文件传输）
// - 禁用Nagle：低延迟要求（如游戏、实时通信）
```

**4. SO_SNDBUF和SO_RCVBUF（缓冲区大小）**

```java
// 设置发送缓冲区
socket.setSendBufferSize(64 * 1024);  // 64KB

// 设置接收缓冲区
socket.setReceiveBufferSize(64 * 1024);  // 64KB

// 影响：
// - 缓冲区太小：频繁的系统调用，性能差
// - 缓冲区太大：内存占用高
```

### 5.2 TIME_WAIT过多的问题

**问题现象**：
```bash
# 查看TIME_WAIT数量
netstat -ant | grep TIME_WAIT | wc -l
# 输出：10000（太多了！）

# 问题：
# - 端口耗尽（客户端）
# - 内存占用高（服务器）
# - 无法建立新连接
```

**原因分析**：
```
TIME_WAIT产生的原因：
1. 主动关闭连接的一方会进入TIME_WAIT
2. 每个TIME_WAIT持续2MSL（60秒）
3. 高并发下，大量短连接导致大量TIME_WAIT

示例：
- QPS=1000，每个请求都是短连接
- 每秒产生1000个TIME_WAIT
- 60秒后，TIME_WAIT数量=60000
```

**解决方案**：

**方案1：使用连接池（推荐）**
```java
// 复用连接，避免频繁创建和关闭
HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)  // 启用Keep-Alive
    .build();
```

**方案2：调整系统参数**
```bash
# 启用TIME_WAIT快速回收
echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse

# 启用TIME_WAIT快速回收（不推荐）
echo 1 > /proc/sys/net/ipv4/tcp_tw_recycle

# 注意：tcp_tw_recycle在NAT环境下会有问题！
```

**方案3：让客户端主动关闭**
```
原理：
- 服务器主动关闭 → 服务器进入TIME_WAIT
- 客户端主动关闭 → 客户端进入TIME_WAIT

优化：
- 让客户端主动关闭连接
- TIME_WAIT分散到客户端
- 服务器不会有大量TIME_WAIT
```

### 5.3 TCP性能优化实战

**场景1：HTTP短连接优化**

```java
// 问题：每次请求都创建新连接
for (int i = 0; i < 1000; i++) {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    // ...
    conn.disconnect();
}

// 优化：使用连接池
HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .build();

for (int i = 0; i < 1000; i++) {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .build();
    client.send(request, HttpResponse.BodyHandlers.ofString());
}

// 效果：
// - 优化前：1000次TCP握手，耗时~3秒
// - 优化后：1次TCP握手，耗时~0.5秒
// - 性能提升：6倍
```

**场景2：大文件传输优化**

```java
// 问题：一次性读取整个文件到内存
byte[] data = Files.readAllBytes(Paths.get("large.dat"));  // OOM!
socket.getOutputStream().write(data);

// 优化：使用零拷贝
FileChannel fileChannel = FileChannel.open(Paths.get("large.dat"));
SocketChannel socketChannel = socket.getChannel();
fileChannel.transferTo(0, fileChannel.size(), socketChannel);

// 效果：
// - 优化前：需要2倍文件大小的内存，速度慢
// - 优化后：几乎不占用内存，速度快
// - 性能提升：10倍以上
```

**场景3：实时通信优化**

```java
// 问题：Nagle算法导致延迟
socket.setTcpNoDelay(false);  // 启用Nagle
socket.getOutputStream().write("H".getBytes());  // 延迟40ms
socket.getOutputStream().write("i".getBytes());  // 延迟40ms

// 优化：禁用Nagle算法
socket.setTcpNoDelay(true);  // 禁用Nagle
socket.getOutputStream().write("H".getBytes());  // 立即发送
socket.getOutputStream().write("i".getBytes());  // 立即发送

// 效果：
// - 优化前：延迟40ms
// - 优化后：延迟<1ms
// - 延迟降低：40倍
```

---

## 六、常见TCP问题排查

### 6.1 连接超时

**现象**：
```
java.net.SocketTimeoutException: connect timed out
```

**排查步骤**：

**1. 检查网络连通性**
```bash
# ping测试
ping 192.168.1.200

# telnet测试
telnet 192.168.1.200 8080

# 如果telnet失败，说明网络不通或端口未开放
```

**2. 抓包分析**
```bash
# 使用tcpdump抓包
tcpdump -i eth0 host 192.168.1.200 and port 8080 -w capture.pcap

# 分析：
# - 如果只有SYN，没有SYN-ACK：服务器未响应
# - 如果有SYN-ACK，但客户端未收到：网络丢包
```

**3. 检查服务器状态**
```bash
# 检查端口是否监听
netstat -lnt | grep 8080

# 检查防火墙
iptables -L -n

# 检查accept队列
ss -lnt
# 如果Send-Q满了，说明backlog不够
```

### 6.2 连接被重置

**现象**：
```
java.net.SocketException: Connection reset
```

**原因分析**：

**原因1：服务器主动关闭**
```
场景：
1. 客户端发送数据
2. 服务器已经关闭连接
3. 服务器发送RST
4. 客户端收到RST，抛出Connection reset

解决：
- 检查服务器日志
- 检查是否超时关闭
```

**原因2：防火墙拦截**
```
场景：
1. 连接空闲时间过长
2. 防火墙认为连接已断开
3. 防火墙发送RST
4. 客户端收到RST

解决：
- 实现心跳保活
- 调整防火墙超时时间
```

### 6.3 大量CLOSE_WAIT

**现象**：
```bash
netstat -ant | grep CLOSE_WAIT | wc -l
# 输出：1000（太多了！）
```

**原因**：应用层未调用close()

```java
// 问题代码：
Socket socket = serverSocket.accept();
// 处理请求
// 忘记关闭socket！

// 结果：
// - 对方发送FIN
// - 本地进入CLOSE_WAIT
// - 但应用层未调用close()
// - 连接一直处于CLOSE_WAIT
```

**解决**：
```java
// 正确代码：
Socket socket = null;
try {
    socket = serverSocket.accept();
    // 处理请求
} finally {
    if (socket != null) {
        socket.close();  // 确保关闭
    }
}
```

---

## 七、核心问题总结

### Q1：为什么TCP需要三次握手？两次不行吗？

**答**：
1. **防止旧连接请求**：避免旧的SYN包导致服务器创建无效连接
2. **确认双方能力**：确认双方都能发送和接收数据
3. **同步序列号**：双方交换初始序列号

### Q2：为什么TCP需要四次挥手？三次不行吗？

**答**：
1. **全双工通信**：需要关闭两个方向的通道
2. **半关闭状态**：一方关闭后，另一方可能还有数据要发送
3. **不能合并**：第二次和第三次挥手不能合并，因为中间可能还有数据传输

### Q3：TIME_WAIT状态的作用是什么？

**答**：
1. **确保最后的ACK到达**：如果ACK丢失，对方会重传FIN
2. **防止旧数据包干扰**：等待旧数据包在网络中消失

### Q4：如何优化TCP性能？

**答**：
1. **使用连接池**：复用连接，避免频繁握手
2. **调整缓冲区**：根据网络情况调整发送/接收缓冲区
3. **禁用Nagle**：低延迟场景下禁用Nagle算法
4. **启用Keep-Alive**：HTTP/1.1的Keep-Alive
5. **使用零拷贝**：大文件传输使用transferTo

---

## 八、下一步学习

在深入理解TCP协议后，下一章我们将学习：

**第3章：HTTP协议演进**
- HTTP/1.0、HTTP/1.1、HTTP/2的区别
- Keep-Alive、Pipeline、多路复用
- HTTPS的工作原理
- HTTP性能优化实战

---

**继续学习**：[03_HTTP协议演进](./03_HTTP协议演进.md)
