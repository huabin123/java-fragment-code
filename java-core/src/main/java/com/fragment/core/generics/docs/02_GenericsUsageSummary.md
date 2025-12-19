# Java 泛型使用总结

## 1. 泛型通配符的选择原则

### 1.1 PECS 原则 (Producer Extends, Consumer Super)

- **Producer Extends**: 当你需要从集合中读取数据时，使用 `<? extends T>`
  - 例如：`List<? extends Number> numbers = new ArrayList<Integer>();`
  - 可以安全地读取元素，但不能添加元素（除了 null）

- **Consumer Super**: 当你需要向集合中写入数据时，使用 `<? super T>`
  - 例如：`List<? super Integer> numbers = new ArrayList<Number>();`
  - 可以安全地添加 T 类型或其子类型的元素，但读取时只能作为 Object 类型

### 1.2 选择指南

- 频繁从集合中读取数据时，使用 `<? extends T>`
- 频繁向集合中写入数据时，使用 `<? super T>`
- 既需要读取又需要写入时，不使用通配符，直接使用具体类型 `<T>`

## 2. 常见泛型使用场景

### 2.1 集合类

```java
// 读取场景（Producer Extends）
List<? extends Number> numbers = new ArrayList<Integer>();
Number num = numbers.get(0);  // 安全

// 写入场景（Consumer Super）
List<? super Integer> integers = new ArrayList<Number>();
integers.add(42);  // 安全
```

### 2.2 方法参数

```java
// 读取场景
public void printAll(List<? extends Number> list) {
    for (Number n : list) {
        System.out.println(n);
    }
}

// 写入场景
public void addIntegers(List<? super Integer> list) {
    list.add(42);
    list.add(10);
}
```

### 2.3 泛型方法

```java
// 泛型方法定义
public <T> T firstOrNull(List<T> list) {
    return list.isEmpty() ? null : list.get(0);
}

// 结合 PECS 原则的泛型方法
public <T> void copy(List<? super T> dest, List<? extends T> src) {
    for (int i = 0; i < src.size(); i++) {
        dest.add(src.get(i));
    }
}
```

## 3. 类型安全转换

在使用无泛型限制的集合赋值给泛型限制的集合时，需要注意类型安全：

```java
// 不安全的转换
List rawList = new ArrayList();
rawList.add("string");
rawList.add(42);

List<String> stringList = rawList;  // 编译警告但不报错
String s = stringList.get(0);  // 安全
String s2 = stringList.get(1);  // 运行时 ClassCastException
```

正确的做法是使用 `instanceof` 进行类型检查：

```java
List rawList = new ArrayList();
rawList.add("string");
rawList.add(42);

for (Object item : rawList) {
    if (item instanceof String) {
        String s = (String) item;
        // 安全地使用字符串
    } else {
        // 处理其他类型
    }
}
```

## 4. 泛型的局限性

### 4.1 类型擦除

Java 泛型在编译时会进行类型擦除，运行时不保留泛型类型信息：

```java
List<String> stringList = new ArrayList<>();
List<Integer> intList = new ArrayList<>();

// 运行时类型相同
System.out.println(stringList.getClass() == intList.getClass());  // true
```

### 4.2 无法创建泛型数组

不能直接创建泛型类型的数组：

```java
// 编译错误
List<String>[] array = new ArrayList<String>[10];

// 替代方案
List<List<String>> listOfLists = new ArrayList<>();
```

### 4.3 不能使用基本类型作为类型参数

必须使用包装类：

```java
// 错误
List<int> intList;

// 正确
List<Integer> intList = new ArrayList<>();
```

## 5. 泛型最佳实践

1. **明确方法的用途**：确定方法是主要用于读取数据还是写入数据，据此选择合适的通配符

2. **避免使用原始类型**：始终使用参数化类型，避免使用原始类型（如 `List` 而不是 `List<String>`）

3. **谨慎使用无界通配符**：`<?>` 适用于与泛型类型无关的操作，如检查列表是否为空

4. **使用泛型方法增加灵活性**：当方法的泛型类型与类的泛型类型无关时，使用泛型方法

5. **设计接口时考虑泛型**：设计通用接口和抽象类时，合理使用泛型提高代码复用性

6. **注意类型安全**：在类型转换时进行必要的检查，避免运行时异常

7. **不要过度使用泛型**：在简单场景下，过度使用泛型可能会使代码变得复杂难懂

## 6. 实际应用示例

### 6.1 通用仓库模式

```java
interface Repository<T, ID> {
    T save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    void delete(ID id);
}

class UserRepository implements Repository<User, Long> {
    // 实现方法
}
```

### 6.2 通用服务层

```java
class GenericService<T, ID> {
    private Repository<T, ID> repository;
    
    public GenericService(Repository<T, ID> repository) {
        this.repository = repository;
    }
    
    public T save(T entity) {
        return repository.save(entity);
    }
    
    public Optional<T> findById(ID id) {
        return repository.findById(id);
    }
}
```

### 6.3 结果包装类

```java
class Result<T> {
    private final boolean success;
    private final T data;
    private final String message;
    
    private Result(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }
    
    public static <T> Result<T> success(T data) {
        return new Result<>(true, data, null);
    }
    
    public static <T> Result<T> failure(String message) {
        return new Result<>(false, null, message);
    }
}
```

## 7. 总结

- 使用 `<? extends T>` 从集合中读取数据（Producer Extends）
- 使用 `<? super T>` 向集合中写入数据（Consumer Super）
- 在类型转换时进行必要的类型检查，避免 `ClassCastException`
- 理解泛型的局限性，如类型擦除和不能创建泛型数组
- 合理设计泛型接口和类，提高代码复用性和类型安全性

## 8. 与里氏替换原则（LSP）的关系

- 定义：里氏替换原则要求子类型必须能替换其父类型而不破坏程序行为。
- `<? extends T>` 支持协变读取：当方法参数为 `List<? extends Animal>` 时，`List<Dog>`、`List<Husky>` 等子类型集合均可被替换传入，符合 LSP 对"在需要父类型的地方可使用子类型"的要求；限制"只读不写"正是为了保持替换后的类型安全。
- `<? super T>` 支持逆变写入：当方法参数为 `List<? super Husky>` 时，`List<Dog>`、`List<Animal>` 等父类型集合均可被替换传入；此时只能安全地向其中写入 `Husky`（或其子类），读取则退化为 `Object`，以避免破坏替换后的行为。
- 示例（见 `java-core/src/main/java/com/fragment/core/generics/PECSPrincipleDemo.java`）：
  - `printAnimals(List<? extends Animal>)`：仅读取，展示协变。
  - `addHuskies(List<? super Husky>)`：仅写入，展示逆变。
  - `copy(List<? super T>, List<? extends T>)`：读写两端同时遵循上述原则。
- 小结：PECS 是在 Java 泛型层面将 LSP 的可替换性通过协变/逆变具体化，从而在编译期获得更强的类型安全与 API 兼容性。
