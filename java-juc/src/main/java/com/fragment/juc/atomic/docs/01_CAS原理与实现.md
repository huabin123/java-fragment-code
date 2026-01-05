# 第一章：CAS原理与实现 - 无锁编程的基石

> **学习目标**：深入理解CAS算法的原理、实现和应用场景

---

## 一、为什么需要CAS？

### 1.1 传统锁的问题

#### 问题1：性能开销大

```java
// 使用synchronized
public class Counter {
    private int count = 0;
    
    public synchronized void increment() {
        count++; // 简单的自增操作
    }
}

// 问题：
// ❌ 线程阻塞：未获取锁的线程被挂起
// ❌ 上下文切换：线程状态切换开销大
// ❌ 锁竞争：多线程竞争锁，性能下降
```

**synchronized的开销**：

```
获取锁的过程：
1. 检查锁状态
2. 如果锁被占用，线程进入阻塞队列
3. 线程被挂起（用户态 -> 内核态）
4. 等待被唤醒
5. 被唤醒后重新竞争锁
6. 获取锁成功（内核态 -> 用户态）

开销：
- 用户态和内核态切换：约1000个CPU周期
- 线程上下文切换：约几千到几万个CPU周期
- 锁竞争：多个线程竞争，性能线性下降
```

#### 问题2：可能死锁

```java
// 死锁场景
public void transfer(Account from, Account to, int amount) {
    synchronized (from) {
        synchronized (to) {
            from.balance -= amount;
            to.balance += amount;
        }
    }
}

// 线程A：transfer(账户1, 账户2, 100)
// 线程B：transfer(账户2, 账户1, 100)
// 结果：死锁！
```

#### 问题3：优先级反转

```java
// 低优先级线程持有锁
// 高优先级线程等待锁
// 中优先级线程抢占CPU
// 结果：高优先级线程被中优先级线程阻塞
```

---

### 1.2 CAS的解决方案

```java
// 使用CAS（无锁）
public class Counter {
    private AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet(); // 内部使用CAS
    }
}

// 优势：
// ✅ 无需加锁，避免线程阻塞
// ✅ 无上下文切换，性能好
// ✅ 无死锁风险
// ✅ 无优先级反转
```

**CAS的优势**：

```
CAS操作过程：
1. 读取内存值V
2. 比较V和预期值A
3. 如果V == A，更新为新值B
4. 如果V != A，重试（自旋）

优势：
- 无需内核态切换：纯用户态操作
- 无线程阻塞：失败后自旋重试
- 性能好：CPU指令级别的原子操作
```

---

## 二、CAS是什么？

### 2.1 CAS的定义

**CAS（Compare And Swap）**：比较并交换

```java
/**
 * CAS操作的伪代码
 * 
 * @param V 内存位置的值
 * @param A 预期值（Expected）
 * @param B 新值（New）
 * @return 是否成功
 */
boolean compareAndSwap(int V, int A, int B) {
    if (V == A) {
        V = B;
        return true;
    }
    return false;
}
```

**三个操作数**：

```
V（Value）：内存位置的当前值
A（Expected）：预期值
B（New）：新值

操作逻辑：
if (V == A) {
    V = B;  // 更新成功
    return true;
} else {
    return false;  // 更新失败，需要重试
}
```

### 2.2 CAS的工作流程

```
场景：两个线程同时对count进行自增

初始状态：count = 0

线程A                          线程B
  ↓                              ↓
读取count = 0                  读取count = 0
  ↓                              ↓
计算newValue = 1               计算newValue = 1
  ↓                              ↓
CAS(0, 1) ✅                   CAS(0, 1) ❌
count = 1                      重新读取count = 1
                                 ↓
                               计算newValue = 2
                                 ↓
                               CAS(1, 2) ✅
                               count = 2

最终结果：count = 2（正确）
```

**流程图**：

```
开始
  ↓
读取当前值V
  ↓
计算新值B
  ↓
CAS(V, B)
  ↓
成功？
├─ 是 → 返回
└─ 否 → 重新读取V → 重试
```

---

## 三、CAS的底层实现

### 3.1 Java层面的实现

```java
// AtomicInteger的incrementAndGet实现
public final int incrementAndGet() {
    for (;;) {  // 无限循环，直到成功
        int current = get();  // 获取当前值
        int next = current + 1;  // 计算新值
        if (compareAndSet(current, next))  // CAS操作
            return next;
        // CAS失败，继续循环重试
    }
}

// compareAndSet的实现
public final boolean compareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
}
```

### 3.2 Unsafe类的实现

```java
// Unsafe类提供CAS操作
public final class Unsafe {
    /**
     * CAS操作
     * 
     * @param o 对象
     * @param offset 字段偏移量
     * @param expected 预期值
     * @param x 新值
     * @return 是否成功
     */
    public final native boolean compareAndSwapInt(
        Object o, long offset, int expected, int x);
    
    public final native boolean compareAndSwapLong(
        Object o, long offset, long expected, long x);
    
    public final native boolean compareAndSwapObject(
        Object o, long offset, Object expected, Object x);
}
```

