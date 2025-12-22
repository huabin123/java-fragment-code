package com.fragment.core.reflection;

import java.lang.reflect.Array;

/**
 * 数组反射操作演示
 */
public class ArrayReflectionDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 数组反射操作演示 ===\n");

        demonstrateArrayCreation();
        demonstrateArrayAccess();
        demonstrateMultiDimensionalArray();
        demonstrateArrayTypeInfo();
    }

    /**
     * 演示动态创建数组
     */
    private static void demonstrateArrayCreation() throws Exception {
        System.out.println("1. 动态创建数组:");

        // 创建 int 数组
        Object intArray = Array.newInstance(int.class, 5);
        System.out.println("创建 int[5]: " + intArray.getClass().getName());

        // 创建 String 数组
        Object stringArray = Array.newInstance(String.class, 3);
        System.out.println("创建 String[3]: " + stringArray.getClass().getName());

        // 创建自定义类型数组
        Object personArray = Array.newInstance(Person.class, 2);
        System.out.println("创建 Person[2]: " + personArray.getClass().getName());
        System.out.println();
    }

    /**
     * 演示数组元素访问
     */
    private static void demonstrateArrayAccess() throws Exception {
        System.out.println("2. 数组元素访问:");

        // 创建并操作 int 数组
        Object intArray = Array.newInstance(int.class, 5);
        
        // 设置数组元素
        for (int i = 0; i < 5; i++) {
            Array.setInt(intArray, i, i * 10);
        }

        // 读取数组元素
        System.out.println("int 数组内容:");
        for (int i = 0; i < Array.getLength(intArray); i++) {
            int value = Array.getInt(intArray, i);
            System.out.println("  [" + i + "] = " + value);
        }

        // 创建并操作 String 数组
        Object stringArray = Array.newInstance(String.class, 3);
        Array.set(stringArray, 0, "Hello");
        Array.set(stringArray, 1, "World");
        Array.set(stringArray, 2, "Reflection");

        System.out.println("\nString 数组内容:");
        for (int i = 0; i < Array.getLength(stringArray); i++) {
            String value = (String) Array.get(stringArray, i);
            System.out.println("  [" + i + "] = " + value);
        }

        // 创建并操作对象数组
        Object personArray = Array.newInstance(Person.class, 2);
        Array.set(personArray, 0, new Person("张三", 25));
        Array.set(personArray, 1, new Person("李四", 30));

        System.out.println("\nPerson 数组内容:");
        for (int i = 0; i < Array.getLength(personArray); i++) {
            Person person = (Person) Array.get(personArray, i);
            System.out.println("  [" + i + "] = " + person);
        }
        System.out.println();
    }

    /**
     * 演示多维数组
     */
    private static void demonstrateMultiDimensionalArray() throws Exception {
        System.out.println("3. 多维数组:");

        // 创建二维 int 数组
        Object twoDimArray = Array.newInstance(int.class, 3, 4);
        System.out.println("创建 int[3][4]: " + twoDimArray.getClass().getName());

        // 填充二维数组
        for (int i = 0; i < 3; i++) {
            Object row = Array.get(twoDimArray, i);
            for (int j = 0; j < 4; j++) {
                Array.setInt(row, j, i * 4 + j);
            }
        }

        // 读取二维数组
        System.out.println("二维数组内容:");
        for (int i = 0; i < Array.getLength(twoDimArray); i++) {
            Object row = Array.get(twoDimArray, i);
            System.out.print("  [" + i + "]: ");
            for (int j = 0; j < Array.getLength(row); j++) {
                int value = Array.getInt(row, j);
                System.out.print(value + " ");
            }
            System.out.println();
        }

        // 创建不规则数组
        Object jaggedArray = Array.newInstance(int.class, 3);
        Array.set(jaggedArray, 0, Array.newInstance(int.class, 2));
        Array.set(jaggedArray, 1, Array.newInstance(int.class, 4));
        Array.set(jaggedArray, 2, Array.newInstance(int.class, 3));

        System.out.println("\n不规则数组维度:");
        for (int i = 0; i < Array.getLength(jaggedArray); i++) {
            Object row = Array.get(jaggedArray, i);
            System.out.println("  行 " + i + " 长度: " + Array.getLength(row));
        }
        System.out.println();
    }

    /**
     * 演示数组类型信息
     */
    private static void demonstrateArrayTypeInfo() throws Exception {
        System.out.println("4. 数组类型信息:");

        // 不同类型的数组
        int[] intArray = new int[5];
        String[] stringArray = new String[3];
        Person[] personArray = new Person[2];
        int[][] twoDimArray = new int[3][4];

        analyzeArrayType("int[]", intArray.getClass());
        analyzeArrayType("String[]", stringArray.getClass());
        analyzeArrayType("Person[]", personArray.getClass());
        analyzeArrayType("int[][]", twoDimArray.getClass());

        // 通过反射创建的数组
        Object reflectionArray = Array.newInstance(double.class, 10);
        analyzeArrayType("double[] (reflection)", reflectionArray.getClass());
        System.out.println();
    }

    /**
     * 分析数组类型信息
     */
    private static void analyzeArrayType(String description, Class<?> arrayClass) {
        System.out.println(description + ":");
        System.out.println("  类名: " + arrayClass.getName());
        System.out.println("  是否为数组: " + arrayClass.isArray());
        
        if (arrayClass.isArray()) {
            Class<?> componentType = arrayClass.getComponentType();
            System.out.println("  组件类型: " + componentType.getName());
            System.out.println("  组件类型是否为数组: " + componentType.isArray());
        }
        System.out.println();
    }
}
