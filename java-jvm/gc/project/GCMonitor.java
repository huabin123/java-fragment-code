package com.example.jvm.gc.project;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * GC监控工具实战项目
 * 
 * 功能：
 * 1. 实时监控GC活动
 * 2. 统计GC指标
 * 3. 检测GC异常
 * 4. 生成GC报告
 * 5. GC告警
 * 
 * @author JavaGuide
 */
public class GCMonitor {

    public static void main(String[] args) throws Exception {
        System.out.println("========== GC监控工具演示 ==========\n");

        // 创建GC监控器
        GCMonitorManager monitor = new GCMonitorManager();
        
        // 启动监控
        monitor.startMonitoring();
        
        // 模拟应用负载
        System.out.println("1. 启动应用负载模拟...\n");
        ApplicationSimulator simulator = new ApplicationSimulator();
        simulator.start();
        
        // 运行一段时间
        Thread.sleep(30000);
        
        // 停止监控
        monitor.stopMonitoring();
        simulator.stop();
        
        // 生成报告
        System.out.println("\n2. 生成GC监控报告...\n");
        monitor.generateReport();
    }

    /**
     * GC监控管理器
     */
    static class GCMonitorManager {
        
        private final GCMetricsCollector metricsCollector;
        private final GCAlertManager alertManager;
        private final ScheduledExecutorService scheduler;
        private volatile boolean monitoring = false;
        
        public GCMonitorManager() {
            this.metricsCollector = new GCMetricsCollector();
            this.alertManager = new GCAlertManager();
            this.scheduler = Executors.newScheduledThreadPool(2);
        }
        
        /**
         * 启动监控
         */
        public void startMonitoring() {
            if (monitoring) {
                return;
            }
            
            monitoring = true;
            System.out.println("GC监控已启动");
            
            // 每秒收集一次GC指标
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    GCMetrics metrics = metricsCollector.collect();
                    checkAlerts(metrics);
                } catch (Exception e) {
                    System.err.println("收集GC指标失败: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
        }
        
        /**
         * 停止监控
         */
        public void stopMonitoring() {
            if (!monitoring) {
                return;
            }
            
            monitoring = false;
            scheduler.shutdown();
            System.out.println("GC监控已停止");
        }
        
        /**
         * 检查告警
         */
        private void checkAlerts(GCMetrics metrics) {
            alertManager.check(metrics);
        }
        
        /**
         * 生成报告
         */
        public void generateReport() {
            GCReport report = metricsCollector.generateReport();
            report.print();
        }
    }

    /**
     * GC指标收集器
     */
    static class GCMetricsCollector {
        
        private final List<GCMetrics> history = new CopyOnWriteArrayList<>();
        private final Map<String, Long> lastGCCount = new ConcurrentHashMap<>();
        private final Map<String, Long> lastGCTime = new ConcurrentHashMap<>();
        
        /**
         * 收集GC指标
         */
        public GCMetrics collect() {
            GCMetrics metrics = new GCMetrics();
            metrics.timestamp = System.currentTimeMillis();
            
            // 收集GC信息
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                String name = gcBean.getName();
                long count = gcBean.getCollectionCount();
                long time = gcBean.getCollectionTime();
                
                // 计算增量
                long lastCount = lastGCCount.getOrDefault(name, 0L);
                long lastTime = lastGCTime.getOrDefault(name, 0L);
                
                long deltaCount = count - lastCount;
                long deltaTime = time - lastTime;
                
                if (deltaCount > 0) {
                    GCEvent event = new GCEvent();
                    event.gcName = name;
                    event.count = deltaCount;
                    event.time = deltaTime;
                    event.avgTime = deltaTime * 1.0 / deltaCount;
                    
                    metrics.events.add(event);
                }
                
                // 更新上次值
                lastGCCount.put(name, count);
                lastGCTime.put(name, time);
                
                // 累计统计
                metrics.totalGCCount += count;
                metrics.totalGCTime += time;
            }
            
            // 收集内存信息
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            
            metrics.heapUsed = heapUsage.getUsed();
            metrics.heapMax = heapUsage.getMax();
            metrics.heapUsagePercent = metrics.heapUsed * 100.0 / metrics.heapMax;
            
            // 保存历史
            history.add(metrics);
            
            return metrics;
        }
        
