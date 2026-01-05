# Lost Wakeup（丢失唤醒）问题详解

## 📝 什么是 Lost Wakeup？

**Lost Wakeup（丢失唤醒）** 是多线程编程中的一个经典陷阱，指的是：`notify()`在`wait()`之前执行，导致唤醒信号丢失，等待线程永远无法被唤醒的问题。

## 🔍 问题场景

### 时间线演示

```
时间点    线程A（等待线程）              线程B（通知线程）
------    ------------------            ------------------
T1        检查条件（ready = false）
T2                                      修改条件（ready = true）
T3                                      调用 notify() ← 唤醒信号发出
T4        调用 wait()                   ← 但此时没有线程在等待！
T5        永远等待...                   唤醒信号已经丢失
```

### 问题本质

- **竞态条件**：检查条件和调用`wait()`之间没有原子性保护
- **时序错误**：`notify()`在`wait()`之前执行
- **信号丢失**：`notify()`的唤醒信号没有被保存，一旦错过就永远丢失

## ❌ 错误示例

### 代码演示

```java
public class LostWakeupBug {
    private static final Object lock = new Object();
    private static boolean ready = false;
    
    public static void main(String[] args) {
        // 等待线程
        Thread waiter = new Thread(() -> {
            System.out.println("[等待线程] 启动");
            
            // ❌ 错误：检查条件在synchronized块外
            if (!ready) {
                System.out.println("[等待线程] 条件不满足，准备wait...");
                
                // 假设这里发生线程切换
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                synchronized (lock) {
                    try {
                        System.out.println("[等待线程] 调用wait()");
                        lock.wait(); // 唤醒信号已经丢失！
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            System.out.println("[等待线程] ready = " + ready);
        }, "Waiter");
        
        // 通知线程
        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(50); // 在waiter检查条件后、wait()前执行
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            synchronized (lock) {
                System.out.println("[通知线程] 修改条件 ready = true");
                ready = true;
                System.out.println("[通知线程] 调用notify()");
                lock.notify(); // 此时waiter还没有wait()，唤醒信号丢失！
            }
            System.out.println("[通知线程] 完成");
        }, "Notifier");
        
        waiter.start();
        notifier.start();
    }
}
```

### 执行结果

```
[等待线程] 启动
[等待线程] 条件不满足，准备wait...
[通知线程] 修改条件 ready = true
[通知线程] 调用notify()
[通知线程] 完成
[等待线程] 调用wait()
← 等待线程永远阻塞在这里！
```

## ✅ 正确解决方案

### 核心原则

**检查条件和`wait()`必须在同一个`synchronized`块中**

### 正确代码

```java
public class LostWakeupFix {
    private static final Object lock = new Object();
    private static boolean ready = false;
    
    public static void main(String[] args) {
        // 等待线程
        Thread waiter = new Thread(() -> {
            System.out.println("[等待线程] 启动");
            
            // ✅ 正确：检查条件和wait()在同一个synchronized块中
            synchronized (lock) {
                System.out.println("[等待线程] 检查条件");
                while (!ready) {
                    try {
                        System.out.println("[等待线程] 条件不满足，调用wait()");
                        lock.wait();
                        System.out.println("[等待线程] 被唤醒，重新检查条件");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("[等待线程] 条件满足，继续执行");
            }
            
            System.out.println("[等待线程] ready = " + ready);
        }, "Waiter");
        
        // 通知线程
        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            synchronized (lock) {
                System.out.println("[通知线程] 修改条件 ready = true");
                ready = true;
                System.out.println("[通知线程] 调用notify()");
                lock.notify();
            }
            System.out.println("[通知线程] 完成");
        }, "Notifier");
        
        waiter.start();
        notifier.start();
    }
}
```

### 执行结果

```
[等待线程] 启动
[等待线程] 检查条件
[等待线程] 条件不满足，调用wait()
[通知线程] 修改条件 ready = true
[通知线程] 调用notify()
[通知线程] 完成
[等待线程] 被唤醒，重新检查条件
[等待线程] 条件满足，继续执行
[等待线程] ready = true
```

## 🎯 为什么这样能避免 Lost Wakeup？

### 1. 原子性保证

```java
synchronized (lock) {
    // 检查条件和wait()在同一个临界区
    while (!ready) {
        lock.wait();
    }
}
```

- 检查条件和调用`wait()`是原子操作
- 不会在中间被打断
- 避免了竞态条件

### 2. 条件变量的作用

```java
// 关键：ready 变量保存了状态信息
synchronized (lock) {
    while (!ready) { // 即使notify()先执行，ready已经是true
        lock.wait();  // 不会执行wait()
    }
}
```

**原理**：
1. 条件变量`ready`保存了状态信息
2. 即使`notify()`先执行，`ready`已经是`true`
3. 等待线程检查条件时发现已满足，不会调用`wait()`
4. 成功避免了 Lost Wakeup

### 3. while 循环的双重保护

```java
while (!ready) {  // 1. 防止Lost Wakeup
    lock.wait();  // 2. 防止虚假唤醒
}
```

- 第一次检查：避免 Lost Wakeup
- 被唤醒后再次检查：防止虚假唤醒

