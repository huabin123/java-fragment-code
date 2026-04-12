# 第三章：LinkedList 源码深度剖析

## 3.1 add 系列方法的完整路径

LinkedList 的 add 方法有多个入口，它们最终都归结到 `linkFirst` 或 `linkLast` 或 `linkBefore`：

```
add(e)          → linkLast(e)         O(1) 尾部插入
addLast(e)      → linkLast(e)         O(1)
addFirst(e)     → linkFirst(e)        O(1)
offer(e)        → add(e)              O(1)
offerFirst(e)   → addFirst(e)         O(1)
offerLast(e)    → addLast(e)          O(1)
push(e)         → addFirst(e)         O(1)
add(index, e)   → linkBefore(e, node(index))  O(n)（先定位，再 O(1) 插入）
```

### linkLast 源码

```java
void linkLast(E e) {
    final Node<E> l = last;
    final Node<E> newNode = new Node<>(l, e, null);  // prev=旧last, next=null
    last = newNode;
    if (l == null)
        first = newNode;  // 链表原来为空
    else
        l.next = newNode; // 旧 last 的 next 指向新节点
    size++;
    modCount++;
}
```

### linkBefore 源码（在指定节点前插入）

```java
void linkBefore(E e, Node<E> succ) {
    final Node<E> pred = succ.prev;
    final Node<E> newNode = new Node<>(pred, e, succ);
    succ.prev = newNode;
    if (pred == null)
        first = newNode;  // succ 是头节点
    else
        pred.next = newNode;
    size++;
    modCount++;
}
```

**关键理解**：`linkBefore` 本身是 O(1) 的指针操作，但调用方 `add(int index, E e)` 需要先执行 `node(index)` 找到 `succ`，这一步是 O(n)。因此"有迭代器时删除 O(1)，无迭代器时实际 O(n)"的规律同样适用于插入。

---

## 3.2 remove 系列方法

```
remove()          → removeFirst()               O(1)
removeFirst()     → unlinkFirst(first)           O(1)
removeLast()      → unlinkLast(last)             O(1)
poll()            → (first == null) ? null : unlinkFirst(first)  O(1)
pop()             → removeFirst()                O(1)
remove(index)     → unlink(node(index))          O(n)
remove(Object)    → 遍历找到节点再 unlink        O(n)
```

### remove(Object o) 的完整实现

```java
public boolean remove(Object o) {
    if (o == null) {
        for (Node<E> x = first; x != null; x = x.next) {
            if (x.item == null) {   // null 用 == 比较
                unlink(x);
                return true;
            }
        }
    } else {
        for (Node<E> x = first; x != null; x = x.next) {
            if (o.equals(x.item)) { // 非 null 用 equals
                unlink(x);
                return true;
            }
        }
    }
    return false;
}
```

**值得注意**：
1. 对 null 和非 null 分别处理，避免在非 null 对象上调用 `null.equals()`
2. 只删除**第一个**匹配的元素，之后立即返回
3. 时间复杂度 O(n)，最坏遍历整个链表（元素不存在）

---

## 3.3 ListIterator 的双向遍历与安全删除

LinkedList 的迭代器是 `ListItr`，支持双向遍历（`hasPrevious`/`previous`）：

```java
private class ListItr implements ListIterator<E> {
    private Node<E> lastReturned;  // 最近一次 next() 或 previous() 返回的节点
    private Node<E> next;          // 下一次 next() 将返回的节点
    private int nextIndex;
    private int expectedModCount = modCount;  // fail-fast 检查

    public E next() {
        checkForComodification();  // 检查 modCount 是否被外部修改
        lastReturned = next;
        next = next.next;
        nextIndex++;
        return lastReturned.item;
    }

    public void remove() {
        checkForComodification();
        // 删除 lastReturned 节点，并更新迭代器状态
        if (next == lastReturned)
            next = lastReturned.next;
        else
            nextIndex--;
        LinkedList.this.unlink(lastReturned);  // O(1) 删除（已有节点引用）
        lastReturned = null;
        expectedModCount++;  // 同步 modCount，避免下次 checkForComodification 误报
    }
}
```

**迭代中删除的正确方式**：

