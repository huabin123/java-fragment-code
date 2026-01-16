# 显式锁(Lock)深度学习指南

## 📚 目录结构

```
lock/
├── docs/                                    # 文档目录
│   ├── 01_Lock接口与ReentrantLock.md        # 第一章：Lock接口、ReentrantLock基础
│   ├── 02_公平锁与非公平锁.md                # 第二章：公平性、性能对比
│   ├── 03_Condition条件队列.md               # 第三章：Condition使用、生产者消费者
│   ├── 04_ReadWriteLock读写锁.md             # 第四章：读写分离、性能优化
│   ├── 05_StampedLock乐观锁.md               # 第五章：乐观读、性能提升
│   └── 06_Lock实现原理与AQS.md               # 第六章：AQS源码分析、实现原理
├── demo/                                    # 演示代码
│   ├── ReentrantLockDemo.java              # ReentrantLock基本使用
│   ├── ConditionDemo.java                  # Condition条件队列演示
│   ├── ReadWriteLockDemo.java              # 读写锁演示
│   └── StampedLockDemo.java                # StampedLock演示
├── project/                                 # 实际项目Demo
│   ├── BoundedBufferWithLock.java          # 基于Lock的有界缓冲
│   └── ReadWriteCache.java                 # 读写锁缓存实现
└── README.md                                # 本文件
```

---

## 🎯 学习路径

### 阶段1：掌握ReentrantLock（第1章）

**核心问题**：

- ❓ Lock接口有哪些方法？
- ❓ ReentrantLock vs synchronized，有什么区别？
- ❓ 什么是可重入锁？
- ❓ tryLock()的使用场景是什么？
- ❓ lockInterruptibly()如何响应中断？
- ❓ 为什么必须在finally中unlock()？

**学习方式**：

1. 阅读 `docs/01_Lock接口与ReentrantLock.md`
2. 运行 `demo/ReentrantLockDemo.java`
3. 对比Lock和synchronized的使用
4. 实践各种lock方法

**关键收获**：

- ✅ 掌握Lock接口的使用
- ✅ 理解ReentrantLock的特性
- ✅ 掌握正确的加锁解锁模式
- ✅ 了解Lock的优势和适用场景

---

### 阶段2：理解公平锁（第2章）

**核心问题**：

- ❓ 什么是公平锁和非公平锁？
- ❓ 公平锁如何保证FIFO？
- ❓ 公平锁vs非公平锁，性能差异有多大？
- ❓ 什么时候应该使用公平锁？
- ❓ 如何选择公平性策略？

**学习方式**：

1. 阅读 `docs/02_公平锁与非公平锁.md`
2. 对比公平锁和非公平锁的行为
3. 进行性能测试
4. 分析适用场景

**关键收获**：

- ✅ 理解公平锁的实现原理
- ✅ 掌握公平性的权衡
- ✅ 了解性能差异
- ✅ 能够选择合适的策略

---

### 阶段3：精通Condition（第3章）

**核心问题**：

- ❓ Condition是什么？
- ❓ Condition vs wait/notify，有什么区别？
- ❓ 如何使用多个Condition？
- ❓ await()和signal()的工作原理？
- ❓ 如何实现生产者-消费者模式？

**学习方式**：

1. 阅读 `docs/03_Condition条件队列.md`
2. 运行 `demo/ConditionDemo.java`
3. 实现生产者-消费者模式
4. 理解条件队列的原理

**关键收获**：

- ✅ 掌握Condition的使用
- ✅ 理解条件队列的原理
- ✅ 掌握多条件的应用
- ✅ 能够实现复杂的线程协作

---

### 阶段4：掌握ReadWriteLock（第4章）

**核心问题**：

- ❓ 什么是读写锁？
- ❓ 读写锁如何提升性能？
- ❓ 读锁和写锁的规则是什么？
- ❓ 锁降级是什么？
- ❓ 什么场景适合使用读写锁？

**学习方式**：

1. 阅读 `docs/04_ReadWriteLock读写锁.md`
2. 运行 `demo/ReadWriteLockDemo.java`
3. 实现读写锁缓存
4. 进行性能对比

**关键收获**：

- ✅ 掌握读写锁的使用
- ✅ 理解读写分离的原理
- ✅ 掌握锁降级技术
- ✅ 了解读写锁的适用场景

---

### 阶段5：深入StampedLock（第5章）

**核心问题**：

