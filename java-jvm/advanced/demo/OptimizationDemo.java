package com.example.jvm.advanced.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM优化技术演示
 * 
 * 演示内容：
 * 1. 逃逸分析
 * 2. 标量替换
 * 3. 栈上分配
 * 4. 同步消除
 * 5. 循环优化
 * 
 * 运行参数：
 * -XX:+DoEscapeAnalysis              # 开启逃逸分析（默认）
 * -XX:+EliminateAllocations          # 开启标量替换（默认）
 * -XX:+EliminateLocks                # 开启同步消除（默认）
 * -XX:+PrintEliminateAllocations     # 打印标量替换信息
 * -XX:+UnlockDiagnosticVMOptions     # 解锁诊断选项
 * 
 * @author JavaGuide
 */
public class OptimizationDemo {

    public static void main(String[] args) {
        System.out.println("========== JVM优化技术演示 ==========\n");
        
        // 演示1：逃逸分析
        demonstrateEscapeAnalysis();
        
        // 演示2：标量替换
        demonstrateScalarReplacement();
        
        // 演示3：同步消除
        demonstrateLockElimination();
        
        // 演示4：循环优化
        demonstrateLoopOptimization();
    }

    /**
     * 演示1：逃逸分析
     */
    private static void demonstrateEscapeAnalysis() {
        System.out.println("1. 逃逸分析演示:\n");
        
        long start = System.currentTimeMillis();
        
        // 对象不逃逸，可以优化
        for (int i = 0; i < 10000000; i++) {
            noEscape();
        }
        
        long end = System.currentTimeMillis();
        System.out.println("   不逃逸对象分配时间: " + (end - start) + "ms");
        
        start = System.currentTimeMillis();
        
        // 对象逃逸，无法优化
        List<Point> list = new ArrayList<>();
        for (int i = 0; i < 10000000; i++) {
            Point p = escape();
            if (i % 1000000 == 0) {
                list.add(p);  // 防止被完全优化掉
            }
        }
        
        end = System.currentTimeMillis();
        System.out.println("   逃逸对象分配时间: " + (end - start) + "ms\n");
    }

    /**
     * 对象不逃逸
     */
    private static void noEscape() {
        Point p = new Point(1, 2);
        int sum = p.x + p.y;  // 只在方法内使用
    }

    /**
     * 对象逃逸（返回）
     */
    private static Point escape() {
        Point p = new Point(1, 2);
        return p;  // 逃逸
    }

    /**
     * 演示2：标量替换
     */
    private static void demonstrateScalarReplacement() {
        System.out.println("2. 标量替换演示:");
        System.out.println("   运行参数: -XX:+PrintEliminateAllocations\n");
        
        long start = System.currentTimeMillis();
        
        // 对象会被标量替换
        for (int i = 0; i < 10000000; i++) {
            scalarReplacement();
        }
        
        long end = System.currentTimeMillis();
        System.out.println("   执行时间: " + (end - start) + "ms");
        System.out.println("   观察是否有 'Eliminated allocation' 输出\n");
    }

    /**
     * 标量替换示例
     */
    private static int scalarReplacement() {
        Point p = new Point(1, 2);
        // JIT优化后：
        // int x = 1;
        // int y = 2;
        // return x + y;
        return p.x + p.y;
    }

    /**
     * 演示3：同步消除
     */
    private static void demonstrateLockElimination() {
        System.out.println("3. 同步消除演示:\n");
        
        // 测试1：局部锁对象（会被消除）
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000000; i++) {
            localLock();
        }
        long localTime = System.currentTimeMillis() - start;
        
        // 测试2：全局锁对象（不会被消除）
        start = System.currentTimeMillis();
        for (int i = 0; i < 10000000; i++) {
            globalLock();
        }
        long globalTime = System.currentTimeMillis() - start;
        
