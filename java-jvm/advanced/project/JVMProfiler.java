package com.example.jvm.advanced.project;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * JVM性能分析器
 * 
 * 功能：
 * 1. JIT编译监控
 * 2. 方法内联分析
 * 3. 逃逸分析监控
 * 4. 安全点分析
 * 5. 代码缓存监控
 * 6. 性能报告生成
 * 
 * @author JavaGuide
 */
public class JVMProfiler {

    public static void main(String[] args) throws Exception {
        System.out.println("========== JVM性能分析器 ==========\n");

        // 创建分析器
        Profiler profiler = new Profiler();
        
        // 启动监控
        profiler.start();
        
        // 模拟应用负载
        System.out.println("启动应用负载模拟...\n");
        ApplicationWorkload workload = new ApplicationWorkload();
        workload.start();
        
        // 运行一段时间
        Thread.sleep(30000);  // 运行30秒
        
        // 停止监控
        profiler.stop();
        workload.stop();
        
        // 生成报告
        System.out.println("\n生成性能分析报告...\n");
        profiler.generateReport();
    }

    /**
     * 性能分析器
     */
    static class Profiler {
        
        private final CompilationMonitor compilationMonitor;
        private final CodeCacheMonitor codeCacheMonitor;
        private final OptimizationMonitor optimizationMonitor;
        private final ScheduledExecutorService scheduler;
        private volatile boolean running = false;
        
        public Profiler() {
            this.compilationMonitor = new CompilationMonitor();
            this.codeCacheMonitor = new CodeCacheMonitor();
            this.optimizationMonitor = new OptimizationMonitor();
            this.scheduler = Executors.newScheduledThreadPool(3);
        }
        
        /**
         * 启动监控
         */
        public void start() {
            if (running) {
                return;
            }
            
            running = true;
            System.out.println("性能分析器已启动");
            
            // 监控编译
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    compilationMonitor.monitor();
                } catch (Exception e) {
                    System.err.println("编译监控失败: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            // 监控代码缓存
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    codeCacheMonitor.monitor();
                } catch (Exception e) {
                    System.err.println("代码缓存监控失败: " + e.getMessage());
                }
            }, 0, 5, TimeUnit.SECONDS);
            
