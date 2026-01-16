# DelayQueue详解

> **本章目标**：深入理解DelayQueue的延迟获取机制、优先级队列实现、Leader-Follower模式及应用场景

---

## 一、为什么需要DelayQueue？

### 问题1：如何实现延迟任务？

#### 传统方案的问题

**方案1：轮询检查**
```java
// ❌ 低效：不断轮询
while (true) {
    for (Task task : tasks) {
        if (task.getExpireTime() <= System.currentTimeMillis()) {
            execute(task);
        }
    }
    Thread.sleep(100);  // 浪费CPU
}
```

**问题**：
- CPU空转，资源浪费
- 延迟不精确（取决于轮询间隔）
- 无法动态调整

**方案2：Timer/ScheduledExecutorService**
```java
// 每个任务一个定时器
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
scheduler.schedule(task, delay, TimeUnit.SECONDS);
```

**问题**：
- 线程资源消耗大
- 不适合大量短期任务
- 无法统一管理

---

### 问题2：DelayQueue如何解决？

#### 核心思想

```java
// DelayQueue：阻塞 + 延迟获取
BlockingQueue<DelayedTask> queue = new DelayQueue<>();

// 添加延迟任务
queue.put(new DelayedTask(data, 5000));  // 5秒后可取

// 消费者自动等待到期
DelayedTask task = queue.take();  // 阻塞到任务到期
```

**优势**：
1. ✅ **自动等待**：无需轮询，线程挂起
2. ✅ **精准延迟**：基于优先级队列，按到期时间排序
3. ✅ **统一管理**：所有延迟任务在一个队列
4. ✅ **高效**：Leader-Follower模式优化等待

---

### 问题3：DelayQueue的典型应用场景

#### 场景1：订单超时取消

```java
// 订单创建后30分钟未支付自动取消
public void createOrder(Order order) {
    // 保存订单
    orderRepository.save(order);
    
    // 添加延迟任务
    DelayedTask task = new DelayedTask(order.getId(), 30 * 60 * 1000);
    delayQueue.put(task);
}

// 消费者线程
while (true) {
    DelayedTask task = delayQueue.take();
    Order order = orderRepository.findById(task.getOrderId());
    if (order.getStatus() == UNPAID) {
        order.cancel();  // 取消订单
    }
}
```

#### 场景2：缓存过期清理

```java
// 缓存项过期自动删除
public void put(String key, Object value, long ttl) {
    cache.put(key, value);
    delayQueue.put(new ExpireTask(key, ttl));
}

// 清理线程
while (true) {
    ExpireTask task = delayQueue.take();
    cache.remove(task.getKey());
}
```

#### 场景3：定时任务调度

```java
// 每天凌晨执行任务
public void scheduleDaily(Runnable task) {
    long delay = calculateDelayToMidnight();
    delayQueue.put(new DelayedTask(task, delay));
}
```

---

## 二、核心数据结构

### 2.1 类定义

```java
public class DelayQueue<E extends Delayed> extends AbstractQueue<E>
        implements BlockingQueue<E> {
    
    // 全局锁
    private final transient ReentrantLock lock = new ReentrantLock();
    
    // 优先级队列（按到期时间排序）
    private final PriorityQueue<E> q = new PriorityQueue<E>();
    
    // Leader线程（等待队头元素的线程）
    private Thread leader = null;
    
    // 条件变量（队列非空）
    private final Condition available = lock.newCondition();
}
```

### 2.2 Delayed接口

```java
public interface Delayed extends Comparable<Delayed> {
    /**
     * 返回剩余延迟时间（纳秒）
     * 负数表示已到期
     */
    long getDelay(TimeUnit unit);
}
```

**使用示例**：
```java
class DelayedTask implements Delayed {
    private final long expireTime;  // 到期时间（绝对时间）
    
    public DelayedTask(long delay) {
        this.expireTime = System.currentTimeMillis() + delay;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = expireTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int compareTo(Delayed o) {
        // 按到期时间排序
        return Long.compare(this.expireTime, ((DelayedTask) o).expireTime);
    }
}
```

