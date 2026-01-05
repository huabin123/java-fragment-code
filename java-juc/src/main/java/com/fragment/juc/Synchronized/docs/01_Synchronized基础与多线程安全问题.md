# Synchronized基础与多线程安全问题

## 1. 为什么需要Synchronized？

### 1.1 问题1：多线程环境下会出现什么问题？

**场景：银行账户转账**

假设你有一个银行账户，余额1000元，现在有两个线程同时执行转账操作：
- 线程A：转出500元
- 线程B：转出600元

**不使用同步的代码**：

```java
public class BankAccount {
    private int balance = 1000;
    
    public void withdraw(int amount) {
        // 步骤1：检查余额
        if (balance >= amount) {
            // 步骤2：模拟处理时间
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 步骤3：扣减余额
            balance -= amount;
            System.out.println(Thread.currentThread().getName() + 
                " 取款 " + amount + "，余额：" + balance);
        } else {
            System.out.println(Thread.currentThread().getName() + 
                " 余额不足");
        }
    }
}
```

**执行结果**：

```
时间轴：
T1: 线程A检查余额(1000 >= 500) ✓
T2: 线程B检查余额(1000 >= 600) ✓
T3: 线程A扣减余额(1000 - 500 = 500)
T4: 线程B扣减余额(500 - 600 = -100) ❌

最终余额：-100元（出现了负数！）
```

**问题分析**：

这就是典型的**线程安全问题**，具体表现为：

1. **竞态条件（Race Condition）**：多个线程同时访问共享资源，结果依赖于线程执行的时序
2. **原子性问题**：`检查余额 → 扣减余额` 这个操作不是原子的，可能被中断
3. **可见性问题**：一个线程修改了余额，其他线程可能看不到最新值
4. **有序性问题**：指令重排序可能导致执行顺序与代码顺序不一致

---

### 1.2 问题2：线程安全问题的本质是什么？

**三大核心问题**：

#### **问题1：原子性（Atomicity）**

**定义**：一个操作或多个操作要么全部执行成功，要么全部不执行，中间不能被打断。

**示例**：

```java
public class Counter {
    private int count = 0;
    
    // ❌ 这不是原子操作！
    public void increment() {
        count++; // 实际上是三个步骤
    }
}
```

**count++ 的字节码分析**：

```
1. getfield      // 读取count的值
2. iconst_1      // 将常量1压入栈
3. iadd          // 执行加法
4. putfield      // 将结果写回count
```

**多线程执行时序**：

```
初始值：count = 0

时间  线程A              线程B              count值
T1   读取count(0)                          0
T2                      读取count(0)       0
T3   计算0+1=1                             0
T4                      计算0+1=1          0
T5   写回count=1                           1
T6                      写回count=1        1

期望结果：2
实际结果：1（丢失了一次更新！）
```

#### **问题2：可见性（Visibility）**

**定义**：一个线程修改了共享变量，其他线程能够立即看到修改后的值。

**示例**：

```java
public class VisibilityDemo {
    private boolean flag = false;
    
    // 线程A执行
    public void writer() {
        flag = true; // 写入
    }
    
    // 线程B执行
    public void reader() {
        while (!flag) { // 可能永远循环！
            // do something
        }
        System.out.println("flag is true");
    }
}
```

**为什么会出现可见性问题？**

```
CPU架构：
┌─────────────┐       ┌─────────────┐
│   线程A     │       │   线程B     │
│  CPU Core1  │       │  CPU Core2  │
├─────────────┤       ├─────────────┤
│  L1 Cache   │       │  L1 Cache   │
│  flag=true  │       │  flag=false │
└──────┬──────┘       └──────┬──────┘
       │                     │
       └──────┬──────────────┘
              │
       ┌──────▼──────┐
       │ Main Memory │
       │ flag=false  │
       └─────────────┘
```

- 线程A修改了flag=true，但只写入了CPU1的缓存
- 线程B读取flag时，从CPU2的缓存读取，仍然是false
- 导致线程B看不到线程A的修改

#### **问题3：有序性（Ordering）**

**定义**：程序执行的顺序按照代码的先后顺序执行。

**示例：双重检查锁定（DCL）的问题**

```java
public class Singleton {
    private static Singleton instance;
    
    public static Singleton getInstance() {
        if (instance == null) {              // 第一次检查
            synchronized (Singleton.class) {
                if (instance == null) {      // 第二次检查
                    instance = new Singleton(); // ❌ 可能出问题！
                }
            }
        }
        return instance;
    }
}
```

**为什么会出问题？**

