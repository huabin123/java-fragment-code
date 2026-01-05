# Java并发编程(JUC)深度学习指南

> 基于java-core/thread、threadpool、threadlocal的深入学习，系统掌握JUC并发包

---

## 📚 模块概览

```
java-juc/
├── jmm/                    # Java内存模型 ✅ 已创建
├── atomic/                 # 原子类与CAS ✅ 已创建
├── lock/                   # 显式锁
├── aqs/                    # AQS框架
├── sync/                   # 同步工具类
├── container/              # 并发容器
└── async/                  # 异步编程
```

---

## 🎯 学习路径图

```
前置知识（java-core模块）
├── thread          ✅ 线程基础、生命周期、协作机制
├── threadpool      ✅ 线程池原理与实践
└── threadlocal     ✅ ThreadLocal原理与应用
    ↓
JUC深入学习（java-juc模块）
├── 1. jmm          ← 理论基础（可见性、原子性、有序性）
├── 2. atomic       ← 无锁编程（CAS、原子类）
├── 3. lock         ← 显式锁（ReentrantLock、ReadWriteLock）
├── 4. aqs          ← 核心框架（AQS实现原理）
├── 5. sync         ← 同步工具（CountDownLatch、Semaphore等）
├── 6. container    ← 并发容器（ConcurrentHashMap等）
└── 7. async        ← 异步编程（CompletableFuture）
```

---

## 📖 各模块详细介绍

### 1. JMM - Java内存模型 ✅

**学习目标**：理解并发编程的理论基础

**核心内容**：
- JMM基础与三大特性（可见性、原子性、有序性）
- happens-before原则
- volatile深入解析
- final的内存语义
- 内存屏障与指令重排序

**实践项目**：
- 可见性/原子性/有序性问题演示
- 双重检查锁单例模式
- 基于volatile的简单缓存

**学习时间**：3-5天

---

### 2. Atomic - 原子类与无锁编程 ✅

**学习目标**：掌握CAS和原子类的使用

**核心内容**：
- CAS原理与实现
- 基本类型原子类（AtomicInteger、AtomicLong）
- 引用类型原子类（AtomicReference、AtomicStampedReference）
- 数组类型原子类
- 高性能原子类（LongAdder、LongAccumulator）

**实践项目**：
- CAS操作演示
- 无锁计数器
- 无锁栈实现

**学习时间**：3-5天

---

### 3. Lock - 显式锁 🔄

**学习目标**：掌握Lock接口及其实现

**核心内容**：
- Lock接口与ReentrantLock
- 公平锁vs非公平锁
- Condition条件队列
- ReadWriteLock读写锁
- StampedLock乐观锁

**实践项目**：
- ReentrantLock使用演示
- 基于Lock的有界缓冲
- 读写锁缓存实现

**学习时间**：3-5天

---

### 4. AQS - AbstractQueuedSynchronizer 🔄

**学习目标**：理解JUC的核心基础框架

**核心内容**：
- AQS的设计思想
- 同步状态state与CLH队列
- 独占模式源码分析
- 共享模式源码分析
- 自定义同步器实践

**实践项目**：
- 自定义互斥锁
- 自定义信号量
- 自定义倒计时门栓

**学习时间**：5-7天（难度较高）

---

### 5. Sync - 同步工具类 🔄

**学习目标**：掌握高级同步工具

**核心内容**：
- CountDownLatch（倒计时门栓）
- CyclicBarrier（循环栅栏）
- Semaphore（信号量）
- Exchanger（交换器）
- Phaser（分阶段器）

**实践项目**：
- 并行任务执行器
- 基于Semaphore的资源池
- 数据交换服务

**学习时间**：3-5天

---

### 6. Container - 并发容器 🔄

**学习目标**：掌握线程安全的集合类

**核心内容**：
- ConcurrentHashMap深入
- CopyOnWriteArrayList/Set
- ConcurrentLinkedQueue/Deque
- BlockingQueue系列
- ConcurrentSkipListMap/Set

**实践项目**：
- 并发缓存实现
- 基于并发容器的事件总线
- 任务队列系统

