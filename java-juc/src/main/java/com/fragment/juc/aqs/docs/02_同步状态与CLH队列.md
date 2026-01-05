# 第二章：同步状态与CLH队列深度剖析

> **学习目标**：深入理解state的操作机制和CLH队列的实现原理

---

## 一、同步状态（state）深度剖析

### 1.1 state的设计哲学

#### 为什么是volatile int？

```java
public abstract class AbstractQueuedSynchronizer {
    /**
     * 同步状态
     * - volatile：保证可见性和有序性
     * - int：32位，足够表示大多数同步状态
     */
    private volatile int state;
}
```

**设计考量**：

1. **为什么是volatile？**
```
线程A修改state后，线程B必须立即看到最新值

没有volatile的问题：
Thread A: state = 1  ──┐
                       │ CPU缓存
Thread B: 读取state   │ 可能读到0（旧值）
                       └─ 导致错误判断
                       
有volatile：
Thread A: state = 1  ──> 立即刷新到主内存
Thread B: 读取state ──> 从主内存读取最新值
```

2. **为什么是int而不是long？**
```
int的优势：
✅ 32位操作，大多数平台原子性更好
✅ 足够表示常见场景（重入次数、许可数等）
✅ 可以拆分使用（如ReadWriteLock的高16位和低16位）

long的问题：
❌ 64位操作，某些平台不是原子的
❌ 占用更多内存
❌ 大多数场景用不到这么大的范围
```

3. **为什么不是boolean？**
```
boolean的局限：
❌ 只能表示两种状态（锁定/未锁定）
❌ 无法表示重入次数
❌ 无法表示许可数量
❌ 无法拆分使用

int的灵活性：
✅ ReentrantLock: state表示重入次数（0, 1, 2, ...）
✅ Semaphore: state表示许可数（0, 1, 2, ..., n）
✅ CountDownLatch: state表示倒计时（n, n-1, ..., 1, 0）
✅ ReadWriteLock: 高16位读锁，低16位写锁
```

---

### 1.2 state的三大操作

#### 操作1：getState() - 读取状态

```java
protected final int getState() {
    return state;
}
```

**为什么这么简单？**
- volatile保证了可见性，直接读取即可
- 不需要加锁，性能高

**使用场景**：
```java
// ReentrantLock判断是否可重入
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState(); // 读取当前状态
    
    if (c == 0) {
        // 锁空闲
    } else if (current == getExclusiveOwnerThread()) {
        // 可重入：当前线程已持有锁
    }
}
```

#### 操作2：setState() - 设置状态

```java
protected final void setState(int newState) {
    state = newState;
}
```

**何时使用？**
- 在已经持有锁的情况下修改状态
- 不需要CAS，因为没有竞争

**使用场景**：
```java
// ReentrantLock释放锁
protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    
    boolean free = false;
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c); // 直接设置，因为当前线程持有锁，没有竞争
    return free;
}
```

#### 操作3：compareAndSetState() - CAS更新

```java
protected final boolean compareAndSetState(int expect, int update) {
    // 使用Unsafe的CAS操作
    return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
}
```

**为什么需要CAS？**

```
场景：两个线程同时尝试获取锁

Thread A                    Thread B
读取state=0                 读取state=0
CAS(0->1) ✅               CAS(0->1) ❌ (state已经是1)
获取成功                    获取失败，进入队列

如果不用CAS：
Thread A: state = 1
Thread B: state = 1
结果：两个线程都认为自己获取了锁！❌
```

**CAS的工作原理**：

```java
// CAS的伪代码
boolean compareAndSwapInt(Object obj, long offset, int expect, int update) {
    synchronized (obj) { // 实际是CPU指令，这里用synchronized演示
        int current = getIntVolatile(obj, offset);
        if (current == expect) {
            putIntVolatile(obj, offset, update);
            return true;
        }
        return false;
    }
}
```

**使用场景**：
```java
// Semaphore获取许可
protected int tryAcquireShared(int acquires) {
    for (;;) {
        int available = getState();
        int remaining = available - acquires;
        
        // 许可不足，或者CAS成功
        if (remaining < 0 ||
            compareAndSetState(available, remaining))
            return remaining;
        // CAS失败，自旋重试
    }
}
```

---

### 1.3 state在不同同步器中的含义