- ❓ StampedLock是什么？
- ❓ 乐观读是如何工作的？
- ❓ StampedLock vs ReadWriteLock，有什么区别？
- ❓ 如何正确使用乐观读？
- ❓ StampedLock有哪些注意事项？

**学习方式**：

1. 阅读 `docs/05_StampedLock乐观锁.md`
2. 运行 `demo/StampedLockDemo.java`
3. 对比StampedLock和ReadWriteLock
4. 理解乐观读的原理

**关键收获**：

- ✅ 掌握StampedLock的使用
- ✅ 理解乐观读的原理
- ✅ 掌握锁转换技术
- ✅ 了解StampedLock的适用场景

---

### 阶段6：理解实现原理（第6章）⭐

**核心问题**：

- ❓ AQS是什么？如何工作？
- ❓ ReentrantLock如何基于AQS实现？
- ❓ 加锁和解锁的完整流程是什么？
- ❓ 等待队列是如何管理的？
- ❓ 公平锁和非公平锁的实现差异？
- ❓ 如何实现可重入？

**学习方式**：

1. 阅读 `docs/06_Lock实现原理与AQS.md`
2. 对照JDK源码理解实现细节
3. 画出加锁解锁流程图
4. 理解AQS的设计思想

**关键收获**：

- ✅ 深入理解AQS框架
- ✅ 掌握Lock的实现原理
- ✅ 理解同步队列的管理
- ✅ 掌握CAS和LockSupport的使用
- ✅ 理解模板方法模式的应用

---

## 🚀 快速开始

### 1. 运行ReentrantLock演示

```bash
javac -d target/classes src/main/java/com/fragment/juc/lock/demo/ReentrantLockDemo.java
java -cp target/classes com.fragment.juc.lock.demo.ReentrantLockDemo
```

**演示内容**：
- ReentrantLock的基本使用
- tryLock()的使用
- lockInterruptibly()响应中断

---

### 2. 运行Condition演示

```bash
javac -d target/classes src/main/java/com/fragment/juc/lock/demo/ConditionDemo.java
java -cp target/classes com.fragment.juc.lock.demo.ConditionDemo
```

**演示内容**：
- Condition的基本使用
- 多条件队列
- 生产者-消费者模式

---

### 3. 运行ReadWriteLock演示

```bash
javac -d target/classes src/main/java/com/fragment/juc/lock/demo/ReadWriteLockDemo.java
java -cp target/classes com.fragment.juc.lock.demo.ReadWriteLockDemo
```

**演示内容**：
- 读写锁的使用
- 读写分离的性能优势
- 锁降级

---

## 💡 核心知识点

### 1. Lock vs synchronized

| 特性 | synchronized | Lock |
|------|--------------|------|
| **使用方式** | 关键字 | 接口 |
| **锁的释放** | 自动 | 手动（必须finally） |
| **可中断** | 不可中断 | lockInterruptibly() |
| **尝试获取** | 不支持 | tryLock() |
| **公平性** | 非公平 | 可选公平/非公平 |
| **条件队列** | 单个（wait/notify） | 多个（Condition） |
| **性能** | JDK6后优化，相近 | 略高 |
| **灵活性** | 低 | 高 |

---

### 2. Lock接口方法

```java
public interface Lock {
    // 获取锁（阻塞）
    void lock();
    
    // 可中断地获取锁
    void lockInterruptibly() throws InterruptedException;
    
    // 尝试获取锁（非阻塞）
    boolean tryLock();
    
    // 超时获取锁
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;
    
    // 释放锁
    void unlock();
    
    // 创建条件队列
    Condition newCondition();
}
```

---

### 3. ReentrantLock使用模板

```java
Lock lock = new ReentrantLock();

lock.lock();
try {
    // 临界区代码
} finally {
    lock.unlock(); // 必须在finally中释放
}
```

---

### 4. 读写锁规则

| 操作 | 读锁 | 写锁 |
|------|------|------|
| **读锁** | ✅ 可以 | ❌ 不可以 |
| **写锁** | ❌ 不可以 | ❌ 不可以 |

**规则**：
- 读-读：不互斥
- 读-写：互斥
- 写-写：互斥

---

### 5. StampedLock三种模式

```java
StampedLock lock = new StampedLock();

// 1. 写锁（独占）
long stamp = lock.writeLock();
try {
    // 写操作
} finally {
    lock.unlockWrite(stamp);
}

// 2. 悲观读锁（共享）
long stamp = lock.readLock();
try {
    // 读操作
} finally {
    lock.unlockRead(stamp);
}

// 3. 乐观读（无锁）
long stamp = lock.tryOptimisticRead();
// 读取数据
if (!lock.validate(stamp)) {
    // 数据被修改，升级为悲观读锁
    stamp = lock.readLock();
    try {
        // 重新读取
    } finally {
        lock.unlockRead(stamp);
    }
}
```

