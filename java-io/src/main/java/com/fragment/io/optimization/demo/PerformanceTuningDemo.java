package com.fragment.io.optimization.demo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.lang.management.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能调优演示
 * 
 * 演示内容：
 * 1. EventLoopGroup线程数配置
 * 2. Channel参数优化
 * 3. ByteBuf池化
 * 4. JVM监控
 * 5. 性能指标统计
 * 
 * @author fragment
 */
public class PerformanceTuningDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 性能调优演示 ===\n");
        
        // 1. JVM信息
        System.out.println("--- JVM信息 ---");
        printJvmInfo();
        
        // 2. 线程配置建议
        System.out.println("\n--- 线程配置建议 ---");
        printThreadConfig();
        
        // 3. Netty优化配置
        System.out.println("\n--- Netty优化配置 ---");
        printNettyConfig();
        
        // 4. 启动优化的服务器
        System.out.println("\n--- 启动优化的服务器 ---");
        startOptimizedServer();
    }
    
    /**
     * 打印JVM信息
     */
    private static void printJvmInfo() {
        // 运行时信息
        Runtime runtime = Runtime.getRuntime();
        System.out.println("CPU核心数: " + runtime.availableProcessors());
        System.out.println("最大内存: " + (runtime.maxMemory() / 1024 / 1024) + "MB");
        System.out.println("已分配内存: " + (runtime.totalMemory() / 1024 / 1024) + "MB");
        System.out.println("空闲内存: " + (runtime.freeMemory() / 1024 / 1024) + "MB");
        
        // 内存使用情况
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        System.out.println("\n堆内存:");
        System.out.println("  初始: " + (heapUsage.getInit() / 1024 / 1024) + "MB");
        System.out.println("  已用: " + (heapUsage.getUsed() / 1024 / 1024) + "MB");
        System.out.println("  提交: " + (heapUsage.getCommitted() / 1024 / 1024) + "MB");
        System.out.println("  最大: " + (heapUsage.getMax() / 1024 / 1024) + "MB");
        
        System.out.println("\n非堆内存:");
        System.out.println("  初始: " + (nonHeapUsage.getInit() / 1024 / 1024) + "MB");
        System.out.println("  已用: " + (nonHeapUsage.getUsed() / 1024 / 1024) + "MB");
        System.out.println("  提交: " + (nonHeapUsage.getCommitted() / 1024 / 1024) + "MB");
        System.out.println("  最大: " + (nonHeapUsage.getMax() / 1024 / 1024) + "MB");
        
        // GC信息
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        System.out.println("\nGC信息:");
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            System.out.println("  " + gcBean.getName() + ":");
            System.out.println("    次数: " + gcBean.getCollectionCount());
            System.out.println("    耗时: " + gcBean.getCollectionTime() + "ms");
        }
        
        // 线程信息
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        System.out.println("\n线程信息:");
        System.out.println("  当前线程数: " + threadMXBean.getThreadCount());
        System.out.println("  峰值线程数: " + threadMXBean.getPeakThreadCount());
        System.out.println("  总启动线程数: " + threadMXBean.getTotalStartedThreadCount());
    }
    
    /**
     * 打印线程配置建议
     */
    private static void printThreadConfig() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        System.out.println("CPU核心数: " + cpuCores);
        System.out.println("\n线程数配置建议:");
        System.out.println("  Boss线程: 1 (只负责accept连接)");
        System.out.println("  Worker线程 (IO密集型): " + (cpuCores * 2));
        System.out.println("  Worker线程 (CPU密集型): " + (cpuCores + 1));
        System.out.println("  Worker线程 (混合型): " + cpuCores + " ~ " + (cpuCores * 2));
        System.out.println("\n业务线程池配置建议:");
        System.out.println("  核心线程数: " + cpuCores);
        System.out.println("  最大线程数: " + (cpuCores * 4));
        System.out.println("  队列大小: 1000 ~ 10000");
    }
    
    /**
     * 打印Netty配置建议
     */
    private static void printNettyConfig() {
        System.out.println("Channel参数配置:");
        System.out.println("  SO_BACKLOG: 1024 (连接队列大小)");
        System.out.println("  SO_KEEPALIVE: true (保持连接)");
        System.out.println("  TCP_NODELAY: true (禁用Nagle算法，降低延迟)");
        System.out.println("  SO_RCVBUF: 32KB (接收缓冲区)");
        System.out.println("  SO_SNDBUF: 32KB (发送缓冲区)");
        
        System.out.println("\nByteBuf配置:");
        System.out.println("  使用池化: PooledByteBufAllocator.DEFAULT");
        System.out.println("  使用直接内存: directBuffer()");
        
        System.out.println("\n写缓冲区水位线:");
        System.out.println("  低水位: 32KB");
        System.out.println("  高水位: 64KB");
    }
    
    /**
     * 启动优化的服务器
     */
    private static void startOptimizedServer() throws Exception {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        // 优化的线程配置
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(cpuCores * 2);
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                
                // 服务器端配置
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                
                // 客户端连接配置
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_RCVBUF, 32 * 1024)
                .childOption(ChannelOption.SO_SNDBUF, 32 * 1024)
                
                // 写缓冲区水位线
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                    new WriteBufferWaterMark(32 * 1024, 64 * 1024))
                
                // 使用池化ByteBuf
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new PerformanceMonitorHandler());
                    }
                });
            
            System.out.println("优化的服务器启动在端口: 8080");
            System.out.println("配置:");
            System.out.println("  Boss线程数: 1");
            System.out.println("  Worker线程数: " + (cpuCores * 2));
            System.out.println("  使用池化ByteBuf: 是");
            System.out.println("  TCP_NODELAY: 是");
            
            // 启动性能监控
            startPerformanceMonitor();
            
            ChannelFuture future = bootstrap.bind(8080).sync();
            
            System.out.println("\n服务器启动成功，按Ctrl+C停止");
            
            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
    
    /**
     * 启动性能监控
     */
    private static void startPerformanceMonitor() {
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10000); // 每10秒打印一次
                    
                    System.out.println("\n=== 性能监控 ===");
                    
                    // 内存使用
                    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
                    MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
                    System.out.println("堆内存使用: " + (heapUsage.getUsed() / 1024 / 1024) + "MB / " +
                        (heapUsage.getMax() / 1024 / 1024) + "MB");
                    
                    // GC统计
                    List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
                    for (GarbageCollectorMXBean gcBean : gcBeans) {
                        System.out.println(gcBean.getName() + " - 次数: " + gcBean.getCollectionCount() +
                            ", 耗时: " + gcBean.getCollectionTime() + "ms");
                    }
                    
                    // 线程数
                    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                    System.out.println("线程数: " + threadMXBean.getThreadCount());
                    
                    // 连接统计
                    System.out.println("总连接数: " + PerformanceMonitorHandler.getTotalConnections());
                    System.out.println("活跃连接: " + PerformanceMonitorHandler.getActiveConnections());
                    System.out.println("总接收字节: " + PerformanceMonitorHandler.getTotalBytesRead());
                    System.out.println("总发送字节: " + PerformanceMonitorHandler.getTotalBytesWritten());
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        monitorThread.setDaemon(true);
        monitorThread.setName("performance-monitor");
        monitorThread.start();
    }
    
    /**
     * 性能监控Handler
     */
    static class PerformanceMonitorHandler extends ChannelInboundHandlerAdapter {
        
        private static final AtomicLong totalConnections = new AtomicLong(0);
        private static final AtomicLong activeConnections = new AtomicLong(0);
        private static final AtomicLong totalBytesRead = new AtomicLong(0);
        private static final AtomicLong totalBytesWritten = new AtomicLong(0);
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            totalConnections.incrementAndGet();
            activeConnections.incrementAndGet();
            ctx.fireChannelActive();
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            activeConnections.decrementAndGet();
            ctx.fireChannelInactive();
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof io.netty.buffer.ByteBuf) {
                totalBytesRead.addAndGet(((io.netty.buffer.ByteBuf) msg).readableBytes());
            }
            
            // 简单回显
            ctx.writeAndFlush(msg).addListener(future -> {
                if (future.isSuccess() && msg instanceof io.netty.buffer.ByteBuf) {
                    totalBytesWritten.addAndGet(((io.netty.buffer.ByteBuf) msg).readableBytes());
                }
            });
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
        
        public static long getTotalConnections() {
            return totalConnections.get();
        }
        
        public static long getActiveConnections() {
            return activeConnections.get();
        }
        
        public static long getTotalBytesRead() {
            return totalBytesRead.get();
        }
        
        public static long getTotalBytesWritten() {
            return totalBytesWritten.get();
        }
    }
}
