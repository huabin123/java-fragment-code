# 第五章：Phaser详解 - 分阶段器

> **学习目标**：深入理解Phaser的原理和使用场景

---

## 一、什么是Phaser？

### 1.1 定义

```
Phaser（分阶段器）：
一个可重用的同步屏障，功能类似CyclicBarrier和CountDownLatch，
但更加灵活，支持动态调整参与方数量和多阶段任务。

核心概念：
- 阶段（Phase）：任务的不同阶段
- 参与方（Party）：参与同步的线程
- 注册/注销：动态增加或减少参与方
- 终止：Phaser可以终止
```

### 1.2 与其他工具的对比

```
Phaser vs CyclicBarrier vs CountDownLatch：

CountDownLatch：
- 一次性，不可重用
- 计数减到0
- 不支持动态调整

CyclicBarrier：
- 可重用
- 固定参与方数量
- 不支持动态调整

Phaser：
- 可重用
- 支持动态调整参与方
- 支持多阶段
- 支持层次结构
- 功能最强大
```

### 1.3 应用场景

```
典型场景：

1. 多阶段任务：
   - 任务分多个阶段执行
   - 每个阶段需要同步

2. 动态参与方：
   - 参与方数量不固定
   - 可以动态增加或减少

3. 层次结构：
   - 多层Phaser
   - 父子关系

4. 迭代算法：
   - 多轮迭代
   - 每轮需要同步
```

---

## 二、Phaser API

### 2.1 核心方法

```java
public class Phaser {
    /**
     * 构造函数
     */
    public Phaser();
    
    /**
     * 构造函数（指定参与方数量）
     * @param parties 初始参与方数量
     */
    public Phaser(int parties);
    
    /**
     * 构造函数（指定父Phaser）
     * @param parent 父Phaser
     */
    public Phaser(Phaser parent);
    
    /**
     * 构造函数（指定父Phaser和参与方数量）
     */
    public Phaser(Phaser parent, int parties);
    
    /**
     * 注册一个参与方
     * @return 当前阶段号
     */
    public int register();
    
    /**
     * 批量注册参与方
     * @param parties 参与方数量
     * @return 当前阶段号
     */
    public int bulkRegister(int parties);
    
    /**
     * 到达并等待其他参与方
     * @return 到达时的阶段号
     */
    public int arriveAndAwaitAdvance();
    
    /**
     * 到达但不等待
     * @return 到达时的阶段号
     */
    public int arrive();
    
    /**
     * 到达并注销
     * @return 到达时的阶段号
     */
    public int arriveAndDeregister();
    
    /**
     * 等待前进到指定阶段
     * @param phase 阶段号
     * @return 到达时的阶段号
     */
    public int awaitAdvance(int phase);
    
    /**
     * 可中断地等待
     */
    public int awaitAdvanceInterruptibly(int phase) 
        throws InterruptedException;
    
    /**
     * 超时等待
     */
    public int awaitAdvanceInterruptibly(int phase, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException;
    
    /**
     * 强制终止
     */
    public void forceTermination();
    
    /**
     * 查询是否已终止
     */
    public boolean isTerminated();
    
    /**
     * 获取当前阶段号
     */
    public int getPhase();
    
    /**
     * 获取注册的参与方数量
     */
    public int getRegisteredParties();
    
    /**
     * 获取已到达的参与方数量
     */
    public int getArrivedParties();
    
    /**
     * 获取未到达的参与方数量
     */
    public int getUnarrivedParties();
    
    /**
     * 获取父Phaser
     */
    public Phaser getParent();
    
    /**
     * 获取根Phaser
     */
    public Phaser getRoot();
    
    /**
     * 阶段前进时的回调（可重写）
     * @param phase 当前阶段号
     * @param registeredParties 注册的参与方数量
     * @return true表示终止，false表示继续
     */
    protected boolean onAdvance(int phase, int registeredParties);
}
```

### 2.2 标准使用模式

```java
// 模式1：基本使用（类似CyclicBarrier）
int parties = 3;
Phaser phaser = new Phaser(parties);

for (int i = 0; i < parties; i++) {
    new Thread(() -> {
        // 阶段1
        doPhase1();
        phaser.arriveAndAwaitAdvance(); // 等待所有线程
        
        // 阶段2
        doPhase2();
        phaser.arriveAndAwaitAdvance();
        
        // 阶段3
        doPhase3();
        phaser.arriveAndAwaitAdvance();
    }).start();
}

// 模式2：动态注册
Phaser phaser = new Phaser(1); // 主线程

for (int i = 0; i < 5; i++) {
    phaser.register(); // 动态注册
    new Thread(() -> {
        doWork();
        phaser.arriveAndDeregister(); // 完成后注销
    }).start();
}

phaser.arriveAndDeregister(); // 主线程注销

// 模式3：自定义阶段控制
Phaser phaser = new Phaser(parties) {
    @Override
    protected boolean onAdvance(int phase, int registeredParties) {
        System.out.println("阶段" + phase + "完成");
        return phase >= 2; // 3个阶段后终止
    }
};
```

