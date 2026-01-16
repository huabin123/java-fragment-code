# PriorityBlockingQueue详解

> **本章目标**：深入理解PriorityBlockingQueue的优先级队列实现、堆排序算法、动态扩容机制及应用场景

---

## 一、为什么需要PriorityBlockingQueue？

### 问题1：如何实现优先级任务调度？

#### 传统方案的问题

```java
// 方案1：普通队列 + 手动排序
BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
// 问题：无法保证优先级，先进先出

// 方案2：每次取出后排序
List<Task> tasks = new ArrayList<>();
Collections.sort(tasks);  // 每次都要排序，O(n log n)
```

#### PriorityBlockingQueue的解决方案

```java
// 自动按优先级排序
BlockingQueue<Task> queue = new PriorityBlockingQueue<>();

queue.put(new Task(5));  // 优先级5
queue.put(new Task(1));  // 优先级1（最高）
queue.put(new Task(3));  // 优先级3

Task task = queue.take();  // 自动取出优先级最高的（1）
```

**优势**：
- ✅ 自动排序（堆结构）
- ✅ 插入O(log n)，取最小O(1)
- ✅ 无界队列，自动扩容

---

### 问题2：PriorityBlockingQueue vs PriorityQueue

| 特性 | PriorityBlockingQueue | PriorityQueue |
|-----|----------------------|---------------|
| **线程安全** | ✅ | ❌ |
| **阻塞语义** | ✅ | ❌ |
| **容量** | 无界（自动扩容） | 无界（自动扩容） |
| **锁机制** | ReentrantLock | 无 |

---

## 二、核心数据结构

### 2.1 类定义

```java
public class PriorityBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    
    // 默认初始容量
    private static final int DEFAULT_INITIAL_CAPACITY = 11;
    
    // 最大容量
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    
    // 底层数组（堆）
    private transient Object[] queue;
    
    // 元素个数
    private transient int size;
    
    // 比较器
    private transient Comparator<? super E> comparator;
    
    // 全局锁
    private final ReentrantLock lock;
    
    // 非空条件
    private final Condition notEmpty;
    
    // 扩容锁（CAS）
    private transient volatile int allocationSpinLock;
}
```

### 2.2 堆结构

```
二叉堆（小顶堆）：

数组表示：[1, 3, 2, 7, 5, 4, 6]

树形表示：
        1
       / \
      3   2
     / \ / \
    7  5 4  6

性质：
- 父节点 <= 子节点（小顶堆）
- 完全二叉树
- 数组索引关系：
  - 父节点：(i-1)/2
  - 左子节点：2*i+1
  - 右子节点：2*i+2
```

---

## 三、核心操作流程

### 3.1 入队操作（offer）

#### 流程图

```
offer(E e) 流程
    │
    ▼
┌─────────────┐
│ 检查元素非空 │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取lock    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 检查容量     │
└──────┬──────┘
       │
   ┌───┴───┐
   │       │
  满了     未满
   │       │
   ▼       ▼
┌──────┐ ┌──────────┐
│扩容  │ │插入堆尾   │
│tryGrow│ │siftUp   │
└──┬───┘ └────┬─────┘
   │          │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ signal   │
   │(notEmpty)│
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ 释放lock  │
   └──────────┘
```

#### 源码分析

```java
public boolean offer(E e) {
    if (e == null)
        throw new NullPointerException();
    
    final ReentrantLock lock = this.lock;
    lock.lock();
    
    int n, cap;
    Object[] array;
    
    // 如果容量不足，扩容
    while ((n = size) >= (cap = (array = queue).length))
        tryGrow(array, cap);
    
    try {
        Comparator<? super E> cmp = comparator;
        
        // 插入堆尾，然后上浮
        if (cmp == null)
            siftUpComparable(n, e, array);
        else
            siftUpUsingComparator(n, e, array, cmp);
        
        size = n + 1;
        notEmpty.signal();  // 唤醒等待线程
    } finally {
        lock.unlock();
    }
    
    return true;
}

// 上浮操作（使用Comparable）
private static <T> void siftUpComparable(int k, T x, Object[] array) {
    Comparable<? super T> key = (Comparable<? super T>) x;
    
    while (k > 0) {
        int parent = (k - 1) >>> 1;  // 父节点索引
        Object e = array[parent];
        
        // 如果key >= 父节点，停止
        if (key.compareTo((T) e) >= 0)
            break;
        
        // 否则，父节点下移
        array[k] = e;
        k = parent;
    }
    
    array[k] = key;  // 插入最终位置
}
```

