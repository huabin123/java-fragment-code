# JVM 调优实战与面试排查指南

> **核心观点**：九成项目不需要调 JVM，但这道题能一秒分出谁背的谁真查过线上问题。
>
> 面试官盯着 JVM 问，要的不是参数值，是排查路径。

---

## 一、面试官问 JVM 到底在筛什么

`-Xmx` 设多大、G1 和 ZGC 怎么选、新生代老年代比例多少，这些参数背一背都能答。面试官真正想听的不是参数值，是碰到问题的时候怎么一步步定位到需要调这个参数。

**面试官的三步追问套路**：

```
第一问：GC 有哪几种？          → 送分题，谁都能答
第二问：线上碰到过 GC 问题吗？  → 分水岭，背八股的开始卡壳
第三问：怎么定位的、最后怎么解决的？ → 真正在考的东西
```

三个问题下来，背八股的和真干过的，区别很明显。

### 1.1 典型面试场景：偶发超时排查

> 一个 Spring Boot 服务，上线三天后开始偶发超时。运维说 CPU 不高、内存没爆、网络没丢包。这时候怎么查？

**第一步：先看 GC 状态**

```bash
jstat -gcutil <pid> 1000
```

这条命令每秒打印一次 GC 状态。重点关注的列：

| 列名 | 含义 | 正常参考值 |
|------|------|----------|
| `YGC` | Young GC 次数 | - |
| `YGCT` | Young GC 累计耗时 | - |
| `FGC` | Full GC 次数 | 一天一两次甚至没有 |
| `FGCT` | Full GC 累计耗时 | - |
| `O` | Old 区使用率 | 30%~60% 之间波动 |

> 如果 `jstat` 打出来 Old 区 95%，Full GC 每隔几分钟来一次，每次停顿几百毫秒，超时的原因基本就在这了。

**第二步：找出内存大户**

```bash
jmap -histo:live <pid> | head -30
```

> ⚠️ 注意：`:live` 参数会触发一次 Full GC，生产环境执行时会有一次停顿。

按占用内存大小排序列出前 30 个类，解读方式：

- 排在前面的是 `byte[]`、`char[]`、`String` → 业务代码创建了大量字符串或字节数组
- 排在前面的是某个业务 DTO 或 `HashMap$Node` → 更明确，直接查哪段代码在批量创建这些对象

---

## 二、九成项目的瓶颈不在 JVM

JDK 8 默认用 Parallel GC，JDK 9+ 默认 G1。Spring Boot 应用如果堆设了 2G 到 4G，G1 的默认配置已经把 Young GC 控制在 10ms 以内了。

### 2.1 "够用"的三个指标

| 指标 | 目标值 |
|------|--------|
| 接口 P99 | < 50ms |
| GC 停顿占比 | < 总响应时间的 1% |
| 监控曲线 | 平滑，无毛刺 |

满足这三条，JVM 参数一个都不用动。

### 2.2 常见的误判：把非 JVM 问题归咎于 JVM

**误判一：SQL 问题**

一个列表接口慢了，十有八九是 SQL 问题：
- 没加索引
- `SELECT *` 拉了二十个字段
- 分页用了 `LIMIT 100000, 10`

把慢查询修了，RT 从 800ms 降到 20ms，跟 JVM 半毛钱关系没有。

**误判二：N+1 调用问题**

```
查一个订单列表，每条订单调一次用户服务拿用户名
10 条订单 × 20ms/次 RPC = 200ms 串行耗时

改成批量查询一次拿回来 → RT 直接降到 30ms
```

这种问题跟 JVM 没有任何关系，但不少人第一反应是"是不是 GC 导致的"。

### 2.3 先用 Arthas 确认瓶颈在哪

判断瓶颈在不在 JVM，先用 Arthas 的 `trace` 命令看接口耗时分布：

```bash
trace com.example.OrderController getOrderList
```

Arthas 会把这个方法内部每一步的耗时都打出来。**如果 90% 的时间花在数据库查询或者 RPC 调用上，JVM 调优就是在错误的方向上使劲。**

---

## 三、"伪 JVM 问题"：RSS 高不等于内存泄漏

服务内存占用高，运维一看 RSS 3G，堆才设了 1G，慌了，以为内存泄漏。

### 3.1 Java 进程内存组成

