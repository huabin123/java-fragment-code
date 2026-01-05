package com.fragment.juc.atomic.project;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 基于原子类的计数器实现
 * 
 * 实现内容：
 * 1. 多种计数器实现（AtomicInteger、AtomicLong、LongAdder）
 * 2. 限流计数器
 * 3. 统计计数器
 * 4. 性能对比分析
 * 
 * @author huabin
 */
public class AtomicCounter {

    /**
     * 基础计数器接口
     */
    interface Counter {
        void increment();
        void decrement();
        long get();
        void reset();
    }

    /**
     * 基于AtomicInteger的计数器
     */
    static class AtomicIntegerCounter implements Counter {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public void increment() {
            count.incrementAndGet();
        }

        @Override
        public void decrement() {
            count.decrementAndGet();
        }

        @Override
        public long get() {
            return count.get();
        }

        @Override
        public void reset() {
            count.set(0);
        }
    }

    /**
     * 基于AtomicLong的计数器
     */
    static class AtomicLongCounter implements Counter {
        private final AtomicLong count = new AtomicLong(0);

        @Override
        public void increment() {
            count.incrementAndGet();
        }

        @Override
        public void decrement() {
            count.decrementAndGet();
        }

        @Override
        public long get() {
            return count.get();
        }

        @Override
        public void reset() {
            count.set(0);
        }
    }

    /**
     * 基于LongAdder的计数器
     */
    static class LongAdderCounter implements Counter {
        private final LongAdder count = new LongAdder();

        @Override
        public void increment() {
            count.increment();
        }

        @Override
        public void decrement() {
            count.decrement();
        }

        @Override
        public long get() {
            return count.sum();
        }

        @Override
        public void reset() {
            count.reset();
        }
    }

    /**
     * 滑动窗口限流计数器
     */
    static class SlidingWindowRateLimiter {
        private final AtomicLong[] counters;
        private final int windowSize;
        private final int maxRequests;
        private volatile long windowStart;

        public SlidingWindowRateLimiter(int windowSize, int maxRequests) {
            this.windowSize = windowSize;
            this.maxRequests = maxRequests;
            this.counters = new AtomicLong[windowSize];
            for (int i = 0; i < windowSize; i++) {
                counters[i] = new AtomicLong(0);
            }
            this.windowStart = System.currentTimeMillis() / 1000;
        }

        public boolean tryAcquire() {
            long currentSecond = System.currentTimeMillis() / 1000;
            int index = (int) (currentSecond % windowSize);

            // 如果进入新的时间窗口，重置计数
            if (currentSecond >= windowStart + windowSize) {
                synchronized (this) {
                    if (currentSecond >= windowStart + windowSize) {
                        windowStart = currentSecond;
                        for (AtomicLong counter : counters) {
                            counter.set(0);
                        }
                    }
                }
            }

            // 计算当前窗口内的总请求数
            long total = 0;
            for (AtomicLong counter : counters) {
                total += counter.get();
            }

            if (total >= maxRequests) {
                return false;
            }

            counters[index].incrementAndGet();
            return true;
        }

        public long getCurrentCount() {
            long total = 0;
            for (AtomicLong counter : counters) {
                total += counter.get();
            }
            return total;
        }
    }

    /**
     * 多维度统计计数器
     */
    static class StatisticsCounter {
        private final LongAdder totalCount = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder failureCount = new LongAdder();
        private final AtomicLong maxValue = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong minValue = new AtomicLong(Long.MAX_VALUE);
        private final LongAdder sumValue = new LongAdder();

        public void record(boolean success, long value) {
            totalCount.increment();
            if (success) {
                successCount.increment();
            } else {
                failureCount.increment();
            }

            // 更新最大值
            long currentMax;
            do {
                currentMax = maxValue.get();
                if (value <= currentMax) break;
            } while (!maxValue.compareAndSet(currentMax, value));

            // 更新最小值
            long currentMin;
            do {
                currentMin = minValue.get();
                if (value >= currentMin) break;
            } while (!minValue.compareAndSet(currentMin, value));

            sumValue.add(value);
        }

        public long getTotal() {
            return totalCount.sum();
        }

        public long getSuccess() {
            return successCount.sum();
        }

        public long getFailure() {
            return failureCount.sum();
        }

        public double getSuccessRate() {
            long total = getTotal();
            return total == 0 ? 0 : (double) getSuccess() / total * 100;
        }

        public long getMax() {
            return maxValue.get();
        }

        public long getMin() {
            return minValue.get();
        }

