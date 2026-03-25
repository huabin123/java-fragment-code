package com.example.jvm.gc.project;

import java.lang.management.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 内存泄漏检测工具实战项目
 * 
 * 功能：
 * 1. 检测常见内存泄漏模式
 * 2. 分析堆内存增长趋势
 * 3. 检测对象泄漏
 * 4. 生成泄漏报告
 * 5. 提供修复建议
 * 
 * @author JavaGuide
 */
public class MemoryLeakDetector {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 内存泄漏检测工具演示 ==========\n");

        // 创建检测器
        LeakDetectorManager detector = new LeakDetectorManager();
        
        // 启动检测
        detector.startDetection();
        
        // 模拟内存泄漏场景
        System.out.println("1. 模拟内存泄漏场景...\n");
        LeakSimulator simulator = new LeakSimulator();
        simulator.simulateStaticCollectionLeak();
        simulator.simulateListenerLeak();
        simulator.simulateThreadLocalLeak();
        
        // 运行一段时间
        Thread.sleep(10000);
        
        // 停止检测
        detector.stopDetection();
        
        // 生成报告
        System.out.println("\n2. 生成内存泄漏检测报告...\n");
        detector.generateReport();
    }

    /**
     * 泄漏检测管理器
     */
    static class LeakDetectorManager {
        
        private final HeapGrowthAnalyzer heapAnalyzer;
        private final ObjectLeakDetector objectDetector;
        private final PatternDetector patternDetector;
        private final ScheduledExecutorService scheduler;
        private volatile boolean detecting = false;
        
        public LeakDetectorManager() {
            this.heapAnalyzer = new HeapGrowthAnalyzer();
            this.objectDetector = new ObjectLeakDetector();
            this.patternDetector = new PatternDetector();
            this.scheduler = Executors.newScheduledThreadPool(2);
        }
        
        /**
         * 启动检测
         */
        public void startDetection() {
            if (detecting) {
                return;
            }
            
            detecting = true;
            System.out.println("内存泄漏检测已启动");
            
            // 每秒分析堆增长
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    heapAnalyzer.analyze();
                } catch (Exception e) {
                    System.err.println("堆分析失败: " + e.getMessage());
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            // 每5秒检测对象泄漏
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    objectDetector.detect();
                } catch (Exception e) {
                    System.err.println("对象检测失败: " + e.getMessage());
                }
            }, 0, 5, TimeUnit.SECONDS);
        }
        
        /**
         * 停止检测
         */
        public void stopDetection() {
            if (!detecting) {
                return;
            }
            
            detecting = false;
            scheduler.shutdown();
            System.out.println("内存泄漏检测已停止");
        }
        
        /**
         * 生成报告
         */
        public void generateReport() {
            LeakReport report = new LeakReport();
            
            // 堆增长分析
            report.heapGrowthAnalysis = heapAnalyzer.getAnalysis();
            
            // 对象泄漏检测
            report.objectLeaks = objectDetector.getLeaks();
            
            // 模式检测
            report.leakPatterns = patternDetector.detect();
            
            // 打印报告
            report.print();
        }
    }

    /**
     * 堆增长分析器
     */
    static class HeapGrowthAnalyzer {
        
        private final List<HeapSnapshot> snapshots = new CopyOnWriteArrayList<>();
        private static final int MAX_SNAPSHOTS = 100;
        
        /**
         * 分析堆内存
         */
        public void analyze() {
            HeapSnapshot snapshot = takeSnapshot();
            snapshots.add(snapshot);
            
            // 保持最近的快照
            if (snapshots.size() > MAX_SNAPSHOTS) {
                snapshots.remove(0);
            }
            
            // 检测异常增长
            if (snapshots.size() >= 10) {
                checkAbnormalGrowth();
            }
        }
        
        /**
         * 获取快照
         */
        private HeapSnapshot takeSnapshot() {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            
            HeapSnapshot snapshot = new HeapSnapshot();
            snapshot.timestamp = System.currentTimeMillis();
            snapshot.used = heapUsage.getUsed();
            snapshot.max = heapUsage.getMax();
            snapshot.usagePercent = snapshot.used * 100.0 / snapshot.max;
            
            return snapshot;
        }
        
        /**
         * 检测异常增长
         */
        private void checkAbnormalGrowth() {
            // 计算最近10次的增长率
            int size = snapshots.size();
            HeapSnapshot first = snapshots.get(size - 10);
            HeapSnapshot last = snapshots.get(size - 1);
            
            long growth = last.used - first.used;
            double growthRate = growth * 100.0 / first.used;
            
            // 如果10秒内增长超过50%，可能存在泄漏
            if (growthRate > 50) {
                System.err.println("⚠️  检测到异常堆增长:");
                System.err.println("   增长量: " + (growth / 1024 / 1024) + "MB");
                System.err.println("   增长率: " + String.format("%.2f%%", growthRate));
                System.err.println();
            }
        }
        
        /**
         * 获取分析结果
         */
        public HeapGrowthAnalysis getAnalysis() {
            HeapGrowthAnalysis analysis = new HeapGrowthAnalysis();
            
            if (snapshots.isEmpty()) {
                return analysis;
            }
            
            HeapSnapshot first = snapshots.get(0);
            HeapSnapshot last = snapshots.get(snapshots.size() - 1);
            
            analysis.startTime = first.timestamp;
            analysis.endTime = last.timestamp;
            analysis.duration = analysis.endTime - analysis.startTime;
            analysis.startUsed = first.used;
            analysis.endUsed = last.used;
            analysis.growth = analysis.endUsed - analysis.startUsed;
            analysis.growthRate = analysis.growth * 100.0 / analysis.startUsed;
            
            // 计算平均使用率
            analysis.avgUsagePercent = snapshots.stream()
                .mapToDouble(s -> s.usagePercent)
                .average()
                .orElse(0);
            
            // 判断是否可能泄漏
            analysis.possibleLeak = analysis.growthRate > 100 && analysis.avgUsagePercent > 80;
            
            return analysis;
        }
    }

    /**
     * 对象泄漏检测器
     */
    static class ObjectLeakDetector {
        
        private final Map<Class<?>, Integer> objectCounts = new ConcurrentHashMap<>();
        private final List<ObjectLeak> leaks = new CopyOnWriteArrayList<>();
        
        /**
         * 检测对象泄漏
         */
        public void detect() {
            // 获取当前对象计数
            Map<Class<?>, Integer> currentCounts = getCurrentObjectCounts();
            
            // 与上次对比
            for (Map.Entry<Class<?>, Integer> entry : currentCounts.entrySet()) {
                Class<?> clazz = entry.getKey();
                int currentCount = entry.getValue();
                int lastCount = objectCounts.getOrDefault(clazz, 0);
                
                // 如果对象数量持续增长，可能泄漏
                if (currentCount > lastCount * 2 && currentCount > 1000) {
                    ObjectLeak leak = new ObjectLeak();
                    leak.className = clazz.getName();
                    leak.count = currentCount;
                    leak.growth = currentCount - lastCount;
                    leak.timestamp = System.currentTimeMillis();
                    
                    leaks.add(leak);
                    
                    System.err.println("⚠️  检测到对象泄漏:");
                    System.err.println("   类名: " + leak.className);
                    System.err.println("   数量: " + leak.count);
                    System.err.println("   增长: " + leak.growth);
                    System.err.println();
                }
            }
            
            // 更新计数
            objectCounts.putAll(currentCounts);
        }
        
        /**
         * 获取当前对象计数（简化版，实际需要使用JVMTI或堆转储分析）
         */
        private Map<Class<?>, Integer> getCurrentObjectCounts() {
            // 这里只是示例，实际应该使用jmap或JVMTI获取
            Map<Class<?>, Integer> counts = new HashMap<>();
            
            // 模拟数据
            counts.put(String.class, 10000);
            counts.put(byte[].class, 5000);
            
            return counts;
        }
        
        /**
         * 获取泄漏列表
         */
        public List<ObjectLeak> getLeaks() {
            return new ArrayList<>(leaks);
        }
    }

    /**
     * 模式检测器
     */
    static class PatternDetector {
        
        /**
         * 检测常见泄漏模式
         */
        public List<LeakPattern> detect() {
            List<LeakPattern> patterns = new ArrayList<>();
            
            // 检测静态集合
            patterns.add(detectStaticCollections());
            
            // 检测监听器
            patterns.add(detectListeners());
            
            // 检测ThreadLocal
            patterns.add(detectThreadLocals());
            
            // 检测资源未关闭
            patterns.add(detectUnclosedResources());
            
            return patterns;
        }
        
        private LeakPattern detectStaticCollections() {
            LeakPattern pattern = new LeakPattern();
            pattern.name = "静态集合持有对象";
            pattern.description = "静态集合（如List、Map）持有对象，导致对象无法被GC";
            pattern.example = "private static List<Object> cache = new ArrayList<>();";
            pattern.solution = "使用WeakHashMap或及时清理集合";
            pattern.detected = false;  // 实际需要通过代码分析或堆转储检测
            return pattern;
        }
        
        private LeakPattern detectListeners() {
            LeakPattern pattern = new LeakPattern();
            pattern.name = "监听器未注销";
            pattern.description = "注册的监听器未注销，导致对象无法被GC";
            pattern.example = "eventBus.register(listener); // 忘记unregister";
            pattern.solution = "在对象销毁时调用unregister";
            pattern.detected = false;
            return pattern;
        }
        
        private LeakPattern detectThreadLocals() {
            LeakPattern pattern = new LeakPattern();
            pattern.name = "ThreadLocal未清理";
            pattern.description = "ThreadLocal设置值后未清理，导致内存泄漏";
            pattern.example = "threadLocal.set(value); // 忘记remove";
            pattern.solution = "使用try-finally确保调用remove";
            pattern.detected = false;
            return pattern;
        }
        
        private LeakPattern detectUnclosedResources() {
            LeakPattern pattern = new LeakPattern();
            pattern.name = "资源未关闭";
            pattern.description = "数据库连接、文件流等资源未关闭";
            pattern.example = "Connection conn = getConnection(); // 忘记close";
            pattern.solution = "使用try-with-resources自动关闭";
            pattern.detected = false;
            return pattern;
        }
    }

    /**
     * 堆快照
     */
    static class HeapSnapshot {
        long timestamp;
        long used;
        long max;
        double usagePercent;
    }

    /**
     * 堆增长分析
     */
    static class HeapGrowthAnalysis {
        long startTime;
        long endTime;
        long duration;
        long startUsed;
        long endUsed;
        long growth;
        double growthRate;
        double avgUsagePercent;
        boolean possibleLeak;
    }

    /**
     * 对象泄漏
     */
    static class ObjectLeak {
        String className;
        int count;
        int growth;
        long timestamp;
    }

    /**
     * 泄漏模式
     */
    static class LeakPattern {
        String name;
        String description;
        String example;
        String solution;
        boolean detected;
    }

    /**
     * 泄漏报告
     */
    static class LeakReport {
        HeapGrowthAnalysis heapGrowthAnalysis;
        List<ObjectLeak> objectLeaks;
        List<LeakPattern> leakPatterns;
        
        public void print() {
            System.out.println("==================== 内存泄漏检测报告 ====================");
            System.out.println();
            
            // 堆增长分析
            if (heapGrowthAnalysis != null) {
                System.out.println("堆增长分析:");
                System.out.println("  监控时长: " + (heapGrowthAnalysis.duration / 1000) + "秒");
                System.out.println("  起始使用: " + (heapGrowthAnalysis.startUsed / 1024 / 1024) + "MB");
                System.out.println("  结束使用: " + (heapGrowthAnalysis.endUsed / 1024 / 1024) + "MB");
                System.out.println("  增长量: " + (heapGrowthAnalysis.growth / 1024 / 1024) + "MB");
                System.out.println("  增长率: " + String.format("%.2f%%", heapGrowthAnalysis.growthRate));
                System.out.println("  平均使用率: " + String.format("%.2f%%", heapGrowthAnalysis.avgUsagePercent));
                System.out.println("  可能泄漏: " + (heapGrowthAnalysis.possibleLeak ? "是" : "否"));
                System.out.println();
            }
            
            // 对象泄漏
            if (objectLeaks != null && !objectLeaks.isEmpty()) {
                System.out.println("对象泄漏检测:");
                for (ObjectLeak leak : objectLeaks) {
                    System.out.println("  类名: " + leak.className);
                    System.out.println("  数量: " + leak.count);
                    System.out.println("  增长: " + leak.growth);
                    System.out.println();
                }
            }
            
            // 泄漏模式
            if (leakPatterns != null && !leakPatterns.isEmpty()) {
                System.out.println("常见泄漏模式:");
                for (LeakPattern pattern : leakPatterns) {
                    System.out.println("  " + pattern.name + ":");
                    System.out.println("    描述: " + pattern.description);
                    System.out.println("    示例: " + pattern.example);
                    System.out.println("    解决: " + pattern.solution);
                    System.out.println("    检测到: " + (pattern.detected ? "是" : "否"));
                    System.out.println();
                }
            }
            
            System.out.println("========================================================");
        }
    }

    /**
     * 泄漏模拟器
     */
    static class LeakSimulator {
        
        // 静态集合泄漏
        private static final List<Object> staticCache = new ArrayList<>();
        
        // 监听器泄漏
        private static final List<Object> listeners = new ArrayList<>();
        
        // ThreadLocal泄漏
        private static final ThreadLocal<byte[]> threadLocal = new ThreadLocal<>();
        
        /**
         * 模拟静态集合泄漏
         */
        public void simulateStaticCollectionLeak() {
            System.out.println("模拟静态集合泄漏...");
            for (int i = 0; i < 100; i++) {
                staticCache.add(new byte[1024 * 1024]);  // 1MB
            }
            System.out.println("静态集合大小: " + staticCache.size());
        }
        
        /**
         * 模拟监听器泄漏
         */
        public void simulateListenerLeak() {
            System.out.println("模拟监听器泄漏...");
            for (int i = 0; i < 100; i++) {
                Object listener = new Object();
                listeners.add(listener);
                // 忘记移除监听器
            }
            System.out.println("监听器数量: " + listeners.size());
        }
        
        /**
         * 模拟ThreadLocal泄漏
         */
        public void simulateThreadLocalLeak() {
            System.out.println("模拟ThreadLocal泄漏...");
            ExecutorService executor = Executors.newFixedThreadPool(10);
            
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    threadLocal.set(new byte[1024 * 1024]);  // 1MB
                    // 忘记调用remove
                });
            }
            
            executor.shutdown();
            System.out.println("ThreadLocal已设置");
        }
    }
}

