package com.fragment.juc.Synchronized.demo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 死锁演示与解决方案
 * 
 * 演示内容：
 * 1. 死锁的产生
 * 2. 死锁检测
 * 3. 按顺序获取锁避免死锁
 * 4. 使用tryLock避免死锁
 * 5. 避免嵌套锁
 * 
 * @author huabin
 */
public class DeadlockDemo {
    
    /**
     * 示例1：经典死锁场景
     */
    static class ClassicDeadlock {
        private static final Object lock1 = new Object();
        private static final Object lock2 = new Object();
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例1：经典死锁场景 ==========");
            
            Thread t1 = new Thread(() -> {
                synchronized(lock1) {
                    System.out.println("Thread-1: 持有lock1，等待lock2");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    synchronized(lock2) {
                        System.out.println("Thread-1: 获得lock2");
                    }
                }
            }, "Thread-1");
            
            Thread t2 = new Thread(() -> {
                synchronized(lock2) {
                    System.out.println("Thread-2: 持有lock2，等待lock1");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    synchronized(lock1) {
                        System.out.println("Thread-2: 获得lock1");
                    }
                }
            }, "Thread-2");
            
            t1.start();
            t2.start();
            
            // 等待3秒，观察死锁
            Thread.sleep(3000);
            
            System.out.println("\n检测到死锁！");
            System.out.println("使用jstack命令可以检测死锁：");
            System.out.println("1. jps 查看进程ID");
            System.out.println("2. jstack <pid> 查看线程堆栈");
            
