package com.fragment.core.collections.hashmap.demo;

import java.util.HashMap;
import java.util.Map;

/**
 * HashMap基础使用演示
 * 
 * 演示内容：
 * 1. HashMap的基本操作（put、get、remove）
 * 2. HashMap的遍历方式
 * 3. HashMap的常用方法
 * 4. null key和null value的处理
 * 
 * @author huabin
 */
public class HashMapBasicDemo {

    public static void main(String[] args) {
        System.out.println("========== HashMap基础使用演示 ==========\n");
        
        // 1. 基本操作
        basicOperations();
        
        // 2. 遍历方式
        iterationMethods();
        
        // 3. 常用方法
        commonMethods();
        
        // 4. null key和null value
        nullKeyAndValue();
    }

    /**
     * 1. 基本操作演示
     */
    private static void basicOperations() {
        System.out.println("1. 基本操作演示");
        System.out.println("----------------------------------------");
        
        // 创建HashMap
        Map<String, Integer> map = new HashMap<>();
        
        // put：添加元素
        map.put("张三", 20);
        map.put("李四", 25);
        map.put("王五", 30);
        System.out.println("添加元素后: " + map);
        
        // get：获取元素
        Integer age = map.get("张三");
        System.out.println("张三的年龄: " + age);
        
        // put：更新元素（key相同）
        Integer oldAge = map.put("张三", 21);
        System.out.println("更新张三的年龄，旧值: " + oldAge + ", 新值: " + map.get("张三"));
        
        // remove：删除元素
        Integer removedAge = map.remove("李四");
        System.out.println("删除李四，返回值: " + removedAge);
        System.out.println("删除后: " + map);
        
        // containsKey：判断key是否存在
        boolean hasZhangSan = map.containsKey("张三");
        System.out.println("是否包含张三: " + hasZhangSan);
        
        // containsValue：判断value是否存在
        boolean hasAge30 = map.containsValue(30);
        System.out.println("是否包含年龄30: " + hasAge30);
        
        // size：获取元素个数
        System.out.println("元素个数: " + map.size());
        
        // isEmpty：判断是否为空
        System.out.println("是否为空: " + map.isEmpty());
        
        System.out.println();
    }

    /**
     * 2. 遍历方式演示
     */
    private static void iterationMethods() {
        System.out.println("2. 遍历方式演示");
        System.out.println("----------------------------------------");
        
        Map<String, Integer> map = new HashMap<>();
        map.put("Java", 95);
        map.put("Python", 88);
        map.put("JavaScript", 92);
        map.put("Go", 85);
        
        // 方式1：遍历entrySet（推荐，性能最好）
        System.out.println("方式1：遍历entrySet");
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        
        // 方式2：遍历keySet
        System.out.println("\n方式2：遍历keySet");
        for (String key : map.keySet()) {
            Integer value = map.get(key);
            System.out.println("  " + key + " -> " + value);
        }
        
        // 方式3：遍历values
        System.out.println("\n方式3：遍历values");
        for (Integer value : map.values()) {
            System.out.println("  分数: " + value);
        }
        
        // 方式4：JDK 8的forEach（推荐）
        System.out.println("\n方式4：JDK 8的forEach");
        map.forEach((key, value) -> System.out.println("  " + key + " -> " + value));
        
        // 方式5：使用Iterator
        System.out.println("\n方式5：使用Iterator");
        var iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        
        System.out.println();
    }

    /**
     * 3. 常用方法演示
     */
    private static void commonMethods() {
        System.out.println("3. 常用方法演示");
        System.out.println("----------------------------------------");
        
        Map<String, Integer> map = new HashMap<>();
        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        
        // putIfAbsent：如果key不存在才添加
        Integer result1 = map.putIfAbsent("D", 4);
        System.out.println("putIfAbsent(D, 4)，返回值: " + result1 + ", map: " + map);
        
        Integer result2 = map.putIfAbsent("A", 10);
        System.out.println("putIfAbsent(A, 10)，返回值: " + result2 + ", map: " + map);
        
        // getOrDefault：获取值，如果不存在返回默认值
        Integer value1 = map.getOrDefault("A", 0);
        System.out.println("getOrDefault(A, 0): " + value1);
        
        Integer value2 = map.getOrDefault("E", 0);
        System.out.println("getOrDefault(E, 0): " + value2);
        
        // replace：替换值
        Integer oldValue = map.replace("A", 100);
        System.out.println("replace(A, 100)，旧值: " + oldValue + ", map: " + map);
        
        // replace：只有当前值匹配时才替换
        boolean replaced = map.replace("B", 2, 200);
        System.out.println("replace(B, 2, 200): " + replaced + ", map: " + map);
        
        boolean notReplaced = map.replace("C", 999, 300);
        System.out.println("replace(C, 999, 300): " + notReplaced + ", map: " + map);
        
        // compute：计算新值
        map.compute("A", (key, value) -> value == null ? 1 : value + 1);
        System.out.println("compute(A, v+1): " + map);
        
        // computeIfAbsent：如果key不存在，计算并添加
        map.computeIfAbsent("E", key -> 5);
        System.out.println("computeIfAbsent(E, 5): " + map);
        
        // computeIfPresent：如果key存在，计算新值
        map.computeIfPresent("E", (key, value) -> value * 2);
        System.out.println("computeIfPresent(E, v*2): " + map);
        
        // merge：合并值
        map.merge("A", 1, (oldVal, newVal) -> oldVal + newVal);
        System.out.println("merge(A, 1, sum): " + map);
        
        // clear：清空
        map.clear();
        System.out.println("clear后: " + map + ", size: " + map.size());
        
        System.out.println();
    }

    /**
     * 4. null key和null value演示
     */
    private static void nullKeyAndValue() {
        System.out.println("4. null key和null value演示");
        System.out.println("----------------------------------------");
        
        Map<String, String> map = new HashMap<>();
        
        // HashMap允许一个null key
        map.put(null, "null-key-value");
        System.out.println("添加null key: " + map);
        
        // 获取null key的值
        String value = map.get(null);
        System.out.println("get(null): " + value);
        
        // HashMap允许多个null value
        map.put("key1", null);
        map.put("key2", null);
        map.put("key3", "value3");
        System.out.println("添加null value: " + map);
        
        // 判断是否包含null key
        boolean hasNullKey = map.containsKey(null);
        System.out.println("是否包含null key: " + hasNullKey);
        
        // 判断是否包含null value
        boolean hasNullValue = map.containsValue(null);
        System.out.println("是否包含null value: " + hasNullValue);
        
        // 注意：get返回null有两种情况
        // 1. key不存在
        // 2. key存在但value为null
        System.out.println("\nget返回null的两种情况:");
        System.out.println("  get(\"key1\"): " + map.get("key1") + " (value为null)");
        System.out.println("  get(\"notExist\"): " + map.get("notExist") + " (key不存在)");
        
        // 使用containsKey区分这两种情况
        String key = "key1";
        if (map.containsKey(key)) {
            System.out.println("  " + key + "存在，value为: " + map.get(key));
        } else {
            System.out.println("  " + key + "不存在");
        }
        
        System.out.println();
    }
}
