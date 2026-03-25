# 性能调优（Tuning）

## 📚 模块简介

本模块深入讲解JVM性能调优的方法论、实战技巧和最佳实践，涵盖JVM参数配置、性能监控、问题排查和调优实战等核心内容。

## 🎯 学习目标

- ✅ 掌握JVM参数配置
- ✅ 建立性能监控体系
- ✅ 能够排查CPU、内存、死锁等问题
- ✅ 掌握性能调优方法论
- ✅ 具备生产环境调优能力
- ✅ 能够进行性能分析和优化

## 📂 目录结构

```
tuning/
├── docs/                                      # 文档目录
│   ├── 01_JVM参数详解.md                       # JVM参数配置
│   ├── 02_性能指标与监控.md                    # 性能监控体系
│   ├── 03_CPU飙高问题排查.md                   # CPU问题排查
│   ├── 04_内存泄漏排查.md                      # 内存泄漏排查
│   ├── 05_死锁问题排查.md                      # 死锁问题排查
│   └── 06_性能调优最佳实践.md                  # 调优最佳实践
├── demo/                                      # 演示代码
│   ├── PerformanceDemo.java                   # 性能监控演示
│   ├── CPUHighDemo.java                       # CPU问题演示
│   └── MemoryLeakDemo.java                    # 内存泄漏演示
├── project/                                   # 实战项目
│   ├── JVMMonitoringSystem.java               # JVM监控系统
│   └── PerformanceAnalyzer.java               # 性能分析器
└── README.md                                  # 本文件
```

## 🚀 快速开始

### 1. 学习JVM参数

**阅读文档**：`docs/01_JVM参数详解.md`

**关键问题**：
- ❓ 为什么需要配置JVM参数？
- ❓ 如何选择合适的堆内存大小？
- ❓ 如何选择合适的GC收集器？
- ❓ 生产环境必备的JVM参数有哪些？

**推荐配置**：
```bash
# 中型应用（4-8GB堆）
java -server \
  -Xms8g -Xmx8g \
  -Xmn4g \
  -Xss1m \
  -XX:MetaspaceSize=512m \
  -XX:MaxMetaspaceSize=1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:/path/to/gc.log \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/path/to/dumps \
  -jar app.jar
```

### 2. 建立性能监控

**阅读文档**：`docs/02_性能指标与监控.md`

**运行Demo**：
```bash
cd demo
javac PerformanceDemo.java
java com.example.jvm.tuning.demo.PerformanceDemo
```

**关键指标**：
- CPU使用率
- 堆内存使用率
- GC频率和时间
- 线程数
- 响应时间

### 3. 排查CPU飙高

**阅读文档**：`docs/03_CPU飙高问题排查.md`

**运行Demo**：
```bash
cd demo
javac CPUHighDemo.java
java com.example.jvm.tuning.demo.CPUHighDemo
```

**排查步骤**：
```bash
# 1. 查看CPU使用率
top

# 2. 找到占用CPU高的线程
top -H -p <pid>

# 3. 转换线程ID为十六进制
printf "%x\n" <线程ID>

# 4. 导出线程栈
jstack <pid> > thread.txt

# 5. 查找对应线程
grep <十六进制线程ID> thread.txt
```

### 4. 排查内存泄漏

**阅读文档**：`docs/04_内存泄漏排查.md`

**运行Demo**：
```bash
cd demo
javac MemoryLeakDemo.java
java com.example.jvm.tuning.demo.MemoryLeakDemo
```

**排查步骤**：
```bash
# 1. 观察内存趋势
jstat -gcutil <pid> 1000

# 2. 生成堆转储
jmap -dump:format=b,file=heap.hprof <pid>

# 3. 使用MAT分析
# 打开heap.hprof
# 查看Leak Suspects
# 分析引用链
```

### 5. 排查死锁

**阅读文档**：`docs/05_死锁问题排查.md`

**排查步骤**：
```bash
# 1. 导出线程栈
jstack <pid> > thread.txt

# 2. 查找死锁信息
grep -A 20 "deadlock" thread.txt

# 3. 分析死锁线程
# 查看持有的锁
# 查看等待的锁
# 定位代码位置
```

