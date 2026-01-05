# 第三章：volatile深入解析 - 轻量级同步机制

> **学习目标**：深入理解volatile的原理、使用场景和性能特点

---

## 一、为什么需要volatile？

### 1.1 synchronized的性能问题

```java
// 使用synchronized保证可见性
public class Counter {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
    
    public synchronized int get() {
        return count;
    }
}

// 问题：
// ❌ 性能开销大（加锁、解锁）
// ❌ 线程阻塞（未获取锁的线程等待）
// ❌ 上下文切换（线程状态切换）

// 如果只需要可见性，synchronized太重了
```

### 1.2 volatile的解决方案

```java
// 使用volatile保证可见性
public class Counter {
    private volatile int count = 0;
    
    public void increment() {
        count++;  // 注意：仍然不是原子的
    }
    
    public int get() {
        return count;  // 保证可见性
    }
}

// 优势：
// ✅ 无锁，性能好
// ✅ 保证可见性
// ✅ 禁止指令重排序

// 劣势：
// ❌ 不保证原子性
```

---

## 二、volatile的两大特性

### 2.1 保证可见性

```
可见性：
一个线程修改了volatile变量的值，
新值对其他线程立即可见。
```

**实现原理**：

```
写volatile变量：
1. 修改工作内存的值
2. 立即刷新到主内存
3. 使其他CPU缓存失效

读volatile变量：
1. 从主内存读取最新值
2. 更新工作内存
3. 不使用缓存的旧值

结果：
- 写操作对后续读操作立即可见
- 避免了缓存不一致问题
```

**示例**：

```java
public class VolatileVisibility {
    private volatile boolean flag = false;
    
    // 线程1：写线程
    public void writer() {
        flag = true;  // 立即刷新到主内存
    }
    
    // 线程2：读线程
    public void reader() {
        while (!flag) {  // 从主内存读取
            // 等待
        }
        System.out.println("Flag is true!");
    }
}

// 如果没有volatile：
// - 线程2可能一直读取缓存中的false
// - 永远无法退出循环

// 有了volatile：
// - 线程1写入后立即刷新到主内存
// - 线程2能立即看到true
```

### 2.2 禁止指令重排序

```
有序性：
volatile变量的读写操作不会被重排序。

具体规则：
1. volatile写之前的操作不会被重排序到写之后
2. volatile读之后的操作不会被重排序到读之前
3. volatile写 happens-before volatile读
```

**示例**：

```java
public class VolatileOrdering {
    private int a = 0;
    private int b = 0;
    private volatile int c = 0;
    
    public void writer() {
        a = 1;  // 1
        b = 2;  // 2
        c = 3;  // 3（volatile写）
    }
    
    public void reader() {
        int r1 = c;  // 4（volatile读）
        int r2 = b;  // 5
        int r3 = a;  // 6
    }
}

// 重排序规则：
// 1. 1和2可以重排序（都在volatile写之前）
// 2. 1和2不能重排序到3之后（volatile写规则）
// 3. 5和6可以重排序（都在volatile读之后）
// 4. 5和6不能重排序到4之前（volatile读规则）

// happens-before：
// 1,2 happens-before 3（程序顺序 + volatile写规则）
// 3 happens-before 4（volatile规则）
// 4 happens-before 5,6（程序顺序 + volatile读规则）
// 因此：1,2 happens-before 5,6（传递性）
```

---

## 三、volatile的实现原理

### 3.1 内存屏障

```
内存屏障（Memory Barrier）：
CPU指令，用于控制内存操作的顺序。

4种类型：
1. LoadLoad屏障：   Load1; LoadLoad; Load2
   - 保证Load1的数据在Load2之前加载

2. StoreStore屏障： Store1; StoreStore; Store2
   - 保证Store1的数据在Store2之前刷新到内存

3. LoadStore屏障：  Load1; LoadStore; Store2
   - 保证Load1的数据在Store2之前加载

4. StoreLoad屏障：  Store1; StoreLoad; Load2
   - 保证Store1的数据在Load2之前刷新到内存
   - 开销最大，相当于全屏障
```

### 3.2 volatile的内存屏障插入策略

```
volatile写操作：
StoreStore屏障
volatile写
StoreLoad屏障

作用：
- StoreStore：保证前面的普通写在volatile写之前刷新
- StoreLoad：保证volatile写在后面的读之前完成

volatile读操作：
volatile读
LoadLoad屏障
LoadStore屏障

作用：
- LoadLoad：保证volatile读在后面的普通读之前完成
- LoadStore：保证volatile读在后面的普通写之前完成
```

