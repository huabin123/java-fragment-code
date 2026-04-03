# HashMap线程安全问题与解决方案

## 1. HashMap为什么不是线程安全的？

### 1.1 线程安全的定义

**线程安全**：当多个线程访问同一个对象时，如果不需要额外的同步措施，就能保证程序的正确性。

**HashMap不是线程安全的**：
- 多个线程同时修改HashMap，可能导致数据不一致
- 可能导致死循环（JDK 1.7）
- 可能导致数据丢失

---

### 1.2 HashMap的设计目标

HashMap的设计目标是**高性能的单线程使用**：

```java
// HashMap的方法都没有synchronized关键字
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

public V get(Object key) {
    Node<K,V> e;
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}
```

**为什么不设计成线程安全？**

1. **性能考虑**：同步会带来性能损失
2. **使用场景**：大部分场景是单线程使用
3. **灵活性**：用户可以根据需要选择同步方案

---

## 2. 并发场景下的问题

### 2.1 问题1：数据覆盖

**场景**：两个线程同时put，可能导致数据丢失。

```java
// 线程1和线程2同时执行put操作
public V put(K key, V value) {
    // ...
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);  // 可能被覆盖
    // ...
}
```

**问题分析**：

```
时间线：
t1: 线程1计算索引 i=5，发现tab[5]为null
t2: 线程2计算索引 i=5，发现tab[5]为null
t3: 线程1创建节点，tab[5] = node1
t4: 线程2创建节点，tab[5] = node2  // 覆盖了node1

结果：线程1的数据丢失
```

**代码示例**：

```java
public class HashMapDataLossDemo {
    public static void main(String[] args) throws InterruptedException {
        Map<String, String> map = new HashMap<>();
        
        // 创建100个线程，每个线程put 1000个元素
        int threadCount = 100;
        int putCount = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < putCount; j++) {
                    map.put("key_" + threadId + "_" + j, "value");
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        // 期望：100 * 1000 = 100000
        // 实际：可能小于100000（数据丢失）
        System.out.println("期望大小: " + (threadCount * putCount));
        System.out.println("实际大小: " + map.size());
    }
}
```

---

### 2.2 问题2：扩容时的死循环（JDK 1.7）

**JDK 1.7的扩容代码**：

```java
void transfer(Entry[] newTable, boolean rehash) {
    int newCapacity = newTable.length;
    for (Entry<K,V> e : table) {
        while(null != e) {
            Entry<K,V> next = e.next;  // 1. 保存next
            if (rehash) {
                e.hash = null == e.key ? 0 : hash(e.key);
            }
            int i = indexFor(e.hash, newCapacity);
            e.next = newTable[i];  // 2. 头插法
            newTable[i] = e;       // 3. 放入新数组
            e = next;              // 4. 继续下一个
        }
    }
}
```

**死循环场景**：

```
初始状态：
table[3] → A → B → null

线程1和线程2同时扩容：

线程1执行到：
  Entry<K,V> next = e.next;  // e=A, next=B
  然后被挂起

线程2完成扩容（头插法导致顺序反转）：
  newTable[3] → B → A → null

线程1恢复执行：
  e=A, next=B
  A.next = newTable[3] = B  // A指向B
  newTable[3] = A           // A成为头节点
  e = next = B              // 继续处理B
  
  next = B.next = A         // B的next是A
  B.next = newTable[3] = A  // B指向A
  newTable[3] = B           // B成为头节点
  e = next = A              // 继续处理A
  
  next = A.next = B         // A的next是B
  A.next = newTable[3] = B  // A指向B
  newTable[3] = A           // A成为头节点
  e = next = B              // 继续处理B
  
  形成环形链表：A → B → A → B → ...
```

**流程图**：

```
初始状态：
A → B → null

线程1挂起时：
e = A, next = B

线程2完成扩容（头插法）：
B → A → null

线程1恢复：
第1次循环：
  A.next = B
  newTable[3] = A
  e = B
  结果：A → B → null

第2次循环：
  B.next = A
  newTable[3] = B
  e = A
  结果：B → A → B（环形链表）

第3次循环：
  A.next = B
  newTable[3] = A
  e = B
  结果：A → B → A（环形链表）

死循环！
```

**代码示例**：

