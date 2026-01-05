package com.fragment.juc.jmm.demo;

/**
 * 有序性问题演示
 * 
 * 演示内容：
 * 1. 指令重排序导致的问题
 * 2. 使用volatile禁止重排序
 * 
 * @author huabin
 */
public class OrderingDemo {

    /**
     * 场景1：没有volatile - 可能出现指令重排序
     */
    static class NoVolatileReordering {
        private int a = 0;
        private boolean flag = false;

        public void writer() {
            a = 1;          // 操作1
            flag = true;    // 操作2
            // 可能重排序为: flag = true; a = 1;
        }

        public void reader() {
            if (flag) {     // 操作3
                int i = a;  // 操作4
                // 如果发生重排序，可能读到a=0
                if (i != 1) {
                    System.out.println("⚠️  检测到重排序！a=" + i + " (期望是1)");
                }
            }
        }
    }

    /**
     * 场景2：使用volatile - 禁止重排序
     */
    static class VolatileReordering {
        private int a = 0;
        private volatile boolean flag = false;

        public void writer() {
            a = 1;          // 操作1
            flag = true;    // 操作2 (volatile写)
            // volatile写之前的操作不会被重排序到volatile写之后
        }

        public void reader() {
            if (flag) {     // 操作3 (volatile读)
                int i = a;  // 操作4
                // volatile读之后的操作不会被重排序到volatile读之前
                // 由于happens-before，这里一定能读到a=1
                if (i != 1) {
                    System.out.println("⚠️  意外：a=" + i + " (期望是1)");
                } else {
                    System.out.println("✅ 正确：a=" + i);
                }
            }
        }
    }

    /**
     * 经典案例：双重检查锁的重排序问题
     */
    static class Singleton {
        // 没有volatile - 可能出现问题
        private static Singleton instanceNoVolatile;
        
        // 使用volatile - 正确
        private static volatile Singleton instanceWithVolatile;

        private Singleton() {
            // 模拟复杂的初始化
        }

        /**
         * 错误的双重检查锁（没有volatile）
         */
        public static Singleton getInstanceNoVolatile() {
            if (instanceNoVolatile == null) {
                synchronized (Singleton.class) {
                    if (instanceNoVolatile == null) {
                        // 问题：这行代码可能被重排序
                        // 1. 分配内存
                        // 2. 初始化对象
                        // 3. 将引用指向内存
                        // 可能重排序为: 1 -> 3 -> 2
                        // 导致其他线程看到未初始化的对象
                        instanceNoVolatile = new Singleton();
                    }
                }
            }
            return instanceNoVolatile;
        }

        /**
         * 正确的双重检查锁（使用volatile）
         */
        public static Singleton getInstanceWithVolatile() {
            if (instanceWithVolatile == null) {
                synchronized (Singleton.class) {
                    if (instanceWithVolatile == null) {
                        // volatile禁止重排序
                        // 保证对象完全初始化后才对其他线程可见
                        instanceWithVolatile = new Singleton();
                    }
                }
            }
            return instanceWithVolatile;
        }
    }

