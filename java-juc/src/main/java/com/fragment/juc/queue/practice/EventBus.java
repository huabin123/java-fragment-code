package com.fragment.juc.queue.practice;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件总线实战 - 基于ConcurrentLinkedQueue的事件驱动架构
 * 
 * <p>场景：事件发布-订阅模式
 * <ul>
 *   <li>异步事件处理</li>
 *   <li>多订阅者支持</li>
 *   <li>事件类型路由</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>ConcurrentLinkedQueue实现事件队列</li>
 *   <li>无锁高性能</li>
 *   <li>线程安全</li>
 * </ul>
 * 
 * @author fragment
 */
public class EventBus {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 事件总线实战 ==========\n");

        SimpleEventBus eventBus = new SimpleEventBus();
        eventBus.start();

        // 场景1：单订阅者
        demonstrateSingleSubscriber(eventBus);

        Thread.sleep(1000);

        // 场景2：多订阅者
        demonstrateMultipleSubscribers(eventBus);

        Thread.sleep(1000);

        // 场景3：事件类型路由
        demonstrateEventTypeRouting(eventBus);

        Thread.sleep(2000);
        eventBus.shutdown();
    }

    /**
     * 场景1：单订阅者
     */
    private static void demonstrateSingleSubscriber(SimpleEventBus eventBus) {
        System.out.println("=== 场景1：单订阅者 ===\n");

        // 订阅事件
        eventBus.subscribe(UserEvent.class, event -> {
            System.out.println("[订阅者] 收到用户事件: " + event.getMessage());
        });

        // 发布事件
        eventBus.post(new UserEvent("用户登录"));
        eventBus.post(new UserEvent("用户注销"));

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景2：多订阅者
     */
    private static void demonstrateMultipleSubscribers(SimpleEventBus eventBus) {
        System.out.println("=== 场景2：多订阅者 ===\n");

        // 多个订阅者订阅同一事件
        eventBus.subscribe(OrderEvent.class, event -> {
            System.out.println("[订阅者1-邮件服务] 发送订单确认邮件: " + event.getOrderId());
        });

        eventBus.subscribe(OrderEvent.class, event -> {
            System.out.println("[订阅者2-库存服务] 扣减库存: " + event.getOrderId());
        });

        eventBus.subscribe(OrderEvent.class, event -> {
            System.out.println("[订阅者3-积分服务] 增加积分: " + event.getOrderId());
        });

        // 发布订单事件
        eventBus.post(new OrderEvent("ORDER-001"));

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景3：事件类型路由
     */
    private static void demonstrateEventTypeRouting(SimpleEventBus eventBus) {
        System.out.println("=== 场景3：事件类型路由 ===\n");

        // 不同类型事件的订阅者
        eventBus.subscribe(PaymentEvent.class, event -> {
            System.out.println("[支付服务] 处理支付: " + event.getAmount() + "元");
        });

        eventBus.subscribe(NotificationEvent.class, event -> {
            System.out.println("[通知服务] 发送通知: " + event.getMessage());
        });

        // 发布不同类型的事件
        eventBus.post(new PaymentEvent(100.0));
        eventBus.post(new NotificationEvent("您的订单已发货"));
        eventBus.post(new PaymentEvent(200.0));

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 简单事件总线
     */
    static class SimpleEventBus {
        // 事件队列
        private final ConcurrentLinkedQueue<Event> eventQueue = new ConcurrentLinkedQueue<>();
        
        // 订阅者映射：事件类型 -> 订阅者列表
        private final Map<Class<? extends Event>, CopyOnWriteArrayList<EventHandler<?>>> 
            subscribers = new ConcurrentHashMap<>();
        
        // 工作线程
        private final Thread[] workers;
        
        // 运行状态
        private volatile boolean running = false;
        
        // 统计
        private final AtomicInteger publishedCount = new AtomicInteger(0);
        private final AtomicInteger processedCount = new AtomicInteger(0);

        public SimpleEventBus() {
            this(3);  // 默认3个工作线程
        }

        public SimpleEventBus(int workerCount) {
            this.workers = new Thread[workerCount];
            for (int i = 0; i < workerCount; i++) {
                workers[i] = new EventWorker(i);
            }
        }

        /**
         * 启动事件总线
         */
        public void start() {
            running = true;
            for (Thread worker : workers) {
                worker.start();
            }
        }

        /**
         * 订阅事件
         */
        public <T extends Event> void subscribe(Class<T> eventType, EventHandler<T> handler) {
            subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                      .add(handler);
        }

        /**
         * 发布事件
         */
        public void post(Event event) {
            eventQueue.offer(event);
            publishedCount.incrementAndGet();
        }

        /**
         * 停止事件总线
         */
        public void shutdown() throws InterruptedException {
            running = false;
            
            // 等待队列清空
            while (!eventQueue.isEmpty()) {
                Thread.sleep(100);
            }
            
            // 停止工作线程
            for (Thread worker : workers) {
                worker.interrupt();
                worker.join(1000);
            }
            
            System.out.println("\n=== 事件总线统计 ===");
            System.out.println("发布事件数: " + publishedCount.get());
            System.out.println("处理事件数: " + processedCount.get());
        }

        /**
         * 事件工作线程
         */
        class EventWorker extends Thread {
            private final int workerId;

            public EventWorker(int workerId) {
                super("EventWorker-" + workerId);
                this.workerId = workerId;
            }

            @Override
            public void run() {
                while (running || !eventQueue.isEmpty()) {
                    Event event = eventQueue.poll();
                    if (event != null) {
                        processEvent(event);
                    } else {
                        Thread.yield();
                    }
                }
            }

            @SuppressWarnings("unchecked")
            private void processEvent(Event event) {
                CopyOnWriteArrayList<EventHandler<?>> handlers = 
                    subscribers.get(event.getClass());
                
                if (handlers != null) {
                    for (EventHandler<?> handler : handlers) {
                        try {
                            ((EventHandler<Event>) handler).handle(event);
                            processedCount.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("[Worker-" + workerId + "] 处理事件异常: " + 
                                             e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * 事件接口
     */
    interface Event {
    }

    /**
     * 事件处理器
     */
    interface EventHandler<T extends Event> {
        void handle(T event);
    }

    /**
     * 用户事件
     */
    static class UserEvent implements Event {
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
    static class OrderEvent implements Event {
        private final String orderId;

        public OrderEvent(String orderId) {
            this.orderId = orderId;
        }

        public String getOrderId() {
            return orderId;
        }
    }

    /**
     * 支付事件
     */
    static class PaymentEvent implements Event {
        private final double amount;

        public PaymentEvent(double amount) {
            this.amount = amount;
        }

        public double getAmount() {
            return amount;
        }
    }

    /**
     * 通知事件
     */
    static class NotificationEvent implements Event {
        private final String message;

        public NotificationEvent(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
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
