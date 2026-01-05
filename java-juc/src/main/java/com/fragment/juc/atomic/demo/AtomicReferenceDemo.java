package com.fragment.juc.atomic.demo;

import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * 引用类型原子类演示
 * 
 * 演示内容：
 * 1. AtomicReference的使用
 * 2. AtomicStampedReference解决ABA问题
 * 3. AtomicMarkableReference的使用
 * 4. 实际应用场景
 * 
 * @author huabin
 */
public class AtomicReferenceDemo {

    /**
     * 用户类
     */
    static class User {
        private final String name;
        private final int age;

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        @Override
        public String toString() {
            return "User{name='" + name + "', age=" + age + "}";
        }
    }

    /**
     * 演示1：AtomicReference基本使用
     */
    public static void demoAtomicReference() {
        System.out.println("\n========== 演示1：AtomicReference基本使用 ==========\n");

        User user1 = new User("张三", 25);
        User user2 = new User("李四", 30);
        User user3 = new User("王五", 35);

        AtomicReference<User> userRef = new AtomicReference<>(user1);
        System.out.println("初始用户: " + userRef.get());

        // CAS更新：期望是user1，更新为user2
        boolean success1 = userRef.compareAndSet(user1, user2);
        System.out.println("\nCAS(user1 -> user2): " + success1);
        System.out.println("当前用户: " + userRef.get());

        // CAS更新：期望是user1，更新为user3（会失败）
        boolean success2 = userRef.compareAndSet(user1, user3);
        System.out.println("\nCAS(user1 -> user3): " + success2 + " (失败，因为当前不是user1)");
        System.out.println("当前用户: " + userRef.get());

        // getAndSet：设置新值并返回旧值
        User oldUser = userRef.getAndSet(user3);
        System.out.println("\ngetAndSet(user3):");
        System.out.println("  返回的旧用户: " + oldUser);
        System.out.println("  当前用户: " + userRef.get());

        System.out.println("\n✅ AtomicReference可以原子地更新对象引用");
    }

