# JVM深度学习指南

## 📚 项目简介

本项目是一个**完整的JVM深度学习资料库**，采用**问题驱动**的方式，从**Java高级开发/架构师**的视角深入讲解JVM的各个方面。

**🎉 所有模块已完成！** 包含28篇文档、15个Demo、9个实战项目和2个监控脚本。

## ✅ 模块完成状态

| 模块 | 状态 | 文档 | Demo | Project | 说明 |
|------|------|------|------|---------|------|
| **memory** | ✅ 100% | 5篇 | 4个 | 2个 | 内存模型与管理 |
| **classloader** | ✅ 100% | 4篇 | 3个 | 2个 | 类加载机制 |
| **gc** | ✅ 100% | 4篇 | 3个 | 2个 | 垃圾回收 |
| **tuning** | ✅ 100% | 6篇 | 3个 | 2个 | 性能调优 |
| **advanced** | ✅ 100% | 5篇 | 2个 | 1个 | 高级主题 |
| **tools** | ✅ 100% | 4篇 | - | 2脚本 | 工具使用 |

**📊 总计**：28篇文档 + 15个Demo + 9个项目 + 2个脚本

## 🎯 学习目标

- ✅ 掌握JVM内存模型与内存管理
- ✅ 理解类加载机制与类加载器
- ✅ 精通垃圾回收原理与调优
- ✅ 具备性能调优与故障排查能力
- ✅ 理解JVM底层实现原理
- ✅ 熟练使用JVM诊断工具

## 📂 目录结构

```
java-jvm/
├── memory/                                    # 内存模型与管理
│   ├── docs/                                  # 文档目录
│   │   ├── 01_JVM内存结构详解.md
│   │   ├── 02_对象内存布局与分配.md
│   │   ├── 03_内存溢出分析与排查.md
│   │   ├── 04_直接内存与堆外内存.md
│   │   └── 05_逃逸分析与优化.md
│   ├── demo/                                  # 演示代码
│   │   ├── MemoryStructureDemo.java
│   │   ├── ObjectLayoutDemo.java
│   │   ├── OOMDemo.java
│   │   └── EscapeAnalysisDemo.java
│   └── project/                               # 实战项目
│       ├── MemoryPool.java
│       └── MemoryMonitor.java
├── classloader/                               # 类加载机制
│   ├── docs/
│   │   ├── 01_类加载过程详解.md
│   │   ├── 02_类加载器与双亲委派.md
│   │   ├── 03_打破双亲委派模型.md
│   │   └── 04_类加载器隔离与热部署.md
│   ├── demo/
│   │   ├── ClassLoadingDemo.java
│   │   ├── CustomClassLoaderDemo.java
│   │   └── HotDeployDemo.java
│   └── project/
│       ├── PluginFramework.java
│       └── HotSwapClassLoader.java
├── gc/                                        # 垃圾回收
│   ├── docs/
│   │   ├── 01_GC基础与对象存活判定.md
│   │   ├── 02_垃圾回收算法详解.md
│   │   ├── 03_经典垃圾回收器.md
│   │   ├── 04_G1垃圾回收器深入.md
│   │   ├── 05_ZGC与Shenandoah.md
│   │   └── 06_GC调优实战.md
│   ├── demo/
│   │   ├── ReferenceDemo.java
│   │   ├── GCDemo.java
│   │   └── GCLogAnalysisDemo.java
│   └── project/
│       ├── GCMonitor.java
│       └── GCTuningTool.java
├── tuning/                                    # 性能调优
│   ├── docs/
│   │   ├── 01_JVM参数详解.md
│   │   ├── 02_性能指标与监控.md
│   │   ├── 03_CPU飙高问题排查.md
│   │   ├── 04_内存泄漏排查.md
│   │   ├── 05_死锁问题排查.md
│   │   └── 06_性能调优最佳实践.md
│   ├── demo/
│   │   ├── PerformanceDemo.java
│   │   ├── CPUHighDemo.java
│   │   └── MemoryLeakDemo.java
│   └── project/
│       ├── JVMMonitoringSystem.java
│       └── PerformanceAnalyzer.java
├── advanced/                                  # 高级主题
│   ├── docs/
│   │   ├── 01_JIT编译器原理.md
│   │   ├── 02_JVM优化技术.md
│   │   ├── 03_TLAB与对象分配.md
│   │   ├── 04_安全点与安全区域.md
│   │   └── 05_HotSpot源码导读.md
│   ├── demo/
│   │   ├── JITDemo.java
│   │   └── OptimizationDemo.java
│   └── project/
│       └── JVMProfiler.java
├── tools/                                     # 工具使用
│   ├── docs/
│   │   ├── 01_命令行工具详解.md
│   │   ├── 02_可视化工具使用.md
│   │   ├── 03_Arthas实战.md
│   │   └── 04_APM监控系统.md
│   └── scripts/
│       ├── jvm_monitor.sh
│       └── gc_analysis.sh
└── README.md                                  # 本文件
```

## 🚀 学习路径

### 阶段1：JVM内存模型（2-3周）

