package com.fragment.juc.lock.demo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ReadWriteLock读写锁演示
 * 
 * 演示内容：
 * 1. 读写锁基本使用
 * 2. 读-读并发，读-写互斥，写-写互斥
 * 3. 锁降级
 * 4. 性能对比
 * 5. 实现缓存
 * 
 * @author huabin
 */
public class ReadWriteLockDemo {

    /**
     * 演示1：读写锁基本使用
     */
    public static void demoBasicUsage() throws InterruptedException {
        System.out.println("\n========== 演示1：读写锁基本使用 ==========\n");

        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        String[] data = {"初始数据"};

        // 读线程
        Runnable reader = () -> {
            rwLock.readLock().lock();
            try {
                System.out.println("[" + Thread.currentThread().getName() + 
                                 "] 读取: " + data[0]);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.readLock().unlock();
            }
        };

        // 写线程
        Runnable writer = () -> {
            rwLock.writeLock().lock();
            try {
                System.out.println("[" + Thread.currentThread().getName() + 
                                 "] 写入: 新数据");
                data[0] = "新数据";
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.writeLock().unlock();
            }
        };

        // 启动多个读线程
        Thread r1 = new Thread(reader, "Reader-1");
        Thread r2 = new Thread(reader, "Reader-2");
        Thread w1 = new Thread(writer, "Writer-1");

        r1.start();
        r2.start();
        Thread.sleep(100);
        w1.start();

        r1.join();
        r2.join();
        w1.join();

        System.out.println("\n✅ 读锁可以并发，写锁独占");
    }

