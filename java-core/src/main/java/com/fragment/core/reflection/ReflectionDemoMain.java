package com.fragment.core.reflection;

/**
 * 反射演示主程序
 */
public class ReflectionDemoMain {

    public static void main(String[] args) throws Exception {
        System.out.println("Java 反射深入学习演示");
        System.out.println("===================\n");

        // 1. 基础反射操作
        System.out.println(">>> 运行基础反射演示:");
        BasicReflectionDemo.main(args);

        System.out.println("\n" + "=".repeat(50) + "\n");

        // 2. 泛型反射
        System.out.println(">>> 运行泛型反射演示:");
        GenericReflectionDemo.main(args);

        System.out.println("\n" + "=".repeat(50) + "\n");

        // 3. 数组反射
        System.out.println(">>> 运行数组反射演示:");
        ArrayReflectionDemo.main(args);

        System.out.println("\n" + "=".repeat(50) + "\n");

        // 4. 性能对比
        System.out.println(">>> 运行性能对比演示:");
        PerformanceDemo.main(args);

        System.out.println("\n" + "=".repeat(50) + "\n");

        // 5. 实用工具演示
        System.out.println(">>> Bean 拷贝工具演示:");
        demonstrateBeanCopy();
    }

    private static void demonstrateBeanCopy() throws Exception {
        Person source = new Person("张三", 30);
        source.setEmail("zhangsan@example.com");
        source.setActive(true);

        System.out.println("源对象: " + source);

        Person target = BeanCopyUtil.copyProperties(source, Person.class);
        System.out.println("拷贝对象: " + target);
    }
}