`instance = new Singleton()` 实际上包含三个步骤：

```
1. memory = allocate();    // 分配内存空间
2. ctorInstance(memory);   // 初始化对象
3. instance = memory;      // 设置instance指向内存地址
```

**指令重排序后**：

```
1. memory = allocate();    // 分配内存空间
3. instance = memory;      // 设置instance指向内存地址（重排序！）
2. ctorInstance(memory);   // 初始化对象
```

**多线程执行时序**：

```
时间  线程A                          线程B
T1   分配内存
T2   instance指向内存（未初始化）
T3                                  检查instance != null
T4                                  返回instance（未初始化！）
T5   初始化对象
```

线程B拿到的是一个**未完全初始化的对象**，使用时会出错！

---

### 1.3 问题3：在Synchronized出现之前，如何解决线程安全问题？

#### **方案1：单线程执行（避免并发）**

```java
// 所有操作都在主线程执行
public class SingleThreadSolution {
    private int count = 0;
    
    public void process() {
        for (int i = 0; i < 10000; i++) {
            count++;
        }
    }
}
```

**缺点**：
- ❌ 无法利用多核CPU
- ❌ 性能低下
- ❌ 无法处理并发请求

#### **方案2：使用volatile（解决可见性）**

```java
public class VolatileSolution {
    private volatile boolean flag = false;
    
    public void setFlag() {
        flag = true; // 保证可见性
    }
    
    public boolean getFlag() {
        return flag; // 能看到最新值
    }
}
```

**缺点**：
- ✓ 解决了可见性问题
- ✓ 禁止指令重排序
- ❌ **不能保证原子性**（count++仍然不安全）

#### **方案3：使用原子类（解决原子性）**

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicSolution {
    private AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet(); // 原子操作
    }
}
```

**缺点**：
- ✓ 解决了原子性问题
- ✓ 性能较好（CAS无锁）
- ❌ **只能保护单个变量**
- ❌ **无法保护复杂的业务逻辑**

#### **方案4：使用ThreadLocal（线程隔离）**

```java
public class ThreadLocalSolution {
    private ThreadLocal<Integer> count = ThreadLocal.withInitial(() -> 0);
    
    public void increment() {
        count.set(count.get() + 1); // 每个线程独立
    }
}
```

**缺点**：
- ✓ 避免了线程安全问题
- ❌ **无法共享数据**（每个线程有自己的副本）
- ❌ **不适合需要协作的场景**

---

### 1.4 问题4：Synchronized如何解决这些问题？

**Synchronized的三大保证**：

```java
public class SynchronizedSolution {
    private int count = 0;
    
    // ✅ 使用synchronized保护
    public synchronized void increment() {
        count++;
    }
}
```

**保证1：原子性**
- synchronized块内的代码要么全部执行，要么全部不执行
- 同一时刻只有一个线程能执行synchronized块

**保证2：可见性**
- 线程释放锁时，会将修改刷新到主内存
- 线程获取锁时，会从主内存读取最新值

**保证3：有序性**
- synchronized块内的代码不会被重排序到块外
- 保证了happens-before原则

**执行流程**：

```
线程A                    线程B
  │                        │
  ├─ 尝试获取锁            │
  ├─ 成功获取锁            │
  ├─ 执行count++           │
  │                        ├─ 尝试获取锁
  │                        ├─ 阻塞等待（锁被A持有）
  ├─ 释放锁                │
  │                        ├─ 成功获取锁
  │                        ├─ 执行count++
  │                        ├─ 释放锁
```

---

## 2. Synchronized的基本使用

### 2.1 问题5：Synchronized有哪几种使用方式？

#### **方式1：修饰实例方法**

```java
public class InstanceMethodSync {
    private int count = 0;
    
    // 锁对象：this（当前实例）
    public synchronized void increment() {
        count++;
    }
    
    // 等价于：
    public void incrementEquivalent() {
        synchronized (this) {
            count++;
        }
    }
}
```

**特点**：
- 锁对象是**当前实例（this）**
- 不同实例之间不互斥
- 适合保护实例变量

**示例**：

```java
InstanceMethodSync obj1 = new InstanceMethodSync();
InstanceMethodSync obj2 = new InstanceMethodSync();

// 线程A和B不互斥（不同实例）
Thread t1 = new Thread(() -> obj1.increment());
Thread t2 = new Thread(() -> obj2.increment());

// 线程C和D互斥（同一实例）
Thread t3 = new Thread(() -> obj1.increment());
Thread t4 = new Thread(() -> obj1.increment());
```

#### **方式2：修饰静态方法**

```java
public class StaticMethodSync {
    private static int count = 0;
    
