# 第三章：PriorityQueue 堆结构与应用

> **对应演示代码**：`demo/PriorityQueueDemo.java`

## 3.1 为什么需要优先级队列？

普通队列按"先到先得"处理任务，但现实中任务往往有轻重缓急。

考虑一个运维告警系统：同时收到"磁盘使用率 85%"（低优先级）和"服务 OOM 崩溃"（紧急）两条告警。普通队列会按接收顺序处理，告警风暴时，紧急事件可能排在几百条低级告警后面，错过了最佳处置时机。

**优先级队列解决的核心问题**：在任何时刻，都能以 O(log n) 的代价取出当前权重最高的元素，而不需要对所有元素排序（O(n log n)）。

---

## 3.2 底层数据结构：二叉堆

`PriorityQueue` 的底层是一个**二叉最小堆**（Binary Min-Heap），存储在一个普通数组中。

### 3.2.1 堆的核心性质

**堆序性质（Heap Property）**：对于最小堆，每个节点的值都**小于等于**其子节点的值。这保证了根节点（数组索引 0）始终是整个堆中最小的元素。

**形状性质（Shape Property）**：二叉堆是一棵**完全二叉树**——除最后一层外，其他层全部填满；最后一层从左往右连续填入。完全二叉树可以用数组高效表示，无需指针。

### 3.2.2 数组与树的对应关系

这是理解堆操作的关键：

```
逻辑树形式：
         1         ← 索引 0（根，最小值）
       /   \
      3     2      ← 索引 1, 2
     / \   / \
    5   4 8   6   ← 索引 3, 4, 5, 6

数组存储：[1, 3, 2, 5, 4, 8, 6]
          0  1  2  3  4  5  6

父子关系（设当前节点索引为 i）：
  父节点索引 = (i - 1) / 2
  左子节点索引 = 2 * i + 1
  右子节点索引 = 2 * i + 2
```

这种映射关系使得在数组中导航树结构只需要简单的整数运算，不需要额外的指针存储，内存极度紧凑。

---

## 3.3 核心操作的原理：上浮与下沉

所有堆操作都基于两个基本过程：**上浮（Sift Up）** 和 **下沉（Sift Down）**。

### 3.3.1 入队（offer）：上浮

当插入新元素时，先把它放到数组末尾（完全二叉树的最后一个位置），然后与父节点比较，如果比父节点小就交换，重复直到满足堆序性质。

```
插入 0 到堆 [1, 3, 2, 5, 4, 8, 6]：

Step 1：放到末尾
  [1, 3, 2, 5, 4, 8, 6, 0]
                          ↑ 新插入，索引 7，父节点索引 = (7-1)/2 = 3，值为 5

Step 2：0 < 5，交换
  [1, 3, 2, 0, 4, 8, 6, 5]
              ↑ 新位置索引 3，父节点索引 = (3-1)/2 = 1，值为 3

Step 3：0 < 3，交换
  [1, 0, 2, 3, 4, 8, 6, 5]
      ↑ 新位置索引 1，父节点索引 = (1-1)/2 = 0，值为 1

Step 4：0 < 1，交换
  [0, 1, 2, 3, 4, 8, 6, 5]
   ↑ 已到根节点，结束
```

上浮的时间复杂度 = 树的高度 = **O(log n)**

### 3.3.2 出队（poll）：下沉

`poll()` 始终取走根节点（最小值）。直接删除根节点会破坏完全二叉树的形状，因此采用以下策略：

1. 取走根节点（结果）
2. 把**最后一个元素**移到根节点位置（保持完全二叉树形状）
3. 从根节点开始下沉：与两个子节点中较小的那个比较，如果比它大就交换，重复直到满足堆序性质

```
对堆 [0, 1, 2, 3, 4, 8, 6, 5] 执行 poll()：

Step 1：取走根节点 0（返回给调用方）

Step 2：把最后元素 5 移到根
  [5, 1, 2, 3, 4, 8, 6]
   ↑ 子节点：索引 1(值1) 和 索引 2(值2)，较小的是 1

Step 3：5 > 1，与左子节点交换
  [1, 5, 2, 3, 4, 8, 6]
      ↑ 子节点：索引 3(值3) 和 索引 4(值4)，较小的是 3

Step 4：5 > 3，与左子节点交换
  [1, 3, 2, 5, 4, 8, 6]
              ↑ 索引 3 的子节点：索引 7 和 8，均超出数组范围，结束
```

下沉的时间复杂度也是 **O(log n)**。

