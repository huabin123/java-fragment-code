# AQS(AbstractQueuedSynchronizer)深度学习指南

## 📚 目录结构

```
aqs/
├── docs/                                    # 文档目录
│   ├── 01_AQS设计思想.md                    # 第一章：模板方法、状态管理
│   ├── 02_同步状态与CLH队列.md              # 第二章：state、Node、队列操作
│   ├── 03_独占模式源码分析.md               # 第三章：acquire、release流程
│   ├── 04_共享模式源码分析.md               # 第四章：acquireShared、releaseShared
│   └── 05_自定义同步器实践.md               # 第五章：实现自定义锁
├── demo/                                    # 演示代码
│   ├── AQSStateDemo.java                   # AQS状态演示
│   ├── ExclusiveLockDemo.java              # 独占锁演示
│   └── SharedLockDemo.java                 # 共享锁演示
├── project/                                 # 实际项目Demo
│   ├── CustomMutex.java                    # 自定义互斥锁
│   ├── CustomSemaphore.java                # 自定义信号量
│   └── CustomCountDownLatch.java           # 自定义倒计时门栓
└── README.md                                # 本文件
```

---

## 🎯 AQS核心概念

### 1. 同步状态(state)

```java
// state的含义取决于具体实现
private volatile int state;

// ReentrantLock: state表示重入次数
// Semaphore: state表示可用许可数
// CountDownLatch: state表示倒计时数量
```

### 2. CLH队列

```
等待队列结构：
head -> Node1 -> Node2 -> Node3 -> tail
        (等待)   (等待)   (等待)
```

### 3. 模板方法

```java
// 需要子类实现的方法
protected boolean tryAcquire(int arg);
protected boolean tryRelease(int arg);
protected int tryAcquireShared(int arg);
protected boolean tryReleaseShared(int arg);
protected boolean isHeldExclusively();
```

---

## 💡 AQS的设计思想

### 核心思想

1. **模板方法模式**：定义算法骨架，子类实现具体逻辑
2. **状态管理**：使用volatile int state表示同步状态
3. **FIFO队列**：使用CLH队列管理等待线程
4. **独占/共享**：支持独占模式和共享模式

### 工作流程

```
获取锁流程：
1. tryAcquire() 尝试获取
   ├─> 成功：直接返回
   └─> 失败：进入队列等待
       ├─> 加入队列尾部
       ├─> 自旋或阻塞
       └─> 被唤醒后重试

释放锁流程：
1. tryRelease() 尝试释放
   ├─> 成功：唤醒后继节点
   └─> 失败：保持锁定状态
```

---

## 📊 基于AQS的同步器

| 同步器 | 模式 | state含义 |
|--------|------|-----------|
| **ReentrantLock** | 独占 | 重入次数 |
| **ReentrantReadWriteLock** | 共享+独占 | 高16位读锁，低16位写锁 |
| **Semaphore** | 共享 | 可用许可数 |
| **CountDownLatch** | 共享 | 倒计时数量 |
| **CyclicBarrier** | - | 基于ReentrantLock+Condition |

---

## ⚠️ 学习建议

AQS是JUC的核心基础，但也是最难的部分：

1. **先学会使用**：先掌握ReentrantLock等工具的使用
2. **再看源码**：有了使用经验后再研究AQS源码
3. **动手实践**：尝试实现自定义同步器
4. **循序渐进**：从简单的互斥锁开始，逐步深入

---

## 📖 参考资料

- 《Java并发编程的艺术》第5章：Java中的锁
- [AQS论文](http://gee.cs.oswego.edu/dl/papers/aqs.pdf) by Doug Lea
- [AQS源码](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/locks/AbstractQueuedSynchronizer.java)

---

**Happy Learning! 🚀**
