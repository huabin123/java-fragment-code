# LinkedTransferQueue详解

> **本章目标**：深入理解LinkedTransferQueue的传输机制、无锁实现、与其他队列的对比及应用场景

---

## 一、为什么需要LinkedTransferQueue？

### 问题1：什么是传输队列？

#### 传输（Transfer）的概念

```java
// 普通队列：异步传递
queue.put(item);     // 放入队列就返回
// ... 稍后
item = queue.take(); // 消费者取出

// 传输队列：同步传递（可选）
queue.transfer(item); // 阻塞，直到消费者接收
// 或
queue.put(item);      // 也支持异步
```

**Transfer vs Put**：
- `put()`：放入队列就返回（异步）
- `transfer()`：等待消费者接收（同步）

---

### 问题2：LinkedTransferQueue vs SynchronousQueue

| 特性 | LinkedTransferQueue | SynchronousQueue |
|-----|---------------------|------------------|
| **容量** | 无界 | 0 |
| **异步模式** | ✅ 支持put | ❌ 不支持 |
| **同步模式** | ✅ 支持transfer | ✅ 仅此模式 |
| **性能** | 高（无锁） | 中（CAS+自旋） |
| **灵活性** | 高 | 低 |

**LinkedTransferQueue = SynchronousQueue + LinkedBlockingQueue**

---

### 问题3：LinkedTransferQueue vs LinkedBlockingQueue

| 特性 | LinkedTransferQueue | LinkedBlockingQueue |
|-----|---------------------|---------------------|
| **锁机制** | CAS无锁 | 双锁 |
| **传输模式** | ✅ 支持 | ❌ 不支持 |
| **性能** | 更高 | 高 |
| **容量** | 无界 | 可选 |

---

## 二、核心数据结构

### 2.1 类定义

```java
public class LinkedTransferQueue<E> extends AbstractQueue<E>
        implements TransferQueue<E>, java.io.Serializable {
    
    // 头节点
    transient volatile Node head;
    
    // 尾节点
    private transient volatile Node tail;
    
    // 等待匹配的节点数量
    private transient volatile int sweepVotes;
}
```

### 2.2 TransferQueue接口

```java
public interface TransferQueue<E> extends BlockingQueue<E> {
    // 传输元素，等待消费者接收
    boolean tryTransfer(E e);
    
    // 传输元素，阻塞直到被接收
    void transfer(E e) throws InterruptedException;
    
    // 传输元素，超时
    boolean tryTransfer(E e, long timeout, TimeUnit unit)
        throws InterruptedException;
    
    // 是否有等待的消费者
    boolean hasWaitingConsumer();
    
    // 等待的消费者数量
    int getWaitingConsumerCount();
}
```

### 2.3 节点结构

```java
static final class Node {
    final boolean isData;   // true=数据节点，false=请求节点
    volatile Object item;   // 数据或null
    volatile Node next;     // 下一个节点
    volatile Thread waiter; // 等待线程
}
```

**节点类型**：
- **数据节点**（isData=true）：生产者放入的数据
- **请求节点**（isData=false）：消费者的请求

---

## 三、核心操作流程

### 3.1 异步模式（put/offer）

```java
public void put(E e) {
    xfer(e, true, ASYNC, 0);
}

public boolean offer(E e) {
    xfer(e, true, ASYNC, 0);
    return true;
}
```

**流程**：
1. 创建数据节点
2. 检查队列中是否有等待的消费者
3. 如果有，直接匹配
4. 如果没有，加入队列

---

### 3.2 同步模式（transfer）

```java
public void transfer(E e) throws InterruptedException {
    if (xfer(e, true, SYNC, 0) != null) {
        Thread.interrupted();
        throw new InterruptedException();
    }
}

public boolean tryTransfer(E e) {
    return xfer(e, true, NOW, 0) == null;
}

public boolean tryTransfer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
    if (xfer(e, true, TIMED, unit.toNanos(timeout)) == null)
        return true;
    if (!Thread.interrupted())
        return false;
    throw new InterruptedException();
}
```

**模式常量**：
```java
private static final int NOW   = 0; // 立即返回
private static final int ASYNC = 1; // 异步（不等待）
private static final int SYNC  = 2; // 同步（等待匹配）
private static final int TIMED = 3; // 超时
```

---

### 3.3 核心传输方法（xfer）

```java
private E xfer(E e, boolean haveData, int how, long nanos) {
    if (haveData && (e == null))
        throw new NullPointerException();
    
    Node s = null;
    
    retry:
    for (;;) {
        // 1. 尝试匹配队头
        for (Node h = head, p = h; p != null;) {
            boolean isData = p.isData;
            Object item = p.item;
            
            // 如果模式不同（可以匹配）
            if (item != p && (item != null) == isData) {
                if (isData == haveData)  // 模式相同，跳过
                    break;
                
                // 尝试匹配
                if (p.casItem(item, e)) {
                    // 匹配成功
                    for (Node q = p; q != h;) {
                        Node n = q.next;
                        if (head == h && casHead(h, n == null ? q : n)) {
                            h.forgetNext();
                            break;
                        }
                        if ((h = head) == null ||
                            (q = h.next) == null || !q.isMatched())
                            break;
                    }
                    LockSupport.unpark(p.waiter);
                    return LinkedTransferQueue.<E>cast(item);
                }
            }
            
            Node n = p.next;
            p = (p != n) ? n : (h = head);
        }
        
        // 2. 如果是NOW模式，立即返回
        if (how == NOW)
            return e;
        
        // 3. 创建节点并加入队列
        if (s == null)
            s = new Node(e, haveData);
        
        Node pred = tryAppend(s, haveData);
        if (pred == null)
            continue retry;
        
        // 4. 如果是ASYNC模式，返回
        if (how == ASYNC)
            return null;
        
        // 5. 等待匹配
        return awaitMatch(s, pred, e, (how == TIMED), nanos);
    }
}
```

