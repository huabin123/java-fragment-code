package com.fragment.core.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 代理性能对比演示
 */
public class ProxyPerformanceDemo {

    private static final int ITERATIONS = 1_000_000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== 代理性能对比演示 ===\n");

        UserService target = new UserServiceImpl();
        UserService jdkProxy = createJdkProxy(target);
        Calculator calculator = new Calculator();
        Calculator cglibProxy = new CglibProxyDemo.CalculatorProxy();

        // 预热JVM
        warmUp(target, jdkProxy, calculator, cglibProxy);

        // 性能测试
        testDirectCall(target);
        testJdkProxy(jdkProxy);
        testCglibProxy(calculator, cglibProxy);
        
        // 内存使用分析
        analyzeMemoryUsage();
    }

    /**
     * JVM预热
     */
    private static void warmUp(UserService target, UserService jdkProxy, Calculator calculator, Calculator cglibProxy) {
        System.out.println("JVM 预热中...");
        
        for (int i = 0; i < 10000; i++) {
            target.count();
            jdkProxy.count();
            calculator.add(1, 2);
            cglibProxy.add(1, 2);
        }
        
        // 触发GC
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("预热完成\n");
    }

    /**
     * 测试直接调用性能
     */
    private static void testDirectCall(UserService target) {
        System.out.println("1. 直接调用性能测试:");
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            target.count();
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // 转换为毫秒
        
        System.out.println("直接调用 " + ITERATIONS + " 次耗时: " + duration + " ms");
        System.out.println("平均每次调用耗时: " + (double) (endTime - startTime) / ITERATIONS + " ns");
        System.out.println();
    }

    /**
     * 测试JDK代理性能
     */
    private static void testJdkProxy(UserService jdkProxy) {
        System.out.println("2. JDK动态代理性能测试:");
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            jdkProxy.count();
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000;
        
        System.out.println("JDK代理调用 " + ITERATIONS + " 次耗时: " + duration + " ms");
        System.out.println("平均每次调用耗时: " + (double) (endTime - startTime) / ITERATIONS + " ns");
        System.out.println();
    }

    /**
     * 测试CGLIB代理性能（模拟）
     */
    private static void testCglibProxy(Calculator direct, Calculator cglibProxy) {
        System.out.println("3. CGLIB代理性能测试 (模拟):");
        
        // 测试直接调用
        long startTime1 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            direct.add(1, 2);
        }
        long endTime1 = System.nanoTime();
        long directDuration = (endTime1 - startTime1) / 1_000_000;
        
        // 测试CGLIB代理调用
        long startTime2 = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            cglibProxy.add(1, 2);
        }
        long endTime2 = System.nanoTime();
        long proxyDuration = (endTime2 - startTime2) / 1_000_000;
        
        System.out.println("直接调用 Calculator.add() " + ITERATIONS + " 次耗时: " + directDuration + " ms");
        System.out.println("CGLIB代理调用 " + ITERATIONS + " 次耗时: " + proxyDuration + " ms");
        System.out.println("性能开销: " + (proxyDuration - directDuration) + " ms (" + 
                          String.format("%.2f", (double) proxyDuration / directDuration) + "x)");
        System.out.println();
    }

    /**
     * 创建JDK代理
     */
    private static UserService createJdkProxy(UserService target) {
        return (UserService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new SimpleInvocationHandler(target)
        );
    }

    /**
     * 简单的InvocationHandler，减少额外开销
     */
    static class SimpleInvocationHandler implements InvocationHandler {
        private final Object target;

        public SimpleInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return method.invoke(target, args);
        }
    }

    /**
     * 分析内存使用情况
     */
    private static void analyzeMemoryUsage() {
        System.out.println("4. 内存使用分析:");
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        System.out.println("总内存: " + formatBytes(totalMemory));
        System.out.println("已使用内存: " + formatBytes(usedMemory));
        System.out.println("可用内存: " + formatBytes(freeMemory));
        
        // 创建大量代理对象测试内存占用
        System.out.println("\n创建1000个JDK代理对象...");
        UserService[] proxies = new UserService[1000];
        UserService target = new UserServiceImpl();
        
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        
        for (int i = 0; i < 1000; i++) {
            proxies[i] = createJdkProxy(target);
        }
        
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = afterMemory - beforeMemory;
        
        System.out.println("内存增长: " + formatBytes(memoryIncrease));
        System.out.println("平均每个代理对象占用: " + formatBytes(memoryIncrease / 1000));
        System.out.println();
    }

    /**
     * 格式化字节数
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
