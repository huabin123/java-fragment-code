package com.fragment.juc.Synchronized.project;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 线程安全计数器实现
 * 
 * 实现方式：
 * 1. 使用synchronized实现
 * 2. 使用AtomicInteger实现
 * 3. 使用LongAdder实现（高并发场景）
 * 4. 性能对比
 * 
 * @author huabin
 */
public class ThreadSafeCounter {
    
    /**
     * 方式1：使用synchronized实现
     */
    static class SynchronizedCounter {
        private int count = 0;
        
        public synchronized void increment() {
            count++;
        }
        
        public synchronized void decrement() {
            count--;
        }
        
        public synchronized int get() {
            return count;
        }
        
        public synchronized void reset() {
            count = 0;
        }
    }
    
    /**
     * 方式2：使用AtomicInteger实现
     */
    static class AtomicCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        
        public void increment() {
            count.incrementAndGet();
        }
        
        public void decrement() {
            count.decrementAndGet();
        }
        
        public int get() {
            return count.get();
        }
        
        public void reset() {
            count.set(0);
        }
    }
    
    /**
     * 方式3：使用LongAdder实现（高并发场景）
     */
    static class LongAdderCounter {
        private final LongAdder count = new LongAdder();
        
        public void increment() {
            count.increment();
        }
        
        public void decrement() {
            count.decrement();
        }
        
        public long get() {
            return count.sum();
        }
        
        public void reset() {
            count.reset();
        }
    }
    
    /**
     * 方式4：分段计数器（手动实现类似LongAdder的思想）
     */
    static class StripedCounter {
        private static final int STRIPE_COUNT = 16;
        private final SynchronizedCounter[] counters;
        
        public StripedCounter() {
            counters = new SynchronizedCounter[STRIPE_COUNT];
            for (int i = 0; i < STRIPE_COUNT; i++) {
                counters[i] = new SynchronizedCounter();
            }
        }
        
        private int getStripeIndex() {
            return (int) (Thread.currentThread().getId() % STRIPE_COUNT);
        }
        
        public void increment() {
            counters[getStripeIndex()].increment();
        }
        
        public void decrement() {
            counters[getStripeIndex()].decrement();
        }
        
        public int get() {
            int sum = 0;
            for (SynchronizedCounter counter : counters) {
                sum += counter.get();
            }
            return sum;
        }
        
        public void reset() {
            for (SynchronizedCounter counter : counters) {
                counter.reset();
            }
        }
    }
    
    /**
     * 方式5：带统计功能的计数器
     */
    static class StatisticsCounter {
        private int count = 0;
        private int maxCount = 0;
        private int minCount = 0;
        private long totalIncrements = 0;
        private long totalDecrements = 0;
        
        public synchronized void increment() {
            count++;
            totalIncrements++;
            if (count > maxCount) {
                maxCount = count;
            }
        }
        
        public synchronized void decrement() {
            count--;
            totalDecrements++;
            if (count < minCount) {
                minCount = count;
            }
        }
        
        public synchronized int get() {
            return count;
        }
        
        public synchronized int getMax() {
            return maxCount;
        }
        
        public synchronized int getMin() {
            return minCount;
        }
        
        public synchronized long getTotalIncrements() {
            return totalIncrements;
        }
        
        public synchronized long getTotalDecrements() {
            return totalDecrements;
        }
        
        public synchronized void reset() {
            count = 0;
            maxCount = 0;
            minCount = 0;
            totalIncrements = 0;
            totalDecrements = 0;
        }
        
        public synchronized void printStatistics() {
            System.out.println("当前值: " + count);
            System.out.println("最大值: " + maxCount);
            System.out.println("最小值: " + minCount);
            System.out.println("总增加次数: " + totalIncrements);
            System.out.println("总减少次数: " + totalDecrements);
        }
    }
    
    /**
     * 性能测试
     */
    static class PerformanceTest {
        private static final int THREAD_COUNT = 10;
        private static final int ITERATIONS = 1000000;
        
        public static void testSynchronized() throws InterruptedException {
            SynchronizedCounter counter = new SynchronizedCounter();
            long start = System.currentTimeMillis();
            
            Thread[] threads = new Thread[THREAD_COUNT];
            for (int i = 0; i < THREAD_COUNT; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < ITERATIONS; j++) {
                        counter.increment();
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            long end = System.currentTimeMillis();
            System.out.println("Synchronized耗时: " + (end - start) + "ms, 结果: " + counter.get());
        }
        
        public static void testAtomic() throws InterruptedException {
            AtomicCounter counter = new AtomicCounter();
            long start = System.currentTimeMillis();
            
            Thread[] threads = new Thread[THREAD_COUNT];
            for (int i = 0; i < THREAD_COUNT; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < ITERATIONS; j++) {
                        counter.increment();
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            long end = System.currentTimeMillis();
            System.out.println("AtomicInteger耗时: " + (end - start) + "ms, 结果: " + counter.get());
        }
        
        public static void testLongAdder() throws InterruptedException {
            LongAdderCounter counter = new LongAdderCounter();
            long start = System.currentTimeMillis();
            
            Thread[] threads = new Thread[THREAD_COUNT];
            for (int i = 0; i < THREAD_COUNT; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < ITERATIONS; j++) {
                        counter.increment();
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            long end = System.currentTimeMillis();
            System.out.println("LongAdder耗时: " + (end - start) + "ms, 结果: " + counter.get());
        }
        
        public static void testStriped() throws InterruptedException {
            StripedCounter counter = new StripedCounter();
            long start = System.currentTimeMillis();
            
            Thread[] threads = new Thread[THREAD_COUNT];
            for (int i = 0; i < THREAD_COUNT; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < ITERATIONS; j++) {
                        counter.increment();
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            long end = System.currentTimeMillis();
            System.out.println("Striped耗时: " + (end - start) + "ms, 结果: " + counter.get());
        }
    }
    
    /**
     * 实际应用场景
     */
    static class ApplicationScenarios {
        
        /**
         * 场景1：网站访问计数
         */
        static class WebsiteVisitCounter {
            private final LongAdderCounter totalVisits = new LongAdderCounter();
            private final LongAdderCounter todayVisits = new LongAdderCounter();
            
            public void recordVisit() {
                totalVisits.increment();
                todayVisits.increment();
            }
            
            public long getTotalVisits() {
                return totalVisits.get();
            }
            
            public long getTodayVisits() {
                return todayVisits.get();
            }
            
            public void resetTodayVisits() {
                todayVisits.reset();
            }
        }
        
        /**
         * 场景2：限流计数器
         */
        static class RateLimiter {
            private final AtomicInteger counter = new AtomicInteger(0);
            private final int maxRequests;
            private final long windowMillis;
            private volatile long windowStart;
            
            public RateLimiter(int maxRequests, long windowMillis) {
                this.maxRequests = maxRequests;
                this.windowMillis = windowMillis;
                this.windowStart = System.currentTimeMillis();
            }
            
            public synchronized boolean tryAcquire() {
                long now = System.currentTimeMillis();
                
                // 检查是否需要重置窗口
                if (now - windowStart >= windowMillis) {
                    counter.set(0);
                    windowStart = now;
                }
                
                // 检查是否超过限制
                if (counter.get() < maxRequests) {
                    counter.incrementAndGet();
                    return true;
                }
                
                return false;
            }
            
            public int getCurrentCount() {
                return counter.get();
            }
        }
        
        /**
         * 场景3：任务完成进度计数
         */
        static class ProgressCounter {
            private final AtomicInteger completed = new AtomicInteger(0);
            private final int total;
            
            public ProgressCounter(int total) {
                this.total = total;
            }
            
            public void increment() {
                int current = completed.incrementAndGet();
                if (current % (total / 10) == 0) {
                    System.out.println("进度: " + (current * 100 / total) + "%");
                }
            }
            
            public boolean isCompleted() {
                return completed.get() >= total;
            }
            
            public int getProgress() {
                return completed.get() * 100 / total;
            }
        }
    }
    
    /**
     * 测试代码
     */
    public static void main(String[] args) throws InterruptedException {
        // 测试1：基本功能
        System.out.println("========== 测试1：基本功能 ==========");
        SynchronizedCounter counter1 = new SynchronizedCounter();
        counter1.increment();
        counter1.increment();
        counter1.decrement();
        System.out.println("结果: " + counter1.get());
        
        // 测试2：并发测试
        System.out.println("\n========== 测试2：并发测试 ==========");
        SynchronizedCounter counter2 = new SynchronizedCounter();
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    counter2.increment();
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("并发结果: " + counter2.get());
        
        // 测试3：性能对比
        System.out.println("\n========== 测试3：性能对比 ==========");
        PerformanceTest.testSynchronized();
        PerformanceTest.testAtomic();
        PerformanceTest.testLongAdder();
        PerformanceTest.testStriped();
        
        // 测试4：统计功能
        System.out.println("\n========== 测试4：统计功能 ==========");
        StatisticsCounter counter4 = new StatisticsCounter();
        for (int i = 0; i < 10; i++) {
            counter4.increment();
        }
        for (int i = 0; i < 5; i++) {
            counter4.decrement();
        }
        counter4.printStatistics();
        
        // 测试5：限流器
        System.out.println("\n========== 测试5：限流器 ==========");
        ApplicationScenarios.RateLimiter limiter = 
            new ApplicationScenarios.RateLimiter(5, 1000); // 每秒最多5个请求
        
        for (int i = 0; i < 10; i++) {
            boolean acquired = limiter.tryAcquire();
            System.out.println("请求" + (i + 1) + ": " + (acquired ? "通过" : "被限流"));
        }
        
        // 测试6：进度计数
        System.out.println("\n========== 测试6：进度计数 ==========");
        ApplicationScenarios.ProgressCounter progress = 
            new ApplicationScenarios.ProgressCounter(100);
        
        Thread[] progressThreads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            progressThreads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    progress.increment();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            progressThreads[i].start();
        }
        
        for (Thread thread : progressThreads) {
            thread.join();
        }
        
        System.out.println("任务完成: " + progress.isCompleted());
        
        System.out.println("\n所有测试完成！");
    }
}
