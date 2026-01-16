# 第三章：BIO线程模型 - 从单线程到线程池的演进

> **学习目标**：理解BIO线程模型的演进过程，掌握线程池的正确使用

---

## 一、单线程模型

### 1.1 最简单的实现

```java
public class SingleThreadServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("服务器启动在端口 8080");
        
        while (true) {
            // 等待客户端连接（阻塞）
            Socket socket = serverSocket.accept();
            System.out.println("客户端连接：" + socket.getRemoteSocketAddress());
            
            // 处理客户端请求
            handleClient(socket);
        }
    }
    
    private static void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);  // 阻塞读取
            
            // 处理数据
            String request = new String(buffer, 0, len);
            String response = "Echo: " + request;
            
            // 写入响应
            out.write(response.getBytes());
            out.flush();
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

### 1.2 单线程模型的问题

```
执行流程：
客户端1连接 → 处理请求1 → 关闭连接1
                ↓
客户端2连接 → 处理请求2 → 关闭连接2
                ↓
客户端3连接 → 处理请求3 → 关闭连接3

问题：
1. ❌ 同一时间只能处理一个客户端
2. ❌ 其他客户端必须等待
3. ❌ 如果某个客户端处理时间长，影响所有后续客户端
4. ❌ 完全无法并发

适用场景：
- 学习和演示
- 绝对不能用于生产环境
```

### 1.3 性能分析

```
假设场景：
- 每个请求处理时间：100ms
- 10个并发客户端

单线程模型：
- 客户端1：100ms
- 客户端2：200ms（等待100ms + 处理100ms）
- 客户端3：300ms
- ...
- 客户端10：1000ms

平均响应时间：550ms
吞吐量：10 QPS

