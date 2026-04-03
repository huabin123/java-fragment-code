package com.fragment.core.collections.hashmap.demo;

import java.util.HashMap;
import java.util.Map;

/**
 * HashMap Hash冲突演示
 * 
 * 演示内容：
 * 1. Hash冲突的产生
 * 2. 链表法解决冲突
 * 3. 红黑树优化（JDK 1.8）
 * 4. 自定义hashCode导致的冲突
 * 
 * @author huabin
 */
public class HashMapCollisionDemo {

    public static void main(String[] args) {
        System.out.println("========== HashMap Hash冲突演示 ==========\n");
        
        // 1. Hash冲突的产生
        hashCollision();
        
        // 2. 自定义对象的Hash冲突
        customObjectCollision();
        
        // 3. 大量冲突的性能影响
        performanceImpact();
    }

    /**
     * 1. Hash冲突的产生
     */
    private static void hashCollision() {
        System.out.println("1. Hash冲突的产生");
        System.out.println("----------------------------------------");
        
        // 创建一个容量为16的HashMap
        Map<Integer, String> map = new HashMap<>(16);
        
        // 这些key的hash值对16取模后相同，会产生冲突
        // hash(0) % 16 = 0
        // hash(16) % 16 = 0
        // hash(32) % 16 = 0
        map.put(0, "value0");
        map.put(16, "value16");
        map.put(32, "value32");
        
        System.out.println("添加冲突的key:");
        System.out.println("  key=0, hash=" + hash(0) + ", index=" + (hash(0) & 15));
        System.out.println("  key=16, hash=" + hash(16) + ", index=" + (hash(16) & 15));
        System.out.println("  key=32, hash=" + hash(32) + ", index=" + (hash(32) & 15));
        System.out.println("map: " + map);
        
        // 虽然产生冲突，但HashMap仍然能正确存储和获取
        System.out.println("\n获取值:");
        System.out.println("  get(0): " + map.get(0));
        System.out.println("  get(16): " + map.get(16));
        System.out.println("  get(32): " + map.get(32));
        
        System.out.println();
    }

    /**
     * 2. 自定义对象的Hash冲突
     */
    private static void customObjectCollision() {
        System.out.println("2. 自定义对象的Hash冲突");
        System.out.println("----------------------------------------");
        
        Map<Person, String> map = new HashMap<>();
        
        // 创建多个Person对象，它们的hashCode相同（故意设计）
        Person p1 = new Person("张三", 20);
        Person p2 = new Person("李四", 20);
        Person p3 = new Person("王五", 20);
        
        map.put(p1, "员工1");
        map.put(p2, "员工2");
        map.put(p3, "员工3");
        
        System.out.println("添加Person对象:");
        System.out.println("  " + p1 + ", hashCode=" + p1.hashCode());
        System.out.println("  " + p2 + ", hashCode=" + p2.hashCode());
        System.out.println("  " + p3 + ", hashCode=" + p3.hashCode());
        System.out.println("map size: " + map.size());
        
        System.out.println("\n获取值:");
        System.out.println("  get(p1): " + map.get(p1));
        System.out.println("  get(p2): " + map.get(p2));
        System.out.println("  get(p3): " + map.get(p3));
        
        System.out.println();
    }

    /**
     * 3. 大量冲突的性能影响
     */
    private static void performanceImpact() {
        System.out.println("3. 大量冲突的性能影响");
        System.out.println("----------------------------------------");
        
        // 场景1：无冲突的HashMap
        Map<Integer, String> noCollisionMap = new HashMap<>();
        long start1 = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            noCollisionMap.put(i, "value" + i);
        }
        long end1 = System.nanoTime();
        
        long start2 = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            noCollisionMap.get(i);
        }
        long end2 = System.nanoTime();
        
        System.out.println("无冲突的HashMap:");
        System.out.println("  插入10000个元素: " + (end1 - start1) / 1000000.0 + "ms");
        System.out.println("  查询10000次: " + (end2 - start2) / 1000000.0 + "ms");
        
        // 场景2：大量冲突的HashMap（使用BadHashKey）
        Map<BadHashKey, String> collisionMap = new HashMap<>();
        long start3 = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            collisionMap.put(new BadHashKey(i), "value" + i);
        }
        long end3 = System.nanoTime();
        
        long start4 = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            collisionMap.get(new BadHashKey(i));
        }
        long end4 = System.nanoTime();
        
        System.out.println("\n大量冲突的HashMap:");
        System.out.println("  插入10000个元素: " + (end3 - start3) / 1000000.0 + "ms");
        System.out.println("  查询10000次: " + (end4 - start4) / 1000000.0 + "ms");
        
        System.out.println("\n性能对比:");
        System.out.println("  插入性能下降: " + String.format("%.2f", (end3 - start3) / (double) (end1 - start1)) + "倍");
        System.out.println("  查询性能下降: " + String.format("%.2f", (end4 - start4) / (double) (end2 - start2)) + "倍");
        
        System.out.println("\n说明:");
        System.out.println("  JDK 1.8中，当链表长度超过8时，会转换为红黑树");
        System.out.println("  红黑树的查询时间复杂度为O(log n)，比链表的O(n)快很多");
        System.out.println("  因此即使大量冲突，性能也不会太差");
        
        System.out.println();
    }

    /**
     * 模拟HashMap的hash方法
     */
    private static int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    /**
     * Person类：hashCode只依赖age，容易产生冲突
     */
    static class Person {
        private String name;
        private int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public int hashCode() {
            // 只使用age计算hashCode，容易产生冲突
            return age;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Person person = (Person) obj;
            return age == person.age && name.equals(person.name);
        }

        @Override
        public String toString() {
            return "Person{name='" + name + "', age=" + age + "}";
        }
    }

    /**
     * BadHashKey类：所有对象的hashCode都相同，产生大量冲突
     */
    static class BadHashKey {
        private int value;

        public BadHashKey(int value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            // 所有对象的hashCode都是1，产生大量冲突
            return 1;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            BadHashKey that = (BadHashKey) obj;
            return value == that.value;
        }
    }
}
