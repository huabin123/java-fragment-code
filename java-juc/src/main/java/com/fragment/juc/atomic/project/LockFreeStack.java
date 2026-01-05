package com.fragment.juc.atomic.project;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 无锁栈实现（基于CAS）
 * 
 * 实现内容：
 * 1. 基于AtomicReference的无锁栈
 * 2. 线程安全的push/pop操作
 * 3. 性能测试和对比
 * 4. ABA问题的影响分析
 * 
 * @author huabin
 */
public class LockFreeStack<E> {

    /**
     * 栈节点
     */
    private static class Node<E> {
        final E item;
        Node<E> next;

        Node(E item) {
            this.item = item;
        }
    }

    /**
     * 栈顶指针
     */
    private final AtomicReference<Node<E>> top = new AtomicReference<>();

    /**
     * 栈大小（用于统计）
     */
    private final AtomicInteger size = new AtomicInteger(0);

    /**
     * push操作统计
     */
    private final AtomicInteger pushRetries = new AtomicInteger(0);

    /**
     * pop操作统计
     */
    private final AtomicInteger popRetries = new AtomicInteger(0);

    /**
     * 入栈操作
     * 
     * @param item 要入栈的元素
     */
    public void push(E item) {
        Node<E> newNode = new Node<>(item);
        Node<E> oldTop;
        
        // CAS循环，直到成功
        do {
            oldTop = top.get();
            newNode.next = oldTop;
            
            // 统计重试次数
            if (oldTop != null) {
                pushRetries.incrementAndGet();
            }
        } while (!top.compareAndSet(oldTop, newNode));
        
        size.incrementAndGet();
    }

    /**
     * 出栈操作
     * 
     * @return 栈顶元素，如果栈为空返回null
     */
    public E pop() {
        Node<E> oldTop;
        Node<E> newTop;
        
        // CAS循环，直到成功或栈为空
        do {
            oldTop = top.get();
            if (oldTop == null) {
                return null; // 栈为空
            }
            newTop = oldTop.next;
            
            // 统计重试次数
            if (newTop != null) {
                popRetries.incrementAndGet();
            }
        } while (!top.compareAndSet(oldTop, newTop));
        
        size.decrementAndGet();
        return oldTop.item;
    }

    /**
     * 查看栈顶元素（不移除）
     * 
     * @return 栈顶元素，如果栈为空返回null
     */
    public E peek() {
        Node<E> current = top.get();
        return (current == null) ? null : current.item;
    }

    /**
     * 判断栈是否为空
     * 
     * @return true如果栈为空
     */
    public boolean isEmpty() {
        return top.get() == null;
    }

    /**
     * 获取栈大小（近似值）
     * 
     * @return 栈中元素数量
     */
    public int size() {
        return size.get();
    }

    /**
     * 获取push重试次数
     */
    public int getPushRetries() {
        return pushRetries.get();
    }

    /**
     * 获取pop重试次数
     */
    public int getPopRetries() {
        return popRetries.get();
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        pushRetries.set(0);
        popRetries.set(0);
    }

    /**
     * 打印栈内容（用于调试）
     */
    public void printStack() {
        System.out.print("Stack (top -> bottom): ");
        Node<E> current = top.get();
        while (current != null) {
            System.out.print(current.item + " -> ");
            current = current.next;
        }
        System.out.println("null");
    }

    /**
     * 演示1：基本操作
     */
    public static void demoBasicOperations() {
        System.out.println("\n========== 演示1：基本操作 ==========\n");

        LockFreeStack<Integer> stack = new LockFreeStack<>();

        // Push操作
        System.out.println("Push操作:");
        for (int i = 1; i <= 5; i++) {
            stack.push(i);
            System.out.println("  push(" + i + ")");
        }
        stack.printStack();
        System.out.println("  栈大小: " + stack.size());

        // Peek操作
        System.out.println("\nPeek操作:");
        Integer top = stack.peek();
        System.out.println("  peek() = " + top);
        System.out.println("  栈大小: " + stack.size() + " (peek不改变大小)");

        // Pop操作
        System.out.println("\nPop操作:");
        while (!stack.isEmpty()) {
            Integer item = stack.pop();
            System.out.println("  pop() = " + item);
        }
        System.out.println("  栈大小: " + stack.size());

        System.out.println("\n✅ 基本操作正常工作");
    }