**示例**：

```java
int a = 0;
volatile int v = 0;
int b = 0;

// 写操作
a = 1;              // 普通写
// StoreStore屏障
v = 2;              // volatile写
// StoreLoad屏障
b = 3;              // 普通写

// 读操作
int r1 = v;         // volatile读
// LoadLoad屏障
// LoadStore屏障
int r2 = a;         // 普通读
int r3 = b;         // 普通读

// 保证：
// 1. a=1在v=2之前刷新到内存
// 2. v=2在b=3之前完成
// 3. r1=v在r2=a之前完成
// 4. r1=v在r3=b之前完成
```

### 3.3 CPU缓存一致性协议（MESI）

```
MESI协议：
保证多核CPU缓存一致性的协议

4种状态：
M (Modified)：   已修改，独占，与内存不一致
E (Exclusive)：  独占，与内存一致
S (Shared)：     共享，多个CPU缓存都有
I (Invalid)：    无效，需要从内存重新加载

volatile的作用：
- 写操作：使其他CPU缓存失效（I状态）
- 读操作：从内存重新加载
```

---

## 四、volatile的使用场景

### 4.1 场景1：状态标志

```java
// 最常见的用法：控制线程停止

public class Task implements Runnable {
    private volatile boolean shutdown = false;
    
    @Override
    public void run() {
        while (!shutdown) {
            // 执行任务
        }
        // 清理资源
    }
    
    public void shutdown() {
        shutdown = true;  // 立即可见
    }
}

// 为什么适合：
// ✅ 只有一个线程写，多个线程读
// ✅ 不需要原子性（只是简单的赋值）
// ✅ 需要立即可见
```

### 4.2 场景2：双重检查锁

```java
// 单例模式的线程安全实现

public class Singleton {
    private static volatile Singleton instance;
    
    public static Singleton getInstance() {
        if (instance == null) {  // 第一次检查（无锁）
            synchronized (Singleton.class) {
                if (instance == null) {  // 第二次检查（有锁）
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}

// 为什么需要volatile：
// 1. 防止指令重排序
// 2. 保证对象完全初始化后才对其他线程可见

// instance = new Singleton()的步骤：
// 1. 分配内存
// 2. 初始化对象
// 3. instance指向内存

// 如果没有volatile，可能重排序为：
// 1. 分配内存
// 2. instance指向内存（对象未初始化）
// 3. 初始化对象

// 其他线程可能看到未初始化的对象
```

### 4.3 场景3：独立观察

```java
// 多个线程观察同一个变量的变化

public class VolatileExample {
    private volatile int value;
    
    public void setValue(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
}

// 为什么适合：
// ✅ 读写操作独立
// ✅ 不需要原子性
// ✅ 需要立即可见
```

### 4.4 场景4：一次性安全发布

```java
// 安全发布不可变对象

public class BackgroundFloobleLoader {
    private volatile Flooble theFlooble;
    
    public void initInBackground() {
        // 在后台线程初始化
        Flooble flooble = new Flooble();
        // ... 初始化flooble
        theFlooble = flooble;  // 安全发布
    }
    
    public Flooble getFlooble() {
        return theFlooble;
    }
}

// 为什么适合：
// ✅ 对象不可变
// ✅ 只发布一次
// ✅ 需要保证对象完全初始化
```

---

## 五、volatile不适合的场景

### 5.1 场景1：复合操作

```java
// ❌ 错误：volatile不能保证i++的原子性

public class Counter {
    private volatile int count = 0;
    
    public void increment() {
        count++;  // 不是原子操作
    }
}

// count++的步骤：
// 1. 读取count
// 2. 加1
// 3. 写入count

// 多线程问题：
// 线程A读取count=0
// 线程B读取count=0
// 线程A写入count=1
// 线程B写入count=1
// 结果：count=1（错误，应该是2）

// ✅ 正确：使用AtomicInteger

public class Counter {
    private AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet();  // 原子操作
    }
}
```

### 5.2 场景2：依赖当前值

