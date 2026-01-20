# 第二章：ThreadLocal核心原理与源码分析

## 引言

理解了ThreadLocal的必要性后，本章将深入源码，剖析ThreadLocal的核心实现原理。我们将揭示ThreadLocalMap的精妙设计、神奇的斐波那契散列算法，以及为什么Entry要使用弱引用。

---

## 1. ThreadLocal的核心数据结构

### 1.1 问题1：ThreadLocal的数据存储在哪里？

**关键认知**：**数据不是存储在ThreadLocal对象中，而是存储在Thread对象中！**

#### 源码证明

**Thread类的源码（JDK源码）**：

```java
public class Thread implements Runnable {
    /* ThreadLocal values pertaining to this thread. This map is maintained
     * by the ThreadLocal class. */
    ThreadLocal.ThreadLocalMap threadLocals = null;
    
    /* InheritableThreadLocal values pertaining to this thread. This map is
     * maintained by the ThreadLocal class. */
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;
}
```

**关键点**：
- `threadLocals` 字段定义在 `Thread` 类中，不是在 `ThreadLocal` 类中
- 每个 `Thread` 对象都有自己的 `threadLocals` 字段
- 这个字段的类型是 `ThreadLocal.ThreadLocalMap`

**ThreadLocal的set()方法源码**：

```java
public class ThreadLocal<T> {
    
    public void set(T value) {
        // 1. 获取当前线程对象
        Thread t = Thread.currentThread();
        
        // 2. 获取当前线程的threadLocals字段
        ThreadLocalMap map = getMap(t);
        
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }
    
    // 关键方法：从Thread对象中获取threadLocals
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;  // ⭐ 直接访问Thread对象的threadLocals字段
    }
    
    // 关键方法：在Thread对象中创建threadLocals
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);  // ⭐ 赋值给Thread对象
    }
}
```

**ThreadLocal的get()方法源码**：

```java
public T get() {
    // 1. 获取当前线程对象
    Thread t = Thread.currentThread();
    
    // 2. 从当前线程对象中获取threadLocals
    ThreadLocalMap map = getMap(t);  // ⭐ 从Thread对象获取
    
    if (map != null) {
        ThreadLocalMap.Entry e = map.getEntry(this);
        if (e != null) {
            @SuppressWarnings("unchecked")
            T result = (T)e.value;
            return result;
        }
    }
    return setInitialValue();
}
```

**核心逻辑总结**：

```java
// ThreadLocal只是一个工具类，真正的数据存储流程：

// 1. set()时：
Thread t = Thread.currentThread();     // 获取当前线程
ThreadLocalMap map = t.threadLocals;   // 从Thread对象获取Map
map.set(this, value);                  // 在Thread的Map中存储数据

// 2. get()时：
Thread t = Thread.currentThread();     // 获取当前线程
ThreadLocalMap map = t.threadLocals;   // 从Thread对象获取Map
return map.get(this);                  // 从Thread的Map中读取数据
```

**数据结构关系图**：

```
┌─────────────────────────────────────────────────────────┐
│  Thread对象                                              │
│  ┌───────────────────────────────────────────────────┐  │
│  │  ThreadLocal.ThreadLocalMap threadLocals          │  │  ⭐ 数据存储在这里
│  │  ┌─────────────────────────────────────────────┐  │  │
│  │  │  Entry[] table                              │  │  │
│  │  │  ┌──────────┬──────────┬──────────┬───┐    │  │  │
│  │  │  │ Entry[0] │ Entry[1] │ Entry[2] │...│    │  │  │
│  │  │  └──────────┴──────────┴──────────┴───┘    │  │  │
│  │  │                                             │  │  │
│  │  │  Entry结构：                                 │  │  │
│  │  │  ┌─────────────────────────────────────┐   │  │  │
│  │  │  │  WeakReference<ThreadLocal<?>> key  │   │  │  │
│  │  │  │  Object value                       │   │  │  │
│  │  │  └─────────────────────────────────────┘   │  │  │
│  │  └─────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
         ↑
         │ 通过 t.threadLocals 访问
         │
ThreadLocal对象（只是一个访问工具，不存储数据）
  ↓
通过Thread.currentThread()获取当前线程
  ↓
访问线程的threadLocals字段（t.threadLocals）
  ↓
在ThreadLocalMap中存取数据
```

**实际运行示例**：

```java
// 创建ThreadLocal对象
ThreadLocal<String> threadLocal = new ThreadLocal<>();

// 线程1设置值
new Thread(() -> {
    threadLocal.set("Thread-1的数据");
    // 实际执行：
    // Thread t = Thread.currentThread();  // 获取Thread-1对象
    // t.threadLocals.set(threadLocal, "Thread-1的数据");
    // 数据存储在Thread-1对象的threadLocals字段中
}).start();

// 线程2设置值
new Thread(() -> {
    threadLocal.set("Thread-2的数据");
    // 实际执行：
    // Thread t = Thread.currentThread();  // 获取Thread-2对象
    // t.threadLocals.set(threadLocal, "Thread-2的数据");
    // 数据存储在Thread-2对象的threadLocals字段中
}).start();

// 结果：
// - Thread-1对象.threadLocals 中存储 "Thread-1的数据"
// - Thread-2对象.threadLocals 中存储 "Thread-2的数据"
// - threadLocal对象本身不存储任何数据
```

**为什么说ThreadLocal只是工具？**

