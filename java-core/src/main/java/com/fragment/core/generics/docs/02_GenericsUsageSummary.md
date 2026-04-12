# 第二章：泛型使用总结

## 2.1 泛型的三种使用形式

### 泛型类

```java
// GenericRepositoryDemo.java 的核心类
public class GenericRepository<T, ID> {
    private final Map<ID, T> storage = new HashMap<>();

    public void save(T entity, ID id) {
        storage.put(id, entity);
    }

    public T findById(ID id) {
        return storage.get(id);
    }

    public List<T> findAll() {
        return new ArrayList<>(storage.values());
    }
}

// 使用：类型参数在实例化时确定
GenericRepository<User, Long> userRepo = new GenericRepository<>();
userRepo.save(new User("张三"), 1L);
User user = userRepo.findById(1L);  // 无需强制转型
```

### 泛型接口

```java
// 定义通用的 CRUD 接口
public interface Repository<T, ID> {
    void save(T entity);
    T findById(ID id);
    List<T> findAll();
    void delete(ID id);
}

// 实现时可以指定具体类型，也可以继续使用类型参数
public class UserRepository implements Repository<User, Long> { ... }
public class GenericJpaRepository<T, ID> implements Repository<T, ID> { ... }
```

### 泛型方法

```java
// 类型参数在方法调用时推断
public static <T> List<T> singletonList(T element) {
    List<T> list = new ArrayList<>();
    list.add(element);
    return list;
}

// 调用时类型自动推断，无需指定
List<String> strs = singletonList("hello");
List<Integer> nums = singletonList(42);

// 多类型参数的泛型方法
public static <K, V> Map<K, V> mapOf(K key, V value) {
    Map<K, V> map = new HashMap<>();
    map.put(key, value);
    return map;
}
```

---

## 2.2 有界类型参数：`<T extends Comparable<T>>`

```java
// GenericRepositoryDemo.java → 排序功能
public static <T extends Comparable<T>> T findMax(List<T> list) {
    T max = list.get(0);
    for (T item : list) {
        if (item.compareTo(max) > 0) max = item;
    }
    return max;
}

// 约束：T 必须实现 Comparable<T>，保证 compareTo 方法存在
int maxAge = findMax(Arrays.asList(18, 25, 30, 16));  // 30
String maxStr = findMax(Arrays.asList("apple", "banana", "cherry"));  // cherry
```

### 多重边界：`<T extends Comparable<T> & Serializable>`

```java
// T 必须同时满足多个约束，类约束必须放第一位
public <T extends Cloneable & Serializable> void processAndSend(T obj) { ... }

// 实际使用：Spring 的泛型约束
public <T extends HttpMessage> T readWithMessageConverters(
    HttpInputMessage inputMessage, Class<T> targetType) { ... }
```

---

## 2.3 GenericPitfallsDemo：泛型常见陷阱

`GenericPitfallsDemo.java` 归纳了 5 个高频陷阱：

### 陷阱一：泛型数组创建

```java
// ❌ 编译错误：无法直接创建泛型数组
List<String>[] array = new ArrayList<String>[10];

// ✅ 方案1：使用 @SuppressWarnings
@SuppressWarnings("unchecked")
List<String>[] array = new ArrayList[10];

// ✅ 方案2：使用 List of List
List<List<String>> listOfList = new ArrayList<>();
```

### 陷阱二：原生类型（Raw Type）污染

```java
// ❌ 原生类型：丢失类型安全，只为兼容旧代码
List rawList = new ArrayList();
rawList.add("string");
rawList.add(42);  // 编译通过，运行时炸
String s = (String) rawList.get(1);  // ClassCastException

// ✅ 始终使用带类型参数的泛型
List<String> typedList = new ArrayList<>();
```

### 陷阱三：泛型和 instanceof

```java
// ❌ 运行时类型擦除，无法用 instanceof 检查泛型参数
if (list instanceof List<String>) { }  // 编译错误

// ✅ 只能检查原生类型
if (list instanceof List) { }  // OK，但失去了 String 信息

// ✅ 使用 Class 对象辅助
public <T> boolean isListOf(List<?> list, Class<T> type) {
    return list.stream().allMatch(type::isInstance);
}
```

### 陷阱四：静态上下文中不能使用类型参数

```java
public class Container<T> {
    // ❌ 静态字段不能是 T
    private static T instance;  // 编译错误

    // ❌ 静态方法不能引用 T
    public static T create() { }  // 编译错误

    // ✅ 静态泛型方法（独立的类型参数）
    public static <E> Container<E> of(E element) {
        return new Container<>(element);
    }
}
```

### 陷阱五：泛型方法重载陷阱

```java
// ❌ 类型擦除后方法签名相同，编译错误
public void process(List<String> list) { }
public void process(List<Integer> list) { }  // 编译错误：same erasure

// ✅ 用不同方法名区分
public void processStrings(List<String> list) { }
public void processIntegers(List<Integer> list) { }
```

---

## 2.4 泛型的实用设计模式

### Builder 模式的泛型化

```java
// 支持链式调用并保持类型安全
public class Builder<T extends Builder<T>> {
    @SuppressWarnings("unchecked")
    public T name(String name) {
        this.name = name;
        return (T) this;  // 返回具体子类类型
    }
}
```

### 工厂方法模式

```java
// GenericRepositoryDemo.java 中的工厂模式
public interface EntityFactory<T> {
    T create(Map<String, Object> properties);
}

// 通过 Class 对象反射创建实例
public static <T> T create(Class<T> clazz) throws Exception {
    return clazz.newInstance();
}
```

---

## 2.5 本章总结

- **三种形式**：泛型类（实例化时确定）、泛型接口（实现时可固化或继续参数化）、泛型方法（调用时推断）
- **有界参数**：`extends` 约束功能（确保方法存在），多重边界类约束放第一位
- **五大陷阱**：泛型数组、原生类型污染、instanceof 检查、静态上下文、方法重载
- **设计模式**：Builder 的 `<T extends Builder<T>>` 保持链式调用类型安全

> **本章对应演示代码**：`GenericRepositoryDemo.java`（泛型 CRUD 仓库）、`GenericPitfallsDemo.java`（5 大陷阱详解）

**继续阅读**：[03_GenericsErasure_FakeVsReal.md](./03_GenericsErasure_FakeVsReal.md)
