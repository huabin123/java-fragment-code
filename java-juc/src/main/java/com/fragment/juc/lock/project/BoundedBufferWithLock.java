package com.fragment.juc.lock.project;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于Lock和Condition实现的有界缓冲区
 * 
 * 功能：
 * 1. 生产者-消费者模式
 * 2. 使用两个Condition分离等待队列
 * 3. 支持阻塞的put和take操作
 * 4. 支持非阻塞的offer和poll操作
 * 
 * 核心技术：
 * - ReentrantLock：保证线程安全
 * - Condition：实现线程间协作
 * - 循环数组：实现有界缓冲区
 * 
 * @author fragment
 * @date 2026-01-01
 */
public class BoundedBufferWithLock<T> {
    
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();  // 生产者等待队列
    private final Condition notEmpty = lock.newCondition(); // 消费者等待队列
    
    private final Object[] items;
    private int putIndex;  // 下一个put的位置
    private int takeIndex; // 下一个take的位置
    private int count;     // 当前元素数量
    
    public BoundedBufferWithLock(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.items = new Object[capacity];
    }
    
    /**
     * 放入元素（阻塞）
     * 如果缓冲区满，则等待
     */
    public void put(T item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException();
        }
        
        lock.lock();
        try {
            // 等待缓冲区不满
            while (count == items.length) {
                System.out.println(Thread.currentThread().getName() + 
                    " 缓冲区已满，等待...");
                notFull.await();
            }
            
            // 放入元素
            enqueue(item);
            System.out.println(Thread.currentThread().getName() + 
                " 生产：" + item + "，当前数量：" + count);
            
            // 通知消费者
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 取出元素（阻塞）
     * 如果缓冲区空，则等待
     */
    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            // 等待缓冲区不空
            while (count == 0) {
                System.out.println(Thread.currentThread().getName() + 
                    " 缓冲区为空，等待...");
                notEmpty.await();
            }
            
            // 取出元素
            T item = dequeue();
            System.out.println(Thread.currentThread().getName() + 
                " 消费：" + item + "，当前数量：" + count);
            
            // 通知生产者
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 尝试放入元素（非阻塞）
     * @return 是否成功
     */
    public boolean offer(T item) {
        if (item == null) {
            throw new NullPointerException();
        }
        
        lock.lock();
        try {
            if (count == items.length) {
                return false; // 缓冲区满
            }
            enqueue(item);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 尝试取出元素（非阻塞）
     * @return 元素，如果为空返回null
     */
    @SuppressWarnings("unchecked")
    public T poll() {
        lock.lock();
        try {
            if (count == 0) {
                return null; // 缓冲区空
            }
            T item = dequeue();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取当前元素数量
     */
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取容量
     */
    public int capacity() {
        return items.length;
    }
    
    /**
     * 是否为空
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return count == 0;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 是否已满
     */
    public boolean isFull() {
        lock.lock();
        try {
            return count == items.length;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 清空缓冲区
     */
    public void clear() {
        lock.lock();
        try {
            for (int i = 0; i < items.length; i++) {
                items[i] = null;
            }
            putIndex = 0;
            takeIndex = 0;
            count = 0;
            // 通知所有等待的生产者
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 入队
     */
    private void enqueue(T item) {
        items[putIndex] = item;
        if (++putIndex == items.length) {
            putIndex = 0; // 循环
        }
        count++;
    }
    
    /**
     * 出队
     */
    @SuppressWarnings("unchecked")
    private T dequeue() {
        T item = (T) items[takeIndex];
        items[takeIndex] = null;
        if (++takeIndex == items.length) {
            takeIndex = 0; // 循环
        }
        count--;
        return item;
    }
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        System.out.println("=== 基于Lock的有界缓冲区演示 ===\n");
        
        BoundedBufferWithLock<Integer> buffer = new BoundedBufferWithLock<>(5);
        
        // 创建3个生产者
        for (int i = 0; i < 3; i++) {
            final int producerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        int item = producerId * 100 + j;
                        buffer.put(item);
                        Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "生产者-" + i).start();
        }
        
        // 创建2个消费者
        for (int i = 0; i < 2; i++) {
            final int consumerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < 15; j++) {
                        Integer item = buffer.take();
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "消费者-" + i).start();
        }
        
        // 监控线程
        new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    Thread.sleep(200);
                    System.out.println("\n[监控] 当前缓冲区大小：" + buffer.size() + 
                        "/" + buffer.capacity() + "\n");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "监控线程").start();
    }
}
