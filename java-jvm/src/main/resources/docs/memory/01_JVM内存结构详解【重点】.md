# JVM内存结构详解

## 🔗 相关代码示例

本文档对应的代码示例位于：

### 📝 Demo代码
- **[MemoryStructureDemo.java](../../../java/com/fragment/jvm/memory/demo/MemoryStructureDemo.java)** - 内存结构演示
  - ✅ 堆内存结构演示
  - ✅ 栈内存演示
  - ✅ 方法区演示
  - ✅ 内存使用监控

**运行方式：**
```bash
# 基本运行
java -Xms256m -Xmx256m -Xss256k MemoryStructureDemo

# 查看内存详细信息
java -Xms256m -Xmx256m \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     MemoryStructureDemo

# 监控元空间
java -XX:MetaspaceSize=128m \
     -XX:MaxMetaspaceSize=256m \
     -XX:+TraceClassLoading \
     MemoryStructureDemo
```

### 🚀 项目代码
- **[MemoryMonitor.java](../../../java/com/fragment/jvm/memory/project/MemoryMonitor.java)** - 内存监控工具
  - ✅ 实时监控堆内存
  - ✅ 监控元空间
  - ✅ 监控GC情况
  - ✅ 监控线程情况

---

## 1. 为什么需要了解JVM内存结构？

### 1.1 问题1：不了解内存结构会遇到什么问题？

**实际场景**：

```java
// 场景1：StackOverflowError
public void recursion() {
    recursion(); // 无限递归
}

// 场景2：OutOfMemoryError: Java heap space
List<byte[]> list = new ArrayList<>();
while (true) {
    list.add(new byte[1024 * 1024]); // 不断创建对象
}

// 场景3：OutOfMemoryError: Metaspace
while (true) {
    // 动态生成类
}
```

**不了解内存结构的后果**：
- ❌ 无法理解为什么会发生这些错误
- ❌ 无法有效排查内存问题
- ❌ 无法进行合理的内存配置
- ❌ 无法优化程序性能

**了解内存结构的好处**：
- ✅ 理解内存分配机制
- ✅ 快速定位内存问题
```
┌─────────────────────────────────────────────────────────────┐
│                        JVM内存结构                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────┐        ┌──────────────────┐          │
│  │   线程私有区域    │        │   线程共享区域    │          │
│  ├──────────────────┤        ├──────────────────┤          │
│  │  程序计数器       │        │      堆          │          │
│  │  (PC Register)   │        │    (Heap)        │          │
│  ├──────────────────┤        │                  │          │
│  │  虚拟机栈         │        │  ┌────────────┐ │          │
│  │  (VM Stack)      │        │  │  新生代     │ │          │
│  ├──────────────────┤        │  │  (Young)    │ │          │
│  │  本地方法栈       │        │  │  - Eden     │ │          │
│  │  (Native Stack)  │        │  │  - S0       │ │          │
│  └──────────────────┘        │  │  - S1       │ │          │
│                              │  ├────────────┤ │          │
│                              │  │  老年代     │ │          │
│                              │  │  (Old)      │ │          │
│                              │  └────────────┘ │          │
│                              ├──────────────────┤          │
│                              │    方法区        │          │
│                              │  (Method Area)   │          │
│                              │  - 元空间        │          │
│                              │  (Metaspace)     │          │
│                              └──────────────────┘          │
│                                                              │
│  ┌──────────────────────────────────────────────┐          │
│  │           直接内存 (Direct Memory)             │          │
│  └──────────────────────────────────────────────┘          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**内存区域分类**：

| 区域 | 线程私有/共享 | 是否会OOM | 作用 |
|-----|-------------|----------|------|
| 程序计数器 | 私有 | ❌ 不会 | 记录当前线程执行的字节码行号 |
| 虚拟机栈 | 私有 | ✅ 会 | 存储局部变量、操作数栈等 |
| 本地方法栈 | 私有 | ✅ 会 | 为Native方法服务 |
| 堆 | 共享 | ✅ 会 | 存储对象实例 |
| 方法区 | 共享 | ✅ 会 | 存储类信息、常量、静态变量 |
| 直接内存 | - | ✅ 会 | NIO使用的堆外内存 |

---

## 3. 线程私有区域

### 3.1 程序计数器（Program Counter Register）

#### 问题3：程序计数器是什么？为什么需要它？

**定义**：程序计数器是一块较小的内存空间，可以看作是当前线程所执行的字节码的行号指示器。

**作用**：

```
线程执行流程
    ↓
字节码解释器工作
    ↓
通过改变程序计数器的值
    ↓
选取下一条需要执行的字节码指令
    ↓
分支、循环、跳转、异常处理、线程恢复等
```

**为什么需要程序计数器？**

```java
// 示例：多线程场景
public class PCDemo {
    private static int count = 0;
    
    public static void main(String[] args) {
        // 线程1执行
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                count++; // 执行到这里，PC记录字节码行号
            }
        });
        
        // 线程2执行
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                count++; // 执行到这里，PC记录字节码行号
            }
        });
        
        t1.start();
        t2.start();
    }
}
```

**多线程切换场景**：

```
时间轴：
T1: 线程1执行到字节码行号10
T2: CPU切换到线程2
T3: 线程2执行到字节码行号20
T4: CPU切换回线程1
T5: 线程1从行号10继续执行 ← 程序计数器记录了位置
```

**特点**：
1. ✅ 线程私有：每个线程都有独立的程序计数器
2. ✅ 不会OOM：唯一不会发生OutOfMemoryError的区域
3. ✅ 执行Java方法：记录正在执行的虚拟机字节码指令地址
4. ✅ 执行Native方法：计数器值为空（Undefined）

#### 问题3.1：为什么程序计数器不会发生OOM？

**根本原因**：程序计数器的内存空间是**固定且极小**的，不会动态增长。

**内存分配详解**：

```
程序计数器的内存特性
    ↓
