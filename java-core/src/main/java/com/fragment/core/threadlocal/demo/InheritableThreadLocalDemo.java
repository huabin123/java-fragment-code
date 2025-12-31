package com.fragment.core.threadlocal.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * InheritableThreadLocal演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>InheritableThreadLocal的基本使用</li>
 *   <li>父子线程值传递</li>
 *   <li>自定义childValue方法</li>
 *   <li>线程池场景下的问题</li>
 * </ul>
 * 
 * @author fragment
 */
public class InheritableThreadLocalDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== InheritableThreadLocal演示 ==========\n");
        
        // 1. 基本使用
        demonstrateBasicUsage();
        
        // 2. 父子线程值传递
        demonstrateInheritance();
        
        // 3. 自定义childValue
        demonstrateCustomChildValue();
        
        // 4. 线程池场景下的问题
        demonstrateThreadPoolProblem();
    }
    
    /**
     * 演示1：基本使用
     */
    private static void demonstrateBasicUsage() throws InterruptedException {
        System.out.println("1. InheritableThreadLocal基本使用");
        
        // ThreadLocal：子线程无法获取父线程的值
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        threadLocal.set("ThreadLocal Value");
        
        new Thread(() -> {
            System.out.println("ThreadLocal - 子线程获取: " + threadLocal.get()); // null
        }).start();
        
        Thread.sleep(100);
        
        // InheritableThreadLocal：子线程可以获取父线程的值
        InheritableThreadLocal<String> inheritableThreadLocal = new InheritableThreadLocal<>();
        inheritableThreadLocal.set("InheritableThreadLocal Value");
        
        new Thread(() -> {
            System.out.println("InheritableThreadLocal - 子线程获取: " + inheritableThreadLocal.get());
        }).start();
        
        Thread.sleep(100);
        System.out.println();
    }
    
    /**
     * 演示2：父子线程值传递
     */
    private static void demonstrateInheritance() throws InterruptedException {
        System.out.println("2. 父子线程值传递");
        
        InheritableThreadLocal<String> holder = new InheritableThreadLocal<>();
        
        // 父线程设置值
        holder.set("Parent Value");
        System.out.println("父线程设置: " + holder.get());
        
        // 创建子线程
        Thread child = new Thread(() -> {
            // 子线程继承父线程的值
            System.out.println("子线程继承: " + holder.get());
            
            // 子线程修改值
            holder.set("Child Value");
            System.out.println("子线程修改: " + holder.get());
        });
        
        child.start();
        child.join();
        
        // 父线程的值不受影响
        System.out.println("父线程不受影响: " + holder.get());
        
        System.out.println();
    }
    
    /**
     * 演示3：自定义childValue
     */
    private static void demonstrateCustomChildValue() throws InterruptedException {
        System.out.println("3. 自定义childValue方法");
        
        // 自定义InheritableThreadLocal，实现深拷贝
        InheritableThreadLocal<List<String>> holder = new InheritableThreadLocal<List<String>>() {
            @Override
            protected List<String> childValue(List<String> parentValue) {
                // 深拷贝，避免父子线程共享同一个List
                System.out.println("  调用childValue进行深拷贝");
                return new ArrayList<>(parentValue);
            }
        };
        
        // 父线程设置List
        List<String> parentList = new ArrayList<>();
        parentList.add("item1");
        parentList.add("item2");
        holder.set(parentList);
        System.out.println("父线程设置: " + holder.get());
        
        // 创建子线程
        Thread child = new Thread(() -> {
            List<String> childList = holder.get();
            System.out.println("子线程继承: " + childList);
            
            // 子线程修改List
            childList.add("item3");
            System.out.println("子线程修改后: " + childList);
        });
        
        child.start();
        child.join();
        
        // 父线程的List不受影响（因为深拷贝）
        System.out.println("父线程不受影响: " + holder.get());
        
        System.out.println();
    }
    
    /**
     * 演示4：线程池场景下的问题
     */
    private static void demonstrateThreadPoolProblem() throws InterruptedException {
        System.out.println("4. 线程池场景下的问题");
        
        InheritableThreadLocal<String> holder = new InheritableThreadLocal<>();
        ExecutorService executor = Executors.newFixedThreadPool(1);
        
        // 第1次提交任务
        System.out.println("第1次提交任务:");
        holder.set("value1");
        System.out.println("  主线程设置: " + holder.get());
        
        executor.execute(() -> {
            System.out.println("  任务1获取: " + holder.get()); // "value1"
        });
        
        Thread.sleep(100);
        
        // 第2次提交任务（复用同一个线程）
        System.out.println("第2次提交任务（复用同一个线程）:");
        holder.set("value2");
        System.out.println("  主线程设置: " + holder.get());
        
        executor.execute(() -> {
            System.out.println("  任务2获取: " + holder.get()); // 仍然是"value1"（错误！）
        });
        
        Thread.sleep(100);
        
        executor.shutdown();
        
        System.out.println("\n问题分析:");
        System.out.println("1. 线程池的线程是预先创建的");
        System.out.println("2. 线程创建时继承了主线程的值");
        System.out.println("3. 后续任务复用线程，不会重新继承");
        System.out.println("4. 导致获取到的是旧值");
        System.out.println("\n解决方案:");
        System.out.println("1. 使用TransmittableThreadLocal（阿里开源）");
        System.out.println("2. 手动在任务开始时设置值");
    }
}