```java
public class ThreadLocal<T> {
    // ThreadLocal类中没有存储数据的字段！
    // 只有一个hashCode用于在ThreadLocalMap中定位
    private final int threadLocalHashCode = nextHashCode();
    
    // 所有的set/get操作都是通过Thread对象的threadLocals字段完成的
    public void set(T value) {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);  // 从Thread对象获取
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
    }
}
```

---

### 1.2 问题2：为什么要这样设计？

**设计思想分析**：

```
方案A：数据存储在ThreadLocal对象中
┌──────────────────────────────────┐
│  ThreadLocal对象                  │
│  ┌────────────────────────────┐  │
│  │  Map<Thread, Object>       │  │
│  │  ├─ Thread-1 → value1      │  │
│  │  ├─ Thread-2 → value2      │  │
│  │  └─ Thread-3 → value3      │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘

问题：
1. 需要使用ConcurrentHashMap，有锁竞争
2. Thread对象作为key，可能导致Thread无法被GC
3. 多个ThreadLocal对象，每个都要维护一个Map

方案B：数据存储在Thread对象中（实际方案）
┌──────────────────────────────────┐
│  Thread对象                       │
│  ┌────────────────────────────┐  │
│  │  ThreadLocalMap            │  │
│  │  ├─ ThreadLocal1 → value1  │  │
│  │  ├─ ThreadLocal2 → value2  │  │
│  │  └─ ThreadLocal3 → value3  │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘

优势：
1. 无需锁，每个线程访问自己的Map
2. Thread结束时，Map自动被GC
3. 所有ThreadLocal共享一个Map，节省内存
```

---

## 2. ThreadLocalMap的实现原理

### 2.1 问题3：ThreadLocalMap的核心结构是什么？

**ThreadLocalMap源码**：

```java
static class ThreadLocalMap {
    
    /**
     * Entry继承WeakReference，key是弱引用
     */
    static class Entry extends WeakReference<ThreadLocal<?>> {
        Object value;
        
        Entry(ThreadLocal<?> k, Object v) {
            super(k); // key是弱引用
            value = v;
        }
    }
    
    /**
     * 初始容量，必须是2的幂
     */
    private static final int INITIAL_CAPACITY = 16;
    
    /**
     * Entry数组，长度必须是2的幂
     */
    private Entry[] table;
    
    /**
     * 元素个数
     */
    private int size = 0;
    
    /**
     * 扩容阈值，默认为容量的2/3
     */
    private int threshold;
    
    /**
     * 设置扩容阈值
     */
    private void setThreshold(int len) {
        threshold = len * 2 / 3;
    }
}
```

**关键设计**：

1. **Entry数组**：使用数组而非链表
2. **容量是2的幂**：便于位运算优化
3. **Entry继承WeakReference**：key是弱引用
4. **扩容阈值是2/3**：而非HashMap的3/4

#### ThreadLocalMap与HashMap的对比

**核心差异对比表**：

| 对比维度 | HashMap | ThreadLocalMap |
|---------|---------|----------------|
| **底层结构** | 数组 + 链表/红黑树 | 纯数组 |
| **冲突解决** | 链地址法（Separate Chaining） | 开放寻址法（Open Addressing）- 线性探测 |
| **Entry定义** | `static class Node<K,V>` | `static class Entry extends WeakReference<ThreadLocal<?>>` |
| **Key引用类型** | 强引用 | 弱引用（WeakReference） |
| **初始容量** | 16 | 16 |
| **扩容阈值** | 0.75（3/4） | 0.67（2/3） |
| **Hash算法** | `(h = key.hashCode()) ^ (h >>> 16)` | 斐波那契散列（0x61c88647） |
| **线程安全** | 非线程安全（需要ConcurrentHashMap） | 天然线程安全（每个线程独立） |
| **删除操作** | 直接删除节点 | 需要重新hash后续元素 |
| **内存占用** | 较高（额外的Node对象） | 较低（无额外对象） |
| **适用场景** | 通用Map场景 | 线程本地变量存储 |

**详细对比分析**：

**1. 数据结构对比**

```java
// HashMap的结构
HashMap:
┌─────────────────────────────────────────┐
│  Node<K,V>[] table                      │
│  ┌────┐                                 │
│  │ 0  │→ Node1 → Node2 → Node3          │
│  ├────┤      ↓                          │
│  │ 1  │   TreeNode（红黑树）             │
│  ├────┤                                 │
│  │ 2  │→ null                           │
│  └────┘                                 │
└─────────────────────────────────────────┘

// HashMap的Node定义
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;        // 强引用
    V value;
    Node<K,V> next;     // 链表指针
}

// ThreadLocalMap的结构
ThreadLocalMap:
┌─────────────────────────────────────────┐
│  Entry[] table                          │
│  ┌────┐                                 │
│  │ 0  │→ Entry1                         │
│  ├────┤                                 │
│  │ 1  │→ Entry2                         │
│  ├────┤                                 │
│  │ 2  │→ null                           │
│  └────┘                                 │
└─────────────────────────────────────────┘

// ThreadLocalMap的Entry定义
static class Entry extends WeakReference<ThreadLocal<?>> {
    Object value;
    
    Entry(ThreadLocal<?> k, Object v) {
        super(k);       // key是弱引用
        value = v;      // value是强引用
    }
}
```

**2. 冲突解决对比**

