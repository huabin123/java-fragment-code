package com.example.jvm.tuning.project;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 性能分析器实战项目
 * 
 * 功能：
 * 1. CPU热点分析
 * 2. 内存分析
 * 3. GC分析
 * 4. 线程分析
 * 5. 性能瓶颈定位
 * 6. 生成分析报告
 * 
 * @author JavaGuide
 */
public class PerformanceAnalyzer {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 性能分析器 ==========\n");

        // 创建分析器
        Analyzer analyzer = new Analyzer();
        
        // 启动分析
        analyzer.start();
        
        // 模拟应用负载
        System.out.println("启动应用负载...\n");
        TestApplication app = new TestApplication();
        app.start();
        
        // 运行一段时间
        Thread.sleep(30000);  // 运行30秒
        
        // 停止分析
        analyzer.stop();
        app.stop();
        
        // 生成报告
        System.out.println("\n生成性能分析报告...\n");
        analyzer.generateReport();
    }

    /**
     * 性能分析器
     */
    static class Analyzer {
        
        private final CPUAnalyzer cpuAnalyzer;
        private final MemoryAnalyzer memoryAnalyzer;
        private final GCAnalyzer gcAnalyzer;
        private final ThreadAnalyzer threadAnalyzer;
        private final ScheduledExecutorService scheduler;
        private volatile boolean running = false;
        
        public Analyzer() {
            this.cpuAnalyzer = new CPUAnalyzer();
            this.memoryAnalyzer = new MemoryAnalyzer();
            this.gcAnalyzer = new GCAnalyzer();
            this.threadAnalyzer = new ThreadAnalyzer();
            this.scheduler = Executors.newScheduledThreadPool(4);
        }
        
        /**
         * 启动分析
         */
        public void start() {
            if (running) {
                return;
            }
            
            running = true;
            System.out.println("性能分析器已启动");
            
            // CPU分析
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    cpuAnalyzer.analyze();
                } catch (Exception e) {
                    System.err.println("CPU分析失败: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            // 内存分析
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    memoryAnalyzer.analyze();
                } catch (Exception e) {
                    System.err.println("内存分析失败: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            // GC分析
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    gcAnalyzer.analyze();
                } catch (Exception e) {
                    System.err.println("GC分析失败: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            // 线程分析
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    threadAnalyzer.analyze();
                } catch (Exception e) {
                    System.err.println("线程分析失败: " + e.getMessage());
                }
            }, 0, 5, TimeUnit.SECONDS);
        }
        
        /**
         * 停止分析
         */
        public void stop() {
            if (!running) {
                return;
            }
            
            running = false;
            scheduler.shutdown();
            System.out.println("性能分析器已停止");
        }
        
        /**
         * 生成报告
         */
        public void generateReport() {
            AnalysisReport report = new AnalysisReport();
            
            report.cpuAnalysis = cpuAnalyzer.getResult();
            report.memoryAnalysis = memoryAnalyzer.getResult();
            report.gcAnalysis = gcAnalyzer.getResult();
            report.threadAnalysis = threadAnalyzer.getResult();
            
            report.print();
        }
    }

    /**
     * CPU分析器
     */
    static class CPUAnalyzer {
        
        private final List<Double> cpuUsageHistory = new ArrayList<>();
        private double maxCpuUsage = 0;
        
        /**
         * 分析CPU
         */
        public void analyze() {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                
                double cpuLoad = sunOsBean.getProcessCpuLoad() * 100;
                cpuUsageHistory.add(cpuLoad);
                
                if (cpuLoad > maxCpuUsage) {
                    maxCpuUsage = cpuLoad;
                }
                
                // 检测CPU异常
                if (cpuLoad > 80) {
                    System.err.println("⚠️  CPU使用率过高: " + String.format("%.2f%%", cpuLoad));
                }
            }
        }
        
        /**
         * 获取分析结果
         */
        public CPUAnalysisResult getResult() {
            CPUAnalysisResult result = new CPUAnalysisResult();
            
            if (!cpuUsageHistory.isEmpty()) {
                result.avgCpuUsage = cpuUsageHistory.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);
                
                result.maxCpuUsage = maxCpuUsage;
                
                // 分析CPU热点
                result.hotspots = analyzeHotspots();
            }
            
            return result;
        }
        
        /**
         * 分析CPU热点
         */
        private List<String> analyzeHotspots() {
            List<String> hotspots = new ArrayList<>();
            
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long[] threadIds = threadBean.getAllThreadIds();
            
            // 获取CPU时间最多的线程
            Map<Long, Long> threadCpuTimes = new HashMap<>();
            for (long threadId : threadIds) {
                long cpuTime = threadBean.getThreadCpuTime(threadId);
                if (cpuTime > 0) {
                    threadCpuTimes.put(threadId, cpuTime);
                }
            }
            
            // 排序并取前5个
            threadCpuTimes.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    ThreadInfo info = threadBean.getThreadInfo(entry.getKey());
                    if (info != null) {
                        hotspots.add(info.getThreadName() + " (CPU时间: " + 
                                   (entry.getValue() / 1000000) + "ms)");
                    }
                });
            
            return hotspots;
        }
    }

    /**
     * 内存分析器
     */
    static class MemoryAnalyzer {
        
        private final List<Long> heapUsageHistory = new ArrayList<>();
        private long maxHeapUsage = 0;
        
        /**
         * 分析内存
         */
        public void analyze() {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            
            long used = heapUsage.getUsed();
            heapUsageHistory.add(used);
            
            if (used > maxHeapUsage) {
                maxHeapUsage = used;
            }
            
            // 检测内存异常
            double usagePercent = used * 100.0 / heapUsage.getMax();
            if (usagePercent > 85) {
                System.err.println("⚠️  堆内存使用率过高: " + String.format("%.2f%%", usagePercent));
            }
            
            // 检测内存泄漏
            if (heapUsageHistory.size() >= 10) {
                detectMemoryLeak();
            }
        }
        
        /**
         * 检测内存泄漏
         */
        private void detectMemoryLeak() {
            int size = heapUsageHistory.size();
            long first = heapUsageHistory.get(size - 10);
            long last = heapUsageHistory.get(size - 1);
            
            long growth = last - first;
            double growthRate = growth * 100.0 / first;
            
            if (growthRate > 50) {
                System.err.println("⚠️  检测到内存持续增长，可能存在内存泄漏");
                System.err.println("   增长率: " + String.format("%.2f%%", growthRate));
            }
        }
        
        /**
         * 获取分析结果
         */
        public MemoryAnalysisResult getResult() {
            MemoryAnalysisResult result = new MemoryAnalysisResult();
            
            if (!heapUsageHistory.isEmpty()) {
                result.avgHeapUsage = heapUsageHistory.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
                
                result.maxHeapUsage = maxHeapUsage;
                
                // 分析内存池
                result.memoryPools = analyzeMemoryPools();
            }
            
            return result;
        }
        
        /**
         * 分析内存池
         */
        private List<String> analyzeMemoryPools() {
            List<String> pools = new ArrayList<>();
            
            List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean poolBean : poolBeans) {
                MemoryUsage usage = poolBean.getUsage();
                double usagePercent = usage.getUsed() * 100.0 / usage.getMax();
                
                pools.add(poolBean.getName() + ": " + 
                         String.format("%.2f%%", usagePercent));
            }
            
            return pools;
        }
    }

    /**
     * GC分析器
     */
    static class GCAnalyzer {
        
        private final Map<String, Long> lastGCCount = new ConcurrentHashMap<>();
        private final Map<String, Long> lastGCTime = new ConcurrentHashMap<>();
        private final List<GCEvent> gcEvents = new ArrayList<>();
        
        /**
         * 分析GC
         */
        public void analyze() {
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                String name = gcBean.getName();
                long count = gcBean.getCollectionCount();
                long time = gcBean.getCollectionTime();
                
                Long lastCount = lastGCCount.get(name);
                Long lastTime = lastGCTime.get(name);
                
                if (lastCount != null && count > lastCount) {
                    // 发生了GC
                    long deltaCount = count - lastCount;
                    long deltaTime = time - lastTime;
                    
                    GCEvent event = new GCEvent();
                    event.gcName = name;
                    event.count = deltaCount;
                    event.time = deltaTime;
                    event.avgTime = deltaTime * 1.0 / deltaCount;
                    event.timestamp = System.currentTimeMillis();
                    
                    gcEvents.add(event);
                    
                    // 检测GC异常
                    if (deltaTime > 1000) {
                        System.err.println("⚠️  GC停顿时间过长: " + name + " " + deltaTime + "ms");
                    }
                }
                
                lastGCCount.put(name, count);
                lastGCTime.put(name, time);
            }
        }
        
        /**
         * 获取分析结果
         */
        public GCAnalysisResult getResult() {
            GCAnalysisResult result = new GCAnalysisResult();
            
            // 统计GC次数和时间
            Map<String, Long> gcCountMap = new HashMap<>();
            Map<String, Long> gcTimeMap = new HashMap<>();
            
            for (GCEvent event : gcEvents) {
                gcCountMap.merge(event.gcName, event.count, Long::sum);
                gcTimeMap.merge(event.gcName, event.time, Long::sum);
            }
            
            result.totalGCCount = gcCountMap.values().stream().mapToLong(Long::longValue).sum();
            result.totalGCTime = gcTimeMap.values().stream().mapToLong(Long::longValue).sum();
            
            // 分GC类型统计
            for (Map.Entry<String, Long> entry : gcCountMap.entrySet()) {
                String gcName = entry.getKey();
                long count = entry.getValue();
                long time = gcTimeMap.get(gcName);
                
                result.gcStats.add(gcName + ": " + count + "次, " + time + "ms");
            }
            
            return result;
        }
    }

    /**
     * 线程分析器
     */
    static class ThreadAnalyzer {
        
        private int maxThreadCount = 0;
        private final List<String> deadlocks = new ArrayList<>();
        
        /**
         * 分析线程
         */
        public void analyze() {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            
            int threadCount = threadBean.getThreadCount();
            if (threadCount > maxThreadCount) {
                maxThreadCount = threadCount;
            }
            
            // 检测死锁
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            if (deadlockedThreads != null && deadlockedThreads.length > 0) {
                System.err.println("⚠️  检测到死锁: " + deadlockedThreads.length + " 个线程");
                
                for (long threadId : deadlockedThreads) {
                    ThreadInfo info = threadBean.getThreadInfo(threadId);
                    if (info != null) {
                        deadlocks.add(info.getThreadName());
                    }
                }
            }
        }
        
        /**
         * 获取分析结果
         */
        public ThreadAnalysisResult getResult() {
            ThreadAnalysisResult result = new ThreadAnalysisResult();
            
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            result.currentThreadCount = threadBean.getThreadCount();
            result.maxThreadCount = maxThreadCount;
            result.deadlocks = new ArrayList<>(deadlocks);
            
            // 线程状态分布
            result.threadStates = analyzeThreadStates();
            
            return result;
        }
        
        /**
         * 分析线程状态
         */
        private Map<Thread.State, Integer> analyzeThreadStates() {
            Map<Thread.State, Integer> stateCount = new HashMap<>();
            
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long[] threadIds = threadBean.getAllThreadIds();
            ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
            
            for (ThreadInfo info : threadInfos) {
                if (info != null) {
                    Thread.State state = info.getThreadState();
                    stateCount.put(state, stateCount.getOrDefault(state, 0) + 1);
                }
            }
            
            return stateCount;
        }
    }

    /**
     * GC事件
     */
    static class GCEvent {
        String gcName;
        long count;
        long time;
        double avgTime;
        long timestamp;
    }

    /**
     * CPU分析结果
     */
    static class CPUAnalysisResult {
        double avgCpuUsage;
        double maxCpuUsage;
        List<String> hotspots = new ArrayList<>();
    }

    /**
     * 内存分析结果
     */
    static class MemoryAnalysisResult {
        double avgHeapUsage;
        long maxHeapUsage;
        List<String> memoryPools = new ArrayList<>();
    }

    /**
     * GC分析结果
     */
    static class GCAnalysisResult {
        long totalGCCount;
        long totalGCTime;
        List<String> gcStats = new ArrayList<>();
    }

    /**
     * 线程分析结果
     */
    static class ThreadAnalysisResult {
        int currentThreadCount;
        int maxThreadCount;
        List<String> deadlocks = new ArrayList<>();
        Map<Thread.State, Integer> threadStates = new HashMap<>();
    }

    /**
     * 分析报告
     */
    static class AnalysisReport {
        CPUAnalysisResult cpuAnalysis;
        MemoryAnalysisResult memoryAnalysis;
        GCAnalysisResult gcAnalysis;
        ThreadAnalysisResult threadAnalysis;
        
        public void print() {
            System.out.println("==================== 性能分析报告 ====================");
            System.out.println();
            
            // CPU分析
            System.out.println("CPU分析:");
            System.out.println("  平均CPU使用率: " + String.format("%.2f%%", cpuAnalysis.avgCpuUsage));
            System.out.println("  最大CPU使用率: " + String.format("%.2f%%", cpuAnalysis.maxCpuUsage));
            if (!cpuAnalysis.hotspots.isEmpty()) {
                System.out.println("  CPU热点:");
                for (String hotspot : cpuAnalysis.hotspots) {
                    System.out.println("    " + hotspot);
                }
            }
            System.out.println();
            
            // 内存分析
            System.out.println("内存分析:");
            System.out.println("  平均堆使用: " + (memoryAnalysis.avgHeapUsage / 1024 / 1024) + "MB");
            System.out.println("  最大堆使用: " + (memoryAnalysis.maxHeapUsage / 1024 / 1024) + "MB");
            if (!memoryAnalysis.memoryPools.isEmpty()) {
                System.out.println("  内存池:");
                for (String pool : memoryAnalysis.memoryPools) {
                    System.out.println("    " + pool);
                }
            }
            System.out.println();
            
            // GC分析
            System.out.println("GC分析:");
            System.out.println("  总GC次数: " + gcAnalysis.totalGCCount);
            System.out.println("  总GC时间: " + gcAnalysis.totalGCTime + "ms");
            if (!gcAnalysis.gcStats.isEmpty()) {
                System.out.println("  GC统计:");
                for (String stat : gcAnalysis.gcStats) {
                    System.out.println("    " + stat);
                }
            }
            System.out.println();
            
            // 线程分析
            System.out.println("线程分析:");
            System.out.println("  当前线程数: " + threadAnalysis.currentThreadCount);
            System.out.println("  最大线程数: " + threadAnalysis.maxThreadCount);
            if (!threadAnalysis.deadlocks.isEmpty()) {
                System.out.println("  死锁线程:");
                for (String deadlock : threadAnalysis.deadlocks) {
                    System.out.println("    " + deadlock);
                }
            }
            if (!threadAnalysis.threadStates.isEmpty()) {
                System.out.println("  线程状态分布:");
                for (Map.Entry<Thread.State, Integer> entry : threadAnalysis.threadStates.entrySet()) {
                    System.out.println("    " + entry.getKey() + ": " + entry.getValue());
                }
            }
            System.out.println();
            
            System.out.println("====================================================");
        }
    }

    /**
     * 测试应用
     */
    static class TestApplication {
        
        private static final int _1MB = 1024 * 1024;
        private final ExecutorService executor = Executors.newFixedThreadPool(10);
        private final List<byte[]> cache = new CopyOnWriteArrayList<>();
        private volatile boolean running = false;
        
        public void start() {
            running = true;
            
            // 启动多个任务
            for (int i = 0; i < 10; i++) {
                executor.submit(this::task);
            }
        }
        
        public void stop() {
            running = false;
            executor.shutdown();
            cache.clear();
        }
        
        private void task() {
            while (running) {
                // 模拟内存分配
                cache.add(new byte[_1MB]);
                
                if (cache.size() > 50) {
                    cache.clear();
                }
                
                // 模拟CPU计算
                long sum = 0;
                for (int i = 0; i < 100000; i++) {
                    sum += i;
                }
                
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
