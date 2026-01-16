### 模块说明

**java-core** - Java核心技术学习
- `modifiers/` - Java修饰符学习
- `threadpool/` - JDK8线程池源码深度学习
- `annotations/` - Java注解深度学习（问题驱动式）
- `bitwise/` - 位运算深度学习（通俗易懂） ⭐️ **NEW**

**java-ratelimter** - 单机限流

**java-excel** - Excel处理

**java-file** - 文件处理

**java-juc** - Java并发工具类

---

## 🔥 最新更新：位运算深度学习（通俗易懂）

位置：`java-core/src/main/java/com/fragment/core/bitwise/`

### 学习特色

本教程采用**通俗易懂**的方式，从零开始学习位运算：
- 🎯 用生活中的例子类比抽象概念（开关灯、筛子等）
- 🖼️ 用图示展示位运算过程
- 💻 每个知识点都有可运行的代码
- 🚀 提供实际应用场景（权限管理、状态管理等）

### 内容概览

#### 📚 通俗易懂的文档（3章）
1. **01_位运算基础.md** - 从零开始
   - 什么是位运算？（用开关灯的例子）
   - 二进制基础（如何看懂二进制）
   - 6种基本位运算符详解
   - 负数的二进制表示（补码）
   - 常见误区和陷阱

2. **02_位运算进阶技巧.md** - 实用技巧
   - 15个常用位运算技巧
   - 位掩码（Bitmask）的使用
   - 位运算优化技巧
   - 性能对比分析

3. **03_位运算实战应用.md** - 实际应用
   - 权限管理系统
   - 状态管理
   - 数据压缩
   - 算法优化
   - LeetCode经典题目

#### 💻 演示代码（3个）
1. **BitwiseBasicDemo.java** - 基础演示
   - 6种位运算符的使用
   - 二进制可视化输出
   - 负数的补码演示
   - 常见陷阱演示

2. **BitwiseSkillsDemo.java** - 技巧演示
   - 15个实用技巧的代码实现
   - 位掩码的使用
   - 性能对比测试

3. **BitwisePracticalDemo.java** - 实战演示
   - 权限管理系统实现
   - 状态管理器实现
   - 布隆过滤器实现
   - BitSet的使用

### 快速开始

```bash
# 运行基础演示
cd java-core/src/main/java/com/fragment/core/bitwise/demo
javac BitwiseBasicDemo.java
java com.fragment.core.bitwise.demo.BitwiseBasicDemo

# 运行技巧演示
javac BitwiseSkillsDemo.java
java com.fragment.core.bitwise.demo.BitwiseSkillsDemo

# 运行实战演示
javac BitwisePracticalDemo.java
java com.fragment.core.bitwise.demo.BitwisePracticalDemo
```

### 核心亮点

#### 1. 通俗易懂的讲解
用生活中的例子类比抽象概念：
- 用"开关灯"理解位运算
- 用"筛子"理解位掩码
- 用"权限卡"理解权限管理

#### 2. 可视化展示
每个位运算都有二进制的可视化输出，让你直观看到运算过程。

#### 3. 实际应用场景
不仅教你怎么用，更重要的是告诉你什么时候用、为什么用。

#### 4. 完整的代码示例
所有示例都可以直接运行，包含详细的中文注释。

### 学习路径

1. **第一步**：阅读 `01_位运算基础.md`，理解二进制和6种位运算符
2. **第二步**：运行 `BitwiseBasicDemo.java`，观察位运算的实际效果
3. **第三步**：阅读 `02_位运算进阶技巧.md`，掌握15个实用技巧
4. **第四步**：运行 `BitwiseSkillsDemo.java`，理解技巧的应用
5. **第五步**：阅读 `03_位运算实战应用.md`，学习实际项目应用
6. **第六步**：运行 `BitwisePracticalDemo.java`，观察实战案例

详细内容请查看：[bitwise/README.md](java-core/src/main/java/com/fragment/core/bitwise/README.md)

---

## Java注解深度学习（问题驱动式）

位置：`java-core/src/main/java/com/fragment/core/annotations/`

### 学习特色

本教程采用**问题驱动**的方式，通过一系列核心问题引导你深入理解注解：

#### 核心问题
1. ❓ **为什么需要注解？** - 从XML配置的痛点说起
2. ❓ **如何定义注解？** - 注解的语法和元注解
3. ❓ **注解如何工作？** - 从编译到运行的完整流程
4. ❓ **如何处理注解？** - 反射、APT、字节码增强
5. ❓ **实际如何应用？** - 依赖注入、ORM、数据验证
6. ❓ **有哪些陷阱？** - 10+个常见陷阱和解决方案

### 内容概览

#### 📚 问题驱动式文档（4章）
1. **01_为什么需要注解.md** - 问题的起源
   - XML配置的痛点
   - 注解的价值
   - 注解的本质
   - 适用场景

