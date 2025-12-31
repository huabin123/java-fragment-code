package com.fragment.core.threadlocal.demo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ThreadLocal基础使用演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>ThreadLocal的基本使用</li>
 *   <li>线程隔离效果</li>
 *   <li>SimpleDateFormat线程安全化</li>
 *   <li>正确的清理方式</li>
 * </ul>
 * 
 * @author fragment
 */
public class ThreadLocalBasicDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== ThreadLocal基础演示 ==========\n");
        
        // 1. 基本使用
        demonstrateBasicUsage();
        
        // 2. 线程隔离
        demonstrateThreadIsolation();
        
        // 3. SimpleDateFormat线程安全化
        demonstrateDateFormat();
        
        // 4. 正确的清理方式
        demonstrateProperCleanup();
    }
    
    /**
     * 演示1：基本使用
     */
    private static void demonstrateBasicUsage() {
        System.out.println("1. ThreadLocal基本使用");
        
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        
        // 设置值
        threadLocal.set("Hello ThreadLocal");
        
        // 获取值
        String value = threadLocal.get();
        System.out.println("获取的值: " + value);
        
        // 清理
        threadLocal.remove();
        
        // 清理后获取
        String afterRemove = threadLocal.get();
        System.out.println("清理后的值: " + afterRemove); // null
        
        System.out.println();
    }
    
    /**
     * 演示2：线程隔离
     */
    private static void demonstrateThreadIsolation() throws InterruptedException {
        System.out.println("2. 线程隔离演示");
        
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        
        // 主线程设置值
        threadLocal.set("Main Thread Value");
        System.out.println("主线程设置: " + threadLocal.get());
        
        // 创建子线程1
        Thread t1 = new Thread(() -> {
            // 子线程1设置自己的值
            threadLocal.set("Thread-1 Value");
            System.out.println("线程1设置: " + threadLocal.get());
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // 线程1的值不受其他线程影响
            System.out.println("线程1再次获取: " + threadLocal.get());
        }, "Thread-1");
        
        // 创建子线程2
        Thread t2 = new Thread(() -> {
            // 子线程2设置自己的值
            threadLocal.set("Thread-2 Value");
            System.out.println("线程2设置: " + threadLocal.get());
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // 线程2的值不受其他线程影响
            System.out.println("线程2再次获取: " + threadLocal.get());
        }, "Thread-2");
        
        t1.start();
        t2.start();
        
        t1.join();
        t2.join();
        
        // 主线程的值不受子线程影响
        System.out.println("主线程再次获取: " + threadLocal.get());
        
        System.out.println();
    }
    
    /**
     * 演示3：SimpleDateFormat线程安全化
     */
    private static void demonstrateDateFormat() throws InterruptedException {
        System.out.println("3. SimpleDateFormat线程安全化");
        
        // 使用ThreadLocal包装SimpleDateFormat
        ThreadLocal<SimpleDateFormat> sdfHolder = ThreadLocal.withInitial(
            () -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        );
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        // 提交10个任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.execute(() -> {
                try {
                    // 每个线程使用自己的SimpleDateFormat实例
                    SimpleDateFormat sdf = sdfHolder.get();
                    String dateStr = sdf.format(new Date());
                    System.out.println("任务" + taskId + " 格式化结果: " + dateStr);
                } finally {
                    // 清理ThreadLocal
                    sdfHolder.remove();
                }
            });
        }
        
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }
        
        System.out.println();
    }
    
    /**
     * 演示4：正确的清理方式
     */
    private static void demonstrateProperCleanup() throws InterruptedException {
        System.out.println("4. 正确的清理方式");
        
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // 任务1：不清理（错误示例）
        System.out.println("任务1：不清理ThreadLocal");
        executor.execute(() -> {
            threadLocal.set("Task1 Value");
            System.out.println("  任务1设置: " + threadLocal.get());
            // ❌ 忘记清理
        });
        
        Thread.sleep(100);
        
        // 任务2：复用同一个线程，可能获取到任务1的值
        System.out.println("任务2：可能获取到任务1的值");
        executor.execute(() -> {
            String value = threadLocal.get();
            System.out.println("  任务2获取: " + value); // 可能是"Task1 Value"
        });
        
        Thread.sleep(100);
        
        // 任务3：正确清理
        System.out.println("任务3：正确清理ThreadLocal");
        executor.execute(() -> {
            try {
                threadLocal.set("Task3 Value");
                System.out.println("  任务3设置: " + threadLocal.get());
            } finally {
                // ✅ 正确清理
                threadLocal.remove();
                System.out.println("  任务3已清理");
            }
        });
        
        Thread.sleep(100);
        
        // 任务4：获取不到任务3的值
        System.out.println("任务4：获取不到任务3的值");
        executor.execute(() -> {
            String value = threadLocal.get();
            System.out.println("  任务4获取: " + value); // null
        });
        
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }
        
        System.out.println("\n关键点:");
        System.out.println("1. 线程池场景下，必须在finally中调用remove()");
        System.out.println("2. 否则会导致数据污染和内存泄漏");
    }
}
