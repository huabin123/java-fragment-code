package com.fragment.juc.Synchronized.demo;

/**
 * 可见性问题演示
 * 
 * 演示内容：
 * 1. 可见性问题
 * 2. Synchronized保证可见性
 * 3. volatile保证可见性
 * 4. happens-before规则
 * 
 * @author huabin
 */
public class VisibilityDemo {
    
    /**
     * 示例1：可见性问题演示
     */
    static class VisibilityProblem {
        private boolean flag = false;
        private int count = 0;
        
        public void writer() {
            count = 100;
            flag = true; // 可能不会立即被其他线程看到
        }
        
        public void reader() {
            if (flag) {
                // 可能看到flag=true，但count还是0
                System.out.println("count = " + count);
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例1：可见性问题演示 ==========");
            VisibilityProblem demo = new VisibilityProblem();
            
            Thread writer = new Thread(() -> {
                demo.writer();
                System.out.println("Writer: 写入完成");
            }, "Writer");
            
            Thread reader = new Thread(() -> {
                while (!demo.flag) {
                    // 自旋等待flag变为true
                    // 可能永远等待，因为flag的修改可能不可见
                }
                demo.reader();
            }, "Reader");
            
            reader.start();
            Thread.sleep(100);
            writer.start();
            
            Thread.sleep(3000);
            
            if (reader.isAlive()) {
                System.out.println("Reader线程仍在运行，可能发生了可见性问题！");
                reader.interrupt();
            }
        }
    }
    
    /**
     * 示例2：Synchronized保证可见性
     */
    static class SynchronizedVisibility {
        private boolean flag = false;
        private int count = 0;
        private final Object lock = new Object();
        
        public void writer() {
            synchronized(lock) {
                count = 100;
                flag = true;
            }
        }
        
        public void reader() {
            synchronized(lock) {
                if (flag) {
                    System.out.println("count = " + count);
                }
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例2：Synchronized保证可见性 ==========");
            SynchronizedVisibility demo = new SynchronizedVisibility();
            
            Thread writer = new Thread(() -> {
                demo.writer();
                System.out.println("Writer: 写入完成");
            }, "Writer");
            
            Thread reader = new Thread(() -> {
                while (true) {
                    synchronized(demo.lock) {
                        if (demo.flag) {
                            break;
                        }
                    }
                }
                demo.reader();
            }, "Reader");
            
            reader.start();
            Thread.sleep(100);
            writer.start();
            
            reader.join(3000);
            
            if (!reader.isAlive()) {
                System.out.println("Reader线程正常结束，Synchronized保证了可见性！");
            }
        }
    }
    
    /**
     * 示例3：volatile保证可见性
     */
    static class VolatileVisibility {
        private volatile boolean flag = false; // 使用volatile
        private int count = 0;
        
        public void writer() {
            count = 100;
            flag = true; // volatile写，立即刷新到主内存
        }
        
        public void reader() {
            if (flag) { // volatile读，从主内存读取最新值
                System.out.println("count = " + count);
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例3：volatile保证可见性 ==========");
            VolatileVisibility demo = new VolatileVisibility();
            
            Thread writer = new Thread(() -> {
                demo.writer();
                System.out.println("Writer: 写入完成");
            }, "Writer");
            
            Thread reader = new Thread(() -> {
                while (!demo.flag) {
                    // 自旋等待
                }
                demo.reader();
            }, "Reader");
            
            reader.start();
            Thread.sleep(100);
            writer.start();
            
            reader.join(3000);
            
            if (!reader.isAlive()) {
                System.out.println("Reader线程正常结束，volatile保证了可见性！");
            }
        }
    }
    
    /**
     * 示例4：happens-before规则演示
     */
    static class HappensBeforeDemo {
        private int a = 0;
        private int b = 0;
        private final Object lock = new Object();
        
        // 线程1
        public void thread1() {
            synchronized(lock) {
                a = 1; // 操作1
                b = 2; // 操作2
            } // 操作3：释放锁
        }
        
        // 线程2
        public void thread2() {
            synchronized(lock) { // 操作4：获取锁
                System.out.println("a = " + a); // 操作5
                System.out.println("b = " + b); // 操作6
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例4：happens-before规则演示 ==========");
            HappensBeforeDemo demo = new HappensBeforeDemo();
            
            Thread t1 = new Thread(() -> {
                demo.thread1();
            }, "Thread-1");
            
            Thread t2 = new Thread(() -> {
                demo.thread2();
            }, "Thread-2");
            
            t1.start();
            t1.join();
            t2.start();
            t2.join();
            
            System.out.println("说明：");
            System.out.println("1. 操作1 happens-before 操作2（程序顺序规则）");
            System.out.println("2. 操作2 happens-before 操作3（程序顺序规则）");
            System.out.println("3. 操作3 happens-before 操作4（监视器锁规则）");
            System.out.println("4. 操作4 happens-before 操作5（程序顺序规则）");
            System.out.println("5. 因此，操作1 happens-before 操作5（传递性）");
            System.out.println("6. 所以，线程2一定能看到线程1的修改");
        }
    }
    