    /**
     * 演示1：尝试检测重排序（不保证能检测到）
     */
    public static void demoReordering() {
        System.out.println("\n========== 演示1：尝试检测指令重排序 ==========");
        System.out.println("注意：重排序是JVM优化，不一定每次都发生\n");

        NoVolatileReordering example = new NoVolatileReordering();
        int iterations = 1000000;
        int detectedCount = 0;

        for (int i = 0; i < iterations; i++) {
            NoVolatileReordering test = new NoVolatileReordering();
            
            Thread writerThread = new Thread(test::writer);
            Thread readerThread = new Thread(test::reader);

            writerThread.start();
            readerThread.start();

            try {
                writerThread.join();
                readerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("执行了 " + iterations + " 次测试");
        System.out.println("说明：由于现代JVM的优化，可能很难观察到重排序");
        System.out.println("但这不代表重排序不存在，只是概率很低");
    }

    /**
     * 演示2：volatile禁止重排序
     */
    public static void demoVolatileOrdering() {
        System.out.println("\n========== 演示2：volatile禁止重排序 ==========\n");

        VolatileReordering example = new VolatileReordering();
        int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            VolatileReordering test = new VolatileReordering();
            
            Thread writerThread = new Thread(test::writer);
            Thread readerThread = new Thread(test::reader);

            writerThread.start();
            readerThread.start();

            try {
                writerThread.join();
                readerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n执行了 " + iterations + " 次测试，volatile保证了正确性");
    }

    /**
     * 演示3：双重检查锁的重排序问题
     */
    public static void explainDCLProblem() {
        System.out.println("\n========== 演示3：双重检查锁的重排序问题 ==========");
        System.out.println("\n没有volatile的双重检查锁:");
        System.out.println("  private static Singleton instance;");
        System.out.println("  ");
        System.out.println("  public static Singleton getInstance() {");
        System.out.println("      if (instance == null) {           // 检查1");
        System.out.println("          synchronized (Singleton.class) {");
        System.out.println("              if (instance == null) {   // 检查2");
        System.out.println("                  instance = new Singleton();  // 问题所在");
        System.out.println("              }");
        System.out.println("          }");
        System.out.println("      }");
        System.out.println("      return instance;");
        System.out.println("  }");
        
        System.out.println("\n问题分析:");
        System.out.println("  new Singleton() 分为3步:");
        System.out.println("    1. 分配内存空间");
        System.out.println("    2. 初始化对象");
        System.out.println("    3. 将instance指向内存地址");
        System.out.println("\n  可能的重排序: 1 -> 3 -> 2");
        System.out.println("\n  线程A执行到步骤3时，instance已经不为null");
        System.out.println("  线程B此时执行检查1，发现instance != null");
        System.out.println("  线程B直接返回instance，但对象还没初始化完成！");
        System.out.println("  线程B使用未初始化的对象 -> 出错");
        
        System.out.println("\n解决方案:");
        System.out.println("  private static volatile Singleton instance;");
        System.out.println("\n  volatile的作用:");
        System.out.println("    1. 禁止 new Singleton() 的指令重排序");
        System.out.println("    2. 保证对象完全初始化后才对其他线程可见");
        System.out.println("    3. 保证happens-before关系");
        System.out.println("===========================");
    }

    /**
     * 演示4：volatile的内存屏障
     */
    public static void explainMemoryBarrier() {
        System.out.println("\n========== 演示4：volatile的内存屏障 ==========");
        System.out.println("\nvolatile写操作的内存屏障:");
        System.out.println("  普通写操作");
        System.out.println("  普通写操作");
        System.out.println("  ↓");
        System.out.println("  StoreStore屏障  // 禁止上面的写与下面的写重排序");
        System.out.println("  ↓");
        System.out.println("  volatile写操作");
        System.out.println("  ↓");
        System.out.println("  StoreLoad屏障   // 禁止上面的写与下面的读/写重排序");
        
        System.out.println("\nvolatile读操作的内存屏障:");
        System.out.println("  LoadLoad屏障    // 禁止下面的读与上面的读重排序");
        System.out.println("  ↓");
        System.out.println("  volatile读操作");
        System.out.println("  ↓");
        System.out.println("  LoadStore屏障   // 禁止下面的写与上面的读重排序");
        System.out.println("  ↓");
        System.out.println("  普通读操作");
        System.out.println("  普通写操作");
        
        System.out.println("\n内存屏障的作用:");
        System.out.println("  1. 禁止特定类型的指令重排序");
        System.out.println("  2. 保证内存可见性");
        System.out.println("  3. 实现happens-before规则");
        System.out.println("===========================");
    }

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Java内存模型 - 有序性问题演示                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：尝试检测重排序
        // demoReordering();

        // 演示2：volatile禁止重排序
        demoVolatileOrdering();

        // 演示3：双重检查锁的重排序问题
        explainDCLProblem();

        // 演示4：volatile的内存屏障
        explainMemoryBarrier();

        System.out.println("\n" + "===========================");
        System.out.println("总结：");
        System.out.println("1. 指令重排序是JVM和CPU的优化手段");
        System.out.println("2. 重排序不会改变单线程的执行结果");
        System.out.println("3. 多线程下重排序可能导致问题");
        System.out.println("4. volatile通过内存屏障禁止特定的重排序");
        System.out.println("5. 双重检查锁必须使用volatile");
        System.out.println("===========================");
    }
}