**为什么需要Unsafe？**

```
Unsafe提供了：
1. 直接内存访问
2. CAS原子操作
3. 线程调度（park/unpark）
4. 对象操作

CAS需要：
- 直接操作内存
- CPU指令级别的原子性
- Java无法直接提供，需要native方法
```

### 3.3 CPU指令级别的实现

```
x86架构：
- 使用CMPXCHG指令
- 配合LOCK前缀保证原子性

CMPXCHG指令：
LOCK CMPXCHG [内存地址], 新值

工作原理：
1. LOCK前缀锁定总线或缓存行
2. 比较内存值和累加器（EAX）的值
3. 如果相等，将新值写入内存
4. 如果不等，将内存值加载到累加器
5. 设置标志位（ZF）表示是否成功
```

**为什么CAS是原子的？**

```
硬件保证：
1. LOCK前缀：
   - 锁定缓存行（Cache Line Locking）
   - 或锁定总线（Bus Locking）
   
2. 缓存一致性协议（MESI）：
   - Modified：已修改
   - Exclusive：独占
   - Shared：共享
   - Invalid：无效
   
3. 原子性保证：
   - 单条CPU指令
   - 不可被中断
   - 不可被分割
```

---

## 四、ABA问题

### 4.1 什么是ABA问题？

```java
// 场景：栈的pop操作

初始状态：
栈顶 -> A -> B -> C

线程1：准备pop A
  1. 读取栈顶：A
  2. 读取A.next：B
  3. 准备CAS：将栈顶从A改为B

线程2：在线程1的CAS之前执行
  1. pop A
  2. pop B
  3. push A
  
现在栈：A -> C

线程1：继续执行CAS
  1. CAS成功（栈顶仍是A）
  2. 将栈顶改为B
  
结果：B已经不在栈中了！
栈变成：B -> ???（悬空指针）
```

**ABA问题的本质**：

```
值从A变为B再变回A
CAS只比较值，无法检测中间状态的变化
可能导致逻辑错误
```

### 4.2 ABA问题的危害

```java
// 示例：账户余额

初始：balance = 100

线程1：
  读取balance = 100
  准备转账-50
  
线程2：
  扣款-100（balance = 0）
  充值+100（balance = 100）
  
线程1：
  CAS(100, 50) 成功
  balance = 50
  
问题：
- 线程1不知道中间发生了扣款和充值
- 可能导致业务逻辑错误
```

### 4.3 解决ABA问题

#### 方案1：AtomicStampedReference（版本号）

```java
/**
 * 带版本号的原子引用
 */
public class AtomicStampedReference<V> {
    private static class Pair<T> {
        final T reference;  // 引用
        final int stamp;    // 版本号
    }
    
    /**
     * CAS操作（同时比较引用和版本号）
     */
    public boolean compareAndSet(
        V expectedReference,   // 预期引用
        V newReference,        // 新引用
        int expectedStamp,     // 预期版本号
        int newStamp)          // 新版本号
    {
        Pair<V> current = pair;
        return expectedReference == current.reference &&
               expectedStamp == current.stamp &&
               ((newReference == current.reference &&
                 newStamp == current.stamp) ||
                casPair(current, Pair.of(newReference, newStamp)));
    }
}
```

**使用示例**：

```java
// 创建带版本号的引用
AtomicStampedReference<Integer> ref = 
    new AtomicStampedReference<>(100, 0);

// 线程1
int stamp1 = ref.getStamp();  // 版本号0
ref.compareAndSet(100, 50, stamp1, stamp1 + 1);

// 线程2
int stamp2 = ref.getStamp();  // 版本号0
ref.compareAndSet(100, 0, stamp2, stamp2 + 1);  // 成功，版本号变为1
ref.compareAndSet(0, 100, stamp2 + 1, stamp2 + 2);  // 成功，版本号变为2

// 线程1继续
ref.compareAndSet(100, 50, stamp1, stamp1 + 1);  // 失败！版本号不匹配
```

#### 方案2：AtomicMarkableReference（标记位）

```java
/**
 * 带标记位的原子引用
 */
public class AtomicMarkableReference<V> {
    private static class Pair<T> {
        final T reference;  // 引用
        final boolean mark; // 标记位
    }
    
    /**
     * CAS操作（同时比较引用和标记位）
     */
    public boolean compareAndSet(
        V expectedReference,
        V newReference,
        boolean expectedMark,
        boolean newMark)
    {
        // 实现类似AtomicStampedReference
    }
}
```

**使用场景**：

```java
// 标记节点是否被删除
AtomicMarkableReference<Node> nodeRef = 
    new AtomicMarkableReference<>(node, false);

// 删除节点
nodeRef.compareAndSet(node, null, false, true);  // 标记为已删除
```

---

## 五、CAS vs synchronized

### 5.1 性能对比

