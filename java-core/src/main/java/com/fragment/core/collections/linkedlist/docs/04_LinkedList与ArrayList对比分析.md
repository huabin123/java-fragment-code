# 第四章：LinkedList 与 ArrayList 对比分析

## 4.1 两种结构的本质差异

理解 LinkedList vs ArrayList 的选择，核心是理解**内存布局的不同**如何影响所有操作的性能。

| 维度 | ArrayList | LinkedList |
|------|-----------|------------|
| 底层结构 | 连续内存数组 | 分散堆对象链 |
| 随机访问 | O(1)：下标直接计算地址 | O(n)：从头/尾遍历 |
| 头部插入 | O(n)：后移所有元素 | O(1)：修改两个指针 |
| 尾部插入 | 均摊 O(1)：扩容时 O(n) | O(1)：last 指针直达 |
| 中间插入 | O(n)：数据移动（缓存友好）| O(n)：遍历定位（缓存不友好）|
| 按值删除 | O(n)：遍历 + 数据移动 | O(n)：遍历 |
| 内存开销 | 紧凑，元素引用连续存放 | 每节点额外 16-24 字节 |
| CPU 缓存 | 友好（连续内存）| 不友好（随机分散）|

---

## 4.2 头部插入：LinkedList 的真实优势场景

`LinkedListPerformanceDemo.java → testHeadInsert()` 是最能体现 LinkedList 优势的测试：

```java
// ArrayList 头部插入：每次都要把所有元素右移一位
list.add(0, i);  // 第 i 次插入：移动 i 个元素

// LinkedList 头部插入：只修改 2 个指针
linkedList.addFirst(i);  // 恒定时间
```

**实测（10000次头部插入）**：ArrayList 约 50-100ms，LinkedList 约 1-2ms，**差距约 50 倍**。

这是 O(n²) vs O(n) 的数量级差距：ArrayList 的 n 次头部插入总共移动了 `0+1+2+...+(n-1) = n(n-1)/2` 个元素。

---

## 4.3 中间插入：反直觉的结论

直觉上认为 LinkedList 中间插入应该更快（只需修改指针，不需要移动数据），但 `LinkedListPerformanceDemo.java → testMiddleInsert()` 显示：**中间插入场景 ArrayList 往往更快**。

原因：
1. **LinkedList 需要先遍历到中间位置**：`add(size/2, e)` 的代价是 `node(size/2)` 遍历 O(n) + 指针修改 O(1)，前者占主要开销
2. **ArrayList 的数据移动是 CPU 内存拷贝（`System.arraycopy`）**：这是一个高度优化的本地操作，配合 CPU 缓存效率极高
3. **内存访问模式**：数组连续内存 vs 链表分散节点，CPU 缓存行的命中率完全不同

```
实测（10000次 size/2 位置插入）：
ArrayList: ~10ms  （System.arraycopy 高度优化）
LinkedList: ~80ms （每次遍历到中间）

结论：LinkedList 中间插入并不是"O(1)"，而是 O(n) + 小常数！
```

**什么情况下 LinkedList 中间插入确实更快？**  
当你**已经持有迭代器（ListIterator）**，就不需要遍历定位，指针修改是真正的 O(1)：

```java
ListIterator<Integer> it = linkedList.listIterator();
while (it.hasNext()) {
    int val = it.next();
    if (needInsertBefore(val)) {
        it.add(newElement);  // O(1)！迭代器持有当前节点引用
    }
}
```

---

## 4.4 遍历：隐藏的性能陷阱

`LinkedListPerformanceDemo.java → testIteration()` 展示了最重要的遍历性能对比：

```
10万元素遍历（实测）：

ArrayList for循环(get):   ~1ms      O(n)，下标直接访问
ArrayList foreach:        ~1ms      O(n)，迭代器步进
LinkedList for循环(get):  ~5000ms   O(n²)！每次 get 遍历
LinkedList foreach:       ~3ms      O(n)，迭代器步进
```

**LinkedList for循环+get 是 O(n²)** 的原因：每次 `get(i)` 都从 first 或 last 遍历 O(n)，n 次调用总计 O(n²)。

即使用正确的迭代器遍历，LinkedList 仍然比 ArrayList 慢约 3-5 倍，原因是**CPU 缓存命中率**：
- ArrayList：元素引用连续存放，CPU 预读缓存行高效
- LinkedList：节点散布堆内存，每个 `x = x.next` 几乎都是一次缓存缺失

---

## 4.5 内存占用对比

```java
// 存储 100万个 Integer 对象的内存对比

// ArrayList<Integer>：
// - Integer 对象：100万 × 16 字节 = 16MB
// - Object[] 引用数组：100万 × 4 字节 = 4MB（压缩指针）
// 合计：约 20MB

// LinkedList<Integer>：
// - Integer 对象：100万 × 16 字节 = 16MB
// - Node 对象：100万 × 32 字节 = 32MB（额外！）
// 合计：约 48MB，是 ArrayList 的 2.4 倍
```

在内存受限场景（嵌入式、大数据处理），这个差距不可忽视。

---

## 4.6 选型决策：什么时候用 LinkedList？

根据上面的分析，LinkedList 相对于 ArrayList 真正有优势的场景只有：

**✅ 高频头部/尾部操作（无需中间访问）**
```java
// 作为队列：只用 offer/poll，不用 get(index)
Queue<Task> taskQueue = new LinkedList<>();
// 作为栈：只用 push/pop
Deque<String> stack = new LinkedList<>();
```

**✅ 已有迭代器位置，进行本地插入/删除**
```java
// 遍历链表并在满足条件的位置插入，不需要重新定位
ListIterator<E> it = list.listIterator();
while (it.hasNext()) {
    if (condition(it.next())) it.add(element);  // 真正的 O(1)
}
```

**❌ 以下场景一律用 ArrayList**
- 随机访问（get/set by index）
- 主要是尾部添加
- 频繁遍历
- 内存敏感场景
- 中间插入（除非持有迭代器）

> **现实中，纯队列/栈场景更推荐 `ArrayDeque`**：它是基于循环数组的双端队列，两端操作 O(1)，无额外节点开销，缓存友好，性能通常优于 LinkedList。LinkedList 的主要优势在于同时需要 List 和 Deque 接口、或需要迭代器中间插入的场景。

---

## 4.7 本章总结

- **头部插入**：LinkedList O(1) vs ArrayList O(n²)，差距显著（50倍+）
- **中间插入**：LinkedList 实际 O(n)（定位开销）+ 缓存不友好，ArrayList 通常更快
- **随机访问**：ArrayList O(1) vs LinkedList O(n)，差距数百倍
- **遍历**：LinkedList 必须用迭代器；for+get 是 O(n²) 灾难
- **内存**：LinkedList 每节点额外 32 字节，内存占用约 2-3 倍
- **真实优势**：只有在高频两端操作（且已有迭代器做中间插入）时 LinkedList 才有优势

> **本章对应演示代码**：`LinkedListPerformanceDemo.java`（5 项全面性能对比，数据直观）

**继续阅读**：[05_LinkedList最佳实践与高级应用.md](./05_LinkedList最佳实践与高级应用.md)
