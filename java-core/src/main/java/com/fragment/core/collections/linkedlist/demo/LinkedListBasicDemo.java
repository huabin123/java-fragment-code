package com.fragment.core.collections.linkedlist.demo;

import java.util.LinkedList;
import java.util.List;

/**
 * LinkedList基础使用演示
 * 
 * 演示内容：
 * 1. LinkedList的基本操作
 * 2. 头部和尾部操作
 * 3. 作为List使用
 * 4. 作为Deque使用
 * 
 * @author huabin
 */
public class LinkedListBasicDemo {

    public static void main(String[] args) {
        System.out.println("========== LinkedList基础使用演示 ==========\n");
        
        // 1. 基本操作
        basicOperations();
        
        // 2. 头部和尾部操作
        headAndTailOperations();
        
        // 3. 作为List使用
        useAsList();
        
        // 4. 作为Deque使用
        useAsDeque();
    }

    /**
     * 1. 基本操作演示
     */
    private static void basicOperations() {
        System.out.println("1. 基本操作演示");
        System.out.println("----------------------------------------");
        
        LinkedList<String> list = new LinkedList<>();
        
        // add：添加元素到尾部
        list.add("A");
        list.add("B");
        list.add("C");
        System.out.println("添加元素后: " + list);
        
        // get：获取指定位置的元素
        String element = list.get(1);
        System.out.println("get(1): " + element);
        
        // set：修改指定位置的元素
        String oldElement = list.set(1, "B2");
        System.out.println("set(1, B2)，旧值: " + oldElement + ", 新列表: " + list);
        
        // remove：删除指定位置的元素
        String removed = list.remove(1);
        System.out.println("remove(1)，删除的元素: " + removed + ", 新列表: " + list);
        
        // contains：判断是否包含元素
        boolean contains = list.contains("A");
        System.out.println("contains(A): " + contains);
        
        // size：获取元素个数
        System.out.println("size: " + list.size());
        
        // isEmpty：判断是否为空
        System.out.println("isEmpty: " + list.isEmpty());
        
        System.out.println();
    }

    /**
     * 2. 头部和尾部操作演示
     */
    private static void headAndTailOperations() {
        System.out.println("2. 头部和尾部操作演示");
        System.out.println("----------------------------------------");
        
        LinkedList<String> list = new LinkedList<>();
        
        // addFirst：添加到头部
        list.addFirst("A");
        list.addFirst("B");
        list.addFirst("C");
        System.out.println("addFirst后: " + list);  // [C, B, A]
        
        // addLast：添加到尾部
        list.addLast("D");
        list.addLast("E");
        System.out.println("addLast后: " + list);  // [C, B, A, D, E]
        
        // getFirst：获取头部元素
        String first = list.getFirst();
        System.out.println("getFirst: " + first);
        
        // getLast：获取尾部元素
        String last = list.getLast();
        System.out.println("getLast: " + last);
        
        // removeFirst：删除头部元素
        String removedFirst = list.removeFirst();
        System.out.println("removeFirst: " + removedFirst + ", 新列表: " + list);
        
        // removeLast：删除尾部元素
        String removedLast = list.removeLast();
        System.out.println("removeLast: " + removedLast + ", 新列表: " + list);
        
        System.out.println();
    }

    /**
     * 3. 作为List使用
     */
    private static void useAsList() {
        System.out.println("3. 作为List使用");
        System.out.println("----------------------------------------");
        
        List<String> list = new LinkedList<>();
        
        // 添加元素
        list.add("Java");
        list.add("Python");
        list.add("JavaScript");
        System.out.println("列表: " + list);
        
        // 在指定位置插入
        list.add(1, "Go");
        System.out.println("在位置1插入Go: " + list);
        
        // 删除指定元素
        list.remove("Python");
        System.out.println("删除Python: " + list);
        
        // 遍历（推荐使用foreach，不要用for循环+get）
        System.out.println("遍历:");
        for (String item : list) {
            System.out.println("  " + item);
        }
        
        System.out.println();
    }

    /**
     * 4. 作为Deque使用
     */
    private static void useAsDeque() {
        System.out.println("4. 作为Deque使用");
        System.out.println("----------------------------------------");
        
        LinkedList<String> deque = new LinkedList<>();
        
        // 作为栈使用（LIFO）
        System.out.println("作为栈使用:");
        deque.push("A");
        deque.push("B");
        deque.push("C");
        System.out.println("  push后: " + deque);
        
        String popped = deque.pop();
        System.out.println("  pop: " + popped + ", 剩余: " + deque);
        
        String peeked = deque.peek();
        System.out.println("  peek: " + peeked + ", 剩余: " + deque);
        
        // 清空
        deque.clear();
        
        // 作为队列使用（FIFO）
        System.out.println("\n作为队列使用:");
        deque.offer("A");
        deque.offer("B");
        deque.offer("C");
        System.out.println("  offer后: " + deque);
        
        String polled = deque.poll();
        System.out.println("  poll: " + polled + ", 剩余: " + deque);
        
        String peeked2 = deque.peek();
        System.out.println("  peek: " + peeked2 + ", 剩余: " + deque);
        
        System.out.println();
    }
}
