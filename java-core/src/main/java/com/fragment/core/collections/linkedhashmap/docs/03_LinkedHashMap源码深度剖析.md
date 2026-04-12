# 第三章：LinkedHashMap 源码深度剖析

## 3.1 put 的完整流程：HashMap + 链表维护

LinkedHashMap 的 `put` 继承自 HashMap，但有两处差异：

1. **创建节点时**：调用的是 `newNode`，LinkedHashMap 覆盖了这个方法，返回 `Entry`（带 `before`/`after` 指针）而非 `Node`
2. **插入完成后**：通过 `afterNodeInsertion` 钩子检查是否需要驱逐

```java
// LinkedHashMap 覆盖了 HashMap 的 newNode
Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
    LinkedHashMap.Entry<K,V> p = new LinkedHashMap.Entry<>(hash, key, value, e);
    linkNodeLast(p);  // 将新节点追加到全局链表尾部
    return p;
}

// 追加到链表尾部
private void linkNodeLast(LinkedHashMap.Entry<K,V> p) {
    LinkedHashMap.Entry<K,V> last = tail;
    tail = p;
    if (last == null)
        head = p;  // 链表原来为空
    else {
        p.before = last;
        last.after = p;
    }
}
```

**整体 put 流程**：
```
put(key, value)
  ↓
hash(key)
  ↓
HashMap.putVal()  ← 完整的桶定位、链表/树插入逻辑
  ├── newNode()  ← LinkedHashMap 覆盖：创建 Entry + linkNodeLast（追加到全局链表尾部）
  ├── afterNodeAccess()  ← 若 accessOrder=true 且是更新已有 key，移到尾部
  └── afterNodeInsertion()  ← 检查 removeEldestEntry，决定是否驱逐 head
```

---

## 3.2 get 的 LRU 核心逻辑

```java
public V get(Object key) {
    Node<K,V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    if (accessOrder)
        afterNodeAccess(e);  // LRU 关键：访问后移到链表尾部
    return e.value;
}
```

**accessOrder=false（插入顺序模式）**：`get` 和 HashMap 完全一样，只读，不修改链表。

**accessOrder=true（访问顺序模式）**：每次 `get` 成功后，调用 `afterNodeAccess(e)` 将 `e` 移到链表尾部。链表头部始终是最久未访问的元素。

```
初始状态（accessOrder=true）：
head → [A] ↔ [B] ↔ [C] → tail

get("A") 后（A 移到尾部）：
head → [B] ↔ [C] ↔ [A] → tail

get("B") 后（B 移到尾部）：
head → [C] ↔ [A] ↔ [B] → tail
```

这正是 `LinkedHashMapBasicDemo.java → accessOrderMode()` 演示的效果。

---

## 3.3 removeEldestEntry：LRU 驱逐的触发机制

每次 `put` 后都会调用 `afterNodeInsertion`，其中调用 `removeEldestEntry(head)`：

```java
void afterNodeInsertion(boolean evict) {
    LinkedHashMap.Entry<K,V> first;
    if (evict && (first = head) != null && removeEldestEntry(first)) {
        K key = first.key;
        removeNode(hash(key), key, null, false, true);
    }
}
```

`removeEldestEntry` 接收的参数是 `head`（当前链表头部，即最老/最久未访问的节点）。默认实现返回 `false`（不驱逐）。

**LRU 缓存的标准实现**：

```java
// DatabaseQueryCache.java 的实现模式
new LinkedHashMap<String, QueryResult>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, QueryResult> eldest) {
        boolean shouldEvict = size() > maxSize;
        if (shouldEvict) {
            System.out.println("[Cache] 淘汰: " + eldest.getKey());
            evictionCount++;  // 统计驱逐次数
        }
        return shouldEvict;
    }
};
```

**关键点**：`removeEldestEntry` 在 `put` 完成后调用，此时 `size()` 已经包含了新插入的元素。所以 `size() > maxSize` 的判断是在新元素加入后，驱逐最老元素，使容量维持在 `maxSize`。

---

## 3.4 迭代器：按链表顺序遍历

LinkedHashMap 覆盖了 HashMap 的 `entrySet()`、`keySet()`、`values()`，返回的迭代器按双向链表顺序遍历，而不是按桶数组顺序：

```java
// LinkedHashMap 的迭代器核心逻辑
abstract class LinkedHashIterator {
    LinkedHashMap.Entry<K,V> next;
    LinkedHashMap.Entry<K,V> current;
    int expectedModCount;

    LinkedHashIterator() {
        next = head;  // 从链表头部开始
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
        next = e.after;  // 沿 after 指针前进
        return e;
    }
}
```

遍历时间复杂度是 O(n)（沿链表步进），而 HashMap 迭代器需要跳过空桶（O(capacity + size)）。当 HashMap 容量远大于实际元素数时，LinkedHashMap 的遍历甚至比 HashMap 更快。

---

## 3.5 configurationManager 中的插入顺序遍历

`ConfigurationManager.java` 展示了插入顺序的实用价值：

```java
// ConfigurationManager.java
private final Map<String, String> config = new LinkedHashMap<>();

// 按添加顺序写入配置文件
void saveToFile(String filename) throws IOException {
    try (FileWriter writer = new FileWriter(filename)) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
        }
    }
}
```

配置项的添加顺序：`app.name` → `app.version` → `server.host` → `server.port` → ...

保存到文件后，文件中的配置项顺序与添加顺序完全一致。如果用 HashMap，配置项的顺序会是随机的，降低了配置文件的可读性。

---

## 3.6 accessOrder 下的 put 更新行为

一个容易混淆的细节：在 `accessOrder=true` 模式下，`put` 更新已有 key 时，是否也移到尾部？

```java
Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
map.put("A", 1); map.put("B", 2); map.put("C", 3);
// 链表：head → A ↔ B ↔ C → tail

map.put("A", 99);  // 更新 A 的值
// 链表：head → B ↔ C ↔ A → tail  ← A 移到了尾部！
```

原因：`putVal` 找到已有 key 后，调用 `afterNodeAccess(e)` ——在 `accessOrder=true` 时，这次 "put 更新" 被视为访问，A 移到尾部。

这意味着**在 LRU 场景中，put 更新已有 key 也会刷新该 key 的"最近使用时间"**，这与 LRU 的语义完全一致。

---

## 3.7 本章总结

- **newNode 覆盖**：LinkedHashMap 的新节点创建时就通过 `linkNodeLast` 追加到链表尾部
- **get 的 LRU 机制**：`accessOrder=true` 时，`get` 后调用 `afterNodeAccess` 将节点移到尾部
- **removeEldestEntry**：每次 `put` 后被调用，通过返回 `true` 触发 `head` 节点的驱逐
- **迭代器沿 `after` 指针**：按链表顺序（O(n)）遍历，可能比 HashMap 迭代器（O(capacity+n)）更快
- **put 更新也算访问**：`accessOrder=true` 下，更新已有 key 的 value 也会刷新其"最近使用"位置

> **本章对应演示代码**：`LinkedHashMapLRUDemo.java`（removeEldestEntry 的触发流程）、`ConfigurationManager.java`（插入顺序遍历用于配置文件存储）

**继续阅读**：[04_LinkedHashMap线程安全与性能分析.md](./04_LinkedHashMap线程安全与性能分析.md)