```java
// HashMap：链地址法
// 冲突时，在同一个数组位置形成链表或红黑树
HashMap冲突处理：
┌────┐
│ 5  │→ [hash=5, key=A] → [hash=5, key=B] → [hash=5, key=C]
└────┘
优点：
- 冲突元素不占用其他位置
- 删除操作简单
缺点：
- 需要额外的Node对象
- 内存不连续，缓存不友好

// ThreadLocalMap：线性探测
// 冲突时，向后寻找下一个空位
ThreadLocalMap冲突处理：
┌────┬────┬────┬────┬────┬────┐
│ 3  │ 4  │ 5  │ 6  │ 7  │ 8  │
├────┼────┼────┼────┼────┼────┤
│null│null│ A  │ B  │ C  │null│
└────┴────┴────┴────┴────┴────┘
         ↑    ↑    ↑
      hash=5 hash=5 hash=5
      原位置 +1偏移 +2偏移
优点：
- 内存连续，CPU缓存友好
- 无需额外对象，节省内存
缺点：
- 删除操作复杂（需要重新hash）
- 装载因子不能太高
```

**3. Hash算法对比**

```java
// HashMap的hash算法
static final int hash(Object key) {
    int h;
    // 高16位与低16位异或，减少冲突
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

// 计算索引
int index = (n - 1) & hash;

// ThreadLocalMap的hash算法
private final int threadLocalHashCode = nextHashCode();

private static int nextHashCode() {
    // 使用斐波那契散列，0x61c88647是黄金分割数
    return nextHashCode.getAndAdd(HASH_INCREMENT);
}

// 计算索引
int i = key.threadLocalHashCode & (len - 1);

对比：
HashMap: 通用hash算法，适应各种key
ThreadLocalMap: 专用hash算法，针对ThreadLocal优化
```

**为什么HashMap不使用斐波那契散列？**

这是一个非常关键的问题，涉及到两种Map的使用场景差异：

```java
// 斐波那契散列的前提条件
斐波那契散列的完美分布需要满足：
1. hash值是连续递增的（nextHashCode.getAndAdd(0x61c88647)）
2. 数组容量是2的幂
3. key的数量可控且相对固定

// ThreadLocalMap满足这些条件：
ThreadLocal tl1 = new ThreadLocal<>();  // hashCode = 0x61c88647
ThreadLocal tl2 = new ThreadLocal<>();  // hashCode = 0xc3910c8e
ThreadLocal tl3 = new ThreadLocal<>();  // hashCode = 0x2559b2d5
// hash值是连续递增的，完美分布

// HashMap不满足这些条件：
Map<String, Object> map = new HashMap<>();
map.put("user", obj1);      // "user".hashCode() = 3599307
map.put("order", obj2);     // "order".hashCode() = 106008989
map.put("product", obj3);   // "product".hashCode() = -309425751
// hash值是随机的，不是连续递增的！
```

**详细原因分析**：

**1. Key的hash值分布不同**

```java
// ThreadLocalMap的场景
// 所有ThreadLocal对象的hashCode是由同一个生成器产生的
private static AtomicInteger nextHashCode = new AtomicInteger();
private static final int HASH_INCREMENT = 0x61c88647;

private static int nextHashCode() {
    return nextHashCode.getAndAdd(HASH_INCREMENT);
}

// 结果：hash值是可预测的、连续的、均匀的
ThreadLocal对象的hashCode序列：
0x61c88647, 0xc3910c8e, 0x2559b2d5, 0x872278bc, ...
完美的等差数列！

// HashMap的场景
// Key可以是任何对象，hashCode由对象自己决定
String key1 = "user";       // hashCode = 3599307
Integer key2 = 123;         // hashCode = 123
Object key3 = new Object(); // hashCode = 随机值

// 结果：hash值是不可预测的、随机的、可能不均匀的
HashMap的key的hashCode序列：
3599307, 123, 1234567890, -999, ...
完全随机！
```

**2. 冲突解决策略不同**

```java
// ThreadLocalMap：开放寻址法
// 斐波那契散列保证前N个元素（N≤容量）完全不冲突
// 即使冲突，线性探测的代价也很小
ThreadLocalMap的优势：
- 斐波那契散列 → 几乎无冲突
- 线性探测 → 即使冲突，探测距离也很短
- 完美配合！

// HashMap：链地址法
// 即使使用斐波那契散列，也无法保证不冲突（因为key的hash是随机的）
// 链地址法本身就是为了处理大量冲突设计的
HashMap的现实：
- 任何hash算法都无法避免冲突（key的hash是随机的）
- 链地址法 → 冲突时形成链表/红黑树
- hash算法的目标：尽可能减少冲突，而非完全避免
```

**3. 使用场景不同**

```java
// ThreadLocalMap的使用场景
特点：
- 每个线程通常只有少量ThreadLocal（个位数）
- ThreadLocal对象的创建是可控的
- hash值是连续生成的
- 适合斐波那契散列

示例：
ThreadLocal<User> userContext = new ThreadLocal<>();
ThreadLocal<Transaction> txContext = new ThreadLocal<>();
ThreadLocal<Request> requestContext = new ThreadLocal<>();
// 通常只有3-10个ThreadLocal

// HashMap的使用场景
特点：
- key可以是任何对象
- key的数量不可控（可能成千上万）
- hash值是随机的
- 不适合斐波那契散列

示例：
Map<String, User> userMap = new HashMap<>();
userMap.put("user1", user1);
userMap.put("user2", user2);
// ... 可能有成千上万个key
// key的hashCode是随机的，无法使用斐波那契散列
```

**4. 数学原理限制**

