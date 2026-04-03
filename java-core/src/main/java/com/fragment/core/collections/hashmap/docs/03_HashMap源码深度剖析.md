# HashMap源码深度剖析

## 1. put方法完整流程

### 1.1 put方法入口

```java
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

// 参数说明：
// hash: key的hash值
// key: 键
// value: 值
// onlyIfAbsent: 如果为true，不覆盖已存在的值
// evict: 如果为false，表示是在创建模式（用于LinkedHashMap）
```

---

### 1.2 hash方法

```java
static final int hash(Object key) {
    int h;
    // 1. 如果key为null，hash值为0（存储在table[0]）
    // 2. 否则，计算key的hashCode，并与高16位异或（扰动函数）
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

**为什么允许null key？**
- HashMap允许一个null key，存储在table[0]位置
- HashTable不允许null key，会抛出NullPointerException

---

### 1.3 putVal方法详解

```java
final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
    Node<K,V>[] tab;
    Node<K,V> p;
    int n, i;
    
    // 步骤1：如果table为空或长度为0，进行初始化（懒加载）
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;
    
    // 步骤2：计算索引位置，如果该位置为空，直接创建新节点
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);
    else {
        // 步骤3：该位置已有元素，需要处理冲突
        Node<K,V> e;
        K k;
        
        // 情况1：key已存在（hash相同且key相等）
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;
        
        // 情况2：该位置是红黑树节点
        else if (p instanceof TreeNode)
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
        
        // 情况3：该位置是链表
        else {
            for (int binCount = 0; ; ++binCount) {
                // 遍历到链表尾部，插入新节点（尾插法）
                if ((e = p.next) == null) {
                    p.next = newNode(hash, key, value, null);
                    
                    // 链表长度达到阈值，转换为红黑树
                    if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                        treeifyBin(tab, hash);
                    break;
                }
                
                // 找到相同的key，退出循环
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                
                p = e;
            }
        }
        
        // 步骤4：如果key已存在，更新value
        if (e != null) {
            V oldValue = e.value;
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);  // LinkedHashMap的回调
            return oldValue;
        }
    }
    
    // 步骤5：修改次数+1（用于fail-fast）
    ++modCount;
    
    // 步骤6：如果size超过阈值，进行扩容
    if (++size > threshold)
        resize();
    
    afterNodeInsertion(evict);  // LinkedHashMap的回调
    return null;
}
```

---

### 1.4 put流程图

```
put(key, value)
  ↓
计算hash值
  ↓
table是否为空？
  ├─ 是 → resize()初始化
  └─ 否 → 继续
  ↓
计算索引：i = (n-1) & hash
  ↓
table[i]是否为空？
  ├─ 是 → 直接插入新节点 → 结束
  └─ 否 → 处理冲突
  ↓
key是否相同？
  ├─ 是 → 更新value → 结束
  └─ 否 → 继续
  ↓
是否为红黑树？
  ├─ 是 → 调用putTreeVal() → 结束
  └─ 否 → 遍历链表
  ↓
遍历链表
  ├─ 找到相同key → 更新value → 结束
  └─ 到达链表尾部 → 插入新节点（尾插法）
  ↓
链表长度 >= 8？
  ├─ 是 → treeifyBin()转红黑树
  └─ 否 → 继续
  ↓
size > threshold？
  ├─ 是 → resize()扩容
  └─ 否 → 结束
```

---

### 1.5 关键点分析

#### 关键点1：懒加载

```java
// table在第一次put时才初始化，而不是在构造函数中
if ((tab = table) == null || (n = tab.length) == 0)
    n = (tab = resize()).length;
