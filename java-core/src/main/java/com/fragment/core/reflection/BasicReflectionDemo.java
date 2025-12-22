package com.fragment.core.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 基础反射操作演示
 */
public class BasicReflectionDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 基础反射操作演示 ===\n");

        demonstrateClassLoading();
        demonstrateFieldAccess();
        demonstrateMethodInvocation();
        demonstrateConstructorAccess();
        demonstrateModifiers();
    }

    /**
     * 演示 Class 对象的获取方式
     */
    private static void demonstrateClassLoading() throws Exception {
        System.out.println("1. Class 对象获取方式:");

        // 方式1: 通过 .class 语法
        Class<?> clazz1 = Person.class;
        System.out.println("通过 .class: " + clazz1.getName());

        // 方式2: 通过 Class.forName()
        Class<?> clazz2 = Class.forName("com.fragment.core.reflection.Person");
        System.out.println("通过 Class.forName(): " + clazz2.getName());

        // 方式3: 通过对象的 getClass()
        Person person = new Person();
        Class<?> clazz3 = person.getClass();
        System.out.println("通过 getClass(): " + clazz3.getName());

        // 验证是否为同一个 Class 对象
        System.out.println("三种方式获取的是同一个对象: " + (clazz1 == clazz2 && clazz2 == clazz3));

        // 获取类的基本信息
        System.out.println("类名: " + clazz1.getSimpleName());
        System.out.println("包名: " + clazz1.getPackage().getName());
        System.out.println("父类: " + clazz1.getSuperclass().getName());
        System.out.println();
    }

    /**
     * 演示字段访问
     */
    private static void demonstrateFieldAccess() throws Exception {
        System.out.println("2. 字段访问:");

        Class<?> clazz = Person.class;
        Person person = new Person("张三", 25);

        // 获取所有字段（包括私有字段）
        Field[] allFields = clazz.getDeclaredFields();
        System.out.println("所有字段数量: " + allFields.length);

        for (Field field : allFields) {
            System.out.println("字段: " + field.getName() + ", 类型: " + field.getType().getSimpleName());
        }

        // 访问私有字段
        Field nameField = clazz.getDeclaredField("name");
        nameField.setAccessible(true); // 绕过访问控制
        String name = (String) nameField.get(person);
        System.out.println("通过反射获取 name: " + name);

        // 修改私有字段
        nameField.set(person, "李四");
        System.out.println("修改后的 name: " + nameField.get(person));

        // 访问公共字段
        Field emailField = clazz.getDeclaredField("email");
        emailField.set(person, "lisi@example.com");
        System.out.println("设置 email: " + person.email);

        // 访问静态字段
        Field speciesField = clazz.getDeclaredField("species");
        speciesField.setAccessible(true);
        String species = (String) speciesField.get(null); // 静态字段传入 null
        System.out.println("静态字段 species: " + species);
        System.out.println();
    }

    /**
     * 演示方法调用
     */
    private static void demonstrateMethodInvocation() throws Exception {
        System.out.println("3. 方法调用:");

        Class<?> clazz = Person.class;
        Person person = new Person("王五", 30);

        // 调用公共方法
        Method greetMethod = clazz.getMethod("greet");
        greetMethod.invoke(person);

        // 调用带参数的重载方法
        Method greetWithParamMethod = clazz.getMethod("greet", String.class);
        greetWithParamMethod.invoke(person, "你好");

        // 调用私有方法
        Method privateMethod = clazz.getDeclaredMethod("privateMethod", String.class);
        privateMethod.setAccessible(true);
        privateMethod.invoke(person, "这是私有方法调用");

        // 调用静态方法
        Method staticMethod = clazz.getMethod("getSpecies");
        String result = (String) staticMethod.invoke(null);
        System.out.println("静态方法返回: " + result);

        // 获取所有方法
        Method[] methods = clazz.getDeclaredMethods();
        System.out.println("类中定义的方法数量: " + methods.length);
        System.out.println();
    }

    /**
     * 演示构造器访问
     */
    private static void demonstrateConstructorAccess() throws Exception {
        System.out.println("4. 构造器访问:");

        Class<?> clazz = Person.class;

        // 获取默认构造器
        Constructor<?> defaultConstructor = clazz.getConstructor();
        Person person1 = (Person) defaultConstructor.newInstance();
        System.out.println("默认构造器创建: " + person1);

        // 获取带参构造器
        Constructor<?> paramConstructor = clazz.getConstructor(String.class, int.class);
        Person person2 = (Person) paramConstructor.newInstance("赵六", 28);
        System.out.println("带参构造器创建: " + person2);

        // 访问私有构造器
        Constructor<?> privateConstructor = clazz.getDeclaredConstructor(
                String.class, int.class, boolean.class, String.class);
        privateConstructor.setAccessible(true);
        Person person3 = (Person) privateConstructor.newInstance("孙七", 35, true, "sun@example.com");
        System.out.println("私有构造器创建: " + person3);

        // 获取所有构造器
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        System.out.println("构造器数量: " + constructors.length);
        System.out.println();
    }

    /**
     * 演示修饰符检查
     */
    private static void demonstrateModifiers() throws Exception {
        System.out.println("5. 修饰符检查:");

        Class<?> clazz = Person.class;

        // 检查类的修饰符
        int classModifiers = clazz.getModifiers();
        System.out.println("类是否为 public: " + Modifier.isPublic(classModifiers));
        System.out.println("类是否为 abstract: " + Modifier.isAbstract(classModifiers));
        System.out.println("类是否为 final: " + Modifier.isFinal(classModifiers));

        // 检查字段的修饰符
        Field nameField = clazz.getDeclaredField("name");
        int fieldModifiers = nameField.getModifiers();
        System.out.println("name 字段是否为 private: " + Modifier.isPrivate(fieldModifiers));
        System.out.println("name 字段是否为 static: " + Modifier.isStatic(fieldModifiers));

        Field speciesField = clazz.getDeclaredField("species");
        int staticFieldModifiers = speciesField.getModifiers();
        System.out.println("species 字段是否为 static: " + Modifier.isStatic(staticFieldModifiers));

        // 检查方法的修饰符
        Method greetMethod = clazz.getMethod("greet");
        int methodModifiers = greetMethod.getModifiers();
        System.out.println("greet 方法是否为 public: " + Modifier.isPublic(methodModifiers));

        Method privateMethod = clazz.getDeclaredMethod("privateMethod", String.class);
        int privateMethodModifiers = privateMethod.getModifiers();
        System.out.println("privateMethod 是否为 private: " + Modifier.isPrivate(privateMethodModifiers));
        System.out.println();
    }
}
