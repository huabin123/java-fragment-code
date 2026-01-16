# 第二章：BIO核心API - Socket编程详解

> **学习目标**：深入掌握Socket、ServerSocket等核心类的使用，理解TCP协议细节

---

## 一、Socket核心类概览

### 1.1 核心类关系

```
java.net包：
├── Socket              # 客户端Socket，用于连接服务器
├── ServerSocket        # 服务器Socket，用于监听客户端连接
├── InetAddress         # IP地址封装
├── InetSocketAddress   # IP地址+端口封装
├── SocketAddress       # 抽象的Socket地址
└── SocketException     # Socket异常

java.io包：
├── InputStream         # 输入流（读取数据）
├── OutputStream        # 输出流（写入数据）
├── BufferedInputStream # 缓冲输入流
└── BufferedOutputStream# 缓冲输出流
```

### 1.2 类的职责

```
ServerSocket：
- 绑定端口，监听连接
- accept()等待客户端连接
- 返回Socket对象

Socket：
- 连接服务器（客户端）
- 接受连接（服务器端通过accept()返回）
- 获取输入输出流
- 读写数据

InputStream/OutputStream：
- 面向字节的数据读写
- 阻塞式操作
- 需要手动处理粘包拆包
```

---

## 二、ServerSocket详解

### 2.1 创建ServerSocket

```java
// 方式1：指定端口（推荐）
ServerSocket serverSocket = new ServerSocket(8080);

// 方式2：指定端口和backlog
ServerSocket serverSocket = new ServerSocket(8080, 50);
// backlog：全连接队列的大小，默认50

// 方式3：指定端口、backlog和绑定地址
ServerSocket serverSocket = new ServerSocket(8080, 50, 
    InetAddress.getByName("192.168.1.100"));
// 绑定到特定网卡

// 方式4：先创建后绑定（灵活）
ServerSocket serverSocket = new ServerSocket();
serverSocket.setReuseAddress(true);  // 设置选项
serverSocket.bind(new InetSocketAddress(8080));
```

### 2.2 ServerSocket重要方法

```java
// 1. accept() - 等待客户端连接（阻塞）
Socket socket = serverSocket.accept();
// 返回：连接成功的Socket对象
// 阻塞：直到有客户端连接

// 2. close() - 关闭服务器Socket
serverSocket.close();
// 释放端口，停止监听

// 3. isClosed() - 检查是否已关闭
boolean closed = serverSocket.isClosed();

// 4. isBound() - 检查是否已绑定端口
boolean bound = serverSocket.isBound();

// 5. getLocalPort() - 获取绑定的端口
int port = serverSocket.getLocalPort();

// 6. setSoTimeout() - 设置accept()超时时间
serverSocket.setSoTimeout(5000);  // 5秒超时
// 超时后抛出SocketTimeoutException
```

### 2.3 ServerSocket选项配置

```java
ServerSocket serverSocket = new ServerSocket();

// 1. SO_REUSEADDR - 地址重用（重要！）
serverSocket.setReuseAddress(true);
// 作用：允许绑定处于TIME_WAIT状态的端口
// 场景：服务器重启时，端口可能还在TIME_WAIT状态

// 2. SO_RCVBUF - 接收缓冲区大小
serverSocket.setReceiveBufferSize(64 * 1024);  // 64KB
// 影响：TCP窗口大小，影响吞吐量

// 3. SO_TIMEOUT - accept()超时时间
serverSocket.setSoTimeout(10000);  // 10秒
// 0表示永不超时（默认）

// 绑定端口
serverSocket.bind(new InetSocketAddress(8080), 100);
// 第二个参数：backlog（全连接队列大小）
```

### 2.4 backlog参数详解

```
TCP连接队列：

客户端发起连接
    ↓
SYN队列（半连接队列）
    ↓ 三次握手完成
全连接队列（backlog控制）
    ↓ accept()取出
应用程序处理

backlog的作用：
- 控制全连接队列的大小
- 队列满时，新连接会被拒绝
- 默认值：50（较小）

建议值：
- 高并发场景：500-1000
- 一般场景：100-200
- 低并发场景：50（默认）

注意：
- 实际值受操作系统限制
- Linux: /proc/sys/net/core/somaxconn
- 取min(backlog, somaxconn)
```

---

## 三、Socket详解

### 3.1 创建Socket（客户端）

