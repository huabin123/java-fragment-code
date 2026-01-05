package com.fragment.juc.atomic.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AtomicInteger等基本类型原子类演示
 * 
 * 演示内容：
 * 1. AtomicInteger的常用方法
 * 2. AtomicLong的使用
 * 3. AtomicBoolean的使用
 * 4. 原子类的线程安全性验证
 * 
 * @author huabin
 */
public class AtomicIntegerDemo {

    /**
     * 演示1：AtomicInteger的基本操作
     */
    public static void demoBasicOperations() {
        System.out.println("\n========== 演示1：AtomicInteger基本操作 ==========\n");

        AtomicInteger atomicInt = new AtomicInteger(10);
        System.out.println("初始值: " + atomicInt.get());

        // get和set
        atomicInt.set(20);
        System.out.println("\nset(20)后的值: " + atomicInt.get());

        // getAndIncrement: 先获取再自增（i++）
        int oldValue1 = atomicInt.getAndIncrement();
        System.out.println("\ngetAndIncrement():");
        System.out.println("  返回值(旧值): " + oldValue1);
        System.out.println("  当前值: " + atomicInt.get());

        // incrementAndGet: 先自增再获取（++i）
        int newValue1 = atomicInt.incrementAndGet();
        System.out.println("\nincrementAndGet():");
        System.out.println("  返回值(新值): " + newValue1);
        System.out.println("  当前值: " + atomicInt.get());

        // getAndDecrement: 先获取再自减（i--）
        int oldValue2 = atomicInt.getAndDecrement();
        System.out.println("\ngetAndDecrement():");
        System.out.println("  返回值(旧值): " + oldValue2);
        System.out.println("  当前值: " + atomicInt.get());

        // decrementAndGet: 先自减再获取（--i）
        int newValue2 = atomicInt.decrementAndGet();
        System.out.println("\ndecrementAndGet():");
        System.out.println("  返回值(新值): " + newValue2);
        System.out.println("  当前值: " + atomicInt.get());

        // getAndAdd: 先获取再增加
        int oldValue3 = atomicInt.getAndAdd(5);
        System.out.println("\ngetAndAdd(5):");
        System.out.println("  返回值(旧值): " + oldValue3);
        System.out.println("  当前值: " + atomicInt.get());

        // addAndGet: 先增加再获取
        int newValue3 = atomicInt.addAndGet(5);
        System.out.println("\naddAndGet(5):");
        System.out.println("  返回值(新值): " + newValue3);
        System.out.println("  当前值: " + atomicInt.get());

        // compareAndSet: CAS操作
        boolean success1 = atomicInt.compareAndSet(25, 100);
        System.out.println("\ncompareAndSet(25, 100): " + success1);
        System.out.println("  当前值: " + atomicInt.get());

        boolean success2 = atomicInt.compareAndSet(25, 200);
        System.out.println("\ncompareAndSet(25, 200): " + success2 + " (失败，因为当前值不是25)");
        System.out.println("  当前值: " + atomicInt.get());

        // getAndSet: 设置新值并返回旧值
        int oldValue4 = atomicInt.getAndSet(50);
        System.out.println("\ngetAndSet(50):");
        System.out.println("  返回值(旧值): " + oldValue4);
        System.out.println("  当前值: " + atomicInt.get());
    }