┌─────────────────────────────────────────┐
│  1. 固定大小                             │
│     - 每个线程的PC只需存储一个地址        │
│     - 64位系统：8字节                    │
│     - 32位系统：4字节                    │
├─────────────────────────────────────────┤
│  2. 不会动态增长                         │
│     - 无论程序多复杂，PC永远只存一个值    │
│     - 不像栈会随着方法调用而增长          │
├─────────────────────────────────────────┤
│  3. 线程私有但总量可控                   │
│     - 单个线程PC：8字节                  │
│     - 1000个线程：8KB                    │
│     - 10000个线程：80KB                  │
└─────────────────────────────────────────┘
```

**与其他内存区域对比**：

| 内存区域 | 单线程大小 | 是否动态增长 | 是否会OOM | 原因 |
|---------|-----------|-------------|----------|------|
| **程序计数器** | **8字节** | **❌ 否** | **❌ 不会** | **固定大小，极小** |
| 虚拟机栈 | 1MB（默认） | ✅ 是（栈帧入栈） | ✅ 会 | 递归过深导致栈溢出 |
| 本地方法栈 | 1MB（默认） | ✅ 是 | ✅ 会 | Native方法调用过深 |
| 堆 | GB级别 | ✅ 是（对象创建） | ✅ 会 | 对象过多，内存不足 |
| 方法区 | MB级别 | ✅ 是（类加载） | ✅ 会 | 类过多，元空间不足 |

**内存消耗计算**：

```java
// 场景1：单线程应用
public class SingleThreadApp {
    public static void main(String[] args) {
        // 程序计数器内存消耗：
        // 1个线程 × 8字节 = 8字节
        
        while (true) {
            // 无论循环多少次，PC永远只占8字节
            doSomething();
        }
    }
}

// 场景2：多线程应用
public class MultiThreadApp {
    public static void main(String[] args) {
        // 创建1000个线程
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> {
                while (true) {
                    doSomething();
                }
            }).start();
        }
        
        // 程序计数器总内存消耗：
        // 1000个线程 × 8字节 = 8000字节 = 7.8KB
        // 
        // 对比虚拟机栈：
        // 1000个线程 × 1MB = 1000MB = 0.98GB
        // 
        // 结论：PC内存消耗可以忽略不计
    }
}
```

**极端场景分析**：

```
假设创建100万个线程（实际不可能）
    ↓
程序计数器内存消耗：
1,000,000 线程 × 8 字节 = 8,000,000 字节 = 7.6 MB
    ↓
虚拟机栈内存消耗：
1,000,000 线程 × 1 MB = 1,000,000 MB = 976 GB
    ↓
结论：
- PC内存消耗：7.6 MB（微不足道）
- 栈内存消耗：976 GB（早已OOM）
- 系统会因为栈内存不足而OOM，而不是PC
```

**底层实现原理**：

```c
// HotSpot虚拟机中的程序计数器实现（简化版）

// 线程结构体
struct JavaThread {
    // 程序计数器（只是一个指针）
    address _pc;  // 64位系统：8字节，32位系统：4字节
    
    // 其他线程信息
    JavaFrameAnchor _anchor;
    ThreadLocalStorage _tls;
    // ...
};

// 字节码执行
void BytecodeInterpreter::run() {
    // 获取当前线程的PC
    address pc = thread->pc();
    
    // 读取当前指令
    u1 opcode = *pc;
    
    // 执行指令
    switch (opcode) {
        case Bytecodes::_iconst_0:
            // 执行iconst_0指令
            stack->push(0);
            pc += 1;  // PC指向下一条指令
            break;
        case Bytecodes::_iload_1:
            // 执行iload_1指令
            int value = locals[1];
            stack->push(value);
            pc += 1;
            break;
        // ... 其他指令
    }
    
    // 更新PC
    thread->set_pc(pc);
}
```

**字节码与PC的关系**：

```java
// Java源码
public int add(int a, int b) {
    int c = a + b;
    return c;
}

// 编译后的字节码
public int add(int, int);
  Code:
   0: iload_1        // PC = 方法起始地址 + 0
   1: iload_2        // PC = 方法起始地址 + 1
   2: iadd           // PC = 方法起始地址 + 2
   3: istore_3       // PC = 方法起始地址 + 3
   4: iload_3        // PC = 方法起始地址 + 4
   5: ireturn        // PC = 方法起始地址 + 5

// PC的值示例（假设方法起始地址为0x1000）
执行到第0行：PC = 0x1000
执行到第1行：PC = 0x1001
执行到第2行：PC = 0x1002
执行到第3行：PC = 0x1003
执行到第4行：PC = 0x1004
执行到第5行：PC = 0x1005

// 无论方法多复杂，PC永远只存储一个地址值（8字节）
```

**多线程场景下的PC**：

```
时间轴：线程切换过程

T0: 线程1执行
    Thread-1.PC = 0x1003
    CPU执行Thread-1的指令