```java
// 方式1：直接连接（常用）
Socket socket = new Socket("localhost", 8080);

// 方式2：指定本地地址和端口
Socket socket = new Socket("localhost", 8080,
    InetAddress.getLocalHost(), 9000);
// 绑定本地端口9000

// 方式3：先创建后连接（推荐，可设置选项）
Socket socket = new Socket();
socket.setTcpNoDelay(true);  // 禁用Nagle算法
socket.setSoTimeout(5000);   // 设置读超时
socket.connect(new InetSocketAddress("localhost", 8080), 3000);
// 第二个参数：连接超时时间（3秒）
```

### 3.2 Socket重要方法

```java
// 1. 获取输入输出流
InputStream in = socket.getInputStream();
OutputStream out = socket.getOutputStream();

// 2. 获取连接信息
InetAddress remoteAddr = socket.getInetAddress();     // 远程地址
int remotePort = socket.getPort();                    // 远程端口
InetAddress localAddr = socket.getLocalAddress();     // 本地地址
int localPort = socket.getLocalPort();                // 本地端口

// 3. 连接状态
boolean connected = socket.isConnected();             // 是否已连接
boolean closed = socket.isClosed();                   // 是否已关闭
boolean bound = socket.isBound();                     // 是否已绑定

// 4. 关闭连接
socket.close();                                       // 完全关闭
socket.shutdownInput();                               // 关闭输入流
socket.shutdownOutput();                              // 关闭输出流
```

### 3.3 Socket选项配置（重要！）

```java
Socket socket = new Socket();

// 1. TCP_NODELAY - 禁用Nagle算法（重要！）
socket.setTcpNoDelay(true);
// 作用：立即发送数据，不等待缓冲区满
// 场景：实时性要求高的应用（游戏、即时通讯）
// 默认：false（启用Nagle算法）

// 2. SO_TIMEOUT - 读超时时间（重要！）
socket.setSoTimeout(5000);  // 5秒
// 作用：read()操作超时时间
// 超时：抛出SocketTimeoutException
// 默认：0（永不超时）

// 3. SO_LINGER - 关闭时的行为
socket.setSoLinger(true, 10);
// 参数1：是否启用
// 参数2：等待时间（秒）
// 作用：close()时等待未发送数据发送完成
// 默认：false（立即关闭）

// 4. SO_KEEPALIVE - 保持连接活跃
socket.setKeepAlive(true);
// 作用：定期发送心跳包，检测连接是否存活
// 场景：长连接
// 默认：false

// 5. SO_RCVBUF - 接收缓冲区大小
socket.setReceiveBufferSize(64 * 1024);  // 64KB
// 影响：TCP窗口大小

// 6. SO_SNDBUF - 发送缓冲区大小
socket.setSendBufferSize(64 * 1024);  // 64KB

// 7. SO_REUSEADDR - 地址重用
socket.setReuseAddress(true);
// 允许绑定处于TIME_WAIT状态的端口
```

### 3.4 Nagle算法详解

```
Nagle算法的作用：
- 减少小包的发送，提高网络利用率
- 将多个小数据包合并成一个大包发送

工作原理：
if (有未确认的数据) {
    缓存当前数据，等待ACK
} else if (数据量 >= MSS) {
    立即发送
} else {
    缓存数据，等待更多数据或超时
}

问题：
- 增加延迟（等待缓冲区满或超时）
- 不适合实时性要求高的应用

解决方案：
socket.setTcpNoDelay(true);  // 禁用Nagle算法

适用场景：
- 启用Nagle：批量数据传输、文件传输
- 禁用Nagle：实时通讯、游戏、即时消息
```

---

## 四、数据读写

### 4.1 基础读写

```java
// 写入数据
OutputStream out = socket.getOutputStream();
String message = "Hello, Server!";
out.write(message.getBytes("UTF-8"));
out.flush();  // 刷新缓冲区

// 读取数据
InputStream in = socket.getInputStream();
byte[] buffer = new byte[1024];
int len = in.read(buffer);  // 阻塞读取
String response = new String(buffer, 0, len, "UTF-8");
```

### 4.2 读取的三种返回值

```java
int len = in.read(buffer);

// 返回值1：> 0
// 含义：读取到的字节数
// 处理：正常读取数据

// 返回值2：= 0
// 含义：没有读取到数据（非阻塞模式下）
// 注意：BIO模式下不会返回0，会阻塞等待

// 返回值3：= -1
// 含义：流结束，对方关闭了连接
// 处理：关闭本地连接
if (len == -1) {
    socket.close();
    return;
}
```