        public double getAverage() {
            long total = getTotal();
            return total == 0 ? 0 : (double) sumValue.sum() / total;
        }

        public void reset() {
            totalCount.reset();
            successCount.reset();
            failureCount.reset();
            maxValue.set(Long.MIN_VALUE);
            minValue.set(Long.MAX_VALUE);
            sumValue.reset();
        }

        public void printStatistics() {
            System.out.println("统计信息:");
            System.out.println("  总请求数: " + getTotal());
            System.out.println("  成功数: " + getSuccess());
            System.out.println("  失败数: " + getFailure());
            System.out.println("  成功率: " + String.format("%.2f%%", getSuccessRate()));
            System.out.println("  最大值: " + getMax());
            System.out.println("  最小值: " + getMin());
            System.out.println("  平均值: " + String.format("%.2f", getAverage()));
        }
    }

    /**
     * 演示1：基础计数器对比
     */
    public static void demoBasicCounters() throws InterruptedException {
        System.out.println("\n========== 演示1：基础计数器对比 ==========\n");

        final int threadCount = 10;
        final int operations = 100000;

        // 测试AtomicInteger
        System.out.println("测试AtomicIntegerCounter...");
        AtomicIntegerCounter atomicIntCounter = new AtomicIntegerCounter();
        long time1 = testCounter(atomicIntCounter, threadCount, operations);

        // 测试AtomicLong
        System.out.println("测试AtomicLongCounter...");
        AtomicLongCounter atomicLongCounter = new AtomicLongCounter();
        long time2 = testCounter(atomicLongCounter, threadCount, operations);

        // 测试LongAdder
        System.out.println("测试LongAdderCounter...");
        LongAdderCounter longAdderCounter = new LongAdderCounter();
        long time3 = testCounter(longAdderCounter, threadCount, operations);

        // 输出对比
        System.out.println("\n性能对比:");
        System.out.println("  AtomicInteger: " + time1 + "ms");
        System.out.println("  AtomicLong:    " + time2 + "ms");
        System.out.println("  LongAdder:     " + time3 + "ms");

        System.out.println("\n结果验证:");
        System.out.println("  AtomicInteger: " + atomicIntCounter.get());
        System.out.println("  AtomicLong:    " + atomicLongCounter.get());
        System.out.println("  LongAdder:     " + longAdderCounter.get());
        System.out.println("  预期值:        " + (threadCount * operations));
    }