#### 案例1：ReentrantLock - 重入次数

```java
// state的含义
state = 0  → 锁空闲
state = 1  → 锁被占用1次
state = 2  → 锁被重入2次
state = n  → 锁被重入n次

// 获取锁
protected final boolean tryAcquire(int acquires) {
    int c = getState();
    if (c == 0) {
        // 锁空闲，CAS获取
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(Thread.currentThread());
            return true;
        }
    } else if (Thread.currentThread() == getExclusiveOwnerThread()) {
        // 重入：state + 1
        int nextc = c + acquires;
        if (nextc < 0) // 溢出检查
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}

// 释放锁
protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    
    boolean free = false;
    if (c == 0) {
        // 完全释放
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}
```

**为什么需要记录重入次数？**

```java
// 场景：递归调用
public void methodA() {
    lock.lock();
    try {
        methodB(); // 递归调用
    } finally {
        lock.unlock();
    }
}

public void methodB() {
    lock.lock(); // 重入
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
}

// state的变化
初始: state = 0
methodA获取锁: state = 1
methodB重入: state = 2
methodB释放: state = 1
methodA释放: state = 0 (完全释放)
```

#### 案例2：Semaphore - 许可数

```java
// state的含义
state = n  → 还有n个许可可用
state = 0  → 许可已用完

// 获取许可
protected int tryAcquireShared(int acquires) {
    for (;;) {
        int available = getState(); // 可用许可数
        int remaining = available - acquires;
        
        if (remaining < 0 ||
            compareAndSetState(available, remaining))
            return remaining;
    }
}

// 释放许可
protected final boolean tryReleaseShared(int releases) {
    for (;;) {
        int current = getState();
        int next = current + releases;
        if (next < current) // 溢出检查
            throw new Error("Maximum permit count exceeded");
        if (compareAndSetState(current, next))
            return true;
    }
}
```

**示例**：
```java
Semaphore semaphore = new Semaphore(3); // state = 3

// Thread1获取1个许可
semaphore.acquire(); // state = 2

// Thread2获取2个许可
semaphore.acquire(2); // state = 0

// Thread3尝试获取，阻塞
semaphore.acquire(); // 阻塞，因为state = 0

// Thread1释放1个许可
semaphore.release(); // state = 1

// Thread3被唤醒，获取成功
// state = 0
```

#### 案例3：CountDownLatch - 倒计时

```java
// state的含义
state = n  → 还需要n次countDown
state = 0  → 倒计时完成

// 构造函数
public CountDownLatch(int count) {
    this.sync = new Sync(count);
}

private static final class Sync extends AbstractQueuedSynchronizer {
    Sync(int count) {
        setState(count); // 初始化倒计时
    }
    
    // await()调用
    protected int tryAcquireShared(int acquires) {
        return (getState() == 0) ? 1 : -1; // state=0时返回成功
    }
    
    // countDown()调用
    protected boolean tryReleaseShared(int releases) {
        for (;;) {
            int c = getState();
            if (c == 0)
                return false; // 已经是0，无需释放
            int nextc = c - 1;
            if (compareAndSetState(c, nextc))
                return nextc == 0; // 减到0时返回true，唤醒等待线程
        }
    }
}
```

**示例**：
```java
CountDownLatch latch = new CountDownLatch(3); // state = 3

// 主线程等待
latch.await(); // state != 0，阻塞

// 工作线程1完成
latch.countDown(); // state = 2

// 工作线程2完成
latch.countDown(); // state = 1

// 工作线程3完成
latch.countDown(); // state = 0，唤醒主线程
```

#### 案例4：ReentrantReadWriteLock - 拆分使用

```java
// state的高16位表示读锁，低16位表示写锁
// 
//  31        16 15         0
// ┌────────────┬────────────┐
// │  读锁计数   │  写锁计数   │
// └────────────┴────────────┘

static final int SHARED_SHIFT   = 16;
static final int SHARED_UNIT    = (1 << SHARED_SHIFT); // 0x00010000
static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1; // 0x0000FFFF
static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1; // 0x0000FFFF

// 获取读锁数量
static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }

// 获取写锁数量
static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

// 示例
state = 0x00000000  → 无锁
state = 0x00000001  → 1个写锁
state = 0x00010000  → 1个读锁
state = 0x00020000  → 2个读锁
state = 0x00020001  → 2个读锁 + 1个写锁（不可能，读写互斥）
```

