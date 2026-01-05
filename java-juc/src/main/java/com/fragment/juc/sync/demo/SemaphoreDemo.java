package com.fragment.juc.sync.demo;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Semaphore信号量演示
 * 
 * 演示内容：
 * 1. 基本使用：控制并发数量
 * 2. 公平vs非公平
 * 3. 一次获取多个许可
 * 4. 实际应用：资源池、限流
 * 
 * @author huabin
 */
public class SemaphoreDemo {

    /**
     * 演示1：基本使用 - 控制并发数量
     */
    public static void demoBasicUsage() throws InterruptedException {
        System.out.println("\n========== 演示1：控制并发数量 ==========\n");

        // 只允许3个线程同时访问
        Semaphore semaphore = new Semaphore(3);

        System.out.println("启动10个线程，但只允许3个同时执行...\n");

        for (int i = 0; i < 10; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("[线程" + threadId + "] 尝试获取许可...");
                    semaphore.acquire(); // 获取许可
                    System.out.println("[线程" + threadId + "] 获取许可成功，开始工作 (可用许可: " + 
                                     semaphore.availablePermits() + ")");

                    Thread.sleep(2000); // 模拟工作

                    System.out.println("[线程" + threadId + "] 工作完成，释放许可");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    semaphore.release(); // 释放许可
                }
            }, "Thread-" + threadId).start();

            Thread.sleep(100);
        }

        Thread.sleep(8000);
        System.out.println("\n✅ Semaphore可以控制并发数量");
    }

    /**
     * 演示2：公平vs非公平
     */
    public static void demoFairness() throws InterruptedException {
        System.out.println("\n========== 演示2：公平vs非公平 ==========\n");

        // 非公平信号量
        System.out.println("非公平信号量:");
        Semaphore unfairSemaphore = new Semaphore(1, false);
        testSemaphore(unfairSemaphore);

        Thread.sleep(2000);

        // 公平信号量
        System.out.println("\n公平信号量:");
        Semaphore fairSemaphore = new Semaphore(1, true);
        testSemaphore(fairSemaphore);

        Thread.sleep(2000);
        System.out.println("\n📊 公平信号量保证FIFO，但性能略低");
    }

    private static void testSemaphore(Semaphore semaphore) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    semaphore.acquire();
                    System.out.println("  [线程" + threadId + "] 获取许可");
                    Thread.sleep(100);
                    semaphore.release();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Thread-" + threadId).start();
            Thread.sleep(10);
        }
    }

    /**
     * 演示3：tryAcquire() - 非阻塞获取
     */
    public static void demoTryAcquire() throws InterruptedException {
        System.out.println("\n========== 演示3：tryAcquire()非阻塞获取 ==========\n");

        Semaphore semaphore = new Semaphore(2);

        // 线程1和2：持有许可
        for (int i = 0; i < 2; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    semaphore.acquire();
                    System.out.println("[线程" + threadId + "] 获取许可，持有3秒");
                    Thread.sleep(3000);
                    semaphore.release();
                    System.out.println("[线程" + threadId + "] 释放许可");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Thread-" + threadId).start();
        }

        Thread.sleep(500);

        // 线程3：尝试获取（立即返回）
        new Thread(() -> {
            System.out.println("[线程3] 尝试获取许可（不等待）...");
            if (semaphore.tryAcquire()) {
                try {
                    System.out.println("[线程3] 获取成功");
                } finally {
                    semaphore.release();
                }
            } else {
                System.out.println("[线程3] 获取失败，立即返回");
            }
        }, "Thread-3").start();

        Thread.sleep(500);

        // 线程4：尝试获取（等待1秒）
        new Thread(() -> {
            System.out.println("[线程4] 尝试获取许可（最多等待1秒）...");
            try {
                if (semaphore.tryAcquire(1, TimeUnit.SECONDS)) {
                    try {
                        System.out.println("[线程4] 获取成功");
                    } finally {
                        semaphore.release();
                    }
                } else {
                    System.out.println("[线程4] 等待1秒后仍未获取到");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-4").start();

        Thread.sleep(5000);
        System.out.println("\n✅ tryAcquire()可以避免无限期等待");
    }

    /**
     * 演示4：一次获取多个许可
     */
    public static void demoMultiplePermits() throws InterruptedException {
        System.out.println("\n========== 演示4：一次获取多个许可 ==========\n");

        Semaphore semaphore = new Semaphore(5);

        System.out.println("初始许可数: " + semaphore.availablePermits() + "\n");

        // 获取2个许可
        new Thread(() -> {
            try {
                System.out.println("[线程1] 尝试获取2个许可...");
                semaphore.acquire(2);
                System.out.println("[线程1] 获取2个许可成功 (剩余: " + 
                                 semaphore.availablePermits() + ")");
                Thread.sleep(2000);
                semaphore.release(2);
                System.out.println("[线程1] 释放2个许可 (剩余: " + 
                                 semaphore.availablePermits() + ")");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-1").start();

        Thread.sleep(500);

        // 获取3个许可
        new Thread(() -> {
            try {
                System.out.println("[线程2] 尝试获取3个许可...");
                semaphore.acquire(3);
                System.out.println("[线程2] 获取3个许可成功 (剩余: " + 
                                 semaphore.availablePermits() + ")");
                Thread.sleep(2000);
                semaphore.release(3);
                System.out.println("[线程2] 释放3个许可 (剩余: " + 
                                 semaphore.availablePermits() + ")");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-2").start();

        Thread.sleep(5000);
        System.out.println("\n✅ 可以一次获取/释放多个许可");
    }

    /**
     * 演示5：实际应用 - 数据库连接池
     */
    public static void demoConnectionPool() throws InterruptedException {
        System.out.println("\n========== 演示5：数据库连接池 ==========\n");

        class ConnectionPool {
            private final Semaphore semaphore;
            private final int poolSize;

            public ConnectionPool(int poolSize) {
                this.poolSize = poolSize;
                this.semaphore = new Semaphore(poolSize);
            }

            public void executeQuery(String query) {
                try {
                    System.out.println("[" + Thread.currentThread().getName() + 
                                     "] 等待连接... (可用: " + semaphore.availablePermits() + 
                                     "/" + poolSize + ")");
                    semaphore.acquire();
                    System.out.println("[" + Thread.currentThread().getName() + 
                                     "] 获取连接，执行: " + query);
                    Thread.sleep((long) (Math.random() * 2000)); // 模拟查询
                    System.out.println("[" + Thread.currentThread().getName() + 
                                     "] 查询完成，释放连接");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    semaphore.release();
                }
            }
        }

        ConnectionPool pool = new ConnectionPool(3);

        System.out.println("连接池大小: 3\n");
        System.out.println("模拟10个并发查询...\n");

        for (int i = 0; i < 10; i++) {
            final int queryId = i + 1;
            new Thread(() -> {
                pool.executeQuery("SELECT * FROM users WHERE id=" + queryId);
            }, "Query-" + queryId).start();

            Thread.sleep(100);
        }

        Thread.sleep(8000);
        System.out.println("\n✅ Semaphore非常适合实现资源池");
    }

    /**
     * 演示6：实际应用 - 限流器
     */
    public static void demoRateLimiter() throws InterruptedException {
        System.out.println("\n========== 演示6：限流器 ==========\n");

        class RateLimiter {
            private final Semaphore semaphore;
            private final int maxRequests;

            public RateLimiter(int maxRequests) {
                this.maxRequests = maxRequests;
                this.semaphore = new Semaphore(maxRequests);

                // 定时释放许可
                new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(1000); // 每秒释放
                            int released = maxRequests - semaphore.availablePermits();
                            if (released > 0) {
                                semaphore.release(released);
                                System.out.println("  [限流器] 重置许可: " + released);
                            }
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "RateLimiter").start();
            }

            public boolean tryAcquire() {
                return semaphore.tryAcquire();
            }
        }

        RateLimiter limiter = new RateLimiter(5);

        System.out.println("限流规则: 每秒最多5个请求\n");

        // 模拟请求
        for (int i = 0; i < 20; i++) {
            final int requestId = i + 1;
            new Thread(() -> {
                if (limiter.tryAcquire()) {
                    System.out.println("[请求" + requestId + "] ✅ 通过");
                } else {
                    System.out.println("[请求" + requestId + "] ❌ 被限流");
                }
            }, "Request-" + requestId).start();

            Thread.sleep(100);
        }

        Thread.sleep(5000);
        System.out.println("\n✅ Semaphore可以实现简单的限流器");
    }

    /**
     * 演示7：实际应用 - 停车场
     */
    public static void demoParkingLot() throws InterruptedException {
        System.out.println("\n========== 演示7：停车场 ==========\n");

        class ParkingLot {
            private final Semaphore semaphore;
            private final int capacity;

            public ParkingLot(int capacity) {
                this.capacity = capacity;
                this.semaphore = new Semaphore(capacity);
            }

            public void park(String carId) {
                try {
                    System.out.println("[" + carId + "] 到达停车场 (空位: " + 
                                     semaphore.availablePermits() + "/" + capacity + ")");
                    semaphore.acquire();
                    System.out.println("[" + carId + "] 停车成功");
                    Thread.sleep((long) (Math.random() * 3000)); // 停车时间
                    System.out.println("[" + carId + "] 离开停车场");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    semaphore.release();
                }
            }
        }

        ParkingLot parkingLot = new ParkingLot(3);

        System.out.println("停车场容量: 3个车位\n");

        for (int i = 0; i < 8; i++) {
            final String carId = "Car-" + (i + 1);
            new Thread(() -> {
                parkingLot.park(carId);
            }, carId).start();

            Thread.sleep(500);
        }

        Thread.sleep(10000);
        System.out.println("\n✅ Semaphore适合实现停车场等场景");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== Semaphore总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 信号量：控制并发访问数量");
        System.out.println("   2. 许可证：acquire()获取，release()释放");
        System.out.println("   3. 公平性：支持公平和非公平模式");
        System.out.println("   4. 可重用：可以重复获取和释放");

        System.out.println("\n📊 核心方法:");
        System.out.println("   acquire()      - 获取1个许可（阻塞）");
        System.out.println("   acquire(n)     - 获取n个许可");
        System.out.println("   tryAcquire()   - 尝试获取（非阻塞）");
        System.out.println("   tryAcquire(timeout) - 超时获取");
        System.out.println("   release()      - 释放1个许可");
        System.out.println("   release(n)     - 释放n个许可");
        System.out.println("   availablePermits() - 可用许可数");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 资源池（数据库连接池、线程池）");
        System.out.println("   ✅ 限流器");
        System.out.println("   ✅ 停车场");
        System.out.println("   ✅ 控制并发数量");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. acquire()和release()要配对");
        System.out.println("   2. release()通常放在finally中");
        System.out.println("   3. 可以在不同线程中acquire和release");
        System.out.println("   4. 注意许可数量的管理");

        System.out.println("\n🔄 vs Lock:");
        System.out.println("   Semaphore:");
        System.out.println("     - 控制并发数量（N个）");
        System.out.println("     - 可以在不同线程获取和释放");
        System.out.println("     - 适合资源池场景");
        System.out.println("   Lock:");
        System.out.println("     - 互斥锁（1个）");
        System.out.println("     - 必须在同一线程获取和释放");
        System.out.println("     - 适合临界区保护");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              Semaphore信号量演示                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：公平性
        demoFairness();

        // 演示3：tryAcquire
        demoTryAcquire();

        // 演示4：多个许可
        demoMultiplePermits();

        // 演示5：连接池
        demoConnectionPool();

        // 演示6：限流器
        demoRateLimiter();

        // 演示7：停车场
        demoParkingLot();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. Semaphore用于控制并发访问数量");
        System.out.println("2. 非常适合实现资源池和限流器");
        System.out.println("3. 支持公平和非公平模式");
        System.out.println("4. acquire()和release()要配对使用");
        System.out.println("===========================");
    }
}