T1: 操作系统调度，切换到线程2
    保存Thread-1的上下文（包括PC = 0x1003）
    恢复Thread-2的上下文（包括PC = 0x2005）
    
T2: 线程2执行
    Thread-2.PC = 0x2005
    CPU执行Thread-2的指令
    
T3: 线程2执行下一条指令
    Thread-2.PC = 0x2006
    
T4: 操作系统调度，切换回线程1
    保存Thread-2的上下文（包括PC = 0x2006）
    恢复Thread-1的上下文（包括PC = 0x1003）
    
T5: 线程1继续执行
    Thread-1.PC = 0x1003（从上次暂停的位置继续）
    
关键点：
- 每个线程的PC独立存储
- 每个PC只占8字节
- 线程切换时保存/恢复PC
- PC不会随着程序执行而增长
```

**内存分配总结**：

```
┌─────────────────────────────────────────────────┐
│  程序计数器的内存特性                            │
├─────────────────────────────────────────────────┤
│  1. 每个线程的PC大小                             │
│     - 64位JVM：8字节                            │
│     - 32位JVM：4字节                            │
├─────────────────────────────────────────────────┤
│  2. 最大内存消耗（理论值）                       │
│     - 假设10,000个线程                          │
│     - 10,000 × 8字节 = 80KB                    │
│     - 实际上系统会因为栈内存不足而崩溃           │
├─────────────────────────────────────────────────┤
│  3. 为什么不会OOM                               │
│     ✅ 固定大小，不动态增长                      │
│     ✅ 内存消耗极小（KB级别）                    │
│     ✅ 即使创建大量线程，PC内存也可忽略不计       │
│     ✅ 系统会先因为栈内存不足而OOM               │
├─────────────────────────────────────────────────┤
│  4. 与其他区域的对比                             │
│     - PC：8字节/线程                            │
│     - 栈：1MB/线程（默认）                       │
│     - 堆：GB级别（所有线程共享）                 │
│     - 方法区：MB级别（所有线程共享）             │
└─────────────────────────────────────────────────┘
```

**实际测试**：

```java
public class PCMemoryTest {
    public static void main(String[] args) throws Exception {
        // 获取JVM内存信息
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        
        // 创建线程前的内存
        long beforeThreads = getThreadMemory();
        System.out.println("创建线程前内存: " + beforeThreads + " bytes");
        
        // 创建1000个线程
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            t.start();
            threads.add(t);
        }
        
        Thread.sleep(1000); // 等待线程启动
        
        // 创建线程后的内存
        long afterThreads = getThreadMemory();
        System.out.println("创建线程后内存: " + afterThreads + " bytes");
        
        // 内存增长
        long increase = afterThreads - beforeThreads;
        System.out.println("内存增长: " + increase + " bytes");
        System.out.println("平均每个线程: " + (increase / 1000) + " bytes");
        
        // 结果分析：
        // 内存增长约1GB（主要是虚拟机栈）
        // 1000个线程 × 1MB栈 = 1000MB
        // 程序计数器：1000 × 8字节 = 8KB（可忽略不计）
    }
    
    private static long getThreadMemory() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadMXBean.getAllThreadIds();
        long totalMemory = 0;
        for (long id : threadIds) {
            ThreadInfo info = threadMXBean.getThreadInfo(id);
            if (info != null) {
                // 注意：这里只能估算栈内存，PC内存太小无法直接测量
                totalMemory += threadMXBean.getThreadAllocatedBytes(id);
            }
        }
        return totalMemory;
    }
}

// 输出示例：
// 创建线程前内存: 1048576 bytes (1MB)
// 创建线程后内存: 1049624576 bytes (1001MB)
// 内存增长: 1048576000 bytes (1000MB)
// 平均每个线程: 1048576 bytes (1MB)
//
// 结论：每个线程增长约1MB，这是虚拟机栈的大小
//       程序计数器的8字节完全被淹没在测量误差中
```

**关键结论**：

```
为什么程序计数器不会OOM？
    ↓
1. 固定大小
   - 每个线程只需8字节（64位系统）
   - 不会随着程序复杂度增长
    ↓
2. 内存消耗极小
   - 即使10,000个线程也只需80KB
   - 相比栈的10GB，可以忽略不计
    ↓
3. 系统会先因其他原因OOM
   - 虚拟机栈内存不足（每线程1MB）
   - 堆内存不足（对象过多）
   - 元空间不足（类过多）
    ↓
4. JVM规范明确规定
   - 程序计数器是唯一不会OOM的区域
   - 因为它的内存需求是确定且极小的
```

---

### 3.2 虚拟机栈（Java Virtual Machine Stack）

#### 问题4：虚拟机栈是什么？它存储什么？

**定义**：虚拟机栈描述的是Java方法执行的内存模型。

**栈帧结构**：

```
虚拟机栈
    ↓
┌─────────────────────┐
│   栈帧3 (方法3)      │ ← 栈顶（当前执行的方法）
├─────────────────────┤
│   栈帧2 (方法2)      │
├─────────────────────┤
│   栈帧1 (方法1)      │
└─────────────────────┘