```
Java 进程 RSS ≠ 堆大小（-Xmx）

RSS 实际组成：
  ├─ Java Heap（-Xmx 控制）
  ├─ Metaspace（存类信息，JDK 8 的 PermGen 替代品）
  ├─ 线程栈（每个线程默认 1MB，-Xss 控制）
  ├─ NIO DirectBuffer（网络 IO 缓冲区）
  ├─ CodeCache（JIT 编译后的机器码缓存）
  └─ JVM 自身数据结构

这些加起来，RSS 比 -Xmx 大一倍都正常。
```

### 3.2 排查非堆内存：NativeMemoryTracking

**启动时开启追踪**：

```bash
-XX:NativeMemoryTracking=summary
```

**运行时查看**：

```bash
jcmd <pid> VM.native_memory summary
```

输出按区域列出：`Java Heap`、`Class（Metaspace）`、`Thread`、`Code（JIT 编译缓存）`、`GC`、`Internal`、`Symbol` 等。

### 3.3 常见非堆内存过高的原因与解法

**Thread 占用过高**（如 500MB）：

```
Tomcat 默认最大 200 线程 × 1MB = 200MB
+ 业务线程池 + 定时任务线程 → 容易到 500MB

这不是泄漏，是线程数该控制了。
```

**Class 区域（Metaspace）持续增长不回收**：

```
可能是动态生成类的框架（CGLIB、Groovy 脚本引擎）在不断加载新类
Metaspace 默认无上限 → 会把整台机器的内存吃光

解决：加上限，让问题暴露得更早
```

```bash
-XX:MaxMetaspaceSize=256m
```

---

## 四、真需要调的场景

### 4.1 典型触发场景

```
大促期间流量是平时十倍：
  平时 Young GC 1次/秒、耗时 5ms
  大促 Young GC 3~4次/秒 + 偶发 Mixed GC
  接口 P99 从 30ms 跳到 200ms
```

**需要 JVM 调优的真实信号**：
- 接口 RT 出现**周期性毛刺**
- 服务内存**只涨不降**，重启才能续命
- 容器里莫名其妙**被 OOM Killed**

### 4.2 第一步：开启 GC 日志

生产环境建议**常开 GC 日志**，性能损耗可以忽略不计（官方文档说法是"negligible"），但出问题的时候没有日志就只能猜。

**JDK 8**：

```bash
-XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:/tmp/gc.log
```

**JDK 9+**：

```bash
-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags
```

### 4.3 第二步：分析 GC 日志