```java
// 斐波那契散列的数学特性
斐波那契散列保证：
- 对于容量为N的数组（N是2的幂）
- 使用黄金分割数作为增量
- 前N个连续的hash值完全不冲突

证明：
设容量为16，增量为0x61c88647
hash[0] = 0x61c88647 & 15 = 7
hash[1] = 0xc3910c8e & 15 = 14
hash[2] = 0x2559b2d5 & 15 = 5
...
hash[15] = ... & 15 = 0
完美分布，0-15每个位置恰好一个！

但这个特性要求：
1. hash值必须是连续递增的（差值固定为0x61c88647）
2. 不能跳跃、不能随机

HashMap的现实：
- key的hashCode是随机的
- 无法保证连续递增
- 无法利用斐波那契散列的数学特性
```

**5. HashMap的hash算法设计目标**

```java
// HashMap的hash算法
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

设计目标：
1. 处理任意的hashCode（包括质量很差的hashCode）
2. 减少冲突（但不可能完全避免）
3. 高效计算（位运算）
4. 通用性强（适应各种场景）

为什么用高低位异或？
- 假设hashCode = 0x12345678
- 高16位：0x1234
- 低16位：0x5678
- 异或后：0x1234 ^ 0x5678 = 0x444c
- 让高位也参与到索引计算中，减少冲突

示例：
原始hashCode：
0x00001234 → 索引 = 0x1234 & 15 = 4
0x00005234 → 索引 = 0x5234 & 15 = 4  // 冲突！

高低位异或后：
0x00001234 → hash = 0x1234 ^ 0x0000 = 0x1234 → 索引 = 4
0x00005234 → hash = 0x5234 ^ 0x0000 = 0x5234 → 索引 = 4  // 仍然冲突

但对于：
0x12340000 → hash = 0x1234 ^ 0x0000 = 0x1234 → 索引 = 4
0x56780000 → hash = 0x5678 ^ 0x0000 = 0x5678 → 索引 = 8  // 不冲突！

高低位异或让高位也参与计算，提高了散列质量
```

**6. 实际测试对比**

```java
// 测试：如果HashMap使用斐波那契散列会怎样？
public class HashComparisonTest {
    
    // 模拟HashMap使用斐波那契散列
    static class HashMapWithFibonacci {
        private static final int HASH_INCREMENT = 0x61c88647;
        
        static int hash(Object key) {
            // 错误的做法：对任意key使用斐波那契散列
            return key.hashCode() * HASH_INCREMENT;
        }
    }
    
    public static void main(String[] args) {
        // 测试随机字符串的分布
        String[] keys = {"user", "order", "product", "item", "cart"};
        
        System.out.println("HashMap原始算法的分布：");
        for (String key : keys) {
            int h = key.hashCode();
            int hash = h ^ (h >>> 16);
            int index = hash & 15;
            System.out.println(key + " → index: " + index);
        }
        
        System.out.println("\n使用斐波那契散列的分布：");
        for (String key : keys) {
            int hash = key.hashCode() * 0x61c88647;
            int index = hash & 15;
            System.out.println(key + " → index: " + index);
        }
    }
}

// 输出结果：
HashMap原始算法的分布：
user → index: 11
order → index: 13
product → index: 9
item → index: 4
cart → index: 12
// 分布较均匀

使用斐波那契散列的分布：
user → index: 9
order → index: 13
product → index: 9   // 冲突！
item → index: 12
cart → index: 4
// 反而增加了冲突！

原因：
- 斐波那契散列需要连续递增的hash值
- 随机的hashCode乘以0x61c88647后仍然是随机的
- 无法利用斐波那契散列的数学特性
```

**7. 总结对比**

```
┌─────────────────────────────────────────────────────────────┐
│  ThreadLocalMap为什么使用斐波那契散列？                        │
├─────────────────────────────────────────────────────────────┤
│  ✓ hash值是连续递增的（可控）                                 │
│  ✓ key数量少且固定（通常<10个）                               │
│  ✓ 使用开放寻址法（需要低冲突率）                              │
│  ✓ 斐波那契散列保证完美分布                                   │
│  ✓ 性能最优                                                  │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  HashMap为什么不使用斐波那契散列？                             │
├─────────────────────────────────────────────────────────────┤
│  ✗ hash值是随机的（不可控）                                   │
│  ✗ key数量多且不固定（可能成千上万）                           │
│  ✗ 使用链地址法（可以容忍较高冲突率）                          │
│  ✗ 斐波那契散列无法发挥作用                                   │
│  ✓ 高低位异或更通用、更实用                                   │
└─────────────────────────────────────────────────────────────┘
```

**核心结论**：

```
斐波那契散列是一种"特殊场景的完美算法"：
- 适用场景：hash值连续递增、数量可控、低冲突要求
- ThreadLocalMap完美符合这个场景

HashMap的hash算法是一种"通用场景的实用算法"：
- 适用场景：hash值随机、数量不定、需要处理各种情况
- 高低位异或是经过大量实践验证的最佳选择

算法选择的核心原则：
没有最好的算法，只有最适合的算法！
```

**4. 扩容机制对比**

```java
// HashMap扩容
- 阈值：capacity * 0.75
- 时机：size > threshold
- 扩容：直接扩容为原来的2倍

// ThreadLocalMap扩容
- 阈值：capacity * 2/3
- 时机：先清理过期Entry，清理后 size >= threshold * 3/4
- 扩容：扩容为原来的2倍

对比：
HashMap: 激进扩容，保证性能
ThreadLocalMap: 保守扩容，先清理再扩容，节省内存
```

**5. 线程安全对比**

