# 第四章：TLAB 与对象分配

## 4.1 为什么需要 TLAB？

堆是所有线程共享的，分配对象时必须保证线程安全：

```
没有 TLAB 时（所有线程竞争同一个分配指针）：

线程1：top += 16（CAS）→ 成功
线程2：top += 24（CAS）→ 失败，重试
线程3：top += 16（CAS）→ 失败，重试
线程2：top += 24（CAS）→ 成功
线程3：top += 16（CAS）→ 成功

问题：
  1. CAS 自旋开销：高并发下大量 CAS 失败重试，浪费 CPU
  2. 缓存行失效：多个 CPU 核心修改同一块内存，L1/L2 缓存频繁失效（伪共享）
  3. 性能瓶颈：线程越多，竞争越激烈，分配越慢

引入 TLAB 后（每个线程有自己的私有分配区域）：

堆（Eden 区）
┌──────────┬──────────┬──────────┬─────────────────┐
│  TLAB-1  │  TLAB-2  │  TLAB-3  │   未分配空间     │
│ (线程1)  │ (线程2)  │ (线程3)  │                  │
│ 私有，无锁│ 私有，无锁│ 私有，无锁│                  │
└──────────┴──────────┴──────────┴─────────────────┘

线程1：在 TLAB-1 中分配，指针碰撞，无 CAS，无竞争
线程2：在 TLAB-2 中分配，指针碰撞，无 CAS，无竞争
线程3：在 TLAB-3 中分配，指针碰撞，无 CAS，无竞争
→ 各自独立，互不干扰，性能提升约 10 倍
```

---

## 4.2 TLAB 结构与指针碰撞

```
TLAB 内部结构：

┌──────────────────────────────────────┐
│  对象A  │  对象B  │   空闲空间        │
└─────────┴─────────┴──────────────────┘
↑                    ↑                  ↑
start                top                end

三个关键指针：
  start ── TLAB 起始地址
  top   ── 当前分配位置（下一个对象从这里开始）
  end   ── TLAB 结束地址

分配过程（指针碰撞 / Bump-the-Pointer）：
  1. 计算对象大小（如 16 字节，含对象头 + 字段 + 对齐填充）
  2. 检查空间：top + 16 ≤ end？
  3. 分配：result = top; top += 16;
  4. 返回 result

时间复杂度：O(1)，仅需一次加法 + 一次比较，几个 CPU 指令
无锁：TLAB 线程私有，不需要 CAS 或 synchronized
```

```java
// 指针碰撞分配伪代码（HotSpot 内部逻辑简化）
long allocate(int objectSize) {
    long newTop = top + objectSize;
    if (newTop <= end) {
        long result = top;
        top = newTop;       // 指针碰撞，O(1)
        return result;      // 返回对象起始地址
    }
    // TLAB 空间不足，走慢速路径
    return slowPathAllocate(objectSize);
}
```

---

## 4.3 对象分配完整流程

```
new Object()
    │
    ├─① 逃逸分析：对象是否逃逸？
    │    不逃逸 → 标量替换（拆散为基本类型，分配在栈/寄存器，见第二章）
    │    逃逸   ↓
    │
    ├─② TLAB 分配：top + size ≤ end？
    │    是 → 指针碰撞，立即返回（最快的堆分配路径，无锁）
    │    否 ↓
    │
    ├─③ TLAB 剩余空间判断：剩余空间 > refill_waste_limit？
    │    是 → 剩余空间还大，不值得丢弃 → 在 Eden 共享区 CAS 分配（慢速路径）
    │    否 → 剩余空间很小，丢弃旧 TLAB → 申请新 TLAB → 在新 TLAB 中分配
    │
    ├─④ 大对象判断：对象 > PretenureSizeThreshold？
    │    是 → 直接在老年代分配（跳过新生代，避免大对象频繁复制）
    │    否 → 在 Eden 共享区 CAS 分配
    │
    └─⑤ Eden 区也满了 → 触发 Minor GC → GC 后重试分配
         仍然失败 → 触发 Full GC → 仍然失败 → OOM

分配路径性能对比（相对耗时）：
  标量替换（栈）：1x     ← 最快，无堆分配
  TLAB 分配：    ~2x    ← 极快，无锁指针碰撞
  Eden CAS 分配：~10x   ← 需要 CAS 同步，可能重试
  老年代分配：    ~100x  ← CAS + 可能触发 Full GC
```

