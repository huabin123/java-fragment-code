package com.fragment.juc.lock.demo;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Condition条件队列演示
 * 
 * 演示内容：
 * 1. Condition基本使用
 * 2. 多条件队列
 * 3. 生产者-消费者模式
 * 4. Condition vs wait/notify
 * 
 * @author huabin
 */
public class ConditionDemo {

    /**
     * 演示1：Condition基本使用
     */
    public static void demoBasicUsage() throws InterruptedException {
        System.out.println("\n========== 演示1：Condition基本使用 ==========\n");

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        boolean[] ready = {false};

        // 等待线程
        Thread waiter = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("[Waiter] 开始等待...");
                while (!ready[0]) {
                    condition.await(); // 释放锁并等待
                }
                System.out.println("[Waiter] 条件满足，继续执行");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Waiter");

        // 通知线程
        Thread signaler = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            lock.lock();
            try {
                System.out.println("[Signaler] 设置条件为true");
                ready[0] = true;
                condition.signal(); // 唤醒等待线程
                System.out.println("[Signaler] 发送信号");
            } finally {
                lock.unlock();
            }
        }, "Signaler");

        waiter.start();
        signaler.start();

        waiter.join();
        signaler.join();

        System.out.println("\n✅ Condition提供了类似wait/notify的功能");
    }

    /**
     * 演示2：多条件队列
     */
    public static void demoMultipleConditions() throws InterruptedException {
        System.out.println("\n========== 演示2：多条件队列 ==========\n");

        Lock lock = new ReentrantLock();
        Condition condition1 = lock.newCondition();
        Condition condition2 = lock.newCondition();

        // 等待条件1的线程
        Thread waiter1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("[Waiter1] 等待条件1...");
                condition1.await();
                System.out.println("[Waiter1] 条件1满足");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Waiter1");

        // 等待条件2的线程
        Thread waiter2 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("[Waiter2] 等待条件2...");
                condition2.await();
                System.out.println("[Waiter2] 条件2满足");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Waiter2");

        waiter1.start();
        waiter2.start();
        Thread.sleep(500);

        // 通知条件1
        lock.lock();
        try {
            System.out.println("[Main] 通知条件1");
            condition1.signal();
        } finally {
            lock.unlock();
        }

        Thread.sleep(500);

        // 通知条件2
        lock.lock();
        try {
            System.out.println("[Main] 通知条件2");
            condition2.signal();
        } finally {
            lock.unlock();
        }

        waiter1.join();
        waiter2.join();

        System.out.println("\n✅ 一个Lock可以创建多个Condition");
    }

    /**
     * 演示3：生产者-消费者模式（单条件）
     */
    public static void demoProducerConsumer() throws InterruptedException {
        System.out.println("\n========== 演示3：生产者-消费者（单条件）==========\n");

        class BoundedBuffer<T> {
            private final Queue<T> queue = new LinkedList<>();
            private final int capacity;
            private final Lock lock = new ReentrantLock();
            private final Condition notFull = lock.newCondition();
            private final Condition notEmpty = lock.newCondition();

            public BoundedBuffer(int capacity) {
                this.capacity = capacity;
            }

            public void put(T item) throws InterruptedException {
                lock.lock();
                try {
                    while (queue.size() == capacity) {
                        System.out.println("  [Producer] 队列已满，等待...");
                        notFull.await();
                    }
                    queue.offer(item);
                    System.out.println("  [Producer] 生产: " + item + 
                                     ", 队列大小: " + queue.size());
                    notEmpty.signal(); // 通知消费者
                } finally {
                    lock.unlock();
                }
            }

            public T take() throws InterruptedException {
                lock.lock();
                try {
                    while (queue.isEmpty()) {
                        System.out.println("  [Consumer] 队列为空，等待...");
                        notEmpty.await();
                    }
                    T item = queue.poll();
                    System.out.println("  [Consumer] 消费: " + item + 
                                     ", 队列大小: " + queue.size());
                    notFull.signal(); // 通知生产者
                    return item;
                } finally {
                    lock.unlock();
                }
            }
        }

        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(5);

        // 生产者
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    buffer.put(i);
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
                    buffer.take();
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

        System.out.println("\n✅ Condition实现生产者-消费者模式");
    }

    /**
     * 演示4：Condition vs wait/notify对比
     */
    public static void compareConditionAndWaitNotify() {
        System.out.println("\n========== 演示4：Condition vs wait/notify ==========\n");

        System.out.println("📊 特性对比:");
        System.out.println("  ┌─────────────────┬──────────────┬──────────────┐");
        System.out.println("  │     特性        │  Condition   │ wait/notify  │");
        System.out.println("  ├─────────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 所属            │     Lock     │    Object    │");
        System.out.println("  │ 条件队列数量    │     多个     │     单个     │");
        System.out.println("  │ 等待方法        │   await()    │    wait()    │");
        System.out.println("  │ 通知方法        │   signal()   │   notify()   │");
        System.out.println("  │ 通知所有        │ signalAll()  │ notifyAll()  │");
        System.out.println("  │ 可中断等待      │     支持     │     支持     │");
        System.out.println("  │ 超时等待        │     支持     │     支持     │");
        System.out.println("  │ 灵活性          │     高       │     低       │");
        System.out.println("  └─────────────────┴──────────────┴──────────────┘");

        System.out.println("\n代码对比:");
        System.out.println("\n使用wait/notify:");
        System.out.println("  synchronized (lock) {");
        System.out.println("      while (!condition) {");
        System.out.println("          lock.wait();");
        System.out.println("      }");
        System.out.println("      // 业务逻辑");
        System.out.println("      lock.notifyAll();");
        System.out.println("  }");

        System.out.println("\n使用Condition:");
        System.out.println("  lock.lock();");
        System.out.println("  try {");
        System.out.println("      while (!condition) {");
        System.out.println("          condition.await();");
        System.out.println("      }");
        System.out.println("      // 业务逻辑");
        System.out.println("      condition.signalAll();");
        System.out.println("  } finally {");
        System.out.println("      lock.unlock();");
        System.out.println("  }");

        System.out.println("\n✅ Condition提供了更灵活的线程协作机制");
    }

    /**
     * 演示5：实现阻塞队列
     */
    public static void demoBlockingQueue() throws InterruptedException {
        System.out.println("\n========== 演示5：实现阻塞队列 ==========\n");

        class SimpleBlockingQueue<T> {
            private final Queue<T> queue = new LinkedList<>();
            private final int capacity;
            private final Lock lock = new ReentrantLock();
            private final Condition notFull = lock.newCondition();
            private final Condition notEmpty = lock.newCondition();

            public SimpleBlockingQueue(int capacity) {
                this.capacity = capacity;
            }

            public void put(T item) throws InterruptedException {
                lock.lock();
                try {
                    while (queue.size() == capacity) {
                        notFull.await();
                    }
                    queue.offer(item);
                    notEmpty.signal();
                } finally {
                    lock.unlock();
                }
            }

            public T take() throws InterruptedException {
                lock.lock();
                try {
                    while (queue.isEmpty()) {
                        notEmpty.await();
                    }
                    T item = queue.poll();
                    notFull.signal();
                    return item;
                } finally {
                    lock.unlock();
                }
            }

            public int size() {
                lock.lock();
                try {
                    return queue.size();
                } finally {
                    lock.unlock();
                }
            }
        }

        SimpleBlockingQueue<String> queue = new SimpleBlockingQueue<>(3);

        // 生产者线程
        Thread[] producers = new Thread[2];
        for (int i = 0; i < 2; i++) {
            final int producerId = i;
            producers[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 5; j++) {
                        String item = "Item-" + producerId + "-" + j;
                        queue.put(item);
                        System.out.println("[Producer-" + producerId + "] 生产: " + item);
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Producer-" + i);
        }

        // 消费者线程
        Thread[] consumers = new Thread[2];
        for (int i = 0; i < 2; i++) {
            final int consumerId = i;
            consumers[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 5; j++) {
                        String item = queue.take();
                        System.out.println("[Consumer-" + consumerId + "] 消费: " + item);
                        Thread.sleep(150);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Consumer-" + i);
        }

        // 启动所有线程
        for (Thread producer : producers) {
            producer.start();
        }
        for (Thread consumer : consumers) {
            consumer.start();
        }

        // 等待所有线程完成
        for (Thread producer : producers) {
            producer.join();
        }
        for (Thread consumer : consumers) {
            consumer.join();
        }

        System.out.println("\n最终队列大小: " + queue.size());
        System.out.println("✅ 使用Condition实现了线程安全的阻塞队列");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== Condition总结 ==========");

        System.out.println("\n✅ 核心方法:");
        System.out.println("   await()      - 等待，释放锁");
        System.out.println("   signal()     - 唤醒一个等待线程");
        System.out.println("   signalAll()  - 唤醒所有等待线程");

        System.out.println("\n⚠️  使用注意:");
        System.out.println("   1. await()必须在lock()和unlock()之间");
        System.out.println("   2. await()必须在while循环中（防止虚假唤醒）");
        System.out.println("   3. signal()前要先设置条件");
        System.out.println("   4. 优先使用signalAll()而非signal()");

        System.out.println("\n📊 适用场景:");
        System.out.println("   ✅ 生产者-消费者模式");
        System.out.println("   ✅ 阻塞队列实现");
        System.out.println("   ✅ 需要多个等待条件");
        System.out.println("   ✅ 复杂的线程协作");

        System.out.println("\n💡 最佳实践:");
        System.out.println("   1. 一个Lock可以创建多个Condition");
        System.out.println("   2. 不同条件使用不同的Condition");
        System.out.println("   3. 避免在持有锁时执行耗时操作");
        System.out.println("   4. 优先使用JUC提供的BlockingQueue");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              Condition条件队列演示                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：多条件队列
        demoMultipleConditions();

        // 演示3：生产者-消费者
        demoProducerConsumer();

        // 演示4：对比
        compareConditionAndWaitNotify();

        // 演示5：阻塞队列
        demoBlockingQueue();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. Condition提供了比wait/notify更灵活的线程协作");
        System.out.println("2. 一个Lock可以创建多个Condition");
        System.out.println("3. await()必须在while循环中");
        System.out.println("4. 适用于生产者-消费者等复杂协作场景");
        System.out.println("===========================");
    }
}
