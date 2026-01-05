package com.fragment.juc.sync.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CountDownLatch演示
 * 
 * 演示内容：
 * 1. 基本使用：主线程等待子线程
 * 2. 多个线程等待一个事件
 * 3. 超时等待
 * 4. 实际应用场景
 * 
 * @author huabin
 */
public class CountDownLatchDemo {

    /**
     * 演示1：主线程等待子线程完成
     */
    public static void demoWaitForThreads() throws InterruptedException {
        System.out.println("\n========== 演示1：主线程等待子线程 ==========\n");

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);

        System.out.println("启动" + threadCount + "个工作线程...\n");

        for (int i = 0; i < threadCount; i++) {
            final int taskId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("[任务" + taskId + "] 开始执行");
                    Thread.sleep((long) (Math.random() * 2000));
                    System.out.println("[任务" + taskId + "] 执行完成");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown(); // 计数减1
                    System.out.println("[任务" + taskId + "] 倒计时: " + latch.getCount());
                }
            }, "Worker-" + taskId).start();
        }

        System.out.println("\n[Main] 等待所有任务完成...");
        latch.await(); // 阻塞直到计数为0
        System.out.println("[Main] 所有任务已完成！\n");

        System.out.println("✅ CountDownLatch适合主线程等待多个子线程");
    }

    /**
     * 演示2：多个线程等待一个事件（起跑线）
     */
    public static void demoStartSignal() throws InterruptedException {
        System.out.println("\n========== 演示2：多个线程等待起跑信号 ==========\n");

        CountDownLatch startSignal = new CountDownLatch(1);
        int runnerCount = 5;

        System.out.println("运动员准备就绪...\n");

        for (int i = 0; i < runnerCount; i++) {
            final int runnerId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("[运动员" + runnerId + "] 准备就绪，等待发令枪");
                    startSignal.await(); // 等待起跑信号
                    System.out.println("[运动员" + runnerId + "] 开始跑！");
                    Thread.sleep((long) (Math.random() * 1000));
                    System.out.println("[运动员" + runnerId + "] 到达终点");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Runner-" + runnerId).start();
        }

        Thread.sleep(2000);
        System.out.println("\n[裁判] 预备...开始！\n");
        startSignal.countDown(); // 发令枪响

        Thread.sleep(2000);
        System.out.println("\n✅ CountDownLatch可以实现统一起跑");
    }

    /**
     * 演示3：超时等待
     */
    public static void demoTimeout() throws InterruptedException {
        System.out.println("\n========== 演示3：超时等待 ==========\n");

        CountDownLatch latch = new CountDownLatch(3);

        // 启动2个快速任务
        for (int i = 0; i < 2; i++) {
            final int taskId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("[快速任务" + taskId + "] 执行中...");
                    Thread.sleep(500);
                    System.out.println("[快速任务" + taskId + "] 完成");
                    latch.countDown();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        // 启动1个慢速任务
        new Thread(() -> {
            try {
                System.out.println("[慢速任务] 执行中...");
                Thread.sleep(5000); // 5秒
                System.out.println("[慢速任务] 完成");
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        System.out.println("\n[Main] 最多等待2秒...");
        boolean finished = latch.await(2, TimeUnit.SECONDS);

        if (finished) {
            System.out.println("[Main] 所有任务在2秒内完成");
        } else {
            System.out.println("[Main] 超时！还有 " + latch.getCount() + " 个任务未完成");
        }

        System.out.println("\n✅ await(timeout)可以避免无限期等待");
    }

    /**
     * 演示4：双重门栓（起跑+终点）
     */
    public static void demoDoubleBarrier() throws InterruptedException {
        System.out.println("\n========== 演示4：双重门栓 ==========\n");

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            final int workerId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("[工作线程" + workerId + "] 准备就绪");
                    startSignal.await(); // 等待开始信号

                    System.out.println("[工作线程" + workerId + "] 开始工作");
                    Thread.sleep((long) (Math.random() * 2000));
                    System.out.println("[工作线程" + workerId + "] 工作完成");

                    doneSignal.countDown(); // 通知完成
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Worker-" + workerId).start();
        }

        Thread.sleep(1000);
        System.out.println("\n[Main] 发送开始信号\n");
        startSignal.countDown();

        System.out.println("[Main] 等待所有工作完成...\n");
        doneSignal.await();
        System.out.println("[Main] 所有工作已完成！");

        System.out.println("\n✅ 可以使用多个CountDownLatch实现复杂协作");
    }

    /**
     * 演示5：实际应用 - 并行初始化
     */
    public static void demoParallelInit() throws InterruptedException {
        System.out.println("\n========== 演示5：并行初始化 ==========\n");

        class ApplicationContext {
            private CountDownLatch initLatch = new CountDownLatch(3);
            private boolean dbReady = false;
            private boolean cacheReady = false;
            private boolean configReady = false;

            public void init() throws InterruptedException {
                System.out.println("开始初始化应用...\n");

                // 初始化数据库
                new Thread(() -> {
                    try {
                        System.out.println("[DB] 初始化数据库连接池...");
                        Thread.sleep(1000);
                        dbReady = true;
                        System.out.println("[DB] 数据库初始化完成");
                        initLatch.countDown();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }, "DB-Init").start();

                // 初始化缓存
                new Thread(() -> {
                    try {
                        System.out.println("[Cache] 初始化缓存系统...");
                        Thread.sleep(800);
                        cacheReady = true;
                        System.out.println("[Cache] 缓存初始化完成");
                        initLatch.countDown();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }, "Cache-Init").start();

                // 加载配置
                new Thread(() -> {
                    try {
                        System.out.println("[Config] 加载配置文件...");
                        Thread.sleep(500);
                        configReady = true;
                        System.out.println("[Config] 配置加载完成");
                        initLatch.countDown();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }, "Config-Init").start();

                // 等待所有初始化完成
                initLatch.await();

                System.out.println("\n应用初始化完成！");
                System.out.println("  数据库: " + (dbReady ? "✅" : "❌"));
                System.out.println("  缓存:   " + (cacheReady ? "✅" : "❌"));
                System.out.println("  配置:   " + (configReady ? "✅" : "❌"));
            }
        }

        ApplicationContext context = new ApplicationContext();
        context.init();

        System.out.println("\n✅ CountDownLatch适合并行初始化场景");
    }

    /**
     * 演示6：实际应用 - 批量任务处理
     */
    public static void demoBatchProcessing() throws InterruptedException {
        System.out.println("\n========== 演示6：批量任务处理 ==========\n");

        int batchSize = 10;
        CountDownLatch latch = new CountDownLatch(batchSize);

        System.out.println("处理" + batchSize + "个任务...\n");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < batchSize; i++) {
            final int taskId = i + 1;
            new Thread(() -> {
                try {
                    // 模拟任务处理
                    Thread.sleep((long) (Math.random() * 500));
                    System.out.println("[任务" + taskId + "] 处理完成");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }, "Task-" + taskId).start();
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        System.out.println("\n所有任务处理完成！");
        System.out.println("总耗时: " + (endTime - startTime) + "ms");
        System.out.println("✅ 并行处理提高了效率");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== CountDownLatch总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 倒计时门栓：计数从N递减到0");
        System.out.println("   2. 一次性：计数到0后不能重置");
        System.out.println("   3. 阻塞等待：await()阻塞直到计数为0");
        System.out.println("   4. 超时等待：await(timeout)支持超时");

        System.out.println("\n📊 核心方法:");
        System.out.println("   countDown()  - 计数减1");
        System.out.println("   await()      - 等待计数到0");
        System.out.println("   await(timeout) - 超时等待");
        System.out.println("   getCount()   - 获取当前计数");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 主线程等待多个子线程完成");
        System.out.println("   ✅ 多个线程等待一个事件（起跑线）");
        System.out.println("   ✅ 并行初始化");
        System.out.println("   ✅ 批量任务处理");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. CountDownLatch是一次性的，不能重用");
        System.out.println("   2. 计数必须准确，否则可能永久阻塞");
        System.out.println("   3. countDown()通常放在finally中");
        System.out.println("   4. 考虑使用超时等待避免死锁");

        System.out.println("\n🔄 vs CyclicBarrier:");
        System.out.println("   CountDownLatch:");
        System.out.println("     - 一次性，不可重用");
        System.out.println("     - 主线程等待子线程");
        System.out.println("     - countDown()和await()可以在不同线程");
        System.out.println("   CyclicBarrier:");
        System.out.println("     - 可重用");
        System.out.println("     - 线程互相等待");
        System.out.println("     - 所有线程都调用await()");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              CountDownLatch演示                             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：主线程等待子线程
        demoWaitForThreads();

        // 演示2：起跑线
        demoStartSignal();

        // 演示3：超时等待
        demoTimeout();

        // 演示4：双重门栓
        demoDoubleBarrier();

        // 演示5：并行初始化
        demoParallelInit();

        // 演示6：批量任务处理
        demoBatchProcessing();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. CountDownLatch是倒计时门栓");
        System.out.println("2. 适合主线程等待子线程完成");
        System.out.println("3. 一次性使用，不可重置");
        System.out.println("4. 非常适合并行初始化和批量处理");
        System.out.println("===========================");
    }
}
