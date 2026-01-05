# JVM参数详解

## 📚 概述

JVM参数是控制JVM行为的重要手段，合理配置JVM参数可以显著提升应用性能。本文从架构师视角深入讲解JVM参数的分类、作用和最佳实践。

## 🎯 核心问题

- ❓ 为什么需要配置JVM参数？不配置会怎样？
- ❓ JVM参数有哪些分类？各自的作用是什么？
- ❓ 如何选择合适的堆内存大小？
- ❓ 如何选择合适的GC收集器？
- ❓ 生产环境必备的JVM参数有哪些？
- ❓ 如何验证参数是否生效？

---

## 一、为什么需要JVM参数

### 1.1 默认配置的问题

```
问题1：默认堆内存太小
- JVM默认最大堆：物理内存的1/4
- 对于8G内存的服务器：最大堆只有2G
- 大型应用很容易OOM

问题2：默认GC收集器不适合
- JDK 8默认：Parallel GC（高吞吐量）
- 互联网应用需要：低延迟GC（G1、ZGC）
- 导致响应时间长

问题3：缺少监控和诊断
- 没有GC日志
- OOM时没有堆转储
- 无法排查问题

问题4：性能优化受限
- 无法启用JIT优化
- 无法调整线程栈大小
- 无法优化类加载
```

### 1.2 配置参数的价值

```
价值1：提升性能
- 合理的堆大小 → 减少GC频率
- 合适的GC收集器 → 降低停顿时间
- 优化参数 → 提升吞吐量

价值2：增强稳定性
- 避免OOM
- 减少Full GC
- 防止内存泄漏

价值3：便于问题排查
- GC日志分析
- 堆转储分析
- 性能监控

价值4：资源优化
- 合理利用内存
- 降低CPU消耗
- 提高资源利用率
```

### 1.3 不配置的后果

```java
/**
 * 案例：生产环境未配置JVM参数导致的问题
 */
public class NoJVMParamsIssue {
    
    // 场景1：默认堆太小，频繁Full GC
    // 现象：应用响应慢，CPU飙高
    // 原因：堆内存不足，频繁Full GC
    
    // 场景2：OOM无法排查
    // 现象：应用崩溃，无堆转储文件
    // 原因：未配置-XX:+HeapDumpOnOutOfMemoryError
    
    // 场景3：GC停顿时间长
    // 现象：接口超时，用户投诉
    // 原因：使用Parallel GC，停顿时间长
}
```

---

## 二、JVM参数分类

### 2.1 参数类型

```
1. 标准参数（-）
   - 所有JVM都支持
   - 稳定，不会变化
   - 例如：-version、-cp、-D

2. 非标准参数（-X）
   - 大部分JVM支持
   - 可能会变化
   - 例如：-Xms、-Xmx、-Xss

3. 不稳定参数（-XX）
   - 特定JVM支持
   - 可能会变化或移除
   - 例如：-XX:+UseG1GC、-XX:MaxGCPauseMillis
```

### 2.2 参数格式

```bash
# 标准参数
java -version
java -cp /path/to/classes Main

# 非标准参数
-Xms2g          # 初始堆大小
-Xmx4g          # 最大堆大小
-Xss1m          # 线程栈大小

# 不稳定参数（布尔型）
-XX:+UseG1GC    # 启用G1 GC
-XX:-UseParallelGC  # 禁用Parallel GC

# 不稳定参数（数值型）
-XX:MaxGCPauseMillis=200  # 最大GC停顿时间
-XX:MetaspaceSize=256m    # 元空间初始大小

# 不稳定参数（字符串型）
-XX:HeapDumpPath=/path/to/dumps  # 堆转储路径
```

---

## 三、内存相关参数

### 3.1 堆内存参数

#### 为什么需要配置堆内存？

```
问题：默认堆内存计算方式
- 初始堆：物理内存 / 64
- 最大堆：物理内存 / 4

例如：16G物理内存的服务器
- 初始堆：256MB
- 最大堆：4GB

问题：
1. 初始堆太小 → 频繁扩容 → 性能抖动
2. 最大堆可能不够 → OOM
3. 初始堆和最大堆不一致 → 动态调整 → 性能损耗
```

#### 核心参数

| 参数 | 说明 | 推荐值 | 示例 |
|------|------|--------|------|
| `-Xms` | 初始堆大小 | 与-Xmx相同 | `-Xms4g` |
| `-Xmx` | 最大堆大小 | 物理内存的50-75% | `-Xmx4g` |
| `-Xmn` | 新生代大小 | 堆的1/3到1/2 | `-Xmn2g` |
| `-XX:NewRatio` | 老年代/新生代比例 | 2（默认） | `-XX:NewRatio=2` |
| `-XX:SurvivorRatio` | Eden/Survivor比例 | 8（默认） | `-XX:SurvivorRatio=8` |

