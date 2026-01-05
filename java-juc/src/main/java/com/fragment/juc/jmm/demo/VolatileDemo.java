package com.fragment.juc.jmm.demo;

/**
 * volatile关键字综合演示
 * 
 * 演示内容：
 * 1. volatile的典型应用场景
 * 2. volatile的正确使用
 * 3. volatile的错误使用
 * 
 * @author huabin
 */
public class VolatileDemo {

    /**
     * 场景1：状态标志（volatile的典型应用）
     */
    static class StatusFlag {
        private volatile boolean shutdown = false;

        public void shutdown() {
            System.out.println("[Main] 发送关闭信号");
            shutdown = true;
        }

        public void doWork() {
            System.out.println("[Worker] 开始工作...");
            int count = 0;
            while (!shutdown) {
                count++;
                // 模拟工作
                if (count % 100000000 == 0) {
                    System.out.println("[Worker] 已处理 " + count + " 次");
                }
            }
            System.out.println("[Worker] 检测到关闭信号，停止工作");
        }
    }

    /**
     * 场景2：一次性安全发布
     */
    static class SafePublication {
        private volatile Configuration config;

        static class Configuration {
            private final String host;
            private final int port;

            public Configuration(String host, int port) {
                this.host = host;
                this.port = port;
            }

            @Override
            public String toString() {
                return "Configuration{host='" + host + "', port=" + port + "}";
            }
        }

        public void updateConfig(String host, int port) {
            // volatile写保证config完全初始化后才对其他线程可见
            config = new Configuration(host, port);
            System.out.println("[Updater] 配置已更新: " + config);
        }

        public Configuration getConfig() {
            // volatile读保证能看到最新的config
            return config;
        }
    }

    /**
     * 场景3：独立观察（每次读写都是独立的）
     */
    static class IndependentObservation {
        private volatile long lastUpdateTime;

        public void update() {
            lastUpdateTime = System.currentTimeMillis();
            System.out.println("[Updater] 更新时间: " + lastUpdateTime);
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
    }

    /**
     * 场景4：volatile的错误使用 - 不能保证原子性
     */
    static class WrongUsage {
        private volatile int count = 0;

        // ❌ 错误：volatile不能保证count++的原子性
        public void increment() {
            count++; // 读-改-写，不是原子操作
        }

        public int getCount() {
            return count;
        }
    }

    /**
     * 演示1：状态标志
     */
    public static void demoStatusFlag() throws InterruptedException {
        System.out.println("\n========== 演示1：状态标志 ==========\n");

        StatusFlag flag = new StatusFlag();

        Thread workerThread = new Thread(flag::doWork, "Worker-Thread");
        workerThread.start();

        // 让worker运行一段时间
        Thread.sleep(1000);

        // 发送关闭信号
        flag.shutdown();

        // 等待worker结束
        workerThread.join(5000);

        if (workerThread.isAlive()) {
            System.out.println("⚠️  Worker线程未能正常结束");
        } else {
            System.out.println("✅ Worker线程正常结束");
        }
    }

    /**
     * 演示2：一次性安全发布
     */
    public static void demoSafePublication() throws InterruptedException {
        System.out.println("\n========== 演示2：一次性安全发布 ==========\n");

        SafePublication pub = new SafePublication();

        // 更新线程
        Thread updater = new Thread(() -> {
            pub.updateConfig("localhost", 8080);
        }, "Updater-Thread");

        // 读取线程
        Thread reader = new Thread(() -> {
            try {
                Thread.sleep(100); // 确保updater先执行
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            SafePublication.Configuration config = pub.getConfig();
            System.out.println("[Reader] 读取到配置: " + config);
        }, "Reader-Thread");

        updater.start();
        reader.start();

        updater.join();
        reader.join();

        System.out.println("✅ volatile保证了配置的安全发布");
    }

    /**
     * 演示3：独立观察
     */
    public static void demoIndependentObservation() throws InterruptedException {
        System.out.println("\n========== 演示3：独立观察 ==========\n");

        IndependentObservation obs = new IndependentObservation();

        // 更新线程
        Thread updater = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                obs.update();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Updater-Thread");

        // 观察线程
        Thread observer = new Thread(() -> {
            long lastSeen = 0;
            for (int i = 0; i < 10; i++) {
                long current = obs.getLastUpdateTime();
                if (current != lastSeen) {
                    System.out.println("[Observer] 观察到时间变化: " + current);
                    lastSeen = current;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Observer-Thread");

        updater.start();
        observer.start();

        updater.join();
        observer.join();

        System.out.println("✅ volatile保证了时间戳的可见性");
    }

    /**
     * 演示4：volatile的错误使用
     */
    public static void demoWrongUsage() throws InterruptedException {
        System.out.println("\n========== 演示4：volatile的错误使用 ==========\n");

        WrongUsage wrong = new WrongUsage();
        int threadCount = 10;
        int incrementPerThread = 1000;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    wrong.increment();
                }
            }, "Thread-" + i);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        int expected = threadCount * incrementPerThread;
        int actual = wrong.getCount();

        System.out.println("预期结果: " + expected);
        System.out.println("实际结果: " + actual);
        System.out.println("数据丢失: " + (expected - actual));

        if (actual == expected) {
            System.out.println("✅ 结果正确（运气好）");
        } else {
            System.out.println("❌ 结果错误 - volatile不能保证原子性");
        }
    }

    /**
     * 总结volatile的使用规则
     */
    public static void summarizeVolatileRules() {
        System.out.println("\n========== volatile使用规则总结 ==========");
        
        System.out.println("\n✅ 适合使用volatile的场景:");
        System.out.println("  1. 状态标志（boolean flag）");
        System.out.println("  2. 一次性安全发布（发布不可变对象）");
        System.out.println("  3. 独立观察（读写操作互不依赖）");
        System.out.println("  4. 双重检查锁（配合synchronized）");
        
        System.out.println("\n❌ 不适合使用volatile的场景:");
        System.out.println("  1. 复合操作（如i++）");
        System.out.println("  2. 不变式约束（如范围检查）");
        System.out.println("  3. 需要原子性的场景");
        
        System.out.println("\nvolatile的特性:");
        System.out.println("  ✅ 保证可见性");
        System.out.println("  ✅ 保证有序性（禁止重排序）");
        System.out.println("  ❌ 不保证原子性");
        
        System.out.println("\nvolatile vs synchronized:");
        System.out.println("  volatile:");
        System.out.println("    - 轻量级，性能好");
        System.out.println("    - 只能修饰变量");
        System.out.println("    - 不保证原子性");
        System.out.println("  synchronized:");
        System.out.println("    - 重量级，性能差");
        System.out.println("    - 可以修饰方法和代码块");
        System.out.println("    - 保证原子性");
        
        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              volatile关键字综合演示                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：状态标志
        demoStatusFlag();

        // 演示2：一次性安全发布
        demoSafePublication();

        // 演示3：独立观察
        demoIndependentObservation();

        // 演示4：错误使用
        demoWrongUsage();

        // 总结
        summarizeVolatileRules();

        System.out.println("\n" + "===========================");
        System.out.println("学习要点：");
        System.out.println("1. volatile适用于简单的状态标志");
        System.out.println("2. volatile不能替代synchronized");
        System.out.println("3. 理解volatile的适用场景很重要");
        System.out.println("4. 复合操作必须使用synchronized或Atomic类");
        System.out.println("===========================");
    }
}
