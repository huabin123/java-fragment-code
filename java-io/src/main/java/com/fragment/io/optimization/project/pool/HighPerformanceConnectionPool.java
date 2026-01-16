package com.fragment.io.optimization.project.pool;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能数据库连接池
 * 
 * 功能特性：
 * 1. 连接复用与管理
 * 2. 连接有效性检测
 * 3. 连接泄漏检测
 * 4. 连接超时控制
 * 5. 动态扩容与缩容
 * 6. 连接池监控统计
 * 7. 优雅关闭
 * 
 * @author fragment
 */
public class HighPerformanceConnectionPool {
    
    // 配置参数
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int coreSize;
    private final int maxSize;
    private final long maxWaitMillis;
    private final long maxIdleMillis;
    private final long validationInterval;
    private final String validationQuery;
    private final boolean testOnBorrow;
    private final boolean testWhileIdle;
    private final long leakDetectionThreshold;
    
    // 连接管理
    private final BlockingQueue<PooledConnection> idleConnections;
    private final Set<PooledConnection> activeConnections;
    private final AtomicInteger totalConnections;
    
    // 统计信息
    private final AtomicLong totalRequests;
    private final AtomicLong totalWaitTime;
    private final AtomicLong totalLeaks;
    
    // 后台任务
    private final ScheduledExecutorService scheduler;
    private volatile boolean closed = false;
    
    /**
     * 连接池配置
     */
    public static class Config {
        private String jdbcUrl;
        private String username;
        private String password;
        private int coreSize = 10;
        private int maxSize = 50;
        private long maxWaitMillis = 3000;
        private long maxIdleMillis = 600000; // 10分钟
        private long validationInterval = 30000; // 30秒
        private String validationQuery = "SELECT 1";
        private boolean testOnBorrow = true;
        private boolean testWhileIdle = true;
        private long leakDetectionThreshold = 60000; // 60秒
        
        public Config(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }
        
        public Config coreSize(int coreSize) {
            this.coreSize = coreSize;
            return this;
        }
        
        public Config maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }
        
        public Config maxWaitMillis(long maxWaitMillis) {
            this.maxWaitMillis = maxWaitMillis;
            return this;
        }
        
        public Config maxIdleMillis(long maxIdleMillis) {
            this.maxIdleMillis = maxIdleMillis;
            return this;
        }
        
        public Config validationInterval(long validationInterval) {
            this.validationInterval = validationInterval;
            return this;
        }
        
