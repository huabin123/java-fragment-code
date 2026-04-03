# LinkedHashMap核心原理与数据结构

## 1. LinkedHashMap的底层数据结构

### 1.1 整体架构

LinkedHashMap = HashMap + 双向链表

```
LinkedHashMap内部结构：

HashMap部分（数组+链表/红黑树）：
table[0] → Entry1
table[1] → null
table[2] → Entry2
table[3] → Entry3

双向链表部分（维护顺序）：
head → Entry1 ↔ Entry2 ↔ Entry3 ↔ tail

每个Entry既在HashMap的桶中，又在双向链表中
```

**示意图**：

```
HashMap的hash表：
┌─────┬─────┬─────┬─────┐
│  0  │  1  │  2  │  3  │
└──┬──┴─────┴──┬──┴──┬──┘
   │           │     │
   ↓           ↓     ↓
  Entry1     Entry2 Entry3

双向链表（维护顺序）：
head → Entry1 ↔ Entry2 ↔ Entry3 ← tail
```

---

### 1.2 类继承关系

```java
public class LinkedHashMap<K,V>
    extends HashMap<K,V>
    implements Map<K,V> {
    
    // 双向链表的头节点
    transient LinkedHashMap.Entry<K,V> head;
    
    // 双向链表的尾节点
    transient LinkedHashMap.Entry<K,V> tail;
    
    // 访问顺序标志：true=访问顺序，false=插入顺序
    final boolean accessOrder;
}
```

**继承关系**：

```
Object
  ↓
AbstractMap
  ↓
HashMap
  ↓
LinkedHashMap
```

---

### 1.3 Entry节点结构

**HashMap的Node节点**：

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;  // 指向hash表中的下一个节点
}
```

**LinkedHashMap的Entry节点**：

```java
static class Entry<K,V> extends HashMap.Node<K,V> {
    Entry<K,V> before;  // 双向链表的前一个节点
    Entry<K,V> after;   // 双向链表的后一个节点
    
    Entry(int hash, K key, V value, Node<K,V> next) {
        super(hash, key, value, next);
    }
}
```

**节点结构对比**：

```
HashMap.Node:
┌─────────────────┐
│ hash            │
│ key             │
│ value           │
│ next  ────────→ │ (hash表中的下一个)
└─────────────────┘

LinkedHashMap.Entry:
┌─────────────────┐
│ hash            │
│ key             │
│ value           │
│ next  ────────→ │ (hash表中的下一个)
│ before ←──────  │ (链表中的前一个)
│ after  ──────→  │ (链表中的后一个)
└─────────────────┘
```

---

### 1.4 核心字段

```java
public class LinkedHashMap<K,V> extends HashMap<K,V> {
    
    // 双向链表的头节点（最老的元素）
    transient LinkedHashMap.Entry<K,V> head;
    
    // 双向链表的尾节点（最新的元素）
    transient LinkedHashMap.Entry<K,V> tail;
    
    // 访问顺序标志
    // false: 插入顺序（默认）
    // true: 访问顺序（用于LRU缓存）
    final boolean accessOrder;
}
```

---

## 2. 插入顺序 vs 访问顺序

### 2.1 插入顺序（accessOrder=false，默认）

**特点**：按元素插入的顺序维护链表。

```java
Map<String, String> map = new LinkedHashMap<>();
map.put("key1", "value1");  // 插入
map.put("key2", "value2");  // 插入
map.put("key3", "value3");  // 插入

map.get("key1");  // 访问，不改变顺序

// 遍历顺序：key1, key2, key3（插入顺序）
```

**链表变化**：

```
插入key1:
head → key1 ← tail

插入key2:
head → key1 ↔ key2 ← tail

插入key3:
head → key1 ↔ key2 ↔ key3 ← tail

