# Synchronized与ReentrantLock对比

## 1. 为什么有了Synchronized还需要ReentrantLock？

### 1.1 问题1：Synchronized有什么局限性？

在JDK 1.5之前，Synchronized存在以下问题：

```
Synchronized的局限性（JDK 1.5之前）
         ↓
    ┌────┴────┐
    ↓         ↓         ↓         ↓
性能差    功能弱    不灵活    不可中断
    ↓         ↓         ↓         ↓
重量级锁  只能非公平  自动释放  无法响应中断
```

**Doug Lea设计了ReentrantLock来解决这些问题**：

1. **性能问题**：JDK 1.6之后Synchronized已优化，性能相当
2. **功能问题**：ReentrantLock提供了更多高级功能
3. **灵活性问题**：ReentrantLock提供了更灵活的控制

---

### 1.2 问题2：ReentrantLock提供了哪些Synchronized没有的功能？

```
ReentrantLock的高级功能
         ↓
    ┌────┴────┐
    ↓         ↓         ↓         ↓
可中断    支持超时   公平锁    多个Condition
    ↓         ↓         ↓         ↓
lockInterruptibly  tryLock  new ReentrantLock(true)  newCondition()
```

---

## 2. 实现层面的对比

### 2.1 实现机制

| 特性 | Synchronized | ReentrantLock |
|-----|-------------|---------------|
| 实现层面 | JVM层面（C++实现） | JDK层面（Java实现） |
| 底层实现 | Monitor机制 | AQS（AbstractQueuedSynchronizer） |
| 字节码指令 | monitorenter/monitorexit | 普通方法调用 |
| 锁的获取 | 隐式获取 | 显式调用lock() |
| 锁的释放 | 自动释放 | 必须手动调用unlock() |

**Synchronized字节码**：

```java
public class SynchronizedBytecode {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
}
```

**字节码**：

```
public synchronized void increment();
  descriptor: ()V
  flags: ACC_PUBLIC, ACC_SYNCHRONIZED  // ACC_SYNCHRONIZED标志
  Code:
    stack=3, locals=1, args_size=1
       0: aload_0
       1: dup
       2: getfield      #2  // Field count:I
       5: iconst_1
       6: iadd
       7: putfield      #2  // Field count:I
      10: return
```

**同步代码块字节码**：

```java
public void increment() {
    synchronized(this) {
        count++;
    }
}
```

**字节码**：

```
public void increment();
  Code:
     0: aload_0
     1: dup
     2: astore_1
     3: monitorenter        // 进入Monitor
     4: aload_0
     5: dup
     6: getfield      #2
     9: iconst_1
    10: iadd
    11: putfield      #2
    14: aload_1
    15: monitorexit         // 退出Monitor
    16: goto          24
    19: astore_2
    20: aload_1
    21: monitorexit         // 异常时也要退出Monitor
    22: aload_2
    23: athrow
    24: return
```

**ReentrantLock字节码**：

```java
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockBytecode {
    private int count = 0;
    private final ReentrantLock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }
}
```

**字节码**：

```
public void increment();
  Code:
     0: aload_0
     1: getfield      #3  // Field lock:Ljava/util/concurrent/locks/ReentrantLock;
     4: invokevirtual #4  // Method java/util/concurrent/locks/ReentrantLock.lock:()V
     7: aload_0
     8: dup
     9: getfield      #2  // Field count:I
    12: iconst_1
    13: iadd
    14: putfield      #2
    17: aload_0
    18: getfield      #3
    21: invokevirtual #5  // Method java/util/concurrent/locks/ReentrantLock.unlock:()V
    24: goto          35
    27: astore_1
    28: aload_0
    29: getfield      #3
    32: invokevirtual #5
    35: return
```

**对比**：
- Synchronized使用特殊的字节码指令（monitorenter/monitorexit）
- ReentrantLock使用普通的方法调用（invokevirtual）

---

### 2.2 底层实现

**Synchronized底层实现**：