```java
public class HashMapDeadLoopDemo {
    public static void main(String[] args) throws InterruptedException {
        final Map<String, String> map = new HashMap<>(2);
        
        // 先放入一些数据
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        
        // 创建多个线程同时put，触发扩容
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                for (int j = 0; j < 10000; j++) {
                    map.put("key_" + Thread.currentThread().getName() + "_" + j, "value");
                }
            }).start();
        }
        
        Thread.sleep(5000);
        
        // 尝试get，可能会死循环
        System.out.println(map.get("key1"));
    }
}
```

**注意**：JDK 1.8已经解决了这个问题（使用尾插法），但仍然不是线程安全的。

---

### 2.3 问题3：size不准确

**场景**：多个线程同时put，size字段可能不准确。

```java
public V put(K key, V value) {
    // ...
    ++modCount;
    if (++size > threshold)  // 非原子操作
        resize();
    // ...
}
```

**问题分析**：

```
++size 等价于：
1. temp = size
2. temp = temp + 1
3. size = temp

多线程执行：
t1: 线程1读取size=10
t2: 线程2读取size=10
t3: 线程1计算size=11
t4: 线程2计算size=11
t5: 线程1写入size=11
t6: 线程2写入size=11

结果：两次put，size只增加了1
```

---

### 2.4 问题4：Fast-fail异常

**场景**：一个线程在迭代，另一个线程修改HashMap。

```java
Map<String, String> map = new HashMap<>();
map.put("key1", "value1");
map.put("key2", "value2");

// 线程1：迭代
new Thread(() -> {
    for (String key : map.keySet()) {
        System.out.println(key);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}).start();

// 线程2：修改
new Thread(() -> {
    try {
        Thread.sleep(50);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    map.put("key3", "value3");  // 抛出ConcurrentModificationException
}).start();
```

**原因**：

```java
// 迭代器检查modCount
if (modCount != expectedModCount)
    throw new ConcurrentModificationException();
```

---

## 3. JDK 1.8的改进

### 3.1 解决了死循环问题

**JDK 1.8使用尾插法**：

```java
// JDK 1.8的扩容代码（简化）
Node<K,V> loHead = null, loTail = null;
Node<K,V> hiHead = null, hiTail = null;

do {
    next = e.next;
    if ((e.hash & oldCap) == 0) {
        if (loTail == null)
            loHead = e;
        else
            loTail.next = e;  // 尾插法
        loTail = e;
    } else {
        if (hiTail == null)
            hiHead = e;
        else
            hiTail.next = e;  // 尾插法
        hiTail = e;
    }
} while ((e = next) != null);
```

**优势**：
- 保持原有顺序，不会反转
- 不会形成环形链表
- 避免了死循环问题

**注意**：虽然解决了死循环，但仍然不是线程安全的！

---

### 3.2 仍然存在的问题

即使在JDK 1.8中，HashMap仍然不是线程安全的：

1. **数据覆盖**：仍然可能发生
2. **size不准确**：仍然可能发生
3. **Fast-fail异常**：仍然会抛出

---

## 4. 线程安全的解决方案

### 4.1 方案1：HashTable

**特点**：
- JDK 1.0引入
- 所有方法都加synchronized
- 线程安全，但性能差

**源码**：

```java
public class Hashtable<K,V> extends Dictionary<K,V>
    implements Map<K,V>, Cloneable, java.io.Serializable {
    
    public synchronized V put(K key, V value) {
        // ...
    }
    
    public synchronized V get(Object key) {
        // ...
    }
    
    public synchronized V remove(Object key) {
        // ...
    }
}
```

**优点**：
- 线程安全
- 实现简单

**缺点**：
- 性能差：所有操作都需要获取锁
- 锁粒度大：锁住整个对象
- 不允许null key和null value
- 已过时，不推荐使用

**性能分析**：

```
单线程场景：
  HashTable: 100ms
  HashMap: 50ms
  性能损失：50%

多线程场景（高并发）：
  HashTable: 1000ms（大量线程等待锁）
  ConcurrentHashMap: 200ms
  性能差距：5倍
```

---

### 4.2 方案2：Collections.synchronizedMap

**特点**：
- 使用装饰器模式
- 内部使用synchronized同步
- 性能与HashTable类似

**源码**：

