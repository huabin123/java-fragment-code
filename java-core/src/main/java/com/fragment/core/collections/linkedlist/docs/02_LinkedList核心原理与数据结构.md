# 第二章：LinkedList 核心原理与数据结构

## 2.1 双向链表节点：Node 内部类

```java
private static class Node<E> {
    E item;         // 数据
    Node<E> next;   // 后继节点指针
    Node<E> prev;   // 前驱节点指针

    Node(Node<E> prev, E element, Node<E> next) {
        this.item = element;
        this.next = next;
        this.prev = prev;
    }
}
```

每个节点在堆上是独立的对象，通过指针连接成链。与数组不同，节点之间**没有内存连续性的保证**——这是 LinkedList 在随机访问上天然处于劣势的根本原因（CPU 缓存无法预读下一个节点）。

### 内存开销估算

在 64 位 JVM（开启压缩指针）下，每个 `Node<E>` 对象：
- 对象头：16 字节
- `item` 引用：4 字节
- `next` 引用：4 字节
- `prev` 引用：4 字节
- 对齐填充：4 字节
- **合计：约 32 字节/节点（不含 item 对象本身）**

存储 100 万个 Integer：
- ArrayList：约 4 MB（引用数组）+ Integer 对象
- LinkedList：约 32 MB（节点对象）+ Integer 对象，**额外开销约 8 倍**

---

## 2.2 LinkedList 的核心字段

```java
public class LinkedList<E> extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, Serializable {

    transient int size = 0;
    transient Node<E> first;  // 头节点（链表第一个元素）
    transient Node<E> last;   // 尾节点（链表最后一个元素）
}
```

**为什么同时持有 `first` 和 `last`？**

有 `first`：`addFirst`/`removeFirst`/`peekFirst` 都是 O(1)。  
有 `last`：`addLast`/`removeLast`/`peekLast` 也是 O(1)。  

如果只有 `first`，找到 `last` 需要遍历全链表 O(n)，实现 Deque 接口的尾部操作就无法高效完成。

**`first` 和 `last` 的不变式（Invariant）**：
- 链表为空时：`first == null && last == null`
- 链表有一个元素：`first == last && first.prev == null && first.next == null`
- 链表有多个元素：`first.prev == null`，`last.next == null`

---

## 2.3 核心操作的指针变化

理解这几个操作的指针变化，是理解所有链表算法的基础。

### addFirst(E e)：在头部插入

```java
private void linkFirst(E e) {
    final Node<E> f = first;
    final Node<E> newNode = new Node<>(null, e, f);  // new.prev=null, new.next=旧first
    first = newNode;
    if (f == null)
        last = newNode;    // 链表原来为空，last 也指向新节点
    else
        f.prev = newNode;  // 旧 first 的 prev 指向新节点
    size++;
    modCount++;
}
```

```
插入前：null ← [B] ↔ [C] → null
                ↑first     ↑last

插入 A 后：null ← [A] ↔ [B] ↔ [C] → null
                  ↑first          ↑last
```

### removeLast()：从尾部删除

```java
private E unlinkLast(Node<E> l) {
    final E element = l.item;
    final Node<E> prev = l.prev;
    l.item = null;          // help GC
    l.prev = null;          // help GC
    last = prev;
    if (prev == null)
        first = null;       // 链表变空
    else
        prev.next = null;   // 原倒数第二个节点成为新 last，断开对被删节点的引用
    size--;
    modCount++;
    return element;
}
```

**`l.item = null` 的作用**：断开节点对数据对象的引用，让 GC 能回收 `element`（即使外部还持有被删节点的引用，也不会阻止 item 被回收）。

### unlink(Node<E> x)：从中间删除

```java
E unlink(Node<E> x) {
    final E element = x.item;
    final Node<E> next = x.next;
    final Node<E> prev = x.prev;

    if (prev == null) {
        first = next;   // 删除的是头节点
    } else {
        prev.next = next;
        x.prev = null;  // help GC
    }

    if (next == null) {
        last = prev;    // 删除的是尾节点
    } else {
        next.prev = prev;
        x.next = null;  // help GC
    }

    x.item = null;      // help GC
    size--;
    modCount++;
    return element;
}
```

