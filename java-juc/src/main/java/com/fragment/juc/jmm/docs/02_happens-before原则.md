# 第二章：happens-before原则 - JMM的核心规则

> **学习目标**：深入理解happens-before原则，掌握并发程序的正确性分析方法

---

## 一、为什么需要happens-before？

### 1.1 并发程序的正确性问题

```java
// 问题：如何保证线程B能看到线程A的修改？

// 线程A
int data = 42;
boolean ready = true;

// 线程B
if (ready) {
    int value = data;  // 能看到42吗？
}

// 问题：
// 1. 线程B能看到ready=true吗？（可见性）
// 2. 如果看到ready=true，能看到data=42吗？（有序性）
// 3. 如何保证？
```

### 1.2 happens-before的定义

```
happens-before：
如果操作A happens-before 操作B，
那么A的执行结果对B可见，
且A的执行顺序在B之前。

注意：
- happens-before不是时间上的先后
- 是可见性和有序性的保证
- 是JMM的核心规则
```

**两层含义**：

```
1. 可见性保证：
   A happens-before B
   → A的结果对B可见

2. 有序性保证：
   A happens-before B
   → A不会被重排序到B之后
```

---

## 二、happens-before的8条规则

### 2.1 程序顺序规则（Program Order Rule）

```
规则：
在一个线程内，按照代码顺序，
前面的操作 happens-before 后面的操作。

注意：
- 只是代码顺序，不是执行顺序
- 允许重排序，但要保证as-if-serial
```

**示例**：

```java
// 单线程
int a = 1;  // 1
int b = 2;  // 2
int c = a + b;  // 3

// happens-before关系：
// 1 happens-before 2
// 2 happens-before 3
// 1 happens-before 3（传递性）

// 可能的执行顺序：
// - 1 → 2 → 3（原始顺序）
// - 2 → 1 → 3（重排序，但不影响结果）
// - 不能：3 → 1 → 2（违反数据依赖）
```

### 2.2 监视器锁规则（Monitor Lock Rule）

```
规则：
对一个锁的unlock操作 happens-before 
后续对同一个锁的lock操作。

关键词：
- 同一个锁
- unlock在前，lock在后
```

**示例**：

```java
int data = 0;
Object lock = new Object();

// 线程A
synchronized (lock) {
    data = 42;  // 1
}  // unlock

// 线程B
synchronized (lock) {  // lock
    int value = data;  // 2，一定能看到42
}

// happens-before关系：
// 1 happens-before unlock（程序顺序）
// unlock happens-before lock（监视器锁规则）
// lock happens-before 2（程序顺序）
// 因此：1 happens-before 2（传递性）
```

**内存语义**：

```
线程A                          线程B
  ↓
data = 42（写入工作内存）
  ↓
synchronized退出（unlock）
  ↓
刷新到主内存
                                ↓
                           synchronized进入（lock）
                                ↓
                           从主内存读取
                                ↓
                           value = data（能看到42）
```

### 2.3 volatile变量规则（Volatile Variable Rule）

```
规则：
对一个volatile变量的写操作 happens-before
后续对这个变量的读操作。

关键词：
- 同一个volatile变量
- 写在前，读在后
```

**示例**：

```java
int data = 0;
volatile boolean ready = false;

// 线程A
data = 42;          // 1
ready = true;       // 2（volatile写）

// 线程B
if (ready) {        // 3（volatile读）
    int value = data;  // 4，一定能看到42
}

// happens-before关系：
// 1 happens-before 2（程序顺序）
// 2 happens-before 3（volatile规则）
// 3 happens-before 4（程序顺序）
// 因此：1 happens-before 4（传递性）
```

**内存语义**：

```
线程A                          线程B
  ↓
data = 42
  ↓
ready = true（volatile写）
  ↓
立即刷新到主内存
  ↓
禁止前面的操作重排序到后面
                                ↓
                           if (ready)（volatile读）
                                ↓
                           从主内存读取
                                ↓
                           禁止后面的操作重排序到前面
                                ↓
                           value = data（能看到42）
```

### 2.4 线程启动规则（Thread Start Rule）

```
规则：
Thread.start()的调用 happens-before
该线程内的任何操作。
```

**示例**：

```java
int data = 0;

// 主线程
data = 42;  // 1
Thread t = new Thread(() -> {
    int value = data;  // 2，一定能看到42
});
t.start();  // 3

// happens-before关系：
// 1 happens-before 3（程序顺序）
// 3 happens-before 2（线程启动规则）
// 因此：1 happens-before 2（传递性）
```

### 2.5 线程终止规则（Thread Termination Rule）