```java
// ❌ 错误：新值依赖旧值

public class NumberRange {
    private volatile int lower = 0;
    private volatile int upper = 10;
    
    public void setLower(int value) {
        if (value > upper) {  // 依赖upper的当前值
            throw new IllegalArgumentException();
        }
        lower = value;
    }
    
    public void setUpper(int value) {
        if (value < lower) {  // 依赖lower的当前值
            throw new IllegalArgumentException();
        }
        upper = value;
    }
}

// 问题：
// 线程A：setLower(5)
// 线程B：setUpper(3)
// 可能都通过检查，导致lower > upper

// ✅ 正确：使用synchronized

public class NumberRange {
    private int lower = 0;
    private int upper = 10;
    
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

### 5.3 场景3：多个变量的一致性

```java
// ❌ 错误：volatile不能保证多个变量的一致性

public class Point {
    private volatile int x;
    private volatile int y;
    
    public void set(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public int[] get() {
        return new int[]{x, y};
    }
}

// 问题：
// 线程A：set(1, 2)
// 线程B：get()
// 可能得到[1, 0]或[0, 2]（不一致）

// ✅ 正确：使用synchronized或不可变对象

public class Point {
    private int x;
    private int y;
    
    public synchronized void set(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public synchronized int[] get() {
        return new int[]{x, y};
    }
}
```

---

## 六、volatile vs synchronized

### 6.1 对比表

| 特性 | volatile | synchronized |
|------|----------|--------------|
| **可见性** | ✅ 保证 | ✅ 保证 |
| **原子性** | ❌ 不保证 | ✅ 保证 |
| **有序性** | ✅ 禁止重排序 | ✅ 保证 |
| **性能** | 高（无锁） | 低（有锁） |
| **阻塞** | 不阻塞 | 可能阻塞 |
| **适用场景** | 简单读写、状态标志 | 复合操作、临界区 |

### 6.2 选择建议

```java
// ✅ 使用volatile的场景：
// 1. 状态标志
private volatile boolean flag = false;

// 2. 一次性安全发布
private volatile Configuration config;

// 3. 独立观察
private volatile int value;

// ✅ 使用synchronized的场景：
// 1. 复合操作
public synchronized void increment() {
    count++;
}

// 2. 多个变量的一致性
public synchronized void update() {
    x = 1;
    y = 2;
}

// 3. 需要阻塞等待
public synchronized void waitForCondition() {
    while (!condition) {
        wait();
    }
}
```

---

## 七、性能分析

### 7.1 性能对比

```java
public class PerformanceTest {
    private static final int ITERATIONS = 10000000;
    
    // 测试1：volatile读写
    private volatile int volatileValue = 0;
    
    public void testVolatile() {
        for (int i = 0; i < ITERATIONS; i++) {
            volatileValue = i;
            int value = volatileValue;
        }
    }
    
    // 测试2：synchronized读写
    private int syncValue = 0;
    
    public void testSynchronized() {
        for (int i = 0; i < ITERATIONS; i++) {
            synchronized (this) {
                syncValue = i;
                int value = syncValue;
            }
        }
    }
}

// 性能结果（1000万次操作）：
// volatile：     约100ms
// synchronized： 约2000ms

// 结论：
// volatile比synchronized快20倍
```

### 7.2 性能特点

```
volatile的性能：
✅ 无锁，不阻塞
✅ 无上下文切换
✅ 读操作几乎无开销
❌ 写操作有内存屏障开销
❌ 使其他CPU缓存失效

synchronized的性能：
❌ 需要加锁解锁
❌ 可能阻塞
❌ 可能上下文切换
✅ JVM有锁优化（偏向锁、轻量级锁）
```

---

## 八、总结

### 8.1 核心要点

1. **两大特性**：可见性、有序性
2. **实现原理**：内存屏障、缓存一致性
3. **适用场景**：状态标志、双重检查锁、独立观察
4. **不适合**：复合操作、依赖当前值、多变量一致性
5. **性能**：比synchronized快，但不保证原子性

### 8.2 使用建议

```
✅ 适合volatile：
- 只有一个线程写，多个线程读
- 不需要原子性
- 需要立即可见

❌ 不适合volatile：
- 需要原子性
- 新值依赖旧值
- 多个变量需要一致性
```

### 8.3 思考题

1. **volatile如何保证可见性？**
2. **volatile如何禁止重排序？**
3. **为什么volatile不能保证原子性？**
4. **什么时候用volatile，什么时候用synchronized？**

---

**下一章预告**：我们将学习final的内存语义和不可变对象的设计。

---

**参考资料**：
- 《Java并发编程实战》第16章
- 《深入理解Java虚拟机》第12章
- JSR 133 (Java Memory Model) FAQ
