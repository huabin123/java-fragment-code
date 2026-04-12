# 第三章：final 关键字详解

## 3.1 final 的三个维度

`final` 在 Java 中有三个截然不同的用途，理解每个维度的"不可变"含义是关键：

| 用于 | 含义 |
|------|------|
| 变量/字段 | 引用（或基本类型值）不可重新赋值 |
| 方法 | 不可被子类 override |
| 类 | 不可被继承 |

---

## 3.2 final 变量：引用不可变，内容可能可变

```java
// FinalDemo.java 的核心演示

// 基本类型：值不可变（真正的常量）
final int MAX_SIZE = 100;
// MAX_SIZE = 200;  // ❌ 编译错误

// 引用类型：引用不可变，但对象内容可以修改！
final List<String> list = new ArrayList<>();
// list = new ArrayList<>();  // ❌ 编译错误：引用不能重新赋值
list.add("hello");  // ✅ 对象内容可以修改！
list.clear();       // ✅

final StringBuilder sb = new StringBuilder("hello");
sb.append(" world");  // ✅ StringBuilder 可以修改
// sb = new StringBuilder();  // ❌ 引用不可变
```

**这是最常见的 final 误解**：`final` 只保证引用不被重新赋值，不保证对象是不可变的（immutable）。真正的不可变对象需要自己实现（如 `String`、`Integer`）。

### final 字段的初始化

```java
public class ImmutableConfig {
    // 方式1：直接初始化
    private final String host = "localhost";

    // 方式2：在构造函数中初始化（每次实例化可以赋不同值）
    private final int port;
    private final String protocol;

    public ImmutableConfig(int port, String protocol) {
        this.port = port;
        this.protocol = protocol;
        // 构造函数执行完之前必须完成所有 final 字段的初始化
    }
    // 注意：不能在普通方法中赋值，只能在构造函数或直接初始化
}
```

### static final：真正的常量

```java
// 编译时常量（基本类型 + String 字面量）
public static final int MAX_CONNECTIONS = 100;
public static final String VERSION = "1.0.0";
// 编译器会将这些常量内联到调用处，修改后需要重新编译所有引用方

// 运行时常量（引用类型或非字面量）
public static final UUID APP_ID = UUID.randomUUID();  // 每次启动不同
public static final List<String> ALLOWED_METHODS = Collections.unmodifiableList(
    Arrays.asList("GET", "POST", "PUT", "DELETE")
);
```

---

## 3.3 final 方法：防止 override

```java
// FinalDemo.java → 模板方法中的 final

public abstract class ReportGenerator {
    // final：模板流程不可被子类破坏
    public final String generate(Data data) {
        String validated = validate(data);
        String formatted = format(validated);  // 子类可以重写这步
        return addHeader(formatted) + addFooter(formatted);
    }

    // final：公共工具方法，不应被子类修改行为
    protected final String addHeader(String content) {
        return "=== REPORT ===\n" + content;
    }

    // 允许子类重写：定制化扩展点
    protected String format(String data) {
        return data;  // 默认实现
    }

    protected abstract String validate(Data data);
}
```

**设计意图**：当你希望某个方法的行为对所有子类保持一致（模板流程、安全关键逻辑）时，用 `final` 防止被误 override。

---

## 3.4 final 类：防止继承

```java
// 典型的 final 类
public final class String { ... }     // 不可变字符串，不允许子类破坏其不变性
public final class Integer { ... }    // 包装类
public final class Math { ... }       // 纯工具类，禁止继承

// 自定义不可继承类
public final class EncryptionUtil {
    private EncryptionUtil() {}  // 配合 private 构造，彻底防止实例化和继承

    public static String encrypt(String data, String key) { ... }
}
```

**何时用 final 类**：
1. **不可变值对象**：防止子类添加可变状态，破坏不变性（String 的设计就是如此）
2. **安全性相关类**：防止子类通过继承绕过安全检查
3. **工具类**：`Math`、各种 `Utils` 类不应该被继承

---

## 3.5 final 与 JVM 内存可见性

`final` 有一个重要但容易忽略的作用：**JVM 保证 final 字段在构造函数完成后对所有线程立即可见**。

```java
// FinalDemo.java → 线程安全的不可变对象
public class ImmutablePoint {
    private final int x;
    private final int y;

    public ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
        // 构造函数完成后，JVM 保证 x 和 y 对所有线程可见
        // 无需 volatile 或 synchronized
    }
}

// 对比：非 final 字段没有这个保证
public class MutablePoint {
    private int x;   // 没有 final
    private int y;

    // 其他线程可能看到半初始化状态（x 已设置，y 还是 0）
}
```

这就是为什么不可变对象（Immutable Object）天生是线程安全的——`final` 字段的初始化写操作有 happens-before 保证。

---

## 3.6 实现真正的不可变类

`FinalDemo.java` 展示了不可变类的完整实现模式：

```java
public final class Money {  // 1. final 类：防止子类破坏不变性
    private final long amount;       // 2. final 字段
    private final String currency;   // 3. final 字段

    public Money(long amount, String currency) {
        // 4. 构造时验证
        if (amount < 0) throw new IllegalArgumentException("金额不能为负");
        this.amount = amount;
        this.currency = currency;
    }

    // 5. 只提供 getter，无 setter
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }

    // 6. 修改操作返回新对象
    public Money add(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException("货币类型不一致");
        return new Money(this.amount + other.amount, this.currency);  // 新对象
    }

    // 7. 防御性拷贝（如果字段是可变对象）
    // 若有 Date 字段：return new Date(this.date.getTime());
}
```

不可变类的六条规则：
1. `final` 类
2. 所有字段 `private final`
3. 构造函数验证参数
4. 无 setter
5. 修改操作返回新对象
6. 可变对象字段做防御性拷贝

---

## 3.7 本章总结

- **final 变量**：引用不可重新赋值，引用类型的内容依然可变；`static final` 才是真常量
- **final 方法**：禁止子类 override，用于保护模板流程和安全关键逻辑
- **final 类**：禁止继承，用于不可变值对象和工具类
- **JVM 可见性**：final 字段有 happens-before 保证，不可变对象天生线程安全
- **不可变类六条**：final 类 + final 字段 + 构造验证 + 无 setter + 操作返回新对象 + 防御性拷贝

> **本章对应演示代码**：`FinalDemo.java`（三种 final 用法 + 不可变类实现）、`CombinedModifiersDemo.java`（final + static + 访问修饰符的组合）

**返回目录**：[README.md](../README.md)
