# 第一章：Lock接口与ReentrantLock - 显式锁的基础

> **学习目标**：深入理解Lock接口和ReentrantLock的使用

---

## 一、为什么需要Lock接口？

### 1.1 synchronized的局限性

```java
// synchronized的问题

// 问题1：无法中断
synchronized (lock) {
    // 如果获取不到锁，线程会一直阻塞
    // 无法响应中断
}

// 问题2：无法尝试获取
synchronized (lock) {
    // 无法尝试获取锁
    // 要么获取成功，要么一直等待
}

// 问题3：只有一个条件队列
synchronized (lock) {
    lock.wait();   // 只有一个等待队列
    lock.notify(); // 无法精确唤醒
}

// 问题4：无法选择公平性
synchronized (lock) {
    // 总是非公平的
}
```

### 1.2 Lock接口的优势

```
Lock的优势：

1. 可中断：lockInterruptibly()
   - 等待锁的线程可以响应中断

2. 可尝试：tryLock()
   - 尝试获取锁，立即返回
   - 可以避免死锁

3. 可超时：tryLock(timeout)
   - 限时等待锁

4. 多条件：newCondition()
   - 可以创建多个条件队列
   - 精确唤醒

5. 可选公平性：
   - 可以选择公平锁或非公平锁

6. 更灵活：
   - 不需要块结构
   - 可以跨方法
```

---

## 二、Lock接口详解

### 2.1 Lock接口方法

```java
public interface Lock {
    /**
     * 获取锁（阻塞）
     * - 如果锁可用，立即获取
     * - 如果锁不可用，阻塞等待
     * - 不响应中断
     */
    void lock();
    
    /**
     * 可中断地获取锁
     * - 如果锁可用，立即获取
     * - 如果锁不可用，阻塞等待
     * - 响应中断，抛出InterruptedException
     */
    void lockInterruptibly() throws InterruptedException;
    
    /**
     * 尝试获取锁（非阻塞）
     * - 如果锁可用，获取并返回true
     * - 如果锁不可用，立即返回false
     * - 不阻塞
     */
    boolean tryLock();
    
    /**
     * 超时获取锁
     * - 在指定时间内尝试获取锁
     * - 成功返回true，超时返回false
     * - 响应中断
     */
    boolean tryLock(long time, TimeUnit unit) 
        throws InterruptedException;
    
    /**
     * 释放锁
     * - 必须由持有锁的线程调用
     * - 通常在finally块中调用
     */
    void unlock();
    
    /**
     * 创建条件队列
     * - 返回与此锁绑定的Condition对象
     * - 可以创建多个Condition
     */
    Condition newCondition();
}
```

### 2.2 标准使用模式

```java
Lock lock = new ReentrantLock();

// 模式1：基本使用
lock.lock();
try {
    // 临界区代码
} finally {
    lock.unlock(); // 必须在finally中释放
}

// 模式2：可中断
try {
    lock.lockInterruptibly();
    try {
        // 临界区代码
    } finally {
        lock.unlock();
    }
} catch (InterruptedException e) {
    // 处理中断
}

// 模式3：尝试获取
if (lock.tryLock()) {
    try {
        // 临界区代码
    } finally {
        lock.unlock();
    }
} else {
    // 获取锁失败的处理
}

// 模式4：超时获取
try {
    if (lock.tryLock(10, TimeUnit.SECONDS)) {
        try {
            // 临界区代码
        } finally {
            lock.unlock();
        }
    } else {
        // 超时处理
    }
} catch (InterruptedException e) {
    // 处理中断
}
```

---

## 三、ReentrantLock详解

### 3.1 什么是可重入？

```
可重入（Reentrant）：
同一个线程可以多次获取同一个锁。

为什么需要可重入？
- 避免死锁
- 支持递归调用
- 简化编程
```

**示例**：

```java
public class ReentrantExample {
    private final Lock lock = new ReentrantLock();
    
    public void method1() {
        lock.lock();
        try {
            System.out.println("method1");
            method2(); // 调用method2
        } finally {
            lock.unlock();
        }
    }
    
    public void method2() {
        lock.lock(); // 重入：同一线程再次获取锁
        try {
            System.out.println("method2");
        } finally {
            lock.unlock();
        }
    }
}

// 如果不可重入，method1调用method2时会死锁
// 因为method1已经持有锁，method2无法获取
```

### 3.2 ReentrantLock的构造函数

