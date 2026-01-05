package com.fragment.juc.jmm.demo;

/**
 * 可见性问题演示
 * 
 * 演示内容：
 * 1. 没有volatile时的可见性问题
 * 2. 使用volatile后的可见性保证
 * 3. 使用synchronized后的可见性保证
 * 
 * @author huabin
 */
public class VisibilityDemo {

    /**
     * 场景1：没有volatile - 可能出现可见性问题
     */
    static class NoVolatileExample {
        private boolean flag = false;
        private int count = 0;

        public void writer() {
            count = 42;
            flag = true;
            System.out.println("[Writer] 已设置 count=" + count + ", flag=" + flag);
        }

        public void reader() {
            System.out.println("[Reader] 开始等待 flag 变为 true...");
            while (!flag) {
                // 忙等待
                // 可能永远看不到flag的变化，因为没有可见性保证
            }
            System.out.println("[Reader] 检测到 flag=true, count=" + count);
        }
    }

    /**
     * 场景2：使用volatile - 保证可见性
     */
    static class VolatileExample {
        private volatile boolean flag = false;
        private int count = 0;

        public void writer() {
            count = 42;
            flag = true; // volatile写，保证之前的写操作对其他线程可见
            System.out.println("[Writer] 已设置 count=" + count + ", flag=" + flag);
        }

        public void reader() {
            System.out.println("[Reader] 开始等待 flag 变为 true...");
            while (!flag) {
                // volatile读，能及时看到flag的变化
            }
            // 由于happens-before规则，这里一定能看到count=42
            System.out.println("[Reader] 检测到 flag=true, count=" + count);
        }
    }

    /**
     * 场景3：使用synchronized - 保证可见性
     */
    static class SynchronizedExample {
        private boolean flag = false;
        private int count = 0;
        private final Object lock = new Object();

        public void writer() {
            synchronized (lock) {
                count = 42;
                flag = true;
                System.out.println("[Writer] 已设置 count=" + count + ", flag=" + flag);
            }
        }

        public void reader() {
            System.out.println("[Reader] 开始等待 flag 变为 true...");
            while (true) {
                synchronized (lock) {
                    if (flag) {
                        System.out.println("[Reader] 检测到 flag=true, count=" + count);
                        break;
                    }
                }
                // 短暂休眠，避免过度占用CPU
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 演示1：没有volatile的可见性问题
     */
    public static void demoNoVolatile() throws InterruptedException {
        System.out.println("\n========== 演示1：没有volatile的可见性问题 ==========");
        System.out.println("注意：这个例子可能会永远运行下去，因为reader线程可能永远看不到flag的变化");
        System.out.println("如果5秒后还没结束，说明出现了可见性问题\n");

        NoVolatileExample example = new NoVolatileExample();

        Thread readerThread = new Thread(() -> example.reader(), "Reader-Thread");
        Thread writerThread = new Thread(() -> example.writer(), "Writer-Thread");

        readerThread.start();
        Thread.sleep(100); // 确保reader先启动
        writerThread.start();

        // 等待最多5秒
        writerThread.join();
        readerThread.join(5000);

        if (readerThread.isAlive()) {
            System.out.println("\n⚠️  Reader线程仍在运行 - 出现了可见性问题！");
            System.out.println("强制中断reader线程...");
            readerThread.interrupt();
            readerThread.join(1000);
        } else {
            System.out.println("\n✅ Reader线程正常结束");
        }
    }

    /**
     * 演示2：使用volatile保证可见性
     */
    public static void demoWithVolatile() throws InterruptedException {
        System.out.println("\n========== 演示2：使用volatile保证可见性 ==========\n");

        VolatileExample example = new VolatileExample();

        Thread readerThread = new Thread(() -> example.reader(), "Reader-Thread");
        Thread writerThread = new Thread(() -> example.writer(), "Writer-Thread");

        readerThread.start();
        Thread.sleep(100); // 确保reader先启动
        writerThread.start();

        writerThread.join();
        readerThread.join(5000);

        if (readerThread.isAlive()) {
            System.out.println("\n⚠️  意外：Reader线程仍在运行");
            readerThread.interrupt();
        } else {
            System.out.println("\n✅ Reader线程正常结束 - volatile保证了可见性");
        }
    }

    /**
     * 演示3：使用synchronized保证可见性
     */
    public static void demoWithSynchronized() throws InterruptedException {
        System.out.println("\n========== 演示3：使用synchronized保证可见性 ==========\n");

        SynchronizedExample example = new SynchronizedExample();

        Thread readerThread = new Thread(() -> example.reader(), "Reader-Thread");
        Thread writerThread = new Thread(() -> example.writer(), "Writer-Thread");

        readerThread.start();
        Thread.sleep(100); // 确保reader先启动
        writerThread.start();

        writerThread.join();
        readerThread.join(5000);

        if (readerThread.isAlive()) {
            System.out.println("\n⚠️  意外：Reader线程仍在运行");
            readerThread.interrupt();
        } else {
            System.out.println("\n✅ Reader线程正常结束 - synchronized保证了可见性");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Java内存模型 - 可见性问题演示                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：可能出现可见性问题（可能需要手动中断）
        // demoNoVolatile();

        // 演示2：volatile保证可见性
        demoWithVolatile();

        // 演示3：synchronized保证可见性
        demoWithSynchronized();

        System.out.println("\n" + "===========================");
        System.out.println("总结：");
        System.out.println("1. 没有同步机制时，可能出现可见性问题");
        System.out.println("2. volatile保证可见性，适用于简单的状态标志");
        System.out.println("3. synchronized保证可见性，适用于需要原子性的场景");
        System.out.println("===========================");
    }
}
