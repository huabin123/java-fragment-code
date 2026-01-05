package com.example.jvm.memory.project;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单内存池实现
 * 
 * 功能：
 * 1. 对象池化，减少对象创建
 * 2. 支持对象复用
 * 3. 自动扩容和收缩
 * 4. 线程安全
 * 5. 性能监控
 * 
 * 应用场景：
 * - 频繁创建和销毁的对象
 * - 创建成本高的对象
 * - 需要控制对象数量的场景
 * 
 * @author JavaGuide
 */
public class SimpleMemoryPool<T> {

    // 对象工厂
    private final ObjectFactory<T> factory;
    
    // 对象池
    private final BlockingQueue<PooledObject<T>> pool;
    
    // 池配置
    private final PoolConfig config;
    
    // 统计信息
    private final PoolStatistics statistics;
    
    // 池状态
    private volatile boolean running = true;
    
    /**
     * 构造函数
     */
    public SimpleMemoryPool(ObjectFactory<T> factory, PoolConfig config) {
        this.factory = factory;
        this.config = config;
        this.pool = new LinkedBlockingQueue<>(config.maxSize);
        this.statistics = new PoolStatistics();
        
        // 初始化池
        initPool();
        
        // 启动维护线程
        startMaintenanceThread();
    }

    /**
     * 初始化池
     */
    private void initPool() {
        for (int i = 0; i < config.initialSize; i++) {
            try {
                T object = factory.create();
                pool.offer(new PooledObject<>(object));
                statistics.incrementCreated();
            } catch (Exception e) {
                System.err.println("初始化对象失败: " + e.getMessage());
            }
        }
        
        System.out.println("内存池初始化完成，初始大小: " + pool.size());
    }

    /**
     * 从池中获取对象
     */
    public T acquire() throws Exception {
        return acquire(config.maxWaitTime, TimeUnit.MILLISECONDS);
    }

    /**
     * 从池中获取对象（带超时）
     */
    public T acquire(long timeout, TimeUnit unit) throws Exception {
        if (!running) {
            throw new IllegalStateException("内存池已关闭");
        }
        
        statistics.incrementAcquireRequests();
        long startTime = System.currentTimeMillis();
        
        // 尝试从池中获取
        PooledObject<T> pooledObject = pool.poll(timeout, unit);
        
        if (pooledObject == null) {
            // 池中没有可用对象，尝试创建新对象
            if (statistics.getTotalCreated() < config.maxSize) {
                T object = factory.create();
                pooledObject = new PooledObject<>(object);
                statistics.incrementCreated();
                System.out.println("创建新对象，当前池大小: " + pool.size());
            } else {
                statistics.incrementAcquireFailures();
                throw new TimeoutException("获取对象超时，池已满");
            }
        }
        
        // 验证对象
        if (!factory.validate(pooledObject.getObject())) {
            // 对象无效，重新创建
            T object = factory.create();
            pooledObject = new PooledObject<>(object);
            statistics.incrementCreated();
        }
        
        pooledObject.setLastAccessTime(System.currentTimeMillis());
        pooledObject.incrementUseCount();
        
        long duration = System.currentTimeMillis() - startTime;
        statistics.recordAcquireTime(duration);
        
        return pooledObject.getObject();
    }

    /**
     * 归还对象到池
     */
    public void release(T object) {
        if (!running) {
            return;
        }
        
        statistics.incrementReleaseRequests();
        
        try {
            // 重置对象状态
            factory.reset(object);
            
            // 归还到池
            PooledObject<T> pooledObject = new PooledObject<>(object);
            pooledObject.setLastAccessTime(System.currentTimeMillis());
            
            if (!pool.offer(pooledObject, config.maxWaitTime, TimeUnit.MILLISECONDS)) {
                // 池已满，销毁对象
                factory.destroy(object);
                statistics.incrementDestroyed();
            }
        } catch (Exception e) {
            System.err.println("归还对象失败: " + e.getMessage());
        }
    }

    /**
     * 启动维护线程
     */
    private void startMaintenanceThread() {
        Thread maintenanceThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(config.maintenanceInterval);
                    performMaintenance();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "MemoryPool-Maintenance");
        
