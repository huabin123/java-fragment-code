# 对象头与Monitor机制（重量级锁）

## 1. Synchronized的底层实现原理

### 1.1 问题1：Synchronized是如何实现锁的？

要理解Synchronized的实现原理，需要先了解Java对象在内存中的结构。

**Java对象的内存布局**：

```
┌─────────────────────────────────────┐
│         Java对象内存布局             │
├─────────────────────────────────────┤
│  1. 对象头 (Object Header)          │
│     ├─ Mark Word (标记字)           │
│     └─ Class Pointer (类型指针)     │
├─────────────────────────────────────┤
│  2. 实例数据 (Instance Data)        │
├─────────────────────────────────────┤
│  3. 对齐填充 (Padding)              │
└─────────────────────────────────────┘
```

**Synchronized的锁信息就存储在对象头的Mark Word中！**

---

### 1.2 问题2：对象头的结构是什么样的？

#### **32位JVM的对象头结构**

**对象头 = Mark Word (4字节) + Class Pointer (4字节)**

```
┌──────────────────────────────────────────────────────────┐
│                    Mark Word (32 bits)                   │
├──────────────────────────────────────────────────────────┤
│  锁状态  │  25 bit        │  4 bit  │ 1 bit │  2 bit    │
│         │                │         │       │           │
│  无锁    │  hashCode(25) │ age(4)  │ 0     │  01       │
│  偏向锁  │  线程ID(23)   │ epoch(2)│ age(4)│ 1 │ 01    │
│  轻量锁  │  指向栈中锁记录的指针(30)        │  00       │
│  重量锁  │  指向Monitor对象的指针(30)       │  10       │
│  GC标记  │  空                              │  11       │
└──────────────────────────────────────────────────────────┘
```

#### **64位JVM的对象头结构**

**对象头 = Mark Word (8字节) + Class Pointer (4字节，开启压缩指针)**

```
┌────────────────────────────────────────────────────────────────┐
│                    Mark Word (64 bits)                         │
├────────────────────────────────────────────────────────────────┤
│  锁状态  │  56 bit                    │  1 bit  │  2 bit       │
│         │                            │         │              │
│  无锁    │  unused(25) │ hashCode(31)│ age(4) │ 0 │  01      │
│  偏向锁  │  线程ID(54) │ epoch(2)    │ age(4) │ 1 │  01      │
│  轻量锁  │  指向栈中锁记录的指针(62)            │  00          │
│  重量锁  │  指向Monitor对象的指针(62)           │  10          │
│  GC标记  │  空                                  │  11          │
└────────────────────────────────────────────────────────────────┘
```

**字段说明**：

| 字段 | 说明 |
|------|------|
| **hashCode** | 对象的hashCode值（调用hashCode()后才生成） |
| **age** | 分代年龄（GC次数，最大15） |
| **biased_lock** | 偏向锁标志位（1=偏向锁，0=无锁） |
| **lock** | 锁状态标志位（01=无锁/偏向锁，00=轻量锁，10=重量锁，11=GC标记） |
| **thread ID** | 持有偏向锁的线程ID |
| **epoch** | 偏向时间戳（用于批量重偏向） |
| **ptr_to_lock_record** | 指向栈中锁记录的指针 |
| **ptr_to_heavyweight_monitor** | 指向Monitor对象的指针 |

---

### 1.3 问题3：如何查看对象头信息？

可以使用JOL（Java Object Layout）工具查看对象头：

**添加依赖**：

```xml
<dependency>
    <groupId>org.openjdk.jol</groupId>
    <artifactId>jol-core</artifactId>
    <version>0.16</version>
</dependency>
```

**示例代码**：

```java
import org.openjdk.jol.info.ClassLayout;

public class ObjectHeaderDemo {
    public static void main(String[] args) {
        Object obj = new Object();
        
        // 查看对象头
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        
        // 加锁后再查看
        synchronized (obj) {
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }
    }
}
```

**输出示例（64位JVM）**：

```
# 无锁状态
java.lang.Object object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                VALUE
      0     4        (object header)            01 00 00 00 (00000001 00000000 00000000 00000000) (1)
      4     4        (object header)            00 00 00 00 (00000000 00000000 00000000 00000000) (0)
      8     4        (object header)            e5 01 00 f8 (11100101 00000001 00000000 11111000) (-134217243)
     12     4        (loss due to the next object alignment)

# 重量级锁状态
java.lang.Object object internals:
 OFFSET  SIZE   TYPE DESCRIPTION                VALUE
      0     4        (object header)            ca 48 00 1e (11001010 01001000 00000000 00011110) (503793866)
      4     4        (object header)            00 70 00 00 (00000000 01110000 00000000 00000000) (28672)
      8     4        (object header)            e5 01 00 f8 (11100101 00000001 00000000 11111000) (-134217243)
     12     4        (loss due to the next object alignment)
```

