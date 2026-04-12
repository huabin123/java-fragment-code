# 第二章：static 关键字详解

## 2.1 static 的本质：属于类，而不是实例

`static` 修饰的成员在类加载时初始化，存储在方法区（JDK 8+ 是元空间），所有实例共享同一份。

```java
// StaticDemo.java 的核心演示
public class Counter {
    private static int count = 0;  // 静态字段：所有实例共享
    private int id;                 // 实例字段：每个实例独有

    public Counter() {
        count++;         // 每创建一个实例，静态计数器+1
        this.id = count; // 实例字段记录自己的编号
    }

    public static int getCount() { return count; }  // 静态方法
    public int getId() { return id; }               // 实例方法
}

Counter a = new Counter();  // count=1, a.id=1
Counter b = new Counter();  // count=2, b.id=2
System.out.println(Counter.getCount());  // 2（类名调用静态方法）
System.out.println(a.id);  // 1（实例字段独立）
```

---

## 2.2 静态字段的四种典型用法

### 用途一：全局常量（static final）

```java
public class MathConstants {
    public static final double PI = 3.14159265358979;
    public static final int MAX_RETRY = 3;
    public static final String ENCODING = "UTF-8";
}
// 通过类名直接访问，无需创建实例
double area = MathConstants.PI * r * r;
```

### 用途二：计数器/共享状态

```java
// StaticDemo.java → Counter 类
// 实例计数、自增 ID 生成、连接池大小等
private static final AtomicInteger idGenerator = new AtomicInteger(0);
public static int nextId() { return idGenerator.incrementAndGet(); }
```

### 用途三：单例持有实例

```java
// 懒汉式单例（线程不安全，仅作示例）
public class Singleton {
    private static Singleton instance;

    private Singleton() {}  // 私有构造函数

    public static Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}

// 线程安全的静态内部类方式（推荐）
public class Singleton {
    private static class Holder {
        private static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```

### 用途四：工具方法集合（无状态工具类）

```java
// 所有方法都是静态的，不需要实例化
public final class StringUtils {
    private StringUtils() {}  // 防止实例化

    public static boolean isEmpty(String s) { return s == null || s.isEmpty(); }
    public static String trim(String s) { return s == null ? null : s.trim(); }
}
```

---

## 2.3 静态初始化块

```java
// StaticDemo.java → 静态初始化块演示
public class DatabaseConfig {
    private static final Map<String, String> CONFIG = new HashMap<>();

    // 静态代码块：类加载时执行一次，用于复杂初始化
    static {
        System.out.println("加载数据库配置...");
        CONFIG.put("url", "jdbc:mysql://localhost:3306/mydb");
        CONFIG.put("username", "root");
        CONFIG.put("maxPoolSize", "10");
        // 可以有 try-catch，做异常处理
    }

    public static String get(String key) { return CONFIG.get(key); }
}
```

**执行顺序**：父类静态块 → 子类静态块 → 父类实例初始化 → 子类实例初始化

---

## 2.4 static 的关键限制

### 静态方法不能直接访问实例成员

```java
public class Example {
    private int instanceVar = 10;
    private static int staticVar = 20;

    public static void staticMethod() {
        System.out.println(staticVar);    // ✅ 静态方法可访问静态字段
        // System.out.println(instanceVar);  // ❌ 编译错误：需要通过实例
        // System.out.println(this.instanceVar);  // ❌ 静态方法中没有 this

        // ✅ 通过创建实例访问
        Example e = new Example();
        System.out.println(e.instanceVar);
    }
}
```

### 接口中的 static 方法不能被 override

```java
interface Printable {
    static void defaultPrint(String msg) { System.out.println(msg); }
}

class MyPrint implements Printable {
    // 这不是 override，是隐藏（hiding）！
    public static void defaultPrint(String msg) { System.out.println("MY: " + msg); }
}

Printable.defaultPrint("test");  // 调用接口的方法，不会多态分发
```

### `StaticVariableInInstanceMethodDemo.java`：静态变量在实例方法中的陷阱

```java
// StaticVariableInInstanceMethodDemo.java 演示的核心问题
public class OrderProcessor {
    private static int processedCount = 0;  // 静态字段，所有实例共享

    public void process(Order order) {
        processedCount++;  // 看起来像实例状态，实际是全局状态！
        // 多线程下：多个线程同时调用 process()，processedCount++ 非原子操作
        // 线程1读取 count=5，线程2读取 count=5，两个都写回 6，丢失一次更新
    }
}

// ✅ 修复：改为原子类型
private static final AtomicInteger processedCount = new AtomicInteger(0);
// 或者改为实例字段（如果每个处理器应该独立计数）
private int processedCount = 0;
```

---

## 2.5 static 与继承

```java
// CombinedModifiersDemo.java 中的静态继承行为
class Animal {
    static String type = "动物";
    static void describe() { System.out.println("我是: " + type); }
}

class Dog extends Animal {
    static String type = "狗";  // 隐藏（hiding），不是 override！
    static void describe() { System.out.println("我是: " + type); }
}

Animal.describe();  // 我是: 动物
Dog.describe();     // 我是: 狗

Animal a = new Dog();
a.describe();       // 我是: 动物 ← 静态方法不参与多态！
```

**核心区别**：实例方法根据运行时类型分派（多态），静态方法根据**编译时类型**决定，不参与多态。

---

## 2.6 静态导入（import static）

```java
// 避免重复写类名
import static java.lang.Math.*;
import static java.util.Collections.*;

double area = PI * r * r;   // 无需 Math.PI
sort(list);                  // 无需 Collections.sort
unmodifiableList(list);      // 无需 Collections.unmodifiableList
```

**使用原则**：对于高频使用的常量/工具方法（如 Math.PI、Assert.assertEquals），静态导入提升可读性；滥用会导致代码难以溯源。

---

## 2.7 本章总结

- **本质**：属于类，不属于实例；类加载时初始化，所有实例共享
- **四种用途**：全局常量（final + static）、计数器/共享状态、单例、无状态工具类
- **静态初始化块**：类加载时执行一次，处理复杂初始化逻辑
- **关键限制**：静态方法无 this，无法直接访问实例成员；静态方法不参与多态
- **并发陷阱**：静态字段是共享状态，多线程访问需要同步（AtomicXxx 或 synchronized）

> **本章对应演示代码**：`StaticDemo.java`（静态字段/方法/块）、`StaticVariableInInstanceMethodDemo.java`（并发陷阱）、`CombinedModifiersDemo.java`（静态与继承）

**继续阅读**：[03_final关键字详解.md](./03_final关键字详解.md)