### 2.3 内部结构

```
DelayQueue内部结构：

┌─────────────────────────────────────┐
│         DelayQueue                  │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   PriorityQueue (小顶堆)      │  │
│  │                               │  │
│  │        [Task1: 100ms]         │  │ ← 队头（最早到期）
│  │       /              \        │  │
│  │  [Task2: 200ms]  [Task3: 300ms] │
│  │                               │  │
│  └──────────────────────────────┘  │
│                                     │
│  leader = Thread-1 (等待Task1到期)  │
│                                     │
│  available (Condition)              │
│    - Thread-2 (等待)                │
│    - Thread-3 (等待)                │
└─────────────────────────────────────┘
```

---

## 三、核心操作流程

### 3.1 入队操作（offer/put）

#### 流程图

```
offer(E e) 流程
    │
    ▼
┌─────────────┐
│ 获取lock    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 添加到堆     │
│ q.offer(e)  │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ 新元素是队头？   │
└──────┬──────────┘
       │
      Yes
       │
       ▼
┌─────────────┐
│ leader=null │ ← 清空leader
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ signal()    │ ← 唤醒等待线程
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 释放lock    │
└─────────────┘
```

#### 源码分析

```java
public boolean offer(E e) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 1. 添加到优先级队列
        q.offer(e);
        
        // 2. 如果新元素成为队头，清空leader并唤醒
        if (q.peek() == e) {
            leader = null;
            available.signal();
        }
        return true;
    } finally {
        lock.unlock();
    }
}

// put等同于offer（无界队列，永不阻塞）
public void put(E e) {
    offer(e);
}
```

**关键点**：

1. **为什么新元素是队头时要清空leader？**
```java
// 场景：原队头到期时间100ms，新元素到期时间50ms
// 如果不清空leader，leader还在等待100ms
// 新元素50ms就到期了，但没人取

if (q.peek() == e) {
    leader = null;      // 清空leader
    available.signal(); // 唤醒等待线程重新竞争
}
```

2. **为什么无界队列？**
```java
// DelayQueue基于PriorityQueue，无容量限制
// 所有元素都会被接受
// put永不阻塞
```

---

### 3.2 出队操作（take）

#### 完整流程图

```
take() 流程
    │
    ▼
┌─────────────┐
│ 获取lock    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 循环开始     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取队头     │
│ first=q.peek()│
└──────┬──────┘
       │
   ┌───┴───┐
   │       │
  null    非null
   │       │
   ▼       ▼
┌──────┐ ┌──────────┐
│await │ │ 检查延迟  │
└──────┘ │getDelay()│
          └────┬─────┘
               │
          ┌────┴────┐
          │         │
        <=0        >0
          │         │
          ▼         ▼
     ┌────────┐ ┌──────────┐
     │出队返回│ │leader存在?│
     │q.poll()│ └────┬─────┘
     └────────┘      │
                 ┌───┴───┐
                 │       │
                Yes      No
                 │       │
                 ▼       ▼
            ┌──────┐ ┌──────────┐
            │await │ │设为leader │
            └──────┘ │awaitNanos│
                     └────┬─────┘
                          │
                     ┌────┴────┐
                     │         │
                   到期      被唤醒
                     │         │
                     └────┬────┘
                          │
                          ▼
                     ┌──────────┐
                     │leader=null│
                     └──────────┘
```

#### 源码分析

