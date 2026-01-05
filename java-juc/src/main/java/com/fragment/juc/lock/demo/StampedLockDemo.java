package com.fragment.juc.lock.demo;

import java.util.concurrent.locks.StampedLock;

/**
 * StampedLock乐观锁演示
 * 
 * 演示内容：
 * 1. 乐观读锁
 * 2. 悲观读锁
 * 3. 写锁
 * 4. 锁转换
 * 5. 性能对比
 * 
 * @author huabin
 */
public class StampedLockDemo {

    /**
     * 演示1：乐观读锁
     */
    public static void demoOptimisticRead() throws InterruptedException {
        System.out.println("\n========== 演示1：乐观读锁 ==========\n");

        StampedLock lock = new StampedLock();
        int[] data = {100};

        // 读线程：使用乐观读
        Thread reader = new Thread(() -> {
            // 获取乐观读戳记
            long stamp = lock.tryOptimisticRead();
            System.out.println("[Reader] 获取乐观读戳记: " + stamp);

            // 读取数据
            int value = data[0];
            System.out.println("[Reader] 读取数据: " + value);

            try {
                Thread.sleep(1000); // 模拟读取耗时
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // 验证戳记是否有效
            if (lock.validate(stamp)) {
                System.out.println("[Reader] 戳记有效，数据未被修改: " + value);
            } else {
                System.out.println("[Reader] 戳记无效，数据已被修改，需要重新读取");
                // 升级为悲观读锁
                stamp = lock.readLock();
                try {
                    value = data[0];
                    System.out.println("[Reader] 使用悲观读重新读取: " + value);
                } finally {
                    lock.unlockRead(stamp);
                }
            }
        }, "Reader");

        // 写线程：修改数据
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(500); // 在reader读取过程中修改
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long stamp = lock.writeLock();
            try {
                System.out.println("[Writer] 获取写锁，修改数据");
                data[0] = 200;
                System.out.println("[Writer] 数据已修改为: " + data[0]);
            } finally {
                lock.unlockWrite(stamp);
            }
        }, "Writer");

        reader.start();
        writer.start();

        reader.join();
        writer.join();

