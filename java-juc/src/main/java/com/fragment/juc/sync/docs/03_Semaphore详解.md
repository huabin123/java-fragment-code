# 第三章：Semaphore详解 - 信号量

> **学习目标**：深入理解Semaphore的原理和使用场景

---

## 一、什么是Semaphore？

### 1.1 定义

```
Semaphore（信号量）：
一个计数信号量，用于控制同时访问某个资源的线程数量。

核心概念：
- 许可证（Permits）：可用的资源数量
- acquire()：获取许可证，如果没有则等待
- release()：释放许可证
- 公平性：可选择公平或非公平模式
```

### 1.2 应用场景

```
典型场景：

1. 限流：
   - 限制同时访问某个资源的线程数
   - 例如：数据库连接池、线程池

2. 资源池：
   - 管理有限的资源
   - 例如：打印机、文件句柄

3. 流量控制：
   - 控制请求速率
   - 例如：API限流

4. 互斥锁：
   - permits=1时，相当于互斥锁
```

---

## 二、Semaphore API

### 2.1 核心方法

```java
public class Semaphore {
    /**
     * 构造函数
     * @param permits 初始许可证数量
     */
    public Semaphore(int permits);
    
    /**
     * 构造函数（可选公平性）
     * @param permits 初始许可证数量
     * @param fair 是否公平模式
     */
    public Semaphore(int permits, boolean fair);
    
    /**
     * 获取1个许可证（阻塞）
     * - 如果有可用许可证，立即返回
     * - 如果没有，阻塞等待
     */
    public void acquire() throws InterruptedException;
    
    /**
     * 获取N个许可证
     */
    public void acquire(int permits) throws InterruptedException;
    
    /**
     * 获取许可证（不响应中断）
     */
    public void acquireUninterruptibly();
    
    /**
     * 尝试获取许可证（非阻塞）
     * @return 是否成功获取
     */
    public boolean tryAcquire();
    
    /**
     * 尝试获取N个许可证
     */
    public boolean tryAcquire(int permits);
    
    /**
     * 超时获取许可证
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) 
        throws InterruptedException;
    
    /**
     * 释放1个许可证
     */
    public void release();
    
    /**
     * 释放N个许可证
     */
    public void release(int permits);
    
    /**
     * 获取可用许可证数量
     */
    public int availablePermits();
    
    /**
     * 获取并清空所有许可证
     */
    public int drainPermits();
    
    /**
     * 查询是否有线程在等待
     */
    public boolean hasQueuedThreads();
    
    /**
     * 查询等待队列长度
     */
    public int getQueueLength();
}
```

### 2.2 标准使用模式

```java
// 模式1：基本使用
Semaphore semaphore = new Semaphore(3); // 3个许可证

semaphore.acquire(); // 获取许可证
try {
    // 访问受限资源
    useResource();
} finally {
    semaphore.release(); // 释放许可证
}

// 模式2：尝试获取
if (semaphore.tryAcquire()) {
    try {
        useResource();
    } finally {
        semaphore.release();
    }
} else {
    // 获取失败的处理
    handleFailure();
}

// 模式3：超时获取
if (semaphore.tryAcquire(10, TimeUnit.SECONDS)) {
    try {
        useResource();
    } finally {
        semaphore.release();
    }
} else {
    // 超时处理
    handleTimeout();
}
```

---

## 三、实现原理

### 3.1 内部结构

```java
// Semaphore的内部实现（简化版）

public class Semaphore {
    // 基于AQS实现
    abstract static class Sync extends AbstractQueuedSynchronizer {
        Sync(int permits) {
            setState(permits); // 设置初始许可证数量
        }
        
        final int getPermits() {
            return getState();
        }
        
        // 非公平模式：尝试获取许可证
        final int nonfairTryAcquireShared(int acquires) {
            for (;;) {
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 || 
                    compareAndSetState(available, remaining)) {
                    return remaining;
                }
            }
        }
        
        // 释放许可证
        protected final boolean tryReleaseShared(int releases) {
            for (;;) {
                int current = getState();
                int next = current + releases;
                if (compareAndSetState(current, next)) {
                    return true;
                }
            }
        }
    }
    
    // 非公平模式
    static final class NonfairSync extends Sync {
        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
    }
    
    // 公平模式
    static final class FairSync extends Sync {
        protected int tryAcquireShared(int acquires) {
            for (;;) {
                // 检查是否有前驱节点
                if (hasQueuedPredecessors()) {
                    return -1;
                }
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 || 
                    compareAndSetState(available, remaining)) {
                    return remaining;
                }
            }
        }
    }
    
    private final Sync sync;
    
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
    
    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    
    public void release() {
        sync.releaseShared(1);
    }
}
```