```java
public E take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        for (;;) {
            // 1. 获取队头元素
            E first = q.peek();
            
            // 2. 队列空，等待
            if (first == null)
                available.await();
            else {
                // 3. 获取剩余延迟时间
                long delay = first.getDelay(NANOSECONDS);
                
                // 4. 已到期，直接返回
                if (delay <= 0)
                    return q.poll();
                
                // 5. 未到期，需要等待
                first = null;  // 释放引用（避免内存泄漏）
                
                // 6. 如果已有leader，当前线程无限期等待
                if (leader != null)
                    available.await();
                else {
                    // 7. 成为leader，等待指定时间
                    Thread thisThread = Thread.currentThread();
                    leader = thisThread;
                    try {
                        available.awaitNanos(delay);
                    } finally {
                        // 8. 等待结束，清空leader
                        if (leader == thisThread)
                            leader = null;
                    }
                }
            }
        }
    } finally {
        // 9. 如果队列非空且无leader，唤醒一个等待线程
        if (leader == null && q.peek() != null)
            available.signal();
        lock.unlock();
    }
}
```

**关键点分析**：

1. **为什么要设置first = null？**
```java
E first = q.peek();
long delay = first.getDelay(NANOSECONDS);
if (delay > 0) {
    first = null;  // 释放引用
    // ...
}

// 原因：
// - 当前线程可能等待很长时间
// - 如果持有first引用，该对象无法被GC
// - 设为null，允许GC回收
```

2. **Leader-Follower模式详解**

**什么是Leader-Follower？**
```java
// Leader：等待队头元素到期的线程
// Follower：其他等待线程

// 场景：队头元素100ms后到期
Thread-1: leader = Thread-1, awaitNanos(100ms)  // Leader
Thread-2: await()                                // Follower
Thread-3: await()                                // Follower

// 100ms后：
Thread-1: 被唤醒，取出元素，leader = null
Thread-2/3: 被signal唤醒，重新竞争leader
```

**为什么需要Leader-Follower？**
```java
// ❌ 没有Leader-Follower：
Thread-1: awaitNanos(100ms)
Thread-2: awaitNanos(100ms)
Thread-3: awaitNanos(100ms)
// 问题：100ms后3个线程同时被唤醒，但只有1个元素
// 造成惊群效应

// ✅ 有Leader-Follower：
Thread-1: awaitNanos(100ms)  // Leader
Thread-2: await()            // Follower（无限期等待）
Thread-3: await()            // Follower
// 优势：只有Leader在100ms后被唤醒，Follower继续等待
```

3. **为什么finally中要signal？**
```java
finally {
    if (leader == null && q.peek() != null)
        available.signal();
    lock.unlock();
}

// 场景：
// 1. Thread-1取出元素，leader = null
// 2. 队列还有元素
// 3. 需要唤醒一个Follower成为新的Leader
```

---

### 3.3 非阻塞操作（poll）

```java
public E poll() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        E first = q.peek();
        // 队列空或未到期，返回null
        if (first == null || first.getDelay(NANOSECONDS) > 0)
            return null;
        else
            return q.poll();
    } finally {
        lock.unlock();
    }
}

// 超时版本
public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        for (;;) {
            E first = q.peek();
            if (first == null) {
                if (nanos <= 0)
                    return null;
                else
                    nanos = available.awaitNanos(nanos);
            } else {
                long delay = first.getDelay(NANOSECONDS);
                if (delay <= 0)
                    return q.poll();
                if (nanos <= 0)
                    return null;
                
                first = null;
                
                // 超时时间 < 延迟时间，等待超时时间
                if (nanos < delay || leader != null)
                    nanos = available.awaitNanos(nanos);
                else {
                    // 超时时间 >= 延迟时间，成为leader
                    Thread thisThread = Thread.currentThread();
                    leader = thisThread;
                    try {
                        long timeLeft = available.awaitNanos(delay);
                        nanos -= delay - timeLeft;
                    } finally {
                        if (leader == thisThread)
                            leader = null;
                    }
                }
            }
        }
    } finally {
        if (leader == null && q.peek() != null)
            available.signal();
        lock.unlock();
    }
}
```

---

## 四、核心设计精髓

### 4.1 优先级队列 + 延迟获取

#### 为什么用PriorityQueue？

```java
// 堆结构，自动按到期时间排序
private final PriorityQueue<E> q = new PriorityQueue<E>();

// 添加元素：O(log n)
q.offer(element);

// 获取最早到期：O(1)
E first = q.peek();
```

