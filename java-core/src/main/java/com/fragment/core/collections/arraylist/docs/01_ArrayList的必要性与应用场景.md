# 第一章：ArrayList 的必要性与应用场景

## 1.1 为什么需要 ArrayList？

数组（`int[]`）是 Java 最基础的线性结构，但有一个致命缺陷：**长度固定**。

```java
// 数组的问题：长度在创建时就定死了
int[] arr = new int[10];
// 要存第11个？没办法，只能手动创建更大的数组并拷贝
int[] bigger = new int[20];
System.arraycopy(arr, 0, bigger, 0, arr.length);
// 这段样板代码在每个需要动态存储的地方都要重写一遍
```

ArrayList 把这个"动态扩容"的逻辑封装起来，对外提供 `add/remove/get` 等语义清晰的操作，内部自动管理数组的扩容与拷贝。

---

## 1.2 ArrayList 的核心特征

```
底层结构：Object[] elementData（连续内存）
随机访问：O(1) —— 直接数组索引
末尾追加：O(1) 均摊 —— 偶尔触发扩容
中间插入：O(n) —— 需要移动后续元素
按值查找：O(n) —— 线性扫描
```

**与 LinkedList 的一句话区别**：
- `ArrayList`：内存连续，随机访问快，中间插删慢
- `LinkedList`：内存离散，随机访问慢，头尾插删快，但缓存不友好

---

## 1.3 最适合 ArrayList 的场景

**场景一：随机访问频繁**

```java
// 电商商品列表翻页：根据索引取第 N 页
List<Product> products = loadAllProducts();
int pageSize = 20;
int page = 3;
List<Product> pageData = products.subList(page * pageSize, (page + 1) * pageSize);
// O(1) 随机访问，ArrayList 远优于 LinkedList
```

**场景二：数据量可预估，一次写入多次读取**

```java
// 配置加载：启动时读取，之后只读
List<String> config = new ArrayList<>(50);  // 预设容量，避免扩容
loadConfigLines(config);
config.trimToSize();  // 裁剪多余空间
// 之后只调用 get() 读取
```

**场景三：需要与 Stream API 配合做数据处理**

```java
// StudentGradeManager.java → printSortedByScore()
students.stream()
    .sorted(Comparator.comparingDouble(Student::getScore).reversed())
    .filter(s -> s.getScore() >= 60)
    .forEach(System.out::println);
```

---

## 1.4 不适合 ArrayList 的场景

```java
// ❌ 头部频繁插入：每次 add(0, x) 都要把所有元素右移 O(n)
List<String> log = new ArrayList<>();
for (int i = 0; i < 10000; i++) {
    log.add(0, "新日志");  // 极慢！用 LinkedList 或 ArrayDeque
}

// ❌ 多线程并发写：ArrayList 非线程安全
// 解决：Collections.synchronizedList() 或 CopyOnWriteArrayList（见 ArrayListConcurrentDemo）

// ❌ 存储大量基本类型：int/long 会自动装箱为 Integer/Long，内存翻倍
// 解决：用 int[]、IntStream，或第三方 IntArrayList（如 Eclipse Collections）
```

---

## 1.5 本章总结

- **ArrayList 本质**：对 `Object[]` 的动态封装，自动扩容（1.5 倍）
- **首选场景**：随机访问、顺序遍历、数据量可预估的列表
- **避免场景**：头部高频插入、多线程并发写、大量基本类型存储

> **本章对应演示代码**：`ArrayListBasicDemo.java`（增删改查、遍历、subList）

**继续阅读**：[02_ArrayList核心原理与扩容机制.md](./02_ArrayList核心原理与扩容机制.md)
