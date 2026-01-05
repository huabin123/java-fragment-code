package com.example.jvm.classloader.demo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 类加载过程演示
 * 
 * 演示内容：
 * 1. 类加载的时机
 * 2. 类初始化顺序
 * 3. 静态代码块执行时机
 * 4. 类加载器的层次结构
 * 
 * @author JavaGuide
 */
public class ClassLoadingDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. 类加载器层次结构 ==========");
        demonstrateClassLoaderHierarchy();
        
        System.out.println("\n========== 2. 类加载时机 ==========");
        demonstrateClassLoadingTiming();
        
        System.out.println("\n========== 3. 类初始化顺序 ==========");
        demonstrateInitializationOrder();
        
        System.out.println("\n========== 4. 不触发类初始化的情况 ==========");
        demonstrateNoInitialization();
        
        System.out.println("\n========== 5. 准备阶段 vs 初始化阶段 ==========");
        demonstratePreparationVsInitialization();
    }

    /**
     * 演示类加载器层次结构
     */
    private static void demonstrateClassLoaderHierarchy() {
        // 获取当前类的类加载器
        ClassLoader currentLoader = ClassLoadingDemo.class.getClassLoader();
        System.out.println("当前类的类加载器: " + currentLoader);
        
        // 获取父加载器
        ClassLoader parentLoader = currentLoader.getParent();
        System.out.println("父加载器(Extension): " + parentLoader);
        
        // 获取祖父加载器
        ClassLoader grandParentLoader = parentLoader.getParent();
        System.out.println("祖父加载器(Bootstrap): " + grandParentLoader);
        
        // String类由Bootstrap ClassLoader加载
        ClassLoader stringLoader = String.class.getClassLoader();
        System.out.println("String类的类加载器: " + stringLoader);
        
        // 打印类加载路径
        System.out.println("\n类加载路径:");
        System.out.println("Bootstrap ClassPath: " + System.getProperty("sun.boot.class.path"));
        System.out.println("\nExtension ClassPath: " + System.getProperty("java.ext.dirs"));
        System.out.println("\nApplication ClassPath: " + System.getProperty("java.class.path"));
    }

    /**
     * 演示类加载时机
     */
    private static void demonstrateClassLoadingTiming() throws Exception {
        System.out.println("1. 使用new关键字创建对象:");
        LoadingTimingDemo1 demo1 = new LoadingTimingDemo1();
        
        System.out.println("\n2. 访问静态字段:");
        int value = LoadingTimingDemo2.staticField;
        
        System.out.println("\n3. 调用静态方法:");
        LoadingTimingDemo3.staticMethod();
        
        System.out.println("\n4. 使用反射:");
        Class.forName("com.example.jvm.classloader.demo.ClassLoadingDemo$LoadingTimingDemo4");
        
        System.out.println("\n5. 初始化子类时，父类会先初始化:");
        ChildClass child = new ChildClass();
    }

    /**
     * 演示类初始化顺序
     */
    private static void demonstrateInitializationOrder() {
        System.out.println("创建Child对象:");
        new Child();
    }

    /**
     * 演示不触发类初始化的情况
     */
    private static void demonstrateNoInitialization() throws Exception {
        System.out.println("1. 通过子类引用父类的静态字段:");
        System.out.println("ParentStatic.VALUE = " + ChildStatic.PARENT_VALUE);
        
        System.out.println("\n2. 通过数组定义来引用类:");
        NoInitClass[] array = new NoInitClass[10];
        System.out.println("创建了数组，但NoInitClass未初始化");
        
        System.out.println("\n3. 引用常量:");
        System.out.println("ConstantClass.CONSTANT = " + ConstantClass.CONSTANT);
        
        System.out.println("\n4. 使用ClassLoader.loadClass():");
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> clazz = loader.loadClass("com.example.jvm.classloader.demo.ClassLoadingDemo$LoadClassDemo");
        System.out.println("类已加载但未初始化");
    }

    /**
     * 演示准备阶段和初始化阶段的区别
     */
    private static void demonstratePreparationVsInitialization() {
        System.out.println("查看PreparationDemo类的初始化过程");
        new PreparationDemo();
    }

    // ==================== 内部测试类 ====================

    /**
     * 加载时机演示1：new关键字
     */
    static class LoadingTimingDemo1 {
        static {
            System.out.println("  LoadingTimingDemo1 类初始化");
        }
    }

    /**
     * 加载时机演示2：访问静态字段
     */
    static class LoadingTimingDemo2 {
        static int staticField = 100;
        
        static {
            System.out.println("  LoadingTimingDemo2 类初始化");
        }
    }

    /**
     * 加载时机演示3：调用静态方法
     */
    static class LoadingTimingDemo3 {
        static {
            System.out.println("  LoadingTimingDemo3 类初始化");
        }
        
        static void staticMethod() {
            System.out.println("  静态方法被调用");
        }
    }

    /**
     * 加载时机演示4：反射
     */
    static class LoadingTimingDemo4 {
        static {
            System.out.println("  LoadingTimingDemo4 类初始化（通过反射）");
        }
    }

    /**
     * 父类
     */
    static class ParentClass {
        static {
            System.out.println("  ParentClass 静态代码块");
        }
        
        {
            System.out.println("  ParentClass 实例代码块");
        }
        
        public ParentClass() {
            System.out.println("  ParentClass 构造方法");
        }
    }

    /**
     * 子类
     */
    static class ChildClass extends ParentClass {
        static {
            System.out.println("  ChildClass 静态代码块");
        }
        
        {
            System.out.println("  ChildClass 实例代码块");
        }
        
        public ChildClass() {
            System.out.println("  ChildClass 构造方法");
        }
    }

    /**
     * 父类（完整初始化顺序）
     */
    static class Parent {
        static int parentStaticField = getParentStaticField();
        
        static {
            System.out.println("  1. Parent 静态代码块");
        }
        
        int parentInstanceField = getParentInstanceField();
        
        {
            System.out.println("  3. Parent 实例代码块");
        }
        
        public Parent() {
            System.out.println("  4. Parent 构造方法");
        }
        
        static int getParentStaticField() {
            System.out.println("  0. Parent 静态字段初始化");
            return 1;
        }
        
        int getParentInstanceField() {
            System.out.println("  2. Parent 实例字段初始化");
            return 1;
        }
    }

    /**
     * 子类（完整初始化顺序）
     */
    static class Child extends Parent {
        static int childStaticField = getChildStaticField();
        
        static {
            System.out.println("  5. Child 静态代码块");
        }
        
        int childInstanceField = getChildInstanceField();
        
        {
            System.out.println("  7. Child 实例代码块");
        }
        
        public Child() {
            System.out.println("  8. Child 构造方法");
        }
        
        static int getChildStaticField() {
            System.out.println("  4. Child 静态字段初始化");
            return 1;
        }
        
        int getChildInstanceField() {
            System.out.println("  6. Child 实例字段初始化");
            return 1;
        }
    }

    /**
     * 父类（静态字段）
     */
    static class ParentStatic {
        static final int VALUE = 100;
        
        static {
            System.out.println("  ParentStatic 类初始化");
        }
    }

    /**
     * 子类（静态字段）
     */
    static class ChildStatic extends ParentStatic {
        static final int PARENT_VALUE = ParentStatic.VALUE;
        
        static {
            System.out.println("  ChildStatic 类初始化");
        }
    }

    /**
     * 不初始化的类
     */
    static class NoInitClass {
        static {
            System.out.println("  NoInitClass 类初始化");
        }
    }

    /**
     * 常量类
     */
    static class ConstantClass {
        static final String CONSTANT = "CONSTANT_VALUE";
        
        static {
            System.out.println("  ConstantClass 类初始化");
        }
    }

    /**
     * loadClass演示
     */
    static class LoadClassDemo {
        static {
            System.out.println("  LoadClassDemo 类初始化");
        }
    }

    /**
     * 准备阶段演示
     */
    static class PreparationDemo {
        // 准备阶段：value = 0
        // 初始化阶段：value = 123
        private static int value = 123;
        
        // 准备阶段：str = null
        // 初始化阶段：str = "hello"
        private static String str = "hello";
        
        // 准备阶段：CONSTANT = 456（final常量直接赋值）
        private static final int CONSTANT = 456;
        
        static {
            System.out.println("  准备阶段后，初始化阶段前:");
            System.out.println("  value应该是0，实际是: " + value);
            System.out.println("  str应该是null，实际是: " + str);
            System.out.println("  CONSTANT应该是456，实际是: " + CONSTANT);
            
            // 在静态代码块中修改值
            value = 789;
            str = "world";
        }
        
        public PreparationDemo() {
            System.out.println("  初始化阶段后:");
            System.out.println("  value = " + value);
            System.out.println("  str = " + str);
            System.out.println("  CONSTANT = " + CONSTANT);
        }
    }
}

