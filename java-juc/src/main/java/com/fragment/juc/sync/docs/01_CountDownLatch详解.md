# 第一章：CountDownLatch详解 - 倒计时门栓

> **学习目标**：深入理解CountDownLatch的原理和使用场景

---

## 一、什么是CountDownLatch？

### 1.1 定义

```
CountDownLatch（倒计时门栓）：
一个同步辅助类，允许一个或多个线程等待，
直到其他线程完成一组操作。

核心概念：
- 计数器：初始化时设置一个计数值
- countDown()：计数器减1
- await()：等待计数器归零
- 一次性：计数器归零后不能重置
```

### 1.2 应用场景

```
典型场景：

1. 主线程等待子线程：
   - 主线程启动多个子线程
   - 等待所有子线程完成
   - 汇总结果

2. 多线程协同启动：
   - 多个线程同时开始执行
   - 类似赛跑的发令枪

3. 服务启动检查：
   - 等待多个服务初始化完成
   - 再对外提供服务
```

---

## 二、CountDownLatch API

### 2.1 核心方法

```java
public class CountDownLatch {
    /**
     * 构造函数
     * @param count 初始计数值（必须大于0）
     */
    public CountDownLatch(int count);
    
    /**
     * 等待计数器归零
     * - 如果计数器为0，立即返回
     * - 如果计数器大于0，阻塞等待
     * - 响应中断
     */
    public void await() throws InterruptedException;
    
    /**
     * 超时等待
     * @return 是否在超时前计数器归零
     */
    public boolean await(long timeout, TimeUnit unit) 
        throws InterruptedException;
    
    /**
     * 计数器减1
     * - 如果计数器变为0，唤醒所有等待的线程
     * - 如果计数器已经为0，无效果
     */
    public void countDown();
    
    /**
     * 获取当前计数值
     */
    public long getCount();
}
```

### 2.2 标准使用模式

```java
// 模式1：主线程等待子线程
CountDownLatch latch = new CountDownLatch(N);

// 启动N个子线程
for (int i = 0; i < N; i++) {
    new Thread(() -> {
        try {
            // 执行任务
            doWork();
        } finally {
            latch.countDown(); // 完成后计数减1
        }
    }).start();
}

// 主线程等待
latch.await(); // 等待所有子线程完成
System.out.println("所有任务完成");

// 模式2：多线程同时启动
CountDownLatch startSignal = new CountDownLatch(1);
CountDownLatch doneSignal = new CountDownLatch(N);

for (int i = 0; i < N; i++) {
    new Thread(() -> {
        try {
            startSignal.await(); // 等待开始信号
            doWork();
        } finally {
            doneSignal.countDown();
        }
    }).start();
}

startSignal.countDown(); // 发出开始信号
doneSignal.await(); // 等待所有线程完成
```

---

## 三、实现原理

### 3.1 内部结构

```java
// CountDownLatch的内部实现（简化版）

public class CountDownLatch {
    // 基于AQS实现
    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync(int count) {
            setState(count); // 设置初始计数值
        }
        
        int getCount() {
            return getState();
        }
        
        // 尝试获取共享锁（await()调用）
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }
        
        // 尝试释放共享锁（countDown()调用）
        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                int c = getState();
                if (c == 0) {
                    return false; // 已经为0
                }
                int nextc = c - 1;
                if (compareAndSetState(c, nextc)) {
                    return nextc == 0; // 减为0时返回true
                }
            }
        }
    }
    
    private final Sync sync;
    
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException();
        this.sync = new Sync(count);
    }
    
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    
    public void countDown() {
        sync.releaseShared(1);
    }
    
    public long getCount() {
        return sync.getCount();
    }
}
```

### 3.2 工作流程

```
CountDownLatch工作流程：

1. 初始化：
   CountDownLatch(3)
   计数器 = 3

2. 线程调用await()：
   - 检查计数器是否为0
   - 如果为0，立即返回
   - 如果不为0，加入等待队列

3. 线程调用countDown()：
   - 计数器减1（CAS操作）
   - 如果减为0，唤醒所有等待的线程

流程图：
线程A: await() → 阻塞（计数器=3）
线程B: await() → 阻塞（计数器=3）
线程1: countDown() → 计数器=2
线程2: countDown() → 计数器=1
线程3: countDown() → 计数器=0 → 唤醒A和B
```

