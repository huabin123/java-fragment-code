# LinkedList源码深度剖析

## 1. 类定义与继承关系

### 1.1 类声明

```java
public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable {
    
    private static final long serialVersionUID = 876323262645176354L;
}
```

**继承关系**：

```
Object
  ↓
AbstractCollection
  ↓
AbstractList
  ↓
AbstractSequentialList
  ↓
LinkedList

实现的接口：
- List<E>: 列表接口
- Deque<E>: 双端队列接口
- Cloneable: 可克隆
- Serializable: 可序列化
```

---

### 1.2 为什么继承AbstractSequentialList？

**AbstractSequentialList**：专为顺序访问设计的抽象类。

```java
public abstract class AbstractSequentialList<E> extends AbstractList<E> {
    // 只需实现listIterator和size方法
    public abstract ListIterator<E> listIterator(int index);
    
    // 其他方法都基于迭代器实现
    public E get(int index) {
        try {
            return listIterator(index).next();
        } catch (NoSuchElementException exc) {
            throw new IndexOutOfBoundsException("Index: "+index);
        }
    }
    
    public void add(int index, E element) {
        try {
            listIterator(index).add(element);
        } catch (NoSuchElementException exc) {
            throw new IndexOutOfBoundsException("Index: "+index);
        }
    }
}
```

**优势**：
- 减少代码重复
- 统一实现方式
- 适合顺序访问的数据结构

---

## 2. 核心字段与构造方法

### 2.1 核心字段

```java
public class LinkedList<E> {
    // 元素数量
    transient int size = 0;
    
    // 指向第一个节点
    transient Node<E> first;
    
    // 指向最后一个节点
    transient Node<E> last;
}
```

**为什么使用transient？**

```java
// transient表示不参与默认序列化
// LinkedList自定义了序列化方式

private void writeObject(java.io.ObjectOutputStream s)
    throws java.io.IOException {
    // 写入size
    s.defaultWriteObject();
    
    // 写入所有元素
    s.writeInt(size);
    for (Node<E> x = first; x != null; x = x.next)
        s.writeObject(x.item);
}

private void readObject(java.io.ObjectInputStream s)
    throws java.io.IOException, ClassNotFoundException {
    // 读取size
    s.defaultReadObject();
    
    // 读取所有元素
    int size = s.readInt();
    for (int i = 0; i < size; i++)
        linkLast((E)s.readObject());
}
```

**原因**：
- 只序列化元素，不序列化节点结构
- 减少序列化数据量
- 反序列化时重建链表结构

---

### 2.2 构造方法

```java
// 无参构造
public LinkedList() {
}

// 集合构造
public LinkedList(Collection<? extends E> c) {
    this();
    addAll(c);
}
```

**特点**：
- 无参构造不做任何初始化（懒加载）
- 集合构造调用addAll方法

---

## 3. 添加元素的完整实现

### 3.1 add(E e) - 尾部添加

```java
public boolean add(E e) {
    linkLast(e);
    return true;
}

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
```

**流程图**：

```
空链表添加第一个元素：
  first = null, last = null
  ↓
  创建newNode
  ↓
  last = newNode
  first = newNode
  ↓
  first → [A] ← last

非空链表添加元素：
  first → [A] ↔ [B] ← last
  ↓
  创建newNode [C]
  ↓
  B.next = C
  C.prev = B
  last = C
  ↓
  first → [A] ↔ [B] ↔ [C] ← last
```

---

### 3.2 addFirst(E e) - 头部添加

```java
public void addFirst(E e) {
    linkFirst(e);
}

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
```

---

### 3.3 addLast(E e) - 尾部添加

```java
public void addLast(E e) {
    linkLast(e);
}

// linkLast方法同add(E e)
```

---

### 3.4 add(int index, E element) - 指定位置添加