---

## 三、实现原理

### 3.1 内部结构

```java
// Phaser的内部实现（简化版）

public class Phaser {
    // 状态变量（使用long存储多个信息）
    private volatile long state;
    
    // 状态位分配：
    // [0-15]   未到达的参与方数量
    // [16-31]  注册的参与方数量
    // [32-62]  阶段号
    // [63]     终止标志
    
    public Phaser(int parties) {
        if (parties < 0) throw new IllegalArgumentException();
        this.state = ((long) parties << 16) | parties;
    }
    
    public int arriveAndAwaitAdvance() {
        return doArrive(false);
    }
    
    private int doArrive(boolean deregister) {
        for (;;) {
            long s = state;
            int phase = (int) (s >>> 32);
            int unarrived = (int) s & 0xFFFF;
            
            if (unarrived == 0) {
                // 已经前进到下一阶段
                continue;
            }
            
            int nextUnarrived = unarrived - 1;
            
            if (nextUnarrived == 0) {
                // 最后一个到达
                int nextPhase = phase + 1;
                if (onAdvance(phase, getRegisteredParties())) {
                    // 终止
                    return phase;
                }
                // 前进到下一阶段
                long nextState = ((long) nextPhase << 32) | ...;
                if (compareAndSetState(s, nextState)) {
                    releaseWaiters(phase);
                    return nextPhase;
                }
            } else {
                // 不是最后一个，等待
                if (compareAndSetState(s, ...)) {
                    return awaitAdvance(phase);
                }
            }
        }
    }
    
    protected boolean onAdvance(int phase, int registeredParties) {
        return registeredParties == 0; // 默认：没有参与方时终止
    }
}
```

---

## 四、使用示例

### 4.1 多阶段任务

```java
public class MultiPhaseTaskExample {
    public static void main(String[] args) {
        int workerCount = 3;
        Phaser phaser = new Phaser(workerCount) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("\n=== 阶段" + phase + "完成 ===\n");
                return phase >= 2; // 3个阶段后终止
            }
        };
        
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            new Thread(() -> {
                // 阶段0：准备数据
                System.out.println("工作线程" + workerId + "：准备数据");
                sleep(1000);
                phaser.arriveAndAwaitAdvance();
                
                // 阶段1：处理数据
                System.out.println("工作线程" + workerId + "：处理数据");
                sleep(1000);
                phaser.arriveAndAwaitAdvance();
                
                // 阶段2：输出结果
                System.out.println("工作线程" + workerId + "：输出结果");
                sleep(1000);
                phaser.arriveAndAwaitAdvance();
                
                System.out.println("工作线程" + workerId + "：全部完成");
            }, "Worker-" + i).start();
        }
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 4.2 动态参与方

```java
public class DynamicPartiesExample {
    public static void main(String[] args) {
        Phaser phaser = new Phaser(1); // 主线程
        
        System.out.println("主线程：启动任务");
        
        // 动态启动5个任务
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            
            // 延迟启动
            new Thread(() -> {
                sleep((long) (Math.random() * 2000));
                
                phaser.register(); // 动态注册
                System.out.println("任务" + taskId + "：已注册，当前参与方：" + 
                    phaser.getRegisteredParties());
                
                // 执行任务
                sleep(2000);
                System.out.println("任务" + taskId + "：完成");
                
                phaser.arriveAndDeregister(); // 注销
            }).start();
        }
        
        // 主线程等待所有任务完成
        phaser.arriveAndDeregister();
        
        // 等待Phaser终止
        while (!phaser.isTerminated()) {
            sleep(100);
        }
        
