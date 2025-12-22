package com.fragment.core.proxy;

/**
 * CGLIB 动态代理演示
 * 注意：由于CGLIB是第三方库，这里提供模拟实现和概念演示
 * 实际使用需要添加CGLIB依赖
 */
public class CglibProxyDemo {

    public static void main(String[] args) {
        System.out.println("=== CGLIB 动态代理演示 ===\n");

        demonstrateCglibConcepts();
        demonstrateManualSubclassProxy();
    }

    /**
     * 演示CGLIB的核心概念
     */
    private static void demonstrateCglibConcepts() {
        System.out.println("1. CGLIB 核心概念:");
        System.out.println("- CGLIB 通过字节码技术创建目标类的子类");
        System.out.println("- 不需要目标类实现接口");
        System.out.println("- 通过重写父类方法实现代理");
        System.out.println("- final 类和 final 方法无法被代理");
        System.out.println("- 私有方法不会被代理");
        System.out.println();

        /*
        实际CGLIB使用示例（需要添加依赖）:
        
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(Calculator.class);
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
                System.out.println("CGLIB 代理 - 方法调用前: " + method.getName());
                Object result = proxy.invokeSuper(obj, args);
                System.out.println("CGLIB 代理 - 方法调用后: " + method.getName());
                return result;
            }
        });
        
        Calculator proxy = (Calculator) enhancer.create();
        */
    }

    /**
     * 手动实现子类代理演示CGLIB原理
     */
    private static void demonstrateManualSubclassProxy() {
        System.out.println("2. 手动子类代理演示 (模拟CGLIB原理):");

        Calculator proxy = new CalculatorProxy();
        
        int result1 = proxy.add(10, 5);
        System.out.println("加法结果: " + result1);
        
        int result2 = proxy.multiply(3, 4);
        System.out.println("乘法结果: " + result2);
        
        // final 方法调用
        String version = proxy.getVersion();
        System.out.println("版本信息: " + version);
        System.out.println();
    }

    /**
     * 手动创建的代理子类 - 模拟CGLIB生成的字节码
     */
    static class CalculatorProxy extends Calculator {
        
        @Override
        public int add(int a, int b) {
            System.out.println("[CGLIB代理] 方法调用前: add(" + a + ", " + b + ")");
            long startTime = System.currentTimeMillis();
            
            try {
                int result = super.add(a, b);
                return result;
            } finally {
                long endTime = System.currentTimeMillis();
                System.out.println("[CGLIB代理] 方法调用后: add, 耗时: " + (endTime - startTime) + "ms");
            }
        }
        
        @Override
        public int subtract(int a, int b) {
            System.out.println("[CGLIB代理] 方法调用前: subtract(" + a + ", " + b + ")");
            long startTime = System.currentTimeMillis();
            
            try {
                int result = super.subtract(a, b);
                return result;
            } finally {
                long endTime = System.currentTimeMillis();
                System.out.println("[CGLIB代理] 方法调用后: subtract, 耗时: " + (endTime - startTime) + "ms");
            }
        }
        
        @Override
        public int multiply(int a, int b) {
            System.out.println("[CGLIB代理] 方法调用前: multiply(" + a + ", " + b + ")");
            long startTime = System.currentTimeMillis();
            
            try {
                int result = super.multiply(a, b);
                return result;
            } finally {
                long endTime = System.currentTimeMillis();
                System.out.println("[CGLIB代理] 方法调用后: multiply, 耗时: " + (endTime - startTime) + "ms");
            }
        }
        
        @Override
        public double divide(int a, int b) {
            System.out.println("[CGLIB代理] 方法调用前: divide(" + a + ", " + b + ")");
            long startTime = System.currentTimeMillis();
            
            try {
                double result = super.divide(a, b);
                return result;
            } finally {
                long endTime = System.currentTimeMillis();
                System.out.println("[CGLIB代理] 方法调用后: divide, 耗时: " + (endTime - startTime) + "ms");
            }
        }
        
        // 注意：final 方法无法重写，因此不会被代理
        // getVersion() 方法会直接调用父类实现
    }
}
