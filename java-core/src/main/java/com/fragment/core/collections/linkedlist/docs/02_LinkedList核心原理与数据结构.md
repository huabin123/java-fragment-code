# LinkedList核心原理与数据结构

## 1. LinkedList的底层数据结构

### 1.1 整体架构

LinkedList是基于**双向链表**实现的List和Deque。

```
LinkedList内部结构：

LinkedList对象
├── first: 指向第一个节点
├── last: 指向最后一个节点
└── size: 元素数量

双向链表：
null ← [Node1] ↔ [Node2] ↔ [Node3] ↔ [Node4] → null
       ↑                                    ↑
      first                                last

每个Node包含：
- item: 存储数据
- next: 指向下一个节点
- prev: 指向上一个节点
```

---

### 1.2 核心字段

```java
public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable {
    
    // 元素数量
    transient int size = 0;
    
    // 指向第一个节点
    transient Node<E> first;
    
    // 指向最后一个节点
    transient Node<E> last;
    
    // 构造函数
    public LinkedList() {
    }
    
    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }
}
```

**关键点**：
- `first`和`last`指针：快速访问头尾节点
- `size`字段：记录元素数量，size()方法O(1)
- `transient`关键字：序列化时不保存，通过writeObject/readObject自定义序列化

---

### 1.3 Node节点结构

```java
private static class Node<E> {
    E item;          // 存储的数据
    Node<E> next;    // 指向下一个节点
    Node<E> prev;    // 指向上一个节点
    
    Node(Node<E> prev, E element, Node<E> next) {
        this.item = element;
        this.next = next;
        this.prev = prev;
    }
}
```

**节点内存结构**：

```
Node对象内存布局（64位JVM，开启压缩指针）：
├── 对象头：12字节
├── item引用：4字节（压缩指针）
├── next引用：4字节（压缩指针）
├── prev引用：4字节（压缩指针）
└── 对齐填充：4字节
总计：28字节

如果不压缩指针：
├── 对象头：16字节
├── item引用：8字节
├── next引用：8字节
└── prev引用：8字节
总计：40字节
```

---

## 2. 为什么采用双向链表？

### 2.1 单向链表 vs 双向链表

**单向链表**：

```
单向链表结构：
[A] → [B] → [C] → [D] → null

优点：
- 内存占用小（只需一个next指针）
- 实现简单

缺点：
- 只能从前往后遍历
- 删除节点需要知道前一个节点
- 无法从尾部快速访问
```

**双向链表**：

```
双向链表结构：
null ← [A] ↔ [B] ↔ [C] ↔ [D] → null

优点：
- 可以双向遍历
- 删除节点只需节点本身
- 可以从头部或尾部快速访问

缺点：
- 内存占用大（需要两个指针）
- 实现稍复杂
```

---

### 2.2 双向链表的优势

#### 优势1：双向遍历

```java
// 从前往后遍历
Node<E> node = first;
while (node != null) {
    System.out.println(node.item);
    node = node.next;
}

// 从后往前遍历
Node<E> node = last;
while (node != null) {
    System.out.println(node.item);
    node = node.prev;
}
```

---

#### 优势2：快速删除

**单向链表删除节点**：

```java
// 需要知道前一个节点
void remove(Node prev, Node current) {
    prev.next = current.next;
}

// 如果只知道current，需要遍历找到prev
void remove(Node current) {
    Node prev = head;
    while (prev.next != current) {
        prev = prev.next;  // O(n)
    }
    prev.next = current.next;
}
```

**双向链表删除节点**：

```java
// 只需节点本身
void remove(Node<E> node) {
    Node<E> prev = node.prev;
    Node<E> next = node.next;
    
    if (prev != null) {
        prev.next = next;
    }
    if (next != null) {
        next.prev = prev;
    }
    
    // O(1)
}
```

---

#### 优势3：实现Deque接口

双向链表天然支持双端队列操作：

```java
// 头部操作
addFirst(E e);    // 头部插入
removeFirst();    // 头部删除
getFirst();       // 获取头部元素

// 尾部操作
addLast(E e);     // 尾部插入
removeLast();     // 尾部删除
getLast();        // 获取尾部元素

// 所有操作都是O(1)
```

---

### 2.3 为什么不用循环链表？

**循环链表**：