**上浮过程示例**：

```
插入元素2到堆[1, 3, 5, 7]

初始：
        1
       / \
      3   5
     /
    7

步骤1：插入到堆尾
        1
       / \
      3   5
     / \
    7   2

步骤2：与父节点3比较，2 < 3，交换
        1
       / \
      2   5
     / \
    7   3

步骤3：与父节点1比较，2 >= 1，停止

最终：
        1
       / \
      2   5
     / \
    7   3
```

---

### 3.2 扩容操作（tryGrow）

```java
private void tryGrow(Object[] array, int oldCap) {
    lock.unlock();  // 释放锁，允许take操作
    
    Object[] newArray = null;
    
    // CAS获取扩容权限
    if (allocationSpinLock == 0 &&
        UNSAFE.compareAndSwapInt(this, allocationSpinLockOffset, 0, 1)) {
        try {
            // 计算新容量
            int newCap = oldCap + ((oldCap < 64) ?
                                  (oldCap + 2) :  // 小容量：翻倍+2
                                  (oldCap >> 1)); // 大容量：增加50%
            
            // 检查溢出
            if (newCap - MAX_ARRAY_SIZE > 0) {
                int minCap = oldCap + 1;
                if (minCap < 0 || minCap > MAX_ARRAY_SIZE)
                    throw new OutOfMemoryError();
                newCap = MAX_ARRAY_SIZE;
            }
            
            // 创建新数组
            if (newArray == null && array == queue)
                newArray = new Object[newCap];
        } finally {
            allocationSpinLock = 0;  // 释放扩容权限
        }
    }
    
    // 如果其他线程在扩容，让出CPU
    if (newArray == null)
        Thread.yield();
    
    // 重新获取锁，复制数据
    lock.lock();
    if (newArray != null && queue == array) {
        queue = newArray;
        System.arraycopy(array, 0, newArray, 0, oldCap);
    }
}
```

**关键点**：

1. **为什么扩容时释放锁？**
```java
lock.unlock();  // 释放锁

// 原因：
// - 扩容可能耗时（创建大数组）
// - 释放锁允许take操作继续
// - 提高并发度
```

2. **CAS控制扩容**
```java
// 只有一个线程能扩容
if (allocationSpinLock == 0 &&
    UNSAFE.compareAndSwapInt(this, allocationSpinLockOffset, 0, 1)) {
    // 扩容逻辑
}
```

3. **扩容策略**
```java
// 小容量（<64）：翻倍+2
newCap = oldCap + oldCap + 2;

// 大容量（>=64）：增加50%
newCap = oldCap + (oldCap >> 1);
```

---

### 3.3 出队操作（poll/take）

```java
public E poll() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return dequeue();
    } finally {
        lock.unlock();
    }
}

public E take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    
    E result;
    try {
        // 如果队列空，等待
        while ((result = dequeue()) == null)
            notEmpty.await();
    } finally {
        lock.unlock();
    }
    
    return result;
}

// 出队核心逻辑
private E dequeue() {
    int n = size - 1;
    if (n < 0)
        return null;
    else {
        Object[] array = queue;
        E result = (E) array[0];  // 取堆顶
        
        E x = (E) array[n];  // 取堆尾
        array[n] = null;
        
        Comparator<? super E> cmp = comparator;
        
        // 堆尾元素下沉
        if (cmp == null)
            siftDownComparable(0, x, array, n);
        else
            siftDownUsingComparator(0, x, array, n, cmp);
        
        size = n;
        return result;
    }
}

// 下沉操作
private static <T> void siftDownComparable(int k, T x, Object[] array, int n) {
    if (n > 0) {
        Comparable<? super T> key = (Comparable<? super T>)x;
        int half = n >>> 1;  // 非叶子节点的最大索引
        
        while (k < half) {
            int child = (k << 1) + 1;  // 左子节点
            Object c = array[child];
            int right = child + 1;
            
            // 选择较小的子节点
            if (right < n &&
                ((Comparable<? super T>) c).compareTo((T) array[right]) > 0)
                c = array[child = right];
            
            // 如果key <= 子节点，停止
            if (key.compareTo((T) c) <= 0)
                break;
            
            // 否则，子节点上移
            array[k] = c;
            k = child;
        }
        
        array[k] = key;
    }
}
```