每个栈帧包含：
┌─────────────────────┐
│   局部变量表         │ ← 存储方法参数和局部变量
├─────────────────────┤
│   操作数栈          │ ← 进行算术运算和方法调用
├─────────────────────┤
│   动态链接          │ ← 指向运行时常量池的方法引用
├─────────────────────┤
│   方法返回地址       │ ← 方法退出后返回的位置
└─────────────────────┘
```

**示例代码**：

```java
public class StackDemo {
    public static void main(String[] args) {
        int a = 1;
        int b = 2;
        int c = add(a, b);
        System.out.println(c);
    }
    
    public static int add(int x, int y) {
        int result = x + y;
        return result;
    }
}
```

**执行流程**：

```
1. main方法入栈
   栈帧：
   - 局部变量表：[args, a=1, b=2, c=未初始化]
   - 操作数栈：[]

2. 调用add方法，add方法入栈
   栈帧：
   - 局部变量表：[x=1, y=2, result=未初始化]
   - 操作数栈：[]

3. add方法执行完毕，返回结果3
   - add方法出栈
   - 返回值3压入main方法的操作数栈

4. main方法继续执行
   - c = 3
   - 打印c

5. main方法执行完毕，出栈
```

**局部变量表详解**：

```
局部变量表
    ↓
存储内容：
- 基本数据类型（boolean、byte、char、short、int、float、long、double）
- 对象引用（reference类型）
- returnAddress类型（指向字节码指令地址）

存储单位：
- Slot（变量槽）
- long和double占用2个Slot
- 其他类型占用1个Slot

示例：
public void method(int a, long b, Object c) {
    // 局部变量表：
    // Slot 0: this（实例方法）
    // Slot 1: a (int, 1个Slot)
    // Slot 2-3: b (long, 2个Slot)
    // Slot 4: c (引用, 1个Slot)
}
```

#### 问题4.1：运行时常量池 vs 局部变量表 - 有什么不同？

这是两个完全不同的内存区域，存储的内容和用途都不同。

**核心区别对比**：

| 特性 | 局部变量表 | 运行时常量池 |
|------|-----------|-------------|
| **所属区域** | 虚拟机栈（栈帧中） | 方法区（元空间） |
| **线程私有/共享** | 线程私有 | 线程共享 |
| **生命周期** | 方法调用期间 | 类加载到卸载 |
| **存储内容** | 方法的局部变量、参数 | 字面量、符号引用 |
| **数据来源** | 运行时动态产生 | 编译期确定 |
| **是否可变** | 可变（运行时修改） | 部分可变（可动态添加） |
| **大小** | 编译期确定 | 可动态扩展 |

**详细对比分析**：

```java
public class ComparisonDemo {
    // 这个字符串字面量存储在运行时常量池
    private static final String CONSTANT = "Hello";
    
    public void method(int param) {
        // 局部变量表存储：
        // - this引用
        // - param参数
        // - localVar局部变量
        // - str引用（指向常量池中的"World"）
        
        int localVar = 100;        // 存储在局部变量表
        String str = "World";      // "World"存储在常量池，str引用存储在局部变量表
        
        // 运行时常量池存储：
        // - "Hello"字面量
        // - "World"字面量
        // - 类名、方法名、字段名等符号引用
    }
}
```

**1. 局部变量表详解**

```
┌─────────────────────────────────────────────────┐
│              局部变量表                          │
├─────────────────────────────────────────────────┤
│  位置：虚拟机栈 → 栈帧 → 局部变量表              │
├─────────────────────────────────────────────────┤
│  存储内容：                                      │
│  1. 方法参数                                     │
│     - 实例方法：this + 参数                      │
│     - 静态方法：参数                             │
│  2. 局部变量                                     │
│     - 基本类型：直接存储值                       │
│     - 引用类型：存储对象引用（指针）             │
├─────────────────────────────────────────────────┤
│  特点：                                          │
│  ✅ 线程私有（每个线程独立）                     │
│  ✅ 方法执行时创建，结束时销毁                   │
│  ✅ 编译期确定大小                               │
│  ✅ 存储运行时的临时数据                         │
└─────────────────────────────────────────────────┘
```

**示例：局部变量表的内容**

```java
public class LocalVariableTableExample {
    public int calculate(int a, int b) {
        int sum = a + b;           // sum存储在局部变量表
        int product = a * b;       // product存储在局部变量表
        String result = "Result";  // result引用存储在局部变量表
        return sum + product;
    }
}

// 字节码
public int calculate(int, int);
  Code:
   0: iload_1          // 从局部变量表Slot 1加载a
   1: iload_2          // 从局部变量表Slot 2加载b
   2: iadd             // 相加
   3: istore_3         // 存储到局部变量表Slot 3（sum）
   4: iload_1          // 从局部变量表Slot 1加载a
   5: iload_2          // 从局部变量表Slot 2加载b
   6: imul             // 相乘
   7: istore 4         // 存储到局部变量表Slot 4（product）
   9: ldc #2           // 从常量池加载"Result"
  11: astore 5         // 引用存储到局部变量表Slot 5（result）
  13: iload_3          // 从局部变量表加载sum
  14: iload 4          // 从局部变量表加载product
  16: iadd
  17: ireturn

// 局部变量表布局：
LocalVariableTable:
  Start  Length  Slot  Name   Signature
      0      18     0  this   LLocalVariableTableExample;
      0      18     1     a   I
      0      18     2     b   I
      4      14     3   sum   I
      9       9     4 product I
     13       5     5 result Ljava/lang/String;