### 4.3 完整读取指定长度数据

```java
/**
 * 读取指定长度的数据
 * 问题：read()可能只读取部分数据
 * 解决：循环读取直到满足长度
 */
public byte[] readFully(InputStream in, int length) throws IOException {
    byte[] buffer = new byte[length];
    int offset = 0;
    int remaining = length;
    
    while (remaining > 0) {
        int len = in.read(buffer, offset, remaining);
        if (len == -1) {
            throw new IOException("连接已关闭");
        }
        offset += len;
        remaining -= len;
    }
    
    return buffer;
}

// 使用示例
// 先读取4字节的长度
byte[] lenBytes = readFully(in, 4);
int dataLen = ByteBuffer.wrap(lenBytes).getInt();

// 再读取实际数据
byte[] data = readFully(in, dataLen);
```

### 4.4 使用缓冲流提高性能

```java
// ❌ 不推荐：直接使用原始流
InputStream in = socket.getInputStream();
OutputStream out = socket.getOutputStream();

// ✅ 推荐：使用缓冲流
BufferedInputStream in = new BufferedInputStream(
    socket.getInputStream(), 8192);  // 8KB缓冲区
BufferedOutputStream out = new BufferedOutputStream(
    socket.getOutputStream(), 8192);

// 性能提升：
// - 减少系统调用次数
// - 批量读写，提高效率
// - 适合频繁的小数据读写
```

### 4.5 使用DataInputStream/DataOutputStream

```java
// 读写基本数据类型
DataInputStream dis = new DataInputStream(socket.getInputStream());
DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

// 写入
dos.writeInt(123);           // int
dos.writeLong(456L);         // long
dos.writeDouble(3.14);       // double
dos.writeUTF("Hello");       // String（UTF-8编码）
dos.flush();

// 读取（顺序必须一致）
int intValue = dis.readInt();
long longValue = dis.readLong();
double doubleValue = dis.readDouble();
String strValue = dis.readUTF();
```

---

## 五、粘包和拆包问题

### 5.1 什么是粘包和拆包？

```
发送端：
消息1: "Hello"
消息2: "World"

接收端可能收到：
情况1（正常）：
  "Hello"
  "World"

情况2（粘包）：
  "HelloWorld"  ← 两个消息粘在一起

情况3（拆包）：
  "Hel"
  "loWorld"  ← 消息被拆开

情况4（粘包+拆包）：
  "HelloWor"
  "ld"
```

### 5.2 产生原因

```
TCP是面向流的协议：
- 没有消息边界的概念
- 只保证字节流的顺序
- 不保证消息的完整性

具体原因：
1. Nagle算法：合并小包发送
2. TCP缓冲区：数据在缓冲区中累积
3. MSS限制：超过MSS会拆包
4. 接收端处理速度：来不及读取，多个包累积
```

### 5.3 解决方案

#### 方案1：固定长度

```java
// 每个消息固定100字节
public void sendMessage(OutputStream out, String message) throws IOException {
    byte[] data = message.getBytes("UTF-8");
    byte[] packet = new byte[100];
    
    // 拷贝数据，不足部分填充0
    System.arraycopy(data, 0, packet, 0, Math.min(data.length, 100));
    out.write(packet);
    out.flush();
}

public String readMessage(InputStream in) throws IOException {
    byte[] packet = new byte[100];
    int len = 0;
    while (len < 100) {
        int n = in.read(packet, len, 100 - len);
        if (n == -1) throw new IOException("连接关闭");
        len += n;
    }
    
    // 去除填充的0
    int actualLen = 100;
    for (int i = 0; i < 100; i++) {
        if (packet[i] == 0) {
            actualLen = i;
            break;
        }
    }
    
    return new String(packet, 0, actualLen, "UTF-8");
}

// 优点：实现简单
// 缺点：浪费空间，不灵活
```

#### 方案2：长度前缀（推荐）