**为什么这样设计？**

```
优势：
✅ 一个int同时表示读锁和写锁
✅ 节省内存
✅ 原子操作更简单

劣势：
❌ 读锁和写锁各最多65535次
❌ 位运算稍复杂
```

---

## 二、CLH队列深度剖析

### 2.1 为什么选择CLH队列？

#### 对比：其他队列方案

**方案1：数组队列**
```java
Thread[] queue = new Thread[100];
int head = 0, tail = 0;

// 问题：
❌ 固定大小，可能溢出
❌ 扩容复杂
❌ 删除中间节点困难
```

**方案2：单向链表**
```java
Node head -> Node1 -> Node2 -> Node3

// 问题：
❌ 删除节点需要从头遍历
❌ 无法快速找到前驱节点
❌ 取消节点效率低
```

**方案3：双向链表（CLH变种）**
```java
head <-> Node1 <-> Node2 <-> Node3 <-> tail

// 优势：
✅ 动态大小
✅ O(1)插入和删除
✅ 可以快速找到前驱和后继
✅ 取消节点高效
```

---

### 2.2 Node节点的结构

```java
static final class Node {
    // ========== 模式 ==========
    /** 共享模式标记 */
    static final Node SHARED = new Node();
    /** 独占模式标记 */
    static final Node EXCLUSIVE = null;
    
    // ========== 等待状态 ==========
    /** 节点已取消 */
    static final int CANCELLED =  1;
    /** 后继节点需要被唤醒 */
    static final int SIGNAL    = -1;
    /** 节点在条件队列中等待 */
    static final int CONDITION = -2;
    /** 共享模式下，释放需要传播 */
    static final int PROPAGATE = -3;
    
    /**
     * 等待状态，取值：
     * - SIGNAL:    后继节点需要唤醒
     * - CANCELLED: 节点已取消
     * - CONDITION: 在条件队列中
     * - PROPAGATE: 共享释放需要传播
     * - 0:         初始状态
     */
    volatile int waitStatus;
    
    // ========== 链表指针 ==========
    /** 前驱节点 */
    volatile Node prev;
    /** 后继节点 */
    volatile Node next;
    
    // ========== 线程信息 ==========
    /** 等待的线程 */
    volatile Thread thread;
    
    // ========== 条件队列 ==========
    /** 条件队列的下一个节点，或者SHARED标记 */
    Node nextWaiter;
}
```

#### waitStatus的状态转换

```
初始状态：0
    ↓
SIGNAL(-1)：前驱节点设置，表示需要唤醒后继
    ↓
0：被唤醒后，重置为0
    ↓
CANCELLED(1)：超时或中断，取消等待

特殊状态：
CONDITION(-2)：在条件队列中
PROPAGATE(-3)：共享模式下传播
```

**为什么需要waitStatus？**

```java
// 场景：释放锁时，如何知道是否需要唤醒后继节点？

// 没有waitStatus：
public void unlock() {
    // 总是尝试唤醒，即使后继节点不需要
    LockSupport.unpark(head.next.thread); // 可能浪费
}

// 有waitStatus：
public void unlock() {
    if (head.waitStatus == SIGNAL) {
        // 只有在需要时才唤醒
        LockSupport.unpark(head.next.thread);
    }
}
```

---

### 2.3 队列的初始化

#### 懒初始化策略

```java
private transient volatile Node head;
private transient volatile Node tail;

// 队列初始时，head和tail都是null
// 第一个线程竞争失败时，才初始化队列
```

**为什么懒初始化？**

```
场景1：无竞争
Thread A获取锁成功 → 不需要队列 → 节省内存

场景2：有竞争
Thread A持有锁
Thread B竞争失败 → 初始化队列 → 加入队列
```

#### 初始化过程

```java
private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) { // 队列未初始化
            // 创建哨兵节点
            if (compareAndSetHead(new Node()))
                tail = head; // head和tail指向同一个哨兵节点
        } else {
            // 队列已初始化，加入队尾
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

**初始化过程图解**：

```
1. 初始状态
head = null
tail = null