结论：性能极差，不可接受
```

---

## 二、一线程一连接模型

### 2.1 基本实现

```java
public class MultiThreadServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("服务器启动在端口 8080");
        
        while (true) {
            // 等待客户端连接
            Socket socket = serverSocket.accept();
            System.out.println("客户端连接：" + socket.getRemoteSocketAddress());
            
            // 为每个连接创建一个新线程
            new Thread(new ClientHandler(socket)).start();
        }
    }
    
    static class ClientHandler implements Runnable {
        private Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                
                byte[] buffer = new byte[1024];
                int len = in.read(buffer);
                
                // 处理数据
                String request = new String(buffer, 0, len);
                System.out.println("[" + Thread.currentThread().getName() + "] 收到: " + request);
                
                String response = "Echo: " + request;
                out.write(response.getBytes());
                out.flush();
                
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
```

### 2.2 优点

```
✅ 1. 支持并发
   - 多个客户端可以同时连接
   - 每个客户端独立处理
   - 互不影响

✅ 2. 响应及时
   - 新连接立即得到处理
   - 不需要等待其他连接

✅ 3. 实现简单
   - 代码直观
   - 易于理解
```

### 2.3 缺点（严重！）

```
❌ 1. 线程数量不可控
   - 1000个连接 = 1000个线程
   - 可能导致系统崩溃

❌ 2. 资源消耗大
   - 每个线程占用1MB栈空间
   - 1000个线程 = 1GB内存

❌ 3. 线程创建销毁开销大
   - 创建线程：需要分配栈空间、初始化
   - 销毁线程：需要回收资源
   - 频繁创建销毁影响性能

❌ 4. 线程上下文切换频繁
   - 线程数 > CPU核心数
   - 频繁切换，CPU利用率低

❌ 5. 容易被攻击
   - 恶意客户端大量连接
   - 耗尽服务器线程资源
   - 导致拒绝服务（DoS）
```

### 2.4 性能分析

```
假设场景：
- 每个请求处理时间：100ms
- 10个并发客户端
- CPU：4核

一线程一连接模型：
- 10个线程同时运行
- 每个客户端：100ms（理想情况）
- 实际：120-150ms（考虑上下文切换）

平均响应时间：130ms
吞吐量：~77 QPS

结论：性能提升明显，但资源消耗大
```

### 2.5 资源消耗计算

```
场景：1000个并发连接

内存消耗：
- 线程栈：1000 * 1MB = 1GB
- 堆内存：每个连接的对象（Socket、Stream等）
- 总计：~1.5-2GB

CPU消耗：
- 4核CPU
- 1000个线程竞争4个核心
- 上下文切换频繁
- CPU利用率：10-20%（大量时间在切换）

结论：资源严重浪费，性能差
```

---

## 三、线程池模型（推荐）

### 3.1 基本实现

```java
public class ThreadPoolServer {
    private static final int THREAD_POOL_SIZE = 100;
    
    public static void main(String[] args) throws IOException {
        // 创建固定大小的线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("服务器启动在端口 8080，线程池大小：" + THREAD_POOL_SIZE);
        
        while (true) {
            // 等待客户端连接
            Socket socket = serverSocket.accept();
            System.out.println("客户端连接：" + socket.getRemoteSocketAddress());
            
            // 提交任务到线程池
            executor.submit(new ClientHandler(socket));
        }
    }
    
    static class ClientHandler implements Runnable {
        private Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                
                byte[] buffer = new byte[1024];
                int len = in.read(buffer);
                
                String request = new String(buffer, 0, len);
                System.out.println("[" + Thread.currentThread().getName() + "] 处理请求");
                
                String response = "Echo: " + request;
                out.write(response.getBytes());
                out.flush();
                
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
```

### 3.2 优点

```
✅ 1. 线程数量可控
   - 固定大小的线程池
   - 不会无限制创建线程
   - 防止资源耗尽

✅ 2. 线程复用
   - 线程处理完一个任务后，继续处理下一个
   - 避免频繁创建销毁
   - 提高性能

✅ 3. 资源消耗可预测
   - 100个线程 = 100MB栈空间
   - 可以根据硬件配置调整

✅ 4. 防止DoS攻击
   - 最多100个并发处理
   - 其他连接在队列中等待
   - 不会耗尽系统资源
```

### 3.3 缺点

```
❌ 1. 仍然是阻塞I/O
   - 线程在read()时阻塞
   - 100个线程最多处理100个连接
   - 超过100个连接需要排队

❌ 2. 长连接场景性能差
   - 线程被长时间占用
   - 其他连接等待时间长

❌ 3. 无法支持海量连接
   - C10K问题无法解决
   - 需要升级到NIO
```

### 3.4 线程池大小的选择

```java
/**
 * 线程池大小计算公式
 */

// 1. CPU密集型任务
int cpuCount = Runtime.getRuntime().availableProcessors();
int threadPoolSize = cpuCount + 1;
// 原因：CPU密集型，线程数不宜过多

// 2. I/O密集型任务（BIO属于这种）
int threadPoolSize = cpuCount * 2;
// 或更激进：cpuCount * (1 + 等待时间/计算时间)

// 3. 实际建议
// 根据业务特点和硬件配置调整
// 一般：50-200之间
// 需要压测确定最优值

// 示例：
// 4核CPU
// 每个请求：90%时间在I/O，10%时间在计算
// 线程池大小 = 4 * (1 + 9) = 40
```

### 3.5 不同线程池的对比

```java
// 1. newFixedThreadPool - 固定大小（推荐）
ExecutorService executor = Executors.newFixedThreadPool(100);
// 优点：线程数固定，资源可控
// 缺点：队列无界，可能OOM

// 2. newCachedThreadPool - 缓存线程池（不推荐）
ExecutorService executor = Executors.newCachedThreadPool();
// 优点：自动扩展，按需创建
// 缺点：线程数不可控，可能耗尽资源

// 3. newSingleThreadExecutor - 单线程（特殊场景）
ExecutorService executor = Executors.newSingleThreadExecutor();
// 优点：保证顺序执行
// 缺点：无并发能力

// 4. 自定义线程池（最推荐）
ExecutorService executor = new ThreadPoolExecutor(
    50,                      // 核心线程数
    200,                     // 最大线程数
    60L,                     // 空闲线程存活时间
    TimeUnit.SECONDS,        // 时间单位
    new ArrayBlockingQueue<>(1000),  // 有界队列
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);
// 优点：完全可控，生产环境推荐
```

---

## 四、伪异步I/O模型

### 4.1 什么是伪异步I/O？

```
定义：
- 使用线程池 + 任务队列
- 将I/O操作和业务处理解耦
- 本质仍是阻塞I/O，但架构更合理

架构：
客户端连接 → 任务队列 → 线程池 → 处理任务

优点：
- 解耦I/O和业务逻辑
- 更好的资源管理
- 更灵活的任务调度
```

### 4.2 实现示例

```java
public class PseudoAsyncServer {
    private static final int CORE_POOL_SIZE = 50;
    private static final int MAX_POOL_SIZE = 200;
    private static final int QUEUE_SIZE = 1000;
    
    public static void main(String[] args) throws IOException {
        // 自定义线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_SIZE),
            new ThreadFactory() {
                private AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("Worker-" + count.incrementAndGet());
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("伪异步I/O服务器启动");
        System.out.println("核心线程数：" + CORE_POOL_SIZE);
        System.out.println("最大线程数：" + MAX_POOL_SIZE);
        System.out.println("队列大小：" + QUEUE_SIZE);
        
        while (true) {
            Socket socket = serverSocket.accept();
            
            try {
                // 提交任务到线程池
                executor.execute(new ClientHandler(socket));
            } catch (RejectedExecutionException e) {
                // 队列满，拒绝新连接
                System.err.println("服务器繁忙，拒绝连接：" + socket.getRemoteSocketAddress());
                socket.close();
            }
        }
    }
    
    static class ClientHandler implements Runnable {
        private Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            String threadName = Thread.currentThread().getName();
            System.out.println("[" + threadName + "] 开始处理连接：" + 
                             socket.getRemoteSocketAddress());
            
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                
                socket.setSoTimeout(30000);  // 30秒超时
                
                byte[] buffer = new byte[1024];
                int len = in.read(buffer);
                
                if (len == -1) {
                    return;
                }
                
                String request = new String(buffer, 0, len);
                System.out.println("[" + threadName + "] 收到请求：" + request.trim());
                
                // 模拟业务处理
                Thread.sleep(100);
                
                String response = "Echo: " + request;
                out.write(response.getBytes());
                out.flush();
                
                System.out.println("[" + threadName + "] 处理完成");
                
            } catch (SocketTimeoutException e) {
                System.err.println("[" + threadName + "] 超时");
            } catch (IOException e) {
                System.err.println("[" + threadName + "] I/O异常：" + e.getMessage());
            } catch (InterruptedException e) {
                System.err.println("[" + threadName + "] 被中断");
                Thread.currentThread().interrupt();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }
}
```

### 4.3 拒绝策略详解

```java
// 1. AbortPolicy - 抛出异常（默认）
new ThreadPoolExecutor.AbortPolicy()
// 队列满时抛出RejectedExecutionException
// 适合：需要感知任务被拒绝的场景

// 2. CallerRunsPolicy - 调用者运行（推荐）
new ThreadPoolExecutor.CallerRunsPolicy()
// 队列满时，由提交任务的线程执行
// 适合：不能丢弃任务，可以降低提交速度
// 效果：自动降速，保护系统

// 3. DiscardPolicy - 静默丢弃
new ThreadPoolExecutor.DiscardPolicy()
// 队列满时，直接丢弃任务，不抛异常
// 适合：可以容忍任务丢失的场景

// 4. DiscardOldestPolicy - 丢弃最老的任务
new ThreadPoolExecutor.DiscardOldestPolicy()
// 队列满时，丢弃队列头部的任务，加入新任务
// 适合：新任务优先级高的场景

// 5. 自定义拒绝策略
new RejectedExecutionHandler() {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // 记录日志
        System.err.println("任务被拒绝：" + r);
        
        // 关闭连接
        if (r instanceof ClientHandler) {
            ClientHandler handler = (ClientHandler) r;
            try {
                handler.socket.close();
            } catch (IOException e) {
                // 忽略
            }
        }
    }
}
```

---

## 五、线程模型对比

### 5.1 性能对比

```
测试场景：
- 1000个并发连接
- 每个请求处理100ms
- 4核CPU，8GB内存

单线程模型：
- 响应时间：50秒（平均）
- 吞吐量：20 QPS
- 内存：50MB
- CPU：5%
- 结论：不可用

一线程一连接：
- 响应时间：150ms（平均）
- 吞吐量：6666 QPS
- 内存：1.5GB
- CPU：15%
- 结论：资源消耗大，不稳定

线程池（100线程）：
- 响应时间：1秒（平均，包括排队）
- 吞吐量：1000 QPS
- 内存：200MB
- CPU：60%
- 结论：可用，推荐

NIO（对比）：
- 响应时间：100ms
- 吞吐量：10000 QPS
- 内存：100MB
- CPU：80%
- 结论：最优
```

### 5.2 适用场景对比

```
单线程模型：
✅ 学习和演示
❌ 生产环境

一线程一连接：
✅ 连接数少（< 50）
✅ 短连接、快速处理
❌ 高并发
❌ 长连接

线程池模型：
✅ 中等并发（100-1000）
✅ 短连接或中等长度连接
✅ 可预测的负载
❌ 海量连接（> 10000）
❌ 长连接 + 低频消息

伪异步I/O：
✅ 中高并发（1000-5000）
✅ 需要任务调度
✅ 需要流量控制
❌ 海量连接
```

---

## 六、线程池最佳实践

### 6.1 监控线程池状态

```java
public class MonitoredThreadPool {
    private ThreadPoolExecutor executor;
    
    public MonitoredThreadPool(int coreSize, int maxSize, int queueSize) {
        this.executor = new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueSize),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 启动监控线程
        startMonitor();
    }
    
    private void startMonitor() {
        Thread monitor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);  // 每5秒打印一次
                    
                    System.out.println("========== 线程池状态 ==========");
                    System.out.println("活跃线程数：" + executor.getActiveCount());
                    System.out.println("核心线程数：" + executor.getCorePoolSize());
                    System.out.println("最大线程数：" + executor.getMaximumPoolSize());
                    System.out.println("当前线程数：" + executor.getPoolSize());
                    System.out.println("队列大小：" + executor.getQueue().size());
                    System.out.println("已完成任务数：" + executor.getCompletedTaskCount());
                    System.out.println("总任务数：" + executor.getTaskCount());
                    System.out.println("================================");
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        monitor.setDaemon(true);
        monitor.setName("ThreadPool-Monitor");
        monitor.start();
    }
    
    public void execute(Runnable task) {
        executor.execute(task);
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

### 6.2 优雅关闭线程池

```java
public void shutdownGracefully(ExecutorService executor) {
    System.out.println("开始关闭线程池...");
    
    // 1. 停止接受新任务
    executor.shutdown();
    
    try {
        // 2. 等待已有任务完成（最多30秒）
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            System.out.println("超时，强制关闭");
            
            // 3. 强制关闭
            List<Runnable> droppedTasks = executor.shutdownNow();
            System.out.println("丢弃的任务数：" + droppedTasks.size());
            
            // 4. 再次等待
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("线程池无法关闭");
            }
        }
    } catch (InterruptedException e) {
        // 当前线程被中断，强制关闭线程池
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
    
    System.out.println("线程池已关闭");
}
```

### 6.3 处理任务异常

```java
public class SafeThreadPool {
    public static ExecutorService create(int coreSize, int maxSize) {
        ThreadFactory factory = new ThreadFactory() {
            private AtomicInteger count = new AtomicInteger(0);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("Worker-" + count.incrementAndGet());
                
                // 设置未捕获异常处理器
                thread.setUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread t, Throwable e) {
                            System.err.println("线程 " + t.getName() + " 发生异常：");
                            e.printStackTrace();
                            
                            // 记录日志、发送告警等
                        }
                    }
                );
                
                return thread;
            }
        };
        
        return new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000),
            factory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

---

## 七、总结

### 7.1 核心要点

```
1. 单线程模型
   - 最简单，但不可用
   - 仅用于学习

2. 一线程一连接
   - 支持并发，但资源消耗大
   - 线程数不可控
   - 容易被攻击

3. 线程池模型（推荐）
   - 线程数可控
   - 线程复用
   - 资源消耗可预测
   - 生产环境推荐

4. 伪异步I/O
   - 线程池 + 任务队列
   - 更好的架构
   - 流量控制

5. 线程池大小
   - CPU密集：CPU核心数 + 1
   - I/O密集：CPU核心数 * 2
   - 实际：50-200，需压测
```

### 7.2 选择建议

```
场景1：学习和演示
→ 单线程或一线程一连接

场景2：小型应用（< 100并发）
→ 线程池（50线程）

场景3：中型应用（100-1000并发）
→ 伪异步I/O（100-200线程）

场景4：大型应用（> 1000并发）
→ 必须升级到NIO或Netty
```

---

**下一章**：我们将学习BIO的实战应用和性能优化技巧。

**继续阅读**：[第四章：BIO实战与优化](./04_BIO实战与优化.md)
