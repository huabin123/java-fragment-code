package com.example.jvm.tuning.demo;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * CPU飙高问题演示
 * 
 * 演示内容：
 * 1. 死循环导致CPU飙高
 * 2. 大量计算导致CPU飙高
 * 3. 正则表达式回溯导致CPU飙高
 * 4. 频繁GC导致CPU飙高
 * 5. 线程竞争导致CPU飙高
 * 
 * 注意：这些都是问题代码示例，用于演示排查过程
 * 
 * @author JavaGuide
 */
public class CPUHighDemo {

    public static void main(String[] args) {
        System.out.println("========== CPU飙高问题演示 ==========\n");
        System.out.println("选择要演示的场景:");
        System.out.println("1. 死循环");
        System.out.println("2. 大量计算");
        System.out.println("3. 正则表达式回溯");
        System.out.println("4. 频繁GC");
        System.out.println("5. 线程竞争");
        
        // 取消注释运行对应场景
        // demonstrateDeadLoop();
        // demonstrateHeavyComputation();
        // demonstrateRegexBacktracking();
        // demonstrateFrequentGC();
        // demonstrateThreadContention();
    }

    /**
     * 场景1：死循环导致CPU飙高
     */
    public static void demonstrateDeadLoop() {
        System.out.println("\n========== 场景1：死循环 ==========");
        
        new Thread(() -> {
            System.out.println("线程启动，进入死循环...");
            
            // 问题代码：死循环
            while (true) {
                // 没有退出条件
                // 没有sleep
                // CPU 100%
            }
        }, "DeadLoop-Thread").start();
        
        System.out.println("死循环线程已启动");
        System.out.println("使用 jstack 可以看到该线程一直在运行");
    }

    /**
     * 场景2：大量计算导致CPU飙高
     */
    public static void demonstrateHeavyComputation() {
        System.out.println("\n========== 场景2：大量计算 ==========");
        
        new Thread(() -> {
            System.out.println("开始大量计算...");
            
            long sum = 0;
            for (long i = 0; i < Long.MAX_VALUE; i++) {
                // 大量计算
                sum += Math.pow(i, 2);
                sum += Math.sqrt(i);
                
                if (i % 100000000 == 0) {
                    System.out.println("已计算: " + i);
                }
            }
        }, "HeavyComputation-Thread").start();
        
        System.out.println("大量计算线程已启动");
    }

    /**
     * 场景3：正则表达式回溯导致CPU飙高
     */
    public static void demonstrateRegexBacktracking() {
        System.out.println("\n========== 场景3：正则表达式回溯 ==========");
        
        // 问题正则：会导致大量回溯
        Pattern pattern = Pattern.compile("(a+)+b");
        
        new Thread(() -> {
            // 问题输入：大量'a'但没有'b'
            String input = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaac";
            
            System.out.println("开始正则匹配...");
            long start = System.currentTimeMillis();
            
            try {
                boolean matches = pattern.matcher(input).matches();
                System.out.println("匹配结果: " + matches);
            } catch (Exception e) {
                System.out.println("匹配异常: " + e.getMessage());
            }
            
            long duration = System.currentTimeMillis() - start;
            System.out.println("耗时: " + duration + "ms");
        }, "RegexBacktracking-Thread").start();
        
        System.out.println("正则匹配线程已启动");
        System.out.println("该正则会导致大量回溯，CPU飙高");
    }

    /**
     * 场景4：频繁GC导致CPU飙高
     */
    public static void demonstrateFrequentGC() {
        System.out.println("\n========== 场景4：频繁GC ==========");
        
        new Thread(() -> {
            System.out.println("开始频繁创建对象...");
            
            List<byte[]> list = new ArrayList<>();
            
            while (true) {
                // 频繁创建大对象
                byte[] data = new byte[1024 * 1024];  // 1MB
                list.add(data);
                
                // 定期清理，触发GC
                if (list.size() > 100) {
                    list.clear();
                    System.out.println("清理对象，触发GC");
                }
                
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "FrequentGC-Thread").start();
        
        System.out.println("频繁GC线程已启动");
        System.out.println("观察GC日志可以看到频繁的Minor GC");
    }

    /**
     * 场景5：线程竞争导致CPU飙高
     */
    public static void demonstrateThreadContention() {
        System.out.println("\n========== 场景5：线程竞争 ==========");
        
        // 共享资源
        final Object lock = new Object();
        final Counter counter = new Counter();
        
        // 创建大量线程竞争同一个锁
        int threadCount = 100;
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                while (true) {
                    synchronized (lock) {
                        counter.increment();
                    }
                    
                    // 模拟一些处理
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "Contention-Thread-" + i).start();
        }
        
        System.out.println("已启动 " + threadCount + " 个竞争线程");
        System.out.println("使用 jstack 可以看到大量BLOCKED线程");
    }

    /**
     * 计数器
     */
    static class Counter {
        private int count = 0;
        
        public void increment() {
            count++;
        }
        
        public int getCount() {
            return count;
        }
    }
}

/**
 * CPU飙高问题排查演示
 */
class CPUHighTroubleshooting {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== CPU飙高排查演示 ==========\n");
        
