# 第三章：HashMap 源码深度剖析

## 3.1 put 的完整决策路径

`put(key, value)` 看起来简单，背后的决策路径有 5 个分支。理解每个分支的触发条件，才能真正掌握 HashMap 的行为。

```
put(key, value)
  ↓
hash(key)  ← 扰动函数，null key → 0
  ↓
table == null？ → 是：resize() 初始化（懒加载）
  ↓
tab[i = hash & (n-1)] == null？ → 是：直接放，O(1)
  ↓
tab[i] 是 TreeNode？ → 是：红黑树插入，O(log n)
  ↓
遍历链表：
  找到相同 key？ → 更新 value，返回旧值
  到达尾部？    → 尾插法新增节点
                  链表长度 >= 8？ → treeifyBin()
  ↓
size++ 后 size > threshold？ → resize() 扩容
```

### 源码逐行解读

```java
final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
    Node<K,V>[] tab; Node<K,V> p; int n, i;

    // 分支1：table 尚未初始化（懒加载策略）
    // 好处：new HashMap() 本身不分配数组，节省空间
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;

    // 分支2：目标桶为空，直接创建节点
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);
    else {
        Node<K,V> e; K k;

        // 分支3：桶的第一个节点就是目标 key
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;

        // 分支4：桶已树化
        else if (p instanceof TreeNode)
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);

        // 分支5：链表遍历
        else {
            for (int binCount = 0; ; ++binCount) {
                if ((e = p.next) == null) {
                    p.next = newNode(hash, key, value, null);  // 尾插法
                    if (binCount >= TREEIFY_THRESHOLD - 1)     // binCount 从0开始，等于7时节点数=8
                        treeifyBin(tab, hash);
                    break;
                }
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                p = e;
            }
        }

        // 找到了已存在的 key，更新 value
        if (e != null) {
            V oldValue = e.value;
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);  // LinkedHashMap 的钩子：维护访问顺序
            return oldValue;     // 返回旧值，size 不增加
        }
    }
    ++modCount;
    if (++size > threshold)  // 先增 size，再判断是否超阈值
        resize();
    afterNodeInsertion(evict);  // LinkedHashMap 的钩子：可能移除最旧节点（LRU）
    return null;  // 新增元素返回 null
}
```

> **关键细节**：`binCount` 从 0 开始，`binCount >= TREEIFY_THRESHOLD - 1`（即 >= 7）时触发树化。此时新节点刚被加入，链表实际长度是 `binCount + 2 = 9`，但源码注释说"for 1st"，实际阈值是链表长度 8 时就触发。

---

## 3.2 get 的查找流程