```java
public void add(int index, E element) {
    checkPositionIndex(index);
    
    if (index == size)
        linkLast(element);  // 尾部添加
    else
        linkBefore(element, node(index));  // 中间添加
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

**关键方法：node(int index)**

```java
Node<E> node(int index) {
    // 二分查找优化
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
访问索引900：从尾遍历100次

平均遍历次数：size / 4
```

---

### 3.5 addAll(Collection c) - 批量添加

```java
public boolean addAll(Collection<? extends E> c) {
    return addAll(size, c);  // 在尾部添加
}

public boolean addAll(int index, Collection<? extends E> c) {
    checkPositionIndex(index);
    
    Object[] a = c.toArray();
    int numNew = a.length;
    if (numNew == 0)
        return false;
    
    Node<E> pred, succ;
    if (index == size) {
        // 在尾部添加
        succ = null;
        pred = last;
    } else {
        // 在中间添加
        succ = node(index);
        pred = succ.prev;
    }
    
    // 批量创建节点
    for (Object o : a) {
        @SuppressWarnings("unchecked") E e = (E) o;
        Node<E> newNode = new Node<>(pred, e, null);
        if (pred == null)
            first = newNode;
        else
            pred.next = newNode;
        pred = newNode;
    }
    
    // 连接后继节点
    if (succ == null) {
        last = pred;
    } else {
        pred.next = succ;
        succ.prev = pred;
    }
    
    size += numNew;
    modCount++;
    return true;
}
```

**优化**：
- 先转换为数组，避免多次调用集合的迭代器
- 批量创建节点，减少方法调用

---

## 4. 删除元素的完整实现

### 4.1 remove() / removeFirst() - 删除头部

```java
public E remove() {
    return removeFirst();
}

public E removeFirst() {
    final Node<E> f = first;
    if (f == null)
        throw new NoSuchElementException();
    return unlinkFirst(f);
}

private E unlinkFirst(Node<E> f) {
    final E element = f.item;
    final Node<E> next = f.next;
    f.item = null;
    f.next = null;  // help GC
    first = next;
    if (next == null)
        last = null;
    else
        next.prev = null;
    size--;
    modCount++;
    return element;
}
```

---

### 4.2 removeLast() - 删除尾部

```java
public E removeLast() {
    final Node<E> l = last;
    if (l == null)
        throw new NoSuchElementException();
    return unlinkLast(l);
}

private E unlinkLast(Node<E> l) {
    final E element = l.item;
    final Node<E> prev = l.prev;
    l.item = null;
    l.prev = null;  // help GC
    last = prev;
    if (prev == null)
        first = null;
    else
        prev.next = null;
    size--;
    modCount++;
    return element;
}
```

---

### 4.3 remove(int index) - 删除指定位置

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

---

### 4.4 remove(Object o) - 删除指定元素

```java
public boolean remove(Object o) {
    if (o == null) {
        // 删除null元素
        for (Node<E> x = first; x != null; x = x.next) {
            if (x.item == null) {
                unlink(x);
                return true;
            }
        }
    } else {
        // 删除非null元素
        for (Node<E> x = first; x != null; x = x.next) {
            if (o.equals(x.item)) {
                unlink(x);
                return true;
            }
        }
    }
    return false;
}
```

**特点**：
- 支持删除null元素
- 只删除第一个匹配的元素
- 时间复杂度：O(n)

---

### 4.5 clear() - 清空链表

```java
public void clear() {
    // 遍历所有节点，帮助GC
    for (Node<E> x = first; x != null; ) {
        Node<E> next = x.next;
        x.item = null;
        x.next = null;
        x.prev = null;
        x = next;
    }
    first = last = null;
    size = 0;
    modCount++;
}
```

**为什么要遍历所有节点？**

```java
// 如果只是：
first = last = null;

// 问题：
// 虽然first和last不再引用链表，但节点之间仍然互相引用
// 如果有其他地方持有某个节点的引用，整个链表都无法被GC

// 解决：
// 遍历所有节点，断开所有引用
// 即使有地方持有某个节点，其他节点也能被GC
```

---

## 5. 查询元素的完整实现

### 5.1 get(int index) - 获取指定位置元素

```java
public E get(int index) {
    checkElementIndex(index);
    return node(index).item;
}
```

---

### 5.2 getFirst() - 获取头部元素

```java
public E getFirst() {
    final Node<E> f = first;
    if (f == null)
        throw new NoSuchElementException();
    return f.item;
}
```

---

### 5.3 getLast() - 获取尾部元素

```java
public E getLast() {
    final Node<E> l = last;
    if (l == null)
        throw new NoSuchElementException();
    return l.item;
}
```

---

### 5.4 contains(Object o) - 是否包含元素

```java
public boolean contains(Object o) {
    return indexOf(o) != -1;
}

public int indexOf(Object o) {
    int index = 0;
    if (o == null) {
        for (Node<E> x = first; x != null; x = x.next) {
            if (x.item == null)
                return index;
            index++;
        }
    } else {
        for (Node<E> x = first; x != null; x = x.next) {
            if (o.equals(x.item))
                return index;
            index++;
        }
    }
    return -1;
}
```

**时间复杂度**：O(n)

---

### 5.5 lastIndexOf(Object o) - 最后一次出现的位置

```java
public int lastIndexOf(Object o) {
    int index = size;
    if (o == null) {
        // 从尾部开始遍历
        for (Node<E> x = last; x != null; x = x.prev) {
            index--;
            if (x.item == null)
                return index;
        }
    } else {
        for (Node<E> x = last; x != null; x = x.prev) {
            index--;
            if (o.equals(x.item))
                return index;
        }
    }
    return -1;
}
```

**优化**：从尾部开始遍历，找到第一个匹配的就是最后一次出现的位置。

---

## 6. 修改元素的完整实现

### 6.1 set(int index, E element) - 修改指定位置元素

```java
public E set(int index, E element) {
    checkElementIndex(index);
    Node<E> x = node(index);
    E oldVal = x.item;
    x.item = element;
    return oldVal;
}
```

**时间复杂度**：O(n)（需要先定位到节点）

---

## 7. Queue接口的实现

### 7.1 offer(E e) - 入队

```java
public boolean offer(E e) {
    return add(e);  // 尾部添加
}
```

---

### 7.2 poll() - 出队

```java
public E poll() {
    final Node<E> f = first;
    return (f == null) ? null : unlinkFirst(f);
}
```

**对比remove()**：
- `poll()`：队列为空返回null
- `remove()`：队列为空抛出异常

---

### 7.3 peek() - 查看队首元素

```java
public E peek() {
    final Node<E> f = first;
    return (f == null) ? null : f.item;
}
```

**对比getFirst()**：
- `peek()`：队列为空返回null
- `getFirst()`：队列为空抛出异常

---

## 8. Deque接口的实现

### 8.1 双端队列方法

```java
// 头部操作
public void addFirst(E e) { linkFirst(e); }
public void addLast(E e) { linkLast(e); }
public E removeFirst() { return unlinkFirst(first); }
public E removeLast() { return unlinkLast(last); }
public E getFirst() { /* ... */ }
public E getLast() { /* ... */ }

// 队列方法（不抛异常版本）
public boolean offerFirst(E e) { addFirst(e); return true; }
public boolean offerLast(E e) { addLast(e); return true; }
public E pollFirst() { return (first == null) ? null : unlinkFirst(first); }
public E pollLast() { return (last == null) ? null : unlinkLast(last); }
public E peekFirst() { return (first == null) ? null : first.item; }
public E peekLast() { return (last == null) ? null : last.item; }
```

---

### 8.2 栈方法

```java
// 入栈（头部插入）
public void push(E e) {
    addFirst(e);
}

// 出栈（头部删除）
public E pop() {
    return removeFirst();
}
```

---

## 9. 迭代器实现

### 9.1 ListIterator实现

```java
public ListIterator<E> listIterator(int index) {
    checkPositionIndex(index);
    return new ListItr(index);
}

private class ListItr implements ListIterator<E> {
    private Node<E> lastReturned;  // 上次返回的节点
    private Node<E> next;          // 下一个节点
    private int nextIndex;         // 下一个索引
    private int expectedModCount = modCount;
    
    ListItr(int index) {
        next = (index == size) ? null : node(index);
        nextIndex = index;
    }
    
    public boolean hasNext() {
        return nextIndex < size;
    }
    
    public E next() {
        checkForComodification();
        if (!hasNext())
            throw new NoSuchElementException();
        
        lastReturned = next;
        next = next.next;
        nextIndex++;
        return lastReturned.item;
    }
    
    public boolean hasPrevious() {
        return nextIndex > 0;
    }
    
    public E previous() {
        checkForComodification();
        if (!hasPrevious())
            throw new NoSuchElementException();
        
        lastReturned = next = (next == null) ? last : next.prev;
        nextIndex--;
        return lastReturned.item;
    }
    
    public int nextIndex() {
        return nextIndex;
    }
    
    public int previousIndex() {
        return nextIndex - 1;
    }
    
    public void remove() {
        checkForComodification();
        if (lastReturned == null)
            throw new IllegalStateException();
        
        Node<E> lastNext = lastReturned.next;
        unlink(lastReturned);
        if (next == lastReturned)
            next = lastNext;
        else
            nextIndex--;
        lastReturned = null;
        expectedModCount++;
    }
    
    public void set(E e) {
        if (lastReturned == null)
            throw new IllegalStateException();
        checkForComodification();
        lastReturned.item = e;
    }
    
    public void add(E e) {
        checkForComodification();
        lastReturned = null;
        if (next == null)
            linkLast(e);
        else
            linkBefore(e, next);
        nextIndex++;
        expectedModCount++;
    }
    
    final void checkForComodification() {
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
    }
}
```

**特点**：
- 支持双向遍历
- 支持在迭代过程中添加、删除、修改元素
- fail-fast机制

---

### 9.2 DescendingIterator - 逆序迭代器

```java
public Iterator<E> descendingIterator() {
    return new DescendingIterator();
}

private class DescendingIterator implements Iterator<E> {
    private final ListItr itr = new ListItr(size());
    
    public boolean hasNext() {
        return itr.hasPrevious();
    }
    
    public E next() {
        return itr.previous();
    }
    
    public void remove() {
        itr.remove();
    }
}
```

**使用示例**：

```java
LinkedList<String> list = new LinkedList<>();
list.add("A");
list.add("B");
list.add("C");

// 正序遍历：A B C
for (String s : list) {
    System.out.println(s);
}

// 逆序遍历：C B A
Iterator<String> it = list.descendingIterator();
while (it.hasNext()) {
    System.out.println(it.next());
}
```

---

## 10. 其他重要方法

### 10.1 toArray() - 转换为数组

```java
public Object[] toArray() {
    Object[] result = new Object[size];
    int i = 0;
    for (Node<E> x = first; x != null; x = x.next)
        result[i++] = x.item;
    return result;
}

@SuppressWarnings("unchecked")
public <T> T[] toArray(T[] a) {
    if (a.length < size)
        a = (T[])java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(), size);
    int i = 0;
    Object[] result = a;
    for (Node<E> x = first; x != null; x = x.next)
        result[i++] = x.item;
    
    if (a.length > size)
        a[size] = null;
    
    return a;
}
```

---

### 10.2 clone() - 克隆

```java
public Object clone() {
    LinkedList<E> clone = superClone();
    
    // 重置状态
    clone.first = clone.last = null;
    clone.size = 0;
    clone.modCount = 0;
    
    // 复制所有元素
    for (Node<E> x = first; x != null; x = x.next)
        clone.add(x.item);
    
    return clone;
}

