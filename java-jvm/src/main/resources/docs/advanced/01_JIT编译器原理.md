# JIT编译器原理

## 📚 概述

JIT（Just-In-Time）即时编译器是JVM性能优化的核心技术之一。它在程序运行时将热点代码编译成本地机器码，显著提升执行效率。本文从架构师视角深入讲解JIT编译器的工作原理和优化技术。

## 🎯 核心问题

- ❓ 为什么需要JIT编译器？解释执行不够吗？
- ❓ JIT编译器如何工作？什么时候触发编译？
- ❓ C1和C2编译器有什么区别？
- ❓ 什么是分层编译？为什么需要它？
- ❓ JIT编译器有哪些优化技术？
- ❓ 如何查看和分析JIT编译日志？

---

## 一、为什么需要JIT编译器

### 1.1 解释执行的问题

```
Java程序执行流程：
.java源文件
    ↓
javac编译
    ↓
.class字节码
    ↓
JVM解释执行
    ↓
机器码

问题：
1. 解释执行慢
   - 每次执行都要翻译字节码
   - 无法进行深度优化
   - 性能损失严重

2. 与C/C++性能差距大
   - C/C++直接编译成机器码
   - Java需要逐条解释
   - 性能差距10-100倍
```

### 1.2 JIT编译器的价值

```
引入JIT编译器后：

.class字节码
    ↓
┌───┴───┐
│       │
解释执行  JIT编译
│       │
│       ↓
│    本地机器码
│       │
└───┬───┘
    ↓
执行结果

优势：
1. 热点代码编译成机器码
   - 执行速度接近C/C++
   - 性能提升10-100倍

2. 运行时优化
   - 根据实际运行情况优化
   - 比静态编译更智能
   - 可以做激进优化

3. 平衡启动时间和执行效率
   - 解释执行快速启动
   - JIT编译提升性能
```

### 1.3 JIT vs AOT

```
JIT（Just-In-Time）即时编译：
- 运行时编译
- 可以做运行时优化
- 启动慢，运行快

AOT（Ahead-Of-Time）提前编译：
- 编译时编译
- 启动快，运行慢
- 无法做运行时优化

对比：
┌──────────┬──────────┬──────────┐
│          │   JIT    │   AOT    │
├──────────┼──────────┼──────────┤
│ 启动时间  │   慢     │   快     │
│ 运行性能  │   快     │   慢     │
│ 内存占用  │   高     │   低     │
│ 优化能力  │   强     │   弱     │
└──────────┴──────────┴──────────┘

Java选择JIT的原因：
1. 长期运行的服务器应用
2. 需要最佳运行性能
3. 可以接受启动开销
```

---

## 二、JIT编译器工作原理

### 2.1 整体架构

```
JIT编译器架构

字节码
    ↓
解释执行 + 性能监控
    ↓
热点探测
    ↓
是热点代码？
    ↓
  是 │ 否
    │  └→ 继续解释执行
    ↓
选择编译器
    ↓
┌───┴───┐
│       │
C1编译  C2编译
│       │
└───┬───┘
    ↓
本地代码缓存
    ↓
执行机器码
```

### 2.2 热点探测

#### 什么是热点代码？

```
热点代码（Hot Spot）：
1. 被多次调用的方法
2. 被多次执行的循环体

判断标准：
- 方法调用计数器
- 回边计数器（循环）
```

#### 热点探测方式

```java
/**
 * 方法调用计数器
 */
public class MethodCounter {
    
    // 每个方法都有一个调用计数器
    private int invocationCounter = 0;
    
    // 阈值（可配置）
    private static final int THRESHOLD = 10000;
    
    public void method() {
        invocationCounter++;
        
        if (invocationCounter >= THRESHOLD) {
            // 触发JIT编译
            compileMethod();
        }
        
        // 方法体
    }
}

/**
 * 回边计数器
 */
public class BackEdgeCounter {
    
    // 循环回边计数器
    private int backEdgeCounter = 0;
    
    // 阈值
    private static final int THRESHOLD = 10000;
    
    public void loopMethod() {
        for (int i = 0; i < 1000000; i++) {
            backEdgeCounter++;
            
            if (backEdgeCounter >= THRESHOLD) {
                // 触发OSR（On-Stack Replacement）编译
                compileLoop();
            }
            
            // 循环体
        }
    }
}
```

### 2.3 编译触发条件