```java
public static <K,V> Map<K,V> synchronizedMap(Map<K,V> m) {
    return new SynchronizedMap<>(m);
}

private static class SynchronizedMap<K,V>
    implements Map<K,V>, Serializable {
    
    private final Map<K,V> m;     // 被装饰的Map
    final Object mutex;           // 锁对象
    
    SynchronizedMap(Map<K,V> m) {
        this.m = Objects.requireNonNull(m);
        mutex = this;
    }
    
    public int size() {
        synchronized (mutex) { return m.size(); }
    }
    
    public V get(Object key) {
        synchronized (mutex) { return m.get(key); }
    }
    
    public V put(K key, V value) {
        synchronized (mutex) { return m.put(key, value); }
    }
    
    public V remove(Object key) {
        synchronized (mutex) { return m.remove(key); }
    }
}
```

**使用示例**：

```java
Map<String, String> map = Collections.synchronizedMap(new HashMap<>());
map.put("key1", "value1");
String value = map.get("key1");
```

**优点**：
- 线程安全
- 可以装饰任何Map实现

**缺点**：
- 性能差：所有操作都需要获取锁
- 锁粒度大：锁住整个对象
- 迭代时需要手动同步

**迭代时的陷阱**：

```java
Map<String, String> map = Collections.synchronizedMap(new HashMap<>());

// ❌ 错误：迭代时没有同步
for (String key : map.keySet()) {
    System.out.println(key);  // 可能抛出ConcurrentModificationException
}

// ✅ 正确：迭代时手动同步
synchronized (map) {
    for (String key : map.keySet()) {
        System.out.println(key);
    }
}
```

---

### 4.3 方案3：ConcurrentHashMap（推荐）

**特点**：
- JDK 1.5引入
- 专为并发设计
- 性能优异

#### JDK 1.7的实现：分段锁

**数据结构**：

```
ConcurrentHashMap (JDK 1.7)
├── Segment[0] (继承ReentrantLock)
│   └── HashEntry[]
├── Segment[1]
│   └── HashEntry[]
├── Segment[2]
│   └── HashEntry[]
└── ...
```

**核心思想**：
- 将数据分成多个段（Segment）
- 每个段独立加锁
- 不同段可以并发访问

**源码**：

```java
// JDK 1.7
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V>
    implements ConcurrentMap<K, V>, Serializable {
    
    // 默认并发级别：16
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;
    
    // Segment数组
    final Segment<K,V>[] segments;
    
    static final class Segment<K,V> extends ReentrantLock implements Serializable {
        transient volatile HashEntry<K,V>[] table;
        transient int count;
        
        V put(K key, int hash, V value, boolean onlyIfAbsent) {
            lock();  // 只锁当前Segment
            try {
                // put操作
            } finally {
                unlock();
            }
        }
    }
}
```

**优势**：
- 锁粒度小：只锁一个Segment
- 并发度高：最多支持16个线程并发写

**劣势**：
- 实现复杂
- 内存占用大

---

#### JDK 1.8的实现：CAS + synchronized

**数据结构**：

```
ConcurrentHashMap (JDK 1.8)
├── Node[] table (类似HashMap)
├── 使用CAS操作
└── 使用synchronized锁桶
```

**核心思想**：
- 取消Segment，直接使用Node数组
- 使用CAS进行无锁操作
- 冲突时使用synchronized锁桶

**源码**：

```java
// JDK 1.8
public class ConcurrentHashMap<K,V> extends AbstractMap<K,V>
    implements ConcurrentMap<K,V>, Serializable {
    
    transient volatile Node<K,V>[] table;
    
    public V put(K key, V value) {
        return putVal(key, value, false);
    }
    
    final V putVal(K key, V value, boolean onlyIfAbsent) {
        if (key == null || value == null) throw new NullPointerException();
        
        int hash = spread(key.hashCode());
        int binCount = 0;
        
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();  // 初始化
            
            // 情况1：桶为空，使用CAS插入
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value, null)))
                    break;  // CAS成功，退出
            }
            
            // 情况2：正在扩容，帮助扩容
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            
            // 情况3：桶不为空，使用synchronized
            else {
                V oldVal = null;
                synchronized (f) {  // 锁住桶的第一个节点
                    if (tabAt(tab, i) == f) {
                        // 链表或红黑树操作
                        // ...
                    }
                }
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        addCount(1L, binCount);
        return null;
    }
    
    // CAS操作
    static final <K,V> boolean casTabAt(Node<K,V>[] tab, int i,
                                        Node<K,V> c, Node<K,V> v) {
        return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
    }
}
```

**优势**：
- 锁粒度更小：只锁一个桶
- 使用CAS：无锁操作，性能更好
- 内存占用小：取消了Segment

**CAS vs synchronized**：

