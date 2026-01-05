package com.fragment.core.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 模拟 Spring AOP 代理选择机制
 * 
 * Spring AOP 会根据目标类的特征自动选择 JDK 动态代理或 CGLIB 代理：
 * 1. Spring 5.x 之前：有接口用 JDK，无接口用 CGLIB
 * 2. Spring Boot 2.x+：默认使用 CGLIB（proxyTargetClass=true）
 * 
 * 注意：本示例主要演示 JDK 动态代理，CGLIB 部分需要添加依赖
 * 
 * 运行环境：JDK 1.8
 */
public class SpringAopProxyDemo {

    // 检查是否有 CGLIB 依赖
    private static final boolean CGLIB_AVAILABLE = checkCglibAvailable();

    public static void main(String[] args) {
        System.out.println("=== Spring AOP 代理选择机制演示 ===\n");
        System.out.println("CGLIB 可用: " + CGLIB_AVAILABLE);
        System.out.println();

        // 场景1：有接口的类 - Spring 5.x 之前默认用 JDK 代理
        demonstrateInterfaceBasedProxy();

        System.out.println("\n" + "===========================" + "\n");

        if (CGLIB_AVAILABLE) {
            // 场景2：无接口的类 - 只能用 CGLIB 代理
            demonstrateClassBasedProxy();

            System.out.println("\n" + "===========================" + "\n");

            // 场景3：强制使用 CGLIB（proxyTargetClass=true）
            demonstrateForceCglibProxy();

            System.out.println("\n" + "===========================" + "\n");
        } else {
            System.out.println("【提示】CGLIB 未找到，跳过 CGLIB 相关演示");
            System.out.println("如需使用 CGLIB，请添加依赖：");
            System.out.println("<dependency>");
            System.out.println("    <groupId>cglib</groupId>");
            System.out.println("    <artifactId>cglib</artifactId>");
            System.out.println("    <version>3.3.0</version>");
            System.out.println("</dependency>");
            System.out.println("\n" + "===========================" + "\n");
        }

        // 场景4：判断代理类型
        demonstrateProxyTypeDetection();
    }

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
     * 场景1：有接口的类 - 使用 JDK 动态代理
     */
    private static void demonstrateInterfaceBasedProxy() {
        System.out.println("【场景1】有接口的类 - JDK 动态代理");
        System.out.println("Spring 5.x 之前的默认行为\n");

        UserService target = new UserServiceImpl();
        
        // 模拟 Spring 的 JdkDynamicAopProxy
        UserService proxy = createJdkProxy(target, UserService.class);

        System.out.println("代理类型: " + proxy.getClass().getName());
        System.out.println("是否为 JDK 代理: " + Proxy.isProxyClass(proxy.getClass()));
        System.out.println();

        User user = proxy.findById(1L);
        System.out.println("返回结果: " + user);
    }

    /**
     * 场景2：无接口的类 - 使用 CGLIB 代理
     */
    private static void demonstrateClassBasedProxy() {
        System.out.println("【场景2】无接口的类 - CGLIB 代理");
        System.out.println("Spring 无法使用 JDK 代理，自动选择 CGLIB\n");

        try {
            OrderService target = new OrderService();
            
            // 模拟 Spring 的 CglibAopProxy
            OrderService proxy = createCglibProxy(target);

            System.out.println("代理类型: " + proxy.getClass().getName());
            System.out.println("是否为 CGLIB 代理: " + proxy.getClass().getName().contains("$$"));
            System.out.println();

            proxy.createOrder("ORDER-001");
        } catch (Exception e) {
            System.err.println("CGLIB 代理创建失败: " + e.getMessage());
        }
    }

    /**
     * 场景3：强制使用 CGLIB（即使有接口）
     * 对应 @EnableAspectJAutoProxy(proxyTargetClass = true)
     */
    private static void demonstrateForceCglibProxy() {
        System.out.println("【场景3】强制使用 CGLIB - proxyTargetClass=true");
        System.out.println("Spring Boot 2.x+ 的默认行为\n");

        try {
            UserServiceImpl target = new UserServiceImpl();
            
            // 强制使用 CGLIB，即使实现了接口
            UserServiceImpl proxy = createCglibProxy(target);

            System.out.println("代理类型: " + proxy.getClass().getName());
            System.out.println("父类类型: " + proxy.getClass().getSuperclass().getName());
            System.out.println();

            User user = proxy.findById(2L);
            System.out.println("返回结果: " + user);
        } catch (Exception e) {
            System.err.println("CGLIB 代理创建失败: " + e.getMessage());
        }
    }

