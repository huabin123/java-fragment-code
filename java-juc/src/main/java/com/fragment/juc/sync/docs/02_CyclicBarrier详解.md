# 第二章：CyclicBarrier详解 - 循环栅栏

> **学习目标**：深入理解CyclicBarrier的原理和使用场景

---

## 一、什么是CyclicBarrier？

### 1.1 定义

```
CyclicBarrier（循环栅栏）：
一个同步辅助类，允许一组线程互相等待，
直到所有线程都到达某个公共屏障点。

核心概念：
- 栅栏：所有线程必须到达的同步点
- 循环：栅栏可以重复使用
- 屏障动作：所有线程到达后执行的任务
- 参与方数量：固定的线程数量
```

### 1.2 与CountDownLatch的区别

```
CyclicBarrier vs CountDownLatch：

CountDownLatch：
- 一次性，不能重用
- 主线程等待子线程
- countDown()和await()可以是不同的线程
- 计数减到0

CyclicBarrier：
- 可重用（Cyclic）
- 线程互相等待
- await()的线程就是参与方
- 计数增到N

使用场景：
CountDownLatch：主线程等待多个子线程完成
CyclicBarrier：多个线程互相等待，协同工作
```

---

## 二、CyclicBarrier API

### 2.1 核心方法

```java
public class CyclicBarrier {
    /**
     * 构造函数
     * @param parties 参与方数量
     */
    public CyclicBarrier(int parties);
    
    /**
     * 构造函数（带屏障动作）
     * @param parties 参与方数量
     * @param barrierAction 所有线程到达后执行的任务
     */
    public CyclicBarrier(int parties, Runnable barrierAction);
    
    /**
     * 等待所有线程到达栅栏
     * - 如果是最后一个到达的线程，执行屏障动作
     * - 然后唤醒所有等待的线程
     * @return 到达的顺序（最后到达返回0）
     */
    public int await() throws InterruptedException, BrokenBarrierException;
    
    /**
     * 超时等待
     * @return 到达的顺序
     */
    public int await(long timeout, TimeUnit unit) 
        throws InterruptedException, BrokenBarrierException, TimeoutException;
    
    /**
     * 重置栅栏
     * - 打破当前栅栏
     * - 开始新一轮
     */
    public void reset();
    
    /**
     * 查询栅栏是否被打破
     */
    public boolean isBroken();
    
    /**
     * 获取参与方数量
     */
    public int getParties();
    
    /**
     * 获取当前等待的线程数
     */
    public int getNumberWaiting();
}
```

### 2.2 标准使用模式

```java
// 模式1：基本使用
int parties = 3;
CyclicBarrier barrier = new CyclicBarrier(parties);

for (int i = 0; i < parties; i++) {
    new Thread(() -> {
        try {
            // 第一阶段工作
            doPhase1();
            barrier.await(); // 等待其他线程
            
            // 第二阶段工作
            doPhase2();
            barrier.await(); // 可以重复使用
            
            // 第三阶段工作
            doPhase3();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }).start();
}

// 模式2：带屏障动作
CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
    // 所有线程到达后执行
    System.out.println("所有线程已到达，执行汇总操作");
});

for (int i = 0; i < parties; i++) {
    new Thread(() -> {
        try {
            doWork();
            barrier.await(); // 等待并触发屏障动作
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }).start();
}
```

---

## 三、实现原理

### 3.1 内部结构

```java
// CyclicBarrier的内部实现（简化版）

public class CyclicBarrier {
    // 使用ReentrantLock和Condition实现
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition trip = lock.newCondition();
    
    private final int parties;           // 参与方数量
    private final Runnable barrierCommand; // 屏障动作
    private int count;                   // 当前等待的线程数
    
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }
    
    public int await() throws InterruptedException, BrokenBarrierException {
        lock.lock();
        try {
            final int index = --count; // 计数减1
            
            if (index == 0) {
                // 最后一个到达的线程
                try {
                    if (barrierCommand != null) {
                        barrierCommand.run(); // 执行屏障动作
                    }
                } finally {
                    count = parties; // 重置计数
                    trip.signalAll(); // 唤醒所有等待的线程
                }
                return 0;
            }
            
            // 不是最后一个，等待
            while (count != parties) {
                trip.await();
            }
            return index;
        } finally {
            lock.unlock();
        }
    }
}
```