            // 监控优化
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    optimizationMonitor.monitor();
                } catch (Exception e) {
                    System.err.println("优化监控失败: " + e.getMessage());
                }
            }, 0, 5, TimeUnit.SECONDS);
        }
        
        /**
         * 停止监控
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
            ProfileReport report = new ProfileReport();
            
            report.compilationStats = compilationMonitor.getStatistics();
            report.codeCacheStats = codeCacheMonitor.getStatistics();
            report.optimizationStats = optimizationMonitor.getStatistics();
            
            report.print();
        }
    }

    /**
     * 编译监控器
     */
    static class CompilationMonitor {
        
        private final CompilationMXBean compilationBean;
        private final List<CompilationEvent> events;
        private long lastCompilationTime = 0;
        
        public CompilationMonitor() {
            this.compilationBean = ManagementFactory.getCompilationMXBean();
            this.events = new CopyOnWriteArrayList<>();
        }
        
        /**
         * 监控编译
         */
        public void monitor() {
            if (compilationBean.isCompilationTimeMonitoringSupported()) {
                long currentTime = compilationBean.getTotalCompilationTime();
                
                if (currentTime > lastCompilationTime) {
                    long delta = currentTime - lastCompilationTime;
                    
                    CompilationEvent event = new CompilationEvent();
                    event.timestamp = System.currentTimeMillis();
                    event.compilationTime = delta;
                    
                    events.add(event);
                    lastCompilationTime = currentTime;
                }
            }
        }
        
        /**
         * 获取统计信息
         */
        public CompilationStatistics getStatistics() {
            CompilationStatistics stats = new CompilationStatistics();
            
            if (compilationBean.isCompilationTimeMonitoringSupported()) {
                stats.totalCompilationTime = compilationBean.getTotalCompilationTime();
                stats.compilationEvents = events.size();
                
                if (!events.isEmpty()) {
                    stats.avgCompilationTime = events.stream()
                        .mapToLong(e -> e.compilationTime)
                        .average()
                        .orElse(0);
                }
            }
            
            return stats;
        }
    }

    /**
     * 代码缓存监控器
     */
    static class CodeCacheMonitor {
        
        private final List<MemoryPoolMXBean> codeCachePools;
        private final List<CodeCacheSnapshot> snapshots;
        
        public CodeCacheMonitor() {
            this.codeCachePools = new ArrayList<>();
            this.snapshots = new CopyOnWriteArrayList<>();
            
            // 查找代码缓存池
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getName().contains("Code Cache") || 
                    pool.getType() == MemoryType.NON_HEAP) {
                    codeCachePools.add(pool);
                }
            }
        }
        
        /**
         * 监控代码缓存
         */
        public void monitor() {
            for (MemoryPoolMXBean pool : codeCachePools) {
                MemoryUsage usage = pool.getUsage();
                
                CodeCacheSnapshot snapshot = new CodeCacheSnapshot();
                snapshot.timestamp = System.currentTimeMillis();
                snapshot.poolName = pool.getName();
                snapshot.used = usage.getUsed();
                snapshot.max = usage.getMax();
                snapshot.usagePercent = usage.getUsed() * 100.0 / usage.getMax();
                
                snapshots.add(snapshot);
                
                // 检测代码缓存满
                if (snapshot.usagePercent > 90) {
                    System.err.println("⚠️  代码缓存使用率过高: " + 
                                     String.format("%.2f%%", snapshot.usagePercent));
                }
            }
        }
        
        /**
         * 获取统计信息
         */
        public CodeCacheStatistics getStatistics() {
            CodeCacheStatistics stats = new CodeCacheStatistics();
            
            if (!snapshots.isEmpty()) {
                CodeCacheSnapshot latest = snapshots.get(snapshots.size() - 1);
                stats.currentUsed = latest.used;
                stats.currentMax = latest.max;
                stats.currentUsagePercent = latest.usagePercent;
                
                stats.avgUsagePercent = snapshots.stream()
                    .mapToDouble(s -> s.usagePercent)
                    .average()
                    .orElse(0);
                
                stats.maxUsagePercent = snapshots.stream()
                    .mapToDouble(s -> s.usagePercent)
                    .max()
                    .orElse(0);
            }
            
            return stats;
        }
    }

    /**
     * 优化监控器
     */
    static class OptimizationMonitor {
        
        private final RuntimeMXBean runtimeBean;
        private final List<OptimizationEvent> events;
        
        public OptimizationMonitor() {
            this.runtimeBean = ManagementFactory.getRuntimeMXBean();
            this.events = new CopyOnWriteArrayList<>();
        }
        
        /**
         * 监控优化
         */
        public void monitor() {
            // 检查JVM参数
            List<String> arguments = runtimeBean.getInputArguments();
            
            OptimizationEvent event = new OptimizationEvent();
            event.timestamp = System.currentTimeMillis();
            
            // 检查逃逸分析
            event.escapeAnalysisEnabled = !arguments.contains("-XX:-DoEscapeAnalysis");
            
            // 检查标量替换
            event.scalarReplacementEnabled = !arguments.contains("-XX:-EliminateAllocations");
            
            // 检查同步消除
            event.lockEliminationEnabled = !arguments.contains("-XX:-EliminateLocks");
            
            // 检查分层编译
            event.tieredCompilationEnabled = !arguments.contains("-XX:-TieredCompilation");
            
            events.add(event);
        }
        
        /**
         * 获取统计信息
         */
        public OptimizationStatistics getStatistics() {
            OptimizationStatistics stats = new OptimizationStatistics();
            
            if (!events.isEmpty()) {
                OptimizationEvent latest = events.get(events.size() - 1);
                stats.escapeAnalysisEnabled = latest.escapeAnalysisEnabled;
                stats.scalarReplacementEnabled = latest.scalarReplacementEnabled;
                stats.lockEliminationEnabled = latest.lockEliminationEnabled;
                stats.tieredCompilationEnabled = latest.tieredCompilationEnabled;
            }
            
            return stats;
        }
    }

    /**
     * 编译事件
     */
    static class CompilationEvent {
        long timestamp;
        long compilationTime;
    }

    /**
     * 代码缓存快照
     */
    static class CodeCacheSnapshot {
        long timestamp;
        String poolName;
        long used;
        long max;
        double usagePercent;
    }

    /**
     * 优化事件
     */
    static class OptimizationEvent {
        long timestamp;
        boolean escapeAnalysisEnabled;
        boolean scalarReplacementEnabled;
        boolean lockEliminationEnabled;
        boolean tieredCompilationEnabled;
    }

    /**
     * 编译统计
     */
    static class CompilationStatistics {
        long totalCompilationTime;
        int compilationEvents;
        double avgCompilationTime;
    }

    /**
     * 代码缓存统计
     */
    static class CodeCacheStatistics {
        long currentUsed;
        long currentMax;
        double currentUsagePercent;
        double avgUsagePercent;
        double maxUsagePercent;
    }

    /**
     * 优化统计
     */
    static class OptimizationStatistics {
        boolean escapeAnalysisEnabled;
        boolean scalarReplacementEnabled;
        boolean lockEliminationEnabled;
        boolean tieredCompilationEnabled;
    }

    /**
     * 性能报告
     */
    static class ProfileReport {
        CompilationStatistics compilationStats;
        CodeCacheStatistics codeCacheStats;
        OptimizationStatistics optimizationStats;
        
        public void print() {
            System.out.println("==================== 性能分析报告 ====================");
            System.out.println();
            
            // 编译统计
            System.out.println("编译统计:");
            System.out.println("  总编译时间: " + compilationStats.totalCompilationTime + "ms");
            System.out.println("  编译事件数: " + compilationStats.compilationEvents);
            System.out.println("  平均编译时间: " + String.format("%.2f", compilationStats.avgCompilationTime) + "ms");
            System.out.println();
            
            // 代码缓存统计
            System.out.println("代码缓存统计:");
            System.out.println("  当前使用: " + (codeCacheStats.currentUsed / 1024 / 1024) + "MB");
            System.out.println("  最大容量: " + (codeCacheStats.currentMax / 1024 / 1024) + "MB");
            System.out.println("  当前使用率: " + String.format("%.2f%%", codeCacheStats.currentUsagePercent));
            System.out.println("  平均使用率: " + String.format("%.2f%%", codeCacheStats.avgUsagePercent));
            System.out.println("  最大使用率: " + String.format("%.2f%%", codeCacheStats.maxUsagePercent));
            System.out.println();
            
            // 优化统计
            System.out.println("优化配置:");
            System.out.println("  逃逸分析: " + (optimizationStats.escapeAnalysisEnabled ? "✓ 开启" : "✗ 关闭"));
            System.out.println("  标量替换: " + (optimizationStats.scalarReplacementEnabled ? "✓ 开启" : "✗ 关闭"));
            System.out.println("  同步消除: " + (optimizationStats.lockEliminationEnabled ? "✓ 开启" : "✗ 关闭"));
            System.out.println("  分层编译: " + (optimizationStats.tieredCompilationEnabled ? "✓ 开启" : "✗ 关闭"));
            System.out.println();
            
            System.out.println("====================================================");
        }
    }

    /**
     * 应用负载模拟
     */
    static class ApplicationWorkload {
        
        private final ExecutorService executor = Executors.newFixedThreadPool(10);
        private volatile boolean running = false;
        
        /**
         * 启动负载
         */
        public void start() {
            running = true;
            
            for (int i = 0; i < 10; i++) {
                executor.submit(this::workload);
            }
        }
        
        /**
         * 停止负载
         */
        public void stop() {
            running = false;
            executor.shutdown();
        }
        
        /**
         * 负载任务
         */
        private void workload() {
            while (running) {
                // 模拟计算
                compute();
                
                // 模拟对象分配
                allocateObjects();
                
                // 模拟同步
                synchronize();
                
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        
        /**
         * 计算任务
         */
        private void compute() {
            int sum = 0;
            for (int i = 0; i < 1000; i++) {
                sum += i * i;
            }
        }
        
        /**
         * 对象分配
         */
        private void allocateObjects() {
            for (int i = 0; i < 100; i++) {
                Object obj = new Object();
            }
        }
        
        /**
         * 同步任务
         */
        private void synchronize() {
            Object lock = new Object();
            synchronized (lock) {
                // 同步块
            }
        }
    }
}

/**
 * JIT编译分析器
 */
class JITCompilationAnalyzer {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========== JIT编译分析器 ==========\n");
        
        System.out.println("需要的JVM参数:");
        System.out.println("-XX:+PrintCompilation");
        System.out.println("-XX:+UnlockDiagnosticVMOptions");
        System.out.println("-XX:+PrintInlining");
        System.out.println("-XX:+LogCompilation");
        System.out.println("-XX:LogFile=compilation.log\n");
        
        CompilationMXBean compilationBean = ManagementFactory.getCompilationMXBean();
        
        System.out.println("编译器信息:");
        System.out.println("  名称: " + compilationBean.getName());
        System.out.println("  支持监控: " + compilationBean.isCompilationTimeMonitoringSupported());
        
        if (compilationBean.isCompilationTimeMonitoringSupported()) {
            long startTime = compilationBean.getTotalCompilationTime();
            
            // 执行一些任务
            for (int i = 0; i < 100000; i++) {
                testMethod(i);
            }
            
            long endTime = compilationBean.getTotalCompilationTime();
            long compilationTime = endTime - startTime;
            
            System.out.println("  编译时间: " + compilationTime + "ms");
        }
    }
    
    private static int testMethod(int n) {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            result += i * n;
        }
        return result;
    }
}