    /**
     * 场景4：如何判断代理类型
     */
    private static void demonstrateProxyTypeDetection() {
        System.out.println("【场景4】代理类型检测\n");

        UserService jdkProxy = createJdkProxy(new UserServiceImpl(), UserService.class);

        System.out.println("=== JDK 代理检测 ===");
        detectProxyType(jdkProxy);

        if (CGLIB_AVAILABLE) {
            try {
                UserServiceImpl cglibProxy = createCglibProxy(new UserServiceImpl());
                System.out.println("\n=== CGLIB 代理检测 ===");
                detectProxyType(cglibProxy);
            } catch (Exception e) {
                System.err.println("\nCGLIB 代理创建失败: " + e.getMessage());
            }
        }
    }

    /**
     * 创建 JDK 动态代理（模拟 Spring 的 JdkDynamicAopProxy）
     */
    @SuppressWarnings("unchecked")
    private static <T> T createJdkProxy(T target, Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{interfaceClass},
                new SpringAopInvocationHandler(target)
        );
    }

    /**
     * 创建 CGLIB 代理（模拟 Spring 的 CglibAopProxy）
     * 使用反射避免编译时依赖
     */
    @SuppressWarnings("unchecked")
    private static <T> T createCglibProxy(T target) {
        try {
            // 使用反射加载 CGLIB 类
            Class<?> enhancerClass = Class.forName("net.sf.cglib.proxy.Enhancer");
            Object enhancer = enhancerClass.newInstance();
            
            // 设置父类
            Method setSuperclass = enhancerClass.getMethod("setSuperclass", Class.class);
            setSuperclass.invoke(enhancer, target.getClass());
            
            // 设置回调
            Class<?> callbackClass = Class.forName("net.sf.cglib.proxy.Callback");
            Method setCallback = enhancerClass.getMethod("setCallback", callbackClass);
            Object callback = createCglibCallback(target);
            setCallback.invoke(enhancer, callback);
            
            // 创建代理
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
            
            return java.lang.reflect.Proxy.newProxyInstance(
                    methodInterceptorClass.getClassLoader(),
                    new Class<?>[]{methodInterceptorClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            // intercept(Object obj, Method method, Object[] args, MethodProxy proxy)
                            if (method.getName().equals("intercept") && args.length == 4) {
                                Object obj = args[0];
                                Method targetMethod = (Method) args[1];
                                Object[] methodArgs = (Object[]) args[2];
                                Object methodProxy = args[3];
                                
                                System.out.println("[CGLIB Proxy] Before: " + targetMethod.getName());
                                
                                try {
                                    // 调用目标方法
                                    Object result = targetMethod.invoke(target, methodArgs);
                                    System.out.println("[CGLIB Proxy] After: " + targetMethod.getName());
                                    return result;
                                } catch (Exception e) {
                                    System.out.println("[CGLIB Proxy] Exception: " + targetMethod.getName());
                                    throw e;
                                }
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
     * 检测代理类型（模拟 Spring 的 AopUtils）
     */
    private static void detectProxyType(Object proxy) {
        Class<?> clazz = proxy.getClass();
        
        System.out.println("类名: " + clazz.getName());
        System.out.println("简单类名: " + clazz.getSimpleName());
        
        // 方法1：通过 Proxy.isProxyClass() 判断
        boolean isJdkProxy = Proxy.isProxyClass(clazz);
        System.out.println("是否为 JDK 代理 (Proxy.isProxyClass): " + isJdkProxy);
        
        // 方法2：通过类名判断 CGLIB
        boolean isCglibProxy = clazz.getName().contains("$$EnhancerBy");
        System.out.println("是否为 CGLIB 代理 (类名包含 $$): " + isCglibProxy);
        
        // 方法3：通过父类判断
        if (!isJdkProxy) {
            System.out.println("父类: " + clazz.getSuperclass().getName());
        }
        
        // 方法4：通过接口判断
        Class<?>[] interfaces = clazz.getInterfaces();
        System.out.println("实现的接口数量: " + interfaces.length);
        if (interfaces.length > 0) {
            System.out.print("接口列表: ");
            for (Class<?> iface : interfaces) {
                System.out.print(iface.getSimpleName() + " ");
            }
            System.out.println();
        }
    }

    /**
     * 模拟 Spring 的 JdkDynamicAopProxy.invoke()
     */
    static class SpringAopInvocationHandler implements InvocationHandler {
        private final Object target;

        public SpringAopInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 模拟 Spring AOP 的拦截器链执行
            System.out.println("[JDK Proxy] Before: " + method.getName());
            
            try {
                // 执行目标方法
                Object result = method.invoke(target, args);
                
                System.out.println("[JDK Proxy] After: " + method.getName());
                return result;
            } catch (Exception e) {
                System.out.println("[JDK Proxy] Exception: " + method.getName());
                throw e;
            }
        }
    }


    /**
     * 无接口的服务类（只能用 CGLIB 代理）
     */
    static class OrderService {
        public void createOrder(String orderId) {
            System.out.println("创建订单: " + orderId);
        }

        public void cancelOrder(String orderId) {
            System.out.println("取消订单: " + orderId);
        }
    }
}