```
Synchronized
    ↓
Monitor机制
    ↓
ObjectMonitor (C++对象)
    ↓
    ┌────┴────┐
    ↓         ↓         ↓
_owner    _EntryList  _WaitSet
(持有者)   (等待队列)  (条件队列)
```

**ReentrantLock底层实现**：

```
ReentrantLock
    ↓
AQS (AbstractQueuedSynchronizer)
    ↓
    ┌────┴────┐
    ↓         ↓         ↓
state     CLH队列    ConditionObject
(锁状态)   (等待队列)  (条件队列)
```

---

## 3. 功能对比

### 3.1 基本功能对比

| 功能 | Synchronized | ReentrantLock |
|-----|-------------|---------------|
| 可重入 | ✅ 支持 | ✅ 支持 |
| 互斥性 | ✅ 支持 | ✅ 支持 |
| 可见性 | ✅ 支持 | ✅ 支持 |
| 自动释放 | ✅ 自动释放 | ❌ 必须手动释放 |
| 异常处理 | ✅ 自动释放 | ⚠️ 需要在finally中释放 |

**示例对比**：

```java
// Synchronized：自动释放
public synchronized void method() {
    // 即使抛出异常，锁也会自动释放
    if (someCondition) {
        throw new RuntimeException();
    }
}

// ReentrantLock：必须手动释放
private final ReentrantLock lock = new ReentrantLock();

public void method() {
    lock.lock();
    try {
        // 必须在try-finally中使用
        if (someCondition) {
            throw new RuntimeException();
        }
    } finally {
        lock.unlock();  // 必须手动释放
    }
}
```

---

### 3.2 高级功能对比

#### 功能1：可中断锁

**Synchronized**：❌ 不支持中断

```java
public class SynchronizedInterrupt {
    private final Object lock = new Object();
    
    public void method() {
        synchronized(lock) {
            // 无法响应中断
            // 即使调用Thread.interrupt()，线程仍会继续等待锁
            while (true) {
                // ...
            }
        }
    }
}
```

**ReentrantLock**：✅ 支持中断

```java
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockInterrupt {
    private final ReentrantLock lock = new ReentrantLock();
    
    public void method() throws InterruptedException {
        lock.lockInterruptibly();  // 可中断的锁
        try {
            while (true) {
                // 如果线程被中断，会抛出InterruptedException
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        ReentrantLockInterrupt demo = new ReentrantLockInterrupt();
        
        Thread t = new Thread(() -> {
            try {
                demo.method();
            } catch (InterruptedException e) {
                System.out.println("线程被中断");
            }
        });
        
        t.start();
        Thread.sleep(100);
        t.interrupt();  // 中断线程
    }
}
```

---

#### 功能2：超时锁

**Synchronized**：❌ 不支持超时

```java
public class SynchronizedTimeout {
    private final Object lock = new Object();
    
    public void method() {
        synchronized(lock) {
            // 无法设置超时时间
            // 如果获取不到锁，会一直等待
        }
    }
}
```

**ReentrantLock**：✅ 支持超时

```java
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

public class ReentrantLockTimeout {
    private final ReentrantLock lock = new ReentrantLock();
    
    public boolean method() {
        try {
            // 尝试获取锁，最多等待3秒
            if (lock.tryLock(3, TimeUnit.SECONDS)) {
                try {
                    // 获取锁成功，执行业务逻辑
                    System.out.println("获取锁成功");
                    return true;
                } finally {
                    lock.unlock();
                }
            } else {
                // 获取锁失败（超时）
                System.out.println("获取锁超时");
                return false;
            }
        } catch (InterruptedException e) {
            System.out.println("等待锁时被中断");
            return false;
        }
    }
    
    public static void main(String[] args) {
        ReentrantLockTimeout demo = new ReentrantLockTimeout();
        
        // 线程1持有锁
        Thread t1 = new Thread(() -> {
            demo.lock.lock();
            try {
                System.out.println("Thread-1持有锁");
                Thread.sleep(5000);  // 持有锁5秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                demo.lock.unlock();
            }
        });
        
        // 线程2尝试获取锁（超时3秒）
        Thread t2 = new Thread(() -> {
            demo.method();
        });
        
        t1.start();
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        t2.start();
    }
}
```

