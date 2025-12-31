package com.fragment.core.threadpool.project;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实际项目Demo：订单处理系统
 * 
 * <p>场景：电商系统的订单处理，包括：
 * <ul>
 *   <li>订单验证</li>
 *   <li>库存扣减</li>
 *   <li>支付处理</li>
 *   <li>发送通知</li>
 * </ul>
 * 
 * @author fragment
 */
public class OrderProcessingSystem {
    
    /** 订单处理线程池 - IO密集型 */
    private final ThreadPoolExecutor orderPool;
    
    /** 通知发送线程池 - IO密集型 */
    private final ThreadPoolExecutor notificationPool;
    
    /** 订单计数器 */
    private final AtomicInteger orderCounter = new AtomicInteger(0);
    
    /** 成功订单数 */
    private final AtomicInteger successCount = new AtomicInteger(0);
    
    /** 失败订单数 */
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    /** 订单处理器 */
    private final OrderProcessor orderProcessor;
    
    public OrderProcessingSystem() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        
        // 订单处理线程池：IO密集型，线程数较多
        this.orderPool = new ThreadPoolExecutor(
            cpuCount * 2,
            cpuCount * 4,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new CustomThreadFactory("order-processor"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 通知发送线程池：IO密集型，独立线程池避免相互影响
        this.notificationPool = new ThreadPoolExecutor(
            cpuCount,
            cpuCount * 2,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new CustomThreadFactory("notification-sender"),
            new ThreadPoolExecutor.DiscardPolicy()  // 通知失败可以丢弃
        );
        
        // 创建订单处理器
        this.orderProcessor = new OrderProcessor(notificationPool, successCount, failureCount);
        
        // 启动监控
        startMonitoring();
    }
    
    /**
     * 提交订单
     */
    public void submitOrder(Order order) {
        orderPool.execute(() -> orderProcessor.processOrder(order));
    }
    
    /**
     * 启动监控
     */
    private void startMonitoring() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
            new CustomThreadFactory("monitor")
        );
        
        monitor.scheduleAtFixedRate(() -> {
            System.out.println("\n========== 系统监控 ==========");
            System.out.println("订单处理线程池:");
            printPoolStatus(orderPool);
            System.out.println("\n通知发送线程池:");
            printPoolStatus(notificationPool);
            System.out.println("\n业务指标:");
            System.out.println("  总订单数: " + orderCounter.get());
            System.out.println("  成功订单: " + successCount.get());
            System.out.println("  失败订单: " + failureCount.get());
            System.out.println("  成功率: " + String.format("%.2f%%", 
                successCount.get() * 100.0 / Math.max(1, orderCounter.get())));
            System.out.println("==============================\n");
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 打印线程池状态
     */
    private void printPoolStatus(ThreadPoolExecutor pool) {
        System.out.println("  核心线程数: " + pool.getCorePoolSize());
        System.out.println("  最大线程数: " + pool.getMaximumPoolSize());
        System.out.println("  当前线程数: " + pool.getPoolSize());
        System.out.println("  活跃线程数: " + pool.getActiveCount());
        System.out.println("  队列大小: " + pool.getQueue().size());
        System.out.println("  已完成任务: " + pool.getCompletedTaskCount());
    }
    
    /**
     * 关闭系统
     */
    public void shutdown() {
        log("系统开始关闭...");
        
        orderPool.shutdown();
        notificationPool.shutdown();
        
        try {
            if (!orderPool.awaitTermination(60, TimeUnit.SECONDS)) {
                orderPool.shutdownNow();
            }
            if (!notificationPool.awaitTermination(60, TimeUnit.SECONDS)) {
                notificationPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            orderPool.shutdownNow();
            notificationPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log("系统已关闭");
    }
    
    /**
     * 日志输出
     */
    private void log(String message) {
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        );
        System.out.println("[" + timestamp + "] [" + 
                         Thread.currentThread().getName() + "] " + message);
    }
    
    /**
     * 自定义线程工厂
     */
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        public CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setUncaughtExceptionHandler((thread, throwable) -> {
                System.err.println("线程 " + thread.getName() + " 发生异常:");
                throwable.printStackTrace();
            });
            return t;
        }
    }
    
    /**
     * 订单实体
     */
    public static class Order {
        private final String orderId;
        private final String userId;
        private final double amount;
        
        public Order(String orderId, String userId, double amount) {
            this.orderId = orderId;
            this.userId = userId;
            this.amount = amount;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public double getAmount() {
            return amount;
        }
    }
    
    /**
     * 测试主方法
     */
    public static void main(String[] args) throws InterruptedException {
        OrderProcessingSystem system = new OrderProcessingSystem();
        
        // 模拟订单提交
        for (int i = 0; i < 100; i++) {
            Order order = new Order(
                "ORD" + String.format("%06d", i),
                "USER" + (i % 10),
                Math.random() * 1000 + 100
            );
            
            system.submitOrder(order);
            system.orderCounter.incrementAndGet();
            
            // 模拟订单间隔
            Thread.sleep(50);
        }
        
        // 等待处理完成
        Thread.sleep(30000);
        
        // 关闭系统
        system.shutdown();
    }
}
