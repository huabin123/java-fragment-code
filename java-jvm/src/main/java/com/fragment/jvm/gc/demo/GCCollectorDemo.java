package com.example.jvm.gc.demo;

import java.util.*;

/**
 * GC收集器演示
 * 
 * 演示内容：
 * 1. 不同GC收集器的使用
 * 2. Minor GC和Full GC
 * 3. 对象分配和晋升
 * 4. GC日志分析
 * 
 * VM参数示例见各个方法注释
 * 
 * @author JavaGuide
 */
public class GCCollectorDemo {

    private static final int _1MB = 1024 * 1024;

    public static void main(String[] args) {
        System.out.println("========== GC收集器演示 ==========\n");
        System.out.println("当前JVM信息:");
        printJVMInfo();
        
        System.out.println("\n选择演示:");
        System.out.println("1. Minor GC演示");
        System.out.println("2. Full GC演示");
        System.out.println("3. 对象晋升演示");
        System.out.println("4. 大对象分配演示");
        
        // 根据需要取消注释运行
        // testMinorGC();
        // testFullGC();
        // testPromotion();
        // testLargeObject();
    }

    /**
     * 打印JVM信息
     */
    private static void printJVMInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / _1MB;
        long totalMemory = runtime.totalMemory() / _1MB;
        long freeMemory = runtime.freeMemory() / _1MB;
        
        System.out.println("最大内存: " + maxMemory + "MB");
        System.out.println("总内存: " + totalMemory + "MB");
        System.out.println("空闲内存: " + freeMemory + "MB");
        System.out.println("已用内存: " + (totalMemory - freeMemory) + "MB");
        
        // 打印GC信息
        System.out.println("\nGC参数:");
        System.out.println("使用的GC: " + System.getProperty("java.vm.name"));
    }

    /**
     * Minor GC演示
     * 
     * VM参数：
     * -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails -XX:SurvivorRatio=8
     * 
     * 说明：
     * - 堆大小20M
     * - 新生代10M（Eden=8M, S0=1M, S1=1M）
     * - 老年代10M
     */
    public static void testMinorGC() {
        System.out.println("\n========== Minor GC演示 ==========");
        
        byte[] allocation1, allocation2, allocation3, allocation4;
        
        // 分配3个2MB对象，在Eden区
        allocation1 = new byte[2 * _1MB];
        allocation2 = new byte[2 * _1MB];
        allocation3 = new byte[2 * _1MB];
        
        System.out.println("分配3个2MB对象后");
        printMemory();
        
        // 分配4MB对象，Eden区不足，触发Minor GC
        System.out.println("\n分配4MB对象，触发Minor GC...");
        allocation4 = new byte[4 * _1MB];
        
        System.out.println("Minor GC后");
        printMemory();
    }

    /**
     * Full GC演示
     * 
     * VM参数：
     * -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails
     */
    public static void testFullGC() {
        System.out.println("\n========== Full GC演示 ==========");
        
        byte[] allocation1, allocation2, allocation3, allocation4, allocation5;
        
        // 填充老年代
        allocation1 = new byte[2 * _1MB];
        allocation2 = new byte[2 * _1MB];
        allocation3 = new byte[2 * _1MB];
        allocation4 = new byte[2 * _1MB];
        
        System.out.println("分配8MB到老年代");
        printMemory();
        
        // 老年代空间不足，触发Full GC
        System.out.println("\n分配大对象，触发Full GC...");
        allocation5 = new byte[8 * _1MB];
        
        System.out.println("Full GC后");
        printMemory();
    }

    /**
     * 对象晋升演示
     * 
     * VM参数：
     * -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails 
     * -XX:+PrintTenuringDistribution -XX:MaxTenuringThreshold=1
     */
    public static void testPromotion() {
        System.out.println("\n========== 对象晋升演示 ==========");
        
        byte[] allocation1, allocation2, allocation3;
        
        // 分配对象
        allocation1 = new byte[_1MB / 4];
        
        System.out.println("分配256KB对象");
        printMemory();
        
        // 触发多次Minor GC，观察对象晋升
        for (int i = 0; i < 3; i++) {
            System.out.println("\n第" + (i + 1) + "次Minor GC:");
            allocation2 = new byte[4 * _1MB];
            allocation2 = null;
            printMemory();
        }
        
        System.out.println("\n对象经历多次GC后晋升到老年代");
    }

    /**
     * 大对象分配演示
     * 
     * VM参数：
     * -Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails
     * -XX:PretenureSizeThreshold=3145728 -XX:+UseSerialGC
     */
    public static void testLargeObject() {
        System.out.println("\n========== 大对象分配演示 ==========");
        
        // 小对象，在Eden区分配
        byte[] allocation1 = new byte[2 * _1MB];
        System.out.println("分配2MB小对象（Eden区）");
        printMemory();
        
        // 大对象（>3MB），直接在老年代分配
        System.out.println("\n分配4MB大对象（老年代）");
        byte[] allocation2 = new byte[4 * _1MB];
        printMemory();
        
        System.out.println("\n大对象直接进入老年代，避免复制开销");
    }

    /**
     * 打印内存使用情况
     */
    private static void printMemory() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory() / _1MB;
        long free = runtime.freeMemory() / _1MB;
        long used = total - free;
        
        System.out.println("总内存: " + total + "MB, 已用: " + used + "MB, 空闲: " + free + "MB");
    }
}

