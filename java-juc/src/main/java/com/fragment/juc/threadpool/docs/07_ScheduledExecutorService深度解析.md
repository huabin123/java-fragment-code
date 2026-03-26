# ScheduledExecutorService深度解析

## 💡 大白话精华总结

**ScheduledExecutorService是什么？**
- 就是一个能定时执行任务的线程池
- 就像闹钟，可以设置延迟多久执行，或者每隔多久执行一次

**核心功能：**
```
1. schedule()        - 延迟执行一次（延迟3秒后执行）
2. scheduleAtFixedRate()   - 固定频率执行（每隔2秒执行一次）
3. scheduleWithFixedDelay() - 固定延迟执行（上次执行完后延迟2秒再执行）
```

**底层核心组件：**
```
1. DelayedWorkQueue - 延迟队列（优先级队列，最早到期的任务在队首）
2. ScheduledFutureTask - 定时任务包装类（记录任务、延迟时间、周期）
3. 堆排序算法 - 维护任务的执行顺序
```

**一句话记住：**
> ScheduledExecutorService用延迟队列存储任务，用堆排序保证最早到期的任务先执行！

---

## 📚 目录

1. [基本使用](#1-基本使用)
2. [核心类结构](#2-核心类结构)
3. [DelayedWorkQueue深度解析](#3-delayedworkqueue深度解析)
4. [ScheduledFutureTask详解](#4-scheduledfuturetask详解)
5. [任务调度流程](#5-任务调度流程)
6. [源码深度剖析](#6-源码深度剖析)
7. [性能优化](#7-性能优化)
8. [最佳实践](#8-最佳实践)

---

## 1. 基本使用

### 1.1 创建ScheduledExecutorService

```java
// 方式1：使用Executors工厂方法
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

// 方式2：直接创建ScheduledThreadPoolExecutor
ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
    5,  // 核心线程数
    new ThreadFactory() {
        private AtomicInteger count = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("scheduled-pool-" + count.incrementAndGet());
            return t;
        }
    }
);
```

### 1.2 三种调度方式

```java
public class ScheduledDemo {
    public static void main(String[] args) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
        
        // 1. schedule - 延迟执行一次
        scheduler.schedule(() -> {
            System.out.println("延迟3秒后执行一次");
        }, 3, TimeUnit.SECONDS);
        
        // 2. scheduleAtFixedRate - 固定频率执行
        // 从开始时间算，每隔2秒执行一次（不管上次任务是否完成）
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("每隔2秒执行一次: " + System.currentTimeMillis());
            try {
                Thread.sleep(1000); // 模拟任务执行1秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 0, 2, TimeUnit.SECONDS);
        
        // 3. scheduleWithFixedDelay - 固定延迟执行
        // 上次任务执行完后，延迟2秒再执行下一次
        scheduler.scheduleWithFixedDelay(() -> {
            System.out.println("上次执行完后延迟2秒: " + System.currentTimeMillis());
            try {
                Thread.sleep(1000); // 模拟任务执行1秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 0, 2, TimeUnit.SECONDS);
    }
}
```

### 1.3 scheduleAtFixedRate vs scheduleWithFixedDelay

```
scheduleAtFixedRate（固定频率）：
时间轴：0s----2s----4s----6s----8s
任务：  T1    T2    T3    T4    T5
说明：每隔2秒执行一次，不管任务执行多久

如果任务执行时间 > 周期时间：
时间轴：0s----2s----4s----6s----8s
任务：  T1(3秒)     T2(3秒)     T3
说明：T1执行3秒，T2会在T1执行完后立即执行

scheduleWithFixedDelay（固定延迟）：
时间轴：0s----2s----4s----6s----8s----10s
任务：  T1(1秒) 延迟2秒 T2(1秒) 延迟2秒 T3
说明：每次任务执行完后，延迟2秒再执行下一次
```

---

## 2. 核心类结构

### 2.1 类继承关系

```
┌─────────────────────────────────────────────────────────┐
│                    Executor                              │
│                       ↑                                  │
│                       │                                  │
│                 ExecutorService                          │
│                       ↑                                  │
│                       │                                  │
│            ScheduledExecutorService (接口)               │
│                       ↑                                  │
│                       │                                  │
│              ThreadPoolExecutor                          │
│                       ↑                                  │
│                       │                                  │
│          ScheduledThreadPoolExecutor (实现类)            │
└─────────────────────────────────────────────────────────┘
```

### 2.2 核心组件

```java
public class ScheduledThreadPoolExecutor extends ThreadPoolExecutor
        implements ScheduledExecutorService {
    
    // 1. 延迟队列（核心！）
    // 使用DelayedWorkQueue而不是普通的BlockingQueue
    private final DelayedWorkQueue workQueue = new DelayedWorkQueue();
    
    // 2. 是否在shutdown后继续执行周期任务
    private volatile boolean continueExistingPeriodicTasksAfterShutdown;
    
    // 3. 是否在shutdown后继续执行延迟任务
    private volatile boolean executeExistingDelayedTasksAfterShutdown = true;
    
    // 4. 是否在取消时从队列移除任务
    private volatile boolean removeOnCancel = false;
    
    // 5. 任务序列号（用于FIFO排序）
    private static final AtomicLong sequencer = new AtomicLong();
}
```

---

## 3. DelayedWorkQueue深度解析

### 3.1 DelayedWorkQueue是什么？

```
DelayedWorkQueue是一个基于堆实现的优先级队列：
1. 无界队列（可以无限添加任务）
2. 按照任务的到期时间排序
3. 最早到期的任务在队首
4. 使用小顶堆（Min Heap）实现
```

### 3.2 核心数据结构

```java
static class DelayedWorkQueue extends AbstractQueue<Runnable>
        implements BlockingQueue<Runnable> {
    
    // 初始容量
    private static final int INITIAL_CAPACITY = 16;
    
    // 任务数组（堆）
    private RunnableScheduledFuture<?>[] queue = 
        new RunnableScheduledFuture<?>[INITIAL_CAPACITY];
    
    // 锁（保证线程安全）
    private final ReentrantLock lock = new ReentrantLock();
    
    // 队列大小
    private int size = 0;
    
    // 等待队首任务的线程（Leader-Follower模式）
    private Thread leader = null;
    
    // 条件变量（用于等待新任务或任务到期）
    private final Condition available = lock.newCondition();
}
```

### 3.3 堆结构示意图

```
小顶堆（最小的在堆顶）：

           任务A(1s)
          /         \
      任务B(3s)    任务C(5s)
      /    \       /    \
  任务D(7s) 任务E(9s) 任务F(11s) 任务G(13s)

数组表示：
索引：  0      1      2      3      4      5       6
任务： A(1s)  B(3s)  C(5s)  D(7s)  E(9s)  F(11s)  G(13s)

堆性质：
- 父节点 <= 子节点
- 父节点索引 i，左子节点 2*i+1，右子节点 2*i+2
- 子节点索引 i，父节点 (i-1)/2
```

### 3.4 核心方法源码分析

#### 3.4.1 offer() - 添加任务

```java
public boolean offer(Runnable x) {
    if (x == null)
        throw new NullPointerException();
    RunnableScheduledFuture<?> e = (RunnableScheduledFuture<?>)x;
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        int i = size;
        // 1. 扩容检查
        if (i >= queue.length)
            grow();  // 扩容为原来的1.5倍
        
        // 2. 队列大小+1
        size = i + 1;
        
        // 3. 如果是第一个任务，直接放在堆顶
        if (i == 0) {
            queue[0] = e;
            setIndex(e, 0);
        } else {
            // 4. 否则，执行上浮操作（siftUp）
            siftUp(i, e);
        }
        
        // 5. 如果新任务在堆顶，唤醒等待的线程
        if (queue[0] == e) {
            leader = null;
            available.signal();
        }
    } finally {
        lock.unlock();
    }
    return true;
}
```

#### 3.4.2 siftUp() - 上浮操作

```java
/**
 * 上浮操作：将新任务从底部向上调整到合适位置
 * @param k 当前位置
 * @param key 要插入的任务
 */
private void siftUp(int k, RunnableScheduledFuture<?> key) {
    while (k > 0) {
        // 1. 找到父节点
        int parent = (k - 1) >>> 1;  // 等价于 (k-1)/2
        RunnableScheduledFuture<?> e = queue[parent];
        
        // 2. 如果当前任务 >= 父节点，停止上浮
        if (key.compareTo(e) >= 0)
            break;
        
        // 3. 否则，交换当前节点和父节点
        queue[k] = e;
        setIndex(e, k);
        k = parent;
    }
    
    // 4. 将任务放在最终位置
    queue[k] = key;
    setIndex(key, k);
}
```

**上浮过程示意图：**

```
插入任务X(2s)到堆中：

初始堆：
           A(1s)
          /     \
      B(3s)     C(5s)
      /
  D(7s)

步骤1：将X放在末尾（索引4）
           A(1s)
          /     \
      B(3s)     C(5s)
      /    \
  D(7s)  X(2s)

步骤2：比较X(2s)和父节点B(3s)，X < B，交换
           A(1s)
          /     \
      X(2s)     C(5s)
      /    \
  D(7s)  B(3s)

步骤3：比较X(2s)和父节点A(1s)，X > A，停止
最终堆：
           A(1s)
          /     \
      X(2s)     C(5s)
      /    \
  D(7s)  B(3s)
```

#### 3.4.3 take() - 获取任务

```java
public RunnableScheduledFuture<?> take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        for (;;) {
            // 1. 获取堆顶任务（最早到期的任务）
            RunnableScheduledFuture<?> first = queue[0];
            
            // 2. 如果队列为空，等待
            if (first == null)
                available.await();
            else {
                // 3. 计算任务还需要多久到期
                long delay = first.getDelay(NANOSECONDS);
                
                // 4. 如果已经到期，取出任务
                if (delay <= 0)
                    return finishPoll(first);
                
                // 5. 如果还没到期，需要等待
                first = null; // 帮助GC
                
                // 6. Leader-Follower模式
                if (leader != null)
                    // 如果已经有线程在等待，当前线程无限期等待
                    available.await();
                else {
                    // 否则，当前线程成为leader，等待指定时间
                    Thread thisThread = Thread.currentThread();
                    leader = thisThread;
                    try {
                        available.awaitNanos(delay);
                    } finally {
                        if (leader == thisThread)
                            leader = null;
                    }
                }
            }
        }
    } finally {
        // 7. 如果队列不为空且没有leader，唤醒一个等待线程
        if (leader == null && queue[0] != null)
            available.signal();
        lock.unlock();
    }
}
```

#### 3.4.4 finishPoll() - 完成取出操作

```java
private RunnableScheduledFuture<?> finishPoll(RunnableScheduledFuture<?> f) {
    // 1. 队列大小-1
    int s = --size;
    
    // 2. 获取最后一个任务
    RunnableScheduledFuture<?> x = queue[s];
    queue[s] = null;
    
    // 3. 如果队列不为空，执行下沉操作
    if (s != 0)
        siftDown(0, x);
    
    // 4. 清除任务的堆索引
    setIndex(f, -1);
    return f;
}
```

#### 3.4.5 siftDown() - 下沉操作

```java
/**
 * 下沉操作：将任务从顶部向下调整到合适位置
 * @param k 当前位置
 * @param key 要插入的任务
 */
private void siftDown(int k, RunnableScheduledFuture<?> key) {
    int half = size >>> 1;  // 非叶子节点的数量
    
    while (k < half) {
        // 1. 找到左子节点
        int child = (k << 1) + 1;  // 等价于 2*k+1
        RunnableScheduledFuture<?> c = queue[child];
        
        // 2. 找到右子节点
        int right = child + 1;
        
        // 3. 选择左右子节点中较小的
        if (right < size && c.compareTo(queue[right]) > 0)
            c = queue[child = right];
        
        // 4. 如果当前任务 <= 子节点，停止下沉
        if (key.compareTo(c) <= 0)
            break;
        
        // 5. 否则，交换当前节点和子节点
        queue[k] = c;
        setIndex(c, k);
        k = child;
    }
    
    // 6. 将任务放在最终位置
    queue[k] = key;
    setIndex(key, k);
}
```

**下沉过程示意图：**

```
取出堆顶任务A后，将最后一个任务D放到堆顶：

初始堆：
           A(1s)
          /     \
      B(3s)     C(5s)
      /    \
  D(7s)  E(9s)

步骤1：移除A，将D放到堆顶
           D(7s)
          /     \
      B(3s)     C(5s)
      /
  E(9s)

步骤2：比较D(7s)和子节点B(3s)、C(5s)，选择最小的B
           B(3s)
          /     \
      D(7s)     C(5s)
      /
  E(9s)

步骤3：比较D(7s)和子节点E(9s)，D < E，停止
最终堆：
           B(3s)
          /     \
      D(7s)     C(5s)
      /
  E(9s)
```

### 3.5 Leader-Follower模式

```
Leader-Follower模式是一种优化策略：

问题：
如果多个线程都在等待队首任务到期，会造成资源浪费

解决方案：
1. 只有一个线程（Leader）等待指定时间
2. 其他线程（Follower）无限期等待
3. Leader取到任务后，会唤醒一个Follower成为新的Leader

示意图：
┌─────────────────────────────────────────────┐
│  DelayedWorkQueue                           │
│  ┌───────────────────────────────────┐     │
│  │  堆顶任务：3秒后到期              │     │
│  └───────────────────────────────────┘     │
│                                             │
│  Thread-1 (Leader)  ← 等待3秒              │
│  Thread-2 (Follower) ← 无限期等待          │
│  Thread-3 (Follower) ← 无限期等待          │
│                                             │
│  3秒后：                                    │
│  Thread-1取到任务，唤醒Thread-2             │
│  Thread-2成为新的Leader                     │
└─────────────────────────────────────────────┘

优点：
1. 减少不必要的唤醒
2. 降低CPU消耗
3. 提高性能
```

---

## 4. ScheduledFutureTask详解

### 4.1 ScheduledFutureTask是什么？

```
ScheduledFutureTask是定时任务的包装类：
1. 继承自FutureTask（可以获取任务结果）
2. 实现了RunnableScheduledFuture接口（支持延迟和周期）
3. 实现了Comparable接口（支持排序）
```

### 4.2 核心字段

```java
private class ScheduledFutureTask<V> extends FutureTask<V>
        implements RunnableScheduledFuture<V> {
    
    // 1. 任务序列号（用于FIFO排序）
    private final long sequenceNumber;
    
    // 2. 任务的执行时间（纳秒）
    private long time;
    
    // 3. 任务的周期
    // period = 0：一次性任务
    // period > 0：固定频率任务（scheduleAtFixedRate）
    // period < 0：固定延迟任务（scheduleWithFixedDelay）
    private final long period;
    
    // 4. 实际的任务（用于周期任务的重新入队）
    RunnableScheduledFuture<V> outerTask = this;
    
    // 5. 任务在堆中的索引（用于快速删除）
    int heapIndex;
}
```

### 4.3 任务比较逻辑

```java
public int compareTo(Delayed other) {
    if (other == this)
        return 0;
    
    if (other instanceof ScheduledFutureTask) {
        ScheduledFutureTask<?> x = (ScheduledFutureTask<?>)other;
        long diff = time - x.time;
        
        // 1. 先比较执行时间
        if (diff < 0)
            return -1;
        else if (diff > 0)
            return 1;
        
        // 2. 如果执行时间相同，比较序列号（FIFO）
        else if (sequenceNumber < x.sequenceNumber)
            return -1;
        else
            return 1;
    }
    
    // 3. 如果是其他类型的Delayed，只比较延迟时间
    long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
    return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
}
```

### 4.4 run() - 任务执行逻辑

```java
public void run() {
    // 1. 判断是否是周期任务
    boolean periodic = isPeriodic();
    
    // 2. 检查是否可以执行
    if (!canRunInCurrentRunState(periodic))
        cancel(false);
    
    // 3. 如果是一次性任务，直接执行
    else if (!periodic)
        ScheduledFutureTask.super.run();
    
    // 4. 如果是周期任务
    else if (ScheduledFutureTask.super.runAndReset()) {
        // 4.1 计算下次执行时间
        setNextRunTime();
        
        // 4.2 重新入队
        reExecutePeriodic(outerTask);
    }
}
```

### 4.5 setNextRunTime() - 计算下次执行时间

```java
private void setNextRunTime() {
    long p = period;
    
    // 1. 固定频率任务（scheduleAtFixedRate）
    if (p > 0)
        time += p;  // 下次执行时间 = 当前时间 + 周期
    
    // 2. 固定延迟任务（scheduleWithFixedDelay）
    else
        time = triggerTime(-p);  // 下次执行时间 = now() + 延迟
}

long triggerTime(long delay) {
    return now() + 
        ((delay < (Long.MAX_VALUE >> 1)) ? delay : overflowFree(delay));
}
```

**两种周期任务的区别：**

```
scheduleAtFixedRate（period > 0）：
时间轴：0s----2s----4s----6s----8s
任务：  T1    T2    T3    T4    T5
计算：  0+2   2+2   4+2   6+2   8+2
说明：下次执行时间 = 上次执行时间 + 周期

scheduleWithFixedDelay（period < 0）：
时间轴：0s----2s----4s----6s----8s----10s
任务：  T1(1秒) 延迟2秒 T2(1秒) 延迟2秒 T3
计算：  now()+2      now()+2
说明：下次执行时间 = 当前时间 + 延迟
```

---

## 5. 任务调度流程

### 5.1 schedule() - 延迟执行一次

```
完整流程：
┌─────────────────────────────────────────────────────────┐
│ 1. 用户调用 schedule(task, 3, SECONDS)                  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. 创建 ScheduledFutureTask                             │
│    - time = now() + 3秒                                 │
│    - period = 0（一次性任务）                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 调用 delayedExecute(task)                            │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 将任务加入 DelayedWorkQueue                          │
│    - 执行 offer() 操作                                  │
│    - 执行 siftUp() 调整堆                               │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. 工作线程从队列取任务                                  │
│    - 调用 take() 方法                                   │
│    - 如果任务未到期，等待                                │
│    - 如果任务到期，取出任务                              │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 6. 执行任务                                              │
│    - 调用 task.run()                                    │
│    - 执行用户的业务逻辑                                  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 7. 任务完成                                              │
│    - 设置结果                                            │
│    - 唤醒等待的线程                                      │
└─────────────────────────────────────────────────────────┘
```

### 5.2 scheduleAtFixedRate() - 固定频率执行

```
完整流程：
┌─────────────────────────────────────────────────────────┐
│ 1. 用户调用 scheduleAtFixedRate(task, 0, 2, SECONDS)    │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. 创建 ScheduledFutureTask                             │
│    - time = now() + 0秒（立即执行）                     │
│    - period = 2秒（正数，固定频率）                     │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 加入队列，工作线程取出并执行                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 执行 task.run()                                      │
│    - 调用 runAndReset()（不设置结果，可重复执行）       │
│    - 执行用户业务逻辑                                    │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. 计算下次执行时间                                      │
│    - time = time + period                               │
│    - 例如：0 + 2 = 2秒                                  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 6. 重新入队                                              │
│    - 调用 reExecutePeriodic(task)                       │
│    - 将任务重新加入 DelayedWorkQueue                    │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 7. 循环执行                                              │
│    - 工作线程再次取出任务                                │
│    - 重复步骤4-6                                         │
└─────────────────────────────────────────────────────────┘
```

### 5.3 scheduleWithFixedDelay() - 固定延迟执行

```
完整流程：
┌─────────────────────────────────────────────────────────┐
│ 1. 用户调用 scheduleWithFixedDelay(task, 0, 2, SECONDS) │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. 创建 ScheduledFutureTask                             │
│    - time = now() + 0秒（立即执行）                     │
│    - period = -2秒（负数，固定延迟）                    │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 加入队列，工作线程取出并执行                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 执行 task.run()                                      │
│    - 调用 runAndReset()                                 │
│    - 执行用户业务逻辑                                    │
│    - 假设执行了1秒                                       │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. 计算下次执行时间                                      │
│    - time = now() + |period|                            │
│    - 例如：当前时间 + 2秒                                │
│    - 注意：是任务执行完后的当前时间                      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 6. 重新入队并循环执行                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 6. 源码深度剖析

### 6.1 schedule() 源码

```java
public ScheduledFuture<?> schedule(Runnable command,
                                   long delay,
                                   TimeUnit unit) {
    // 1. 参数校验
    if (command == null || unit == null)
        throw new NullPointerException();
    
    // 2. 装饰任务（创建ScheduledFutureTask）
    RunnableScheduledFuture<?> t = decorateTask(command,
        new ScheduledFutureTask<Void>(command, null,
                                      triggerTime(delay, unit)));
    
    // 3. 延迟执行
    delayedExecute(t);
    return t;
}

/**
 * 计算任务的触发时间
 */
private long triggerTime(long delay, TimeUnit unit) {
    return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
}

long triggerTime(long delay) {
    return now() + 
        ((delay < (Long.MAX_VALUE >> 1)) ? delay : overflowFree(delay));
}
```

### 6.2 scheduleAtFixedRate() 源码

```java
public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                              long initialDelay,
                                              long period,
                                              TimeUnit unit) {
    // 1. 参数校验
    if (command == null || unit == null)
        throw new NullPointerException();
    if (period <= 0)
        throw new IllegalArgumentException();
    
    // 2. 创建周期任务（period为正数）
    ScheduledFutureTask<Void> sft =
        new ScheduledFutureTask<Void>(command,
                                      null,
                                      triggerTime(initialDelay, unit),
                                      unit.toNanos(period));  // 正数
    
    // 3. 装饰任务
    RunnableScheduledFuture<Void> t = decorateTask(command, sft);
    sft.outerTask = t;
    
    // 4. 延迟执行
    delayedExecute(t);
    return t;
}
```

### 6.3 scheduleWithFixedDelay() 源码

```java
public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                 long initialDelay,
                                                 long delay,
                                                 TimeUnit unit) {
    // 1. 参数校验
    if (command == null || unit == null)
        throw new NullPointerException();
    if (delay <= 0)
        throw new IllegalArgumentException();
    
    // 2. 创建周期任务（period为负数）
    ScheduledFutureTask<Void> sft =
        new ScheduledFutureTask<Void>(command,
                                      null,
                                      triggerTime(initialDelay, unit),
                                      unit.toNanos(-delay));  // 负数！
    
    // 3. 装饰任务
    RunnableScheduledFuture<Void> t = decorateTask(command, sft);
    sft.outerTask = t;
    
    // 4. 延迟执行
    delayedExecute(t);
    return t;
}
```

### 6.4 delayedExecute() - 核心调度方法

```java
private void delayedExecute(RunnableScheduledFuture<?> task) {
    // 1. 如果线程池已关闭，拒绝任务
    if (isShutdown())
        reject(task);
    else {
        // 2. 将任务加入延迟队列
        super.getQueue().add(task);
        
        // 3. 再次检查线程池状态
        if (isShutdown() &&
            !canRunInCurrentRunState(task.isPeriodic()) &&
            remove(task))
            task.cancel(false);
        else
            // 4. 确保有工作线程
            ensurePrestart();
    }
}

/**
 * 确保至少有一个工作线程
 */
void ensurePrestart() {
    int wc = workerCountOf(ctl.get());
    
    // 如果工作线程数 < 核心线程数，添加工作线程
    if (wc < corePoolSize)
        addWorker(null, true);
    
    // 如果核心线程数为0，至少添加一个线程
    else if (wc == 0)
        addWorker(null, false);
}
```

### 6.5 reExecutePeriodic() - 周期任务重新入队

```java
void reExecutePeriodic(RunnableScheduledFuture<?> task) {
    // 1. 检查是否可以执行
    if (canRunInCurrentRunState(true)) {
        // 2. 重新加入队列
        super.getQueue().add(task);
        
        // 3. 再次检查状态
        if (!canRunInCurrentRunState(true) && remove(task))
            task.cancel(false);
        else
            // 4. 确保有工作线程
            ensurePrestart();
    }
}
```

---

## 7. 性能优化

### 7.1 堆操作的时间复杂度

```
操作          时间复杂度    说明
offer()       O(log n)     上浮操作
take()        O(log n)     下沉操作
peek()        O(1)         只看堆顶
remove()      O(n)         需要查找+调整

优化建议：
1. 尽量使用peek()而不是take()来查看任务
2. 避免频繁remove()操作
3. 合理设置初始容量，减少扩容
```

### 7.2 Leader-Follower模式的优势

```
传统方式：
- 所有线程都等待相同的时间
- 大量无效的唤醒和上下文切换
- CPU资源浪费

Leader-Follower方式：
- 只有一个线程等待指定时间
- 其他线程无限期等待
- 减少不必要的唤醒
- 降低CPU消耗

性能提升：
在高并发场景下，可以提升20-30%的性能
```

### 7.3 任务序列号的作用

```java
// 任务序列号用于FIFO排序
private static final AtomicLong sequencer = new AtomicLong();

// 在创建任务时分配
this.sequenceNumber = sequencer.getAndIncrement();

作用：
1. 当多个任务的执行时间相同时，按照提交顺序执行
2. 保证公平性
3. 避免饥饿

示例：
任务A：time=100, seq=1
任务B：time=100, seq=2
任务C：time=100, seq=3

执行顺序：A → B → C（FIFO）
```

---

## 8. 最佳实践

### 8.1 合理设置核心线程数

```java
// 不推荐：线程数太少
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

// 推荐：根据任务数量设置
int taskCount = 10;  // 预计的并发任务数
ScheduledExecutorService scheduler = 
    Executors.newScheduledThreadPool(Math.min(taskCount, 10));

// 最佳实践：使用自定义ThreadFactory
ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
    5,
    new ThreadFactory() {
        private AtomicInteger count = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("scheduled-pool-" + count.incrementAndGet());
            t.setDaemon(true);  // 设置为守护线程
            return t;
        }
    }
);
```

### 8.2 异常处理

```java
// 问题：周期任务中的异常会导致任务停止
scheduler.scheduleAtFixedRate(() -> {
    int result = 1 / 0;  // 抛出异常，任务停止！
}, 0, 1, TimeUnit.SECONDS);

// 解决方案1：捕获所有异常
scheduler.scheduleAtFixedRate(() -> {
    try {
        // 业务逻辑
        int result = 1 / 0;
    } catch (Exception e) {
        log.error("任务执行失败", e);
    }
}, 0, 1, TimeUnit.SECONDS);

// 解决方案2：使用UncaughtExceptionHandler
ThreadFactory factory = new ThreadFactory() {
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setUncaughtExceptionHandler((thread, throwable) -> {
            log.error("线程异常: " + thread.getName(), throwable);
        });
        return t;
    }
};
```

### 8.3 优雅关闭

```java
public class SchedulerManager {
    private ScheduledExecutorService scheduler;
    
    public void start() {
        scheduler = Executors.newScheduledThreadPool(5);
        // 添加任务...
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            // 1. 停止接收新任务
            scheduler.shutdown();
            
            try {
                // 2. 等待已有任务完成（最多等待30秒）
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    // 3. 如果超时，强制关闭
                    List<Runnable> pendingTasks = scheduler.shutdownNow();
                    log.warn("强制关闭，未执行的任务数: " + pendingTasks.size());
                    
                    // 4. 再等待一段时间
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("线程池无法关闭");
                    }
                }
            } catch (InterruptedException e) {
                // 5. 如果被中断，强制关闭
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

### 8.4 避免任务堆积

```java
// 问题：任务执行时间 > 周期时间，导致任务堆积
scheduler.scheduleAtFixedRate(() -> {
    Thread.sleep(5000);  // 任务执行5秒
}, 0, 1, TimeUnit.SECONDS);  // 每秒执行一次

// 解决方案1：使用scheduleWithFixedDelay
scheduler.scheduleWithFixedDelay(() -> {
    Thread.sleep(5000);  // 任务执行5秒
}, 0, 1, TimeUnit.SECONDS);  // 执行完后延迟1秒

// 解决方案2：监控队列大小
ScheduledThreadPoolExecutor executor = 
    new ScheduledThreadPoolExecutor(5);

// 定期检查队列大小
scheduler.scheduleAtFixedRate(() -> {
    int queueSize = executor.getQueue().size();
    if (queueSize > 100) {
        log.warn("任务队列堆积: " + queueSize);
    }
}, 0, 10, TimeUnit.SECONDS);
```

### 8.5 使用ScheduledFuture获取结果

```java
// 提交有返回值的任务
ScheduledFuture<String> future = scheduler.schedule(() -> {
    // 执行业务逻辑
    return "任务结果";
}, 3, TimeUnit.SECONDS);

try {
    // 获取结果（会阻塞）
    String result = future.get();
    System.out.println("结果: " + result);
    
    // 或者设置超时时间
    String result2 = future.get(5, TimeUnit.SECONDS);
} catch (InterruptedException | ExecutionException | TimeoutException e) {
    e.printStackTrace();
}

// 取消任务
future.cancel(true);  // true表示中断正在执行的任务
```

### 8.6 实战案例：心跳检测

```java
public class HeartbeatMonitor {
    private final ScheduledExecutorService scheduler;
    private final Map<String, Long> lastHeartbeatTime;
    
    public HeartbeatMonitor() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.lastHeartbeatTime = new ConcurrentHashMap<>();
        
        // 启动心跳检测任务
        startHeartbeatCheck();
    }
    
    /**
     * 记录心跳
     */
    public void recordHeartbeat(String clientId) {
        lastHeartbeatTime.put(clientId, System.currentTimeMillis());
    }
    
    /**
     * 启动心跳检测
     */
    private void startHeartbeatCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                long timeout = 30_000; // 30秒超时
                
                lastHeartbeatTime.forEach((clientId, lastTime) -> {
                    if (now - lastTime > timeout) {
                        System.out.println("客户端超时: " + clientId);
                        // 处理超时逻辑
                        handleTimeout(clientId);
                    }
                });
            } catch (Exception e) {
                System.err.println("心跳检测异常: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);  // 每10秒检测一次
    }
    
    private void handleTimeout(String clientId) {
        lastHeartbeatTime.remove(clientId);
        // 其他处理逻辑...
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

---

## 9. 总结

### 9.1 核心要点

```
1. 数据结构
   - DelayedWorkQueue：基于堆的优先级队列
   - ScheduledFutureTask：任务包装类
   - 小顶堆：保证最早到期的任务在堆顶

2. 调度机制
   - schedule：延迟执行一次
   - scheduleAtFixedRate：固定频率执行
   - scheduleWithFixedDelay：固定延迟执行

3. 优化策略
   - Leader-Follower模式：减少无效唤醒
   - 堆排序：O(log n)的时间复杂度
   - 任务序列号：保证FIFO顺序

4. 最佳实践
   - 合理设置线程数
   - 捕获异常
   - 优雅关闭
   - 避免任务堆积
```

### 9.2 与Timer的对比

```
                Timer                  ScheduledExecutorService
线程模型        单线程                  多线程
异常处理        异常会导致Timer停止      异常只影响当前任务
精度            较低                    较高
功能            简单                    丰富
推荐度          不推荐                  推荐

结论：
生产环境应该使用ScheduledExecutorService，而不是Timer
```

### 9.3 适用场景

```
1. 定时任务
   - 定时清理缓存
   - 定时生成报表
   - 定时备份数据

2. 周期任务
   - 心跳检测
   - 健康检查
   - 数据同步

3. 延迟任务
   - 延迟发送消息
   - 延迟关闭连接
   - 延迟重试
```

---

## 🔗 相关代码示例

本文档对应的代码示例位于：

- **[ScheduledExecutorServiceDemo.java](../demo/ScheduledExecutorServiceDemo.java)** - 基本使用示例
- **[HeartbeatMonitor.java](../project/HeartbeatMonitor.java)** - 心跳检测实战

**运行方式：**
```bash
# 编译
javac demo/ScheduledExecutorServiceDemo.java

# 运行
java demo.ScheduledExecutorServiceDemo
```

---

**参考资料：**
- JDK源码：`java.util.concurrent.ScheduledThreadPoolExecutor`
- 《Java并发编程实战》
- 《Java并发编程的艺术》