```
规则：
线程内的任何操作 happens-before
其他线程检测到该线程终止。

检测方式：
- Thread.join()返回
- Thread.isAlive()返回false
```

**示例**：

```java
int data = 0;

Thread t = new Thread(() -> {
    data = 42;  // 1
});
t.start();
t.join();  // 2
int value = data;  // 3，一定能看到42

// happens-before关系：
// 1 happens-before 2（线程终止规则）
// 2 happens-before 3（程序顺序）
// 因此：1 happens-before 3（传递性）
```

### 2.6 线程中断规则（Thread Interruption Rule）

```
规则：
对线程interrupt()的调用 happens-before
被中断线程检测到中断事件的发生。

检测方式：
- Thread.interrupted()
- Thread.isInterrupted()
- 抛出InterruptedException
```

**示例**：

```java
Thread t = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        // 工作
    }
});
t.start();
t.interrupt();  // 1

// 线程t内：
if (Thread.currentThread().isInterrupted()) {  // 2
    // 一定能检测到中断
}

// 1 happens-before 2（线程中断规则）
```

### 2.7 对象终结规则（Finalizer Rule）

```
规则：
对象的构造函数执行结束 happens-before
它的finalize()方法的开始。
```

**示例**：

```java
public class Resource {
    private int data;
    
    public Resource() {
        data = 42;  // 1
    }
    
    @Override
    protected void finalize() {
        int value = data;  // 2，一定能看到42
    }
}

// 1 happens-before 2（对象终结规则）
```

### 2.8 传递性（Transitivity）

```
规则：
如果A happens-before B，
且B happens-before C，
则A happens-before C。
```

**示例**：

```java
int data = 0;
volatile boolean ready = false;

// 线程A
data = 42;      // 1
ready = true;   // 2（volatile写）

// 线程B
if (ready) {    // 3（volatile读）
    int value = data;  // 4
}

// happens-before链：
// 1 happens-before 2（程序顺序）
// 2 happens-before 3（volatile规则）
// 3 happens-before 4（程序顺序）
// 因此：1 happens-before 4（传递性）
```

---

## 三、happens-before的应用

### 3.1 分析并发程序的正确性

```java
// 问题：这个程序正确吗？

class Example {
    private int a = 0;
    private int b = 0;
    
    public void writer() {
        a = 1;
        b = 2;
    }
    
    public void reader() {
        int r1 = b;
        int r2 = a;
    }
}

// 分析：
// 1. writer和reader没有happens-before关系
// 2. 可能的结果：
//    - r1=0, r2=0（都没看到）
//    - r1=2, r2=1（都看到了）
//    - r1=2, r2=0（看到b但没看到a，重排序）
//    - r1=0, r2=1（理论上不可能，但实际可能）

// 结论：不正确，需要同步
```

### 3.2 使用volatile保证可见性

```java
class Example {
    private int a = 0;
    private volatile int b = 0;  // 添加volatile
    
    public void writer() {
        a = 1;          // 1
        b = 2;          // 2（volatile写）
    }
    
    public void reader() {
        int r1 = b;     // 3（volatile读）
        int r2 = a;     // 4
    }
}

// happens-before分析：
// 1 happens-before 2（程序顺序）
// 2 happens-before 3（volatile规则）
// 3 happens-before 4（程序顺序）
// 因此：1 happens-before 4（传递性）

// 结论：正确，reader一定能看到writer的修改
```

### 3.3 双重检查锁的正确性

```java
// 错误版本
class Singleton {
    private static Singleton instance;
    
    public static Singleton getInstance() {
        if (instance == null) {  // 1
            synchronized (Singleton.class) {
                if (instance == null) {  // 2
                    instance = new Singleton();  // 3
                }
            }
        }
        return instance;  // 4
    }
}

// 问题分析：
// 3可能重排序为：
//   3.1 分配内存
//   3.2 instance指向内存（此时对象未初始化）
//   3.3 初始化对象
// 如果另一个线程在3.2和3.3之间执行1，
// 会看到instance != null，但对象未初始化

// 正确版本
class Singleton {
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

// happens-before分析：
// volatile写 happens-before volatile读
// 保证对象完全初始化后才对其他线程可见
```

---

## 四、happens-before与JMM的关系

### 4.1 JMM的设计目标

```
1. 为程序员提供简单的内存模型：
   - 只要遵循happens-before规则
   - 就能写出正确的并发程序

2. 为编译器和处理器提供优化空间：
   - 在不违反happens-before的前提下
   - 可以进行重排序优化
```

### 4.2 happens-before与as-if-serial