```java
// ✅ 使用 Iterator.remove()：安全且 O(1)
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (shouldRemove(it.next())) {
        it.remove();  // 通过迭代器删除，内部更新 expectedModCount
    }
}

// ✅ JDK 8：removeIf（底层也是迭代器）
list.removeIf(e -> shouldRemove(e));

// ❌ 遍历时直接调用 list.remove(e)：会抛 ConcurrentModificationException
for (String e : list) {
    if (shouldRemove(e)) {
        list.remove(e);  // modCount++ 但 expectedModCount 不变，下次 next() 抛异常
    }
}
```

---

## 3.4 Deque 接口方法的二义性陷阱

LinkedList 实现了 `Deque`，同一个操作有多个方法名，它们的区别在于**失败时的行为**：

| 操作 | 返回特殊值（推荐）| 抛出异常 |
|------|----------------|---------|
| 头部入队 | `offerFirst(e)` | `addFirst(e)` |
| 尾部入队 | `offerLast(e)` / `offer(e)` | `addLast(e)` / `add(e)` |
| 头部出队 | `pollFirst()` / `poll()` | `removeFirst()` / `remove()` |
| 尾部出队 | `pollLast()` | `removeLast()` |
| 查看头部 | `peekFirst()` / `peek()` | `getFirst()` / `element()` |
| 查看尾部 | `peekLast()` | `getLast()` |

```java
// LinkedListAsQueueDemo.java → offerPollVsAddRemove()
Queue<String> queue = new LinkedList<>();
queue.offer("A");
queue.poll();    // 空时返回 null，不抛异常 ✅

queue.remove();  // 空时抛 NoSuchElementException ❌（除非你需要这个语义）
```

**推荐**：业务代码中优先使用返回特殊值的方法（`offer/poll/peek`），避免空队列时的意外异常。

---

## 3.5 Queue 接口 vs Deque 接口的声明变量

```java
// LinkedListBasicDemo.java 中的三种声明方式

// 1. 只用队列功能时，声明为 Queue
Queue<String> queue = new LinkedList<>();
// 好处：接口约束，不会误用 Deque 特有方法

// 2. 用双端队列功能时，声明为 Deque
Deque<String> deque = new LinkedList<>();
// 好处：可以使用 offerFirst/pollLast 等双端方法

// 3. 需要同时用 List 和 Deque 时，才声明为 LinkedList
LinkedList<String> linked = new LinkedList<>();
// 代价：暴露了更多 API，容易被误用 get(index) 等 O(n) 方法
```

---

## 3.6 TaskQueue 实战中的 LinkedList 用法

`LinkedListAsQueueDemo.java → taskQueueExample()` 和 `TaskQueue.java` 演示了队列的标准实现模式：

```java
// TaskQueue.java（thread-safe 版本，用 synchronized 保护 LinkedList）
private final LinkedList<Task> taskQueue = new LinkedList<>();

public synchronized void enqueue(Task task) {
    taskQueue.offerLast(task);   // 尾部入队
    notifyAll();
}

public synchronized Task dequeue() throws InterruptedException {
    while (taskQueue.isEmpty()) {
        wait();  // 队列空时阻塞等待
    }
    return taskQueue.pollFirst();  // 头部出队
}
```

这是一个在 LinkedList 基础上手动实现阻塞队列的典型模式。现代 Java 代码中通常直接使用 `LinkedBlockingQueue`（内部也是链表结构，但并发安全性更完整）。

---

## 3.7 本章总结

- **add 路径**：所有插入最终是 `linkFirst`/`linkLast`/`linkBefore` 的组合，获取到节点引用后 O(1)
- **remove 路径**：`remove(Object)` 需要遍历 O(n)，迭代器 `remove()` 因持有节点引用是 O(1)
- **迭代器安全删除**：必须用 `Iterator.remove()`，不能在 foreach 中直接调 list.remove()
- **Deque 方法选择**：优先用返回特殊值的 `offer/poll/peek`，而非抛异常的 `add/remove/element`
- **声明类型**：按最小接口原则，队列场景用 `Queue`，双端场景用 `Deque`，避免暴露 `LinkedList`

> **本章对应演示代码**：`LinkedListAsQueueDemo.java`（队列操作与异常行为）、`LinkedListBasicDemo.java`（List/Deque 双重接口）

**继续阅读**：[04_LinkedList与ArrayList对比分析.md](./04_LinkedList与ArrayList对比分析.md)
