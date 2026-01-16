# SynchronousQueue详解

> **本章目标**：深入理解SynchronousQueue的零容量设计、直接传递机制、公平/非公平模式及在线程池中的应用

---

## 一、为什么需要SynchronousQueue？

### 问题1：什么是直接传递？

#### 传统队列的问题

```java
// 传统队列：有中间存储
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);

// 生产者
queue.put(task);  // 任务放入队列

// 消费者（稍后）
Task task = queue.take();  // 从队列取出

// 问题：
// 1. 占用内存（队列存储）
// 2. 延迟（任务在队列中等待）
// 3. 无法直接交互
```

#### 直接传递的优势

```java
// SynchronousQueue：零容量，直接传递
BlockingQueue<Task> queue = new SynchronousQueue<>();

// 生产者
queue.put(task);  // 阻塞，直到有消费者接收

// 消费者
Task task = queue.take();  // 直接从生产者手中接收

// 优势：
// 1. 零内存占用
// 2. 零延迟（直接传递）
// 3. 生产者和消费者直接交互
```

---

### 问题2：SynchronousQueue的核心特性

#### 特性1：容量为0

```java
SynchronousQueue<String> queue = new SynchronousQueue<>();

System.out.println(queue.size());              // 0
System.out.println(queue.isEmpty());           // true
System.out.println(queue.remainingCapacity()); // 0

// 无法存储元素
queue.offer("item");  // false（没有消费者等待）
```

#### 特性2：必须配对操作

```java
// 场景1：生产者先到
Thread producer = new Thread(() -> {
    queue.put("item");  // 阻塞，等待消费者
});
producer.start();

// 场景2：消费者后到
Thread consumer = new Thread(() -> {
    String item = queue.take();  // 接收，生产者解除阻塞
});
consumer.start();

// 必须配对：put必须等待take，take必须等待put
```

#### 特性3：公平/非公平模式

```java
// 非公平模式（默认）：栈结构，LIFO
SynchronousQueue<String> unfair = new SynchronousQueue<>();

// 公平模式：队列结构，FIFO
SynchronousQueue<String> fair = new SynchronousQueue<>(true);
```

---

### 问题3：SynchronousQueue的典型应用

#### 应用1：线程池（Executors.newCachedThreadPool）

```java
// JDK源码
public static ExecutorService newCachedThreadPool() {
    return new ThreadPoolExecutor(
        0,                      // 核心线程数=0
        Integer.MAX_VALUE,      // 最大线程数=无限
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>()  // 使用SynchronousQueue
    );
}

// 工作原理：
// 1. 提交任务 -> SynchronousQueue.offer()
// 2. 如果有空闲线程等待 -> 直接传递给该线程
// 3. 如果没有空闲线程 -> offer返回false -> 创建新线程
// 4. 线程执行完任务 -> 等待60秒 -> 如果没有新任务 -> 线程销毁
```

**为什么用SynchronousQueue？**
- 任务不排队，立即执行或创建新线程
- 线程数动态调整，按需创建
- 适合大量短期异步任务

#### 应用2：生产者-消费者直接交互

```java
// 场景：实时数据处理
SynchronousQueue<Data> queue = new SynchronousQueue<>();

// 生产者：采集数据
new Thread(() -> {
    while (true) {
        Data data = collectData();
        queue.put(data);  // 阻塞，直到消费者处理
    }
}).start();

// 消费者：实时处理
new Thread(() -> {
    while (true) {
        Data data = queue.take();  // 接收数据
        process(data);             // 立即处理
    }
}).start();
```

---

## 二、核心数据结构

### 2.1 类定义

```java
public class SynchronousQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    
    // 传输器（公平或非公平）
    private transient volatile Transferer<E> transferer;
    
    // 构造方法
    public SynchronousQueue() {
        this(false);  // 默认非公平
    }
    
    public SynchronousQueue(boolean fair) {
        transferer = fair ? new TransferQueue<E>() : new TransferStack<E>();
    }
}
```

### 2.2 Transferer接口

```java
abstract static class Transferer<E> {
    /**
     * 传输元素
     * @param e 如果非null，表示put操作；如果null，表示take操作
     * @param timed 是否超时
     * @param nanos 超时时间
     * @return 传输的元素，如果失败返回null
     */
    abstract E transfer(E e, boolean timed, long nanos);
}
```

### 2.3 两种实现

#### 非公平模式：TransferStack（栈）

