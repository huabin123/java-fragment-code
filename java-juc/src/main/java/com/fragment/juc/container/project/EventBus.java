package com.fragment.juc.container.project;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件总线实战 - 基于并发容器的事件驱动架构
 * 
 * <p>功能特性：
 * <ul>
 *   <li>发布-订阅模式</li>
 *   <li>支持同步/异步事件</li>
 *   <li>支持事件继承</li>
 *   <li>线程安全</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>ConcurrentHashMap存储订阅关系</li>
 *   <li>CopyOnWriteArrayList存储订阅者</li>
 *   <li>线程池处理异步事件</li>
 * </ul>
 * 
 * <p>应用场景：
 * <ul>
 *   <li>模块解耦</li>
 *   <li>事件驱动架构</li>
 *   <li>消息通知系统</li>
 * </ul>
 * 
 * @author fragment
 */
public class EventBus {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 事件总线实战 ==========\n");

        // 场景1：基本事件发布订阅
        demonstrateBasicEventBus();

        Thread.sleep(1000);

        // 场景2：异步事件处理
        demonstrateAsyncEventBus();

        Thread.sleep(1000);

        // 场景3：实际应用场景
        demonstratePracticalScenario();
    }

    /**
     * 场景1：基本事件发布订阅
     */
    private static void demonstrateBasicEventBus() {
        System.out.println("=== 场景1：基本事件发布订阅 ===\n");

        SimpleEventBus eventBus = new SimpleEventBus();

        // 订阅者1
        Object subscriber1 = new Object() {
            @Subscribe
            public void handleUserEvent(UserEvent event) {
                System.out.println("[订阅者1] 收到用户事件: " + event.getMessage());
            }
        };

        // 订阅者2
        Object subscriber2 = new Object() {
            @Subscribe
            public void handleUserEvent(UserEvent event) {
                System.out.println("[订阅者2] 收到用户事件: " + event.getMessage());
            }
        };

        // 注册订阅者
        eventBus.register(subscriber1);
        eventBus.register(subscriber2);

        // 发布事件
        System.out.println("发布事件:");
        eventBus.post(new UserEvent("用户登录"));
        eventBus.post(new UserEvent("用户注销"));

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景2：异步事件处理
     */
    private static void demonstrateAsyncEventBus() throws InterruptedException {
        System.out.println("=== 场景2：异步事件处理 ===\n");

        AsyncEventBus eventBus = new AsyncEventBus();

        // 订阅者
        Object subscriber = new Object() {
            @Subscribe
            public void handleOrderEvent(OrderEvent event) {
                System.out.println("[" + Thread.currentThread().getName() + 
                                 "] 处理订单: " + event.getOrderId());
                try {
                    Thread.sleep(500);  // 模拟耗时操作
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("[" + Thread.currentThread().getName() + 
                                 "] 订单处理完成: " + event.getOrderId());
            }
        };

        eventBus.register(subscriber);

        // 发布多个事件（异步处理）
        System.out.println("发布3个订单事件（异步处理）:");
        eventBus.post(new OrderEvent("ORDER-001"));
        eventBus.post(new OrderEvent("ORDER-002"));
        eventBus.post(new OrderEvent("ORDER-003"));
        System.out.println("事件发布完成，不阻塞主线程\n");

        Thread.sleep(2000);
        eventBus.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景3：实际应用场景
     */
    private static void demonstratePracticalScenario() throws InterruptedException {
        System.out.println("=== 场景3：实际应用场景 - 订单系统 ===\n");

        AsyncEventBus eventBus = new AsyncEventBus();

        // 邮件服务
        EmailService emailService = new EmailService();
        eventBus.register(emailService);

        // 库存服务
        InventoryService inventoryService = new InventoryService();
        eventBus.register(inventoryService);

        // 积分服务
        PointsService pointsService = new PointsService();
        eventBus.register(pointsService);

        // 模拟订单创建
        System.out.println("创建订单:");
        OrderCreatedEvent event = new OrderCreatedEvent("ORDER-12345", "user@example.com", 3);
        eventBus.post(event);
        System.out.println("订单创建完成，后续处理异步进行\n");

        Thread.sleep(2000);
        eventBus.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 简单事件总线
     */
    static class SimpleEventBus {
        // 事件类型 -> 订阅者方法列表
        private final Map<Class<?>, CopyOnWriteArrayList<SubscriberMethod>> 
            subscribers = new ConcurrentHashMap<>();

        /**
         * 注册订阅者
         */
        public void register(Object subscriber) {
            // 扫描订阅者的所有方法
            for (Method method : subscriber.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Subscribe.class)) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length == 1) {
                        Class<?> eventType = paramTypes[0];
                        
                        subscribers.computeIfAbsent(eventType, 
                            k -> new CopyOnWriteArrayList<>())
                            .add(new SubscriberMethod(subscriber, method));
                    }
                }
            }
        }

        /**
         * 发布事件
         */
        public void post(Object event) {
            Class<?> eventType = event.getClass();
            List<SubscriberMethod> methods = subscribers.get(eventType);
            
            if (methods != null) {
                for (SubscriberMethod method : methods) {
                    method.invoke(event);
                }
            }
        }
    }

    /**
     * 异步事件总线
     */
    static class AsyncEventBus extends SimpleEventBus {
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        @Override
        public void post(Object event) {
            executor.submit(() -> super.post(event));
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 订阅者方法
     */
    static class SubscriberMethod {
        private final Object subscriber;
        private final Method method;

        public SubscriberMethod(Object subscriber, Method method) {
            this.subscriber = subscriber;
            this.method = method;
            this.method.setAccessible(true);
        }

        public void invoke(Object event) {
            try {
                method.invoke(subscriber, event);
            } catch (Exception e) {
                System.err.println("事件处理异常: " + e.getMessage());
            }
        }
    }

    /**
     * 订阅注解
     */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @interface Subscribe {
    }

    /**
     * 用户事件
     */
    static class UserEvent {
        private final String message;

        public UserEvent(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 订单事件
     */
    static class OrderEvent {
        private final String orderId;

        public OrderEvent(String orderId) {
            this.orderId = orderId;
        }

        public String getOrderId() {
            return orderId;
        }
    }

    /**
     * 订单创建事件
     */
    static class OrderCreatedEvent {
        private final String orderId;
        private final String userEmail;
        private final int quantity;

        public OrderCreatedEvent(String orderId, String userEmail, int quantity) {
            this.orderId = orderId;
            this.userEmail = userEmail;
            this.quantity = quantity;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    /**
     * 邮件服务
     */
    static class EmailService {
        @Subscribe
        public void onOrderCreated(OrderCreatedEvent event) {
            System.out.println("[邮件服务] 发送订单确认邮件到: " + event.getUserEmail());
            System.out.println("           订单号: " + event.getOrderId());
        }
    }

    /**
     * 库存服务
     */
    static class InventoryService {
        @Subscribe
        public void onOrderCreated(OrderCreatedEvent event) {
            System.out.println("[库存服务] 扣减库存: " + event.getQuantity() + "件");
            System.out.println("           订单号: " + event.getOrderId());
        }
    }

    /**
     * 积分服务
     */
    static class PointsService {
        @Subscribe
        public void onOrderCreated(OrderCreatedEvent event) {
            int points = event.getQuantity() * 10;
            System.out.println("[积分服务] 增加积分: " + points + "分");
            System.out.println("           订单号: " + event.getOrderId());
        }
    }

    /**
     * 创建分隔线
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