2. **02_注解的定义与元注解.md** - 注解的基础
   - 注解的定义语法
   - 8种支持的属性类型
   - 5个元注解详解
   - @Retention、@Target、@Inherited、@Repeatable

3. **03_注解的工作原理.md** - 深入原理
   - 注解是接口
   - 三个处理阶段（SOURCE/CLASS/RUNTIME）
   - 反射API详解
   - 动态代理机制
   - 性能优化

4. **04_注解的实际应用与陷阱.md** - 实战与避坑
   - 实现依赖注入
   - 实现ORM映射
   - 10+个常见陷阱
   - 最佳实践
   - 注解 vs 其他方案

#### 📝 演示代码（2个）
1. **AnnotationBasicDemo.java** - 基础演示
   - 读取类/字段/方法上的注解
   - 注解的继承
   - 可重复注解

2. **AnnotationProcessorDemo.java** - 处理器演示
   - 简单IoC容器实现
   - 简单ORM框架实现
   - 数据验证器实现
   - 缓存管理器实现

#### 🚀 实际项目Demo（1个）
**ValidationFramework.java** - 完整的数据验证框架
- ✅ 10+种验证注解
- ✅ 支持级联验证
- ✅ 支持分组验证
- ✅ 详细的错误信息
- ✅ 可直接用于生产环境

### 快速开始

```bash
# 运行基础演示
cd java-core/src/main/java/com/fragment/core/annotations/demo
javac AnnotationBasicDemo.java
java com.fragment.core.annotations.demo.AnnotationBasicDemo

# 运行处理器演示
javac AnnotationProcessorDemo.java
java com.fragment.core.annotations.demo.AnnotationProcessorDemo

# 运行项目Demo
cd ../project
javac ValidationFramework.java
java com.fragment.core.annotations.project.ValidationFramework
```

### 核心亮点

#### 1. 问题驱动式学习
每一章都从实际问题出发，循序渐进地引导你理解注解的原理和应用。

#### 2. 完整的代码示例
所有示例都可以直接运行，包含详细的中文注释。

#### 3. 实际项目Demo
提供了一个完整的数据验证框架，可以直接用于实际项目。

#### 4. 深入原理剖析
不仅教你怎么用，更重要的是让你理解为什么这样设计。

### 学习路径

1. **第一步**：阅读文档，理解核心概念
2. **第二步**：运行演示代码，观察执行流程
3. **第三步**：阅读项目Demo，学习实际应用
4. **第四步**：尝试实现自己的注解处理器

详细内容请查看：[annotations/README.md](java-core/src/main/java/com/fragment/core/annotations/README.md)

---

## JDK8线程池源码深度学习

位置：`java-core/src/main/java/com/fragment/core/threadpool/`

### 内容概览

#### 📝 调试示例代码
1. **ThreadPoolExecutorDebug.java** - 核心源码调试示例
   - 线程池状态管理（ctl的巧妙设计）
   - 任务提交流程
   - 线程复用机制
   - 拒绝策略
   - 优雅关闭

2. **WorkerInternalsDemo.java** - Worker内部类深度解析
   - Worker的锁机制
   - Worker的生命周期
   - getTask的超时机制

3. **PracticalApplicationDemo.java** - 实际应用场景
   - 异步任务处理
   - 批量数据处理
   - 定时任务调度
   - 并行计算
   - 线程池监控
   - 动态调整参数

4. **EnhancedThreadPoolExecutor.java** - 生产级线程池实现
   - 任务执行时间统计
   - 异常捕获和处理
   - 任务超时控制
   - 详细统计信息
   - 优雅关闭

#### 📚 详细文档
1. **01_线程池核心原理.md** - 核心原理深度解析
2. **02_源码巧妙设计点.md** - 源码中的巧妙设计
3. **03_实战应用指南.md** - 实际项目应用指南

### 快速开始

```bash
# 运行核心调试示例
cd java-core/src/main/java/com/fragment/core/threadpool
javac ThreadPoolExecutorDebug.java
java com.fragment.core.threadpool.ThreadPoolExecutorDebug

# 或在IDE中直接运行main方法
```

### 核心亮点

#### 1. ctl的巧妙设计
用一个`AtomicInteger`同时维护线程池状态和工作线程数，实现原子性操作。

#### 2. Worker的不可重入锁
通过继承AQS实现不可重入锁，准确判断线程是否空闲。

#### 3. getTask的超时机制
利用阻塞队列的超时特性，优雅实现线程自动回收。

### 学习建议

1. 先阅读文档了解核心概念
2. 运行调试示例，通过断点观察执行流程
3. 学习源码中的巧妙设计
4. 应用到实际项目中

详细内容请查看：[threadpool/README.md](java-core/src/main/java/com/fragment/core/threadpool/README.md)
