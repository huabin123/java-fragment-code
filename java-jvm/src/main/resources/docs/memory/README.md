# JVM内存模型与管理

## 📚 模块简介

本模块深入讲解JVM内存结构、对象内存布局、内存分配策略以及内存溢出分析，帮助你全面掌握JVM内存管理。

## 🎯 学习目标

- ✅ 理解JVM内存结构的设计原理
- ✅ 掌握对象在内存中的布局
- ✅ 理解内存分配和回收机制
- ✅ 能够分析和排查内存问题
- ✅ 掌握内存监控和优化技巧

## 📂 目录结构

```
memory/
├── docs/                                      # 文档目录
│   ├── 01_JVM内存结构详解.md                  # JVM内存区域划分
│   ├── 02_对象内存布局与分配.md                # 对象内存布局
│   └── 03_内存溢出分析与排查.md                # OOM分析
├── demo/                                      # 演示代码
│   ├── MemoryStructureDemo.java              # 内存结构演示
│   ├── ObjectLayoutDemo.java                 # 对象布局演示
│   └── OOMDemo.java                           # 内存溢出演示
├── project/                                   # 实战项目
│   ├── MemoryMonitor.java                     # 内存监控工具
│   └── SimpleMemoryPool.java                  # 简单内存池实现
└── README.md                                  # 本文件
```

## 🚀 快速开始

### 1. 学习JVM内存结构

**阅读文档**：`docs/01_JVM内存结构详解.md`

**运行Demo**：
```bash
javac MemoryStructureDemo.java
java -Xms256m -Xmx256m -Xss256k MemoryStructureDemo
```

**关键问题**：
- ❓ JVM内存为什么要分区？
- ❓ 程序计数器的作用是什么？
- ❓ 栈和堆的区别是什么？
- ❓ 为什么要分代？

### 2. 学习对象内存布局

**阅读文档**：`docs/02_对象内存布局与分配.md`

**运行Demo**：
```bash
# 需要添加JOL依赖
java -XX:+UseCompressedOops ObjectLayoutDemo
```

**关键问题**：
- ❓ 对象在内存中如何存储？
- ❓ 对象头包含什么信息？
- ❓ 为什么需要对齐填充？
- ❓ 如何计算对象大小？

### 3. 学习内存溢出分析

**阅读文档**：`docs/03_内存溢出分析与排查.md`

**运行Demo**：
```bash
# 堆内存溢出
java -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError OOMDemo

# 栈溢出
java -Xss256k OOMDemo
```

**关键问题**：
- ❓ 有哪些OOM类型？
- ❓ 如何排查内存泄漏？
- ❓ 如何分析堆转储文件？

### 4. 实战项目

#### 4.1 内存监控工具

**运行项目**：
```bash
cd project
javac MemoryMonitor.java
java com.example.jvm.memory.project.MemoryMonitor
```

**功能**：
- 实时监控堆内存
- 监控元空间
- 监控GC情况
- 监控线程情况
- 内存告警

#### 4.2 简单内存池

**运行项目**：
```bash
cd project
javac SimpleMemoryPool.java
java com.example.jvm.memory.project.SimpleMemoryPool
```

**功能**：
- 对象池化，减少对象创建
- 支持对象复用
- 自动扩容和收缩
- 线程安全
- 性能监控

**应用场景**：
- 频繁创建和销毁的对象（如字节数组、StringBuilder）
- 创建成本高的对象（如数据库连接、线程）
- 需要控制对象数量的场景

## 📊 核心知识点

### 1. JVM内存结构

```
JVM内存
    ↓
┌────────────────────────────────┐
│  线程私有                       │
│  - 程序计数器                   │
│  - 虚拟机栈                     │
│  - 本地方法栈                   │
├────────────────────────────────┤
│  线程共享                       │
│  - 堆（新生代、老年代）         │
│  - 方法区（元空间）             │
├────────────────────────────────┤
│  直接内存                       │
└────────────────────────────────┘
```

### 2. 对象内存布局

```
对象 = 对象头 + 实例数据 + 对齐填充

对象头：
- Mark Word（8字节）
- 类型指针（4字节/8字节）
- 数组长度（4字节，仅数组）

实例数据：
- 字段数据

对齐填充：
- 补齐到8字节的倍数
```

### 3. 对象分配流程

```
创建对象
    ↓
1. 尝试在Eden区分配
    ↓
2. Eden空间不足，触发Minor GC
    ↓
3. 存活对象移到Survivor
    ↓
4. 年龄达到阈值，晋升到老年代
```

### 4. 常见OOM类型