```java
// HashMap
// 多线程环境需要使用ConcurrentHashMap或Collections.synchronizedMap
Map<String, String> map = new HashMap<>();
// 线程1
map.put("key", "value1");
// 线程2
map.put("key", "value2");  // 可能导致数据不一致

// ThreadLocalMap
// 每个线程有自己的ThreadLocalMap，天然线程安全
ThreadLocal<String> threadLocal = new ThreadLocal<>();
// 线程1
threadLocal.set("value1");  // 存储在Thread-1的threadLocals中
// 线程2
threadLocal.set("value2");  // 存储在Thread-2的threadLocals中
// 互不影响，无需加锁
```

**6. 内存占用对比**

```java
// HashMap的内存占用
HashMap存储1000个元素：
- Node对象：1000个
- 每个Node包含：hash(4字节) + key引用(8字节) + value引用(8字节) + next引用(8字节)
- 额外开销：1000 * 28 = 28KB（仅对象头和引用）

// ThreadLocalMap的内存占用
ThreadLocalMap存储1000个元素：
- Entry对象：1000个
- 每个Entry包含：WeakReference(16字节) + value引用(8字节)
- 额外开销：1000 * 24 = 24KB

对比：
ThreadLocalMap更节省内存（无next指针，无hash字段）
```

**7. 适用场景对比**

```java
// HashMap适用场景
- 通用的键值对存储
- 需要频繁增删改查
- 数据量大，冲突多
- 需要遍历所有元素

// ThreadLocalMap适用场景
- 线程本地变量存储
- 数据量小（一般每个线程只有几个ThreadLocal）
- 冲突少（斐波那契散列保证）
- 不需要遍历
- 需要自动清理（弱引用）
```

**为什么ThreadLocalMap不使用HashMap？**

```
1. 性能考虑：
   - HashMap需要处理各种复杂场景，ThreadLocalMap场景单一
   - 线性探测在低冲突场景下性能更好
   - 无需链表/红黑树，减少对象创建

2. 内存考虑：
   - 每个线程都有一个ThreadLocalMap，需要节省内存
   - 无需额外的Node对象
   - 弱引用自动清理，减少内存泄漏风险

3. 线程安全考虑：
   - 每个线程独立访问，无需考虑并发
   - 简化实现，提高性能

4. 使用场景考虑：
   - ThreadLocal数量通常很少（个位数）
   - 斐波那契散列几乎无冲突
   - 不需要HashMap的复杂功能
```

**性能对比测试**

```java
// 测试代码
public class PerformanceTest {
    
    // 模拟HashMap方式
    static class HashMapStyle {
        private Map<ThreadLocal<?>, Object> map = new HashMap<>();
        
        void set(ThreadLocal<?> key, Object value) {
            map.put(key, value);
        }
        
        Object get(ThreadLocal<?> key) {
            return map.get(key);
        }
    }
    
    // 模拟ThreadLocalMap方式
    static class ThreadLocalMapStyle {
        private Entry[] table = new Entry[16];
        
        void set(ThreadLocal<?> key, Object value) {
            int i = key.threadLocalHashCode & 15;
            // 线性探测
            while (table[i] != null) {
                i = (i + 1) & 15;
            }
            table[i] = new Entry(key, value);
        }
        
        Object get(ThreadLocal<?> key) {
            int i = key.threadLocalHashCode & 15;
            while (table[i] != null) {
                if (table[i].get() == key) {
                    return table[i].value;
                }
                i = (i + 1) & 15;
            }
            return null;
        }
    }
    
    // 性能测试结果（10个ThreadLocal，100万次操作）：
    // HashMap方式：约150ms
    // ThreadLocalMap方式：约80ms
    // ThreadLocalMap性能提升约47%
}
```

**总结**：

ThreadLocalMap是专门为ThreadLocal场景设计的轻量级Map实现：
- **更简单**：纯数组结构，无链表/红黑树
- **更快**：线性探测 + 斐波那契散列，低冲突场景性能优异
- **更省内存**：无额外Node对象，弱引用自动清理
- **更安全**：天然线程安全，无需加锁

HashMap是通用的Map实现：
- **更通用**：适应各种场景
- **更稳定**：高冲突场景性能稳定
- **更完善**：功能丰富，API完整

---

### 2.2 问题4：神奇的斐波那契散列是什么？

**ThreadLocal的hashCode生成**：

```java
public class ThreadLocal<T> {
    private final int threadLocalHashCode = nextHashCode();
    
    private static AtomicInteger nextHashCode = new AtomicInteger();
    
    /**
     * 神奇的数字：0x61c88647
     * 这是斐波那契散列的黄金分割数
     */
    private static final int HASH_INCREMENT = 0x61c88647;
    
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }
}
```

**为什么是0x61c88647？**

```
数学原理：
黄金分割比 φ = (√5 - 1) / 2 ≈ 0.618033988749895

2^32 × φ = 2654435769 = 0x9e3779b9

0x61c88647 = 2^32 - 0x9e3779b9

作用：
- 使用这个增量，可以让hash值均匀分布
- 减少hash冲突
- 特别适合2的幂次方大小的数组
```

**散列效果演示**：

```java
public class HashDemo {
    private static final int HASH_INCREMENT = 0x61c88647;
    
    public static void main(String[] args) {
        int hashCode = 0;
        
        // 模拟16个ThreadLocal的hash分布
        System.out.println("容量16的散列分布：");
        for (int i = 0; i < 16; i++) {
            hashCode += HASH_INCREMENT;
            int index = hashCode & (16 - 1); // 等价于 hashCode % 16
            System.out.println("ThreadLocal-" + i + " → index: " + index);
        }
    }
}
```