```
循环链表结构：
[A] ↔ [B] ↔ [C] ↔ [D]
 ↑                   ↓
 └───────────────────┘

优点：
- 可以从任意节点遍历整个链表
- 没有null指针

缺点：
- 实现复杂
- 容易出现死循环
- 不符合List的语义（有明确的头尾）
```

**LinkedList选择非循环链表的原因**：
1. 符合List的语义（有明确的头尾）
2. 实现简单，不易出错
3. first和last指针已经提供了快速访问头尾的能力

---

## 3. 核心操作原理

### 3.1 头部插入（addFirst）

**操作流程**：

```
原链表：
null ← [A] ↔ [B] ↔ [C] → null
       ↑              ↑
      first          last

插入X到头部：
1. 创建新节点X
2. X.next = first (A)
3. X.prev = null
4. A.prev = X
5. first = X

结果：
null ← [X] ↔ [A] ↔ [B] ↔ [C] → null
       ↑                    ↑
      first                last
```

**源码实现**：

```java
private void linkFirst(E e) {
    final Node<E> f = first;
    final Node<E> newNode = new Node<>(null, e, f);
    first = newNode;
    if (f == null)
        last = newNode;  // 链表为空
    else
        f.prev = newNode;
    size++;
    modCount++;
}

public void addFirst(E e) {
    linkFirst(e);
}
```

**时间复杂度**：O(1)

---

### 3.2 尾部插入（addLast）

**操作流程**：

```
原链表：
null ← [A] ↔ [B] ↔ [C] → null
       ↑              ↑
      first          last

插入X到尾部：
1. 创建新节点X
2. X.prev = last (C)
3. X.next = null
4. C.next = X
5. last = X

结果：
null ← [A] ↔ [B] ↔ [C] ↔ [X] → null
       ↑                    ↑
      first                last
```

**源码实现**：

```java
void linkLast(E e) {
    final Node<E> l = last;
    final Node<E> newNode = new Node<>(l, e, null);
    last = newNode;
    if (l == null)
        first = newNode;  // 链表为空
    else
        l.next = newNode;
    size++;
    modCount++;
}

public void addLast(E e) {
    linkLast(e);
}

public boolean add(E e) {
    linkLast(e);  // add默认在尾部插入
    return true;
}
```

**时间复杂度**：O(1)

---

### 3.3 中间插入（add(index, element)）

**操作流程**：

```
原链表：
null ← [A] ↔ [B] ↔ [C] ↔ [D] → null
       ↑                    ↑
      first                last

在索引2插入X（B和C之间）：
1. 定位到索引2的节点（C）
2. 创建新节点X
3. X.prev = B
4. X.next = C
5. B.next = X
6. C.prev = X

结果：
null ← [A] ↔ [B] ↔ [X] ↔ [C] ↔ [D] → null
       ↑                         ↑
      first                     last
```

**源码实现**：

```java
public void add(int index, E element) {
    checkPositionIndex(index);  // 检查索引范围
    
    if (index == size)
        linkLast(element);  // 尾部插入
    else
        linkBefore(element, node(index));  // 中间插入
}

void linkBefore(E e, Node<E> succ) {
    // succ: 后继节点
    final Node<E> pred = succ.prev;  // 前驱节点
    final Node<E> newNode = new Node<>(pred, e, succ);
    succ.prev = newNode;
    if (pred == null)
        first = newNode;
    else
        pred.next = newNode;
    size++;
    modCount++;
}
```

**时间复杂度**：O(n)（需要先定位到索引位置）

---

### 3.4 定位节点（node方法）

**核心优化**：二分查找优化

```java
Node<E> node(int index) {
    // 二分查找优化：根据索引位置决定从头还是从尾遍历
    if (index < (size >> 1)) {
        // 索引在前半部分，从头开始遍历
        Node<E> x = first;
        for (int i = 0; i < index; i++)
            x = x.next;
        return x;
    } else {
        // 索引在后半部分，从尾开始遍历
        Node<E> x = last;
        for (int i = size - 1; i > index; i--)
            x = x.prev;
        return x;
    }
}
```

**优化效果**：

```
链表长度：1000
访问索引100：从头遍历100次
访问索引900：从尾遍历100次（而不是从头遍历900次）

平均遍历次数：size / 4（而不是size / 2）
```

**时间复杂度**：O(n/2) = O(n)

---

