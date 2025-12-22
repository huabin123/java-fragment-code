package com.fragment.core.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;

/**
 * 泛型反射演示
 */
public class GenericReflectionDemo {

    // 用于演示的泛型字段
    private List<String> stringList;
    private Map<String, Integer> stringIntMap;
    private List<? extends Number> numberList;
    private Map<String, ? super Integer> wildcardMap;

    public static void main(String[] args) throws Exception {
        System.out.println("=== 泛型反射演示 ===\n");

        demonstrateFieldGenericTypes();
        demonstrateMethodGenericTypes();
        demonstrateClassTypeParameters();
        demonstrateWildcardTypes();
    }

    /**
     * 演示字段的泛型类型获取
     */
    private static void demonstrateFieldGenericTypes() throws Exception {
        System.out.println("1. 字段泛型类型:");

        Class<?> clazz = GenericReflectionDemo.class;

        // List<String>
        Field stringListField = clazz.getDeclaredField("stringList");
        Type genericType1 = stringListField.getGenericType();
        System.out.println("stringList 字段类型: " + genericType1);
        
        if (genericType1 instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType1;
            System.out.println("  原始类型: " + pt.getRawType());
            Type[] actualTypes = pt.getActualTypeArguments();
            System.out.println("  类型参数: " + actualTypes[0]);
        }

        // Map<String, Integer>
        Field mapField = clazz.getDeclaredField("stringIntMap");
        Type genericType2 = mapField.getGenericType();
        System.out.println("\nstringIntMap 字段类型: " + genericType2);
        
        if (genericType2 instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType2;
            Type[] actualTypes = pt.getActualTypeArguments();
            System.out.println("  键类型: " + actualTypes[0]);
            System.out.println("  值类型: " + actualTypes[1]);
        }
        System.out.println();
    }

    /**
     * 演示方法的泛型类型
     */
    private static void demonstrateMethodGenericTypes() throws Exception {
        System.out.println("2. 方法泛型类型:");

        Class<?> clazz = GenericContainer.class;

        // 泛型方法
        Method addMethod = clazz.getMethod("add", Object.class);
        Type[] paramTypes = addMethod.getGenericParameterTypes();
        System.out.println("add 方法参数类型: " + paramTypes[0]);

        Type returnType = addMethod.getGenericReturnType();
        System.out.println("add 方法返回类型: " + returnType);

        // 获取方法的类型变量
        TypeVariable<?>[] typeParameters = addMethod.getTypeParameters();
        if (typeParameters.length > 0) {
            System.out.println("方法类型参数: " + typeParameters[0].getName());
        }
        System.out.println();
    }

    /**
     * 演示类的类型参数
     */
    private static void demonstrateClassTypeParameters() throws Exception {
        System.out.println("3. 类的类型参数:");

        Class<?> clazz = GenericContainer.class;
        TypeVariable<?>[] typeParameters = clazz.getTypeParameters();
        
        for (TypeVariable<?> typeParam : typeParameters) {
            System.out.println("类型参数名: " + typeParam.getName());
            Type[] bounds = typeParam.getBounds();
            System.out.println("类型边界: ");
            for (Type bound : bounds) {
                System.out.println("  " + bound);
            }
        }
        System.out.println();
    }

    /**
     * 演示通配符类型
     */
    private static void demonstrateWildcardTypes() throws Exception {
        System.out.println("4. 通配符类型:");

        Class<?> clazz = GenericReflectionDemo.class;

        // List<? extends Number>
        Field numberListField = clazz.getDeclaredField("numberList");
        Type genericType = numberListField.getGenericType();
        
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] actualTypes = pt.getActualTypeArguments();
            Type wildcardType = actualTypes[0];
            
            if (wildcardType instanceof WildcardType) {
                WildcardType wt = (WildcardType) wildcardType;
                System.out.println("numberList 通配符类型: " + wt);
                System.out.println("  上界: " + java.util.Arrays.toString(wt.getUpperBounds()));
                System.out.println("  下界: " + java.util.Arrays.toString(wt.getLowerBounds()));
            }
        }

        // Map<String, ? super Integer>
        Field wildcardMapField = clazz.getDeclaredField("wildcardMap");
        Type wildcardMapType = wildcardMapField.getGenericType();
        
        if (wildcardMapType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) wildcardMapType;
            Type[] actualTypes = pt.getActualTypeArguments();
            Type valueType = actualTypes[1];
            
            if (valueType instanceof WildcardType) {
                WildcardType wt = (WildcardType) valueType;
                System.out.println("\nwildcardMap 值类型: " + wt);
                System.out.println("  上界: " + java.util.Arrays.toString(wt.getUpperBounds()));
                System.out.println("  下界: " + java.util.Arrays.toString(wt.getLowerBounds()));
            }
        }
        System.out.println();
    }

    /**
     * 用于演示的泛型容器类
     */
    static class GenericContainer<T extends Comparable<T>> {
        private T item;

        public <U> U add(U element) {
            return element;
        }

        public T getItem() {
            return item;
        }

        public void setItem(T item) {
            this.item = item;
        }
    }
}
