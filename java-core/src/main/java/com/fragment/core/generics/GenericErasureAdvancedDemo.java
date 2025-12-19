package com.fragment.core.generics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.lang.System.out;

/**
 * 泛型擦除进阶示例：
 * 1) 运行时类型相同（擦除）
 * 2) 桥接方法（bridge method）
 * 3) Type Token 与泛型数组创建
 * 4) 通配符捕获（wildcard capture）
 * 5) @SafeVarargs 避免非具体化类型的 varargs 警告
 */
public class GenericErasureAdvancedDemo {

    public static void main(String[] args) throws Exception {
        demonstrateRuntimeTypeEquality();
        separator();
        demonstrateBridgeMethod();
        separator();
        demonstrateTypeTokenAndArrayCreation();
        separator();
        demonstrateWildcardCapture();
        separator();
        demonstrateSafeVarargs();
    }

    private static void demonstrateRuntimeTypeEquality() {
        out.println("===== 1) 运行时类型相同（类型擦除） =====");
        List<String> sl = new ArrayList<>();
        List<Integer> il = new ArrayList<>();
        out.println("sl.getClass() == il.getClass()? " + (sl.getClass() == il.getClass()));
        out.println("实际运行时类型：" + sl.getClass().getName());

        // 说明：无法使用参数化类型做 instanceof（以下为编译错误的示意）
        out.println("无法编译的示例：// if (sl instanceof List<String>) {}");
    }

    // 2) 桥接方法示例 ---------------------------------------
    private static void demonstrateBridgeMethod() throws Exception {
        out.println("===== 2) 桥接方法（bridge method）=====");
        class BaseBox<T> {
            private final T value;
            BaseBox(T value) { this.value = value; }
            public T get() { return value; }
        }
        class StringBox extends BaseBox<String> {
            StringBox(String value) { super(value); }
            @Override public String get() { return super.get(); }
        }

        StringBox box = new StringBox("hello");
        out.println("StringBox.get(): " + box.get());

        // 反射查看桥接方法：通常会看到两个名为 get 的方法，其中一个是 bridge/synthetic
        for (Method m : StringBox.class.getDeclaredMethods()) {
            if (m.getName().equals("get")) {
                out.println("method: " + m + ", isBridge=" + m.isBridge() + ", isSynthetic=" + m.isSynthetic());
            }
        }
    }

    // 3) Type Token 与泛型数组创建 ----------------------------
    private static void demonstrateTypeTokenAndArrayCreation() {
        out.println("===== 3) Type Token 与泛型数组创建 =====");

        // 通过 Class<T> 作为类型令牌创建实例
        Person p = create(Person::new);
        out.println("create(Supplier): " + p);

        // 通过 Class<T> + Array.newInstance 创建泛型数组
        String[] names = newArray(String.class, 3);
        names[0] = "Alice"; names[1] = "Bob"; names[2] = "Carol";
        out.println("newArray(String.class,3): " + Arrays.toString(names));

        // 无法直接 new T[] / T.class（编译期会报错，这里仅注释说明）
        out.println("无法编译的示例：// T[] arr = new T[10]; // Class<T> c = T.class");
    }

    private static <T> T create(Supplier<T> factory) { return factory.get(); }

    @SuppressWarnings("unchecked")
    private static <T> T[] newArray(Class<T> componentType, int len) {
        return (T[]) java.lang.reflect.Array.newInstance(componentType, len);
    }

    static class Person {
        private String name = "Anonymous";
        @Override public String toString() { return "Person{" + name + '}'; }
    }

    // 4) 通配符捕获（wildcard capture） ------------------------
    private static void demonstrateWildcardCapture() {
        out.println("===== 4) 通配符捕获（wildcard capture） =====");
        List<Integer> ints = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
        out.println("before reverse: " + ints);
        reverse(ints); // 传入的是 List<Integer>，方法签名却是 List<?>
        out.println("after reverse:  " + ints);
    }

    public static void reverse(List<?> list) {
        // 不能直接对 List<?> 进行写操作，但可通过私有泛型方法捕获通配符类型
        reverseHelper(list);
    }

    private static <T> void reverseHelper(List<T> list) {
        Collections.reverse(list);
    }

    // 5) @SafeVarargs 与 varargs 泛型 ---------------------------
    private static void demonstrateSafeVarargs() {
        out.println("===== 5) @SafeVarargs 与 varargs 泛型 =====");
        List<Integer> a = Arrays.asList(1, 2);
        List<Integer> b = Arrays.asList(3, 4);
        List<Integer> c = Arrays.asList(5, 6);
        List<Integer> flat = flatten(a, b, c); // 无警告（方法已声明 @SafeVarargs）
        out.println("flatten: " + flat);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> List<T> flatten(List<? extends T>... lists) {
        List<T> result = new ArrayList<>();
        for (List<? extends T> l : lists) {
            result.addAll(l);
        }
        return result;
    }

    private static void separator() {
        out.println("\n-----------------------------------------\n");
    }
}
