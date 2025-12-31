package com.fragment.core.threadlocal.project;

import java.util.Random;

/**
 * 实际项目Demo：用户上下文管理系统
 * 
 * <p>场景：Web应用中的用户上下文传递
 * <ul>
 *   <li>在Filter中设置用户信息</li>
 *   <li>在Controller、Service、DAO中获取用户信息</li>
 *   <li>无需层层传递参数</li>
 *   <li>请求结束时自动清理</li>
 * </ul>
 * 
 * @author fragment
 */
public class UserContextManager {
    
    /**
     * 用户上下文持有者
     */
    private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();
    
    /**
     * 设置用户上下文
     */
    public static void setContext(UserContext context) {
        contextHolder.set(context);
    }
    
    /**
     * 获取用户上下文
     */
    public static UserContext getContext() {
        return contextHolder.get();
    }
    
    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        UserContext context = contextHolder.get();
        return context != null ? context.getUserId() : null;
    }
    
    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        UserContext context = contextHolder.get();
        return context != null ? context.getUsername() : null;
    }
    
    /**
     * 获取当前用户角色
     */
    public static String getRole() {
        UserContext context = contextHolder.get();
        return context != null ? context.getRole() : null;
    }
    
    /**
     * 清理用户上下文
     */
    public static void clear() {
        contextHolder.remove();
    }
    
    /**
     * 用户上下文对象
     */
    public static class UserContext {
        private Long userId;
        private String username;
        private String role;
        private String traceId;
        
        public UserContext(Long userId, String username, String role, String traceId) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.traceId = traceId;
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getRole() {
            return role;
        }
        
        public String getTraceId() {
            return traceId;
        }
        
        @Override
        public String toString() {
            return "UserContext{" +
                    "userId=" + userId +
                    ", username='" + username + '\'' +
                    ", role='" + role + '\'' +
                    ", traceId='" + traceId + '\'' +
                    '}';
        }
    }
    
    /**
     * 模拟Filter：请求拦截器
     */
    public static class RequestFilter {
        public void doFilter(String token) {
            try {
                // 1. 解析Token，获取用户信息
                UserContext context = parseToken(token);
                
                // 2. 设置到ThreadLocal
                UserContextManager.setContext(context);
                System.out.println("[Filter] 设置用户上下文: " + context);
                
                // 3. 执行请求处理
                handleRequest();
                
            } finally {
                // 4. 请求结束，清理ThreadLocal
                UserContextManager.clear();
                System.out.println("[Filter] 清理用户上下文");
            }
        }
        
        private UserContext parseToken(String token) {
            // 模拟解析Token
            return new UserContext(
                1001L,
                "zhangsan",
                "admin",
                generateTraceId()
            );
        }
        
        private String generateTraceId() {
            return "trace-" + System.currentTimeMillis();
        }
    }
    
    /**
     * 模拟Controller：控制器
     */
    public static class UserController {
        private UserService userService = new UserService();
        
        public void updateUser() {
            // 可以在任何地方获取用户信息，无需传递参数
            Long userId = UserContextManager.getUserId();
            String username = UserContextManager.getUsername();
            
            System.out.println("[Controller] 当前用户: " + username + " (ID: " + userId + ")");
            
            // 调用Service
            userService.updateUserInfo();
        }
    }
    
    /**
     * 模拟Service：业务逻辑层
     */
    public static class UserService {
        private UserDao userDao = new UserDao();
        
        public void updateUserInfo() {
            // Service层也可以获取用户信息
            String username = UserContextManager.getUsername();
            String role = UserContextManager.getRole();
            
            System.out.println("[Service] 处理用户: " + username + ", 角色: " + role);
            
            // 调用DAO
            userDao.update();
        }
    }
    
    /**
     * 模拟DAO：数据访问层
     */
    public static class UserDao {
        public void update() {
            // DAO层也可以获取用户信息（如记录操作日志）
            Long userId = UserContextManager.getUserId();
            UserContext context = UserContextManager.getContext();
            
            System.out.println("[DAO] 执行更新操作");
            System.out.println("[DAO] 操作人: " + userId);
            System.out.println("[DAO] TraceId: " + context.getTraceId());
        }
    }
    
    /**
     * 模拟请求处理
     */
    private static void handleRequest() {
        UserController controller = new UserController();
        controller.updateUser();
    }
    
    /**
     * 测试主方法
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 用户上下文管理系统演示 ==========\n");
        
        // 模拟多个请求
        RequestFilter filter = new RequestFilter();
        
        // 请求1
        System.out.println("===== 请求1 =====");
        filter.doFilter("token-1");
        
        System.out.println();
        
        // 请求2
        System.out.println("===== 请求2 =====");
        filter.doFilter("token-2");
        
        System.out.println();
        
        // 模拟并发请求
        System.out.println("===== 并发请求演示 =====");
        Thread t1 = new Thread(() -> {
            filter.doFilter("token-thread-1");
        }, "Thread-1");
        
        Thread t2 = new Thread(() -> {
            filter.doFilter("token-thread-2");
        }, "Thread-2");
        
        t1.start();
        t2.start();
        
        t1.join();
        t2.join();
        
        System.out.println("\n========== 演示完成 ==========");
        System.out.println("\n优势:");
        System.out.println("1. 无需在每个方法中传递user参数");
        System.out.println("2. 在任何地方都能获取当前用户信息");
        System.out.println("3. 线程隔离，不同请求互不干扰");
        System.out.println("4. 自动清理，避免内存泄漏");
    }
}
