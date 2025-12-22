package com.fragment.core.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * JDK 动态代理演示
 */
public class JdkProxyDemo {

    public static void main(String[] args) {
        System.out.println("=== JDK 动态代理演示 ===\n");

        // 创建目标对象
        UserService target = new UserServiceImpl();

        // 1. 基础代理演示
        demonstrateBasicProxy(target);

        // 2. 日志代理演示
        demonstrateLoggingProxy(target);

        // 3. 性能监控代理演示
        demonstratePerformanceProxy(target);

        // 4. 缓存代理演示
        demonstrateCacheProxy(target);
    }

    /**
     * 基础代理演示
     */
    private static void demonstrateBasicProxy(UserService target) {
        System.out.println("1. 基础代理演示:");

        UserService proxy = (UserService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new BasicInvocationHandler(target)
        );

        // 使用代理对象
        User user = proxy.findById(1L);
        System.out.println("查询结果: " + user);
        System.out.println();
    }

    /**
     * 日志代理演示
     */
    private static void demonstrateLoggingProxy(UserService target) {
        System.out.println("2. 日志代理演示:");

        UserService proxy = (UserService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new LoggingInvocationHandler(target)
        );

        proxy.save(new User(null, "赵六", "zhaoliu@example.com", 35));
        proxy.findById(1L);
        System.out.println();
    }

    /**
     * 性能监控代理演示
     */
    private static void demonstratePerformanceProxy(UserService target) {
        System.out.println("3. 性能监控代理演示:");

        UserService proxy = (UserService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new PerformanceInvocationHandler(target)
        );

        proxy.findById(2L);
        proxy.count();
        System.out.println();
    }

    /**
     * 缓存代理演示
     */
    private static void demonstrateCacheProxy(UserService target) {
        System.out.println("4. 缓存代理演示:");

        UserService proxy = (UserService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new CacheInvocationHandler(target)
        );

        // 第一次查询 - 会访问目标对象
        System.out.println("第一次查询:");
        User user1 = proxy.findById(1L);
        System.out.println("结果: " + user1);

        // 第二次查询 - 从缓存获取
        System.out.println("第二次查询:");
        User user2 = proxy.findById(1L);
        System.out.println("结果: " + user2);
        System.out.println();
    }

    /**
     * 基础 InvocationHandler
     */
    static class BasicInvocationHandler implements InvocationHandler {
        private final Object target;

        public BasicInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("代理调用方法: " + method.getName());
            return method.invoke(target, args);
        }
    }

    /**
     * 日志 InvocationHandler
     */
    static class LoggingInvocationHandler implements InvocationHandler {
        private final Object target;

        public LoggingInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("[LOG] 开始执行方法: " + method.getName());
            if (args != null && args.length > 0) {
                System.out.println("[LOG] 方法参数: " + java.util.Arrays.toString(args));
            }

            try {
                Object result = method.invoke(target, args);
                System.out.println("[LOG] 方法执行成功: " + method.getName());
                if (result != null) {
                    System.out.println("[LOG] 返回值: " + result);
                }
                return result;
            } catch (Exception e) {
                System.out.println("[LOG] 方法执行异常: " + method.getName() + ", 异常: " + e.getMessage());
                throw e;
            }
        }
    }

    /**
     * 性能监控 InvocationHandler
     */
    static class PerformanceInvocationHandler implements InvocationHandler {
        private final Object target;

        public PerformanceInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long startTime = System.currentTimeMillis();

            try {
                Object result = method.invoke(target, args);
                return result;
            } finally {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                System.out.println("[PERF] " + method.getName() + " 执行耗时: " + duration + " ms");
            }
        }
    }

    /**
     * 缓存 InvocationHandler
     */
    static class CacheInvocationHandler implements InvocationHandler {
        private final Object target;
        private final java.util.Map<String, Object> cache = new java.util.HashMap<>();

        public CacheInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 只对查询方法进行缓存
            if (method.getName().startsWith("find") || method.getName().startsWith("get")) {
                String cacheKey = method.getName() + "_" + java.util.Arrays.toString(args);
                
                if (cache.containsKey(cacheKey)) {
                    System.out.println("[CACHE] 缓存命中: " + cacheKey);
                    return cache.get(cacheKey);
                }

                Object result = method.invoke(target, args);
                cache.put(cacheKey, result);
                System.out.println("[CACHE] 缓存存储: " + cacheKey);
                return result;
            } else {
                // 非查询方法清空缓存
                if (method.getName().startsWith("save") || 
                    method.getName().startsWith("update") || 
                    method.getName().startsWith("delete")) {
                    cache.clear();
                    System.out.println("[CACHE] 缓存已清空");
                }
                return method.invoke(target, args);
            }
        }
    }
}
