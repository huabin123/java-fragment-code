# 第二章：CopyOnWrite容器 - 写时复制的艺术

> **学习目标**：深入理解CopyOnWrite容器的设计思想、适用场景和性能特点

---

## 一、为什么需要CopyOnWrite容器？

### 1.1 ArrayList的线程安全问题

```java
// 问题：ArrayList在多线程下不安全
List<String> list = new ArrayList<>();

// 线程1：添加元素
list.add("item1");

// 线程2：遍历
for (String item : list) {
    // ConcurrentModificationException
}

// 问题：
// 1. 并发修改异常
// 2. 数据不一致
// 3. 索引越界
```

### 1.2 Collections.synchronizedList的性能问题

```java
// synchronizedList：包装ArrayList
List<String> list = Collections.synchronizedList(new ArrayList<>());

// 实现：
public boolean add(E e) {
    synchronized (mutex) {
        return c.add(e);
    }
}

public E get(int index) {
    synchronized (mutex) {
        return c.get(index);
    }
}

// 问题：
// ❌ 读写互斥（读也要加锁）
// ❌ 性能差（高并发下）
// ❌ 迭代需要手动加锁
```

### 1.3 CopyOnWriteArrayList的解决方案

```java
// CopyOnWriteArrayList：读多写少的场景
List<String> list = new CopyOnWriteArrayList<>();

// 优势：
// ✅ 读操作无锁
// ✅ 写操作复制数组
// ✅ 适合读多写少
// ✅ 迭代不会抛异常
```

---

## 二、CopyOnWrite的核心思想

### 2.1 写时复制（Copy-On-Write）

```
传统方式：
读写共享同一个数组
    ↓
[Array] ← 读线程1
    ↑     读线程2
    ↓     写线程
  需要加锁

CopyOnWrite方式：
写操作复制新数组
    ↓
[旧Array] ← 读线程1、读线程2（无锁）
    ↓
[新Array] ← 写线程（复制并修改）
    ↓
切换引用
    ↓
[新Array] ← 所有线程
```

**核心思想**：

```
1. 读写分离：
   - 读操作：直接读原数组
   - 写操作：复制新数组

2. 最终一致性：
   - 读可能读到旧数据
   - 写完成后才可见

3. 适用场景：
   - 读多写少
   - 允许短暂的数据不一致
```

---

## 三、CopyOnWriteArrayList源码分析

### 3.1 核心数据结构

```java
public class CopyOnWriteArrayList<E>
    implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    
    // 可重入锁（保护写操作）
    final transient ReentrantLock lock = new ReentrantLock();
    
    // 数组（volatile保证可见性）
    private transient volatile Object[] array;
    
    // 获取数组
    final Object[] getArray() {
        return array;
    }
    
    // 设置数组
    final void setArray(Object[] a) {
        array = a;
    }
    
    // 构造方法
    public CopyOnWriteArrayList() {
        setArray(new Object[0]);
    }
}
```

### 3.2 add操作详解

```java
public boolean add(E e) {
    final ReentrantLock lock = this.lock;
    lock.lock();  // 加锁
    try {
        // 1. 获取原数组
        Object[] elements = getArray();
        int len = elements.length;
        
        // 2. 复制新数组（长度+1）
        Object[] newElements = Arrays.copyOf(elements, len + 1);
        
        // 3. 添加新元素
        newElements[len] = e;
        
        // 4. 切换数组引用
        setArray(newElements);
        
        return true;
    } finally {
        lock.unlock();  // 释放锁
    }
}
```

**add流程图**：

```
开始
  ↓
获取锁
  ↓
获取原数组
  ↓
复制新数组（长度+1）
  ↓
在新数组末尾添加元素
  ↓
切换数组引用（volatile写）
  ↓
释放锁
  ↓
结束

特点：
1. 整个过程加锁
2. 复制整个数组
3. 读线程可能读到旧数组
```

### 3.3 get操作详解

```java
public E get(int index) {
    return get(getArray(), index);
}

private E get(Object[] a, int index) {
    return (E) a[index];
}

// 特点：
// 1. 无锁
// 2. 直接读数组
// 3. 可能读到旧数据
```

### 3.4 remove操作详解

```java
public E remove(int index) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 1. 获取原数组
        Object[] elements = getArray();
        int len = elements.length;
        E oldValue = get(elements, index);
        int numMoved = len - index - 1;
        
        // 2. 复制新数组（长度-1）
        if (numMoved == 0)
            setArray(Arrays.copyOf(elements, len - 1));
        else {
            Object[] newElements = new Object[len - 1];
            System.arraycopy(elements, 0, newElements, 0, index);
            System.arraycopy(elements, index + 1, newElements, index, numMoved);
            setArray(newElements);
        }
        
        return oldValue;
    } finally {
        lock.unlock();
    }
}
```