**下沉过程示例**：

```
从堆[1, 2, 5, 7, 3]中删除堆顶1

初始：
        1
       / \
      2   5
     / \
    7   3

步骤1：取出堆顶1，堆尾3移到堆顶
        3
       / \
      2   5
     /
    7

步骤2：3与子节点2、5比较，2最小，交换
        2
       / \
      3   5
     /
    7

步骤3：3与子节点7比较，3 < 7，停止

最终：
        2
       / \
      3   5
     /
    7
```

---

## 四、核心设计精髓

### 4.1 堆排序算法

#### 为什么用堆？

```java
// 堆的优势：
// - 插入：O(log n)
// - 取最小：O(1)
// - 删除最小：O(log n)

// 对比其他数据结构：
// 有序数组：插入O(n)，取最小O(1)
// 无序数组：插入O(1)，取最小O(n)
// 平衡树：插入O(log n)，取最小O(log n)
```

#### 堆的性质

```java
// 小顶堆性质：
// 1. 完全二叉树
// 2. 父节点 <= 子节点
// 3. 堆顶是最小元素

// 数组表示：
// parent(i) = (i-1)/2
// left(i) = 2*i+1
// right(i) = 2*i+2
```

---

### 4.2 扩容时释放锁

```java
private void tryGrow(Object[] array, int oldCap) {
    lock.unlock();  // 释放锁
    
    // 扩容逻辑（可能耗时）
    
    lock.lock();    // 重新获取锁
}
```

**为什么这样设计？**
- 扩容可能耗时（创建大数组）
- 释放锁允许take操作继续
- 提高并发度
- 使用CAS控制只有一个线程扩容

---

### 4.3 无notFull条件

```java
// 只有notEmpty，没有notFull
private final Condition notEmpty;

// 原因：无界队列，永不满
// put永不阻塞（除非OOM）
```

---

## 五、应用场景与最佳实践

### 5.1 优先级任务调度

```java
public class PriorityTaskScheduler {
    private final BlockingQueue<PriorityTask> taskQueue = 
        new PriorityBlockingQueue<>();
    
    // 提交任务
    public void submit(Runnable task, int priority) {
        taskQueue.offer(new PriorityTask(task, priority));
    }
    
    // 工作线程
    public void startWorker() {
        new Thread(() -> {
            while (true) {
                try {
                    PriorityTask task = taskQueue.take();
                    task.run();  // 执行高优先级任务
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}

class PriorityTask implements Comparable<PriorityTask>, Runnable {
    private final Runnable task;
    private final int priority;  // 数字越小优先级越高
    
    public PriorityTask(Runnable task, int priority) {
        this.task = task;
        this.priority = priority;
    }
    
    @Override
    public int compareTo(PriorityTask o) {
        return Integer.compare(this.priority, o.priority);
    }
    
    @Override
    public void run() {
        task.run();
    }
}
```

---

### 5.2 常见陷阱

#### 陷阱1：忘记实现Comparable

```java
// ❌ 错误：没有实现Comparable
class Task {
    private int priority;
}

PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>();
queue.offer(new Task());  // ClassCastException!

// ✅ 正确：实现Comparable或提供Comparator
class Task implements Comparable<Task> {
    private int priority;
    
    @Override
    public int compareTo(Task o) {
        return Integer.compare(this.priority, o.priority);
    }
}
```

#### 陷阱2：无界队列OOM

```java
// ❌ 危险：无界队列
PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>();
while (true) {
    queue.offer(new Task());  // 可能OOM
}

// ✅ 正确：监控队列大小
if (queue.size() < MAX_SIZE) {
    queue.offer(task);
}
```

---

## 六、总结

### 核心要点

1. **设计思想**：
   - 堆排序，自动按优先级排序
   - 无界队列，自动扩容
   - 单锁 + Condition

2. **关键实现**：
   - siftUp/siftDown维护堆性质
   - 扩容时释放锁，提高并发
   - CAS控制扩容权限

3. **适用场景**：
   - 优先级任务调度
   - 事件按优先级处理

4. **注意事项**：
   - 必须实现Comparable或提供Comparator
   - 无界队列，注意OOM
   - 插入O(log n)，不适合频繁插入

---

**下一章**：SynchronousQueue详解