2. 第一个节点入队
   ┌────┐
   │哨兵│
   └────┘
    ↑  ↑
   head tail

3. 第二个节点入队
   ┌────┐    ┌────┐
   │哨兵│ ←→ │Node│
   └────┘    └────┘
    ↑              ↑
   head           tail

4. 第三个节点入队
   ┌────┐    ┌────┐    ┌────┐
   │哨兵│ ←→ │Node│ ←→ │Node│
   └────┘    └────┘    └────┘
    ↑                        ↑
   head                     tail
```

**为什么需要哨兵节点？**

```
没有哨兵节点的问题：
head -> Thread1 -> Thread2

Thread1获取锁成功，出队：
head -> Thread2

问题：head既是队列头，又是持有锁的线程，逻辑混乱

有哨兵节点：
head(哨兵) -> Thread1 -> Thread2

Thread1获取锁成功：
head -> Thread1(新哨兵) -> Thread2

优势：
✅ head始终是哨兵，不代表任何线程
✅ head.next才是第一个等待的线程
✅ 逻辑清晰，代码简洁
```

---

### 2.4 入队操作

#### 完整的入队流程

```java
private Node addWaiter(Node mode) {
    // 1. 创建节点
    Node node = new Node(Thread.currentThread(), mode);
    
    // 2. 快速尝试入队（优化）
    Node pred = tail;
    if (pred != null) {
        node.prev = pred;
        if (compareAndSetTail(pred, node)) {
            pred.next = node;
            return node;
        }
    }
    
    // 3. 快速入队失败，进入完整入队流程
    enq(node);
    return node;
}

