package com.fragment.core.thread.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * 线程协作机制演示
 *
 * <p>演示内容：
 * <ul>
 *   <li>sleep()的使用和特点</li>
 *   <li>join()的使用和特点</li>
 *   <li>yield()的使用和特点</li>
 *   <li>wait/notify的使用和特点</li>
 *   <li>Lost Wakeup（丢失唤醒）问题及解决方案</li>
 *   <li>两个线程交替打印（经典面试题）</li>
 * </ul>
 *
 * @author fragment
 */
public class ThreadCooperationDemo {

    private static final Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 线程协作机制演示 ==========\n");

        // 1. sleep演示
        demonstrateSleep();

        // 2. join演示
        demonstrateJoin();

        // 3. yield演示
        demonstrateYield();

        // 4. wait/notify演示
        demonstrateWaitNotify();

        // 5. Lost Wakeup问题演示
        demonstrateLostWakeup();

        // 6. 两个线程交替打印1到100
        demonstrateAlternatePrint();
    }

    /**
     * 演示sleep()
     */
    private static void demonstrateSleep() throws InterruptedException {
        System.out.println("1. sleep()演示");
        System.out.println("特点: 不释放锁，进入TIMED_WAITING状态\n");

        Thread t = new Thread(() -> {
            synchronized (lock) {
                System.out.println("线程获得锁");
                try {
                    System.out.println("线程开始sleep 2秒（持有锁）");
                    Thread.sleep(2000);
                    System.out.println("线程sleep结束");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("线程释放锁");
            }
        });
        t.start();
        Thread.sleep(100);

        // 尝试获取锁（会被阻塞）
        new Thread(() -> {
            System.out.println("另一个线程尝试获取锁...");
            synchronized (lock) {
                System.out.println("另一个线程获得锁（等待了2秒）");
            }
        }).start();

        t.join();
        Thread.sleep(100);
        System.out.println();
    }

    /**
     * 演示join()
     */
    private static void demonstrateJoin() throws InterruptedException {
        System.out.println("2. join()演示");
        System.out.println("特点: 等待目标线程结束\n");

        List<Thread> threads = new ArrayList<>();

        // 创建3个工作线程
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            Thread t = new Thread(() -> {
                System.out.println("任务 " + taskId + " 开始执行");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("任务 " + taskId + " 执行完成");
            });
            threads.add(t);
            t.start();
        }

        System.out.println("主线程等待所有任务完成...");

        // 等待所有线程完成
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("所有任务完成，主线程继续执行\n");
    }

    /**
     * 演示yield()
     */
    private static void demonstrateYield() throws InterruptedException {
        System.out.println("3. yield()演示");
        System.out.println("特点: 提示调度器让出CPU，但不保证\n");

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("线程1: " + i);
                Thread.yield(); // 让步
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("线程2: " + i);
                Thread.yield(); // 让步
            }
        }, "Thread-2");

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        System.out.println("注意: yield()不保证一定让步，输出顺序可能不规律\n");
    }

    /**
     * 演示wait/notify
     */
    private static void demonstrateWaitNotify() throws InterruptedException {
        System.out.println("4. wait/notify演示");
        System.out.println("特点: wait()释放锁，notify()唤醒等待线程\n");

        final boolean[] ready = {false};

        // 等待线程
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                System.out.println("等待线程: 检查条件");
                while (!ready[0]) {
                    try {
                        System.out.println("等待线程: 条件不满足，调用wait()释放锁");
                        lock.wait();
                        System.out.println("等待线程: 被唤醒，重新检查条件");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("等待线程: 条件满足，继续执行");
            }
        }, "Waiter");

        // 通知线程
        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            synchronized (lock) {
                System.out.println("通知线程: 修改条件");
                ready[0] = true;
                System.out.println("通知线程: 调用notify()唤醒等待线程");
                lock.notify();
                System.out.println("通知线程: notify()调用完成，但锁还未释放");
            }
            System.out.println("通知线程: 退出synchronized块，释放锁");
        }, "Notifier");

        waiter.start();
        Thread.sleep(100); // 确保waiter先执行
        notifier.start();

        waiter.join();
        notifier.join();

        System.out.println("\n关键点:");
        System.out.println("1. wait()必须在synchronized块中调用");
        System.out.println("2. wait()会释放锁");
        System.out.println("3. notify()不会立即释放锁");
        System.out.println("4. 被唤醒的线程需要重新竞争锁");
        System.out.println();
    }

    /**
     * 演示Lost Wakeup（丢失唤醒）问题
     *
     * <p>Lost Wakeup是指：notify()在wait()之前执行，导致唤醒信号丢失，
     * 等待线程永远无法被唤醒的问题。
     *
     * <p>原因：
     * <ul>
     *   <li>线程在检查条件和调用wait()之间没有同步</li>
     *   <li>notify()在wait()之前执行</li>
     *   <li>唤醒信号没有被保存，导致永久等待</li>
     * </ul>
     */
    private static void demonstrateLostWakeup() throws InterruptedException {
        System.out.println("5. Lost Wakeup（丢失唤醒）问题演示");
        System.out.println("特点: notify()在wait()之前执行，导致唤醒信号丢失\n");

        // 错误示例：可能发生Lost Wakeup
        System.out.println("【错误示例】可能发生Lost Wakeup:");
        demonstrateLostWakeupBug();

        System.out.println("\n" + createSeparator(50) + "\n");

        // 正确示例：避免Lost Wakeup
        System.out.println("【正确示例】避免Lost Wakeup:");
        demonstrateLostWakeupFix();

        System.out.println("\n关键点:");
        System.out.println("1. 检查条件和wait()必须在同一个synchronized块中");
        System.out.println("2. notify()必须在修改条件后立即调用");
        System.out.println("3. 使用while循环检查条件，防止虚假唤醒");
        System.out.println("4. 条件变量和锁对象必须配合使用");
        System.out.println();
    }

    /**
     * 错误示例：可能发生Lost Wakeup
     */
    private static void demonstrateLostWakeupBug() throws InterruptedException {
        final Object lock = new Object();
        final boolean[] ready = {false};

        Thread waiter = new Thread(() -> {
            System.out.println("[等待线程] 启动");

            // ❌ 错误：检查条件在synchronized块外
            if (!ready[0]) {
                System.out.println("[等待线程] 条件不满足，准备wait...");

                // 模拟线程切换延迟
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                synchronized (lock) {
                    try {
                        System.out.println("[等待线程] 调用wait()");
                        lock.wait(2000); // 使用超时避免永久阻塞
                        System.out.println("[等待线程] 被唤醒（可能是超时）");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            System.out.println("[等待线程] ready = " + ready[0]);
        }, "Waiter");

        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(50); // 在waiter检查条件后、wait()前执行
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            synchronized (lock) {
                System.out.println("[通知线程] 修改条件 ready = true");
                ready[0] = true;
                System.out.println("[通知线程] 调用notify()");
                lock.notify(); // 此时waiter还没有wait()，唤醒信号丢失！
            }
            System.out.println("[通知线程] 完成");
        }, "Notifier");

        waiter.start();
        notifier.start();

        waiter.join();
        notifier.join();

        System.out.println("\n分析：notify()在wait()之前执行，唤醒信号丢失");
        System.out.println("等待线程最终通过超时退出，而不是被正常唤醒");
    }

    /**
     * 正确示例：避免Lost Wakeup
     */
    private static void demonstrateLostWakeupFix() throws InterruptedException {
        final Object lock = new Object();
        final boolean[] ready = {false};

        Thread waiter = new Thread(() -> {
            System.out.println("[等待线程] 启动");

            // ✅ 正确：检查条件和wait()在同一个synchronized块中
            synchronized (lock) {
                System.out.println("[等待线程] 检查条件");
                while (!ready[0]) {
                    try {
                        System.out.println("[等待线程] 条件不满足，调用wait()");
                        lock.wait();
                        System.out.println("[等待线程] 被唤醒，重新检查条件");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("[等待线程] 条件满足，继续执行");
            }

            System.out.println("[等待线程] ready = " + ready[0]);
        }, "Waiter");

        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            synchronized (lock) {
                System.out.println("[通知线程] 修改条件 ready = true");
                ready[0] = true;
                System.out.println("[通知线程] 调用notify()");
                lock.notify();
            }
            System.out.println("[通知线程] 完成");
        }, "Notifier");

        waiter.start();
        notifier.start();

        waiter.join();
        notifier.join();

        System.out.println("\n分析：检查条件和wait()在同一个synchronized块中");
        System.out.println("即使notify()先执行，条件变量也能正确传递信息");
    }

    /**
     * 创建分隔线（JDK 1.8兼容）
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }

    /**
     * 演示两个线程交替打印1到100（经典面试题）
     *
     * <p>实现思路：
     * <ul>
     *   <li>使用wait/notify机制实现线程间协作</li>
     *   <li>使用标志位控制哪个线程该打印</li>
     *   <li>打印完后唤醒另一个线程，自己进入等待</li>
     * </ul>
     */
    private static void demonstrateAlternatePrint() throws InterruptedException {
        System.out.println("5. 两个线程交替打印1到100（经典面试题）");
        System.out.println("特点: 使用wait/notify实现线程交替执行\n");

        // 方案1: 使用wait/notify
        System.out.println("方案1: 使用wait/notify实现");
        alternatePrintWithWaitNotify();

        // 打印分隔线（JDK 1.8兼容）
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            separator.append("=");
        }
        System.out.println("\n" + separator.toString() + "\n");

        // 方案2: 使用synchronized + 标志位
        System.out.println("方案2: 使用synchronized + 标志位实现");
        alternatePrintWithFlag();

        System.out.println("\n关键点:");
        System.out.println("1. 必须保证线程间的同步");
        System.out.println("2. 使用标志位判断轮到哪个线程");
        System.out.println("3. wait/notify是最常用的实现方式");
        System.out.println("4. 注意避免虚假唤醒（使用while而不是if）");
    }

    /**
     * 方案1: 使用wait/notify实现交替打印
     */
    private static void alternatePrintWithWaitNotify() throws InterruptedException {
        final Object printLock = new Object();
        final int[] currentNumber = {1};
        final int MAX = 100;

        // 奇数线程
        Thread oddThread = new Thread(() -> {
            synchronized (printLock) {
                while (currentNumber[0] <= MAX) {  // 这样写能确保currentNumber[0]最多就是100
                    // 如果当前数字是偶数，等待
                    while (currentNumber[0] % 2 == 0 && currentNumber[0] <= MAX) {
                        try {
                            printLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    // 打印奇数
                    if (currentNumber[0] <= MAX) {
                        System.out.println("奇数线程: " + currentNumber[0]);
                        currentNumber[0]++;
                        printLock.notify(); // 唤醒偶数线程
                    }
                }
            }
        }, "OddThread");

        // 偶数线程
        Thread evenThread = new Thread(() -> {
            synchronized (printLock) {
                while (currentNumber[0] <= MAX) {
                    // 如果当前数字是奇数，等待
                    while (currentNumber[0] % 2 == 1 && currentNumber[0] <= MAX) {
                        try {
                            printLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    // 打印偶数
                    if (currentNumber[0] <= MAX) {
                        System.out.println("偶数线程: " + currentNumber[0]);
                        currentNumber[0]++;
                        printLock.notify(); // 唤醒奇数线程
                    }
                }
            }
        }, "EvenThread");

        oddThread.start();
        evenThread.start();

        oddThread.join();
        evenThread.join();
    }

    /**
     * 方案2: 使用synchronized + 标志位实现交替打印
     */
    private static void alternatePrintWithFlag() throws InterruptedException {
        AlternatePrinter printer = new AlternatePrinter();

        // 线程1打印奇数
        Thread t1 = new Thread(() -> {
            for (int i = 1; i <= 100; i += 2) {
                printer.printOdd(i);
            }
        }, "Thread-1");

        // 线程2打印偶数
        Thread t2 = new Thread(() -> {
            for (int i = 2; i <= 100; i += 2) {
                printer.printEven(i);
            }
        }, "Thread-2");

        t1.start();
        t2.start();

        t1.join();
        t2.join();
    }

    /**
     * 交替打印辅助类
     */
    static class AlternatePrinter {
        private final Object lock = new Object();
        private boolean isOddTurn = true; // true表示轮到奇数，false表示轮到偶数

        /**
         * 打印奇数
         */
        public void printOdd(int number) {
            synchronized (lock) {
                // 如果不是奇数的回合，等待
                while (!isOddTurn) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                System.out.println("线程1: " + number);
                isOddTurn = false; // 切换到偶数回合
                lock.notify(); // 唤醒偶数线程
            }
        }

        /**
         * 打印偶数
         */
        public void printEven(int number) {
            synchronized (lock) {
                // 如果不是偶数的回合，等待
                while (isOddTurn) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                System.out.println("线程2: " + number);
                isOddTurn = true; // 切换到奇数回合
                lock.notify(); // 唤醒奇数线程
            }
        }
    }
}
