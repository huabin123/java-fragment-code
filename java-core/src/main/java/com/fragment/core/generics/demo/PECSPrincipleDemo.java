package com.fragment.core.generics.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PECS 原则演示：Producer Extends, Consumer Super
 * - Producer Extends: 使用 <? extends T> 从集合中读取数据
 * - Consumer Super: 使用 <? super T> 向集合中写入数据
 */
public class PECSPrincipleDemo {

    public static void main(String[] args) {
        // 创建不同层次的动物列表
        List<Animal> animals = Arrays.asList(new Animal("动物1"), new Animal("动物2"));
        List<Dog> dogs = Arrays.asList(new Dog("狗1"), new Dog("狗2"));
        List<Husky> huskies = Arrays.asList(new Husky("哈士奇1"), new Husky("哈士奇2"));

        // 演示 Producer Extends
        System.out.println("===== Producer Extends 示例 =====");
        // 读取动物
        printAnimals(animals);
        // 读取狗 (Dog 是 Animal 的子类)
        printAnimals(dogs);
        // 读取哈士奇 (Husky 是 Dog 的子类，也是 Animal 的子类)
        printAnimals(huskies);

        System.out.println("\n===== Consumer Super 示例 =====");
        // 创建目标列表
        List<Animal> animalList = new ArrayList<>();
        List<Dog> dogList = new ArrayList<>();

        // 添加哈士奇到动物列表 (Animal 是 Husky 的父类)
        addHuskies(animalList);
        System.out.println("添加哈士奇到动物列表:");
        printAnimals(animalList);

        // 添加哈士奇到狗列表 (Dog 是 Husky 的父类)
        addHuskies(dogList);
        System.out.println("\n添加哈士奇到狗列表:");
        printAnimals(dogList);

        // 以下代码会导致编译错误，因为 Husky 不是 Dog 的父类
        // List<Husky> huskyList = new ArrayList<>();
        // addDogs(huskyList); // 编译错误

        System.out.println("\n===== 复制示例 =====");
        // 创建源列表和目标列表
        List<Dog> sourceDogs = Arrays.asList(new Dog("源狗1"), new Dog("源狗2"));
        List<Animal> targetAnimals = new ArrayList<>();

        // 将狗列表复制到动物列表
        copy(targetAnimals, sourceDogs);
        System.out.println("复制后的动物列表:");
        printAnimals(targetAnimals);
    }

    /**
     * Producer Extends: 使用 <? extends Animal> 从集合中读取数据
     * 可以接受 Animal 及其任何子类的集合
     */
    public static void printAnimals(List<? extends Animal> animals) {
        for (Animal animal : animals) {
            System.out.println(animal.getName() + " 发出声音: " + animal.makeSound());
        }
    }

    /**
     * Consumer Super: 使用 <? super Husky> 向集合中写入数据
     * 可以接受 Husky 及其任何父类的集合
     */
    public static void addHuskies(List<? super Husky> list) {
        list.add(new Husky("新哈士奇1"));
        list.add(new Husky("新哈士奇2"));
    }

    /**
     * Consumer Super: 使用 <? super Dog> 向集合中写入数据
     * 可以接受 Dog 及其任何父类的集合
     */
    public static void addDogs(List<? super Dog> list) {
        list.add(new Dog("新狗1"));
        list.add(new Dog("新狗2"));
    }

    /**
     * 结合 Producer Extends 和 Consumer Super 的例子
     * - 从 src 中读取数据，所以 src 使用 <? extends T>
     * - 向 dest 中写入数据，所以 dest 使用 <? super T>
     */
    public static <T> void copy(List<? super T> dest, List<? extends T> src) {
        for (int i = 0; i < src.size(); i++) {
            dest.add(src.get(i));
        }
    }

    // 基类：动物
    static class Animal {
        private String name;

        public Animal(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String makeSound() {
            return "某种动物声音";
        }

        @Override
        public String toString() {
            return "Animal{" + "name='" + name + '\'' + '}';
        }
    }

    // 子类：狗
    static class Dog extends Animal {
        public Dog(String name) {
            super(name);
        }

        @Override
        public String makeSound() {
            return "汪汪";
        }

        @Override
        public String toString() {
            return "Dog{" + "name='" + getName() + '\'' + '}';
        }
    }

    // 子类的子类：哈士奇
    static class Husky extends Dog {
        public Husky(String name) {
            super(name);
        }

        @Override
        public String makeSound() {
            return "嗷呜 (哈士奇特有的声音)";
        }

        @Override
        public String toString() {
            return "Husky{" + "name='" + getName() + '\'' + '}';
        }
    }
}