```

**2. 运行时常量池详解**

```
┌─────────────────────────────────────────────────┐
│            运行时常量池                          │
├─────────────────────────────────────────────────┤
│  位置：方法区（元空间）                          │
├─────────────────────────────────────────────────┤
│  存储内容：                                      │
│  1. 字面量（Literal）                            │
│     - 文本字符串："Hello", "World"               │
│     - 基本类型常量：100, 3.14, true              │
│     - final常量值                                │
│  2. 符号引用（Symbolic Reference）               │
│     - 类的全限定名：java/lang/String             │
│     - 字段名称和描述符：name:Ljava/lang/String;  │
│     - 方法名称和描述符：toString:()Ljava/lang... │
├─────────────────────────────────────────────────┤
│  特点：                                          │
│  ✅ 线程共享（所有线程共用）                     │
│  ✅ 类加载时创建，类卸载时销毁                   │
│  ✅ 可动态扩展（String.intern()）                │
│  ✅ 存储编译期确定的常量                         │
└─────────────────────────────────────────────────┘
```

**示例：运行时常量池的内容**

```java
public class RuntimeConstantPoolExample {
    private static final String CONSTANT = "Constant";
    private int value = 100;
    
    public String getMessage() {
        return "Hello World";
    }
}

// 使用javap -v查看常量池
Constant pool:
   #1 = Methodref          #6.#20         // java/lang/Object."<init>":()V
   #2 = Fieldref           #5.#21         // RuntimeConstantPoolExample.value:I
   #3 = String             #22            // Hello World
   #4 = String             #23            // Constant
   #5 = Class              #24            // RuntimeConstantPoolExample
   #6 = Class              #25            // java/lang/Object
   #7 = Utf8               CONSTANT
   #8 = Utf8               Ljava/lang/String;
   #9 = Utf8               value
  #10 = Utf8               I
  #11 = Utf8               <init>
  #12 = Utf8               ()V
  #13 = Utf8               Code
  #14 = Utf8               getMessage
  #15 = Utf8               ()Ljava/lang/String;
  #16 = Utf8               SourceFile
  #17 = Utf8               RuntimeConstantPoolExample.java
  #18 = NameAndType        #11:#12        // "<init>":()V
  #19 = NameAndType        #9:#10         // value:I
  #20 = Utf8               Hello World
  #21 = Utf8               Constant
  #22 = Class              #24            // RuntimeConstantPoolExample
  #23 = Class              #25            // java/lang/Object

// 运行时常量池存储：
// - 字面量："Hello World", "Constant", 100
// - 符号引用：类名、方法名、字段名
// - 方法描述符：()V, ()Ljava/lang/String;
```

**3. 实际应用场景对比**

```java
public class PracticalExample {
    // 常量池：存储"DEFAULT_NAME"字面量
    private static final String DEFAULT_NAME = "DEFAULT_NAME";
    
    public void processUser(String userName) {
        // ============ 局部变量表 ============
        // Slot 0: this
        // Slot 1: userName（引用）
        
        // ============ 运行时常量池 ============
        // "Guest"字面量
        // "User: "字面量
        
        // 局部变量表：存储name引用
        // 常量池：存储"Guest"字面量
        String name = (userName != null) ? userName : "Guest";
        
        // 局部变量表：存储age值
        int age = 18;
        
        // 局部变量表：存储message引用
        // 常量池：存储"User: "字面量
        String message = "User: " + name;
        
        // 局部变量表：存储isValid值
        boolean isValid = age >= 18;
        
        System.out.println(message);
    }
}
```

**4. 字符串常量池特殊说明**

```java
public class StringPoolExample {
    public static void main(String[] args) {
        // ============ 运行时常量池 ============
        // "hello"字面量在编译期就放入常量池
        String s1 = "hello";
        String s2 = "hello";
        System.out.println(s1 == s2);  // true（同一个常量池引用）
        
        // ============ 堆 + 局部变量表 ============
        // new String()在堆中创建新对象
        // s3引用存储在局部变量表
        String s3 = new String("hello");
        System.out.println(s1 == s3);  // false（不同对象）
        
        // ============ intern()方法 ============
        // 将堆中的字符串尝试放入常量池
        String s4 = s3.intern();
        System.out.println(s1 == s4);  // true（返回常量池中的引用）
        
        // ============ 运行时动态添加到常量池 ============
        String s5 = new StringBuilder("ja").append("va").toString();
        String s6 = s5.intern();  // JDK 7+：在常量池中存储堆对象的引用
        System.out.println(s5 == s6);  // JDK 7+: true, JDK 6: false
    }
}
```

**5. 内存位置对比图**

```
JVM内存布局
    ↓
┌─────────────────────────────────────────────────┐
│  线程1                                           │
│  ┌─────────────────────────────────────────┐   │
│  │  虚拟机栈                                │   │
│  │  ┌───────────────────────────────────┐ │   │
│  │  │  栈帧（method1）                   │ │   │
│  │  │  ┌─────────────────────────────┐  │ │   │
│  │  │  │  局部变量表                  │  │ │   │
│  │  │  │  Slot 0: this = 0x1234      │  │ │   │
│  │  │  │  Slot 1: param = 100        │  │ │   │
│  │  │  │  Slot 2: localVar = 200     │  │ │   │
│  │  │  │  Slot 3: str = 0x5678 ──────┼──┼─┼───┐
│  │  │  └─────────────────────────────┘  │ │   │
│  │  │  │  操作数栈                    │  │ │   │
│  │  │  └─────────────────────────────┘  │ │   │
│  │  └───────────────────────────────────┘ │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
                                                   │
