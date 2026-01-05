package com.fragment.jvm.memory.project;

import java.lang.management.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JVM内存监控工具
 * 
 * 功能：
 * 1. 实时监控堆内存使用情况
 * 2. 监控非堆内存（元空间）
 * 3. 监控GC情况
 * 4. 监控线程情况
 * 5. 内存告警
 * 
 * @author huabin
 */
public class MemoryMonitor {
    
    private final MemoryMXBean memoryMXBean;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    private final ThreadMXBean threadMXBean;
    private final RuntimeMXBean runtimeMXBean;
    
    // 告警阈值
    private double heapUsageThreshold = 0.8;  // 堆内存使用率80%告警
    private double metaspaceUsageThreshold = 0.8;  // 元空间使用率80%告警
    
    public MemoryMonitor() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    }
    
    /**
     * 获取堆内存使用情况
     */
    public HeapMemoryInfo getHeapMemoryInfo() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        
        long init = heapUsage.getInit();
        long used = heapUsage.getUsed();
        long committed = heapUsage.getCommitted();
        long max = heapUsage.getMax();
        double usageRate = (double) used / max;
        
        return new HeapMemoryInfo(init, used, committed, max, usageRate);
    }
    
    /**
     * 获取非堆内存使用情况（元空间）
     */
    public NonHeapMemoryInfo getNonHeapMemoryInfo() {
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        long init = nonHeapUsage.getInit();
        long used = nonHeapUsage.getUsed();
        long committed = nonHeapUsage.getCommitted();
        long max = nonHeapUsage.getMax();
        double usageRate = max > 0 ? (double) used / max : 0;
        
        return new NonHeapMemoryInfo(init, used, committed, max, usageRate);
    }
    
    /**
     * 获取GC信息
     */
    public GCInfo getGCInfo() {
        long youngGCCount = 0;
        long youngGCTime = 0;
        long oldGCCount = 0;
        long oldGCTime = 0;
        
        for (GarbageCollectorMXBean gcMXBean : gcMXBeans) {
            String name = gcMXBean.getName();
            long count = gcMXBean.getCollectionCount();
            long time = gcMXBean.getCollectionTime();
            
            // 判断是Young GC还是Old GC
            if (name.contains("Young") || name.contains("New") || 
                name.contains("Scavenge") || name.contains("Copy")) {
                youngGCCount += count;
                youngGCTime += time;
            } else {
                oldGCCount += count;
                oldGCTime += time;
            }
        }
        
        return new GCInfo(youngGCCount, youngGCTime, oldGCCount, oldGCTime);
    }
    
    /**
     * 获取线程信息
     */
    public ThreadInfo getThreadInfo() {
        int threadCount = threadMXBean.getThreadCount();
        int peakThreadCount = threadMXBean.getPeakThreadCount();
        long totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();
        int daemonThreadCount = threadMXBean.getDaemonThreadCount();
        
        return new ThreadInfo(threadCount, peakThreadCount, 
            totalStartedThreadCount, daemonThreadCount);
    }
    
    /**
     * 获取运行时信息
     */
    public RuntimeInfo getRuntimeInfo() {
        long uptime = runtimeMXBean.getUptime();
        String vmName = runtimeMXBean.getVmName();
        String vmVersion = runtimeMXBean.getVmVersion();
        
        return new RuntimeInfo(uptime, vmName, vmVersion);
    }
    
    /**
     * 打印完整的监控信息
     */
    public void printMonitorInfo() {
        System.out.println("\n========== JVM内存监控 ==========");
        System.out.println("时间: " + new java.util.Date());
        
        // 堆内存
        HeapMemoryInfo heapInfo = getHeapMemoryInfo();
        System.out.println("\n【堆内存】");
        System.out.println(heapInfo);
        if (heapInfo.usageRate > heapUsageThreshold) {
            System.out.println("⚠️  警告：堆内存使用率超过" + (heapUsageThreshold * 100) + "%");
        }
        
        // 非堆内存
        NonHeapMemoryInfo nonHeapInfo = getNonHeapMemoryInfo();
        System.out.println("\n【非堆内存（元空间）】");
        System.out.println(nonHeapInfo);
        if (nonHeapInfo.usageRate > metaspaceUsageThreshold) {
            System.out.println("⚠️  警告：元空间使用率超过" + (metaspaceUsageThreshold * 100) + "%");
        }
        
        // GC信息
        GCInfo gcInfo = getGCInfo();
        System.out.println("\n【GC信息】");
        System.out.println(gcInfo);
        
        // 线程信息
        ThreadInfo threadInfo = getThreadInfo();
        System.out.println("\n【线程信息】");
        System.out.println(threadInfo);
        
        // 运行时信息
        RuntimeInfo runtimeInfo = getRuntimeInfo();
        System.out.println("\n【运行时信息】");
        System.out.println(runtimeInfo);
        
        System.out.println("================================\n");
    }
    
    /**
     * 启动定时监控
     */
    public void startMonitoring(long intervalSeconds) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                printMonitorInfo();
            } catch (Exception e) {
                System.err.println("监控异常: " + e.getMessage());
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
        
        System.out.println("内存监控已启动，监控间隔: " + intervalSeconds + "秒");
        System.out.println("按Ctrl+C停止监控");
    }
    
    /**
     * 堆内存信息
     */
    public static class HeapMemoryInfo {
        public final long init;
        public final long used;
        public final long committed;
        public final long max;
        public final double usageRate;
        
        public HeapMemoryInfo(long init, long used, long committed, long max, double usageRate) {
            this.init = init;
            this.used = used;
            this.committed = committed;
            this.max = max;
            this.usageRate = usageRate;
        }
        
        @Override
        public String toString() {
            return String.format(
                "初始: %dMB, 已使用: %dMB, 已提交: %dMB, 最大: %dMB, 使用率: %.2f%%",
                init / 1024 / 1024,
                used / 1024 / 1024,
                committed / 1024 / 1024,
                max / 1024 / 1024,
                usageRate * 100
            );
        }
    }
    
    /**
     * 非堆内存信息
     */
    public static class NonHeapMemoryInfo {
        public final long init;
        public final long used;
        public final long committed;
        public final long max;
        public final double usageRate;
        
        public NonHeapMemoryInfo(long init, long used, long committed, long max, double usageRate) {
            this.init = init;
            this.used = used;
            this.committed = committed;
            this.max = max;
            this.usageRate = usageRate;
        }
        
        @Override
        public String toString() {
            return String.format(
                "初始: %dMB, 已使用: %dMB, 已提交: %dMB, 最大: %s, 使用率: %s",
                init / 1024 / 1024,
                used / 1024 / 1024,
                committed / 1024 / 1024,
                max > 0 ? (max / 1024 / 1024) + "MB" : "无限制",
                max > 0 ? String.format("%.2f%%", usageRate * 100) : "N/A"
            );
        }
    }
    
    /**
     * GC信息
     */
    public static class GCInfo {
        public final long youngGCCount;
        public final long youngGCTime;
        public final long oldGCCount;
        public final long oldGCTime;
        
        public GCInfo(long youngGCCount, long youngGCTime, long oldGCCount, long oldGCTime) {
            this.youngGCCount = youngGCCount;
            this.youngGCTime = youngGCTime;
            this.oldGCCount = oldGCCount;
            this.oldGCTime = oldGCTime;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Young GC: %d次, 耗时: %dms\nOld GC: %d次, 耗时: %dms\n总GC次数: %d, 总耗时: %dms",
                youngGCCount, youngGCTime,
                oldGCCount, oldGCTime,
                youngGCCount + oldGCCount,
                youngGCTime + oldGCTime
            );
        }
    }
    
    /**
     * 线程信息
     */
    public static class ThreadInfo {
        public final int threadCount;
        public final int peakThreadCount;
        public final long totalStartedThreadCount;
        public final int daemonThreadCount;
        
        public ThreadInfo(int threadCount, int peakThreadCount, 
                         long totalStartedThreadCount, int daemonThreadCount) {
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.totalStartedThreadCount = totalStartedThreadCount;
            this.daemonThreadCount = daemonThreadCount;
        }
        
        @Override
        public String toString() {
            return String.format(
                "当前线程数: %d, 峰值: %d, 总启动: %d, 守护线程: %d",
                threadCount, peakThreadCount, totalStartedThreadCount, daemonThreadCount
            );
        }
    }
    
    /**
     * 运行时信息
     */
    public static class RuntimeInfo {
        public final long uptime;
        public final String vmName;
        public final String vmVersion;
        
        public RuntimeInfo(long uptime, String vmName, String vmVersion) {
            this.uptime = uptime;
            this.vmName = vmName;
            this.vmVersion = vmVersion;
        }
        
        @Override
        public String toString() {
            long seconds = uptime / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            return String.format(
                "运行时间: %d天%d小时%d分钟\nJVM: %s\n版本: %s",
                days, hours % 24, minutes % 60,
                vmName, vmVersion
            );
        }
    }
    
    /**
     * 测试代码
     */
    public static void main(String[] args) {
        MemoryMonitor monitor = new MemoryMonitor();
        
        // 打印一次监控信息
        monitor.printMonitorInfo();
        
        // 启动定时监控（每5秒一次）
        // monitor.startMonitoring(5);
        
        // 模拟内存使用
        System.out.println("开始模拟内存使用...");
        java.util.List<byte[]> list = new java.util.ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            // 每次分配10MB
            list.add(new byte[10 * 1024 * 1024]);
            System.out.println("已分配: " + (i + 1) * 10 + "MB");
            
            // 打印监控信息
            monitor.printMonitorInfo();
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
