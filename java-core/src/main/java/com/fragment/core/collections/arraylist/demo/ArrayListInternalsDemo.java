package com.fragment.core.collections.arraylist.demo;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * ArrayList 内部原理演示
 *
 * 演示内容：
 * 1. 动态扩容机制（1.5 倍扩容）
 * 2. 通过反射查看内部数组容量
 * 3. trimToSize() 的作用
 * 4. ensureCapacity() 预扩容优化
 */
public class ArrayListInternalsDemo {

    public static void main(String[] args) throws Exception {
        demonstrateExpansion();
        demonstrateTrimToSize();
        demonstrateEnsureCapacity();
    }

    /**
     * 演示扩容：每次容量不足时扩容为原来的 1.5 倍
     * 新容量 = 旧容量 + (旧容量 >> 1)
     */
    private static void demonstrateExpansion() throws Exception {
        System.out.println("=== 1. 扩容机制演示 ===");

        ArrayList<Integer> list = new ArrayList<>();  // 初始容量 0（JDK 8+ 懒初始化）
        System.out.println("初始容量: " + getCapacity(list));

        list.add(1);  // 第一次 add，扩容到 10
        System.out.println("add 第1个后容量: " + getCapacity(list));

        for (int i = 2; i <= 10; i++) list.add(i);
        System.out.println("add 到第10个后容量: " + getCapacity(list));

        list.add(11);  // 第11个触发扩容：10 + (10 >> 1) = 15
        System.out.println("add 第11个后容量: " + getCapacity(list));

        for (int i = 12; i <= 15; i++) list.add(i);
        list.add(16);  // 第16个触发扩容：15 + (15 >> 1) = 22
        System.out.println("add 第16个后容量: " + getCapacity(list));
        System.out.println("实际元素数: " + list.size());
        System.out.println();
    }

    /**
     * trimToSize()：裁剪内部数组到 size 大小，释放多余内存
     */
    private static void demonstrateTrimToSize() throws Exception {
        System.out.println("=== 2. trimToSize() ===");

        ArrayList<Integer> list = new ArrayList<>(100);  // 预分配 100
        for (int i = 0; i < 10; i++) list.add(i);

        System.out.println("添加10个后，size=" + list.size() + ", capacity=" + getCapacity(list));
        list.trimToSize();
        System.out.println("trimToSize 后，size=" + list.size() + ", capacity=" + getCapacity(list));
        System.out.println("适合场景：列表填充完毕后长期只读，节省内存");
        System.out.println();
    }

    /**
     * ensureCapacity()：提前扩容，避免多次小扩容带来的数组拷贝开销
     */
    private static void demonstrateEnsureCapacity() throws Exception {
        System.out.println("=== 3. ensureCapacity() 性能优化 ===");

        int count = 1_000_000;

        long start = System.currentTimeMillis();
        ArrayList<Integer> list1 = new ArrayList<>();
        for (int i = 0; i < count; i++) list1.add(i);
        System.out.println("不预扩容耗时: " + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        ArrayList<Integer> list2 = new ArrayList<>();
        list2.ensureCapacity(count);
        for (int i = 0; i < count; i++) list2.add(i);
        System.out.println("ensureCapacity 后耗时: " + (System.currentTimeMillis() - start) + "ms");
    }

    private static int getCapacity(ArrayList<?> list) throws Exception {
        Field field = ArrayList.class.getDeclaredField("elementData");
        field.setAccessible(true);
        Object[] elementData = (Object[]) field.get(list);
        return elementData.length;
    }
}