        maintenanceThread.setDaemon(true);
        maintenanceThread.start();
    }

    /**
     * 执行维护任务
     */
    private void performMaintenance() {
        // 清理过期对象
        cleanupExpiredObjects();
        
        // 收缩池
        shrinkPool();
        
        // 打印统计信息
        if (config.enableStatistics) {
            printStatistics();
        }
    }

    /**
     * 清理过期对象
     */
    private void cleanupExpiredObjects() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        
        Iterator<PooledObject<T>> iterator = pool.iterator();
        while (iterator.hasNext()) {
            PooledObject<T> pooledObject = iterator.next();
            
            // 检查是否过期
            if (now - pooledObject.getLastAccessTime() > config.maxIdleTime) {
                iterator.remove();
                factory.destroy(pooledObject.getObject());
                statistics.incrementDestroyed();
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            System.out.println("清理过期对象: " + cleaned + " 个");
        }
    }

    /**
     * 收缩池
     */
    private void shrinkPool() {
        int currentSize = pool.size();
        int targetSize = Math.max(config.initialSize, currentSize / 2);
        
        if (currentSize > config.maxSize * 0.8) {
            int toRemove = currentSize - targetSize;
            
            for (int i = 0; i < toRemove; i++) {
                PooledObject<T> pooledObject = pool.poll();
                if (pooledObject != null) {
                    factory.destroy(pooledObject.getObject());
                    statistics.incrementDestroyed();
                }
            }
            
            System.out.println("收缩池大小: " + currentSize + " -> " + pool.size());
        }
    }

    /**
     * 打印统计信息
     */
    private void printStatistics() {
        System.out.println("\n========== 内存池统计 ==========");
        System.out.println("当前池大小: " + pool.size());
        System.out.println("总创建数: " + statistics.getTotalCreated());
        System.out.println("总销毁数: " + statistics.getTotalDestroyed());
        System.out.println("获取请求数: " + statistics.getAcquireRequests());
        System.out.println("获取失败数: " + statistics.getAcquireFailures());
        System.out.println("归还请求数: " + statistics.getReleaseRequests());
        System.out.println("平均获取时间: " + statistics.getAvgAcquireTime() + "ms");
        System.out.println("池使用率: " + String.format("%.2f%%", 
            (config.maxSize - pool.size()) * 100.0 / config.maxSize));
        System.out.println("===============================\n");
    }

    /**
     * 关闭池
     */
    public void shutdown() {
        running = false;
        
        // 销毁所有对象
        PooledObject<T> pooledObject;
        while ((pooledObject = pool.poll()) != null) {
            factory.destroy(pooledObject.getObject());
            statistics.incrementDestroyed();
        }
        
        System.out.println("内存池已关闭");
    }

    /**
     * 获取统计信息
     */
    public PoolStatistics getStatistics() {
        return statistics;
    }

    /**
     * 对象工厂接口
     */
    public interface ObjectFactory<T> {
        /**
         * 创建对象
         */
        T create() throws Exception;
        
        /**
         * 验证对象
         */
        boolean validate(T object);
        
        /**
         * 重置对象
         */
        void reset(T object);
        
        /**
         * 销毁对象
         */
        void destroy(T object);
    }

    /**
     * 池化对象
     */
    private static class PooledObject<T> {
        private final T object;
        private long lastAccessTime;
        private int useCount;
        
        public PooledObject(T object) {
            this.object = object;
            this.lastAccessTime = System.currentTimeMillis();
            this.useCount = 0;
        }
        
        public T getObject() {
            return object;
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
        
        public void setLastAccessTime(long lastAccessTime) {
            this.lastAccessTime = lastAccessTime;
        }
        
        public int getUseCount() {
            return useCount;
        }
        
        public void incrementUseCount() {
            this.useCount++;
        }
    }

    /**
     * 池配置
     */
    public static class PoolConfig {
        private int initialSize = 10;           // 初始大小
        private int maxSize = 100;              // 最大大小
        private long maxWaitTime = 5000;        // 最大等待时间（毫秒）
        private long maxIdleTime = 60000;       // 最大空闲时间（毫秒）
        private long maintenanceInterval = 10000; // 维护间隔（毫秒）
        private boolean enableStatistics = true;  // 启用统计
        
        public PoolConfig initialSize(int initialSize) {
            this.initialSize = initialSize;
            return this;
        }
        
        public PoolConfig maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }
        
        public PoolConfig maxWaitTime(long maxWaitTime) {
            this.maxWaitTime = maxWaitTime;
            return this;
        }
        
        public PoolConfig maxIdleTime(long maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
            return this;
        }
        
        public PoolConfig maintenanceInterval(long maintenanceInterval) {
            this.maintenanceInterval = maintenanceInterval;
            return this;
        }
        
        public PoolConfig enableStatistics(boolean enableStatistics) {
            this.enableStatistics = enableStatistics;
            return this;
        }
    }

    /**
     * 池统计信息
     */
    public static class PoolStatistics {
        private final AtomicInteger totalCreated = new AtomicInteger(0);
        private final AtomicInteger totalDestroyed = new AtomicInteger(0);
        private final AtomicInteger acquireRequests = new AtomicInteger(0);
        private final AtomicInteger acquireFailures = new AtomicInteger(0);
        private final AtomicInteger releaseRequests = new AtomicInteger(0);
        private final List<Long> acquireTimes = new CopyOnWriteArrayList<>();
        
        public void incrementCreated() {
            totalCreated.incrementAndGet();
        }
        
        public void incrementDestroyed() {
            totalDestroyed.incrementAndGet();
        }
        
        public void incrementAcquireRequests() {
            acquireRequests.incrementAndGet();
        }
        
        public void incrementAcquireFailures() {
            acquireFailures.incrementAndGet();
        }
        
        public void incrementReleaseRequests() {
            releaseRequests.incrementAndGet();
        }
        
        public void recordAcquireTime(long time) {
            acquireTimes.add(time);
            
            // 保持最近1000条记录
            if (acquireTimes.size() > 1000) {
                acquireTimes.remove(0);
            }
        }
        
        public int getTotalCreated() {
            return totalCreated.get();
        }
        
        public int getTotalDestroyed() {
            return totalDestroyed.get();
        }
        
        public int getAcquireRequests() {
            return acquireRequests.get();
        }
        
        public int getAcquireFailures() {
            return acquireFailures.get();
        }
        
        public int getReleaseRequests() {
            return releaseRequests.get();
        }
        
        public double getAvgAcquireTime() {
            if (acquireTimes.isEmpty()) {
                return 0;
            }
            return acquireTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }

    /**
     * 示例：字节数组池
     */
    public static class ByteArrayFactory implements ObjectFactory<byte[]> {
        
        private final int arraySize;
        
        public ByteArrayFactory(int arraySize) {
            this.arraySize = arraySize;
        }
        
        @Override
        public byte[] create() {
            return new byte[arraySize];
        }
        
        @Override
        public boolean validate(byte[] object) {
            return object != null && object.length == arraySize;
        }
        
        @Override
        public void reset(byte[] object) {
            Arrays.fill(object, (byte) 0);
        }
        
        @Override
        public void destroy(byte[] object) {
            // 字节数组不需要特殊清理
        }
    }

    /**
     * 示例：StringBuilder池
     */
    public static class StringBuilderFactory implements ObjectFactory<StringBuilder> {
        
        private final int initialCapacity;
        
        public StringBuilderFactory(int initialCapacity) {
            this.initialCapacity = initialCapacity;
        }
        
        @Override
        public StringBuilder create() {
            return new StringBuilder(initialCapacity);
        }
        
        @Override
        public boolean validate(StringBuilder object) {
            return object != null;
        }
        
        @Override
        public void reset(StringBuilder object) {
            object.setLength(0);
        }
        
        @Override
        public void destroy(StringBuilder object) {
            // StringBuilder不需要特殊清理
        }
    }

    /**
     * 测试程序
     */
    public static void main(String[] args) throws Exception {
        System.out.println("========== 内存池测试 ==========\n");
        
        // 测试1：字节数组池
        testByteArrayPool();
        
        // 测试2：StringBuilder池
        testStringBuilderPool();
        
        // 测试3：并发测试
        testConcurrency();
    }

    /**
     * 测试字节数组池
     */
    private static void testByteArrayPool() throws Exception {
        System.out.println("1. 测试字节数组池:");
        
        PoolConfig config = new PoolConfig()
            .initialSize(5)
            .maxSize(20)
            .maxWaitTime(1000)
            .enableStatistics(true);
        
        SimpleMemoryPool<byte[]> pool = new SimpleMemoryPool<>(
            new ByteArrayFactory(1024), config);
        
        // 获取和归还对象
        List<byte[]> arrays = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] array = pool.acquire();
            arrays.add(array);
            System.out.println("获取对象 " + (i + 1));
        }
        
        for (byte[] array : arrays) {
            pool.release(array);
            System.out.println("归还对象");
        }
        
        Thread.sleep(2000);
        pool.shutdown();
        
        System.out.println();
    }

    /**
     * 测试StringBuilder池
     */
    private static void testStringBuilderPool() throws Exception {
        System.out.println("2. 测试StringBuilder池:");
        
        PoolConfig config = new PoolConfig()
            .initialSize(3)
            .maxSize(10)
            .enableStatistics(true);
        
        SimpleMemoryPool<StringBuilder> pool = new SimpleMemoryPool<>(
            new StringBuilderFactory(256), config);
        
        // 使用对象
        StringBuilder sb = pool.acquire();
        sb.append("Hello, World!");
        System.out.println("使用StringBuilder: " + sb);
        pool.release(sb);
        
        Thread.sleep(2000);
        pool.shutdown();
        
        System.out.println();
    }

    /**
     * 测试并发
     */
    private static void testConcurrency() throws Exception {
        System.out.println("3. 并发测试:");
        
        PoolConfig config = new PoolConfig()
            .initialSize(10)
            .maxSize(50)
            .enableStatistics(true);
        
        SimpleMemoryPool<byte[]> pool = new SimpleMemoryPool<>(
            new ByteArrayFactory(1024), config);
        
        int threadCount = 20;
        int iterations = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        byte[] array = pool.acquire();
                        // 模拟使用
                        Thread.sleep(1);
                        pool.release(array);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("并发测试完成:");
        System.out.println("  线程数: " + threadCount);
        System.out.println("  每线程迭代: " + iterations);
        System.out.println("  总耗时: " + duration + "ms");
        System.out.println("  QPS: " + (threadCount * iterations * 1000 / duration));
        
        Thread.sleep(2000);
        
        executor.shutdown();
        pool.shutdown();
    }
}