```
CAS（Compare And Swap）：
  - 无锁操作
  - 适合无冲突场景
  - 性能最好

synchronized：
  - 有锁操作
  - 适合有冲突场景
  - 性能较好（JDK 1.6优化后）
```

---

### 4.4 性能对比

**测试场景**：100个线程，每个线程put 10000个元素

| 方案 | 耗时 | 相对性能 |
|------|------|---------|
| **HashMap** | 崩溃 | - |
| **HashTable** | 5000ms | 1x |
| **Collections.synchronizedMap** | 4800ms | 1.04x |
| **ConcurrentHashMap (JDK 1.7)** | 1000ms | 5x |
| **ConcurrentHashMap (JDK 1.8)** | 800ms | 6.25x |

**结论**：ConcurrentHashMap性能远超HashTable和synchronizedMap。

---

### 4.5 方案选择

| 场景 | 推荐方案 | 原因 |
|------|---------|------|
| **单线程** | HashMap | 性能最好 |
| **多线程读多写少** | ConcurrentHashMap | 读操作无锁 |
| **多线程读写均衡** | ConcurrentHashMap | 锁粒度小 |
| **需要排序** | ConcurrentSkipListMap | 并发+有序 |
| **遗留代码** | Collections.synchronizedMap | 兼容性 |

**不推荐**：
- ❌ HashTable：已过时
- ❌ Collections.synchronizedMap：性能差

---

## 5. ConcurrentHashMap深入分析

### 5.1 为什么不允许null key和null value？

**HashMap允许null**：

```java
Map<String, String> map = new HashMap<>();
map.put(null, "value");  // 允许
map.put("key", null);    // 允许
```

**ConcurrentHashMap不允许null**：

```java
Map<String, String> map = new ConcurrentHashMap<>();
map.put(null, "value");  // NullPointerException
map.put("key", null);    // NullPointerException
```

**原因**：二义性问题

```java
// 场景1：key不存在
V value = map.get("key");  // 返回null

// 场景2：key存在，但value为null
map.put("key", null);
V value = map.get("key");  // 返回null

// 问题：无法区分key不存在还是value为null
```

**HashMap的解决方案**：

```java
// 使用containsKey判断
if (map.containsKey("key")) {
    // key存在，value可能为null
} else {
    // key不存在
}
```

**ConcurrentHashMap的问题**：

```java
// 多线程场景下，containsKey和get不是原子操作
if (map.containsKey("key")) {
    // 此时另一个线程可能删除了key
    V value = map.get("key");  // 可能返回null
}
```

**解决方案**：不允许null key和null value

---

### 5.2 size方法的实现

**问题**：如何在不锁住整个Map的情况下，获取准确的size？

**JDK 1.7的实现**：

```java
public int size() {
    final Segment<K,V>[] segments = this.segments;
    
    // 尝试2次不加锁统计
    for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
        int sum = 0;
        int mcsum = 0;
        for (int i = 0; i < segments.length; ++i) {
            sum += segments[i].count;
            mcsum += segments[i].modCount;
        }
        
        // 如果modCount没变，说明没有修改，返回结果
        if (mcsum == lastMcsum)
            return sum;
        
        lastMcsum = mcsum;
    }
    
    // 如果2次都失败，锁住所有Segment
    for (int i = 0; i < segments.length; ++i)
        segments[i].lock();
    
    try {
        int sum = 0;
        for (int i = 0; i < segments.length; ++i)
            sum += segments[i].count;
        return sum;
    } finally {
        for (int i = 0; i < segments.length; ++i)
            segments[i].unlock();
    }
}
```

**JDK 1.8的实现**：

```java
public int size() {
    long n = sumCount();
    return ((n < 0L) ? 0 :
            (n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
            (int)n);
}

final long sumCount() {
    CounterCell[] as = counterCells;
    CounterCell a;
    long sum = baseCount;
    if (as != null) {
        for (int i = 0; i < as.length; ++i) {
            if ((a = as[i]) != null)
                sum += a.value;
        }
    }
    return sum;
}
```

**优化**：使用CounterCell数组分散计数，减少竞争。

---

### 5.3 ConcurrentHashMap的局限性

#### 局限性1：弱一致性

**问题**：迭代器不保证看到最新的修改。

```java
Map<String, String> map = new ConcurrentHashMap<>();
map.put("key1", "value1");

// 线程1：迭代
Iterator<String> iterator = map.keySet().iterator();

// 线程2：添加元素
map.put("key2", "value2");

// 线程1继续迭代
while (iterator.hasNext()) {
    String key = iterator.next();
    // 可能看不到key2
}
```