**学习时间**：4-6天

---

### 7. Async - 异步编程 🔄

**学习目标**：掌握现代异步编程

**核心内容**：
- Future与Callable
- CompletableFuture基础
- 异步编排与组合
- 异常处理与超时控制
- 最佳实践与性能优化

**实践项目**：
- 异步HTTP客户端
- 并行数据处理器
- 异步工作流引擎

**学习时间**：3-5天

---

## 🚀 快速开始

### 环境要求

- JDK 8+
- Maven 3.x

### 克隆项目

```bash
git clone <repository-url>
cd java-fragment-code/java-juc
```

### 编译项目

```bash
mvn clean compile
```

### 运行示例

```bash
# 运行JMM模块的可见性演示
java -cp target/classes com.fragment.juc.jmm.demo.VisibilityDemo

# 运行Atomic模块的CAS演示
java -cp target/classes com.fragment.juc.atomic.demo.CASDemo
```

---

## 📊 学习建议

### 推荐学习顺序

1. **必学模块**（按顺序）：
   - jmm → atomic → lock → sync → container → async

2. **深入研究**（可选）：
   - aqs（理解JUC底层原理）

### 学习方法

1. **理论先行**：先阅读docs文档，理解原理
2. **实践验证**：运行demo代码，观察现象
3. **项目应用**：完成project项目，巩固知识
4. **源码研究**：阅读JDK源码，深入理解

### 时间规划

- **快速学习**：21天（每个模块3天）
- **深入学习**：35天（每个模块5天）
- **精通掌握**：50天（包括源码研究）

---

## 💡 核心知识点速查

### JMM三大特性

| 特性 | 说明 | 保证方式 |
|------|------|---------|
| **可见性** | 一个线程的修改对其他线程可见 | volatile、synchronized、final |
| **原子性** | 操作不可分割 | synchronized、Lock、Atomic |
| **有序性** | 按代码顺序执行 | volatile、synchronized、happens-before |

### 原子类分类

- **基本类型**：AtomicInteger、AtomicLong、AtomicBoolean
- **引用类型**：AtomicReference、AtomicStampedReference
- **数组类型**：AtomicIntegerArray、AtomicLongArray
- **字段更新器**：AtomicIntegerFieldUpdater等
- **高性能**：LongAdder、LongAccumulator

### 锁的分类

- **悲观锁**：synchronized、ReentrantLock
- **乐观锁**：CAS、StampedLock
- **读写锁**：ReadWriteLock
- **公平锁/非公平锁**：ReentrantLock构造参数

### 同步工具类

- **CountDownLatch**：等待多个线程完成
- **CyclicBarrier**：多个线程互相等待
- **Semaphore**：控制并发数量
- **Exchanger**：两个线程交换数据
- **Phaser**：分阶段的栅栏

### 并发容器

- **Map**：ConcurrentHashMap、ConcurrentSkipListMap
- **List**：CopyOnWriteArrayList
- **Set**：CopyOnWriteArraySet、ConcurrentSkipListSet
- **Queue**：ConcurrentLinkedQueue、BlockingQueue系列

---

## ⚠️ 常见陷阱

### 1. volatile不能保证原子性

```java
// ❌ 错误
private volatile int count = 0;
public void increment() {
    count++; // 非原子操作
}

// ✅ 正确
private AtomicInteger count = new AtomicInteger(0);
public void increment() {
    count.incrementAndGet();
}
```

### 2. 双重检查锁忘记volatile

```java
// ❌ 错误
private static Singleton instance;

// ✅ 正确
private static volatile Singleton instance;
```

### 3. 死锁问题

```java
// ❌ 可能死锁
synchronized (lockA) {
    synchronized (lockB) {
        // ...
    }
}

// ✅ 使用tryLock避免死锁
if (lockA.tryLock()) {
    try {
        if (lockB.tryLock()) {
            try {
                // ...
            } finally {
                lockB.unlock();
            }
        }
    } finally {
        lockA.unlock();
    }
}
```

### 4. 忘记unlock

