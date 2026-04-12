# Netty 深度学习文档（v2）

> 本目录是对 `docs/` 目录的重组优化版本，解决了原文档中的重复、风格不统一和内容缺失问题。原 `docs/` 目录保持不变。

---

## 文档结构

| 章节 | 文件 | 核心内容 |
|------|------|---------|
| 第一章 | [01_为什么需要Netty.md](./01_为什么需要Netty.md) | NIO 五大痛点、Netty 架构全景、Epoll Bug 修复原理 |
| 第二章 | [02_线程模型与EventLoop.md](./02_线程模型与EventLoop.md) | Reactor 模式、EventLoop 执行流程、黄金法则（不阻塞 EventLoop）|
| 第三章 | [03_ChannelPipeline责任链.md](./03_ChannelPipeline责任链.md) | 入站/出站事件流向、Handler 类型与共享、SimpleChannelInboundHandler |
| 第四章 | [04_粘包拆包与编解码.md](./04_粘包拆包与编解码.md) | 四种解帧方案、LengthField 最佳实践、自定义协议解码器 |
| 第五章 | [05_ByteBuf与零拷贝.md](./05_ByteBuf与零拷贝.md) | 双指针原理、三种零拷贝（slice/Composite/FileRegion）、引用计数 |
| 第六章 | [06_生产实践与最佳实践.md](./06_生产实践与最佳实践.md) | 心跳检测、优雅关闭、写缓冲水位线、常见问题排查 |

---

## 快速入口：按问题查找

**遇到 CPU 100%**  
→ 第二章 2.4（不要在 EventLoop 中执行耗时操作）、第六章 6.5（空轮询排查）

**遇到 Direct buffer memory OOM**  
→ 第五章 5.5（引用计数与内存泄漏）、第六章 6.5（开启泄漏检测）

**消息粘包/拆包问题**  
→ 第四章 4.2（四种解决方案）、4.3（`LengthFieldBasedFrameDecoder` 推荐配置）

**如何设计自定义二进制协议**  
→ 第四章 4.2（方案3+4）、4.4（继承 `ByteToMessageDecoder`）

**如何实现心跳/检测死连接**  
→ 第六章 6.1（`IdleStateHandler` 完整实现）

**高并发下写操作积压**  
→ 第六章 6.5（写缓冲区水位线 + `isWritable()`）

**如何广播消息给所有在线用户**  
→ 第六章 6.2（`ChannelGroup`）

---

## 与 Demo / Project 代码的对应关系

```
demo/
├── EchoServerDemo.java       ← 第一章（Netty vs NIO 对比）、第二章（Boss/Worker线程组）
├── PipelineDemo.java         ← 第三章（Pipeline 事件传播完整演示）
├── CodecDemo.java            ← 第四章（粘包拆包、长度字段、自定义协议）
├── ByteBufDemo.java          ← 第五章（双指针、零拷贝、引用计数）
└── HeartbeatDemo.java        ← 第六章（心跳、空闲检测、断线重连）

project/
├── rpc/                      ← 自定义协议 + Protobuf + 动态代理（第四、五章综合）
├── websocket/                ← WebSocket + ChannelGroup 广播（第三、六章综合）
├── http/                     ← HTTP codec + FileRegion 零拷贝（第五章综合）
└── push/                     ← 长连接推送 + 心跳 + 断线重连（第六章综合）
```

---

## 学习路径建议

```
入门路径（理解 Netty 基础）
  第一章 → 第二章 → 运行 EchoServerDemo.java

核心路径（掌握日常开发）
  第三章（Pipeline）→ 第四章（粘包拆包）→ 第五章（ByteBuf）

进阶路径（生产实战）
  第六章（心跳/优雅关闭）→ 阅读 project/ 下的实战项目

面试准备
  第二章 2.2（EventLoop 单线程模型）
  第四章 4.1（粘包拆包原因）
  第五章 5.3（ByteBuf 三种类型）、5.4（零拷贝实现）
  第六章 6.4（TCP 参数含义）
```

---

## 与原文档（docs/）的差异说明

| 原文档问题 | docs-v2 的改进 |
|-----------|--------------|
| `01_Netty核心概念与架构设计.md` 与 `01_为什么需要Netty及核心概念.md` 内容重复 | 合并为单一章节，去除冗余 |
| 章节编号风格不统一（"一、二、三"与"1.1、1.2"混用）| 统一为 `1.1 / 1.2` 风格 |
| 高级特性（心跳、关闭）内容过于简略 | 第六章补充了完整代码和参数说明 |
| 缺乏与 demo/project 代码的精确交叉引用 | 每章末尾和 README 均有精确对应 |
| 缺乏按问题查找的快速入口 | README 增加"按问题查找"速查表 |
| 线程模型独立章节缺失（原文档分散在多处）| 第二章专门讲 EventLoop 和线程安全规则 |
