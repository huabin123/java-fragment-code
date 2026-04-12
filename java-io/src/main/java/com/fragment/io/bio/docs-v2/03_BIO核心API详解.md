# 第三章：BIO 核心 API 详解

## 3.1 ServerSocket：服务端监听

```java
// BasicSocketDemo.java → demonstrateServerSocketOptions()

// 创建 ServerSocket，绑定端口
ServerSocket server = new ServerSocket();
server.setReuseAddress(true);     // SO_REUSEADDR：服务重启时不等 TIME_WAIT 超时
server.bind(new InetSocketAddress(8080), 128);  // 第二个参数：accept 队列长度（backlog）

// 关键参数说明：
// backlog（accept 队列）：
//   TCP 三次握手完成后，连接进入 accept 队列等待 accept() 调用
//   如果 accept 速度跟不上，队列满了 → 新连接被拒绝（RST）
//   backlog 太小 → 高并发时丢连接
//   backlog 太大 → 占用内核内存
//   生产建议：128~1024，配合 SO_BACKLOG 系统参数

// SO_REUSEADDR：
//   服务器崩溃重启后，端口处于 TIME_WAIT（等待 2MSL≈4分钟）
//   不设置 → 重启失败，报 "Address already in use"
//   设置后 → 可以立即复用端口，生产环境必须设置

// 超时设置
server.setSoTimeout(0);  // 0 = accept() 永久阻塞；> 0 = 超时后抛 SocketTimeoutException
```

---

## 3.2 Socket：连接与读写

```java
// BasicSocketDemo.java → demonstrateSocketOptions()

Socket socket = new Socket();
socket.connect(new InetSocketAddress("localhost", 8080), 3000);  // 连接超时 3 秒

// 读写超时（防止 read() 永久阻塞）
socket.setSoTimeout(5000);  // read() 等待超过 5 秒 → 抛 SocketTimeoutException
// 生产必须设置！否则一个慢客户端会永久占用线程

// TCP 选项
socket.setTcpNoDelay(true);       // 禁用 Nagle 算法（低延迟场景）
socket.setKeepAlive(true);        // TCP 保活（2小时无数据发探测包）
socket.setSendBufferSize(64 * 1024);    // 发送缓冲区 64KB
socket.setReceiveBufferSize(64 * 1024); // 接收缓冲区 64KB

// 读写操作
OutputStream out = socket.getOutputStream();
InputStream  in  = socket.getInputStream();

// 写数据（先 flush 再 write，或使用 BufferedOutputStream）
BufferedOutputStream bos = new BufferedOutputStream(out, 8192);
bos.write(data);
bos.flush();  // 必须 flush，否则数据留在 BufferedOutputStream 的缓冲区里

// 读数据（注意：read() 可能返回 -1，表示对方关闭了连接）
int len;
byte[] buf = new byte[4096];
while ((len = in.read(buf)) != -1) {
    process(buf, len);
}
// 读到 -1 → 服务端调用了 socket.shutdownOutput() 或 socket.close()
```

---

## 3.3 流的正确使用

```java
// ❌ 常见错误1：忘记设置超时
Socket socket = new Socket("host", 8080);
// socket.getSoTimeout() 默认 0，read() 可能永久阻塞！

// ❌ 常见错误2：不用 BufferedStream，频繁系统调用
OutputStream out = socket.getOutputStream();
out.write(1);  // 每次 write 都是一次系统调用，性能极差
out.write(2);

// ✅ 正确：BufferedOutputStream 批量写
BufferedOutputStream bos = new BufferedOutputStream(out, 8192);
bos.write(1);
bos.write(2);
bos.flush();  // 一次系统调用写 2 字节（实际项目中累积更多再 flush）

// ❌ 常见错误3：资源泄漏（Socket 未关闭）
Socket s = new Socket("host", 8080);
// ... 处理中抛异常 ...
s.close();  // 异常路径不会执行！

// ✅ 正确：try-with-resources
try (Socket s2 = new Socket("host", 8080)) {
    // 正常或异常都会自动 close()
}

// ❌ 常见错误4：关闭顺序错误
socket.getInputStream().close();   // 关闭 InputStream 会同时关闭 Socket！
socket.getOutputStream().write(1); // Socket 已关闭，抛 SocketException

// ✅ 正确：只关闭 Socket，不单独关闭流
socket.close();  // 自动关闭 InputStream 和 OutputStream
```

---

## 3.4 序列化与协议

```java
// BIO 通常用 ObjectOutputStream 传输 Java 对象（简单但有局限）

// ❌ 不推荐：Java 原生序列化
try (ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
    oos.writeObject(new User("张三", 25));
}
// 问题：序列化后数据量大、跨语言不支持、版本兼容复杂、有安全漏洞

// ✅ 推荐：手动读写 DataInputStream/DataOutputStream（固定格式简单协议）
DataOutputStream dos = new DataOutputStream(
    new BufferedOutputStream(socket.getOutputStream()));
dos.writeInt(userId);           // 4字节 int
dos.writeUTF(userName);         // 2字节长度 + UTF-8 内容
dos.writeDouble(balance);       // 8字节 double
dos.flush();

DataInputStream dis = new DataInputStream(
    new BufferedInputStream(socket.getInputStream()));
int    id      = dis.readInt();
String name    = dis.readUTF();
double balance = dis.readDouble();

// ✅ 更好：JSON/Protobuf 序列化 + 长度头协议（见 protocol 模块）
```

---

## 3.5 本章总结

- **`ServerSocket`**：`setReuseAddress(true)` 生产必设；`backlog` 控制 accept 队列大小
- **`Socket`**：`setSoTimeout()` 生产必设（防止 `read()` 永久阻塞）；`setTcpNoDelay(true)` 降低延迟
- **流的使用**：必须用 `BufferedOutputStream` 减少系统调用；`read()` 返回 `-1` 表示连接关闭
- **资源管理**：用 `try-with-resources` 确保 `Socket` 关闭；关闭 `Socket` 即关闭所有流
- **序列化**：避免 Java 原生序列化，用 `DataOutputStream` + 自定义协议或 JSON/Protobuf

> **本章对应演示代码**：`BasicSocketDemo.java`（Socket 参数配置、超时、流使用）

**继续阅读**：[04_BIO最佳实践与适用边界.md](./04_BIO最佳实践与适用边界.md)