```java
/**
 * 协议格式：[4字节长度][实际数据]
 */
public void sendMessage(OutputStream out, String message) throws IOException {
    byte[] data = message.getBytes("UTF-8");
    
    // 写入长度（4字节）
    ByteBuffer buffer = ByteBuffer.allocate(4 + data.length);
    buffer.putInt(data.length);
    buffer.put(data);
    
    out.write(buffer.array());
    out.flush();
}

public String readMessage(InputStream in) throws IOException {
    // 读取长度（4字节）
    byte[] lenBytes = new byte[4];
    int offset = 0;
    while (offset < 4) {
        int n = in.read(lenBytes, offset, 4 - offset);
        if (n == -1) throw new IOException("连接关闭");
        offset += n;
    }
    
    int dataLen = ByteBuffer.wrap(lenBytes).getInt();
    
    // 读取数据
    byte[] data = new byte[dataLen];
    offset = 0;
    while (offset < dataLen) {
        int n = in.read(data, offset, dataLen - offset);
        if (n == -1) throw new IOException("连接关闭");
        offset += n;
    }
    
    return new String(data, "UTF-8");
}

// 优点：灵活，不浪费空间
// 缺点：需要两次读取
```

#### 方案3：分隔符

```java
/**
 * 使用换行符作为分隔符
 */
public void sendMessage(OutputStream out, String message) throws IOException {
    String packet = message + "\n";
    out.write(packet.getBytes("UTF-8"));
    out.flush();
}

public String readMessage(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    int ch;
    
    while ((ch = in.read()) != -1) {
        if (ch == '\n') {
            break;
        }
        sb.append((char) ch);
    }
    
    if (ch == -1 && sb.length() == 0) {
        throw new IOException("连接关闭");
    }
    
    return sb.toString();
}

// 或使用BufferedReader
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream(), "UTF-8"));
String message = reader.readLine();  // 读取一行

// 优点：简单直观
// 缺点：消息中不能包含分隔符，需要转义
```

#### 方案4：固定头部+可变体

```java
/**
 * 协议格式：
 * [1字节类型][4字节长度][实际数据]
 */
public class Message {
    private byte type;
    private byte[] data;
    
    public void send(OutputStream out) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(5 + data.length);
        buffer.put(type);
        buffer.putInt(data.length);
        buffer.put(data);
        
        out.write(buffer.array());
        out.flush();
    }
    
    public static Message receive(InputStream in) throws IOException {
        // 读取头部（5字节）
        byte[] header = new byte[5];
        int offset = 0;
        while (offset < 5) {
            int n = in.read(header, offset, 5 - offset);
            if (n == -1) throw new IOException("连接关闭");
            offset += n;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(header);
        byte type = buffer.get();
        int dataLen = buffer.getInt();
        
        // 读取数据
        byte[] data = new byte[dataLen];
        offset = 0;
        while (offset < dataLen) {
            int n = in.read(data, offset, dataLen - offset);
            if (n == -1) throw new IOException("连接关闭");
            offset += n;
        }
        
        Message msg = new Message();
        msg.type = type;
        msg.data = data;
        return msg;
    }
}

// 优点：灵活，支持多种消息类型
// 缺点：实现复杂
```

---

## 六、优雅关闭连接

### 6.1 TCP四次挥手

```
客户端                    服务器
  │                        │
  │  1. FIN（我要关闭了）    │
  ├───────────────────────>│
  │                        │
  │  2. ACK（收到）         │
  │<───────────────────────┤
  │                        │
  │  3. FIN（我也关闭）     │
  │<───────────────────────┤
  │                        │
  │  4. ACK（收到）         │
  ├───────────────────────>│
  │                        │
  │    TIME_WAIT（2MSL）    │
  │                        │
  ↓    CLOSED              ↓
```

### 6.2 关闭方式对比

```java
// 方式1：直接close()
socket.close();
// 效果：同时关闭输入和输出流
// 问题：可能丢失未发送的数据

// 方式2：shutdownOutput() + close()（推荐）
socket.shutdownOutput();  // 发送FIN，关闭输出
// 此时仍可以读取对方发送的数据
String response = readResponse(socket.getInputStream());
socket.close();  // 完全关闭

// 方式3：shutdownInput() + shutdownOutput() + close()
socket.shutdownOutput();  // 关闭输出
socket.shutdownInput();   // 关闭输入
socket.close();           // 完全关闭
```

### 6.3 优雅关闭的最佳实践

```java
/**
 * 优雅关闭Socket连接
 */
public void closeGracefully(Socket socket) {
    if (socket == null || socket.isClosed()) {
        return;
    }
    
    try {
        // 1. 关闭输出流（发送FIN）
        if (!socket.isOutputShutdown()) {
            socket.shutdownOutput();
        }
        
        // 2. 读取对方剩余数据（直到收到FIN）
        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[1024];
        while (in.read(buffer) != -1) {
            // 丢弃数据
        }
        
        // 3. 关闭输入流
        if (!socket.isInputShutdown()) {
            socket.shutdownInput();
        }
        
    } catch (IOException e) {
        // 忽略异常
    } finally {
        // 4. 完全关闭Socket
        try {
            socket.close();
        } catch (IOException e) {
            // 忽略异常
        }
    }
}
```

