package com.fragment.core.modifiers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * static 关键字演示
 * 演示静态变量、静态方法、静态代码块、静态内部类的使用
 */
public class StaticDemo {
    
    // ========== 静态变量 ==========
    
    // 静态常量
    public static final String APP_NAME = "Static Demo";
    public static final int MAX_COUNT = 100;
    
    // 静态变量（所有实例共享）
    private static int instanceCount = 0;
    private static AtomicInteger atomicCount = new AtomicInteger(0);
    
    // 静态集合（共享缓存）
    private static Map<String, String> cache = new HashMap<>();
    
    // 实例变量（每个实例独立）
    private int instanceId;
    private String name;
    
    // ========== 静态代码块 ==========
    
    static {
        System.out.println("=== Static block 1 executed ===");
        // 初始化静态变量
        cache.put("key1", "value1");
        cache.put("key2", "value2");
    }
    
    static {
        System.out.println("=== Static block 2 executed ===");
        // 可以有多个静态代码块，按顺序执行
        System.out.println("Cache initialized with " + cache.size() + " items");
    }
    
    // ========== 实例代码块 ==========
    
    {
        System.out.println("Instance block executed");
        // 每次创建实例时执行
    }
    
    // ========== 构造器 ==========
    
    public StaticDemo(String name) {
        this.name = name;
        this.instanceId = ++instanceCount;
        atomicCount.incrementAndGet();
        System.out.println("Constructor: Created instance #" + instanceId + " - " + name);
    }
    
    // ========== 静态方法 ==========
    
    /**
     * 获取实例总数
     */
    public static int getInstanceCount() {
        return instanceCount;
    }
    
    /**
     * 获取原子计数
     */
    public static int getAtomicCount() {
        return atomicCount.get();
    }
    
    /**
     * 静态方法：从缓存获取值
     */
    public static String getFromCache(String key) {
        return cache.get(key);
    }
    
    /**
     * 静态方法：添加到缓存
     */
    public static void putToCache(String key, String value) {
        cache.put(key, value);
    }
    
    /**
     * 静态方法：清空缓存
     */
    public static void clearCache() {
        cache.clear();
        System.out.println("Cache cleared");
    }
    
    /**
     * 静态方法不能访问实例成员
     */
    public static void staticMethodDemo() {
        System.out.println("\n=== Static Method Demo ===");
        
        // ✓ 可以访问静态成员
        System.out.println("APP_NAME: " + APP_NAME);
        System.out.println("Instance count: " + instanceCount);
        System.out.println("Cache size: " + cache.size());
        
        // ✗ 不能访问实例成员
        // System.out.println(instanceId); // 编译错误
        // System.out.println(name); // 编译错误
        // instanceMethod(); // 编译错误
        
        // ✗ 不能使用 this 和 super
        // System.out.println(this.name); // 编译错误
    }
    
    // ========== 实例方法 ==========
    
    /**
     * 实例方法可以访问静态成员和实例成员
     */
    public void instanceMethod() {
        System.out.println("\n=== Instance Method Demo ===");
        
        // ✓ 可以访问静态成员
        System.out.println("APP_NAME: " + APP_NAME);
        System.out.println("Total instances: " + instanceCount);
        
        // ✓ 可以访问实例成员
        System.out.println("Instance ID: " + instanceId);
        System.out.println("Name: " + name);
        
        // ✓ 可以调用静态方法
        staticMethodDemo();
    }
    
    public void displayInfo() {
        System.out.println("Instance #" + instanceId + ": " + name);
    }
    
    // ========== 静态内部类 ==========
    
    /**
     * 静态内部类：不持有外部类的引用
     */
    public static class StaticInnerClass {
        private String data;
        
        public StaticInnerClass(String data) {
            this.data = data;
        }
        
        public void display() {
            System.out.println("\n=== Static Inner Class ===");
            System.out.println("Data: " + data);
            
            // ✓ 可以访问外部类的静态成员
            System.out.println("APP_NAME: " + APP_NAME);
            System.out.println("Instance count: " + instanceCount);
            
            // ✗ 不能访问外部类的实例成员
            // System.out.println(instanceId); // 编译错误
            // System.out.println(name); // 编译错误
        }
        
        public static void staticMethod() {
            System.out.println("Static method in static inner class");
        }
    }
    
    /**
     * 非静态内部类：持有外部类的引用
     */
    public class InnerClass {
        private String data;
        
        public InnerClass(String data) {
            this.data = data;
        }
        
        public void display() {
            System.out.println("\n=== Non-Static Inner Class ===");
            System.out.println("Data: " + data);
            
            // ✓ 可以访问外部类的静态成员
            System.out.println("APP_NAME: " + APP_NAME);
            
            // ✓ 可以访问外部类的实例成员
            System.out.println("Outer instance ID: " + instanceId);
            System.out.println("Outer name: " + name);
        }
        
        // ✗ 非静态内部类不能有静态成员（除了常量）
        // public static void staticMethod() { } // 编译错误
        public static final String CONSTANT = "Allowed";
    }
    