访问key1（不改变顺序）:
head → key1 ↔ key2 ↔ key3 ← tail
```

---

### 2.2 访问顺序（accessOrder=true）

**特点**：按元素访问的顺序维护链表，最近访问的元素移到链表尾部。

```java
Map<String, String> map = new LinkedHashMap<>(16, 0.75f, true);
map.put("key1", "value1");  // 插入
map.put("key2", "value2");  // 插入
map.put("key3", "value3");  // 插入

map.get("key1");  // 访问，key1移到尾部

// 遍历顺序：key2, key3, key1（key1最近访问）
```

**链表变化**：

```
插入key1:
head → key1 ← tail

插入key2:
head → key1 ↔ key2 ← tail

插入key3:
head → key1 ↔ key2 ↔ key3 ← tail

访问key1（移到尾部）:
head → key2 ↔ key3 ↔ key1 ← tail
```

---

### 2.3 accessOrder的作用

```java
// 构造函数
public LinkedHashMap(int initialCapacity,
                     float loadFactor,
                     boolean accessOrder) {
    super(initialCapacity, loadFactor);
    this.accessOrder = accessOrder;
}

// 默认构造函数：accessOrder=false（插入顺序）
public LinkedHashMap() {
    super();
    accessOrder = false;
}
```

**accessOrder的影响**：

| accessOrder | 顺序类型 | get操作 | put操作 | 适用场景 |
|-------------|---------|---------|---------|---------|
| **false** | 插入顺序 | 不改变顺序 | 插入到尾部 | 保持插入顺序 |
| **true** | 访问顺序 | 移到尾部 | 插入到尾部 | LRU缓存 |

---

## 3. 核心操作原理

### 3.1 插入元素（put）

**流程**：

```
1. 调用HashMap的put方法，将元素插入hash表
2. 在HashMap的newNode方法中创建LinkedHashMap.Entry
3. 调用linkNodeLast方法，将新节点添加到链表尾部
```

**关键方法**：

```java
// HashMap中的方法，LinkedHashMap重写
Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
    LinkedHashMap.Entry<K,V> p =
        new LinkedHashMap.Entry<K,V>(hash, key, value, e);
    linkNodeLast(p);  // 添加到链表尾部
    return p;
}

// 将节点添加到链表尾部
private void linkNodeLast(LinkedHashMap.Entry<K,V> p) {
    LinkedHashMap.Entry<K,V> last = tail;
    tail = p;
    if (last == null)
        head = p;
    else {
        p.before = last;
        last.after = p;
    }
}
```

**示意图**：

```
原链表：
head → A ↔ B ← tail

插入C：
1. 创建Entry C
2. C.before = B
3. B.after = C
4. tail = C

结果：
head → A ↔ B ↔ C ← tail
```

---

### 3.2 访问元素（get）

**插入顺序模式（accessOrder=false）**：

```java
public V get(Object key) {
    Node<K,V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    // accessOrder=false，不调用afterNodeAccess
    return e.value;
}
```

**访问顺序模式（accessOrder=true）**：

```java
public V get(Object key) {
    Node<K,V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    if (accessOrder)
        afterNodeAccess(e);  // 将节点移到链表尾部
    return e.value;
}

