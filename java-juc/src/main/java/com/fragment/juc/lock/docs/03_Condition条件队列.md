# 第三章：Condition条件队列 - 精确的线程协作

> **学习目标**：深入理解Condition的使用和实现原理

---

## 一、为什么需要Condition？

### 1.1 wait/notify的局限性

```java
// synchronized + wait/notify的问题

public class BoundedBuffer {
    private final Object lock = new Object();
    private final Queue<Object> queue = new LinkedList<>();
    private final int capacity = 10;
    
    public void put(Object item) throws InterruptedException {
        synchronized (lock) {
            while (queue.size() == capacity) {
                lock.wait(); // 问题1：只有一个等待队列
            }
            queue.offer(item);
            lock.notifyAll(); // 问题2：唤醒所有线程（包括生产者）
        }
    }
    
    public Object take() throws InterruptedException {
        synchronized (lock) {
            while (queue.isEmpty()) {
                lock.wait(); // 问题1：与put()共享同一个等待队列
            }
            Object item = queue.poll();
            lock.notifyAll(); // 问题2：唤醒所有线程（包括消费者）
            return item;
        }
    }
}

// 问题：
// 1. 只有一个等待队列，生产者和消费者混在一起
// 2. notifyAll()唤醒所有线程，效率低
// 3. 无法精确唤醒特定类型的线程
```

### 1.2 Condition的解决方案

```java
// Lock + Condition的优势

public class BoundedBuffer {
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();  // 生产者等待队列
    private final Condition notEmpty = lock.newCondition(); // 消费者等待队列
    private final Queue<Object> queue = new LinkedList<>();
    private final int capacity = 10;
    
    public void put(Object item) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                notFull.await(); // 生产者在notFull队列等待
            }
            queue.offer(item);
            notEmpty.signal(); // 精确唤醒一个消费者
        } finally {
            lock.unlock();
        }
    }
    
    public Object take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await(); // 消费者在notEmpty队列等待
            }
            Object item = queue.poll();
            notFull.signal(); // 精确唤醒一个生产者
            return item;
        } finally {
            lock.unlock();
        }
    }
}

// 优势：
// 1. 多个等待队列，分离生产者和消费者
// 2. signal()精确唤醒一个线程，效率高
// 3. 更灵活的线程协作
```

---

## 二、Condition接口详解

### 2.1 Condition接口方法

```java
public interface Condition {
    /**
     * 等待（阻塞）
     * - 释放锁
     * - 进入等待队列
     * - 被唤醒后重新获取锁
     */
    void await() throws InterruptedException;
    
    /**
     * 等待（不响应中断）
     */
    void awaitUninterruptibly();
    
    /**
     * 超时等待（纳秒）
     * - 返回剩余等待时间
     */
    long awaitNanos(long nanosTimeout) throws InterruptedException;
    
    /**
     * 超时等待
     * - 返回是否在超时前被唤醒
     */
    boolean await(long time, TimeUnit unit) throws InterruptedException;
    
    /**
     * 等待到指定时间
     * - 返回是否在截止时间前被唤醒
     */
    boolean awaitUntil(Date deadline) throws InterruptedException;
    
    /**
     * 唤醒一个等待线程
     */
    void signal();
    
    /**
     * 唤醒所有等待线程
     */
    void signalAll();
}
```

### 2.2 标准使用模式

```java
Lock lock = new ReentrantLock();
Condition condition = lock.newCondition();

// 等待模式
lock.lock();
try {
    while (!conditionPredicate()) {
        condition.await();
    }
    // 条件满足后的操作
} finally {
    lock.unlock();
}

// 通知模式
lock.lock();
try {
    // 改变条件
    changeCondition();
    condition.signal(); // 或signalAll()
} finally {
    lock.unlock();
}
```

---

## 三、Condition的实现原理

### 3.1 等待队列结构

```
Lock的结构：

同步队列（AQS队列）：
[Head] → [Node1] → [Node2] → [Tail]
         (等待锁)  (等待锁)

条件队列（Condition队列）：
[FirstWaiter] → [Node3] → [Node4] → [LastWaiter]
                (等待条件) (等待条件)

关系：
- 每个Lock可以有多个Condition
- 每个Condition有自己的等待队列
- await()：从同步队列移到条件队列
- signal()：从条件队列移到同步队列
```

