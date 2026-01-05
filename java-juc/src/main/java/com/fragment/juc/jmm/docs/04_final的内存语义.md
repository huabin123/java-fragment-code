# 第四章：final的内存语义 - 不可变对象的基石

> **学习目标**：深入理解final的内存保证和不可变对象的设计

---

## 一、为什么需要final的内存语义？

### 1.1 对象发布的问题

```java
// 问题：对象未完全初始化就被其他线程看到

public class UnsafePublish {
    private int value;
    
    public UnsafePublish(int value) {
        this.value = value;  // 1
    }  // 2
    
    // 在另一个线程
    public static UnsafePublish instance;
    
    public static void publish() {
        instance = new UnsafePublish(42);  // 3
    }
}

// 问题分析：
// 3可能重排序为：
//   3.1 分配内存
//   3.2 instance指向内存（对象未初始化）
//   3.3 执行构造函数（初始化value）

// 其他线程可能看到：
// instance != null，但value = 0（未初始化）
```

### 1.2 final的解决方案

```java
// 使用final保证初始化安全

public class SafePublish {
    private final int value;
    
    public SafePublish(int value) {
        this.value = value;  // final域的初始化
    }
    
    public static SafePublish instance;
    
    public static void publish() {
        instance = new SafePublish(42);
    }
}

// final的保证：
// 1. 构造函数内对final域的写入
// 2. 与构造函数完成之间建立happens-before
// 3. 其他线程看到对象引用时，一定能看到final域的正确值
```

---

## 二、final的内存语义

### 2.1 final域的重排序规则

```
规则1：final域的写入
在构造函数内对final域的写入，
与随后把这个对象的引用赋值给一个引用变量，
这两个操作之间不能重排序。

规则2：final域的读取
初次读取包含final域的对象的引用，
与随后初次读取这个final域，
这两个操作之间不能重排序。
```

**示例**：

```java
public class FinalExample {
    private final int x;
    private int y;
    private static FinalExample instance;
    
    public FinalExample() {
        x = 1;  // 1. final域的写入
        y = 2;  // 2. 普通域的写入
    }  // 3. 构造函数结束
    
    public static void writer() {
        instance = new FinalExample();  // 4. 对象引用赋值
    }
    
    public static void reader() {
        FinalExample obj = instance;  // 5. 读取对象引用
        int a = obj.x;  // 6. 读取final域
        int b = obj.y;  // 7. 读取普通域
    }
}

// 重排序规则：
// 1. 1不能重排序到4之后（final域写入规则）
// 2. 5和6不能重排序（final域读取规则）
// 3. 2可能重排序到4之后（普通域没有保证）

// 结果：
// - a一定能看到1（final保证）
// - b可能看到0或2（普通域不保证）
```

### 2.2 final域的happens-before规则

```
final域的happens-before规则：

1. 对final域的写入 happens-before 构造函数结束
2. 构造函数结束 happens-before 对象引用赋值
3. 对象引用赋值 happens-before 读取对象引用
4. 读取对象引用 happens-before 读取final域

传递性：
对final域的写入 happens-before 读取final域
```

---

## 三、final的实现原理

### 3.1 内存屏障

```
final域的内存屏障插入策略：

写final域：
StoreStore屏障
写final域
StoreStore屏障
构造函数返回

作用：
- 保证final域的写入在构造函数返回前完成
- 保证final域的写入不会重排序到构造函数之外

读final域：
LoadLoad屏障
读对象引用
LoadLoad屏障
读final域

作用：
- 保证读取对象引用在读取final域之前完成
- 保证读取final域时能看到正确的值
```

### 3.2 示例分析

```java
public class FinalFieldExample {
    private final int x;
    private int y;
    
    public FinalFieldExample() {
        x = 1;
        // StoreStore屏障
        y = 2;
        // StoreStore屏障
    }  // 构造函数返回
}

// 内存屏障的作用：
// 1. 第一个StoreStore：保证x=1在y=2之前完成
// 2. 第二个StoreStore：保证y=2在构造函数返回前完成
// 3. 结果：对象完全初始化后才对其他线程可见
```

---

## 四、final引用类型