---

## 四、使用示例

### 4.1 主线程等待子线程

```java
public class WaitForThreadsExample {
    public static void main(String[] args) throws InterruptedException {
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 启动5个工作线程
        for (int i = 0; i < threadCount; i++) {
            final int taskId = i;
            new Thread(() -> {
                try {
                    System.out.println("任务" + taskId + "开始");
                    Thread.sleep((long) (Math.random() * 3000));
                    System.out.println("任务" + taskId + "完成");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown(); // 完成后计数减1
                }
            }, "Worker-" + i).start();
        }
        
        // 主线程等待
        System.out.println("主线程等待所有任务完成...");
        latch.await();
        System.out.println("所有任务完成，主线程继续执行");
    }
}

// 输出：
// 主线程等待所有任务完成...
// 任务0开始
// 任务1开始
// 任务2开始
// 任务3开始
// 任务4开始
// 任务2完成
// 任务0完成
// 任务4完成
// 任务1完成
// 任务3完成
// 所有任务完成，主线程继续执行
```

### 4.2 多线程同时启动

```java
public class SimultaneousStartExample {
    public static void main(String[] args) throws InterruptedException {
        int runnerCount = 5;
        CountDownLatch startSignal = new CountDownLatch(1); // 开始信号
        CountDownLatch doneSignal = new CountDownLatch(runnerCount); // 完成信号
        
        // 创建5个运动员
        for (int i = 0; i < runnerCount; i++) {
            final int runnerId = i;
            new Thread(() -> {
                try {
                    System.out.println("运动员" + runnerId + "准备就绪");
                    startSignal.await(); // 等待发令枪
                    
                    long startTime = System.currentTimeMillis();
                    Thread.sleep((long) (Math.random() * 3000)); // 跑步
                    long endTime = System.currentTimeMillis();
                    
                    System.out.println("运动员" + runnerId + "完成，用时：" + 
                        (endTime - startTime) + "ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            }, "Runner-" + i).start();
        }
        
        Thread.sleep(1000); // 等待所有运动员准备
        System.out.println("\n发令枪响！\n");
        startSignal.countDown(); // 发出开始信号
        
        doneSignal.await(); // 等待所有运动员完成
        System.out.println("\n比赛结束！");
    }
}
```

### 4.3 服务启动检查

```java
public class ServiceStartupExample {
    private static class Service {
        private final String name;
        private final CountDownLatch latch;
        
        public Service(String name, CountDownLatch latch) {
            this.name = name;
            this.latch = latch;
        }
        
        public void start() {
            new Thread(() -> {
                try {
                    System.out.println(name + "正在启动...");
                    Thread.sleep((long) (Math.random() * 3000));
                    System.out.println(name + "启动完成");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        int serviceCount = 3;
        CountDownLatch latch = new CountDownLatch(serviceCount);
        
        // 启动多个服务
        new Service("数据库服务", latch).start();
        new Service("缓存服务", latch).start();
        new Service("消息队列服务", latch).start();
        
        System.out.println("等待所有服务启动...\n");
        latch.await();
        System.out.println("\n所有服务启动完成，应用程序可以对外提供服务");
    }
}
```

---

## 五、高级用法

### 5.1 超时等待

```java
public class TimeoutExample {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        
        // 启动2个快速任务
        for (int i = 0; i < 2; i++) {
            final int taskId = i;
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    System.out.println("任务" + taskId + "完成");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // 启动1个慢速任务
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 很慢
                System.out.println("任务2完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        }).start();
        
        // 超时等待
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        if (completed) {
            System.out.println("所有任务完成");
        } else {
            System.out.println("超时！还有" + latch.getCount() + "个任务未完成");
        }
    }
}
```

### 5.2 分批处理