**关键点**：获取到节点引用（Iterator.remove() 就持有当前节点）后，删除本身是 O(1)；但通过索引 `remove(int index)` 要先 `node(index)` 找到节点，这一步是 O(n)。

---

## 2.4 按索引定位的二分优化

```java
Node<E> node(int index) {
    // size >> 1 == size / 2，从较近的一端开始遍历
    if (index < (size >> 1)) {
        Node<E> x = first;
        for (int i = 0; i < index; i++)
            x = x.next;
        return x;
    } else {
        Node<E> x = last;
        for (int i = size - 1; i > index; i--)
            x = x.prev;
        return x;
    }
}
```

**二分优化**：`index < size/2` 时从 `first` 向后找，否则从 `last` 向前找。最坏情况是访问中间元素，遍历 `size/2` 步，复杂度仍是 O(n)，但**常数减半**。

这也是为什么说 `get(0)` 和 `get(size-1)` 是 O(1)（直接返回 first/last），而 `get(size/2)` 是 O(n) 最慢的位置。

---

## 2.5 迭代器：O(n) 遍历的正确姿势

```java
// LinkedListPerformanceDemo.java → testIteration()

// ❌ for + get：O(n²)！每次 get(i) 都从头/尾遍历 O(n)
for (int i = 0; i < linkedList.size(); i++) {
    int value = linkedList.get(i);  // 每次都遍历
}

// ✅ 迭代器/foreach：O(n)，节点间按 next 指针跳转，每步 O(1)
for (int value : linkedList) { }

// ✅ 等价的迭代器写法
ListIterator<Integer> it = linkedList.listIterator();
while (it.hasNext()) {
    it.next();
}
```

**性能数据（10万元素）**：
- `for + get`：约几百 ms（O(n²)）
- `foreach/iterator`：约 1-2 ms（O(n)）

差距随 n 增大呈平方增长，**这是使用 LinkedList 最容易犯的错误之一**。

---

## 2.6 `transient` 字段与自定义序列化

`size`、`first`、`last` 都标记了 `transient`，LinkedList 实现了自定义的 `writeObject`/`readObject`：

```java
private void writeObject(java.io.ObjectOutputStream s) throws IOException {
    s.defaultWriteObject();
    s.writeInt(size);
    for (Node<E> x = first; x != null; x = x.next)
        s.writeObject(x.item);  // 只序列化元素本身，不序列化节点对象
}

private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
    s.defaultReadObject();
    int size = s.readInt();
    for (int i = 0; i < size; i++)
        linkLast((E) s.readObject());  // 重建链表
}
```

原因：序列化节点对象（包含 prev/next 指针）会产生一个对象图，与直接序列化元素相比，体积更大，反序列化也更慢。自定义序列化只存储元素序列，反序列化时重新串联链表，既简洁又高效。

---

## 2.7 本章总结

- **Node 结构**：三字段（prev/item/next），堆上独立分配，内存不连续，每节点约 32 字节额外开销
- **first + last**：两个哨兵指针确保两端操作 O(1)，是实现 Deque 接口的基础
- **插入/删除**：获取到节点引用后 O(1)；通过索引则需要先遍历 O(n)
- **二分优化**：`node(index)` 从较近的一端遍历，常数减半但渐进复杂度不变
- **遍历大忌**：绝对禁止 `for + get` 循环（O(n²)），必须用迭代器/foreach（O(n)）

> **本章对应演示代码**：`LinkedListBasicDemo.java`（节点操作）、`LinkedListPerformanceDemo.java`（for 循环 vs foreach 的性能灾难）

**继续阅读**：[03_LinkedList源码深度剖析.md](./03_LinkedList源码深度剖析.md)