@SuppressWarnings("unchecked")
private LinkedList<E> superClone() {
    try {
        return (LinkedList<E>) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new InternalError(e);
    }
}
```

**注意**：浅拷贝，只复制引用，不复制元素对象。

---

### 10.3 spliterator() - 可分割迭代器

```java
@Override
public Spliterator<E> spliterator() {
    return new LLSpliterator<E>(this, -1, 0);
}

static final class LLSpliterator<E> implements Spliterator<E> {
    // 支持并行流
    // ...
}
```

**用途**：支持Java 8的Stream API和并行流。

---

## 11. 源码中的精妙设计

### 11.1 二分查找优化

```java
Node<E> node(int index) {
    if (index < (size >> 1)) {
        // 从头遍历
        Node<E> x = first;
        for (int i = 0; i < index; i++)
            x = x.next;
        return x;
    } else {
        // 从尾遍历
        Node<E> x = last;
        for (int i = size - 1; i > index; i--)
            x = x.prev;
        return x;
    }
}
```

**优化效果**：平均遍历次数减半。

---

### 11.2 help GC

```java
E unlink(Node<E> x) {
    // ...
    x.item = null;
    x.next = null;
    x.prev = null;
    // ...
}
```

**作用**：帮助GC回收，避免内存泄漏。

---

### 11.3 fail-fast机制

```java
transient int modCount = 0;