```

**优势**：
- 节省内存：如果创建了HashMap但没有使用，不会占用内存
- 延迟初始化：只有在需要时才分配内存

---

#### 关键点2：尾插法

```java
// JDK 1.8使用尾插法
for (int binCount = 0; ; ++binCount) {
    if ((e = p.next) == null) {
        p.next = newNode(hash, key, value, null);  // 插入到链表尾部
        break;
    }
    p = e;
}
```

**对比JDK 1.7的头插法**：

```java
// JDK 1.7使用头插法
void addEntry(int hash, K key, V value, int bucketIndex) {
    Entry<K,V> e = table[bucketIndex];
    table[bucketIndex] = new Entry<>(hash, key, value, e);  // 插入到链表头部
}
```

**尾插法的优势**：
- 保持插入顺序
- 避免扩容时的死循环问题

---

#### 关键点3：链表转红黑树

```java
if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
    treeifyBin(tab, hash);
```

**转换条件**：
1. 链表长度 >= 8
2. 数组容量 >= 64（在treeifyBin中判断）

```java
final void treeifyBin(Node<K,V>[] tab, int hash) {
    int n, index;
    Node<K,V> e;
    
    // 如果数组容量 < 64，优先扩容而不是转红黑树
    if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
        resize();
    else if ((e = tab[index = (n - 1) & hash]) != null) {
        // 转换为红黑树
        TreeNode<K,V> hd = null, tl = null;
        do {
            TreeNode<K,V> p = replacementTreeNode(e, null);
            if (tl == null)
                hd = p;
            else {
                p.prev = tl;
                tl.next = p;
            }
            tl = p;
        } while ((e = e.next) != null);
        
        if ((tab[index] = hd) != null)
            hd.treeify(tab);
    }
}
```

---

## 2. get方法完整流程

### 2.1 get方法入口

```java
public V get(Object key) {
    Node<K,V> e;
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}
```

---

### 2.2 getNode方法详解

```java
final Node<K,V> getNode(int hash, Object key) {
    Node<K,V>[] tab;
    Node<K,V> first, e;
    int n;
    K k;
    
    // 步骤1：table不为空，且对应位置有元素
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (first = tab[(n - 1) & hash]) != null) {
        
        // 步骤2：检查第一个节点（最常见的情况）
        if (first.hash == hash &&
            ((k = first.key) == key || (key != null && key.equals(k))))
            return first;
        
        // 步骤3：第一个节点不匹配，继续查找
        if ((e = first.next) != null) {
            // 情况1：红黑树查找
            if (first instanceof TreeNode)
                return ((TreeNode<K,V>)first).getTreeNode(hash, key);
            
            // 情况2：链表查找
            do {
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    return e;
            } while ((e = e.next) != null);
        }
    }
    
    return null;
}
```

---

### 2.3 get流程图

```
get(key)
  ↓
计算hash值
  ↓
table是否为空？
  ├─ 是 → 返回null
  └─ 否 → 继续
  ↓
计算索引：i = (n-1) & hash
  ↓
table[i]是否为空？
  ├─ 是 → 返回null
  └─ 否 → 继续
  ↓
第一个节点是否匹配？
  ├─ 是 → 返回value
  └─ 否 → 继续
  ↓
是否有next节点？
  ├─ 否 → 返回null
  └─ 是 → 继续
  ↓
是否为红黑树？
  ├─ 是 → 调用getTreeNode()
  └─ 否 → 遍历链表
  ↓
找到匹配的节点？
  ├─ 是 → 返回value
  └─ 否 → 返回null
```

---

### 2.4 关键点分析

#### 关键点1：快速路径优化

```java
// 先检查第一个节点，这是最常见的情况
if (first.hash == hash &&
    ((k = first.key) == key || (key != null && key.equals(k))))
    return first;
```

**原因**：
- 如果hash函数设计良好，大部分桶只有一个元素
- 先检查第一个节点可以快速返回，避免不必要的遍历

---

#### 关键点2：红黑树查找

```java
if (first instanceof TreeNode)
    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
```

**红黑树查找的时间复杂度**：O(log n)

```java
final TreeNode<K,V> getTreeNode(int h, Object k) {
    return ((parent != null) ? root() : this).find(h, k, null);
}