#### 配置原则

```bash
# 原则1：-Xms = -Xmx（避免动态扩展）
-Xms4g -Xmx4g

# 原则2：新生代大小合理
# 新生代太小 → 频繁Minor GC
# 新生代太大 → Minor GC时间长
-Xmn2g  # 堆的1/3到1/2

# 原则3：预留系统内存
# 16G物理内存的服务器
# JVM堆：12G（75%）
# 系统+其他：4G（25%）
-Xms12g -Xmx12g
```

#### 堆内存大小选择流程

```
确定堆大小
    ↓
1. 评估应用内存需求
   - 并发用户数
   - 单个请求内存消耗
   - 缓存数据大小
    ↓
2. 考虑物理内存限制
   - 预留系统内存（1-2G）
   - 预留堆外内存
   - 预留其他进程内存
    ↓
3. 考虑GC停顿时间
   - 堆越大，GC时间越长
   - 需要平衡内存和停顿时间
    ↓
4. 压力测试验证
   - 模拟生产流量
   - 观察GC表现
   - 调整参数
```

### 3.2 元空间参数

#### 为什么需要元空间？

```
历史演进：
JDK 7及之前：永久代（PermGen）
- 存储类元数据
- 大小固定
- 容易OOM

JDK 8及之后：元空间（Metaspace）
- 存储类元数据
- 使用本地内存
- 自动扩展

为什么改为元空间？
1. 避免PermGen OOM
2. 简化Full GC
3. 支持更多类
4. 提高性能
```

#### 核心参数

| 参数 | 说明 | 推荐值 | 示例 |
|------|------|--------|------|
| `-XX:MetaspaceSize` | 元空间初始大小 | 256m | `-XX:MetaspaceSize=256m` |
| `-XX:MaxMetaspaceSize` | 元空间最大大小 | 512m | `-XX:MaxMetaspaceSize=512m` |
| `-XX:MinMetaspaceFreeRatio` | 最小空闲比例 | 40（默认） | `-XX:MinMetaspaceFreeRatio=40` |
| `-XX:MaxMetaspaceFreeRatio` | 最大空闲比例 | 70（默认） | `-XX:MaxMetaspaceFreeRatio=70` |

#### 配置建议

```bash
# 基本配置
-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m

# 大型应用（类很多）
-XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=1g

# 动态类加载应用（如Groovy、热部署）
-XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=2g
```

### 3.3 线程栈参数

#### 为什么需要配置栈大小？

```
问题：默认栈大小
- Linux/macOS：1MB
- Windows：根据系统

影响：
1. 栈太小 → StackOverflowError
2. 栈太大 → 浪费内存，线程数受限

计算：
线程数 = (MaxProcessMemory - JVMMemory) / ThreadStackSize

例如：
- 4G进程内存
- 2G JVM堆
- 1MB线程栈
- 最大线程数 ≈ 2000
```

#### 核心参数

| 参数 | 说明 | 推荐值 | 示例 |
|------|------|--------|------|
| `-Xss` | 线程栈大小 | 512k-1m | `-Xss1m` |

#### 配置建议

```bash
# 默认配置（大部分应用）
-Xss1m

# 递归深度大的应用
-Xss2m

# 线程数多的应用（减小栈大小）
-Xss512k
```

### 3.4 直接内存参数

#### 核心参数

| 参数 | 说明 | 推荐值 | 示例 |
|------|------|--------|------|
| `-XX:MaxDirectMemorySize` | 最大直接内存 | 根据需求 | `-XX:MaxDirectMemorySize=1g` |

---

## 四、GC相关参数

### 4.1 GC收集器选择

#### 为什么需要选择GC收集器？

```
不同应用的需求不同：

1. 后台批处理
   - 需求：高吞吐量
   - 选择：Parallel GC
   
2. 互联网应用
   - 需求：低延迟
   - 选择：G1 GC
   
3. 超大堆应用
   - 需求：超低延迟
   - 选择：ZGC

默认GC的问题：
- JDK 8默认：Parallel GC
- 适合批处理，不适合Web应用
- 停顿时间长，影响用户体验
```

#### GC收集器参数

| GC收集器 | 参数 | 适用场景 | 停顿时间 |
|---------|------|---------|---------|
| **Serial GC** | `-XX:+UseSerialGC` | 单核CPU，小堆 | 长 |
| **Parallel GC** | `-XX:+UseParallelGC` | 多核CPU，后台计算 | 长 |
| **CMS GC** | `-XX:+UseConcMarkSweepGC` | 互联网应用 | 短 |
| **G1 GC** | `-XX:+UseG1GC` | 大堆，低延迟 | 可控 |
| **ZGC** | `-XX:+UseZGC` | 超大堆，超低延迟 | 极短 |