```
TransferStack结构（LIFO）

head -> [Node3: take] -> [Node2: put] -> [Node1: take] -> null
         ↑
         栈顶（最后进入的）

新操作总是与栈顶配对
```

**特点**：
- 后进先出（LIFO）
- 新操作优先与最近的操作配对
- 性能更高（减少线程切换）

#### 公平模式：TransferQueue（队列）

```
TransferQueue结构（FIFO）

head -> [Node1: take] -> [Node2: put] -> [Node3: take] -> tail
         ↑                                                 ↑
         队头（最早进入的）                                队尾

新操作总是与队头配对
```

**特点**：
- 先进先出（FIFO）
- 新操作与最早的操作配对
- 公平性好（避免饥饿）

---

## 三、核心操作流程

### 3.1 put操作

#### 流程图

```
put(E e) 流程
    │
    ▼
┌─────────────┐
│ 检查元素非空 │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ transfer(e, ...) │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ 有消费者等待？   │
└──────┬──────────┘
       │
   ┌───┴───┐
   │       │
  Yes      No
   │       │
   ▼       ▼
┌──────┐ ┌──────────┐
│直接  │ │加入等待队列│
│传递  │ │(阻塞)    │
└──┬───┘ └────┬─────┘
   │          │
   │          ▼
   │     ┌──────────┐
   │     │等待配对   │
   │     └────┬─────┘
   │          │
   │     ┌────┴────┐
   │     │         │
   │   配对成功   超时/中断
   │     │         │
   └─────┴────┬────┘
             │
             ▼
        ┌─────────┐
        │ 返回    │
        └─────────┘
```

#### 源码分析

```java
public void put(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    
    // 调用transfer，阻塞直到配对成功
    if (transferer.transfer(e, false, 0) == null) {
        Thread.interrupted();
        throw new InterruptedException();
    }
}

// TransferStack的transfer实现（简化版）
E transfer(E e, boolean timed, long nanos) {
    SNode s = null;
    int mode = (e == null) ? REQUEST : DATA;  // REQUEST=take, DATA=put
    
    for (;;) {
        SNode h = head;
        
        // 情况1：栈空或模式相同（都是put或都是take）
        if (h == null || h.mode == mode) {
            // 超时检查
            if (timed && nanos <= 0) {
                if (h != null && h.isCancelled())
                    casHead(h, h.next);
                return null;
            }
            
            // 创建新节点并入栈
            if (s == null)
                s = new SNode(e);
            s.next = h;
            if (casHead(h, s)) {
                // 等待配对
                SNode m = awaitFulfill(s, timed, nanos);
                if (m == s) {  // 被取消
                    clean(s);
                    return null;
                }
                // 配对成功，返回
                return (E) ((mode == REQUEST) ? m.item : s.item);
            }
        }
        // 情况2：栈顶模式不同（可以配对）
        else if (!isFulfilling(h.mode)) {
            // 尝试配对
            if (h.isCancelled()) {
                casHead(h, h.next);
            } else if (casHead(h, s = snode(s, e, h, FULFILLING | mode))) {
                for (;;) {
                    SNode m = s.next;
                    if (m == null) {
                        casHead(s, null);
                        s = null;
                        break;
                    }
                    SNode mn = m.next;
                    if (m.tryMatch(s)) {
                        casHead(s, mn);
                        return (E) ((mode == REQUEST) ? m.item : s.item);
                    } else {
                        s.casNext(m, mn);
                    }
                }
            }
        }
        // 情况3：栈顶正在配对，帮助完成
        else {
            // 帮助配对
            // ...
        }
    }
}
```

**关键点**：

1. **三种情况处理**
```java
// 情况1：栈空或模式相同 -> 入栈等待
if (h == null || h.mode == mode) {
    // 创建节点，入栈，阻塞等待
}

// 情况2：栈顶模式不同 -> 尝试配对
else if (!isFulfilling(h.mode)) {
    // 与栈顶配对
}

// 情况3：栈顶正在配对 -> 帮助完成
else {
    // 帮助其他线程完成配对
}
```

