package com.fragment.core.threadpool.project;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单处理器
 * 
 * <p>负责订单的业务逻辑处理，包括：
 * <ul>
 *   <li>订单验证</li>
 *   <li>库存扣减</li>
 *   <li>支付处理</li>
 *   <li>发送通知</li>
 * </ul>
 * 
 * @author fragment
 */
public class OrderProcessor {
    
    /** 通知发送线程池 */
    private final ThreadPoolExecutor notificationPool;
    
    /** 成功订单数 */
    private final AtomicInteger successCount;
    
    /** 失败订单数 */
    private final AtomicInteger failureCount;
    
    /**
     * 构造函数
     * 
     * @param notificationPool 通知发送线程池
     * @param successCount 成功订单计数器
     * @param failureCount 失败订单计数器
     */
    public OrderProcessor(ThreadPoolExecutor notificationPool, 
                         AtomicInteger successCount, 
                         AtomicInteger failureCount) {
        this.notificationPool = notificationPool;
        this.successCount = successCount;
        this.failureCount = failureCount;
    }
    
    /**
     * 处理订单
     * 
     * @param order 订单
     */
    public void processOrder(OrderProcessingSystem.Order order) {
        String orderId = order.getOrderId();
        log("开始处理订单: " + orderId);
        
        try {
            // 1. 验证订单
            validateOrder(order);
            log("订单验证通过: " + orderId);
            
            // 2. 扣减库存
            deductInventory(order);
            log("库存扣减成功: " + orderId);
            
            // 3. 处理支付
            processPayment(order);
            log("支付处理成功: " + orderId);
            
            // 4. 异步发送通知
            sendNotification(order);
            
            successCount.incrementAndGet();
            log("订单处理完成: " + orderId);
            
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log("订单处理失败: " + orderId + ", 原因: " + e.getMessage());
            
            // 发送失败通知
            sendFailureNotification(order, e);
        }
    }
    
    /**
     * 验证订单
     * 
     * @param order 订单
     * @throws Exception 验证失败
     */
    private void validateOrder(OrderProcessingSystem.Order order) throws Exception {
        // 模拟验证逻辑
        Thread.sleep(100);
        
        if (order.getAmount() <= 0) {
            throw new Exception("订单金额无效");
        }
    }
    
    /**
     * 扣减库存
     * 
     * @param order 订单
     * @throws Exception 库存不足
     */
    private void deductInventory(OrderProcessingSystem.Order order) throws Exception {
        // 模拟数据库操作
        Thread.sleep(200);
        
        // 模拟库存不足
        if (Math.random() < 0.1) {
            throw new Exception("库存不足");
        }
    }
    
    /**
     * 处理支付
     * 
     * @param order 订单
     * @throws Exception 支付失败
     */
    private void processPayment(OrderProcessingSystem.Order order) throws Exception {
        // 模拟支付接口调用
        Thread.sleep(300);
        
        // 模拟支付失败
        if (Math.random() < 0.05) {
            throw new Exception("支付失败");
        }
    }
    
    /**
     * 发送通知（异步）
     * 
     * @param order 订单
     */
    private void sendNotification(OrderProcessingSystem.Order order) {
        notificationPool.execute(() -> {
            try {
                // 模拟发送短信/邮件
                Thread.sleep(500);
                log("通知发送成功: " + order.getOrderId());
            } catch (Exception e) {
                log("通知发送失败: " + order.getOrderId());
            }
        });
    }
    
    /**
     * 发送失败通知
     * 
     * @param order 订单
     * @param e 异常信息
     */
    private void sendFailureNotification(OrderProcessingSystem.Order order, Exception e) {
        notificationPool.execute(() -> {
            try {
                Thread.sleep(500);
                log("失败通知发送成功: " + order.getOrderId());
            } catch (Exception ex) {
                log("失败通知发送失败: " + order.getOrderId());
            }
        });
    }
    
    /**
     * 日志输出
     * 
     * @param message 日志消息
     */
    private void log(String message) {
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        );
        System.out.println("[" + timestamp + "] [" + 
                         Thread.currentThread().getName() + "] " + message);
    }
}
