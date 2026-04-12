# 第一章：JIT 编译器原理

## 1.1 解释执行 vs JIT 编译

Java 代码的执行方式经历了两个阶段：

```
.java → javac → .class（字节码）→ JVM 执行

方式1：解释执行（Interpreter）
  逐条读取字节码 → 翻译为机器码 → 执行
  优点：启动快，内存占用少
  缺点：每次都要翻译，反复执行的代码效率低

方式2：JIT（Just-In-Time Compilation）
  发现"热点代码"→ 一次性编译为本地机器码 → 后续直接执行机器码
  优点：执行速度接近 C++，重复执行的代码极快
  缺点：编译需要时间（warmup），编译后的代码占内存

JVM 默认两者结合：
  启动时解释执行（快启动）
  热点代码触发 JIT 编译（高性能）
```

---

## 1.2 热点探测与编译阈值

```java
// JITDemo.java → demonstrateJITCompilation()
// 演示：方法调用多次后触发 JIT 编译

for (int i = 0; i < 20_000; i++) {
    calculate(i);  // 调用足够多次后，JVM 将 calculate() 编译为本地代码
}
// 通过 -XX:+PrintCompilation 可观察到编译日志

// 热点探测的两种方式：
// 1. 方法调用计数器：调用次数超过阈值 → 编译该方法
// 2. 回边计数器：循环体执行次数超过阈值 → OSR（On-Stack Replacement，栈上替换）

// 触发编译的阈值参数：
// -XX:CompileThreshold=10000   方法调用计数阈值（默认1万次，Client模式1500次）
// -XX:OnStackReplacePercentage=140  回边计数器阈值 = CompileThreshold * ratio/100

// JVM 有两个 JIT 编译器：
// C1（Client Compiler）：编译快，优化少，适合桌面/短任务
// C2（Server Compiler）：编译慢，优化深，适合长期运行的服务
// 分层编译（Tiered Compilation，JDK 8+ 默认）：
//   Level 0：解释执行
//   Level 1-3：C1 编译（不同优化程度）
//   Level 4：C2 编译（最高优化）
```

---

## 1.3 JIT 的关键优化：方法内联

内联是 JIT 最重要的优化，也是其他优化的基础：

```java
// JITDemo.java → demonstrateInlining()

// 原始代码：
public static int add(int a, int b) {
    return a + b;
}
int result = add(3, 5);

// 内联后（JIT 在机器码层面等价于）：
int result = 3 + 5;  // 消除了方法调用开销（参数压栈、跳转、返回）

// 内联的条件：
// 1. 方法字节码不超过 35 字节（-XX:MaxInlineSize，可调）
// 2. 方法不是虚方法，或经过内联缓存确认是单态调用
// 3. 方法调用频繁（热点方法）

// 内联为什么是基础？
// 内联后代码变成一个整体 → 才能做常量折叠、无效代码消除等进一步优化
```

---

## 1.4 查看 JIT 编译情况

```bash
# 打印 JIT 编译日志
java -XX:+PrintCompilation -jar app.jar

# 典型输出：
#   timestamp  compilation_id  attributes  (tier)  method  (bytes)
    56    1       %    4       com.example.Main::hotLoop @ 2 (25 bytes)
    ↑     ↑       ↑    ↑       ↑
  时间戳  编译ID  OSR  C2级  被编译的方法

# % 表示 OSR（栈上替换，针对循环）
# ! 表示方法有异常处理器（影响优化）
# s 表示同步方法
# n 表示 native 方法（不编译）

# 打印内联信息
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -jar app.jar

# JDK 11+：更详细的 JIT 日志
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -jar app.jar
```

---

## 1.5 本章总结

- **JIT 的价值**：热点代码编译为本地机器码，消除解释执行开销，性能接近 C++
- **热点探测**：方法调用计数器（方法级）+ 回边计数器（循环级，触发 OSR）
- **两个编译器**：C1（快速，适合启动）、C2（深度优化，适合运行）；JDK 8+ 分层编译两者结合
- **方法内联**：JIT 最重要的优化，消除调用开销，同时是其他优化的前提
- **参数控制**：`-XX:CompileThreshold` 控制触发阈值，`-XX:+PrintCompilation` 查看编译日志

> **本章对应演示代码**：`JITDemo.java`（JIT 触发演示、内联演示、解释 vs JIT 性能对比）

**继续阅读**：[02_逃逸分析与标量替换.md](./02_逃逸分析与标量替换.md)