void afterNodeAccess(Node<K,V> e) {
    LinkedHashMap.Entry<K,V> last;
    // 如果e不是尾节点，将e移到尾部
    if (accessOrder && (last = tail) != e) {
        LinkedHashMap.Entry<K,V> p =
            (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
        p.after = null;
        
        // 从原位置移除
        if (b == null)
            head = a;
        else
            b.after = a;
        
        if (a != null)
            a.before = b;
        else
            last = b;
        
        // 添加到尾部
        if (last == null)
            head = p;
        else {
            p.before = last;
            last.after = p;
        }
        tail = p;
    }
}
```

**示意图**：

```
原链表：
head → A ↔ B ↔ C ← tail

访问B（accessOrder=true）：
1. 从原位置移除B
   head → A ↔ C ← tail

2. 将B添加到尾部
   head → A ↔ C ↔ B ← tail
```

---

### 3.3 删除元素（remove）

**流程**：

```
1. 调用HashMap的remove方法，从hash表中删除
2. 调用afterNodeRemoval方法，从链表中删除
```

**关键方法**：

```java
void afterNodeRemoval(Node<K,V> e) {
    LinkedHashMap.Entry<K,V> p =
        (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
    p.before = p.after = null;
    
    if (b == null)
        head = a;
    else
        b.after = a;
    
    if (a == null)
        tail = b;
    else
        a.before = b;
}
```

**示意图**：

```
原链表：
head → A ↔ B ↔ C ← tail

删除B：
1. A.after = C
2. C.before = A
3. B.before = null
4. B.after = null

结果：
head → A ↔ C ← tail
```

---

### 3.4 遍历元素

**LinkedHashMap的遍历**：

```java
// 遍历链表，而不是hash表
for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
    K key = e.key;
    V value = e.value;
    // 处理元素
}
```

**对比HashMap的遍历**：

```java
// HashMap遍历hash表
for (int i = 0; i < table.length; i++) {
    for (Node<K,V> e = table[i]; e != null; e = e.next) {
        // 处理元素
    }
}
```

**优势**：
- LinkedHashMap遍历链表，顺序可预测
- HashMap遍历hash表，顺序不可预测

---

## 4. LRU缓存的实现原理

### 4.1 LRU算法

**LRU（Least Recently Used）**：最近最少使用算法。

**核心思想**：
- 最近访问的元素放在链表尾部
- 最久未访问的元素在链表头部
- 当缓存满时，删除链表头部的元素

---

### 4.2 removeEldestEntry方法

```java
protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return false;  // 默认不删除
}
```

**作用**：
- 在插入新元素后调用
- 如果返回true，删除最老的元素（链表头部）
- 可以重写此方法实现LRU缓存

**调用时机**：

```java
void afterNodeInsertion(boolean evict) {
    LinkedHashMap.Entry<K,V> first;
    if (evict && (first = head) != null && removeEldestEntry(first)) {
        K key = first.key;
        removeNode(hash(key), key, null, false, true);
    }
}
```

---

### 4.3 LRU缓存实现

```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    
    public LRUCache(int maxSize) {
        // accessOrder=true：按访问顺序
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        // 当size超过maxSize时，删除最老的元素
        return size() > maxSize;
    }
}
```

**工作流程**：

```
初始状态（maxSize=3）：
head → ← tail

put(1, "A"):
head → 1 ← tail

put(2, "B"):
head → 1 ↔ 2 ← tail

put(3, "C"):
head → 1 ↔ 2 ↔ 3 ← tail

get(1)（访问1，移到尾部）:
head → 2 ↔ 3 ↔ 1 ← tail

put(4, "D")（插入4，删除最老的2）:
1. 插入4到尾部：head → 2 ↔ 3 ↔ 1 ↔ 4 ← tail
2. size > maxSize，删除head（2）
3. 结果：head → 3 ↔ 1 ↔ 4 ← tail
```

---

## 5. 内存结构分析

### 5.1 LinkedHashMap对象内存占用

```
LinkedHashMap对象：
├── HashMap的字段：
│   ├── table引用：4字节
│   ├── size：4字节
│   ├── modCount：4字节
│   ├── threshold：4字节
│   └── loadFactor：4字节
├── LinkedHashMap的字段：
│   ├── head引用：4字节
│   ├── tail引用：4字节
│   └── accessOrder：1字节
├── 对象头：12字节
└── 对齐：3字节
总计：48字节
```

---

### 5.2 Entry节点内存占用

```
LinkedHashMap.Entry节点：
├── HashMap.Node的字段：
│   ├── hash：4字节
│   ├── key引用：4字节
│   ├── value引用：4字节
│   └── next引用：4字节
├── LinkedHashMap.Entry的字段：
│   ├── before引用：4字节
│   └── after引用：4字节
├── 对象头：12字节
└── 对齐：4字节
总计：40字节
```

---

### 5.3 内存占用对比

**存储1000个元素**：

```
HashMap：
- HashMap对象：40字节
- table数组：16 + 1024 * 4 = 4112字节
- Node节点：1000 * 28 = 28000字节
- 总计：32152字节