**输出**：

```
Thread-1持有锁
获取锁超时  // 3秒后超时
```

---

#### 功能3：公平锁

**Synchronized**：❌ 只支持非公平锁

```java
public class SynchronizedFairness {
    public synchronized void method() {
        // Synchronized是非公平锁
        // 新来的线程可能会插队，不保证FIFO
    }
}
```

**ReentrantLock**：✅ 支持公平锁和非公平锁

```java
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockFairness {
    // 公平锁：按照请求锁的顺序获取锁（FIFO）
    private final ReentrantLock fairLock = new ReentrantLock(true);
    
    // 非公平锁：允许插队
    private final ReentrantLock unfairLock = new ReentrantLock(false);
    
    public void fairMethod() {
        fairLock.lock();
        try {
            System.out.println(Thread.currentThread().getName() + " 获得公平锁");
        } finally {
            fairLock.unlock();
        }
    }
    
    public void unfairMethod() {
        unfairLock.lock();
        try {
            System.out.println(Thread.currentThread().getName() + " 获得非公平锁");
        } finally {
            unfairLock.unlock();
        }
    }
    
    public static void main(String[] args) {
        ReentrantLockFairness demo = new ReentrantLockFairness();
        
        // 创建10个线程竞争公平锁
        for (int i = 0; i < 10; i++) {
            new Thread(() -> demo.fairMethod(), "Thread-" + i).start();
        }
    }
}
```

**公平锁 vs 非公平锁**：

| 特性 | 公平锁 | 非公平锁 |
|-----|-------|---------|
| 获取顺序 | FIFO（先来先得） | 允许插队 |
| 性能 | 较低（需要维护队列） | 较高（减少上下文切换） |
| 吞吐量 | 较低 | 较高 |
| 适用场景 | 需要严格顺序 | 追求性能 |

---

#### 功能4：多个Condition

**Synchronized**：❌ 只有一个条件队列（WaitSet）

```java
public class SynchronizedCondition {
    private final Object lock = new Object();
    private boolean condition1 = false;
    private boolean condition2 = false;
    
    public void waitForCondition1() throws InterruptedException {
        synchronized(lock) {
            while (!condition1) {
                lock.wait();  // 只有一个等待队列
            }
        }
    }
    
    public void waitForCondition2() throws InterruptedException {
        synchronized(lock) {
            while (!condition2) {
                lock.wait();  // 同一个等待队列
            }
        }
    }
    
    public void signalCondition1() {
        synchronized(lock) {
            condition1 = true;
            lock.notifyAll();  // 唤醒所有线程（包括等待condition2的）
        }
    }
}
```

**问题**：
- 只有一个等待队列，无法区分不同的等待条件
- notifyAll()会唤醒所有等待的线程，包括不应该被唤醒的

**ReentrantLock**：✅ 支持多个Condition

```java
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class ReentrantLockCondition {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition1 = lock.newCondition();  // 条件1
    private final Condition condition2 = lock.newCondition();  // 条件2
    
    private boolean flag1 = false;
    private boolean flag2 = false;
    
    public void waitForCondition1() throws InterruptedException {
        lock.lock();
        try {
            while (!flag1) {
                condition1.await();  // 在condition1上等待
            }
        } finally {
            lock.unlock();
        }
    }
    
    public void waitForCondition2() throws InterruptedException {
        lock.lock();
        try {
            while (!flag2) {
                condition2.await();  // 在condition2上等待
            }
        } finally {
            lock.unlock();
        }
    }
    
    public void signalCondition1() {
        lock.lock();
        try {
            flag1 = true;
            condition1.signal();  // 只唤醒等待condition1的线程
        } finally {
            lock.unlock();
        }
    }
    
    public void signalCondition2() {
        lock.lock();
        try {
            flag2 = true;
            condition2.signal();  // 只唤醒等待condition2的线程
        } finally {
            lock.unlock();
        }
    }
}
```

