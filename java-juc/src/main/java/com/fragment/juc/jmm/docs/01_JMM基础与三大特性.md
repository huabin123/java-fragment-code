# 第一章：JMM基础与三大特性 - 并发编程的基石

> **学习目标**：深入理解Java内存模型的必要性、核心概念和三大特性

---

## 一、为什么需要Java内存模型？

### 1.1 硬件内存架构的挑战

```
问题1：CPU和内存的速度差距

CPU速度：   ~3GHz (每秒30亿次操作)
内存速度：  ~200ns (读取一次需要200纳秒)
速度差距：  约100-200倍

结果：CPU大部分时间在等待内存
```

**解决方案：多级缓存**

```
CPU架构：

CPU Core 1              CPU Core 2              CPU Core 3
    ↓                       ↓                       ↓
  L1 Cache               L1 Cache                L1 Cache
  (32KB, 1ns)           (32KB, 1ns)            (32KB, 1ns)
    ↓                       ↓                       ↓
  L2 Cache               L2 Cache                L2 Cache
  (256KB, 3ns)          (256KB, 3ns)           (256KB, 3ns)
    ↓                       ↓                       ↓
         ↓                  ↓                  ↓
              L3 Cache (共享, 8MB, 12ns)
                        ↓
                   主内存 (GB级, 200ns)

优势：
- L1缓存命中：1ns
- L2缓存命中：3ns
- L3缓存命中：12ns
- 主内存访问：200ns

问题：
- 缓存一致性
- 可见性问题
- 指令重排序
```

### 1.2 多核CPU带来的问题

```java
// 问题场景：两个线程操作同一个变量

int count = 0;  // 主内存中的变量

// 线程1（CPU1）
count++;  // 1. 从主内存读取count=0到L1缓存
          // 2. 在L1缓存中计算count=1
          // 3. 写回主内存（可能延迟）

// 线程2（CPU2）
count++;  // 1. 从主内存读取count=0到L1缓存（可能还是0）
          // 2. 在L1缓存中计算count=1
          // 3. 写回主内存

// 结果：count=1（错误，应该是2）
```

**核心问题**：

```
1. 可见性问题：
   - CPU1修改了count，但CPU2看不到
   - 原因：各自的缓存不同步

2. 原子性问题：
   - count++不是原子操作
   - 分为：读取、加1、写入三步

3. 有序性问题：
   - CPU和编译器可能重排序指令
   - 优化性能，但可能破坏语义
```

### 1.3 Java内存模型的解决方案

```
JMM的目标：

1. 屏蔽硬件差异：
   - 不同CPU架构的内存模型不同
   - JMM提供统一的抽象

2. 定义规则：
   - 什么时候一个线程能看到另一个线程的修改
   - 如何保证操作的原子性
   - 如何控制指令的执行顺序

3. 提供工具：
   - volatile：保证可见性和有序性
   - synchronized：保证三大特性
   - final：保证初始化安全
```

---

## 二、JMM的核心概念

### 2.1 主内存与工作内存

```
JMM的抽象模型：

线程1                     线程2                     线程3
  ↓                         ↓                         ↓
工作内存1                 工作内存2                 工作内存3
(本地副本)               (本地副本)               (本地副本)
  ↓                         ↓                         ↓
  ←←←←←←←←←←←←←←←←←←←←←←←←←←←←←→→→→→→→→→→→→→→→→→→→→→→→→→→→→
                          ↓
                      主内存
                  (共享变量)

特点：
1. 所有共享变量存储在主内存
2. 每个线程有自己的工作内存
3. 线程不能直接访问主内存
4. 线程间通过主内存通信
```

**内存交互操作**：

```java
// JMM定义了8种原子操作

1. lock（锁定）：主内存变量，标识为线程独占
2. unlock（解锁）：释放锁定的变量
3. read（读取）：从主内存读取变量到工作内存
4. load（载入）：将read的值放入工作内存的变量副本
5. use（使用）：将工作内存的值传递给执行引擎
6. assign（赋值）：将执行引擎的值赋给工作内存
7. store（存储）：将工作内存的值传送到主内存
8. write（写入）：将store的值写入主内存

操作规则：
- read和load必须成对出现
- store和write必须成对出现
- 不允许丢弃assign操作
- 不允许无assign就store
- 等等...
```

### 2.2 JMM与硬件内存的对应

