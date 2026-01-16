package com.fragment.juc.queue.practice;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产者-消费者模式实战
 * 
 * <p>场景：日志异步处理系统
 * <ul>
 *   <li>多个业务线程产生日志（生产者）</li>
 *   <li>专门的日志线程写入文件（消费者）</li>
 *   <li>使用阻塞队列解耦，提高性能</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>阻塞队列实现线程间通信</li>
 *   <li>优雅停止机制</li>
 *   <li>异常处理</li>
 *   <li>性能监控</li>
 * </ul>
 * 
 * @author fragment
 */
public class ProducerConsumerPattern {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 生产者-消费者模式实战 ==========\n");

        // 创建日志处理系统
        AsyncLogSystem logSystem = new AsyncLogSystem(3);
        logSystem.start();

        // 模拟业务线程产生日志
        simulateBusinessThreads(logSystem, 5, 1000);

        // 运行5秒
        Thread.sleep(5000);

        // 优雅停止
        System.out.println("\n========== 开始优雅停止 ==========");
        logSystem.shutdown();

        // 打印统计信息
        logSystem.printStatistics();
    }

    /**
     * 模拟业务线程
     */
    private static void simulateBusinessThreads(AsyncLogSystem logSystem, 
                                               int threadCount, 
                                               int logsPerThread) {
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < logsPerThread; j++) {
                    String log = String.format("[Thread-%d] Log message %d", threadId, j);
                    logSystem.log(log);
                    
                    // 模拟业务处理
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println("[Thread-" + threadId + "] 完成，产生了 " + logsPerThread + " 条日志");
            }, "Business-Thread-" + i).start();
        }
    }

    /**
     * 异步日志系统
     */
    static class AsyncLogSystem {
        // 日志队列
        private final BlockingQueue<LogEvent> logQueue;
        
        // 消费者线程
        private final LogConsumer[] consumers;
        
        // 运行状态
        private volatile boolean running = true;
        
        // 统计信息
        private final AtomicInteger producedCount = new AtomicInteger(0);
        private final AtomicInteger consumedCount = new AtomicInteger(0);
        private final AtomicInteger droppedCount = new AtomicInteger(0);

        public AsyncLogSystem(int consumerCount) {
            // 有界队列，容量10000
            this.logQueue = new LinkedBlockingQueue<>(10000);
            this.consumers = new LogConsumer[consumerCount];
            
            // 创建消费者线程
            for (int i = 0; i < consumerCount; i++) {
                consumers[i] = new LogConsumer(i);
            }
        }

        /**
         * 启动系统
         */
        public void start() {
            System.out.println("启动日志系统，消费者数量: " + consumers.length);
            for (LogConsumer consumer : consumers) {
                consumer.start();
            }
        }

        /**
         * 记录日志（生产者调用）
         */
        public void log(String message) {
            if (!running) {
                System.err.println("日志系统已停止，丢弃日志: " + message);
                droppedCount.incrementAndGet();
                return;
            }

            LogEvent event = new LogEvent(message, System.currentTimeMillis());
            
            // 使用offer而不是put，避免阻塞业务线程
            boolean success = logQueue.offer(event);
            if (success) {
                producedCount.incrementAndGet();
            } else {
                // 队列满，丢弃日志（或者可以选择阻塞）
                System.err.println("队列满，丢弃日志: " + message);
                droppedCount.incrementAndGet();
            }
        }

        /**
         * 优雅停止
         */
        public void shutdown() throws InterruptedException {
            System.out.println("停止接收新日志...");
            running = false;

            System.out.println("等待队列中的日志处理完成...");
            System.out.println("当前队列大小: " + logQueue.size());

            // 等待队列清空（最多等待10秒）
            long start = System.currentTimeMillis();
            while (!logQueue.isEmpty() && System.currentTimeMillis() - start < 10000) {
                Thread.sleep(100);
                if (logQueue.size() % 100 == 0) {
                    System.out.println("剩余日志: " + logQueue.size());
                }
            }

            // 停止消费者
            System.out.println("停止消费者线程...");
            for (LogConsumer consumer : consumers) {
                consumer.shutdown();
            }

            // 等待消费者线程结束
            for (LogConsumer consumer : consumers) {
                consumer.join(1000);
            }

            System.out.println("日志系统已停止");
        }

        /**
         * 打印统计信息
         */
        public void printStatistics() {
            System.out.println("\n========== 统计信息 ==========");
            System.out.println("生产日志数: " + producedCount.get());
            System.out.println("消费日志数: " + consumedCount.get());
            System.out.println("丢弃日志数: " + droppedCount.get());
            System.out.println("队列剩余: " + logQueue.size());
            
            int total = 0;
            for (LogConsumer consumer : consumers) {
                System.out.println("消费者-" + consumer.id + " 处理: " + consumer.processedCount.get());
                total += consumer.processedCount.get();
            }
            System.out.println("消费者总计: " + total);
        }

        /**
         * 日志消费者
         */
        class LogConsumer extends Thread {
            private final int id;
            private volatile boolean running = true;
            private final AtomicInteger processedCount = new AtomicInteger(0);

            public LogConsumer(int id) {
                super("LogConsumer-" + id);
                this.id = id;
            }

            @Override
            public void run() {
                System.out.println("[消费者-" + id + "] 启动");

                while (running || !logQueue.isEmpty()) {
                    try {
                        // 使用poll超时，避免永久阻塞
                        LogEvent event = logQueue.poll(100, TimeUnit.MILLISECONDS);
                        
                        if (event != null) {
                            // 处理日志
                            processLog(event);
                            processedCount.incrementAndGet();
                            consumedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        System.out.println("[消费者-" + id + "] 被中断");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[消费者-" + id + "] 处理异常: " + e.getMessage());
                    }
                }

                System.out.println("[消费者-" + id + "] 退出，共处理 " + processedCount.get() + " 条日志");
            }

            /**
             * 处理日志（模拟写入文件）
             */
            private void processLog(LogEvent event) {
                // 模拟I/O操作
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 实际应用中这里会写入文件
                // System.out.println("[消费者-" + id + "] " + event.message);
            }

            /**
             * 停止消费者
             */
            public void shutdown() {
                running = false;
                this.interrupt();
            }
        }
    }

    /**
     * 日志事件
     */
    static class LogEvent {
        private final String message;
        private final long timestamp;

        public LogEvent(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s", timestamp, message);
        }
    }
}