**优势**：
- 可以创建多个Condition，分别管理不同的等待条件
- 精确唤醒，避免不必要的唤醒

**实际应用：生产者-消费者**

```java
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.LinkedList;
import java.util.Queue;

public class ProducerConsumerWithCondition {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int capacity = 10;
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();   // 队列未满
    private final Condition notEmpty = lock.newCondition();  // 队列非空
    
    // 生产者
    public void produce(int value) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                notFull.await();  // 队列满了，等待notFull条件
            }
            queue.offer(value);
            System.out.println("生产: " + value + ", 队列大小: " + queue.size());
            notEmpty.signal();  // 唤醒等待notEmpty的消费者
        } finally {
            lock.unlock();
        }
    }
    
    // 消费者
    public int consume() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();  // 队列空了，等待notEmpty条件
            }
            int value = queue.poll();
            System.out.println("消费: " + value + ", 队列大小: " + queue.size());
            notFull.signal();  // 唤醒等待notFull的生产者
            return value;
        } finally {
            lock.unlock();
        }
    }
    
    public static void main(String[] args) {
        ProducerConsumerWithCondition pc = new ProducerConsumerWithCondition();
        
        // 生产者线程
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    pc.produce(i);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        
        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    pc.consume();
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        
        producer.start();
        consumer.start();
    }
}
```

---

#### 功能5：尝试获取锁

**Synchronized**：❌ 不支持尝试获取锁

```java
public class SynchronizedTryLock {
    private final Object lock = new Object();
    
    public void method() {
        synchronized(lock) {
            // 无法尝试获取锁
            // 要么获取成功，要么一直等待
        }
    }
}
```

**ReentrantLock**：✅ 支持尝试获取锁

```java
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockTryLock {
    private final ReentrantLock lock = new ReentrantLock();
    
    public boolean method() {
        // 尝试获取锁，立即返回
        if (lock.tryLock()) {
            try {
                System.out.println("获取锁成功");
                return true;
            } finally {
                lock.unlock();
            }
        } else {
            System.out.println("获取锁失败");
            return false;
        }
    }
    
    public static void main(String[] args) {
        ReentrantLockTryLock demo = new ReentrantLockTryLock();
        
        // 线程1持有锁
        Thread t1 = new Thread(() -> {
            demo.lock.lock();
            try {
                System.out.println("Thread-1持有锁");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                demo.lock.unlock();
            }
        });
        
        // 线程2尝试获取锁
        Thread t2 = new Thread(() -> {
            demo.method();
        });
        
        t1.start();
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        t2.start();
    }
}
```

**输出**：

```
Thread-1持有锁
获取锁失败  // 立即返回，不等待
```

---

## 4. 性能对比

### 4.1 历史性能对比

| JDK版本 | Synchronized性能 | ReentrantLock性能 | 结论 |
|--------|-----------------|------------------|------|
| JDK 1.5之前 | 差（重量级锁） | 好 | ReentrantLock胜出 |
| JDK 1.6+ | 好（锁优化） | 好 | 性能相当 |

**JDK 1.6的锁优化**：
1. 偏向锁
2. 轻量级锁
3. 自旋锁
4. 锁消除
5. 锁粗化

---

### 4.2 性能测试

