# 第二章：BIO 线程模型演进

## 2.1 单线程模型：只能服务一个客户端

```java
// BasicSocketDemo.java → SingleThreadServer
// 最原始的 BIO：同一时刻只能服务一个客户端

ServerSocket server = new ServerSocket(8080);
while (true) {
    Socket client = server.accept();          // 等待连接1
    handleClient(client);                     // 处理连接1（期间连接2无法被接受）
    // handleClient 返回后，才能处理下一个连接
}

// 问题：handleClient() 如果耗时3秒，这3秒内其他所有客户端都只能等待
// 适用：只有顺序处理需求的场景（文件批处理等），实际很少用于网络服务
```

---

## 2.2 一连接一线程：BIO 的标准模式

```java
// MultiThreadServerDemo.java → MultiThreadServer

ServerSocket server = new ServerSocket(8080);
while (true) {
    Socket client = server.accept();
    // 为每个连接创建一个新线程处理
    new Thread(() -> handleClient(client)).start();
}

// 解决了：多个客户端可以同时被服务
// 引入了：每次 new Thread() 的开销
//   - 线程创建：需要向 OS 申请线程资源，约 1ms
//   - 线程销毁：需要 OS 回收资源
//   - 内存占用：每个线程默认 512KB~1MB 的栈空间

// 实测（MultiThreadServerDemo.java → benchmarkThreadCreation）：
// 创建 1000 个线程：约 1200ms
// 创建 10000 个线程：OOM（线程栈耗尽堆外内存）
```

---

## 2.3 线程池模型：BIO 的最优方案

```java
// ThreadPoolServerDemo.java → ThreadPoolServer

// 使用线程池复用线程，避免频繁创建销毁
ExecutorService pool = new ThreadPoolExecutor(
    20,               // corePoolSize：核心线程数
    200,              // maximumPoolSize：最大线程数
    60L,              // keepAliveTime
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),  // 任务队列
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用者线程执行
);

ServerSocket server = new ServerSocket(8080);
while (true) {
    Socket client = server.accept();
    pool.submit(() -> handleClient(client));  // 提交到线程池
}
```

**三种 BIO 模型的对比**：

```
模型              最大并发连接   线程开销   代码复杂度   适用场景
──────────────────────────────────────────────────────────────
单线程            1             极低       最简单       批处理/测试
一连接一线程       受内存限制     高         简单         小型内网工具
线程池            maximumPoolSize 中等      中等         生产 BIO 服务
```

---

## 2.4 线程池模型的参数调优

```java
// ThreadPoolServerDemo.java → optimizedThreadPoolServer()

// 线程数估算公式（I/O 密集型服务）：
// 最优线程数 = CPU 核心数 × (1 + 等待时间/计算时间)
// 
// 例：4 核 CPU，每个请求 80% 时间在 I/O（等待数据库/网络），20% 时间在计算
// 最优线程数 = 4 × (1 + 0.8/0.2) = 4 × 5 = 20

int cpuCores = Runtime.getRuntime().availableProcessors();
double ioRatio = 0.8;   // I/O 时间占比（根据实际业务测量）
int optimalThreads = (int) (cpuCores * (1 + ioRatio / (1 - ioRatio)));

ExecutorService pool = new ThreadPoolExecutor(
    optimalThreads,
    optimalThreads * 2,
    60L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(500),    // 有界队列，防止任务积压撑爆内存
    new ThreadFactory() {             // 有意义的线程名，便于排查
        private final AtomicInteger n = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "bio-worker-" + n.getAndIncrement());
        }
    },
    (r, executor) -> {                // 拒绝策略：记录日志 + 返回 503
        log.warn("线程池已满，拒绝请求");
        // 可以给客户端发 "SERVER_BUSY" 响应
    }
);
```

---

## 2.5 BIO 的瓶颈：即使线程池也无法突破

```
BIO 线程池的根本瓶颈：
  每个连接必须占用一个线程（即使在等待数据）

场景：1000 个长连接客户端，每秒发 1 条消息
  线程池必须保持 1000 个线程
  其中 990+ 个线程大部分时间在阻塞等待 read()
  CPU 在 1000 个线程间不停切换，开销巨大

解决方案：NIO + Selector（一个线程管理所有连接）
```

---

## 2.6 本章总结

- **三种 BIO 线程模型**：单线程（串行）→ 一连接一线程（并发但开销大）→ 线程池（复用线程，生产推荐）
- **线程池是 BIO 最优解**：避免了频繁创建销毁线程，但仍有"连接占用线程"的根本限制
- **线程数估算**：`CPU核 × (1 + I/O等待时间/计算时间)`，I/O 密集型一般配 CPU 核的 5~10 倍
- **有界队列必选**：`LinkedBlockingQueue` 默认无界，任务积压会撑爆内存，改用 `ArrayBlockingQueue`

> **本章对应演示代码**：`MultiThreadServerDemo.java`（多线程 BIO）、`ThreadPoolServerDemo.java`（线程池 BIO、参数调优、压测）

**继续阅读**：[03_BIO核心API详解.md](./03_BIO核心API详解.md)
