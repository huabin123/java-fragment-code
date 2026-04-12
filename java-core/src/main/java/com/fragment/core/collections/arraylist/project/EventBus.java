package com.fragment.core.collections.arraylist.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 简单事件总线
 *
 * 使用 CopyOnWriteArrayList 管理监听器列表（读多写少场景）。
 * 演示 ArrayList 系列在观察者模式中的应用。
 */
public class EventBus {

    // 监听器注册/注销频率远低于事件发布频率 → CopyOnWriteArrayList 最合适
    private final Map<String, CopyOnWriteArrayList<Consumer<Object>>> listeners =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {
        EventBus bus = new EventBus();

        // 注册监听器
        bus.on("user.login", event -> System.out.println("[日志] 用户登录: " + event));
        bus.on("user.login", event -> System.out.println("[监控] 登录事件统计: " + event));
        bus.on("order.created", event -> System.out.println("[通知] 订单创建: " + event));

        // 发布事件
        bus.emit("user.login", "userId=123");
        bus.emit("order.created", "orderId=456");
        bus.emit("user.logout", "userId=123");  // 无监听器，忽略

        // 注销
        Consumer<Object> handler = event -> System.out.println("临时监听: " + event);
        bus.on("test.event", handler);
        bus.emit("test.event", "data1");
        bus.off("test.event", handler);
        bus.emit("test.event", "data2");  // 注销后不再触发

        System.out.println("\n监听器统计: " + bus.listenerCount());
    }

    public void on(String event, Consumer<Object> handler) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void off(String event, Consumer<Object> handler) {
        List<Consumer<Object>> handlers = listeners.get(event);
        if (handlers != null) handlers.remove(handler);
    }

    public void emit(String event, Object data) {
        List<Consumer<Object>> handlers = listeners.get(event);
        if (handlers == null || handlers.isEmpty()) return;
        // 遍历时无需加锁（CopyOnWriteArrayList 快照遍历）
        for (Consumer<Object> handler : handlers) {
            try {
                handler.accept(data);
            } catch (Exception e) {
                System.err.println("监听器异常: " + e.getMessage());
            }
        }
    }

    public Map<String, Integer> listenerCount() {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        listeners.forEach((k, v) -> counts.put(k, v.size()));
        return counts;
    }
}
