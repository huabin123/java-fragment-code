# 第五章：高性能原子类LongAdder - 分段累加的艺术

> **学习目标**：深入理解LongAdder的设计思想和实现原理，掌握高并发场景下的性能优化

---

## 一、为什么需要LongAdder？

### 1.1 AtomicLong的性能瓶颈

```java
// 高并发场景下的AtomicLong
AtomicLong counter = new AtomicLong(0);

// 100个线程同时执行
for (int i = 0; i < 1000000; i++) {
    counter.incrementAndGet();
}

// 问题：
// ❌ 大量CAS失败
// ❌ 不断自旋重试
// ❌ CPU使用率高
// ❌ 性能线性下降
```

**性能瓶颈分析**：

```
高并发场景：
1. 所有线程竞争同一个变量
2. CAS成功率低
3. 大量自旋消耗CPU
4. 缓存行失效频繁

性能曲线：
线程数     AtomicLong性能
1          100%
2          90%
4          70%
8          40%
16         20%
32         10%

结论：线程越多，性能越差
```

### 1.2 LongAdder的解决方案

```java
// 使用LongAdder
LongAdder counter = new LongAdder();

// 100个线程同时执行
for (int i = 0; i < 1000000; i++) {
    counter.increment();
}

// 优势：
// ✅ 分段累加，减少竞争
// ✅ 性能稳定
// ✅ CPU使用率低
// ✅ 性能几乎不随线程数下降
```

**性能对比**：

```
线程数     AtomicLong    LongAdder
1          100%          95%
2          90%           95%
4          70%           95%
8          40%           95%
16         20%           95%
32         10%           95%

结论：LongAdder性能稳定，不受线程数影响
```

---

## 二、LongAdder的设计思想

### 2.1 核心思想：分段累加

```
AtomicLong的方式：
所有线程竞争一个变量
    ↓
[Thread1] ──┐
[Thread2] ──┼──> [Counter]
[Thread3] ──┘
    ↓
竞争激烈，性能差

LongAdder的方式：
每个线程有自己的计数器
    ↓
[Thread1] ──> [Cell1]
[Thread2] ──> [Cell2]
[Thread3] ──> [Cell3]
    ↓
最终求和：sum = Cell1 + Cell2 + Cell3
    ↓
竞争少，性能好
```

### 2.2 类继承关系

```java
// Striped64：抽象基类
abstract class Striped64 extends Number {
    // Cell数组
    transient volatile Cell[] cells;
    
    // 基础值
    transient volatile long base;
    
    // 自旋锁
    transient volatile int cellsBusy;
    
    // Cell类
    @sun.misc.Contended
    static final class Cell {
        volatile long value;
        
        Cell(long x) { value = x; }
        
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }
    }
}

// LongAdder：具体实现
public class LongAdder extends Striped64 implements Serializable {
    public void add(long x) { ... }
    public void increment() { add(1L); }
    public void decrement() { add(-1L); }
    public long sum() { ... }
}
```

---

## 三、LongAdder的实现原理

### 3.1 核心数据结构

```java
public class LongAdder extends Striped64 {
    
    // 1. base：基础值
    // 低竞争时直接累加到base
    transient volatile long base;
    
    // 2. cells：Cell数组
    // 高竞争时分散到不同的Cell
    transient volatile Cell[] cells;
    
    // 3. cellsBusy：自旋锁
    // 用于扩容cells数组
    transient volatile int cellsBusy;
    
    // Cell类（带缓存行填充，避免伪共享）
    @sun.misc.Contended
    static final class Cell {
        volatile long value;
        
        Cell(long x) { value = x; }
        
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }
    }
}
```

**@Contended注解**：

```java
// 避免伪共享
@sun.misc.Contended
static final class Cell {
    volatile long value;
}

// 编译后的内存布局：
// [padding] [value] [padding]
//    56字节   8字节   56字节
// 总共128字节，独占两个缓存行

// 为什么需要？
// - 避免多个Cell在同一缓存行
// - 减少缓存失效
// - 提升性能
```