```java
// 非公平锁（默认）
ReentrantLock lock = new ReentrantLock();

// 公平锁
ReentrantLock lock = new ReentrantLock(true);

// 非公平锁（显式）
ReentrantLock lock = new ReentrantLock(false);
```

### 3.3 ReentrantLock的特有方法

```java
public class ReentrantLock implements Lock {
    // 查询当前线程持有此锁的次数
    public int getHoldCount();
    
    // 查询等待获取此锁的线程数
    public int getQueueLength();
    
    // 查询指定线程是否在等待获取此锁
    public boolean hasQueuedThread(Thread thread);
    
    // 查询是否有线程在等待获取此锁
    public boolean hasQueuedThreads();
    
    // 查询当前线程是否持有此锁
    public boolean isHeldByCurrentThread();
    
    // 查询此锁是否被任意线程持有
    public boolean isLocked();
    
    // 查询此锁是否为公平锁
    public boolean isFair();
    
    // 查询是否有线程在等待指定的条件
    public boolean hasWaiters(Condition condition);
    
    // 查询等待指定条件的线程数
    public int getWaitQueueLength(Condition condition);
}
```

---

## 四、Lock vs synchronized

### 4.1 对比表

| 特性 | synchronized | ReentrantLock |
|------|--------------|---------------|
| **使用方式** | 关键字 | 类 |
| **锁的释放** | 自动（JVM保证） | 手动（必须finally） |
| **可中断** | ❌ 不支持 | ✅ lockInterruptibly() |
| **尝试获取** | ❌ 不支持 | ✅ tryLock() |
| **超时获取** | ❌ 不支持 | ✅ tryLock(timeout) |
| **公平性** | ❌ 非公平 | ✅ 可选 |
| **条件队列** | 1个（wait/notify） | 多个（Condition） |
| **可重入** | ✅ 支持 | ✅ 支持 |
| **性能** | JDK6后优化，相近 | 略高 |
| **灵活性** | 低（块结构） | 高（跨方法） |
| **使用难度** | 简单 | 复杂 |

### 4.2 性能对比

```java
// 性能测试
public class PerformanceTest {
    private static final int ITERATIONS = 10000000;
    
    // synchronized测试
    private int syncCount = 0;
    
    public synchronized void syncIncrement() {
        syncCount++;
    }
    
    // ReentrantLock测试
    private int lockCount = 0;
    private final Lock lock = new ReentrantLock();
    
    public void lockIncrement() {
        lock.lock();
        try {
            lockCount++;
        } finally {
            lock.unlock();
        }
    }
}

// 性能结果（1000万次操作）：
// synchronized：  约1000ms
// ReentrantLock： 约950ms

// 结论：
// JDK6后，synchronized性能已经很好
// ReentrantLock略快，但差距不大
// 选择时应考虑功能需求，而非性能
```

### 4.3 选择建议

```
使用synchronized：
✅ 简单的同步需求
✅ 不需要高级特性
✅ 代码简洁优先

使用ReentrantLock：
✅ 需要可中断
✅ 需要尝试获取
✅ 需要超时获取
✅ 需要公平锁
✅ 需要多个条件队列
✅ 需要跨方法加锁
```

---

## 五、实战应用

### 5.1 避免死锁

```java
// 使用tryLock避免死锁

public class Account {
    private int balance;
    private final Lock lock = new ReentrantLock();
    
    // ❌ 可能死锁
    public void transferBad(Account to, int amount) {
        this.lock.lock();
        to.lock.lock();
        try {
            this.balance -= amount;
            to.balance += amount;
        } finally {
            to.lock.unlock();
            this.lock.unlock();
        }
    }
    
    // ✅ 使用tryLock避免死锁
    public boolean transferGood(Account to, int amount) {
        if (this.lock.tryLock()) {
            try {
                if (to.lock.tryLock()) {
                    try {
                        this.balance -= amount;
                        to.balance += amount;
                        return true;
                    } finally {
                        to.lock.unlock();
                    }
                }
            } finally {
                this.lock.unlock();
            }
        }
        return false;
    }
    
    // ✅ 使用超时避免死锁
    public boolean transferWithTimeout(Account to, int amount) 
            throws InterruptedException {
        long timeout = 1000; // 1秒
        if (this.lock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
            try {
                if (to.lock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
                    try {
                        this.balance -= amount;
                        to.balance += amount;
                        return true;
                    } finally {
                        to.lock.unlock();
                    }
                }
            } finally {
                this.lock.unlock();
            }
        }
        return false;
    }
}
```