### 4.1 final引用的特殊规则

```
final引用的增强语义：

在构造函数内：
1. 对final引用的对象的成员域的写入
2. 与随后在构造函数外把这个被构造对象的引用赋值给一个引用变量
这两个操作之间不能重排序。
```

**示例**：

```java
public class FinalReferenceExample {
    private final int[] array;
    
    public FinalReferenceExample() {
        array = new int[10];  // 1. 创建数组
        array[0] = 1;         // 2. 写入数组元素
        array[1] = 2;         // 3. 写入数组元素
    }  // 4. 构造函数结束
}

// final引用的保证：
// 1. 1、2、3不能重排序到4之后
// 2. 其他线程看到对象时，一定能看到array[0]=1, array[1]=2
// 3. 即使array不是final，其元素也能正确初始化
```

### 4.2 注意事项

```java
// ❌ 错误：final引用的对象内容可以修改

public class MutableFinalReference {
    private final List<String> list = new ArrayList<>();
    
    public void add(String item) {
        list.add(item);  // 可以修改
    }
}

// final只保证引用不变，不保证对象内容不变

// ✅ 正确：使用不可变集合

public class ImmutableFinalReference {
    private final List<String> list;
    
    public ImmutableFinalReference(List<String> list) {
        this.list = Collections.unmodifiableList(new ArrayList<>(list));
    }
}
```

---

## 五、不可变对象设计

### 5.1 不可变对象的定义

```
不可变对象（Immutable Object）：
对象创建后，其状态不能被修改。

特点：
1. 所有域都是final
2. 对象正确构造（this引用不逃逸）
3. 没有setter方法
4. 引用的对象也是不可变的
```

### 5.2 不可变对象的优势

```
优势：
✅ 天然线程安全（无需同步）
✅ 可以自由共享（无需复制）
✅ 可以作为Map的key（hashCode不变）
✅ 简化并发编程
✅ 防止意外修改

劣势：
❌ 每次修改都要创建新对象
❌ 可能产生大量对象
❌ 可能影响性能
```

### 5.3 不可变对象示例

```java
// 示例1：不可变的Point类

public final class ImmutablePoint {
    private final int x;
    private final int y;
    
    public ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    // 修改操作返回新对象
    public ImmutablePoint move(int dx, int dy) {
        return new ImmutablePoint(x + dx, y + dy);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutablePoint)) return false;
        ImmutablePoint that = (ImmutablePoint) o;
        return x == that.x && y == that.y;
    }
    
    @Override
    public int hashCode() {
        return 31 * x + y;
    }
}
```

```java
// 示例2：不可变的Person类

public final class ImmutablePerson {
    private final String name;
    private final int age;
    private final List<String> hobbies;
    
    public ImmutablePerson(String name, int age, List<String> hobbies) {
        this.name = name;
        this.age = age;
        // 防御性复制 + 不可变包装
        this.hobbies = Collections.unmodifiableList(
            new ArrayList<>(hobbies)
        );
    }
    
    public String getName() {
        return name;
    }
    
    public int getAge() {
        return age;
    }
    
    public List<String> getHobbies() {
        return hobbies;  // 已经是不可变的
    }
    
    // 修改操作返回新对象
    public ImmutablePerson withAge(int newAge) {
        return new ImmutablePerson(name, newAge, hobbies);
    }
}
```

---

## 六、安全发布

### 6.1 什么是安全发布？

```
安全发布（Safe Publication）：
确保对象对其他线程可见时，
对象已经完全初始化。

不安全发布的问题：
- 对象引用可见，但对象未完全初始化
- 其他线程可能看到部分初始化的对象
```

### 6.2 安全发布的方式

