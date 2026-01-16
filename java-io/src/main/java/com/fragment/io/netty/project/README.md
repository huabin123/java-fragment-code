# Netty实战项目集合

> 4个完整的Netty实战项目，涵盖主要应用场景

---

## 📚 项目列表

| 项目 | 难度 | 端口 | 核心技术 | 说明 |
|------|------|------|---------|------|
| **RPC框架** | ⭐⭐⭐ | 8888 | 自定义协议、动态代理、反射 | 简单但完整的RPC实现 |
| **WebSocket聊天室** | ⭐⭐⭐ | 8080 | WebSocket、群聊、私聊 | 多人在线聊天 |
| **HTTP文件服务器** | ⭐⭐ | 8888 | HTTP、零拷贝、文件传输 | 文件浏览和下载 |
| **TCP推送服务** | ⭐⭐⭐⭐ | 9999 | 长连接、心跳、推送 | 消息推送系统 |

---

## 🚀 快速开始

### 1. RPC框架

**启动服务端：**
```bash
java -cp target/classes com.fragment.io.netty.project.rpc.RpcServer
```

**启动客户端：**
```bash
java -cp target/classes com.fragment.io.netty.project.rpc.RpcClient
```

**功能特点：**
- ✅ 自定义RPC协议（魔数、版本、类型、请求ID、长度）
- ✅ 动态代理（透明的远程调用）
- ✅ 异步通信（基于Netty）
- ✅ 服务注册（支持多服务）
- ✅ 反射调用（自动查找方法）

---

### 2. WebSocket聊天室

**启动服务端：**
```bash
java -cp target/classes com.fragment.io.netty.project.websocket.WebSocketChatServer
```

**访问聊天室：**
```
浏览器打开：http://localhost:8080/chat.html
```

**功能特点：**
- ✅ 多人在线聊天
- ✅ 用户名设置（`/name 用户名`）
- ✅ 查看在线用户（`/list`）
- ✅ 私聊功能（`/to 用户名 消息`）
- ✅ 群聊广播
- ✅ 美观的Web界面

**命令列表：**
```
/name 用户名    - 设置用户名
/list          - 查看在线用户列表
/to 用户名 消息  - 发送私聊消息
直接输入        - 群聊消息
```

---

### 3. HTTP文件服务器

**启动服务器：**
```bash
java -cp target/classes com.fragment.io.netty.project.http.HttpFileServer
```

**访问文件服务器：**
```
浏览器打开：http://localhost:8888/
```

**功能特点：**
- ✅ 文件浏览（目录列表）
- ✅ 文件下载
- ✅ 零拷贝传输（FileRegion）
- ✅ 多种文件类型支持
- ✅ 美观的目录列表界面
- ✅ 文件大小格式化显示

**支持的文件类型：**
- 文本：txt, html, css, js
- 图片：jpg, png, gif
- 文档：pdf
- 其他：自动下载

---

### 4. TCP推送服务

**启动服务端：**
```bash
java -cp target/classes com.fragment.io.netty.project.push.PushServer
```

**启动客户端：**
```bash
java -cp target/classes com.fragment.io.netty.project.push.PushClient
```

**功能特点：**
- ✅ 长连接维持
- ✅ 心跳检测（客户端30秒，服务端60秒）
- ✅ 定时推送（每10秒推送一次）
- ✅ 自动重连（连接断开5秒后重连）
- ✅ 在线统计

---

## 📊 项目对比

### 协议对比

| 项目 | 协议 | 编解码 | 特点 |
|------|------|--------|------|
| RPC框架 | 自定义二进制 | 自定义编解码器 | 高效、紧凑 |
| WebSocket聊天室 | WebSocket | Netty内置 | 双向通信、浏览器支持 |
| HTTP文件服务器 | HTTP | Netty内置 | 标准协议、通用性强 |
| TCP推送服务 | 文本协议 | StringDecoder/Encoder | 简单、易调试 |

### 应用场景

| 项目 | 应用场景 | 典型案例 |
|------|---------|---------|
| RPC框架 | 分布式服务调用 | Dubbo、gRPC |
| WebSocket聊天室 | 实时通信 | 在线客服、IM系统 |
| HTTP文件服务器 | 文件服务 | 文件下载站、CDN |
| TCP推送服务 | 消息推送 | 推送系统、实时通知 |

---

## 🎯 学习建议

### 学习顺序

1. **第1周**：RPC框架
   - 理解自定义协议设计
   - 掌握编解码器实现
   - 学习动态代理

2. **第2周**：WebSocket聊天室
   - 理解WebSocket协议
   - 掌握群组管理
   - 学习前端交互