/**
 * 内存泄漏修复建议
 */
class LeakFixSuggestions {
    
    public static void main(String[] args) {
        System.out.println("========== 内存泄漏修复建议 ==========\n");
        
        printStaticCollectionFix();
        printListenerFix();
        printThreadLocalFix();
        printResourceFix();
    }
    
    /**
     * 静态集合修复
     */
    private static void printStaticCollectionFix() {
        System.out.println("1. 静态集合泄漏修复:");
        System.out.println();
        
        System.out.println("问题代码:");
        System.out.println("private static List<Object> cache = new ArrayList<>();");
        System.out.println();
        
        System.out.println("修复方案1 - 使用WeakHashMap:");
        System.out.println("private static Map<Object, Object> cache = new WeakHashMap<>();");
        System.out.println();
        
        System.out.println("修复方案2 - 及时清理:");
        System.out.println("public void clear() {");
        System.out.println("    cache.clear();");
        System.out.println("}");
        System.out.println();
        
        System.out.println("修复方案3 - 使用软引用:");
        System.out.println("private static Map<String, SoftReference<Object>> cache = new HashMap<>();");
        System.out.println();
    }
    
    /**
     * 监听器修复
     */
    private static void printListenerFix() {
        System.out.println("2. 监听器泄漏修复:");
        System.out.println();
        
        System.out.println("问题代码:");
        System.out.println("eventBus.register(listener);");
        System.out.println();
        
        System.out.println("修复方案:");
        System.out.println("public void destroy() {");
        System.out.println("    eventBus.unregister(listener);");
        System.out.println("}");
        System.out.println();
    }
    
    /**
     * ThreadLocal修复
     */
    private static void printThreadLocalFix() {
        System.out.println("3. ThreadLocal泄漏修复:");
        System.out.println();
        
        System.out.println("问题代码:");
        System.out.println("threadLocal.set(value);");
        System.out.println();
        
        System.out.println("修复方案:");
        System.out.println("try {");
        System.out.println("    threadLocal.set(value);");
        System.out.println("    // 使用");
        System.out.println("} finally {");
        System.out.println("    threadLocal.remove();");
        System.out.println("}");
        System.out.println();
    }
    
    /**
     * 资源修复
     */
    private static void printResourceFix() {
        System.out.println("4. 资源未关闭修复:");
        System.out.println();
        
        System.out.println("问题代码:");
        System.out.println("Connection conn = getConnection();");
        System.out.println("// 使用conn");
        System.out.println("// 忘记关闭");
        System.out.println();
        
        System.out.println("修复方案 - 使用try-with-resources:");
        System.out.println("try (Connection conn = getConnection()) {");
        System.out.println("    // 使用conn");
        System.out.println("}  // 自动关闭");
        System.out.println();
    }
}