```
触发JIT编译的条件：

1. 方法调用次数达到阈值
   -XX:CompileThreshold=10000（C2）
   -XX:Tier3CompileThreshold=2000（C1）

2. 回边次数达到阈值
   -XX:OnStackReplacePercentage=140

3. 分层编译策略
   Level 0: 解释执行
   Level 1: C1编译（无profiling）
   Level 2: C1编译（有profiling）
   Level 3: C1编译（完整profiling）
   Level 4: C2编译

流程：
解释执行（Level 0）
    ↓
C1编译（Level 1-3）
    ↓
收集运行时信息
    ↓
C2编译（Level 4）
```

---

## 三、C1和C2编译器

### 3.1 C1编译器（Client Compiler）

```
特点：
- 编译速度快
- 优化程度低
- 适合客户端应用

优化技术：
1. 方法内联
2. 去虚拟化
3. 冗余消除
4. 简单的循环优化

使用场景：
- 桌面应用
- 短期运行的程序
- 需要快速启动的应用

JVM参数：
-client
-XX:+TieredCompilation
-XX:TieredStopAtLevel=1
```

### 3.2 C2编译器（Server Compiler）

```
特点：
- 编译速度慢
- 优化程度高
- 适合服务器应用

优化技术：
1. 激进的方法内联
2. 逃逸分析
3. 标量替换
4. 栈上分配
5. 同步消除
6. 循环展开
7. 循环剥离
8. 范围检查消除
9. 空值检查消除

使用场景：
- 服务器应用
- 长期运行的程序
- 对性能要求高的应用

JVM参数：
-server
-XX:+TieredCompilation
-XX:TieredStopAtLevel=4
```

### 3.3 C1 vs C2 对比

```
┌──────────────┬──────────┬──────────┐
│              │    C1    │    C2    │
├──────────────┼──────────┼──────────┤
│ 编译速度      │   快     │   慢     │
│ 优化程度      │   低     │   高     │
│ 编译时间      │  毫秒级  │  秒级    │
│ 代码质量      │   一般   │   优秀   │
│ 适用场景      │  客户端  │  服务器  │
└──────────────┴──────────┴──────────┘

示例：
方法执行1000次

C1编译：
- 编译时间：10ms
- 执行时间：100ms
- 总时间：110ms

C2编译：
- 编译时间：100ms
- 执行时间：50ms
- 总时间：150ms

方法执行10000次

C1编译：
- 编译时间：10ms
- 执行时间：1000ms
- 总时间：1010ms

C2编译：
- 编译时间：100ms
- 执行时间：500ms
- 总时间：600ms

结论：
- 短期运行：C1更优
- 长期运行：C2更优
```

---

## 四、分层编译

### 4.1 什么是分层编译

```
分层编译（Tiered Compilation）：
结合C1和C2的优势
在不同阶段使用不同编译器

目标：
1. 快速启动（C1）
2. 最佳性能（C2）
3. 平衡编译开销

JDK 7引入，JDK 8默认开启
```

### 4.2 编译层级

```
Level 0: 解释执行
- 收集方法调用次数
- 收集回边次数

Level 1: C1编译，无profiling
- 快速编译
- 基本优化
- 不收集运行时信息

Level 2: C1编译，有限profiling
- 收集部分运行时信息
- 为C2编译做准备

Level 3: C1编译，完整profiling
- 收集完整运行时信息
- 类型信息、分支信息等

Level 4: C2编译
- 最高级别优化
- 基于Level 3收集的信息
- 生成最优代码
```

### 4.3 分层编译流程

```
典型流程：

方法首次调用
    ↓
Level 0: 解释执行
    ↓
调用次数达到阈值
    ↓
Level 3: C1编译（完整profiling）
    ↓
继续执行，收集运行时信息
    ↓
调用次数再次达到阈值
    ↓
Level 4: C2编译
    ↓
执行优化后的机器码

特殊情况：
1. 简单方法：Level 0 → Level 1
2. 热点方法：Level 0 → Level 3 → Level 4
3. 超级热点：Level 0 → Level 4（跳过C1）
```

### 4.4 分层编译的优势

```
优势1：快速响应
- C1快速编译
- 立即提升性能
- 不等待C2编译

优势2：收集信息
- Level 3收集运行时信息
- 为C2提供优化依据
- 做出更好的优化决策

优势3：降低风险
- 激进优化可能失败
- 可以回退到C1代码
- 保证程序正确性

示例：
public void process(Object obj) {
    // Level 3收集类型信息
    // 发现obj总是String类型
    
    // Level 4基于此信息优化
    // 去虚拟化，内联String方法
    obj.toString();
}
```

---

## 五、JIT优化技术

### 5.1 方法内联