#### 选择流程

```
选择GC收集器
    ↓
1. 确定应用类型
   - 批处理？→ Parallel GC
   - Web应用？→ G1 GC
   - 超大堆？→ ZGC
    ↓
2. 确定堆大小
   - < 4GB → Parallel GC / CMS
   - 4-32GB → G1 GC
   - > 32GB → ZGC
    ↓
3. 确定延迟要求
   - 高吞吐量 → Parallel GC
   - 低延迟 → G1 GC / ZGC
    ↓
4. 压力测试验证
```

### 4.2 G1 GC参数

#### 核心参数

| 参数 | 说明 | 推荐值 | 示例 |
|------|------|--------|------|
| `-XX:+UseG1GC` | 启用G1 GC | - | `-XX:+UseG1GC` |
| `-XX:MaxGCPauseMillis` | 最大停顿时间目标 | 200ms | `-XX:MaxGCPauseMillis=200` |
| `-XX:G1HeapRegionSize` | Region大小 | 1-32MB | `-XX:G1HeapRegionSize=16m` |
| `-XX:InitiatingHeapOccupancyPercent` | 触发并发GC的堆占用率 | 45% | `-XX:InitiatingHeapOccupancyPercent=45` |
| `-XX:G1ReservePercent` | 预留空间百分比 | 10% | `-XX:G1ReservePercent=10` |
| `-XX:ConcGCThreads` | 并发GC线程数 | CPU核数/4 | `-XX:ConcGCThreads=4` |

#### 推荐配置

```bash
# 基础配置
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:G1HeapRegionSize=16m \
-XX:InitiatingHeapOccupancyPercent=45

# 优化配置
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=100 \
-XX:G1HeapRegionSize=16m \
-XX:InitiatingHeapOccupancyPercent=40 \
-XX:G1ReservePercent=15 \
-XX:ConcGCThreads=4 \
-XX:+ParallelRefProcEnabled
```

### 4.3 GC日志参数

#### 为什么需要GC日志？

```
价值：
1. 分析GC行为
2. 定位性能问题
3. 调优依据
4. 问题排查

不开启的后果：
- 无法分析GC问题
- 无法优化GC参数
- 生产问题难以排查
```

#### JDK 8参数

```bash
# 基础GC日志
-XX:+PrintGCDetails \
-XX:+PrintGCDateStamps \
-XX:+PrintGCTimeStamps \
-Xloggc:/path/to/gc.log

# 详细GC日志
-XX:+PrintGCDetails \
-XX:+PrintGCDateStamps \
-XX:+PrintGCTimeStamps \
-XX:+PrintHeapAtGC \
-XX:+PrintTenuringDistribution \
-XX:+PrintGCApplicationStoppedTime \
-Xloggc:/path/to/gc-%t.log \
-XX:+UseGCLogFileRotation \
-XX:NumberOfGCLogFiles=5 \
-XX:GCLogFileSize=20M
```

#### JDK 9+参数

```bash
# 统一日志格式
-Xlog:gc*:file=/path/to/gc.log:time,uptime,level,tags:filecount=5,filesize=20M
```

---

## 五、性能优化参数

### 5.1 JIT编译参数

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `-XX:+TieredCompilation` | 启用分层编译 | 默认启用 |
| `-XX:CompileThreshold` | 方法编译阈值 | 10000（默认） |
| `-XX:+PrintCompilation` | 打印编译信息 | 调试时启用 |

### 5.2 逃逸分析参数

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `-XX:+DoEscapeAnalysis` | 启用逃逸分析 | 默认启用 |
| `-XX:+EliminateAllocations` | 启用标量替换 | 默认启用 |
| `-XX:+UseTLAB` | 启用TLAB | 默认启用 |

---

## 六、诊断和监控参数

### 6.1 OOM时堆转储

```bash
# 必备参数
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/path/to/dumps/heap-dump.hprof

# 为什么必须配置？
# 1. OOM时自动生成堆转储
# 2. 可以分析OOM原因
# 3. 定位内存泄漏
```

### 6.2 JMX监控

```bash
# 启用JMX
-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.port=9999 \
-Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.ssl=false

# 生产环境（启用认证）
-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.port=9999 \
-Dcom.sun.management.jmxremote.authenticate=true \
-Dcom.sun.management.jmxremote.password.file=/path/to/jmxremote.password \
-Dcom.sun.management.jmxremote.access.file=/path/to/jmxremote.access \
-Dcom.sun.management.jmxremote.ssl=true
```

### 6.3 其他诊断参数