**原因**：ConcurrentHashMap的迭代器是弱一致性的，不会抛出ConcurrentModificationException，但也不保证看到最新的修改。

---

#### 局限性2：复合操作不是原子的

**问题**：多个操作组合不是原子的。

```java
// ❌ 错误：不是原子操作
if (!map.containsKey("key")) {
    map.put("key", "value");
}

// ✅ 正确：使用putIfAbsent
map.putIfAbsent("key", "value");
```

**ConcurrentHashMap提供的原子操作**：

```java
// 如果key不存在，则put
V putIfAbsent(K key, V value);

// 如果key存在且值为oldValue，则替换为newValue
boolean replace(K key, V oldValue, V newValue);

// 如果key存在，则替换
V replace(K key, V value);

// 如果key存在且值为value，则删除
boolean remove(Object key, Object value);

// JDK 1.8新增：计算
V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);
V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);
V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);
```

---

## 6. 最佳实践

### 6.1 选择合适的Map实现

```java
// 单线程场景
Map<String, String> map = new HashMap<>();

// 多线程场景
Map<String, String> map = new ConcurrentHashMap<>();

// 需要排序
Map<String, String> map = new TreeMap<>();

// 需要保持插入顺序
Map<String, String> map = new LinkedHashMap<>();

// 多线程 + 排序
Map<String, String> map = new ConcurrentSkipListMap<>();
```

---

### 6.2 使用原子操作

```java
Map<String, Integer> map = new ConcurrentHashMap<>();

// ❌ 错误：不是原子操作
Integer count = map.get("key");
if (count == null) {
    count = 0;
}
map.put("key", count + 1);

// ✅ 正确：使用compute
map.compute("key", (k, v) -> v == null ? 1 : v + 1);

// ✅ 正确：使用merge
map.merge("key", 1, Integer::sum);
```

---

### 6.3 避免在迭代时修改

```java
Map<String, String> map = new ConcurrentHashMap<>();

// ❌ 错误：可能看不到最新的修改
for (String key : map.keySet()) {
    map.put("newKey", "newValue");  // 不推荐
}

// ✅ 正确：先收集要修改的key，再修改
List<String> keysToModify = new ArrayList<>();
for (String key : map.keySet()) {
    if (needModify(key)) {
        keysToModify.add(key);
    }
}
for (String key : keysToModify) {
    map.put(key, "newValue");
}
```

---

### 6.4 合理设置初始容量

```java
// 如果知道元素数量，设置合适的初始容量
int expectedSize = 10000;
Map<String, String> map = new ConcurrentHashMap<>(expectedSize);

// 避免频繁扩容
```

---

## 7. 总结

### 7.1 HashMap线程安全问题

| 问题 | JDK 1.7 | JDK 1.8 |
|------|---------|---------|
| **数据覆盖** | 存在 | 存在 |
| **死循环** | 存在 | 不存在 |
| **size不准确** | 存在 | 存在 |
| **Fast-fail异常** | 存在 | 存在 |

---

### 7.2 线程安全方案对比

| 方案 | 线程安全 | 性能 | null key | null value | 推荐 |
|------|---------|------|----------|-----------|------|
| **HashMap** | 否 | 最高 | 允许 | 允许 | 单线程 |
| **HashTable** | 是 | 低 | 不允许 | 不允许 | 不推荐 |
| **synchronizedMap** | 是 | 低 | 取决于被装饰的Map | 取决于被装饰的Map | 不推荐 |
| **ConcurrentHashMap** | 是 | 高 | 不允许 | 不允许 | 推荐 |

---

### 7.3 核心要点

1. **HashMap不是线程安全的**：多线程场景下会出现各种问题
2. **JDK 1.8解决了死循环**：使用尾插法，但仍不是线程安全的
3. **ConcurrentHashMap是最佳选择**：专为并发设计，性能优异
4. **使用原子操作**：避免复合操作的线程安全问题
5. **理解弱一致性**：ConcurrentHashMap的迭代器是弱一致性的

---

## 8. 下一步学习

在理解了HashMap的线程安全问题后，接下来我们将学习：

**HashMap最佳实践与性能优化**：掌握正确的使用方式和性能优化技巧

---

**继续阅读**：[05_HashMap最佳实践与性能优化.md](./05_HashMap最佳实践与性能优化.md)