### 3.5 头部删除（removeFirst）

**操作流程**：

```
原链表：
null ← [A] ↔ [B] ↔ [C] → null
       ↑              ↑
      first          last

删除头部节点A：
1. first = A.next (B)
2. B.prev = null
3. A.next = null (help GC)
4. A.item = null (help GC)

结果：
null ← [B] ↔ [C] → null
       ↑         ↑
      first     last
```

**源码实现**：

```java
private E unlinkFirst(Node<E> f) {
    // f是first节点
    final E element = f.item;
    final Node<E> next = f.next;
    f.item = null;
    f.next = null;  // help GC
    first = next;
    if (next == null)
        last = null;  // 链表变空
    else
        next.prev = null;
    size--;
    modCount++;
    return element;
}

public E removeFirst() {
    final Node<E> f = first;
    if (f == null)
        throw new NoSuchElementException();
    return unlinkFirst(f);
}
```

**时间复杂度**：O(1)

---

### 3.6 尾部删除（removeLast）

**操作流程**：

```
原链表：
null ← [A] ↔ [B] ↔ [C] → null
       ↑              ↑
      first          last

删除尾部节点C：
1. last = C.prev (B)
2. B.next = null
3. C.prev = null (help GC)
4. C.item = null (help GC)

结果：
null ← [A] ↔ [B] → null
       ↑         ↑
      first     last
```

**源码实现**：

```java
private E unlinkLast(Node<E> l) {
    // l是last节点
    final E element = l.item;
    final Node<E> prev = l.prev;
    l.item = null;
    l.prev = null;  // help GC
    last = prev;
    if (prev == null)
        first = null;  // 链表变空
    else
        prev.next = null;
    size--;
    modCount++;
    return element;
}

public E removeLast() {
    final Node<E> l = last;
    if (l == null)
        throw new NoSuchElementException();
    return unlinkLast(l);
}
```

**时间复杂度**：O(1)

---

### 3.7 中间删除（remove(index)）

**操作流程**：

```
原链表：
null ← [A] ↔ [B] ↔ [C] ↔ [D] → null
       ↑                    ↑
      first                last

删除索引2的节点（C）：
1. 定位到索引2的节点（C）
2. B.next = C.next (D)
3. D.prev = C.prev (B)
4. C.next = null (help GC)
5. C.prev = null (help GC)
6. C.item = null (help GC)

结果：
null ← [A] ↔ [B] ↔ [D] → null
       ↑              ↑
      first          last
```

**源码实现**：

```java
public E remove(int index) {
    checkElementIndex(index);
    return unlink(node(index));
}

E unlink(Node<E> x) {
    final E element = x.item;
    final Node<E> next = x.next;
    final Node<E> prev = x.prev;
    
    if (prev == null) {
        first = next;
    } else {
        prev.next = next;
        x.prev = null;
    }
    
    if (next == null) {
        last = prev;
    } else {
        next.prev = prev;
        x.next = null;
    }
    
    x.item = null;
    size--;
    modCount++;
    return element;
}
```

**时间复杂度**：O(n)（需要先定位到索引位置）

---

### 3.8 获取元素（get）

**源码实现**：

```java
public E get(int index) {
    checkElementIndex(index);
    return node(index).item;
}

public E getFirst() {
    final Node<E> f = first;
    if (f == null)
        throw new NoSuchElementException();
    return f.item;
}

public E getLast() {
    final Node<E> l = last;
    if (l == null)
        throw new NoSuchElementException();
    return l.item;
}
```

**时间复杂度**：
- `get(index)`：O(n)
- `getFirst()`：O(1)
- `getLast()`：O(1)

---

## 4. 时间复杂度总结

| 操作 | 时间复杂度 | 说明 |
|------|-----------|------|
| **addFirst** | O(1) | 直接在头部插入 |
| **addLast** | O(1) | 直接在尾部插入 |
| **add(index, e)** | O(n) | 需要先定位到索引位置 |
| **removeFirst** | O(1) | 直接删除头部 |
| **removeLast** | O(1) | 直接删除尾部 |
| **remove(index)** | O(n) | 需要先定位到索引位置 |
| **get(index)** | O(n) | 需要遍历到索引位置 |
| **getFirst** | O(1) | 直接访问first指针 |
| **getLast** | O(1) | 直接访问last指针 |
| **size** | O(1) | 直接返回size字段 |
| **contains** | O(n) | 需要遍历整个链表 |
| **indexOf** | O(n) | 需要遍历整个链表 |