### 6.4 使用try-with-resources（推荐）

```java
// Java 7+自动资源管理
try (ServerSocket serverSocket = new ServerSocket(8080);
     Socket socket = serverSocket.accept();
     InputStream in = socket.getInputStream();
     OutputStream out = socket.getOutputStream()) {
    
    // 处理连接
    byte[] buffer = new byte[1024];
    int len = in.read(buffer);
    out.write(buffer, 0, len);
    
} catch (IOException e) {
    e.printStackTrace();
}
// 自动关闭所有资源，按声明的逆序关闭
```

---

## 七、常见异常处理

### 7.1 异常类型

```java
// 1. BindException - 端口已被占用
try {
    ServerSocket server = new ServerSocket(8080);
} catch (BindException e) {
    System.err.println("端口8080已被占用");
}

// 2. ConnectException - 连接被拒绝
try {
    Socket socket = new Socket("localhost", 8080);
} catch (ConnectException e) {
    System.err.println("连接被拒绝，服务器可能未启动");
}

// 3. SocketTimeoutException - 超时
try {
    socket.setSoTimeout(5000);
    int len = in.read(buffer);
} catch (SocketTimeoutException e) {
    System.err.println("读取超时");
}

// 4. SocketException - Socket错误
try {
    out.write(data);
} catch (SocketException e) {
    System.err.println("Socket错误：" + e.getMessage());
    // 可能原因：连接已关闭、网络中断等
}

// 5. IOException - 通用I/O错误
try {
    // I/O操作
} catch (IOException e) {
    System.err.println("I/O错误：" + e.getMessage());
}
```

### 7.2 异常处理最佳实践

```java
public void handleClient(Socket socket) {
    InputStream in = null;
    OutputStream out = null;
    
    try {
        socket.setSoTimeout(30000);  // 30秒超时
        in = socket.getInputStream();
        out = socket.getOutputStream();
        
        // 处理请求
        byte[] buffer = new byte[1024];
        int len = in.read(buffer);
        
        if (len == -1) {
            // 对方关闭连接
            return;
        }
        
        // 处理数据...
        out.write(response);
        out.flush();
        
    } catch (SocketTimeoutException e) {
        // 超时处理
        System.err.println("客户端超时：" + socket.getRemoteSocketAddress());
        
    } catch (SocketException e) {
        // Socket异常（连接断开等）
        System.err.println("连接异常：" + e.getMessage());
        
    } catch (IOException e) {
        // 其他I/O异常
        System.err.println("I/O异常：" + e.getMessage());
        e.printStackTrace();
        
    } finally {
        // 确保资源被关闭
        closeQuietly(out);
        closeQuietly(in);
        closeQuietly(socket);
    }
}

private void closeQuietly(Closeable closeable) {
    if (closeable != null) {
        try {
            closeable.close();
        } catch (IOException e) {
            // 忽略关闭时的异常
        }
    }
}
```

---

## 八、总结

### 8.1 核心要点

```
1. ServerSocket
   - 绑定端口，监听连接
   - accept()阻塞等待
   - 配置backlog、SO_REUSEADDR

2. Socket
   - 连接服务器或接受连接
   - 配置TCP_NODELAY、SO_TIMEOUT
   - 获取输入输出流

3. 数据读写
   - read()可能只读取部分数据
   - 需要循环读取直到满足长度
   - 使用缓冲流提高性能

4. 粘包拆包
   - TCP是面向流的，没有消息边界
   - 使用长度前缀或分隔符
   - 完整读取指定长度数据

5. 优雅关闭
   - shutdownOutput()发送FIN
   - 读取对方剩余数据
   - 最后close()
```

### 8.2 最佳实践

```
1. 使用try-with-resources自动管理资源
2. 设置合理的超时时间
3. 禁用Nagle算法（实时性要求高时）
4. 使用缓冲流提高性能
5. 正确处理粘包拆包
6. 优雅关闭连接
7. 完善的异常处理
```

---

**下一章**：我们将学习BIO的线程模型，从单线程到线程池的演进。

**继续阅读**：[第三章：BIO线程模型](./03_BIO线程模型.md)
