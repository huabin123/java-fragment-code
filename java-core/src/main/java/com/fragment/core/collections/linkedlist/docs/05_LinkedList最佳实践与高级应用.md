# 第五章：LinkedList 最佳实践与高级应用

## 5.1 遍历的正确姿势

LinkedList 的遍历选择直接决定代码质量，从性能角度从高到低排列：

```java
// LinkedListPerformanceDemo.java → testIteration() 验证了以下结论

// ✅ 方式1：foreach（推荐，简洁，O(n)）
for (String item : list) {
    process(item);
}

// ✅ 方式2：Iterator（需要在遍历中删除时用）
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String item = it.next();
    if (shouldRemove(item)) it.remove();  // 安全删除
}

// ✅ 方式3：ListIterator（双向遍历或遍历中插入时用）
ListIterator<String> lit = list.listIterator(list.size()); // 从尾部开始
while (lit.hasPrevious()) {
    String item = lit.previous();
    if (needInsertAfter(item)) lit.add(newItem);  // O(1) 插入
}

// ✅ 方式4：Stream API（函数式，底层用迭代器）
list.stream().filter(s -> s.startsWith("A")).forEach(System.out::println);

// ❌ 绝对禁止：for + get(index)，O(n²)
for (int i = 0; i < list.size(); i++) {
    list.get(i);  // 每次都从头/尾遍历，灾难性性能
}
```

---

## 5.2 作为 Deque 的正确 API 选择

当用 LinkedList 实现栈、队列、双端队列时，选用返回特殊值（而非抛异常）的方法：

```java
// 队列（FIFO）
Deque<String> queue = new LinkedList<>();
queue.offerLast("A");    // 入队，等价于 offer()
queue.pollFirst();       // 出队，等价于 poll()，空时返回 null 而非抛异常
queue.peekFirst();       // 查看队首，等价于 peek()

// 栈（LIFO）
Deque<String> stack = new LinkedList<>();
stack.offerFirst("A");   // 压栈，等价于 push()
stack.pollFirst();       // 弹栈，等价于 pop()，但 pop() 空时抛异常
stack.peekFirst();       // 查看栈顶

// 双端队列
Deque<String> deque = new LinkedList<>();
deque.offerFirst("前");   // 从前端插入
deque.offerLast("后");    // 从后端插入
deque.pollFirst();        // 从前端删除
deque.pollLast();         // 从后端删除
```

---

## 5.3 浏览器历史记录的完整实现分析

`BrowserHistoryManager.java` 是 LinkedList 作为双端容器的典型实战案例：

```java
private final LinkedList<String> history = new LinkedList<>();
private int currentIndex = -1;
```

**为什么用 LinkedList 而不是 ArrayList？**

关键操作分析：
- `visit(url)`：可能调用 `removeLast()`（清除前进历史）和 `addLast(url)`，都是 O(1)
- `back()`：只改变 `currentIndex`，用 `history.get(currentIndex)`，O(n)
- `forward()`：同上，O(n)

坦诚地说，`BrowserHistoryManager` 中使用了 `history.get(currentIndex)` 按索引访问，这是 LinkedList 的弱项（O(n)）。更合适的实现应该用两个栈（`ArrayDeque`）分别存储后退历史和前进历史：

```java
// 更优化的两栈实现
Deque<String> backStack = new ArrayDeque<>();   // 后退历史
Deque<String> forwardStack = new ArrayDeque<>();// 前进历史

void visit(String url) {
    backStack.push(url);
    forwardStack.clear();  // 访问新页面，清除前进历史
}

String back() {
    if (backStack.size() <= 1) return backStack.peek();
    forwardStack.push(backStack.pop());  // 当前页移到前进栈
    return backStack.peek();            // 返回新的当前页
}

String forward() {
    if (forwardStack.isEmpty()) return backStack.peek();
    String page = forwardStack.pop();   // 前进栈弹出
    backStack.push(page);              // 压入后退栈
    return page;
}
```

这个两栈实现的所有操作都是 O(1)，是更正确的选型。

---

## 5.4 线程安全的 LinkedList 使用

LinkedList 本身不是线程安全的。根据使用场景选择替代方案：

**场景一：高并发生产者-消费者（推荐 LinkedBlockingQueue）**

```java
// 不要在多线程中直接使用 LinkedList + synchronized
// ✅ 使用 LinkedBlockingQueue（内部链表结构，线程安全，支持阻塞）
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(1000);

// 生产者
queue.put(task);     // 队列满时阻塞

// 消费者
Task task = queue.take();  // 队列空时阻塞
```

