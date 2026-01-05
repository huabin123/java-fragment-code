package com.fragment.juc.jmm.project;

/**
 * 双重检查锁单例模式
 * 
 * 演示内容：
 * 1. 错误的双重检查锁（没有volatile）
 * 2. 正确的双重检查锁（使用volatile）
 * 3. 其他单例模式实现
 * 
 * @author huabin
 */
public class DoubleCheckSingleton {

    /**
     * 错误实现：没有volatile
     * 问题：可能返回未完全初始化的对象
     */
    static class WrongSingleton {
        private static WrongSingleton instance;
        private final String data;

        private WrongSingleton() {
            // 模拟复杂的初始化
            this.data = "Initialized at " + System.currentTimeMillis();
        }

        public static WrongSingleton getInstance() {
            if (instance == null) {                    // 第一次检查
                synchronized (WrongSingleton.class) {
                    if (instance == null) {            // 第二次检查
                        // 问题：new WrongSingleton() 可能被重排序
                        // 1. 分配内存
                        // 2. 初始化对象
                        // 3. 将instance指向内存
                        // 可能重排序为: 1 -> 3 -> 2
                        instance = new WrongSingleton();
                    }
                }
            }
            return instance;
        }

        public String getData() {
            return data;
        }
    }

    /**
     * 正确实现：使用volatile
     */
    static class CorrectSingleton {
        private static volatile CorrectSingleton instance;
        private final String data;

        private CorrectSingleton() {
            this.data = "Initialized at " + System.currentTimeMillis();
        }

        public static CorrectSingleton getInstance() {
            if (instance == null) {                     // 第一次检查
                synchronized (CorrectSingleton.class) {
                    if (instance == null) {             // 第二次检查
                        // volatile禁止重排序
                        // 保证对象完全初始化后才对其他线程可见
                        instance = new CorrectSingleton();
                    }
                }
            }
            return instance;
        }

        public String getData() {
            return data;
        }
    }

    /**
     * 方案3：静态内部类（推荐）
     * 优点：懒加载 + 线程安全 + 无需synchronized
     */
    static class StaticInnerClassSingleton {
        private final String data;

        private StaticInnerClassSingleton() {
            this.data = "Initialized at " + System.currentTimeMillis();
        }

        // 静态内部类只有在被使用时才会加载
        private static class Holder {
            private static final StaticInnerClassSingleton INSTANCE = 
                new StaticInnerClassSingleton();
        }

        public static StaticInnerClassSingleton getInstance() {
            // JVM保证类加载的线程安全
            return Holder.INSTANCE;
        }

        public String getData() {
            return data;
        }
    }

    /**
     * 方案4：枚举（最安全）
     * 优点：线程安全 + 防止反序列化创建新对象 + 防止反射攻击
     */
    enum EnumSingleton {
        INSTANCE;

        private final String data;

        EnumSingleton() {
            this.data = "Initialized at " + System.currentTimeMillis();
        }

        public String getData() {
            return data;
        }
    }

