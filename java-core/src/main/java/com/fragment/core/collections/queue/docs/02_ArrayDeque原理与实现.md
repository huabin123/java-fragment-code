# 第二章：ArrayDeque 原理与实现

> **对应演示代码**：`demo/ArrayDequeDemo.java`

## 2.1 为什么推荐 ArrayDeque？

Java 里有三种常见的"栈/队列"用法：

```java
Stack<String> stack = new Stack<>();           // JDK 1.0 的老类
LinkedList<String> queue = new LinkedList<>(); // 双向链表实现
ArrayDeque<String> deque = new ArrayDeque<>(); // JDK 1.6 引入，推荐
```

`Stack` 和 `LinkedList` 在实践中都有明显的缺陷：

**`Stack` 的问题**：继承自 `Vector`，所有方法（包括 `push/pop/peek`）都加了 `synchronized`。在单线程场景下，每次操作都要获取一把没有竞争的锁，这是纯粹的性能浪费。此外，继承 `Vector` 也暴露了 `get(index)`、`insertElementAt()` 等与"栈"语义无关的方法，破坏了封装性。

**`LinkedList` 的问题**：每次 `offer` 都要分配一个 `Node` 对象（`new Node(item, null, last)`），每次 `poll` 都要回收一个 `Node`。在高频操作场景下，GC 压力显著。此外，节点对象分散在堆内存的不同位置，CPU 缓存命中率低。

**官方文档的明确推荐**（JDK `ArrayDeque` 类注释）：
> "This class is likely to be faster than Stack when used as a stack, and faster than LinkedList when used as a queue."

---

## 2.2 核心数据结构：循环数组

`ArrayDeque` 的底层是一个**可扩容的循环数组**（Circular Array）。理解这个结构是理解所有操作的基础。

### 2.2.1 为什么用循环数组？

普通数组实现队列有一个致命缺陷：

```
初始状态：[A][B][C][ ][ ]
         head=0        tail=3

poll() 出队 A：[ ][B][C][ ][ ]
              head=1      tail=3
              ↑ 位置0 永远浪费了！

继续 poll()：[ ][ ][C][ ][ ]
             head=2      tail=3
             ↑ 越来越多空间浪费
```

普通数组每次从头部出队后，头部的空间就永久废弃，只能通过整体移动元素（O(n)）来回收，或者用循环数组。

**循环数组的思路**：把数组想象成一个环，`head` 和 `tail` 都可以绕着这个环移动，头部出队的空间下一次入队时可以复用：

```
数组大小 = 8（索引 0~7）

初始：head=0, tail=0, 数组空

入队 A,B,C：
  [A][B][C][ ][ ][ ][ ][ ]
   0  1  2  3  4  5  6  7
  head=0        tail=3

出队 A（头部）：
  [ ][B][C][ ][ ][ ][ ][ ]
   0  1  2  3  4  5  6  7
      head=1    tail=3

继续入队 D,E,F,G,H（超出尾部，绕回数组头部）：
  [H][B][C][D][E][F][G][ ]
   0  1  2  3  4  5  6  7
        ↑                 ↑
       tail=1            head=1
  （此时 tail 绕回到了 0，写入后 tail=1，与 head 重叠说明队列满）
```

### 2.2.2 关键字段

```java
// JDK 源码（简化）
public class ArrayDeque<E> extends AbstractCollection<E>
        implements Deque<E>, Cloneable, Serializable {

    transient Object[] elements;  // 底层数组
    transient int head;           // 队头索引（下一个 poll 的位置）
    transient int tail;           // 队尾索引（下一个 offer 的位置）

    // 数组容量必须是 2 的幂次方（原因见 2.2.3）
    private static final int MIN_INITIAL_CAPACITY = 8;
}
```

### 2.2.3 为什么容量必须是 2 的幂次方？

循环数组的核心操作是**取模运算**：当 `tail` 到达数组末尾后，需要绕回到数组头部，即 `tail = (tail + 1) % capacity`。

