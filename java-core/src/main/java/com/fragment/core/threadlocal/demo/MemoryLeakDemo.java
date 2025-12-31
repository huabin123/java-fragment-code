package com.fragment.core.threadlocal.demo;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ThreadLocal内存泄漏演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>内存泄漏的产生过程</li>
 *   <li>弱引用的作用</li>
 *   <li>过期Entry的清理</li>
 *   <li>正确的使用方式</li>
 * </ul>
 * 
 * @author fragment
 */
public class MemoryLeakDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== ThreadLocal内存泄漏演示 ==========\n");
        
        // 1. 弱引用的作用
        demonstrateWeakReference();
        
        // 2. ThreadLocal的弱引用
        demonstrateThreadLocalWeakReference();
        
        // 3. 内存泄漏场景
        demonstrateMemoryLeak();
        
        // 4. 正确的使用方式
        demonstrateCorrectUsage();
    }
    
    /**
     * 演示1：弱引用的作用
     */
    private static void demonstrateWeakReference() {
        System.out.println("1. 弱引用的作用");
        
        // 强引用
        Object strongRef = new Object();
        System.out.println("强引用对象: " + strongRef);
        
        // 弱引用
        WeakReference<Object> weakRef = new WeakReference<>(new Object());
        System.out.println("弱引用对象（GC前）: " + weakRef.get());
        
        // 触发GC
        System.gc();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 强引用对象仍然存在
        System.out.println("强引用对象（GC后）: " + strongRef);
        
        // 弱引用对象被回收
        System.out.println("弱引用对象（GC后）: " + weakRef.get()); // null
        
        System.out.println();
    }
    
    /**
     * 演示2：ThreadLocal的弱引用
     */
    private static void demonstrateThreadLocalWeakReference() {
        System.out.println("2. ThreadLocal的弱引用机制");
        
        // 创建ThreadLocal对象
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        threadLocal.set("value");
        
        System.out.println("设置值后: " + threadLocal.get());
        
        // 断开强引用
        threadLocal = null;
        
        System.out.println("断开强引用后，触发GC");
        System.gc();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("说明:");
        System.out.println("1. ThreadLocal对象被GC回收");
        System.out.println("2. Entry的key变为null（弱引用）");
        System.out.println("3. 但Entry的value仍然存在（强引用）");
        System.out.println("4. 这就是过期Entry");
        
        System.out.println();
    }
    
    /**
     * 演示3：内存泄漏场景
     */
    private static void demonstrateMemoryLeak() throws InterruptedException {
        System.out.println("3. 内存泄漏场景演示");
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // 模拟内存泄漏
        System.out.println("提交10个任务，每个任务存储1MB数据");
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.execute(() -> {
                // 创建ThreadLocal（局部变量）
                ThreadLocal<byte[]> localData = new ThreadLocal<>();
                
                // 存储1MB数据
                localData.set(new byte[1024 * 1024]);
                
                System.out.println("任务" + taskId + " 存储了1MB数据");
                
                // 模拟业务处理
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                // ❌ 忘记remove，导致内存泄漏
                // localData.remove();
                
                // 任务结束，localData变量超出作用域
                // ThreadLocal对象可以被GC
                // 但1MB的byte[]数组仍然被Entry.value引用，无法被GC
            });
        }
        
        Thread.sleep(2000);
        
        System.out.println("\n内存泄漏分析:");
        System.out.println("1. 线程池有2个线程");
        System.out.println("2. 每个线程执行了5个任务");
        System.out.println("3. 每个任务存储1MB数据，但不清理");
        System.out.println("4. 每个线程的ThreadLocalMap中有5个过期Entry");
        System.out.println("5. 总内存泄漏：2线程 × 5任务 × 1MB = 10MB");
        System.out.println("6. 只有线程结束时，这10MB才能被GC");
        
        executor.shutdown();
        System.out.println();
    }
    
    /**
     * 演示4：正确的使用方式
     */
    private static void demonstrateCorrectUsage() throws InterruptedException {
        System.out.println("4. 正确的使用方式");
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        System.out.println("提交10个任务，正确清理ThreadLocal");
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.execute(() -> {
                ThreadLocal<byte[]> localData = new ThreadLocal<>();
                
                try {
                    // 存储1MB数据
                    localData.set(new byte[1024 * 1024]);
                    
                    System.out.println("任务" + taskId + " 存储了1MB数据");
                    
                    // 模拟业务处理
                    Thread.sleep(100);
                    
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    // ✅ 正确：在finally中remove
                    localData.remove();
                    System.out.println("任务" + taskId + " 已清理ThreadLocal");
                }
            });
        }
        
        Thread.sleep(2000);
        
        System.out.println("\n正确使用的效果:");
        System.out.println("1. 每个任务结束时调用remove()");
        System.out.println("2. Entry被清理，value的引用被断开");
        System.out.println("3. 1MB数据可以被GC回收");
        System.out.println("4. 无内存泄漏");
        
        executor.shutdown();
        
        System.out.println("\n关键点:");
        System.out.println("1. ThreadLocal使用后必须调用remove()");
        System.out.println("2. 使用try-finally保证remove()一定执行");
        System.out.println("3. 特别是在线程池场景下");
    }
}
