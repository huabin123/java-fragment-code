package com.fragment.juc.threadpool.dynamic;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Jdk8DynamicThreadPool {

    // 支持动态调整的线程池
    static class DynamicThreadPool extends ThreadPoolExecutor {
        private final AtomicInteger dynamicCore = new AtomicInteger();
        private final AtomicInteger dynamicMax = new AtomicInteger();
        private final ResizableBlockingQueue<Runnable> dynamicQueue;

        public DynamicThreadPool(int core, int max, int queueSize) {
            super(core, max, 60, TimeUnit.SECONDS, new ResizableBlockingQueue<>(queueSize));

            // JDK8兼容写法：显式初始化原子变量
            dynamicCore.set(core);
            dynamicMax.set(max);
            dynamicQueue = (ResizableBlockingQueue<Runnable>) super.getQueue();
        }

        public void reconfigure(int newCore, int newMax, int newQueueSize) {
            // 调整核心线程数
            if (newCore != dynamicCore.get()) {
                dynamicCore.set(newCore);
                super.setCorePoolSize(newCore); // 调用父类方法

                // 立即创建新核心线程（兼容JDK8）
                int start = dynamicCore.get() - getPoolSize();
                while (start-- > 0) {
                    prestartCoreThread();
                }
            }

            // 调整最大线程数
            if (newMax != dynamicMax.get()) {
                dynamicMax.set(newMax);
                super.setMaximumPoolSize(newMax);
            }

            // 调整队列容量
            dynamicQueue.setCapacity(newQueueSize);
        }
    }

    // 动态容量阻塞队列（适配JDK8）
    static class ResizableBlockingQueue<E> extends LinkedBlockingQueue<E> {
        private final AtomicInteger capacity = new AtomicInteger();
        private final ReentrantLock putLock = new ReentrantLock();
        private final Condition notFull = putLock.newCondition();

        // 添加对线程池的引用
        private ThreadPoolExecutor executor;

        public ResizableBlockingQueue(int initCapacity) {
            super(initCapacity);
            capacity.set(initCapacity);
        }

        public void setCapacity(int newCapacity) {
            final int oldCapacity = capacity.get();
            if (newCapacity <= 0)
                throw new IllegalArgumentException();

            capacity.set(newCapacity);

            // 缩容处理
            if (newCapacity < oldCapacity && size() > newCapacity) {
                synchronized (this) {
                    int diff = size() - newCapacity;
                    RejectedExecutionHandler handler =
                            executor.getRejectedExecutionHandler();
                    for (int i = 0; i < diff; i++) {
                        Runnable task = (Runnable) super.poll();
                        if (task != null) {
                            handler.rejectedExecution(task, executor);
                        }
                    }
                }
            }

            // 唤醒等待的生产者线程
            if (newCapacity > oldCapacity) {
                signalNotFull();
            }
        }

        @Override
        public void put(E e) throws InterruptedException {
            putLock.lockInterruptibly();
            try {
                while (size() >= capacity.get()) {
                    notFull.await();
                }
                super.offer(e);
            } finally {
                putLock.unlock();
            }
        }

        @Override
        public boolean offer(E e, long timeout, TimeUnit unit)
                throws InterruptedException {
            long nanos = unit.toNanos(timeout);
            putLock.lockInterruptibly();
            try {
                while (size() >= capacity.get()) {
                    if (nanos <= 0) return false;
                    nanos = notFull.awaitNanos(nanos);
                }
                return super.offer(e);
            } finally {
                putLock.unlock();
            }
        }

        private void signalNotFull() {
            putLock.lock();
            try {
                notFull.signalAll();
            } finally {
                putLock.unlock();
            }
        }
    }

    // 监控指标采集（适配JDK8）
    static class ThreadPoolMonitor {
        void registerMetrics(ThreadPoolExecutor pool, String name) {
            // 简化实现：直接打印指标
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                System.out.printf("[Monitor] %s - Active: %d, Queue: %d/%d, Completed: %d%n",
                        name,
                        pool.getActiveCount(),
                        pool.getQueue().size(),
                        ((ResizableBlockingQueue<?>) pool.getQueue()).capacity.get(),
                        pool.getCompletedTaskCount());
            }, 0, 2, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) {
        // 初始化线程池：核心2，最大5，队列10
        DynamicThreadPool pool = new DynamicThreadPool(2, 5, 10);
        new ThreadPoolMonitor().registerMetrics(pool, "main-pool");

        // 配置动态调整线程
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                System.out.println("\n=== 第一次扩容：core=4, max=8, queue=20 ===");
                pool.reconfigure(4, 8, 20);

                Thread.sleep(10000);
                System.out.println("\n=== 第二次缩容：core=3, queue=15 ===");
                pool.reconfigure(3, 8, 15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        // 持续提交任务
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            for (int i = 0; i < 20; i++) {
                try {
                    pool.execute(() -> {
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(500));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } catch (RejectedExecutionException e) {
                    System.out.println("Task rejected: " + e.getMessage());
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}