> **对应代码**：`PriorityQueueDemo.java → naturalOrdering()` 演示了 `offer` 和 `poll` 的行为。可以注意到：无序插入 5, 1, 3, 2, 4 后，`poll()` 的输出顺序是 1, 2, 3, 4, 5——这正是堆每次 `poll` 后重新 `sift down` 维护堆序性质的结果。

---

## 3.4 迭代器不保证顺序：一个容易踩的坑

这是 `PriorityQueue` 最容易被误解的特性：

```java
// 对应代码：PriorityQueueDemo.java → naturalOrdering() 中的 forEach 演示

PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(5); pq.offer(1); pq.offer(3);

// ❌ 错误理解：以为 forEach 按优先级输出
pq.forEach(e -> System.out.print(e + " "));  // 可能输出 1 5 3（不保证顺序！）

// ✅ 正确做法：只有 poll() 才保证按优先级出队
while (!pq.isEmpty()) {
    System.out.print(pq.poll() + " ");  // 输出 1 3 5（保证从小到大）
}
```

**为什么迭代器不保证顺序？**

堆是一棵树，数组中的存储顺序只保证父子关系（父 ≤ 子），**不保证兄弟节点之间的顺序**。例如堆 `[1, 3, 2, 5, 4, 8, 6]` 中，索引 1（值3）和索引 2（值2）是兄弟节点，3 > 2，但迭代器按数组顺序访问，会先输出 3 再输出 2。

只有通过 `poll()` 触发 `sift down` 操作，才能每次都精确地找到当前最小值。

---

## 3.5 最大堆：通过 Comparator 反转优先级

`PriorityQueue` 默认是最小堆（`poll()` 返回最小值）。通过传入反向的 `Comparator`，可以变成最大堆。

```java
// 对应代码：PriorityQueueDemo.java → maxHeap()

// 方式1：Comparator.reverseOrder()
PriorityQueue<Integer> maxPQ = new PriorityQueue<>(Comparator.reverseOrder());

// 方式2：Collections.reverseOrder()（等价，JDK 早期风格）
PriorityQueue<Integer> maxPQ2 = new PriorityQueue<>(Collections.reverseOrder());
```

**原理**：`PriorityQueue` 内部的 `sift up/down` 操作使用 `Comparator` 来比较大小。当 `Comparator` 被反转后，原来"小的优先"变成"大的优先"，堆的形状和操作逻辑完全不变，只是比较的方向变了。

**自定义对象的两种排序方式**（代码见 `PriorityQueueDemo.java → objectOrdering()`）：

```java
// 方式1：元素实现 Comparable 接口（侵入式，但语义清晰）
class Job implements Comparable<Job> {
    int priority;
    @Override
    public int compareTo(Job other) {
        return Integer.compare(this.priority, other.priority);
    }
}
PriorityQueue<Job> pq = new PriorityQueue<>();

// 方式2：构造时传入 Comparator（非侵入式，更灵活）
PriorityQueue<Task> pq = new PriorityQueue<>(Comparator.comparingInt(t -> t.priority));

// 多字段排序：优先级相同时按名称
PriorityQueue<Task> pq2 = new PriorityQueue<>(
    Comparator.comparingInt((Task t) -> t.priority)
              .thenComparing(t -> t.name)
);
```

`Comparator` 方式更推荐：不需要修改原始类，在不同场景可以使用不同的排序规则。

---

## 3.6 TopK 问题：堆的经典应用

> **对应代码**：`PriorityQueueDemo.java → topKProblem()`

TopK 是面试中最高频的堆应用题。核心思想是：**用一个大小为 K 的堆作为"候选集"，一次遍历完成筛选**。

### 3.6.1 求最大的 K 个数：用最小堆

直觉上会想到：找最大的，应该用最大堆。但实际上应该**用最小堆**，原因是：

最小堆的堆顶是当前 K 个候选中最小的那个，它是"淘汰线"——新元素只有比这个淘汰线更大，才有资格进入 TopK 候选集：

```java
// 对应代码：PriorityQueueDemo.java → topKProblem()

int[] nums = {3, 2, 1, 5, 6, 4, 8, 7, 9, 10, 0};
int k = 4;

PriorityQueue<Integer> minHeap = new PriorityQueue<>(k);

for (int num : nums) {
    if (minHeap.size() < k) {
        minHeap.offer(num);          // 前 k 个直接放入
    } else if (num > minHeap.peek()) { // 新元素 > 堆顶（淘汰线）
        minHeap.poll();              // 淘汰当前最小值
        minHeap.offer(num);          // 放入新的更大值
    }
    // 新元素 ≤ 堆顶：直接忽略，它进不了 TopK
}
// 堆中剩余的 k 个元素就是答案
```

