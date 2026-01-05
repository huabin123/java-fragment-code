package com.fragment.juc.Synchronized.demo;

import org.openjdk.jol.info.ClassLayout;

/**
 * 锁升级过程演示
 * 
 * 演示内容：
 * 1. 无锁状态
 * 2. 偏向锁
 * 3. 轻量级锁
 * 4. 重量级锁
 * 5. 锁升级过程
 * 
 * 注意：需要添加JOL依赖
 * <dependency>
 *     <groupId>org.openjdk.jol</groupId>
 *     <artifactId>jol-core</artifactId>
 *     <version>0.16</version>
 * </dependency>
 * 
 * JVM参数：
 * -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0
 * 
 * @author huabin
 */
public class LockUpgradeDemo {
    
    /**
     * 示例1：查看对象头（无锁状态）
     */
    static class NoLockDemo {
        public static void test() {
            System.out.println("\n========== 示例1：无锁状态 ==========");
            Object obj = new Object();
            System.out.println("创建对象后（无锁状态）：");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        }
    }
    
    /**
     * 示例2：偏向锁演示
     */
    static class BiasedLockDemo {
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例2：偏向锁 ==========");
            
            // 等待偏向锁启动（JVM启动后有4秒延迟）
            // 或者使用JVM参数：-XX:BiasedLockingStartupDelay=0
            Thread.sleep(5000);
            
            Object obj = new Object();
            System.out.println("创建对象后：");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            
            // 第一个线程获取锁
            synchronized(obj) {
                System.out.println("\n第一个线程获取锁后（偏向锁）：");
                System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            }
            
