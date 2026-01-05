# 同步工具类深度学习指南

## 📚 目录结构

```
sync/
├── docs/                                    # 文档目录
│   ├── 01_CountDownLatch详解.md             # 第一章：倒计时门栓
│   ├── 02_CyclicBarrier详解.md              # 第二章：循环栅栏
│   ├── 03_Semaphore详解.md                  # 第三章：信号量
│   ├── 04_Exchanger详解.md                  # 第四章：交换器
│   └── 05_Phaser详解.md                     # 第五章：分阶段器
├── demo/                                    # 演示代码
│   ├── CountDownLatchDemo.java             # CountDownLatch使用
│   ├── CyclicBarrierDemo.java              # CyclicBarrier使用
│   ├── SemaphoreDemo.java                  # Semaphore使用
│   ├── ExchangerDemo.java                  # Exchanger使用
│   └── PhaserDemo.java                     # Phaser使用
├── project/                                 # 实际项目Demo
│   ├── ParallelTaskRunner.java             # 并行任务执行器
│   ├── ResourcePool.java                   # 基于Semaphore的资源池
│   └── DataExchangeService.java            # 数据交换服务
└── README.md                                # 本文件
```

---

## 🎯 核心工具类对比

| 工具类 | 作用 | 可重用 | 适用场景 |
|--------|------|--------|---------|
| **CountDownLatch** | 等待多个线程完成 | ❌ 不可重用 | 主线程等待子线程 |
| **CyclicBarrier** | 多个线程互相等待 | ✅ 可重用 | 多线程协同工作 |
| **Semaphore** | 控制并发数量 | ✅ 可重用 | 限流、资源池 |
| **Exchanger** | 两个线程交换数据 | ✅ 可重用 | 数据交换 |
| **Phaser** | 分阶段的栅栏 | ✅ 可重用 | 多阶段任务 |

---

## 💡 快速选择指南

```
需要等待多个线程完成？
  └─> 主线程等待？
       ├─> 是 → CountDownLatch
       └─> 否，线程互相等待 → CyclicBarrier

需要控制并发数量？
  └─> Semaphore

需要两个线程交换数据？
  └─> Exchanger

需要分多个阶段执行？
  └─> Phaser
```

---

## 📖 参考资料

- 《Java并发编程实战》第5章：基础构建模块
- 《Java并发编程的艺术》第8章：Java中的并发工具类

---

**Happy Learning! 🚀**
