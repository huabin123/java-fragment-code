package com.fragment.juc.atomic.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * LongAdder高性能原子类演示
 * 
 * 演示内容：
 * 1. LongAdder vs AtomicLong性能对比
 * 2. LongAdder的实现原理
 * 3. LongAccumulator的使用
 * 4. 适用场景分析
 * 
 * @author huabin
 */
public class LongAdderDemo {

    /**
     * 演示1：LongAdder基本使用
     */
    public static void demoBasicUsage() {
        System.out.println("\n========== 演示1：LongAdder基本使用 ==========\n");

        LongAdder adder = new LongAdder();
        System.out.println("初始值: " + adder.sum());

        // 增加操作
        adder.increment(); // +1
        System.out.println("increment()后: " + adder.sum());

        adder.add(5); // +5
        System.out.println("add(5)后: " + adder.sum());

        adder.decrement(); // -1
        System.out.println("decrement()后: " + adder.sum());

        // 重置
        adder.reset();
        System.out.println("reset()后: " + adder.sum());

        // sumThenReset: 获取总和并重置
        adder.add(10);
        long sum = adder.sumThenReset();
        System.out.println("\nsumThenReset()返回: " + sum);
        System.out.println("重置后的值: " + adder.sum());

        System.out.println("\n✅ LongAdder提供了简单的累加操作");
    }

