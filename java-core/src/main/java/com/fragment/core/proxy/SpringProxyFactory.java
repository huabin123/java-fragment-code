package com.fragment.core.proxy;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 模拟 Spring 的 DefaultAopProxyFactory
 * 
 * 这个类展示了 Spring 如何根据配置和目标类特征选择代理方式
 */
public class SpringProxyFactory {

    /**
     * 代理配置（模拟 Spring 的 AdvisedSupport）
     */
    public static class ProxyConfig {
        private Object target;
        private boolean optimize = false;           // 优化标志
        private boolean proxyTargetClass = false;   // 是否强制 CGLIB
        private Class<?>[] interfaces;              // 目标接口

        public ProxyConfig(Object target) {
            this.target = target;
            this.interfaces = target.getClass().getInterfaces();
        }

        public Object getTarget() {
            return target;
        }

        public Class<?> getTargetClass() {
            return target.getClass();
        }

        public boolean isOptimize() {
            return optimize;
        }

        public void setOptimize(boolean optimize) {
            this.optimize = optimize;
        }

        public boolean isProxyTargetClass() {
            return proxyTargetClass;
        }

        public void setProxyTargetClass(boolean proxyTargetClass) {
            this.proxyTargetClass = proxyTargetClass;
        }

        public Class<?>[] getProxiedInterfaces() {
            return interfaces;
        }

        public boolean hasNoUserSuppliedProxyInterfaces() {
            return interfaces == null || interfaces.length == 0;
        }
    }