    /**
     * 演示2：AtomicStampedReference解决ABA问题
     */
    public static void demoAtomicStampedReference() throws InterruptedException {
        System.out.println("\n========== 演示2：AtomicStampedReference解决ABA问题 ==========\n");

        User user1 = new User("张三", 25);
        User user2 = new User("李四", 30);

        // 初始：user1，版本号0
        AtomicStampedReference<User> stampedRef = 
            new AtomicStampedReference<>(user1, 0);

        System.out.println("初始状态:");
        System.out.println("  用户: " + stampedRef.getReference());
        System.out.println("  版本号: " + stampedRef.getStamp());

        // 线程1：读取当前值和版本号，延迟更新
        Thread thread1 = new Thread(() -> {
            User expectedUser = stampedRef.getReference();
            int expectedStamp = stampedRef.getStamp();
            
            System.out.println("\n[线程1] 读取:");
            System.out.println("  用户: " + expectedUser);
            System.out.println("  版本号: " + expectedStamp);
            System.out.println("[线程1] 准备更新，但先休眠1秒...");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            boolean success = stampedRef.compareAndSet(
                expectedUser, user2, expectedStamp, expectedStamp + 1);
            
            System.out.println("\n[线程1] CAS更新: " + (success ? "成功" : "失败"));
            if (!success) {
                System.out.println("  失败原因: 版本号已改变");
            }
            System.out.println("  当前用户: " + stampedRef.getReference());
            System.out.println("  当前版本号: " + stampedRef.getStamp());
        }, "Thread-1");

        // 线程2：修改值并改回，但版本号会递增
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(100); // 确保线程1先读取
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            User currentUser = stampedRef.getReference();
            int currentStamp = stampedRef.getStamp();

            System.out.println("\n[线程2] 第一次修改:");
            System.out.println("  " + currentUser + " -> " + user2);
            stampedRef.compareAndSet(currentUser, user2, currentStamp, currentStamp + 1);
            System.out.println("  当前版本号: " + stampedRef.getStamp());

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            currentUser = stampedRef.getReference();
            currentStamp = stampedRef.getStamp();

            System.out.println("\n[线程2] 第二次修改（改回原值）:");
            System.out.println("  " + currentUser + " -> " + user1);
            stampedRef.compareAndSet(currentUser, user1, currentStamp, currentStamp + 1);
            System.out.println("  当前版本号: " + stampedRef.getStamp());
        }, "Thread-2");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        System.out.println("\n最终状态:");
        System.out.println("  用户: " + stampedRef.getReference());
        System.out.println("  版本号: " + stampedRef.getStamp());

        System.out.println("\n✅ 通过版本号机制成功检测到了ABA问题");
    }

    /**
     * 演示3：AtomicMarkableReference的使用
     */
    public static void demoAtomicMarkableReference() {
        System.out.println("\n========== 演示3：AtomicMarkableReference使用 ==========\n");

        User user1 = new User("张三", 25);
        User user2 = new User("李四", 30);

        // 初始：user1，标记为false（未删除）
        AtomicMarkableReference<User> markableRef = 
            new AtomicMarkableReference<>(user1, false);

        System.out.println("初始状态:");
        System.out.println("  用户: " + markableRef.getReference());
        System.out.println("  标记: " + markableRef.isMarked() + " (false表示未删除)");

        // 标记为已删除
        boolean[] markHolder = new boolean[1];
        User currentUser = markableRef.get(markHolder);
        System.out.println("\n标记为已删除:");
        boolean success1 = markableRef.compareAndSet(
            currentUser, currentUser, markHolder[0], true);
        System.out.println("  操作: " + (success1 ? "成功" : "失败"));
        System.out.println("  用户: " + markableRef.getReference());
        System.out.println("  标记: " + markableRef.isMarked() + " (true表示已删除)");

        // 尝试更新已删除的对象（应该失败）
        System.out.println("\n尝试更新已删除的对象:");
        boolean success2 = markableRef.compareAndSet(
            currentUser, user2, false, false);
        System.out.println("  操作: " + (success2 ? "成功" : "失败") + " (失败，因为标记不匹配)");

        // 正确的更新方式
        currentUser = markableRef.get(markHolder);
        boolean currentMark = markHolder[0];
        System.out.println("\n使用正确的标记更新:");
        boolean success3 = markableRef.compareAndSet(
            currentUser, user2, currentMark, false);
        System.out.println("  操作: " + (success3 ? "成功" : "失败"));
        System.out.println("  用户: " + markableRef.getReference());
        System.out.println("  标记: " + markableRef.isMarked());

        System.out.println("\n✅ AtomicMarkableReference适用于标记对象状态");
        System.out.println("   （如：是否删除、是否过期等）");
    }

    /**
     * 演示4：实现无锁的栈（简化版）
     */
    public static void demoLockFreeStack() throws InterruptedException {
        System.out.println("\n========== 演示4：无锁栈实现 ==========\n");

        class Node<T> {
            final T value;
            Node<T> next;

            Node(T value) {
                this.value = value;
            }

            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        class LockFreeStack<T> {
            private final AtomicReference<Node<T>> top = new AtomicReference<>();

            public void push(T value) {
                Node<T> newNode = new Node<>(value);
                Node<T> oldTop;
                do {
                    oldTop = top.get();
                    newNode.next = oldTop;
                } while (!top.compareAndSet(oldTop, newNode));
                
                System.out.println("  [" + Thread.currentThread().getName() + "] push: " + value);
            }

            public T pop() {
                Node<T> oldTop;
                Node<T> newTop;
                do {
                    oldTop = top.get();
                    if (oldTop == null) {
                        return null;
                    }
                    newTop = oldTop.next;
                } while (!top.compareAndSet(oldTop, newTop));
                
                System.out.println("  [" + Thread.currentThread().getName() + "] pop: " + oldTop.value);
                return oldTop.value;
            }

            public boolean isEmpty() {
                return top.get() == null;
            }
        }

        LockFreeStack<Integer> stack = new LockFreeStack<>();

        System.out.println("多线程并发push:");
        Thread[] pushThreads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int threadId = i;
            pushThreads[i] = new Thread(() -> {
                for (int j = 0; j < 3; j++) {
                    stack.push(threadId * 10 + j);
                }
            }, "Push-" + i);
            pushThreads[i].start();
        }

        for (Thread thread : pushThreads) {
            thread.join();
        }

        System.out.println("\n多线程并发pop:");
        Thread[] popThreads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            popThreads[i] = new Thread(() -> {
                for (int j = 0; j < 3; j++) {
                    stack.pop();
                }
            }, "Pop-" + i);
            popThreads[i].start();
        }

        for (Thread thread : popThreads) {
            thread.join();
        }

        System.out.println("\n栈是否为空: " + stack.isEmpty());
        System.out.println("✅ 无锁栈通过CAS保证了线程安全");
    }

    /**
     * 演示5：实现配置热更新
     */
    public static void demoConfigHotUpdate() throws InterruptedException {
        System.out.println("\n========== 演示5：配置热更新 ==========\n");

        class Configuration {
            private final String host;
            private final int port;
            private final int timeout;

            public Configuration(String host, int port, int timeout) {
                this.host = host;
                this.port = port;
                this.timeout = timeout;
            }

            @Override
            public String toString() {
                return "Configuration{host='" + host + "', port=" + port + 
                       ", timeout=" + timeout + "}";
            }
        }

        class ConfigManager {
            private final AtomicReference<Configuration> configRef;

            public ConfigManager(Configuration initialConfig) {
                this.configRef = new AtomicReference<>(initialConfig);
            }

            public Configuration getConfig() {
                return configRef.get();
            }

            public void updateConfig(Configuration newConfig) {
                Configuration oldConfig = configRef.getAndSet(newConfig);
                System.out.println("  配置已更新:");
                System.out.println("    旧配置: " + oldConfig);
                System.out.println("    新配置: " + newConfig);
            }

            public boolean updateConfigIfMatch(Configuration expected, 
                                               Configuration newConfig) {
                boolean success = configRef.compareAndSet(expected, newConfig);
                if (success) {
                    System.out.println("  条件更新成功: " + newConfig);
                } else {
                    System.out.println("  条件更新失败: 配置已被其他线程修改");
                }
                return success;
            }
        }

        Configuration initialConfig = new Configuration("localhost", 8080, 3000);
        ConfigManager configManager = new ConfigManager(initialConfig);

        System.out.println("初始配置: " + configManager.getConfig());

        // 模拟配置热更新
        Thread updater1 = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Configuration newConfig = new Configuration("192.168.1.100", 9090, 5000);
            configManager.updateConfig(newConfig);
        }, "Updater-1");

        // 模拟业务线程读取配置
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                Configuration config = configManager.getConfig();
                System.out.println("  [Reader] 读取配置: " + config);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Reader");

        reader.start();
        updater1.start();

        reader.join();
        updater1.join();

        System.out.println("\n✅ AtomicReference实现了配置的无锁热更新");
    }

    /**
     * 总结引用类型原子类的使用
     */
    public static void summarizeUseCases() {
        System.out.println("\n========== 引用类型原子类使用总结 ==========");
        
        System.out.println("\n✅ AtomicReference:");
        System.out.println("   适用场景:");
        System.out.println("     - 对象引用的原子更新");
        System.out.println("     - 配置热更新");
        System.out.println("     - 无锁数据结构（栈、队列等）");
        System.out.println("   注意事项:");
        System.out.println("     - 只保证引用的原子性，不保证对象内部的线程安全");
        System.out.println("     - 可能出现ABA问题");
        
        System.out.println("\n✅ AtomicStampedReference:");
        System.out.println("   适用场景:");
        System.out.println("     - 需要解决ABA问题的场景");
        System.out.println("     - 需要版本控制的场景");
        System.out.println("     - 乐观锁实现");
        System.out.println("   注意事项:");
        System.out.println("     - 版本号需要合理管理");
        System.out.println("     - 性能略低于AtomicReference");
        
        System.out.println("\n✅ AtomicMarkableReference:");
        System.out.println("   适用场景:");
        System.out.println("     - 需要标记对象状态（如删除标记）");
        System.out.println("     - 简化版的版本控制（只有两个状态）");
        System.out.println("   注意事项:");
        System.out.println("     - 只有true/false两个标记");
        System.out.println("     - 不能解决多次ABA问题");
        
        System.out.println("\n📊 三者对比:");
        System.out.println("   AtomicReference:         简单、高性能、可能有ABA问题");
        System.out.println("   AtomicStampedReference:  版本号控制、解决ABA、性能稍低");
        System.out.println("   AtomicMarkableReference: 布尔标记、简化版本控制");
        
        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              引用类型原子类演示                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：AtomicReference
        demoAtomicReference();

        // 演示2：AtomicStampedReference
        demoAtomicStampedReference();

        // 演示3：AtomicMarkableReference
        demoAtomicMarkableReference();

        // 演示4：无锁栈
        demoLockFreeStack();

        // 演示5：配置热更新
        demoConfigHotUpdate();

        // 总结
        summarizeUseCases();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. AtomicReference用于对象引用的原子更新");
        System.out.println("2. AtomicStampedReference通过版本号解决ABA问题");
        System.out.println("3. AtomicMarkableReference用于标记对象状态");
        System.out.println("4. 可以实现无锁的数据结构");
        System.out.println("5. 适用于配置热更新等场景");
        System.out.println("===========================");
    }
}