---

## ⚠️ 常见陷阱

### 1. 忘记unlock()

```java
// ❌ 错误：异常时不会释放锁
lock.lock();
doSomething(); // 可能抛异常
lock.unlock();

// ✅ 正确：使用finally
lock.lock();
try {
    doSomething();
} finally {
    lock.unlock();
}
```

---

### 2. 重复unlock()

```java
// ❌ 错误：重复释放
lock.lock();
try {
    doSomething();
    lock.unlock(); // 错误的位置
} finally {
    lock.unlock(); // 重复释放，抛异常
}

// ✅ 正确：只在finally中释放一次
lock.lock();
try {
    doSomething();
} finally {
    lock.unlock();
}
```

---

### 3. 死锁

```java
// ❌ 可能死锁
public void transfer(Account from, Account to, int amount) {
    from.lock.lock();
    to.lock.lock();
    try {
        // 转账
    } finally {
        to.lock.unlock();
        from.lock.unlock();
    }
}

// ✅ 使用tryLock避免死锁
public boolean transfer(Account from, Account to, int amount) {
    if (from.lock.tryLock()) {
        try {
            if (to.lock.tryLock()) {
                try {
                    // 转账
                    return true;
                } finally {
                    to.lock.unlock();
                }
            }
        } finally {
            from.lock.unlock();
        }
    }
    return false;
}
```

---

### 4. Condition使用错误

```java
// ❌ 错误：使用if
lock.lock();
try {
    if (!condition) {
        condition.await(); // 虚假唤醒
    }
} finally {
    lock.unlock();
}

// ✅ 正确：使用while
lock.lock();
try {
    while (!condition) {
        condition.await();
    }
} finally {
    lock.unlock();
}
```

---

## 📊 最佳实践

### 1. 优先使用try-finally模式

```java
// ✅ 标准模式
lock.lock();
try {
    // 业务逻辑
} finally {
    lock.unlock();
}
```

---

### 2. 使用tryLock避免死锁

```java
// ✅ 推荐
if (lock.tryLock(timeout, TimeUnit.SECONDS)) {
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
} else {
    // 获取锁失败的处理
}
```

---

### 3. 读多写少用ReadWriteLock

```java
// ✅ 读写分离
ReadWriteLock rwLock = new ReentrantReadWriteLock();

// 读操作
rwLock.readLock().lock();
try {
    // 读取数据
} finally {
    rwLock.readLock().unlock();
}

// 写操作
rwLock.writeLock().lock();
try {
    // 修改数据
} finally {
    rwLock.writeLock().unlock();
}
```

---

### 4. 高性能场景用StampedLock

```java
// ✅ 乐观读
long stamp = lock.tryOptimisticRead();
// 读取数据
if (!lock.validate(stamp)) {
    // 升级为悲观读
    stamp = lock.readLock();
    try {
        // 重新读取
    } finally {
        lock.unlockRead(stamp);
    }
}
```

---

## 📖 参考资料

### 官方文档

- [Lock API](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/Lock.html)
- [ReentrantLock API](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html)
- [ReadWriteLock API](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReadWriteLock.html)

### 推荐书籍

- 《Java并发编程实战》第13章：显式锁
- 《Java并发编程的艺术》第5章：Java中的锁

---

## 📝 总结

通过本模块的学习，你应该掌握：

1. ✅ **Lock接口**：lock()、tryLock()、lockInterruptibly()
2. ✅ **ReentrantLock**：可重入、公平/非公平
3. ✅ **Condition**：条件队列、await/signal
4. ✅ **ReadWriteLock**：读写分离、锁降级
5. ✅ **StampedLock**：乐观读、性能优化
6. ✅ **AQS原理**：同步队列、state状态、CAS操作

**核心收获**：

- 🎯 掌握显式锁的使用
- 🔍 理解各种锁的适用场景
- 💡 能够选择合适的锁
- 📚 掌握正确的加锁模式
- ✨ 避免常见的锁陷阱
- 🚀 深入理解Lock的实现原理

**继续学习**：

- 深入研究AQS的其他应用（Semaphore、CountDownLatch等）
- 学习同步工具类（sync模块）
- 学习并发容器（container模块）

---

**Happy Learning! 🚀**