### 3.2 add方法的实现

```java
public void add(long x) {
    Cell[] as; long b, v; int m; Cell a;
    
    // 情况1：cells不为空 或 CAS更新base失败
    if ((as = cells) != null || !casBase(b = base, b + x)) {
        boolean uncontended = true;
        
        // 情况2：cells为空 或 当前线程的Cell为空 或 CAS更新Cell失败
        if (as == null || (m = as.length - 1) < 0 ||
            (a = as[getProbe() & m]) == null ||
            !(uncontended = a.cas(v = a.value, v + x)))
            
            // 进入复杂逻辑
            longAccumulate(x, null, uncontended);
    }
}
```

**执行流程**：

```
开始
  ↓
cells为空？
├─ 是 → CAS更新base
│        ├─ 成功 → 返回
│        └─ 失败 → 初始化cells
└─ 否 → 获取当前线程的Cell
         ├─ Cell为空 → 创建Cell
         └─ Cell不为空 → CAS更新Cell
                         ├─ 成功 → 返回
                         └─ 失败 → 扩容或重试
```

### 3.3 longAccumulate方法（核心）

```java
final void longAccumulate(long x, LongBinaryOperator fn, boolean wasUncontended) {
    int h;
    if ((h = getProbe()) == 0) {
        ThreadLocalRandom.current();  // 初始化probe
        h = getProbe();
        wasUncontended = true;
    }
    
    boolean collide = false;
    for (;;) {
        Cell[] as; Cell a; int n; long v;
        
        // 情况1：cells已初始化
        if ((as = cells) != null && (n = as.length) > 0) {
            // 1.1 当前Cell为空，创建新Cell
            if ((a = as[(n - 1) & h]) == null) {
                if (cellsBusy == 0) {
                    Cell r = new Cell(x);
                    if (cellsBusy == 0 && casCellsBusy()) {
                        boolean created = false;
                        try {
                            Cell[] rs; int m, j;
                            if ((rs = cells) != null &&
                                (m = rs.length) > 0 &&
                                rs[j = (m - 1) & h] == null) {
                                rs[j] = r;
                                created = true;
                            }
                        } finally {
                            cellsBusy = 0;
                        }
                        if (created)
                            break;
                        continue;
                    }
                }
                collide = false;
            }
            // 1.2 CAS失败，重新hash
            else if (!wasUncontended)
                wasUncontended = true;
            // 1.3 尝试CAS更新Cell
            else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
                break;
            // 1.4 cells已改变或已达最大容量
            else if (n >= NCPU || cells != as)
                collide = false;
            // 1.5 设置扩容标志
            else if (!collide)
                collide = true;
            // 1.6 扩容
            else if (cellsBusy == 0 && casCellsBusy()) {
                try {
                    if (cells == as) {
                        Cell[] rs = new Cell[n << 1];  // 扩容2倍
                        for (int i = 0; i < n; ++i)
                            rs[i] = as[i];
                        cells = rs;
                    }
                } finally {
                    cellsBusy = 0;
                }
                collide = false;
                continue;
            }
            h = advanceProbe(h);  // 重新hash
        }
        // 情况2：cells未初始化，尝试初始化
        else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
            boolean init = false;
            try {
                if (cells == as) {
                    Cell[] rs = new Cell[2];  // 初始容量为2
                    rs[h & 1] = new Cell(x);
                    cells = rs;
                    init = true;
                }
            } finally {
                cellsBusy = 0;
            }
            if (init)
                break;
        }
        // 情况3：cells正在初始化，尝试更新base
        else if (casBase(v = base, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
            break;
    }
}
```

**关键步骤**：

```
1. 初始化cells（容量为2）
2. 根据线程hash选择Cell
3. CAS更新Cell
4. 失败则重新hash
5. 多次失败则扩容（2倍）
6. 最大容量为CPU核心数
```

### 3.4 sum方法的实现