| OOM类型 | 原因 | 解决方案 |
|--------|------|---------|
| Java heap space | 堆内存不足 | 增大堆内存、排查内存泄漏 |
| Metaspace | 元空间不足 | 增大元空间、减少类加载 |
| Direct buffer memory | 直接内存不足 | 增大直接内存、及时释放 |
| unable to create new native thread | 线程数过多 | 减少线程数、使用线程池 |
| StackOverflowError | 栈溢出 | 增大栈大小、优化递归 |

## 🛠️ 工具使用

### 1. jmap - 堆转储

```bash
# 生成堆转储
jmap -dump:format=b,file=heap.hprof <pid>

# 查看堆内存
jmap -heap <pid>

# 查看对象统计
jmap -histo <pid>
```

### 2. JOL - 对象布局

```xml
<dependency>
    <groupId>org.openjdk.jol</groupId>
    <artifactId>jol-core</artifactId>
    <version>0.16</version>
</dependency>
```

```java
import org.openjdk.jol.info.ClassLayout;

Object obj = new Object();
System.out.println(ClassLayout.parseInstance(obj).toPrintable());
```

### 3. MAT - 内存分析

1. 下载MAT：https://www.eclipse.org/mat/
2. 打开堆转储文件
3. 查看Leak Suspects
4. 分析Dominator Tree
5. 定位内存泄漏

## 💡 最佳实践

### 1. 内存配置

```bash
# 堆内存配置
-Xms4g -Xmx4g  # 初始和最大相同，避免动态扩展

# 新生代配置
-Xmn1g  # 新生代大小
-XX:SurvivorRatio=8  # Eden:Survivor=8:1

# 元空间配置
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# 栈配置
-Xss1m

# OOM时生成堆转储
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/path/to/dump
```

### 2. 预防内存泄漏

1. ✅ 及时释放资源（使用try-with-resources）
2. ✅ 注销监听器
3. ✅ 清理ThreadLocal
4. ✅ 避免静态集合持有大量对象
5. ✅ 使用弱引用/软引用
6. ✅ 定期清理缓存

### 3. 对象大小优化

```java
// ❌ 不推荐：使用包装类
public class User {
    private Long id;      // 对象 + 对象头
    private Integer age;  // 对象 + 对象头
}

// ✅ 推荐：使用基本类型
public class User {
    private long id;      // 8字节
    private int age;      // 4字节
}
```

### 4. 字段对齐优化

```java
// ❌ 未优化：产生更多填充
public class Data {
    private byte a;   // 1字节
    private long b;   // 8字节
    private byte c;   // 1字节
}

// ✅ 优化：减少填充
public class Data {
    private long b;   // 8字节
    private byte a;   // 1字节
    private byte c;   // 1字节
}
```

## 📝 常见问题

### Q1: 堆内存设置多大合适？

**A**: 
- 根据应用需求和可用内存决定
- 一般设置为物理内存的1/2到2/3
- 初始堆大小和最大堆大小设置相同
- 留足够内存给操作系统和其他进程

### Q2: 如何判断是否发生内存泄漏？

**A**:
1. 观察堆内存使用趋势（持续增长）
2. Full GC后内存仍然很高
3. 使用MAT分析堆转储
4. 查找大对象和对象引用链

### Q3: 为什么要开启指针压缩？

**A**:
- 节省内存（引用从8字节降到4字节）
- 提高缓存命中率
- 可以表示32GB堆内存
- JDK 8默认开启

### Q4: TLAB是什么？

**A**:
- Thread Local Allocation Buffer
- 线程私有的内存分配缓冲区
- 减少线程竞争，提高分配速度
- 默认开启

## 🎓 进阶学习

### 1. 深入理解对象分配

- 指针碰撞 vs 空闲列表
- TLAB分配机制
- 大对象直接进入老年代
- 动态年龄判定

### 2. 内存泄漏案例分析

- 静态集合持有对象
- 监听器未注销
- ThreadLocal未清理
- 资源未关闭

### 3. 内存优化技巧

- 对象池复用
- 字段重排序
- 使用基本类型
- 减少对象创建

## 📚 参考资料

1. **《深入理解Java虚拟机（第3版）》** - 周志明
2. **JVM规范** - https://docs.oracle.com/javase/specs/
3. **OpenJDK源码** - https://openjdk.java.net/
4. **JOL工具** - https://openjdk.java.net/projects/code-tools/jol/

## 🔗 相关模块

- [类加载机制](../classloader/README.md)
- [垃圾回收](../gc/README.md)
- [性能调优](../tuning/README.md)

---

**下一步**：学习[类加载机制](../classloader/README.md)