            // 中断线程
            t1.interrupt();
            t2.interrupt();
        }
    }
    
    /**
     * 示例2：银行转账死锁
     */
    static class BankTransferDeadlock {
        static class Account {
            private int id;
            private int balance;
            
            public Account(int id, int balance) {
                this.id = id;
                this.balance = balance;
            }
            
            public int getId() {
                return id;
            }
            
            public int getBalance() {
                return balance;
            }
            
            public void debit(int amount) {
                balance -= amount;
            }
            
            public void credit(int amount) {
                balance += amount;
            }
        }
        
        // 错误的转账方法（可能死锁）
        public static void transferWrong(Account from, Account to, int amount) {
            synchronized(from) {
                System.out.println(Thread.currentThread().getName() + 
                    ": 锁定账户" + from.getId());
                
                try {
                    Thread.sleep(100); // 模拟处理时间
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                synchronized(to) {
                    System.out.println(Thread.currentThread().getName() + 
                        ": 锁定账户" + to.getId());
                    from.debit(amount);
                    to.credit(amount);
                    System.out.println(Thread.currentThread().getName() + 
                        ": 转账成功 " + amount);
                }
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例2：银行转账死锁 ==========");
            
            Account account1 = new Account(1, 1000);
            Account account2 = new Account(2, 1000);
            
            // 线程1: account1 -> account2
            Thread t1 = new Thread(() -> {
                transferWrong(account1, account2, 100);
            }, "Thread-1");
            
            // 线程2: account2 -> account1
            Thread t2 = new Thread(() -> {
                transferWrong(account2, account1, 200);
            }, "Thread-2");
            
            t1.start();
            t2.start();
            
            // 等待3秒，观察死锁
            Thread.sleep(3000);
            
            System.out.println("\n发生死锁！");
            
            // 中断线程
            t1.interrupt();
            t2.interrupt();
        }
    }
    
    /**
     * 示例3：按顺序获取锁避免死锁
     */
    static class OrderedLockSolution {
        static class Account {
            private int id;
            private int balance;
            
            public Account(int id, int balance) {
                this.id = id;
                this.balance = balance;
            }
            
            public int getId() {
                return id;
            }
            
            public void debit(int amount) {
                balance -= amount;
            }
            
            public void credit(int amount) {
                balance += amount;
            }
        }
        
        // 正确的转账方法（按顺序获取锁）
        public static void transfer(Account from, Account to, int amount) {
            Account first, second;
            
            // 按账户ID顺序获取锁
            if (from.getId() < to.getId()) {
                first = from;
                second = to;
            } else {
                first = to;
                second = from;
            }
            
            synchronized(first) {
                System.out.println(Thread.currentThread().getName() + 
                    ": 锁定账户" + first.getId());
                
                synchronized(second) {
                    System.out.println(Thread.currentThread().getName() + 
                        ": 锁定账户" + second.getId());
                    from.debit(amount);
                    to.credit(amount);
                    System.out.println(Thread.currentThread().getName() + 
                        ": 转账成功 " + amount);
                }
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例3：按顺序获取锁避免死锁 ==========");
            
            Account account1 = new Account(1, 1000);
            Account account2 = new Account(2, 1000);
            
            // 线程1: account1 -> account2
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    transfer(account1, account2, 10);
                }
            }, "Thread-1");
            
            // 线程2: account2 -> account1
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    transfer(account2, account1, 20);
                }
            }, "Thread-2");
            
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            
            System.out.println("\n转账完成，没有发生死锁！");
        }
    }
    
    /**
     * 示例4：使用tryLock避免死锁
     */
    static class TryLockSolution {
        static class Account {
            private int id;
            private int balance;
            private final Lock lock = new ReentrantLock();
            
            public Account(int id, int balance) {
                this.id = id;
                this.balance = balance;
            }
            
            public int getId() {
                return id;
            }
            
            public Lock getLock() {
                return lock;
            }
            
            public void debit(int amount) {
                balance -= amount;
            }
            
            public void credit(int amount) {
                balance += amount;
            }
        }
        
        // 使用tryLock避免死锁
        public static boolean transfer(Account from, Account to, int amount) {
            try {
                // 尝试获取from的锁，超时1秒
                if (from.getLock().tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        System.out.println(Thread.currentThread().getName() + 
                            ": 获得账户" + from.getId() + "的锁");
                        
                        // 尝试获取to的锁，超时1秒
                        if (to.getLock().tryLock(1, TimeUnit.SECONDS)) {
                            try {
                                System.out.println(Thread.currentThread().getName() + 
                                    ": 获得账户" + to.getId() + "的锁");
                                from.debit(amount);
                                to.credit(amount);
                                System.out.println(Thread.currentThread().getName() + 
                                    ": 转账成功 " + amount);
                                return true;
                            } finally {
                                to.getLock().unlock();
                            }
                        } else {
                            System.out.println(Thread.currentThread().getName() + 
                                ": 获取账户" + to.getId() + "的锁超时，放弃转账");
                            return false;
                        }
                    } finally {
                        from.getLock().unlock();
                    }
                } else {
                    System.out.println(Thread.currentThread().getName() + 
                        ": 获取账户" + from.getId() + "的锁超时，放弃转账");
                    return false;
                }
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread().getName() + ": 被中断");
                return false;
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例4：使用tryLock避免死锁 ==========");
            
            Account account1 = new Account(1, 1000);
            Account account2 = new Account(2, 1000);
            
            // 线程1: account1 -> account2
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    if (!transfer(account1, account2, 10)) {
                        System.out.println(Thread.currentThread().getName() + 
                            ": 转账失败，重试");
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "Thread-1");
            
            // 线程2: account2 -> account1
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    if (!transfer(account2, account1, 20)) {
                        System.out.println(Thread.currentThread().getName() + 
                            ": 转账失败，重试");
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "Thread-2");
            
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            
            System.out.println("\n转账完成，使用tryLock避免了死锁！");
        }
    }
    
    /**
     * 示例5：避免嵌套锁
     */
    static class AvoidNestedLock {
        private final Object lock1 = new Object();
        private final Object lock2 = new Object();
        private int count1 = 0;
        private int count2 = 0;
        
        // 错误：嵌套锁
        public void badMethod() {
            synchronized(lock1) {
                count1++;
                synchronized(lock2) {
                    count2++;
                }
            }
        }
        
        // 正确：避免嵌套锁
        public void goodMethod1() {
            synchronized(lock1) {
                count1++;
            }
        }
        
        public void goodMethod2() {
            synchronized(lock2) {
                count2++;
            }
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例5：避免嵌套锁 ==========");
            AvoidNestedLock demo = new AvoidNestedLock();
            
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        demo.goodMethod1();
                        demo.goodMethod2();
                    }
                });
                threads[i].start();
            }
            
            for (Thread thread : threads) {
                thread.join();
            }
            
            System.out.println("count1: " + demo.count1);
            System.out.println("count2: " + demo.count2);
            System.out.println("说明: 避免嵌套锁可以降低死锁风险");
        }
    }
    
    /**
     * 示例6：死锁检测工具演示
     */
    static class DeadlockDetection {
        private static final Object lock1 = new Object();
        private static final Object lock2 = new Object();
        
        public static void createDeadlock() {
            Thread t1 = new Thread(() -> {
                synchronized(lock1) {
                    System.out.println("Thread-1: 持有lock1");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    System.out.println("Thread-1: 等待lock2");
                    synchronized(lock2) {
                        System.out.println("Thread-1: 获得lock2");
                    }
                }
            }, "DeadlockThread-1");
            
            Thread t2 = new Thread(() -> {
                synchronized(lock2) {
                    System.out.println("Thread-2: 持有lock2");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    System.out.println("Thread-2: 等待lock1");
                    synchronized(lock1) {
                        System.out.println("Thread-2: 获得lock1");
                    }
                }
            }, "DeadlockThread-2");
            
            t1.start();
            t2.start();
        }
        
        public static void test() throws InterruptedException {
            System.out.println("\n========== 示例6：死锁检测工具演示 ==========");
            
            createDeadlock();
            
            Thread.sleep(1000);
            
            System.out.println("\n死锁已产生！");
            System.out.println("使用以下命令检测死锁：");
            System.out.println("1. jps - 查看Java进程ID");
            System.out.println("2. jstack <pid> - 查看线程堆栈和死锁信息");
            System.out.println("3. jconsole - 图形化工具，可以检测死锁");
            System.out.println("4. VisualVM - 更强大的图形化工具");
            
            System.out.println("\njstack输出示例：");
            System.out.println("Found one Java-level deadlock:");
            System.out.println("=============================");
            System.out.println("\"DeadlockThread-2\":");
            System.out.println("  waiting to lock monitor <0x...> (a java.lang.Object),");
            System.out.println("  which is held by \"DeadlockThread-1\"");
            System.out.println("\"DeadlockThread-1\":");
            System.out.println("  waiting to lock monitor <0x...> (a java.lang.Object),");
            System.out.println("  which is held by \"DeadlockThread-2\"");
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("死锁演示与解决方案\n");
        
        // 运行所有示例
        ClassicDeadlock.test();
        BankTransferDeadlock.test();
        OrderedLockSolution.test();
        TryLockSolution.test();
        AvoidNestedLock.test();
        DeadlockDetection.test();
        
        System.out.println("\n\n总结：");
        System.out.println("1. 死锁的四个必要条件：互斥、持有并等待、不可剥夺、循环等待");
        System.out.println("2. 避免死锁的方法：");
        System.out.println("   - 按顺序获取锁");
        System.out.println("   - 使用tryLock设置超时");
        System.out.println("   - 避免嵌套锁");
        System.out.println("   - 使用死锁检测工具");
        
        // 退出程序
        System.exit(0);
    }
}
