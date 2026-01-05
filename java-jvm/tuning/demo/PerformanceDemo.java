package com.example.jvm.tuning.demo;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 性能监控演示
 * 
 * 演示内容：
 * 1. JVM性能指标获取
 * 2. GC监控
 * 3. 线程监控
 * 4. 内存监控
 * 5. 类加载监控
 * 
 * @author JavaGuide
 */
public class PerformanceDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== JVM性能监控演示 ==========\n");
        
        System.out.println("1. JVM基本信息");
        demonstrateJVMInfo();
        
        System.out.println("\n2. 内存监控");
        demonstrateMemoryMonitoring();
        
        System.out.println("\n3. GC监控");
        demonstrateGCMonitoring();
        
        System.out.println("\n4. 线程监控");
        demonstrateThreadMonitoring();
        
        System.out.println("\n5. 类加载监控");
        demonstrateClassLoadingMonitoring();
        
        System.out.println("\n6. 性能压测");
        demonstratePerformanceTest();
    }

    /**
     * JVM基本信息
     */
    private static void demonstrateJVMInfo() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        
        System.out.println("JVM名称: " + runtimeBean.getVmName());
        System.out.println("JVM版本: " + runtimeBean.getVmVersion());
        System.out.println("JVM厂商: " + runtimeBean.getVmVendor());
        System.out.println("启动时间: " + new Date(runtimeBean.getStartTime()));
        System.out.println("运行时长: " + (runtimeBean.getUptime() / 1000) + "秒");
        
        System.out.println("\nJVM参数:");
        List<String> arguments = runtimeBean.getInputArguments();
        for (String arg : arguments) {
            System.out.println("  " + arg);
        }
    }

    /**
     * 内存监控
     */
    private static void demonstrateMemoryMonitoring() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        // 堆内存
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        System.out.println("堆内存:");
        printMemoryUsage(heapUsage);
        
        // 非堆内存
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        System.out.println("\n非堆内存:");
        printMemoryUsage(nonHeapUsage);
        
        // 各个内存池
        System.out.println("\n内存池详情:");
        List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean poolBean : poolBeans) {
            System.out.println("  " + poolBean.getName() + ":");
            MemoryUsage usage = poolBean.getUsage();
            System.out.println("    已用: " + (usage.getUsed() / 1024 / 1024) + "MB");
            System.out.println("    最大: " + (usage.getMax() / 1024 / 1024) + "MB");
            System.out.println("    使用率: " + String.format("%.2f%%", 
                usage.getUsed() * 100.0 / usage.getMax()));
        }
    }

    /**
     * GC监控
     */
    private static void demonstrateGCMonitoring() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            System.out.println("GC名称: " + gcBean.getName());
            System.out.println("  GC次数: " + gcBean.getCollectionCount());
            System.out.println("  GC时间: " + gcBean.getCollectionTime() + "ms");
            
            if (gcBean.getCollectionCount() > 0) {
                long avgTime = gcBean.getCollectionTime() / gcBean.getCollectionCount();
                System.out.println("  平均GC时间: " + avgTime + "ms");
            }
            System.out.println();
        }
        
        // 计算GC吞吐量
        long totalGCTime = 0;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalGCTime += gcBean.getCollectionTime();
        }
        
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long uptime = runtimeBean.getUptime();
        double throughput = (uptime - totalGCTime) * 100.0 / uptime;
        
        System.out.println("GC吞吐量: " + String.format("%.2f%%", throughput));
    }

    /**
     * 线程监控
     */
    private static void demonstrateThreadMonitoring() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        System.out.println("线程总数: " + threadBean.getThreadCount());
        System.out.println("峰值线程数: " + threadBean.getPeakThreadCount());
        System.out.println("守护线程数: " + threadBean.getDaemonThreadCount());
        System.out.println("启动线程总数: " + threadBean.getTotalStartedThreadCount());
        
        // 线程状态统计
        long[] threadIds = threadBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
        
        Map<Thread.State, Integer> stateCount = new HashMap<>();
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                Thread.State state = info.getThreadState();
                stateCount.put(state, stateCount.getOrDefault(state, 0) + 1);
            }
        }
        
        System.out.println("\n线程状态分布:");
        for (Map.Entry<Thread.State, Integer> entry : stateCount.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
        
        // 检测死锁
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            System.out.println("\n⚠️  检测到死锁线程: " + deadlockedThreads.length);
        } else {
            System.out.println("\n✓ 未检测到死锁");
        }
    }

    /**
     * 类加载监控
     */
    private static void demonstrateClassLoadingMonitoring() {
        ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
        
        System.out.println("已加载类数: " + classLoadingBean.getLoadedClassCount());
        System.out.println("总加载类数: " + classLoadingBean.getTotalLoadedClassCount());
        System.out.println("已卸载类数: " + classLoadingBean.getUnloadedClassCount());
    }

    /**
     * 性能压测
     */
    private static void demonstratePerformanceTest() throws InterruptedException {
        System.out.println("开始性能压测...");
        
        int threadCount = 10;
        int iterations = 1000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        // 模拟业务处理
                        doWork();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        long duration = endTime - startTime;
        int totalRequests = threadCount * iterations;
        double qps = totalRequests * 1000.0 / duration;
        
        System.out.println("\n压测结果:");
        System.out.println("  总请求数: " + totalRequests);
        System.out.println("  总耗时: " + duration + "ms");
        System.out.println("  QPS: " + String.format("%.2f", qps));
        System.out.println("  平均响应时间: " + (duration * 1.0 / totalRequests) + "ms");
    }

    /**
     * 模拟业务处理
     */
    private static void doWork() {
        // 创建一些对象
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add("item" + i);
        }
        
        // 模拟计算
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            sum += i;
        }
    }

    /**
     * 打印内存使用情况
     */
    private static void printMemoryUsage(MemoryUsage usage) {
        long init = usage.getInit();
        long used = usage.getUsed();
        long committed = usage.getCommitted();
        long max = usage.getMax();
        
        System.out.println("  初始: " + (init / 1024 / 1024) + "MB");
        System.out.println("  已用: " + (used / 1024 / 1024) + "MB");
        System.out.println("  提交: " + (committed / 1024 / 1024) + "MB");
        System.out.println("  最大: " + (max / 1024 / 1024) + "MB");
        System.out.println("  使用率: " + String.format("%.2f%%", used * 100.0 / max));
    }
}