    /**
     * 测试单例的线程安全性
     */
    private static void testSingleton(String name, java.util.function.Supplier<?> getInstance) 
            throws InterruptedException {
        System.out.println("\n---------- 测试: " + name + " ----------");

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        Object[] instances = new Object[threadCount];

        // 创建多个线程同时获取单例
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                instances[index] = getInstance.get();
                System.out.println("Thread-" + index + " 获取到实例: " + 
                                   System.identityHashCode(instances[index]));
            }, "Thread-" + i);
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 检查是否是同一个实例
        boolean allSame = true;
        Object first = instances[0];
        for (int i = 1; i < threadCount; i++) {
            if (instances[i] != first) {
                allSame = false;
                System.out.println("⚠️  发现不同的实例: " + 
                                   System.identityHashCode(instances[i]));
            }
        }

        if (allSame) {
            System.out.println("✅ 所有线程获取到同一个实例");
        } else {
            System.out.println("❌ 存在多个实例 - 线程不安全");
        }
    }

    /**
     * 解释双重检查锁的原理
     */
    public static void explainDCL() {
        System.out.println("\n========== 双重检查锁原理解释 ==========");
        
        System.out.println("\n为什么需要双重检查？");
        System.out.println("  第一次检查（外层）：");
        System.out.println("    - 避免不必要的同步");
        System.out.println("    - 提高性能");
        System.out.println("  第二次检查（内层）：");
        System.out.println("    - 防止多个线程同时通过第一次检查");
        System.out.println("    - 保证只创建一个实例");
        
        System.out.println("\n为什么需要volatile？");
        System.out.println("  new Singleton() 的执行步骤：");
        System.out.println("    1. memory = allocate()   // 分配内存");
        System.out.println("    2. ctorInstance(memory)  // 初始化对象");
        System.out.println("    3. instance = memory     // 设置引用");
        
        System.out.println("\n  可能的重排序：1 -> 3 -> 2");
        System.out.println("    线程A执行到步骤3，instance不为null");
        System.out.println("    线程B执行第一次检查，发现instance != null");
        System.out.println("    线程B直接返回instance");
        System.out.println("    但此时对象还没初始化完成（步骤2未执行）");
        System.out.println("    线程B使用未初始化的对象 -> 出错！");
        
        System.out.println("\n  volatile的作用：");
        System.out.println("    1. 禁止指令重排序（保证1-2-3的顺序）");
        System.out.println("    2. 保证可见性（对象初始化完成后才对其他线程可见）");
        System.out.println("    3. 建立happens-before关系");
        
        System.out.println("\n性能考虑：");
        System.out.println("  - volatile的开销很小");
        System.out.println("  - 第一次检查避免了大部分同步开销");
        System.out.println("  - 只有第一次创建时需要同步");
        System.out.println("  - 后续调用只需要读取volatile变量");
        
        System.out.println("===========================");
    }

    /**
     * 比较各种单例实现
     */
    public static void compareSingletonPatterns() {
        System.out.println("\n========== 单例模式实现对比 ==========");
        
        System.out.println("\n1. 饿汉式（类加载时创建）");
        System.out.println("   优点：简单、线程安全");
        System.out.println("   缺点：不是懒加载、可能浪费内存");
        System.out.println("   代码：private static final Singleton INSTANCE = new Singleton();");
        
        System.out.println("\n2. 懒汉式 + synchronized方法");
        System.out.println("   优点：懒加载、线程安全");
        System.out.println("   缺点：性能差（每次都要同步）");
        System.out.println("   代码：public static synchronized Singleton getInstance()");
        
        System.out.println("\n3. 双重检查锁 + volatile");
        System.out.println("   优点：懒加载、线程安全、性能好");
        System.out.println("   缺点：代码复杂、需要理解JMM");
        System.out.println("   代码：private static volatile Singleton instance;");
        
        System.out.println("\n4. 静态内部类");
        System.out.println("   优点：懒加载、线程安全、简单、性能好");
        System.out.println("   缺点：无");
        System.out.println("   代码：private static class Holder { static final Singleton INSTANCE = ...; }");
        System.out.println("   推荐：✅ 最佳实践");
        
        System.out.println("\n5. 枚举");
        System.out.println("   优点：最安全、防反射、防反序列化");
        System.out.println("   缺点：不是懒加载、语法不够直观");
        System.out.println("   代码：enum Singleton { INSTANCE; }");
        System.out.println("   推荐：✅ 《Effective Java》推荐");
        
        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              双重检查锁单例模式演示                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 解释双重检查锁
        explainDCL();

        // 测试各种单例实现
        testSingleton("双重检查锁（volatile）", CorrectSingleton::getInstance);
        testSingleton("静态内部类", StaticInnerClassSingleton::getInstance);
        testSingleton("枚举", () -> EnumSingleton.INSTANCE);

        // 比较各种实现
        compareSingletonPatterns();

        System.out.println("\n" + "===========================");
        System.out.println("学习要点：");
        System.out.println("1. 双重检查锁必须使用volatile");
        System.out.println("2. volatile禁止new对象时的指令重排序");
        System.out.println("3. 静态内部类是最佳实践（简单且高效）");
        System.out.println("4. 枚举是最安全的实现（防反射和反序列化）");
        System.out.println("===========================");
    }
}