final TreeNode<K,V> find(int h, Object k, Class<?> kc) {
    TreeNode<K,V> p = this;
    do {
        int ph, dir;
        K pk;
        TreeNode<K,V> pl = p.left, pr = p.right, q;
        
        // 根据hash值比较，决定往左还是往右
        if ((ph = p.hash) > h)
            p = pl;
        else if (ph < h)
            p = pr;
        else if ((pk = p.key) == k || (k != null && k.equals(pk)))
            return p;
        // ... 更复杂的比较逻辑
    } while (p != null);
    
    return null;
}
```

---

## 3. resize扩容机制

### 3.1 何时触发扩容？

```java
// 条件1：第一次put时，table为空
if ((tab = table) == null || (n = tab.length) == 0)
    n = (tab = resize()).length;

// 条件2：元素数量超过阈值
if (++size > threshold)
    resize();
```

---

### 3.2 resize方法详解

```java
final Node<K,V>[] resize() {
    Node<K,V>[] oldTab = table;
    int oldCap = (oldTab == null) ? 0 : oldTab.length;
    int oldThr = threshold;
    int newCap, newThr = 0;
    
    // 步骤1：计算新容量和新阈值
    if (oldCap > 0) {
        // 情况1：已达到最大容量，不再扩容
        if (oldCap >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return oldTab;
        }
        // 情况2：容量翻倍，阈值翻倍
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                 oldCap >= DEFAULT_INITIAL_CAPACITY)
            newThr = oldThr << 1; // double threshold
    }
    else if (oldThr > 0) // initial capacity was placed in threshold
        newCap = oldThr;
    else {
        // 情况3：使用默认值初始化
        newCap = DEFAULT_INITIAL_CAPACITY;  // 16
        newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);  // 12
    }
    
    // 步骤2：计算新阈值（如果还没计算）
    if (newThr == 0) {
        float ft = (float)newCap * loadFactor;
        newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                  (int)ft : Integer.MAX_VALUE);
    }
    
    threshold = newThr;
    
    // 步骤3：创建新数组
    @SuppressWarnings({"rawtypes","unchecked"})
    Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
    table = newTab;
    
    // 步骤4：迁移旧数据
    if (oldTab != null) {
        for (int j = 0; j < oldCap; ++j) {
            Node<K,V> e;
            if ((e = oldTab[j]) != null) {
                oldTab[j] = null;  // help GC
                
                // 情况1：只有一个节点，直接迁移
                if (e.next == null)
                    newTab[e.hash & (newCap - 1)] = e;
                
                // 情况2：红黑树，调用split方法
                else if (e instanceof TreeNode)
                    ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                
                // 情况3：链表，拆分为两条链表
                else {
                    Node<K,V> loHead = null, loTail = null;  // 低位链表
                    Node<K,V> hiHead = null, hiTail = null;  // 高位链表
                    Node<K,V> next;
                    
                    do {
                        next = e.next;
                        // 判断元素在新数组中的位置
                        if ((e.hash & oldCap) == 0) {
                            // 保持原位置
                            if (loTail == null)
                                loHead = e;
                            else
                                loTail.next = e;
                            loTail = e;
                        }
                        else {
                            // 原位置 + oldCap
                            if (hiTail == null)
                                hiHead = e;
                            else
                                hiTail.next = e;
                            hiTail = e;
                        }
                    } while ((e = next) != null);
                    
                    // 放入新数组
                    if (loTail != null) {
                        loTail.next = null;
                        newTab[j] = loHead;
                    }
                    if (hiTail != null) {
                        hiTail.next = null;
                        newTab[j + oldCap] = hiHead;
                    }
                }
            }
        }
    }
    
    return newTab;
}
```

---

### 3.3 扩容优化：元素位置计算

**JDK 1.8的核心优化**：通过位运算快速确定元素在新数组中的位置。

```java
// 判断元素在新数组中的位置
if ((e.hash & oldCap) == 0) {
    // 保持原位置
    newTab[j] = loHead;
} else {
    // 原位置 + oldCap
    newTab[j + oldCap] = hiHead;
}
```

**原理**：

```
假设oldCap = 16 = 0b10000，newCap = 32 = 0b100000

元素hash值 = 0b10101 = 21

