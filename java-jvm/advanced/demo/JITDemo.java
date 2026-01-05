package com.example.jvm.advanced.demo;

/**
 * JIT编译器演示
 * 
 * 演示内容：
 * 1. JIT编译触发
 * 2. 方法内联
 * 3. 编译日志分析
 * 4. 解释执行 vs JIT编译性能对比
 * 
 * 运行参数：
 * -XX:+PrintCompilation              # 打印编译信息
 * -XX:+UnlockDiagnosticVMOptions     # 解锁诊断选项
 * -XX:+PrintInlining                 # 打印内联信息
 * -XX:CompileThreshold=1000          # 设置编译阈值
 * 
 * @author JavaGuide
 */
public class JITDemo {

    public static void main(String[] args) {
        System.out.println("========== JIT编译器演示 ==========\n");
        
        // 演示1：JIT编译触发
        demonstrateJITCompilation();
        
        // 演示2：方法内联
        demonstrateInlining();
        
        // 演示3：性能对比
        demonstratePerformanceComparison();
    }

    /**
     * 演示1：JIT编译触发
     */
    private static void demonstrateJITCompilation() {
        System.out.println("1. JIT编译触发演示:");
        System.out.println("   观察 -XX:+PrintCompilation 输出\n");
        
        long start = System.currentTimeMillis();
        
        // 调用方法多次，触发JIT编译
        for (int i = 0; i < 20000; i++) {
            calculate(i);
        }
        
        long end = System.currentTimeMillis();
        System.out.println("   执行时间: " + (end - start) + "ms\n");
    }

    /**
     * 计算方法（会被JIT编译）
     */
    private static int calculate(int n) {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            result += i * n;
        }
        return result;
    }

    /**
     * 演示2：方法内联
     */
    private static void demonstrateInlining() {
        System.out.println("2. 方法内联演示:");
        System.out.println("   观察 -XX:+PrintInlining 输出\n");
        
        long start = System.currentTimeMillis();
        
        // 调用包含小方法的代码
        for (int i = 0; i < 20000; i++) {
            int result = add(i, i + 1);
            result = multiply(result, 2);
        }
        
        long end = System.currentTimeMillis();
        System.out.println("   执行时间: " + (end - start) + "ms\n");
    }

    /**
     * 小方法（会被内联）
     */
    private static int add(int a, int b) {
        return a + b;
    }

    /**
     * 小方法（会被内联）
     */
    private static int multiply(int a, int b) {
        return a * b;
    }

    /**
     * 演示3：性能对比
     */
    private static void demonstratePerformanceComparison() {
        System.out.println("3. 性能对比:");
        
        // 预热，让JIT编译
        System.out.println("   预热中...");
        for (int i = 0; i < 20000; i++) {
            performanceTest();
        }
        
        // 测试JIT编译后的性能
        System.out.println("   测试JIT编译后性能...");
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            performanceTest();
        }
        long jitTime = System.currentTimeMillis() - start;
        
        System.out.println("   JIT编译后: " + jitTime + "ms\n");
    }

    /**
     * 性能测试方法
     */
    private static int performanceTest() {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
        }
        return sum;
    }
}

/**
 * JIT编译层级演示
 */
class TieredCompilationDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 分层编译演示 ==========\n");
        
        System.out.println("运行参数:");
        System.out.println("-XX:+TieredCompilation          # 开启分层编译（默认）");
        System.out.println("-XX:+PrintCompilation           # 打印编译信息");
        System.out.println("-XX:+PrintTieredEvents          # 打印分层事件\n");
        
        // 方法会经历不同的编译层级
        for (int i = 0; i < 50000; i++) {
            tieredMethod(i);
            
            if (i % 10000 == 0) {
                System.out.println("已执行: " + i + " 次");
            }
        }
        
        System.out.println("\n编译层级说明:");
        System.out.println("Level 0: 解释执行");
        System.out.println("Level 1: C1编译（无profiling）");
        System.out.println("Level 2: C1编译（有限profiling）");
        System.out.println("Level 3: C1编译（完整profiling）");
        System.out.println("Level 4: C2编译（最高优化）");
    }
    
    private static int tieredMethod(int n) {
        int result = 0;
        for (int i = 0; i < 10; i++) {
            result += i * n;
        }
        return result;
    }
}

/**
 * 去优化演示
 */
class DeoptimizationDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 去优化演示 ==========\n");
        
        System.out.println("运行参数:");
        System.out.println("-XX:+PrintCompilation");
        System.out.println("-XX:+UnlockDiagnosticVMOptions");
        System.out.println("-XX:+PrintDeoptimization\n");
        
        // 第一阶段：只传入Dog对象
        System.out.println("第一阶段：单态（Monomorphic）");
        for (int i = 0; i < 20000; i++) {
            process(new Dog());
        }
        
        System.out.println("JIT编译器假设：animal总是Dog类型");
        System.out.println("优化：去虚拟化 + 内联Dog.eat()\n");
        
        // 第二阶段：传入Cat对象
        System.out.println("第二阶段：双态（Bimorphic）");
        for (int i = 0; i < 10000; i++) {
            process(new Cat());
        }
        
        System.out.println("假设不成立，触发去优化");
        System.out.println("回退到解释执行或重新编译\n");
    }
    
    private static void process(Animal animal) {
        animal.eat();
    }
    
    interface Animal {
        void eat();
    }
    
    static class Dog implements Animal {
        public void eat() {
            // Dog eating
        }
    }
    
    static class Cat implements Animal {
        public void eat() {
            // Cat eating
        }
    }
}

/**
 * OSR编译演示
 */
class OSRDemo {
    
    public static void main(String[] args) {
        System.out.println("========== OSR编译演示 ==========\n");
        
        System.out.println("OSR（On-Stack Replacement）：");
        System.out.println("在方法执行过程中，将解释执行替换为编译执行\n");
        
        System.out.println("运行参数:");
        System.out.println("-XX:+PrintCompilation");
        System.out.println("观察输出中的 % 标记（表示OSR编译）\n");
        
        // 长循环，会触发OSR编译
        long sum = 0;
        for (long i = 0; i < 1000000000L; i++) {
            sum += i;
            
            if (i % 100000000 == 0) {
                System.out.println("已执行: " + i + " 次");
            }
        }
        
        System.out.println("\n结果: " + sum);
        System.out.println("循环在执行过程中被OSR编译");
    }
}

/**
 * 编译阈值演示
 */
class CompileThresholdDemo {
    
    private static int counter = 0;
    
    public static void main(String[] args) {
        System.out.println("========== 编译阈值演示 ==========\n");
        
        System.out.println("默认编译阈值:");
        System.out.println("C2编译器: 10000次");
        System.out.println("C1编译器: 2000次\n");
        
        System.out.println("自定义阈值:");
        System.out.println("-XX:CompileThreshold=1000\n");
        
        // 调用方法直到触发编译
        for (int i = 0; i < 15000; i++) {
            hotMethod();
            
            if (i % 1000 == 0) {
                System.out.println("调用次数: " + i);
            }
        }
        
        System.out.println("\n观察 -XX:+PrintCompilation 输出");
        System.out.println("查看方法在哪次调用后被编译");
    }
    
    private static void hotMethod() {
        counter++;
    }
}

/**
 * 内联限制演示
 */
class InliningLimitDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 内联限制演示 ==========\n");
        
        System.out.println("内联参数:");
        System.out.println("-XX:MaxInlineSize=35            # 最大内联方法大小");
        System.out.println("-XX:FreqInlineSize=325          # 热点方法内联大小");
        System.out.println("-XX:MaxInlineLevel=9            # 最大内联层数\n");
        
        System.out.println("运行参数:");
        System.out.println("-XX:+PrintInlining\n");
        
        // 预热
        for (int i = 0; i < 20000; i++) {
            testInlining();
        }
        
        System.out.println("观察哪些方法被内联，哪些没有");
    }
    
    private static int testInlining() {
        return level1();
    }
    
    private static int level1() {
        return level2() + level2();
    }
    
    private static int level2() {
        return level3() + level3();
    }
    
    private static int level3() {
        return smallMethod();
    }
    
    private static int smallMethod() {
        return 42;
    }
    
    // 大方法，不会被内联
    private static int largeMethod() {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            sum *= 2;
            sum -= 1;
            sum /= 2;
            sum += i * i;
            sum -= i / 2;
        }
        return sum;
    }
}

/**
 * JIT编译器性能测试
 */
class JITPerformanceTest {
    
    private static final int ITERATIONS = 10000000;
    
    public static void main(String[] args) {
        System.out.println("========== JIT性能测试 ==========\n");
        
        // 测试1：解释执行
        System.out.println("1. 解释执行（-Xint）:");
        testInterpretedMode();
        
        // 测试2：混合模式（默认）
        System.out.println("\n2. 混合模式（默认）:");
        testMixedMode();
        
        // 测试3：纯编译模式
        System.out.println("\n3. 纯编译模式（-Xcomp）:");
        System.out.println("   需要使用 -Xcomp 参数重新运行");
    }
    
    private static void testInterpretedMode() {
        System.out.println("   需要使用 -Xint 参数重新运行");
    }
    
    private static void testMixedMode() {
        // 预热
        for (int i = 0; i < 20000; i++) {
            compute(i);
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            compute(i);
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("   执行时间: " + duration + "ms");
        System.out.println("   QPS: " + (ITERATIONS * 1000L / duration));
    }
    
    private static int compute(int n) {
        int result = 0;
        for (int i = 0; i < 10; i++) {
            result += i * n;
        }
        return result;
    }
}