private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) {
            // 初始化队列
            if (compareAndSetHead(new Node()))
                tail = head;
        } else {
            // 入队
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

**为什么分两步？**

```
快速路径（addWaiter）：
- 队列已初始化
- 一次CAS成功
- 性能最优

慢速路径（enq）：
- 队列未初始化，需要创建哨兵
- CAS可能失败，需要自旋重试
- 保证最终成功
```

#### 入队的并发安全性

```java
// 关键：先设置prev，再CAS tail，最后设置next

node.prev = t;                    // 1. 先建立prev链接
if (compareAndSetTail(t, node)) { // 2. CAS设置tail
    t.next = node;                // 3. 最后建立next链接
    return t;
}
```

**为什么这个顺序？**

```
场景：Thread A和Thread B同时入队

Thread A                    Thread B
node1.prev = tail          node2.prev = tail
CAS(tail, node1) ✅       CAS(tail, node2) ❌
tail.next = node1          重试...

关键点：
1. prev先设置，保证从tail向前遍历总是能找到所有节点
2. CAS保证只有一个线程成功
3. next后设置，即使暂时断开也不影响（可以通过prev遍历）
```

**为什么从tail向前遍历更安全？**

```java
// 场景：取消节点时，从后向前遍历
private void cancelAcquire(Node node) {
    // 从node向前找到第一个非取消节点
    Node pred = node.prev;
    while (pred.waitStatus > 0)
        pred = pred.prev; // 向前遍历，总是安全的
    
    // 因为prev总是先设置，所以向前遍历不会丢失节点
}
```

---

### 2.5 出队操作

#### 获取锁成功后的出队

```java
private void setHead(Node node) {
    head = node;
    node.thread = null; // 清空线程引用
    node.prev = null;   // 清空prev引用
}

// 使用场景
final boolean acquireQueued(final Node node, int arg) {
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                // 获取成功，出队
                setHead(node);
                p.next = null; // 帮助GC
                return false;
            }
            // ...
        }
    } catch (Throwable t) {
        // ...
    }
}
```

**出队过程图解**：

```
1. 初始状态
   ┌────┐    ┌────┐    ┌────┐
   │哨兵│ ←→ │Node│ ←→ │Node│
   └────┘    └────┘    └────┘
    ↑                        ↑
   head                     tail
   
2. Node1获取锁成功
   setHead(Node1)
   
   ┌────┐    ┌────┐
   │Node│ ←→ │Node│
   └────┘    └────┘
    ↑              ↑
   head           tail
   
   原来的哨兵被GC回收
   Node1成为新的哨兵
```

**为什么要清空thread和prev？**

```
1. thread = null
   - Node1已经获取锁，不再需要thread引用
   - 帮助GC回收Thread对象

2. prev = null
   - Node1成为新的head，不需要prev
   - 断开与旧head的链接，帮助GC
```

---

### 2.6 节点取消

#### 取消的场景

```java
// 场景1：中断
lock.lockInterruptibly();
// 等待过程中被中断 → 取消节点

// 场景2：超时
lock.tryLock(1, TimeUnit.SECONDS);
// 超时未获取到锁 → 取消节点
```

#### 取消的实现

```java
private void cancelAcquire(Node node) {
    if (node == null)
        return;
    
    // 1. 清空线程引用
    node.thread = null;
    
    // 2. 跳过前面已取消的节点
    Node pred = node.prev;
    while (pred.waitStatus > 0)
        pred = pred.prev;
    
    Node predNext = pred.next;
    
    // 3. 设置为CANCELLED状态
    node.waitStatus = Node.CANCELLED;
    
    // 4. 如果是tail，尝试移除
    if (node == tail && compareAndSetTail(node, pred)) {
        compareAndSetNext(pred, predNext, null);
    } else {
        // 5. 不是tail，设置pred的waitStatus为SIGNAL
        int ws;
        if (pred != head &&
            ((ws = pred.waitStatus) == Node.SIGNAL ||
             (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
            pred.thread != null) {
            Node next = node.next;
            if (next != null && next.waitStatus <= 0)
                compareAndSetNext(pred, predNext, next);
        } else {
            // pred是head，唤醒后继节点
            unparkSuccessor(node);
        }
        
        node.next = node; // 帮助GC
    }
}
```

**取消过程图解**：

```
1. 初始状态
   head ←→ Node1 ←→ Node2 ←→ Node3 ←→ tail
   
2. Node2超时，取消
   Node2.waitStatus = CANCELLED
   
3. 跳过Node2，连接Node1和Node3
   head ←→ Node1 ←→ Node3 ←→ tail
                ↑
              Node2（孤立，等待GC）
```

---

## 三、state和CLH队列的协作

### 3.1 完整的获取流程

```java
public final void acquire(int arg) {
    // 1. 尝试获取（修改state）
    if (!tryAcquire(arg)) {
        // 2. 获取失败，加入队列
        Node node = addWaiter(Node.EXCLUSIVE);
        // 3. 在队列中等待
        acquireQueued(node, arg);
    }
}

final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            // 4. 如果是第一个节点，再次尝试获取
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null;
                failed = false;
                return interrupted;
            }
            // 5. 判断是否需要阻塞
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

**流程图**：

```
开始
  ↓
tryAcquire() ──成功──> 返回
  ↓ 失败
addWaiter() ──> 加入队列
  ↓
acquireQueued()
  ↓
是第一个节点？
  ├─是─> tryAcquire() ──成功──> 出队，返回
  │        ↓ 失败
  │      阻塞
  │        ↓
  │      被唤醒 ──> 重新尝试
  │
  └─否─> 阻塞
           ↓
         被唤醒 ──> 重新尝试
```

---

### 3.2 完整的释放流程

```java
public final boolean release(int arg) {
    // 1. 尝试释放（修改state）
    if (tryRelease(arg)) {
        Node h = head;
        // 2. 唤醒后继节点
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

private void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);
    
    // 找到下一个需要唤醒的节点
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        // 从后向前找第一个非取消节点
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }
    if (s != null)
        LockSupport.unpark(s.thread);
}
```

---

## 四、总结

### 4.1 state的核心要点

1. **volatile int**：保证可见性，足够灵活
2. **三大操作**：getState、setState、compareAndSetState
3. **含义由子类定义**：重入次数、许可数、倒计时等
4. **可拆分使用**：ReadWriteLock的高低16位

### 4.2 CLH队列的核心要点

1. **双向链表**：支持O(1)插入删除
2. **哨兵节点**：简化逻辑
3. **懒初始化**：节省内存
4. **waitStatus**：标记节点状态
5. **从后向前遍历**：更安全

### 4.3 思考题

1. **为什么入队时先设置prev，再CAS tail，最后设置next？**
2. **为什么取消节点时要从后向前遍历？**
3. **为什么需要哨兵节点？**
4. **如果state溢出会怎样？**

---

**下一章预告**：我们将深入分析独占模式的完整源码实现。