### 5.2 可中断的锁获取

```java
// 响应中断的任务

public class InterruptibleTask implements Runnable {
    private final Lock lock = new ReentrantLock();
    
    @Override
    public void run() {
        try {
            // 可中断地获取锁
            lock.lockInterruptibly();
            try {
                // 执行任务
                doWork();
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            // 响应中断
            System.out.println("任务被中断");
            Thread.currentThread().interrupt();
        }
    }
    
    private void doWork() throws InterruptedException {
        // 长时间运行的任务
        while (!Thread.currentThread().isInterrupted()) {
            // 工作
        }
    }
}

// 使用
Thread thread = new Thread(new InterruptibleTask());
thread.start();
// 可以中断线程
thread.interrupt();
```

### 5.3 定时锁

```java
// 限时等待锁

public class TimedLockExample {
    private final Lock lock = new ReentrantLock();
    
    public void doSomething() {
        try {
            // 尝试在5秒内获取锁
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    // 执行任务
                    performTask();
                } finally {
                    lock.unlock();
                }
            } else {
                // 超时处理
                System.out.println("无法在5秒内获取锁");
                handleTimeout();
            }
        } catch (InterruptedException e) {
            // 处理中断
            Thread.currentThread().interrupt();
        }
    }
    
    private void performTask() {
        // 任务逻辑
    }
    
    private void handleTimeout() {
        // 超时处理逻辑
    }
}
```

---

## 六、常见陷阱

### 6.1 忘记unlock()

```java
// ❌ 错误：异常时不会释放锁
public void badExample() {
    lock.lock();
    doSomething(); // 可能抛异常
    lock.unlock(); // 可能不会执行
}

// ✅ 正确：使用finally
public void goodExample() {
    lock.lock();
    try {
        doSomething();
    } finally {
        lock.unlock(); // 一定会执行
    }
}
```

### 6.2 重复unlock()

```java
// ❌ 错误：重复释放
public void badExample() {
    lock.lock();
    try {
        doSomething();
        lock.unlock(); // 错误的位置
    } finally {
        lock.unlock(); // 重复释放，抛IllegalMonitorStateException
    }
}

// ✅ 正确：只在finally中释放一次
public void goodExample() {
    lock.lock();
    try {
        doSomething();
    } finally {
        lock.unlock();
    }
}
```

### 6.3 锁泄漏

```java
// ❌ 错误：条件分支导致锁泄漏
public void badExample() {
    lock.lock();
    try {
        if (condition) {
            return; // 提前返回，但锁已释放
        }
        doSomething();
    } finally {
        lock.unlock();
    }
}

// ✅ 正确：finally保证释放
public void goodExample() {
    lock.lock();
    try {
        if (condition) {
            return; // 提前返回，finally仍会执行
        }
        doSomething();
    } finally {
        lock.unlock(); // 一定会执行
    }
}
```

### 6.4 tryLock()后忘记检查

```java
// ❌ 错误：不检查返回值
public void badExample() {
    lock.tryLock();
    try {
        doSomething(); // 可能没有获取到锁
    } finally {
        lock.unlock(); // 可能抛异常
    }
}

// ✅ 正确：检查返回值
public void goodExample() {
    if (lock.tryLock()) {
        try {
            doSomething();
        } finally {
            lock.unlock();
        }
    } else {
        // 获取锁失败的处理
    }
}
```

---

## 七、总结

### 7.1 核心要点

1. **Lock接口**：提供比synchronized更灵活的锁操作
2. **ReentrantLock**：可重入的独占锁
3. **标准模式**：lock() + try-finally + unlock()
4. **高级特性**：可中断、可尝试、可超时、多条件
5. **选择建议**：简单场景用synchronized，复杂场景用Lock

### 7.2 使用建议

```
✅ 必须遵守：
- 总是在finally中unlock()
- 检查tryLock()的返回值
- 不要重复unlock()

✅ 推荐做法：
- 优先使用synchronized
- 需要高级特性时使用Lock
- 使用tryLock()避免死锁
- 使用lockInterruptibly()响应中断
```

### 7.3 思考题

1. **Lock和synchronized有什么区别？**
2. **什么是可重入锁？**
3. **为什么必须在finally中unlock()？**
4. **如何使用tryLock()避免死锁？**

---

**下一章预告**：我们将学习公平锁和非公平锁的区别和性能对比。

---

**参考资料**：
- 《Java并发编程实战》第13章
- 《Java并发编程的艺术》第5章
- Lock API文档