### 3.2 工作流程

```
CyclicBarrier工作流程：

1. 初始化：
   CyclicBarrier(3)
   parties = 3, count = 3

2. 线程1调用await()：
   count = 2, 线程1等待

3. 线程2调用await()：
   count = 1, 线程2等待

4. 线程3调用await()：
   count = 0, 执行屏障动作
   count = 3（重置）
   唤醒线程1和线程2

5. 可以重复使用：
   线程再次调用await()
   重复上述流程

流程图：
线程1: await() → 等待（count=2）
线程2: await() → 等待（count=1）
线程3: await() → count=0 → 执行屏障动作 → 唤醒所有 → 重置count=3
```

---

## 四、使用示例

### 4.1 多线程计算

```java
public class ParallelCalculationExample {
    public static void main(String[] args) {
        int threadCount = 4;
        int[] results = new int[threadCount];
        CyclicBarrier barrier = new CyclicBarrier(threadCount, () -> {
            // 所有线程计算完成后，汇总结果
            int sum = 0;
            for (int result : results) {
                sum += result;
            }
            System.out.println("汇总结果：" + sum);
        });
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    // 计算部分结果
                    int partialResult = (index + 1) * 10;
                    results[index] = partialResult;
                    System.out.println("线程" + index + "计算完成：" + partialResult);
                    
                    // 等待其他线程
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Worker-" + i).start();
        }
    }
}

// 输出：
// 线程0计算完成：10
// 线程1计算完成：20
// 线程2计算完成：30
// 线程3计算完成：40
// 汇总结果：100
```

### 4.2 多阶段任务

```java
public class MultiPhaseTaskExample {
    public static void main(String[] args) {
        int workerCount = 3;
        CyclicBarrier barrier = new CyclicBarrier(workerCount, () -> {
            System.out.println("=== 所有线程完成本阶段 ===\n");
        });
        
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            new Thread(() -> {
                try {
                    // 阶段1：数据准备
                    System.out.println("工作线程" + workerId + "：准备数据");
                    Thread.sleep(1000);
                    barrier.await();
                    
                    // 阶段2：数据处理
                    System.out.println("工作线程" + workerId + "：处理数据");
                    Thread.sleep(1000);
                    barrier.await();
                    
                    // 阶段3：结果输出
                    System.out.println("工作线程" + workerId + "：输出结果");
                    Thread.sleep(1000);
                    barrier.await();
                    
                    System.out.println("工作线程" + workerId + "：全部完成");
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Worker-" + i).start();
        }
    }
}
```

### 4.3 模拟赛跑

```java
public class RaceSimulation {
    public static void main(String[] args) {
        int runnerCount = 5;
        CyclicBarrier barrier = new CyclicBarrier(runnerCount, () -> {
            System.out.println("\n发令枪响！比赛开始！\n");
        });
        
        for (int i = 0; i < runnerCount; i++) {
            final int runnerId = i;
            new Thread(() -> {
                try {
                    System.out.println("运动员" + runnerId + "准备就绪");
                    barrier.await(); // 等待所有运动员准备好
                    
                    // 开始跑步
                    long startTime = System.currentTimeMillis();
                    Thread.sleep((long) (Math.random() * 3000));
                    long endTime = System.currentTimeMillis();
                    
                    System.out.println("运动员" + runnerId + "到达终点，用时：" + 
                        (endTime - startTime) + "ms");
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Runner-" + i).start();
        }
    }
}
```

---

## 五、高级用法

### 5.1 获取到达顺序

```java
public class ArrivalOrderExample {
    public static void main(String[] args) {
        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties);
        
        for (int i = 0; i < parties; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 1000));
                    int arrivalIndex = barrier.await();
                    System.out.println("线程" + threadId + 
                        "到达，顺序：" + arrivalIndex);
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}

// 输出（示例）：
// 线程1到达，顺序：2
// 线程0到达，顺序：1
// 线程2到达，顺序：0（最后到达）
```

### 5.2 重置栅栏