### 6. 实战项目

#### 6.1 JVM监控系统

**运行项目**：
```bash
cd project
javac JVMMonitoringSystem.java
java com.example.jvm.tuning.project.JVMMonitoringSystem
```

**功能特性**：
- ✅ 实时监控JVM性能指标
- ✅ 异常检测和告警
- ✅ 性能数据收集和存储
- ✅ 生成监控报告

#### 6.2 性能分析器

**运行项目**：
```bash
cd project
javac PerformanceAnalyzer.java
java com.example.jvm.tuning.project.PerformanceAnalyzer
```

**功能特性**：
- ✅ CPU热点分析
- ✅ 内存分析
- ✅ GC分析
- ✅ 线程分析
- ✅ 性能瓶颈定位

## 📊 核心知识点

### 1. JVM参数分类

| 分类 | 格式 | 示例 | 说明 |
|------|------|------|------|
| **标准参数** | `-` | `-version` | 所有JVM支持 |
| **非标准参数** | `-X` | `-Xms`, `-Xmx` | 大部分JVM支持 |
| **不稳定参数** | `-XX` | `-XX:+UseG1GC` | 特定JVM支持 |

### 2. 性能指标体系

```
性能指标
    ↓
┌────┴────┬────┬────┐
│         │    │    │
系统级    JVM级  应用级  业务级

系统级：CPU、内存、磁盘、网络
JVM级：堆内存、GC、线程、类加载
应用级：QPS、响应时间、错误率
业务级：订单量、转化率、GMV
```

### 3. 常见问题排查

| 问题 | 现象 | 排查工具 | 解决方案 |
|------|------|---------|---------|
| **CPU飙高** | CPU 90%+ | top, jstack | 定位热点代码，优化算法 |
| **内存泄漏** | 内存持续增长 | jmap, MAT | 找到泄漏对象，修复代码 |
| **死锁** | 应用无响应 | jstack | 调整加锁顺序，使用tryLock |
| **频繁GC** | GC时间长 | GC日志 | 调整堆大小，优化代码 |

### 4. 调优方法论

```
调优流程：
1. 建立基线
2. 分析问题
3. 制定方案
4. 实施优化
5. 验证效果
6. 持续优化

调优原则：
1. 先测量，后优化
2. 优化最大瓶颈
3. 一次优化一个点
4. 在测试环境验证
5. 持续监控
```

## 🛠️ 常用工具

### 1. 命令行工具

```bash
# JVM进程
jps -l                    # 查看Java进程

# GC统计
jstat -gc <pid> 1000      # 查看GC统计
jstat -gcutil <pid> 1000  # 查看GC百分比

# 内存分析
jmap -heap <pid>          # 查看堆信息
jmap -histo <pid>         # 查看对象统计
jmap -dump:format=b,file=heap.hprof <pid>  # 生成堆转储

# 线程分析
jstack <pid>              # 查看线程栈
jstack -l <pid>           # 查看线程栈（含锁信息）

# JVM参数
jinfo -flags <pid>        # 查看JVM参数
jinfo -flag <name> <pid>  # 查看特定参数
```

### 2. 可视化工具

- **JConsole**：JDK自带，实时监控
- **VisualVM**：功能强大，支持插件
- **MAT**：堆转储分析，查找内存泄漏
- **JProfiler**：商业工具，功能最全
- **Arthas**：在线诊断工具

### 3. APM工具

- **Prometheus + Grafana**：开源监控方案
- **SkyWalking**：分布式追踪和APM
- **Pinpoint**：分布式追踪
- **Cat**：美团开源APM

## 💡 最佳实践

### 1. JVM参数配置

```bash
# 基本原则
-Xms = -Xmx              # 避免动态扩展
-Xmn = 堆的1/3到1/2      # 新生代大小
MetaspaceSize设置合理    # 避免频繁Full GC

# 必备参数
-XX:+PrintGCDetails      # GC日志
-XX:+HeapDumpOnOutOfMemoryError  # OOM时堆转储
-Xloggc:gc.log          # GC日志文件
```

