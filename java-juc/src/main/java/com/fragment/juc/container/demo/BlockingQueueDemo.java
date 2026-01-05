package com.fragment.juc.container.demo;

import java.util.concurrent.*;

/**
 * BlockingQueue阻塞队列演示
 * 
 * 演示内容：
 * 1. ArrayBlockingQueue - 有界阻塞队列
 * 2. LinkedBlockingQueue - 链表阻塞队列
 * 3. PriorityBlockingQueue - 优先级队列
 * 4. DelayQueue - 延迟队列
 * 5. 生产者-消费者模式
 * 
 * @author huabin
 */
public class BlockingQueueDemo {

    /**
     * 演示1：ArrayBlockingQueue基本使用
     */
    public static void demoArrayBlockingQueue() throws InterruptedException {
        System.out.println("\n========== 演示1：ArrayBlockingQueue ==========\n");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(3);

        // 生产者
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    String item = "Item-" + i;
                    System.out.println("[生产者] 放入: " + item + 
                                     " (队列大小: " + queue.size() + "/3)");
                    queue.put(item); // 队列满时阻塞
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Producer");

        // 消费者
        Thread consumer = new Thread(() -> {
            try {
                Thread.sleep(2000); // 延迟启动
                for (int i = 1; i <= 5; i++) {
                    String item = queue.take(); // 队列空时阻塞
                    System.out.println("[消费者] 取出: " + item + 
                                     " (队列大小: " + queue.size() + "/3)");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        System.out.println("\n✅ ArrayBlockingQueue是有界阻塞队列");
    }

    /**
     * 演示2：四种操作方法对比
     */
    public static void demoOperationMethods() throws InterruptedException {
        System.out.println("\n========== 演示2：四种操作方法 ==========\n");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        System.out.println("1. 抛异常方法（add/remove/element）:");
        queue.add("A");
        queue.add("B");
        System.out.println("  添加A和B成功");
        try {
            queue.add("C"); // 队列满，抛异常
        } catch (IllegalStateException e) {
            System.out.println("  添加C失败: " + e.getClass().getSimpleName());
        }

        queue.clear();

        System.out.println("\n2. 返回特殊值方法（offer/poll/peek）:");
        System.out.println("  offer(A): " + queue.offer("A"));
        System.out.println("  offer(B): " + queue.offer("B"));
        System.out.println("  offer(C): " + queue.offer("C")); // 返回false
        System.out.println("  peek(): " + queue.peek()); // 不移除
        System.out.println("  poll(): " + queue.poll()); // 移除并返回

        queue.clear();

        System.out.println("\n3. 阻塞方法（put/take）:");
        new Thread(() -> {
            try {
                queue.put("X");
                queue.put("Y");
                System.out.println("  [线程1] put(X)和put(Y)成功");
                System.out.println("  [线程1] 尝试put(Z)，将阻塞...");
                queue.put("Z"); // 阻塞
                System.out.println("  [线程1] put(Z)成功");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        Thread.sleep(1000);
        System.out.println("  [主线程] take(): " + queue.take());

        Thread.sleep(500);

        System.out.println("\n4. 超时方法（offer/poll with timeout）:");
        queue.clear();
        queue.offer("M");
        queue.offer("N");
        boolean success = queue.offer("O", 1, TimeUnit.SECONDS);
        System.out.println("  offer(O, 1s): " + success);

        System.out.println("\n✅ 四种方法适用不同场景");
    }

    /**
     * 演示3：LinkedBlockingQueue
     */
    public static void demoLinkedBlockingQueue() throws InterruptedException {
        System.out.println("\n========== 演示3：LinkedBlockingQueue ==========\n");

        // 无界队列（实际上有界，Integer.MAX_VALUE）
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();

        System.out.println("生产者-消费者模式:\n");

        // 生产者
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    queue.put(i);
                    System.out.println("[生产者] 生产: " + i);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Producer");

        // 消费者
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    Integer item = queue.take();
                    System.out.println("[消费者] 消费: " + item);
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        System.out.println("\n✅ LinkedBlockingQueue适合生产者-消费者模式");
    }

    /**
     * 演示4：PriorityBlockingQueue
     */
    public static void demoPriorityBlockingQueue() throws InterruptedException {
        System.out.println("\n========== 演示4：PriorityBlockingQueue ==========\n");

        class Task implements Comparable<Task> {
            String name;
            int priority;

            Task(String name, int priority) {
                this.name = name;
                this.priority = priority;
            }

            @Override
            public int compareTo(Task other) {
                return Integer.compare(other.priority, this.priority); // 高优先级优先
            }

            @Override
            public String toString() {
                return name + "(优先级:" + priority + ")";
            }
        }

        BlockingQueue<Task> queue = new PriorityBlockingQueue<>();

        // 添加任务（乱序）
        queue.put(new Task("任务A", 3));
        queue.put(new Task("任务B", 1));
        queue.put(new Task("任务C", 5));
        queue.put(new Task("任务D", 2));
        queue.put(new Task("任务E", 4));

        System.out.println("按优先级取出任务:");
        while (!queue.isEmpty()) {
            Task task = queue.take();
            System.out.println("  执行: " + task);
        }

        System.out.println("\n✅ PriorityBlockingQueue按优先级排序");
    }

    /**
     * 演示5：DelayQueue
     */
    public static void demoDelayQueue() throws InterruptedException {
        System.out.println("\n========== 演示5：DelayQueue ==========\n");

        class DelayedTask implements Delayed {
            String name;
            long executeTime;

            DelayedTask(String name, long delayMs) {
                this.name = name;
                this.executeTime = System.currentTimeMillis() + delayMs;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                long diff = executeTime - System.currentTimeMillis();
                return unit.convert(diff, TimeUnit.MILLISECONDS);
            }

            @Override
            public int compareTo(Delayed other) {
                return Long.compare(this.executeTime, ((DelayedTask) other).executeTime);
            }

            @Override
            public String toString() {
                return name;
            }
        }

        BlockingQueue<DelayedTask> queue = new DelayQueue<>();

        // 添加延迟任务
        queue.put(new DelayedTask("任务1", 3000)); // 3秒后执行
        queue.put(new DelayedTask("任务2", 1000)); // 1秒后执行
        queue.put(new DelayedTask("任务3", 2000)); // 2秒后执行

        System.out.println("添加了3个延迟任务\n");

        // 消费者
        new Thread(() -> {
            try {
                while (true) {
                    DelayedTask task = queue.take();
                    System.out.println("[" + System.currentTimeMillis() % 100000 + "] 执行: " + task);
                }
            } catch (InterruptedException e) {
                System.out.println("消费者结束");
            }
        }, "Consumer").start();

        Thread.sleep(5000);

        System.out.println("\n✅ DelayQueue适合定时任务");
    }

    /**
     * 演示6：多生产者多消费者
     */
    public static void demoMultiProducerConsumer() throws InterruptedException {
        System.out.println("\n========== 演示6：多生产者多消费者 ==========\n");

        BlockingQueue<String> queue = new LinkedBlockingQueue<>(10);
        int producerCount = 2;
        int consumerCount = 3;
        int itemsPerProducer = 5;

        CountDownLatch latch = new CountDownLatch(producerCount + consumerCount);

        // 生产者
        for (int i = 0; i < producerCount; i++) {
            final int producerId = i + 1;
            new Thread(() -> {
                try {
                    for (int j = 1; j <= itemsPerProducer; j++) {
                        String item = "P" + producerId + "-Item" + j;
                        queue.put(item);
                        System.out.println("[生产者" + producerId + "] 生产: " + item);
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }, "Producer-" + producerId).start();
        }

        // 消费者
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i + 1;
            new Thread(() -> {
                try {
                    while (true) {
                        String item = queue.poll(3, TimeUnit.SECONDS);
                        if (item == null) break;
                        System.out.println("[消费者" + consumerId + "] 消费: " + item);
                        Thread.sleep(300);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }, "Consumer-" + consumerId).start();
        }

        latch.await();
        System.out.println("\n✅ BlockingQueue天然支持多生产者多消费者");
    }

    /**
     * 演示7：实际应用 - 任务队列
     */
    public static void demoTaskQueue() throws InterruptedException {
        System.out.println("\n========== 演示7：任务队列 ==========\n");

        class Task {
            String name;
            Runnable action;

            Task(String name, Runnable action) {
                this.name = name;
                this.action = action;
            }
        }

        BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>(5);

        // 工作线程
        for (int i = 1; i <= 3; i++) {
            final int workerId = i;
            new Thread(() -> {
                while (true) {
                    try {
                        Task task = taskQueue.take();
                        System.out.println("[Worker-" + workerId + "] 执行: " + task.name);
                        task.action.run();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "Worker-" + workerId).start();
        }

        // 提交任务
        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            Task task = new Task("Task-" + taskId, () -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            taskQueue.put(task);
            System.out.println("[Main] 提交: " + task.name);
            Thread.sleep(100);
        }

        Thread.sleep(3000);
        System.out.println("\n✅ BlockingQueue是线程池的核心");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== BlockingQueue总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 阻塞：队列满时put阻塞，队列空时take阻塞");
        System.out.println("   2. 线程安全：内部使用锁保证线程安全");
        System.out.println("   3. 生产者-消费者：天然支持该模式");

        System.out.println("\n📊 常用实现:");
        System.out.println("   ArrayBlockingQueue:    有界，数组实现");
        System.out.println("   LinkedBlockingQueue:   可选有界，链表实现");
        System.out.println("   PriorityBlockingQueue: 无界，优先级队列");
        System.out.println("   DelayQueue:            无界，延迟队列");
        System.out.println("   SynchronousQueue:      容量为0，直接传递");

        System.out.println("\n📊 四种操作方法:");
        System.out.println("   ┌──────────┬──────────┬──────────┬──────────┐");
        System.out.println("   │          │  抛异常  │ 特殊值   │  阻塞    │");
        System.out.println("   ├──────────┼──────────┼──────────┼──────────┤");
        System.out.println("   │ 插入     │  add()   │ offer()  │  put()   │");
        System.out.println("   │ 移除     │ remove() │  poll()  │  take()  │");
        System.out.println("   │ 检查     │element() │  peek()  │    -     │");
        System.out.println("   └──────────┴──────────┴──────────┴──────────┘");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 生产者-消费者模式");
        System.out.println("   ✅ 线程池任务队列");
        System.out.println("   ✅ 消息队列");
        System.out.println("   ✅ 任务调度");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 选择合适的队列类型");
        System.out.println("   2. 合理设置队列容量");
        System.out.println("   3. 注意队列满和空的处理");
        System.out.println("   4. 不要在队列中放null");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            BlockingQueue阻塞队列演示                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：ArrayBlockingQueue
        demoArrayBlockingQueue();

        // 演示2：操作方法
        demoOperationMethods();

        // 演示3：LinkedBlockingQueue
        demoLinkedBlockingQueue();

        // 演示4：PriorityBlockingQueue
        demoPriorityBlockingQueue();

        // 演示5：DelayQueue
        demoDelayQueue();

        // 演示6：多生产者多消费者
        demoMultiProducerConsumer();

        // 演示7：任务队列
        demoTaskQueue();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. BlockingQueue是线程安全的阻塞队列");
        System.out.println("2. 天然支持生产者-消费者模式");
        System.out.println("3. 是线程池的核心组件");
        System.out.println("4. 根据场景选择合适的实现");
        System.out.println("===========================");
    }
}