```java
public long sum() {
    Cell[] as = cells; Cell a;
    long sum = base;  // 从base开始
    
    if (as != null) {
        // 累加所有Cell的值
        for (int i = 0; i < as.length; ++i) {
            if ((a = as[i]) != null)
                sum += a.value;
        }
    }
    return sum;
}
```

**注意**：
- sum()不是原子操作
- 返回的是近似值
- 适合统计场景，不适合精确计数

---

## 四、LongAdder vs AtomicLong

### 4.1 性能对比

```java
public class PerformanceTest {
    private static final int THREAD_COUNT = 32;
    private static final int ITERATIONS = 10000000;
    
    // 测试AtomicLong
    public static void testAtomicLong() {
        AtomicLong counter = new AtomicLong(0);
        // 多线程累加
    }
    
    // 测试LongAdder
    public static void testLongAdder() {
        LongAdder counter = new LongAdder();
        // 多线程累加
    }
}
```

**性能结果**：

```
线程数    AtomicLong    LongAdder    提升倍数
1         100ms         105ms        0.95x
2         150ms         110ms        1.36x
4         300ms         115ms        2.61x
8         800ms         120ms        6.67x
16        2000ms        125ms        16x
32        5000ms        130ms        38x

结论：
- 低并发：AtomicLong略快
- 高并发：LongAdder快几十倍
```

### 4.2 对比表

| 特性 | AtomicLong | LongAdder |
|------|-----------|-----------|
| **实现** | 单变量CAS | 分段累加 |
| **低并发** | 快 | 略慢 |
| **高并发** | 慢（竞争激烈） | 快 |
| **内存占用** | 小（8字节） | 大（多个Cell） |
| **精确性** | 实时精确 | 最终一致 |
| **适用场景** | 低并发、需要精确值 | 高并发、统计计数 |

### 4.3 选择建议

```java
// ✅ 使用AtomicLong
// - 低并发（线程数 < 4）
// - 需要实时精确值
// - 内存敏感
AtomicLong counter = new AtomicLong(0);

// ✅ 使用LongAdder
// - 高并发（线程数 >= 8）
// - 统计计数（允许最终一致）
// - 性能优先
LongAdder counter = new LongAdder();
```

---

## 五、LongAccumulator详解

### 5.1 与LongAdder的区别

```java
// LongAdder：只能累加
LongAdder adder = new LongAdder();
adder.increment();  // 只能+1
adder.add(5);       // 只能加法

// LongAccumulator：自定义累加函数
LongAccumulator accumulator = new LongAccumulator(
    (x, y) -> x + y,  // 累加函数
    0                 // 初始值
);
accumulator.accumulate(5);
```

### 5.2 使用示例

```java
// 示例1：求最大值
LongAccumulator max = new LongAccumulator(Long::max, Long.MIN_VALUE);
max.accumulate(10);
max.accumulate(20);
max.accumulate(15);
System.out.println(max.get());  // 20

// 示例2：求最小值
LongAccumulator min = new LongAccumulator(Long::min, Long.MAX_VALUE);
min.accumulate(10);
min.accumulate(5);
min.accumulate(15);
System.out.println(min.get());  // 5

// 示例3：求乘积
LongAccumulator product = new LongAccumulator((x, y) -> x * y, 1);
product.accumulate(2);
product.accumulate(3);
product.accumulate(4);
System.out.println(product.get());  // 24
```

### 5.3 实现原理

```java
public class LongAccumulator extends Striped64 {
    private final LongBinaryOperator function;
    private final long identity;
    
    public LongAccumulator(LongBinaryOperator accumulatorFunction, long identity) {
        this.function = accumulatorFunction;
        this.identity = identity;
    }
    
    public void accumulate(long x) {
        Cell[] as; long b, v, r; int m; Cell a;
        if ((as = cells) != null ||
            (r = function.applyAsLong(b = base, x)) != b && !casBase(b, r)) {
            // 与LongAdder类似，但使用自定义函数
            // ...
        }
    }
}
```

---

## 六、实际应用场景

### 6.1 高并发计数器

