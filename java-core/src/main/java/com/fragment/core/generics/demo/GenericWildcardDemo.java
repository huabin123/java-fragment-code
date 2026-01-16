package com.fragment.core.generics.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * 泛型通配符示例：演示 <? extends T> 和 <? super T> 的使用场景和限制
 */
public class GenericWildcardDemo {

    public static void main(String[] args) {
        // 演示 <? extends T> 通配符
        demonstrateExtendsWildcard();

        System.out.println("\n-----------------------------------------\n");

        // 演示 <? super T> 通配符
        demonstrateSuperWildcard();

        System.out.println("\n-----------------------------------------\n");

        // 演示类型安全转换
        demonstrateTypeSafety();
    }

    /**
     * 演示 <? extends T> 通配符的使用
     * - 可以读取元素（读取出的元素类型是 T 或 T 的子类）
     * - 不能添加元素（除了 null）
     */
    private static void demonstrateExtendsWildcard() {
        System.out.println("演示 <? extends T> 通配符:");

        // 创建具体类型的列表
        List<Integer> integers = new ArrayList<>();
        integers.add(1);
        integers.add(2);
        integers.add(3);

        // 使用 <? extends Number> 通配符引用列表
        // Number 是 Integer 的父类
        List<? extends Number> numbers = integers;

        // 读取操作是安全的
        Number firstNumber = numbers.get(0);
        System.out.println("读取元素: " + firstNumber);

        // 遍历所有元素
        System.out.println("遍历所有元素:");
        for (Number number : numbers) {
            System.out.println(number);
        }

        // 以下添加操作将导致编译错误
        // numbers.add(Integer.valueOf(4));  // 编译错误
        // numbers.add(Double.valueOf(5.0)); // 编译错误
        // numbers.add(new Number() {...});  // 编译错误

        // 只能添加 null
        // numbers.add(null); // 这是允许的，但不推荐

        System.out.println("使用 <? extends T> 的列表不能添加元素，因为无法确保类型安全");
    }

    /**
     * 演示 <? super T> 通配符的使用
     * - 可以添加 T 类型或 T 的子类型的元素
     * - 读取出的元素只能作为 Object 类型
     */
    private static void demonstrateSuperWildcard() {
        System.out.println("演示 <? super T> 通配符:");

        // 创建 Number 类型的列表
        List<Number> numberList = new ArrayList<>();
        numberList.add(1);
        numberList.add(2.0);

        // 使用 <? super Integer> 通配符引用列表
        // Number 是 Integer 的父类
        List<? super Integer> integerConsumer = numberList;

        // 可以添加 Integer 或其子类型的元素
        integerConsumer.add(Integer.valueOf(3));
        integerConsumer.add(Integer.valueOf(4));

        // 以下添加操作是合法的
        // integerConsumer.add(new Integer(5)); // 已弃用但合法

        // 以下添加操作将导致编译错误
        // integerConsumer.add(Double.valueOf(6.0)); // 编译错误，Double 不是 Integer 的子类

        // 读取操作只能得到 Object 类型
        Object obj = integerConsumer.get(0);
        System.out.println("读取元素 (只能作为 Object): " + obj);

        // 无法直接获取为 Integer 或 Number
        // Integer i = integerConsumer.get(0); // 编译错误
        // Number n = integerConsumer.get(0);  // 编译错误

        // 需要手动转换类型
        if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            System.out.println("转换为 Integer 后: " + i);
        }

        System.out.println("使用 <? super T> 的列表可以添加 T 或 T 的子类型的元素，但读取时只能作为 Object");
    }

    /**
     * 演示在无泛型限制的集合赋值给泛型限制的集合时的类型安全问题
     */
    private static void demonstrateTypeSafety() {
        System.out.println("演示类型安全转换:");

        // 创建无泛型限制的列表
        List rawList = new ArrayList();
        rawList.add("字符串");
        rawList.add(123);
        rawList.add(45.67);

        // 将无泛型限制的列表赋值给有泛型限制的列表
        // 这在编译时会产生警告，但不会报错
        List<String> stringList = rawList; // 不安全的操作

        // 遍历时需要进行类型检查
        System.out.println("安全遍历元素:");
        for (Object item : stringList) {
            if (item instanceof String) {
                String str = (String) item;
                System.out.println("字符串: " + str);
            } else {
                System.out.println("非字符串类型: " + item + " (" + item.getClass().getSimpleName() + ")");
            }
        }

        // 不进行检查直接使用会导致运行时异常
        try {
            System.out.println("\n尝试直接使用第二个元素作为字符串:");
            String secondItem = stringList.get(1); // 运行时会抛出 ClassCastException
            System.out.println(secondItem);
        } catch (ClassCastException e) {
            System.out.println("发生类型转换异常: " + e.getMessage());
        }
    }
}