    /**
     * 演示2：AtomicInteger的线程安全性
     */
    public static void demoThreadSafety() throws InterruptedException {
        System.out.println("\n========== 演示2：AtomicInteger线程安全性 ==========\n");

        final int threadCount = 10;
        final int incrementPerThread = 1000;
        
        AtomicInteger atomicCounter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        // 创建多个线程并发执行
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    atomicCounter.incrementAndGet();
                }
                latch.countDown();
            }, "Thread-" + i).start();
        }

        // 等待所有线程完成
        latch.await();
        long endTime = System.currentTimeMillis();

        int expected = threadCount * incrementPerThread;
        int actual = atomicCounter.get();

        System.out.println("线程数: " + threadCount);
        System.out.println("每线程操作数: " + incrementPerThread);
        System.out.println("预期结果: " + expected);
        System.out.println("实际结果: " + actual);
        System.out.println("耗时: " + (endTime - startTime) + "ms");

        if (actual == expected) {
            System.out.println("✅ 结果正确 - AtomicInteger保证了线程安全");
        } else {
            System.out.println("❌ 结果错误");
        }
    }

    /**
     * 演示3：AtomicBoolean的使用
     */
    public static void demoAtomicBoolean() throws InterruptedException {
        System.out.println("\n========== 演示3：AtomicBoolean使用 ==========\n");

        // 场景1：一次性开关
        System.out.println("场景1：一次性开关（确保只执行一次）");
        AtomicBoolean initialized = new AtomicBoolean(false);

        Runnable initTask = () -> {
            // 只有第一个线程能成功将false改为true
            if (initialized.compareAndSet(false, true)) {
                System.out.println("  [" + Thread.currentThread().getName() + "] 执行初始化");
                try {
                    Thread.sleep(100); // 模拟初始化耗时
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("  [" + Thread.currentThread().getName() + "] 初始化完成");
            } else {
                System.out.println("  [" + Thread.currentThread().getName() + "] 已经初始化，跳过");
            }
        };

        Thread t1 = new Thread(initTask, "Thread-1");
        Thread t2 = new Thread(initTask, "Thread-2");
        Thread t3 = new Thread(initTask, "Thread-3");

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        System.out.println("  ✅ 只有一个线程执行了初始化");

        // 场景2：状态标志
        System.out.println("\n场景2：状态标志（优雅关闭）");
        AtomicBoolean running = new AtomicBoolean(true);

        Thread worker = new Thread(() -> {
            int count = 0;
            System.out.println("  [Worker] 开始工作...");
            while (running.get()) {
                count++;
                if (count % 1000000 == 0) {
                    System.out.println("  [Worker] 已处理 " + count + " 次");
                }
            }
            System.out.println("  [Worker] 检测到停止信号，优雅退出");
        }, "Worker");

        worker.start();
        Thread.sleep(100);

        System.out.println("  [Main] 发送停止信号");
        running.set(false);

        worker.join();
        System.out.println("  ✅ Worker线程优雅退出");
    }

    /**
     * 演示4：AtomicLong的使用
     */
    public static void demoAtomicLong() {
        System.out.println("\n========== 演示4：AtomicLong使用 ==========\n");

        AtomicLong atomicLong = new AtomicLong(0L);

        // 模拟ID生成器
        System.out.println("模拟分布式ID生成器:");
        for (int i = 0; i < 5; i++) {
            long id = atomicLong.incrementAndGet();
            System.out.println("  生成ID: " + id);
        }

        // 大数值操作
        System.out.println("\n大数值操作:");
        atomicLong.set(Long.MAX_VALUE - 10);
        System.out.println("  设置为: " + atomicLong.get());
        System.out.println("  自增后: " + atomicLong.incrementAndGet());

        System.out.println("\n✅ AtomicLong适用于:");
        System.out.println("   - ID生成器");
        System.out.println("   - 统计计数");
        System.out.println("   - 时间戳管理");
    }

    /**
     * 演示5：实现一个简单的限流器
     */
    public static void demoRateLimiter() throws InterruptedException {
        System.out.println("\n========== 演示5：基于AtomicInteger的简单限流器 ==========\n");

        // 简单的计数器限流器
        class SimpleRateLimiter {
            private final AtomicInteger counter = new AtomicInteger(0);
            private final int maxRequests;
            private final long windowMs;
            private volatile long windowStart;

            public SimpleRateLimiter(int maxRequests, long windowMs) {
                this.maxRequests = maxRequests;
                this.windowMs = windowMs;
                this.windowStart = System.currentTimeMillis();
            }

            public boolean tryAcquire() {
                long now = System.currentTimeMillis();
                
                // 检查是否需要重置窗口
                if (now - windowStart >= windowMs) {
                    synchronized (this) {
                        if (now - windowStart >= windowMs) {
                            counter.set(0);
                            windowStart = now;
                        }
                    }
                }

                // 尝试获取许可
                int current;
                do {
                    current = counter.get();
                    if (current >= maxRequests) {
                        return false;
                    }
                } while (!counter.compareAndSet(current, current + 1));

                return true;
            }

            public int getCurrentCount() {
                return counter.get();
            }
        }

        // 测试限流器：每秒最多5个请求
        SimpleRateLimiter limiter = new SimpleRateLimiter(5, 1000);

        System.out.println("限流规则: 每秒最多5个请求\n");

        // 快速发送10个请求
        for (int i = 1; i <= 10; i++) {
            boolean allowed = limiter.tryAcquire();
            System.out.println("请求" + i + ": " + 
                             (allowed ? "✅ 通过" : "❌ 被限流") + 
                             " (当前计数: " + limiter.getCurrentCount() + ")");
            Thread.sleep(50);
        }

        System.out.println("\n等待1秒，窗口重置...");
        Thread.sleep(1000);

        // 再次发送请求
        System.out.println("\n新窗口的请求:");
        for (int i = 1; i <= 3; i++) {
            boolean allowed = limiter.tryAcquire();
            System.out.println("请求" + i + ": " + 
                             (allowed ? "✅ 通过" : "❌ 被限流") + 
                             " (当前计数: " + limiter.getCurrentCount() + ")");
        }

        System.out.println("\n✅ AtomicInteger可以用于实现简单的限流器");
    }

    /**
     * 总结原子类的使用场景
     */
    public static void summarizeUseCases() {
        System.out.println("\n========== 原子类使用场景总结 ==========");
        
        System.out.println("\n✅ AtomicInteger适用场景:");
        System.out.println("   1. 计数器（访问量、点击量等）");
        System.out.println("   2. 序列号生成");
        System.out.println("   3. 状态标记");
        System.out.println("   4. 简单的限流器");
        
        System.out.println("\n✅ AtomicLong适用场景:");
        System.out.println("   1. 大数值计数");
        System.out.println("   2. 分布式ID生成");
        System.out.println("   3. 时间戳管理");
        System.out.println("   4. 统计数据");
        
        System.out.println("\n✅ AtomicBoolean适用场景:");
        System.out.println("   1. 一次性开关（初始化标志）");
        System.out.println("   2. 状态标志（运行/停止）");
        System.out.println("   3. 特性开关（Feature Toggle）");
        
        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 只适用于单个变量的原子操作");
        System.out.println("   2. 复合操作需要CAS循环");
        System.out.println("   3. 高竞争场景考虑使用LongAdder");
        System.out.println("   4. 不能替代锁的所有场景");
        
        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           AtomicInteger等基本类型原子类演示                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本操作
        demoBasicOperations();

        // 演示2：线程安全性
        demoThreadSafety();

        // 演示3：AtomicBoolean
        demoAtomicBoolean();

        // 演示4：AtomicLong
        demoAtomicLong();

        // 演示5：限流器
        demoRateLimiter();

        // 总结
        summarizeUseCases();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. 原子类提供了线程安全的基本类型操作");
        System.out.println("2. 基于CAS实现，性能优于synchronized");
        System.out.println("3. 提供了丰富的原子操作方法");
        System.out.println("4. 适用于简单的并发场景");
        System.out.println("5. 复杂场景需要配合CAS循环使用");
        System.out.println("===========================");
    }
}