2. **自旋 + 阻塞**
```java
SNode awaitFulfill(SNode s, boolean timed, long nanos) {
    long deadline = timed ? System.nanoTime() + nanos : 0L;
    Thread w = Thread.currentThread();
    
    // 自旋次数
    int spins = (shouldSpin(s) ? 
                (timed ? maxTimedSpins : maxUntimedSpins) : 0);
    
    for (;;) {
        if (w.isInterrupted())
            s.tryCancel();
        
        SNode m = s.match;
        if (m != null)  // 配对成功
            return m;
        
        if (timed) {
            nanos = deadline - System.nanoTime();
            if (nanos <= 0L) {
                s.tryCancel();
                continue;
            }
        }
        
        // 自旋
        if (spins > 0)
            spins = shouldSpin(s) ? (spins - 1) : 0;
        // 设置waiter
        else if (s.waiter == null)
            s.waiter = w;
        // 阻塞
        else if (!timed)
            LockSupport.park(this);
        else if (nanos > spinForTimeoutThreshold)
            LockSupport.parkNanos(this, nanos);
    }
}
```

---

### 3.2 take操作

```java
public E take() throws InterruptedException {
    // 调用transfer，传入null表示take操作
    E e = transferer.transfer(null, false, 0);
    if (e != null)
        return e;
    Thread.interrupted();
    throw new InterruptedException();
}
```

**与put的区别**：
- put传入元素e（非null）
- take传入null
- transfer内部根据参数判断是DATA还是REQUEST模式

---

### 3.3 非阻塞操作

```java
public boolean offer(E e) {
    if (e == null) throw new NullPointerException();
    // 立即返回，不阻塞
    return transferer.transfer(e, true, 0) != null;
}

public E poll() {
    // 立即返回，不阻塞
    return transferer.transfer(null, true, 0);
}

public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
    if (e == null) throw new NullPointerException();
    // 超时返回
    if (transferer.transfer(e, true, unit.toNanos(timeout)) != null)
        return true;
    if (!Thread.interrupted())
        return false;
    throw new InterruptedException();
}

public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    E e = transferer.transfer(null, true, unit.toNanos(timeout));
    if (e != null || !Thread.interrupted())
        return e;
    throw new InterruptedException();
}
```

---

## 四、核心设计精髓

### 4.1 零容量设计

#### 为什么容量为0？

```java
// 传统队列：中间存储
Producer -> [Queue: item1, item2, ...] -> Consumer

// SynchronousQueue：直接传递
Producer -> (hand-off) -> Consumer
```

**优势**：
1. **零内存占用**：无需存储元素
2. **零延迟**：直接传递，无等待
3. **背压自然形成**：生产者必须等待消费者

**代价**：
- 吞吐量受限（必须配对）
- 不适合生产/消费速度不匹配的场景

---

### 4.2 公平 vs 非公平

#### 非公平模式（TransferStack）

```java
// 栈结构，LIFO
// 新操作总是与栈顶配对

时间线：
T1: Thread-1 put("A") -> 入栈
T2: Thread-2 put("B") -> 入栈（栈顶）
T3: Thread-3 take()   -> 与Thread-2配对（栈顶）
T4: Thread-4 take()   -> 与Thread-1配对

// Thread-2后到，但先被配对（非公平）
```

**优势**：
- 减少线程切换（与最近的线程配对）
- 缓存友好（栈顶数据可能在缓存中）
- 性能更高

**劣势**：
- 可能导致饥饿（早到的线程可能一直等待）

#### 公平模式（TransferQueue）

```java
// 队列结构，FIFO
// 新操作总是与队头配对

时间线：
T1: Thread-1 put("A") -> 入队
T2: Thread-2 put("B") -> 入队（队尾）
T3: Thread-3 take()   -> 与Thread-1配对（队头）
T4: Thread-4 take()   -> 与Thread-2配对

// Thread-1先到，先被配对（公平）
```

**优势**：
- 公平性好（FIFO顺序）
- 避免饥饿

**劣势**：
- 可能增加线程切换
- 性能略低

---

### 4.3 自旋优化

```java
// 先自旋，后阻塞
int spins = shouldSpin(s) ? maxUntimedSpins : 0;

for (;;) {
    if (m != null)  // 配对成功
        return m;
    
    if (spins > 0)
        spins--;  // 自旋
    else
        LockSupport.park(this);  // 阻塞
}
```

**为什么自旋？**
- 如果配对很快发生，避免线程阻塞/唤醒的开销
- 适合高并发、短等待的场景

**自旋次数**：
- 单核：0（不自旋）
- 多核：32（未超时）或16（超时）

---

## 五、应用场景与最佳实践

### 5.1 线程池（CachedThreadPool）