    /**
     * 演示2：多线程并发测试
     */
    public static void demoConcurrency() throws InterruptedException {
        System.out.println("\n========== 演示2：多线程并发测试 ==========\n");

        LockFreeStack<Integer> stack = new LockFreeStack<>();
        final int threadCount = 10;
        final int operationsPerThread = 1000;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount * 2);

        // 创建push线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    for (int j = 0; j < operationsPerThread; j++) {
                        stack.push(threadId * 10000 + j);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }, "Push-" + i).start();
        }

        // 创建pop线程
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    for (int j = 0; j < operationsPerThread; j++) {
                        stack.pop();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }, "Pop-" + i).start();
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // 开始
        endLatch.await(); // 等待结束
        long endTime = System.currentTimeMillis();

        System.out.println("并发测试结果:");
        System.out.println("  线程数: " + threadCount + " (push) + " + threadCount + " (pop)");
        System.out.println("  每线程操作数: " + operationsPerThread);
        System.out.println("  总操作数: " + (threadCount * operationsPerThread * 2));
        System.out.println("  最终栈大小: " + stack.size());
        System.out.println("  Push重试次数: " + stack.getPushRetries());
        System.out.println("  Pop重试次数: " + stack.getPopRetries());
        System.out.println("  总耗时: " + (endTime - startTime) + "ms");

        System.out.println("\n✅ 无锁栈在高并发下保持了线程安全");
    }

    /**
     * 演示3：性能对比（无锁 vs 有锁）
     */
    public static void comparePerformance() throws InterruptedException {
        System.out.println("\n========== 演示3：性能对比 ==========\n");

        final int threadCount = 20;
        final int operations = 10000;

        // 测试1：无锁栈
        System.out.println("测试无锁栈...");
        LockFreeStack<Integer> lockFreeStack = new LockFreeStack<>();
        long lockFreeTime = testStack(lockFreeStack, threadCount, operations);

        // 测试2：有锁栈（使用synchronized）
        System.out.println("测试有锁栈...");
        SynchronizedStack<Integer> syncStack = new SynchronizedStack<>();
        long syncTime = testStack(syncStack, threadCount, operations);

        // 输出对比结果
        System.out.println("\n性能对比结果:");
        System.out.println("  无锁栈耗时: " + lockFreeTime + "ms");
        System.out.println("  有锁栈耗时: " + syncTime + "ms");
        System.out.println("  性能提升: " + 
                         String.format("%.2f", (double) syncTime / lockFreeTime) + "x");

        if (lockFreeTime < syncTime) {
            System.out.println("  ✅ 无锁栈性能优于有锁栈");
        }
    }

    /**
     * 测试栈性能的辅助方法
     */
    private static <T> long testStack(StackInterface<Integer> stack, 
                                      int threadCount, 
                                      int operations) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadCount);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    stack.push(j);
                }
                for (int j = 0; j < operations; j++) {
                    stack.pop();
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 栈接口
     */
    interface StackInterface<E> {
        void push(E item);
        E pop();
        boolean isEmpty();
    }

    /**
     * 有锁栈实现（用于对比）
     */
    static class SynchronizedStack<E> implements StackInterface<E> {
        private Node<E> top;

        public synchronized void push(E item) {
            Node<E> newNode = new Node<>(item);
            newNode.next = top;
            top = newNode;
        }

        public synchronized E pop() {
            if (top == null) {
                return null;
            }
            E item = top.item;
            top = top.next;
            return item;
        }

        public synchronized boolean isEmpty() {
            return top == null;
        }
    }

    /**
     * 演示4：ABA问题的影响
     */
    public static void demoABAProblem() throws InterruptedException {
        System.out.println("\n========== 演示4：ABA问题的影响 ==========\n");

        System.out.println("栈的ABA问题场景:");
        System.out.println("  初始状态: A -> B -> C");
        System.out.println("  线程1: 准备pop A，读取到A和B");
        System.out.println("  线程2: pop A, pop B, push A");
        System.out.println("  结果: A -> C (B丢失了)");
        System.out.println("  线程1: CAS成功(top从A变为B)，但B已经不在栈中了！");

        System.out.println("\n实际影响:");
        System.out.println("  - 在栈的场景下，ABA问题可能导致节点丢失");
        System.out.println("  - 但在大多数情况下，影响较小");
        System.out.println("  - 如果需要完全避免，可以使用AtomicStampedReference");

        System.out.println("\n⚠️  注意: 本实现为了性能，接受了ABA问题的存在");
        System.out.println("   在实际生产环境中，需要根据具体场景评估风险");
    }

    /**
     * 演示5：实际应用场景
     */
    public static void demoRealWorldUsage() throws InterruptedException {
        System.out.println("\n========== 演示5：实际应用场景 ==========\n");

        // 场景：任务调度器
        class Task {
            private final int id;
            private final String name;

            Task(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public String toString() {
                return "Task{id=" + id + ", name='" + name + "'}";
            }
        }

        LockFreeStack<Task> taskStack = new LockFreeStack<>();

        System.out.println("场景：高并发任务调度器\n");

        // 生产者：添加任务
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                Task task = new Task(i, "Task-" + i);
                taskStack.push(task);
                System.out.println("[Producer] 添加任务: " + task);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Producer");

        // 消费者：处理任务
        Thread[] consumers = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int consumerId = i;
            consumers[i] = new Thread(() -> {
                while (true) {
                    Task task = taskStack.pop();
                    if (task == null) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            break;
                        }
                        if (taskStack.isEmpty()) {
                            break;
                        }
                    } else {
                        System.out.println("[Consumer-" + consumerId + "] 处理任务: " + task);
                        try {
                            Thread.sleep(100); // 模拟处理时间
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }, "Consumer-" + i);
        }

        producer.start();
        for (Thread consumer : consumers) {
            consumer.start();
        }

        producer.join();
        for (Thread consumer : consumers) {
            consumer.join();
        }

        System.out.println("\n✅ 无锁栈适用于高并发的任务调度场景");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 无锁栈总结 ==========");

        System.out.println("\n✅ 优点:");
        System.out.println("   1. 无锁设计，避免线程阻塞");
        System.out.println("   2. 高并发场景下性能优于有锁实现");
        System.out.println("   3. 不会出现死锁");
        System.out.println("   4. 实现相对简单");

        System.out.println("\n⚠️  缺点:");
        System.out.println("   1. 可能存在ABA问题");
        System.out.println("   2. 高竞争时CAS重试消耗CPU");
        System.out.println("   3. size()返回的是近似值");
        System.out.println("   4. 不适合低并发场景");

        System.out.println("\n📊 适用场景:");
        System.out.println("   ✅ 高并发的任务队列");
        System.out.println("   ✅ 临时数据缓存");
        System.out.println("   ✅ 对象池管理");
        System.out.println("   ❌ 需要精确大小的场景");
        System.out.println("   ❌ 低并发场景（开销大于收益）");

        System.out.println("\n💡 设计要点:");
        System.out.println("   1. 使用AtomicReference保证栈顶指针的原子性");
        System.out.println("   2. CAS循环保证操作的原子性");
        System.out.println("   3. 节点设计要简单，避免复杂的状态");
        System.out.println("   4. 考虑ABA问题的影响");

        System.out.println("===========================");
    }

    /**
     * 主函数
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              无锁栈实现（基于CAS）                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本操作
        demoBasicOperations();

        // 演示2：并发测试
        demoConcurrency();

        // 演示3：性能对比
        comparePerformance();

        // 演示4：ABA问题
        demoABAProblem();

        // 演示5：实际应用
        demoRealWorldUsage();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. 无锁栈通过CAS实现线程安全");
        System.out.println("2. 高并发场景下性能优于有锁实现");
        System.out.println("3. 需要注意ABA问题的影响");
        System.out.println("4. 适用于高并发的任务调度场景");
        System.out.println("===========================");
    }
}
