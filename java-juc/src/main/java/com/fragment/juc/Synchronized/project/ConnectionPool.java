package com.fragment.juc.Synchronized.project;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;

/**
 * 数据库连接池实现（使用Synchronized）
 * 
 * 功能：
 * 1. 连接池管理
 * 2. 连接获取与归还
 * 3. 连接超时处理
 * 4. 连接池监控
 * 
 * @author huabin
 */
public class ConnectionPool {
    
    /**
     * 简单的数据库连接池实现
     */
    static class SimpleConnectionPool {
        private final LinkedList<Connection> availableConnections = new LinkedList<>();
        private final LinkedList<Connection> usedConnections = new LinkedList<>();
        private final int maxPoolSize;
        private final String url;
        private final String username;
        private final String password;
        
        public SimpleConnectionPool(String url, String username, String password, int maxPoolSize) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.maxPoolSize = maxPoolSize;
        }
        
        // 初始化连接池
        public synchronized void initialize(int initialSize) throws SQLException {
            for (int i = 0; i < initialSize; i++) {
                availableConnections.add(createConnection());
            }
            System.out.println("连接池初始化完成，初始连接数: " + initialSize);
        }
        
        // 创建新连接
        private Connection createConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        
        // 获取连接
        public synchronized Connection getConnection() throws SQLException, InterruptedException {
            // 如果有可用连接，直接返回
            if (!availableConnections.isEmpty()) {
                Connection conn = availableConnections.removeFirst();
                usedConnections.add(conn);
                System.out.println(Thread.currentThread().getName() + 
                    " 获取连接，可用: " + availableConnections.size() + 
                    ", 使用中: " + usedConnections.size());
                return conn;
            }
            
            // 如果未达到最大连接数，创建新连接
            if (usedConnections.size() < maxPoolSize) {
                Connection conn = createConnection();
                usedConnections.add(conn);
                System.out.println(Thread.currentThread().getName() + 
                    " 创建新连接，使用中: " + usedConnections.size());
                return conn;
            }
            
            // 等待连接归还
            System.out.println(Thread.currentThread().getName() + " 等待连接...");
            wait();
            return getConnection(); // 递归调用
        }
        