取模运算（`%`）需要做除法，比较耗时。当容量是 2 的幂次方时，可以用位运算替代：

```java
// 普通取模（需要除法）
tail = (tail + 1) % capacity;

// 位与运算替代（capacity 是 2 的幂次方时等价，速度更快）
tail = (tail + 1) & (capacity - 1);

// 原理：capacity = 8 = 1000（二进制）
//       capacity - 1 = 7 = 0111（二进制）
//       任何数 & 0111 的结果都在 0~7 之间，自动实现了取模
```

这是 Java 集合框架中大量使用的技巧（HashMap 的数组大小也是 2 的幂次方，原因相同）。

---

## 2.3 核心操作的实现原理

### 2.3.1 offerFirst（头部入队）

```java
// JDK 源码
public void addFirst(E e) {
    if (e == null)
        throw new NullPointerException();  // 不允许 null
    elements[head = (head - 1) & (elements.length - 1)] = e;  // head 向左移一位（循环）
    if (head == tail)                      // head 追上 tail，说明数组满了
        doubleCapacity();                  // 扩容为 2 倍
}
```

`head = (head - 1) & (elements.length - 1)` 这一行是精髓：
- 当 `head = 0` 时，`(0 - 1) & 7 = (-1) & 7 = 7`，即绕回到数组尾部，实现了循环

### 2.3.2 offerLast（尾部入队）

```java
// JDK 源码
public void addLast(E e) {
    if (e == null)
        throw new NullPointerException();
    elements[tail] = e;
    if ((tail = (tail + 1) & (elements.length - 1)) == head)  // tail 向右移一位（循环）
        doubleCapacity();
}
```

> **注意**：不允许存放 `null` 元素，是因为 `null` 被用作"数组槽位为空"的哨兵标志。这一限制在演示代码 `ArrayDequeDemo.java` 中有体现（操作方法对比部分，`poll()` 返回 `null` 就意味着队列为空）。

### 2.3.3 扩容机制（doubleCapacity）

```java
// JDK 源码（简化）
private void doubleCapacity() {
    int p = head;
    int n = elements.length;
    int r = n - p;               // head 右侧的元素个数
    int newCapacity = n << 1;    // 新容量 = 旧容量 × 2（位运算）
    if (newCapacity < 0)
        throw new IllegalStateException("Sorry, deque too big");
    Object[] a = new Object[newCapacity];
    System.arraycopy(elements, p, a, 0, r);    // 复制 head 右侧的部分
    System.arraycopy(elements, 0, a, r, p);    // 复制 head 左侧的部分（绕回的那段）
    elements = a;
    head = 0;
    tail = n;
}
```

扩容时把"断开"的循环数组重新整理成连续的，`head` 归零。新数组大小是原来的 2 倍，保证仍是 2 的幂次方。

**这里体现了一个重要的设计取舍**：`ArrayDeque` 选择了"懒扩容"——只有在数组真正满了才扩容，而不是提前预留空间。这使得内存利用率更高，代价是偶尔有一次 O(n) 的扩容操作，但均摊下来每个元素的插入复杂度仍是 O(1)。

---

## 2.4 三种使用模式详解

### 2.4.1 作为队列（FIFO）

对应演示：`ArrayDequeDemo.java → useAsQueue()`

```java
// 用 Queue 接口接收，隐藏 Deque 的两端操作，语义更清晰
Queue<String> queue = new ArrayDeque<>();

queue.offer("任务A");  // 等同于 offerLast，从尾部入队
queue.offer("任务B");
queue.offer("任务C");

String head = queue.peek();   // 查看队头，不移除
String item = queue.poll();   // 从头部出队，返回 "任务A"
```

**为什么用 `Queue` 接口接收而不是 `ArrayDeque`？**

接口编程的好处：调用方只依赖 `Queue` 接口，如果将来需要替换为 `LinkedBlockingQueue`（并发场景），只需修改一行赋值代码，调用方完全不受影响。

