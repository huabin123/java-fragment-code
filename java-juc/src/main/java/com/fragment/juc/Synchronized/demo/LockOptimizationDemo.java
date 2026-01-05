package com.fragment.juc.Synchronized.demo;

/**
 * 锁优化机制演示
 * 
 * 演示内容：
 * 1. 自旋锁
 * 2. 锁消除
 * 3. 锁粗化
 * 4. 逃逸分析
 * 
 * JVM参数：
 * -XX:+DoEscapeAnalysis -XX:+EliminateLocks -XX:+PrintCompilation
 * 
 * @author huabin
 */
public class LockOptimizationDemo {
    
    /**
     * 示例1：自旋锁演示
     */
    static class SpinLockDemo {
        private volatile boolean locked = false;
        private int count = 0;
        
        // 简单的自旋锁实现
        public void lock() {
            while (locked) {
                // 自旋等待
            }
            locked = true;
        }
        
        public void unlock() {
            locked = false;
        }
        
        public void increment() {
            lock();
            try {
                count++;
            } finally {
                unlock();
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例1：自旋锁演示 ==========");
            SpinLockDemo demo = new SpinLockDemo();
            
            long start = System.currentTimeMillis();
            
            // 创建10个线程，每个线程执行1000次
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        demo.increment();
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            long end = System.currentTimeMillis();
            
            System.out.println("最终结果: " + demo.count);
            System.out.println("耗时: " + (end - start) + "ms");
            System.out.println("说明: 自旋锁适合锁持有时间短的场景");
        }
    }
    
    /**
     * 示例2：自适应自旋演示
     */
    static class AdaptiveSpinLockDemo {
        private volatile boolean locked = false;
        private int count = 0;
        private volatile int spinCount = 100; // 初始自旋次数
        
        public void lock() {
            int spins = spinCount;
            int attempts = 0;
            
            // 自旋尝试获取锁
            while (attempts < spins) {
                if (!locked) {
                    locked = true;
                    // 自旋成功，增加自旋次数
                    spinCount = Math.min(spinCount * 2, 10000);
                    return;
                }
                attempts++;
            }
            
            // 自旋失败，减少自旋次数
            spinCount = Math.max(spinCount / 2, 10);
            
            // 阻塞等待
            while (locked) {
                Thread.yield();
            }
            locked = true;
        }
        
        public void unlock() {
            locked = false;
        }
        
        public void increment() {
            lock();
            try {
                count++;
            } finally {
                unlock();
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例2：自适应自旋演示 ==========");
            AdaptiveSpinLockDemo demo = new AdaptiveSpinLockDemo();
            
            long start = System.currentTimeMillis();
            
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        demo.increment();
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            long end = System.currentTimeMillis();
            
            System.out.println("最终结果: " + demo.count);
            System.out.println("最终自旋次数: " + demo.spinCount);
            System.out.println("耗时: " + (end - start) + "ms");
            System.out.println("说明: 自适应自旋根据历史情况动态调整自旋次数");
        }
    }
    
    /**
     * 示例3：锁消除演示
     */
    static class LockEliminationDemo {
        
        // 场景1：局部变量不会逃逸，锁会被消除
        public String concat1(String s1, String s2) {
            StringBuffer sb = new StringBuffer(); // 局部变量
            sb.append(s1);
            sb.append(s2);
            return sb.toString();
            // JIT编译后，StringBuffer的锁会被消除
        }
        
        // 场景2：使用StringBuilder（无锁）
        public String concat2(String s1, String s2) {
            StringBuilder sb = new StringBuilder();
            sb.append(s1);
            sb.append(s2);
            return sb.toString();
        }
        
        public static void test() {
            System.out.println("\n========== 示例3：锁消除演示 ==========");
            LockEliminationDemo demo = new LockEliminationDemo();
            
            int iterations = 1000000;
            
            // 测试StringBuffer（会被锁消除）
            long start1 = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                demo.concat1("Hello", "World");
            }
            long end1 = System.currentTimeMillis();
            
            // 测试StringBuilder
            long start2 = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                demo.concat2("Hello", "World");
            }
            long end2 = System.currentTimeMillis();
            
            System.out.println("StringBuffer耗时: " + (end1 - start1) + "ms");
            System.out.println("StringBuilder耗时: " + (end2 - start2) + "ms");
            System.out.println("说明: JIT编译后，StringBuffer的锁会被消除，性能接近StringBuilder");
            System.out.println("需要JVM参数: -XX:+DoEscapeAnalysis -XX:+EliminateLocks");
        }
    }
    
    /**
     * 示例4：锁粗化演示
     */
    static class LockCoarseningDemo {
        private final Object lock = new Object();
        private int count = 0;
        
        // 场景1：循环内加锁（会被粗化）
        public void increment1(int n) {
            for (int i = 0; i < n; i++) {
                synchronized(lock) { // 每次循环都加锁
                    count++;
                }
            }
            // JIT编译后，会被优化为：
            // synchronized(lock) {
            //     for (int i = 0; i < n; i++) {
            //         count++;
            //     }
            // }
        }
        
        // 场景2：手动粗化
        public void increment2(int n) {
            synchronized(lock) { // 只加一次锁
                for (int i = 0; i < n; i++) {
                    count++;
                }
            }
        }
        
