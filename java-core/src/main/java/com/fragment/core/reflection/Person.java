package com.fragment.core.reflection;

import java.util.List;

/**
 * 用于反射示例的简单 POJO 类
 */
public class Person {
    private String name;
    private int age;
    private boolean active;
    public String email;
    private static String species = "Homo sapiens";
    private List<String> hobbies;

    // 默认构造器
    public Person() {
    }

    // 带参构造器
    public Person(String name, int age) {
        this.name = name;
        this.age = age;
        this.active = true;
    }

    // 私有构造器
    private Person(String name, int age, boolean active, String email) {
        this.name = name;
        this.age = age;
        this.active = active;
        this.email = email;
    }

    // Getter 和 Setter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getHobbies() {
        return hobbies;
    }

    public void setHobbies(List<String> hobbies) {
        this.hobbies = hobbies;
    }

    // 静态方法
    public static String getSpecies() {
        return species;
    }

    // 私有方法
    private void privateMethod(String message) {
        System.out.println("Private method called with: " + message);
    }

    // 重载方法
    public void greet() {
        System.out.println("Hello, I'm " + name);
    }

    public void greet(String greeting) {
        System.out.println(greeting + ", I'm " + name);
    }

    // 抛出异常的方法
    public void validateAge() throws IllegalArgumentException {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative");
        }
    }

    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", active=" + active +
                ", email='" + email + '\'' +
                ", hobbies=" + hobbies +
                '}';
    }
}