---

## 4.4 TLAB 大小与动态调整

```
TLAB 大小计算公式（JVM 自动计算，-XX:TLABSize=0 时）：

  TLAB 大小 ≈ Eden 区大小 / (线程数 × 目标 refill 次数)

  示例：
    Eden 区 = 100MB，活跃线程 = 10，目标 refill 次数 = 100
    TLAB ≈ 100MB / (10 × 100) = 100KB

动态调整（-XX:+ResizeTLAB，默认开启）：
  JVM 运行时根据每个线程的分配速率动态调整 TLAB 大小：
  ┌────────────────┬──────────────────────────────┐
  │ 分配速率高的线程 │ 分配更大的 TLAB（减少 refill）│
  │ 分配速率低的线程 │ 分配更小的 TLAB（减少浪费）   │
  └────────────────┴──────────────────────────────┘

TLAB 浪费：
  场景1：TLAB 剩余空间不足以放下新对象
    ┌─────────────────────────────┐
    │ 已使用       │ 剩余 5 字节  │
    └──────────────┴──────────────┘
    新对象需要 16 字节 → 放不下 → 剩余 5 字节被填充（dummy 对象）后丢弃

  场景2：线程结束，TLAB 未用完
    ┌─────────────────────────────┐
    │ 已使用  │ 未使用（浪费）     │
    └─────────┴───────────────────┘

  控制浪费的参数：
    -XX:TLABWasteTargetPercent=1      允许浪费 Eden 区的 1%
    -XX:TLABRefillWasteFraction=64    剩余 < TLAB/64 时丢弃旧 TLAB 申请新的
                                      剩余 ≥ TLAB/64 时在 Eden 共享区 CAS 分配
```

---

## 4.5 TLAB 与 GC 的关系

```
TLAB 位于 Eden 区，随 Minor GC 一起回收：

Minor GC 触发
    ↓
1. 所有线程的 TLAB 被"退还"给 Eden
   （TLAB 中的空闲空间用 dummy 对象填充，便于 GC 线性扫描）
    ↓
2. 扫描整个 Eden 区（含所有 TLAB 区域）
    ↓
3. 标记存活对象 → 复制到 Survivor 区
    ↓
4. 清空 Eden 区（所有 TLAB 随之清空）
    ↓
5. 为每个线程重新分配新的 TLAB

关键点：
  - TLAB 只在 Eden 区，Survivor 和老年代没有 TLAB
  - TLAB 的 dummy 填充保证 GC 扫描时不会遇到"空洞"
  - Minor GC 后 TLAB 大小可能调整（根据上一轮的分配统计）
```

---

## 4.6 TLAB 监控与参数

### 关键 JVM 参数

```bash
# 启用/禁用 TLAB（默认开启，几乎不应关闭）
-XX:+UseTLAB              # 默认开启
-XX:-UseTLAB              # 关闭（仅用于对比测试）

# TLAB 大小（0 = 自动计算，推荐默认）
-XX:TLABSize=0

# 动态调整 TLAB 大小（默认开启）
-XX:+ResizeTLAB

# 最小 TLAB 大小
-XX:MinTLABSize=2048

# 浪费控制
-XX:TLABWasteTargetPercent=1       # 允许浪费 Eden 的 1%
-XX:TLABRefillWasteFraction=64     # 剩余 < 1/64 时重新申请 TLAB

# 大对象直接进老年代阈值（仅 Serial/ParNew 生效，G1 不适用）
-XX:PretenureSizeThreshold=0       # 0 = 不限制，默认
```