final void checkForComodification() {
    if (modCount != expectedModCount)
        throw new ConcurrentModificationException();
}
```

**作用**：检测并发修改，快速失败。

---

### 11.4 自定义序列化

```java
private void writeObject(java.io.ObjectOutputStream s)
    throws java.io.IOException {
    s.defaultWriteObject();
    s.writeInt(size);
    for (Node<E> x = first; x != null; x = x.next)
        s.writeObject(x.item);
}

private void readObject(java.io.ObjectInputStream s)
    throws java.io.IOException, ClassNotFoundException {
    s.defaultReadObject();
    int size = s.readInt();
    for (int i = 0; i < size; i++)
        linkLast((E)s.readObject());
}
```

**优势**：
- 只序列化元素，不序列化节点结构
- 减少序列化数据量
- 反序列化时重建链表

---

## 12. 时间复杂度总结

| 操作 | 时间复杂度 | 说明 |
|------|-----------|------|
| **add(E e)** | O(1) | 尾部添加 |
| **addFirst(E e)** | O(1) | 头部添加 |
| **addLast(E e)** | O(1) | 尾部添加 |
| **add(int, E)** | O(n) | 需要定位 |
| **remove()** | O(1) | 头部删除 |
| **removeFirst()** | O(1) | 头部删除 |
| **removeLast()** | O(1) | 尾部删除 |
| **remove(int)** | O(n) | 需要定位 |
| **remove(Object)** | O(n) | 需要遍历 |
| **get(int)** | O(n) | 需要遍历 |
| **getFirst()** | O(1) | 直接访问 |
| **getLast()** | O(1) | 直接访问 |
| **set(int, E)** | O(n) | 需要定位 |
| **contains(Object)** | O(n) | 需要遍历 |
| **indexOf(Object)** | O(n) | 需要遍历 |
| **size()** | O(1) | 直接返回 |

---

## 13. 总结

### 13.1 核心实现

1. **双向链表**：每个节点包含item、next、prev
2. **first和last指针**：快速访问头尾节点
3. **二分查找优化**：根据索引位置决定遍历方向
4. **help GC**：删除节点时断开所有引用

---

### 13.2 设计亮点

1. **继承AbstractSequentialList**：减少代码重复
2. **实现多个接口**：List、Deque、Queue
3. **自定义序列化**：减少序列化数据量
4. **fail-fast机制**：检测并发修改

---

### 13.3 下一步学习

在理解了LinkedList的源码实现后，接下来我们将学习：

**LinkedList与ArrayList对比分析**：性能对比、选型指南

---

**继续阅读**：[04_LinkedList与ArrayList对比分析.md](./04_LinkedList与ArrayList对比分析.md)
