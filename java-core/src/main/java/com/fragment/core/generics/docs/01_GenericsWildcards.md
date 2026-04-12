# 第一章：泛型通配符与 PECS 原则

## 1.1 问题的起点：为什么需要通配符？

泛型在 Java 中是不协变的（invariant）：`List<Dog>` 不是 `List<Animal>` 的子类型，即使 `Dog extends Animal`。

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;  // ❌ 编译错误！
```

这个限制是正确的——如果允许赋值，就可以往 `dogs`（实际是 `List<Dog>`）里放 `Cat`，破坏类型安全。

但这带来了新问题：`printAnimals(List<Animal> list)` 无法接收 `List<Dog>`，尽管我们只是想读取元素打印。通配符解决了这个矛盾。

---

## 1.2 上界通配符 `<? extends T>`：Producer（只读）

```java
// PECSPrincipleDemo.java → printAnimals()
public static void printAnimals(List<? extends Animal> list) {
    for (Animal animal : list) {  // ✅ 可以读取，元素保证是 Animal 或其子类
        System.out.println(animal.getName());
    }
    // list.add(new Dog());  // ❌ 编译错误！无法写入
}

// 调用时接受所有 Animal 的子类型列表
printAnimals(animals);  // ✅
printAnimals(dogs);     // ✅
printAnimals(huskies);  // ✅
```

**为什么不能写入？** 假设 `list` 实际是 `List<Husky>`，写入一个 `Dog` 就破坏了 Husky 的类型约束。编译器无法在编译期确定 `?` 的具体类型，因此拒绝任何写入（null 除外）。

**记忆法**：`extends`（上界） → **Producer**（生产数据供读取）→ **只读**

---

## 1.3 下界通配符 `<? super T>`：Consumer（只写）

```java
// PECSPrincipleDemo.java → addHuskies()
public static void addHuskies(List<? super Husky> list) {
    list.add(new Husky("哈士奇"));  // ✅ 可以写入，Husky 一定兼容 list 的实际类型
    // Husky h = list.get(0);  // ❌ 只能读出 Object 类型，失去了类型信息
}

// 调用时接受所有 Husky 的父类型列表
addHuskies(dogList);     // ✅ List<Dog>，Dog 是 Husky 的父类
addHuskies(animalList);  // ✅ List<Animal>，Animal 是 Husky 的父类
// addHuskies(huskyOnlyList);  // ❌ 如果 list 是 List<Cat>（Cat 是 Husky 的父类？不是），无法保证
```

**为什么不能安全读取类型化元素？** 假设 `list` 是 `List<Animal>`，读出来的可能是 Dog、Cat 等任何 Animal，编译器无法确定具体类型，只能给 `Object`。

**记忆法**：`super`（下界） → **Consumer**（消费数据进行写入）→ **只写**

---

## 1.4 PECS 原则：Producer Extends, Consumer Super

```java
// PECSPrincipleDemo.java → copy()：经典的 PECS 双通配符
public static <T> void copy(List<? super T> dest, List<? extends T> src) {
    for (T item : src) {  // src 是 Producer（只读）→ extends
        dest.add(item);   // dest 是 Consumer（只写）→ super
    }
}

// 使用：把 List<Dog> 复制到 List<Animal>
List<Dog> sourceDogs = Arrays.asList(new Dog("狗1"), new Dog("狗2"));
List<Animal> targetAnimals = new ArrayList<>();
copy(targetAnimals, sourceDogs);  // ✅
```

`Collections.copy(dest, src)` 就是这个签名，JDK 源码中大量使用 PECS。

---

## 1.5 无界通配符 `<?>`

```java
// 只关心对象存在，不关心类型时使用
public static void printSize(List<?> list) {
    System.out.println("size: " + list.size());  // ✅
    // list.add(new Object());  // ❌ 无法写入（除 null）
    Object obj = list.get(0);  // ✅ 只能读出 Object
}

// 与 List<Object> 的区别：
List<String> strings = new ArrayList<>();
printSize(strings);  // ✅ List<?> 可以接收

List<Object> objects = strings;  // ❌ List<Object> 无法接收 List<String>
```

---

## 1.6 本章总结

- **泛型不协变**：`List<Dog>` 不是 `List<Animal>` 的子类型，通配符是解决方案
- **`? extends T`（上界）**：Producer，只读，可接受 T 的所有子类型列表
- **`? super T`（下界）**：Consumer，只写，可接受 T 的所有父类型列表
- **PECS 原则**：函数参数是 Producer 用 extends，是 Consumer 用 super
- **`<?>`**：无界通配符，完全只读，等价于 `? extends Object`

> **本章对应演示代码**：`PECSPrincipleDemo.java`（Producer Extends、Consumer Super、copy 双通配符完整演示）

**继续阅读**：[02_GenericsUsageSummary.md](./02_GenericsUsageSummary.md)