**解读**：
- 前8字节是Mark Word
- 最后2位是锁标志位（01=无锁，10=重量锁）
- 重量锁状态下，Mark Word存储的是Monitor对象的指针

---

## 2. Monitor机制详解

### 2.1 问题4：什么是Monitor？

**Monitor（监视器）** 是操作系统提供的一种同步机制，Java的重量级锁就是基于Monitor实现的。

**Monitor的核心组件**：

```
┌─────────────────────────────────────────┐
│           Monitor 对象结构               │
├─────────────────────────────────────────┤
│  _owner        : 持有锁的线程           │
│  _recursions   : 锁的重入次数           │
│  _EntryList    : 等待获取锁的线程队列   │
│  _WaitSet      : 调用wait()的线程队列   │
│  _count        : 等待线程数量           │
└─────────────────────────────────────────┘
```

**Monitor的工作流程**：

```
                    ┌─────────────┐
                    │   线程池    │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  尝试获取锁  │
                    └──────┬──────┘
                           │
                ┌──────────┴──────────┐
                │                     │
           成功 │                     │ 失败
                ▼                     ▼
        ┌──────────────┐      ┌──────────────┐
        │  _owner      │      │  _EntryList  │
        │  (持有锁)    │      │  (阻塞等待)  │
        └──────┬───────┘      └──────────────┘
               │                      ▲
               │ 调用wait()           │
               ▼                      │
        ┌──────────────┐              │
        │  _WaitSet    │              │
        │  (等待唤醒)  │              │
        └──────┬───────┘              │
               │                      │
               │ 被notify()           │
               └──────────────────────┘
```

---

### 2.2 问题5：Monitor的工作原理是什么？

#### **场景：3个线程竞争同一把锁**

**初始状态**：

```
Monitor对象：
  _owner = null
  _EntryList = []
  _WaitSet = []
```

**时刻1：线程A获取锁**

```java
synchronized (obj) {
    // 线程A执行业务逻辑
}
```

```
Monitor对象：
  _owner = 线程A
  _recursions = 1
  _EntryList = []
  _WaitSet = []
```

**时刻2：线程B和C尝试获取锁（失败）**

```
Monitor对象：
  _owner = 线程A
  _recursions = 1
  _EntryList = [线程B, 线程C]  ← 进入等待队列
  _WaitSet = []

线程B和C的状态：BLOCKED（阻塞状态）
```

**时刻3：线程A调用wait()**

```java
synchronized (obj) {
    obj.wait(); // 释放锁，进入等待集合
}
```

```
Monitor对象：
  _owner = 线程B  ← 从EntryList中唤醒一个线程
  _recursions = 1
  _EntryList = [线程C]
  _WaitSet = [线程A]  ← 线程A进入等待集合

线程A的状态：WAITING（等待状态）
```

**时刻4：线程B调用notify()**

```java
synchronized (obj) {
    obj.notify(); // 唤醒一个等待线程
}
```

```
Monitor对象：
  _owner = 线程B
  _recursions = 1
  _EntryList = [线程C, 线程A]  ← 线程A被移到EntryList
  _WaitSet = []

线程A的状态：BLOCKED（重新竞争锁）
```

**时刻5：线程B释放锁**

```java
synchronized (obj) {
    // 执行完毕，退出同步块
}
```

```
Monitor对象：
  _owner = 线程C  ← 从EntryList中唤醒线程C
  _recursions = 1
  _EntryList = [线程A]
  _WaitSet = []
```

---

### 2.3 问题6：Monitor的源码实现是什么样的？

**ObjectMonitor的核心源码**（C++实现，位于HotSpot虚拟机）：

```cpp
ObjectMonitor() {
    _header       = NULL;
    _count        = 0;      // 等待线程数
    _waiters      = 0;
    _recursions   = 0;      // 重入次数
    _object       = NULL;
    _owner        = NULL;   // 持有锁的线程
    _WaitSet      = NULL;   // 等待集合（调用wait的线程）
    _WaitSetLock  = 0;
    _Responsible  = NULL;
    _succ         = NULL;
    _cxq          = NULL;   // 竞争队列
    FreeNext      = NULL;
    _EntryList    = NULL;   // 等待锁的线程队列
    _SpinFreq     = 0;
    _SpinClock    = 0;
    OwnerIsThread = 0;
}
```

