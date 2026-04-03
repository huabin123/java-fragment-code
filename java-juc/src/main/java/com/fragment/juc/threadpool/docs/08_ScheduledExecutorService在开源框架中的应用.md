# ScheduledExecutorService在开源框架中的应用

## 💡 大白话精华总结

**ScheduledExecutorService解决什么问题？**
- 需要定时执行的任务（每隔一段时间做一件事）
- 需要延迟执行的任务（过一会儿再做）
- 需要周期性检查的任务（定期检查状态）

**核心应用场景（4大类）：**
```
1. 心跳检测 - 定期发送心跳，检测连接是否存活
2. 健康检查 - 定期检查服务健康状态
3. 数据同步 - 定期同步数据、刷新缓存
4. 资源清理 - 定期清理过期数据、释放资源
```

**一句话记住：**
> 只要是"定期做某事"或"延迟做某事"，都可以用ScheduledExecutorService！

---

## 📚 目录

1. [ScheduledExecutorService的通用应用场景](#1-scheduledexecutorservice的通用应用场景)
2. [Nacos中的应用](#2-nacos中的应用)
3. [Spring框架中的应用](#3-spring框架中的应用)
4. [Dubbo中的应用](#4-dubbo中的应用)
5. [Netty中的应用](#5-netty中的应用)
6. [Kafka中的应用](#6-kafka中的应用)
7. [Redis客户端中的应用](#7-redis客户端中的应用)
8. [Redisson分布式锁中的应用](#8-redisson分布式锁中的应用)
9. [Elasticsearch中的应用](#9-elasticsearch中的应用)
10. [其他框架中的应用](#10-其他框架中的应用)
11. [通用问题总结](#11-通用问题总结)
12. [实战案例](#12-实战案例)

---

## 1. ScheduledExecutorService的通用应用场景

### 1.1 四大核心场景

```
┌─────────────────────────────────────────────────────────┐
│ 1. 心跳检测（Heartbeat）                                 │
│    - 客户端定期向服务端发送心跳                          │
│    - 服务端定期检查客户端是否存活                        │
│    - 检测连接是否超时                                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 2. 健康检查（Health Check）                              │
│    - 定期检查服务是否可用                                │
│    - 定期检查数据库连接                                  │
│    - 定期检查依赖服务状态                                │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 3. 数据同步（Data Sync）                                 │
│    - 定期刷新缓存                                        │
│    - 定期同步配置                                        │
│    - 定期拉取远程数据                                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 4. 资源清理（Resource Cleanup）                          │
│    - 定期清理过期数据                                    │
│    - 定期清理临时文件                                    │
│    - 定期释放空闲连接                                    │
└─────────────────────────────────────────────────────────┘
```

### 1.2 典型使用模式

```java
// 模式1：心跳检测
scheduler.scheduleAtFixedRate(() -> {
    sendHeartbeat();  // 每隔5秒发送一次心跳
}, 0, 5, TimeUnit.SECONDS);

// 模式2：健康检查
scheduler.scheduleWithFixedDelay(() -> {
    checkHealth();  // 检查完成后延迟10秒再检查
}, 0, 10, TimeUnit.SECONDS);

// 模式3：延迟任务
scheduler.schedule(() -> {
    cleanupExpiredData();  // 30秒后清理过期数据
}, 30, TimeUnit.SECONDS);

// 模式4：超时检测
ScheduledFuture<?> future = scheduler.schedule(() -> {
    handleTimeout();  // 如果任务超时，执行超时处理
}, 60, TimeUnit.SECONDS);
// 任务完成后取消超时检测
future.cancel(false);
```

---

## 2. Nacos中的应用

### 2.1 客户端心跳机制

**场景：** 服务实例向Nacos服务端发送心跳，证明自己还活着

```java
// Nacos客户端心跳实现（简化版）
public class BeatReactor {
    private final ScheduledExecutorService executorService;
    
    public BeatReactor() {
        // 创建心跳线程池
        this.executorService = new ScheduledThreadPoolExecutor(
            1,
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    thread.setName("com.alibaba.nacos.naming.beat.sender");
                    return thread;
                }
            }
        );
    }
    
    /**
     * 添加心跳任务
     */
    public void addBeatInfo(String serviceName, BeatInfo beatInfo) {
        // 每隔5秒发送一次心跳
        executorService.scheduleAtFixedRate(
            new BeatTask(beatInfo),
            0,
            beatInfo.getPeriod(),  // 默认5秒
            TimeUnit.MILLISECONDS
        );
    }
    
    class BeatTask implements Runnable {
        private BeatInfo beatInfo;
        
        @Override
        public void run() {
            try {
                // 发送心跳到Nacos服务端
                JSONObject result = serverProxy.sendBeat(beatInfo);
                
                // 根据服务端返回，调整心跳间隔
                long nextTime = result.getLong("clientBeatInterval");
                beatInfo.setPeriod(nextTime);
                
            } catch (Exception e) {
                log.error("发送心跳失败", e);
            }
        }
    }
}
```

**流程图：**
```
客户端                                    Nacos服务端
  │                                           │
  │──────── 注册服务 ─────────────────────────>│
  │                                           │
  │<─────── 返回心跳间隔(5s) ──────────────────│
  │                                           │
  │                                           │
  │──────── 5秒后发送心跳 ─────────────────────>│
  │                                           │
  │<─────── 确认收到 ──────────────────────────│
  │                                           │
  │──────── 5秒后发送心跳 ─────────────────────>│
  │                                           │
  │<─────── 确认收到 ──────────────────────────│
  │                                           │
  │         (循环发送)                         │
```

### 2.2 服务端健康检查

**场景：** Nacos服务端定期检查服务实例是否超时

```java
// Nacos服务端健康检查（简化版）
public class HealthCheckReactor {
    private final ScheduledExecutorService executorService;
    
    public HealthCheckReactor() {
        this.executorService = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() / 2,
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    thread.setName("com.alibaba.nacos.naming.health.check");
                    return thread;
                }
            }
        );
    }
    
    /**
     * 调度健康检查任务
     */
    public void scheduleCheck(HealthCheckTask task) {
        // 每隔5秒检查一次
        executorService.scheduleWithFixedDelay(
            task,
            5000,
            5000,
            TimeUnit.MILLISECONDS
        );
    }
    
    class HealthCheckTask implements Runnable {
        @Override
        public void run() {
            try {
                // 检查所有服务实例
                for (Instance instance : instances) {
                    long lastBeat = instance.getLastBeat();
                    long now = System.currentTimeMillis();
                    
                    // 如果15秒没有心跳，标记为不健康
                    if (now - lastBeat > 15000) {
                        instance.setHealthy(false);
                        log.warn("实例不健康: {}", instance);
                    }
                    
                    // 如果30秒没有心跳，删除实例
                    if (now - lastBeat > 30000) {
                        removeInstance(instance);
                        log.warn("删除实例: {}", instance);
                    }
                }
            } catch (Exception e) {
                log.error("健康检查失败", e);
            }
        }
    }
}
```

---

## 3. Spring框架中的应用

### 3.1 @Scheduled注解的底层实现

**场景：** Spring的定时任务注解

```java
// Spring定时任务示例
@Component
public class ScheduledTasks {
    
    // 每隔5秒执行一次
    @Scheduled(fixedRate = 5000)
    public void reportCurrentTime() {
        System.out.println("当前时间: " + new Date());
    }
    
    // 上次执行完成后延迟5秒
    @Scheduled(fixedDelay = 5000)
    public void doSomething() {
        System.out.println("执行任务");
    }
    
    // Cron表达式
    @Scheduled(cron = "0 0 1 * * ?")  // 每天凌晨1点执行
    public void cleanupData() {
        System.out.println("清理数据");
    }
}
```

**底层实现（简化版）：**

```java
// Spring的ScheduledAnnotationBeanPostProcessor
public class ScheduledAnnotationBeanPostProcessor {
    private ScheduledExecutorService scheduler;
    
    @Override
    public void afterPropertiesSet() {
        // 创建线程池
        this.scheduler = Executors.newScheduledThreadPool(
            this.poolSize,
            new CustomizableThreadFactory("scheduling-")
        );
    }
    
    /**
     * 处理@Scheduled注解
     */
    protected void processScheduled(Scheduled scheduled, Method method, Object bean) {
        try {
            Runnable runnable = () -> {
                ReflectionUtils.invokeMethod(method, bean);
            };
            
            // fixedRate
            if (scheduled.fixedRate() > 0) {
                scheduler.scheduleAtFixedRate(
                    runnable,
                    scheduled.initialDelay(),
                    scheduled.fixedRate(),
                    TimeUnit.MILLISECONDS
                );
            }
            
            // fixedDelay
            else if (scheduled.fixedDelay() > 0) {
                scheduler.scheduleWithFixedDelay(
                    runnable,
                    scheduled.initialDelay(),
                    scheduled.fixedDelay(),
                    TimeUnit.MILLISECONDS
                );
            }
            
            // cron表达式
            else if (!scheduled.cron().isEmpty()) {
                // 使用CronTrigger实现
                scheduleCronTask(runnable, scheduled.cron());
            }
        } catch (Exception e) {
            throw new IllegalStateException("处理@Scheduled失败", e);
        }
    }
}
```

### 3.2 Spring Session的过期清理

**场景：** 定期清理过期的Session

```java
// Spring Session的清理任务
public class SessionCleanupTask {
    private final ScheduledExecutorService scheduler;
    
    public SessionCleanupTask() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // 每隔1分钟清理一次过期Session
        scheduler.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            1,
            1,
            TimeUnit.MINUTES
        );
    }
    
    private void cleanupExpiredSessions() {
        try {
            long now = System.currentTimeMillis();
            sessionRepository.findAll().forEach(session -> {
                if (session.isExpired(now)) {
                    sessionRepository.delete(session.getId());
                }
            });
        } catch (Exception e) {
            log.error("清理Session失败", e);
        }
    }
}
```

---

## 4. Dubbo中的应用

### 4.1 心跳检测

**场景：** Dubbo客户端和服务端之间的心跳

```java
// Dubbo心跳机制（简化版）
public class HeaderExchangeClient {
    private final ScheduledExecutorService scheduled;
    private final int heartbeat;  // 心跳间隔
    private final int heartbeatTimeout;  // 心跳超时时间
    
    public HeaderExchangeClient() {
        this.scheduled = Executors.newScheduledThreadPool(2);
        this.heartbeat = 60000;  // 60秒
        this.heartbeatTimeout = heartbeat * 3;  // 180秒
        
        // 启动心跳任务
        startHeartbeatTimer();
    }
    
    private void startHeartbeatTimer() {
        // 发送心跳
        scheduled.scheduleWithFixedDelay(
            new HeartbeatTask(),
            heartbeat,
            heartbeat,
            TimeUnit.MILLISECONDS
        );
        
        // 检测心跳超时
        scheduled.scheduleWithFixedDelay(
            new HeartbeatTimeoutTask(),
            heartbeatTimeout,
            heartbeatTimeout,
            TimeUnit.MILLISECONDS
        );
    }
    
    private class HeartbeatTask implements Runnable {
        @Override
        public void run() {
            try {
                long lastRead = getLastRead();
                long lastWrite = getLastWrite();
                long now = System.currentTimeMillis();
                
                // 如果超过心跳间隔没有读写，发送心跳
                if (now - lastRead > heartbeat || now - lastWrite > heartbeat) {
                    Request req = new Request();
                    req.setEvent(Request.HEARTBEAT_EVENT);
                    channel.send(req);
                }
            } catch (Exception e) {
                log.error("发送心跳失败", e);
            }
        }
    }
    
    private class HeartbeatTimeoutTask implements Runnable {
        @Override
        public void run() {
            try {
                long lastRead = getLastRead();
                long now = System.currentTimeMillis();
                
                // 如果超过3倍心跳时间没有读取数据，认为连接断开
                if (now - lastRead > heartbeatTimeout) {
                    log.warn("心跳超时，关闭连接");
                    channel.close();
                }
            } catch (Exception e) {
                log.error("心跳超时检测失败", e);
            }
        }
    }
}
```

### 4.2 重连机制

**场景：** 连接断开后自动重连

```java
// Dubbo重连机制
public class FailbackRegistry {
    private final ScheduledExecutorService retryExecutor;
    private final int retryPeriod = 5000;  // 5秒重试一次
    
    public FailbackRegistry() {
        this.retryExecutor = Executors.newScheduledThreadPool(1);
        
        // 启动重试任务
        this.retryExecutor.scheduleWithFixedDelay(
            new RetryTask(),
            retryPeriod,
            retryPeriod,
            TimeUnit.MILLISECONDS
        );
    }
    
    private class RetryTask implements Runnable {
        @Override
        public void run() {
            try {
                // 重试失败的注册
                retryFailedRegistered();
                
                // 重试失败的取消注册
                retryFailedUnregistered();
                
                // 重试失败的订阅
                retryFailedSubscribed();
                
                // 重试失败的取消订阅
                retryFailedUnsubscribed();
            } catch (Exception e) {
                log.error("重试失败", e);
            }
        }
    }
}
```

---

## 5. Netty中的应用

### 5.1 IdleStateHandler - 空闲检测

**场景：** 检测连接是否空闲，实现心跳

```java
// Netty的IdleStateHandler底层实现
public class IdleStateHandler extends ChannelDuplexHandler {
    private final long readerIdleTimeNanos;  // 读空闲时间
    private final long writerIdleTimeNanos;  // 写空闲时间
    private final long allIdleTimeNanos;     // 读写空闲时间
    
    private ScheduledFuture<?> readerIdleTimeout;
    private ScheduledFuture<?> writerIdleTimeout;
    private ScheduledFuture<?> allIdleTimeout;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 连接激活时，启动空闲检测
        initialize(ctx);
        super.channelActive(ctx);
    }
    
    private void initialize(ChannelHandlerContext ctx) {
        EventExecutor executor = ctx.executor();
        
        // 读空闲检测
        if (readerIdleTimeNanos > 0) {
            readerIdleTimeout = executor.schedule(
                new ReaderIdleTimeoutTask(ctx),
                readerIdleTimeNanos,
                TimeUnit.NANOSECONDS
            );
        }
        
        // 写空闲检测
        if (writerIdleTimeNanos > 0) {
            writerIdleTimeout = executor.schedule(
                new WriterIdleTimeoutTask(ctx),
                writerIdleTimeNanos,
                TimeUnit.NANOSECONDS
            );
        }
        
        // 读写空闲检测
        if (allIdleTimeNanos > 0) {
            allIdleTimeout = executor.schedule(
                new AllIdleTimeoutTask(ctx),
                allIdleTimeNanos,
                TimeUnit.NANOSECONDS
            );
        }
    }
    
    private final class ReaderIdleTimeoutTask implements Runnable {
        private final ChannelHandlerContext ctx;
        
        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }
            
            long nextDelay = readerIdleTimeNanos;
            long lastReadTime = getLastReadTime();
            long currentTime = System.nanoTime();
            
            if (currentTime - lastReadTime >= readerIdleTimeNanos) {
                // 触发读空闲事件
                ctx.fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
                nextDelay = readerIdleTimeNanos;
            } else {
                nextDelay = readerIdleTimeNanos - (currentTime - lastReadTime);
            }
            
            // 继续调度下一次检测
            readerIdleTimeout = ctx.executor().schedule(
                this,
                nextDelay,
                TimeUnit.NANOSECONDS
            );
        }
    }
}
```

**使用示例：**

```java
// 在Netty中使用IdleStateHandler
public class NettyServer {
    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        // 60秒没有读操作，触发读空闲事件
                        .addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
                        .addLast(new HeartbeatHandler());
                }
            });
    }
    
    class HeartbeatHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.READER_IDLE) {
                    // 60秒没有读到数据，关闭连接
                    log.warn("连接空闲，关闭连接");
                    ctx.close();
                }
            }
        }
    }
}
```

### 5.2 HashedWheelTimer - 时间轮定时器

**场景：** Netty的高性能定时器实现

```java
// HashedWheelTimer使用ScheduledExecutorService
public class HashedWheelTimer implements Timer {
    private final Worker worker = new Worker();
    private final Thread workerThread;
    
    public HashedWheelTimer() {
        workerThread = threadFactory.newThread(worker);
        workerThread.start();
    }
    
    private final class Worker implements Runnable {
        @Override
        public void run() {
            // 时间轮的核心逻辑
            long deadline = System.nanoTime() + tickDuration;
            
            for (;;) {
                // 等待到下一个tick
                long sleepTime = deadline - System.nanoTime();
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime / 1000000);
                    } catch (InterruptedException e) {
                        // 忽略
                    }
                }
                
                // 处理当前tick的任务
                processTasks();
                
                // 计算下一个tick的deadline
                deadline += tickDuration;
            }
        }
    }
}
```

---

## 6. Kafka中的应用

### 6.1 定期提交Offset

**场景：** Kafka消费者定期提交消费位置

```java
// Kafka消费者自动提交Offset
public class KafkaConsumer<K, V> {
    private final ScheduledExecutorService autoCommitExecutor;
    
    public KafkaConsumer(Properties properties) {
        // 如果开启自动提交
        if (config.getBoolean("enable.auto.commit")) {
            long autoCommitInterval = config.getLong("auto.commit.interval.ms");
            
            this.autoCommitExecutor = Executors.newSingleThreadScheduledExecutor();
            
            // 定期提交Offset
            this.autoCommitExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        commitSync();
                    } catch (Exception e) {
                        log.error("自动提交Offset失败", e);
                    }
                },
                autoCommitInterval,
                autoCommitInterval,
                TimeUnit.MILLISECONDS
            );
        }
    }
}
```

### 6.2 定期更新元数据

**场景：** Kafka客户端定期刷新集群元数据

```java
// Kafka元数据更新
public class Metadata {
    private final ScheduledExecutorService scheduler;
    private final long metadataExpireMs;  // 元数据过期时间
    
    public Metadata() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.metadataExpireMs = 300000;  // 5分钟
        
        // 定期检查元数据是否过期
        scheduler.scheduleAtFixedRate(
            () -> {
                if (isExpired()) {
                    requestUpdate();
                }
            },
            metadataExpireMs,
            metadataExpireMs,
            TimeUnit.MILLISECONDS
        );
    }
}
```

---

## 7. Redis客户端中的应用

### 7.1 Jedis连接池的空闲检测

**场景：** 定期检测和清理空闲连接

```java
// Jedis连接池的空闲检测
public class GenericObjectPool<T> {
    private final ScheduledExecutorService evictor;
    
    public GenericObjectPool(GenericObjectPoolConfig config) {
        // 如果配置了空闲检测
        if (config.getTimeBetweenEvictionRunsMillis() > 0) {
            this.evictor = Executors.newScheduledThreadPool(1);
            
            // 定期执行空闲检测
            this.evictor.scheduleAtFixedRate(
                new Evictor(),
                config.getTimeBetweenEvictionRunsMillis(),
                config.getTimeBetweenEvictionRunsMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }
    
    class Evictor implements Runnable {
        @Override
        public void run() {
            try {
                evict();  // 清理空闲连接
            } catch (Exception e) {
                log.error("清理空闲连接失败", e);
            }
        }
        
        private void evict() {
            // 遍历连接池
            for (PooledObject<T> pooledObject : idleObjects) {
                long idleTime = System.currentTimeMillis() - pooledObject.getLastReturnTime();
                
                // 如果空闲时间超过阈值，销毁连接
                if (idleTime > config.getMinEvictableIdleTimeMillis()) {
                    destroy(pooledObject);
                }
            }
        }
    }
}
```

### 7.2 Lettuce的心跳机制

**场景：** Lettuce（另一个Redis客户端）的连接心跳

```java
// Lettuce心跳机制
public class ConnectionWatchdog {
    private final ScheduledExecutorService reconnectScheduler;
    
    public ConnectionWatchdog() {
        this.reconnectScheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * 调度重连任务
     */
    public void scheduleReconnect() {
        reconnectScheduler.schedule(
            () -> {
                try {
                    reconnect();
                } catch (Exception e) {
                    // 重连失败，继续调度
                    scheduleReconnect();
                }
            },
            2,  // 2秒后重连
            TimeUnit.SECONDS
        );
    }
}
```

---

## 8. Redisson分布式锁中的应用

### 8.1 Watch Dog锁续期机制

**场景：** Redisson的分布式锁自动续期，防止业务执行时间过长导致锁过期

**问题背景：**
```
分布式锁的困境：
1. 设置过期时间太短 → 业务还没执行完，锁就过期了
2. 设置过期时间太长 → 如果服务宕机，锁长时间不释放

Redisson的解决方案：
Watch Dog（看门狗）机制 - 自动续期
```

### 8.2 Watch Dog核心实现

```java
// Redisson锁续期实现（简化版）
public class RedissonLock {
    private final ScheduledExecutorService scheduler;
    private final long internalLockLeaseTime = 30000;  // 锁过期时间30秒
    
    public RedissonLock() {
        // 创建定时任务线程池
        this.scheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            new ThreadFactory() {
                private AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("redisson-lock-watchdog-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
        );
    }
    
    /**
     * 加锁
     */
    public void lock() {
        // 1. 尝试获取锁
        String lockKey = "myLock";
        long threadId = Thread.currentThread().getId();
        
        // 2. 使用Lua脚本原子性加锁
        Boolean locked = tryLock(lockKey, threadId, internalLockLeaseTime);
        
        if (locked) {
            // 3. 加锁成功，启动Watch Dog
            scheduleExpirationRenewal(lockKey, threadId);
        }
    }
    
    /**
     * 调度锁续期任务（Watch Dog核心）
     */
    private void scheduleExpirationRenewal(String lockKey, long threadId) {
        // 创建续期任务
        ExpirationEntry entry = new ExpirationEntry();
        
        // 每隔 internalLockLeaseTime/3 续期一次（即每10秒续期一次）
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    // 续期：重置过期时间为30秒
                    renewExpiration(lockKey, threadId, internalLockLeaseTime);
                    log.debug("锁续期成功: {}, threadId: {}", lockKey, threadId);
                } catch (Exception e) {
                    log.error("锁续期失败", e);
                }
            },
            internalLockLeaseTime / 3,  // 初始延迟10秒
            internalLockLeaseTime / 3,  // 每隔10秒执行一次
            TimeUnit.MILLISECONDS
        );
        
        entry.setTask(task);
        expirationRenewalMap.put(getEntryName(lockKey, threadId), entry);
    }
    
    /**
     * 续期操作（Lua脚本）
     */
    private void renewExpiration(String lockKey, long threadId, long leaseTime) {
        // Lua脚本：如果锁还存在且是当前线程持有，则续期
        String script = 
            "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then " +
            "    redis.call('pexpire', KEYS[1], ARGV[2]); " +
            "    return 1; " +
            "else " +
            "    return 0; " +
            "end";
        
        redisClient.eval(script, 
            Collections.singletonList(lockKey),
            Arrays.asList(String.valueOf(threadId), String.valueOf(leaseTime))
        );
    }
    
    /**
     * 解锁
     */
    public void unlock() {
        String lockKey = "myLock";
        long threadId = Thread.currentThread().getId();
        
        // 1. 释放锁
        releaseLock(lockKey, threadId);
        
        // 2. 取消Watch Dog续期任务
        cancelExpirationRenewal(lockKey, threadId);
    }
    
    /**
     * 取消续期任务
     */
    private void cancelExpirationRenewal(String lockKey, long threadId) {
        ExpirationEntry entry = expirationRenewalMap.remove(
            getEntryName(lockKey, threadId)
        );
        
        if (entry != null && entry.getTask() != null) {
            // 取消定时任务
            entry.getTask().cancel(false);
            log.debug("取消锁续期任务: {}, threadId: {}", lockKey, threadId);
        }
    }
    
    /**
     * 续期任务信息
     */
    static class ExpirationEntry {
        private ScheduledFuture<?> task;
        
        public ScheduledFuture<?> getTask() {
            return task;
        }
        
        public void setTask(ScheduledFuture<?> task) {
            this.task = task;
        }
    }
}
```

### 8.3 Watch Dog工作流程

```
完整流程：
┌─────────────────────────────────────────────────────────┐
│ 1. 客户端调用 lock()                                     │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 2. 尝试获取锁（SET key value EX 30 NX）                 │
│    - 成功：继续                                          │
│    - 失败：自旋重试                                      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 3. 启动Watch Dog（scheduleAtFixedRate）                 │
│    - 初始延迟：10秒                                      │
│    - 执行间隔：10秒                                      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 4. 执行业务逻辑                                          │
│    - 业务执行中...                                       │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 5. Watch Dog定期续期（每10秒）                           │
│    - 检查锁是否还存在                                    │
│    - 如果存在，重置过期时间为30秒                        │
│    - 如果不存在，停止续期                                │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 6. 业务执行完成，调用 unlock()                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 7. 释放锁 + 取消Watch Dog任务                            │
│    - DEL key                                             │
│    - task.cancel(false)                                  │
└─────────────────────────────────────────────────────────┘
```

### 8.4 时间轴示意图

```
时间轴：0s----10s----20s----30s----40s----50s----60s
锁状态：[获取锁，过期时间30s]
        │
        │<─────── 业务执行中 ───────────────────────────>│
        │                                                 │
Watch Dog: 10s续期    20s续期    30s续期    40s续期    50s释放锁
           ↓          ↓          ↓          ↓          ↓
过期时间：  30s → 30s → 30s → 30s → 30s → 删除

说明：
1. 0s：获取锁，设置过期时间30秒
2. 10s：Watch Dog续期，过期时间重置为30秒（实际到期时间：40s）
3. 20s：Watch Dog续期，过期时间重置为30秒（实际到期时间：50s）
4. 30s：Watch Dog续期，过期时间重置为30秒（实际到期时间：60s）
5. 40s：Watch Dog续期，过期时间重置为30秒（实际到期时间：70s）
6. 50s：业务执行完成，释放锁，取消Watch Dog
```

### 8.5 Redisson源码分析

```java
// Redisson真实源码（org.redisson.RedissonLock）
public class RedissonLock implements RLock {
    
    // 锁过期时间（默认30秒）
    private long internalLockLeaseTime;
    
    // 续期任务Map
    private static final ConcurrentMap<String, ExpirationEntry> EXPIRATION_RENEWAL_MAP = 
        new ConcurrentHashMap<>();
    
    @Override
    public void lock() {
        try {
            lock(-1, null, false);
        } catch (InterruptedException e) {
            throw new IllegalStateException();
        }
    }
    
    private void lock(long leaseTime, TimeUnit unit, boolean interruptibly) 
            throws InterruptedException {
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(-1, leaseTime, unit, threadId);
        
        // 如果获取锁成功
        if (ttl == null) {
            return;
        }
        
        // 如果获取锁失败，订阅锁释放事件，然后自旋重试
        // ...
    }
    
    private Long tryAcquire(long waitTime, long leaseTime, TimeUnit unit, long threadId) {
        return get(tryAcquireAsync(waitTime, leaseTime, unit, threadId));
    }
    
    private <T> RFuture<Long> tryAcquireAsync(long waitTime, long leaseTime, 
                                               TimeUnit unit, long threadId) {
        // 如果没有指定leaseTime，使用默认的30秒，并启动Watch Dog
        if (leaseTime != -1) {
            return tryLockInnerAsync(waitTime, leaseTime, unit, threadId, RedisCommands.EVAL_LONG);
        }
        
        // 使用默认过期时间，并启动Watch Dog
        RFuture<Long> ttlRemainingFuture = tryLockInnerAsync(
            waitTime,
            internalLockLeaseTime,
            TimeUnit.MILLISECONDS, 
            threadId, 
            RedisCommands.EVAL_LONG
        );
        
        ttlRemainingFuture.onComplete((ttlRemaining, e) -> {
            if (e != null) {
                return;
            }
            
            // 如果获取锁成功（ttlRemaining == null），启动Watch Dog
            if (ttlRemaining == null) {
                scheduleExpirationRenewal(threadId);
            }
        });
        
        return ttlRemainingFuture;
    }
    
    /**
     * 调度续期任务（Watch Dog核心）
     */
    private void scheduleExpirationRenewal(long threadId) {
        ExpirationEntry entry = new ExpirationEntry();
        ExpirationEntry oldEntry = EXPIRATION_RENEWAL_MAP.putIfAbsent(getEntryName(), entry);
        
        if (oldEntry != null) {
            oldEntry.addThreadId(threadId);
        } else {
            entry.addThreadId(threadId);
            // 启动续期任务
            renewExpiration();
        }
    }
    
    /**
     * 续期任务
     */
    private void renewExpiration() {
        ExpirationEntry ee = EXPIRATION_RENEWAL_MAP.get(getEntryName());
        if (ee == null) {
            return;
        }
        
        // 创建定时任务
        Timeout task = commandExecutor.getConnectionManager().newTimeout(timeout -> {
            ExpirationEntry ent = EXPIRATION_RENEWAL_MAP.get(getEntryName());
            if (ent == null) {
                return;
            }
            
            Long threadId = ent.getFirstThreadId();
            if (threadId == null) {
                return;
            }
            
            // 执行续期
            RFuture<Boolean> future = renewExpirationAsync(threadId);
            future.onComplete((res, e) -> {
                if (e != null) {
                    log.error("Can't update lock " + getName() + " expiration", e);
                    return;
                }
                
                if (res) {
                    // 续期成功，继续调度下一次续期
                    renewExpiration();
                }
            });
        }, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);  // 每10秒执行一次
        
        ee.setTimeout(task);
    }
    
    /**
     * 执行续期（Lua脚本）
     */
    protected RFuture<Boolean> renewExpirationAsync(long threadId) {
        return evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
            // Lua脚本：如果锁存在，则续期
            "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                "return 1; " +
            "end; " +
            "return 0;",
            Collections.singletonList(getName()),
            internalLockLeaseTime, getLockName(threadId));
    }
    
    @Override
    public void unlock() {
        try {
            get(unlockAsync(Thread.currentThread().getId()));
        } catch (RedisException e) {
            if (e.getCause() instanceof IllegalMonitorStateException) {
                throw (IllegalMonitorStateException) e.getCause();
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public RFuture<Void> unlockAsync(long threadId) {
        RPromise<Void> result = new RedissonPromise<>();
        
        // 释放锁
        RFuture<Boolean> future = unlockInnerAsync(threadId);
        
        future.onComplete((opStatus, e) -> {
            // 取消Watch Dog
            cancelExpirationRenewal(threadId);
            
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            if (opStatus == null) {
                IllegalMonitorStateException cause = 
                    new IllegalMonitorStateException("attempt to unlock lock, not locked by current thread");
                result.tryFailure(cause);
                return;
            }
            
            result.trySuccess(null);
        });
        
        return result;
    }
    
    /**
     * 取消续期任务
     */
    void cancelExpirationRenewal(Long threadId) {
        ExpirationEntry task = EXPIRATION_RENEWAL_MAP.get(getEntryName());
        if (task == null) {
            return;
        }
        
        if (threadId != null) {
            task.removeThreadId(threadId);
        }
        
        if (threadId == null || task.hasNoThreads()) {
            Timeout timeout = task.getTimeout();
            if (timeout != null) {
                timeout.cancel();  // 取消定时任务
            }
            EXPIRATION_RENEWAL_MAP.remove(getEntryName());
        }
    }
}
```

### 8.6 Watch Dog的优势

```
1. 自动续期
   - 不需要手动设置过期时间
   - 业务执行多久，锁就持有多久
   - 避免锁提前过期

2. 防止死锁
   - 如果服务宕机，Watch Dog停止
   - 锁会在30秒后自动过期
   - 不会永久占用锁

3. 性能优化
   - 使用ScheduledExecutorService
   - 异步续期，不阻塞业务
   - 续期失败不影响业务

4. 可靠性
   - 使用Lua脚本保证原子性
   - 检查锁是否还存在
   - 只续期自己持有的锁
```

### 8.7 Watch Dog的注意事项

```
1. 只有不指定leaseTime才会启动Watch Dog
   // 会启动Watch Dog
   lock.lock();
   
   // 不会启动Watch Dog
   lock.lock(10, TimeUnit.SECONDS);

2. 续期间隔是过期时间的1/3
   - 过期时间30秒
   - 续期间隔10秒
   - 保证在过期前至少续期2次

3. 必须正确释放锁
   try {
       lock.lock();
       // 业务逻辑
   } finally {
       lock.unlock();  // 必须释放，否则Watch Dog一直续期
   }

4. Watch Dog线程是守护线程
   - 不会阻止JVM退出
   - JVM退出时，锁会在30秒后自动过期
```

### 8.8 实战案例：使用Redisson分布式锁

```java
public class RedissonLockDemo {
    private final RedissonClient redisson;
    
    public RedissonLockDemo() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        this.redisson = Redisson.create(config);
    }
    
    /**
     * 使用Watch Dog自动续期
     */
    public void processOrderWithWatchDog(String orderId) {
        RLock lock = redisson.getLock("order:lock:" + orderId);
        
        try {
            // 获取锁（会自动启动Watch Dog）
            lock.lock();
            
            // 执行业务逻辑（可能需要很长时间）
            processOrder(orderId);
            
        } finally {
            // 释放锁（会自动取消Watch Dog）
            lock.unlock();
        }
    }
    
    /**
     * 手动指定过期时间（不会启动Watch Dog）
     */
    public void processOrderWithLeaseTime(String orderId) {
        RLock lock = redisson.getLock("order:lock:" + orderId);
        
        try {
            // 获取锁，10秒后自动过期（不会启动Watch Dog）
            lock.lock(10, TimeUnit.SECONDS);
            
            // 执行业务逻辑（必须在10秒内完成）
            processOrder(orderId);
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 尝试获取锁
     */
    public void tryLockWithWatchDog(String orderId) {
        RLock lock = redisson.getLock("order:lock:" + orderId);
        
        try {
            // 尝试获取锁，等待3秒（会启动Watch Dog）
            if (lock.tryLock(3, TimeUnit.SECONDS)) {
                try {
                    processOrder(orderId);
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("获取锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void processOrder(String orderId) {
        // 模拟业务处理
        System.out.println("处理订单: " + orderId);
        try {
            Thread.sleep(5000);  // 模拟耗时操作
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 9. Elasticsearch中的应用

### 9.1 定期刷新索引

**场景：** ES定期将内存中的数据刷新到磁盘

```java
// Elasticsearch索引刷新
public class IndexService {
    private final ScheduledExecutorService scheduler;
    
    public IndexService() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // 每隔1秒刷新一次索引
        scheduler.scheduleWithFixedDelay(
            () -> {
                try {
                    refresh();
                } catch (Exception e) {
                    log.error("刷新索引失败", e);
                }
            },
            1,
            1,
            TimeUnit.SECONDS
        );
    }
    
    private void refresh() {
        // 将内存中的数据刷新到磁盘
        engine.refresh("scheduled");
    }
}
```

### 9.2 定期合并段（Segment）

**场景：** ES定期合并小的段文件

```java
// Elasticsearch段合并
public class MergeScheduler {
    private final ScheduledExecutorService scheduler;
    
    public MergeScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // 定期检查是否需要合并
        scheduler.scheduleWithFixedDelay(
            () -> {
                try {
                    maybeMerge();
                } catch (Exception e) {
                    log.error("合并段失败", e);
                }
            },
            10,
            10,
            TimeUnit.SECONDS
        );
    }
}
```

---

## 10. 其他框架中的应用

### 10.1 Tomcat - Session清理

```java
// Tomcat的Session清理
public class StandardManager {
    protected void startInternal() {
        // 启动后台线程，定期清理过期Session
        threadStart();
    }
    
    protected void threadStart() {
        thread = new Thread(new BackgroundProcessor());
        thread.setDaemon(true);
        thread.start();
    }
    
    class BackgroundProcessor implements Runnable {
        @Override
        public void run() {
            while (!threadDone) {
                try {
                    Thread.sleep(backgroundProcessorDelay * 1000L);
                    processExpires();  // 清理过期Session
                } catch (InterruptedException e) {
                    // 忽略
                }
            }
        }
    }
}
```

### 10.2 Quartz - 任务调度

```java
// Quartz使用ScheduledExecutorService
public class QuartzScheduler {
    private final ScheduledExecutorService schedExec;
    
    public QuartzScheduler() {
        this.schedExec = Executors.newScheduledThreadPool(
            threadPoolSize,
            new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("Quartz Scheduler Thread");
                    return t;
                }
            }
        );
    }
    
    public void scheduleJob(JobDetail jobDetail, Trigger trigger) {
        long delay = trigger.getNextFireTime().getTime() - System.currentTimeMillis();
        
        schedExec.schedule(
            () -> executeJob(jobDetail),
            delay,
            TimeUnit.MILLISECONDS
        );
    }
}
```

### 10.3 HikariCP - 连接池管理

```java
// HikariCP的连接池维护
public class HikariPool {
    private final ScheduledExecutorService houseKeepingExecutorService;
    
    public HikariPool() {
        this.houseKeepingExecutorService = 
            Executors.newScheduledThreadPool(1);
        
        // 定期执行连接池维护任务
        this.houseKeepingExecutorService.scheduleWithFixedDelay(
            new HouseKeeper(),
            100,
            housekeepingPeriodMs,
            TimeUnit.MILLISECONDS
        );
    }
    
    private final class HouseKeeper implements Runnable {
        @Override
        public void run() {
            // 1. 填充连接池到最小空闲数
            fillPool();
            
            // 2. 清理空闲超时的连接
            evictIdleConnections();
            
            // 3. 检测泄漏的连接
            detectLeakedConnections();
        }
    }
}
```

---

## 10. 通用问题总结

### 10.1 ScheduledExecutorService解决的核心问题

```
┌─────────────────────────────────────────────────────────┐
│ 问题1：需要定期执行某个任务                              │
│ 解决：scheduleAtFixedRate / scheduleWithFixedDelay      │
│ 示例：心跳检测、健康检查、数据同步                       │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 问题2：需要延迟执行某个任务                              │
│ 解决：schedule                                           │
│ 示例：延迟重试、超时处理、延迟关闭                       │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 问题3：需要在指定时间执行任务                            │
│ 解决：计算延迟时间 + schedule                            │
│ 示例：定时任务、定时清理、定时报表                       │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 问题4：需要检测超时                                      │
│ 解决：schedule + cancel                                  │
│ 示例：请求超时、连接超时、任务超时                       │
└─────────────────────────────────────────────────────────┘
```

### 10.2 使用场景分类

```
1. 连接管理
   ├── 心跳检测（Nacos、Dubbo、Netty）
   ├── 空闲检测（Netty、HikariCP）
   ├── 重连机制（Dubbo、Redis客户端）
   └── 超时检测（所有网络框架）

2. 数据管理
   ├── 定期刷新（Spring Cache、Elasticsearch）
   ├── 定期同步（Kafka、Nacos）
   ├── 定期清理（Session、临时文件）
   └── 定期合并（Elasticsearch、LSM Tree）

3. 健康检查
   ├── 服务健康检查（Nacos、Dubbo）
   ├── 连接健康检查（HikariCP、Jedis）
   └── 资源健康检查（内存、磁盘）

4. 任务调度
   ├── 定时任务（Spring @Scheduled、Quartz）
   ├── 延迟任务（消息队列、重试机制）
   └── 周期任务（报表生成、数据备份）
```

### 10.3 为什么选择ScheduledExecutorService？

```
对比Timer：
✅ 多线程执行（Timer是单线程）
✅ 异常隔离（一个任务异常不影响其他任务）
✅ 更精确的调度
✅ 更丰富的API

对比自己实现：
✅ 线程安全
✅ 高性能（DelayQueue + 堆）
✅ 经过充分测试
✅ 易于使用

结论：
ScheduledExecutorService是Java生态中定时任务的标准解决方案
```

---

## 11. 实战案例

### 11.1 实现一个通用的心跳管理器

```java
/**
 * 通用心跳管理器
 * 适用于任何需要心跳检测的场景
 */
public class HeartbeatManager {
    private final ScheduledExecutorService scheduler;
    private final Map<String, HeartbeatInfo> heartbeats;
    private final long heartbeatInterval;  // 心跳间隔
    private final long heartbeatTimeout;   // 心跳超时时间
    
    public HeartbeatManager(long heartbeatInterval, long heartbeatTimeout) {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.heartbeats = new ConcurrentHashMap<>();
        this.heartbeatInterval = heartbeatInterval;
        this.heartbeatTimeout = heartbeatTimeout;
        
        // 启动心跳检测
        startHeartbeatCheck();
    }
    
    /**
     * 注册心跳
     */
    public void register(String id, Runnable heartbeatTask) {
        HeartbeatInfo info = new HeartbeatInfo(id, heartbeatTask);
        heartbeats.put(id, info);
        
        // 调度心跳任务
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    heartbeatTask.run();
                    info.updateLastHeartbeat();
                } catch (Exception e) {
                    log.error("心跳任务执行失败: {}", id, e);
                }
            },
            0,
            heartbeatInterval,
            TimeUnit.MILLISECONDS
        );
        
        info.setFuture(future);
    }
    
    /**
     * 取消注册
     */
    public void unregister(String id) {
        HeartbeatInfo info = heartbeats.remove(id);
        if (info != null && info.getFuture() != null) {
            info.getFuture().cancel(false);
        }
    }
    
    /**
     * 启动心跳超时检测
     */
    private void startHeartbeatCheck() {
        scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    long now = System.currentTimeMillis();
                    heartbeats.forEach((id, info) -> {
                        if (now - info.getLastHeartbeat() > heartbeatTimeout) {
                            log.warn("心跳超时: {}", id);
                            handleTimeout(id, info);
                        }
                    });
                } catch (Exception e) {
                    log.error("心跳检测失败", e);
                }
            },
            heartbeatTimeout,
            heartbeatTimeout,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 处理超时
     */
    private void handleTimeout(String id, HeartbeatInfo info) {
        // 取消心跳任务
        unregister(id);
        
        // 触发超时回调
        if (info.getTimeoutCallback() != null) {
            info.getTimeoutCallback().run();
        }
    }
    
    /**
     * 关闭
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 心跳信息
     */
    static class HeartbeatInfo {
        private final String id;
        private final Runnable heartbeatTask;
        private volatile long lastHeartbeat;
        private ScheduledFuture<?> future;
        private Runnable timeoutCallback;
        
        public HeartbeatInfo(String id, Runnable heartbeatTask) {
            this.id = id;
            this.heartbeatTask = heartbeatTask;
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        public void updateLastHeartbeat() {
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        // Getters and Setters...
    }
}
```

**使用示例：**

```java
public class HeartbeatDemo {
    public static void main(String[] args) {
        // 创建心跳管理器：每5秒发送心跳，15秒超时
        HeartbeatManager manager = new HeartbeatManager(5000, 15000);
        
        // 注册客户端1的心跳
        manager.register("client-1", () -> {
            System.out.println("发送心跳: client-1");
            // 实际的心跳逻辑，比如发送HTTP请求
            sendHeartbeatToServer("client-1");
        });
        
        // 注册客户端2的心跳
        manager.register("client-2", () -> {
            System.out.println("发送心跳: client-2");
            sendHeartbeatToServer("client-2");
        });
        
        // 10秒后取消client-1的心跳
        try {
            Thread.sleep(10000);
            manager.unregister("client-1");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private static void sendHeartbeatToServer(String clientId) {
        // 实际的心跳发送逻辑
    }
}
```

### 11.2 实现一个健康检查器

```java
/**
 * 通用健康检查器
 */
public class HealthChecker {
    private final ScheduledExecutorService scheduler;
    private final Map<String, HealthCheckTask> tasks;
    
    public HealthChecker() {
        this.scheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
        this.tasks = new ConcurrentHashMap<>();
    }
    
    /**
     * 添加健康检查任务
     */
    public void addCheck(String name, 
                        Supplier<Boolean> checkLogic,
                        long interval,
                        TimeUnit unit) {
        HealthCheckTask task = new HealthCheckTask(name, checkLogic);
        tasks.put(name, task);
        
        // 调度检查任务
        scheduler.scheduleWithFixedDelay(
            task,
            0,
            interval,
            unit
        );
    }
    
    /**
     * 获取健康状态
     */
    public Map<String, Boolean> getHealthStatus() {
        Map<String, Boolean> status = new HashMap<>();
        tasks.forEach((name, task) -> {
            status.put(name, task.isHealthy());
        });
        return status;
    }
    
    /**
     * 健康检查任务
     */
    static class HealthCheckTask implements Runnable {
        private final String name;
        private final Supplier<Boolean> checkLogic;
        private volatile boolean healthy = true;
        
        public HealthCheckTask(String name, Supplier<Boolean> checkLogic) {
            this.name = name;
            this.checkLogic = checkLogic;
        }
        
        @Override
        public void run() {
            try {
                healthy = checkLogic.get();
                if (!healthy) {
                    log.warn("健康检查失败: {}", name);
                }
            } catch (Exception e) {
                healthy = false;
                log.error("健康检查异常: {}", name, e);
            }
        }
        
        public boolean isHealthy() {
            return healthy;
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

**使用示例：**

```java
public class HealthCheckDemo {
    public static void main(String[] args) {
        HealthChecker checker = new HealthChecker();
        
        // 检查数据库连接
        checker.addCheck("database", () -> {
            try {
                return dataSource.getConnection().isValid(1);
            } catch (Exception e) {
                return false;
            }
        }, 10, TimeUnit.SECONDS);
        
        // 检查Redis连接
        checker.addCheck("redis", () -> {
            try {
                return jedis.ping().equals("PONG");
            } catch (Exception e) {
                return false;
            }
        }, 10, TimeUnit.SECONDS);
        
        // 检查磁盘空间
        checker.addCheck("disk", () -> {
            File root = new File("/");
            long freeSpace = root.getFreeSpace();
            long totalSpace = root.getTotalSpace();
            return (double) freeSpace / totalSpace > 0.1;  // 剩余空间>10%
        }, 60, TimeUnit.SECONDS);
        
        // 获取健康状态
        Map<String, Boolean> status = checker.getHealthStatus();
        System.out.println("健康状态: " + status);
    }
}
```

---

## 12. 总结

### 12.1 核心要点

```
1. ScheduledExecutorService是Java生态中定时任务的标准解决方案

2. 主要应用场景：
   - 心跳检测（最常见）
   - 健康检查
   - 数据同步
   - 资源清理

3. 几乎所有主流框架都在使用：
   - 注册中心：Nacos、Eureka、Consul
   - RPC框架：Dubbo、gRPC
   - 网络框架：Netty
   - 消息队列：Kafka、RocketMQ
   - 缓存：Redis客户端
   - 搜索：Elasticsearch
   - 连接池：HikariCP、Druid
   - Web容器：Tomcat、Jetty

4. 核心优势：
   - 多线程执行
   - 异常隔离
   - 高性能
   - 易于使用
```

### 12.2 最佳实践

```
1. 合理设置线程数
   - 根据任务数量和频率设置
   - 避免线程过多或过少

2. 异常处理
   - 捕获所有异常
   - 避免任务停止

3. 优雅关闭
   - shutdown() + awaitTermination()
   - 必要时使用shutdownNow()

4. 监控
   - 监控任务执行情况
   - 监控队列大小
   - 监控线程池状态
```

---

## 🔗 相关代码示例

- **[HeartbeatManager.java](../project/HeartbeatManager.java)** - 通用心跳管理器
- **[HealthChecker.java](../project/HealthChecker.java)** - 通用健康检查器
- **[ScheduledExecutorServiceDemo.java](../demo/ScheduledExecutorServiceDemo.java)** - 基本使用示例

---

**参考资料：**
- Nacos源码：`com.alibaba.nacos.client.naming.beat.BeatReactor`
- Dubbo源码：`org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeClient`
- Netty源码：`io.netty.handler.timeout.IdleStateHandler`
- Spring源码：`org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor`
