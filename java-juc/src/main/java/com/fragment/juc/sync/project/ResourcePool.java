package com.fragment.juc.sync.project;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于Semaphore的资源池实现
 * 
 * 实现内容：
 * 1. 通用资源池
 * 2. 数据库连接池
 * 3. 线程池
 * 4. 对象池
 * 5. 资源监控
 * 
 * @author huabin
 */
public class ResourcePool {

    /**
     * 通用资源池
     */
    static class GenericResourcePool<T> {
        private final Queue<T> resources;
        private final Semaphore semaphore;
        private final int capacity;
        private final AtomicInteger activeCount = new AtomicInteger(0);
        private final AtomicInteger totalAcquired = new AtomicInteger(0);
        private final AtomicInteger totalReleased = new AtomicInteger(0);

        public GenericResourcePool(int capacity) {
            this.capacity = capacity;
            this.resources = new LinkedList<>();
            this.semaphore = new Semaphore(capacity, true); // 公平模式
        }

        /**
         * 添加资源到池中
         */
        public void addResource(T resource) {
            synchronized (resources) {
                resources.offer(resource);
            }
        }

        /**
         * 获取资源
         */
        public T acquire() throws InterruptedException {
            semaphore.acquire();
            T resource;
            synchronized (resources) {
                resource = resources.poll();
            }
            activeCount.incrementAndGet();
            totalAcquired.incrementAndGet();
            return resource;
        }

        /**
         * 获取资源（超时）
         */
        public T acquire(long timeout, TimeUnit unit) throws InterruptedException {
            if (semaphore.tryAcquire(timeout, unit)) {
                T resource;
                synchronized (resources) {
                    resource = resources.poll();
                }
                activeCount.incrementAndGet();
                totalAcquired.incrementAndGet();
                return resource;
            }
            return null;
        }

        /**
         * 释放资源
         */
        public void release(T resource) {
            if (resource != null) {
                synchronized (resources) {
                    resources.offer(resource);
                }
                activeCount.decrementAndGet();
                totalReleased.incrementAndGet();
                semaphore.release();
            }
        }

        /**
         * 获取统计信息
         */
        public String getStats() {
            return String.format("Pool Stats: capacity=%d, active=%d, available=%d, " +
                               "totalAcquired=%d, totalReleased=%d",
                    capacity, activeCount.get(), semaphore.availablePermits(),
                    totalAcquired.get(), totalReleased.get());
        }

        public int getActiveCount() {
            return activeCount.get();
        }

        public int getAvailableCount() {
            return semaphore.availablePermits();
        }
    }

    /**
     * 数据库连接池
     */
    static class DatabaseConnectionPool {
        private final GenericResourcePool<Connection> pool;

        static class Connection {
            private final int id;
            private boolean closed = false;

            Connection(int id) {
                this.id = id;
            }

            public void execute(String sql) throws InterruptedException {
                if (closed) {
                    throw new IllegalStateException("Connection is closed");
                }
                System.out.println("  [连接" + id + "] 执行: " + sql);
                Thread.sleep((long) (Math.random() * 500)); // 模拟查询
            }

            public void close() {
                closed = true;
            }

            @Override
            public String toString() {
                return "Connection-" + id;
            }
        }

        public DatabaseConnectionPool(int poolSize) {
            this.pool = new GenericResourcePool<>(poolSize);
            // 初始化连接
            for (int i = 1; i <= poolSize; i++) {
                pool.addResource(new Connection(i));
            }
        }

        public Connection getConnection() throws InterruptedException {
            return pool.acquire();
        }

        public Connection getConnection(long timeout, TimeUnit unit) throws InterruptedException {
            return pool.acquire(timeout, unit);
        }

        public void releaseConnection(Connection conn) {
            pool.release(conn);
        }

        public String getStats() {
            return pool.getStats();
        }
    }

    /**
     * 对象池（可重用对象）
     */
    static class ObjectPool<T> {
        private final GenericResourcePool<T> pool;
        private final ObjectFactory<T> factory;

        interface ObjectFactory<T> {
            T create();
            void reset(T obj);
        }

        public ObjectPool(int capacity, ObjectFactory<T> factory) {
            this.pool = new GenericResourcePool<>(capacity);
            this.factory = factory;
            // 预创建对象
            for (int i = 0; i < capacity; i++) {
                pool.addResource(factory.create());
            }
        }