┌─────────────────────────────────────────────────┼─┐
│  堆（所有线程共享）                              │ │
│  ┌─────────────────────────────────────────┐   │ │
│  │  String对象（0x5678）                    │ ←─┘ │
│  │  value = [H, e, l, l, o]                │     │
│  └─────────────────────────────────────────┘     │
└───────────────────────────────────────────────────┘
                                                   
┌───────────────────────────────────────────────────┐
│  方法区/元空间（所有线程共享）                     │
│  ┌─────────────────────────────────────────┐     │
│  │  运行时常量池                            │     │
│  │  #1: "Hello"                             │     │
│  │  #2: "World"                             │     │
│  │  #3: 100                                 │     │
│  │  #4: Class: java/lang/String            │     │
│  │  #5: Method: toString:()Ljava/lang/...  │     │
│  │  #6: Field: value:I                      │     │
│  └─────────────────────────────────────────┘     │
└───────────────────────────────────────────────────┘
```

**6. 访问方式对比**

```java
public class AccessExample {
    private static final String CONSTANT = "Constant";  // 常量池
    
    public void method() {
        int local = 100;  // 局部变量表
        
        // 字节码：访问局部变量表
        // iload_1  // 加载局部变量表Slot 1的值
        
        // 字节码：访问常量池
        // ldc #2   // 从常量池加载#2号常量
    }
}

// 字节码对比
public void method();
  Code:
   0: bipush        100      // 将100压入操作数栈
   2: istore_1              // 存储到局部变量表Slot 1（local）
   3: ldc           #2       // 从常量池加载#2（CONSTANT）
   5: astore_2              // 引用存储到局部变量表Slot 2
   6: return

// 访问局部变量表：iload, aload, istore, astore
// 访问常量池：ldc, ldc_w, ldc2_w
```

**7. 关键总结**

```
┌─────────────────────────────────────────────────┐
│  局部变量表 vs 运行时常量池                      │
├─────────────────────────────────────────────────┤
│  局部变量表：                                    │
│  ✅ 存储方法执行时的临时数据                     │
│  ✅ 线程私有，方法调用时创建                     │
│  ✅ 存储变量的值或引用                           │
│  ✅ 运行时动态变化                               │
│  ✅ 位于虚拟机栈                                 │
├─────────────────────────────────────────────────┤
│  运行时常量池：                                  │
│  ✅ 存储编译期确定的常量和符号引用               │
│  ✅ 线程共享，类加载时创建                       │
│  ✅ 存储字面量和类/方法/字段的元信息             │
│  ✅ 编译期确定，运行时可扩展                     │
│  ✅ 位于方法区（元空间）                         │
└─────────────────────────────────────────────────┘
```

**实际应用理解**：

```java
public class FinalExample {
    public void example() {
        // 问题：下面的数据分别存储在哪里？
        
        int age = 18;              // age的值18：局部变量表
        String name = "Alice";     // name引用：局部变量表
                                   // "Alice"字面量：运行时常量池
        
        final int MAX = 100;       // MAX的值100：局部变量表（编译期内联）
                                   // 100字面量：运行时常量池
        
        String msg = "Hello";      // msg引用：局部变量表
                                   // "Hello"字面量：运行时常量池
        
        User user = new User();    // user引用：局部变量表
                                   // User对象：堆
                                   // User类信息：方法区
                                   // "User"类名：运行时常量池
    }
}
```

---

**可能出现的异常**：

1. **StackOverflowError**：线程请求的栈深度大于虚拟机允许的深度

```java
public void recursion() {
    recursion(); // 无限递归，导致StackOverflowError
}
```

2. **OutOfMemoryError**：虚拟机栈动态扩展时无法申请到足够内存

```java
// 创建大量线程，每个线程都有自己的栈
while (true) {
    new Thread(() -> {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }).start();
}
```

**JVM参数**：

```bash
# 设置栈大小（默认1MB）
-Xss256k  # 设置为256KB
-Xss1m    # 设置为1MB
```

---

### 3.3 本地方法栈（Native Method Stack）

#### 问题5：本地方法栈与虚拟机栈有什么区别？

**区别**：

| 特性 | 虚拟机栈 | 本地方法栈 |
|-----|---------|-----------|
| 服务对象 | Java方法 | Native方法 |
| 实现语言 | Java | C/C++ |
| 是否规范 | 有明确规范 | 规范宽松 |

**Native方法示例**：

```java
public class NativeDemo {
    // Native方法声明
    public native void nativeMethod();
    
    // Object类的Native方法
    public final native Class<?> getClass();
    public native int hashCode();
    protected native Object clone() throws CloneNotSupportedException;
    
    // Thread类的Native方法
    public static native void sleep(long millis) throws InterruptedException;
    public static native void yield();
}
```

**HotSpot虚拟机的实现**：

HotSpot虚拟机直接将虚拟机栈和本地方法栈合二为一。

```
HotSpot虚拟机
    ↓
虚拟机栈 = Java方法栈 + Native方法栈
```

---

## 4. 线程共享区域

### 4.1 堆（Heap）

#### 问题6：堆是什么？为什么要分代？

**定义**：堆是JVM管理的最大一块内存区域，所有线程共享，用于存储对象实例。

**堆的结构**：

```
堆 (Heap)
    ↓