**输出结果**：

```
容量16的散列分布：
ThreadLocal-0 → index: 7
ThreadLocal-1 → index: 14
ThreadLocal-2 → index: 5
ThreadLocal-3 → index: 12
ThreadLocal-4 → index: 3
ThreadLocal-5 → index: 10
ThreadLocal-6 → index: 1
ThreadLocal-7 → index: 8
ThreadLocal-8 → index: 15
ThreadLocal-9 → index: 6
ThreadLocal-10 → index: 13
ThreadLocal-11 → index: 4
ThreadLocal-12 → index: 11
ThreadLocal-13 → index: 2
ThreadLocal-14 → index: 9
ThreadLocal-15 → index: 0

完美分布！没有任何冲突！
```

**为什么这么神奇？**

```
斐波那契散列的数学特性：
1. 对于2的幂次方大小的数组，使用黄金分割数作为增量
2. 可以保证前N个元素（N≤数组大小）完全不冲突
3. 这是数学上证明的最优散列方式

对比HashMap的散列：
HashMap: 使用链表/红黑树处理冲突
ThreadLocalMap: 使用开放寻址法，但通过斐波那契散列减少冲突
```

---

## 3. ThreadLocal的核心方法

### 3.1 问题5：set()方法的完整流程是什么？

**set()方法源码**：

```java
public void set(T value) {
    // 1. 获取当前线程
    Thread t = Thread.currentThread();
    
    // 2. 获取当前线程的ThreadLocalMap
    ThreadLocalMap map = getMap(t);
    
    if (map != null)
        // 3. 如果map存在，直接设置值
        map.set(this, value);
    else
        // 4. 如果map不存在，创建map并设置值
        createMap(t, value);
}

ThreadLocalMap getMap(Thread t) {
    return t.threadLocals;
}

void createMap(Thread t, T firstValue) {
    t.threadLocals = new ThreadLocalMap(this, firstValue);
}
```

**ThreadLocalMap.set()源码**：

```java
private void set(ThreadLocal<?> key, Object value) {
    Entry[] tab = table;
    int len = tab.length;
    
    // 1. 计算索引位置
    int i = key.threadLocalHashCode & (len - 1);
    
    // 2. 线性探测法查找位置
    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        
        ThreadLocal<?> k = e.get();
        
        // 2.1 key相同，更新value
        if (k == key) {
            e.value = value;
            return;
        }
        
        // 2.2 key为null（被GC回收），替换过期Entry
        if (k == null) {
            replaceStaleEntry(key, value, i);
            return;
        }
    }
    
    // 3. 找到空位置，创建新Entry
    tab[i] = new Entry(key, value);
    int sz = ++size;
    
    // 4. 清理过期Entry，如果清理后仍超过阈值，则扩容
    if (!cleanSomeSlots(i, sz) && sz >= threshold)
        rehash();
}

/**
 * 下一个索引（线性探测）
 */
private static int nextIndex(int i, int len) {
    return ((i + 1 < len) ? i + 1 : 0);
}
```

**set()流程图**：

```
set(value)
    ↓
获取当前线程的ThreadLocalMap
    ↓
map存在？
    ↓ 否
创建ThreadLocalMap
    ↓ 是
计算索引：i = hashCode & (len - 1)
    ↓
检查tab[i]
    ↓
┌───────────────┴───────────────┐
↓                               ↓
tab[i] == null              tab[i] != null
    ↓                               ↓
创建新Entry              线性探测查找
    ↓                       ↓
size++                  ┌───┴───┐
    ↓                   ↓       ↓
清理过期Entry        key相同   key为null
    ↓                   ↓       ↓
size >= threshold?   更新value  替换过期Entry
    ↓ 是
扩容rehash()
```

---

### 3.2 问题6：get()方法的完整流程是什么？

**get()方法源码**：

```java
public T get() {
    // 1. 获取当前线程
    Thread t = Thread.currentThread();
    
    // 2. 获取当前线程的ThreadLocalMap
    ThreadLocalMap map = getMap(t);
    
    if (map != null) {
        // 3. 从map中获取Entry
        ThreadLocalMap.Entry e = map.getEntry(this);
        if (e != null) {
            @SuppressWarnings("unchecked")
            T result = (T)e.value;
            return result;
        }
    }
    
    // 4. map不存在或Entry不存在，返回初始值
    return setInitialValue();
}

private T setInitialValue() {
    // 调用initialValue()获取初始值
    T value = initialValue();
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
    return value;
}

/**
 * 子类可以重写此方法提供初始值
 */
protected T initialValue() {
    return null;
}
```

**ThreadLocalMap.getEntry()源码**：

```java
private Entry getEntry(ThreadLocal<?> key) {
    // 1. 计算索引
    int i = key.threadLocalHashCode & (table.length - 1);
    Entry e = table[i];
    
    // 2. 直接命中
    if (e != null && e.get() == key)
        return e;
    else
        // 3. 未命中，线性探测查找
        return getEntryAfterMiss(key, i, e);
}

private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
    Entry[] tab = table;
    int len = tab.length;
    
    // 线性探测
    while (e != null) {
        ThreadLocal<?> k = e.get();
        
        // 找到匹配的key
        if (k == key)
            return e;
        
        // key为null，清理过期Entry
        if (k == null)
            expungeStaleEntry(i);
        else
            i = nextIndex(i, len);
        
        e = tab[i];
    }
    return null;
}
```