```
JMM抽象模型 ←→ 硬件实现

主内存      ←→ 主内存（RAM）
工作内存    ←→ CPU缓存（L1/L2/L3）+ 寄存器

注意：
- JMM是抽象概念，不是物理实现
- 不同硬件有不同的实现方式
- JMM保证跨平台的一致性
```

---

## 三、可见性问题

### 3.1 什么是可见性？

```
可见性：
一个线程修改了共享变量的值，
其他线程能够立即看到修改后的值。
```

### 3.2 可见性问题的产生

```java
// 经典案例：线程无法停止

public class VisibilityProblem {
    private boolean stop = false;  // 没有volatile
    
    public void run() {
        // 线程1：工作线程
        while (!stop) {
            // 执行任务
            // stop的值可能一直是false（从缓存读取）
        }
    }
    
    public void shutdown() {
        // 线程2：主线程
        stop = true;  // 修改主内存的值
        // 但线程1可能看不到这个修改
    }
}
```

**问题分析**：

```
时间线：

T1: 线程1读取stop=false到工作内存
T2: 线程1在工作内存中检查stop=false
T3: 主线程修改主内存stop=true
T4: 线程1继续从工作内存读取stop=false（看不到修改）
T5: 线程1永远无法停止

原因：
1. 线程1的工作内存中缓存了stop=false
2. 主线程修改了主内存的stop=true
3. 线程1没有及时从主内存刷新
4. 导致线程1看不到修改
```

### 3.3 解决可见性问题

#### 方案1：使用volatile

```java
public class VisibilitySolution {
    private volatile boolean stop = false;  // 添加volatile
    
    public void run() {
        while (!stop) {
            // 每次都从主内存读取stop
        }
    }
    
    public void shutdown() {
        stop = true;  // 立即刷新到主内存
    }
}
```

#### 方案2：使用synchronized

```java
public class VisibilitySolution {
    private boolean stop = false;
    
    public void run() {
        while (!isStop()) {  // synchronized方法
            // 执行任务
        }
    }
    
    public synchronized boolean isStop() {
        return stop;  // synchronized保证可见性
    }
    
    public synchronized void shutdown() {
        stop = true;
    }
}
```

#### 方案3：使用Lock

```java
public class VisibilitySolution {
    private boolean stop = false;
    private final Lock lock = new ReentrantLock();
    
    public void run() {
        while (true) {
            lock.lock();
            try {
                if (stop) break;
            } finally {
                lock.unlock();
            }
            // 执行任务
        }
    }
    
    public void shutdown() {
        lock.lock();
        try {
            stop = true;
        } finally {
            lock.unlock();
        }
    }
}
```

---

## 四、原子性问题

### 4.1 什么是原子性？

```
原子性：
一个操作或多个操作，要么全部执行且执行过程不被中断，
要么全部不执行。
```

### 4.2 原子性问题的产生

```java
// 经典案例：i++不是原子操作

public class AtomicityProblem {
    private int count = 0;
    
    public void increment() {
        count++;  // 看似一行代码，实际是三个操作
    }
}
```

**字节码分析**：

```
count++的字节码：

0: aload_0
1: dup
2: getfield      #2  // 读取count
5: iconst_1
6: iadd              // 加1
7: putfield      #2  // 写入count
10: return

三个步骤：
1. 读取count的值
2. 将值加1
3. 写回count

问题：
这三个步骤不是原子的，可能被中断
```

**并发问题**：

```
时间线（两个线程同时执行count++）：

初始：count = 0

T1: 线程1读取count=0
T2: 线程2读取count=0
T3: 线程1计算0+1=1
T4: 线程2计算0+1=1
T5: 线程1写入count=1
T6: 线程2写入count=1

结果：count=1（错误，应该是2）

原因：
两个线程都读到了count=0，
各自加1后都写入1，
导致一次自增丢失
```

### 4.3 解决原子性问题

#### 方案1：使用synchronized

```java
public class AtomicitySolution {
    private int count = 0;
    
    public synchronized void increment() {
        count++;  // synchronized保证原子性
    }
}
```

#### 方案2：使用AtomicInteger

```java
public class AtomicitySolution {
    private AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet();  // 原子操作
    }
}
```

#### 方案3：使用Lock

```java
public class AtomicitySolution {
    private int count = 0;
    private final Lock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }
}
```