        /**
         * 生成报告
         */
        public GCReport generateReport() {
            GCReport report = new GCReport();
            
            if (history.isEmpty()) {
                return report;
            }
            
            // 统计GC次数和时间
            Map<String, Long> gcCountMap = new HashMap<>();
            Map<String, Long> gcTimeMap = new HashMap<>();
            
            for (GCMetrics metrics : history) {
                for (GCEvent event : metrics.events) {
                    gcCountMap.merge(event.gcName, event.count, Long::sum);
                    gcTimeMap.merge(event.gcName, event.time, Long::sum);
                }
            }
            
            // 计算总GC次数和时间
            report.totalGCCount = gcCountMap.values().stream().mapToLong(Long::longValue).sum();
            report.totalGCTime = gcTimeMap.values().stream().mapToLong(Long::longValue).sum();
            
            // 计算平均GC时间
            if (report.totalGCCount > 0) {
                report.avgGCTime = report.totalGCTime * 1.0 / report.totalGCCount;
            }
            
            // 计算最大堆使用率
            report.maxHeapUsagePercent = history.stream()
                .mapToDouble(m -> m.heapUsagePercent)
                .max()
                .orElse(0);
            
            // 计算平均堆使用率
            report.avgHeapUsagePercent = history.stream()
                .mapToDouble(m -> m.heapUsagePercent)
                .average()
                .orElse(0);
            
            // 分GC类型统计
            for (Map.Entry<String, Long> entry : gcCountMap.entrySet()) {
                String gcName = entry.getKey();
                long count = entry.getValue();
                long time = gcTimeMap.get(gcName);
                
                GCTypeStats stats = new GCTypeStats();
                stats.gcName = gcName;
                stats.count = count;
                stats.totalTime = time;
                stats.avgTime = time * 1.0 / count;
                
                report.gcTypeStats.add(stats);
            }
            
            // 监控时长
            report.monitorDuration = history.get(history.size() - 1).timestamp - history.get(0).timestamp;
            
            return report;
        }
    }

    /**
     * GC告警管理器
     */
    static class GCAlertManager {
        
        // 告警阈值
        private static final double HEAP_USAGE_THRESHOLD = 90.0;  // 堆使用率阈值
        private static final long GC_TIME_THRESHOLD = 1000;  // GC时间阈值（ms）
        private static final long GC_FREQUENCY_THRESHOLD = 10;  // GC频率阈值（次/秒）
        
        private final Map<String, Long> alertCount = new ConcurrentHashMap<>();
        
        /**
         * 检查告警
         */
        public void check(GCMetrics metrics) {
            // 检查堆使用率
            if (metrics.heapUsagePercent > HEAP_USAGE_THRESHOLD) {
                alert("堆使用率过高", 
                      String.format("当前: %.2f%%, 阈值: %.2f%%", 
                                  metrics.heapUsagePercent, HEAP_USAGE_THRESHOLD));
            }
            
            // 检查GC时间
            for (GCEvent event : metrics.events) {
                if (event.time > GC_TIME_THRESHOLD) {
                    alert("GC停顿时间过长", 
                          String.format("%s: %dms, 阈值: %dms", 
                                      event.gcName, event.time, GC_TIME_THRESHOLD));
                }
                
                // 检查GC频率
                if (event.count > GC_FREQUENCY_THRESHOLD) {
                    alert("GC频率过高", 
                          String.format("%s: %d次/秒, 阈值: %d次/秒", 
                                      event.gcName, event.count, GC_FREQUENCY_THRESHOLD));
                }
            }
        }
        
        /**
         * 发送告警
         */
        private void alert(String type, String message) {
            // 防止告警风暴，同一类型告警1分钟内只发送一次
            long now = System.currentTimeMillis();
            Long lastAlertTime = alertCount.get(type);
            
            if (lastAlertTime != null && now - lastAlertTime < 60000) {
                return;
            }
            
            alertCount.put(type, now);
            
            System.err.println("⚠️  告警: " + type);
            System.err.println("   详情: " + message);
            System.err.println("   时间: " + new Date());
            System.err.println();
        }
    }

    /**
     * GC指标
     */
    static class GCMetrics {
        long timestamp;
        List<GCEvent> events = new ArrayList<>();
        long totalGCCount;
        long totalGCTime;
        long heapUsed;
        long heapMax;
        double heapUsagePercent;
    }

    /**
     * GC事件
     */
    static class GCEvent {
        String gcName;
        long count;
        long time;
        double avgTime;
    }

    /**
     * GC报告
     */
    static class GCReport {
        long monitorDuration;
        long totalGCCount;
        long totalGCTime;
        double avgGCTime;
        double maxHeapUsagePercent;
        double avgHeapUsagePercent;
        List<GCTypeStats> gcTypeStats = new ArrayList<>();
        
        public void print() {
            System.out.println("==================== GC监控报告 ====================");
            System.out.println();
            
            System.out.println("监控时长: " + (monitorDuration / 1000) + "秒");
            System.out.println();
            
            System.out.println("GC统计:");
            System.out.println("  总GC次数: " + totalGCCount);
            System.out.println("  总GC时间: " + totalGCTime + "ms");
            System.out.println("  平均GC时间: " + String.format("%.2f", avgGCTime) + "ms");
            System.out.println();
            
            System.out.println("堆内存使用:");
            System.out.println("  最大使用率: " + String.format("%.2f%%", maxHeapUsagePercent));
            System.out.println("  平均使用率: " + String.format("%.2f%%", avgHeapUsagePercent));
            System.out.println();
            
            System.out.println("分GC类型统计:");
            for (GCTypeStats stats : gcTypeStats) {
                System.out.println("  " + stats.gcName + ":");
                System.out.println("    次数: " + stats.count);
                System.out.println("    总时间: " + stats.totalTime + "ms");
                System.out.println("    平均时间: " + String.format("%.2f", stats.avgTime) + "ms");
            }
            
            System.out.println();
            System.out.println("==================================================");
        }
    }