```java
public class CachedThreadPoolExample {
    public static void main(String[] args) {
        // 使用SynchronousQueue的线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        
        // 提交100个任务
        for (int i = 0; i < 100; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("执行任务: " + taskId);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        executor.shutdown();
    }
}

// 工作原理：
// 1. 提交任务 -> offer到SynchronousQueue
// 2. 如果有空闲线程 -> 直接传递
// 3. 如果没有空闲线程 -> offer返回false -> 创建新线程
// 4. 最终可能创建100个线程（如果任务提交很快）
```

**适用场景**：
- 大量短期异步任务
- 任务执行时间短
- 任务数量不可预测

**不适用场景**：
- 任务执行时间长（会创建大量线程）
- 任务数量巨大（可能OOM）

---

### 5.2 生产者-消费者（实时处理）

```java
public class RealTimeProcessor {
    private final SynchronousQueue<Event> queue = new SynchronousQueue<>();
    
    // 生产者：采集事件
    public void collectEvent(Event event) throws InterruptedException {
        queue.put(event);  // 阻塞，直到消费者处理
        // 保证事件被立即处理
    }
    
    // 消费者：处理事件
    public void startProcessor() {
        new Thread(() -> {
            while (true) {
                try {
                    Event event = queue.take();
                    process(event);  // 实时处理
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
    
    private void process(Event event) {
        // 处理逻辑
    }
}
```

**优势**：
- 零延迟（事件立即处理）
- 背压自然形成（生产者等待消费者）
- 无内存占用

---

### 5.3 请求-响应模式

```java
public class RequestResponsePattern {
    private final SynchronousQueue<Request> requestQueue = new SynchronousQueue<>();
    private final Map<String, SynchronousQueue<Response>> responseQueues = 
        new ConcurrentHashMap<>();
    
    // 客户端：发送请求并等待响应
    public Response sendRequest(Request request) throws InterruptedException {
        String requestId = request.getId();
        SynchronousQueue<Response> responseQueue = new SynchronousQueue<>();
        responseQueues.put(requestId, responseQueue);
        
        // 发送请求
        requestQueue.put(request);
        
        // 等待响应
        Response response = responseQueue.take();
        responseQueues.remove(requestId);
        
        return response;
    }
    
    // 服务端：处理请求并返回响应
    public void startServer() {
        new Thread(() -> {
            while (true) {
                try {
                    // 接收请求
                    Request request = requestQueue.take();
                    
                    // 处理请求
                    Response response = handleRequest(request);
                    
                    // 返回响应
                    SynchronousQueue<Response> responseQueue = 
                        responseQueues.get(request.getId());
                    if (responseQueue != null) {
                        responseQueue.put(response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
    
    private Response handleRequest(Request request) {
        // 处理逻辑
        return new Response();
    }
}
```

---

### 5.4 常见陷阱

#### 陷阱1：误用为普通队列

```java
// ❌ 错误：当作普通队列使用
SynchronousQueue<Task> queue = new SynchronousQueue<>();
queue.offer(task);  // 返回false（没有消费者）

// ✅ 正确：理解其语义
queue.put(task);  // 阻塞，直到有消费者
```

#### 陷阱2：忘记容量为0

```java
// ❌ 错误：期望缓冲
SynchronousQueue<Task> queue = new SynchronousQueue<>();
for (int i = 0; i < 100; i++) {
    queue.offer(task);  // 大部分返回false
}

// ✅ 正确：使用有容量的队列
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);
```

#### 陷阱3：CachedThreadPool的OOM风险

```java
// ❌ 危险：大量长时间任务
ExecutorService executor = Executors.newCachedThreadPool();
for (int i = 0; i < 10000; i++) {
    executor.submit(() -> {
        Thread.sleep(60000);  // 长时间任务
    });
}
// 可能创建10000个线程，导致OOM

// ✅ 正确：使用固定线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
```

---

## 六、总结

### 核心要点

1. **设计思想**：
   - 零容量，直接传递
   - 必须配对操作
   - 公平/非公平模式

2. **关键实现**：
   - TransferStack（非公平，LIFO）
   - TransferQueue（公平，FIFO）
   - 自旋 + 阻塞优化

3. **适用场景**：
   - CachedThreadPool
   - 实时处理系统
   - 请求-响应模式

4. **注意事项**：
   - 容量为0，无缓冲
   - 吞吐量受限于配对速度
   - 不适合生产/消费速度不匹配

---

**下一章**：ConcurrentLinkedQueue详解 - CAS无锁队列
