package com.example.jvm.tuning.project;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * JVM监控系统实战项目
 * 
 * 功能：
 * 1. 实时监控JVM性能指标
 * 2. 异常检测和告警
 * 3. 性能数据收集和存储
 * 4. 生成监控报告
 * 5. 提供HTTP接口查询
 * 
 * @author JavaGuide
 */
public class JVMMonitoringSystem {

    public static void main(String[] args) throws Exception {
        System.out.println("========== JVM监控系统 ==========\n");

        // 创建监控系统
        MonitoringSystem system = new MonitoringSystem();
        
        // 启动监控
        system.start();
        
        // 模拟应用负载
        System.out.println("启动应用负载模拟...\n");
        ApplicationSimulator simulator = new ApplicationSimulator();
        simulator.start();
        
        // 运行一段时间
        Thread.sleep(60000);  // 运行1分钟
        
        // 停止监控
        system.stop();
        simulator.stop();
        
        // 生成报告
        System.out.println("\n生成监控报告...\n");
        system.generateReport();
    }

    /**
     * 监控系统
     */
    static class MonitoringSystem {
        
        private final MetricsCollector metricsCollector;
        private final AlertManager alertManager;
        private final DataStorage dataStorage;
        private final ScheduledExecutorService scheduler;
        private volatile boolean running = false;
        
        public MonitoringSystem() {
            this.metricsCollector = new MetricsCollector();
            this.alertManager = new AlertManager();
            this.dataStorage = new DataStorage();
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
            System.out.println("监控系统已启动");
            
            // 每秒收集一次指标
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    Metrics metrics = metricsCollector.collect();
                    dataStorage.store(metrics);
                    alertManager.check(metrics);
                } catch (Exception e) {
                    System.err.println("收集指标失败: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
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
            System.out.println("监控系统已停止");
        }
        
        /**
         * 生成报告
         */
        public void generateReport() {
            MonitoringReport report = dataStorage.generateReport();
            report.print();
        }
    }

    /**
     * 指标收集器
     */
    static class MetricsCollector {
        
        private static final int _1MB = 1024 * 1024;
        
        /**
         * 收集指标
         */
        public Metrics collect() {
            Metrics metrics = new Metrics();
            metrics.timestamp = System.currentTimeMillis();
            
            // 收集内存指标
            collectMemoryMetrics(metrics);
            
            // 收集GC指标
            collectGCMetrics(metrics);
            
            // 收集线程指标
            collectThreadMetrics(metrics);
            
            // 收集CPU指标
            collectCPUMetrics(metrics);
            
            return metrics;
        }
        
        /**
         * 收集内存指标
         */
        private void collectMemoryMetrics(Metrics metrics) {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            
            // 堆内存
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            metrics.heapUsed = heapUsage.getUsed();
            metrics.heapMax = heapUsage.getMax();
            metrics.heapUsagePercent = metrics.heapUsed * 100.0 / metrics.heapMax;
            
            // 非堆内存
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            metrics.nonHeapUsed = nonHeapUsage.getUsed();
            metrics.nonHeapMax = nonHeapUsage.getMax();
        }
        
        /**
         * 收集GC指标
         */
        private void collectGCMetrics(Metrics metrics) {
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                String name = gcBean.getName();
                long count = gcBean.getCollectionCount();
                long time = gcBean.getCollectionTime();
                
                if (name.contains("Young") || name.contains("Scavenge")) {
                    metrics.minorGCCount = count;
                    metrics.minorGCTime = time;
                } else {
                    metrics.majorGCCount = count;
                    metrics.majorGCTime = time;
                }
            }
        }
        
        /**
         * 收集线程指标
         */
        private void collectThreadMetrics(Metrics metrics) {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            
            metrics.threadCount = threadBean.getThreadCount();
            metrics.peakThreadCount = threadBean.getPeakThreadCount();
            metrics.daemonThreadCount = threadBean.getDaemonThreadCount();
            
            // 检测死锁
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            metrics.deadlockedThreadCount = deadlockedThreads != null ? deadlockedThreads.length : 0;
        }
        