```java
public class BatchProcessingExample {
    public static void main(String[] args) throws InterruptedException {
        int totalTasks = 100;
        int batchSize = 10;
        
        for (int batch = 0; batch < totalTasks / batchSize; batch++) {
            CountDownLatch latch = new CountDownLatch(batchSize);
            
            System.out.println("处理第" + (batch + 1) + "批任务");
            
            for (int i = 0; i < batchSize; i++) {
                final int taskId = batch * batchSize + i;
                new Thread(() -> {
                    try {
                        // 处理任务
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }
            
            latch.await(); // 等待本批次完成
            System.out.println("第" + (batch + 1) + "批任务完成\n");
        }
        
        System.out.println("所有批次完成");
    }
}
```

---

## 六、常见陷阱

### 6.1 忘记countDown()

```java
// ❌ 错误：忘记countDown()
CountDownLatch latch = new CountDownLatch(3);

new Thread(() -> {
    doWork();
    // 忘记调用latch.countDown()
}).start();

latch.await(); // 永远等待

// ✅ 正确：在finally中countDown()
new Thread(() -> {
    try {
        doWork();
    } finally {
        latch.countDown(); // 确保一定会调用
    }
}).start();
```

### 6.2 计数值设置错误

```java
// ❌ 错误：计数值与线程数不匹配
CountDownLatch latch = new CountDownLatch(5); // 计数5

// 只启动3个线程
for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        try {
            doWork();
        } finally {
            latch.countDown();
        }
    }).start();
}

latch.await(); // 永远等待（计数只能减到2）

// ✅ 正确：计数值与线程数匹配
int threadCount = 3;
CountDownLatch latch = new CountDownLatch(threadCount);

for (int i = 0; i < threadCount; i++) {
    // ...
}
```

### 6.3 重复使用

```java
// ❌ 错误：CountDownLatch不能重用
CountDownLatch latch = new CountDownLatch(3);

// 第一次使用
latch.countDown();
latch.countDown();
latch.countDown();
latch.await(); // 返回

// 第二次使用
latch.countDown(); // 无效，计数已经为0
latch.await(); // 立即返回，不会等待

// ✅ 正确：需要重用时使用CyclicBarrier
CyclicBarrier barrier = new CyclicBarrier(3);
// 可以重复使用
```

---

## 七、性能考虑

### 7.1 性能特点

```
CountDownLatch的性能：

优点：
✅ 基于AQS，性能高
✅ 无锁操作（CAS）
✅ 共享模式，多个线程可以同时await()

缺点：
❌ 一次性，不能重用
❌ 计数只能减少，不能增加
```

### 7.2 与其他方式对比

```java
// 方式1：使用join()
Thread[] threads = new Thread[N];
for (int i = 0; i < N; i++) {
    threads[i] = new Thread(() -> doWork());
    threads[i].start();
}
for (Thread t : threads) {
    t.join(); // 逐个等待
}

// 方式2：使用CountDownLatch
CountDownLatch latch = new CountDownLatch(N);
for (int i = 0; i < N; i++) {
    new Thread(() -> {
        try {
            doWork();
        } finally {
            latch.countDown();
        }
    }).start();
}
latch.await(); // 一次等待所有

// CountDownLatch的优势：
// 1. 更灵活（不需要持有Thread引用）
// 2. 更清晰（意图明确）
// 3. 支持超时
```

---

## 八、总结

### 8.1 核心要点

1. **定义**：倒计时门栓，等待计数器归零
2. **核心方法**：await()等待，countDown()减1
3. **特点**：一次性，不能重用
4. **实现**：基于AQS的共享模式
5. **场景**：主线程等待子线程、多线程同时启动

### 8.2 使用建议

```
✅ 适合使用：
- 主线程等待多个子线程完成
- 多个线程同时开始执行
- 服务启动检查
- 分批处理任务

❌ 不适合使用：
- 需要重复使用（用CyclicBarrier）
- 需要增加计数（用Semaphore）
- 只有两个线程（用join()更简单）
```

### 8.3 思考题

1. **CountDownLatch和join()有什么区别？**
2. **为什么CountDownLatch不能重用？**
3. **如何避免忘记countDown()？**
4. **CountDownLatch的实现原理是什么？**

---

**下一章预告**：我们将学习CyclicBarrier（循环栅栏）的使用。

---

**参考资料**：
- 《Java并发编程实战》第5章
- 《Java并发编程的艺术》第8章
- CountDownLatch API文档
