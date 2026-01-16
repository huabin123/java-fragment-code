package com.fragment.io.optimization.project.benchmark;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能基准测试框架
 *
 * 功能特性：
 * 1. I/O性能测试（BIO vs NIO vs 零拷贝）
 * 2. 连接池性能测试
 * 3. 内存池性能测试
 * 4. Netty性能测试
 * 5. 并发性能测试
 * 6. 详细的性能报告
 *
 * @author fragment
 */
public class PerformanceBenchmark {

    private static final String TEST_FILE = "benchmark_test.dat";
    private static final int FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public static void main(String[] args) throws Exception {
        System.out.println("=== 性能基准测试 ===\n");

        // 创建测试文件
        createTestFile();

        // 1. I/O性能测试
        System.out.println("--- I/O性能测试 ---");
        testIOPerformance();

        // 2. 连接池性能测试
        System.out.println("\n--- 连接池性能测试 ---");
        testConnectionPoolPerformance();

        // 3. 内存池性能测试
        System.out.println("\n--- 内存池性能测试 ---");
        testMemoryPoolPerformance();

        // 4. Netty性能测试
        System.out.println("\n--- Netty性能测试 ---");
        testNettyPerformance();

        // 清理
        new File(TEST_FILE).delete();

        System.out.println("\n=== 测试完成 ===");
    }