    /**
     * 演示2：读-读并发，读-写互斥，写-写互斥
     */
    public static void demoLockRules() throws InterruptedException {
        System.out.println("\n========== 演示2：锁的互斥规则 ==========\n");

        ReadWriteLock rwLock = new ReentrantReadWriteLock();

        // 测试读-读并发
        System.out.println("测试1：读-读并发");
        Thread r1 = new Thread(() -> {
            rwLock.readLock().lock();
            try {
                System.out.println("  [Reader-1] 获取读锁");
                Thread.sleep(2000);
                System.out.println("  [Reader-1] 释放读锁");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.readLock().unlock();
            }
        }, "Reader-1");

        Thread r2 = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            rwLock.readLock().lock();
            try {
                System.out.println("  [Reader-2] 获取读锁（并发）");
                Thread.sleep(1000);
                System.out.println("  [Reader-2] 释放读锁");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.readLock().unlock();
            }
        }, "Reader-2");

        r1.start();
        r2.start();
        r1.join();
        r2.join();

        System.out.println("  ✅ 读锁可以并发获取\n");

        // 测试读-写互斥
        System.out.println("测试2：读-写互斥");
        Thread r3 = new Thread(() -> {
            rwLock.readLock().lock();
            try {
                System.out.println("  [Reader-3] 获取读锁");
                Thread.sleep(2000);
                System.out.println("  [Reader-3] 释放读锁");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.readLock().unlock();
            }
        }, "Reader-3");

        Thread w1 = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("  [Writer-1] 尝试获取写锁...");
            rwLock.writeLock().lock();
            try {
                System.out.println("  [Writer-1] 获取写锁（等待读锁释放后）");
                Thread.sleep(1000);
                System.out.println("  [Writer-1] 释放写锁");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.writeLock().unlock();
            }
        }, "Writer-1");

        r3.start();
        w1.start();
        r3.join();
        w1.join();

        System.out.println("  ✅ 读锁和写锁互斥\n");

        // 测试写-写互斥
        System.out.println("测试3：写-写互斥");
        Thread w2 = new Thread(() -> {
            rwLock.writeLock().lock();
            try {
                System.out.println("  [Writer-2] 获取写锁");
                Thread.sleep(2000);
                System.out.println("  [Writer-2] 释放写锁");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.writeLock().unlock();
            }
        }, "Writer-2");

        Thread w3 = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("  [Writer-3] 尝试获取写锁...");
            rwLock.writeLock().lock();
            try {
                System.out.println("  [Writer-3] 获取写锁（等待写锁释放后）");
                Thread.sleep(1000);
                System.out.println("  [Writer-3] 释放写锁");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                rwLock.writeLock().unlock();
            }
        }, "Writer-3");

        w2.start();
        w3.start();
        w2.join();
        w3.join();

        System.out.println("  ✅ 写锁和写锁互斥");
    }

    /**
     * 演示3：锁降级
     */
    public static void demoLockDowngrade() {
        System.out.println("\n========== 演示3：锁降级 ==========\n");

        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        Map<String, String> cache = new HashMap<>();

        // 锁降级：写锁 -> 读锁
        rwLock.writeLock().lock();
        try {
            System.out.println("[Main] 获取写锁");
            cache.put("key", "value");
            System.out.println("[Main] 写入数据: key=value");

            // 在释放写锁前获取读锁（锁降级）
            rwLock.readLock().lock();
            System.out.println("[Main] 获取读锁（锁降级）");
        } finally {
            rwLock.writeLock().unlock();
            System.out.println("[Main] 释放写锁");
        }

        // 现在只持有读锁
        try {
            System.out.println("[Main] 读取数据: " + cache.get("key"));
        } finally {
            rwLock.readLock().unlock();
            System.out.println("[Main] 释放读锁");
        }

        System.out.println("\n⚠️  注意：不支持锁升级（读锁 -> 写锁）");
        System.out.println("   如果尝试锁升级会导致死锁");

        System.out.println("\n✅ 锁降级保证了数据的一致性");
    }

    /**
     * 演示4：性能对比
     */
    public static void comparePerformance() throws InterruptedException {
        System.out.println("\n========== 演示4：性能对比 ==========\n");

        final int threadCount = 10;
        final int readOperations = 1000;
        final int writeOperations = 100;

        // 测试1：使用ReentrantLock
        System.out.println("测试ReentrantLock...");
        Lock lock = new java.util.concurrent.locks.ReentrantLock();
        int[] data1 = {0};
        long time1 = testWithLock(lock, data1, threadCount, readOperations, writeOperations);

        // 测试2：使用ReadWriteLock
        System.out.println("测试ReadWriteLock...");
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        int[] data2 = {0};
        long time2 = testWithReadWriteLock(rwLock, data2, threadCount, readOperations, writeOperations);

        // 输出对比
        System.out.println("\n性能对比:");
        System.out.println("  ReentrantLock:   " + time1 + "ms");
        System.out.println("  ReadWriteLock:   " + time2 + "ms");
        System.out.println("  性能提升: " + 
                         String.format("%.2f%%", (time1 - time2) * 100.0 / time1));

        System.out.println("\n📊 分析:");
        System.out.println("  读多写少场景下，ReadWriteLock性能显著优于ReentrantLock");
        System.out.println("  因为读操作可以并发执行");
    }

    private static long testWithLock(Lock lock, int[] data, int threadCount, 
                                     int readOps, int writeOps) throws InterruptedException {
        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                // 读操作
                for (int j = 0; j < readOps; j++) {
                    lock.lock();
                    try {
                        int value = data[0];
                    } finally {
                        lock.unlock();
                    }
                }
                // 写操作
                for (int j = 0; j < writeOps; j++) {
                    lock.lock();
                    try {
                        data[0]++;
                    } finally {
                        lock.unlock();
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

    private static long testWithReadWriteLock(ReadWriteLock rwLock, int[] data, 
                                              int threadCount, int readOps, int writeOps) 
            throws InterruptedException {
        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                // 读操作
                for (int j = 0; j < readOps; j++) {
                    rwLock.readLock().lock();
                    try {
                        int value = data[0];
                    } finally {
                        rwLock.readLock().unlock();
                    }
                }
                // 写操作
                for (int j = 0; j < writeOps; j++) {
                    rwLock.writeLock().lock();
                    try {
                        data[0]++;
                    } finally {
                        rwLock.writeLock().unlock();
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
     * 演示5：实现简单缓存
     */
    public static void demoCache() throws InterruptedException {
        System.out.println("\n========== 演示5：实现简单缓存 ==========\n");

        class ReadWriteCache<K, V> {
            private final Map<K, V> cache = new HashMap<>();
            private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

            public V get(K key) {
                rwLock.readLock().lock();
                try {
                    return cache.get(key);
                } finally {
                    rwLock.readLock().unlock();
                }
            }

            public void put(K key, V value) {
                rwLock.writeLock().lock();
                try {
                    cache.put(key, value);
                } finally {
                    rwLock.writeLock().unlock();
                }
            }

            public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction) {
                // 先尝试读取
                rwLock.readLock().lock();
                try {
                    V value = cache.get(key);
                    if (value != null) {
                        return value;
                    }
                } finally {
                    rwLock.readLock().unlock();
                }

                // 需要计算，获取写锁
                rwLock.writeLock().lock();
                try {
                    // 双重检查
                    V value = cache.get(key);
                    if (value == null) {
                        value = mappingFunction.apply(key);
                        cache.put(key, value);
                    }
                    return value;
                } finally {
                    rwLock.writeLock().unlock();
                }
            }

            public int size() {
                rwLock.readLock().lock();
                try {
                    return cache.size();
                } finally {
                    rwLock.readLock().unlock();
                }
            }
        }

        ReadWriteCache<String, String> cache = new ReadWriteCache<>();

        // 写入数据
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                cache.put("key" + i, "value" + i);
                System.out.println("[Writer] 写入: key" + i + " = value" + i);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Writer");

        // 读取数据
        Thread[] readers = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int readerId = i;
            readers[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    String key = "key" + (j % 5);
                    String value = cache.get(key);
                    System.out.println("[Reader-" + readerId + "] 读取: " + key + " = " + value);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "Reader-" + i);
        }

        writer.start();
        for (Thread reader : readers) {
            reader.start();
        }

        writer.join();
        for (Thread reader : readers) {
            reader.join();
        }

        System.out.println("\n缓存大小: " + cache.size());
        System.out.println("✅ ReadWriteLock非常适合实现缓存");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== ReadWriteLock总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 读-读并发：多个读线程可以同时持有读锁");
        System.out.println("   2. 读-写互斥：读锁和写锁互斥");
        System.out.println("   3. 写-写互斥：写锁是独占的");
        System.out.println("   4. 锁降级：支持写锁降级为读锁");
        System.out.println("   5. 不支持锁升级：读锁不能升级为写锁");

        System.out.println("\n📊 适用场景:");
        System.out.println("   ✅ 读多写少的场景");
        System.out.println("   ✅ 缓存实现");
        System.out.println("   ✅ 配置管理");
        System.out.println("   ❌ 写操作频繁的场景");

        System.out.println("\n⚠️  使用注意:");
        System.out.println("   1. 读锁和写锁都要在finally中释放");
        System.out.println("   2. 避免在持有读锁时尝试获取写锁（死锁）");
        System.out.println("   3. 锁降级要先获取读锁再释放写锁");
        System.out.println("   4. 写操作频繁时性能可能不如ReentrantLock");

        System.out.println("\n💡 性能优化:");
        System.out.println("   1. 读操作占比越高，性能提升越明显");
        System.out.println("   2. 读操作耗时越长，性能提升越明显");
        System.out.println("   3. 考虑使用StampedLock获得更好性能");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              ReadWriteLock读写锁演示                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：锁的互斥规则
        demoLockRules();

        // 演示3：锁降级
        demoLockDowngrade();

        // 演示4：性能对比
        comparePerformance();

        // 演示5：实现缓存
        demoCache();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. ReadWriteLock实现了读写分离");
        System.out.println("2. 读多写少场景下性能优于ReentrantLock");
        System.out.println("3. 支持锁降级，不支持锁升级");
        System.out.println("4. 非常适合实现缓存");
        System.out.println("===========================");
    }
}