```
什么是方法内联？
将方法调用替换为方法体

示例：
// 优化前
public int add(int a, int b) {
    return a + b;
}

public int calculate() {
    int result = add(1, 2);  // 方法调用
    return result;
}

// 优化后
public int calculate() {
    int result = 1 + 2;  // 内联后
    return result;
}

优势：
1. 消除方法调用开销
2. 暴露更多优化机会
3. 提升执行效率

限制：
- 方法体不能太大（-XX:MaxInlineSize=35字节）
- 不能是native方法
- 不能有异常处理（某些情况）
```

### 5.2 逃逸分析

```
什么是逃逸分析？
分析对象的作用域范围

逃逸类型：
1. 方法逃逸：对象被返回或传递给其他方法
2. 线程逃逸：对象被其他线程访问

示例：
// 不逃逸
public void noEscape() {
    Point p = new Point(1, 2);
    int sum = p.x + p.y;
    // p只在方法内使用
}

// 方法逃逸
public Point methodEscape() {
    Point p = new Point(1, 2);
    return p;  // 逃逸
}

// 线程逃逸
public void threadEscape() {
    Point p = new Point(1, 2);
    this.globalPoint = p;  // 逃逸
}

基于逃逸分析的优化：
1. 栈上分配
2. 标量替换
3. 同步消除
```

### 5.3 标量替换

```
什么是标量替换？
将对象拆分成基本类型

示例：
// 优化前
public int sum() {
    Point p = new Point(1, 2);
    return p.x + p.y;
}

// 优化后（标量替换）
public int sum() {
    int x = 1;
    int y = 2;
    return x + y;
}

优势：
1. 不需要在堆上分配对象
2. 减少GC压力
3. 提升执行效率

条件：
- 对象不逃逸
- 对象字段可以拆分
- 开启逃逸分析
```

### 5.4 栈上分配

```
什么是栈上分配？
在栈上分配对象，而不是堆上

示例：
public void stackAllocation() {
    // 对象不逃逸
    Point p = new Point(1, 2);
    int sum = p.x + p.y;
    // 方法结束，栈帧销毁，对象自动回收
}

优势：
1. 分配速度快（栈分配比堆分配快）
2. 自动回收（随栈帧销毁）
3. 减少GC压力

注意：
- JVM实际上是通过标量替换实现的
- 并非真正在栈上分配对象
- 效果等同于栈上分配
```

### 5.5 同步消除

```
什么是同步消除？
消除不必要的同步操作

示例：
// 优化前
public void syncElimination() {
    Object lock = new Object();  // 不逃逸
    synchronized (lock) {
        // 同步块
    }
}

// 优化后
public void syncElimination() {
    // 同步消除
    // 直接执行代码
}

条件：
- 锁对象不逃逸
- 只有一个线程访问
- 开启逃逸分析

优势：
- 消除同步开销
- 提升执行效率
```

### 5.6 循环优化

```
循环展开（Loop Unrolling）：

// 优化前
for (int i = 0; i < 4; i++) {
    sum += array[i];
}

// 优化后
sum += array[0];
sum += array[1];
sum += array[2];
sum += array[3];

循环剥离（Loop Peeling）：

// 优化前
for (int i = 0; i < n; i++) {
    if (i == 0) {
        // 特殊处理
    } else {
        // 正常处理
    }
}

// 优化后
if (n > 0) {
    // 特殊处理（i == 0）
}
for (int i = 1; i < n; i++) {
    // 正常处理
}

循环不变量外提：

// 优化前
for (int i = 0; i < n; i++) {
    int x = a + b;  // 循环不变
    sum += x * i;
}

// 优化后
int x = a + b;  // 提到循环外
for (int i = 0; i < n; i++) {
    sum += x * i;
}
```

---

## 六、JIT编译日志分析

### 6.1 开启编译日志

```bash
# 基本编译日志
-XX:+PrintCompilation

# 详细编译日志
-XX:+UnlockDiagnosticVMOptions
-XX:+PrintInlining
-XX:+PrintCodeCache
-XX:+PrintAssembly  # 需要hsdis插件

# 输出到文件
-XX:+LogCompilation
-XX:LogFile=compilation.log
```

### 6.2 日志格式

```
编译日志格式：
timestamp compilation_id attributes (tiered_level) method_name size deopt

示例：
    100   1       3       java.lang.String::hashCode (55 bytes)
    │     │       │       │                           │
    │     │       │       │                           └─ 字节码大小
    │     │       │       └─ 方法名
    │     │       └─ 编译层级
    │     └─ 编译ID
    └─ 时间戳（ms）

attributes含义：
%  - OSR编译（On-Stack Replacement）
s  - 同步方法
!  - 有异常处理
b  - 阻塞模式编译
n  - native方法包装
```