    /**
     * 创建测试文件
     */
    private static void createTestFile() throws IOException {
        System.out.println("创建测试文件: " + TEST_FILE + " (10MB)\n");

        try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "rw")) {
            file.setLength(FILE_SIZE);
            byte[] data = new byte[1024];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) i;
            }
            for (int i = 0; i < FILE_SIZE / 1024; i++) {
                file.write(data);
            }
        }
    }

    /**
     * I/O性能测试
     */
    private static void testIOPerformance() throws Exception {
        int iterations = 10;

        // 测试1：传统IO
        long time1 = benchmarkTraditionalIO(iterations);
        System.out.println("传统IO (FileInputStream):     " + time1 + "ms");

        // 测试2：NIO
        long time2 = benchmarkNIO(iterations);
        System.out.println("NIO (FileChannel):            " + time2 + "ms");

        // 测试3：DirectBuffer
        long time3 = benchmarkDirectBuffer(iterations);
        System.out.println("DirectBuffer:                 " + time3 + "ms");

        // 测试4：mmap
        long time4 = benchmarkMmap(iterations);
        System.out.println("mmap (MappedByteBuffer):      " + time4 + "ms");

        System.out.println("\n性能对比:");
        System.out.println("  NIO vs 传统IO:        " + String.format("%.2f", (double) time1 / time2) + "x");
        System.out.println("  DirectBuffer vs 传统IO: " + String.format("%.2f", (double) time1 / time3) + "x");
        System.out.println("  mmap vs 传统IO:       " + String.format("%.2f", (double) time1 / time4) + "x");
    }

    private static long benchmarkTraditionalIO(int iterations) throws IOException {
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            try (FileInputStream fis = new FileInputStream(TEST_FILE)) {
                byte[] buffer = new byte[4096];
                while (fis.read(buffer) != -1) {
                    // 读取数据
                }
            }
        }

        return System.currentTimeMillis() - start;
    }

    private static long benchmarkNIO(int iterations) throws IOException {
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "r");
                 FileChannel channel = file.getChannel()) {

                ByteBuffer buffer = ByteBuffer.allocate(4096);
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    buffer.clear();
                }
            }
        }

        return System.currentTimeMillis() - start;
    }

    private static long benchmarkDirectBuffer(int iterations) throws IOException {
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "r");
                 FileChannel channel = file.getChannel()) {

                ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    buffer.clear();
                }
            }
        }

        return System.currentTimeMillis() - start;
    }

    private static long benchmarkMmap(int iterations) throws IOException {
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "r");
                 FileChannel channel = file.getChannel()) {

                MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.READ_ONLY, 0, channel.size());

                while (buffer.hasRemaining()) {
                    buffer.get();
                }
            }
        }

        return System.currentTimeMillis() - start;
    }

    /**
     * 连接池性能测试
     */
    private static void testConnectionPoolPerformance() throws Exception {
        String url = "jdbc:h2:mem:benchmark";
        String username = "sa";
        String password = "";

        // 初始化数据库
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE test (id INT PRIMARY KEY, data VARCHAR(100))");
            stmt.execute("INSERT INTO test VALUES (1, 'test data')");
        }

        int operations = 1000;

        // 测试1：不使用连接池
        long time1 = benchmarkWithoutPool(url, username, password, operations);
        System.out.println("不使用连接池:  " + time1 + "ms");

        // 测试2：使用连接池
        long time2 = benchmarkWithPool(url, username, password, operations);
        System.out.println("使用连接池:    " + time2 + "ms");

        System.out.println("性能提升: " + String.format("%.2f", (double) time1 / time2) + "x");
    }

    private static long benchmarkWithoutPool(String url, String username, String password,
                                            int operations) throws Exception {
        long start = System.currentTimeMillis();

        for (int i = 0; i < operations; i++) {
            try (Connection conn = DriverManager.getConnection(url, username, password);
                 Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM test");
            }
        }

        return System.currentTimeMillis() - start;
    }

    private static long benchmarkWithPool(String url, String username, String password,
                                         int operations) throws Exception {
        SimpleConnectionPool pool = new SimpleConnectionPool(url, username, password, 10);

        long start = System.currentTimeMillis();

        for (int i = 0; i < operations; i++) {
            Connection conn = pool.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.executeQuery("SELECT * FROM test");
            }
            pool.releaseConnection(conn);
        }

        long elapsed = System.currentTimeMillis() - start;
        pool.shutdown();

        return elapsed;
    }

    /**
     * 简单连接池（用于测试）
     */
    static class SimpleConnectionPool {
        private final BlockingQueue<Connection> pool;
        private final String url;
        private final String username;
        private final String password;

        public SimpleConnectionPool(String url, String username, String password, int size)
                throws Exception {
            this.url = url;
            this.username = username;
            this.password = password;
            this.pool = new LinkedBlockingQueue<>(size);

            for (int i = 0; i < size; i++) {
                pool.offer(DriverManager.getConnection(url, username, password));
            }
        }

        public Connection getConnection() throws Exception {
            return pool.take();
        }

        public void releaseConnection(Connection conn) {
            pool.offer(conn);
        }

        public void shutdown() throws Exception {
            for (Connection conn : pool) {
                conn.close();
            }
        }
    }

    /**
     * 内存池性能测试
     */
    private static void testMemoryPoolPerformance() throws Exception {
        int operations = 100000;

        // 测试1：不使用对象池
        long time1 = benchmarkWithoutObjectPool(operations);
        System.out.println("不使用对象池:  " + time1 + "ms");

        // 测试2：使用对象池
        long time2 = benchmarkWithObjectPool(operations);
        System.out.println("使用对象池:    " + time2 + "ms");

        // 测试3：不使用ByteBuf池
        long time3 = benchmarkWithoutByteBufPool(operations);
        System.out.println("不使用ByteBuf池: " + time3 + "ms");

        // 测试4：使用ByteBuf池
        long time4 = benchmarkWithByteBufPool(operations);
        System.out.println("使用ByteBuf池:   " + time4 + "ms");

        System.out.println("\n性能对比:");
        System.out.println("  对象池: " + String.format("%.2f", (double) time1 / time2) + "x");
        System.out.println("  ByteBuf池: " + String.format("%.2f", (double) time3 / time4) + "x");
    }

    private static long benchmarkWithoutObjectPool(int operations) {
        long start = System.currentTimeMillis();

        for (int i = 0; i < operations; i++) {
            TestObject obj = new TestObject();
            obj.setData("test data " + i);
            // 使用对象
        }

        return System.currentTimeMillis() - start;
    }

    private static long benchmarkWithObjectPool(int operations) {
        ObjectPool<TestObject> pool = new ObjectPool<>(100);

        long start = System.currentTimeMillis();

        for (int i = 0; i < operations; i++) {
            TestObject obj = pool.borrow();
            obj.setData("test data " + i);
            pool.returnObject(obj);
        }

        return System.currentTimeMillis() - start;
    }

    private static long benchmarkWithoutByteBufPool(int operations) {
        long start = System.currentTimeMillis();

        for (int i = 0; i < operations; i++) {
            ByteBuf buf = Unpooled.buffer(1024);
            buf.writeBytes("test data".getBytes());
            buf.release();
        }

        return System.currentTimeMillis() - start;
    }

    private static long benchmarkWithByteBufPool(int operations) {
        long start = System.currentTimeMillis();

        for (int i = 0; i < operations; i++) {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer(1024);
            buf.writeBytes("test data".getBytes());
            buf.release();
        }

        return System.currentTimeMillis() - start;
    }

    /**
     * 测试对象
     */
    static class TestObject {
        private String data;

        public void setData(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }

    /**
     * 简单对象池
     */
    static class ObjectPool<T> {
        private final BlockingQueue<T> pool;

        public ObjectPool(int size) {
            this.pool = new LinkedBlockingQueue<>(size);
        }

        @SuppressWarnings("unchecked")
        public T borrow() {
            T obj = pool.poll();
            if (obj == null) {
                try {
                    obj = (T) new TestObject();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return obj;
        }

        public void returnObject(T obj) {
            pool.offer(obj);
        }
    }

    /**
     * Netty性能测试
     */
    private static void testNettyPerformance() throws Exception {
        // 启动服务器
        NettyBenchmarkServer server = new NettyBenchmarkServer(9999);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // 等待服务器启动
        Thread.sleep(1000);

        // 并发测试
        int threads = 10;
        int requestsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicLong totalTime = new AtomicLong(0);

        long start = System.currentTimeMillis();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    long threadStart = System.currentTimeMillis();

                    for (int j = 0; j < requestsPerThread; j++) {
                        java.nio.channels.SocketChannel channel =
                            java.nio.channels.SocketChannel.open(
                                new InetSocketAddress("localhost", 9999));

                        ByteBuffer buffer = ByteBuffer.wrap("PING\n".getBytes());
                        channel.write(buffer);

                        buffer.clear();
                        channel.read(buffer);

                        channel.close();
                    }

                    totalTime.addAndGet(System.currentTimeMillis() - threadStart);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long elapsed = System.currentTimeMillis() - start;

        int totalRequests = threads * requestsPerThread;
        double qps = totalRequests * 1000.0 / elapsed;
        double avgLatency = (double) totalTime.get() / totalRequests;

        System.out.println("并发线程数: " + threads);
        System.out.println("总请求数:   " + totalRequests);
        System.out.println("总耗时:     " + elapsed + "ms");
        System.out.println("QPS:        " + String.format("%.2f", qps));
        System.out.println("平均延迟:   " + String.format("%.2f", avgLatency) + "ms");

        executor.shutdown();
        server.shutdown();
    }

    /**
     * Netty基准测试服务器
     */
    static class NettyBenchmarkServer {
        private final int port;
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;

        public NettyBenchmarkServer(int port) {
            this.port = port;
        }

        public void start() throws Exception {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new BenchmarkHandler());
                        }
                    });

                ChannelFuture future = bootstrap.bind(port).sync();
                future.channel().closeFuture().sync();
            } finally {
                shutdown();
            }
        }

        public void shutdown() {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
    }

    /**
     * 基准测试处理器
     */
    static class BenchmarkHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // 简单回显
            ctx.writeAndFlush(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
