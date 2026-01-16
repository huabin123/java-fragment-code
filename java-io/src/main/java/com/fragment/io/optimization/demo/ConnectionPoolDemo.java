package com.fragment.io.optimization.demo;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连接池演示
 * 
 * 演示内容：
 * 1. 简单连接池实现
 * 2. 连接池性能对比
 * 3. 连接验证
 * 4. 连接泄漏检测
 * 5. 连接池监控
 * 
 * @author fragment
 */
public class ConnectionPoolDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 连接池演示 ===\n");
        
        // 模拟数据库连接
        String url = "jdbc:h2:mem:test";
        String username = "sa";
        String password = "";
        
        // 初始化H2数据库
        initDatabase(url, username, password);
        
        // 1. 性能对比测试
        System.out.println("--- 性能对比测试 ---");
        performanceTest(url, username, password);
        
        // 2. 连接池功能演示
        System.out.println("\n--- 连接池功能演示 ---");
        connectionPoolDemo(url, username, password);
        
        // 3. 连接泄漏检测
        System.out.println("\n--- 连接泄漏检测 ---");
        leakDetectionDemo(url, username, password);
    }
    
    /**
     * 初始化数据库
     */
    private static void initDatabase(String url, String username, String password) 
            throws SQLException {
        Connection conn = DriverManager.getConnection(url, username, password);
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50))");
        stmt.execute("INSERT INTO users VALUES (1, '张三')");
        stmt.execute("INSERT INTO users VALUES (2, '李四')");
        stmt.close();
        conn.close();
        System.out.println("数据库初始化完成\n");
    }
    
    /**
     * 性能对比测试
     */
    private static void performanceTest(String url, String username, String password) 
            throws Exception {
        int testCount = 100;
        
        // 测试1：每次创建新连接
        long time1 = testWithoutPool(url, username, password, testCount);
        System.out.println("不使用连接池 - 总耗时: " + time1 + "ms, 平均: " + (time1 / testCount) + "ms");
        
        // 测试2：使用连接池
        long time2 = testWithPool(url, username, password, testCount);
        System.out.println("使用连接池   - 总耗时: " + time2 + "ms, 平均: " + (time2 / testCount) + "ms");
        
        System.out.println("性能提升: " + (time1 * 100 / time2) + "%");
    }
    
    /**
     * 不使用连接池测试
     */
    private static long testWithoutPool(String url, String username, String password, int count) 
            throws SQLException {
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < count; i++) {
            Connection conn = DriverManager.getConnection(url, username, password);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM users");
            while (rs.next()) {
                // 处理结果
            }
            rs.close();
            stmt.close();
            conn.close();
        }
        
        return System.currentTimeMillis() - start;
    }
    
    /**
     * 使用连接池测试
     */
    private static long testWithPool(String url, String username, String password, int count) 
            throws Exception {
        SimpleConnectionPool pool = new SimpleConnectionPool(url, username, password, 10, 20, 3000);
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < count; i++) {
            Connection conn = pool.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM users");
            while (rs.next()) {
                // 处理结果
            }
            rs.close();
            stmt.close();
            pool.releaseConnection(conn);
        }
        
        long time = System.currentTimeMillis() - start;
        pool.shutdown();
        
        return time;
    }
    
    /**
     * 连接池功能演示
     */
    private static void connectionPoolDemo(String url, String username, String password) 
            throws Exception {
        SimpleConnectionPool pool = new SimpleConnectionPool(url, username, password, 5, 10, 3000);
        
        System.out.println("连接池初始化完成");
        pool.printStats();
        
        // 获取连接
        List<Connection> connections = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Connection conn = pool.getConnection();
            connections.add(conn);
            System.out.println("获取连接 " + (i + 1));
        }
        
        pool.printStats();
        
        // 归还连接
        for (int i = 0; i < 3; i++) {
            pool.releaseConnection(connections.get(i));
            System.out.println("归还连接 " + (i + 1));
        }
        
        pool.printStats();
        
        // 清理
        for (int i = 3; i < connections.size(); i++) {
            pool.releaseConnection(connections.get(i));
        }
        
        pool.shutdown();
        System.out.println("连接池已关闭");
    }
    
    /**
     * 连接泄漏检测演示
     */
    private static void leakDetectionDemo(String url, String username, String password) 
            throws Exception {
        SimpleConnectionPool pool = new SimpleConnectionPool(url, username, password, 3, 5, 3000);
        
        // 模拟连接泄漏
        Connection conn1 = pool.getConnection();
        Connection conn2 = pool.getConnection();
        Connection conn3 = pool.getConnection();
        
        System.out.println("获取了3个连接，但只归还2个");
        
        // 只归还2个
        pool.releaseConnection(conn1);
        pool.releaseConnection(conn2);
        // conn3没有归还，造成泄漏
        
        pool.printStats();
        
        // 尝试获取更多连接
        try {
            System.out.println("尝试获取第4个连接...");
            Connection conn4 = pool.getConnection();
            System.out.println("成功获取第4个连接");
            pool.releaseConnection(conn4);
        } catch (SQLException e) {
            System.out.println("获取连接失败: " + e.getMessage());
        }
        
        // 清理
        pool.releaseConnection(conn3);
        pool.shutdown();
    }
    
    /**
     * 简单连接池实现
     */
    static class SimpleConnectionPool {
        private final int coreSize;
        private final int maxSize;
        private final long maxWait;
        
        private final BlockingQueue<Connection> idleConnections;
        private final Set<Connection> activeConnections;
        private final AtomicInteger totalConnections;
        
        private final String url;
        private final String username;
        private final String password;
        
        public SimpleConnectionPool(String url, String username, String password,
                                   int coreSize, int maxSize, long maxWait) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.coreSize = coreSize;
            this.maxSize = maxSize;
            this.maxWait = maxWait;
            
            this.idleConnections = new LinkedBlockingQueue<>();
            this.activeConnections = Collections.synchronizedSet(new HashSet<>());
            this.totalConnections = new AtomicInteger(0);
            
            initCoreConnections();
        }
        
        private void initCoreConnections() {
            for (int i = 0; i < coreSize; i++) {
                try {
                    Connection conn = createConnection();
                    idleConnections.offer(conn);
                    totalConnections.incrementAndGet();
                } catch (SQLException e) {
                    throw new RuntimeException("初始化连接池失败", e);
                }
            }
        }
        
        private Connection createConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        
        public Connection getConnection() throws SQLException {
            Connection conn = idleConnections.poll();
            
            if (conn != null && isValid(conn)) {
                activeConnections.add(conn);
                return conn;
            }
            
            if (totalConnections.get() < maxSize) {
                synchronized (this) {
                    if (totalConnections.get() < maxSize) {
                        conn = createConnection();
                        totalConnections.incrementAndGet();
                        activeConnections.add(conn);
                        return conn;
                    }
                }
            }
            
            try {
                conn = idleConnections.poll(maxWait, TimeUnit.MILLISECONDS);
                if (conn == null) {
                    throw new SQLException("获取连接超时");
                }
                
                if (!isValid(conn)) {
                    totalConnections.decrementAndGet();
                    return getConnection();
                }
                
                activeConnections.add(conn);
                return conn;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("获取连接被中断", e);
            }
        }
        
        public void releaseConnection(Connection conn) {
            if (conn == null) {
                return;
            }
            
            activeConnections.remove(conn);
            
            if (isValid(conn)) {
                idleConnections.offer(conn);
            } else {
                closeConnection(conn);
                totalConnections.decrementAndGet();
                
                if (totalConnections.get() < coreSize) {
                    try {
                        Connection newConn = createConnection();
                        idleConnections.offer(newConn);
                        totalConnections.incrementAndGet();
                    } catch (SQLException e) {
                        // 记录日志
                    }
                }
            }
        }
        
        private boolean isValid(Connection conn) {
            try {
                return conn != null && !conn.isClosed() && conn.isValid(1);
            } catch (SQLException e) {
                return false;
            }
        }
        
        private void closeConnection(Connection conn) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                // 记录日志
            }
        }
        
        public void shutdown() {
            Connection conn;
            while ((conn = idleConnections.poll()) != null) {
                closeConnection(conn);
            }
            
            for (Connection activeConn : activeConnections) {
                closeConnection(activeConn);
            }
            
            activeConnections.clear();
            totalConnections.set(0);
        }
        
        public void printStats() {
            System.out.println("连接池状态:");
            System.out.println("  总连接数: " + totalConnections.get());
            System.out.println("  空闲连接: " + idleConnections.size());
            System.out.println("  活跃连接: " + activeConnections.size());
        }
    }
}
