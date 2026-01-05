# 并发容器深度学习指南

> **学习目标**：从ConcurrentHashMap到跳表，掌握Java并发容器的核心技术

---

## 📚 目录结构

```
container/
├── docs/                                    # 文档目录（5个，约40000字）
│   ├── 01_ConcurrentHashMap深入.md          # 第一章：JDK 7/8实现、分段锁、CAS
│   ├── 02_CopyOnWrite容器.md                # 第二章：写时复制、读多写少场景
│   ├── 03_并发队列详解.md                   # 第三章：无锁队列、松弛阈值
│   ├── 04_阻塞队列深入.md                   # 第四章：BlockingQueue、生产者消费者
│   └── 05_跳表容器SkipList.md               # 第五章：跳表原理、并发有序Map
├── demo/                                    # 演示代码（4个）
│   ├── ConcurrentHashMapDemo.java          # ConcurrentHashMap使用
│   ├── CopyOnWriteDemo.java                # CopyOnWrite容器使用
│   ├── ConcurrentQueueDemo.java            # 并发队列使用
│   └── SkipListDemo.java                   # 跳表容器使用
├── project/                                 # 实际项目Demo（3个）
│   ├── ConcurrentCache.java                # 并发缓存实现
│   ├── EventBus.java                       # 基于并发容器的事件总线
│   └── TaskQueue.java                      # 任务队列系统
└── README.md                                # 本文件
```

---

## 🎯 容器选择指南

### Map选择

```
需要线程安全的Map？
  ├─> 高并发读写 → ConcurrentHashMap
  ├─> 需要排序 → ConcurrentSkipListMap
  └─> 读多写少 → 普通HashMap + ReadWriteLock
```

### List选择

```
需要线程安全的List？
  ├─> 读多写少 → CopyOnWriteArrayList
  └─> 读写都多 → Collections.synchronizedList()
```

### Queue选择

```
需要线程安全的Queue？
  ├─> 无界非阻塞 → ConcurrentLinkedQueue
  ├─> 有界阻塞 → ArrayBlockingQueue
  ├─> 无界阻塞 → LinkedBlockingQueue
  ├─> 优先级 → PriorityBlockingQueue
  └─> 延迟 → DelayQueue
```

---

## 💡 核心容器对比

| 容器 | 线程安全 | 阻塞 | 有界 | 排序 | 适用场景 |
|------|---------|------|------|------|---------|
| **ConcurrentHashMap** | ✅ | ❌ | ❌ | ❌ | 高并发Map |
| **CopyOnWriteArrayList** | ✅ | ❌ | ❌ | ❌ | 读多写少List |
| **ConcurrentLinkedQueue** | ✅ | ❌ | ❌ | ❌ | 高并发队列 |
| **ArrayBlockingQueue** | ✅ | ✅ | ✅ | ❌ | 生产者消费者 |
| **LinkedBlockingQueue** | ✅ | ✅ | ❌ | ❌ | 生产者消费者 |
| **PriorityBlockingQueue** | ✅ | ✅ | ❌ | ✅ | 优先级队列 |
| **ConcurrentSkipListMap** | ✅ | ❌ | ❌ | ✅ | 并发有序Map |

---

## 🎓 学习路径

### 阶段1：ConcurrentHashMap（第1章）

**核心问题**：
- 为什么需要ConcurrentHashMap？
- JDK 7和JDK 8的实现有什么区别？
- 如何保证线程安全？
- size()为什么不精确？

**学习方式**：
1. 阅读 `docs/01_ConcurrentHashMap深入.md`
2. 运行 `demo/ConcurrentHashMapDemo.java`
3. 理解分段锁和CAS的区别
4. 分析put和get的源码

**关键收获**：
- ✅ 理解分段锁和CAS的设计思想
- ✅ 掌握ConcurrentHashMap的使用
- ✅ 理解链表转红黑树的优化
- ✅ 了解size()的实现原理

---

### 阶段2：CopyOnWrite容器（第2章）

**核心问题**：
- 什么是写时复制？
- 为什么适合读多写少？
- 内存占用如何？
- 什么时候不应该使用？

**学习方式**：
1. 阅读 `docs/02_CopyOnWrite容器.md`
2. 运行 `demo/CopyOnWriteDemo.java`
3. 理解写时复制的机制
4. 分析性能特点

**关键收获**：
- ✅ 理解写时复制的原理
- ✅ 掌握适用场景
- ✅ 了解性能特点
- ✅ 避免常见陷阱

---

### 阶段3：并发队列（第3章）

**核心问题**：
- 什么是无锁队列？
- 松弛阈值是什么？
- 为什么size()很慢？
- 如何实现无锁算法？

**学习方式**：
1. 阅读 `docs/03_并发队列详解.md`
2. 运行 `demo/ConcurrentQueueDemo.java`
3. 理解CAS的应用
4. 分析offer和poll的源码

**关键收获**：
- ✅ 理解无锁队列的实现
- ✅ 掌握CAS的应用
- ✅ 理解松弛阈值的优化
- ✅ 了解自引用的作用

---

### 阶段4：阻塞队列（第4章）

**核心问题**：
- 什么是阻塞队列？
- ArrayBlockingQueue和LinkedBlockingQueue有什么区别？
- 如何实现生产者消费者模式？
- 如何选择合适的阻塞队列？

**学习方式**：
1. 阅读 `docs/04_阻塞队列深入.md`
2. 实践生产者消费者模式
3. 对比不同阻塞队列的性能
4. 分析put和take的源码

**关键收获**：
- ✅ 理解阻塞队列的原理
- ✅ 掌握生产者消费者模式
- ✅ 了解不同队列的特点
- ✅ 能够选择合适的队列

---

### 阶段5：跳表容器（第5章）

**核心问题**：
- 什么是跳表？
- 为什么跳表适合并发？
- 如何实现有序Map？
- 什么时候使用ConcurrentSkipListMap？

**学习方式**：
1. 阅读 `docs/05_跳表容器SkipList.md`
2. 运行 `demo/SkipListDemo.java`
3. 理解跳表的数据结构
4. 分析查找和插入的过程

**关键收获**：
- ✅ 理解跳表的原理
- ✅ 掌握NavigableMap的使用
- ✅ 了解跳表的性能特点
- ✅ 能够应用到实际场景

---

## 📖 参考资料

### 官方文档
- [java.util.concurrent包文档](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/package-summary.html)
- [ConcurrentHashMap API](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html)

### 推荐书籍
- 《Java并发编程实战》第5章：基础构建模块
- 《Java并发编程的艺术》第6章：Java并发容器和框架

### 论文
- Michael & Scott：Simple, Fast, and Practical Non-Blocking Queue
- William Pugh：Skip Lists: A Probabilistic Alternative to Balanced Trees

---

## 📊 文档统计

- **文档数量**：5个
- **总字数**：约40000字
- **代码示例**：150+个
- **实际场景**：50+个
- **Demo代码**：4个
- **Project代码**：3个

---

## 🎓 学习成果

完成本模块学习后，你将能够：

- ✅ 深入理解各种并发容器的实现原理
- ✅ 熟练选择和使用合适的并发容器
- ✅ 理解无锁算法和CAS的应用
- ✅ 掌握生产者消费者模式
- ✅ 理解跳表等高级数据结构
- ✅ 能够在实际项目中应用并发容器
- ✅ 避免常见的并发容器陷阱

---

**Happy Learning! 🚀**

**开始学习**：从 `docs/01_ConcurrentHashMap深入.md` 开始，循序渐进掌握Java并发容器！