┌─────────────────────────────────────────┐
│           新生代 (Young Generation)      │
│  ┌────────────────────────────────────┐ │
│  │         Eden区 (80%)                │ │
│  ├────────────────────────────────────┤ │
│  │  Survivor 0 (From) (10%)           │ │
│  ├────────────────────────────────────┤ │
│  │  Survivor 1 (To) (10%)             │ │
│  └────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│           老年代 (Old Generation)        │
│                                          │
│                                          │
└─────────────────────────────────────────┘

默认比例：
新生代 : 老年代 = 1 : 2
Eden : Survivor0 : Survivor1 = 8 : 1 : 1
```

**为什么要分代？**

```
对象生命周期观察
    ↓
发现规律：
- 98%的对象朝生夕死（短命对象）
- 2%的对象长期存活（长寿对象）
    ↓
分代设计思想
    ↓
┌────────────────────────────────┐
│  新生代：存放短命对象            │
│  - 频繁GC（Minor GC）           │
│  - 回收速度快                   │
│  - 使用复制算法                 │
├────────────────────────────────┤
│  老年代：存放长寿对象            │
│  - 不频繁GC（Major GC）         │
│  - 回收速度慢                   │
│  - 使用标记-清除或标记-整理     │
└────────────────────────────────┘
    ↓
优势：
✅ 提高GC效率
✅ 减少GC停顿时间
✅ 提高内存利用率
```

**对象分配流程**：

```
创建对象
    ↓
1. 尝试在Eden区分配
    ↓
    ┌──────┴──────┐
    ↓             ↓
Eden有空间    Eden空间不足
    ↓             ↓
分配成功      触发Minor GC
              ↓
          清理Eden区
              ↓
          存活对象移到Survivor
              ↓
          年龄+1
              ↓
          ┌──────┴──────┐
          ↓             ↓
      年龄<15        年龄>=15
          ↓             ↓
      留在Survivor   晋升到老年代
```

**特殊情况**：

1. **大对象直接进入老年代**

```java
// -XX:PretenureSizeThreshold=3145728 (3MB)
byte[] bigObject = new byte[4 * 1024 * 1024]; // 4MB，直接进入老年代
```

2. **动态年龄判定**

```
如果Survivor中相同年龄所有对象大小的总和 > Survivor空间的一半
    ↓
年龄 >= 该年龄的对象直接进入老年代
```

3. **空间分配担保**

```
Minor GC前检查：
老年代最大可用连续空间 > 新生代所有对象总空间
    ↓
    ┌──────┴──────┐
    ↓             ↓
   是            否
    ↓             ↓
安全执行      检查HandlePromotionFailure
Minor GC      ↓
          ┌──────┴──────┐
          ↓             ↓
        允许          不允许
          ↓             ↓
      执行Minor GC  执行Full GC
```

**JVM参数**：

```bash
# 堆大小配置
-Xms2g          # 初始堆大小2GB
-Xmx4g          # 最大堆大小4GB
-Xmn1g          # 新生代大小1GB

# 新生代与老年代比例
-XX:NewRatio=2  # 新生代:老年代=1:2

# Eden与Survivor比例
-XX:SurvivorRatio=8  # Eden:Survivor0:Survivor1=8:1:1

# 晋升老年代的年龄阈值
-XX:MaxTenuringThreshold=15  # 默认15

# 大对象阈值
-XX:PretenureSizeThreshold=3145728  # 3MB
```

---

### 4.2 方法区（Method Area）

#### 问题7：方法区存储什么？为什么JDK 8要用元空间替代永久代？

**定义**：方法区用于存储已被虚拟机加载的类信息、常量、静态变量、即时编译器编译后的代码等数据。

**方法区存储内容**：

```
方法区 (Method Area)
    ↓
┌─────────────────────────────────┐
│  类信息                          │
│  - 类的全限定名                  │
│  - 父类的全限定名                │
│  - 接口列表                      │
│  - 字段信息                      │
│  - 方法信息                      │
│  - 访问修饰符                    │
├─────────────────────────────────┤
│  运行时常量池                    │
│  - 字面量（Literal）             │
│  - 符号引用（Symbolic Reference）│
├─────────────────────────────────┤
│  静态变量                        │
│  - static修饰的变量              │
├─────────────────────────────────┤
│  即时编译器编译后的代码          │
│  - JIT编译的本地代码             │
└─────────────────────────────────┘
```

**永久代 vs 元空间**：

| 特性 | 永久代（JDK 7及之前） | 元空间（JDK 8+） |
|-----|---------------------|-----------------|
| 位置 | JVM堆内存 | 本地内存（Native Memory） |
| 大小限制 | 固定大小 | 默认无限制（受系统内存限制） |
| GC | Full GC回收 | 类卸载时回收 |
| OOM | 容易发生 | 不容易发生 |

**为什么要用元空间替代永久代？**

```
永久代的问题
    ↓
1. 固定大小限制
   - 难以确定合适的大小
   - 容易发生OOM
    ↓
2. GC效率低
   - Full GC才回收
   - 影响性能
    ↓
3. 与堆内存竞争
   - 占用堆空间
    ↓
元空间的优势
    ↓
1. 使用本地内存
   - 不占用堆空间
   - 大小可动态调整
    ↓
2. 减少OOM
   - 受系统内存限制
   - 更灵活
    ↓
3. 简化GC
   - 类卸载时回收
   - 提高效率