### 3.2 await()的实现流程

```java
// await()的实现（简化版）

public final void await() throws InterruptedException {
    // 1. 创建节点并加入条件队列
    Node node = addConditionWaiter();
    
    // 2. 释放锁（完全释放，包括重入次数）
    int savedState = fullyRelease(node);
    
    // 3. 阻塞等待
    while (!isOnSyncQueue(node)) {
        LockSupport.park(this);
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }
    
    // 4. 被唤醒后，重新获取锁
    acquireQueued(node, savedState);
}

// 流程图：
// 1. 持有锁 → 2. 加入条件队列 → 3. 释放锁 → 4. 阻塞等待
//    ↑                                              ↓
//    ← 6. 重新获取锁 ← 5. 被唤醒，移到同步队列 ←
```

### 3.3 signal()的实现流程

```java
// signal()的实现（简化版）

public final void signal() {
    // 1. 检查是否持有锁
    if (!isHeldExclusively()) {
        throw new IllegalMonitorStateException();
    }
    
    // 2. 从条件队列头部取出一个节点
    Node first = firstWaiter;
    if (first != null) {
        doSignal(first);
    }
}

private void doSignal(Node first) {
    // 3. 将节点从条件队列移到同步队列
    transferForSignal(first);
}

final boolean transferForSignal(Node node) {
    // 4. 修改节点状态
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
        return false;
    }
    
    // 5. 加入同步队列
    Node p = enq(node);
    
    // 6. 唤醒线程
    LockSupport.unpark(node.thread);
    return true;
}

// 流程图：
// 条件队列：[Node1] → [Node2] → [Node3]
//              ↓ signal()
// 同步队列：[Head] → [Node1] → [Tail]
//                      ↓ unpark()
//                   线程被唤醒
```

---

## 四、生产者-消费者模式

### 4.1 有界缓冲区实现

```java
public class BoundedBuffer<T> {
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Object[] items;
    private int putIndex, takeIndex, count;
    
    public BoundedBuffer(int capacity) {
        items = new Object[capacity];
    }
    
    // 生产者：放入元素
    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            // 等待缓冲区不满
            while (count == items.length) {
                notFull.await();
            }
            // 放入元素
            items[putIndex] = item;
            if (++putIndex == items.length) {
                putIndex = 0;
            }
            count++;
            // 通知消费者
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }
    
    // 消费者：取出元素
    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            // 等待缓冲区不空
            while (count == 0) {
                notEmpty.await();
            }
            // 取出元素
            T item = (T) items[takeIndex];
            items[takeIndex] = null;
            if (++takeIndex == items.length) {
                takeIndex = 0;
            }
            count--;
            // 通知生产者
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }
    
    // 获取当前大小
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}
```

### 4.2 使用示例

```java
public class ProducerConsumerExample {
    public static void main(String[] args) {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(10);
        
        // 创建3个生产者
        for (int i = 0; i < 3; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        buffer.put(id * 100 + j);
                        System.out.println("生产者" + id + "生产：" + (id * 100 + j));
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Producer-" + i).start();
        }
        
        // 创建2个消费者
        for (int i = 0; i < 2; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < 150; j++) {
                        Integer item = buffer.take();
                        System.out.println("消费者" + id + "消费：" + item);
                        Thread.sleep(15);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i).start();
        }
    }
}
```

---

## 五、多条件队列的应用

### 5.1 读写锁的简化实现