        // 场景3：连续加锁（会被粗化）
        public void multipleOperations() {
            synchronized(lock) {
                count++;
            }
            synchronized(lock) {
                count++;
            }
            synchronized(lock) {
                count++;
            }
            // JIT编译后，会被优化为：
            // synchronized(lock) {
            //     count++;
            //     count++;
            //     count++;
            // }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例4：锁粗化演示 ==========");
            
            // 测试循环内加锁
            LockCoarseningDemo demo1 = new LockCoarseningDemo();
            long start1 = System.currentTimeMillis();
            
            Thread[] threads1 = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads1[i] = new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        demo1.increment1(100);
                    }
                });
                threads1[i].start();
            }
            for (Thread thread : threads1) {
                thread.join();
            }
            
            long end1 = System.currentTimeMillis();
            
            // 测试手动粗化
            LockCoarseningDemo demo2 = new LockCoarseningDemo();
            long start2 = System.currentTimeMillis();
            
            Thread[] threads2 = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads2[i] = new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        demo2.increment2(100);
                    }
                });
                threads2[i].start();
            }
            for (Thread thread : threads2) {
                thread.join();
            }
            
            long end2 = System.currentTimeMillis();
            
            System.out.println("循环内加锁耗时: " + (end1 - start1) + "ms");
            System.out.println("手动粗化耗时: " + (end2 - start2) + "ms");
            System.out.println("说明: JIT编译后，循环内的锁会被粗化，性能接近手动粗化");
        }
    }
    
    /**
     * 示例5：逃逸分析演示
     */
    static class EscapeAnalysisDemo {
        
        // 场景1：对象不逃逸（栈上分配）
        public void noEscape() {
            Object obj = new Object();
            synchronized(obj) {
                // obj不会逃逸出方法
            }
            // JIT编译后，obj可能被分配在栈上，锁会被消除
        }
        
        // 场景2：对象逃逸（堆上分配）
        private Object escapedObj;
        
        public void escape() {
            Object obj = new Object();
            synchronized(obj) {
                escapedObj = obj; // obj逃逸了
            }
            // obj逃逸了，必须在堆上分配，锁不能被消除
        }
        
        public static void test() {
            System.out.println("\n========== 示例5：逃逸分析演示 ==========");
            EscapeAnalysisDemo demo = new EscapeAnalysisDemo();
            
            int iterations = 10000000;
            
            // 测试不逃逸
            long start1 = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                demo.noEscape();
            }
            long end1 = System.currentTimeMillis();
            
            // 测试逃逸
            long start2 = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                demo.escape();
            }
            long end2 = System.currentTimeMillis();
            
            System.out.println("不逃逸耗时: " + (end1 - start1) + "ms");
            System.out.println("逃逸耗时: " + (end2 - start2) + "ms");
            System.out.println("说明: 不逃逸的对象可能被栈上分配，锁被消除，性能更好");
            System.out.println("需要JVM参数: -XX:+DoEscapeAnalysis");
        }
    }
    
    /**
     * 示例6：锁优化对比
     */
    static class OptimizationComparisonDemo {
        private int count = 0;
        
        // 方式1：频繁加锁（未优化）
        public void frequentLock() {
            for (int i = 0; i < 1000; i++) {
                synchronized(this) {
                    count++;
                }
            }
        }
        
        // 方式2：锁粗化（手动优化）
        public void coarsenedLock() {
            synchronized(this) {
                for (int i = 0; i < 1000; i++) {
                    count++;
                }
            }
        }
        
        // 方式3：无锁（使用局部变量）
        public void noLock() {
            int localCount = 0;
            for (int i = 0; i < 1000; i++) {
                localCount++;
            }
            synchronized(this) {
                count += localCount;
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例6：锁优化对比 ==========");
            
            // 测试频繁加锁
            OptimizationComparisonDemo demo1 = new OptimizationComparisonDemo();
            long start1 = System.currentTimeMillis();
            Thread[] threads1 = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads1[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        demo1.frequentLock();
                    }
                });
                threads1[i].start();
            }
            for (Thread thread : threads1) {
                thread.join();
            }
            long end1 = System.currentTimeMillis();
            
            // 测试锁粗化
            OptimizationComparisonDemo demo2 = new OptimizationComparisonDemo();
            long start2 = System.currentTimeMillis();
            Thread[] threads2 = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads2[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        demo2.coarsenedLock();
                    }
                });
                threads2[i].start();
            }
            for (Thread thread : threads2) {
                thread.join();
            }
            long end2 = System.currentTimeMillis();
            
            // 测试无锁
            OptimizationComparisonDemo demo3 = new OptimizationComparisonDemo();
            long start3 = System.currentTimeMillis();
            Thread[] threads3 = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads3[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        demo3.noLock();
                    }
                });
                threads3[i].start();
            }
            for (Thread thread : threads3) {
                thread.join();
            }
            long end3 = System.currentTimeMillis();
            
            System.out.println("频繁加锁耗时: " + (end1 - start1) + "ms, 结果: " + demo1.count);
            System.out.println("锁粗化耗时: " + (end2 - start2) + "ms, 结果: " + demo2.count);
            System.out.println("无锁优化耗时: " + (end3 - start3) + "ms, 结果: " + demo3.count);
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("锁优化机制演示");
        System.out.println("建议JVM参数：-XX:+DoEscapeAnalysis -XX:+EliminateLocks -XX:+PrintCompilation\n");
        
        // 运行所有示例
        SpinLockDemo.test();
        AdaptiveSpinLockDemo.test();
        LockEliminationDemo.test();
        LockCoarseningDemo.test();
        EscapeAnalysisDemo.test();
        OptimizationComparisonDemo.test();
    }
}
