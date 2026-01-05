package com.example.jvm.gc.demo;

import java.lang.management.*;
import java.util.*;

/**
 * GC调优演示
 * 
 * 演示内容：
 * 1. GC监控
 * 2. 内存泄漏检测
 * 3. GC参数调优
 * 4. 性能对比
 * 
 * @author JavaGuide
 */
public class GCTuningDemo {

    private static final int _1MB = 1024 * 1024;

    public static void main(String[] args) throws Exception {
        System.out.println("========== GC调优演示 ==========\n");
        
        // 1. GC监控
        System.out.println("1. GC监控:");
        monitorGC();
        
        // 2. 内存泄漏检测
        System.out.println("\n2. 内存泄漏检测:");
        detectMemoryLeak();
        
        // 3. GC参数建议
        System.out.println("\n3. GC参数建议:");
        printGCTuningAdvice();
    }

    /**
     * GC监控
     */
    private static void monitorGC() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            System.out.println("GC名称: " + gcBean.getName());
            System.out.println("GC次数: " + gcBean.getCollectionCount());
            System.out.println("GC耗时: " + gcBean.getCollectionTime() + "ms");
            System.out.println();
        }
        
        // 内存使用情况
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        System.out.println("堆内存使用:");
        System.out.println("初始: " + (heapUsage.getInit() / _1MB) + "MB");
        System.out.println("已用: " + (heapUsage.getUsed() / _1MB) + "MB");
        System.out.println("提交: " + (heapUsage.getCommitted() / _1MB) + "MB");
        System.out.println("最大: " + (heapUsage.getMax() / _1MB) + "MB");
    }

    /**
     * 内存泄漏检测
     */
    private static void detectMemoryLeak() {
        System.out.println("常见内存泄漏场景:");
        
        System.out.println("\n1. 静态集合持有对象:");
        System.out.println("   private static List<Object> cache = new ArrayList<>();");
        System.out.println("   解决: 使用WeakHashMap或及时清理");
        
        System.out.println("\n2. 监听器未注销:");
        System.out.println("   eventBus.register(listener);");
        System.out.println("   解决: eventBus.unregister(listener);");
        
        System.out.println("\n3. ThreadLocal未清理:");
        System.out.println("   threadLocal.set(value);");
        System.out.println("   解决: threadLocal.remove();");
        
        System.out.println("\n4. 资源未关闭:");
        System.out.println("   Connection conn = getConnection();");
        System.out.println("   解决: 使用try-with-resources");
    }

    /**
     * GC参数调优建议
     */
    private static void printGCTuningAdvice() {
        System.out.println("根据应用类型选择GC:");
        
        System.out.println("\n小型应用（<2GB堆）:");
        System.out.println("java -XX:+UseSerialGC -Xms512m -Xmx512m");
        
        System.out.println("\n中型应用（2-8GB堆）:");
        System.out.println("java -XX:+UseG1GC -Xms4g -Xmx4g -XX:MaxGCPauseMillis=200");
        
        System.out.println("\n大型应用（>8GB堆）:");
        System.out.println("java -XX:+UseG1GC -Xms16g -Xmx16g -XX:MaxGCPauseMillis=100");
        
        System.out.println("\n超大型应用（>32GB堆）:");
        System.out.println("java -XX:+UseZGC -Xms64g -Xmx64g");
        
        System.out.println("\n通用优化参数:");
        System.out.println("-XX:+PrintGCDetails  # 打印GC详情");
        System.out.println("-XX:+PrintGCDateStamps  # 打印GC时间戳");
        System.out.println("-Xloggc:gc.log  # GC日志文件");
        System.out.println("-XX:+HeapDumpOnOutOfMemoryError  # OOM时生成堆转储");
        System.out.println("-XX:HeapDumpPath=/path/to/dumps  # 堆转储路径");
    }
}

/**
 * GC性能对比测试
 */
class GCPerformanceComparison {
    
    private static final int _1MB = 1024 * 1024;
    
    public static void main(String[] args) {
        System.out.println("========== GC性能对比测试 ==========\n");
        System.out.println("测试不同GC收集器的性能");
        System.out.println("请使用不同的GC参数运行本程序\n");
        
        runTest();
    }
    
    private static void runTest() {
        int iterations = 1000;
        List<byte[]> list = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        long startGCTime = getTotalGCTime();
        
        for (int i = 0; i < iterations; i++) {
            list.add(new byte[_1MB]);
            
            if (i % 100 == 0 && i > 0) {
                list.clear();
            }
        }
        
        long endTime = System.currentTimeMillis();
        long endGCTime = getTotalGCTime();
        
        long totalTime = endTime - startTime;
        long gcTime = endGCTime - startGCTime;
        
        System.out.println("性能统计:");
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("GC耗时: " + gcTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f%%", (totalTime - gcTime) * 100.0 / totalTime));
    }
    
    private static long getTotalGCTime() {
        long total = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += gcBean.getCollectionTime();
        }
        return total;
    }
}