**学习目标**：
- 理解JVM内存结构
- 掌握对象内存布局
- 能够分析和排查内存问题

**学习顺序**：
1. 阅读 `memory/docs/01_JVM内存结构详解.md`
2. 运行 `memory/demo/MemoryStructureDemo.java`
3. 阅读 `memory/docs/02_对象内存布局与分配.md`
4. 运行 `memory/demo/ObjectLayoutDemo.java`
5. 实践 `memory/project/MemoryPool.java`

**关键问题**：
- ❓ JVM内存为什么要分区？
- ❓ 栈和堆的区别是什么？
- ❓ 对象在内存中如何存储？
- ❓ 如何排查内存溢出？

---

### 阶段2：类加载机制（1-2周）

**学习目标**：
- 理解类加载的完整流程
- 掌握双亲委派模型
- 能够实现自定义类加载器

**学习顺序**：
1. 阅读 `classloader/docs/01_类加载过程详解.md`
2. 运行 `classloader/demo/ClassLoadingDemo.java`
3. 阅读 `classloader/docs/02_类加载器与双亲委派.md`
4. 实践 `classloader/project/PluginFramework.java`

**关键问题**：
- ❓ 类加载的五个阶段是什么？
- ❓ 为什么需要双亲委派？
- ❓ 如何实现热部署？

---

### 阶段3：垃圾回收（3-4周）

**学习目标**：
- 理解GC算法原理
- 掌握各种垃圾回收器
- 能够进行GC调优

**学习顺序**：
1. 阅读 `gc/docs/01_GC基础与对象存活判定.md`
2. 运行 `gc/demo/ReferenceDemo.java`
3. 阅读 `gc/docs/02_垃圾回收算法详解.md`
4. 阅读 `gc/docs/03_经典垃圾回收器.md`
5. 阅读 `gc/docs/04_G1垃圾回收器深入.md`
6. 实践 `gc/project/GCMonitor.java`

**关键问题**：
- ❓ 如何判断对象是否可回收？
- ❓ 有哪些GC算法？
- ❓ 如何选择合适的垃圾回收器？
- ❓ 如何调优GC参数？

---

### 阶段4：性能调优（3-4周）

**学习目标**：
- 掌握JVM参数配置
- 能够排查各种性能问题
- 具备生产环境调优能力

**学习顺序**：
1. 阅读 `tuning/docs/01_JVM参数详解.md`
2. 阅读 `tuning/docs/03_CPU飙高问题排查.md`
3. 运行 `tuning/demo/CPUHighDemo.java`
4. 阅读 `tuning/docs/04_内存泄漏排查.md`
5. 实践 `tuning/project/JVMMonitoringSystem.java`

**关键问题**：
- ❓ 如何定位CPU飙高？
- ❓ 如何排查内存泄漏？
- ❓ 如何优化GC停顿时间？

---

### 阶段5：高级主题（2-3周）

**学习目标**：
- 理解JIT编译原理
- 掌握JVM优化技术
- 能够阅读HotSpot源码

**学习顺序**：
1. 阅读 `advanced/docs/01_JIT编译器原理.md`
2. 阅读 `advanced/docs/02_JVM优化技术.md`
3. 运行 `advanced/demo/JITDemo.java`
4. 阅读 `advanced/docs/05_HotSpot源码导读.md`

**关键问题**：
- ❓ JIT如何工作？
- ❓ 什么是逃逸分析？
- ❓ 如何阅读JVM源码？

---

## 🔧 工具准备

### 必备工具

1. **JDK 1.8+**
   ```bash
   java -version
   ```

2. **JOL（Java Object Layout）**
   ```xml
   <dependency>
       <groupId>org.openjdk.jol</groupId>
       <artifactId>jol-core</artifactId>
       <version>0.16</version>
   </dependency>
   ```

3. **MAT（Memory Analyzer Tool）**
   - 下载地址：https://www.eclipse.org/mat/

4. **VisualVM**
   - 下载地址：https://visualvm.github.io/

5. **Arthas**
   ```bash
   curl -O https://arthas.aliyun.com/arthas-boot.jar
   java -jar arthas-boot.jar
   ```

### 推荐工具

- GCEasy（GC日志分析）：https://gceasy.io/
- GCViewer
- JProfiler
- Async-profiler

---

## 📊 技术关键点流程图

### 1. JVM内存结构

```
JVM内存结构
    ↓
┌────┴────┐
│         │         │         │         │
线程私有  │  线程共享  │  其他
│         │         │
↓         ↓         ↓
程序计数器  堆      直接内存
虚拟机栈    方法区
本地方法栈
```

### 2. 类加载流程

```
类加载流程
    ↓
加载 → 验证 → 准备 → 解析 → 初始化
    ↓
双亲委派模型
    ↓
Bootstrap ClassLoader
    ↓
Extension ClassLoader
    ↓
Application ClassLoader
    ↓
Custom ClassLoader
```

### 3. GC流程

