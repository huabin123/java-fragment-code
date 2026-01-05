package com.example.jvm.tuning.demo;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 内存泄漏问题演示
 * 
 * 演示内容：
 * 1. 静态集合导致内存泄漏
 * 2. 监听器未注销导致内存泄漏
 * 3. ThreadLocal未清理导致内存泄漏
 * 4. 资源未关闭导致内存泄漏
 * 5. 内部类持有外部类引用导致内存泄漏
 * 
 * 注意：这些都是问题代码示例，用于演示排查过程
 * 
 * @author JavaGuide
 */
public class MemoryLeakDemo {

    public static void main(String[] args) {
        System.out.println("========== 内存泄漏问题演示 ==========\n");
        System.out.println("选择要演示的场景:");
        System.out.println("1. 静态集合泄漏");
        System.out.println("2. 监听器泄漏");
        System.out.println("3. ThreadLocal泄漏");
        System.out.println("4. 资源未关闭泄漏");
        System.out.println("5. 内部类泄漏");
        
        // 取消注释运行对应场景
        // demonstrateStaticCollectionLeak();
        // demonstrateListenerLeak();
        // demonstrateThreadLocalLeak();
        // demonstrateResourceLeak();
        // demonstrateInnerClassLeak();
    }

    /**
     * 场景1：静态集合导致内存泄漏
     */
    public static void demonstrateStaticCollectionLeak() {
        System.out.println("\n========== 场景1：静态集合泄漏 ==========");
        
        StaticCollectionLeakExample example = new StaticCollectionLeakExample();
        
        for (int i = 0; i < 100000; i++) {
            example.addToCache(new byte[1024]);  // 每次1KB
            
            if (i % 10000 == 0) {
                System.out.println("已添加: " + i + " 个对象");
                printMemoryUsage();
            }
        }
        
        System.out.println("\n问题：静态集合持有对象，无法被GC");
        System.out.println("解决：使用WeakHashMap或及时清理");
    }

    /**
     * 场景2：监听器未注销导致内存泄漏
     */
    public static void demonstrateListenerLeak() {
        System.out.println("\n========== 场景2：监听器泄漏 ==========");
        
        EventBus eventBus = new EventBus();
        
        for (int i = 0; i < 10000; i++) {
            // 创建监听器但不注销
            EventListener listener = new EventListener() {
                private byte[] data = new byte[1024];  // 1KB
                
                @Override
                public void onEvent(String event) {
                    // 处理事件
                }
            };
            
            eventBus.register(listener);
            
            if (i % 1000 == 0) {
                System.out.println("已注册: " + i + " 个监听器");
                printMemoryUsage();
            }
        }
        
        System.out.println("\n问题：监听器未注销，无法被GC");
        System.out.println("解决：及时调用unregister");
    }

    /**
     * 场景3：ThreadLocal未清理导致内存泄漏
     */
    public static void demonstrateThreadLocalLeak() {
        System.out.println("\n========== 场景3：ThreadLocal泄漏 ==========");
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                // 设置ThreadLocal但不清理
                ThreadLocalLeakExample.setData(new byte[1024 * 1024]);  // 1MB
                
                // 业务处理
                doSomething();
                
                // 问题：忘记清理ThreadLocal
                // ThreadLocalLeakExample.remove();
            });
            
            if (i % 100 == 0) {
                System.out.println("已提交: " + i + " 个任务");
                printMemoryUsage();
            }
        }
        
        executor.shutdown();
        
        System.out.println("\n问题：ThreadLocal未清理，线程池中的线程持有数据");
        System.out.println("解决：使用try-finally确保调用remove");
    }

    /**
     * 场景4：资源未关闭导致内存泄漏
     */
    public static void demonstrateResourceLeak() {
        System.out.println("\n========== 场景4：资源未关闭泄漏 ==========");
        
        for (int i = 0; i < 1000; i++) {
            // 创建资源但不关闭
            ResourceLeakExample resource = new ResourceLeakExample();
            resource.open();
            
            // 使用资源
            resource.use();
            
            // 问题：忘记关闭资源
            // resource.close();
            
            if (i % 100 == 0) {
                System.out.println("已创建: " + i + " 个资源");
                printMemoryUsage();
            }
        }
        
        System.out.println("\n问题：资源未关闭，占用内存");
        System.out.println("解决：使用try-with-resources");
    }

    /**
     * 场景5：内部类持有外部类引用导致内存泄漏
     */
    public static void demonstrateInnerClassLeak() {
        System.out.println("\n========== 场景5：内部类泄漏 ==========");
        
        List<Object> cache = new ArrayList<>();
        
        for (int i = 0; i < 10000; i++) {
            OuterClass outer = new OuterClass();
            OuterClass.InnerClass inner = outer.new InnerClass();
            
            // 只缓存内部类对象
            cache.add(inner);
            
            // 问题：内部类持有外部类引用，外部类也无法被GC
            
            if (i % 1000 == 0) {
                System.out.println("已缓存: " + i + " 个内部类对象");
                printMemoryUsage();
            }
        }
        
        System.out.println("\n问题：内部类持有外部类引用");
        System.out.println("解决：使用静态内部类");
    }

    /**
     * 打印内存使用情况
     */
    private static void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long max = runtime.maxMemory() / 1024 / 1024;
        System.out.println("  内存使用: " + used + "MB / " + max + "MB");
    }

    /**
     * 模拟业务处理
     */
    private static void doSomething() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/**
 * 静态集合泄漏示例
 */