**获取锁的核心逻辑**：

```cpp
void ATTR ObjectMonitor::enter(TRAPS) {
    Thread * const Self = THREAD;
    
    // 1. 尝试快速获取锁（CAS操作）
    void * cur = Atomic::cmpxchg_ptr(Self, &_owner, NULL);
    if (cur == NULL) {
        // 成功获取锁
        return;
    }
    
    // 2. 如果是重入（当前线程已持有锁）
    if (cur == Self) {
        _recursions++; // 重入次数+1
        return;
    }
    
    // 3. 自旋尝试获取锁
    if (Self->is_lock_owned((address)cur)) {
        _recursions = 1;
        _owner = Self;
        return;
    }
    
    // 4. 自旋失败，进入阻塞状态
    EnterI(THREAD);
}

void ObjectMonitor::EnterI(TRAPS) {
    Thread * Self = THREAD;
    
    // 再次尝试获取锁
    if (TryLock(Self) > 0) {
        return;
    }
    
    // 将线程加入_EntryList
    ObjectWaiter node(Self);
    Self->_ParkEvent->reset();
    node._prev = (ObjectWaiter *) 0xBAD;
    node.TState = ObjectWaiter::TS_CXQ;
    
    // 自旋等待
    for (;;) {
        if (TryLock(Self) > 0) break;
        
        // 进入阻塞状态（调用操作系统的park）
        Self->_ParkEvent->park();
    }
}
```

**释放锁的核心逻辑**：

```cpp
void ATTR ObjectMonitor::exit(bool not_suspended, TRAPS) {
    Thread * Self = THREAD;
    
    // 1. 检查是否是锁的持有者
    if (THREAD != _owner) {
        throw IllegalMonitorStateException;
    }
    
    // 2. 处理重入
    if (_recursions != 0) {
        _recursions--; // 重入次数-1
        return;
    }
    
    // 3. 释放锁
    _owner = NULL;
    
    // 4. 唤醒等待线程
    ObjectWaiter * w = NULL;
    
    // 从_EntryList或_cxq中选择一个线程唤醒
    if (_EntryList != NULL) {
        w = _EntryList;
        _EntryList = w->_next;
    } else {
        w = _cxq;
        // ...
    }
    
    if (w != NULL) {
        // 唤醒线程（调用操作系统的unpark）
        w->_thread->_ParkEvent->unpark();
    }
}
```

---

### 2.4 问题7：为什么Monitor是重量级锁？

**重量级锁的性能开销**：

```
┌─────────────────────────────────────────────┐
│          重量级锁的性能开销                  │
├─────────────────────────────────────────────┤
│  1. 用户态 → 内核态切换                     │
│     - 保存用户态寄存器                      │
│     - 切换到内核栈                          │
│     - 执行系统调用                          │
│     - 切换回用户态                          │
│     开销：约 1000-1500 个CPU周期            │
├─────────────────────────────────────────────┤
│  2. 线程阻塞与唤醒                          │
│     - park()：将线程挂起（阻塞）            │
│     - unpark()：唤醒线程                    │
│     开销：约 10000 个CPU周期                │
├─────────────────────────────────────────────┤
│  3. 上下文切换                              │
│     - 保存当前线程上下文                    │
│     - 加载新线程上下文                      │
│     - 刷新CPU缓存                           │
│     开销：约 5000-10000 个CPU周期           │
└─────────────────────────────────────────────┘
```

**对比：轻量级操作的开销**

```
CAS操作：约 10-50 个CPU周期
普通方法调用：约 5-10 个CPU周期
```

**结论**：重量级锁的开销是轻量级操作的 **100-1000倍**！

---

### 2.5 问题8：为什么要设计Monitor这种机制？

虽然Monitor性能开销大，但它有不可替代的优势：

#### **优势1：支持线程阻塞与唤醒**

```java
// 场景：生产者-消费者模式
synchronized (queue) {
    while (queue.isEmpty()) {
        queue.wait(); // 阻塞等待，释放CPU
    }
    Object item = queue.take();
}
```

**如果没有Monitor**：
- 只能使用自旋等待（while循环）
- 浪费CPU资源
- 无法实现高效的线程协作

#### **优势2：支持可重入**

```java
public synchronized void method1() {
    method2(); // 可重入
}

public synchronized void method2() {
    // 同一线程可以再次获取锁
}
```

**Monitor的_recursions字段记录重入次数**：

```
第一次进入：_recursions = 1
第二次进入：_recursions = 2
退出一次：_recursions = 1
退出两次：_recursions = 0，释放锁
```

