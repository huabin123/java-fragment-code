package com.fragment.juc.sync.demo;

import java.util.concurrent.Phaser;

/**
 * Phaser分阶段器演示
 * 
 * 演示内容：
 * 1. 基本使用：多阶段同步
 * 2. 动态注册/注销
 * 3. 阶段动作
 * 4. 实际应用场景
 * 
 * @author huabin
 */
public class PhaserDemo {

    /**
     * 演示1：基本使用 - 多阶段同步
     */
    public static void demoBasicUsage() throws InterruptedException {
        System.out.println("\n========== 演示1：多阶段同步 ==========\n");

        int parties = 3;
        Phaser phaser = new Phaser(parties);

        System.out.println("启动" + parties + "个线程，执行3个阶段...\n");

        for (int i = 0; i < parties; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                // 阶段1
                System.out.println("[线程" + threadId + "] 阶段1工作");
                sleep(random(1000));
                System.out.println("[线程" + threadId + "] 阶段1完成");
                phaser.arriveAndAwaitAdvance(); // 到达并等待

                // 阶段2
                System.out.println("[线程" + threadId + "] 阶段2工作");
                sleep(random(1000));
                System.out.println("[线程" + threadId + "] 阶段2完成");
                phaser.arriveAndAwaitAdvance();

                // 阶段3
                System.out.println("[线程" + threadId + "] 阶段3工作");
                sleep(random(1000));
                System.out.println("[线程" + threadId + "] 阶段3完成");
                phaser.arriveAndAwaitAdvance();

                System.out.println("[线程" + threadId + "] 所有阶段完成");
            }, "Thread-" + threadId).start();
        }

        Thread.sleep(6000);
        System.out.println("\n✅ Phaser支持多阶段同步");
    }

    /**
     * 演示2：动态注册和注销
     */
    public static void demoDynamicParties() throws InterruptedException {
        System.out.println("\n========== 演示2：动态注册和注销 ==========\n");

        Phaser phaser = new Phaser(1); // 主线程注册

        System.out.println("初始参与者数: " + phaser.getRegisteredParties() + "\n");

        // 动态注册3个线程
        for (int i = 0; i < 3; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                phaser.register(); // 动态注册
                System.out.println("[线程" + threadId + "] 注册 (参与者: " + 
                                 phaser.getRegisteredParties() + ")");

                // 阶段1
                System.out.println("[线程" + threadId + "] 阶段1工作");
                sleep(random(1000));
                phaser.arriveAndAwaitAdvance();

                // 阶段2
                System.out.println("[线程" + threadId + "] 阶段2工作");
                sleep(random(1000));
                
                if (threadId == 2) {
                    System.out.println("[线程" + threadId + "] 提前退出");
                    phaser.arriveAndDeregister(); // 到达并注销
                } else {
                    phaser.arriveAndAwaitAdvance();
                    
                    // 阶段3
                    System.out.println("[线程" + threadId + "] 阶段3工作");
                    sleep(random(1000));
                    phaser.arriveAndAwaitAdvance();
                }

                System.out.println("[线程" + threadId + "] 完成");
            }, "Thread-" + threadId).start();

            Thread.sleep(100);
        }

        // 主线程参与同步
        phaser.arriveAndAwaitAdvance(); // 阶段1
        System.out.println("\n[Main] 阶段1完成\n");

        phaser.arriveAndAwaitAdvance(); // 阶段2
        System.out.println("\n[Main] 阶段2完成 (参与者: " + 
                         phaser.getRegisteredParties() + ")\n");

        phaser.arriveAndAwaitAdvance(); // 阶段3
        System.out.println("\n[Main] 阶段3完成\n");

        phaser.arriveAndDeregister(); // 主线程注销

        System.out.println("✅ Phaser支持动态注册和注销");
    }

    /**
     * 演示3：自定义阶段动作
     */
    public static void demoOnAdvance() throws InterruptedException {
        System.out.println("\n========== 演示3：自定义阶段动作 ==========\n");

        Phaser phaser = new Phaser(3) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("\n>>> 阶段" + (phase + 1) + "完成！" +
                                 " (参与者: " + registeredParties + ") <<<\n");
                return phase >= 2; // 3个阶段后终止
            }
        };

        for (int i = 0; i < 3; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                while (!phaser.isTerminated()) {
                    int phase = phaser.getPhase();
                    System.out.println("[线程" + threadId + "] 阶段" + (phase + 1) + "工作");
                    sleep(random(1000));
                    phaser.arriveAndAwaitAdvance();
                }
                System.out.println("[线程" + threadId + "] Phaser已终止");
            }, "Thread-" + threadId).start();
        }

        Thread.sleep(6000);
        System.out.println("✅ onAdvance()可以自定义阶段完成动作");
    }

    /**
     * 演示4：arrive()系列方法
     */
    public static void demoArriveMethods() throws InterruptedException {
        System.out.println("\n========== 演示4：arrive()系列方法 ==========\n");

        Phaser phaser = new Phaser(3);

        // 线程1：arriveAndAwaitAdvance() - 到达并等待
        new Thread(() -> {
            System.out.println("[线程1] arriveAndAwaitAdvance() - 到达并等待");
            phaser.arriveAndAwaitAdvance();
            System.out.println("[线程1] 继续执行");
        }, "Thread-1").start();

        Thread.sleep(500);

        // 线程2：arrive() - 到达但不等待
        new Thread(() -> {
            System.out.println("[线程2] arrive() - 到达但不等待");
            phaser.arrive();
            System.out.println("[线程2] 立即继续执行");
        }, "Thread-2").start();

        Thread.sleep(500);

        // 线程3：arriveAndDeregister() - 到达并注销
        new Thread(() -> {
            System.out.println("[线程3] arriveAndDeregister() - 到达并注销");
            phaser.arriveAndDeregister();
            System.out.println("[线程3] 已注销，不再参与后续阶段");
        }, "Thread-3").start();

        Thread.sleep(2000);
        System.out.println("\n📊 三种到达方法:");
        System.out.println("  arriveAndAwaitAdvance() - 到达并等待其他线程");
        System.out.println("  arrive()                - 到达但不等待");
        System.out.println("  arriveAndDeregister()   - 到达并注销");
    }

    /**
     * 演示5：实际应用 - 多阶段任务
     */
    public static void demoMultiPhaseTask() throws InterruptedException {
        System.out.println("\n========== 演示5：多阶段任务 ==========\n");

        int workerCount = 3;
        Phaser phaser = new Phaser(workerCount) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                String[] phases = {"初始化", "数据加载", "数据处理", "结果汇总"};
                System.out.println("\n>>> " + phases[phase] + "阶段完成 <<<\n");
                return phase >= 3; // 4个阶段后终止
            }
        };

        for (int i = 0; i < workerCount; i++) {
            final int workerId = i + 1;
            new Thread(() -> {
                // 阶段1：初始化
                System.out.println("[工作线程" + workerId + "] 初始化...");
                sleep(random(1000));
                phaser.arriveAndAwaitAdvance();

                // 阶段2：数据加载
                System.out.println("[工作线程" + workerId + "] 加载数据...");
                sleep(random(1000));
                phaser.arriveAndAwaitAdvance();

                // 阶段3：数据处理
                System.out.println("[工作线程" + workerId + "] 处理数据...");
                sleep(random(1000));
                phaser.arriveAndAwaitAdvance();

                // 阶段4：结果汇总
                System.out.println("[工作线程" + workerId + "] 汇总结果...");
                sleep(random(1000));
                phaser.arriveAndAwaitAdvance();

                System.out.println("[工作线程" + workerId + "] 任务完成");
            }, "Worker-" + workerId).start();
        }

        Thread.sleep(8000);
        System.out.println("✅ Phaser适合多阶段任务协调");
    }

    /**
     * 演示6：实际应用 - 迭代计算
     */
    public static void demoIterativeComputation() throws InterruptedException {
        System.out.println("\n========== 演示6：迭代计算 ==========\n");

        int threadCount = 4;
        double[] results = new double[threadCount];
        double[] sum = {0.0};

        Phaser phaser = new Phaser(threadCount) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                // 汇总结果
                sum[0] = 0;
                for (double result : results) {
                    sum[0] += result;
                }
                System.out.println(">>> 第" + (phase + 1) + "轮迭代完成，总和: " + 
                                 String.format("%.2f", sum[0]) + " <<<\n");
                return phase >= 2; // 3轮迭代
            }
        };

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int iteration = 0; iteration < 3; iteration++) {
                    // 计算
                    results[threadId] = Math.random() * 100;
                    System.out.println("[线程" + threadId + "] 第" + (iteration + 1) + 
                                     "轮计算: " + String.format("%.2f", results[threadId]));
                    
                    phaser.arriveAndAwaitAdvance();
                    
                    sleep(500);
                }
            }, "Compute-" + i).start();
        }

        Thread.sleep(5000);
        System.out.println("最终结果: " + String.format("%.2f", sum[0]));
        System.out.println("✅ Phaser适合迭代计算场景");
    }

    /**
     * 演示7：Phaser层级结构
     */
    public static void demoTieredPhaser() throws InterruptedException {
        System.out.println("\n========== 演示7：Phaser层级结构 ==========\n");

        // 根Phaser
        Phaser root = new Phaser(2) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println(">>> 根Phaser - 阶段" + phase + "完成 <<<");
                return false;
            }
        };

        // 子Phaser1
        Phaser child1 = new Phaser(root, 2) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("  >> 子Phaser1 - 阶段" + phase + "完成");
                return false;
            }
        };

        // 子Phaser2
        Phaser child2 = new Phaser(root, 2) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("  >> 子Phaser2 - 阶段" + phase + "完成");
                return false;
            }
        };

        // 子Phaser1的线程
        for (int i = 0; i < 2; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                System.out.println("[组1-线程" + threadId + "] 工作");
                sleep(random(1000));
                child1.arriveAndAwaitAdvance();
                System.out.println("[组1-线程" + threadId + "] 完成");
            }, "Group1-Thread-" + threadId).start();
        }

        // 子Phaser2的线程
        for (int i = 0; i < 2; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                System.out.println("[组2-线程" + threadId + "] 工作");
                sleep(random(1500));
                child2.arriveAndAwaitAdvance();
                System.out.println("[组2-线程" + threadId + "] 完成");
            }, "Group2-Thread-" + threadId).start();
        }

        Thread.sleep(3000);
        System.out.println("\n✅ Phaser支持层级结构，适合大规模同步");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== Phaser总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 多阶段：支持多个阶段的同步");
        System.out.println("   2. 动态调整：可以动态注册/注销参与者");
        System.out.println("   3. 可重用：可以重复使用");
        System.out.println("   4. 层级结构：支持树形结构");
        System.out.println("   5. 灵活终止：可以通过onAdvance()控制终止");

        System.out.println("\n📊 核心方法:");
        System.out.println("   register()              - 注册参与者");
        System.out.println("   arriveAndAwaitAdvance() - 到达并等待");
        System.out.println("   arrive()                - 到达但不等待");
        System.out.println("   arriveAndDeregister()   - 到达并注销");
        System.out.println("   getPhase()              - 获取当前阶段");
        System.out.println("   getRegisteredParties()  - 获取参与者数");
        System.out.println("   isTerminated()          - 是否已终止");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 多阶段任务协调");
        System.out.println("   ✅ 迭代计算");
        System.out.println("   ✅ 动态参与者场景");
        System.out.println("   ✅ 大规模并行计算");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. onAdvance()返回true会终止Phaser");
        System.out.println("   2. 注册和注销要配对");
        System.out.println("   3. 层级结构要合理设计");
        System.out.println("   4. 注意内存泄漏（未注销）");

        System.out.println("\n🔄 vs CyclicBarrier:");
        System.out.println("   Phaser:");
        System.out.println("     - 支持多阶段");
        System.out.println("     - 动态注册/注销");
        System.out.println("     - 支持层级结构");
        System.out.println("     - 更灵活但更复杂");
        System.out.println("   CyclicBarrier:");
        System.out.println("     - 固定参与者数量");
        System.out.println("     - 简单易用");
        System.out.println("     - 适合简单场景");

        System.out.println("\n💡 选择建议:");
        System.out.println("   - 简单场景：使用CyclicBarrier");
        System.out.println("   - 多阶段：使用Phaser");
        System.out.println("   - 动态参与者：使用Phaser");
        System.out.println("   - 大规模并行：使用Phaser层级结构");

        System.out.println("===========================");
    }

    // 工具方法
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static long random(int max) {
        return (long) (Math.random() * max);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              Phaser分阶段器演示                             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：动态注册/注销
        demoDynamicParties();

        // 演示3：自定义阶段动作
        demoOnAdvance();

        // 演示4：arrive()方法
        demoArriveMethods();

        // 演示5：多阶段任务
        demoMultiPhaseTask();

        // 演示6：迭代计算
        demoIterativeComputation();

        // 演示7：层级结构
        demoTieredPhaser();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. Phaser是最灵活的同步工具");
        System.out.println("2. 支持多阶段、动态参与者、层级结构");
        System.out.println("3. 适合复杂的多阶段任务协调");
        System.out.println("4. 简单场景建议使用CyclicBarrier");
        System.out.println("===========================");
    }
}