```java
// 测试代码
public class CASvsSynchronized {
    private static final int THREAD_COUNT = 10;
    private static final int ITERATIONS = 1000000;
    
    // 使用synchronized
    static class SyncCounter {
        private int count = 0;
        public synchronized void increment() {
            count++;
        }
    }
    
    // 使用CAS
    static class CASCounter {
        private AtomicInteger count = new AtomicInteger(0);
        public void increment() {
            count.incrementAndGet();
        }
    }
}

// 性能结果（10个线程，各执行100万次）：
// synchronized：约2000ms
// CAS：约500ms
// CAS快4倍！
```

**性能对比表**：

| 场景 | synchronized | CAS |
|------|-------------|-----|
| 低竞争 | 快 | 更快 |
| 中竞争 | 慢 | 快 |
| 高竞争 | 很慢 | 较慢（自旋开销） |
| 内存占用 | 小 | 小 |
| 死锁风险 | 有 | 无 |

### 5.2 适用场景

```java
// ✅ 使用CAS的场景
1. 简单的原子操作（自增、自减）
2. 低到中等竞争
3. 读多写少
4. 无需阻塞线程

// ✅ 使用synchronized的场景
1. 复杂的复合操作
2. 高竞争（避免过度自旋）
3. 需要等待条件（wait/notify）
4. 代码块较大
```

---

## 六、CAS的优缺点

### 6.1 优点

```
✅ 性能好：
   - 无锁，避免线程阻塞
   - 无上下文切换
   - CPU指令级别的原子操作

✅ 无死锁：
   - 不需要获取锁
   - 失败后重试，不会阻塞

✅ 实时性好：
   - 不会被挂起
   - 响应时间可预测
```

### 6.2 缺点

```
❌ ABA问题：
   - 值可能被修改后又改回
   - 需要版本号机制

❌ 自旋开销：
   - 高竞争下，大量自旋消耗CPU
   - 可能比synchronized还慢

❌ 只能保证单个变量：
   - 无法保证多个变量的原子性
   - 需要AtomicReference包装对象
```

### 6.3 自旋开销示例

```java
// 高竞争场景
AtomicInteger count = new AtomicInteger(0);

// 100个线程同时执行
for (int i = 0; i < 1000000; i++) {
    count.incrementAndGet();
}

// 问题：
// - 大量CAS失败
// - 不断自旋重试
// - CPU使用率高
// - 可能比synchronized慢
```

---

## 七、CAS的应用场景

### 7.1 计数器

```java
// 全局计数器
public class GlobalCounter {
    private AtomicLong count = new AtomicLong(0);
    
    public void increment() {
        count.incrementAndGet();
    }
    
    public long get() {
        return count.get();
    }
}
```

### 7.2 无锁队列

```java
// 无锁栈
public class LockFreeStack<E> {
    private AtomicReference<Node<E>> top = new AtomicReference<>();
    
    public void push(E item) {
        Node<E> newHead = new Node<>(item);
        Node<E> oldHead;
        do {
            oldHead = top.get();
            newHead.next = oldHead;
        } while (!top.compareAndSet(oldHead, newHead));
    }
    
    public E pop() {
        Node<E> oldHead;
        Node<E> newHead;
        do {
            oldHead = top.get();
            if (oldHead == null) return null;
            newHead = oldHead.next;
        } while (!top.compareAndSet(oldHead, newHead));
        return oldHead.item;
    }
}
```

### 7.3 乐观锁

```java
// 版本号机制
public class OptimisticLock {
    private AtomicInteger version = new AtomicInteger(0);
    private volatile Data data;
    
    public Data read() {
        return data;
    }
    
    public boolean update(Data newData, int expectedVersion) {
        // 比较版本号
        if (version.compareAndSet(expectedVersion, expectedVersion + 1)) {
            data = newData;
            return true;
        }
        return false;
    }
}
```

---

## 八、总结

### 8.1 核心要点

1. **CAS定义**：比较并交换，三个操作数（V、A、B）
2. **工作原理**：比较内存值和预期值，相等则更新
3. **底层实现**：CPU指令（CMPXCHG + LOCK前缀）
4. **ABA问题**：值变化后又变回，需要版本号解决
5. **性能特点**：低竞争快，高竞争可能慢

### 8.2 使用建议

```
✅ 适合CAS：
   - 简单原子操作
   - 低到中等竞争
   - 读多写少

❌ 不适合CAS：
   - 复杂复合操作
   - 高竞争场景
   - 需要阻塞等待
```

### 8.3 思考题

1. **CAS为什么是原子的？**
2. **ABA问题的本质是什么？**
3. **什么时候CAS比synchronized慢？**
4. **如何避免CAS的自旋开销？**

---

**下一章预告**：我们将学习基本类型原子类（AtomicInteger、AtomicLong等）的使用和实现原理。

---

**参考资料**：
- 《Java并发编程实战》第15章
- 《Java并发编程的艺术》第7章
- Intel x86指令集手册