**关键流程**：

```
xfer流程图

    ┌─────────────┐
    │ 检查队头     │
    └──────┬──────┘
           │
      ┌────┴────┐
      │         │
   可匹配    不可匹配
      │         │
      ▼         ▼
 ┌────────┐ ┌──────────┐
 │直接匹配│ │创建节点   │
 │返回    │ │加入队列   │
 └────────┘ └────┬─────┘
                 │
            ┌────┴────┐
            │         │
          ASYNC     SYNC
            │         │
            ▼         ▼
       ┌────────┐ ┌──────────┐
       │立即返回│ │等待匹配   │
       └────────┘ └──────────┘
```

---

### 3.4 消费操作（take/poll）

```java
public E take() throws InterruptedException {
    E e = xfer(null, false, SYNC, 0);
    if (e != null)
        return e;
    Thread.interrupted();
    throw new InterruptedException();
}

public E poll() {
    return xfer(null, false, NOW, 0);
}

public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    E e = xfer(null, false, TIMED, unit.toNanos(timeout));
    if (e != null || !Thread.interrupted())
        return e;
    throw new InterruptedException();
}
```

---

## 四、核心设计精髓

### 4.1 双重数据结构

```java
// 队列中可能同时存在：
// 1. 数据节点（生产者放入）
// 2. 请求节点（消费者等待）

// 示例1：生产者多
[Data1] -> [Data2] -> [Data3] -> null

// 示例2：消费者多
[Request1] -> [Request2] -> null

// 示例3：混合（不应该出现，会立即匹配）
// 不会同时存在Data和Request
```

**匹配规则**：
- 数据节点 + 请求节点 → 匹配
- 数据节点 + 数据节点 → 排队
- 请求节点 + 请求节点 → 排队

---

### 4.2 松弛策略

```java
// 类似ConcurrentLinkedQueue
// head和tail允许滞后
// 减少CAS操作

// 每隔一个节点才更新head/tail
```

---

### 4.3 无锁实现

```java
// 完全基于CAS
// 无ReentrantLock
// 性能更高

// CAS操作
boolean casItem(Object cmp, Object val) {
    return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
}
```

---

## 五、应用场景与最佳实践

### 5.1 请求-响应模式

```java
public class RequestResponseSystem {
    private final TransferQueue<Request> requestQueue = 
        new LinkedTransferQueue<>();
    
    // 客户端：发送请求并等待响应
    public Response sendRequest(Request request) throws InterruptedException {
        // 使用transfer，等待服务端处理
        requestQueue.transfer(request);
        return request.getResponse();
    }
    
    // 服务端：处理请求
    public void startServer() {
        new Thread(() -> {
            while (true) {
                try {
                    Request request = requestQueue.take();
                    Response response = handleRequest(request);
                    request.setResponse(response);
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

### 5.2 事件总线

```java
public class EventBus {
    private final TransferQueue<Event> eventQueue = 
        new LinkedTransferQueue<>();
    
    // 发布事件（异步）
    public void post(Event event) {
        eventQueue.offer(event);
    }
    
    // 发布事件（同步，等待处理）
    public void postSync(Event event) throws InterruptedException {
        eventQueue.transfer(event);
    }
    
    // 订阅者
    public void subscribe(EventHandler handler) {
        new Thread(() -> {
            while (true) {
                try {
                    Event event = eventQueue.take();
                    handler.handle(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}
```

---

### 5.3 性能优化的消息队列

```java
public class HighPerformanceMessageQueue {
    private final TransferQueue<Message> queue = 
        new LinkedTransferQueue<>();
    
    // 检查是否有等待的消费者
    public void send(Message msg) throws InterruptedException {
        if (queue.hasWaitingConsumer()) {
            // 有消费者等待，直接传输
            queue.transfer(msg);
        } else {
            // 无消费者，异步放入
            queue.put(msg);
        }
    }
    
    public Message receive() throws InterruptedException {
        return queue.take();
    }
}
```

---

## 六、性能对比

### 6.1 吞吐量对比

```
10生产者 + 10消费者，100万次操作

LinkedTransferQueue:     700万 ops/s
LinkedBlockingQueue:     500万 ops/s
SynchronousQueue:        100万 ops/s

LinkedTransferQueue性能最高
```

---

### 6.2 延迟对比

```
单生产者 + 单消费者，延迟测试

LinkedTransferQueue:     ~100ns
LinkedBlockingQueue:     ~200ns
SynchronousQueue:        ~500ns

LinkedTransferQueue延迟最低
```

---

## 七、总结

### 核心要点

1. **设计思想**：
   - 传输队列，支持同步/异步
   - CAS无锁，高性能
   - 双重数据结构（数据节点+请求节点）

2. **关键实现**：
   - xfer统一处理所有操作
   - 松弛策略减少CAS
   - 自旋+阻塞优化

3. **适用场景**：
   - 请求-响应模式
   - 事件总线
   - 高性能消息队列

4. **优势**：
   - 性能最高（无锁）
   - 灵活性好（支持多种模式）
   - 功能丰富（hasWaitingConsumer等）

---

**下一章**：队列选型与对比