**优势**：
- 队头始终是最早到期的元素
- 无需遍历整个队列
- 高效

---

### 4.2 Leader-Follower模式

#### 模式详解

```java
// Leader：等待队头元素的线程
private Thread leader = null;

// 成为Leader
if (leader == null) {
    leader = Thread.currentThread();
    available.awaitNanos(delay);  // 等待指定时间
}

// Follower：其他等待线程
else {
    available.await();  // 无限期等待
}
```

**时序图**：
```
时间线：队头元素100ms后到期

T0:  Thread-1 take() -> leader = Thread-1, awaitNanos(100ms)
     Thread-2 take() -> leader != null, await()
     Thread-3 take() -> leader != null, await()

T100: Thread-1被唤醒 -> poll() -> leader = null -> signal()
      Thread-2被唤醒 -> 竞争leader
      Thread-3继续等待

T101: Thread-2成为leader -> awaitNanos(...)
```

**性能优化**：
- 减少不必要的唤醒
- 避免惊群效应
- 精准控制等待时间

---

### 4.3 内存泄漏防护

```java
E first = q.peek();
long delay = first.getDelay(NANOSECONDS);
if (delay > 0) {
    first = null;  // 释放引用
    // ...
}
```

**为什么需要？**
```java
// 场景：队头元素1小时后到期
E first = q.peek();  // 持有引用
available.awaitNanos(3600000);  // 等待1小时

// 问题：
// - first对象被局部变量引用
// - 即使队列中已删除，也无法GC
// - 造成内存泄漏

// 解决：
first = null;  // 释放引用，允许GC
```

---

## 五、应用场景与最佳实践

### 5.1 订单超时取消

```java
public class OrderTimeoutManager {
    private final DelayQueue<OrderTimeout> queue = new DelayQueue<>();
    
    // 创建订单
    public void createOrder(Order order) {
        orderRepository.save(order);
        
        // 30分钟后超时
        OrderTimeout timeout = new OrderTimeout(
            order.getId(), 
            30 * 60 * 1000
        );
        queue.put(timeout);
    }
    
    // 支付订单
    public void payOrder(String orderId) {
        Order order = orderRepository.findById(orderId);
        order.setStatus(PAID);
        orderRepository.save(order);
        // 注意：已支付的订单仍在队列中，需要在消费时判断
    }
    
    // 超时检查线程
    public void startTimeoutChecker() {
        new Thread(() -> {
            while (true) {
                try {
                    OrderTimeout timeout = queue.take();
                    Order order = orderRepository.findById(timeout.getOrderId());
                    
                    // 只取消未支付的订单
                    if (order.getStatus() == UNPAID) {
                        order.cancel();
                        orderRepository.save(order);
                        System.out.println("订单超时取消: " + order.getId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}

class OrderTimeout implements Delayed {
    private final String orderId;
    private final long expireTime;
    
    public OrderTimeout(String orderId, long delay) {
        this.orderId = orderId;
        this.expireTime = System.currentTimeMillis() + delay;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = expireTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.expireTime, ((OrderTimeout) o).expireTime);
    }
    
    public String getOrderId() {
        return orderId;
    }
}
```

---

### 5.2 缓存过期管理

```java
public class ExpiringCache<K, V> {
    private final Map<K, V> cache = new ConcurrentHashMap<>();
    private final DelayQueue<ExpireEntry<K>> expireQueue = new DelayQueue<>();
    
    // 添加缓存（带过期时间）
    public void put(K key, V value, long ttl) {
        cache.put(key, value);
        expireQueue.put(new ExpireEntry<>(key, ttl));
    }
    
    // 获取缓存
    public V get(K key) {
        return cache.get(key);
    }
    
    // 启动清理线程
    public void startCleanup() {
        new Thread(() -> {
            while (true) {
                try {
                    ExpireEntry<K> entry = expireQueue.take();
                    cache.remove(entry.getKey());
                    System.out.println("缓存过期删除: " + entry.getKey());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}

class ExpireEntry<K> implements Delayed {
    private final K key;
    private final long expireTime;
    
    public ExpireEntry(K key, long ttl) {
        this.key = key;
        this.expireTime = System.currentTimeMillis() + ttl;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = expireTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.expireTime, ((ExpireEntry<?>) o).expireTime);
    }
    
    public K getKey() {
        return key;
    }
}
```