class StaticCollectionLeakExample {
    
    // 问题：静态集合
    private static List<Object> cache = new ArrayList<>();
    
    public void addToCache(Object obj) {
        cache.add(obj);
    }
    
    // 解决方案1：使用WeakHashMap
    private static Map<Object, Object> weakCache = new WeakHashMap<>();
    
    public void addToWeakCache(Object key, Object value) {
        weakCache.put(key, value);
    }
    
    // 解决方案2：及时清理
    public void clearCache() {
        cache.clear();
    }
}

/**
 * 事件总线
 */
class EventBus {
    
    private static List<EventListener> listeners = new ArrayList<>();
    
    public void register(EventListener listener) {
        listeners.add(listener);
    }
    
    public void unregister(EventListener listener) {
        listeners.remove(listener);
    }
    
    public void fire(String event) {
        for (EventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}

/**
 * 事件监听器
 */
interface EventListener {
    void onEvent(String event);
}

/**
 * ThreadLocal泄漏示例
 */
class ThreadLocalLeakExample {
    
    private static ThreadLocal<byte[]> threadLocal = new ThreadLocal<>();
    
    public static void setData(byte[] data) {
        threadLocal.set(data);
    }
    
    public static byte[] getData() {
        return threadLocal.get();
    }
    
    public static void remove() {
        threadLocal.remove();
    }
}

/**
 * 资源泄漏示例
 */
class ResourceLeakExample {
    
    private byte[] buffer = new byte[1024 * 1024];  // 1MB
    
    public void open() {
        // 打开资源
    }
    
    public void use() {
        // 使用资源
    }
    
    public void close() {
        // 关闭资源
        buffer = null;
    }
}

/**
 * 外部类
 */
class OuterClass {
    
    private byte[] data = new byte[1024 * 1024];  // 1MB
    
    // 非静态内部类（持有外部类引用）
    public class InnerClass {
        public void doSomething() {
            // 可以访问外部类成员
            System.out.println(data.length);
        }
    }
    
    // 静态内部类（不持有外部类引用）
    public static class StaticInnerClass {
        public void doSomething() {
            // 无法访问外部类成员
        }
    }
}

/**
 * 内存泄漏检测工具
 */
class MemoryLeakDetector {
    
    private static final int _1MB = 1024 * 1024;
    private static List<Long> memoryHistory = new ArrayList<>();
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 内存泄漏检测 ==========\n");
        
        // 启动内存监控
        startMonitoring();
        
        // 模拟内存泄漏
        simulateMemoryLeak();
    }
    
    private static void startMonitoring() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            memoryHistory.add(used);
            
            System.out.println("内存使用: " + (used / _1MB) + "MB");
            
            // 检测内存泄漏
            if (memoryHistory.size() >= 10) {
                detectLeak();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    private static void detectLeak() {
        int size = memoryHistory.size();
        long first = memoryHistory.get(size - 10);
        long last = memoryHistory.get(size - 1);
        
        long growth = last - first;
        double growthRate = growth * 100.0 / first;
        
        if (growthRate > 50) {
            System.err.println("⚠️  检测到内存泄漏:");
            System.err.println("   增长量: " + (growth / _1MB) + "MB");
            System.err.println("   增长率: " + String.format("%.2f%%", growthRate));
        }
    }
    
    private static void simulateMemoryLeak() {
        List<byte[]> leak = new ArrayList<>();
        
        while (true) {
            leak.add(new byte[_1MB]);
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}

/**
 * 内存泄漏修复示例
 */
class MemoryLeakFix {
    
    public static void main(String[] args) {
        System.out.println("========== 内存泄漏修复示例 ==========\n");
        
        // 修复1：静态集合使用WeakHashMap
        demonstrateWeakHashMap();
        
        // 修复2：ThreadLocal使用try-finally
        demonstrateThreadLocalFix();
        
        // 修复3：资源使用try-with-resources
        demonstrateResourceFix();
    }
    
    private static void demonstrateWeakHashMap() {
        System.out.println("1. 使用WeakHashMap:");
        
        Map<Object, Object> map = new WeakHashMap<>();
        
        Object key = new Object();
        map.put(key, "value");
        
        System.out.println("  添加前: " + map.size());
        
        key = null;
        System.gc();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("  GC后: " + map.size());
        System.out.println("  Key被回收，entry自动删除\n");
    }
    
    private static void demonstrateThreadLocalFix() {
        System.out.println("2. ThreadLocal使用try-finally:");
        
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        
        try {
            threadLocal.set("value");
            System.out.println("  使用ThreadLocal");
        } finally {
            threadLocal.remove();
            System.out.println("  清理ThreadLocal\n");
        }
    }
    
    private static void demonstrateResourceFix() {
        System.out.println("3. 资源使用try-with-resources:");
        
        try (AutoCloseableResource resource = new AutoCloseableResource()) {
            resource.use();
            System.out.println("  使用资源");
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("  资源自动关闭");
    }
    
    static class AutoCloseableResource implements AutoCloseable {
        
        public void use() {
            System.out.println("  使用资源");
        }
        
        @Override
        public void close() {
            System.out.println("  关闭资源");
        }
    }
}