```java
public class ResetExample {
    public static void main(String[] args) throws InterruptedException {
        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties);
        
        // 启动2个线程
        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    System.out.println("栅栏被打破：" + e.getMessage());
                }
            }).start();
        }
        
        Thread.sleep(1000);
        System.out.println("重置栅栏");
        barrier.reset(); // 打破当前栅栏，重新开始
        
        // 启动3个新线程
        for (int i = 0; i < parties; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    System.out.println("成功通过栅栏");
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
```

### 5.3 超时处理

```java
public class TimeoutExample {
    public static void main(String[] args) {
        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties);
        
        // 启动2个快速线程
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    barrier.await(2, TimeUnit.SECONDS);
                    System.out.println("线程" + threadId + "通过栅栏");
                } catch (InterruptedException | BrokenBarrierException | 
                         TimeoutException e) {
                    System.out.println("线程" + threadId + "超时：" + 
                        e.getClass().getSimpleName());
                }
            }).start();
        }
        
        // 第3个线程不启动，导致超时
    }
}
```

---

## 六、常见陷阱

### 6.1 参与方数量不匹配

```java
// ❌ 错误：参与方数量不匹配
CyclicBarrier barrier = new CyclicBarrier(3); // 需要3个线程

// 只启动2个线程
for (int i = 0; i < 2; i++) {
    new Thread(() -> {
        try {
            barrier.await(); // 永远等待
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }).start();
}

// ✅ 正确：参与方数量匹配
int parties = 3;
CyclicBarrier barrier = new CyclicBarrier(parties);

for (int i = 0; i < parties; i++) {
    // ...
}
```

### 6.2 忘记处理异常

```java
// ❌ 错误：不处理BrokenBarrierException
new Thread(() -> {
    try {
        barrier.await();
    } catch (InterruptedException e) {
        // 只处理中断异常
    }
}).start();

// ✅ 正确：处理所有异常
new Thread(() -> {
    try {
        barrier.await();
    } catch (InterruptedException | BrokenBarrierException e) {
        // 处理栅栏被打破的情况
        e.printStackTrace();
    }
}).start();
```

### 6.3 屏障动作中抛异常

```java
// ❌ 错误：屏障动作中抛异常
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    throw new RuntimeException("屏障动作失败");
});

// 会导致栅栏被打破，所有等待的线程抛BrokenBarrierException

// ✅ 正确：捕获异常
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    try {
        doSomething();
    } catch (Exception e) {
        e.printStackTrace();
    }
});
```

---

## 七、性能考虑

### 7.1 性能特点

```
CyclicBarrier的性能：

优点：
✅ 可重用，避免重复创建
✅ 基于Lock和Condition，性能好
✅ 支持屏障动作，方便汇总

缺点：
❌ 所有线程必须到达才能继续
❌ 一个线程超时会影响其他线程
```

### 7.2 适用场景

```
✅ 适合使用：
- 多线程协同工作
- 分阶段执行任务
- 需要汇总中间结果
- 需要重复使用

❌ 不适合使用：
- 主线程等待子线程（用CountDownLatch）
- 线程数量不固定
- 不需要重复使用
```

---

## 八、总结

### 8.1 核心要点

1. **定义**：循环栅栏，线程互相等待
2. **核心方法**：await()等待所有线程到达
3. **特点**：可重用（Cyclic）
4. **屏障动作**：所有线程到达后执行
5. **实现**：基于ReentrantLock和Condition

### 8.2 CyclicBarrier vs CountDownLatch

| 特性 | CyclicBarrier | CountDownLatch |
|------|---------------|----------------|
| **可重用** | ✅ 是 | ❌ 否 |
| **等待方式** | 线程互相等待 | 主线程等待子线程 |
| **计数方向** | 增加到N | 减少到0 |
| **屏障动作** | ✅ 支持 | ❌ 不支持 |
| **实现** | Lock+Condition | AQS共享模式 |

### 8.3 思考题

1. **CyclicBarrier和CountDownLatch有什么区别？**
2. **为什么CyclicBarrier可以重用？**
3. **屏障动作在哪个线程执行？**
4. **如何处理栅栏被打破的情况？**

---

**下一章预告**：我们将学习Semaphore（信号量）的使用。

---

**参考资料**：
- 《Java并发编程实战》第5章
- 《Java并发编程的艺术》第8章
- CyclicBarrier API文档