        System.out.println("   局部锁时间: " + localTime + "ms");
        System.out.println("   全局锁时间: " + globalTime + "ms");
        System.out.println("   性能提升: " + (globalTime * 1.0 / localTime) + "倍\n");
    }

    /**
     * 局部锁对象（同步会被消除）
     */
    private static void localLock() {
        Object lock = new Object();  // 不逃逸
        synchronized (lock) {
            // 同步块
        }
    }

    private static final Object GLOBAL_LOCK = new Object();

    /**
     * 全局锁对象（同步不会被消除）
     */
    private static void globalLock() {
        synchronized (GLOBAL_LOCK) {
            // 同步块
        }
    }

    /**
     * 演示4：循环优化
     */
    private static void demonstrateLoopOptimization() {
        System.out.println("4. 循环优化演示:\n");
        
        int[] array = new int[1000];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
        
        // 测试循环展开
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            loopUnrolling(array);
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("   循环优化时间: " + duration + "ms");
        System.out.println("   JIT会自动进行循环展开、循环剥离等优化\n");
    }

    /**
     * 循环展开示例
     */
    private static int loopUnrolling(int[] array) {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum;
    }

    /**
     * Point类
     */
    static class Point {
        int x, y;
        
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}

/**
 * 逃逸分析详细演示
 */
class EscapeAnalysisDetailDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 逃逸分析详细演示 ==========\n");
        
        System.out.println("运行参数:");
        System.out.println("-XX:+DoEscapeAnalysis           # 开启逃逸分析");
        System.out.println("-XX:-DoEscapeAnalysis           # 关闭逃逸分析");
        System.out.println("-XX:+PrintEscapeAnalysis        # 打印逃逸分析");
        System.out.println("-XX:+UnlockDiagnosticVMOptions\n");
        
        // 场景1：不逃逸
        testNoEscape();
        
        // 场景2：方法逃逸
        testMethodEscape();
        
        // 场景3：全局逃逸
        testGlobalEscape();
    }
    
    private static void testNoEscape() {
        System.out.println("场景1：不逃逸");
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < 10000000; i++) {
            User user = new User("张三", 25);
            String info = user.getName() + user.getAge();
        }
        
        long duration = System.currentTimeMillis() - start;
        System.out.println("  时间: " + duration + "ms");
        System.out.println("  优化: 标量替换，无对象分配\n");
    }
    
    private static void testMethodEscape() {
        System.out.println("场景2：方法逃逸");
        long start = System.currentTimeMillis();
        
        List<User> list = new ArrayList<>();
        for (int i = 0; i < 10000000; i++) {
            User user = createUser("李四", 30);
            if (i % 1000000 == 0) {
                list.add(user);
            }
        }
        
        long duration = System.currentTimeMillis() - start;
        System.out.println("  时间: " + duration + "ms");
        System.out.println("  优化: 部分优化\n");
    }
    
    private static User createUser(String name, int age) {
        return new User(name, age);  // 逃逸
    }
    
    private static User globalUser;
    
    private static void testGlobalEscape() {
        System.out.println("场景3：全局逃逸");
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < 10000000; i++) {
            globalUser = new User("王五", 35);
        }
        
        long duration = System.currentTimeMillis() - start;
        System.out.println("  时间: " + duration + "ms");
        System.out.println("  优化: 无法优化\n");
    }
    
    static class User {
        private String name;
        private int age;
        
        User(String name, int age) {
            this.name = name;
            this.age = age;
        }
        
        String getName() { return name; }
        int getAge() { return age; }
    }
}

/**
 * 标量替换性能测试
 */
class ScalarReplacementPerformanceTest {
    
    private static final int ITERATIONS = 100000000;
    
    public static void main(String[] args) {
        System.out.println("========== 标量替换性能测试 ==========\n");
        
        System.out.println("测试1：开启标量替换");
        testWithScalarReplacement();
        
        System.out.println("\n测试2：关闭标量替换");
        System.out.println("需要使用参数重新运行:");
        System.out.println("-XX:-DoEscapeAnalysis");
        System.out.println("-XX:-EliminateAllocations\n");
    }
    
    private static void testWithScalarReplacement() {
        // 预热
        for (int i = 0; i < 20000; i++) {
            allocatePoint();
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            allocatePoint();
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("  执行时间: " + duration + "ms");
        System.out.println("  QPS: " + (ITERATIONS * 1000L / duration));
    }
    
    private static int allocatePoint() {
        Point p = new Point(1, 2);
        return p.x + p.y;
    }
    
    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}

/**
 * 同步消除演示
 */
class LockEliminationDetailDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 同步消除详细演示 ==========\n");
        