            System.out.println("\n第一个线程释放锁后（仍是偏向锁）：");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            
            // 同一个线程再次获取锁
            synchronized(obj) {
                System.out.println("\n同一线程再次获取锁（仍是偏向锁）：");
                System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            }
        }
    }
    
    /**
     * 示例3：轻量级锁演示
     */
    static class LightweightLockDemo {
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例3：轻量级锁 ==========");
            
            Thread.sleep(5000); // 等待偏向锁启动
            
            Object obj = new Object();
            
            // 第一个线程获取锁（偏向锁）
            Thread t1 = new Thread(() -> {
                synchronized(obj) {
                    System.out.println("Thread-1获取锁（偏向锁）：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                    
                    try {
                        Thread.sleep(2000); // 持有锁2秒
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "Thread-1");
            
            t1.start();
            t1.join();
            
            // 第二个线程获取锁（升级为轻量级锁）
            Thread t2 = new Thread(() -> {
                synchronized(obj) {
                    System.out.println("\nThread-2获取锁（轻量级锁）：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                }
            }, "Thread-2");
            
            t2.start();
            t2.join();
        }
    }
    
    /**
     * 示例4：重量级锁演示
     */
    static class HeavyweightLockDemo {
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例4：重量级锁 ==========");
            
            Thread.sleep(5000); // 等待偏向锁启动
            
            Object obj = new Object();
            
            // 创建两个线程竞争同一个锁
            Thread t1 = new Thread(() -> {
                synchronized(obj) {
                    System.out.println("Thread-1获取锁：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                    
                    try {
                        Thread.sleep(3000); // 持有锁3秒
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    System.out.println("\nThread-1释放锁前：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                }
            }, "Thread-1");
            
            Thread t2 = new Thread(() -> {
                try {
                    Thread.sleep(100); // 确保t1先获取锁
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                System.out.println("\nThread-2尝试获取锁（竞争发生）：");
                synchronized(obj) {
                    System.out.println("Thread-2获取锁（重量级锁）：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                }
            }, "Thread-2");
            
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        }
    }
    
    /**
     * 示例5：完整的锁升级过程
     */
    static class LockUpgradeProcessDemo {
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例5：完整的锁升级过程 ==========");
            
            Thread.sleep(5000); // 等待偏向锁启动
            
            Object obj = new Object();
            
            System.out.println("1. 初始状态（可偏向）：");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            
            // 阶段1：偏向锁
            Thread t1 = new Thread(() -> {
                synchronized(obj) {
                    System.out.println("\n2. Thread-1获取锁（偏向锁）：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                }
            }, "Thread-1");
            
            t1.start();
            t1.join();
            
            System.out.println("\n3. Thread-1释放锁后（仍是偏向锁）：");
            System.out.println(ClassLayout.parseInstance(obj).toPrintable());
            
            // 阶段2：轻量级锁（不同线程交替访问）
            Thread t2 = new Thread(() -> {
                synchronized(obj) {
                    System.out.println("\n4. Thread-2获取锁（轻量级锁）：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                }
            }, "Thread-2");
            
            t2.start();
            t2.join();
            
            // 阶段3：重量级锁（多线程竞争）
            Thread t3 = new Thread(() -> {
                synchronized(obj) {
                    System.out.println("\n5. Thread-3获取锁：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                    
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "Thread-3");
            
            Thread t4 = new Thread(() -> {
                try {
                    Thread.sleep(100); // 确保t3先获取锁
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                synchronized(obj) {
                    System.out.println("\n6. Thread-4获取锁（重量级锁）：");
                    System.out.println(ClassLayout.parseInstance(obj).toPrintable());
                }
            }, "Thread-4");
            
            t3.start();
            t4.start();
            t3.join();
            t4.join();
        }
    }
    
    /**
     * 示例6：批量重偏向演示
     */
    static class BulkRebiasDemo {
        static class MyObject {
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例6：批量重偏向 ==========");
            
            Thread.sleep(5000); // 等待偏向锁启动
            
            // 创建30个对象
            MyObject[] objects = new MyObject[30];
            for (int i = 0; i < 30; i++) {
                objects[i] = new MyObject();
            }
            
            // Thread-1获取所有对象的锁（偏向Thread-1）
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 30; i++) {
                    synchronized(objects[i]) {
                        // 偏向Thread-1
                    }
                }
                System.out.println("Thread-1完成，所有对象偏向Thread-1");
            }, "Thread-1");
            
            t1.start();
            t1.join();
            
            // Thread-2尝试获取所有对象的锁
            // 当撤销次数达到阈值（默认20），会触发批量重偏向
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 30; i++) {
                    synchronized(objects[i]) {
                        if (i == 19) {
                            System.out.println("\n第20次撤销，触发批量重偏向");
                        }
                        if (i == 25) {
                            System.out.println("对象" + i + "的状态：");
                            System.out.println(ClassLayout.parseInstance(objects[i]).toPrintable());
                        }
                    }
                }
            }, "Thread-2");
            
            t2.start();
            t2.join();
        }
    }
    
    /**
     * 示例7：批量撤销演示
     */
    static class BulkRevokeDemo {
        static class MyObject {
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例7：批量撤销 ==========");
            
            Thread.sleep(5000); // 等待偏向锁启动
            
            // 创建50个对象
            MyObject[] objects = new MyObject[50];
            for (int i = 0; i < 50; i++) {
                objects[i] = new MyObject();
            }
            
            // Thread-1获取所有对象的锁
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    synchronized(objects[i]) {
                        // 偏向Thread-1
                    }
                }
            }, "Thread-1");
            
            t1.start();
            t1.join();
            
            // Thread-2触发批量重偏向
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 30; i++) {
                    synchronized(objects[i]) {
                        // 触发批量重偏向
                    }
                }
            }, "Thread-2");
            
            t2.start();
            t2.join();
            
            // Thread-3再次触发批量撤销
            Thread t3 = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    synchronized(objects[i]) {
                        if (i == 39) {
                            System.out.println("\n第40次撤销，触发批量撤销");
                            System.out.println("该类的所有对象都不再可偏向");
                        }
                    }
                }
            }, "Thread-3");
            
            t3.start();
            t3.join();
            
            // 创建新对象，验证是否还可偏向
            MyObject newObj = new MyObject();
            System.out.println("\n批量撤销后，新创建的对象：");
            System.out.println(ClassLayout.parseInstance(newObj).toPrintable());
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("注意：需要添加JOL依赖才能运行此Demo");
        System.out.println("JVM参数：-XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0");
        System.out.println("如果没有JOL依赖，请注释掉相关代码\n");
        
        // 运行所有示例
        try {
            NoLockDemo.test();
            BiasedLockDemo.test();
            LightweightLockDemo.test();
            HeavyweightLockDemo.test();
            LockUpgradeProcessDemo.test();
            BulkRebiasDemo.test();
            BulkRevokeDemo.test();
        } catch (NoClassDefFoundError e) {
            System.out.println("\n错误：未找到JOL依赖");
            System.out.println("请添加以下依赖到pom.xml：");
            System.out.println("<dependency>");
            System.out.println("    <groupId>org.openjdk.jol</groupId>");
            System.out.println("    <artifactId>jol-core</artifactId>");
            System.out.println("    <version>0.16</version>");
            System.out.println("</dependency>");
        }
    }
}