```java
import java.util.concurrent.locks.ReentrantLock;

public class PerformanceComparison {
    private static final int THREAD_COUNT = 10;
    private static final int ITERATIONS = 1000000;
    
    // Synchronized版本
    static class SynchronizedCounter {
        private int count = 0;
        
        public synchronized void increment() {
            count++;
        }
        
        public synchronized int getCount() {
            return count;
        }
    }
    
    // ReentrantLock版本
    static class ReentrantLockCounter {
        private int count = 0;
        private final ReentrantLock lock = new ReentrantLock();
        
        public void increment() {
            lock.lock();
            try {
                count++;
            } finally {
                lock.unlock();
            }
        }
        
        public int getCount() {
            lock.lock();
            try {
                return count;
            } finally {
                lock.unlock();
            }
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        // 测试Synchronized
        SynchronizedCounter syncCounter = new SynchronizedCounter();
        long syncStart = System.currentTimeMillis();
        
        Thread[] syncThreads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            syncThreads[i] = new Thread(() -> {
                for (int j = 0; j < ITERATIONS; j++) {
                    syncCounter.increment();
                }
            });
            syncThreads[i].start();
        }
        
        for (Thread t : syncThreads) {
            t.join();
        }
        
        long syncTime = System.currentTimeMillis() - syncStart;
        System.out.println("Synchronized耗时: " + syncTime + "ms, 结果: " + syncCounter.getCount());
        
        // 测试ReentrantLock
        ReentrantLockCounter lockCounter = new ReentrantLockCounter();
        long lockStart = System.currentTimeMillis();
        
        Thread[] lockThreads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            lockThreads[i] = new Thread(() -> {
                for (int j = 0; j < ITERATIONS; j++) {
                    lockCounter.increment();
                }
            });
            lockThreads[i].start();
        }
        
        for (Thread t : lockThreads) {
            t.join();
        }
        
        long lockTime = System.currentTimeMillis() - lockStart;
        System.out.println("ReentrantLock耗时: " + lockTime + "ms, 结果: " + lockCounter.getCount());
    }
}
```

**测试结果（JDK 1.8）**：

```
Synchronized耗时: 856ms, 结果: 10000000
ReentrantLock耗时: 892ms, 结果: 10000000
```

**结论**：在JDK 1.6+，两者性能相当。

---

### 4.3 不同场景的性能对比

| 场景 | Synchronized | ReentrantLock | 推荐 |
|-----|-------------|---------------|------|
| 低竞争 | 优秀（偏向锁） | 优秀 | Synchronized |
| 中等竞争 | 优秀（轻量级锁） | 优秀 | Synchronized |
| 高竞争 | 良好（重量级锁） | 良好 | 相当 |
| 公平锁 | 不支持 | 支持（性能较低） | ReentrantLock |

---

## 5. 使用场景对比

### 5.1 什么时候用Synchronized？

**✅ 推荐使用Synchronized的场景**：

1. **简单的同步需求**

```java
public class SimpleSync {
    private int count = 0;
    
    // ✅ 简单的计数器，用Synchronized足够
    public synchronized void increment() {
        count++;
    }
}
```

2. **不需要高级功能**

```java
public class BasicCache {
    private final Map<String, Object> cache = new HashMap<>();
    
    // ✅ 简单的缓存，用Synchronized足够
    public synchronized Object get(String key) {
        return cache.get(key);
    }
    
    public synchronized void put(String key, Object value) {
        cache.put(key, value);
    }
}
```

3. **代码简洁性优先**

```java
// ✅ Synchronized代码更简洁
public synchronized void method() {
    // 自动释放锁，不需要try-finally
}

// ReentrantLock代码较繁琐
private final ReentrantLock lock = new ReentrantLock();
public void method() {
    lock.lock();
    try {
        // 必须手动释放锁
    } finally {
        lock.unlock();
    }
}
```

---

### 5.2 什么时候用ReentrantLock？

**✅ 推荐使用ReentrantLock的场景**：

1. **需要可中断的锁**

```java
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleTask {
    private final ReentrantLock lock = new ReentrantLock();
    
    // ✅ 需要响应中断
    public void process() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            // 长时间任务
            while (!Thread.currentThread().isInterrupted()) {
                // ...
            }
        } finally {
            lock.unlock();
        }
    }
}
```

2. **需要超时获取锁**

```java
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

public class TimeoutTask {
    private final ReentrantLock lock = new ReentrantLock();
    
    // ✅ 需要超时机制
    public boolean process() {
        try {
            if (lock.tryLock(3, TimeUnit.SECONDS)) {
                try {
                    // 业务逻辑
                    return true;
                } finally {
                    lock.unlock();
                }
            } else {
                // 超时处理
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }
}
```

3. **需要公平锁**