/**
 * 代码缓存分析器
 */
class CodeCacheAnalyzer {
    
    public static void main(String[] args) {
        System.out.println("========== 代码缓存分析器 ==========\n");
        
        List<MemoryPoolMXBean> codeCachePools = new ArrayList<>();
        
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.NON_HEAP) {
                codeCachePools.add(pool);
            }
        }
        
        System.out.println("代码缓存池:");
        for (MemoryPoolMXBean pool : codeCachePools) {
            MemoryUsage usage = pool.getUsage();
            
            System.out.println("\n  " + pool.getName() + ":");
            System.out.println("    初始: " + (usage.getInit() / 1024 / 1024) + "MB");
            System.out.println("    已用: " + (usage.getUsed() / 1024 / 1024) + "MB");
            System.out.println("    提交: " + (usage.getCommitted() / 1024 / 1024) + "MB");
            System.out.println("    最大: " + (usage.getMax() / 1024 / 1024) + "MB");
            System.out.println("    使用率: " + String.format("%.2f%%", 
                usage.getUsed() * 100.0 / usage.getMax()));
        }
        
        System.out.println("\n配置参数:");
        System.out.println("-XX:ReservedCodeCacheSize=240m  # 代码缓存大小");
        System.out.println("-XX:InitialCodeCacheSize=160m   # 初始大小");
    }
}

/**
 * 方法内联分析器
 */
class InliningAnalyzer {
    
    public static void main(String[] args) {
        System.out.println("========== 方法内联分析器 ==========\n");
        
        System.out.println("需要的JVM参数:");
        System.out.println("-XX:+PrintInlining");
        System.out.println("-XX:+UnlockDiagnosticVMOptions\n");
        
        System.out.println("内联配置:");
        System.out.println("-XX:MaxInlineSize=35            # 最大内联方法大小");
        System.out.println("-XX:FreqInlineSize=325          # 热点方法内联大小");
        System.out.println("-XX:MaxInlineLevel=9            # 最大内联层数\n");
        
        // 预热
        for (int i = 0; i < 20000; i++) {
            testInlining(i);
        }
        
        System.out.println("观察 -XX:+PrintInlining 输出");
        System.out.println("查看方法内联情况");
    }
    
    private static int testInlining(int n) {
        return add(n, 1) + multiply(n, 2);
    }
    
    private static int add(int a, int b) {
        return a + b;
    }
    
    private static int multiply(int a, int b) {
        return a * b;
    }
}
