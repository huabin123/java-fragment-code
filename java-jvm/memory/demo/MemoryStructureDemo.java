package com.fragment.jvm.memory.demo;

/**
 * JVM内存结构演示
 * 
 * 演示内容：
 * 1. 程序计数器
 * 2. 虚拟机栈
 * 3. 本地方法栈
 * 4. 堆
 * 5. 方法区
 * 
 * @author huabin
 */
public class MemoryStructureDemo {
    
    // 静态变量 - 存储在方法区
    private static int staticVar = 100;
    
    // 实例变量 - 对象存储在堆中
    private int instanceVar = 200;
    
    /**
     * 示例1：程序计数器演示
     */
    static class ProgramCounterDemo {
        public static void test() {
            System.out.println("\n========== 程序计数器演示 ==========");
            
            // 每个线程都有独立的程序计数器
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    System.out.println("Thread-1: " + i);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "Thread-1");
            
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    System.out.println("Thread-2: " + i);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "Thread-2");
            
            t1.start();
            t2.start();
            
            try {
                t1.join();
                t2.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            System.out.println("说明：每个线程有独立的程序计数器，记录各自的执行位置");
        }
    }
    
    /**
     * 示例2：虚拟机栈演示
     */
    static class VMStackDemo {
        private static int stackDepth = 0;
        
        public static void test() {
            System.out.println("\n========== 虚拟机栈演示 ==========");
            
            // 演示方法调用和栈帧
            method1();
            
            System.out.println("\n栈深度测试：");
            try {
                recursion();
            } catch (StackOverflowError e) {
                System.out.println("栈溢出，栈深度: " + stackDepth);
            }
        }
        
        private static void method1() {
            int a = 1;  // 局部变量，存储在栈帧的局部变量表
            int b = 2;
            System.out.println("method1调用method2");
            int result = method2(a, b);
            System.out.println("method2返回结果: " + result);
        }
        
        private static int method2(int x, int y) {
            System.out.println("method2执行中");
            return x + y;
        }
        
        private static void recursion() {
            stackDepth++;
            recursion();
        }
    }
    
    /**
     * 示例3：本地方法栈演示
     */
    static class NativeMethodStackDemo {
        public static void test() {
            System.out.println("\n========== 本地方法栈演示 ==========");
            
            // 调用Native方法
            Object obj = new Object();
            
            // hashCode是Native方法
            int hash = obj.hashCode();
            System.out.println("hashCode (Native方法): " + hash);
            
            // getClass是Native方法
            Class<?> clazz = obj.getClass();
            System.out.println("getClass (Native方法): " + clazz.getName());
            
            // currentTimeMillis是Native方法
            long time = System.currentTimeMillis();
            System.out.println("currentTimeMillis (Native方法): " + time);
            
            System.out.println("说明：Native方法在本地方法栈中执行");
        }
    }
    
    /**
     * 示例4：堆内存演示
     */
    static class HeapDemo {
        static class User {
            private String name;
            private int age;
            
            public User(String name, int age) {
                this.name = name;
                this.age = age;
            }
        }
        
        public static void test() {
            System.out.println("\n========== 堆内存演示 ==========");
            
            // 对象实例存储在堆中
            User user1 = new User("张三", 20);
            User user2 = new User("李四", 25);
            
            System.out.println("user1和user2对象存储在堆中");
            System.out.println("user1引用存储在栈中，指向堆中的对象");
            
            // 数组也存储在堆中
            int[] array = new int[10];
            System.out.println("数组对象也存储在堆中");
            
            // 查看堆内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            System.out.println("\n堆内存使用情况：");
            System.out.println("总内存: " + (totalMemory / 1024 / 1024) + "MB");
            System.out.println("空闲内存: " + (freeMemory / 1024 / 1024) + "MB");
            System.out.println("已使用内存: " + (usedMemory / 1024 / 1024) + "MB");
        }
    }
    
    /**
     * 示例5：方法区演示
     */
    static class MethodAreaDemo {
        // 静态变量存储在方法区
        private static String staticField = "静态变量";
        
        // 常量存储在方法区的运行时常量池
        private static final String CONSTANT = "常量";
        
        public static void test() {
            System.out.println("\n========== 方法区演示 ==========");
            
            // 类信息存储在方法区
            Class<?> clazz = MethodAreaDemo.class;
            System.out.println("类名: " + clazz.getName());
            System.out.println("类信息存储在方法区");
            
            // 静态变量存储在方法区
            System.out.println("静态变量: " + staticField);
            
            // 字符串常量池
            String s1 = "hello";  // 字面量，存储在常量池
            String s2 = "hello";  // 从常量池获取
            String s3 = new String("hello");  // 堆中创建新对象
            
            System.out.println("\n字符串常量池：");
            System.out.println("s1 == s2: " + (s1 == s2));  // true
            System.out.println("s1 == s3: " + (s1 == s3));  // false
            System.out.println("s1 == s3.intern(): " + (s1 == s3.intern()));  // true
        }
    }
    
    /**
     * 示例6：内存区域交互演示
     */
    static class MemoryInteractionDemo {
        private int instanceVar = 100;  // 实例变量，对象在堆中
        private static int staticVar = 200;  // 静态变量，在方法区
        
        public void method() {
            int localVar = 300;  // 局部变量，在栈中
            
            System.out.println("\n========== 内存区域交互 ==========");
            System.out.println("局部变量localVar=" + localVar + " (存储在虚拟机栈)");
            System.out.println("实例变量instanceVar=" + instanceVar + " (对象在堆中)");
            System.out.println("静态变量staticVar=" + staticVar + " (存储在方法区)");
            
            // 创建对象
            String str = new String("test");
            System.out.println("\nString对象创建过程：");
            System.out.println("1. 引用str存储在栈中");
            System.out.println("2. String对象存储在堆中");
            System.out.println("3. \"test\"字面量存储在常量池（方法区）");
        }
        
        public static void test() {
            MemoryInteractionDemo demo = new MemoryInteractionDemo();
            demo.method();
        }
    }
    
    /**
     * 示例7：内存分配演示
     */
    static class MemoryAllocationDemo {
        public static void test() {
            System.out.println("\n========== 内存分配演示 ==========");
            
            // 基本类型变量
            int a = 10;  // 栈中
            long b = 20L;  // 栈中
            
            // 对象类型
            Integer c = 30;  // 引用在栈中，对象在堆中
            String d = "hello";  // 引用在栈中，对象在堆中，字面量在常量池
            
            // 数组
            int[] array = new int[10];  // 引用在栈中，数组对象在堆中
            
            System.out.println("基本类型变量直接存储在栈中");
            System.out.println("对象引用存储在栈中，对象实例存储在堆中");
            System.out.println("数组引用存储在栈中，数组对象存储在堆中");
        }
    }
    
    public static void main(String[] args) {
        System.out.println("JVM内存结构演示");
        System.out.println("JVM参数建议：-Xms256m -Xmx256m -Xss256k");
        
        // 运行所有示例
        ProgramCounterDemo.test();
        VMStackDemo.test();
        NativeMethodStackDemo.test();
        HeapDemo.test();
        MethodAreaDemo.test();
        MemoryInteractionDemo.test();
        MemoryAllocationDemo.test();
        
        System.out.println("\n========== 总结 ==========");
        System.out.println("1. 程序计数器：线程私有，记录字节码行号");
        System.out.println("2. 虚拟机栈：线程私有，存储局部变量和方法调用");
        System.out.println("3. 本地方法栈：线程私有，为Native方法服务");
        System.out.println("4. 堆：线程共享，存储对象实例");
        System.out.println("5. 方法区：线程共享，存储类信息、常量、静态变量");
    }
}