**逐步演示**（k=4，数组 [3,2,1,5,6,4,8,7,9,10,0]）：

```
遍历 3：堆=[3]
遍历 2：堆=[2,3]
遍历 1：堆=[1,3,2]
遍历 5：堆=[1,3,2,5]（堆满）
遍历 6：6 > 堆顶1，poll 1，offer 6 → 堆=[2,3,5,6]
遍历 4：4 > 堆顶2，poll 2，offer 4 → 堆=[3,4,5,6]
遍历 8：8 > 堆顶3，poll 3，offer 8 → 堆=[4,5,6,8]
遍历 7：7 > 堆顶4，poll 4，offer 7 → 堆=[5,6,7,8]
遍历 9：9 > 堆顶5，poll 5，offer 9 → 堆=[6,7,8,9]
遍历 10：10 > 堆顶6，poll 6，offer 10 → 堆=[7,8,9,10]
遍历 0：0 ≤ 堆顶7，忽略
最终堆：[7, 8, 9, 10]  ← 就是最大的 4 个数
```

### 3.6.2 复杂度分析

| 方法 | 时间复杂度 | 空间复杂度 |
|------|----------|----------|
| 全量排序后取前 K | O(n log n) | O(n) |
| **最小堆维护 K 个候选** | **O(n log k)** | **O(k)** |

当 n=1000000，k=100 时：
- 排序：1000000 × 20 = 2000 万次操作
- 堆法：1000000 × 7 ≈ 700 万次操作，且只需 100 个元素的空间

### 3.6.3 求最小的 K 个数：用最大堆

对称地，求最小的 K 个数使用最大堆（堆顶是当前候选中最大的，即"淘汰线"）：

```java
// 对应代码：PriorityQueueDemo.java → topKProblem() 变体部分

PriorityQueue<Integer> maxHeap = new PriorityQueue<>(k, Comparator.reverseOrder());
for (int num : nums) {
    if (maxHeap.size() < k) {
        maxHeap.offer(num);
    } else if (num < maxHeap.peek()) {  // 新元素比淘汰线更小
        maxHeap.poll();
        maxHeap.offer(num);
    }
}
```

**规律总结**：
- 求最大 K 个 → 最小堆（堆顶是 TopK 的"守门员"，淘汰不够大的）
- 求最小 K 个 → 最大堆（堆顶是 BottomK 的"守门员"，淘汰不够小的）

---

## 3.7 扩展：其他堆的应用场景

| 场景 | 堆的使用方式 |
|------|------------|
| **Dijkstra 最短路径** | 最小堆存储（距离, 节点），每次取出最近的未访问节点 |
| **合并 K 个有序链表** | 最小堆维护 K 个链表的当前头节点，每次取最小值后推入该链表下一个节点 |
| **中位数维护** | 两个堆：最大堆存左半部分，最小堆存右半部分，动态维护平衡 |
| **任务调度** | 最小堆按执行时间排序，定时检查堆顶是否到期（`DelayQueue` 的基础原理） |

---

## 3.8 PriorityQueue 的限制

**不支持随机删除的高效操作**

`PriorityQueue` 的 `remove(Object)` 方法需要先遍历数组找到元素（O(n)），再做一次 `sift down`（O(log n)），总体 O(n)。如果需要高效的随机删除，应考虑使用 `TreeSet`（基于红黑树，删除 O(log n)）。

**不是线程安全的**

多线程场景需要使用 `PriorityBlockingQueue`（对应 `BlockingQueueDemo.java → priorityBlockingQueueDemo()`）。`PriorityBlockingQueue` 和 `PriorityQueue` 使用相同的堆实现，区别仅在于加了 `ReentrantLock` 保护。

---

## 3.9 本章总结

`PriorityQueue` 的核心是二叉最小堆：

- **完全二叉树用数组存储**：父子关系通过 `(i-1)/2`、`2i+1`、`2i+2` 计算，无需指针
- **上浮（入队）和下沉（出队）**：均为 O(log n)，是堆所有操作的基础
- **迭代器不保证顺序**：堆的数组存储只保证父子关系，不保证全局有序；要有序输出必须反复 `poll()`
- **TopK 的反直觉技巧**：求最大 K 个用最小堆（不是最大堆），堆顶作为淘汰线是关键思路