/**
 * 性能对比测试
 */
class PoolPerformanceTest {
    
    private static final int ITERATIONS = 100000;
    private static final int ARRAY_SIZE = 1024;
    
    public static void main(String[] args) throws Exception {
        System.out.println("========== 性能对比测试 ==========\n");
        
        // 测试1：不使用池
        testWithoutPool();
        
        // 测试2：使用池
        testWithPool();
    }
    
    private static void testWithoutPool() {
        System.out.println("1. 不使用对象池:");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] array = new byte[ARRAY_SIZE];
            // 使用数组
            array[0] = 1;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("  耗时: " + duration + "ms");
        System.out.println("  QPS: " + (ITERATIONS * 1000 / duration));
        System.out.println();
    }
    
    private static void testWithPool() throws Exception {
        System.out.println("2. 使用对象池:");
        
        SimpleMemoryPool.PoolConfig config = new SimpleMemoryPool.PoolConfig()
            .initialSize(10)
            .maxSize(50)
            .enableStatistics(false);
        
        SimpleMemoryPool<byte[]> pool = new SimpleMemoryPool<>(
            new SimpleMemoryPool.ByteArrayFactory(ARRAY_SIZE), config);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] array = pool.acquire();
            // 使用数组
            array[0] = 1;
            pool.release(array);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("  耗时: " + duration + "ms");
        System.out.println("  QPS: " + (ITERATIONS * 1000 / duration));
        
        pool.shutdown();
    }
}