    /**
     * 演示2：LongAdder vs AtomicLong性能对比
     */
    public static void comparePerformance() throws InterruptedException {
        System.out.println("\n========== 演示2：性能对比 ==========\n");

        final int threadCount = 50;
        final int operations = 100000;

        // 测试1：AtomicLong
        System.out.println("测试AtomicLong...");
        AtomicLong atomicLong = new AtomicLong(0);
        long atomicStartTime = System.currentTimeMillis();

        CountDownLatch atomicLatch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    atomicLong.incrementAndGet();
                }
                atomicLatch.countDown();
            }).start();
        }
        atomicLatch.await();

        long atomicEndTime = System.currentTimeMillis();
        long atomicTime = atomicEndTime - atomicStartTime;

        // 测试2：LongAdder
        System.out.println("测试LongAdder...");
        LongAdder longAdder = new LongAdder();
        long adderStartTime = System.currentTimeMillis();

        CountDownLatch adderLatch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    longAdder.increment();
                }
                adderLatch.countDown();
            }).start();
        }
        adderLatch.await();

        long adderEndTime = System.currentTimeMillis();
        long adderTime = adderEndTime - adderStartTime;

        // 输出结果
        System.out.println("\n性能测试结果:");
        System.out.println("  线程数: " + threadCount);
        System.out.println("  每线程操作数: " + operations);
        System.out.println("  总操作数: " + (threadCount * operations));

        System.out.println("\nAtomicLong:");
        System.out.println("  结果: " + atomicLong.get());
        System.out.println("  耗时: " + atomicTime + "ms");

        System.out.println("\nLongAdder:");
        System.out.println("  结果: " + longAdder.sum());
        System.out.println("  耗时: " + adderTime + "ms");

        System.out.println("\n性能提升:");
        double improvement = (double) atomicTime / adderTime;
        System.out.println("  LongAdder比AtomicLong快 " + 
                         String.format("%.2f", improvement) + " 倍");

        if (improvement > 1.5) {
            System.out.println("  ✅ 高并发场景下LongAdder性能显著优于AtomicLong");
        }
    }

    /**
     * 演示3：LongAdder的实现原理
     */
    public static void explainImplementation() {
        System.out.println("\n========== 演示3：LongAdder实现原理 ==========");

        System.out.println("\nAtomicLong的问题:");
        System.out.println("  ┌─────────────────────────────────────┐");
        System.out.println("  │  所有线程竞争同一个变量              │");
        System.out.println("  │  ↓                                  │");
        System.out.println("  │  [Thread1] → [AtomicLong] ← [Thread2]");
        System.out.println("  │              ↑        ↓              │");
        System.out.println("  │         [Thread3] [Thread4]          │");
        System.out.println("  │                                      │");
        System.out.println("  │  高竞争 → 大量CAS失败 → 自旋 → CPU消耗");
        System.out.println("  └─────────────────────────────────────┘");

        System.out.println("\nLongAdder的解决方案（分段累加）:");
        System.out.println("  ┌─────────────────────────────────────┐");
        System.out.println("  │  将竞争分散到多个Cell上              │");
        System.out.println("  │                                      │");
        System.out.println("  │  [Thread1] → [Cell1]                │");
        System.out.println("  │  [Thread2] → [Cell2]                │");
        System.out.println("  │  [Thread3] → [Cell3]                │");
        System.out.println("  │  [Thread4] → [Cell4]                │");
        System.out.println("  │                                      │");
        System.out.println("  │  sum() = Cell1 + Cell2 + Cell3 + Cell4");
        System.out.println("  │                                      │");
        System.out.println("  │  低竞争 → 高性能                     │");
        System.out.println("  └─────────────────────────────────────┘");

        System.out.println("\n核心思想:");
        System.out.println("  1. 空间换时间: 使用多个Cell减少竞争");
        System.out.println("  2. 热点分离: 将热点数据分散");
        System.out.println("  3. 最终一致: sum()时汇总所有Cell");
        System.out.println("  4. 动态扩容: 根据竞争程度动态增加Cell数量");

        System.out.println("\n内部结构:");
        System.out.println("  LongAdder");
        System.out.println("    ├── base: long           // 基础值");
        System.out.println("    └── cells: Cell[]        // Cell数组");
        System.out.println("         ├── Cell[0]: long");
        System.out.println("         ├── Cell[1]: long");
        System.out.println("         └── Cell[n]: long");

        System.out.println("\n操作流程:");
        System.out.println("  increment():");
        System.out.println("    1. 尝试CAS更新base");
        System.out.println("    2. 如果失败，尝试更新当前线程对应的Cell");
        System.out.println("    3. 如果Cell也失败，考虑扩容或rehash");
        System.out.println("\n  sum():");
        System.out.println("    1. 读取base值");
        System.out.println("    2. 遍历所有Cell并累加");
        System.out.println("    3. 返回总和");
        System.out.println("    注意: sum()不是原子操作，返回的是近似值");

        System.out.println("\n适用场景:");
        System.out.println("  ✅ 适合: 高并发累加、统计计数");
        System.out.println("  ❌ 不适合: 需要精确值、低并发场景");

        System.out.println("===========================");
    }

    /**
     * 演示4：LongAccumulator的使用
     */
    public static void demoLongAccumulator() throws InterruptedException {
        System.out.println("\n========== 演示4：LongAccumulator使用 ==========\n");

        // 场景1：求和（等价于LongAdder）
        System.out.println("场景1：求和");
        LongAccumulator sumAccumulator = new LongAccumulator(Long::sum, 0);
        sumAccumulator.accumulate(10);
        sumAccumulator.accumulate(20);
        sumAccumulator.accumulate(30);
        System.out.println("  求和结果: " + sumAccumulator.get());

        // 场景2：求最大值
        System.out.println("\n场景2：求最大值");
        LongAccumulator maxAccumulator = new LongAccumulator(Long::max, Long.MIN_VALUE);
        
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    long value = threadId * 100 + j;
                    maxAccumulator.accumulate(value);
                    System.out.println("  [Thread-" + threadId + "] accumulate: " + value);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        System.out.println("  最大值: " + maxAccumulator.get());

        // 场景3：求最小值
        System.out.println("\n场景3：求最小值");
        LongAccumulator minAccumulator = new LongAccumulator(Long::min, Long.MAX_VALUE);
        minAccumulator.accumulate(100);
        minAccumulator.accumulate(50);
        minAccumulator.accumulate(75);
        System.out.println("  最小值: " + minAccumulator.get());

        // 场景4：自定义函数（求乘积）
        System.out.println("\n场景4：自定义函数（求乘积）");
        LongAccumulator productAccumulator = new LongAccumulator((x, y) -> x * y, 1);
        productAccumulator.accumulate(2);
        productAccumulator.accumulate(3);
        productAccumulator.accumulate(4);
        System.out.println("  乘积: " + productAccumulator.get());

        System.out.println("\n✅ LongAccumulator提供了更灵活的累加操作");
    }

    /**
     * 演示5：实际应用 - 统计系统
     */
    public static void demoStatisticsSystem() throws InterruptedException {
        System.out.println("\n========== 演示5：实际应用 - 统计系统 ==========\n");

        class StatisticsCollector {
            private final LongAdder totalRequests = new LongAdder();
            private final LongAdder successRequests = new LongAdder();
            private final LongAdder failedRequests = new LongAdder();
            private final LongAccumulator maxResponseTime = 
                new LongAccumulator(Long::max, 0);
            private final LongAccumulator minResponseTime = 
                new LongAccumulator(Long::min, Long.MAX_VALUE);

            public void recordRequest(boolean success, long responseTime) {
                totalRequests.increment();
                if (success) {
                    successRequests.increment();
                } else {
                    failedRequests.increment();
                }
                maxResponseTime.accumulate(responseTime);
                minResponseTime.accumulate(responseTime);
            }

            public void printStatistics() {
                long total = totalRequests.sum();
                long success = successRequests.sum();
                long failed = failedRequests.sum();
                long max = maxResponseTime.get();
                long min = minResponseTime.get();

                System.out.println("\n统计报告:");
                System.out.println("  总请求数: " + total);
                System.out.println("  成功请求: " + success + 
                                 " (" + String.format("%.2f", success * 100.0 / total) + "%)");
                System.out.println("  失败请求: " + failed + 
                                 " (" + String.format("%.2f", failed * 100.0 / total) + "%)");
                System.out.println("  最大响应时间: " + max + "ms");
                System.out.println("  最小响应时间: " + min + "ms");
            }

            public void reset() {
                totalRequests.reset();
                successRequests.reset();
                failedRequests.reset();
                maxResponseTime.reset();
                minResponseTime.reset();
            }
        }

        StatisticsCollector collector = new StatisticsCollector();

        System.out.println("模拟高并发请求统计...");
        int threadCount = 10;
        int requestsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    boolean success = Math.random() > 0.1; // 90%成功率
                    long responseTime = (long) (Math.random() * 1000); // 0-1000ms
                    collector.recordRequest(success, responseTime);
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        collector.printStatistics();
        System.out.println("  统计耗时: " + (endTime - startTime) + "ms");

        System.out.println("\n✅ LongAdder非常适合高并发统计场景");
    }

    /**
     * 总结LongAdder的使用
     */
    public static void summarizeUsage() {
        System.out.println("\n========== LongAdder使用总结 ==========");

        System.out.println("\n📊 AtomicLong vs LongAdder对比:");
        System.out.println("  ┌─────────────────┬──────────────┬──────────────┐");
        System.out.println("  │     特性        │  AtomicLong  │  LongAdder   │");
        System.out.println("  ├─────────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 低并发性能      │     好       │     一般     │");
        System.out.println("  │ 高并发性能      │     差       │     优秀     │");
        System.out.println("  │ 内存占用        │     小       │     大       │");
        System.out.println("  │ 实时精确性      │     是       │     否       │");
        System.out.println("  │ 支持CAS         │     是       │     否       │");
        System.out.println("  │ 适用场景        │  低并发计数  │  高并发统计  │");
        System.out.println("  └─────────────────┴──────────────┴──────────────┘");

        System.out.println("\n✅ 使用建议:");
        System.out.println("  选择AtomicLong的场景:");
        System.out.println("    - 并发度不高（<10个线程）");
        System.out.println("    - 需要精确的实时值");
        System.out.println("    - 需要使用CAS操作");
        System.out.println("    - 内存敏感的场景");

        System.out.println("\n  选择LongAdder的场景:");
        System.out.println("    - 高并发累加（>10个线程）");
        System.out.println("    - 统计计数（访问量、点击量等）");
        System.out.println("    - 可以接受最终一致性");
        System.out.println("    - 性能优先的场景");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("  1. LongAdder.sum()不是原子操作");
        System.out.println("  2. sum()返回的是近似值，不保证精确");
        System.out.println("  3. 不支持CAS操作");
        System.out.println("  4. 内存占用比AtomicLong大");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           LongAdder高性能原子类演示                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：性能对比
        comparePerformance();

        // 演示3：实现原理
        explainImplementation();

        // 演示4：LongAccumulator
        demoLongAccumulator();

        // 演示5：实际应用
        demoStatisticsSystem();

        // 总结
        summarizeUsage();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. LongAdder通过分段累加提升高并发性能");
        System.out.println("2. 空间换时间，用多个Cell减少竞争");
        System.out.println("3. sum()不是原子操作，返回近似值");
        System.out.println("4. 适用于高并发统计场景");
        System.out.println("5. LongAccumulator提供更灵活的累加函数");
        System.out.println("===========================");
    }
}