---

## 5. 内存结构分析

### 5.1 LinkedList对象内存占用

```
LinkedList对象（空链表）：
├── 对象头：12字节
├── first引用：4字节（压缩指针）
├── last引用：4字节（压缩指针）
├── size：4字节
├── modCount：4字节（继承自AbstractList）
└── 对齐填充：4字节
总计：32字节
```

---

### 5.2 Node节点内存占用

```
Node对象：
├── 对象头：12字节
├── item引用：4字节（压缩指针）
├── next引用：4字节（压缩指针）
├── prev引用：4字节（压缩指针）
└── 对齐填充：4字节
总计：28字节
```

---

### 5.3 存储1000个元素的内存占用

```
假设：
- 每个元素是String，平均长度10

计算：
LinkedList对象：32字节
Node节点：1000 * 28 = 28000字节
String对象：1000 * (24 + 10 * 2) = 44000字节

总计：32 + 28000 + 44000 = 72032字节 ≈ 70KB

平均每个元素：70KB / 1000 = 72字节
```

**对比ArrayList**：

```
ArrayList存储1000个元素：
ArrayList对象：40字节
数组：16 + 1024 * 4 = 4112字节（假设capacity=1024）
String对象：44000字节

总计：40 + 4112 + 44000 = 48152字节 ≈ 47KB

结论：LinkedList比ArrayList多占用约50%的内存
```

---

## 6. LinkedList实现的接口

### 6.1 List接口

```java
public interface List<E> extends Collection<E> {
    boolean add(E e);
    void add(int index, E element);
    E get(int index);
    E set(int index, E element);
    E remove(int index);
    int indexOf(Object o);
    int size();
    // ...
}
```

LinkedList实现了List接口，支持索引访问。

---

### 6.2 Deque接口

```java
public interface Deque<E> extends Queue<E> {
    void addFirst(E e);
    void addLast(E e);
    E removeFirst();
    E removeLast();
    E getFirst();
    E getLast();
    // ...
}
```

LinkedList实现了Deque接口，支持双端队列操作。

---

### 6.3 Queue接口

```java
public interface Queue<E> extends Collection<E> {
    boolean offer(E e);  // 入队
    E poll();            // 出队
    E peek();            // 查看队首元素
    // ...
}
```

LinkedList实现了Queue接口，可以作为队列使用。

---

## 7. LinkedList的设计亮点

### 7.1 亮点1：first和last指针

**设计**：维护first和last指针，快速访问头尾节点。

**优势**：
- 头尾操作O(1)
- 支持双端队列

---

### 7.2 亮点2：二分查找优化

**设计**：根据索引位置决定从头还是从尾遍历。

**优势**：
- 平均遍历次数减半
- 提高get/set/add/remove的性能

---

### 7.3 亮点3：help GC

**设计**：删除节点时，将next、prev、item都置为null。

```java
x.item = null;
x.next = null;
x.prev = null;
```

**优势**：
- 帮助GC回收
- 避免内存泄漏

---

### 7.4 亮点4：实现多个接口

**设计**：同时实现List、Deque、Queue接口。

**优势**：
- 功能丰富
- 可以作为List、队列、栈、双端队列使用

---

## 8. 总结

### 8.1 核心数据结构

```
双向链表：
- 每个节点包含item、next、prev
- first指针指向头节点
- last指针指向尾节点
- size字段记录元素数量
```

---

### 8.2 核心优势

1. **头尾操作O(1)**：addFirst、addLast、removeFirst、removeLast
2. **双向遍历**：可以从头或从尾遍历
3. **无需扩容**：动态分配内存
4. **实现多个接口**：List、Deque、Queue

---

### 8.3 核心劣势

1. **随机访问O(n)**：get、set需要遍历
2. **内存占用大**：每个节点需要额外存储两个指针
3. **缓存不友好**：内存不连续

---

### 8.4 下一步学习

在理解了LinkedList的核心原理和数据结构后，接下来我们将深入学习：

**LinkedList源码深度剖析**：分析add/get/remove的完整实现

---

**继续阅读**：[03_LinkedList源码深度剖析.md](./03_LinkedList源码深度剖析.md)