```java
// 方式1：使用final

public class SafePublish1 {
    private final int value;
    
    public SafePublish1(int value) {
        this.value = value;
    }
}

// 方式2：使用volatile

public class SafePublish2 {
    private volatile SafePublish2 instance;
    
    public void publish() {
        instance = new SafePublish2();
    }
}

// 方式3：使用synchronized

public class SafePublish3 {
    private SafePublish3 instance;
    
    public synchronized void publish() {
        instance = new SafePublish3();
    }
    
    public synchronized SafePublish3 getInstance() {
        return instance;
    }
}

// 方式4：使用静态初始化

public class SafePublish4 {
    private static final SafePublish4 INSTANCE = new SafePublish4();
    
    public static SafePublish4 getInstance() {
        return INSTANCE;
    }
}

// 方式5：使用并发容器

public class SafePublish5 {
    private static final ConcurrentHashMap<String, Object> map = 
        new ConcurrentHashMap<>();
    
    public static void publish(String key, Object value) {
        map.put(key, value);
    }
}
```

### 6.3 this引用逃逸

```java
// ❌ 错误：this引用逃逸

public class ThisEscape {
    private final int value;
    
    public ThisEscape() {
        // 在构造函数中启动线程
        new Thread(() -> {
            // 使用this引用
            System.out.println(this.value);  // 可能看到0
        }).start();
        
        value = 42;  // this已经逃逸
    }
}

// 问题：
// 1. 线程启动时，this引用已经逃逸
// 2. 但对象还未完全初始化
// 3. 线程可能看到value=0

// ✅ 正确：使用工厂方法

public class SafeConstruction {
    private final int value;
    
    private SafeConstruction() {
        value = 42;
    }
    
    public static SafeConstruction create() {
        SafeConstruction instance = new SafeConstruction();
        // 对象完全初始化后再启动线程
        new Thread(() -> {
            System.out.println(instance.value);  // 一定能看到42
        }).start();
        return instance;
    }
}
```

---

## 七、实际应用

### 7.1 不可变的配置类

```java
public final class Configuration {
    private final String host;
    private final int port;
    private final int timeout;
    private final Map<String, String> properties;
    
    private Configuration(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.timeout = builder.timeout;
        this.properties = Collections.unmodifiableMap(
            new HashMap<>(builder.properties)
        );
    }
    
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getTimeout() { return timeout; }
    public Map<String, String> getProperties() { return properties; }
    
    // Builder模式
    public static class Builder {
        private String host = "localhost";
        private int port = 8080;
        private int timeout = 30000;
        private Map<String, String> properties = new HashMap<>();
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder property(String key, String value) {
            this.properties.put(key, value);
            return this;
        }
        
        public Configuration build() {
            return new Configuration(this);
        }
    }
}

// 使用
Configuration config = new Configuration.Builder()
    .host("example.com")
    .port(9090)
    .timeout(60000)
    .property("key1", "value1")
    .build();
```

### 7.2 不可变的值对象

```java
// 金额类（不可变）

public final class Money {
    private final long amount;  // 以分为单位
    private final String currency;
    
    public Money(long amount, String currency) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        this.amount = amount;
        this.currency = currency;
    }
    
    public long getAmount() {
        return amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    // 运算返回新对象
    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(amount + other.amount, currency);
    }
    
    public Money subtract(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(amount - other.amount, currency);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return amount == money.amount && currency.equals(money.currency);
    }
    
    @Override
    public int hashCode() {
        return 31 * Long.hashCode(amount) + currency.hashCode();
    }
}
```

---

## 八、总结

### 8.1 核心要点

1. **final的内存语义**：保证初始化安全
2. **重排序规则**：final域的写入和读取不能重排序
3. **不可变对象**：天然线程安全
4. **安全发布**：5种方式
5. **this逃逸**：避免在构造函数中泄露this

### 8.2 使用建议

```
✅ 使用final：
- 不需要修改的域
- 不可变对象的所有域
- 安全发布

✅ 不可变对象：
- 值对象（Money、Point等）
- 配置对象
- 共享数据

❌ 避免：
- this引用逃逸
- 在构造函数中启动线程
- 在构造函数中注册监听器
```

### 8.3 思考题

1. **final如何保证初始化安全？**
2. **final引用的对象可以修改吗？**
3. **什么是this引用逃逸？**
4. **如何设计不可变对象？**

---

**下一章预告**：我们将深入学习内存屏障和指令重排序的底层实现。

---

**参考资料**：
- 《Java并发编程实战》第3章、第16章
- 《Effective Java》第17条
- JSR 133 (Java Memory Model) FAQ