---

## 五、有序性问题

### 5.1 什么是有序性？

```
有序性：
程序执行的顺序按照代码的先后顺序执行。

但实际上：
- 编译器可能重排序
- CPU可能重排序
- 内存系统可能重排序
```

### 5.2 指令重排序

```java
// 经典案例：双重检查锁的问题

public class Singleton {
    private static Singleton instance;
    
    public static Singleton getInstance() {
        if (instance == null) {  // 1. 第一次检查
            synchronized (Singleton.class) {
                if (instance == null) {  // 2. 第二次检查
                    instance = new Singleton();  // 3. 创建对象
                }
            }
        }
        return instance;
    }
}
```

**问题分析**：

```
instance = new Singleton() 的执行步骤：

正常顺序：
1. 分配内存空间
2. 初始化对象
3. 将instance指向内存地址

重排序后：
1. 分配内存空间
2. 将instance指向内存地址（此时对象未初始化）
3. 初始化对象

并发问题：
线程A                          线程B
  ↓
执行到步骤2（instance != null）
                                ↓
                              检查instance != null
                                ↓
                              返回instance（未初始化）
                                ↓
                              使用instance（错误！）
  ↓
执行步骤3（初始化对象）
```

### 5.3 as-if-serial语义

```
as-if-serial：
不管怎么重排序，单线程程序的执行结果不能改变。

例子：
int a = 1;  // 1
int b = 2;  // 2
int c = a + b;  // 3

可能的执行顺序：
- 1 → 2 → 3（原始顺序）
- 2 → 1 → 3（重排序，但结果相同）
- 不能：3 → 1 → 2（违反数据依赖）

数据依赖：
- 写后读：a = 1; b = a;
- 写后写：a = 1; a = 2;
- 读后写：b = a; a = 1;

有数据依赖的指令不能重排序
```

### 5.4 解决有序性问题

#### 方案1：使用volatile

```java
public class Singleton {
    private static volatile Singleton instance;  // 添加volatile
    
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                    // volatile禁止重排序
                }
            }
        }
        return instance;
    }
}
```

#### 方案2：使用synchronized

```java
public class Singleton {
    private static Singleton instance;
    
    public static synchronized Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}
// synchronized保证有序性
```

---

## 六、synchronized的内存语义

### 6.1 synchronized如何保证三大特性？

```java
public class SynchronizedExample {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
}
```

**内存语义**：

```
进入synchronized块：
1. 清空工作内存
2. 从主内存重新读取共享变量
3. 保证可见性

执行synchronized块：
1. 所有操作串行执行
2. 保证原子性

退出synchronized块：
1. 将修改刷新到主内存
2. 保证可见性
3. 禁止重排序到synchronized块外
4. 保证有序性
```

**happens-before规则**：

```
监视器锁规则：
unlock操作 happens-before 后续的lock操作

示例：
线程A                          线程B
  ↓
synchronized (lock) {
    count = 1;  // 1
}  // unlock                   ↓
                           synchronized (lock) {  // lock
                               int value = count;  // 2，一定能看到1
                           }

1 happens-before 2（通过unlock和lock）
```

---

## 七、总结

### 7.1 核心要点

1. **JMM的必要性**：屏蔽硬件差异，提供统一的并发语义
2. **主内存与工作内存**：JMM的抽象模型
3. **可见性**：一个线程的修改对其他线程可见
4. **原子性**：操作不可分割
5. **有序性**：程序按代码顺序执行
6. **synchronized**：保证三大特性

### 7.2 三大特性对比

| 特性 | 问题 | 解决方案 |
|------|------|---------|
| 可见性 | 缓存不一致 | volatile、synchronized、Lock、final |
| 原子性 | 操作被中断 | synchronized、Lock、Atomic类 |
| 有序性 | 指令重排序 | volatile、synchronized、happens-before |

### 7.3 思考题

1. **为什么需要JMM？**
2. **主内存和工作内存是什么？**
3. **可见性问题如何产生的？**
4. **为什么i++不是原子操作？**
5. **什么是指令重排序？**
6. **synchronized如何保证三大特性？**

---

**下一章预告**：我们将学习happens-before原则，这是理解JMM的关键。

---

**参考资料**：
- 《Java并发编程实战》第16章
- 《深入理解Java虚拟机》第12章
- JSR 133 (Java Memory Model) FAQ