        System.out.println("\n主线程：所有任务完成");
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 4.3 迭代算法

```java
public class IterativeAlgorithmExample {
    public static void main(String[] args) {
        int workerCount = 4;
        int iterations = 5;
        
        Phaser phaser = new Phaser(workerCount) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("迭代" + phase + "完成");
                return phase >= iterations - 1; // 5次迭代后终止
            }
        };
        
        // 共享数据
        double[] data = new double[workerCount];
        for (int i = 0; i < workerCount; i++) {
            data[i] = Math.random() * 100;
        }
        
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            new Thread(() -> {
                while (!phaser.isTerminated()) {
                    // 计算
                    double value = data[workerId];
                    double newValue = (value + 
                        data[(workerId + 1) % workerCount]) / 2;
                    
                    System.out.println("工作线程" + workerId + 
                        "：" + value + " → " + newValue);
                    
                    // 等待所有线程计算完成
                    phaser.arriveAndAwaitAdvance();
                    
                    // 更新数据
                    data[workerId] = newValue;
                }
                
                System.out.println("工作线程" + workerId + "：最终值=" + 
                    data[workerId]);
            }, "Worker-" + i).start();
        }
    }
}
```

### 4.4 层次结构

```java
public class HierarchicalPhaserExample {
    public static void main(String[] args) {
        // 根Phaser
        Phaser root = new Phaser() {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("=== 所有组完成阶段" + phase + " ===");
                return phase >= 2;
            }
        };
        
        // 创建3个子Phaser（代表3个组）
        for (int group = 0; group < 3; group++) {
            final int groupId = group;
            Phaser groupPhaser = new Phaser(root, 3); // 每组3个线程
            
            for (int i = 0; i < 3; i++) {
                final int workerId = i;
                new Thread(() -> {
                    for (int phase = 0; phase < 3; phase++) {
                        System.out.println("组" + groupId + "-线程" + workerId + 
                            "：阶段" + phase);
                        sleep(1000);
                        groupPhaser.arriveAndAwaitAdvance();
                    }
                }, "Group" + groupId + "-Worker" + i).start();
            }
        }
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 五、高级用法

### 5.1 arrive()和arriveAndDeregister()

```java
public class ArriveMethodsExample {
    public static void main(String[] args) {
        Phaser phaser = new Phaser(3);
        
        // 线程1：到达但不等待
        new Thread(() -> {
            System.out.println("线程1：到达");
            phaser.arrive(); // 不等待
            System.out.println("线程1：继续执行");
        }).start();
        
        // 线程2：到达并等待
        new Thread(() -> {
            System.out.println("线程2：到达并等待");
            phaser.arriveAndAwaitAdvance();
            System.out.println("线程2：继续执行");
        }).start();
        
        // 线程3：到达并注销
        new Thread(() -> {
            System.out.println("线程3：到达并注销");
            phaser.arriveAndDeregister();
            System.out.println("线程3：完成");
        }).start();
    }
}
```

### 5.2 监控Phaser状态

```java
public class MonitorPhaserExample {
    public static void main(String[] args) {
        Phaser phaser = new Phaser(3);
        
        // 监控线程
        new Thread(() -> {
            while (!phaser.isTerminated()) {
                System.out.println("阶段：" + phaser.getPhase() +
                    "，注册：" + phaser.getRegisteredParties() +
                    "，已到达：" + phaser.getArrivedParties() +
                    "，未到达：" + phaser.getUnarrivedParties());
                sleep(500);
            }
        }, "Monitor").start();
        
        // 工作线程
        for (int i = 0; i < 3; i++) {
            final int workerId = i;
            new Thread(() -> {
                for (int phase = 0; phase < 3; phase++) {
                    sleep((long) (Math.random() * 2000));
                    System.out.println("工作线程" + workerId + "到达阶段" + phase);
                    phaser.arriveAndAwaitAdvance();
                }
            }, "Worker-" + i).start();
        }
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 六、常见陷阱

### 6.1 忘记注册主线程

```java
// ❌ 错误：主线程没有注册
Phaser phaser = new Phaser(); // 0个参与方

for (int i = 0; i < 3; i++) {
    phaser.register();
    new Thread(() -> {
        phaser.arriveAndAwaitAdvance();
    }).start();
}
// 主线程没有参与，Phaser立即终止

// ✅ 正确：主线程也要注册
Phaser phaser = new Phaser(1); // 主线程

for (int i = 0; i < 3; i++) {
    phaser.register();
    new Thread(() -> {
        phaser.arriveAndAwaitAdvance();
    }).start();
}

phaser.arriveAndDeregister(); // 主线程注销
```

### 6.2 参与方数量不匹配

```java
// ❌ 错误：注册了但没有到达
Phaser phaser = new Phaser(3);

// 只启动2个线程
for (int i = 0; i < 2; i++) {
    new Thread(() -> {
        phaser.arriveAndAwaitAdvance();
    }).start();
}
// 永远等待

// ✅ 正确：参与方数量匹配
int parties = 3;
Phaser phaser = new Phaser(parties);

for (int i = 0; i < parties; i++) {
    new Thread(() -> {
        phaser.arriveAndAwaitAdvance();
    }).start();
}
```

---

## 七、总结

### 7.1 核心要点

1. **定义**：分阶段器，支持多阶段和动态参与方
2. **核心方法**：arriveAndAwaitAdvance()、register()、arriveAndDeregister()
3. **特点**：可重用、动态调整、多阶段、层次结构
4. **实现**：基于CAS的状态机
5. **场景**：多阶段任务、动态参与方、迭代算法

### 7.2 对比表

| 特性 | CountDownLatch | CyclicBarrier | Phaser |
|------|----------------|---------------|--------|
| **可重用** | ❌ | ✅ | ✅ |
| **动态调整** | ❌ | ❌ | ✅ |
| **多阶段** | ❌ | ✅ | ✅ |
| **层次结构** | ❌ | ❌ | ✅ |
| **复杂度** | 简单 | 中等 | 复杂 |

### 7.3 思考题

1. **Phaser和CyclicBarrier有什么区别？**
2. **如何动态调整参与方数量？**
3. **onAdvance()方法的作用是什么？**
4. **什么时候使用Phaser？**

---

**恭喜！你已经完成了同步工具类模块的学习！** 🎉

---

**参考资料**：
- 《Java并发编程实战》第5章
- 《Java并发编程的艺术》第8章
- Phaser API文档
- JDK 7新特性