```java
// ❌ 错误
lock.lock();
doSomething(); // 可能抛异常
lock.unlock(); // 可能不执行

// ✅ 正确
lock.lock();
try {
    doSomething();
} finally {
    lock.unlock(); // 一定执行
}
```

---

## 📖 参考资料

### 官方文档

- [Java Concurrency Utilities](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/package-summary.html)
- [Java Memory Model](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html)

### 推荐书籍

1. **《Java并发编程实战》**（必读）
   - 作者：Brian Goetz
   - 难度：⭐⭐⭐⭐
   - 推荐指数：⭐⭐⭐⭐⭐

2. **《Java并发编程的艺术》**
   - 作者：方腾飞等
   - 难度：⭐⭐⭐⭐⭐
   - 推荐指数：⭐⭐⭐⭐⭐

3. **《深入理解Java虚拟机》**
   - 作者：周志明
   - 难度：⭐⭐⭐⭐⭐
   - 推荐指数：⭐⭐⭐⭐⭐

### 在线资源

- [并发编程网](http://ifeve.com/)
- [阿里巴巴Java开发手册](https://github.com/alibaba/p3c)
- [Doug Lea's Workstation](http://gee.cs.oswego.edu/dl/)

---

## 🎓 学习成果

完成本系列学习后，你将能够：

### 理论层面

- ✅ 深入理解Java内存模型
- ✅ 掌握happens-before原则
- ✅ 理解CAS和AQS的原理
- ✅ 掌握各种锁的实现机制

### 实践层面

- ✅ 正确使用volatile和synchronized
- ✅ 熟练使用原子类和Lock
- ✅ 掌握同步工具类的应用
- ✅ 熟练使用并发容器
- ✅ 掌握异步编程技巧

### 能力提升

- 🎯 能够设计高并发系统
- 🔍 能够分析并发问题根因
- 💡 能够选择合适的并发工具
- 📚 能够阅读JUC源码
- ✨ 能够实现自定义同步器

---

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 贡献内容

- 修复文档错误
- 补充示例代码
- 优化项目实现
- 添加测试用例

---

## 📝 学习检查清单

### JMM模块 ✅

- [ ] 理解JMM的三大特性
- [ ] 掌握happens-before规则
- [ ] 熟练使用volatile
- [ ] 理解双重检查锁

### Atomic模块 ✅

- [ ] 理解CAS原理
- [ ] 掌握AtomicInteger使用
- [ ] 理解ABA问题
- [ ] 掌握LongAdder使用

### Lock模块 🔄

- [ ] 掌握ReentrantLock使用
- [ ] 理解公平锁和非公平锁
- [ ] 掌握Condition使用
- [ ] 掌握ReadWriteLock使用

### AQS模块 🔄

- [ ] 理解AQS的设计思想
- [ ] 理解CLH队列
- [ ] 理解独占模式和共享模式
- [ ] 能够实现自定义同步器

### Sync模块 🔄

- [ ] 掌握CountDownLatch使用
- [ ] 掌握CyclicBarrier使用
- [ ] 掌握Semaphore使用
- [ ] 理解各工具的适用场景

### Container模块 🔄

- [ ] 理解ConcurrentHashMap原理
- [ ] 掌握CopyOnWrite容器使用
- [ ] 掌握BlockingQueue使用
- [ ] 能够选择合适的并发容器

### Async模块 🔄

- [ ] 掌握CompletableFuture使用
- [ ] 掌握异步编排
- [ ] 掌握异常处理
- [ ] 理解异步编程最佳实践

---

## 📅 更新日志

### 2025-12-31

- ✅ 创建项目结构
- ✅ 完成JMM模块（README + 4个demo + 2个project）
- ✅ 完成Atomic模块（README）
- 🔄 其他模块规划完成，待实现

---

## 📧 联系方式

如有问题或建议，欢迎联系：

- Issue: [GitHub Issues](https://github.com/your-repo/issues)
- Email: your-email@example.com

---

**Happy Learning! 让我们一起掌握Java并发编程！🚀**