LinkedHashMap：
- LinkedHashMap对象：48字节
- table数组：16 + 1024 * 4 = 4112字节
- Entry节点：1000 * 40 = 40000字节
- 总计：44160字节

结论：LinkedHashMap比HashMap多占用约37%的内存
```

---

## 6. 时间复杂度分析

| 操作 | HashMap | LinkedHashMap | 说明 |
|------|---------|--------------|------|
| **put** | O(1) | O(1) | 额外维护链表，常数时间 |
| **get** | O(1) | O(1) | 可能需要移动节点，常数时间 |
| **remove** | O(1) | O(1) | 额外维护链表，常数时间 |
| **containsKey** | O(1) | O(1) | 同HashMap |
| **遍历** | O(n) | O(n) | LinkedHashMap遍历链表更快 |

**注意**：
- LinkedHashMap的操作时间复杂度与HashMap相同
- 但常数因子略大（需要维护链表）
- 遍历性能更好（链表遍历比hash表遍历快）

---

## 7. LinkedHashMap的设计亮点

### 7.1 亮点1：继承HashMap

**设计**：继承HashMap，复用HashMap的实现。

**优势**：
- 代码复用，减少重复
- 保持HashMap的高性能
- 只需关注顺序维护

---

### 7.2 亮点2：回调方法

**设计**：通过回调方法维护链表。

```java
// HashMap中的回调方法（空实现）
void afterNodeAccess(Node<K,V> p) { }
void afterNodeInsertion(boolean evict) { }
void afterNodeRemoval(Node<K,V> p) { }

// LinkedHashMap重写这些方法
void afterNodeAccess(Node<K,V> e) {
    // 维护访问顺序
}
void afterNodeInsertion(boolean evict) {
    // 可能删除最老的元素
}
void afterNodeRemoval(Node<K,V> e) {
    // 从链表中删除
}
```

**优势**：
- 解耦，HashMap不需要知道LinkedHashMap的存在
- 扩展性好，易于维护

---

### 7.3 亮点3：accessOrder参数

**设计**：通过accessOrder参数控制顺序类型。

**优势**：
- 灵活，可以选择插入顺序或访问顺序
- 一个类实现两种功能

---

### 7.4 亮点4：removeEldestEntry方法

**设计**：提供removeEldestEntry方法，让子类决定是否删除最老的元素。

**优势**：
- 模板方法模式
- 易于实现LRU缓存
- 扩展性好

---

## 8. 总结

### 8.1 核心数据结构

```
LinkedHashMap = HashMap + 双向链表

HashMap部分：负责快速查找（O(1)）
双向链表部分：负责维护顺序
```

---

### 8.2 核心特性

1. **保持顺序**：插入顺序或访问顺序
2. **高性能**：保持HashMap的O(1)性能
3. **实现LRU缓存**：通过accessOrder和removeEldestEntry
4. **易于使用**：API与HashMap完全一致

---

### 8.3 核心优势

1. **继承HashMap**：复用HashMap的实现
2. **回调方法**：通过回调维护链表
3. **accessOrder参数**：灵活控制顺序类型
4. **removeEldestEntry方法**：易于实现LRU缓存

---

### 8.4 下一步学习

在理解了LinkedHashMap的核心原理和数据结构后，接下来我们将深入学习：

**LinkedHashMap源码深度剖析**：分析put/get/remove的完整实现

---

**继续阅读**：[03_LinkedHashMap源码深度剖析.md](./03_LinkedHashMap源码深度剖析.md)