    // ========== 工具方法示例 ==========
    
    /**
     * 工具类风格的静态方法
     */
    public static class StringUtils {
        // 私有构造器，防止实例化
        private StringUtils() {
            throw new AssertionError("Utility class cannot be instantiated");
        }
        
        public static boolean isEmpty(String str) {
            return str == null || str.trim().isEmpty();
        }
        
        public static String capitalize(String str) {
            if (isEmpty(str)) {
                return str;
            }
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
        
        public static String reverse(String str) {
            if (isEmpty(str)) {
                return str;
            }
            return new StringBuilder(str).reverse().toString();
        }
    }
    
    // ========== 单例模式示例 ==========
    
    /**
     * 饿汉式单例（类加载时创建）
     */
    public static class EagerSingleton {
        private static final EagerSingleton INSTANCE = new EagerSingleton();
        
        private EagerSingleton() {
            System.out.println("EagerSingleton instance created");
        }
        
        public static EagerSingleton getInstance() {
            return INSTANCE;
        }
        
        public void doSomething() {
            System.out.println("EagerSingleton doing something");
        }
    }
    
    /**
     * 懒汉式单例（使用时创建）
     */
    public static class LazySingleton {
        private static LazySingleton instance;
        
        private LazySingleton() {
            System.out.println("LazySingleton instance created");
        }
        
        public static synchronized LazySingleton getInstance() {
            if (instance == null) {
                instance = new LazySingleton();
            }
            return instance;
        }
        
        public void doSomething() {
            System.out.println("LazySingleton doing something");
        }
    }
    
    /**
     * 静态内部类单例（推荐）
     */
    public static class Singleton {
        private Singleton() {
            System.out.println("Singleton instance created");
        }
        
        private static class SingletonHolder {
            private static final Singleton INSTANCE = new Singleton();
        }
        
        public static Singleton getInstance() {
            return SingletonHolder.INSTANCE;
        }
        
        public void doSomething() {
            System.out.println("Singleton doing something");
        }
    }
    
    // ========== 主方法 ==========
    
    public static void main(String[] args) {
        System.out.println("\n========== Static Demo Start ==========\n");
        
        // 1. 测试静态变量和方法
        System.out.println("=== Static Variables and Methods ===");
        System.out.println("APP_NAME: " + StaticDemo.APP_NAME);
        System.out.println("Initial count: " + StaticDemo.getInstanceCount());
        
        // 2. 创建实例
        System.out.println("\n=== Creating Instances ===");
        StaticDemo obj1 = new StaticDemo("Object 1");
        StaticDemo obj2 = new StaticDemo("Object 2");
        StaticDemo obj3 = new StaticDemo("Object 3");
        
        System.out.println("\nTotal instances: " + StaticDemo.getInstanceCount());
        System.out.println("Atomic count: " + StaticDemo.getAtomicCount());
        
        // 3. 测试缓存
        System.out.println("\n=== Cache Operations ===");
        System.out.println("Cache value: " + StaticDemo.getFromCache("key1"));
        StaticDemo.putToCache("key3", "value3");
        System.out.println("Added key3, cache size: " + cache.size());
        
        // 4. 测试静态方法
        StaticDemo.staticMethodDemo();
        
        // 5. 测试实例方法
        obj1.instanceMethod();
        obj1.displayInfo();
        obj2.displayInfo();
        obj3.displayInfo();
        
        // 6. 测试静态内部类
        System.out.println("\n=== Static Inner Class ===");
        StaticDemo.StaticInnerClass staticInner = new StaticDemo.StaticInnerClass("Static Inner Data");
        staticInner.display();
        StaticDemo.StaticInnerClass.staticMethod();
        
        // 7. 测试非静态内部类
        System.out.println("\n=== Non-Static Inner Class ===");
        StaticDemo.InnerClass inner = obj1.new InnerClass("Inner Data");
        inner.display();
        
        // 8. 测试工具类
        System.out.println("\n=== Utility Class ===");
        System.out.println("isEmpty: " + StringUtils.isEmpty(""));
        System.out.println("capitalize: " + StringUtils.capitalize("hello"));
        System.out.println("reverse: " + StringUtils.reverse("hello"));
        
        // 9. 测试单例模式
        System.out.println("\n=== Singleton Patterns ===");
        
        EagerSingleton eager1 = EagerSingleton.getInstance();
        EagerSingleton eager2 = EagerSingleton.getInstance();
        System.out.println("Eager singleton same instance: " + (eager1 == eager2));
        
        LazySingleton lazy1 = LazySingleton.getInstance();
        LazySingleton lazy2 = LazySingleton.getInstance();
        System.out.println("Lazy singleton same instance: " + (lazy1 == lazy2));
        
        Singleton singleton1 = Singleton.getInstance();
        Singleton singleton2 = Singleton.getInstance();
        System.out.println("Singleton same instance: " + (singleton1 == singleton2));
        
        System.out.println("\n========== Static Demo End ==========");
    }
}
