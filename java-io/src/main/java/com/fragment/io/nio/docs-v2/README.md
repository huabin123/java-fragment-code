# NIO 模块文档（v2）

> 本目录是对 `docs/` 目录的重组优化版本，原 `docs/` 目录保持不变。

## 文档结构

| 章节 | 文件 | 核心内容 |
|------|------|---------|
| 第一章 | [01_NIO核心概念与三大组件.md](./01_NIO核心概念与三大组件.md) | NIO 解决 BIO 什么问题、三大组件（Channel/Buffer/Selector）快速体验 |
| 第二章 | [02_Buffer深度解析.md](./02_Buffer深度解析.md) | 三指针状态图、flip/clear/compact 区别、Direct vs Heap |
| 第三章 | [03_Selector与多路复用.md](./03_Selector与多路复用.md) | epoll 原理、SelectionKey 四种事件、OP_WRITE 陷阱、Attachment |
| 第四章 | [04_Channel与FileChannel.md](./04_Channel与FileChannel.md) | FileChannel 读写、MappedByteBuffer、transferTo 零拷贝、Scatter/Gather |
| 第五章 | [05_NIO最佳实践.md](./05_NIO最佳实践.md) | 生产级 NIO 七要点、Reactor 模式、Direct Buffer 复用、常见 Bug 速查 |

## 快速入口

**CPU 100%（OP_WRITE 导致）**：→ 第三章 3.2（OP_WRITE 只在有数据时注册）

**事件被重复处理**：→ 第三章 3.4（`selectedKeys` 必须手动 remove）

**大文件传输慢**：→ 第四章 4.2（`FileChannel.transferTo` 零拷贝）

**`register()` 抛 `IllegalBlockingModeException`**：→ 第一章 1.4（先 `configureBlocking(false)`）

**write() 只发了一部分数据**：→ 第五章 5.1（write 要循环直到 `hasRemaining()==false`）

## 与 Demo 代码对应

```
demo/
├── SelectorDemo.java    ← 第一章（NIO 服务器骨架）、第三章（SelectionKey、epoll）
├── BufferDemo.java      ← 第二章（三指针状态、flip/clear/compact、Direct vs Heap）
├── ChannelDemo.java     ← 第四章（FileChannel 随机访问、MappedByteBuffer、Scatter/Gather）
├── ZeroCopyDemo.java    ← 第四章（transferTo 零拷贝）、第五章（Direct Buffer 最佳实践）
└── ReactorDemo.java     ← 第五章（单线程/多线程 Reactor、生产级 NIO 服务器）
```

## 与原文档的差异

| 原问题 | 优化后 |
|-------|--------|
| Buffer 章节缺少三指针状态变化图 | 第二章增加完整的状态变化可视化 |
| 原 `03_Channel与文件操作.md` 过长（43KB）| 拆分为 Channel（第四章）和 Selector（第三章）两章 |
| epoll 底层原理未讲解 | 第三章新增 select/poll/epoll 对比与 O(1) 原理 |
| 常见 Bug 散落各处 | 第五章集中整理为 Bug 速查表 |
| Reactor 模式未独立成节 | 第五章 5.2 完整讲解三种 Reactor 变体 |