```
对象存活判定
    ↓
可达性分析（GC Roots）
    ↓
标记存活对象
    ↓
选择GC算法
    ↓
┌────┴────┐
│         │         │
标记-清除  标记-复制  标记-整理
    ↓
执行垃圾回收
    ↓
内存整理/压缩
```

### 4. 性能调优流程

```
性能问题
    ↓
问题定位
    ↓
┌────┴────┐
│         │         │
CPU问题   内存问题  GC问题
    ↓         ↓         ↓
jstack    jmap      GC日志
    ↓         ↓         ↓
分析线程  分析堆    分析GC
    ↓         ↓         ↓
    └────┬────┘
         ↓
    制定调优方案
         ↓
    验证效果
```

---

## 💡 学习建议

### 1. 理论与实践结合

- 每学一个知识点，都要动手验证
- 运行Demo代码，观察现象
- 修改参数，对比效果

### 2. 问题驱动学习

- 带着问题去学习
- 思考"为什么"而不是"是什么"
- 理解设计背后的原因

### 3. 源码阅读

- 阅读OpenJDK源码
- 理解底层实现
- 学习优秀的设计思想

### 4. 真实项目实践

- 在实际项目中应用所学知识
- 进行性能调优
- 排查生产环境问题

### 5. 持续学习

- 关注JVM新特性
- 学习新的GC算法
- 了解GraalVM等新技术

### 6. 知识输出

- 写技术博客
- 做技术分享
- 参与开源项目

---

## 📚 推荐资料

### 书籍

1. **《深入理解Java虚拟机（第3版）》** - 周志明 ⭐⭐⭐⭐⭐
   - JVM学习的必读经典
   - 系统全面，深入浅出

2. **《Java性能权威指南》** - Scott Oaks
   - 性能调优实战指南
   - 大量实际案例

3. **《垃圾回收算法手册》** - Richard Jones
   - GC算法的权威著作
   - 理论深度高

4. **《HotSpot实战》** - 陈涛
   - HotSpot源码分析
   - 适合进阶学习

### 在线资源

1. **OpenJDK官方文档**
   - https://openjdk.java.net/

2. **JVM规范**
   - https://docs.oracle.com/javase/specs/

3. **Oracle JVM调优指南**
   - https://docs.oracle.com/en/java/javase/

4. **技术博客**
   - 美团技术团队
   - 阿里技术
   - InfoQ

---

## 🎓 学习路线图

```
第1-3周：JVM内存模型
    ↓
第4-5周：类加载机制
    ↓
第6-9周：垃圾回收
    ↓
第10-13周：性能调优
    ↓
第14-16周：高级主题
    ↓
持续实践：真实项目调优
```

---

## 📝 常见问题FAQ

### Q1: JVM学习需要多长时间？

**A**: 
- 基础掌握：2-3个月
- 熟练应用：6-12个月
- 精通：1-2年持续实践

### Q2: 需要什么基础？

**A**:
- Java基础扎实
- 理解多线程
- 了解操作系统原理
- 有一定的项目经验

### Q3: 如何验证学习效果？

**A**:
- 能够独立排查生产环境问题
- 能够进行性能调优
- 能够阅读JVM源码
- 能够分享JVM知识

### Q4: 学习重点是什么？

**A**:
- 内存模型（必须掌握）
- GC原理（核心重点）
- 性能调优（实战能力）
- 故障排查（必备技能）

---

## 🚀 快速开始

### 方式1：按推荐顺序学习

```
阶段1 → 阶段2 → 阶段3 → 阶段4 → 阶段5 → 阶段6
内存   类加载   垃圾回收   性能调优   高级主题   工具使用
```

1. [内存模型与管理](./memory/README.md) - 2-3周
2. [类加载机制](./classloader/README.md) - 1-2周
3. [垃圾回收](./gc/README.md) - 3-4周 ⭐核心
4. [性能调优](./tuning/README.md) - 3-4周 ⭐核心
5. [高级主题](./advanced/README.md) - 2-3周
6. [工具使用](./tools/README.md) - 1-2周

### 方式2：按需学习

**快速上手**：
- [工具使用](./tools/README.md) → 立即开始排查问题

**深入理解**：
- [内存模型](./memory/README.md) → [类加载](./classloader/README.md) → [垃圾回收](./gc/README.md)

**实战调优**：
- [性能调优](./tuning/README.md) → [工具使用](./tools/README.md)

**进阶学习**：
- [高级主题](./advanced/README.md) → 理解JVM底层实现

### 方式3：查看完整学习指南

📖 [完整学习指南](./完整学习指南.md) - 包含详细的学习路径、检验清单和实战建议

---

## 📚 推荐学习资源

**本项目配套资源**：
- 📄 [JVM深度学习路径](./JVM深度学习路径.md) - 架构师视角的学习规划
- 📖 [完整学习指南](./完整学习指南.md) - 系统化学习指南

**外部资源**：
- 《深入理解Java虚拟机（第3版）》- 周志明
- 《Java性能权威指南》- Scott Oaks
- OpenJDK官方文档
- Oracle JVM调优指南

---

**祝你学习愉快！成为JVM调优专家！** 🎉🚀