        public Config validationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
            return this;
        }
        
        public Config testOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
            return this;
        }
        
        public Config testWhileIdle(boolean testWhileIdle) {
            this.testWhileIdle = testWhileIdle;
            return this;
        }
        
        public Config leakDetectionThreshold(long leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
            return this;
        }
    }
    
    /**
     * 池化连接包装类
     */
    private class PooledConnection {
        private final Connection connection;
        private final long createdTime;
        private volatile long lastAccessTime;
        private volatile long lastValidationTime;
        private volatile long borrowTime;
        private volatile StackTraceElement[] borrowStackTrace;
        
        public PooledConnection(Connection connection) {
            this.connection = connection;
            this.createdTime = System.currentTimeMillis();
            this.lastAccessTime = createdTime;
            this.lastValidationTime = createdTime;
        }
        
        public Connection getConnection() {
            return connection;
        }
        
        public void markBorrowed() {
            this.borrowTime = System.currentTimeMillis();
            this.lastAccessTime = borrowTime;
            if (leakDetectionThreshold > 0) {
                this.borrowStackTrace = Thread.currentThread().getStackTrace();
            }
        }
        
        public void markReturned() {
            this.lastAccessTime = System.currentTimeMillis();
            this.borrowTime = 0;
            this.borrowStackTrace = null;
        }
        
        public boolean isIdle() {
            return borrowTime == 0;
        }
        
        public long getIdleTime() {
            return isIdle() ? System.currentTimeMillis() - lastAccessTime : 0;
        }
        
        public long getBorrowDuration() {
            return borrowTime > 0 ? System.currentTimeMillis() - borrowTime : 0;
        }
        
        public boolean needsValidation() {
            return System.currentTimeMillis() - lastValidationTime > validationInterval;
        }
        
        public void markValidated() {
            this.lastValidationTime = System.currentTimeMillis();
        }
        
        public boolean isLeaked() {
            return leakDetectionThreshold > 0 && 
                   borrowTime > 0 && 
                   getBorrowDuration() > leakDetectionThreshold;
        }
        
        public void printLeakTrace() {
            if (borrowStackTrace != null) {
                System.err.println("连接泄漏检测 - 连接未归还超过 " + leakDetectionThreshold + "ms");
                System.err.println("借用位置:");
                for (StackTraceElement element : borrowStackTrace) {
                    System.err.println("  at " + element);
                }
            }
        }
    }
    
    /**
     * 构造函数
     */
    public HighPerformanceConnectionPool(Config config) {
        this.jdbcUrl = config.jdbcUrl;
        this.username = config.username;
        this.password = config.password;
        this.coreSize = config.coreSize;
        this.maxSize = config.maxSize;
        this.maxWaitMillis = config.maxWaitMillis;
        this.maxIdleMillis = config.maxIdleMillis;
        this.validationInterval = config.validationInterval;
        this.validationQuery = config.validationQuery;
        this.testOnBorrow = config.testOnBorrow;
        this.testWhileIdle = config.testWhileIdle;
        this.leakDetectionThreshold = config.leakDetectionThreshold;
        
        this.idleConnections = new LinkedBlockingQueue<>();
        this.activeConnections = Collections.synchronizedSet(new HashSet<>());
        this.totalConnections = new AtomicInteger(0);
        
        this.totalRequests = new AtomicLong(0);
        this.totalWaitTime = new AtomicLong(0);
        this.totalLeaks = new AtomicLong(0);
        
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("ConnectionPool-Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        
        // 初始化核心连接
        initCoreConnections();
        
        // 启动后台任务
        startBackgroundTasks();
        
        System.out.println("连接池初始化完成:");
        System.out.println("  核心连接数: " + coreSize);
        System.out.println("  最大连接数: " + maxSize);
        System.out.println("  最大等待时间: " + maxWaitMillis + "ms");
    }
    
    /**
     * 初始化核心连接
     */
    private void initCoreConnections() {
        for (int i = 0; i < coreSize; i++) {
            try {
                PooledConnection pooledConn = createPooledConnection();
                idleConnections.offer(pooledConn);
                totalConnections.incrementAndGet();
            } catch (SQLException e) {
                throw new RuntimeException("初始化连接池失败", e);
            }
        }
    }
    
    /**
     * 创建池化连接
     */
    private PooledConnection createPooledConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
        return new PooledConnection(conn);
    }
    
    /**
     * 获取连接
     */
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("连接池已关闭");
        }
        
        totalRequests.incrementAndGet();
        long startWait = System.currentTimeMillis();
        
        try {
            PooledConnection pooledConn = borrowConnection();
            
            long waitTime = System.currentTimeMillis() - startWait;
            totalWaitTime.addAndGet(waitTime);
            
            return createProxyConnection(pooledConn);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("获取连接被中断", e);
        }
    }
    
    /**
     * 借用连接
     */
    private PooledConnection borrowConnection() throws SQLException, InterruptedException {
        // 1. 尝试从空闲队列获取
        PooledConnection pooledConn = idleConnections.poll();
        
        if (pooledConn != null) {
            if (validateConnection(pooledConn, testOnBorrow)) {
                pooledConn.markBorrowed();
                activeConnections.add(pooledConn);
                return pooledConn;
            } else {
                // 连接无效，销毁并重试
                destroyConnection(pooledConn);
                return borrowConnection();
            }
        }
        
        // 2. 如果没有空闲连接，尝试创建新连接
        if (totalConnections.get() < maxSize) {
            synchronized (this) {
                if (totalConnections.get() < maxSize) {
                    pooledConn = createPooledConnection();
                    totalConnections.incrementAndGet();
                    pooledConn.markBorrowed();
                    activeConnections.add(pooledConn);
                    return pooledConn;
                }
            }
        }
        
        // 3. 等待空闲连接
        pooledConn = idleConnections.poll(maxWaitMillis, TimeUnit.MILLISECONDS);
        
        if (pooledConn == null) {
            throw new SQLException("获取连接超时，等待时间: " + maxWaitMillis + "ms");
        }
        
        if (!validateConnection(pooledConn, testOnBorrow)) {
            destroyConnection(pooledConn);
            return borrowConnection();
        }
        
        pooledConn.markBorrowed();
        activeConnections.add(pooledConn);
        return pooledConn;
    }
    
    /**
     * 归还连接
     */
    private void returnConnection(PooledConnection pooledConn) {
        if (pooledConn == null) {
            return;
        }
        
        activeConnections.remove(pooledConn);
        
        if (validateConnection(pooledConn, false)) {
            pooledConn.markReturned();
            idleConnections.offer(pooledConn);
        } else {
            destroyConnection(pooledConn);
            
            // 如果连接数少于核心数，创建新连接补充
            if (totalConnections.get() < coreSize) {
                try {
                    PooledConnection newConn = createPooledConnection();
                    idleConnections.offer(newConn);
                    totalConnections.incrementAndGet();
                } catch (SQLException e) {
                    System.err.println("创建补充连接失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 验证连接
     */
    private boolean validateConnection(PooledConnection pooledConn, boolean force) {
        try {
            Connection conn = pooledConn.getConnection();
            
            if (conn == null || conn.isClosed()) {
                return false;
            }
            
            // 检查是否需要验证
            if (!force && !pooledConn.needsValidation()) {
                return true;
            }
            
            // 执行验证查询
            if (validationQuery != null && !validationQuery.isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(1);
                    stmt.execute(validationQuery);
                }
            } else {
                conn.isValid(1);
            }
            
            pooledConn.markValidated();
            return true;
            
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * 销毁连接
     */
    private void destroyConnection(PooledConnection pooledConn) {
        try {
            Connection conn = pooledConn.getConnection();
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            // 忽略关闭异常
        } finally {
            totalConnections.decrementAndGet();
        }
    }
    
    /**
     * 创建代理连接
     */
    private Connection createProxyConnection(PooledConnection pooledConn) {
        return new ConnectionProxy(pooledConn);
    }
    
    /**
     * 连接代理类
     */
    private class ConnectionProxy implements Connection {
        private final PooledConnection pooledConn;
        private volatile boolean closed = false;
        
        public ConnectionProxy(PooledConnection pooledConn) {
            this.pooledConn = pooledConn;
        }
        
        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                returnConnection(pooledConn);
            }
        }
        
        @Override
        public boolean isClosed() throws SQLException {
            return closed;
        }
        
        // 委托其他方法到真实连接
        @Override
        public Statement createStatement() throws SQLException {
            return pooledConn.getConnection().createStatement();
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return pooledConn.getConnection().prepareStatement(sql);
        }
        
        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return pooledConn.getConnection().prepareCall(sql);
        }
        
        @Override
        public String nativeSQL(String sql) throws SQLException {
            return pooledConn.getConnection().nativeSQL(sql);
        }
        
        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            pooledConn.getConnection().setAutoCommit(autoCommit);
        }
        
        @Override
        public boolean getAutoCommit() throws SQLException {
            return pooledConn.getConnection().getAutoCommit();
        }
        
        @Override
        public void commit() throws SQLException {
            pooledConn.getConnection().commit();
        }
        
        @Override
        public void rollback() throws SQLException {
            pooledConn.getConnection().rollback();
        }
        
        // 省略其他委托方法...
        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            return pooledConn.getConnection().getMetaData();
        }
        
        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            pooledConn.getConnection().setReadOnly(readOnly);
        }
        
        @Override
        public boolean isReadOnly() throws SQLException {
            return pooledConn.getConnection().isReadOnly();
        }
        
        @Override
        public void setCatalog(String catalog) throws SQLException {
            pooledConn.getConnection().setCatalog(catalog);
        }
        
        @Override
        public String getCatalog() throws SQLException {
            return pooledConn.getConnection().getCatalog();
        }
        
        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            pooledConn.getConnection().setTransactionIsolation(level);
        }
        
        @Override
        public int getTransactionIsolation() throws SQLException {
            return pooledConn.getConnection().getTransactionIsolation();
        }
        
        @Override
        public SQLWarning getWarnings() throws SQLException {
            return pooledConn.getConnection().getWarnings();
        }
        
        @Override
        public void clearWarnings() throws SQLException {
            pooledConn.getConnection().clearWarnings();
        }
        
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            return pooledConn.getConnection().createStatement(resultSetType, resultSetConcurrency);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return pooledConn.getConnection().prepareStatement(sql, resultSetType, resultSetConcurrency);
        }
        
        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            return pooledConn.getConnection().prepareCall(sql, resultSetType, resultSetConcurrency);
        }
        
        @Override
        public java.util.Map<String, Class<?>> getTypeMap() throws SQLException {
            return pooledConn.getConnection().getTypeMap();
        }
        
        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException {
            pooledConn.getConnection().setTypeMap(map);
        }
        
        @Override
        public void setHoldability(int holdability) throws SQLException {
            pooledConn.getConnection().setHoldability(holdability);
        }
        
        @Override
        public int getHoldability() throws SQLException {
            return pooledConn.getConnection().getHoldability();
        }
        
        @Override
        public Savepoint setSavepoint() throws SQLException {
            return pooledConn.getConnection().setSavepoint();
        }
        
        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            return pooledConn.getConnection().setSavepoint(name);
        }
        
        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            pooledConn.getConnection().rollback(savepoint);
        }
        
        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            pooledConn.getConnection().releaseSavepoint(savepoint);
        }
        
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return pooledConn.getConnection().createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return pooledConn.getConnection().prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            return pooledConn.getConnection().prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return pooledConn.getConnection().prepareStatement(sql, autoGeneratedKeys);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return pooledConn.getConnection().prepareStatement(sql, columnIndexes);
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return pooledConn.getConnection().prepareStatement(sql, columnNames);
        }
        
        @Override
        public Clob createClob() throws SQLException {
            return pooledConn.getConnection().createClob();
        }
        
        @Override
        public Blob createBlob() throws SQLException {
            return pooledConn.getConnection().createBlob();
        }
        
        @Override
        public NClob createNClob() throws SQLException {
            return pooledConn.getConnection().createNClob();
        }
        
        @Override
        public SQLXML createSQLXML() throws SQLException {
            return pooledConn.getConnection().createSQLXML();
        }
        
        @Override
        public boolean isValid(int timeout) throws SQLException {
            return pooledConn.getConnection().isValid(timeout);
        }
        
        @Override
        public void setClientInfo(String name, String value) throws SQLClientInfoException {
            pooledConn.getConnection().setClientInfo(name, value);
        }
        
        @Override
        public void setClientInfo(Properties properties) throws SQLClientInfoException {
            pooledConn.getConnection().setClientInfo(properties);
        }
        
        @Override
        public String getClientInfo(String name) throws SQLException {
            return pooledConn.getConnection().getClientInfo(name);
        }
        
        @Override
        public Properties getClientInfo() throws SQLException {
            return pooledConn.getConnection().getClientInfo();
        }
        
        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            return pooledConn.getConnection().createArrayOf(typeName, elements);
        }
        
        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            return pooledConn.getConnection().createStruct(typeName, attributes);
        }
        
        @Override
        public void setSchema(String schema) throws SQLException {
            pooledConn.getConnection().setSchema(schema);
        }
        
        @Override
        public String getSchema() throws SQLException {
            return pooledConn.getConnection().getSchema();
        }
        
        @Override
        public void abort(java.util.concurrent.Executor executor) throws SQLException {
            pooledConn.getConnection().abort(executor);
        }
        
        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException {
            pooledConn.getConnection().setNetworkTimeout(executor, milliseconds);
        }
        
        @Override
        public int getNetworkTimeout() throws SQLException {
            return pooledConn.getConnection().getNetworkTimeout();
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return pooledConn.getConnection().unwrap(iface);
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return pooledConn.getConnection().isWrapperFor(iface);
        }
    }
    
    /**
     * 启动后台任务
     */
    private void startBackgroundTasks() {
        // 空闲连接清理任务
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                cleanIdleConnections();
            } catch (Exception e) {
                System.err.println("清理空闲连接失败: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
        
        // 连接泄漏检测任务
        if (leakDetectionThreshold > 0) {
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    detectLeakedConnections();
                } catch (Exception e) {
                    System.err.println("检测连接泄漏失败: " + e.getMessage());
                }
            }, 30, 30, TimeUnit.SECONDS);
        }
        
        // 空闲连接验证任务
        if (testWhileIdle) {
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    validateIdleConnections();
                } catch (Exception e) {
                    System.err.println("验证空闲连接失败: " + e.getMessage());
                }
            }, 30, 30, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 清理空闲连接
     */
    private void cleanIdleConnections() {
        List<PooledConnection> toRemove = new ArrayList<>();
        
        for (PooledConnection pooledConn : idleConnections) {
            if (pooledConn.getIdleTime() > maxIdleMillis && 
                totalConnections.get() > coreSize) {
                toRemove.add(pooledConn);
            }
        }
        
        for (PooledConnection pooledConn : toRemove) {
            if (idleConnections.remove(pooledConn)) {
                destroyConnection(pooledConn);
                System.out.println("清理空闲连接，当前连接数: " + totalConnections.get());
            }
        }
    }
    
    /**
     * 检测泄漏的连接
     */
    private void detectLeakedConnections() {
        synchronized (activeConnections) {
            for (PooledConnection pooledConn : activeConnections) {
                if (pooledConn.isLeaked()) {
                    totalLeaks.incrementAndGet();
                    pooledConn.printLeakTrace();
                }
            }
        }
    }
    
    /**
     * 验证空闲连接
     */
    private void validateIdleConnections() {
        List<PooledConnection> toValidate = new ArrayList<>(idleConnections);
        
        for (PooledConnection pooledConn : toValidate) {
            if (!validateConnection(pooledConn, true)) {
                if (idleConnections.remove(pooledConn)) {
                    destroyConnection(pooledConn);
                    
                    // 创建新连接补充
                    if (totalConnections.get() < coreSize) {
                        try {
                            PooledConnection newConn = createPooledConnection();
                            idleConnections.offer(newConn);
                            totalConnections.incrementAndGet();
                        } catch (SQLException e) {
                            System.err.println("创建补充连接失败: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 获取统计信息
     */
    public PoolStats getStats() {
        return new PoolStats(
            totalConnections.get(),
            idleConnections.size(),
            activeConnections.size(),
            totalRequests.get(),
            totalWaitTime.get(),
            totalLeaks.get()
        );
    }
    
    /**
     * 统计信息类
     */
    public static class PoolStats {
        public final int totalConnections;
        public final int idleConnections;
        public final int activeConnections;
        public final long totalRequests;
        public final long totalWaitTime;
        public final long totalLeaks;
        
        public PoolStats(int totalConnections, int idleConnections, int activeConnections,
                        long totalRequests, long totalWaitTime, long totalLeaks) {
            this.totalConnections = totalConnections;
            this.idleConnections = idleConnections;
            this.activeConnections = activeConnections;
            this.totalRequests = totalRequests;
            this.totalWaitTime = totalWaitTime;
            this.totalLeaks = totalLeaks;
        }
        
        public double getAverageWaitTime() {
            return totalRequests > 0 ? (double) totalWaitTime / totalRequests : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "连接池统计:\n" +
                "  总连接数: %d\n" +
                "  空闲连接: %d\n" +
                "  活跃连接: %d\n" +
                "  总请求数: %d\n" +
                "  平均等待时间: %.2fms\n" +
                "  检测到泄漏: %d",
                totalConnections, idleConnections, activeConnections,
                totalRequests, getAverageWaitTime(), totalLeaks
            );
        }
    }
    
    /**
     * 关闭连接池
     */
    public void shutdown() {
        if (closed) {
            return;
        }
        
        closed = true;
        
        // 停止后台任务
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭所有空闲连接
        PooledConnection pooledConn;
        while ((pooledConn = idleConnections.poll()) != null) {
            destroyConnection(pooledConn);
        }
        
        // 关闭所有活跃连接
        synchronized (activeConnections) {
            for (PooledConnection activeConn : new ArrayList<>(activeConnections)) {
                destroyConnection(activeConn);
            }
            activeConnections.clear();
        }
        
        System.out.println("连接池已关闭");
    }
    
    /**
     * 主函数 - 演示使用
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== 高性能连接池演示 ===\n");
        
        // 创建连接池
        Config config = new Config("jdbc:h2:mem:test", "sa", "")
            .coreSize(5)
            .maxSize(10)
            .maxWaitMillis(3000)
            .testOnBorrow(true)
            .leakDetectionThreshold(5000);
        
        HighPerformanceConnectionPool pool = new HighPerformanceConnectionPool(config);
        
        // 初始化数据库
        try (Connection conn = pool.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50))");
            stmt.execute("INSERT INTO users VALUES (1, '张三')");
            stmt.execute("INSERT INTO users VALUES (2, '李四')");
        }
        
        // 并发测试
        System.out.println("\n--- 并发测试 ---");
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(100);
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    Connection conn = pool.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM users");
                    while (rs.next()) {
                        // 处理结果
                    }
                    rs.close();
                    stmt.close();
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long elapsed = System.currentTimeMillis() - start;
        
        System.out.println("100次查询耗时: " + elapsed + "ms");
        System.out.println("\n" + pool.getStats());
        
        // 清理
        executor.shutdown();
        pool.shutdown();
    }
}