### 3.5 迭代器

```java
public Iterator<E> iterator() {
    return new COWIterator<E>(getArray(), 0);
}

static final class COWIterator<E> implements ListIterator<E> {
    // 快照数组
    private final Object[] snapshot;
    private int cursor;
    
    private COWIterator(Object[] elements, int initialCursor) {
        cursor = initialCursor;
        snapshot = elements;  // 保存快照
    }
    
    public E next() {
        if (!hasNext())
            throw new NoSuchElementException();
        return (E) snapshot[cursor++];
    }
    
    // 不支持remove
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
```

**迭代器特点**：

```
1. 快照迭代器：
   - 迭代时保存数组快照
   - 不会看到后续修改
   - 不会抛ConcurrentModificationException

2. 不支持修改：
   - remove()抛异常
   - add()抛异常
   - set()抛异常

3. 弱一致性：
   - 迭代的是快照
   - 可能是旧数据
```

---

## 四、CopyOnWriteArraySet

### 4.1 基于CopyOnWriteArrayList实现

```java
public class CopyOnWriteArraySet<E> extends AbstractSet<E>
        implements java.io.Serializable {
    
    // 内部使用CopyOnWriteArrayList
    private final CopyOnWriteArrayList<E> al;
    
    public CopyOnWriteArraySet() {
        al = new CopyOnWriteArrayList<E>();
    }
    
    public boolean add(E e) {
        return al.addIfAbsent(e);  // 不存在才添加
    }
    
    public boolean remove(Object o) {
        return al.remove(o);
    }
    
    public Iterator<E> iterator() {
        return al.iterator();
    }
}
```

### 4.2 addIfAbsent实现

```java
public boolean addIfAbsent(E e) {
    Object[] snapshot = getArray();
    return indexOf(e, snapshot, 0, snapshot.length) >= 0 ? false :
        addIfAbsent(e, snapshot);
}

private boolean addIfAbsent(E e, Object[] snapshot) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        Object[] current = getArray();
        int len = current.length;
        
        // 再次检查（可能已被其他线程添加）
        if (snapshot != current) {
            int common = Math.min(snapshot.length, len);
            for (int i = 0; i < common; i++)
                if (current[i] != snapshot[i] && eq(e, current[i]))
                    return false;
            if (indexOf(e, current, common, len) >= 0)
                return false;
        }
        
        // 复制并添加
        Object[] newElements = Arrays.copyOf(current, len + 1);
        newElements[len] = e;
        setArray(newElements);
        return true;
    } finally {
        lock.unlock();
    }
}
```

---

## 五、性能分析

### 5.1 时间复杂度

| 操作 | CopyOnWriteArrayList | ArrayList | Vector |
|------|---------------------|-----------|--------|
| get(i) | O(1) | O(1) | O(1) |
| add(e) | O(n) | O(1) | O(1) |
| remove(i) | O(n) | O(n) | O(n) |
| contains(e) | O(n) | O(n) | O(n) |

### 5.2 内存占用

```java
// 写操作会复制整个数组
List<String> list = new CopyOnWriteArrayList<>();

// 添加1000个元素
for (int i = 0; i < 1000; i++) {
    list.add("item" + i);  // 每次add都复制数组
}

// 内存占用：
// - 原数组：1000个元素
// - 写操作时：临时复制一份（2000个元素）
// - 峰值内存：2倍

// 结论：
// ❌ 内存占用大
// ❌ 频繁写操作会导致大量GC
```

### 5.3 性能测试

```java
public class PerformanceTest {
    private static final int THREAD_COUNT = 10;
    private static final int READ_COUNT = 1000000;
    private static final int WRITE_COUNT = 100;
    
    // 测试1：读多写少（CopyOnWrite优势）
    public static void testReadHeavy() {
        List<String> list = new CopyOnWriteArrayList<>();
        // 90%读，10%写
        // CopyOnWriteArrayList性能好
    }
    
    // 测试2：写多读少（CopyOnWrite劣势）
    public static void testWriteHeavy() {
        List<String> list = new CopyOnWriteArrayList<>();
        // 10%读，90%写
        // CopyOnWriteArrayList性能差
    }
}
```

**性能结论**：

```
读多写少（90%读，10%写）：
- CopyOnWriteArrayList：快
- Collections.synchronizedList：慢

写多读少（10%读，90%写）：
- CopyOnWriteArrayList：慢
- Collections.synchronizedList：快

读写均衡（50%读，50%写）：
- CopyOnWriteArrayList：慢
- Collections.synchronizedList：快
```