    /**
     * 示例5：内存屏障演示
     */
    static class MemoryBarrierDemo {
        private int x = 0;
        private int y = 0;
        private final Object lock = new Object();
        
        public void withoutBarrier() {
            x = 1;
            y = 2;
            // 可能发生重排序：y=2可能在x=1之前执行
        }
        
        public void withBarrier() {
            synchronized(lock) {
                x = 1;
                y = 2;
                // synchronized提供内存屏障，禁止重排序
            }
        }
        
        public static void test() {
            System.out.println("\n========== 示例5：内存屏障演示 ==========");
            System.out.println("Synchronized提供的内存屏障：");
            System.out.println("1. 进入synchronized：LoadLoad + LoadStore屏障");
            System.out.println("2. 退出synchronized：StoreStore + StoreLoad屏障");
            System.out.println("3. 保证synchronized块内的操作不会被重排序到块外");
            System.out.println("4. 保证synchronized块内的修改对其他线程可见");
        }
    }
    
    /**
     * 示例6：双重检查锁定的可见性问题
     */
    static class DoubleCheckedLocking {
        // 错误的单例实现（没有volatile）
        static class WrongSingleton {
            private static WrongSingleton instance;
            
            public static WrongSingleton getInstance() {
                if (instance == null) { // 第一次检查
                    synchronized(WrongSingleton.class) {
                        if (instance == null) { // 第二次检查
                            instance = new WrongSingleton();
                            // 可能发生指令重排序：
                            // 1. 分配内存
                            // 2. 将引用指向内存（此时对象还未初始化）
                            // 3. 初始化对象
                            // 其他线程可能看到未初始化的对象
                        }
                    }
                }
                return instance;
            }
        }
        
        // 正确的单例实现（使用volatile）
        static class CorrectSingleton {
            private static volatile CorrectSingleton instance; // volatile
            
            public static CorrectSingleton getInstance() {
                if (instance == null) {
                    synchronized(CorrectSingleton.class) {
                        if (instance == null) {
                            instance = new CorrectSingleton();
                            // volatile禁止指令重排序
                        }
                    }
                }
                return instance;
            }
        }
        
        public static void test() {
            System.out.println("\n========== 示例6：双重检查锁定的可见性问题 ==========");
            System.out.println("错误的实现：");
            System.out.println("private static Singleton instance;");
            System.out.println("问题：可能发生指令重排序，导致其他线程看到未初始化的对象");
            System.out.println();
            System.out.println("正确的实现：");
            System.out.println("private static volatile Singleton instance;");
            System.out.println("解决：volatile禁止指令重排序，保证可见性");
        }
    }
    
    /**
     * 示例7：Synchronized vs volatile
     */
    static class SynchronizedVsVolatile {
        // 使用synchronized
        private int count1 = 0;
        
        public synchronized void incrementSync() {
            count1++;
        }
        
        public synchronized int getCountSync() {
            return count1;
        }
        
        // 使用volatile（错误）
        private volatile int count2 = 0;
        
        public void incrementVolatile() {
            count2++; // 不是原子操作！
        }
        
        public int getCountVolatile() {
            return count2;
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例7：Synchronized vs volatile ==========");
            SynchronizedVsVolatile demo = new SynchronizedVsVolatile();
            
            // 测试synchronized
            Thread[] threads1 = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads1[i] = new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        demo.incrementSync();
                    }
                });
                threads1[i].start();
            }
            for (Thread thread : threads1) {
                thread.join();
            }
            
            // 测试volatile
            Thread[] threads2 = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads2[i] = new Thread(() -> {
                    for (int j = 0; j < 1000; j++) {
                        demo.incrementVolatile();
                    }
                });
                threads2[i].start();
            }
            for (Thread thread : threads2) {
                thread.join();
            }
            
            System.out.println("Synchronized结果: " + demo.getCountSync() + " (正确)");
            System.out.println("Volatile结果: " + demo.getCountVolatile() + " (可能错误)");
            System.out.println();
            System.out.println("总结：");
            System.out.println("1. Synchronized：保证原子性 + 可见性 + 有序性");
            System.out.println("2. volatile：保证可见性 + 有序性，不保证原子性");
            System.out.println("3. count++不是原子操作，需要使用synchronized");
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("可见性问题演示\n");
        
        // 运行所有示例
        VisibilityProblem.test();
        SynchronizedVisibility.test();
        VolatileVisibility.test();
        HappensBeforeDemo.test();
        MemoryBarrierDemo.test();
        DoubleCheckedLocking.test();
        SynchronizedVsVolatile.test();
        
        System.out.println("\n\n总结：");
        System.out.println("1. 可见性问题：一个线程的修改可能不会立即被其他线程看到");
        System.out.println("2. Synchronized保证可见性：通过内存屏障和happens-before规则");
        System.out.println("3. volatile保证可见性：强制从主内存读写");
        System.out.println("4. Synchronized vs volatile：");
        System.out.println("   - Synchronized：原子性 + 可见性 + 有序性");
        System.out.println("   - volatile：可见性 + 有序性");
    }
}