    /**
     * 创建 AOP 代理（模拟 Spring 的 DefaultAopProxyFactory.createAopProxy）
     * 
     * 这是 Spring AOP 的核心逻辑！
     */
    public static Object createAopProxy(ProxyConfig config) {
        Class<?> targetClass = config.getTargetClass();
        
        System.out.println("\n=== Spring 代理选择逻辑 ===");
        System.out.println("目标类: " + targetClass.getName());
        System.out.println("实现的接口数: " + config.getProxiedInterfaces().length);
        System.out.println("optimize: " + config.isOptimize());
        System.out.println("proxyTargetClass: " + config.isProxyTargetClass());
        System.out.println();

        // Spring 的判断逻辑
        if (config.isOptimize() || 
            config.isProxyTargetClass() || 
            config.hasNoUserSuppliedProxyInterfaces()) {
            
            // 使用 CGLIB 的条件
            System.out.println("决策: 使用 CGLIB 代理");
            System.out.println("原因: " + getReasonForCglib(config));
            
            // 特殊情况：如果目标类本身是接口或已经是代理类，仍使用 JDK
            if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
                System.out.println("但目标类是接口或代理类，改用 JDK 代理");
                return createJdkProxy(config);
            }
            
            return createCglibProxy(config);
        } else {
            // 默认使用 JDK 动态代理
            System.out.println("决策: 使用 JDK 动态代理");
            System.out.println("原因: 目标类实现了接口，且未强制使用 CGLIB");
            return createJdkProxy(config);
        }
    }

    /**
     * 获取使用 CGLIB 的原因
     */
    private static String getReasonForCglib(ProxyConfig config) {
        if (config.isProxyTargetClass()) {
            return "proxyTargetClass=true (强制使用 CGLIB)";
        } else if (config.isOptimize()) {
            return "optimize=true (优化模式)";
        } else if (config.hasNoUserSuppliedProxyInterfaces()) {
            return "目标类没有实现接口";
        }
        return "未知";
    }

    /**
     * 创建 JDK 动态代理
     */
    @SuppressWarnings("unchecked")
    private static <T> T createJdkProxy(ProxyConfig config) {
        Object target = config.getTarget();
        Class<?>[] interfaces = config.getProxiedInterfaces();
        
        if (interfaces.length == 0) {
            throw new IllegalArgumentException("JDK 代理要求目标类至少实现一个接口");
        }
        
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                interfaces,
                new SimpleInvocationHandler(target)
        );
    }

    /**
     * 创建 CGLIB 代理
     */
    @SuppressWarnings("unchecked")
    private static <T> T createCglibProxy(ProxyConfig config) {
        Object target = config.getTarget();
        
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(target.getClass());
        
        // 如果有接口，也实现这些接口
        Class<?>[] interfaces = config.getProxiedInterfaces();
        if (interfaces.length > 0) {
            enhancer.setInterfaces(interfaces);
        }
        
        enhancer.setCallback(new SimpleMethodInterceptor(target));
        return (T) enhancer.create();
    }

    /**
     * 简单的 InvocationHandler
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
     * 简单的 MethodInterceptor
     */
    static class SimpleMethodInterceptor implements MethodInterceptor {
        private final Object target;

        public SimpleMethodInterceptor(Object target) {
            this.target = target;
        }

        @Override
        public Object intercept(Object obj, Method method, Object[] args, 
                net.sf.cglib.proxy.MethodProxy proxy) throws Throwable {
            return proxy.invoke(target, args);
        }
    }

    /**
     * 演示程序
     */
    public static void main(String[] args) {
        System.out.println("=== Spring DefaultAopProxyFactory 代理选择逻辑演示 ===");

        // 场景1：有接口，默认配置 -> JDK 代理
        demonstrateScenario1();

        System.out.println("\n" + "=".repeat(70));

        // 场景2：有接口，proxyTargetClass=true -> CGLIB 代理
        demonstrateScenario2();

        System.out.println("\n" + "=".repeat(70));

        // 场景3：无接口 -> CGLIB 代理
        demonstrateScenario3();

        System.out.println("\n" + "=".repeat(70));

        // 场景4：optimize=true -> CGLIB 代理
        demonstrateScenario4();
    }

    /**
     * 场景1：有接口，默认配置
     * Spring 5.x 之前的默认行为
     */
    private static void demonstrateScenario1() {
        System.out.println("\n【场景1】有接口 + 默认配置 (Spring 5.x 之前)");
        
        UserService target = new UserServiceImpl();
        ProxyConfig config = new ProxyConfig(target);
        // 默认: optimize=false, proxyTargetClass=false
        
        Object proxy = createAopProxy(config);
        
        System.out.println("\n结果:");
        System.out.println("代理类: " + proxy.getClass().getName());
        System.out.println("是 JDK 代理: " + Proxy.isProxyClass(proxy.getClass()));
    }

    /**
     * 场景2：有接口，proxyTargetClass=true
     * Spring Boot 2.x 的默认行为
     */
    private static void demonstrateScenario2() {
        System.out.println("\n【场景2】有接口 + proxyTargetClass=true (Spring Boot 2.x)");
        
        UserService target = new UserServiceImpl();
        ProxyConfig config = new ProxyConfig(target);
        config.setProxyTargetClass(true);  // 强制 CGLIB
        
        Object proxy = createAopProxy(config);
        
        System.out.println("\n结果:");
        System.out.println("代理类: " + proxy.getClass().getName());
        System.out.println("是 CGLIB 代理: " + proxy.getClass().getName().contains("$$"));
        System.out.println("父类: " + proxy.getClass().getSuperclass().getName());
    }

    /**
     * 场景3：无接口
     * 只能使用 CGLIB
     */
    private static void demonstrateScenario3() {
        System.out.println("\n【场景3】无接口的类");
        
        NoInterfaceService target = new NoInterfaceService();
        ProxyConfig config = new ProxyConfig(target);
        
        Object proxy = createAopProxy(config);
        
        System.out.println("\n结果:");
        System.out.println("代理类: " + proxy.getClass().getName());
        System.out.println("是 CGLIB 代理: " + proxy.getClass().getName().contains("$$"));
    }

    /**
     * 场景4：optimize=true
     * 优化模式，使用 CGLIB
     */
    private static void demonstrateScenario4() {
        System.out.println("\n【场景4】有接口 + optimize=true");
        
        UserService target = new UserServiceImpl();
        ProxyConfig config = new ProxyConfig(target);
        config.setOptimize(true);  // 优化模式
        
        Object proxy = createAopProxy(config);
        
        System.out.println("\n结果:");
        System.out.println("代理类: " + proxy.getClass().getName());
        System.out.println("是 CGLIB 代理: " + proxy.getClass().getName().contains("$$"));
    }

    /**
     * 无接口的服务类
     */
    static class NoInterfaceService {
        public String doSomething() {
            return "NoInterfaceService.doSomething()";
        }
    }
}
