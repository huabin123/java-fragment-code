package com.fragment.juc.Synchronized.demo;

/**
 * Synchronized基础使用演示
 * 
 * 演示内容：
 * 1. 修饰实例方法
 * 2. 修饰静态方法
 * 3. 同步代码块
 * 4. 线程安全问题演示
 * 5. 可重入性演示
 * 
 * @author huabin
 */
public class BasicUsageDemo {
    
    /**
     * 示例1：修饰实例方法
     */
    static class InstanceMethodDemo {
        private int count = 0;
        
        // synchronized修饰实例方法，锁对象是this
        public synchronized void increment() {
            count++;
            System.out.println(Thread.currentThread().getName() + 
                " increment: " + count);
        }
        
        public synchronized int getCount() {
            return count;
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例1：修饰实例方法 ==========");
            InstanceMethodDemo demo = new InstanceMethodDemo();
            
            // 创建10个线程，每个线程执行100次increment
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        demo.increment();
                    }
                }, "Thread-" + i);
                threads[i].start();
            }
            
            // 等待所有线程执行完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            System.out.println("最终结果: " + demo.getCount());
            System.out.println("预期结果: 1000");
            System.out.println("结果正确: " + (demo.getCount() == 1000));
        }
    }
    
    /**
     * 示例2：修饰静态方法
     */
    static class StaticMethodDemo {
        private static int count = 0;
        
        // synchronized修饰静态方法，锁对象是Class对象
        public static synchronized void increment() {
            count++;
            System.out.println(Thread.currentThread().getName() + 
                " increment: " + count);
        }
        
        public static synchronized int getCount() {
            return count;
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例2：修饰静态方法 ==========");
            count = 0; // 重置计数器
            
            // 创建10个线程，每个线程执行100次increment
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        StaticMethodDemo.increment();
                    }
                }, "Thread-" + i);
                threads[i].start();
            }
            
            // 等待所有线程执行完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            System.out.println("最终结果: " + getCount());
            System.out.println("预期结果: 1000");
            System.out.println("结果正确: " + (getCount() == 1000));
        }
    }
    
    /**
     * 示例3：同步代码块
     */
    static class SynchronizedBlockDemo {
        private int count = 0;
        private final Object lock = new Object();
        
        public void increment() {
            // 同步代码块，锁对象是lock
            synchronized(lock) {
                count++;
                System.out.println(Thread.currentThread().getName() + 
                    " increment: " + count);
            }
        }
        
        public int getCount() {
            synchronized(lock) {
                return count;
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例3：同步代码块 ==========");
            SynchronizedBlockDemo demo = new SynchronizedBlockDemo();
            
            // 创建10个线程，每个线程执行100次increment
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        demo.increment();
                    }
                }, "Thread-" + i);
                threads[i].start();
            }
            
            // 等待所有线程执行完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            System.out.println("最终结果: " + demo.getCount());
            System.out.println("预期结果: 1000");
            System.out.println("结果正确: " + (demo.getCount() == 1000));
        }
    }
    
    /**
     * 示例4：线程安全问题演示（不使用synchronized）
     */
    static class UnsafeDemo {
        private int count = 0;
        
        // 没有synchronized修饰
        public void increment() {
            count++;
        }
        
        public int getCount() {
            return count;
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例4：线程安全问题演示 ==========");
            UnsafeDemo demo = new UnsafeDemo();
            
            // 创建10个线程，每个线程执行1000次increment
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        demo.increment();
                    }
                }, "Thread-" + i);
                threads[i].start();
            }
            
            // 等待所有线程执行完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            System.out.println("最终结果: " + demo.getCount());
            System.out.println("预期结果: 10000");
            System.out.println("结果正确: " + (demo.getCount() == 10000));
            System.out.println("说明: 由于没有使用synchronized，结果可能不正确");
        }
    }
    
    /**
     * 示例5：可重入性演示
     */
    static class ReentrantDemo {
        private int count = 0;
        
        public synchronized void method1() {
            System.out.println(Thread.currentThread().getName() + 
                " 进入method1");
            count++;
            method2(); // 调用另一个synchronized方法
            System.out.println(Thread.currentThread().getName() + 
                " 退出method1");
        }
        
        public synchronized void method2() {
            System.out.println(Thread.currentThread().getName() + 
                " 进入method2");
            count++;
            method3(); // 调用另一个synchronized方法
            System.out.println(Thread.currentThread().getName() + 
                " 退出method2");
        }
        
        public synchronized void method3() {
            System.out.println(Thread.currentThread().getName() + 
                " 进入method3");
            count++;
            System.out.println(Thread.currentThread().getName() + 
                " 退出method3");
        }
        
        public synchronized int getCount() {
            return count;
        }
        
        public static void test() {
            System.out.println("\n========== 示例5：可重入性演示 ==========");
            ReentrantDemo demo = new ReentrantDemo();
            
            Thread thread = new Thread(() -> {
                demo.method1();
            }, "ReentrantThread");
            
            thread.start();
            
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            System.out.println("最终count: " + demo.getCount());
            System.out.println("说明: synchronized是可重入的，同一线程可以多次获取同一个锁");
        }
    }
    
    /**
     * 示例6：不同锁对象的演示
     */
    static class DifferentLockDemo {
        private int count1 = 0;
        private int count2 = 0;
        private final Object lock1 = new Object();
        private final Object lock2 = new Object();
        
        public void incrementCount1() {
            synchronized(lock1) {
                count1++;
                System.out.println(Thread.currentThread().getName() + 
                    " incrementCount1: " + count1);
                try {
                    Thread.sleep(10); // 模拟耗时操作
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        public void incrementCount2() {
            synchronized(lock2) {
                count2++;
                System.out.println(Thread.currentThread().getName() + 
                    " incrementCount2: " + count2);
                try {
                    Thread.sleep(10); // 模拟耗时操作
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例6：不同锁对象的演示 ==========");
            DifferentLockDemo demo = new DifferentLockDemo();
            
            // 线程1操作count1
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    demo.incrementCount1();
                }
            }, "Thread-1");
            
            // 线程2操作count2
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    demo.incrementCount2();
                }
            }, "Thread-2");
            
            long start = System.currentTimeMillis();
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            long end = System.currentTimeMillis();
            
            System.out.println("耗时: " + (end - start) + "ms");
            System.out.println("说明: 两个线程使用不同的锁对象，可以并发执行");
        }
    }
    
    /**
     * 示例7：相同锁对象的演示
     */
    static class SameLockDemo {
        private int count1 = 0;
        private int count2 = 0;
        private final Object lock = new Object(); // 使用同一个锁
        
        public void incrementCount1() {
            synchronized(lock) {
                count1++;
                System.out.println(Thread.currentThread().getName() + 
                    " incrementCount1: " + count1);
                try {
                    Thread.sleep(10); // 模拟耗时操作
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        public void incrementCount2() {
            synchronized(lock) {
                count2++;
                System.out.println(Thread.currentThread().getName() + 
                    " incrementCount2: " + count2);
                try {
                    Thread.sleep(10); // 模拟耗时操作
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例7：相同锁对象的演示 ==========");
            SameLockDemo demo = new SameLockDemo();
            
            // 线程1操作count1
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    demo.incrementCount1();
                }
            }, "Thread-1");
            
            // 线程2操作count2
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    demo.incrementCount2();
                }
            }, "Thread-2");
            
            long start = System.currentTimeMillis();
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            long end = System.currentTimeMillis();
            
            System.out.println("耗时: " + (end - start) + "ms");
            System.out.println("说明: 两个线程使用相同的锁对象，必须串行执行");
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        // 运行所有示例
        InstanceMethodDemo.test();
        StaticMethodDemo.test();
        SynchronizedBlockDemo.test();
        UnsafeDemo.test();
        ReentrantDemo.test();
        DifferentLockDemo.test();
        SameLockDemo.test();
    }
}