## 📊 Lost Wakeup vs 虚假唤醒

| 维度 | Lost Wakeup | 虚假唤醒 |
|------|-------------|---------|
| **定义** | notify()在wait()之前执行 | wait()被意外唤醒 |
| **原因** | 检查条件和wait()不原子 | 操作系统或JVM的实现 |
| **后果** | 永远等待（死锁） | 条件不满足时继续执行 |
| **解决** | 检查条件和wait()在同一个synchronized块 | 使用while循环检查条件 |
| **发生时机** | 编码错误 | 系统行为 |

**共同点**：
- ✅ 都需要使用`while`循环检查条件
- ✅ 都需要在`synchronized`块中操作
- ✅ 都需要使用条件变量

## 🛡️ 最佳实践

### 标准模式（推荐）

```java
// 等待线程的标准写法
synchronized (lock) {
    while (!condition) {  // 1. 使用while循环
        try {
            lock.wait();  // 2. wait()在synchronized块中
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;       // 3. 正确处理中断
        }
    }
    // 执行任务
}

// 通知线程的标准写法
synchronized (lock) {
    condition = true;     // 1. 先修改条件
    lock.notifyAll();     // 2. 再通知（优先使用notifyAll）
}
```

### 核心原则

1. ✅ **检查条件和`wait()`必须在同一个`synchronized`块中**
2. ✅ **使用`while`循环而不是`if`检查条件**
3. ✅ **使用条件变量保存状态**
4. ✅ **修改条件后立即调用`notify()`/`notifyAll()`**
5. ✅ **优先使用`notifyAll()`而不是`notify()`**

## 🔧 使用 Lock 和 Condition

### 更现代的解决方案

```java
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class LockConditionExample {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean ready = false;
    
    // 等待线程
    public void waitForCondition() {
        lock.lock();
        try {
            while (!ready) {
                condition.await(); // 类似wait()
            }
            System.out.println("条件满足");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    // 通知线程
    public void signalCondition() {
        lock.lock();
        try {
            ready = true;
            condition.signal(); // 类似notify()
        } finally {
            lock.unlock();
        }
    }
}
```

### 优势

- ✅ 更灵活的锁控制
- ✅ 可以有多个 Condition
- ✅ 支持公平锁
- ✅ 可中断的锁获取
- ✅ 尝试获取锁（tryLock）

## 📚 实际案例

### 生产者-消费者模式

```java
public class ProducerConsumer {
    private final Object lock = new Object();
    private final Queue<Integer> queue = new LinkedList<>();
    private final int capacity = 10;
    
    // 生产者
    public void produce(int value) throws InterruptedException {
        synchronized (lock) {
            // ✅ 正确：while循环 + 条件变量
            while (queue.size() == capacity) {
                lock.wait(); // 队列满，等待
            }
            queue.offer(value);
            System.out.println("生产: " + value);
            lock.notifyAll(); // 通知消费者
        }
    }
    
    // 消费者
    public int consume() throws InterruptedException {
        synchronized (lock) {
            // ✅ 正确：while循环 + 条件变量
            while (queue.isEmpty()) {
                lock.wait(); // 队列空，等待
            }
            int value = queue.poll();
            System.out.println("消费: " + value);
            lock.notifyAll(); // 通知生产者
            return value;
        }
    }
}
```

## ⚠️ 常见错误

### 错误1：使用 if 而不是 while

```java
// ❌ 错误
synchronized (lock) {
    if (!ready) {
        lock.wait();
    }
}

// ✅ 正确
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
```

### 错误2：检查条件在 synchronized 外

```java
// ❌ 错误
if (!ready) {
    synchronized (lock) {
        lock.wait();
    }
}

// ✅ 正确
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
```

### 错误3：没有条件变量

```java
// ❌ 错误：没有条件变量
synchronized (lock) {
    lock.wait(); // 如果notify()先执行，永远等待
}

// ✅ 正确：使用条件变量
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
```

## 🎓 总结

### 核心要点

1. **Lost Wakeup 的本质**：notify()在wait()之前执行，唤醒信号丢失
2. **根本原因**：检查条件和wait()之间缺乏原子性保护
3. **解决方案**：检查条件和wait()必须在同一个synchronized块中
4. **条件变量的作用**：保存状态信息，即使notify()先执行也能正确工作
5. **while 循环**：同时防止 Lost Wakeup 和虚假唤醒

### 记忆口诀

```
检查等待同一锁，
条件变量不能少。
while循环双保险，
Lost Wakeup跑不了。
```

### 学习建议

1. ✅ 理解 Lost Wakeup 的时序问题
2. ✅ 掌握标准的 wait/notify 模式
3. ✅ 练习生产者-消费者等经典案例
4. ✅ 学习使用 Lock 和 Condition
5. ✅ 在实际项目中应用最佳实践

---

**相关文档**：
- [ThreadCooperationDemo.java](./demo/ThreadCooperationDemo.java) - 完整代码演示
- [02_线程协作机制详解.md](./docs/02_线程协作机制详解.md) - 详细理论讲解
- [README.md](./README.md) - 模块总览

**Happy Coding! 🚀**
