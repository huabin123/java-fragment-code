# 第三章：类型擦除——泛型的"真实"与"虚假"

## 3.1 类型擦除的本质

Java 泛型是**编译时特性**，.class 字节码中不保留泛型信息（除了方法签名的 Signature 属性）。这称为**类型擦除（Type Erasure）**。

```java
// 编译前（源码）
List<String> strings = new ArrayList<String>();
strings.add("hello");
String s = strings.get(0);

// 编译后（字节码等价代码）
List strings = new ArrayList();       // 泛型参数擦除
strings.add("hello");
String s = (String) strings.get(0);   // 自动插入强制转换
```

类型擦除是 Java 为了**向后兼容 JDK 1.4 及之前代码**而做出的设计妥协。泛型类和原始类型（Raw Type）在运行时是同一个 Class 对象。

---

## 3.2 擦除的规则

`GenericErasureAdvancedDemo.java` 演示了三种擦除规则：

### 无界类型参数擦除为 Object

```java
// 编译前
public class Box<T> {
    private T value;
    public T get() { return value; }
}

// 擦除后
public class Box {
    private Object value;
    public Object get() { return value; }
}
```

### 有界类型参数擦除为边界类型

```java
// 编译前
public <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) > 0 ? a : b;
}

// 擦除后
public Comparable max(Comparable a, Comparable b) {
    return a.compareTo(b) > 0 ? a : b;
}
```

### 多重边界擦除为第一个边界

```java
// 编译前
public <T extends Comparable<T> & Serializable> void process(T obj) { }

// 擦除后
public void process(Comparable obj) { }  // 擦除为第一个边界 Comparable
```

---

## 3.3 "假泛型"：运行时无法获取类型参数

```java
// GenericErasureAdvancedDemo.java

// ❌ 运行时获取不到类型参数
List<String> list = new ArrayList<>();
Class<?> clazz = list.getClass();  // ArrayList.class
// 无法从 clazz 知道 T 是 String！

// 以下都是 true（擦除后都是同一个 Class）
List<String> strList = new ArrayList<>();
List<Integer> intList = new ArrayList<>();
System.out.println(strList.getClass() == intList.getClass());  // true!
System.out.println(strList.getClass().getName());  // java.util.ArrayList

// ❌ 无法对类型参数用 instanceof
if (obj instanceof T) { }  // 编译错误

// ❌ 无法直接创建类型参数的实例
T instance = new T();  // 编译错误
```

---

## 3.4 "真泛型"：哪些地方保留了类型信息

虽然运行时实例没有类型参数信息，但以下两种情况**保留了泛型信息**：

### 情况一：通过继承固化类型参数

```java
// GenericErasureAdvancedDemo.java

// 父类用泛型
public abstract class TypedRepository<T> { }

// 子类固化了类型参数！
public class UserRepository extends TypedRepository<User> { }

// 运行时可以获取：
Type superType = UserRepository.class.getGenericSuperclass();
// ParameterizedType: TypedRepository<User>
ParameterizedType pt = (ParameterizedType) superType;
Type actualType = pt.getActualTypeArguments()[0];
// actualType = User.class ← 真正保留了！
```

这是 Spring 框架中 `ResolvableType` 的核心原理，也是 Jackson 的 `TypeReference` 的工作原理。

### 情况二：通过字段/方法签名

```java
// 字段的泛型类型在字节码的 Signature 属性中保留
public class Example {
    private List<String> names;  // Signature: Ljava/util/List<Ljava/lang/String;>;
}

// 运行时获取字段泛型类型
Field field = Example.class.getDeclaredField("names");
Type fieldType = field.getGenericType();
// ParameterizedType: List<String>
ParameterizedType pt = (ParameterizedType) fieldType;
Type elementType = pt.getActualTypeArguments()[0];
// elementType = String.class ← 保留了！
```

Jackson 反序列化 `List<User>` 就是这样获取 User 类型的——通过 `TypeReference<List<User>>{}` 的匿名子类继承固化类型参数。

---

## 3.5 Bridge 方法：类型擦除引发的问题

擦除后可能导致方法签名冲突，编译器自动生成 **bridge 方法**：

```java
// GenericErasureAdvancedDemo.java
interface Comparable<T> {
    int compareTo(T other);
}

class MyString implements Comparable<MyString> {
    public int compareTo(MyString other) { ... }  // 我们写的
}

// 擦除后，Comparable 接口要求一个 compareTo(Object)
// 编译器自动生成 bridge 方法：
public int compareTo(Object other) {
    return compareTo((MyString) other);  // 强制转型，委托真正的方法
}
```

可以通过反射看到 bridge 方法：`method.isBridge() == true`。

---

## 3.6 实战：绕过类型擦除获取泛型类型

```java
// 方案1：传入 Class 对象（最简单）
public <T> T deserialize(String json, Class<T> clazz) {
    return objectMapper.readValue(json, clazz);
}
// 用法：deserialize(json, User.class)

// 方案2：TypeReference 匿名子类（获取复杂泛型）
public <T> T deserialize(String json, TypeReference<T> typeRef) {
    return objectMapper.readValue(json, typeRef);
}
// 用法：deserialize(json, new TypeReference<List<User>>() {})
// 原理：匿名类继承 TypeReference<List<User>>，固化了类型参数，运行时可读取

// 方案3：通过继承获取（Spring ResolvableType 的方式）
abstract class TypedConverter<F, T> {
    protected Class<T> targetType;

    @SuppressWarnings("unchecked")
    public TypedConverter() {
        ParameterizedType pt = (ParameterizedType) getClass().getGenericSuperclass();
        this.targetType = (Class<T>) pt.getActualTypeArguments()[1];
    }
}
```

---

## 3.7 本章总结

- **类型擦除规则**：无界 → Object，有界 → 第一个边界，编译后自动插入强制转换
- **假泛型**：运行时实例没有类型参数，`instanceof T`、`new T()` 均不合法
- **真泛型**：通过**继承固化**（子类 extends 父泛型类）和**字段/方法 Signature** 两种途径保留类型信息
- **Bridge 方法**：编译器自动生成，解决擦除后接口签名不匹配的问题
- **绕过擦除**：传 `Class<T>` 参数、`TypeReference` 匿名子类、继承固化三种方案

> **本章对应演示代码**：`GenericErasureAdvancedDemo.java`（三种擦除规则、Bridge方法、运行时获取泛型类型）

**返回目录**：[README.md](../README.md)
