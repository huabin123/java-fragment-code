package com.fragment.core.threadlocal.project;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 实际项目Demo：数据库连接管理器
 * 
 * <p>场景：事务管理中的数据库连接复用
 * <ul>
 *   <li>同一个事务中的多个SQL使用同一个连接</li>
 *   <li>不同线程使用不同连接</li>
 *   <li>事务结束时关闭连接</li>
 *   <li>支持事务的提交和回滚</li>
 * </ul>
 * 
 * @author fragment
 */
public class DatabaseConnectionManager {
    
    /**
     * 连接持有者
     */
    private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
    
    /**
     * 事务状态持有者
     */
    private static final ThreadLocal<Boolean> transactionHolder = new ThreadLocal<>();
    
    /**
     * 获取当前线程的数据库连接
     */
    public static Connection getConnection() {
        Connection conn = connectionHolder.get();
        if (conn == null) {
            conn = createConnection();
            connectionHolder.set(conn);
            System.out.println("[ConnectionManager] 创建新连接: " + conn);
        } else {
            System.out.println("[ConnectionManager] 复用现有连接: " + conn);
        }
        return conn;
    }
    
    /**
     * 开启事务
     */
    public static void beginTransaction() {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            transactionHolder.set(true);
            System.out.println("[Transaction] 开启事务");
        } catch (SQLException e) {
            throw new RuntimeException("开启事务失败", e);
        }
    }
    
    /**
     * 提交事务
     */
    public static void commit() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                conn.commit();
                System.out.println("[Transaction] 提交事务");
            } catch (SQLException e) {
                throw new RuntimeException("提交事务失败", e);
            }
        }
    }
    
    /**
     * 回滚事务
     */
    public static void rollback() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                conn.rollback();
                System.out.println("[Transaction] 回滚事务");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 关闭连接
     */
    public static void closeConnection() {
        Connection conn = connectionHolder.get();
        if (conn != null) {
            try {
                // 恢复自动提交
                conn.setAutoCommit(true);
                conn.close();
                System.out.println("[ConnectionManager] 关闭连接: " + conn);
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                // 清理ThreadLocal
                connectionHolder.remove();
                transactionHolder.remove();
            }
        }
    }
    
    /**
     * 创建数据库连接（模拟）
     */
    private static Connection createConnection() {
        // 实际项目中应该从连接池获取
        return new MockConnection();
    }
    
    /**
     * 模拟Connection对象
     */
    private static class MockConnection implements Connection {
        private boolean autoCommit = true;
        private boolean closed = false;
        
        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            this.autoCommit = autoCommit;
        }
        
        @Override
        public void commit() throws SQLException {
            if (closed) throw new SQLException("Connection is closed");
        }
        
        @Override
        public void rollback() throws SQLException {
            if (closed) throw new SQLException("Connection is closed");
        }
        
        @Override
        public void close() throws SQLException {
            closed = true;
        }
        
        // 其他方法省略...
        @Override public java.sql.Statement createStatement() { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql) { return null; }
        @Override public String nativeSQL(String sql) { return null; }
        @Override public boolean getAutoCommit() { return autoCommit; }
        @Override public boolean isClosed() { return closed; }
        @Override public java.sql.DatabaseMetaData getMetaData() { return null; }
        @Override public void setReadOnly(boolean readOnly) {}
        @Override public boolean isReadOnly() { return false; }
        @Override public void setCatalog(String catalog) {}
        @Override public String getCatalog() { return null; }
        @Override public void setTransactionIsolation(int level) {}
        @Override public int getTransactionIsolation() { return 0; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) { return null; }
        @Override public java.util.Map<String,Class<?>> getTypeMap() { return null; }
        @Override public void setTypeMap(java.util.Map<String,Class<?>> map) {}
        @Override public void setHoldability(int holdability) {}
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() { return null; }
        @Override public java.sql.Savepoint setSavepoint(String name) { return null; }
        @Override public void rollback(java.sql.Savepoint savepoint) {}
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) {}
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) { return null; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) { return null; }
        @Override public java.sql.Clob createClob() { return null; }
        @Override public java.sql.Blob createBlob() { return null; }
        @Override public java.sql.NClob createNClob() { return null; }
        @Override public java.sql.SQLXML createSQLXML() { return null; }
        @Override public boolean isValid(int timeout) { return !closed; }
        @Override public void setClientInfo(String name, String value) {}
        @Override public void setClientInfo(java.util.Properties properties) {}
        @Override public String getClientInfo(String name) { return null; }
        @Override public java.util.Properties getClientInfo() { return null; }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) { return null; }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) { return null; }
        @Override public void setSchema(String schema) {}
        @Override public String getSchema() { return null; }
        @Override public void abort(java.util.concurrent.Executor executor) {}
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) {}
        @Override public int getNetworkTimeout() { return 0; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
    
    /**
     * 模拟DAO层
     */
    public static class UserDao {
        public void insert(String username) {
            Connection conn = DatabaseConnectionManager.getConnection();
            System.out.println("[DAO] 插入用户: " + username + ", 使用连接: " + conn);
            // 实际执行SQL
        }
        
        public void update(String username) {
            Connection conn = DatabaseConnectionManager.getConnection();
            System.out.println("[DAO] 更新用户: " + username + ", 使用连接: " + conn);
            // 实际执行SQL
        }
        
        public void delete(String username) {
            Connection conn = DatabaseConnectionManager.getConnection();
            System.out.println("[DAO] 删除用户: " + username + ", 使用连接: " + conn);
            // 实际执行SQL
        }
    }
    
    /**
     * 模拟Service层
     */
    public static class UserService {
        private UserDao userDao = new UserDao();
        
        /**
         * 创建用户（带事务）
         */
        public void createUser(String username) {
            try {
                // 开启事务
                DatabaseConnectionManager.beginTransaction();
                
                // 执行多个DAO操作，使用同一个连接
                userDao.insert(username);
                userDao.update(username);
                
                // 模拟业务异常
                if (username.equals("error")) {
                    throw new RuntimeException("模拟业务异常");
                }
                
                // 提交事务
                DatabaseConnectionManager.commit();
                
            } catch (Exception e) {
                // 回滚事务
                System.out.println("[Service] 发生异常: " + e.getMessage());
                DatabaseConnectionManager.rollback();
                throw e;
            } finally {
                // 关闭连接
                DatabaseConnectionManager.closeConnection();
            }
        }
    }
    
    /**
     * 测试主方法
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 数据库连接管理器演示 ==========\n");
        
        UserService userService = new UserService();
        
        // 场景1：正常事务
        System.out.println("===== 场景1：正常事务 =====");
        userService.createUser("zhangsan");
        
        System.out.println();
        
        // 场景2：事务回滚
        System.out.println("===== 场景2：事务回滚 =====");
        try {
            userService.createUser("error");
        } catch (Exception e) {
            System.out.println("[Main] 捕获异常: " + e.getMessage());
        }
        
        System.out.println();
        
        // 场景3：并发事务（不同线程使用不同连接）
        System.out.println("===== 场景3：并发事务 =====");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        executor.execute(() -> {
            System.out.println("[Thread-1] 开始");
            userService.createUser("user1");
            System.out.println("[Thread-1] 结束");
        });
        
        executor.execute(() -> {
            System.out.println("[Thread-2] 开始");
            userService.createUser("user2");
            System.out.println("[Thread-2] 结束");
        });
        
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }
        
        System.out.println("\n========== 演示完成 ==========");
        System.out.println("\n优势:");
        System.out.println("1. 同一个事务中的多个SQL使用同一个连接");
        System.out.println("2. 不同线程使用不同连接，互不干扰");
        System.out.println("3. 无需在方法间传递Connection对象");
        System.out.println("4. 事务结束时自动关闭连接和清理ThreadLocal");
        
        System.out.println("\n注意事项:");
        System.out.println("1. 必须在finally中关闭连接");
        System.out.println("2. 必须调用remove()清理ThreadLocal");
        System.out.println("3. 线程池场景下特别重要");
    }
}
