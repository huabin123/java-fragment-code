# optimization 模块文档（v2）

> 本目录是对 `docs/` 目录的重组优化版本，原 `docs/` 目录保持不变。

## 文档结构

| 章节 | 文件 | 核心内容 |
|------|------|---------|
| 第一章 | [01_连接池与对象池.md](./01_连接池与对象池.md) | TCP 建连代价量化、连接池参数调优、最大连接数估算、ByteBuffer 对象池 |
| 第二章 | [02_零拷贝技术详解.md](./02_零拷贝技术详解.md) | 传统 I/O 四次拷贝路径、mmap/sendfile/Direct Buffer 三种零拷贝、性能对比 |
| 第三章 | [03_IO性能调优实战.md](./03_IO性能调优实战.md) | 瓶颈定位命令（top/iostat/strace）、缓冲区大小基准、批量写、线程数公式 |

## 快速入口

**数据库连接池配置**：→ 第一章 1.2（maxPoolSize / connectionTimeout / maxLifetime 含义）

**最大连接数设多少**：→ 第一章 1.2（估算公式：`业务线程数 × 查询时间/请求间隔`）

**大文件传输慢**：→ 第二章 2.2（`FileChannel.transferTo` 底层 sendfile）

**定位 I/O 瓶颈**：→ 第三章 3.1（`top` iowait、`iostat -x`、`strace -c`）

**小消息高并发吞吐低**：→ 第三章 3.4（批量写 Gather Write，减少系统调用）

## 与 Demo 代码对应

```
demo/
├── ConnectionPoolDemo.java      ← 第一章（简单连接池实现、ByteBuffer 对象池）
├── ZeroCopyDemo.java            ← 第二章（传统 I/O vs mmap vs sendfile 性能对比）
└── PerformanceTuningDemo.java   ← 第三章（缓冲区大小基准、Socket 调优、批量写）
```

## 与原文档的差异

| 原问题 | 优化后 |
|-------|--------|
| 连接池章节缺乏参数含义解释 | 第一章补充每个参数的作用和调优建议 |
| 零拷贝只说"减少拷贝"，无数据流路径图 | 第二章绘制完整的四次拷贝路径 vs 零拷贝路径 |
| 性能调优缺乏定量化对比 | 第三章补充缓冲区大小基准测试数据和性能提升百分比 |
| 缺乏系统级调优命令 | 第三章 3.1 新增 top/iostat/strace 排查流程 |
