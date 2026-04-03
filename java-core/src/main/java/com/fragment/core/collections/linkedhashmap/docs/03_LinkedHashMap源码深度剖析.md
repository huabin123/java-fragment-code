# LinkedHashMap源码深度剖析

## 1. 类定义与核心字段

### 1.1 类声明

```java
public class LinkedHashMap<K,V>
    extends HashMap<K,V>
    implements Map<K,V> {
    
    private static final long serialVersionUID = 3801124242820219131L;
}
```

---

### 1.2 核心字段

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

### 1.3 Entry节点定义

```java
static class Entry<K,V> extends HashMap.Node<K,V> {
    Entry<K,V> before, after;  // 双向链表指针
    
    Entry(int hash, K key, V value, Node<K,V> next) {
        super(hash, key, value, next);
    }
}
```

---

## 2. 构造方法

### 2.1 所有构造方法

```java
// 构造方法1：指定初始容量和负载因子
public LinkedHashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
    accessOrder = false;  // 默认插入顺序
}

// 构造方法2：指定初始容量
public LinkedHashMap(int initialCapacity) {
    super(initialCapacity);
    accessOrder = false;
}

// 构造方法3：无参构造
public LinkedHashMap() {
    super();
    accessOrder = false;
}

// 构造方法4：从Map构造
public LinkedHashMap(Map<? extends K, ? extends V> m) {
    super();
    accessOrder = false;
    putMapEntries(m, false);
}

// 构造方法5：指定初始容量、负载因子和访问顺序
public LinkedHashMap(int initialCapacity,
                     float loadFactor,
                     boolean accessOrder) {
    super(initialCapacity, loadFactor);
    this.accessOrder = accessOrder;
}
```

**关键点**：
- 前4个构造方法：accessOrder=false（插入顺序）
- 第5个构造方法：可以指定accessOrder（访问顺序）

---

## 3. 插入元素的完整实现

### 3.1 put方法

LinkedHashMap没有重写put方法，直接使用HashMap的put方法：

```java
// HashMap的put方法
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
               boolean evict) {
    // ... HashMap的put逻辑
    
    // 关键：调用newNode创建节点
    tab[i] = newNode(hash, key, value, null);
    
    // ... 其他逻辑
    
    // 关键：插入后的回调
    afterNodeInsertion(evict);
    return null;
}
```

---

### 3.2 newNode方法（创建节点）

LinkedHashMap重写了newNode方法：

```java
// HashMap的newNode方法（创建普通Node）
Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
    return new Node<>(hash, key, value, next);
}

// LinkedHashMap重写newNode方法（创建Entry并添加到链表）
Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
    LinkedHashMap.Entry<K,V> p =
        new LinkedHashMap.Entry<K,V>(hash, key, value, e);
    linkNodeLast(p);  // 添加到链表尾部
    return p;
}
```

---

### 3.3 linkNodeLast方法（添加到链表尾部）

```java
private void linkNodeLast(LinkedHashMap.Entry<K,V> p) {
    LinkedHashMap.Entry<K,V> last = tail;
    tail = p;
    if (last == null)
        head = p;  // 链表为空，p成为头节点
    else {
        p.before = last;
        last.after = p;
    }
}
```

**流程图**：

```
空链表插入第一个元素：
head = null, tail = null
  ↓
linkNodeLast(A)
  ↓
tail = A
head = A
  ↓
head → A ← tail

非空链表插入元素：
head → A ← tail
  ↓
linkNodeLast(B)
  ↓
B.before = A
A.after = B
tail = B
  ↓
head → A ↔ B ← tail
```

---

### 3.4 afterNodeInsertion方法（插入后回调）

```java
void afterNodeInsertion(boolean evict) {
    LinkedHashMap.Entry<K,V> first;
    // evict=true 且 head不为空 且 removeEldestEntry返回true
    if (evict && (first = head) != null && removeEldestEntry(first)) {
        K key = first.key;
        removeNode(hash(key), key, null, false, true);
    }
}

// 默认实现：不删除最老的元素
protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return false;
}
```

**作用**：
- 在插入新元素后调用
- 如果removeEldestEntry返回true，删除最老的元素（head）
- 用于实现LRU缓存

---

## 4. 访问元素的完整实现

### 4.1 get方法

```java
public V get(Object key) {
    Node<K,V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    if (accessOrder)
        afterNodeAccess(e);  // 访问顺序模式，移动节点
    return e.value;
}
```

**关键点**：
- 如果accessOrder=true，调用afterNodeAccess
- 如果accessOrder=false，不调用afterNodeAccess

---

### 4.2 getOrDefault方法

```java
public V getOrDefault(Object key, V defaultValue) {
   Node<K,V> e;
   if ((e = getNode(hash(key), key)) == null)
       return defaultValue;
   if (accessOrder)
       afterNodeAccess(e);
   return e.value;
}
```