原索引计算：
  0b10101 & (0b10000 - 1) = 0b10101 & 0b01111 = 0b00101 = 5

新索引计算：
  0b10101 & (0b100000 - 1) = 0b10101 & 0b11111 = 0b10101 = 21

观察：
  新索引 = 原索引 + oldCap = 5 + 16 = 21

判断方法：
  0b10101 & 0b10000 = 0b10000 ≠ 0
  说明新索引 = 原索引 + oldCap

---

元素hash值 = 0b00101 = 5

原索引计算：
  0b00101 & 0b01111 = 0b00101 = 5

新索引计算：
  0b00101 & 0b11111 = 0b00101 = 5

观察：
  新索引 = 原索引 = 5

判断方法：
  0b00101 & 0b10000 = 0b00000 = 0
  说明新索引 = 原索引
```

**优势**：
- 无需重新计算hash值
- 通过一次位运算即可确定新位置
- 时间复杂度从O(n)优化到O(1)

---

### 3.4 扩容流程图

```
resize()
  ↓
计算新容量和新阈值
  ├─ oldCap > 0 → 容量翻倍
  ├─ oldThr > 0 → newCap = oldThr
  └─ 否则 → 使用默认值（16, 12）
  ↓
创建新数组
  ↓
迁移旧数据（遍历旧数组）
  ↓
当前位置是否有元素？
  ├─ 否 → 继续下一个位置
  └─ 是 → 继续
  ↓
只有一个节点？
  ├─ 是 → 直接迁移到新位置
  └─ 否 → 继续
  ↓
是否为红黑树？
  ├─ 是 → 调用split()拆分
  └─ 否 → 拆分链表
  ↓
遍历链表，根据(hash & oldCap)分为两组
  ├─ == 0 → 低位链表（保持原位置）
  └─ != 0 → 高位链表（原位置 + oldCap）
  ↓
放入新数组
  ├─ 低位链表 → newTab[j]
  └─ 高位链表 → newTab[j + oldCap]
