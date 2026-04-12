# AIO 模块文档（v2）

> 本目录是对 `docs/` 目录的重组优化版本，原 `docs/` 目录保持不变。

## 文档结构

| 章节 | 文件 | 核心内容 |
|------|------|---------|
| 第一章 | [01_AIO原理与适用场景.md](./01_AIO原理与适用场景.md) | BIO/NIO/AIO 三种模型对比、Linux 上的真相、两种编程模型 |
| 第二章 | [02_核心组件详解.md](./02_核心组件详解.md) | ChannelGroup、AsynchronousServerSocketChannel、异步文件读写、CompletableFuture 包装 |
| 第三章 | [03_AIO最佳实践与陷阱.md](./03_AIO最佳实践与陷阱.md) | 五个常见陷阱、性能对比、AIO vs NIO 选型建议 |

## 快速入口

**AIO 服务端只能接受一个连接**：→ 第二章 2.2（accept 完成后必须立即重新调用）

**回调中执行数据库查询导致 I/O 变慢**：→ 第二章 2.1（回调不做耗时操作，转交业务线程池）

**主线程退出 AIO 任务未完成**：→ 第三章 3.1（陷阱5，主线程 `latch.await()`）

**回调嵌套太深难以阅读**：→ 第二章 2.5（用 CompletableFuture 包装 AIO 回调）

**AIO 服务端连接 fd 泄漏**：→ 第三章 3.1（陷阱4，`failed()` 中必须关闭 channel）

## 与 Demo 代码对应

```
demo/
├── AsynchronousSocketChannelDemo.java  ← 第一章（AIO vs NIO 对比）、第二章（AIO 服务端实现）
├── AsynchronousFileChannelDemo.java    ← 第二章 2.4（异步文件读写）
└── CompletionHandlerDemo.java          ← 第二章 2.5（CompletableFuture 包装）、第三章（陷阱演示）
```

## 与原文档的差异

| 原问题 | 优化后 |
|-------|--------|
| 未说明 Linux 上 AIO 是线程池模拟 | 第一章新增 Linux/Windows/macOS 底层实现对比 |
| 原 `04_AIO实战与陷阱.md`（35KB）内容分散 | 重组为核心组件（第二章）和最佳实践（第三章）|
| Proactor 模式章节理论多代码少 | 第二章以代码为主，每个组件给出完整可运行示例 |
| 缺乏 AIO vs NIO 的实测性能对比 | 第三章 3.2 补充基准测试结论 |
| 未覆盖 CompletableFuture 包装 AIO | 第二章 2.5 新增完整包装工具代码 |