/**
 * 实时性能监控
 */
class RealTimeMonitor {
    
    private static final int _1MB = 1024 * 1024;
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 实时性能监控 ==========\n");
        System.out.println("每秒刷新一次，按Ctrl+C停止\n");
        
        while (true) {
            printMetrics();
            Thread.sleep(1000);
            System.out.println("----------------------------------------");
        }
    }
    
    private static void printMetrics() {
        // 内存
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        System.out.println("堆内存: " + (heapUsage.getUsed() / _1MB) + "MB / " + 
                         (heapUsage.getMax() / _1MB) + "MB (" + 
                         String.format("%.2f%%", heapUsage.getUsed() * 100.0 / heapUsage.getMax()) + ")");
        
        // GC
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            System.out.println(gcBean.getName() + ": " + 
                             gcBean.getCollectionCount() + "次, " + 
                             gcBean.getCollectionTime() + "ms");
        }
        
        // 线程
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        System.out.println("线程数: " + threadBean.getThreadCount());
        
        // CPU
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = 
                (com.sun.management.OperatingSystemMXBean) osBean;
            double cpuLoad = sunOsBean.getProcessCpuLoad() * 100;
            System.out.println("CPU使用率: " + String.format("%.2f%%", cpuLoad));
        }
    }
}

/**
 * 性能对比测试
 */
class PerformanceComparison {
    
    public static void main(String[] args) {
        System.out.println("========== 性能对比测试 ==========\n");
        
        int iterations = 1000000;
        
        // 测试1：StringBuilder vs String拼接
        System.out.println("1. StringBuilder vs String拼接:");
        testStringConcatenation(iterations);
        
        // 测试2：ArrayList vs LinkedList
        System.out.println("\n2. ArrayList vs LinkedList:");
        testListPerformance(iterations);
        
        // 测试3：HashMap vs ConcurrentHashMap
        System.out.println("\n3. HashMap vs ConcurrentHashMap:");
        testMapPerformance(iterations);
    }
    
    private static void testStringConcatenation(int iterations) {
        // String拼接
        long start = System.currentTimeMillis();
        String str = "";
        for (int i = 0; i < 10000; i++) {
            str += "a";
        }
        long stringTime = System.currentTimeMillis() - start;
        
        // StringBuilder
        start = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("a");
        }
        long sbTime = System.currentTimeMillis() - start;
        
        System.out.println("  String拼接: " + stringTime + "ms");
        System.out.println("  StringBuilder: " + sbTime + "ms");
        System.out.println("  性能提升: " + (stringTime * 1.0 / sbTime) + "倍");
    }
    
    private static void testListPerformance(int iterations) {
        // ArrayList
        long start = System.currentTimeMillis();
        List<Integer> arrayList = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            arrayList.add(i);
        }
        long arrayListTime = System.currentTimeMillis() - start;
        
        // LinkedList
        start = System.currentTimeMillis();
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < iterations; i++) {
            linkedList.add(i);
        }
        long linkedListTime = System.currentTimeMillis() - start;
        
        System.out.println("  ArrayList: " + arrayListTime + "ms");
        System.out.println("  LinkedList: " + linkedListTime + "ms");
    }
    
    private static void testMapPerformance(int iterations) {
        // HashMap
        long start = System.currentTimeMillis();
        Map<Integer, Integer> hashMap = new HashMap<>();
        for (int i = 0; i < iterations; i++) {
            hashMap.put(i, i);
        }
        long hashMapTime = System.currentTimeMillis() - start;
        
        // ConcurrentHashMap
        start = System.currentTimeMillis();
        Map<Integer, Integer> concurrentMap = new ConcurrentHashMap<>();
        for (int i = 0; i < iterations; i++) {
            concurrentMap.put(i, i);
        }
        long concurrentMapTime = System.currentTimeMillis() - start;
        
        System.out.println("  HashMap: " + hashMapTime + "ms");
        System.out.println("  ConcurrentHashMap: " + concurrentMapTime + "ms");
    }
}