```

---

## 4. remove方法完整流程

### 4.1 remove方法入口

```java
public V remove(Object key) {
    Node<K,V> e;
    return (e = removeNode(hash(key), key, null, false, true)) == null ?
        null : e.value;
}
```

---

### 4.2 removeNode方法详解

```java
final Node<K,V> removeNode(int hash, Object key, Object value,
                           boolean matchValue, boolean movable) {
    Node<K,V>[] tab;
    Node<K,V> p;
    int n, index;
    
    // 步骤1：查找要删除的节点
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (p = tab[index = (n - 1) & hash]) != null) {
        
        Node<K,V> node = null, e;
        K k;
        V v;
        
        // 情况1：第一个节点就是要删除的节点
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            node = p;
        
        else if ((e = p.next) != null) {
            // 情况2：红黑树查找
            if (p instanceof TreeNode)
                node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
            
            // 情况3：链表查找
            else {
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key ||
                         (key != null && key.equals(k)))) {
                        node = e;
                        break;
                    }
                    p = e;
                } while ((e = e.next) != null);
            }
        }
        
        // 步骤2：删除节点
        if (node != null && (!matchValue || (v = node.value) == value ||
                             (value != null && value.equals(v)))) {
            // 情况1：红黑树删除
            if (node instanceof TreeNode)
                ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
            
            // 情况2：删除第一个节点
            else if (node == p)
                tab[index] = node.next;
            
            // 情况3：删除链表中的节点
            else
                p.next = node.next;
            
            ++modCount;
            --size;
            afterNodeRemoval(node);  // LinkedHashMap的回调
            return node;
        }
    }
    
    return null;
}
```

---

### 4.3 红黑树节点删除

```java
final void removeTreeNode(HashMap<K,V> map, Node<K,V>[] tab, boolean movable) {
    int n;
    if (tab == null || (n = tab.length) == 0)
        return;
    
    int index = (n - 1) & hash;
    TreeNode<K,V> first = (TreeNode<K,V>)tab[index], root = first, rl;
    TreeNode<K,V> succ = (TreeNode<K,V>)next, pred = prev;
    
    // 步骤1：从链表中删除
    if (pred == null)
        tab[index] = first = succ;
    else
        pred.next = succ;
    
    if (succ != null)
        succ.prev = pred;
    
    if (first == null)
        return;
    
    if (root.parent != null)
        root = root.root();
    
    // 步骤2：判断是否需要退化为链表
    if (root == null || root.right == null ||
        (rl = root.left) == null || rl.left == null) {
        tab[index] = first.untreeify(map);  // 退化为链表
        return;
    }
    
    // 步骤3：红黑树删除操作
    // ... 复杂的红黑树删除和平衡操作
}
```

---

## 5. 其他重要方法

### 5.1 containsKey方法

```java
public boolean containsKey(Object key) {
    return getNode(hash(key), key) != null;
}
```

**实现**：直接调用getNode方法，时间复杂度O(1)。

---

### 5.2 containsValue方法

```java
public boolean containsValue(Object value) {
    Node<K,V>[] tab;
    V v;
    if ((tab = table) != null && size > 0) {
        // 遍历整个table数组
        for (int i = 0; i < tab.length; ++i) {
            // 遍历每个桶中的链表/红黑树
            for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                if ((v = e.value) == value ||
                    (value != null && value.equals(v)))
                    return true;
            }
        }
    }
    return false;
}
```

**实现**：需要遍历所有元素，时间复杂度O(n)。

**对比**：
- containsKey：O(1)
- containsValue：O(n)

**原因**：HashMap是根据key建立索引的，value没有索引。

---

### 5.3 clear方法

```java
public void clear() {
    Node<K,V>[] tab;
    modCount++;
    if ((tab = table) != null && size > 0) {
        size = 0;
        // 将所有桶置为null
        for (int i = 0; i < tab.length; ++i)
            tab[i] = null;
    }
}
```

**实现**：将所有桶置为null，让GC回收节点。

---

### 5.4 size方法

```java
public int size() {
    return size;
}
```

**实现**：直接返回size字段，时间复杂度O(1)。

---

## 6. 迭代器实现

### 6.1 迭代器类型

HashMap提供了三种迭代器：

1. **KeyIterator**：遍历所有key
2. **ValueIterator**：遍历所有value
3. **EntryIterator**：遍历所有Entry

---

### 6.2 HashIterator基类

```java
abstract class HashIterator {
    Node<K,V> next;        // 下一个节点
    Node<K,V> current;     // 当前节点
    int expectedModCount;  // 期望的修改次数（用于fail-fast）
    int index;             // 当前桶的索引
    
    HashIterator() {
        expectedModCount = modCount;
        Node<K,V>[] t = table;
        current = next = null;
        index = 0;
        
        // 找到第一个非空桶
        if (t != null && size > 0) {
            do {} while (index < t.length && (next = t[index++]) == null);
        }
    }
    
    public final boolean hasNext() {
        return next != null;
    }
    