    // 锁对象：StaticMethodSync.class（类对象）
    public static synchronized void increment() {
        count++;
    }
    
    // 等价于：
    public static void incrementEquivalent() {
        synchronized (StaticMethodSync.class) {
            count++;
        }
    }
}
```

**特点**：
- 锁对象是**类的Class对象**
- 所有实例共享同一把锁
- 适合保护静态变量

#### **方式3：修饰代码块（自定义锁对象）**

```java
public class CodeBlockSync {
    private int count1 = 0;
    private int count2 = 0;
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    public void increment1() {
        synchronized (lock1) { // 使用lock1保护count1
            count1++;
        }
    }
    
    public void increment2() {
        synchronized (lock2) { // 使用lock2保护count2
            count2++;
        }
    }
}
```

**特点**：
- 可以自定义锁对象
- **锁粒度更细**，提高并发性
- 适合保护多个独立的资源

**对比**：

```java
// ❌ 粗粒度锁：所有操作互斥
public synchronized void method1() { count1++; }
public synchronized void method2() { count2++; }

// ✅ 细粒度锁：独立操作不互斥
public void method1() { synchronized(lock1) { count1++; } }
public void method2() { synchronized(lock2) { count2++; } }
```

---

### 2.2 问题6：Synchronized的锁对象选择有什么讲究？

#### **原则1：锁对象必须是同一个**

```java
// ❌ 错误示例：每次都创建新锁
public void badExample() {
    synchronized (new Object()) { // 每次都是新对象！
        count++;
    }
}

// ✅ 正确示例：使用同一个锁
private final Object lock = new Object();
public void goodExample() {
    synchronized (lock) {
        count++;
    }
}
```

#### **原则2：锁对象不能为null**

```java
private Object lock = null;

public void badExample() {
    synchronized (lock) { // NullPointerException!
        count++;
    }
}
```

#### **原则3：避免使用String、Integer等作为锁**

```java
// ❌ 危险：字符串常量池导致意外共享
private String lock = "LOCK";
synchronized (lock) { // 可能与其他代码的"LOCK"是同一个对象！
    // ...
}

// ❌ 危险：Integer缓存导致意外共享
private Integer lock = 127; // -128~127会被缓存
synchronized (lock) { // 可能与其他代码的127是同一个对象！
    // ...
}

// ✅ 推荐：使用普通对象
private final Object lock = new Object();
synchronized (lock) {
    // ...
}
```

#### **原则4：锁对象应该是final的**

```java
// ❌ 危险：锁对象可能被改变
private Object lock = new Object();

public void changeLock() {
    lock = new Object(); // 锁对象变了！
}

// ✅ 推荐：锁对象不可变
private final Object lock = new Object();
```

---

### 2.3 问题7：Synchronized使用中有哪些常见陷阱？

#### **陷阱1：锁对象不一致**

```java
public class InconsistentLock {
    private int count = 0;
    
    public void increment() {
        synchronized (this) {
            count++;
        }
    }
    
    public void decrement() {
        synchronized (InconsistentLock.class) { // ❌ 不同的锁！
            count--;
        }
    }
}
```

**问题**：increment和decrement使用不同的锁，无法互斥！

#### **陷阱2：锁粒度过大**

```java
// ❌ 锁粒度过大：整个方法都被锁住
public synchronized void processLargeData() {
    // 1. 读取数据（耗时操作）
    String data = readDataFromDB(); // 1000ms
    
    // 2. 处理数据（不需要同步）
    String result = process(data); // 2000ms
    
    // 3. 更新共享变量（需要同步）
    this.result = result; // 1ms
}

// ✅ 锁粒度优化：只锁必要的部分
public void processLargeDataOptimized() {
    // 1. 读取数据（不需要同步）
    String data = readDataFromDB();
    
    // 2. 处理数据（不需要同步）
    String result = process(data);
    
    // 3. 更新共享变量（需要同步）
    synchronized (this) {
        this.result = result;
    }
}
```

#### **陷阱3：死锁**

```java
public class DeadLockDemo {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    public void method1() {
        synchronized (lock1) {
            System.out.println("method1 获取lock1");
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            synchronized (lock2) { // 等待lock2
                System.out.println("method1 获取lock2");
            }
        }
    }
    