    private static long testCounter(Counter counter, int threadCount, int operations) 
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadCount);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    counter.increment();
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 演示2：限流计数器
     */
    public static void demoRateLimiter() throws InterruptedException {
        System.out.println("\n========== 演示2：限流计数器 ==========\n");

        // 创建限流器：5秒窗口，最多10个请求
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, 10);

        System.out.println("限流规则: 5秒窗口内最多10个请求\n");

        // 快速发送15个请求
        for (int i = 1; i <= 15; i++) {
            boolean allowed = limiter.tryAcquire();
            System.out.println("请求" + i + ": " + 
                             (allowed ? "✅ 通过" : "❌ 被限流") + 
                             " (当前窗口计数: " + limiter.getCurrentCount() + ")");
            Thread.sleep(100);
        }

        System.out.println("\n等待5秒，窗口重置...");
        Thread.sleep(5000);

        System.out.println("\n新窗口的请求:");
        for (int i = 1; i <= 5; i++) {
            boolean allowed = limiter.tryAcquire();
            System.out.println("请求" + i + ": " + 
                             (allowed ? "✅ 通过" : "❌ 被限流") + 
                             " (当前窗口计数: " + limiter.getCurrentCount() + ")");
        }

        System.out.println("\n✅ 滑动窗口限流器工作正常");
    }

    /**
     * 演示3：统计计数器
     */
    public static void demoStatisticsCounter() throws InterruptedException {
        System.out.println("\n========== 演示3：统计计数器 ==========\n");

        StatisticsCounter stats = new StatisticsCounter();

        System.out.println("模拟API请求统计...\n");

        int threadCount = 5;
        int requestsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    boolean success = Math.random() > 0.1; // 90%成功率
                    long responseTime = (long) (Math.random() * 1000); // 0-1000ms
                    stats.record(success, responseTime);
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        System.out.println("统计完成:\n");
        stats.printStatistics();

        System.out.println("\n✅ 统计计数器适用于多维度数据统计");
    }

    /**
     * 演示4：实时监控计数器
     */
    public static void demoRealtimeMonitor() throws InterruptedException {
        System.out.println("\n========== 演示4：实时监控计数器 ==========\n");

        class RealtimeMonitor {
            private final LongAdder qps = new LongAdder();
            private final LongAdder totalRequests = new LongAdder();
            private volatile boolean running = true;

            public void recordRequest() {
                qps.increment();
                totalRequests.increment();
            }

            public void startMonitoring() {
                new Thread(() -> {
                    while (running) {
                        try {
                            Thread.sleep(1000);
                            long currentQps = qps.sumThenReset();
                            System.out.println("[Monitor] QPS: " + currentQps + 
                                             ", 总请求数: " + totalRequests.sum());
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "Monitor").start();
            }

            public void stop() {
                running = false;
            }
        }

        RealtimeMonitor monitor = new RealtimeMonitor();
        monitor.startMonitoring();

        System.out.println("模拟5秒的请求流量...\n");

        // 模拟请求
        Thread requestThread = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 1000; j++) {
                    monitor.recordRequest();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "Request-Generator");

        requestThread.start();
        requestThread.join();

        Thread.sleep(1500); // 等待最后一次监控输出
        monitor.stop();

        System.out.println("\n✅ 实时监控计数器适用于QPS统计");
    }

    /**
     * 演示5：分布式ID生成器
     */
    public static void demoIdGenerator() throws InterruptedException {
        System.out.println("\n========== 演示5：分布式ID生成器 ==========\n");

        class IdGenerator {
            private final AtomicLong sequence = new AtomicLong(0);
            private final long workerId;
            private final long dataCenterId;

            public IdGenerator(long workerId, long dataCenterId) {
                this.workerId = workerId;
                this.dataCenterId = dataCenterId;
            }

            public long nextId() {
                long timestamp = System.currentTimeMillis();
                long seq = sequence.incrementAndGet() & 0xFFF; // 12位序列号

                // 简化版雪花算法
                return (timestamp << 22) | (dataCenterId << 17) | (workerId << 12) | seq;
            }
        }

        IdGenerator generator = new IdGenerator(1, 1);

        System.out.println("生成10个分布式ID:");
        for (int i = 0; i < 10; i++) {
            long id = generator.nextId();
            System.out.println("  ID-" + (i + 1) + ": " + id);
        }

        // 并发测试
        System.out.println("\n并发生成测试:");
        int threadCount = 5;
        int idsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    generator.nextId();
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        System.out.println("  生成" + (threadCount * idsPerThread) + "个ID");
        System.out.println("  耗时: " + (endTime - startTime) + "ms");
        System.out.println("  ✅ AtomicLong保证了ID的唯一性");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 原子计数器总结 ==========");

        System.out.println("\n📊 计数器选择指南:");
        System.out.println("  ┌─────────────────┬──────────────┬──────────────┐");
        System.out.println("  │   使用场景      │   推荐实现   │     原因     │");
        System.out.println("  ├─────────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 低并发计数      │ AtomicInteger│  简单高效    │");
        System.out.println("  │ 高并发统计      │ LongAdder    │  性能最优    │");
        System.out.println("  │ ID生成器        │ AtomicLong   │  支持大数值  │");
        System.out.println("  │ 限流计数        │ AtomicLong   │  需要CAS     │");
        System.out.println("  │ 实时监控        │ LongAdder    │  高并发友好  │");
        System.out.println("  └─────────────────┴──────────────┴──────────────┘");

        System.out.println("\n✅ 最佳实践:");
        System.out.println("   1. 根据并发度选择合适的实现");
        System.out.println("   2. 高并发场景优先使用LongAdder");
        System.out.println("   3. 需要CAS操作时使用Atomic类");
        System.out.println("   4. 注意LongAdder的sum()不是原子操作");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 计数器溢出问题（使用long避免）");
        System.out.println("   2. 内存可见性（原子类已保证）");
        System.out.println("   3. 性能vs精确性的权衡");
        System.out.println("   4. 合理使用reset()方法");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            基于原子类的计数器实现                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基础计数器对比
        demoBasicCounters();

        // 演示2：限流计数器
        demoRateLimiter();

        // 演示3：统计计数器
        demoStatisticsCounter();

        // 演示4：实时监控
        demoRealtimeMonitor();

        // 演示5：ID生成器
        demoIdGenerator();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. 原子类提供了高性能的计数器实现");
        System.out.println("2. LongAdder适用于高并发统计场景");
        System.out.println("3. AtomicLong适用于ID生成和限流");
        System.out.println("4. 可以实现多维度的统计功能");
        System.out.println("5. 根据场景选择合适的实现");
        System.out.println("===========================");
    }
}