/**
 * 不同GC收集器对比演示
 */
class GCCollectorComparison {
    
    private static final int _1MB = 1024 * 1024;
    
    public static void main(String[] args) {
        System.out.println("========== GC收集器对比 ==========\n");
        
        System.out.println("1. Serial GC:");
        System.out.println("   VM参数: -XX:+UseSerialGC");
        System.out.println("   特点: 单线程，STW时间长");
        System.out.println("   适用: 单核CPU，小堆");
        
        System.out.println("\n2. Parallel GC:");
        System.out.println("   VM参数: -XX:+UseParallelGC");
        System.out.println("   特点: 多线程，高吞吐量");
        System.out.println("   适用: 多核CPU，后台计算");
        
        System.out.println("\n3. CMS GC:");
        System.out.println("   VM参数: -XX:+UseConcMarkSweepGC");
        System.out.println("   特点: 并发，低延迟");
        System.out.println("   适用: 互联网应用");
        
        System.out.println("\n4. G1 GC:");
        System.out.println("   VM参数: -XX:+UseG1GC");
        System.out.println("   特点: 可预测停顿，无碎片");
        System.out.println("   适用: 大堆，低延迟");
        
        System.out.println("\n5. ZGC:");
        System.out.println("   VM参数: -XX:+UseZGC");
        System.out.println("   特点: 超低延迟(<10ms)");
        System.out.println("   适用: 超大堆，超低延迟");
    }
}

/**
 * GC日志分析演示
 */
class GCLogAnalysisDemo {
    
    public static void main(String[] args) {
        System.out.println("========== GC日志分析 ==========\n");
        
        System.out.println("开启GC日志的VM参数:");
        System.out.println("-XX:+PrintGCDetails");
        System.out.println("-XX:+PrintGCDateStamps");
        System.out.println("-XX:+PrintGCTimeStamps");
        System.out.println("-Xloggc:gc.log");
        
        System.out.println("\nMinor GC日志示例:");
        System.out.println("[GC (Allocation Failure) [PSYoungGen: 8192K->1024K(9216K)] " +
                         "8192K->5120K(19456K), 0.0012345 secs]");
        
        System.out.println("\n解读:");
        System.out.println("- GC类型: Minor GC");
        System.out.println("- 原因: Allocation Failure（分配失败）");
        System.out.println("- 新生代: 8192K -> 1024K（总容量9216K）");
        System.out.println("- 整个堆: 8192K -> 5120K（总容量19456K）");
        System.out.println("- 耗时: 0.0012345秒");
        
        System.out.println("\nFull GC日志示例:");
        System.out.println("[Full GC (Ergonomics) [PSYoungGen: 1024K->0K(9216K)] " +
                         "[ParOldGen: 8192K->5120K(10240K)] 9216K->5120K(19456K), 0.0123456 secs]");
        
        System.out.println("\n解读:");
        System.out.println("- GC类型: Full GC");
        System.out.println("- 原因: Ergonomics（自适应策略）");
        System.out.println("- 新生代: 1024K -> 0K");
        System.out.println("- 老年代: 8192K -> 5120K");
        System.out.println("- 整个堆: 9216K -> 5120K");
        System.out.println("- 耗时: 0.0123456秒");
    }
}

/**
 * GC性能测试
 */
class GCPerformanceTest {
    
    private static final int _1MB = 1024 * 1024;
    
    public static void main(String[] args) {
        System.out.println("========== GC性能测试 ==========\n");
        
        int iterations = 1000;
        long startTime = System.currentTimeMillis();
        
        List<byte[]> list = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            // 分配对象
            list.add(new byte[_1MB]);
            
            // 定期清理，模拟对象生命周期
            if (i % 100 == 0 && i > 0) {
                list.clear();
                System.out.println("已处理: " + i + " 次");
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("\n性能统计:");
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("平均每次: " + (duration * 1.0 / iterations) + "ms");
        
        // 打印GC统计
        printGCStats();
    }
    
    private static void printGCStats() {
        Runtime runtime = Runtime.getRuntime();
        System.out.println("\n内存统计:");
        System.out.println("最大内存: " + (runtime.maxMemory() / _1MB) + "MB");
        System.out.println("总内存: " + (runtime.totalMemory() / _1MB) + "MB");
        System.out.println("空闲内存: " + (runtime.freeMemory() / _1MB) + "MB");
    }
}