        System.out.println("\n✅ 乐观读不加锁，通过validate()验证数据是否被修改");
    }

    /**
     * 演示2：三种锁模式
     */
    public static void demoThreeLockModes() throws InterruptedException {
        System.out.println("\n========== 演示2：三种锁模式 ==========\n");

        StampedLock lock = new StampedLock();
        int[] data = {0};

        // 模式1：写锁（独占）
        System.out.println("模式1：写锁（独占）");
        Thread writer = new Thread(() -> {
            long stamp = lock.writeLock();
            try {
                System.out.println("  [Writer] 获取写锁");
                data[0] = 100;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlockWrite(stamp);
                System.out.println("  [Writer] 释放写锁");
            }
        }, "Writer");

        writer.start();
        writer.join();

        // 模式2：悲观读锁（共享）
        System.out.println("\n模式2：悲观读锁（共享）");
        Thread reader1 = new Thread(() -> {
            long stamp = lock.readLock();
            try {
                System.out.println("  [Reader-1] 获取悲观读锁");
                System.out.println("  [Reader-1] 读取: " + data[0]);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlockRead(stamp);
                System.out.println("  [Reader-1] 释放读锁");
            }
        }, "Reader-1");

        Thread reader2 = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long stamp = lock.readLock();
            try {
                System.out.println("  [Reader-2] 获取悲观读锁（并发）");
                System.out.println("  [Reader-2] 读取: " + data[0]);
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlockRead(stamp);
                System.out.println("  [Reader-2] 释放读锁");
            }
        }, "Reader-2");

        reader1.start();
        reader2.start();
        reader1.join();
        reader2.join();

        // 模式3：乐观读（无锁）
        System.out.println("\n模式3：乐观读（无锁）");
        long stamp = lock.tryOptimisticRead();
        System.out.println("  [Main] 获取乐观读戳记: " + stamp);
        int value = data[0];
        System.out.println("  [Main] 读取数据: " + value);
        if (lock.validate(stamp)) {
            System.out.println("  [Main] 戳记有效");
        }

        System.out.println("\n✅ StampedLock支持三种锁模式");
    }

    /**
     * 演示3：锁转换
     */
    public static void demoLockConversion() {
        System.out.println("\n========== 演示3：锁转换 ==========\n");

        StampedLock lock = new StampedLock();
        int[] data = {100};

        // 场景1：乐观读 -> 悲观读
        System.out.println("场景1：乐观读 -> 悲观读");
        long stamp = lock.tryOptimisticRead();
        System.out.println("  获取乐观读戳记: " + stamp);
        int value = data[0];

        if (!lock.validate(stamp)) {
            System.out.println("  戳记无效，转换为悲观读");
            stamp = lock.readLock();
            try {
                value = data[0];
                System.out.println("  使用悲观读重新读取: " + value);
            } finally {
                lock.unlockRead(stamp);
            }
        } else {
            System.out.println("  戳记有效，数据: " + value);
        }

        // 场景2：悲观读 -> 写锁
        System.out.println("\n场景2：悲观读 -> 写锁");
        stamp = lock.readLock();
        try {
            System.out.println("  获取悲观读锁");
            value = data[0];
            System.out.println("  读取数据: " + value);

            // 尝试转换为写锁
            long writeStamp = lock.tryConvertToWriteLock(stamp);
            if (writeStamp != 0) {
                stamp = writeStamp;
                System.out.println("  成功转换为写锁");
                data[0] = value + 1;
                System.out.println("  修改数据为: " + data[0]);
            } else {
                System.out.println("  转换失败");
            }
        } finally {
            lock.unlock(stamp);
        }

        // 场景3：写锁 -> 悲观读
        System.out.println("\n场景3：写锁 -> 悲观读");
        stamp = lock.writeLock();
        try {
            System.out.println("  获取写锁");
            data[0] = 200;
            System.out.println("  修改数据为: " + data[0]);

            // 转换为读锁
            stamp = lock.tryConvertToReadLock(stamp);
            System.out.println("  转换为读锁");
            System.out.println("  读取数据: " + data[0]);
        } finally {
            lock.unlockRead(stamp);
        }

        System.out.println("\n✅ StampedLock支持灵活的锁转换");
    }

    /**
     * 演示4：性能对比
     */
    public static void comparePerformance() throws InterruptedException {
        System.out.println("\n========== 演示4：性能对比 ==========\n");

        final int threadCount = 10;
        final int operations = 10000;

        // 测试1：ReadWriteLock
        System.out.println("测试ReadWriteLock...");
        java.util.concurrent.locks.ReadWriteLock rwLock = 
            new java.util.concurrent.locks.ReentrantReadWriteLock();
        int[] data1 = {0};
        long time1 = testReadWriteLock(rwLock, data1, threadCount, operations);

        // 测试2：StampedLock（悲观读）
        System.out.println("测试StampedLock（悲观读）...");
        StampedLock stampedLock1 = new StampedLock();
        int[] data2 = {0};
        long time2 = testStampedLockPessimistic(stampedLock1, data2, threadCount, operations);

        // 测试3：StampedLock（乐观读）
        System.out.println("测试StampedLock（乐观读）...");
        StampedLock stampedLock2 = new StampedLock();
        int[] data3 = {0};
        long time3 = testStampedLockOptimistic(stampedLock2, data3, threadCount, operations);

        // 输出对比
        System.out.println("\n性能对比:");
        System.out.println("  ReadWriteLock:            " + time1 + "ms");
        System.out.println("  StampedLock（悲观读）:    " + time2 + "ms");
        System.out.println("  StampedLock（乐观读）:    " + time3 + "ms");

        System.out.println("\n性能提升:");
        System.out.println("  悲观读 vs ReadWriteLock: " + 
                         String.format("%.2f%%", (time1 - time2) * 100.0 / time1));
        System.out.println("  乐观读 vs ReadWriteLock: " + 
                         String.format("%.2f%%", (time1 - time3) * 100.0 / time1));

        System.out.println("\n📊 分析:");
        System.out.println("  StampedLock的乐观读在读多写少场景下性能最优");
        System.out.println("  因为乐观读完全无锁，只在validate时检查");
    }

    private static long testReadWriteLock(java.util.concurrent.locks.ReadWriteLock rwLock,
                                          int[] data, int threadCount, int operations) 
            throws InterruptedException {
        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    if (j % 10 == 0) {
                        // 10%写操作
                        rwLock.writeLock().lock();
                        try {
                            data[0]++;
                        } finally {
                            rwLock.writeLock().unlock();
                        }
                    } else {
                        // 90%读操作
                        rwLock.readLock().lock();
                        try {
                            int value = data[0];
                        } finally {
                            rwLock.readLock().unlock();
                        }
                    }
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        return System.currentTimeMillis() - startTime;
    }

    private static long testStampedLockPessimistic(StampedLock lock, int[] data,
                                                    int threadCount, int operations) 
            throws InterruptedException {
        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    if (j % 10 == 0) {
                        // 10%写操作
                        long stamp = lock.writeLock();
                        try {
                            data[0]++;
                        } finally {
                            lock.unlockWrite(stamp);
                        }
                    } else {
                        // 90%读操作（悲观读）
                        long stamp = lock.readLock();
                        try {
                            int value = data[0];
                        } finally {
                            lock.unlockRead(stamp);
                        }
                    }
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        return System.currentTimeMillis() - startTime;
    }

    private static long testStampedLockOptimistic(StampedLock lock, int[] data,
                                                   int threadCount, int operations) 
            throws InterruptedException {
        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    if (j % 10 == 0) {
                        // 10%写操作
                        long stamp = lock.writeLock();
                        try {
                            data[0]++;
                        } finally {
                            lock.unlockWrite(stamp);
                        }
                    } else {
                        // 90%读操作（乐观读）
                        long stamp = lock.tryOptimisticRead();
                        int value = data[0];
                        if (!lock.validate(stamp)) {
                            // 升级为悲观读
                            stamp = lock.readLock();
                            try {
                                value = data[0];
                            } finally {
                                lock.unlockRead(stamp);
                            }
                        }
                    }
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        return System.currentTimeMillis() - startTime;
    }

    /**
     * 演示5：实现Point类
     */
    public static void demoPoint() {
        System.out.println("\n========== 演示5：实现Point类 ==========\n");

        class Point {
            private double x, y;
            private final StampedLock lock = new StampedLock();

            public void move(double deltaX, double deltaY) {
                long stamp = lock.writeLock();
                try {
                    x += deltaX;
                    y += deltaY;
                    System.out.println("  移动到: (" + x + ", " + y + ")");
                } finally {
                    lock.unlockWrite(stamp);
                }
            }

            public double distanceFromOrigin() {
                // 乐观读
                long stamp = lock.tryOptimisticRead();
                double currentX = x;
                double currentY = y;

                if (!lock.validate(stamp)) {
                    // 升级为悲观读
                    stamp = lock.readLock();
                    try {
                        currentX = x;
                        currentY = y;
                    } finally {
                        lock.unlockRead(stamp);
                    }
                }

                return Math.sqrt(currentX * currentX + currentY * currentY);
            }

            public void moveIfAtOrigin(double newX, double newY) {
                // 先尝试乐观读
                long stamp = lock.tryOptimisticRead();
                double currentX = x;
                double currentY = y;

                if (!lock.validate(stamp)) {
                    stamp = lock.readLock();
                    try {
                        currentX = x;
                        currentY = y;
                    } finally {
                        lock.unlockRead(stamp);
                    }
                }

                if (currentX == 0.0 && currentY == 0.0) {
                    // 需要修改，获取写锁
                    stamp = lock.writeLock();
                    try {
                        x = newX;
                        y = newY;
                        System.out.println("  从原点移动到: (" + x + ", " + y + ")");
                    } finally {
                        lock.unlockWrite(stamp);
                    }
                }
            }
        }

        Point point = new Point();
        System.out.println("初始距离: " + point.distanceFromOrigin());

        point.move(3, 4);
        System.out.println("移动后距离: " + point.distanceFromOrigin());

        point.moveIfAtOrigin(10, 10);
        System.out.println("条件移动后距离: " + point.distanceFromOrigin());

        System.out.println("\n✅ StampedLock适合实现高性能的数据结构");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== StampedLock总结 ==========");

        System.out.println("\n✅ 三种锁模式:");
        System.out.println("   1. 写锁（writeLock）：独占锁");
        System.out.println("   2. 悲观读锁（readLock）：共享锁");
        System.out.println("   3. 乐观读（tryOptimisticRead）：无锁");

        System.out.println("\n⚠️  重要特性:");
        System.out.println("   1. 乐观读不加锁，通过validate()验证");
        System.out.println("   2. 支持锁转换（读->写，写->读）");
        System.out.println("   3. 不可重入");
        System.out.println("   4. 不支持Condition");

        System.out.println("\n📊 vs ReadWriteLock:");
        System.out.println("   优点:");
        System.out.println("     - 乐观读性能更高");
        System.out.println("     - 支持锁转换");
        System.out.println("   缺点:");
        System.out.println("     - 不可重入");
        System.out.println("     - 不支持Condition");
        System.out.println("     - API更复杂");

        System.out.println("\n💡 使用建议:");
        System.out.println("   ✅ 读多写少场景");
        System.out.println("   ✅ 读操作耗时短");
        System.out.println("   ✅ 不需要重入");
        System.out.println("   ❌ 需要Condition");
        System.out.println("   ❌ 需要可重入");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 必须使用返回的stamp来unlock");
        System.out.println("   2. 不要在持有锁时调用可能阻塞的方法");
        System.out.println("   3. 乐观读后必须validate");
        System.out.println("   4. 不可重入，避免死锁");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              StampedLock乐观锁演示                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：乐观读
        demoOptimisticRead();

        // 演示2：三种锁模式
        demoThreeLockModes();

        // 演示3：锁转换
        demoLockConversion();

        // 演示4：性能对比
        comparePerformance();

        // 演示5：实现Point类
        demoPoint();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. StampedLock提供了乐观读机制");
        System.out.println("2. 乐观读在读多写少场景下性能最优");
        System.out.println("3. 支持灵活的锁转换");
        System.out.println("4. 不可重入，使用时需注意");
        System.out.println("5. 适合高性能场景，但API较复杂");
        System.out.println("===========================");
    }
}