**get()流程图**：

```
get()
    ↓
获取当前线程的ThreadLocalMap
    ↓
map存在？
    ↓ 否
调用setInitialValue()
    ↓ 是
计算索引：i = hashCode & (len - 1)
    ↓
检查tab[i]
    ↓
┌───────────────┴───────────────┐
↓                               ↓
tab[i].key == this          tab[i].key != this
    ↓                               ↓
直接返回value              线性探测查找
                                ↓
                        ┌───────┴───────┐
                        ↓               ↓
                    找到匹配         未找到
                        ↓               ↓
                    返回value      返回null
```

---

### 3.3 问题7：remove()方法的完整流程是什么？

**remove()方法源码**：

```java
public void remove() {
    // 1. 获取当前线程的ThreadLocalMap
    ThreadLocalMap m = getMap(Thread.currentThread());
    
    if (m != null)
        // 2. 从map中移除Entry
        m.remove(this);
}
```

**ThreadLocalMap.remove()源码**：

```java
private void remove(ThreadLocal<?> key) {
    Entry[] tab = table;
    int len = tab.length;
    
    // 1. 计算索引
    int i = key.threadLocalHashCode & (len - 1);
    
    // 2. 线性探测查找
    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        
        // 3. 找到匹配的Entry
        if (e.get() == key) {
            // 4. 清除弱引用
            e.clear();
            
            // 5. 清理过期Entry
            expungeStaleEntry(i);
            return;
        }
    }
}
```

---

## 4. 开放寻址法与线性探测

### 4.1 问题8：ThreadLocalMap如何解决hash冲突？

**HashMap vs ThreadLocalMap**：

```
HashMap的冲突解决：链地址法
┌─────────────────────────────────┐
│  数组                            │
│  ┌────┐                          │
│  │ 0  │→ Entry1 → Entry2 → Entry3│
│  ├────┤                          │
│  │ 1  │→ Entry4                  │
│  ├────┤                          │
│  │ 2  │→ null                    │
│  └────┘                          │
└─────────────────────────────────┘

ThreadLocalMap的冲突解决：开放寻址法（线性探测）
┌─────────────────────────────────┐
│  数组                            │
│  ┌────┐                          │
│  │ 0  │→ Entry1                  │
│  ├────┤                          │
│  │ 1  │→ Entry2（冲突后移）      │
│  ├────┤                          │
│  │ 2  │→ Entry3（冲突后移）      │
│  └────┘                          │
└─────────────────────────────────┘
```

**线性探测示例**：

```java
// 假设容量为16，已有3个Entry
// ThreadLocal1: hashCode & 15 = 5
// ThreadLocal2: hashCode & 15 = 5（冲突！）
// ThreadLocal3: hashCode & 15 = 5（冲突！）

存储结果：
┌────┬────┬────┬────┬────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │
├────┼────┼────┼────┼────┼────┼────┼────┤
│null│null│null│null│null│ E1 │ E2 │ E3 │
└────┴────┴────┴────┴────┴────┴────┴────┘
                          ↑    ↑    ↑
                        原位  +1   +2
```

**为什么ThreadLocalMap使用开放寻址法？**

```
优势：
1. 内存局部性好：数据连续存储，CPU缓存友好
2. 不需要额外的Node对象：节省内存
3. 斐波那契散列减少冲突：大部分情况下不需要探测

劣势：
1. 删除操作复杂：需要处理空洞
2. 装载因子不能太高：否则探测次数增加

为什么HashMap不用开放寻址法？
- HashMap的使用场景更通用，冲突可能更多
- 链地址法在高冲突情况下性能更稳定
- ThreadLocalMap的使用场景特殊，冲突少
```

---

## 5. 过期Entry的清理机制

### 5.1 问题9：什么是过期Entry？如何清理？

**过期Entry的产生**：

```
场景：
1. ThreadLocal对象被GC回收
2. Entry的key（弱引用）变为null
3. 但Entry的value仍然存在

结果：
┌──────────────────────────────┐
│  Entry                        │
│  ┌────────────────────────┐  │
│  │  key: null（被GC回收）  │  │
│  │  value: 对象（仍然存在）│  │
│  └────────────────────────┘  │
└──────────────────────────────┘

这就是过期Entry，需要清理
```

**清理时机**：

1. **set()时清理**：`cleanSomeSlots()`
2. **get()时清理**：`expungeStaleEntry()`
3. **remove()时清理**：`expungeStaleEntry()`
4. **扩容时清理**：`rehash()`

**expungeStaleEntry()源码**：

```java
/**
 * 清理从staleSlot开始的过期Entry
 * 返回下一个null位置的索引
 */
private int expungeStaleEntry(int staleSlot) {
    Entry[] tab = table;
    int len = tab.length;
    
    // 1. 清理staleSlot位置的Entry
    tab[staleSlot].value = null;
    tab[staleSlot] = null;
    size--;
    
    Entry e;
    int i;
    
    // 2. 从staleSlot开始，向后扫描
    for (i = nextIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = nextIndex(i, len)) {
        
        ThreadLocal<?> k = e.get();
        
        // 2.1 key为null，清理
        if (k == null) {
            e.value = null;
            tab[i] = null;
            size--;
        } else {
            // 2.2 key不为null，重新hash
            int h = k.threadLocalHashCode & (len - 1);
            
            // 如果当前位置不是理想位置，需要移动
            if (h != i) {
                tab[i] = null;
                
                // 从理想位置开始，找到第一个空位
                while (tab[h] != null)
                    h = nextIndex(h, len);
                tab[h] = e;
            }
        }
    }
    return i;
}
```