        // 获取连接（带超时）
        public synchronized Connection getConnection(long timeoutMillis) 
                throws SQLException, InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            
            while (availableConnections.isEmpty() && usedConnections.size() >= maxPoolSize) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new SQLException("获取连接超时");
                }
                wait(remaining);
            }
            
            return getConnection();
        }
        
        // 归还连接
        public synchronized void releaseConnection(Connection conn) {
            if (usedConnections.remove(conn)) {
                availableConnections.add(conn);
                System.out.println(Thread.currentThread().getName() + 
                    " 归还连接，可用: " + availableConnections.size() + 
                    ", 使用中: " + usedConnections.size());
                notify(); // 唤醒等待的线程
            }
        }
        
        // 关闭连接池
        public synchronized void shutdown() throws SQLException {
            System.out.println("关闭连接池...");
            
            // 关闭所有可用连接
            for (Connection conn : availableConnections) {
                conn.close();
            }
            availableConnections.clear();
            
            // 关闭所有使用中的连接
            for (Connection conn : usedConnections) {
                conn.close();
            }
            usedConnections.clear();
            
            System.out.println("连接池已关闭");
        }
        
        // 获取统计信息
        public synchronized PoolStatistics getStatistics() {
            return new PoolStatistics(
                availableConnections.size(),
                usedConnections.size(),
                maxPoolSize
            );
        }
    }
    
    /**
     * 连接池统计信息
     */
    static class PoolStatistics {
        private final int availableConnections;
        private final int usedConnections;
        private final int maxPoolSize;
        
        public PoolStatistics(int availableConnections, int usedConnections, int maxPoolSize) {
            this.availableConnections = availableConnections;
            this.usedConnections = usedConnections;
            this.maxPoolSize = maxPoolSize;
        }
        
        public void print() {
            System.out.println("\n========== 连接池统计 ==========");
            System.out.println("可用连接: " + availableConnections);
            System.out.println("使用中连接: " + usedConnections);
            System.out.println("最大连接数: " + maxPoolSize);
            System.out.println("使用率: " + (usedConnections * 100 / maxPoolSize) + "%");
        }
    }
    
    /**
     * 模拟连接（用于测试）
     */
    static class MockConnection {
        private static int idCounter = 0;
        private final int id;
        private boolean closed = false;
        
        public MockConnection() {
            this.id = ++idCounter;
        }
        
        public int getId() {
            return id;
        }
        
        public void close() {
            closed = true;
        }
        
        public boolean isClosed() {
            return closed;
        }
        
        @Override
        public String toString() {
            return "Connection-" + id;
        }
    }
    
    /**
     * 模拟连接池（用于测试）
     */
    static class MockConnectionPool {
        private final LinkedList<MockConnection> availableConnections = new LinkedList<>();
        private final LinkedList<MockConnection> usedConnections = new LinkedList<>();
        private final int maxPoolSize;
        
        public MockConnectionPool(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }
        
        public synchronized void initialize(int initialSize) {
            for (int i = 0; i < initialSize; i++) {
                availableConnections.add(new MockConnection());
            }
            System.out.println("连接池初始化完成，初始连接数: " + initialSize);
        }
        
        public synchronized MockConnection getConnection() throws InterruptedException {
            while (availableConnections.isEmpty() && usedConnections.size() >= maxPoolSize) {
                System.out.println(Thread.currentThread().getName() + " 等待连接...");
                wait();
            }
            
            MockConnection conn;
            if (!availableConnections.isEmpty()) {
                conn = availableConnections.removeFirst();
            } else {
                conn = new MockConnection();
            }
            
            usedConnections.add(conn);
            System.out.println(Thread.currentThread().getName() + 
                " 获取连接 " + conn + ", 可用: " + availableConnections.size() + 
                ", 使用中: " + usedConnections.size());
            return conn;
        }
        
        public synchronized MockConnection getConnection(long timeoutMillis) 
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            
            while (availableConnections.isEmpty() && usedConnections.size() >= maxPoolSize) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    System.out.println(Thread.currentThread().getName() + " 获取连接超时");
                    return null;
                }
                System.out.println(Thread.currentThread().getName() + 
                    " 等待连接，剩余时间: " + remaining + "ms");
                wait(remaining);
            }
            
            return getConnection();
        }
        
        public synchronized void releaseConnection(MockConnection conn) {
            if (usedConnections.remove(conn)) {
                availableConnections.add(conn);
                System.out.println(Thread.currentThread().getName() + 
                    " 归还连接 " + conn + ", 可用: " + availableConnections.size() + 
                    ", 使用中: " + usedConnections.size());
                notifyAll();
            }
        }
        
        public synchronized void shutdown() {
            System.out.println("\n关闭连接池...");
            for (MockConnection conn : availableConnections) {
                conn.close();
            }
            for (MockConnection conn : usedConnections) {
                conn.close();
            }
            availableConnections.clear();
            usedConnections.clear();
            System.out.println("连接池已关闭");
        }
        
        public synchronized void printStatistics() {
            System.out.println("\n========== 连接池统计 ==========");
            System.out.println("可用连接: " + availableConnections.size());
            System.out.println("使用中连接: " + usedConnections.size());
            System.out.println("最大连接数: " + maxPoolSize);
            System.out.println("使用率: " + (usedConnections.size() * 100 / maxPoolSize) + "%");
        }
    }
    
    /**
     * 测试代码
     */
    public static void main(String[] args) throws InterruptedException {
        // 测试1：基本功能
        System.out.println("========== 测试1：基本功能 ==========");
        MockConnectionPool pool1 = new MockConnectionPool(5);
        pool1.initialize(3);
        
        MockConnection conn1 = pool1.getConnection();
        MockConnection conn2 = pool1.getConnection();
        pool1.printStatistics();
        
        pool1.releaseConnection(conn1);
        pool1.releaseConnection(conn2);
        pool1.printStatistics();
        
        // 测试2：并发获取连接
        System.out.println("\n========== 测试2：并发获取连接 ==========");
        MockConnectionPool pool2 = new MockConnectionPool(5);
        pool2.initialize(2);
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                try {
                    MockConnection conn = pool2.getConnection();
                    Thread.sleep(100); // 模拟使用连接
                    pool2.releaseConnection(conn);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "线程-" + i);
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        pool2.printStatistics();
        
        // 测试3：超时获取连接
        System.out.println("\n========== 测试3：超时获取连接 ==========");
        MockConnectionPool pool3 = new MockConnectionPool(2);
        pool3.initialize(2);
        
        // 占用所有连接
        MockConnection conn3_1 = pool3.getConnection();
        MockConnection conn3_2 = pool3.getConnection();
        
        // 尝试获取连接（会超时）
        Thread timeoutThread = new Thread(() -> {
            try {
                MockConnection conn = pool3.getConnection(1000); // 1秒超时
                if (conn == null) {
                    System.out.println("获取连接失败（超时）");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "超时线程");
        
        timeoutThread.start();
        timeoutThread.join();
        
        // 归还连接
        pool3.releaseConnection(conn3_1);
        pool3.releaseConnection(conn3_2);
        
        // 测试4：连接池监控
        System.out.println("\n========== 测试4：连接池监控 ==========");
        MockConnectionPool pool4 = new MockConnectionPool(10);
        pool4.initialize(5);
        
        // 启动监控线程
        Thread monitorThread = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                pool4.printStatistics();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "监控线程");
        monitorThread.start();
        
        // 模拟并发访问
        Thread[] workers = new Thread[5];
        for (int i = 0; i < 5; i++) {
            workers[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 3; j++) {
                        MockConnection conn = pool4.getConnection();
                        Thread.sleep(300);
                        pool4.releaseConnection(conn);
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "工作线程-" + i);
            workers[i].start();
        }
        
        for (Thread worker : workers) {
            worker.join();
        }
        monitorThread.join();
        
        pool4.shutdown();
        
        System.out.println("\n所有测试完成！");
    }
}