/**
 * 单例模式中的类加载顺序问题
 */
class SingletonDemo {
    // 准备阶段：instance = null, counter1 = 0, counter2 = 0
    // 初始化阶段：
    // 1. instance = new SingletonDemo() -> counter1++, counter2++ -> counter1=1, counter2=1
    // 2. counter1 = 0 -> counter1=0
    // 3. counter2保持不变 -> counter2=1
    private static SingletonDemo instance = new SingletonDemo();
    public static int counter1;
    public static int counter2 = 0;
    
    private SingletonDemo() {
        counter1++;
        counter2++;
    }
    
    public static SingletonDemo getInstance() {
        return instance;
    }
    
    public static void main(String[] args) {
        SingletonDemo singleton = SingletonDemo.getInstance();
        System.out.println("counter1 = " + singleton.counter1);  // 0
        System.out.println("counter2 = " + singleton.counter2);  // 1
    }
}

/**
 * 接口的初始化
 */
interface MyInterface {
    // 接口中的字段默认是public static final
    int VALUE = getValue();
    
    static int getValue() {
        System.out.println("MyInterface 初始化");
        return 100;
    }
}

class MyClass implements MyInterface {
    static {
        System.out.println("MyClass 初始化");
    }
    
    public static void main(String[] args) {
        // 访问接口的常量不会初始化实现类
        System.out.println("MyInterface.VALUE = " + MyInterface.VALUE);
        
        // 创建实现类的实例才会初始化实现类
        new MyClass();
    }
}

/**
 * 类加载死锁演示
 */
class DeadLoopClass {
    static {
        // 如果不加if，会导致死循环
        if (true) {
            System.out.println(Thread.currentThread() + " init DeadLoopClass");
            // 模拟耗时操作
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class ClassLoadingDeadlockDemo {
    public static void main(String[] args) {
        Runnable task = () -> {
            System.out.println(Thread.currentThread() + " start");
            new DeadLoopClass();
            System.out.println(Thread.currentThread() + " end");
        };
        
        Thread t1 = new Thread(task, "Thread-1");
        Thread t2 = new Thread(task, "Thread-2");
        t1.start();
        t2.start();
        
        // 输出：
        // Thread[Thread-1,5,main] start
        // Thread[Thread-2,5,main] start
        // Thread[Thread-1,5,main] init DeadLoopClass
        // （Thread-1进入睡眠，Thread-2被阻塞等待）
    }
}