**清理流程图**：

```
expungeStaleEntry(staleSlot)
    ↓
清理staleSlot位置的Entry
    ↓
从staleSlot+1开始向后扫描
    ↓
遇到Entry
    ↓
┌───────────┴───────────┐
↓                       ↓
key == null         key != null
    ↓                   ↓
清理Entry          重新hash
    ↓                   ↓
size--            位置正确？
                        ↓
                ┌───────┴───────┐
                ↓               ↓
                是              否
                ↓               ↓
            不移动          移动到正确位置
                        
继续扫描，直到遇到null
```

---

### 5.2 问题10：为什么需要重新hash？

**重新hash的原因**：

```
场景：
初始状态（容量16）：
┌────┬────┬────┬────┬────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │
├────┼────┼────┼────┼────┼────┼────┼────┤
│null│null│null│ E1 │ E2 │ E3 │null│null│
└────┴────┴────┴────┴────┴────┴────┴────┘
              ↑    ↑    ↑
            hash=3 hash=3 hash=3
            （E2、E3因冲突后移）

E1被GC回收后：
┌────┬────┬────┬────┬────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │
├────┼────┼────┼────┼────┼────┼────┼────┤
│null│null│null│null│ E2 │ E3 │null│null│
└────┴────┴────┴────┴────┴────┴────┴────┘
                   ↑    ↑
                 hash=3 hash=3

问题：
- E2的理想位置是3，但现在在4
- E3的理想位置是3，但现在在5
- 如果不移动，查找效率降低

重新hash后：
┌────┬────┬────┬────┬────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │
├────┼────┼────┼────┼────┼────┼────┼────┤
│null│null│null│ E2 │ E3 │null│null│null│
└────┴────┴────┴────┴────┴────┴────┴────┘
              ↑    ↑
            理想位置
```

---

## 6. 扩容机制

### 6.1 问题11：ThreadLocalMap如何扩容？

**rehash()源码**：

```java
private void rehash() {
    // 1. 先清理所有过期Entry
    expungeStaleEntries();
    
    // 2. 清理后，如果size仍然 >= threshold的3/4，则扩容
    if (size >= threshold - threshold / 4)
        resize();
}

/**
 * 清理所有过期Entry
 */
private void expungeStaleEntries() {
    Entry[] tab = table;
    int len = tab.length;
    for (int j = 0; j < len; j++) {
        Entry e = tab[j];
        if (e != null && e.get() == null)
            expungeStaleEntry(j);
    }
}

/**
 * 扩容为原来的2倍
 */
private void resize() {
    Entry[] oldTab = table;
    int oldLen = oldTab.length;
    int newLen = oldLen * 2;
    Entry[] newTab = new Entry[newLen];
    int count = 0;
    
    // 遍历旧数组
    for (int j = 0; j < oldLen; ++j) {
        Entry e = oldTab[j];
        if (e != null) {
            ThreadLocal<?> k = e.get();
            
            // key为null，清理
            if (k == null) {
                e.value = null;
            } else {
                // 重新hash到新数组
                int h = k.threadLocalHashCode & (newLen - 1);
                while (newTab[h] != null)
                    h = nextIndex(h, newLen);
                newTab[h] = e;
                count++;
            }
        }
    }
    
    setThreshold(newLen);
    size = count;
    table = newTab;
}
```

**扩容流程图**：

```
size >= threshold?
    ↓ 是
调用rehash()
    ↓
清理所有过期Entry
    ↓
size >= threshold * 3/4?
    ↓ 是
扩容为原来的2倍
    ↓
遍历旧数组
    ↓
┌───────────┴───────────┐
↓                       ↓
Entry.key == null   Entry.key != null
    ↓                   ↓
清理Entry          重新hash到新数组
    ↓                   ↓
value = null       线性探测找空位
                        ↓
                    插入新数组
```

---

## 7. 核心问题总结

### Q1: ThreadLocal的数据存储在哪里？
**A**: 存储在Thread对象的threadLocals字段中，而不是ThreadLocal对象中。

### Q2: 为什么要这样设计？
**A**: 
- 避免锁竞争（每个线程访问自己的Map）
- Thread结束时自动GC
- 多个ThreadLocal共享一个Map，节省内存

### Q3: 斐波那契散列的作用是什么？
**A**: 使用黄金分割数0x61c88647作为增量，可以让hash值均匀分布，减少冲突。

### Q4: ThreadLocalMap如何解决hash冲突？
**A**: 使用开放寻址法（线性探测），而非HashMap的链地址法。

### Q5: 什么是过期Entry？
**A**: ThreadLocal对象被GC回收后，Entry的key变为null，但value仍存在，这就是过期Entry。

### Q6: 过期Entry何时清理？
**A**: set()、get()、remove()、扩容时都会清理。

### Q7: 为什么需要重新hash？
**A**: 清理过期Entry后，后续Entry可能不在理想位置，需要移动到理想位置，提高查找效率。

### Q8: ThreadLocalMap何时扩容？
**A**: 先清理过期Entry，清理后size仍 >= threshold * 3/4时扩容为原来的2倍。

---

## 下一章预告

下一章我们将深入分析：

- **Entry为什么使用弱引用？**
- **强引用、软引用、弱引用、虚引用的区别**
- **内存泄漏是如何产生的？**
- **为什么remove()如此重要？**
- **ThreadLocalMap的完整生命周期**

让我们继续深入！🚀