---

### 4.3 afterNodeAccess方法（访问后回调）

```java
void afterNodeAccess(Node<K,V> e) {
    LinkedHashMap.Entry<K,V> last;
    // 如果accessOrder=true 且 e不是尾节点
    if (accessOrder && (last = tail) != e) {
        LinkedHashMap.Entry<K,V> p =
            (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
        p.after = null;
        
        // 步骤1：从原位置移除p
        if (b == null)
            head = a;  // p是头节点
        else
            b.after = a;
        
        if (a != null)
            a.before = b;
        else
            last = b;  // p是尾节点
        
        // 步骤2：将p添加到尾部
        if (last == null)
            head = p;  // 链表只有一个元素
        else {
            p.before = last;
            last.after = p;
        }
        tail = p;
        ++modCount;
    }
}
```

**流程图**：

```
原链表：
head → A ↔ B ↔ C ↔ D ← tail

访问B（accessOrder=true）：

步骤1：从原位置移除B
  A.after = C
  C.before = A
  B.after = null
  
  结果：head → A ↔ C ↔ D ← tail

步骤2：将B添加到尾部
  B.before = D
  D.after = B
  tail = B
  
  结果：head → A ↔ C ↔ D ↔ B ← tail
```

---

## 5. 删除元素的完整实现

### 5.1 remove方法

LinkedHashMap没有重写remove方法，使用HashMap的remove方法：

```java
// HashMap的remove方法
public V remove(Object key) {
    Node<K,V> e;
    return (e = removeNode(hash(key), key, null, false, true)) == null ?
        null : e.value;
}

final Node<K,V> removeNode(int hash, Object key, Object value,
                           boolean matchValue, boolean movable) {
    // ... HashMap的remove逻辑
    
    // 关键：删除后的回调
    afterNodeRemoval(node);
    return node;
}
```

---

### 5.2 afterNodeRemoval方法（删除后回调）

```java
void afterNodeRemoval(Node<K,V> e) {
    LinkedHashMap.Entry<K,V> p =
        (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
    
    // 断开p的链接
    p.before = p.after = null;
    
    // 更新前一个节点的after指针
    if (b == null)
        head = a;  // p是头节点
    else
        b.after = a;
    
    // 更新后一个节点的before指针
    if (a == null)
        tail = b;  // p是尾节点
    else
        a.before = b;
}
```

**流程图**：

```
原链表：
head → A ↔ B ↔ C ↔ D ← tail

删除B：

步骤1：断开B的链接
  B.before = null
  B.after = null

步骤2：连接A和C
  A.after = C
  C.before = A

结果：
head → A ↔ C ↔ D ← tail
```

---

## 6. 遍历元素的实现

### 6.1 entrySet方法

```java
public Set<Map.Entry<K,V>> entrySet() {
    Set<Map.Entry<K,V>> es;
    return (es = entrySet) == null ? (entrySet = new LinkedEntrySet()) : es;
}

final class LinkedEntrySet extends AbstractSet<Map.Entry<K,V>> {
    public final int size() { return size; }
    public final void clear() { LinkedHashMap.this.clear(); }
    
    public final Iterator<Map.Entry<K,V>> iterator() {
        return new LinkedEntryIterator();
    }
    
    // ... 其他方法
}
```

---

### 6.2 LinkedEntryIterator迭代器

```java
final class LinkedEntryIterator extends LinkedHashIterator
    implements Iterator<Map.Entry<K,V>> {
    public final Map.Entry<K,V> next() { return nextNode(); }
}

abstract class LinkedHashIterator {
    LinkedHashMap.Entry<K,V> next;
    LinkedHashMap.Entry<K,V> current;
    int expectedModCount;
    
    LinkedHashIterator() {
        next = head;  // 从头节点开始
        expectedModCount = modCount;
        current = null;
    }
    
    public final boolean hasNext() {
        return next != null;
    }
    
    final LinkedHashMap.Entry<K,V> nextNode() {
        LinkedHashMap.Entry<K,V> e = next;
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        if (e == null)
            throw new NoSuchElementException();
        current = e;
        next = e.after;  // 移动到下一个节点
        return e;
    }
    
    public final void remove() {
        Node<K,V> p = current;
        if (p == null)
            throw new IllegalStateException();
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        current = null;
        K key = p.key;
        removeNode(hash(key), key, null, false, false);
        expectedModCount = modCount;
    }
}
```

**关键点**：
- 遍历双向链表，而不是hash表
- 顺序可预测（插入顺序或访问顺序）
- 性能更好（不需要遍历空桶）

---

## 7. 其他重要方法

### 7.1 containsValue方法

