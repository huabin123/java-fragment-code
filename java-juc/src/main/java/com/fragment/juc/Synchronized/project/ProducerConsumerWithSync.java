package com.fragment.juc.Synchronized.project;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 生产者-消费者模式实现（使用Synchronized）
 * 
 * 实现方式：
 * 1. 使用wait/notify实现
 * 2. 有界缓冲区
 * 3. 多生产者多消费者
 * 4. 优雅关闭
 * 
 * @author huabin
 */
public class ProducerConsumerWithSync {
    
    /**
     * 方式1：基本的生产者-消费者
     */
    static class BasicProducerConsumer<T> {
        private final Queue<T> queue = new LinkedList<>();
        private final int capacity;
        
        public BasicProducerConsumer(int capacity) {
            this.capacity = capacity;
        }
        
        public synchronized void produce(T item) throws InterruptedException {
            while (queue.size() == capacity) {
                wait(); // 队列满，等待
            }
            
            queue.offer(item);
            System.out.println(Thread.currentThread().getName() + 
                " 生产: " + item + ", 队列大小: " + queue.size());
            
            notifyAll(); // 唤醒消费者
        }
        
        public synchronized T consume() throws InterruptedException {
            while (queue.isEmpty()) {
                wait(); // 队列空，等待
            }
            
            T item = queue.poll();
            System.out.println(Thread.currentThread().getName() + 
                " 消费: " + item + ", 队列大小: " + queue.size());
            
            notifyAll(); // 唤醒生产者
            return item;
        }
        
        public synchronized int size() {
            return queue.size();
        }
    }
    
    /**
     * 方式2：支持优雅关闭的生产者-消费者
     */
    static class GracefulProducerConsumer<T> {
        private final Queue<T> queue = new LinkedList<>();
        private final int capacity;
        private volatile boolean shutdown = false;
        
        public GracefulProducerConsumer(int capacity) {
            this.capacity = capacity;
        }
        
        public synchronized void produce(T item) throws InterruptedException {
            while (queue.size() == capacity && !shutdown) {
                wait();
            }
            
            if (shutdown) {
                throw new IllegalStateException("生产者已关闭");
            }
            
            queue.offer(item);
            System.out.println(Thread.currentThread().getName() + 
                " 生产: " + item + ", 队列大小: " + queue.size());
            
            notifyAll();
        }
        
        public synchronized T consume() throws InterruptedException {
            while (queue.isEmpty() && !shutdown) {
                wait();
            }
            
            if (queue.isEmpty() && shutdown) {
                return null; // 队列空且已关闭
            }
            
            T item = queue.poll();
            System.out.println(Thread.currentThread().getName() + 
                " 消费: " + item + ", 队列大小: " + queue.size());
            
            notifyAll();
            return item;
        }
        
        public synchronized void shutdown() {
            shutdown = true;
            notifyAll(); // 唤醒所有等待的线程
        }
        
        public synchronized boolean isShutdown() {
            return shutdown;
        }
    }
    
    /**
     * 方式3：带统计功能的生产者-消费者
     */
    static class StatisticsProducerConsumer<T> {
        private final Queue<T> queue = new LinkedList<>();
        private final int capacity;
        private long totalProduced = 0;
        private long totalConsumed = 0;
        private long totalWaitTime = 0;
        
        public StatisticsProducerConsumer(int capacity) {
            this.capacity = capacity;
        }
        
        public synchronized void produce(T item) throws InterruptedException {
            long startWait = System.currentTimeMillis();
            
            while (queue.size() == capacity) {
                wait();
            }
            
            totalWaitTime += (System.currentTimeMillis() - startWait);
            
            queue.offer(item);
            totalProduced++;
            
            System.out.println(Thread.currentThread().getName() + 
                " 生产: " + item + ", 队列: " + queue.size() + 
                ", 总生产: " + totalProduced);
            
            notifyAll();
        }
        
        public synchronized T consume() throws InterruptedException {
            long startWait = System.currentTimeMillis();
            
            while (queue.isEmpty()) {
                wait();
            }
            
            totalWaitTime += (System.currentTimeMillis() - startWait);
            
            T item = queue.poll();
            totalConsumed++;
            
            System.out.println(Thread.currentThread().getName() + 
                " 消费: " + item + ", 队列: " + queue.size() + 
                ", 总消费: " + totalConsumed);
            
            notifyAll();
            return item;
        }
        
        public synchronized void printStatistics() {
            System.out.println("\n========== 统计信息 ==========");
            System.out.println("总生产: " + totalProduced);
            System.out.println("总消费: " + totalConsumed);
            System.out.println("当前队列: " + queue.size());
            System.out.println("总等待时间: " + totalWaitTime + "ms");
        }
    }
    
    /**
     * 测试代码
     */
    public static void main(String[] args) throws InterruptedException {
        // 测试1：基本生产者-消费者
        System.out.println("========== 测试1：基本生产者-消费者 ==========");
        BasicProducerConsumer<Integer> pc1 = new BasicProducerConsumer<>(5);
        
        Thread producer1 = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    pc1.produce(i);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "生产者-1");
        
        Thread consumer1 = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    pc1.consume();
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "消费者-1");
        
        producer1.start();
        consumer1.start();
        producer1.join();
        consumer1.join();
        
        // 测试2：多生产者多消费者
        System.out.println("\n========== 测试2：多生产者多消费者 ==========");
        BasicProducerConsumer<String> pc2 = new BasicProducerConsumer<>(10);
        
        Thread[] producers = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int producerId = i;
            producers[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 5; j++) {
                        pc2.produce("P" + producerId + "-" + j);
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "生产者-" + i);
            producers[i].start();
        }
        
        Thread[] consumers = new Thread[2];
        for (int i = 0; i < 2; i++) {
            consumers[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 7; j++) {
                        pc2.consume();
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "消费者-" + i);
            consumers[i].start();
        }
        
        for (Thread producer : producers) {
            producer.join();
        }
        for (Thread consumer : consumers) {
            consumer.join();
        }
        
        // 测试3：优雅关闭
        System.out.println("\n========== 测试3：优雅关闭 ==========");
        GracefulProducerConsumer<Integer> pc3 = new GracefulProducerConsumer<>(5);
        
        Thread producer3 = new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    pc3.produce(i);
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                System.out.println("生产者被中断");
            } catch (IllegalStateException e) {
                System.out.println("生产者检测到关闭信号");
            }
        }, "生产者-3");
        
        Thread consumer3 = new Thread(() -> {
            try {
                while (!pc3.isShutdown() || pc3.size() > 0) {
                    Integer item = pc3.consume();
                    if (item == null) break;
                    Thread.sleep(100);
                }
                System.out.println("消费者正常退出");
            } catch (InterruptedException e) {
                System.out.println("消费者被中断");
            }
        }, "消费者-3");
        
        producer3.start();
        consumer3.start();
        
        Thread.sleep(1000);
        System.out.println("\n发送关闭信号...");
        pc3.shutdown();
        
        producer3.join();
        consumer3.join();
        
        // 测试4：带统计功能
        System.out.println("\n========== 测试4：带统计功能 ==========");
        StatisticsProducerConsumer<Integer> pc4 = new StatisticsProducerConsumer<>(3);
        
        Thread producer4 = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    pc4.produce(i);
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "生产者-4");
        
        Thread consumer4 = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    pc4.consume();
                    Thread.sleep(150);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "消费者-4");
        
        producer4.start();
        consumer4.start();
        producer4.join();
        consumer4.join();
        
        pc4.printStatistics();
        
        System.out.println("\n所有测试完成！");
    }
}