### 3.2 工作流程

```
Semaphore工作流程：

1. 初始化：
   Semaphore(3)
   permits = 3

2. 线程1调用acquire()：
   permits = 2, 线程1获取成功

3. 线程2调用acquire()：
   permits = 1, 线程2获取成功

4. 线程3调用acquire()：
   permits = 0, 线程3获取成功

5. 线程4调用acquire()：
   permits = 0, 线程4阻塞等待

6. 线程1调用release()：
   permits = 1, 唤醒线程4
   线程4获取成功，permits = 0

流程图：
初始：permits = 3
线程1: acquire() → permits = 2 ✅
线程2: acquire() → permits = 1 ✅
线程3: acquire() → permits = 0 ✅
线程4: acquire() → 阻塞等待 ❌
线程1: release() → permits = 1 → 唤醒线程4
线程4: 获取成功 → permits = 0 ✅
```

---

## 四、使用示例

### 4.1 资源池管理

```java
public class ResourcePool {
    private final Semaphore semaphore;
    private final List<Resource> resources;
    
    public ResourcePool(int size) {
        this.semaphore = new Semaphore(size);
        this.resources = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            resources.add(new Resource(i));
        }
    }
    
    public Resource acquire() throws InterruptedException {
        semaphore.acquire(); // 获取许可证
        return getResource();
    }
    
    public void release(Resource resource) {
        if (returnResource(resource)) {
            semaphore.release(); // 释放许可证
        }
    }
    
    private synchronized Resource getResource() {
        for (Resource resource : resources) {
            if (resource.isAvailable()) {
                resource.setAvailable(false);
                return resource;
            }
        }
        return null;
    }
    
    private synchronized boolean returnResource(Resource resource) {
        resource.setAvailable(true);
        return true;
    }
    
    static class Resource {
        private final int id;
        private boolean available = true;
        
        public Resource(int id) {
            this.id = id;
        }
        
        public int getId() {
            return id;
        }
        
        public boolean isAvailable() {
            return available;
        }
        
        public void setAvailable(boolean available) {
            this.available = available;
        }
    }
    
    public static void main(String[] args) {
        ResourcePool pool = new ResourcePool(3);
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            new Thread(() -> {
                try {
                    Resource resource = pool.acquire();
                    System.out.println("任务" + taskId + "获取资源" + 
                        resource.getId());
                    Thread.sleep(2000);
                    System.out.println("任务" + taskId + "释放资源" + 
                        resource.getId());
                    pool.release(resource);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}
```

### 4.2 限流器

```java
public class RateLimiter {
    private final Semaphore semaphore;
    
    public RateLimiter(int permitsPerSecond) {
        this.semaphore = new Semaphore(permitsPerSecond);
        
        // 定时补充许可证
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    // 补充到最大值
                    int current = semaphore.availablePermits();
                    if (current < permitsPerSecond) {
                        semaphore.release(permitsPerSecond - current);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
    
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }
    
    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }
    
    public static void main(String[] args) {
        RateLimiter limiter = new RateLimiter(5); // 每秒5个请求
        
        for (int i = 0; i < 20; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    limiter.acquire();
                    System.out.println("请求" + requestId + "通过，时间：" + 
                        System.currentTimeMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}
```

### 4.3 数据库连接池

```java
public class ConnectionPool {
    private final Semaphore semaphore;
    private final Queue<Connection> connections;
    
    public ConnectionPool(int size) {
        this.semaphore = new Semaphore(size);
        this.connections = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < size; i++) {
            connections.offer(new Connection(i));
        }
    }
    
    public Connection getConnection() throws InterruptedException {
        semaphore.acquire();
        return connections.poll();
    }
    
    public void releaseConnection(Connection connection) {
        connections.offer(connection);
        semaphore.release();
    }
    
    static class Connection {
        private final int id;
        
        public Connection(int id) {
            this.id = id;
        }
        
        public void execute(String sql) {
            System.out.println("连接" + id + "执行SQL：" + sql);
        }
    }
    
    public static void main(String[] args) {
        ConnectionPool pool = new ConnectionPool(3);
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            new Thread(() -> {
                try {
                    Connection conn = pool.getConnection();
                    System.out.println("任务" + taskId + "获取连接" + conn.id);
                    conn.execute("SELECT * FROM table");
                    Thread.sleep(1000);
                    pool.releaseConnection(conn);
                    System.out.println("任务" + taskId + "释放连接" + conn.id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}
```

