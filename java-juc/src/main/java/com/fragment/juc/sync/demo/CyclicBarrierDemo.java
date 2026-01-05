package com.fragment.juc.sync.demo;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * CyclicBarrier演示
 * 
 * 演示内容：
 * 1. 基本使用：线程互相等待
 * 2. 可重用性
 * 3. 栅栏动作
 * 4. 超时和异常处理
 * 5. 实际应用场景
 * 
 * @author huabin
 */
public class CyclicBarrierDemo {

    /**
     * 演示1：基本使用 - 线程互相等待
     */
    public static void demoBasicUsage() throws InterruptedException {
        System.out.println("\n========== 演示1：线程互相等待 ==========\n");

        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties);

        System.out.println("启动" + parties + "个线程...\n");

        for (int i = 0; i < parties; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("[线程" + threadId + "] 准备阶段1");
                    Thread.sleep((long) (Math.random() * 2000));
                    System.out.println("[线程" + threadId + "] 完成阶段1，等待其他线程...");

                    barrier.await(); // 等待所有线程到达

                    System.out.println("[线程" + threadId + "] 所有线程已到达，继续执行");
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Thread-" + threadId).start();
        }

        Thread.sleep(4000);
        System.out.println("\n✅ CyclicBarrier让线程互相等待");
    }

    /**
     * 演示2：可重用性
     */
    public static void demoReusable() throws InterruptedException {
        System.out.println("\n========== 演示2：可重用性 ==========\n");

        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties);

        for (int i = 0; i < parties; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    // 第一轮
                    System.out.println("[线程" + threadId + "] 第一轮工作");
                    Thread.sleep((long) (Math.random() * 1000));
                    System.out.println("[线程" + threadId + "] 第一轮完成，等待...");
                    barrier.await();

                    System.out.println("[线程" + threadId + "] 开始第二轮工作");
                    Thread.sleep((long) (Math.random() * 1000));
                    System.out.println("[线程" + threadId + "] 第二轮完成，等待...");
                    barrier.await();

                    System.out.println("[线程" + threadId + "] 开始第三轮工作");
                    Thread.sleep((long) (Math.random() * 1000));
                    System.out.println("[线程" + threadId + "] 第三轮完成");
                    barrier.await();

                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Thread-" + threadId).start();
        }

        Thread.sleep(6000);
        System.out.println("\n✅ CyclicBarrier可以重复使用");
    }

    /**
     * 演示3：栅栏动作（BarrierAction）
     */
    public static void demoBarrierAction() throws InterruptedException {
        System.out.println("\n========== 演示3：栅栏动作 ==========\n");

        int parties = 3;
        int[] counter = {0};

        // 栅栏动作：所有线程到达后执行
        Runnable barrierAction = () -> {
            counter[0]++;
            System.out.println("\n>>> 所有线程已到达！这是第" + counter[0] + "次汇合 <<<\n");
        };

        CyclicBarrier barrier = new CyclicBarrier(parties, barrierAction);

        for (int i = 0; i < parties; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    for (int round = 1; round <= 3; round++) {
                        System.out.println("[线程" + threadId + "] 第" + round + "轮工作");
                        Thread.sleep((long) (Math.random() * 1000));
                        System.out.println("[线程" + threadId + "] 第" + round + "轮完成");
                        barrier.await();
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Thread-" + threadId).start();
        }

        Thread.sleep(6000);
        System.out.println("✅ BarrierAction在所有线程到达后执行");
    }

    /**
     * 演示4：超时处理
     */
    public static void demoTimeout() throws InterruptedException {
        System.out.println("\n========== 演示4：超时处理 ==========\n");

        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties);

        // 启动2个正常线程
        for (int i = 0; i < 2; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("[线程" + threadId + "] 工作中...");
                    Thread.sleep(500);
                    System.out.println("[线程" + threadId + "] 到达栅栏，等待...");
                    barrier.await(2, TimeUnit.SECONDS); // 最多等待2秒
                    System.out.println("[线程" + threadId + "] 继续执行");
                } catch (Exception e) {
                    System.out.println("[线程" + threadId + "] 异常: " + e.getClass().getSimpleName());
                }
            }, "Thread-" + threadId).start();
        }

        // 启动1个慢速线程
        new Thread(() -> {
            try {
                System.out.println("[慢速线程] 工作中...");
                Thread.sleep(5000); // 5秒，超过等待时间
                System.out.println("[慢速线程] 到达栅栏");
                barrier.await();
            } catch (Exception e) {
                System.out.println("[慢速线程] 异常: " + e.getClass().getSimpleName());
            }
        }, "Slow-Thread").start();

        Thread.sleep(4000);
        System.out.println("\n⚠️  超时会导致栅栏破损，其他线程抛出BrokenBarrierException");
    }

    /**
     * 演示5：实际应用 - 多线程计算
     */
    public static void demoParallelComputation() throws InterruptedException {
        System.out.println("\n========== 演示5：多线程并行计算 ==========\n");

        int threadCount = 4;
        int[] results = new int[threadCount];
        int[] sum = {0};

        Runnable mergeAction = () -> {
            for (int result : results) {
                sum[0] += result;
            }
            System.out.println("\n>>> 合并结果: " + sum[0] + " <<<\n");
        };

        CyclicBarrier barrier = new CyclicBarrier(threadCount, mergeAction);

        System.out.println("启动" + threadCount + "个计算线程...\n");

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    // 第一轮计算
                    System.out.println("[线程" + threadId + "] 第一轮计算");
                    Thread.sleep((long) (Math.random() * 1000));
                    results[threadId] = (threadId + 1) * 10;
                    System.out.println("[线程" + threadId + "] 结果: " + results[threadId]);
                    barrier.await();

                    // 第二轮计算
                    System.out.println("[线程" + threadId + "] 第二轮计算");
                    Thread.sleep((long) (Math.random() * 1000));
                    results[threadId] = (threadId + 1) * 20;
                    System.out.println("[线程" + threadId + "] 结果: " + results[threadId]);
                    barrier.await();

                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Compute-" + threadId).start();
        }

        Thread.sleep(5000);
        System.out.println("最终结果: " + sum[0]);
        System.out.println("✅ CyclicBarrier适合多阶段并行计算");
    }

    /**
     * 演示6：实际应用 - 多人游戏
     */
    public static void demoMultiplayerGame() throws InterruptedException {
        System.out.println("\n========== 演示6：多人游戏 ==========\n");

        int playerCount = 4;

        Runnable roundAction = () -> {
            System.out.println("\n>>> 所有玩家准备就绪，游戏开始！<<<\n");
        };

        CyclicBarrier barrier = new CyclicBarrier(playerCount, roundAction);

        for (int i = 0; i < playerCount; i++) {
            final int playerId = i + 1;
            new Thread(() -> {
                try {
                    for (int round = 1; round <= 3; round++) {
                        System.out.println("[玩家" + playerId + "] 第" + round + "轮加载中...");
                        Thread.sleep((long) (Math.random() * 2000));
                        System.out.println("[玩家" + playerId + "] 第" + round + "轮准备完成");
                        barrier.await();

                        System.out.println("[玩家" + playerId + "] 第" + round + "轮游戏中...");
                        Thread.sleep((long) (Math.random() * 1000));
                        System.out.println("[玩家" + playerId + "] 第" + round + "轮结束");
                        barrier.await();
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }, "Player-" + playerId).start();
        }

        Thread.sleep(15000);
        System.out.println("✅ CyclicBarrier适合多人游戏同步");
    }

    /**
     * 演示7：reset()重置栅栏
     */
    public static void demoReset() throws InterruptedException {
        System.out.println("\n========== 演示7：重置栅栏 ==========\n");

        int parties = 2;
        CyclicBarrier barrier = new CyclicBarrier(parties);

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("[线程1] 等待...");
                barrier.await();
                System.out.println("[线程1] 继续执行");
            } catch (Exception e) {
                System.out.println("[线程1] 异常: " + e.getClass().getSimpleName());
            }
        }, "Thread-1");

        t1.start();
        Thread.sleep(1000);

        System.out.println("\n[Main] 重置栅栏");
        barrier.reset();

        Thread.sleep(1000);
        System.out.println("\n⚠️  reset()会导致等待的线程抛出BrokenBarrierException");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== CyclicBarrier总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 循环栅栏：可以重复使用");
        System.out.println("   2. 互相等待：所有线程都到达才继续");
        System.out.println("   3. 栅栏动作：到达后可执行回调");
        System.out.println("   4. 可重置：reset()重置栅栏");

        System.out.println("\n📊 核心方法:");
        System.out.println("   await()        - 等待其他线程");
        System.out.println("   await(timeout) - 超时等待");
        System.out.println("   reset()        - 重置栅栏");
        System.out.println("   getParties()   - 获取参与线程数");
        System.out.println("   getNumberWaiting() - 获取等待线程数");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 多线程协同工作");
        System.out.println("   ✅ 多阶段并行计算");
        System.out.println("   ✅ 多人游戏同步");
        System.out.println("   ✅ 需要重复使用的场景");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 所有线程都必须调用await()");
        System.out.println("   2. 超时会导致栅栏破损");
        System.out.println("   3. reset()会中断等待的线程");
        System.out.println("   4. BarrierAction由最后到达的线程执行");

        System.out.println("\n🔄 vs CountDownLatch:");
        System.out.println("   CyclicBarrier:");
        System.out.println("     - 可重用");
        System.out.println("     - 所有线程互相等待");
        System.out.println("     - 所有线程都调用await()");
        System.out.println("     - 支持栅栏动作");
        System.out.println("   CountDownLatch:");
        System.out.println("     - 一次性");
        System.out.println("     - 主线程等待子线程");
        System.out.println("     - countDown()和await()分离");
        System.out.println("     - 不支持回调");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              CyclicBarrier演示                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：可重用性
        demoReusable();

        // 演示3：栅栏动作
        demoBarrierAction();

        // 演示4：超时处理
        demoTimeout();

        // 演示5：并行计算
        demoParallelComputation();

        // 演示6：多人游戏
        demoMultiplayerGame();

        // 演示7：重置
        demoReset();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. CyclicBarrier是循环栅栏，可重复使用");
        System.out.println("2. 适合多线程协同工作");
        System.out.println("3. 支持栅栏动作（BarrierAction）");
        System.out.println("4. 所有线程都必须调用await()");
        System.out.println("===========================");
    }
}
