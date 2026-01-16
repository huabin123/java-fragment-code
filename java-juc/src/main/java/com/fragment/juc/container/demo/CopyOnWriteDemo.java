package com.fragment.juc.container.demo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CopyOnWrite容器演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>写时复制机制</li>
 *   <li>读多写少场景优化</li>
 *   <li>迭代器不会抛ConcurrentModificationException</li>
 *   <li>内存占用分析</li>
 *   <li>性能对比测试</li>
 * </ul>
 * 
 * @author fragment
 */
public class CopyOnWriteDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== CopyOnWrite容器演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 写时复制机制
        demonstrateCopyOnWrite();

        // 3. 迭代器安全性
        demonstrateIteratorSafety();

        // 4. 读多写少场景
        demonstrateReadHeavyScenario();

        // 5. 性能对比
        demonstratePerformanceComparison();

        // 6. CopyOnWriteArraySet演示
        demonstrateCopyOnWriteArraySet();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: 线程安全的ArrayList，写时复制\n");

        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();

        // 添加元素
        System.out.println("=== 添加元素 ===");
        list.add("A");
        list.add("B");
        list.add("C");
        System.out.println("添加3个元素: " + list);

        // 读取元素
        System.out.println("\n=== 读取元素 ===");
        System.out.println("get(0): " + list.get(0));
        System.out.println("get(1): " + list.get(1));

        // 修改元素
        System.out.println("\n=== 修改元素 ===");
        list.set(1, "B-modified");
        System.out.println("修改后: " + list);

        // 删除元素
        System.out.println("\n=== 删除元素 ===");
        list.remove("A");
        System.out.println("删除后: " + list);

        System.out.println("\n关键点: 所有写操作都会复制底层数组");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 写时复制机制演示
     */
    private static void demonstrateCopyOnWrite() throws InterruptedException {
        System.out.println("2. 写时复制机制演示");
        System.out.println("特点: 写操作时复制整个数组\n");

        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
        
        // 初始化数据
        for (int i = 0; i < 5; i++) {
            list.add(i);
        }
        System.out.println("初始数据: " + list);

        // 读线程：持续读取
        Thread reader = new Thread(() -> {
            // 获取快照
            Object[] snapshot = list.toArray();
            System.out.println("\n[读线程] 获取快照: " + arrayToString(snapshot));
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 再次读取（可能已被修改）
            System.out.println("[读线程] 当前数据: " + list);
            System.out.println("[读线程] 快照依然是: " + arrayToString(snapshot));
        });

        // 写线程：修改数据
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("\n[写线程] 开始修改...");
                list.add(5);
                list.add(6);
                System.out.println("[写线程] 修改完成: " + list);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        reader.start();
        writer.start();
        reader.join();
        writer.join();

        System.out.println("\n关键点: 读线程的快照不受写操作影响");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 迭代器安全性演示
     */
    private static void demonstrateIteratorSafety() throws InterruptedException {
        System.out.println("3. 迭代器安全性演示");
        System.out.println("特点: 迭代器基于快照，不会抛ConcurrentModificationException\n");

        // 对比普通ArrayList
        System.out.println("=== 普通ArrayList（会抛异常） ===");
        List<String> normalList = new ArrayList<>();
        normalList.add("A");
        normalList.add("B");
        normalList.add("C");

        try {
            for (String item : normalList) {
                System.out.println("遍历: " + item);
                if ("B".equals(item)) {
                    normalList.add("D");  // 修改列表
                }
            }
        } catch (Exception e) {
            System.out.println("❌ 抛出异常: " + e.getClass().getSimpleName());
        }

        // CopyOnWriteArrayList
        System.out.println("\n=== CopyOnWriteArrayList（不会抛异常） ===");
        CopyOnWriteArrayList<String> cowList = new CopyOnWriteArrayList<>();
        cowList.add("A");
        cowList.add("B");
        cowList.add("C");

        Iterator<String> iterator = cowList.iterator();
        System.out.println("创建迭代器，当前列表: " + cowList);

        // 在迭代过程中修改
        new Thread(() -> {
            try {
                Thread.sleep(100);
                cowList.add("D");
                System.out.println("[写线程] 添加元素D，当前列表: " + cowList);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        Thread.sleep(200);

        System.out.println("\n继续迭代（基于快照）:");
        while (iterator.hasNext()) {
            System.out.println("遍历: " + iterator.next());
        }

        System.out.println("\n新迭代器会看到最新数据:");
        for (String item : cowList) {
            System.out.println("遍历: " + item);
        }

        System.out.println("\n关键点: 迭代器基于创建时的快照，不受后续修改影响");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 读多写少场景演示
     */
    private static void demonstrateReadHeavyScenario() throws InterruptedException {
        System.out.println("4. 读多写少场景演示");
        System.out.println("场景: 配置中心，读取频繁，修改很少\n");

        CopyOnWriteArrayList<String> configList = new CopyOnWriteArrayList<>();
        
        // 初始化配置
        configList.add("config1=value1");
        configList.add("config2=value2");
        configList.add("config3=value3");

        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeCount = new AtomicInteger(0);

        // 10个读线程
        Thread[] readers = new Thread[10];
        for (int i = 0; i < 10; i++) {
            readers[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    // 读取配置
                    for (String config : configList) {
                        // 模拟读取操作
                    }
                    readCount.incrementAndGet();
                }
            });
            readers[i].start();
        }

        // 1个写线程
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                configList.add("config" + (i + 4) + "=value" + (i + 4));
                writeCount.incrementAndGet();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        writer.start();

        // 等待完成
        for (Thread reader : readers) {
            reader.join();
        }
        writer.join();

        System.out.println("=== 统计 ===");
        System.out.println("读操作次数: " + readCount.get());
        System.out.println("写操作次数: " + writeCount.get());
        System.out.println("读写比: " + (readCount.get() / writeCount.get()) + ":1");
        System.out.println("最终配置数量: " + configList.size());

        System.out.println("\n关键点: 读多写少场景，CopyOnWrite性能优异");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. 性能对比演示
     */
    private static void demonstratePerformanceComparison() throws InterruptedException {
        System.out.println("5. 性能对比演示");
        System.out.println("对比: CopyOnWriteArrayList vs SynchronizedList\n");

        final int THREAD_COUNT = 10;
        final int OPERATIONS = 1000;

        // 测试CopyOnWriteArrayList
        CopyOnWriteArrayList<Integer> cowList = new CopyOnWriteArrayList<>();
        long cowTime = testPerformance(cowList, THREAD_COUNT, OPERATIONS, "读多");

        // 测试SynchronizedList
        List<Integer> syncList = java.util.Collections.synchronizedList(new ArrayList<>());
        long syncTime = testPerformance(syncList, THREAD_COUNT, OPERATIONS, "读多");

        System.out.println("\n=== 性能对比（读多场景） ===");
        System.out.println("CopyOnWriteArrayList: " + cowTime + "ms");
        System.out.println("SynchronizedList: " + syncTime + "ms");
        System.out.println("性能提升: " + String.format("%.2f", (double) syncTime / cowTime) + "倍");

        System.out.println("\n关键点: 读多场景下，CopyOnWrite性能显著优于Synchronized");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 6. CopyOnWriteArraySet演示
     */
    private static void demonstrateCopyOnWriteArraySet() {
        System.out.println("6. CopyOnWriteArraySet演示");
        System.out.println("特点: 基于CopyOnWriteArrayList实现的Set\n");

        CopyOnWriteArraySet<String> set = new CopyOnWriteArraySet<>();

        // 添加元素
        System.out.println("=== 添加元素 ===");
        set.add("A");
        set.add("B");
        set.add("C");
        set.add("A");  // 重复元素
        System.out.println("添加A, B, C, A");
        System.out.println("实际元素: " + set);

        // 检查包含
        System.out.println("\n=== 检查包含 ===");
        System.out.println("contains(A): " + set.contains("A"));
        System.out.println("contains(D): " + set.contains("D"));

        // 删除元素
        System.out.println("\n=== 删除元素 ===");
        set.remove("B");
        System.out.println("删除B后: " + set);

        System.out.println("\n关键点: 自动去重，适合读多写少的Set场景");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 性能测试辅助方法
     */
    private static long testPerformance(List<Integer> list, int threadCount, 
                                       int operations, String scenario) throws InterruptedException {
        // 预填充数据
        for (int i = 0; i < 100; i++) {
            list.add(i);
        }

        CountDownLatch latch = new CountDownLatch(threadCount);
        long start = System.currentTimeMillis();

        // 创建线程
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    // 90%读，10%写
                    if (j % 10 == 0) {
                        list.add(j);
                    } else {
                        for (Integer item : list) {
                            // 读取操作
                        }
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        long end = System.currentTimeMillis();

        return end - start;
    }

    /**
     * 数组转字符串
     */
    private static String arrayToString(Object[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 创建分隔线
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