---

## 五、高级用法

### 5.1 批量获取和释放

```java
public class BatchAcquireExample {
    public static void main(String[] args) throws InterruptedException {
        Semaphore semaphore = new Semaphore(10);
        
        // 批量获取5个许可证
        semaphore.acquire(5);
        System.out.println("获取5个许可证，剩余：" + 
            semaphore.availablePermits());
        
        // 批量释放5个许可证
        semaphore.release(5);
        System.out.println("释放5个许可证，剩余：" + 
            semaphore.availablePermits());
    }
}
```

### 5.2 公平模式

```java
public class FairSemaphoreExample {
    public static void main(String[] args) {
        // 公平模式：按FIFO顺序获取许可证
        Semaphore semaphore = new Semaphore(1, true);
        
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    System.out.println("线程" + threadId + "等待许可证");
                    semaphore.acquire();
                    System.out.println("线程" + threadId + "获取许可证");
                    Thread.sleep(1000);
                    semaphore.release();
                    System.out.println("线程" + threadId + "释放许可证");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}
```

### 5.3 清空许可证

```java
public class DrainPermitsExample {
    public static void main(String[] args) {
        Semaphore semaphore = new Semaphore(10);
        
        System.out.println("初始许可证：" + semaphore.availablePermits());
        
        // 清空所有许可证
        int drained = semaphore.drainPermits();
        System.out.println("清空了" + drained + "个许可证");
        System.out.println("剩余许可证：" + semaphore.availablePermits());
        
        // 恢复许可证
        semaphore.release(drained);
        System.out.println("恢复后许可证：" + semaphore.availablePermits());
    }
}
```

---

## 六、常见陷阱

### 6.1 忘记release()

```java
// ❌ 错误：忘记释放许可证
semaphore.acquire();
doWork(); // 可能抛异常
semaphore.release(); // 可能不会执行

// ✅ 正确：在finally中释放
semaphore.acquire();
try {
    doWork();
} finally {
    semaphore.release(); // 确保释放
}
```

### 6.2 重复release()

```java
// ❌ 错误：重复释放
semaphore.acquire();
try {
    doWork();
    semaphore.release(); // 错误的位置
} finally {
    semaphore.release(); // 重复释放
}

// 结果：许可证数量会超过初始值

// ✅ 正确：只在finally中释放一次
semaphore.acquire();
try {
    doWork();
} finally {
    semaphore.release();
}
```

### 6.3 acquire()和release()不匹配

```java
// ❌ 错误：获取2个，释放1个
semaphore.acquire(2);
try {
    doWork();
} finally {
    semaphore.release(); // 只释放1个
}

// ✅ 正确：获取和释放数量匹配
int permits = 2;
semaphore.acquire(permits);
try {
    doWork();
} finally {
    semaphore.release(permits);
}
```

---

## 七、性能考虑

### 7.1 公平性权衡

```
公平模式 vs 非公平模式：

非公平模式（默认）：
✅ 性能高
✅ 吞吐量大
❌ 可能饥饿

公平模式：
✅ 避免饥饿
✅ FIFO顺序
❌ 性能较低

选择建议：
- 默认使用非公平模式
- 需要严格顺序时使用公平模式
```

### 7.2 适用场景

```
✅ 适合使用：
- 限制并发数量
- 资源池管理
- 流量控制
- API限流

❌ 不适合使用：
- 简单的互斥（用Lock更简单）
- 不需要限制数量
- 需要精确的顺序控制
```

---

## 八、总结

### 8.1 核心要点

1. **定义**：信号量，控制并发数量
2. **核心方法**：acquire()获取，release()释放
3. **特点**：可重用，支持批量操作
4. **实现**：基于AQS的共享模式
5. **场景**：限流、资源池、流量控制

### 8.2 使用建议

```
✅ 必须遵守：
- 总是在finally中release()
- acquire()和release()数量匹配
- 不要重复release()

✅ 推荐做法：
- 默认使用非公平模式
- 使用tryAcquire()避免阻塞
- 监控availablePermits()
```

### 8.3 思考题

1. **Semaphore和Lock有什么区别？**
2. **如何使用Semaphore实现限流？**
3. **公平模式和非公平模式有什么区别？**
4. **为什么可以重复release()？**

---

**下一章预告**：我们将学习Exchanger（交换器）的使用。

---

**参考资料**：
- 《Java并发编程实战》第5章
- 《Java并发编程的艺术》第8章
- Semaphore API文档