    /**
     * GC类型统计
     */
    static class GCTypeStats {
        String gcName;
        long count;
        long totalTime;
        double avgTime;
    }

    /**
     * 应用模拟器
     */
    static class ApplicationSimulator {
        
        private static final int _1MB = 1024 * 1024;
        private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        private final List<byte[]> cache = new CopyOnWriteArrayList<>();
        
        /**
         * 启动模拟
         */
        public void start() {
            // 每100ms分配1MB内存
            executor.scheduleAtFixedRate(() -> {
                try {
                    cache.add(new byte[_1MB]);
                    
                    // 定期清理，避免OOM
                    if (cache.size() > 50) {
                        cache.clear();
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }, 0, 100, TimeUnit.MILLISECONDS);
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
 * GC监控Web服务
 */
class GCMonitorWebServer {
    
    /**
     * 提供HTTP接口查询GC指标
     */
    public static void main(String[] args) {
        System.out.println("========== GC监控Web服务 ==========\n");
        System.out.println("功能:");
        System.out.println("1. GET /gc/metrics - 获取当前GC指标");
        System.out.println("2. GET /gc/report - 获取GC报告");
        System.out.println("3. GET /gc/alerts - 获取告警信息");
        System.out.println();
        System.out.println("示例实现（需要引入HTTP服务器框架）:");
        System.out.println("- Spring Boot + Actuator");
        System.out.println("- Dropwizard Metrics");
        System.out.println("- Micrometer + Prometheus");
    }
}

/**
 * GC监控JMX实现
 */
class GCMonitorJMX {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========== GC监控JMX实现 ==========\n");
        
        // 注册GC监听器
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            if (gcBean instanceof NotificationEmitter) {
                NotificationEmitter emitter = (NotificationEmitter) gcBean;
                
                emitter.addNotificationListener((notification, handback) -> {
                    if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                        GarbageCollectionNotificationInfo info = 
                            GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                        
                        String gcName = info.getGcName();
                        String gcAction = info.getGcAction();
                        String gcCause = info.getGcCause();
                        long duration = info.getGcInfo().getDuration();
                        
                        System.out.println("GC事件:");
                        System.out.println("  名称: " + gcName);
                        System.out.println("  动作: " + gcAction);
                        System.out.println("  原因: " + gcCause);
                        System.out.println("  耗时: " + duration + "ms");
                        System.out.println();
                    }
                }, null, null);
            }
        }
        
        System.out.println("GC监听器已注册");
        System.out.println("等待GC事件...\n");
        
        // 触发GC
        for (int i = 0; i < 10; i++) {
            byte[] data = new byte[1024 * 1024];
            Thread.sleep(100);
        }
        
        System.gc();
        Thread.sleep(1000);
    }
}

/**
 * GC监控指标导出器（Prometheus格式）
 */
class GCMetricsExporter {
    
    public static void main(String[] args) {
        System.out.println("========== GC监控指标导出 ==========\n");
        
        System.out.println("Prometheus格式示例:");
        System.out.println();
        
        exportMetrics();
    }
    
    private static void exportMetrics() {
        // GC次数
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String name = gcBean.getName().toLowerCase().replace(" ", "_");
            System.out.println("# HELP jvm_gc_count_total GC count");
            System.out.println("# TYPE jvm_gc_count_total counter");
            System.out.println("jvm_gc_count_total{gc=\"" + name + "\"} " + gcBean.getCollectionCount());
            System.out.println();
            
            System.out.println("# HELP jvm_gc_time_ms_total GC time in milliseconds");
            System.out.println("# TYPE jvm_gc_time_ms_total counter");
            System.out.println("jvm_gc_time_ms_total{gc=\"" + name + "\"} " + gcBean.getCollectionTime());
            System.out.println();
        }
        
        // 堆内存使用
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        System.out.println("# HELP jvm_heap_used_bytes Heap memory used");
        System.out.println("# TYPE jvm_heap_used_bytes gauge");
        System.out.println("jvm_heap_used_bytes " + heapUsage.getUsed());
        System.out.println();
        
        System.out.println("# HELP jvm_heap_max_bytes Heap memory max");
        System.out.println("# TYPE jvm_heap_max_bytes gauge");
        System.out.println("jvm_heap_max_bytes " + heapUsage.getMax());
        System.out.println();
    }
}