```bash
# 打印JVM参数
-XX:+PrintFlagsFinal

# 打印命令行参数
-XX:+PrintCommandLineFlags

# 错误日志
-XX:ErrorFile=/path/to/hs_err_pid%p.log
```

---

## 七、生产环境推荐配置

### 7.1 小型应用（堆<4GB）

```bash
java -server \
  -Xms2g -Xmx2g \
  -Xmn1g \
  -Xss1m \
  -XX:MetaspaceSize=256m \
  -XX:MaxMetaspaceSize=512m \
  -XX:+UseParallelGC \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:/path/to/gc.log \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/path/to/dumps \
  -jar app.jar
```

### 7.2 中型应用（堆4-16GB）

```bash
java -server \
  -Xms8g -Xmx8g \
  -Xmn4g \
  -Xss1m \
  -XX:MetaspaceSize=512m \
  -XX:MaxMetaspaceSize=1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=16m \
  -XX:InitiatingHeapOccupancyPercent=45 \
  -XX:+ParallelRefProcEnabled \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -XX:+PrintGCTimeStamps \
  -Xloggc:/path/to/gc-%t.log \
  -XX:+UseGCLogFileRotation \
  -XX:NumberOfGCLogFiles=5 \
  -XX:GCLogFileSize=20M \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/path/to/dumps \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9999 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -jar app.jar
```

### 7.3 大型应用（堆>16GB）

```bash
java -server \
  -Xms32g -Xmx32g \
  -Xss1m \
  -XX:MetaspaceSize=512m \
  -XX:MaxMetaspaceSize=1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:G1HeapRegionSize=32m \
  -XX:InitiatingHeapOccupancyPercent=40 \
  -XX:G1ReservePercent=15 \
  -XX:ConcGCThreads=8 \
  -XX:+ParallelRefProcEnabled \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -XX:+PrintGCTimeStamps \
  -Xloggc:/path/to/gc-%t.log \
  -XX:+UseGCLogFileRotation \
  -XX:NumberOfGCLogFiles=10 \
  -XX:GCLogFileSize=50M \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/path/to/dumps \
  -jar app.jar
```

---

## 八、参数验证

### 8.1 查看生效的参数

```bash
# 方法1：启动时打印
java -XX:+PrintFlagsFinal -version | grep -i gc

# 方法2：运行时查看
jinfo -flags <pid>

# 方法3：查看具体参数
jinfo -flag UseG1GC <pid>
```

### 8.2 动态修改参数

```bash
# 查看可修改的参数
jinfo -flag +PrintGC <pid>

# 修改参数
jinfo -flag +PrintGCDetails <pid>

# 注意：只有部分参数支持动态修改
```

---

## 九、常见问题

### Q1: -Xms和-Xmx为什么要设置相同？

**A**: 
```
原因：
1. 避免动态扩展
   - 扩展需要Full GC
   - 影响性能

2. 减少性能抖动
   - 堆大小变化 → GC行为变化
   - 响应时间不稳定

3. 简化调优
   - 堆大小固定
   - GC行为可预测
```

### Q2: 如何确定合适的堆大小？

**A**:
```
步骤：
1. 评估内存需求
   - 并发用户数 × 单用户内存
   - 缓存数据大小
   - 临时对象大小

2. 压力测试
   - 模拟生产流量
   - 观察堆使用情况
   - 调整堆大小

3. 监控调优
   - 观察GC频率
   - 观察GC时间
   - 观察堆使用率

经验值：
- 堆使用率：60-80%
- Full GC频率：< 1次/小时
- GC停顿时间：< 200ms
```

### Q3: 元空间大小如何设置？

**A**:
```
评估方法：
1. 统计类数量
   jmap -clstats <pid>

2. 计算元空间需求
   类数量 × 平均类大小（约1KB）

3. 预留空间
   实际需求 × 1.5

示例：
- 10000个类
- 元空间需求：10MB
- 推荐配置：256MB（预留空间）
```

---

## 十、总结

### 10.1 必备参数清单

```bash
# 内存配置
-Xms<size> -Xmx<size>  # 堆大小
-Xmn<size>             # 新生代大小
-XX:MetaspaceSize=<size> -XX:MaxMetaspaceSize=<size>  # 元空间

# GC配置
-XX:+UseG1GC           # GC收集器
-XX:MaxGCPauseMillis=<ms>  # 停顿时间目标

# GC日志
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:<file>

# 诊断配置
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=<path>
```

### 10.2 配置原则

1. ✅ 堆大小固定（-Xms = -Xmx）
2. ✅ 选择合适的GC收集器
3. ✅ 开启GC日志
4. ✅ 配置OOM堆转储
5. ✅ 压力测试验证
6. ✅ 持续监控调优

---

**下一篇**：[性能指标与监控](./02_性能指标与监控.md)