`TaskQueue.java` 中手动实现了基于 `synchronized` + `wait/notifyAll` 的阻塞队列，这是理解并发原语的好案例，但生产代码中直接用 `LinkedBlockingQueue`。

**场景二：需要线程安全 List 语义（用 CopyOnWriteArrayList 或 synchronizedList）**

```java
// 读多写少：CopyOnWriteArrayList（每次写都复制数组，读无锁）
List<String> list = new CopyOnWriteArrayList<>();

// 读写均衡：Collections.synchronizedList（每个方法加 synchronized）
List<String> list = Collections.synchronizedList(new LinkedList<>());
// 注意：遍历时需要手动 synchronized！
synchronized (list) {
    for (String s : list) { ... }
}
```

---

## 5.5 常见错误与修复

### 错误一：在 LinkedList 上用 for + get（O(n²)）

```java
// ❌ 错误
for (int i = 0; i < list.size(); i++) {
    String s = list.get(i);  // 每次 O(n)，总计 O(n²)
    process(s);
}

// ✅ 修复
for (String s : list) {  // foreach → iterator，O(n)
    process(s);
}
```

### 错误二：以为"链表中间插入一定快"

```java
// ❌ 错误认知
// linkedList.add(index, element) 是 O(1)？
// 实际：node(index) 是 O(n)，链接才是 O(1)

// ✅ 只有在已持有迭代器时才是 O(1)
ListIterator<E> it = list.listIterator();
while (it.hasNext()) {
    if (condition(it.next())) {
        it.add(element);  // 这才是真 O(1)
    }
}
```

### 错误三：声明为 LinkedList 而非接口类型

```java
// ❌ 过度暴露
LinkedList<String> list = new LinkedList<>();
list.get(100);  // O(n) 的危险调用，编译器不警告

// ✅ 限制到最小接口
Queue<String> queue = new LinkedList<>();  // 无法调用 get(index)
Deque<String> deque = new LinkedList<>();  // 无法调用 get(index)
```

声明为接口类型，编译器会阻止误用 O(n) 的随机访问方法。

### 错误四：removeIf 的错误使用（反面对比）

```java
// ❌ 遍历中直接 remove（ConcurrentModificationException）
for (String s : list) {
    if (s.isEmpty()) list.remove(s);
}

// ✅ 使用 removeIf（JDK 8+，迭代器安全删除）
list.removeIf(String::isEmpty);

// ✅ 或者 Iterator 手动删除
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().isEmpty()) it.remove();
}
```

---

## 5.6 LinkedList vs ArrayDeque：纯队列/栈场景的选择

当只需要队列或栈功能（不需要 List 接口）时，`ArrayDeque` 通常是更好的选择：

| 维度 | LinkedList | ArrayDeque |
|------|-----------|------------|
| 底层结构 | 链表节点 | 循环数组 |
| 头尾操作 | O(1) | O(1) |
| 内存效率 | 每节点 32 字节额外开销 | 连续数组，无额外开销 |
| CPU 缓存 | 不友好（分散节点） | 友好（连续内存） |
| null 元素 | ✅ 允许 | ❌ 不允许（NPE）|
| List 接口 | ✅ 实现 | ❌ 未实现 |

**结论**：纯队列/栈场景用 `ArrayDeque`；需要同时使用 List 接口或需要 null 元素时用 `LinkedList`。

---

## 5.7 本章总结

- **遍历铁律**：只用迭代器/foreach，禁止 for + get，否则 O(n²)
- **API 选择**：用 `offer/poll/peek` 系列，不用 `add/remove/element`（会抛异常）
- **中间插入 O(1) 的前提**：必须持有 `ListIterator`，否则仍然是 O(n)
- **线程安全**：生产代码用 `LinkedBlockingQueue`（队列）或 `CopyOnWriteArrayList`（列表）
- **类型声明**：声明为 `Queue`/`Deque` 接口，不要直接声明 `LinkedList`
- **纯队列/栈**：优先考虑 `ArrayDeque`，比 LinkedList 内存更紧凑、缓存更友好

> **本章对应演示代码**：`LinkedListAsQueueDemo.java`（队列操作最佳实践）、`BrowserHistoryManager.java`（实战场景）、`TaskQueue.java`（阻塞队列实现原理）

**返回目录**：[README.md](../../README.md)