    final Node<K,V> nextNode() {
        Node<K,V>[] t;
        Node<K,V> e = next;
        
        // fail-fast检查
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        
        if (e == null)
            throw new NoSuchElementException();
        
        // 移动到下一个节点
        if ((next = (current = e).next) == null && (t = table) != null) {
            // 当前桶遍历完，找下一个非空桶
            do {} while (index < t.length && (next = t[index++]) == null);
        }
        
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

---

### 6.3 KeyIterator实现

```java
final class KeyIterator extends HashIterator
    implements Iterator<K> {
    public final K next() { return nextNode().key; }
}
```

---

### 6.4 ValueIterator实现

```java
final class ValueIterator extends HashIterator
    implements Iterator<V> {
    public final V next() { return nextNode().value; }
}
```

---

### 6.5 EntryIterator实现

```java
final class EntryIterator extends HashIterator
    implements Iterator<Map.Entry<K,V>> {
    public final Map.Entry<K,V> next() { return nextNode(); }
}
```

---

### 6.6 fail-fast机制

**什么是fail-fast？**

在迭代过程中，如果HashMap被修改（除了通过迭代器的remove方法），会立即抛出ConcurrentModificationException。

```java
Map<String, String> map = new HashMap<>();
map.put("key1", "value1");
map.put("key2", "value2");

// 错误示例：迭代时修改
for (String key : map.keySet()) {
    map.remove(key);  // 抛出ConcurrentModificationException
}

// 正确示例：使用迭代器的remove方法
Iterator<String> iterator = map.keySet().iterator();
while (iterator.hasNext()) {
    String key = iterator.next();
    iterator.remove();  // 正确
}
```

**实现原理**：

```java
// 每次修改HashMap时，modCount++
++modCount;

// 迭代器创建时，记录当前的modCount
expectedModCount = modCount;

// 每次迭代时，检查modCount是否变化
if (modCount != expectedModCount)
    throw new ConcurrentModificationException();
```

---

## 7. 时间复杂度总结

| 操作 | 平均时间复杂度 | 最坏时间复杂度 | 说明 |
|------|--------------|--------------|------|
| **put** | O(1) | O(log n) | 无冲突O(1)，有冲突O(log n) |
| **get** | O(1) | O(log n) | 无冲突O(1)，有冲突O(log n) |
| **remove** | O(1) | O(log n) | 无冲突O(1)，有冲突O(log n) |
| **containsKey** | O(1) | O(log n) | 同get |
| **containsValue** | O(n) | O(n) | 需要遍历所有元素 |
| **size** | O(1) | O(1) | 直接返回字段 |
| **clear** | O(n) | O(n) | 需要遍历所有桶 |

**注意**：
- JDK 1.7的最坏时间复杂度是O(n)（链表）
- JDK 1.8优化为O(log n)（红黑树）

---

## 8. 源码中的精妙设计

### 8.1 位运算优化

```java
// 1. 取模运算优化
index = hash % capacity;  // 慢
index = hash & (capacity - 1);  // 快

// 2. 扩容位置计算
if ((e.hash & oldCap) == 0) {
    // 保持原位置
} else {
    // 原位置 + oldCap
}

// 3. 容量调整为2的幂次方
static final int tableSizeFor(int cap) {
    int n = cap - 1;
    n |= n >>> 1;
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}
```

---

### 8.2 懒加载

```java
// table在第一次put时才初始化
if ((tab = table) == null || (n = tab.length) == 0)
    n = (tab = resize()).length;
```

**优势**：节省内存，延迟初始化。

---

### 8.3 链表拆分优化

```java
// 扩容时，将链表拆分为两条链表
Node<K,V> loHead = null, loTail = null;  // 低位链表
Node<K,V> hiHead = null, hiTail = null;  // 高位链表

do {
    next = e.next;
    if ((e.hash & oldCap) == 0) {
        if (loTail == null)
            loHead = e;
        else
            loTail.next = e;
        loTail = e;
    } else {
        if (hiTail == null)
            hiHead = e;
        else
            hiTail.next = e;
        hiTail = e;
    }
} while ((e = next) != null);
```

**优势**：
- 一次遍历完成拆分
- 保持原有顺序
- 避免重新计算hash

---

### 8.4 红黑树优化

```java
// 链表长度 > 8 且数组容量 >= 64 时，转红黑树
if (binCount >= TREEIFY_THRESHOLD - 1)
    treeifyBin(tab, hash);

// 红黑树节点数 <= 6 时，退化为链表
if (root == null || root.right == null ||
    (rl = root.left) == null || rl.left == null) {
    tab[index] = first.untreeify(map);
}
```

**优势**：
- 动态优化，根据实际情况选择数据结构
- 平衡时间和空间

---

## 9. 下一步学习

在理解了HashMap的源码实现后，接下来我们将学习：

1. **HashMap线程安全问题**：理解并发问题和解决方案
2. **HashMap最佳实践**：掌握正确的使用方式和性能优化技巧

---

**继续阅读**：[04_HashMap线程安全问题与解决方案.md](./04_HashMap线程安全问题与解决方案.md)
