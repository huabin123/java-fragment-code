package com.fragment.core.proxy;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 模拟 Spring AOP 代理选择机制
 * 
 * Spring AOP 会根据目标类的特征自动选择 JDK 动态代理或 CGLIB 代理：
 * 1. Spring 5.x 之前：有接口用 JDK，无接口用 CGLIB
 * 2. Spring Boot 2.x+：默认使用 CGLIB（proxyTargetClass=true）
 */
public class SpringAopProxyDemo {

    public static void main(String[] args) {
        System.out.println("=== Spring AOP 代理选择机制演示 ===\n");

        // 场景1：有接口的类 - Spring 5.x 之前默认用 JDK 代理
        demonstrateInterfaceBasedProxy();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // 场景2：无接口的类 - 只能用 CGLIB 代理
        demonstrateClassBasedProxy();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // 场景3：强制使用 CGLIB（proxyTargetClass=true）
        demonstrateForceCglibProxy();

        System.out.println("\n" + "=".repeat(60) + "\n");

        // 场景4：判断代理类型
        demonstrateProxyTypeDetection();
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

        OrderService target = new OrderService();
        
        // 模拟 Spring 的 CglibAopProxy
        OrderService proxy = createCglibProxy(target);

        System.out.println("代理类型: " + proxy.getClass().getName());
        System.out.println("是否为 CGLIB 代理: " + proxy.getClass().getName().contains("$$"));
        System.out.println();

        proxy.createOrder("ORDER-001");
    }

    /**
     * 场景3：强制使用 CGLIB（即使有接口）
     * 对应 @EnableAspectJAutoProxy(proxyTargetClass = true)
     */
    private static void demonstrateForceCglibProxy() {
        System.out.println("【场景3】强制使用 CGLIB - proxyTargetClass=true");
        System.out.println("Spring Boot 2.x+ 的默认行为\n");

        UserServiceImpl target = new UserServiceImpl();
        
        // 强制使用 CGLIB，即使实现了接口
        UserServiceImpl proxy = createCglibProxy(target);

        System.out.println("代理类型: " + proxy.getClass().getName());
        System.out.println("父类类型: " + proxy.getClass().getSuperclass().getName());
        System.out.println();

        User user = proxy.findById(2L);
        System.out.println("返回结果: " + user);
    }

    /**
     * 场景4：如何判断代理类型
     */
    private static void demonstrateProxyTypeDetection() {
        System.out.println("【场景4】代理类型检测\n");

        UserService jdkProxy = createJdkProxy(new UserServiceImpl(), UserService.class);
        UserServiceImpl cglibProxy = createCglibProxy(new UserServiceImpl());

        System.out.println("=== JDK 代理检测 ===");
        detectProxyType(jdkProxy);

        System.out.println("\n=== CGLIB 代理检测 ===");
        detectProxyType(cglibProxy);
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
     */
    @SuppressWarnings("unchecked")
    private static <T> T createCglibProxy(T target) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(target.getClass());
        enhancer.setCallback(new SpringAopMethodInterceptor(target));
        return (T) enhancer.create();
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
     * 模拟 Spring 的 CglibAopProxy.DynamicAdvisedInterceptor
     */
    static class SpringAopMethodInterceptor implements MethodInterceptor {
        private final Object target;

        public SpringAopMethodInterceptor(Object target) {
            this.target = target;
        }

        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) 
                throws Throwable {
            // 模拟 Spring AOP 的拦截器链执行
            System.out.println("[CGLIB Proxy] Before: " + method.getName());
            
            try {
                // 使用 MethodProxy.invoke() 调用目标方法（性能更好）
                Object result = proxy.invoke(target, args);
                
                System.out.println("[CGLIB Proxy] After: " + method.getName());
                return result;
            } catch (Exception e) {
                System.out.println("[CGLIB Proxy] Exception: " + method.getName());
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