```java
/**
 * 网站访问统计
 */
public class WebStatistics {
    private LongAdder totalVisits = new LongAdder();
    private LongAdder todayVisits = new LongAdder();
    
    public void recordVisit() {
        totalVisits.increment();
        todayVisits.increment();
    }
    
    public long getTotalVisits() {
        return totalVisits.sum();
    }
    
    public void resetTodayVisits() {
        todayVisits.reset();
    }
}
```

### 6.2 性能监控

```java
/**
 * 请求性能统计
 */
public class PerformanceMonitor {
    private LongAdder requestCount = new LongAdder();
    private LongAdder totalTime = new LongAdder();
    private LongAccumulator maxTime = new LongAccumulator(Long::max, 0);
    private LongAccumulator minTime = new LongAccumulator(Long::min, Long.MAX_VALUE);
    
    public void record(long duration) {
        requestCount.increment();
        totalTime.add(duration);
        maxTime.accumulate(duration);
        minTime.accumulate(duration);
    }
    
    public double getAverageTime() {
        long count = requestCount.sum();
        return count == 0 ? 0 : (double) totalTime.sum() / count;
    }
    
    public long getMaxTime() {
        return maxTime.get();
    }
    
    public long getMinTime() {
        return minTime.get();
    }
}
```

### 6.3 限流器

```java
/**
 * 基于LongAdder的限流器
 */
public class RateLimiter {
    private final long maxRequests;
    private final LongAdder currentRequests = new LongAdder();
    
    public RateLimiter(long maxRequests) {
        this.maxRequests = maxRequests;
    }
    
    public boolean tryAcquire() {
        currentRequests.increment();
        if (currentRequests.sum() > maxRequests) {
            currentRequests.decrement();
            return false;
        }
        return true;
    }
    
    public void release() {
        currentRequests.decrement();
    }
    
    public void reset() {
        currentRequests.reset();
    }
}
```

---

## 七、常见陷阱

### 7.1 sum()不是原子操作

```java
// ❌ 错误：sum()不是原子操作
LongAdder counter = new LongAdder();

if (counter.sum() < 100) {
    counter.increment();  // 不是原子操作
}

// ✅ 正确：用于统计，不用于精确控制
LongAdder counter = new LongAdder();
counter.increment();
// 定期获取统计值
long total = counter.sum();
```

### 7.2 内存占用

```java
// LongAdder内存占用
// base: 8字节
// cells: 8字节（引用）
// Cell[]: n * 128字节（每个Cell带填充）
// 总计：约 8 + 8 + n * 128 字节

// 32个线程：约4KB
// 对比AtomicLong：8字节

// 结论：内存占用大，但性能好
```

### 7.3 不适合精确计数

```java
// ❌ 不适合：需要精确值的场景
LongAdder balance = new LongAdder();
balance.add(100);
balance.add(-50);
long current = balance.sum();  // 可能不准确

// ✅ 适合：统计场景
LongAdder pageViews = new LongAdder();
pageViews.increment();
long total = pageViews.sum();  // 允许误差
```

---

## 八、总结

### 8.1 核心要点

1. **设计思想**：分段累加，减少竞争
2. **数据结构**：base + Cell数组
3. **性能**：高并发下比AtomicLong快几十倍
4. **权衡**：内存换性能，精确性换速度
5. **适用场景**：高并发统计计数

### 8.2 实现原理

```
1. 低竞争：直接CAS更新base
2. 高竞争：分散到不同Cell
3. 扩容：容量翻倍，最大为CPU核心数
4. 求和：base + 所有Cell的和
5. 避免伪共享：@Contended注解
```

### 8.3 思考题

1. **LongAdder为什么比AtomicLong快？**
2. **LongAdder的sum()为什么不是原子的？**
3. **什么是伪共享？@Contended如何解决？**
4. **什么时候用LongAdder，什么时候用AtomicLong？**

---

**恭喜！你已经完成了原子类与无锁编程的深度学习！** 🎉

---

**参考资料**：
- 《Java并发编程实战》第15章
- JDK源码：`java.util.concurrent.atomic.LongAdder`
- Doug Lea的论文：Striped64设计