    public void method2() {
        synchronized (lock2) {
            System.out.println("method2 获取lock2");
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            synchronized (lock1) { // 等待lock1
                System.out.println("method2 获取lock1");
            }
        }
    }
}
```

**死锁发生**：

```
时间  线程A                线程B
T1   获取lock1            获取lock2
T2   等待lock2            等待lock1
T3   ↓ 死锁 ↓            ↓ 死锁 ↓
```

**解决方案**：

1. **固定锁的顺序**

```java
// ✅ 所有线程都按相同顺序获取锁
public void method1() {
    synchronized (lock1) {
        synchronized (lock2) {
            // ...
        }
    }
}

public void method2() {
    synchronized (lock1) { // 与method1顺序一致
        synchronized (lock2) {
            // ...
        }
    }
}
```

2. **使用tryLock（需要ReentrantLock）**

```java
if (lock1.tryLock()) {
    try {
        if (lock2.tryLock()) {
            try {
                // 业务逻辑
            } finally {
                lock2.unlock();
            }
        }
    } finally {
        lock1.unlock();
    }
}
```

#### **陷阱4：在锁内调用外部方法**

```java
public class AlienMethodCall {
    private final List<Listener> listeners = new ArrayList<>();
    
    // ❌ 危险：在锁内调用外部方法
    public synchronized void fireEvent() {
        for (Listener listener : listeners) {
            listener.onEvent(); // 外部方法，可能很慢或死锁！
        }
    }
    
    // ✅ 安全：复制后在锁外调用
    public void fireEventSafe() {
        List<Listener> copy;
        synchronized (this) {
            copy = new ArrayList<>(listeners);
        }
        for (Listener listener : copy) {
            listener.onEvent(); // 在锁外调用
        }
    }
}
```

---

## 3. Synchronized的使用场景

### 3.1 场景1：保护共享变量

```java
public class SharedCounter {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
    
    public synchronized int getCount() {
        return count;
    }
}
```

### 3.2 场景2：保护复合操作

```java
public class BankAccount {
    private int balance = 1000;
    
    // 复合操作：检查 + 修改
    public synchronized boolean withdraw(int amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }
}
```

### 3.3 场景3：保护多个相关变量

```java
public class Range {
    private int lower = 0;
    private int upper = 10;
    
    // 保护不变式：lower <= upper
    public synchronized void setLower(int value) {
        if (value > upper) {
            throw new IllegalArgumentException();
        }
        lower = value;
    }
    
    public synchronized void setUpper(int value) {
        if (value < lower) {
            throw new IllegalArgumentException();
        }
        upper = value;
    }
}
```

### 3.4 场景4：单例模式

```java
public class Singleton {
    private static volatile Singleton instance;
    
    private Singleton() {}
    
    // 双重检查锁定（DCL）
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

**注意**：必须使用`volatile`修饰instance，防止指令重排序！

---

## 4. 核心问题总结

### Q1: 为什么需要Synchronized？
**A**: 多线程环境下存在**原子性、可见性、有序性**三大问题，导致线程安全问题。Synchronized能同时解决这三个问题。

### Q2: 线程安全问题的本质是什么？
**A**: 
- **原子性**：操作被中断，导致数据不一致
- **可见性**：CPU缓存导致线程看不到最新值
- **有序性**：指令重排序导致执行顺序错乱

### Q3: Synchronized出现之前如何解决线程安全？
**A**: 
- 单线程执行（性能差）
- volatile（不保证原子性）
- 原子类（只能保护单个变量）
- ThreadLocal（无法共享数据）

### Q4: Synchronized有哪几种使用方式？
**A**: 
- 修饰实例方法（锁this）
- 修饰静态方法（锁Class对象）
- 修饰代码块（自定义锁对象）

### Q5: 如何选择锁对象？
**A**: 
- 必须是同一个对象
- 不能为null
- 避免使用String、Integer
- 应该是final的

### Q6: 使用Synchronized有哪些陷阱？
**A**: 
- 锁对象不一致
- 锁粒度过大
- 死锁
- 在锁内调用外部方法

---

## 5. 思考题

1. **为什么count++不是原子操作？它包含哪几个步骤？**
2. **volatile能否替代synchronized？为什么？**
3. **如何避免死锁？有哪些策略？**
4. **为什么双重检查锁定需要volatile？**
5. **synchronized修饰静态方法和实例方法有什么区别？**

---

## 下一章预告

下一章我们将深入学习：

- **对象头结构**：Mark Word、Class Pointer、数组长度
- **Monitor机制**：重量级锁的实现原理
- **ObjectMonitor源码分析**：_owner、_EntryList、_WaitSet
- **为什么重量级锁性能差？**：用户态与内核态切换

让我们继续深入！🚀