```

**运行时常量池**：

```java
public class ConstantPoolDemo {
    public static void main(String[] args) {
        // 字面量
        String s1 = "hello";  // 存储在运行时常量池
        String s2 = "hello";  // 从常量池获取
        System.out.println(s1 == s2);  // true
        
        // 运行时生成
        String s3 = new String("hello");  // 堆中创建新对象
        System.out.println(s1 == s3);  // false
        
        // intern方法
        String s4 = s3.intern();  // 返回常量池中的引用
        System.out.println(s1 == s4);  // true
    }
}
```

**JVM参数**：

```bash
# JDK 7及之前（永久代）
-XX:PermSize=128m       # 初始永久代大小
-XX:MaxPermSize=256m    # 最大永久代大小

# JDK 8+（元空间）
-XX:MetaspaceSize=128m      # 初始元空间大小
-XX:MaxMetaspaceSize=256m   # 最大元空间大小（默认无限制）
```

---

## 5. 直接内存（Direct Memory）

### 5.1 问题8：什么是直接内存？为什么需要它？

**定义**：直接内存不是JVM运行时数据区的一部分，而是通过Native函数库直接分配的堆外内存。

**为什么需要直接内存？**

```
传统IO流程（使用堆内存）
    ↓
1. 应用程序发起读请求
    ↓
2. 操作系统将数据读到内核缓冲区
    ↓
3. 数据从内核缓冲区复制到JVM堆内存
    ↓
4. 应用程序处理数据
    ↓
问题：多了一次复制，性能损耗

NIO流程（使用直接内存）
    ↓
1. 应用程序发起读请求
    ↓
2. 操作系统将数据直接读到直接内存
    ↓
3. 应用程序直接访问直接内存
    ↓
优势：减少一次复制，提高性能
```

**使用示例**：

```java
import java.nio.ByteBuffer;

public class DirectMemoryDemo {
    public static void main(String[] args) {
        // 分配堆内存
        ByteBuffer heapBuffer = ByteBuffer.allocate(1024);
        
        // 分配直接内存
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        
        // 直接内存的优势：
        // 1. 减少数据复制
        // 2. 提高IO性能
        // 3. 适合大数据量、频繁IO的场景
    }
}
```

**直接内存的特点**：

```
优点：
✅ 减少数据复制
✅ 提高IO性能
✅ 不受JVM堆大小限制

缺点：
❌ 分配和回收成本高
❌ 不受JVM GC管理
❌ 可能导致OOM
```

**JVM参数**：

```bash
# 设置直接内存大小（默认与-Xmx相同）
-XX:MaxDirectMemorySize=512m
```

---

## 6. 内存结构总结

### 6.1 各区域对比

| 区域 | 线程私有/共享 | 单线程大小 | 是否会OOM | GC | 存储内容 |
|-----|-------------|-----------|----------|----|---------| 
| 程序计数器 | 私有 | **8字节** | **❌ 不会** | ❌ | 字节码行号指针 |
| 虚拟机栈 | 私有 | 1MB（默认） | ✅ 会 | ❌ | 局部变量、操作数栈 |
| 本地方法栈 | 私有 | 1MB（默认） | ✅ 会 | ❌ | Native方法栈帧 |
| 堆 | 共享 | GB级别 | ✅ 会 | ✅ | 对象实例、数组 |
| 方法区 | 共享 | MB级别 | ✅ 会 | ✅ | 类信息、常量、静态变量 |
| 直接内存 | - | 可配置 | ✅ 会 | ❌ | NIO堆外内存 |

**内存消耗对比（1000个线程）**：

| 区域 | 内存消耗 | 占比 | 是否可忽略 |
|-----|---------|------|-----------|
| 程序计数器 | 1000 × 8字节 = **8KB** | 0.0008% | ✅ 可忽略 |
| 虚拟机栈 | 1000 × 1MB = **1000MB** | 99.2% | ❌ 主要消耗 |
| 堆（共享） | 假设2GB | - | ❌ 主要消耗 |
| 方法区（共享） | 假设256MB | - | - |

### 6.2 内存分配流程

```
对象创建
    ↓
1. 检查类是否已加载
    ↓
2. 分配内存
    ↓
    ┌──────────┴──────────┐
    ↓                     ↓
指针碰撞              空闲列表
(内存规整)          (内存不规整)
    ↓                     ↓
    └──────────┬──────────┘
               ↓
3. 初始化零值
    ↓
4. 设置对象头
    ↓
5. 执行<init>方法
    ↓
对象创建完成
```

### 6.3 关键问题回顾

1. **为什么需要程序计数器？**
   - 多线程切换时恢复执行位置
   - 记录当前线程执行的字节码指令地址

2. **为什么程序计数器不会OOM？**
   - ✅ 固定大小：每个线程只需8字节（64位系统）
   - ✅ 不动态增长：无论程序多复杂，PC永远只存一个地址
   - ✅ 内存消耗极小：即使10,000个线程也只需80KB
   - ✅ 系统会先因栈内存不足而OOM，而不是PC

3. **为什么要分代？**
   - 根据对象生命周期特点优化GC效率
   - 98%的对象朝生夕死，2%长期存活

4. **为什么用元空间替代永久代？**
   - 避免OOM，提高灵活性
   - 使用本地内存，不占用堆空间

5. **为什么需要直接内存？**
   - 减少数据复制，提高IO性能
   - NIO场景下性能更优

---

**下一章**：我们将深入学习对象在内存中的布局和分配策略。