---

### 5.3 定时任务调度

```java
public class SimpleScheduler {
    private final DelayQueue<ScheduledTask> taskQueue = new DelayQueue<>();
    
    // 延迟执行
    public void schedule(Runnable task, long delay) {
        taskQueue.put(new ScheduledTask(task, delay, 0));
    }
    
    // 周期执行
    public void scheduleAtFixedRate(Runnable task, long initialDelay, long period) {
        taskQueue.put(new ScheduledTask(task, initialDelay, period));
    }
    
    // 启动调度器
    public void start() {
        new Thread(() -> {
            while (true) {
                try {
                    ScheduledTask task = taskQueue.take();
                    
                    // 执行任务
                    task.run();
                    
                    // 如果是周期任务，重新加入队列
                    if (task.isPeriodic()) {
                        task.updateNextRunTime();
                        taskQueue.put(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}

class ScheduledTask implements Delayed, Runnable {
    private final Runnable task;
    private long nextRunTime;
    private final long period;
    
    public ScheduledTask(Runnable task, long delay, long period) {
        this.task = task;
        this.nextRunTime = System.currentTimeMillis() + delay;
        this.period = period;
    }
    
    @Override
    public void run() {
        task.run();
    }
    
    public boolean isPeriodic() {
        return period > 0;
    }
    
    public void updateNextRunTime() {
        nextRunTime = System.currentTimeMillis() + period;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = nextRunTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.nextRunTime, ((ScheduledTask) o).nextRunTime);
    }
}
```

---

### 5.4 常见陷阱

#### 陷阱1：忘记实现compareTo

```java
// ❌ 错误：只实现getDelay
class MyTask implements Delayed {
    @Override
    public long getDelay(TimeUnit unit) {
        // ...
    }
    // 忘记实现compareTo
}

// 运行时错误：ClassCastException
// PriorityQueue需要compareTo排序
```

#### 陷阱2：getDelay实现错误

```java
// ❌ 错误：返回相对时间
@Override
public long getDelay(TimeUnit unit) {
    return unit.convert(delay, TimeUnit.MILLISECONDS);  // 错误！
}

// ✅ 正确：返回剩余时间
@Override
public long getDelay(TimeUnit unit) {
    long diff = expireTime - System.currentTimeMillis();
    return unit.convert(diff, TimeUnit.MILLISECONDS);
}
```

#### 陷阱3：已处理的任务仍在队列

```java
// 场景：订单已支付，但超时任务仍在队列
queue.put(new OrderTimeout(orderId, 30 * 60 * 1000));

// 30分钟后，任务被取出
OrderTimeout timeout = queue.take();

// ✅ 必须检查订单状态
Order order = orderRepository.findById(timeout.getOrderId());
if (order.getStatus() == UNPAID) {  // 检查状态
    order.cancel();
}
```

---

## 六、总结

### 核心要点

1. **设计思想**：
   - 优先级队列 + 延迟获取
   - Leader-Follower模式优化等待
   - 基于Delayed接口的灵活扩展

2. **关键实现**：
   - PriorityQueue自动排序
   - Leader精准等待，Follower无限期等待
   - 释放first引用防止内存泄漏

3. **适用场景**：
   - 订单超时取消
   - 缓存过期管理
   - 定时任务调度
   - 限流器（令牌桶）

4. **注意事项**：
   - 正确实现Delayed接口
   - 消费时检查业务状态
   - 无界队列，注意内存

---

**下一章**：SynchronousQueue详解 - 零容量的直接传递队列