```java
public class SimpleReadWriteLock {
    private final Lock lock = new ReentrantLock();
    private final Condition readCondition = lock.newCondition();
    private final Condition writeCondition = lock.newCondition();
    private int readers = 0;
    private int writers = 0;
    private int writeRequests = 0;
    
    // 获取读锁
    public void lockRead() throws InterruptedException {
        lock.lock();
        try {
            // 等待没有写线程
            while (writers > 0 || writeRequests > 0) {
                readCondition.await();
            }
            readers++;
        } finally {
            lock.unlock();
        }
    }
    
    // 释放读锁
    public void unlockRead() {
        lock.lock();
        try {
            readers--;
            if (readers == 0) {
                writeCondition.signal();
            }
        } finally {
            lock.unlock();
        }
    }
    
    // 获取写锁
    public void lockWrite() throws InterruptedException {
        lock.lock();
        try {
            writeRequests++;
            // 等待没有读线程和写线程
            while (readers > 0 || writers > 0) {
                writeCondition.await();
            }
            writeRequests--;
            writers++;
        } finally {
            lock.unlock();
        }
    }
    
    // 释放写锁
    public void unlockWrite() {
        lock.lock();
        try {
            writers--;
            writeCondition.signal();
            readCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
```

### 5.2 多阶段屏障

```java
public class MultiPhaseBarrier {
    private final Lock lock = new ReentrantLock();
    private final Condition[] conditions;
    private final int[] counts;
    private final int parties;
    
    public MultiPhaseBarrier(int parties, int phases) {
        this.parties = parties;
        this.conditions = new Condition[phases];
        this.counts = new int[phases];
        for (int i = 0; i < phases; i++) {
            conditions[i] = lock.newCondition();
            counts[i] = 0;
        }
    }
    
    // 等待指定阶段
    public void await(int phase) throws InterruptedException {
        lock.lock();
        try {
            counts[phase]++;
            if (counts[phase] == parties) {
                // 最后一个到达的线程唤醒所有等待线程
                conditions[phase].signalAll();
                counts[phase] = 0;
            } else {
                // 等待其他线程
                while (counts[phase] > 0) {
                    conditions[phase].await();
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
```

---

## 六、常见陷阱

### 6.1 使用if而不是while

```java
// ❌ 错误：使用if
lock.lock();
try {
    if (!condition) {
        condition.await(); // 虚假唤醒后不会再次检查
    }
    // 可能条件不满足
} finally {
    lock.unlock();
}

// ✅ 正确：使用while
lock.lock();
try {
    while (!condition) {
        condition.await(); // 虚假唤醒后会再次检查
    }
    // 条件一定满足
} finally {
    lock.unlock();
}
```

### 6.2 忘记持有锁

```java
// ❌ 错误：没有持有锁
condition.await(); // 抛IllegalMonitorStateException

// ✅ 正确：先获取锁
lock.lock();
try {
    condition.await();
} finally {
    lock.unlock();
}
```

### 6.3 signal()后立即释放锁

```java
// ❌ 不好：signal()后立即释放锁
lock.lock();
try {
    changeCondition();
    condition.signal();
} finally {
    lock.unlock(); // 立即释放，被唤醒的线程可能立即获取锁
}

// ✅ 更好：signal()后继续持有锁一段时间
lock.lock();
try {
    changeCondition();
    // 继续一些操作
    doMoreWork();
    condition.signal();
} finally {
    lock.unlock(); // 最后释放
}
```

---

## 七、总结

### 7.1 核心要点

1. **Condition**：Lock的条件队列，替代wait/notify
2. **多条件**：一个Lock可以有多个Condition
3. **精确唤醒**：signal()只唤醒一个线程
4. **标准模式**：await()用while，signal()在finally前
5. **应用**：生产者-消费者、读写锁、多阶段屏障

### 7.2 Condition vs wait/notify

| 特性 | wait/notify | Condition |
|------|-------------|-----------|
| **等待队列** | 1个 | 多个 |
| **唤醒方式** | notify/notifyAll | signal/signalAll |
| **精确唤醒** | ❌ 不支持 | ✅ 支持 |
| **灵活性** | 低 | 高 |
| **使用难度** | 简单 | 复杂 |

### 7.3 思考题

1. **Condition和wait/notify有什么区别？**
2. **为什么await()要用while而不是if？**
3. **signal()和signalAll()有什么区别？**
4. **如何实现生产者-消费者模式？**

---

**下一章预告**：我们将学习ReadWriteLock读写锁的使用和性能优化。

---

**参考资料**：
- 《Java并发编程实战》第14章
- 《Java并发编程的艺术》第5章
- Condition API文档