### 6.3 分析示例

```
示例日志：
    100   1       3       java.lang.String::hashCode (55 bytes)
    150   2       4       com.example.Test::calculate (120 bytes)
    200   3   %   3       com.example.Test::loop @ 5 (200 bytes)
    250   2       4       com.example.Test::calculate (120 bytes)   made not entrant

分析：
1. 100ms时，String.hashCode被C1编译（Level 3）
2. 150ms时，Test.calculate被C2编译（Level 4）
3. 200ms时，Test.loop的循环被OSR编译（Level 3）
4. 250ms时，Test.calculate的Level 4代码被标记为不可进入
   （可能是去优化，或者有更优的版本）
```

---

## 七、JIT编译器配置

### 7.1 常用参数

```bash
# 编译阈值
-XX:CompileThreshold=10000          # C2编译阈值
-XX:Tier3CompileThreshold=2000      # C1编译阈值

# 分层编译
-XX:+TieredCompilation              # 开启分层编译（默认）
-XX:TieredStopAtLevel=1             # 只使用C1
-XX:TieredStopAtLevel=4             # 使用C1+C2

# 内联
-XX:MaxInlineSize=35                # 最大内联方法大小
-XX:FreqInlineSize=325              # 热点方法内联大小
-XX:MaxInlineLevel=9                # 最大内联层数

# 逃逸分析
-XX:+DoEscapeAnalysis               # 开启逃逸分析（默认）
-XX:+EliminateAllocations           # 标量替换
-XX:+EliminateLocks                 # 同步消除

# 编译线程
-XX:CICompilerCount=3               # 编译线程数
```

### 7.2 禁用JIT

```bash
# 禁用JIT编译（纯解释执行）
-Xint

# 禁用解释执行（纯编译执行）
-Xcomp

# 混合模式（默认）
-Xmixed
```

### 7.3 性能对比

```
测试代码：计算1-1000000的和

纯解释执行（-Xint）：
- 执行时间：1000ms
- 启动时间：100ms

纯编译执行（-Xcomp）：
- 执行时间：50ms
- 启动时间：500ms

混合模式（默认）：
- 执行时间：60ms
- 启动时间：150ms

结论：
- 解释执行：启动快，运行慢
- 编译执行：启动慢，运行快
- 混合模式：平衡启动和性能
```

---

## 八、去优化（Deoptimization）

### 8.1 什么是去优化

```
去优化（Deoptimization）：
将已编译的代码回退到解释执行

原因：
1. 激进优化的假设不成立
2. 类层次结构变化
3. 代码热度下降

示例：
public void process(Animal animal) {
    animal.eat();  // 假设animal总是Dog
}

// JIT编译时，假设animal总是Dog类型
// 内联Dog.eat()方法

// 运行时，传入Cat对象
// 假设不成立，触发去优化
// 回退到解释执行
```

### 8.2 去优化类型

```
1. Uncommon Trap（不常见陷阱）
   - 执行到不常见的代码路径
   - 如：类型检查失败

2. Made Not Entrant（不可进入）
   - 代码被标记为不可进入
   - 新的调用不会执行此代码
   - 已在执行的继续执行

3. Made Zombie（僵尸）
   - 代码完全失效
   - 等待GC回收

流程：
编译代码
    ↓
Made Not Entrant
    ↓
等待所有执行完成
    ↓
Made Zombie
    ↓
GC回收
```

---

## 九、总结

### 9.1 JIT编译器关键点

```
1. 为什么需要JIT？
   - 解释执行慢
   - 需要接近C/C++的性能
   - 平衡启动和运行效率

2. 如何工作？
   - 热点探测
   - 分层编译
   - 运行时优化

3. 核心技术
   - C1/C2编译器
   - 方法内联
   - 逃逸分析
   - 循环优化

4. 如何配置？
   - 编译阈值
   - 内联参数
   - 逃逸分析开关
```

### 9.2 最佳实践

```
1. 使用默认配置
   - 分层编译已经很好
   - 不要轻易修改

2. 关注编译日志
   - 了解哪些方法被编译
   - 发现性能瓶颈

3. 避免过度优化
   - 代码清晰优先
   - JIT会自动优化

4. 预热应用
   - 让JIT有时间编译
   - 达到最佳性能
```

---

**下一篇**：[JVM优化技术](./02_JVM优化技术.md)