```java
public V get(Object key) {
    Node<K,V> e;
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}

final Node<K,V> getNode(int hash, Object key) {
    Node<K,V>[] tab; Node<K,V> first, e; int n; K k;

    if ((tab = table) != null && (n = tab.length) > 0 &&
        (first = tab[(n - 1) & hash]) != null) {

        // 先检查第一个节点（命中率最高的快速路径）
        if (first.hash == hash &&
            ((k = first.key) == key || (key != null && key.equals(k))))
            return first;

        if ((e = first.next) != null) {
            // 红黑树查找
            if (first instanceof TreeNode)
                return ((TreeNode<K,V>)first).getTreeNode(hash, key);
            // 链表遍历
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

**注意 key 的相等判断顺序**：`(k = first.key) == key || key.equals(k)`

1. 先用 `==` 比较引用（比 equals 快）
2. 再用 `equals` 比较内容

对于 `String`、`Integer` 等缓存了常量的类型，`==` 经常能直接命中，避免 `equals` 的字符串比较开销。

---

## 3.3 resize 扩容机制

扩容是 HashMap 中最重的操作：分配新数组 + 重新分桶所有元素，时间复杂度 O(n)。

```java
final Node<K,V>[] resize() {
    Node<K,V>[] oldTab = table;
    int oldCap = (oldTab == null) ? 0 : oldTab.length;
    int oldThr = threshold;
    int newCap, newThr = 0;

    if (oldCap > 0) {
        if (oldCap >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;  // 不再扩容
            return oldTab;
        }
        // 核心：容量翻倍
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY)
            newThr = oldThr << 1;  // 阈值也翻倍
    }
    // ... 初始化场景的处理

    // 重新分桶：每个节点只有两种去向
    for (int j = 0; j < oldCap; ++j) {
        Node<K,V> e;
        if ((e = oldTab[j]) != null) {
            oldTab[j] = null;  // help GC
            if (e.next == null)
                newTab[e.hash & (newCap - 1)] = e;  // 只有一个节点，直接放
            else if (e instanceof TreeNode)
                ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);  // 树分裂
            else {
                // 链表分裂为两个子链表（低位/高位）
                Node<K,V> loHead = null, loTail = null;
                Node<K,V> hiHead = null, hiTail = null;
                Node<K,V> next;
                do {
                    next = e.next;
                    if ((e.hash & oldCap) == 0) { // 第 (log2(oldCap)+1) 位为 0
                        if (loTail == null) loHead = e; else loTail.next = e;
                        loTail = e;
                    } else {
                        if (hiTail == null) hiHead = e; else hiTail.next = e;
                        hiTail = e;
                    }
                } while ((e = next) != null);
                if (loTail != null) { loTail.next = null; newTab[j] = loHead; }
                if (hiTail != null) { hiTail.next = null; newTab[j + oldCap] = hiHead; }
            }
        }
    }
    return newTab;
}
```

**`HashMapResizeDemo.java → avoidResize()`** 演示了正确计算初始容量的方式，避免扩容：

```java
// 预期存储 1000 个元素
// 错误：new HashMap(1000)，threshold = 1024 × 0.75 = 768，768 < 1000，仍会扩容
// 正确：
int initialCapacity = (int) (expectedSize / 0.75f + 1);  // = 1335，向上取到 2048
Map<Integer, String> map = new HashMap<>(initialCapacity);
// threshold = 2048 × 0.75 = 1536 > 1000，全程不扩容
```

---

## 3.4 JDK 8 新增的函数式方法

**`HashMapBasicDemo.java → commonMethods()`** 演示了以下方法，这里解释每个方法的底层实现逻辑：

### `computeIfAbsent`：线程不安全的"懒初始化"模式

```java
map.computeIfAbsent("E", key -> 5);
// 等价于：
if (!map.containsKey("E")) {
    map.put("E", computeFunction.apply("E"));
}
```

典型用法：按 key 分组时，避免手动判断 null：
```java
// 旧写法（啰嗦且需要两次 get）
List<String> list = map.get(key);
if (list == null) { list = new ArrayList<>(); map.put(key, list); }
list.add(value);

// 新写法
map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
```

### `merge`：统计计数的最优写法

```java
map.merge("A", 1, Integer::sum);
// 等价于：
// key 不存在：put("A", 1)
// key 存在：put("A", oldValue + 1)
```

比 `getOrDefault("A", 0) + 1` 更简洁，且只做一次 hash 查找而不是两次。

---

## 3.5 关键钩子方法（面向 LinkedHashMap 的扩展点）

HashMap 中有三个空方法，专门留给 `LinkedHashMap` 覆盖：

```java
void afterNodeAccess(Node<K,V> p) { }     // get/put 访问节点后调用 → LinkedHashMap 维护访问顺序
void afterNodeInsertion(boolean evict) { } // 插入新节点后调用 → LinkedHashMap 可驱逐最旧节点（LRU）
void afterNodeRemoval(Node<K,V> p) { }    // 删除节点后调用 → LinkedHashMap 维护双向链表
```

这是**模板方法模式**的体现：HashMap 定义算法骨架，LinkedHashMap 通过覆盖钩子注入差异行为，而不需要修改 HashMap 的核心逻辑。

---

## 3.6 本章总结

- **put 5分支**：空桶直插 → 同 key 更新 → 树插入 → 链表尾插 → 链表转树，每个分支的触发条件要熟记
- **懒加载**：`new HashMap()` 不分配 table，第一次 put 时才 `resize()` 初始化
- **get 快速路径**：先检查第一个节点（桶头），再 `==` 比较引用，最后 `equals` 比较内容
- **resize 分桶**：`hash & oldCap` 的 0/1 将链表一分为二，保持原有顺序（尾插法的价值）
- **钩子方法**：`afterNodeAccess` 等是 LinkedHashMap 的扩展点，HashMap 本身是空实现

> **本章对应演示代码**：`HashMapBasicDemo.java`（put/get/computeIfAbsent/merge）、`HashMapResizeDemo.java`（扩容与初始容量设置）

**继续阅读**：[04_HashMap线程安全问题与解决方案.md](./04_HashMap线程安全问题与解决方案.md)