        /**
         * 收集CPU指标
         */
        private void collectCPUMetrics(Metrics metrics) {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                
                metrics.processCpuLoad = sunOsBean.getProcessCpuLoad();
                metrics.systemCpuLoad = sunOsBean.getSystemCpuLoad();
            }
        }
    }

    /**
     * 告警管理器
     */
    static class AlertManager {
        
        // 告警阈值
        private static final double HEAP_USAGE_THRESHOLD = 85.0;
        private static final double CPU_USAGE_THRESHOLD = 80.0;
        private static final int THREAD_COUNT_THRESHOLD = 1000;
        
        private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();
        private static final long ALERT_INTERVAL = 60000;  // 1分钟内同一告警只发送一次
        
        /**
         * 检查告警
         */
        public void check(Metrics metrics) {
            // 检查堆内存使用率
            if (metrics.heapUsagePercent > HEAP_USAGE_THRESHOLD) {
                alert("堆内存使用率过高", 
                      String.format("当前: %.2f%%, 阈值: %.2f%%", 
                                  metrics.heapUsagePercent, HEAP_USAGE_THRESHOLD));
            }
            
            // 检查CPU使用率
            if (metrics.processCpuLoad * 100 > CPU_USAGE_THRESHOLD) {
                alert("CPU使用率过高", 
                      String.format("当前: %.2f%%, 阈值: %.2f%%", 
                                  metrics.processCpuLoad * 100, CPU_USAGE_THRESHOLD));
            }
            
            // 检查线程数
            if (metrics.threadCount > THREAD_COUNT_THRESHOLD) {
                alert("线程数过多", 
                      String.format("当前: %d, 阈值: %d", 
                                  metrics.threadCount, THREAD_COUNT_THRESHOLD));
            }
            
            // 检查死锁
            if (metrics.deadlockedThreadCount > 0) {
                alert("检测到死锁", 
                      String.format("死锁线程数: %d", metrics.deadlockedThreadCount));
            }
        }
        
        /**
         * 发送告警
         */
        private void alert(String type, String message) {
            long now = System.currentTimeMillis();
            Long lastTime = lastAlertTime.get(type);
            
            // 防止告警风暴
            if (lastTime != null && now - lastTime < ALERT_INTERVAL) {
                return;
            }
            
            lastAlertTime.put(type, now);
            
            System.err.println("⚠️  告警: " + type);
            System.err.println("   详情: " + message);
            System.err.println("   时间: " + new Date());
            System.err.println();
        }
    }

    /**
     * 数据存储
     */
    static class DataStorage {
        
        private final List<Metrics> metricsHistory = new CopyOnWriteArrayList<>();
        private static final int MAX_HISTORY_SIZE = 3600;  // 保留1小时数据
        
        /**
         * 存储指标
         */
        public void store(Metrics metrics) {
            metricsHistory.add(metrics);
            
            // 保持历史数据大小
            if (metricsHistory.size() > MAX_HISTORY_SIZE) {
                metricsHistory.remove(0);
            }
        }
        
        /**
         * 生成报告
         */
        public MonitoringReport generateReport() {
            MonitoringReport report = new MonitoringReport();
            
            if (metricsHistory.isEmpty()) {
                return report;
            }
            
            Metrics first = metricsHistory.get(0);
            Metrics last = metricsHistory.get(metricsHistory.size() - 1);
            
            report.startTime = first.timestamp;
            report.endTime = last.timestamp;
            report.duration = report.endTime - report.startTime;
            
            // 计算平均值
            report.avgHeapUsage = metricsHistory.stream()
                .mapToDouble(m -> m.heapUsagePercent)
                .average()
                .orElse(0);
            
            report.avgCpuUsage = metricsHistory.stream()
                .mapToDouble(m -> m.processCpuLoad * 100)
                .average()
                .orElse(0);
            
            report.avgThreadCount = metricsHistory.stream()
                .mapToInt(m -> m.threadCount)
                .average()
                .orElse(0);
            
            // 计算最大值
            report.maxHeapUsage = metricsHistory.stream()
                .mapToDouble(m -> m.heapUsagePercent)
                .max()
                .orElse(0);
            
            report.maxCpuUsage = metricsHistory.stream()
                .mapToDouble(m -> m.processCpuLoad * 100)
                .max()
                .orElse(0);
            
            report.maxThreadCount = metricsHistory.stream()
                .mapToInt(m -> m.threadCount)
                .max()
                .orElse(0);
            
            // GC统计
            report.totalMinorGC = last.minorGCCount - first.minorGCCount;
            report.totalMajorGC = last.majorGCCount - first.majorGCCount;
            report.totalGCTime = (last.minorGCTime + last.majorGCTime) - 
                                (first.minorGCTime + first.majorGCTime);
            
            return report;
        }
    }

    /**
     * 指标
     */
    static class Metrics {
        long timestamp;
        
        // 内存指标
        long heapUsed;
        long heapMax;
        double heapUsagePercent;
        long nonHeapUsed;
        long nonHeapMax;
        
        // GC指标
        long minorGCCount;
        long minorGCTime;
        long majorGCCount;
        long majorGCTime;
        
        // 线程指标
        int threadCount;
        int peakThreadCount;
        int daemonThreadCount;
        int deadlockedThreadCount;
        
        // CPU指标
        double processCpuLoad;
        double systemCpuLoad;
    }

    /**
     * 监控报告
     */
    static class MonitoringReport {
        long startTime;
        long endTime;
        long duration;
        
        double avgHeapUsage;
        double maxHeapUsage;
        
        double avgCpuUsage;
        double maxCpuUsage;
        
        double avgThreadCount;
        int maxThreadCount;
        
        long totalMinorGC;
        long totalMajorGC;
        long totalGCTime;
        
        public void print() {
            System.out.println("==================== 监控报告 ====================");
            System.out.println();
            
            System.out.println("监控时长: " + (duration / 1000) + "秒");
            System.out.println();
            
            System.out.println("内存指标:");
            System.out.println("  平均堆使用率: " + String.format("%.2f%%", avgHeapUsage));
            System.out.println("  最大堆使用率: " + String.format("%.2f%%", maxHeapUsage));
            System.out.println();
            
            System.out.println("CPU指标:");
            System.out.println("  平均CPU使用率: " + String.format("%.2f%%", avgCpuUsage));
            System.out.println("  最大CPU使用率: " + String.format("%.2f%%", maxCpuUsage));
            System.out.println();
            
            System.out.println("线程指标:");
            System.out.println("  平均线程数: " + String.format("%.0f", avgThreadCount));
            System.out.println("  最大线程数: " + maxThreadCount);
            System.out.println();
            
            System.out.println("GC指标:");
            System.out.println("  Minor GC次数: " + totalMinorGC);
            System.out.println("  Major GC次数: " + totalMajorGC);
            System.out.println("  总GC时间: " + totalGCTime + "ms");
            System.out.println();
            
            System.out.println("==================================================");
        }
    }

    /**
     * 应用模拟器
     */
    static class ApplicationSimulator {
        
        private static final int _1MB = 1024 * 1024;
        private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
        private final List<byte[]> cache = new CopyOnWriteArrayList<>();
        
        /**
         * 启动模拟
         */
        public void start() {
            // 模拟内存分配
            executor.scheduleAtFixedRate(() -> {
                cache.add(new byte[_1MB]);
                
                if (cache.size() > 100) {
                    cache.clear();
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
            
            // 模拟CPU密集型任务
            executor.scheduleAtFixedRate(() -> {
                long sum = 0;
                for (int i = 0; i < 1000000; i++) {
                    sum += i;
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        }
        
        /**
         * 停止模拟
         */
        public void stop() {
            executor.shutdown();
            cache.clear();
        }
    }
}

/**
 * 监控Web服务（简化版）
 */
class MonitoringWebServer {
    
    public static void main(String[] args) {
        System.out.println("========== 监控Web服务 ==========\n");
        System.out.println("提供HTTP接口:");
        System.out.println("1. GET /metrics - 获取当前指标");
        System.out.println("2. GET /report - 获取监控报告");
        System.out.println("3. GET /health - 健康检查");
        System.out.println();
        System.out.println("实际实现需要引入HTTP服务器框架（如Spring Boot）");
    }
}

/**
 * 监控数据导出器（Prometheus格式）
 */
class MetricsExporter {
    
    public static void main(String[] args) {
        System.out.println("========== 监控数据导出 ==========\n");
        
        exportMetrics();
    }
    
    private static void exportMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        System.out.println("# HELP jvm_memory_used_bytes JVM memory used");
        System.out.println("# TYPE jvm_memory_used_bytes gauge");
        System.out.println("jvm_memory_used_bytes{area=\"heap\"} " + heapUsage.getUsed());
        System.out.println();
        
        System.out.println("# HELP jvm_memory_max_bytes JVM memory max");
        System.out.println("# TYPE jvm_memory_max_bytes gauge");
        System.out.println("jvm_memory_max_bytes{area=\"heap\"} " + heapUsage.getMax());
        System.out.println();
        
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String name = gcBean.getName().toLowerCase().replace(" ", "_");
            
            System.out.println("# HELP jvm_gc_count_total GC count");
            System.out.println("# TYPE jvm_gc_count_total counter");
            System.out.println("jvm_gc_count_total{gc=\"" + name + "\"} " + gcBean.getCollectionCount());
            System.out.println();
        }
    }
}
