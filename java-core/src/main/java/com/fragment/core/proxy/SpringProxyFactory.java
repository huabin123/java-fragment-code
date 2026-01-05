package com.fragment.core.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 模拟 Spring 的 DefaultAopProxyFactory
 * 
 * 这个类展示了 Spring 如何根据配置和目标类特征选择代理方式
 * 
 * 注意：CGLIB 部分使用反射加载，避免编译时依赖
 * 运行环境：JDK 1.8
 */
public class SpringProxyFactory {
    
    // 检查是否有 CGLIB 依赖
    private static final boolean CGLIB_AVAILABLE = checkCglibAvailable();
    
    /**
     * 检查 CGLIB 是否可用
     */
    private static boolean checkCglibAvailable() {
        try {
            Class.forName("net.sf.cglib.proxy.Enhancer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

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
        System.out.println("CGLIB 可用: " + CGLIB_AVAILABLE);
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
            
            // 检查 CGLIB 是否可用
            if (!CGLIB_AVAILABLE) {
                System.out.println("警告: CGLIB 不可用，降级使用 JDK 代理");
                if (config.getProxiedInterfaces().length > 0) {
                    return createJdkProxy(config);
                } else {
                    throw new IllegalStateException("无法创建代理：目标类没有接口且 CGLIB 不可用");
                }
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
     * 创建 CGLIB 代理（使用反射避免编译时依赖）
     */
    @SuppressWarnings("unchecked")
    private static <T> T createCglibProxy(ProxyConfig config) {
        try {
            Object target = config.getTarget();
            
            // 使用反射加载 CGLIB 类
            Class<?> enhancerClass = Class.forName("net.sf.cglib.proxy.Enhancer");
            Object enhancer = enhancerClass.newInstance();
            
            // 设置父类：enhancer.setSuperclass(target.getClass())
            Method setSuperclass = enhancerClass.getMethod("setSuperclass", Class.class);
            setSuperclass.invoke(enhancer, target.getClass());
            
            // 如果有接口，也实现这些接口：enhancer.setInterfaces(interfaces)
            Class<?>[] interfaces = config.getProxiedInterfaces();
            if (interfaces.length > 0) {
                Method setInterfaces = enhancerClass.getMethod("setInterfaces", Class[].class);
                setInterfaces.invoke(enhancer, (Object) interfaces);
            }
            
            // 设置回调：enhancer.setCallback(callback)
            Class<?> callbackClass = Class.forName("net.sf.cglib.proxy.Callback");
            Method setCallback = enhancerClass.getMethod("setCallback", callbackClass);
            Object callback = createCglibCallback(target);
            setCallback.invoke(enhancer, callback);
            
            // 创建代理：enhancer.create()
            Method create = enhancerClass.getMethod("create");
            return (T) create.invoke(enhancer);
        } catch (Exception e) {
            throw new RuntimeException("创建 CGLIB 代理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建 CGLIB 回调（使用反射）
     */
    private static Object createCglibCallback(final Object target) {
        try {
            Class<?> methodInterceptorClass = Class.forName("net.sf.cglib.proxy.MethodInterceptor");
            
            return Proxy.newProxyInstance(
                    methodInterceptorClass.getClassLoader(),
                    new Class<?>[]{methodInterceptorClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            // intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
                            if (method.getName().equals("intercept") && args != null && args.length == 4) {
                                Method targetMethod = (Method) args[1];
                                Object[] methodArgs = (Object[]) args[2];
                                
                                // 调用目标方法
                                return targetMethod.invoke(target, methodArgs);
                            }
                            return null;
                        }
                    }
            );
        } catch (Exception e) {
            throw new RuntimeException("创建 CGLIB 回调失败: " + e.getMessage(), e);
        }
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
     * 演示程序
     */
    public static void main(String[] args) {
        System.out.println("=== Spring DefaultAopProxyFactory 代理选择逻辑演示 ===");
        
        if (!CGLIB_AVAILABLE) {
            System.out.println("\n【提示】CGLIB 未找到，部分演示将跳过或降级为 JDK 代理");
            System.out.println("如需完整演示，请添加 CGLIB 依赖：");
            System.out.println("<dependency>");
            System.out.println("    <groupId>cglib</groupId>");
            System.out.println("    <artifactId>cglib</artifactId>");
            System.out.println("    <version>3.3.0</version>");
            System.out.println("</dependency>\n");
        }

        // 场景1：有接口，默认配置 -> JDK 代理
        demonstrateScenario1();

        printSeparator();

        // 场景2：有接口，proxyTargetClass=true -> CGLIB 代理
        demonstrateScenario2();

        printSeparator();

        // 场景3：无接口 -> CGLIB 代理
        demonstrateScenario3();

        printSeparator();

        // 场景4：optimize=true -> CGLIB 代理
        demonstrateScenario4();
    }
    
    /**
     * 打印分隔线（JDK 1.8 兼容）
     */
    private static void printSeparator() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 70; i++) {
            sb.append("=");
        }
        System.out.println("\n" + sb.toString());
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