#### **优势3：公平性保证**

```
_EntryList是一个队列，按FIFO顺序唤醒线程
避免了线程饥饿问题
```

#### **优势4：支持wait/notify机制**

```java
// 线程协作
synchronized (obj) {
    obj.wait();    // 释放锁，进入_WaitSet
    obj.notify();  // 唤醒_WaitSet中的线程
}
```

---

## 3. Synchronized字节码分析

### 3.1 问题9：Synchronized在字节码层面是如何实现的？

#### **场景1：同步代码块**

**Java代码**：

```java
public void syncBlock() {
    synchronized (this) {
        count++;
    }
}
```

**字节码**：

```
public void syncBlock();
  Code:
     0: aload_0                    // 加载this
     1: dup                        // 复制栈顶元素
     2: astore_1                   // 存储到局部变量表
     3: monitorenter               // ← 进入Monitor（获取锁）
     4: aload_0
     5: dup
     6: getfield      #2           // 获取count字段
     9: iconst_1
    10: iadd
    11: putfield     #2           // 写回count字段
    14: aload_1
    15: monitorexit                // ← 退出Monitor（释放锁）
    16: goto          24
    19: astore_2
    20: aload_1
    21: monitorexit                // ← 异常情况下也要释放锁
    22: aload_2
    23: athrow
    24: return
  Exception table:
     from    to  target type
         4    16    19   any      // 异常处理
        19    22    19   any
```

**关键指令**：

| 指令 | 说明 |
|------|------|
| **monitorenter** | 进入Monitor，获取锁 |
| **monitorexit** | 退出Monitor，释放锁 |

**注意**：
- 有**两个monitorexit**：一个正常退出，一个异常退出
- 保证无论如何都会释放锁

#### **场景2：同步方法**

**Java代码**：

```java
public synchronized void syncMethod() {
    count++;
}
```

**字节码**：

```
public synchronized void syncMethod();
  descriptor: ()V
  flags: ACC_PUBLIC, ACC_SYNCHRONIZED  // ← 方法标志位
  Code:
    stack=3, locals=1, args_size=1
       0: aload_0
       1: dup
       2: getfield      #2
       5: iconst_1
       6: iadd
       7: putfield     #2
      10: return
```

**关键点**：
- 没有monitorenter/monitorexit指令
- 使用**ACC_SYNCHRONIZED**标志位
- JVM在方法调用时自动加锁/解锁

---

### 3.2 问题10：monitorenter和monitorexit的执行流程是什么？

#### **monitorenter的执行流程**

```
┌─────────────────────────────────────────┐
│        monitorenter 执行流程            │
├─────────────────────────────────────────┤
│  1. 检查对象头的锁状态                  │
│     ├─ 无锁：尝试CAS设置为偏向锁        │
│     ├─ 偏向锁：检查线程ID               │
│     ├─ 轻量锁：尝试CAS获取              │
│     └─ 重量锁：进入Monitor              │
├─────────────────────────────────────────┤
│  2. 如果是重量锁：                      │
│     ├─ 尝试CAS设置_owner为当前线程      │
│     ├─ 成功：获取锁                     │
│     └─ 失败：进入_EntryList阻塞         │
├─────────────────────────────────────────┤
│  3. 如果是重入：                        │
│     └─ _recursions++                    │
└─────────────────────────────────────────┘
```

#### **monitorexit的执行流程**

```
┌─────────────────────────────────────────┐
│        monitorexit 执行流程             │
├─────────────────────────────────────────┤
│  1. 检查是否是锁的持有者                │
│     └─ 不是：抛出IllegalMonitorState    │
├─────────────────────────────────────────┤
│  2. 处理重入：                          │
│     ├─ _recursions > 0：_recursions--   │
│     └─ _recursions == 0：释放锁         │
├─────────────────────────────────────────┤
│  3. 释放锁：                            │
│     ├─ 设置_owner = null                │
│     └─ 从_EntryList唤醒一个线程         │
└─────────────────────────────────────────┘
```

---

## 4. Monitor的设计思想

### 4.1 问题11：Monitor的设计为什么要分_EntryList和_WaitSet？

**设计原因**：区分**竞争锁**和**等待条件**两种不同的等待状态。

#### **_EntryList：竞争锁的线程**

```java
// 线程B和C在等待线程A释放锁
synchronized (obj) {
    // 线程A持有锁
}
// 线程B和C在_EntryList中
```

**特点**：
- 等待**获取锁**
- 一旦锁释放，立即竞争
- 状态：**BLOCKED**

