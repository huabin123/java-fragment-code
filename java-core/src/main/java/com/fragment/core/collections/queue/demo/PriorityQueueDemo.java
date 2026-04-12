package com.fragment.core.collections.queue.demo;

import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * PriorityQueue 基础使用演示
 *
 * 演示内容：
 * 1. 自然排序（最小堆）
 * 2. 自定义排序（最大堆）
 * 3. 对象排序（Comparator）
 * 4. 典型应用：TopK 问题
 *
 * 核心特点：
 * - 基于最小堆（Min-Heap）实现
 * - poll() 始终返回当前最小元素
 * - 不允许存放 null 元素
 * - 非线程安全（线程安全版本用 PriorityBlockingQueue）
 * - 入队/出队时间复杂度 O(log n)，peek O(1)
 *
 * @author huabin
 */
public class PriorityQueueDemo {

    public static void main(String[] args) {
        System.out.println("========== PriorityQueue 基础使用演示 ==========\n");

        // 1. 自然排序（最小堆）
        naturalOrdering();

        // 2. 自定义排序（最大堆）
        maxHeap();

        // 3. 对象排序（Comparator）
        objectOrdering();

        // 4. TopK 问题（面试高频）
        topKProblem();
    }

    /**
     * 1. 自然排序（默认最小堆）
     *
     * 默认按元素自然顺序排列，poll() 优先返回最小值。
     */
    private static void naturalOrdering() {
        System.out.println("1. 自然排序（最小堆）");
        System.out.println("----------------------------------------");

        PriorityQueue<Integer> pq = new PriorityQueue<>();

        // 无序插入
        pq.offer(5);
        pq.offer(1);
        pq.offer(3);
        pq.offer(2);
        pq.offer(4);
        System.out.println("插入: 5, 1, 3, 2, 4");

        // peek 只看，不移除
        System.out.println("peek（当前最小值）: " + pq.peek());

        // poll 按优先级（最小值）依次出队
        System.out.print("poll 顺序: ");
        while (!pq.isEmpty()) {
            System.out.print(pq.poll() + " ");
        }
        System.out.println();

        // 注意：PriorityQueue 的迭代器不保证顺序！
        pq.offer(5);
        pq.offer(1);
        pq.offer(3);
        System.out.print("forEach 遍历（不保证顺序）: ");
        pq.forEach(e -> System.out.print(e + " "));
        System.out.println("\n");
    }

    /**
     * 2. 自定义排序（最大堆）
     *
     * 通过传入 Comparator.reverseOrder() 将最小堆变为最大堆。
     */
    private static void maxHeap() {
        System.out.println("2. 自定义排序（最大堆）");
        System.out.println("----------------------------------------");

        // 方式1：Comparator.reverseOrder()
        PriorityQueue<Integer> maxPQ = new PriorityQueue<>(Comparator.reverseOrder());

        maxPQ.offer(5);
        maxPQ.offer(1);
        maxPQ.offer(3);
        maxPQ.offer(2);
        maxPQ.offer(4);
        System.out.println("插入: 5, 1, 3, 2, 4");
        System.out.println("peek（当前最大值）: " + maxPQ.peek());

        System.out.print("poll 顺序（从大到小）: ");
        while (!maxPQ.isEmpty()) {
            System.out.print(maxPQ.poll() + " ");
        }
        System.out.println();

        // 方式2：Collections.reverseOrder()（等效）
        PriorityQueue<Integer> maxPQ2 = new PriorityQueue<>(Collections.reverseOrder());
        maxPQ2.offer(10);
        maxPQ2.offer(30);
        maxPQ2.offer(20);
        System.out.println("最大堆 peek: " + maxPQ2.peek());
        System.out.println();
    }

    /**
     * 3. 对象排序（Comparator）
     *
     * 对自定义对象按指定字段排序。
     */
    private static void objectOrdering() {
        System.out.println("3. 对象排序（按优先级数字升序，数字越小优先级越高）");
        System.out.println("----------------------------------------");

        // 按 priority 升序（数值小的先出队）
        PriorityQueue<Task> pq = new PriorityQueue<>(Comparator.comparingInt(t -> t.priority));

        pq.offer(new Task("发送邮件", 3));
        pq.offer(new Task("数据库备份", 1));
        pq.offer(new Task("用户登录", 2));
        pq.offer(new Task("系统告警", 1));

        System.out.println("按优先级出队:");
        while (!pq.isEmpty()) {
            Task task = pq.poll();
            System.out.println("  [优先级" + task.priority + "] " + task.name);
        }

        // 多字段排序：先按优先级，再按任务名
        System.out.println("\n多字段排序（优先级相同时按名称）:");
        PriorityQueue<Task> pq2 = new PriorityQueue<>(
            Comparator.comparingInt((Task t) -> t.priority)
                      .thenComparing(t -> t.name)
        );
        pq2.offer(new Task("发送邮件", 1));
        pq2.offer(new Task("数据库备份", 2));
        pq2.offer(new Task("告警通知", 1));
        while (!pq2.isEmpty()) {
            Task task = pq2.poll();
            System.out.println("  [优先级" + task.priority + "] " + task.name);
        }
        System.out.println();
    }

    /**
     * 4. TopK 问题（面试高频）
     *
     * 求数组中最大的 K 个数，使用最小堆维护大小为 K 的滑动窗口。
     *
     * 时间复杂度：O(n log k)，空间复杂度：O(k)
     * 比排序后取前 K 个（O(n log n)）更高效，n 远大于 k 时优势明显。
     */
    private static void topKProblem() {
        System.out.println("4. TopK 问题（求最大的 K 个数）");
        System.out.println("----------------------------------------");

        int[] nums = {3, 2, 1, 5, 6, 4, 8, 7, 9, 10, 0};
        int k = 4;
        System.out.println("数组: [3, 2, 1, 5, 6, 4, 8, 7, 9, 10, 0]，求最大的 " + k + " 个数");

        // 维护一个大小为 k 的最小堆
        // 堆顶是当前 TopK 中最小的，新元素比堆顶大才入堆
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(k);

        for (int num : nums) {
            if (minHeap.size() < k) {
                minHeap.offer(num);
            } else if (num > minHeap.peek()) {
                minHeap.poll();    // 移除当前最小值
                minHeap.offer(num); // 换入更大的值
            }
        }

        System.out.println("最大的 " + k + " 个数（最小堆中的元素，出队顺序从小到大）:");
        System.out.print("  ");
        while (!minHeap.isEmpty()) {
            System.out.print(minHeap.poll() + " ");
        }
        System.out.println();

        // 变体：求最小的 K 个数，用最大堆
        System.out.println("\n变体：最小的 " + k + " 个数（用最大堆）:");
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(k, Comparator.reverseOrder());
        for (int num : nums) {
            if (maxHeap.size() < k) {
                maxHeap.offer(num);
            } else if (num < maxHeap.peek()) {
                maxHeap.poll();
                maxHeap.offer(num);
            }
        }
        System.out.print("  ");
        // 从大到小出队，收集后逆序展示
        int[] result = new int[k];
        for (int i = k - 1; i >= 0; i--) {
            result[i] = maxHeap.poll();
        }
        for (int val : result) {
            System.out.print(val + " ");
        }
        System.out.println("\n");
    }

    /**
     * 任务类
     */
    static class Task {
        String name;
        int priority;

        Task(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