---

## 六、适用场景

### 6.1 适合的场景

```java
// ✅ 场景1：监听器列表
public class EventSource {
    private final List<EventListener> listeners = 
        new CopyOnWriteArrayList<>();
    
    public void addListener(EventListener listener) {
        listeners.add(listener);  // 写少
    }
    
    public void fireEvent(Event event) {
        for (EventListener listener : listeners) {
            listener.onEvent(event);  // 读多
        }
    }
}

// ✅ 场景2：配置列表
public class ConfigManager {
    private final List<Config> configs = 
        new CopyOnWriteArrayList<>();
    
    public void updateConfig(Config config) {
        configs.add(config);  // 写少
    }
    
    public Config getConfig(String key) {
        for (Config config : configs) {  // 读多
            if (config.getKey().equals(key)) {
                return config;
            }
        }
        return null;
    }
}

// ✅ 场景3：黑白名单
public class BlackList {
    private final Set<String> blackList = 
        new CopyOnWriteArraySet<>();
    
    public void addToBlackList(String user) {
        blackList.add(user);  // 写少
    }
    
    public boolean isBlocked(String user) {
        return blackList.contains(user);  // 读多
    }
}
```

### 6.2 不适合的场景

```java
// ❌ 场景1：频繁写入
List<String> list = new CopyOnWriteArrayList<>();
for (int i = 0; i < 10000; i++) {
    list.add("item" + i);  // 每次都复制数组，性能差
}

// ❌ 场景2：实时性要求高
list.add("new item");
// 读线程可能看不到最新数据

// ❌ 场景3：大数据量
List<byte[]> list = new CopyOnWriteArrayList<>();
list.add(new byte[1024 * 1024]);  // 1MB
// 每次写都复制1MB，内存占用大
```

---

## 七、常见陷阱

### 7.1 内存占用问题

```java
// ❌ 错误：大对象使用CopyOnWrite
List<BigObject> list = new CopyOnWriteArrayList<>();
list.add(new BigObject());  // 复制整个数组

// ✅ 正确：小对象或引用
List<String> list = new CopyOnWriteArrayList<>();
list.add("small string");
```

### 7.2 数据一致性问题

```java
// ❌ 错误：期望立即可见
list.add("item");
// 其他线程可能看不到

// ✅ 正确：理解弱一致性
list.add("item");
// 允许短暂的不一致
```

### 7.3 迭代器不支持修改

```java
// ❌ 错误：迭代时修改
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String item = it.next();
    it.remove();  // UnsupportedOperationException
}

// ✅ 正确：直接在list上修改
for (String item : new ArrayList<>(list)) {
    if (shouldRemove(item)) {
        list.remove(item);
    }
}
```

---

## 八、最佳实践

### 8.1 选择合适的容器

```java
// 读多写少 → CopyOnWriteArrayList
List<String> list = new CopyOnWriteArrayList<>();

// 读写均衡 → Collections.synchronizedList
List<String> list = Collections.synchronizedList(new ArrayList<>());

// 高并发写 → ConcurrentLinkedQueue
Queue<String> queue = new ConcurrentLinkedQueue<>();
```

### 8.2 批量操作

```java
// ❌ 不好：多次写操作
for (String item : items) {
    list.add(item);  // 每次都复制数组
}

// ✅ 好的：批量添加
list.addAll(items);  // 只复制一次
```

### 8.3 避免大对象

```java
// ❌ 不好：存储大对象
List<byte[]> list = new CopyOnWriteArrayList<>();

// ✅ 好的：存储引用
List<String> list = new CopyOnWriteArrayList<>();
```

---

## 九、总结

### 9.1 核心要点

1. **写时复制**：写操作复制新数组
2. **读操作无锁**：直接读原数组
3. **弱一致性**：读可能读到旧数据
4. **适用场景**：读多写少
5. **内存占用**：写操作时2倍内存

### 9.2 优缺点

```
优势：
✅ 读操作无锁，性能好
✅ 迭代不会抛异常
✅ 线程安全

劣势：
❌ 写操作性能差（复制数组）
❌ 内存占用大
❌ 弱一致性（数据可能不是最新）
```

### 9.3 思考题

1. **为什么CopyOnWrite适合读多写少？**
2. **CopyOnWrite如何保证线程安全？**
3. **为什么迭代器不支持remove？**
4. **什么时候不应该使用CopyOnWrite？**

---

**下一章预告**：我们将学习并发队列ConcurrentLinkedQueue的无锁实现。

---

**参考资料**：
- 《Java并发编程实战》第5章
- JDK源码：`java.util.concurrent.CopyOnWriteArrayList`