```
as-if-serial：
单线程程序的执行结果不能改变

happens-before：
多线程程序的可见性和有序性保证

关系：
- as-if-serial是单线程的保证
- happens-before是多线程的保证
- 两者共同构成JMM的基础
```

### 4.3 happens-before的实现

```
happens-before是规范，不是实现

实现方式：
1. volatile：内存屏障
2. synchronized：监视器锁
3. final：特殊处理
4. 其他：根据规则推导
```

---

## 五、常见误区

### 5.1 误区1：happens-before就是时间先后

```java
// 错误理解
int a = 1;  // 1
int b = 2;  // 2

// 1 happens-before 2
// 不代表1一定在2之前执行

// 正确理解：
// - 1的结果对2可见
// - 1不会被重排序到2之后
// - 但可能同时执行或2先执行（只要保证结果正确）
```

### 5.2 误区2：没有happens-before就一定不可见

```java
// 错误理解
int a = 1;  // 线程A
int b = a;  // 线程B

// 没有happens-before关系
// 不代表线程B一定看不到a=1

// 正确理解：
// - 没有happens-before，不保证可见
// - 但可能可见（运气好）
// - 不能依赖这种可能性
```

### 5.3 误区3：volatile保证原子性

```java
// 错误理解
volatile int count = 0;

public void increment() {
    count++;  // 以为是原子的
}

// 正确理解：
// - volatile只保证可见性和有序性
// - count++不是原子操作（读-改-写）
// - 需要使用synchronized或AtomicInteger
```

---

## 六、实战技巧

### 6.1 利用volatile建立happens-before

```java
// 技巧：用volatile变量作为"门闩"

class DataHolder {
    private int data1;
    private int data2;
    private volatile boolean ready = false;
    
    public void writer() {
        data1 = 1;      // 1
        data2 = 2;      // 2
        ready = true;   // 3（volatile写）
    }
    
    public void reader() {
        if (ready) {    // 4（volatile读）
            int r1 = data1;  // 5
            int r2 = data2;  // 6
        }
    }
}

// happens-before链：
// 1,2 happens-before 3（程序顺序）
// 3 happens-before 4（volatile规则）
// 4 happens-before 5,6（程序顺序）
// 因此：1,2 happens-before 5,6（传递性）
```

### 6.2 利用synchronized建立happens-before

```java
// 技巧：用synchronized保护共享变量

class Counter {
    private int count = 0;
    private final Object lock = new Object();
    
    public void increment() {
        synchronized (lock) {
            count++;
        }
    }
    
    public int get() {
        synchronized (lock) {
            return count;
        }
    }
}

// happens-before：
// increment的unlock happens-before get的lock
// 保证get能看到increment的修改
```

### 6.3 利用final建立happens-before

```java
// 技巧：用final保证初始化安全

class ImmutablePoint {
    private final int x;
    private final int y;
    
    public ImmutablePoint(int x, int y) {
        this.x = x;  // 1
        this.y = y;  // 2
    }  // 3（构造函数结束）
    
    public int getX() {
        return x;  // 4
    }
}

// happens-before：
// 1,2 happens-before 3（程序顺序）
// 3 happens-before 4（final规则）
// 因此：1,2 happens-before 4（传递性）
// 保证getX一定能看到正确的x值
```

---

## 七、总结

### 7.1 核心要点

1. **happens-before定义**：可见性和有序性的保证
2. **8条规则**：程序顺序、监视器锁、volatile、线程启动/终止/中断、对象终结、传递性
3. **应用**：分析并发程序的正确性
4. **实现**：内存屏障、监视器锁等

### 7.2 规则总结

| 规则 | 说明 | 典型应用 |
|------|------|---------|
| 程序顺序 | 单线程内按代码顺序 | 基础规则 |
| 监视器锁 | unlock happens-before lock | synchronized |
| volatile | 写 happens-before 读 | 状态标志 |
| 线程启动 | start() happens-before 线程内操作 | 线程通信 |
| 线程终止 | 线程内操作 happens-before join() | 等待结果 |
| 线程中断 | interrupt() happens-before 检测中断 | 中断处理 |
| 对象终结 | 构造 happens-before finalize() | 资源清理 |
| 传递性 | A→B, B→C ⇒ A→C | 组合规则 |

### 7.3 思考题

1. **happens-before是时间先后吗？**
2. **如何利用volatile建立happens-before？**
3. **为什么双重检查锁需要volatile？**
4. **没有happens-before就一定不可见吗？**

---

**下一章预告**：我们将深入学习volatile的实现原理和使用场景。

---

**参考资料**：
- 《Java并发编程实战》第16章
- JSR 133 (Java Memory Model) FAQ
- The Java Language Specification §17.4
