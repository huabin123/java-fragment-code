# Java 泛型擦除、假泛型与真泛型

## 1. 什么是类型擦除（Type Erasure）

- Java 的泛型是编译期特性：编译器在编译时进行类型检查与插入强制转换，生成的字节码在运行时不再保留具体的类型参数信息（被“擦除”）。
- 例如：`List<String>` 与 `List<Integer>` 在运行时的 `Class` 相同，均为 `java.util.ArrayList`。

```java
List<String> sl = new ArrayList<>();
List<Integer> il = new ArrayList<>();
System.out.println(sl.getClass() == il.getClass()); // true
```

### 1.1 为什么要擦除
- 为了与 Java 5 之前的类库与字节码保持二进制兼容性。
- 避免为每个类型参数生成专门版本（否则会导致代码膨胀）。

### 1.2 擦除如何实现（概念）
- 将 `List<T>` 等参数化类型在字节码层面替换为其原始类型（如 `List`）。
- 在需要的地方插入强制类型转换（由编译器自动生成）。
- 在多态覆盖场景下，编译器可能生成“桥接方法（bridge method）”以维持多态语义。

```java
class Box<T> { T get() { return null; } }
class StringBox extends Box<String> {
    @Override String get() { return "x"; }
    // 编译器会额外生成一个桥接方法：Object get() { return get(); }
}
```

- 注意：虽然运行时对象上无法直接获得实例的“实参类型”，但类/方法/字段的“声明处”泛型信息通常会以 `Signature` 保留，可通过反射在声明层面读取（`getGenericType()`、`ParameterizedType`），但这与“实例的实参类型重ified”不同。

## 2. 类型擦除带来的限制与现象

- 不能使用参数化类型做 `instanceof`：
```java
if (list instanceof List<String>) {} // 编译错误
if (list instanceof List) {}         // 只能判断原始类型
```

- 不能创建泛型数组、不能获取 `T.class`、不能 `new T()`：
```java
T[] arr = new T[10];        // 编译错误
Class<T> c = T.class;       // 编译错误
T obj = new T();            // 编译错误
```

- 重载易因擦除而冲突（name clash）：
```java
void print(List<String> x) {}
void print(List<Integer> x) {} // 编译错误：擦除后签名相同
```

- 不能捕获/抛出泛型化的异常类型；
- 基本类型不能作为类型参数（需用包装类型，如 `Integer`）；
- 可变参数与泛型混用会有“非具体化类型（non-reifiable）”警告，可能导致堆污染（heap pollution）。

## 3. “假泛型”是什么意思

- 社区里常用“假泛型”来指 Java 的“擦除式泛型”：
  - 泛型在编译期有效，运行时类型参数被擦除；
  - 运行时无法获知实例化的具体类型参数（非 reified）。
- 这并非“贬义”，而是描述实现方式：它在编译期提供强类型检查与 API 设计能力，同时保留了对旧字节码的兼容性。

## 4. 什么是真泛型（Reified/Runtime-Retained Generics）

- “真泛型（具象/具体化泛型）”指运行时仍能获知类型实参的泛型实现。
- 常见对比：
  - C#/.NET：`typeof(List<int>) != typeof(List<string>)`，运行时区分不同封装类型；可对 `T` 做反射、`new T()`（需 `where T : new()` 约束）。
  - Kotlin：JVM 上泛型同样擦除，但 `inline` + `reified` 的内联泛型函数在编译期会将类型实参“具体化”进调用点，从而在函数体中可用 `T::class`/`is T` 等。
  - Swift、Rust、C++ 模板/单态化（monomorphization）：通过在编译期为每个类型生成专门版本，运行时天然区分。

Kotlin 示例（对比“具体化”的能力）：
```kotlin
inline fun <reified T> filterIs(list: List<Any>): List<T> =
    list.filter { it is T }.map { it as T }

val xs = listOf(1, "a", 2L)
val ints = filterIs<Int>(xs) // 在函数体中可以使用 `is T`
```

## 5. 在 Java 中的常用绕过与实践

- 传入 `Class<T>` 或 `Type`/`ParameterizedType` 作为“类型令牌（Type Token）”
```java
public <T> T fromJson(String json, Class<T> type) { /* ... */ }
// 或使用 Gson 等需要 java.lang.reflect.Type 的库
```

- 通过工厂/构造函数引用注入实例化能力：
```java
public <T> T create(Supplier<T> factory) { return factory.get(); }
T user = create(User::new);
```

- 创建数组用 `Array.newInstance`：
```java
@SuppressWarnings("unchecked")
public static <T> T[] newArray(Class<T> componentType, int len) {
    return (T[]) java.lang.reflect.Array.newInstance(componentType, len);
}
```

- 处理 varargs 泛型警告：
  - 尽量改为 `List<List<T>>` 或使用 `@SafeVarargs` 标注只读、不逃逸的可变参数方法。

- 捕获通配符技巧（wildcard capture）：通过私有泛型方法让编译器推断类型。
```java
public static void addAll(List<? super T> dst, List<? extends T> src) { /* ... */ }
// 或将 List<?> 交给私有 <T> 方法处理以完成捕获
```

## 6. 反射能拿到什么？

- 能拿到“声明处”的泛型信息（类、字段、方法的签名），比如：
```java
Field f = Foo.class.getDeclaredField("list");
Type t = f.getGenericType(); // 可能是 ParameterizedType，包含实际类型参数
```
- 不能从“实例对象”上直接得到其运行时的类型实参（因为实例的类型实参未具体化）。

## 7. 速查清单（Checklist）

- 需要读取多种子类型：`<? extends T>`；需要写入：`<? super T>`（PECS）。
- 不能 `new T()`/`T.class`/`T[]`：传 `Class<T>`/`Supplier<T>`/`Array.newInstance`。
- 警惕方法重载与擦除冲突；
- 谨慎使用原始类型与 varargs 泛型，必要时 `@SuppressWarnings`/`@SafeVarargs`；
- 若需要“真泛型”能力，评估：
  - 设计 API 传入类型令牌；
  - 借助库（如 Guava `TypeToken`、Gson `TypeToken`）；
  - 或在 Kotlin 使用 `inline + reified` 的特性（在 Java 调用方仍受限）。

## 8. 关联示例

- `GenericPitfallsDemo.java`：展示类型擦除、泛型数组与原始类型问题。
- `PECSPrincipleDemo.java`：展示协变/逆变（与擦除导致的 API 设计约束相关）。
- `GenericRepositoryDemo.java`：展示真实业务中的泛型接口/实现与服务层组合。
- `GenericErasureAdvancedDemo.java`：进一步演示运行时类型相同、桥接方法、Type Token 与泛型数组创建、通配符捕获、@SafeVarargs 等。