```java
import java.util.concurrent.locks.ReentrantLock;

public class FairQueue {
    private final ReentrantLock lock = new ReentrantLock(true);  // 公平锁
    
    // ✅ 需要严格的FIFO顺序
    public void enqueue(Object item) {
        lock.lock();
        try {
            // 按顺序入队
        } finally {
            lock.unlock();
        }
    }
}
```

4. **需要多个Condition**

```java
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class BoundedBuffer {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    
    // ✅ 需要多个等待条件
    public void put(Object item) throws InterruptedException {
        lock.lock();
        try {
            while (isFull()) {
                notFull.await();
            }
            // 添加元素
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }
    
    private boolean isFull() { return false; }
}
```

5. **需要尝试获取锁**

```java
import java.util.concurrent.locks.ReentrantLock;

public class NonBlockingTask {
    private final ReentrantLock lock = new ReentrantLock();
    
    // ✅ 需要非阻塞的尝试
    public boolean tryProcess() {
        if (lock.tryLock()) {
            try {
                // 业务逻辑
                return true;
            } finally {
                lock.unlock();
            }
        } else {
            // 获取锁失败，执行其他逻辑
            return false;
        }
    }
}
```

---

## 6. 选择决策流程图

```
需要同步？
    ↓
   是
    ↓
需要以下任一高级功能？
- 可中断
- 超时
- 公平锁
- 多个Condition
- 尝试获取锁
    ↓
    ┌──────┴──────┐
    ↓             ↓
   是            否
    ↓             ↓
ReentrantLock  Synchronized
    ↓             ↓
手动管理锁      自动管理锁
代码较复杂      代码简洁
功能强大        功能够用
```

---

## 7. 完整对比表

| 特性 | Synchronized | ReentrantLock | 推荐 |
|-----|-------------|---------------|------|
| **实现层面** |
| 实现方式 | JVM层面（C++） | JDK层面（Java） | - |
| 底层机制 | Monitor | AQS | - |
| **基本功能** |
| 互斥性 | ✅ | ✅ | - |
| 可重入 | ✅ | ✅ | - |
| 可见性 | ✅ | ✅ | - |
| 自动释放 | ✅ | ❌ | Synchronized |
| **高级功能** |
| 可中断 | ❌ | ✅ | ReentrantLock |
| 超时 | ❌ | ✅ | ReentrantLock |
| 公平锁 | ❌ | ✅ | ReentrantLock |
| 多个Condition | ❌ | ✅ | ReentrantLock |
| 尝试获取锁 | ❌ | ✅ | ReentrantLock |
| **性能** |
| JDK 1.5 | 差 | 好 | ReentrantLock |
| JDK 1.6+ | 好 | 好 | 相当 |
| **易用性** |
| 代码简洁性 | ✅ 简洁 | ❌ 繁琐 | Synchronized |
| 异常安全 | ✅ 自动 | ⚠️ 需注意 | Synchronized |
| **适用场景** |
| 简单同步 | ✅ | ✅ | Synchronized |
| 复杂同步 | ❌ | ✅ | ReentrantLock |

---

## 8. 总结

### 8.1 核心区别

1. **实现层面**：Synchronized是JVM层面，ReentrantLock是JDK层面
2. **功能**：ReentrantLock提供了更多高级功能
3. **性能**：JDK 1.6+性能相当
4. **易用性**：Synchronized更简洁，ReentrantLock更灵活

### 8.2 选择建议

**优先使用Synchronized**：
- ✅ 代码简洁
- ✅ 自动释放锁
- ✅ 性能优秀
- ✅ 功能够用

**使用ReentrantLock的场景**：
- ✅ 需要可中断
- ✅ 需要超时
- ✅ 需要公平锁
- ✅ 需要多个Condition
- ✅ 需要尝试获取锁

### 8.3 最佳实践

1. **默认使用Synchronized**，除非需要ReentrantLock的高级功能
2. **使用ReentrantLock时，必须在finally中释放锁**
3. **根据实际需求选择公平锁或非公平锁**
4. **性能不是主要考虑因素**（两者性能相当）
5. **代码简洁性和可维护性更重要**

---

**下一章**：我们将深入分析Synchronized的源码实现，学习JVM的优化技巧。