### 2. 性能监控

```
监控层次：
1. 基础监控：CPU、内存、磁盘、网络
2. JVM监控：堆内存、GC、线程
3. 应用监控：QPS、响应时间、错误率
4. 业务监控：订单量、转化率

告警策略：
P0 - 紧急：服务不可用
P1 - 重要：性能严重下降
P2 - 一般：性能下降
P3 - 提示：潜在问题
```

### 3. 问题排查

```
排查流程：
1. 确认问题（监控数据）
2. 收集信息（日志、线程栈、堆转储）
3. 分析原因（工具分析）
4. 定位代码（源码分析）
5. 修复问题
6. 验证效果

常用命令组合：
# CPU问题
top → top -H -p <pid> → jstack <pid>

# 内存问题
jstat -gcutil <pid> → jmap -dump → MAT分析

# 死锁问题
jstack <pid> → 查找deadlock
```

### 4. 代码优化

```java
// 1. 减少对象创建
String str = "hello";  // 好
String str = new String("hello");  // 不好

// 2. 使用StringBuilder
StringBuilder sb = new StringBuilder();  // 好
String str = "";
for (...) { str += "a"; }  // 不好

// 3. 设置集合初始容量
List<String> list = new ArrayList<>(1000);  // 好
List<String> list = new ArrayList<>();  // 不好

// 4. 及时释放资源
try (Connection conn = getConnection()) {
    // 使用连接
}  // 自动关闭

// 5. 使用并发工具类
ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();  // 好
Map<String, Object> map = Collections.synchronizedMap(new HashMap<>());  // 不好
```

## 📝 常见问题

### Q1: 如何确定堆内存大小？

**A**:
```
评估方法：
1. 并发用户数 × 单用户内存
2. 缓存数据大小
3. 临时对象大小

经验值：
- 堆使用率：60-80%
- Full GC频率：< 1次/小时
- GC停顿时间：< 200ms

示例：
- 1000并发用户
- 每用户10MB
- 需要10GB堆内存
```

### Q2: 如何选择GC收集器？

**A**:
```
选择依据：
1. 堆大小
   - < 4GB：Parallel GC / CMS
   - 4-32GB：G1 GC
   - > 32GB：ZGC

2. 延迟要求
   - 高吞吐量：Parallel GC
   - 低延迟：G1 GC / ZGC

3. 应用类型
   - 批处理：Parallel GC
   - Web应用：G1 GC
   - 超大堆：ZGC
```

### Q3: 如何判断是否需要调优？

**A**:
```
需要调优的情况：
1. 频繁Full GC（> 1次/小时）
2. GC停顿时间长（> 1秒）
3. 应用响应慢
4. 内存使用率过高（> 90%）
5. 出现OOM

调优前提：
1. 有明确的性能问题
2. 有监控数据支撑
3. 在测试环境验证
```

## 🎓 进阶学习

### 1. 性能调优案例

参考 `docs/06_性能调优最佳实践.md` 中的实战案例：
- 电商秒杀系统调优（QPS提升6倍）
- 大数据处理系统调优（处理时间降低75%）

### 2. 深入学习方向

- JVM源码分析
- GC算法原理
- JIT编译优化
- 操作系统调优
- 分布式系统调优

### 3. 实战练习

1. 搭建监控系统
2. 模拟性能问题
3. 排查和解决问题
4. 总结优化经验
5. 分享最佳实践

## 📚 参考资料

1. **《深入理解Java虚拟机（第3版）》** - 周志明
2. **《Java性能权威指南》** - Scott Oaks
3. **《Java性能调优实战》** - 刘超
4. **Oracle JVM调优指南** - https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/
5. **GCEasy** - https://gceasy.io/

## 🔗 相关模块

- [JVM内存模型](../memory/README.md)
- [类加载机制](../classloader/README.md)
- [垃圾回收](../gc/README.md)

---

**学习建议**：
1. 先掌握JVM参数配置
2. 建立性能监控体系
3. 学习问题排查方法
4. 实践调优案例
5. 总结最佳实践
6. 持续优化改进