#### **_WaitSet：等待条件的线程**

```java
synchronized (obj) {
    while (!condition) {
        obj.wait(); // 线程进入_WaitSet
    }
}
```

**特点**：
- 等待**条件满足**
- 需要被notify()唤醒
- 状态：**WAITING**

#### **为什么要分开？**

```
场景：生产者-消费者

生产者线程：
  synchronized (queue) {
    while (queue.isFull()) {
      queue.wait();  // 进入_WaitSet（等待队列不满）
    }
    queue.add(item);
    queue.notify();  // 唤醒_WaitSet中的消费者
  }

消费者线程：
  synchronized (queue) {
    while (queue.isEmpty()) {
      queue.wait();  // 进入_WaitSet（等待队列不空）
    }
    queue.take();
    queue.notify();  // 唤醒_WaitSet中的生产者
  }

其他竞争线程：
  synchronized (queue) {
    // 在_EntryList中等待（只是竞争锁，不等待条件）
  }
```

**如果不分开**：
- 无法区分是在等待锁，还是等待条件
- notify()可能唤醒错误的线程
- 无法实现高效的线程协作

---

### 4.2 问题12：为什么wait()必须在synchronized块内调用？

**原因1：避免竞态条件**

```java
// ❌ 错误示例：wait()在synchronized外
public void consume() {
    while (queue.isEmpty()) {
        queue.wait(); // IllegalMonitorStateException!
    }
    synchronized (queue) {
        queue.take();
    }
}

// 问题：
// T1: 线程A检查queue.isEmpty() = true
// T2: 线程B添加元素，调用notify()（但A还没wait，唤醒失败）
// T3: 线程A调用wait()（永远等待，因为notify已经发生了）
```

**正确示例**：

```java
// ✅ 正确：wait()在synchronized内
public void consume() {
    synchronized (queue) {
        while (queue.isEmpty()) {
            queue.wait(); // 原子性：检查 + 等待
        }
        queue.take();
    }
}
```

**原因2：保证原子性**

```
synchronized块保证了：
  检查条件 + 进入等待 是原子操作
  避免了"丢失唤醒"问题
```

**原因3：实现机制要求**

```
wait()需要操作Monitor的_WaitSet
只有持有锁的线程才能操作Monitor
```

---

## 5. 核心问题总结

### Q1: Synchronized是如何实现锁的？
**A**: 通过对象头的Mark Word存储锁信息，重量级锁时指向Monitor对象。

### Q2: 对象头的结构是什么？
**A**: 
- **Mark Word**：存储锁状态、hashCode、GC年龄等
- **Class Pointer**：指向类元数据
- **数组长度**（如果是数组）

### Q3: 什么是Monitor？
**A**: 操作系统提供的同步机制，包含_owner、_EntryList、_WaitSet等组件。

### Q4: Monitor的工作原理是什么？
**A**: 
- 线程获取锁：CAS设置_owner
- 获取失败：进入_EntryList阻塞
- 调用wait()：进入_WaitSet等待
- 调用notify()：从_WaitSet移到_EntryList

### Q5: 为什么Monitor是重量级锁？
**A**: 需要用户态→内核态切换、线程阻塞/唤醒、上下文切换，开销是轻量级操作的100-1000倍。

### Q6: Synchronized在字节码层面如何实现？
**A**: 
- 同步代码块：monitorenter/monitorexit指令
- 同步方法：ACC_SYNCHRONIZED标志位

### Q7: 为什么要分_EntryList和_WaitSet？
**A**: 区分"竞争锁"和"等待条件"两种不同的等待状态，支持线程协作。

### Q8: 为什么wait()必须在synchronized块内？
**A**: 避免竞态条件、保证原子性、满足实现机制要求。

---

## 6. 思考题

1. **如果对象头只有32位，如何同时存储hashCode和锁信息？**
2. **为什么Monitor要使用_cxq（竞争队列）和_EntryList两个队列？**
3. **重量级锁的性能开销主要在哪里？如何优化？**
4. **为什么synchronized块要有两个monitorexit指令？**
5. **如果线程在wait()时被中断，会发生什么？**

---

## 下一章预告

下一章我们将深入学习：

- **锁升级的完整过程**：无锁 → 偏向锁 → 轻量锁 → 重量锁
- **偏向锁的原理**：为什么单线程访问不需要CAS？
- **轻量锁的原理**：如何使用栈上的Lock Record？
- **锁升级的时机**：什么情况下会升级？
- **锁降级**：锁能否降级？为什么？

让我们继续深入！🚀