        System.out.println("运行参数:");
        System.out.println("-XX:+EliminateLocks             # 开启同步消除");
        System.out.println("-XX:-EliminateLocks             # 关闭同步消除");
        System.out.println("-XX:+PrintEliminateLocks        # 打印同步消除");
        System.out.println("-XX:+UnlockDiagnosticVMOptions\n");
        
        // 测试StringBuffer（有同步）
        testStringBuffer();
        
        // 测试StringBuilder（无同步）
        testStringBuilder();
    }
    
    private static void testStringBuffer() {
        System.out.println("测试StringBuffer:");
        
        // 预热
        for (int i = 0; i < 20000; i++) {
            useStringBuffer();
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            useStringBuffer();
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("  时间: " + duration + "ms");
        System.out.println("  说明: 同步被消除，性能接近StringBuilder\n");
    }
    
    private static String useStringBuffer() {
        StringBuffer sb = new StringBuffer();  // 不逃逸
        sb.append("Hello");
        sb.append(" ");
        sb.append("World");
        return sb.toString();
    }
    
    private static void testStringBuilder() {
        System.out.println("测试StringBuilder:");
        
        // 预热
        for (int i = 0; i < 20000; i++) {
            useStringBuilder();
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            useStringBuilder();
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("  时间: " + duration + "ms\n");
    }
    
    private static String useStringBuilder() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello");
        sb.append(" ");
        sb.append("World");
        return sb.toString();
    }
}

/**
 * 循环优化演示
 */
class LoopOptimizationDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 循环优化演示 ==========\n");
        
        int[] array = new int[10000];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
        
        // 测试1：循环展开
        testLoopUnrolling(array);
        
        // 测试2：循环不变量外提
        testLoopInvariant(array);
        
        // 测试3：范围检查消除
        testRangeCheckElimination(array);
    }
    
    private static void testLoopUnrolling(int[] array) {
        System.out.println("1. 循环展开:");
        
        // 预热
        for (int i = 0; i < 10000; i++) {
            sumArray(array);
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            sumArray(array);
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("   时间: " + duration + "ms");
        System.out.println("   JIT会自动展开循环\n");
    }
    
    private static int sumArray(int[] array) {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum;
    }
    
    private static void testLoopInvariant(int[] array) {
        System.out.println("2. 循环不变量外提:");
        
        int a = 10;
        int b = 20;
        
        // 预热
        for (int i = 0; i < 10000; i++) {
            computeWithInvariant(array, a, b);
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            computeWithInvariant(array, a, b);
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("   时间: " + duration + "ms");
        System.out.println("   JIT会将 a+b 提到循环外\n");
    }
    
    private static int computeWithInvariant(int[] array, int a, int b) {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            int factor = a + b;  // 循环不变量
            sum += array[i] * factor;
        }
        return sum;
    }
    
    private static void testRangeCheckElimination(int[] array) {
        System.out.println("3. 范围检查消除:");
        
        // 预热
        for (int i = 0; i < 10000; i++) {
            accessArray(array);
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            accessArray(array);
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("   时间: " + duration + "ms");
        System.out.println("   JIT会消除数组访问的范围检查\n");
    }
    
    private static int accessArray(int[] array) {
        int sum = 0;
        // JIT分析：i永远不会越界
        // 消除 array[i] 的范围检查
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum;
    }
}

/**
 * 方法内联演示
 */
class InliningOptimizationDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 方法内联演示 ==========\n");
        
        System.out.println("运行参数:");
        System.out.println("-XX:+PrintInlining");
        System.out.println("-XX:+UnlockDiagnosticVMOptions\n");
        
        // 预热
        for (int i = 0; i < 20000; i++) {
            testInlining(i);
        }
        
        // 测试
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000000; i++) {
            testInlining(i);
        }
        long duration = System.currentTimeMillis() - start;
        
        System.out.println("执行时间: " + duration + "ms");
        System.out.println("\n观察 -XX:+PrintInlining 输出");
        System.out.println("查看哪些方法被内联");
    }
    
    private static int testInlining(int n) {
        int result = add(n, 1);
        result = multiply(result, 2);
        result = subtract(result, 1);
        return result;
    }
    
    // 小方法，会被内联
    private static int add(int a, int b) {
        return a + b;
    }
    
    private static int multiply(int a, int b) {
        return a * b;
    }
    
    private static int subtract(int a, int b) {
        return a - b;
    }
}