3. **第3周**：HTTP文件服务器
   - 理解HTTP协议
   - 掌握零拷贝技术
   - 学习文件传输

4. **第4周**：TCP推送服务
   - 理解长连接维持
   - 掌握心跳机制
   - 学习自动重连

### 实践建议

1. **运行项目**：先运行每个项目，理解功能
2. **阅读代码**：仔细阅读源码，理解实现
3. **修改功能**：尝试添加新功能
4. **性能测试**：使用工具测试性能
5. **扩展项目**：根据需求扩展功能

---

## 🔧 扩展功能建议

### RPC框架扩展

- [ ] 服务注册与发现（Zookeeper/Nacos）
- [ ] 负载均衡（随机、轮询、一致性哈希）
- [ ] 失败重试机制
- [ ] 更高效的序列化（Protobuf、Hessian）
- [ ] 连接池
- [ ] 监控统计

### WebSocket聊天室扩展

- [ ] 用户认证
- [ ] 聊天记录持久化
- [ ] 文件传输
- [ ] 表情包支持
- [ ] 多房间支持
- [ ] 在线状态显示

### HTTP文件服务器扩展

- [ ] 断点续传
- [ ] 文件上传
- [ ] 文件搜索
- [ ] 访问权限控制
- [ ] 缓存控制
- [ ] 压缩传输

### TCP推送服务扩展

- [ ] 消息持久化
- [ ] 消息确认机制
- [ ] 离线消息推送
- [ ] 消息优先级
- [ ] 分组推送
- [ ] 推送统计

---

## 💡 核心技术点

### 1. 自定义协议设计

**RPC协议示例：**
```
┌──────┬─────┬──────┬────────┬──────┬─────────┐
│ 魔数 │版本 │ 类型 │ 请求ID │ 长度 │  数据    │
│ 2字节│1字节│1字节 │ 8字节  │4字节 │ N字节   │
└──────┴─────┴──────┴────────┴──────┴─────────┘
```

**设计要点：**
- 魔数：快速识别协议
- 版本：支持协议升级
- 长度字段：解决粘包拆包
- 校验和：保证数据完整性

### 2. 心跳机制

**客户端：**
```java
// 30秒写空闲发送心跳
new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS)
```

**服务端：**
```java
// 60秒读空闲关闭连接
new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS)
```

**配置建议：**
```
客户端心跳间隔 < 服务端超时时间 / 2
```

### 3. 零拷贝

**FileRegion：**
```java
// 使用零拷贝发送文件
FileRegion region = new DefaultFileRegion(fileChannel, 0, length);
ctx.write(region);
```

**优势：**
- 减少数据拷贝次数
- 降低CPU使用率
- 提高传输效率

### 4. 动态代理

**JDK动态代理：**
```java
HelloService service = (HelloService) Proxy.newProxyInstance(
    interfaceClass.getClassLoader(),
    new Class<?>[]{interfaceClass},
    new RpcInvocationHandler()
);
```

**优势：**
- 透明的远程调用
- 自动处理网络通信
- 简化客户端代码

---

## ⚠️ 注意事项

### 1. 端口冲突

如果端口被占用，修改对应的PORT常量：
```java
private static final int PORT = 8888;  // 修改为其他端口
```

### 2. 文件路径

HTTP文件服务器需要修改文件根目录：
```java
private static final String FILE_ROOT = "/your/path";
```

### 3. 内存管理

记得释放ByteBuf：
```java
try {
    // 使用ByteBuf
} finally {
    buf.release();
}
```

### 4. 异常处理

添加完善的异常处理：
```java
@Override
public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
}
```

---

## 📈 性能测试

### 测试工具

- **RPC框架**：JMH、自定义压测工具
- **WebSocket**：WebSocket Bench
- **HTTP服务器**：Apache Bench (ab)、wrk
- **TCP推送**：自定义客户端

### 测试指标

- **QPS**：每秒请求数
- **延迟**：平均响应时间
- **吞吐量**：每秒传输字节数
- **连接数**：最大并发连接数

---

## 🎓 总结

这4个项目涵盖了Netty的主要应用场景：

1. **RPC框架**：学习自定义协议和动态代理
2. **WebSocket聊天室**：学习实时通信和群组管理
3. **HTTP文件服务器**：学习文件传输和零拷贝
4. **TCP推送服务**：学习长连接和心跳机制

通过这些项目，你可以：
- ✅ 掌握Netty的实际应用
- ✅ 理解不同协议的特点
- ✅ 学习最佳实践
- ✅ 积累项目经验

---

**Happy Coding! 🚀**
