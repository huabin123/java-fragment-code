package com.fragment.core.generics.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 泛型常见陷阱和最佳实践演示
 */
public class GenericPitfallsDemo {

    public static void main(String[] args) {
        // 演示泛型类型擦除
        demonstrateTypeErasure();

        System.out.println("\n-----------------------------------------\n");

        // 演示泛型数组创建问题
        demonstrateGenericArrayCreation();

        System.out.println("\n-----------------------------------------\n");

        // 演示原始类型的问题
        demonstrateRawTypes();

        System.out.println("\n-----------------------------------------\n");

        // 演示无界通配符的使用
        demonstrateUnboundedWildcard();

        System.out.println("\n-----------------------------------------\n");

        // 演示泛型方法
        demonstrateGenericMethods();
    }

    /**
     * 演示泛型类型擦除
     * 在运行时，泛型类型信息会被擦除，只保留原始类型
     */
    private static void demonstrateTypeErasure() {
        System.out.println("===== 泛型类型擦除 =====");

        List<String> stringList = new ArrayList<>();
        List<Integer> integerList = new ArrayList<>();

        // 在运行时，stringList 和 integerList 的类型是相同的
        System.out.println("字符串列表类型: " + stringList.getClass().getName());
        System.out.println("整数列表类型: " + integerList.getClass().getName());
        System.out.println("两个列表类型是否相同: " + (stringList.getClass() == integerList.getClass()));

        // 类型擦除后，泛型类型参数信息在运行时不可用
        // 以下代码无法判断列表的泛型类型
        // if (list instanceof ArrayList<String>) {} // 编译错误

        // 只能判断原始类型
        if (stringList instanceof ArrayList) {
            System.out.println("stringList 是 ArrayList 类型");
        }
    }

    /**
     * 演示泛型数组创建问题
     * 不能创建泛型类型的数组，因为类型擦除会导致类型安全问题
     */
    private static void demonstrateGenericArrayCreation() {
        System.out.println("===== 泛型数组创建问题 =====");

        // 以下代码会导致编译错误
        // List<String>[] stringListArray = new ArrayList<String>[10]; // 编译错误

        // 可以创建通配符类型的数组，但使用受限
        List<?>[] wildcardListArray = new ArrayList<?>[10];
        wildcardListArray[0] = new ArrayList<String>();
        wildcardListArray[1] = new ArrayList<Integer>();

        System.out.println("通配符类型数组的第一个元素: " + wildcardListArray[0].getClass().getName());
        System.out.println("通配符类型数组的第二个元素: " + wildcardListArray[1].getClass().getName());

        // 可以使用原始类型创建数组，但会有类型安全警告
        @SuppressWarnings("unchecked")
        List<String>[] unsafeArray = (List<String>[]) new ArrayList[10];
        System.out.println("不安全的泛型数组类型: " + unsafeArray.getClass().getComponentType().getName());

        // 推荐的做法：使用 List<List<String>> 代替 List<String>[]
        List<List<String>> listOfLists = new ArrayList<>();
        listOfLists.add(new ArrayList<>(Arrays.asList("a", "b", "c")));
        listOfLists.add(new ArrayList<>(Arrays.asList("d", "e", "f")));

        System.out.println("列表的列表 (推荐做法): " + listOfLists);
    }

    /**
     * 演示原始类型的问题
     * 原始类型会绕过泛型类型检查，可能导致运行时错误
     */
    private static void demonstrateRawTypes() {
        System.out.println("===== 原始类型问题 =====");

        // 创建原始类型列表
        List rawList = new ArrayList();
        rawList.add("字符串");
        rawList.add(123);
        rawList.add(45.67);

        System.out.println("原始类型列表: " + rawList);

        // 将原始类型赋值给参数化类型 (会产生警告)
        List<String> stringList = rawList; // 不安全的操作

        // 尝试使用 stringList 中的元素作为字符串
        try {
            System.out.println("尝试使用第一个元素: " + stringList.get(0).length()); // 正常工作
            System.out.println("尝试使用第二个元素: " + stringList.get(1).length()); // 运行时错误
        } catch (ClassCastException e) {
            System.out.println("发生类型转换异常: " + e.getMessage());
        }

        // 正确的做法：始终使用参数化类型
        List<Object> objectList = new ArrayList<>();
        objectList.add("字符串");
        objectList.add(123);
        objectList.add(45.67);

        System.out.println("参数化类型列表: " + objectList);

        // 使用时进行类型检查
        for (Object obj : objectList) {
            if (obj instanceof String) {
                System.out.println("字符串长度: " + ((String) obj).length());
            } else {
                System.out.println("非字符串对象: " + obj);
            }
        }
    }

    /**
     * 演示无界通配符 <?> 的使用
     * 适用于与泛型参数类型无关的操作
     */
    private static void demonstrateUnboundedWildcard() {
        System.out.println("===== 无界通配符 <?> =====");

        List<String> stringList = Arrays.asList("a", "b", "c");
        List<Integer> integerList = Arrays.asList(1, 2, 3);

        // 使用无界通配符的方法可以接受任何类型的列表
        System.out.println("字符串列表是否为空: " + isEmptyList(stringList));
        System.out.println("整数列表是否为空: " + isEmptyList(integerList));

        // 打印任何类型的列表
        System.out.println("打印字符串列表:");
        printList(stringList);

        System.out.println("打印整数列表:");
        printList(integerList);

        // 无界通配符的限制：不能添加除 null 以外的元素
        List<?> wildcardList = new ArrayList<>(stringList);
        // wildcardList.add("d"); // 编译错误
        wildcardList.add(null); // 允许添加 null

        System.out.println("添加 null 后的通配符列表: " + wildcardList);
    }

    /**
     * 使用无界通配符的方法，可以接受任何类型的列表
     */
    private static boolean isEmptyList(List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * 使用无界通配符打印列表内容
     */
    private static void printList(List<?> list) {
        for (Object item : list) {
            System.out.println(item);
        }
    }

    /**
     * 演示泛型方法
     * 泛型方法可以独立于类的泛型参数
     */
    private static void demonstrateGenericMethods() {
        System.out.println("===== 泛型方法 =====");

        // 使用泛型方法
        String[] stringArray = {"a", "b", "c"};
        List<String> stringList = arrayToList(stringArray);
        System.out.println("字符串数组转列表: " + stringList);

        Integer[] intArray = {1, 2, 3};
        List<Integer> intList = arrayToList(intArray);
        System.out.println("整数数组转列表: " + intList);

        // 泛型方法可以进行类型推断
        Map<String, Integer> map = createMap("key1", 1);
        System.out.println("创建的映射: " + map);

        // 显式指定类型参数
        List<Double> doubleList = GenericPitfallsDemo.<Double>createEmptyList();
        doubleList.add(1.1);
        doubleList.add(2.2);
        System.out.println("创建的空列表: " + doubleList);
    }

    /**
     * 泛型方法：将数组转换为列表
     */
    private static <T> List<T> arrayToList(T[] array) {
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * 泛型方法：创建键值对映射
     */
    private static <K, V> Map<K, V> createMap(K key, V value) {
        Map<K, V> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * 泛型方法：创建空列表
     */
    private static <E> List<E> createEmptyList() {
        return new ArrayList<>();
    }
}
