package com.fragment.core.collections.queue.demo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

/**
 * ArrayDeque 基础使用演示
 *
 * 演示内容：
 * 1. 作为队列使用（FIFO）
 * 2. 作为栈使用（LIFO）
 * 3. 作为双端队列使用（Deque）
 * 4. ArrayDeque vs LinkedList 对比
 *
 * 核心特点：
 * - 基于可扩容循环数组实现
 * - 不允许存放 null 元素
 * - 非线程安全
 * - 作为栈/队列使用时，性能优于 LinkedList（无节点对象分配开销）
 *
 * @author huabin
 */
public class ArrayDequeDemo {

    public static void main(String[] args) {
        System.out.println("========== ArrayDeque 基础使用演示 ==========\n");

        // 1. 作为队列使用（FIFO）
        useAsQueue();

        // 2. 作为栈使用（LIFO）
        useAsStack();

        // 3. 作为双端队列使用
        useAsDeque();

        // 4. 常用方法对比：抛异常 vs 返回特殊值
        methodComparison();
    }

    /**
     * 1. 作为队列使用（FIFO：先进先出）
     */
    private static void useAsQueue() {
        System.out.println("1. 作为队列使用（FIFO）");
        System.out.println("----------------------------------------");

        Queue<String> queue = new ArrayDeque<>();

        // offer：入队（推荐，失败返回 false，不抛异常）
        queue.offer("任务A");
        queue.offer("任务B");
        queue.offer("任务C");
        System.out.println("入队后: " + queue);

        // peek：查看队头，不移除（队列为空返回 null）
        String head = queue.peek();
        System.out.println("peek 队头: " + head + ", 队列不变: " + queue);

        // poll：出队（队列为空返回 null，不抛异常）
        String polled = queue.poll();
        System.out.println("poll 出队: " + polled + ", 剩余: " + queue);

        polled = queue.poll();
        System.out.println("poll 出队: " + polled + ", 剩余: " + queue);

        System.out.println("size: " + queue.size());
        System.out.println("isEmpty: " + queue.isEmpty());
        System.out.println();
    }

    /**
     * 2. 作为栈使用（LIFO：后进先出）
     *
     * 官方推荐用 ArrayDeque 替代 Stack 类：
     * Stack 继承自 Vector，所有方法都加了 synchronized，
     * 单线程场景下有不必要的性能损耗。
     */
    private static void useAsStack() {
        System.out.println("2. 作为栈使用（LIFO）");
        System.out.println("----------------------------------------");

        Deque<String> stack = new ArrayDeque<>();

        // push：入栈（等同于 addFirst，栈满时抛 IllegalStateException）
        stack.push("第1层");
        stack.push("第2层");
        stack.push("第3层");
        System.out.println("入栈后: " + stack);  // [第3层, 第2层, 第1层]

        // peek：查看栈顶，不移除
        String top = stack.peek();
        System.out.println("peek 栈顶: " + top + ", 栈不变: " + stack);

        // pop：出栈（栈为空时抛 NoSuchElementException）
        String popped = stack.pop();
        System.out.println("pop 出栈: " + popped + ", 剩余: " + stack);

        popped = stack.pop();
        System.out.println("pop 出栈: " + popped + ", 剩余: " + stack);

        System.out.println();
    }

    /**
     * 3. 作为双端队列使用（两端均可入队/出队）
     */
    private static void useAsDeque() {
        System.out.println("3. 作为双端队列使用");
        System.out.println("----------------------------------------");

        Deque<String> deque = new ArrayDeque<>();

        // 从头部操作
        deque.offerFirst("B");
        deque.offerFirst("A");  // [A, B]
        System.out.println("offerFirst A,B 后: " + deque);

        // 从尾部操作
        deque.offerLast("C");
        deque.offerLast("D");  // [A, B, C, D]
        System.out.println("offerLast C,D 后: " + deque);

        // 查看两端
        System.out.println("peekFirst: " + deque.peekFirst());
        System.out.println("peekLast: " + deque.peekLast());

        // 从头部移除
        String fromHead = deque.pollFirst();
        System.out.println("pollFirst: " + fromHead + ", 剩余: " + deque);

        // 从尾部移除
        String fromTail = deque.pollLast();
        System.out.println("pollLast: " + fromTail + ", 剩余: " + deque);

        System.out.println();
    }

    /**
     * 4. 常用方法对比：抛异常版 vs 返回特殊值版
     *
     * Queue 接口方法：
     *   抛异常：add(e)    remove()  element()
     *   返特殊值：offer(e)  poll()    peek()
     *
     * Deque 头部操作：
     *   抛异常：addFirst(e)   removeFirst()  getFirst()
     *   返特殊值：offerFirst(e)  pollFirst()    peekFirst()
     *
     * Deque 尾部操作：
     *   抛异常：addLast(e)   removeLast()  getLast()
     *   返特殊值：offerLast(e)  pollLast()   peekLast()
     */
    private static void methodComparison() {
        System.out.println("4. 方法对比：抛异常 vs 返回特殊值");
        System.out.println("----------------------------------------");

        Deque<Integer> deque = new ArrayDeque<>();

        // 空队列时的差异
        System.out.println("空队列 poll(): " + deque.poll());      // null，不抛异常
        System.out.println("空队列 peek(): " + deque.peek());      // null，不抛异常

        try {
            deque.remove();  // 抛 NoSuchElementException
        } catch (Exception e) {
            System.out.println("空队列 remove() 抛异常: " + e.getClass().getSimpleName());
        }

        try {
            deque.element();  // 抛 NoSuchElementException
        } catch (Exception e) {
            System.out.println("空队列 element() 抛异常: " + e.getClass().getSimpleName());
        }

        // 生产建议：优先使用 offer/poll/peek，避免异常处理开销
        deque.offer(1);
        deque.offer(2);
        deque.offer(3);
        System.out.println("\n推荐写法（offer/poll/peek）:");
        Integer val;
        while ((val = deque.poll()) != null) {
            System.out.println("  处理: " + val);
        }

        System.out.println();
    }
}