```java
public boolean containsValue(Object value) {
    // 遍历链表，而不是hash表
    for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
        V v = e.value;
        if (v == value || (value != null && value.equals(v)))
            return true;
    }
    return false;
}
```

**优化**：
- HashMap需要遍历整个hash表（包括空桶）
- LinkedHashMap只需遍历链表（只有有效元素）

---

### 7.2 clear方法

```java
public void clear() {
    super.clear();  // 清空hash表
    head = tail = null;  // 清空链表
}
```

---

### 7.3 transferLinks方法（替换节点）

```java
// 用dst替换src在链表中的位置
private void transferLinks(LinkedHashMap.Entry<K,V> src,
                           LinkedHashMap.Entry<K,V> dst) {
    LinkedHashMap.Entry<K,V> b = dst.before = src.before;
    LinkedHashMap.Entry<K,V> a = dst.after = src.after;
    if (b == null)
        head = dst;
    else
        b.after = dst;
    if (a == null)
        tail = dst;
    else
        a.before = dst;
}
```

**用途**：在树化和反树化时替换节点。

---

## 8. 红黑树相关方法

### 8.1 newTreeNode方法

```java
TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
    TreeNode<K,V> p = new TreeNode<K,V>(hash, key, value, next);
    linkNodeLast(p);  // 添加到链表尾部
    return p;
}
```

---

### 8.2 replacementNode方法

```java
Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
    LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
    LinkedHashMap.Entry<K,V> t =
        new LinkedHashMap.Entry<K,V>(q.hash, q.key, q.value, next);
    transferLinks(q, t);  // 替换链表中的位置
    return t;
}
```

---

### 8.3 replacementTreeNode方法

```java
TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
    LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
    TreeNode<K,V> t = new TreeNode<K,V>(q.hash, q.key, q.value, next);
    transferLinks(q, t);  // 替换链表中的位置
    return t;
}
```

---

## 9. 回调方法总结

LinkedHashMap通过重写HashMap的回调方法来维护链表：

| 回调方法 | 调用时机 | 作用 |
|---------|---------|------|
| **afterNodeAccess** | get/put(已存在的key) | 将节点移到链表尾部（accessOrder=true） |
| **afterNodeInsertion** | put(新key) | 可能删除最老的元素（removeEldestEntry=true） |
| **afterNodeRemoval** | remove | 从链表中删除节点 |

---

## 10. 源码中的精妙设计

### 10.1 继承HashMap

**设计**：继承HashMap，复用HashMap的实现。

**优势**：
- 代码复用，减少重复
- 保持HashMap的高性能
- 只需关注顺序维护

---

### 10.2 回调方法

**设计**：通过回调方法维护链表。

**优势**：
- 解耦，HashMap不需要知道LinkedHashMap的存在
- 扩展性好，易于维护

---

### 10.3 双向链表

**设计**：使用双向链表维护顺序。

**优势**：
- 可以双向遍历
- 删除节点只需节点本身
- 移动节点到尾部很方便

---

### 10.4 accessOrder参数

**设计**：通过accessOrder参数控制顺序类型。

**优势**：
- 灵活，可以选择插入顺序或访问顺序
- 一个类实现两种功能

---

### 10.5 removeEldestEntry方法

**设计**：提供removeEldestEntry方法，让子类决定是否删除最老的元素。

**优势**：
- 模板方法模式
- 易于实现LRU缓存
- 扩展性好

---

## 11. 时间复杂度总结

| 操作 | 时间复杂度 | 说明 |
|------|-----------|------|
| **put** | O(1) | 同HashMap，额外维护链表O(1) |
| **get** | O(1) | 同HashMap，可能移动节点O(1) |
| **remove** | O(1) | 同HashMap，额外维护链表O(1) |
| **containsKey** | O(1) | 同HashMap |
| **containsValue** | O(n) | 遍历链表，比HashMap快 |
| **遍历** | O(n) | 遍历链表，比HashMap快 |

---

## 12. 总结

### 12.1 核心实现

1. **继承HashMap**：复用HashMap的实现
2. **双向链表**：维护元素顺序
3. **回调方法**：通过回调维护链表
4. **accessOrder参数**：控制顺序类型
5. **removeEldestEntry方法**：实现LRU缓存

---

### 12.2 设计亮点

1. **代码复用**：继承HashMap，减少重复代码
2. **解耦设计**：通过回调方法解耦
3. **灵活性**：accessOrder参数提供灵活性
4. **扩展性**：removeEldestEntry方法易于扩展

---

### 12.3 下一步学习

在理解了LinkedHashMap的源码实现后，接下来我们将学习：

**LinkedHashMap实现LRU缓存**：深入理解LRU算法的实现

---

**继续阅读**：[04_LinkedHashMap实现LRU缓存.md](./04_LinkedHashMap实现LRU缓存.md)