工具推荐：
- **GCEasy**（[gceasy.io](https://gceasy.io)）：在线分析，上传即用
- **GCViewer**：本地打开，功能更全

**重点关注的数据**：

| 指标 | 正常值 | 需关注 |
|------|--------|--------|
| Young GC 平均耗时 | < 10ms | > 50ms |
| Young GC 最大耗时 | - | 明显偏离平均值 |
| 每次 Young GC 的晋升量（Promotion） | 小 | 每次几十 MB → Young 区太小 |
| Mixed GC 触发频率 | 偶尔 | 频繁 → 老年代增长太快 |

### 4.4 常见问题的调参方向

#### 问题一：Young 区太小，对象过早晋升老年代

```bash
-XX:G1NewSizePercent=30      # Young 区最小占比（默认 5%）
-XX:G1MaxNewSizePercent=50   # Young 区最大占比（默认 60%）
```

> G1 默认自动调整 Young 区大小（5%~60%），但大促期间对象创建速率突然暴涨，自动调整可能跟不上，手动把下限抬高能让 Young 区有更多空间容纳短命对象。

#### 问题二：Humongous 对象频繁分配

G1 里**超过 Region 大小一半的对象**算 Humongous 对象，会分配到专门的 Humongous Region，不走 Young 区，只有 Mixed GC 或 Full GC 才能回收。

```
典型例子：一次性查几万条数据序列化成大 JSON 字符串
```

```bash
-XX:G1HeapRegionSize=16m    # 调大 Region 大小（默认按堆大小自动计算，1MB~32MB）
```

> ⚠️ Region 调大后数量减少，G1 的调度灵活性下降，不能无脑调大。根本解法是改代码，避免产生大对象（如改用分页查询）。

#### 问题三：GC 停顿时间过长

```bash
-XX:MaxGCPauseMillis=100    # 期望最大暂停时间（默认 200ms）
```

> ⚠️ **常见误解**：`MaxGCPauseMillis` **不是硬上限**，是"尽量目标"。设了 100ms 不代表每次 GC 一定在 100ms 以内，G1 只是会朝这个目标调整回收策略。实际停顿超过目标值是完全正常的，特别是 Mixed GC 和 Full GC。
>
> 真正要硬保证停顿上限，得上 **ZGC**。
>
> 目标设太低的副作用：G1 每次回收的区域变少，回收速度可能跟不上分配速度，反而导致更频繁的 GC。

### 4.5 调参不是银弹

> 大促场景下最有效的手段往往不是调 JVM。**加机器分流、加缓存降低对象创建速率、把大查询拆成分页批量**，这些代码层面的改动效果远比调参数大。参数调完了该慢还是慢，代码改对了参数都不用动。

---

## 五、容器环境下的真实坑

### 5.1 JVM 无法感知容器资源（JDK 8u131 之前）

```
问题：JVM 读的是宿主机的 CPU 核数和内存总量，不是容器的 limits

场景：64 核宿主机上跑 limits=2核 的容器
  ParallelGCThreads 按 64 核计算 → 43 个 GC 线程
  43 个线程全挤在 2 个 CPU 核上互相抢
  → GC 停顿反而更长
```

**解决**：`-XX:+UseContainerSupport` 在 JDK 8u191 之后默认开启，JDK 11+ 基本不用操心。

**验证 JVM 是否正确识别容器资源**：

```bash
jcmd <pid> VM.flags | grep -i container
java -XX:+PrintFlagsFinal -version 2>&1 | grep ActiveProcessorCount
```

如果输出的 `ActiveProcessorCount` 跟容器的 CPU limits 对不上，手动指定：

```bash
-XX:ActiveProcessorCount=2
```

### 5.2 K8s OOM Killed 却无 Java 日志

```
原因：
  K8s 的 memory limits 包含整个进程的 RSS，不只是堆。
  堆设了 limits 的 80% 看着很安全，
  但 Metaspace + 线程栈 + DirectBuffer + CodeCache 一超 → OOM Killed

  被 K8s kill 的进程不会生成 HeapDump，日志里只有一行 OOMKilled
```

**稳妥的堆大小设置方式**：

```bash
-XX:MaxRAMPercentage=70.0
```

让 JVM 自己根据容器可用内存算堆大小，剩下 30% 留给非堆区域。比手动算 `-Xmx` 靠谱，因为不同环境的容器 limits 可能不一样，硬编码 `-Xmx` 换个环境就得改。

**保险兜底配置**（应对 Java 层面的 OOM）：

```bash
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
```

> Java 层面的 OOM 至少能留下 dump 文件。K8s OOM Killed 救不了，但 Java 自己的 `OutOfMemoryError` 能兜住。

---

## 六、GC 选型：G1、ZGC、Shenandoah 怎么选

**选型的唯一判断标准：停顿时间的容忍度。**

### 6.1 三种 GC 对比

| 维度 | G1 | ZGC | Shenandoah |
|------|----|----|-----------|
| **停顿时间** | 10ms~200ms | < 1ms（亚毫秒） | < 10ms |
| **吞吐量** | 高 | 低 5%~10% | 类似 ZGC |
| **成熟度** | 非常成熟（JDK 9 默认） | JDK 15+ 可生产，JDK 21 成熟 | OpenJDK 独有 |
| **内存占用** | 正常 | 较高（染色指针+多重映射） | 较高 |
| **可用 JDK** | 所有发行版 | Oracle JDK / OpenJDK | 仅 OpenJDK |
| **适用场景** | 通用，堆 4G~16G | 延迟敏感（交易/实时计算） | Red Hat 生态 |

### 6.2 ZGC 的隐藏坑

```
ZGC 用染色指针做并发标记，需要额外的内存映射（多重映射）
实际内存占用会比 G1 高不少：

  -Xmx4g 的服务：
    G1 下 RSS ≈ 5~6G
    ZGC 下 RSS 可能达到 8G 甚至更高

  容器环境下如果 memory limits 没跟着调大
  → 切 ZGC 之后直接 OOM Killed，日志里看不出跟 GC 有关系

验证：用 pmap 能看到多份内存映射（这是 ZGC 的正常现象，不是泄漏）
```

### 6.3 选型建议

```
堆 8G 以下、延迟要求 P99 < 100ms 的业务服务  → G1，不用折腾
堆 16G 以上、或者延迟要求 P99 < 10ms         → 试 ZGC（充分压测后再上）
用 Amazon Corretto / Red Hat JDK             → 可考虑 Shenandoah

不确定选哪个？选 G1。等 G1 真的满足不了需求了再换，别提前优化。
```

---

## 七、Old 区只涨不降：内存泄漏排查

### 7.1 症状

服务运行一段时间后 Old 区使用率持续上升，Full GC 回收不掉多少空间，最终 OOM 或者被运维重启。

### 7.2 排查步骤

#### 第一步：确认是不是真的泄漏

```bash
jstat -gcutil <pid> 5000
```

每 5 秒看一次，连续观察半小时。

```
判断标准：
  Full GC 后 Old 区最低点越来越高：
    第一次 Full GC 后 30%
    第二次 Full GC 后 40%
    第三次 Full GC 后 55%
  → 基本确认是泄漏

  Full GC 后能回到差不多的水平
  → 只是老年代增长正常，不是泄漏
```

#### 第二步：抓 Heap Dump

```bash
jmap -dump:live,format=b,file=/tmp/heap.hprof <pid>
```

> ⚠️ `live` 参数会触发一次 Full GC，服务已经快 OOM 时这次停顿可能很长。
>
> 推荐提前配置 `-XX:+HeapDumpOnOutOfMemoryError`，在 OOM 时自动 dump，避免手动抓时机不对。

#### 第三步：MAT 分析

Eclipse MAT（Memory Analyzer Tool）打开 dump 文件：

1. 直接看 **Leak Suspects 报告**：MAT 自动分析哪些对象占了最多内存、GC Root 引用链是什么
2. **Dominator Tree 视图**：找到"支配"最多内存的对象，顺着引用链往上查，定位泄漏点

### 7.3 常见泄漏模式

| 模式 | 描述 | 典型代码 |
|------|------|---------|
| **静态集合无限增长** | `static` 的 `Map` 或 `List` 只加不删 | 本地缓存用 `HashMap` 实现，无过期策略 |
| **ThreadLocal 未清理** | 线程池环境下线程不销毁，ThreadLocal 对象一直留着 | 用完没调 `remove()` |
| **监听器未注销** | 监听器列表越来越长 | Spring `ApplicationListener` 注册在不断创建销毁的 Bean 上 |
| **连接池泄漏** | 连接借出去没还 | 数据库/HTTP 连接未正确关闭 |

---

## 八、面试回答三个实战场景

> 能说出排查路径的候选人，面试官知道他处理过线上问题。只会背"G1 适合大堆、ZGC 低延迟"的候选人，一追问就露馅。

**回答框架（五步法）**：

```
现象 → 工具 → 发现 → 解决 → 效果
```

### 场景一：Excel 导出撑爆老年代

> 线上服务 4C8G Pod，JDK 17 + G1，默认参数。有一次大批量数据导出接口上线后，监控发现 Mixed GC 频率明显上升。`jstat` 看到 Old 区增长很快，`jmap -histo` 排在前面的是 `byte[]` 和 `org.apache.poi.xssf` 相关的对象。定位到是代码里用了 `XSSFWorkbook`，十万条记录整个工作簿都在内存里。改成 `SXSSFWorkbook` 流式写入，Old 区恢复正常。

### 场景二：本地缓存没有过期策略

> 接口偶发超时，CPU 和网络都正常。`jstat` 看到 Full GC 每隔几分钟来一次，`jmap -histo` 发现大量 `HashMap$Node`。排查到是一个本地缓存用 `HashMap` 实现的，只往里加不往外删，数据量越来越大。换成 Caffeine 加了 TTL 和 maximumSize，Full GC 消失，P99 回到 20ms。

### 场景三：容器里被 OOM Killed

> 服务在 K8s 上跑，memory limits 4G，`-Xmx` 设了 3G。隔几天就被 OOM Killed 一次，但 Java 层面没有 OOM 日志。`jcmd` 看 NativeMemoryTracking，发现 Thread 区域占了 800MB，Tomcat 线程池 + 业务线程池 + 定时任务线程加起来快 800 个线程。把几个线程池的 `maxPoolSize` 砍下来，再把 `-Xmx` 降到 2.5G，稳定了。

**三个场景的共同点**：没有一个是靠调 `-XX` 参数解决的，全是**代码或配置层面的改动**。面试官要的就是这个判断力。

---

## 九、线上问题排查速查清单

### 9.1 接口偶发超时（CPU 和网络正常）

```
Step 1: jstat -gcutil <pid> 1000
        重点看 FGC 次数和 O 列（Old 区使用率）

Step 2: 发现 Full GC 频繁
        → jmap -histo:live <pid> | head -30
        → 找出内存大户，定位到业务代码

Step 3: 代码层修复（改用流式处理、加缓存过期、减少大对象创建等）
```

### 9.2 服务 RSS 很高但业务正常

```
Step 1: 启动参数加 -XX:NativeMemoryTracking=summary

Step 2: jcmd <pid> VM.native_memory summary
        按区域看各部分占比

Step 3: Thread 区域多 → 排查线程池配置，减少线程数
        Class 区域持续涨 → 查动态类加载（CGLIB / 脚本引擎）
```

### 9.3 容器里被 OOM Killed，没有 Java 日志

```
Step 1: 确认 -Xmx 没超过容器 limits 的 70%
        建议改用 -XX:MaxRAMPercentage=70.0

Step 2: 检查线程数（jcmd VM.native_memory summary 看 Thread 区域）
        检查 DirectBuffer 使用量

Step 3: 加 -XX:+HeapDumpOnOutOfMemoryError 兜底
        确认 -XX:+UseContainerSupport 已生效（JDK 8u191+）
```

### 9.4 Old 区使用率持续上升不回落

```
Step 1: jstat -gcutil <pid> 5000
        连续观察，确认 Full GC 后最低点在持续上涨（是泄漏）

Step 2: jmap -dump:live,format=b,file=/tmp/heap.hprof <pid>
        抓 Heap Dump（注意会触发 Full GC，生产谨慎操作）

Step 3: MAT 打开 dump，看 Leak Suspects 报告 + Dominator Tree
        顺着引用链找泄漏点
```

### 9.5 Young GC 频率突然飙高

```
Step 1: 开启 GC 日志，用 GCEasy 分析
        重点看每次 Young GC 的 Promotion（晋升老年代的数据量）

Step 2: 晋升量大
        → 调大 Young 区：-XX:G1NewSizePercent=30

Step 3: 发现 Humongous 对象分配频繁
        → 调大 RegionSize：-XX:G1HeapRegionSize=16m
        → 或改代码，避免创建超过 RegionSize/2 的大对象（如分页查询）
```

---

## 十、常用命令速查

| 命令 | 用途 |
|------|------|
| `jstat -gcutil <pid> 1000` | 每秒查看 GC 状态和各区使用率 |
| `jmap -histo:live <pid> \| head -30` | 查看存活对象内存占用 Top30（触发 Full GC） |
| `jmap -dump:live,format=b,file=/tmp/heap.hprof <pid>` | 抓取 Heap Dump |
| `jcmd <pid> VM.native_memory summary` | 查看非堆内存各区域分布 |
| `jstack <pid>` | 打印线程快照，查死锁和线程状态 |
| `trace com.example.XxxController method` | Arthas 追踪方法内部耗时分布 |
| `jcmd <pid> VM.flags \| grep -i container` | 验证容器资源感知是否生效 |

---

## 十一、关键 JVM 参数速查

| 参数 | 说明 | 推荐场景 |
|------|------|---------|
| `-XX:MaxRAMPercentage=70.0` | 按容器内存比例设堆大小 | 容器部署 |
| `-XX:+UseContainerSupport` | 启用容器资源感知（JDK 8u191+ 默认开） | 容器部署 |
| `-XX:ActiveProcessorCount=N` | 手动指定逻辑 CPU 数 | 容器感知不正确时 |
| `-XX:MaxMetaspaceSize=256m` | 限制 Metaspace 最大值 | 有动态类加载时 |
| `-XX:MaxGCPauseMillis=100` | G1 目标停顿时间（非硬上限） | 延迟敏感服务 |
| `-XX:G1NewSizePercent=30` | G1 Young 区最小占比 | 对象创建速率高时 |
| `-XX:G1HeapRegionSize=16m` | G1 Region 大小 | 有大对象分配时 |
| `-XX:NativeMemoryTracking=summary` | 启用非堆内存追踪 | 排查 RSS 高 |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 时自动 dump | 生产环境必备 |
| `-XX:HeapDumpPath=/tmp/heapdump.hprof` | Heap Dump 路径 | 配合上一条 |
| `-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags` | 开启 GC 日志（JDK 9+） | 生产环境常开 |
| `-XX:+UseCountedLoopSafepoints` | 计数循环中插入安全点 | TTSP 过长时 |

---

> **参考来源**：《面试 Java 岗老喜欢盯着 JVM 问，有那么多项目要调优吗？》——花宝宝 Dev（2026年3月30日）