        // 启动一个CPU密集型线程
        Thread cpuIntensiveThread = new Thread(() -> {
            long sum = 0;
            while (true) {
                for (int i = 0; i < 1000000; i++) {
                    sum += i;
                }
            }
        }, "CPU-Intensive-Thread");
        
        cpuIntensiveThread.start();
        
        System.out.println("CPU密集型线程已启动");
        System.out.println("线程ID: " + cpuIntensiveThread.getId());
        System.out.println("线程名称: " + cpuIntensiveThread.getName());
        
        System.out.println("\n排查步骤:");
        System.out.println("1. top 查看CPU使用率");
        System.out.println("2. top -H -p <pid> 查看线程CPU使用率");
        System.out.println("3. printf \"%x\\n\" <线程ID> 转换为十六进制");
        System.out.println("4. jstack <pid> | grep <十六进制线程ID>");
        System.out.println("5. 分析线程栈，定位问题代码");
        
        // 等待一段时间
        Thread.sleep(10000);
        
        // 停止线程
        cpuIntensiveThread.interrupt();
    }
}

/**
 * 正则表达式优化示例
 */
class RegexOptimization {
    
    public static void main(String[] args) {
        System.out.println("========== 正则表达式优化 ==========\n");
        
        String input = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaac";
        
        // 问题正则
        System.out.println("1. 问题正则（会回溯）:");
        Pattern badPattern = Pattern.compile("(a+)+b");
        testRegex(badPattern, input);
        
        // 优化正则
        System.out.println("\n2. 优化正则（不会回溯）:");
        Pattern goodPattern = Pattern.compile("a+b");
        testRegex(goodPattern, input);
        
        // 使用超时机制
        System.out.println("\n3. 使用超时机制:");
        testRegexWithTimeout(badPattern, input, 1000);
    }
    
    private static void testRegex(Pattern pattern, String input) {
        long start = System.currentTimeMillis();
        try {
            boolean matches = pattern.matcher(input).matches();
            long duration = System.currentTimeMillis() - start;
            System.out.println("  匹配结果: " + matches);
            System.out.println("  耗时: " + duration + "ms");
        } catch (Exception e) {
            System.out.println("  异常: " + e.getMessage());
        }
    }
    
    private static void testRegexWithTimeout(Pattern pattern, String input, long timeoutMs) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> pattern.matcher(input).matches());
        
        try {
            Boolean result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            System.out.println("  匹配结果: " + result);
        } catch (TimeoutException e) {
            System.out.println("  超时，取消匹配");
            future.cancel(true);
        } catch (Exception e) {
            System.out.println("  异常: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }
}

/**
 * 线程竞争优化示例
 */
class ThreadContentionOptimization {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 线程竞争优化 ==========\n");
        
        int threadCount = 10;
        int iterations = 100000;
        
        // 测试1：使用synchronized（竞争激烈）
        System.out.println("1. 使用synchronized:");
        testWithSynchronized(threadCount, iterations);
        
        // 测试2：使用ConcurrentHashMap（无锁）
        System.out.println("\n2. 使用ConcurrentHashMap:");
        testWithConcurrentHashMap(threadCount, iterations);
        
        // 测试3：使用AtomicInteger（CAS）
        System.out.println("\n3. 使用AtomicInteger:");
        testWithAtomicInteger(threadCount, iterations);
    }
    
    private static void testWithSynchronized(int threadCount, int iterations) throws InterruptedException {
        Map<String, Integer> map = new HashMap<>();
        Object lock = new Object();
        
        long start = System.currentTimeMillis();
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    synchronized (lock) {
                        map.put("key" + threadId, j);
                    }
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - start;
        System.out.println("  耗时: " + duration + "ms");
    }
    
    private static void testWithConcurrentHashMap(int threadCount, int iterations) throws InterruptedException {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        
        long start = System.currentTimeMillis();
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    map.put("key" + threadId, j);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - start;
        System.out.println("  耗时: " + duration + "ms");
    }
    
    private static void testWithAtomicInteger(int threadCount, int iterations) throws InterruptedException {
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        
        long start = System.currentTimeMillis();
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    counter.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - start;
        System.out.println("  耗时: " + duration + "ms");
        System.out.println("  最终值: " + counter.get());
    }
}