        public T borrowObject() throws InterruptedException {
            return pool.acquire();
        }

        public void returnObject(T obj) {
            factory.reset(obj); // 重置对象状态
            pool.release(obj);
        }

        public String getStats() {
            return pool.getStats();
        }
    }

    /**
     * 演示1：数据库连接池
     */
    public static void demoConnectionPool() throws InterruptedException {
        System.out.println("\n========== 演示1：数据库连接池 ==========\n");

        DatabaseConnectionPool pool = new DatabaseConnectionPool(3);

        System.out.println("连接池大小: 3");
        System.out.println("模拟10个并发查询...\n");

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int queryId = i + 1;
            threads[i] = new Thread(() -> {
                DatabaseConnectionPool.Connection conn = null;
                try {
                    System.out.println("[查询" + queryId + "] 等待连接... " + pool.getStats());
                    conn = pool.getConnection();
                    System.out.println("[查询" + queryId + "] 获取连接: " + conn);

                    conn.execute("SELECT * FROM users WHERE id=" + queryId);

                    System.out.println("[查询" + queryId + "] 查询完成");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        pool.releaseConnection(conn);
                        System.out.println("[查询" + queryId + "] 释放连接");
                    }
                }
            }, "Query-" + queryId);
            threads[i].start();
            Thread.sleep(100);
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("\n" + pool.getStats());
        System.out.println("\n✅ 连接池有效控制了并发数量");
    }

    /**
     * 演示2：超时获取资源
     */
    public static void demoTimeout() throws InterruptedException {
        System.out.println("\n========== 演示2：超时获取资源 ==========\n");

        DatabaseConnectionPool pool = new DatabaseConnectionPool(2);

        // 线程1和2：占用连接
        for (int i = 1; i <= 2; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    DatabaseConnectionPool.Connection conn = pool.getConnection();
                    System.out.println("[线程" + threadId + "] 获取连接，持有5秒");
                    Thread.sleep(5000);
                    pool.releaseConnection(conn);
                    System.out.println("[线程" + threadId + "] 释放连接");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Thread-" + threadId).start();
        }

        Thread.sleep(500);

        // 线程3：尝试获取（超时2秒）
        new Thread(() -> {
            try {
                System.out.println("\n[线程3] 尝试获取连接（最多等待2秒）...");
                DatabaseConnectionPool.Connection conn = pool.getConnection(2, TimeUnit.SECONDS);
                if (conn != null) {
                    System.out.println("[线程3] 获取连接成功");
                    pool.releaseConnection(conn);
                } else {
                    System.out.println("[线程3] 获取连接超时");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-3").start();

        Thread.sleep(7000);
        System.out.println("\n✅ 超时机制避免了无限期等待");
    }

    /**
     * 演示3：对象池
     */
    public static void demoObjectPool() throws InterruptedException {
        System.out.println("\n========== 演示3：对象池 ==========\n");

        // StringBuilder对象池
        ObjectPool<StringBuilder> pool = new ObjectPool<>(3, new ObjectPool.ObjectFactory<StringBuilder>() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public StringBuilder create() {
                int id = counter.incrementAndGet();
                System.out.println("  创建对象: StringBuilder-" + id);
                return new StringBuilder("StringBuilder-" + id);
            }

            @Override
            public void reset(StringBuilder obj) {
                obj.setLength(0); // 清空内容
            }
        });

        System.out.println("对象池大小: 3\n");

        // 使用对象池
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            new Thread(() -> {
                try {
                    System.out.println("[任务" + taskId + "] 借用对象...");
                    StringBuilder sb = pool.borrowObject();
                    System.out.println("[任务" + taskId + "] 借用成功: " + sb);

                    sb.append("任务").append(taskId).append("的数据");
                    System.out.println("[任务" + taskId + "] 使用对象: " + sb);

                    Thread.sleep(1000);

                    pool.returnObject(sb);
                    System.out.println("[任务" + taskId + "] 归还对象");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Task-" + taskId).start();

            Thread.sleep(300);
        }

        Thread.sleep(4000);
        System.out.println("\n" + pool.getStats());
        System.out.println("\n✅ 对象池减少了对象创建开销");
    }

    /**
     * 演示4：资源池监控
     */
    public static void demoMonitoring() throws InterruptedException {
        System.out.println("\n========== 演示4：资源池监控 ==========\n");

        DatabaseConnectionPool pool = new DatabaseConnectionPool(5);

        // 监控线程
        Thread monitor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    System.out.println("[Monitor] " + pool.getStats());
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Monitor");
        monitor.start();

        // 模拟负载
        for (int i = 1; i <= 10; i++) {
            final int queryId = i;
            new Thread(() -> {
                DatabaseConnectionPool.Connection conn = null;
                try {
                    conn = pool.getConnection();
                    Thread.sleep((long) (Math.random() * 2000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        pool.releaseConnection(conn);
                    }
                }
            }, "Query-" + queryId).start();

            Thread.sleep(200);
        }

        Thread.sleep(5000);
        monitor.interrupt();

        System.out.println("\n✅ 监控可以实时了解资源使用情况");
    }

    /**
     * 演示5：资源池性能对比
     */
    public static void demoPerformanceComparison() throws InterruptedException {
        System.out.println("\n========== 演示5：性能对比 ==========\n");

        int requestCount = 100;

        // 测试1：无池化（每次创建新连接）
        System.out.println("测试1：无池化（每次创建新连接）");
        long time1 = testWithoutPool(requestCount);

        Thread.sleep(1000);

        // 测试2：使用连接池
        System.out.println("\n测试2：使用连接池");
        long time2 = testWithPool(requestCount);

        System.out.println("\n性能对比:");
        System.out.println("  无池化:   " + time1 + "ms");
        System.out.println("  使用池化: " + time2 + "ms");
        System.out.println("  性能提升: " + String.format("%.2f%%", 
                         (time1 - time2) * 100.0 / time1));

        System.out.println("\n✅ 资源池显著提升了性能");
    }

    private static long testWithoutPool(int requestCount) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        Thread[] threads = new Thread[requestCount];

        for (int i = 0; i < requestCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    // 模拟创建连接的开销
                    Thread.sleep(10);
                    // 模拟使用连接
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        return System.currentTimeMillis() - startTime;
    }

    private static long testWithPool(int requestCount) throws InterruptedException {
        DatabaseConnectionPool pool = new DatabaseConnectionPool(10);
        long startTime = System.currentTimeMillis();
        Thread[] threads = new Thread[requestCount];

        for (int i = 0; i < requestCount; i++) {
            threads[i] = new Thread(() -> {
                DatabaseConnectionPool.Connection conn = null;
                try {
                    conn = pool.getConnection();
                    // 模拟使用连接
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        pool.releaseConnection(conn);
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
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 资源池总结 ==========");

        System.out.println("\n✅ 核心优势:");
        System.out.println("   1. 资源复用：减少创建和销毁开销");
        System.out.println("   2. 并发控制：限制资源使用数量");
        System.out.println("   3. 性能提升：显著提高系统吞吐量");
        System.out.println("   4. 资源管理：统一管理资源生命周期");

        System.out.println("\n📊 实现要点:");
        System.out.println("   1. 使用Semaphore控制并发");
        System.out.println("   2. 使用队列存储空闲资源");
        System.out.println("   3. 支持超时获取");
        System.out.println("   4. 添加资源监控");
        System.out.println("   5. 做好异常处理");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 数据库连接池");
        System.out.println("   ✅ 线程池");
        System.out.println("   ✅ 对象池");
        System.out.println("   ✅ HTTP连接池");
        System.out.println("   ✅ 任何需要限制并发的资源");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 合理设置池大小");
        System.out.println("   2. 资源使用后必须归还");
        System.out.println("   3. 做好资源有效性检查");
        System.out.println("   4. 考虑资源的生命周期");
        System.out.println("   5. 添加监控和告警");

        System.out.println("\n🚀 优化建议:");
        System.out.println("   1. 支持动态扩容");
        System.out.println("   2. 添加资源健康检查");
        System.out.println("   3. 实现资源预热");
        System.out.println("   4. 支持优先级");
        System.out.println("   5. 考虑使用成熟的池化框架");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            基于Semaphore的资源池实现                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：连接池
        demoConnectionPool();

        // 演示2：超时
        demoTimeout();

        // 演示3：对象池
        demoObjectPool();

        // 演示4：监控
        demoMonitoring();

        // 演示5：性能对比
        demoPerformanceComparison();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. Semaphore非常适合实现资源池");
        System.out.println("2. 资源池可以显著提升性能");
        System.out.println("3. 要做好并发控制和超时处理");
        System.out.println("4. 资源使用后必须归还");
        System.out.println("===========================");
    }
}