### 2.4.2 作为栈（LIFO）

对应演示：`ArrayDequeDemo.java → useAsStack()`

```java
// 用 Deque 接口接收（Stack 没有对应的接口）
Deque<String> stack = new ArrayDeque<>();

stack.push("第1层");   // 等同于 addFirst，从头部入栈
stack.push("第2层");
stack.push("第3层");
// 此时内部顺序：[第3层, 第2层, 第1层]（头部是栈顶）

String top = stack.peek();  // 查看栈顶（第3层），不移除
String item = stack.pop();  // 弹出栈顶（第3层）
```

`push` 是 `addFirst` 的语义别名，`pop` 是 `removeFirst` 的别名。Java 用这两个方法名来明确表达"我现在把这个 Deque 当栈用"，增强代码可读性。

### 2.4.3 作为双端队列

对应演示：`ArrayDequeDemo.java → useAsDeque()`

双端队列同时具备队列和栈的能力，典型应用场景：

- **滑动窗口最大值**（LeetCode 239）：维护单调递减的双端队列，O(n) 求所有窗口最大值
- **任务调度中的"工作窃取"**：线程维护自己的 `ArrayDeque`，从尾部取任务（自己的），其他线程从头部偷任务（负载均衡），`ForkJoinPool` 就使用了这个机制
- **浏览器的前进/后退**：用双端队列维护两个历史栈

---

## 2.5 性能数据与对比

| 操作 | `ArrayDeque` | `LinkedList` | 说明 |
|------|-------------|-------------|------|
| `offer`/`offerLast` | **O(1)** 均摊 | O(1) | ArrayDeque 偶有扩容 O(n)，均摊仍是 O(1) |
| `offerFirst` | **O(1)** 均摊 | O(1) | 同上 |
| `poll`/`pollFirst` | **O(1)** | O(1) | |
| `pollLast` | **O(1)** | O(1) | |
| `peek` | **O(1)** | O(1) | |
| 内存分配 | **无额外分配** | 每次分配 Node | ArrayDeque 更 GC 友好 |
| CPU 缓存命中 | **高**（数组连续） | 低（节点离散） | |

实际 benchmark 中，`ArrayDeque` 作为栈/队列使用时，吞吐量通常是 `LinkedList` 的 **2~3 倍**，原因就在于：
1. 无 `Node` 对象分配，GC 压力小
2. 数组内存连续，CPU 缓存预取有效

---

## 2.6 关键约束与注意事项

### 约束1：不允许 null 元素

```java
ArrayDeque<String> deque = new ArrayDeque<>();
deque.offer(null);  // 抛出 NullPointerException
```

原因：`poll()` 返回 `null` 表示"队列为空"，如果允许存放 `null`，就无法区分"取到了 null 元素"和"队列为空"这两种情况。

### 约束2：非线程安全

`ArrayDeque` 没有任何同步措施。多线程并发操作会破坏 `head`/`tail` 的一致性，导致数据丢失或数组越界。多线程场景应使用 `ArrayBlockingQueue` 或 `ConcurrentLinkedDeque`。

### 约束3：迭代时不可修改

迭代过程中调用 `offer`/`poll` 会抛出 `ConcurrentModificationException`，这是 `fail-fast` 机制。如果需要边迭代边修改，考虑用 `ConcurrentLinkedDeque`。

---

## 2.7 本章总结

`ArrayDeque` 的设计围绕一个核心思路：**用循环数组在 O(1) 时间内支持两端的高效操作，同时避免频繁的内存分配**。

- **循环数组** 解决了普通数组出队后空间浪费的问题
- **2 的幂次方容量** 把取模变成位运算，每次操作节省了除法开销
- **懒扩容** 保证均摊 O(1) 的同时减少不必要的内存浪费
- **禁止 null** 用 null 作为哨兵，简化了队列空判断的实现

相比 `Stack`（多余的锁）和 `LinkedList`（多余的内存分配），`ArrayDeque` 在任何场景下都是更优的选择。
