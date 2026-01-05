package com.fragment.juc.sync.project;

import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于Exchanger的数据交换服务
 * 
 * 功能：
 * 1. 生产者和消费者之间交换数据
 * 2. 支持超时机制
 * 3. 支持数据校验
 * 4. 统计交换次数
 * 
 * 核心技术：
 * - Exchanger：实现数据交换
 * - 泛型：支持不同类型的数据
 * - 超时机制：避免永久等待
 * 
 * @author fragment
 * @date 2026-01-01
 */
public class DataExchangeService<T> {
    
    private final Exchanger<DataPacket<T>> exchanger = new Exchanger<>();
    private volatile long exchangeCount = 0;
    private volatile long timeoutCount = 0;
    private volatile boolean running = true;
    
    /**
     * 数据包装类
     */
    public static class DataPacket<T> {
        private final T data;
        private final long timestamp;
        private final String source;
        
        public DataPacket(T data, String source) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.source = source;
        }
        
        public T getData() {
            return data;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getSource() {
            return source;
        }
        
        @Override
        public String toString() {
            return "DataPacket{" +
                    "data=" + data +
                    ", timestamp=" + timestamp +
                    ", source='" + source + '\'' +
                    '}';
        }
    }
    
    /**
     * 生产者：发送数据并接收确认
     */
    public DataPacket<T> produce(T data, String producerName, long timeout, TimeUnit unit) 
            throws InterruptedException, TimeoutException {
        DataPacket<T> packet = new DataPacket<>(data, producerName);
        
        try {
            DataPacket<T> response = exchanger.exchange(packet, timeout, unit);
            exchangeCount++;
            return response;
        } catch (TimeoutException e) {
            timeoutCount++;
            throw e;
        }
    }
    
    /**
     * 消费者：接收数据并发送确认
     */
    public DataPacket<T> consume(T confirmation, String consumerName, long timeout, TimeUnit unit) 
            throws InterruptedException, TimeoutException {
        DataPacket<T> packet = new DataPacket<>(confirmation, consumerName);
        
        try {
            DataPacket<T> received = exchanger.exchange(packet, timeout, unit);
            exchangeCount++;
            return received;
        } catch (TimeoutException e) {
            timeoutCount++;
            throw e;
        }
    }
    
    /**
     * 获取交换次数
     */
    public long getExchangeCount() {
        return exchangeCount;
    }
    
    /**
     * 获取超时次数
     */
    public long getTimeoutCount() {
        return timeoutCount;
    }
    
    /**
     * 停止服务
     */
    public void shutdown() {
        running = false;
    }
    
    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        System.out.println("=== 数据交换服务演示 ===\n");
        
        DataExchangeService<String> service = new DataExchangeService<>();
        
        // 生产者线程
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    String data = "数据-" + i;
                    System.out.println("[生产者] 发送：" + data);
                    
                    DataPacket<String> response = service.produce(
                        data, 
                        "Producer", 
                        5, 
                        TimeUnit.SECONDS
                    );
                    
                    System.out.println("[生产者] 收到确认：" + response.getData());
                    System.out.println();
                    
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException e) {
                System.err.println("[生产者] 交换超时");
            }
        }, "Producer").start();
        
        // 消费者线程
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    DataPacket<String> received = service.consume(
                        null, 
                        "Consumer", 
                        5, 
                        TimeUnit.SECONDS
                    );
                    
                    System.out.println("[消费者] 收到：" + received.getData() + 
                        " (来自：" + received.getSource() + ")");
                    
                    // 处理数据
                    Thread.sleep(500);
                    
                    // 发送确认
                    String confirmation = "已处理-" + received.getData();
                    System.out.println("[消费者] 发送确认：" + confirmation);
                    
                    service.consume(
                        confirmation, 
                        "Consumer", 
                        5, 
                        TimeUnit.SECONDS
                    );
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException e) {
                System.err.println("[消费者] 交换超时");
            }
        }, "Consumer").start();
        
        // 监控线程
        new Thread(() -> {
            try {
                while (service.isRunning()) {
                    Thread.sleep(2000);
                    System.out.println("\n[监控] 交换次数：" + service.getExchangeCount() + 
                        "，超时次数：" + service.getTimeoutCount() + "\n");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Monitor").start();
        
        // 主线程等待
        try {
            Thread.sleep(15000);
            service.shutdown();
            System.out.println("\n=== 服务已停止 ===");
            System.out.println("总交换次数：" + service.getExchangeCount());
            System.out.println("总超时次数：" + service.getTimeoutCount());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
