package com.fragment.core.reflection;

import java.lang.reflect.Method;

/**
 * 反射性能对比演示
 */
public class PerformanceDemo {

    private static final int ITERATIONS = 1_000_000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== 反射性能对比演示 ===\n");

        Person person = new Person("测试", 25);
        
        // 预热 JVM
        warmUp(person);
        
        // 性能测试
        testDirectAccess(person);
        testReflectionAccess(person);
        testCachedReflectionAccess(person);
    }

    private static void warmUp(Person person) throws Exception {
        Method method = Person.class.getMethod("getName");
        for (int i = 0; i < 10000; i++) {
            person.getName();
            method.invoke(person);
        }
    }

    private static void testDirectAccess(Person person) {
        long start = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            String name = person.getName();
        }
        
        long end = System.nanoTime();
        System.out.println("直接调用耗时: " + (end - start) / 1_000_000 + " ms");
    }

    private static void testReflectionAccess(Person person) throws Exception {
        long start = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            Method method = Person.class.getMethod("getName");
            String name = (String) method.invoke(person);
        }
        
        long end = System.nanoTime();
        System.out.println("反射调用耗时: " + (end - start) / 1_000_000 + " ms");
    }

    private static void testCachedReflectionAccess(Person person) throws Exception {
        Method method = Person.class.getMethod("getName");
        
        long start = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            String name = (String) method.invoke(person);
        }
        
        long end = System.nanoTime();
        System.out.println("缓存反射调用耗时: " + (end - start) / 1_000_000 + " ms");
    }
}
