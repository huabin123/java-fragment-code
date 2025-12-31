package com.fragment.core.proxy;

/**
 * 动态代理演示主程序
 */
public class ProxyDemoMain {

    public static void main(String[] args) throws Exception {
        System.out.println("Java 动态代理深入学习演示");
        System.out.println("========================\n");

        // 1. JDK动态代理演示
        System.out.println(">>> 运行JDK动态代理演示:");
        JdkProxyDemo.main(args);

        System.out.println("\n" + "================" + "\n");

        // 2. CGLIB代理演示
        System.out.println(">>> 运行CGLIB代理演示:");
        CglibProxyDemo.main(args);

        System.out.println("\n" + "================" + "\n");

        // 3. 性能对比演示
        System.out.println(">>> 运行性能对比演示:");
//        ProxyPerformanceDemo.main(args);

        System.out.println("\n" + "================" + "\n");

        // 4. AOP框架演示
        System.out.println(">>> AOP框架演示:");
        demonstrateAopFramework();

        System.out.println("\n" + "================" + "\n");

        // 5. Spring AOP 代理选择机制演示
        System.out.println(">>> Spring AOP 代理选择机制演示:");
        SpringAopProxyDemo.main(args);

        System.out.println("\n" + "================" + "\n");

        // 6. Spring DefaultAopProxyFactory 逻辑演示
        System.out.println(">>> Spring DefaultAopProxyFactory 逻辑演示:");
        SpringProxyFactory.main(args);
    }

    private static void demonstrateAopFramework() {
        UserService target = new UserServiceImpl();

        // 创建带有多个切面的代理
        UserService proxy = AopFramework.createProxy(target,
                new AopFramework.LoggingAspect(),
                new AopFramework.PerformanceAspect()
        );

        System.out.println("使用AOP框架创建的代理:");
        User user = proxy.findById(1L);
        System.out.println("查询结果: " + user);
    }
}