### 查看 TLAB 统计

```bash
# JDK 8：打印 TLAB 统计信息
java -XX:+PrintTLAB -jar app.jar

# 输出示例：
# TLAB: gc thread: 0x00007f8c3c001000 [id: 12345]
#   desired_size: 131KB          ← TLAB 期望大小
#   slow allocs: 10              ← 慢速分配次数（走 Eden 共享区）
#   refill waste: 2KB            ← refill 时浪费的空间
#   fast: 0.95                   ← 快速分配比例（TLAB 命中率）
#   slow: 0.05                   ← 慢速分配比例

# 优化目标：
#   fast > 95%（TLAB 命中率高）
#   slow allocs 尽量少
#   refill waste 尽量少

# JDK 9+：使用统一日志
java -Xlog:gc+tlab=trace -jar app.jar
```

---

## 4.7 优化实践

### 常见问题与解法

| 问题 | 症状 | 原因 | 解法 |
|------|------|------|------|
| TLAB 命中率低（fast < 90%）| 慢速分配多，GC 日志中 slow allocs 高 | TLAB 太小或对象太大 | 增大 Eden 区（`-Xmn`）；减少线程数（线程池）|
| TLAB 浪费率高 | refill waste 占 Eden 比例超 5% | TLAB 太大或分配不均匀 | 减小 TLAB（`-XX:TLABSize`）；使用线程池避免短命线程 |
| 频繁 TLAB refill | GC 日志中 refill 次数高 | TLAB 太小，分配速率高 | 增大 Eden 区；检查是否有对象分配热点 |
| 大对象绕过 TLAB | 对象大于 TLAB → 走慢速路径 | 创建了大数组或大对象 | 拆分大对象；使用对象池复用；调整 `PretenureSizeThreshold` |

### 编码建议

```java
// ✅ 小对象让 JVM 自由分配（TLAB + 逃逸分析会优化）
public void process() {
    Point p = new Point(1, 2);  // 小对象，TLAB 分配或标量替换
    // ...
}

// ✅ 使用线程池（控制线程数 → 控制 TLAB 数量 → 减少 Eden 碎片）
ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

// ❌ 避免在循环中创建大数组
for (int i = 0; i < 10000; i++) {
    byte[] buf = new byte[64 * 1024];  // 64KB，可能超出 TLAB → 每次走慢速路径
}

// ✅ 改为复用或使用对象池
byte[] buf = new byte[64 * 1024];
for (int i = 0; i < 10000; i++) {
    Arrays.fill(buf, (byte) 0);
    // 复用 buf
}

// ✅ 默认配置通常最优，不要轻易手动设置 TLABSize
// JVM 的动态调整比手动固定值更适应真实负载
```

---

## 4.8 本章总结

- **TLAB 的价值**：线程私有的 Eden 区缓冲，消除对象分配时的 CAS 竞争，性能提升约 10 倍
- **指针碰撞**：`result = top; top += size;`，O(1) 分配，几个 CPU 指令完成
- **分配优先级**：标量替换（栈）→ TLAB（无锁）→ Eden CAS → 老年代
- **动态调整**：JVM 根据每个线程的分配速率自动调整 TLAB 大小，默认配置通常最优
- **浪费控制**：`TLABRefillWasteFraction` 决定"丢弃旧 TLAB 还是走 CAS"的临界点
- **与 GC 的关系**：TLAB 只在 Eden 区，Minor GC 时随 Eden 一起回收，GC 后重新分配
- **监控目标**：TLAB 命中率（fast）> 95%，浪费率 < 5%

> **本章对应演示代码**：`MemoryStructureDemo.java`（memory 模块，TLAB 分配演示）、`EscapeAnalysisDemo.java`（memory 模块，栈分配 vs TLAB vs 堆分配对比）、`JVMProfiler.java`（advanced 模块，TLAB 使用监控）

**返回目录**：参见 `java-jvm` 模块 README
