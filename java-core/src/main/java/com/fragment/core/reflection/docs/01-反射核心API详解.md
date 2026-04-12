# 第一章：反射核心 API 详解

## 1.1 获取 Class 对象的三种方式

```java
// ReflectionBasicDemo.java 中演示的三种方式

// 方式1：类字面量（编译时已知类型，最高效）
Class<String> clazz1 = String.class;
Class<Integer> clazz2 = Integer.class;

// 方式2：对象的 getClass()（运行时获取实际类型）
String str = "hello";
Class<?> clazz3 = str.getClass();  // 返回 String.class
Object obj = new ArrayList<>();
Class<?> clazz4 = obj.getClass();  // 返回 ArrayList.class，不是 List.class！

// 方式3：Class.forName()（类名字符串，用于动态加载）
Class<?> clazz5 = Class.forName("java.util.ArrayList");
Class<?> clazz6 = Class.forName("com.example.UserService");  // 可以是自定义类
```

**三种方式的选择**：
- 编译时已知类型 → 用 `.class`（最安全，编译器检查）
- 运行时实例 → 用 `.getClass()`
- 类名字符串（配置文件、插件系统）→ 用 `Class.forName()`

---

## 1.2 Class 对象的基础信息 API

```java
// ReflectionBasicDemo.java → examineClassInfo()
Class<?> clazz = ArrayList.class;

// 类名
clazz.getName();           // "java.util.ArrayList"（全限定名）
clazz.getSimpleName();     // "ArrayList"（简单名）
clazz.getCanonicalName();  // "java.util.ArrayList"（规范名，匿名类返回null）

// 类型判断
clazz.isInterface();       // false
clazz.isArray();           // false
clazz.isPrimitive();       // false（int.class.isPrimitive() = true）
clazz.isEnum();            // false
clazz.isAnnotation();      // false
clazz.isAnonymousClass();  // false

// 修饰符
int mod = clazz.getModifiers();
Modifier.isPublic(mod);    // true
Modifier.isFinal(mod);     // false
Modifier.isAbstract(mod);  // false

// 继承结构
clazz.getSuperclass();                   // AbstractList.class
clazz.getInterfaces();                   // [List, RandomAccess, Cloneable, Serializable]
clazz.getGenericInterfaces();            // [List<E>, RandomAccess, ...]（含泛型信息）
```

---

## 1.3 字段反射 API

`FieldReflectionDemo.java` 系统演示了字段反射的全部操作：

```java
// getDeclaredFields() vs getFields() 的关键区别
Class<?> clazz = UserEntity.class;

// getDeclaredFields()：获取本类声明的所有字段（含 private），不含父类字段
Field[] ownFields = clazz.getDeclaredFields();

// getFields()：获取所有 public 字段（含父类继承的 public 字段），不含 private
Field[] publicFields = clazz.getFields();
```

### 读取字段值

```java
// FieldReflectionDemo.java → readPrivateField()
UserEntity user = new UserEntity(1L, "张三", "zhangsan@example.com");

Field nameField = UserEntity.class.getDeclaredField("name");

// 关键：private 字段必须先调用 setAccessible(true)
nameField.setAccessible(true);

// 读取值
Object value = nameField.get(user);  // "张三"
String name = (String) nameField.get(user);

// 对于基本类型，有专门的方法避免装箱
Field ageField = UserEntity.class.getDeclaredField("age");
ageField.setAccessible(true);
int age = ageField.getInt(user);  // 直接返回 int，比 (int) field.get() 略快
```

### 设置字段值

```java
// FieldReflectionDemo.java → writePrivateField()
nameField.set(user, "李四");  // 直接修改 private 字段

// 修改 static 字段（第一个参数传 null）
Field staticField = clazz.getDeclaredField("instanceCount");
staticField.setAccessible(true);
staticField.set(null, 100);  // 修改静态字段
```

### 获取字段的泛型类型

```java
// FieldReflectionDemo.java → 获取 List<String> 的实际类型参数
Field listField = clazz.getDeclaredField("tags");  // private List<String> tags
Type genericType = listField.getGenericType();

if (genericType instanceof ParameterizedType) {
    ParameterizedType pt = (ParameterizedType) genericType;
    Type[] typeArgs = pt.getActualTypeArguments();
    // typeArgs[0] = String.class
}
```

---

## 1.4 方法反射 API

`MethodReflectionDemo.java` 演示了完整的方法反射操作：

```java
// getDeclaredMethods() vs getMethods()
Class<?> clazz = UserService.class;

// getDeclaredMethods()：本类所有方法（含 private），不含继承方法
Method[] ownMethods = clazz.getDeclaredMethods();

// getMethods()：所有 public 方法（含从父类/接口继承的）
Method[] allPublicMethods = clazz.getMethods();

// 获取特定方法（方法名 + 参数类型列表）
Method findById = clazz.getDeclaredMethod("findById", Long.class);
Method save = clazz.getDeclaredMethod("save", User.class, boolean.class);
```

### 调用方法

```java
// MethodReflectionDemo.java → invokeMethod()
UserService service = new UserServiceImpl();
Method findByIdMethod = UserService.class.getDeclaredMethod("findById", Long.class);
findByIdMethod.setAccessible(true);

// invoke(对象, 参数...)
User user = (User) findByIdMethod.invoke(service, 1L);

// 调用静态方法（第一个参数传 null）
Method staticMethod = MathUtils.class.getDeclaredMethod("add", int.class, int.class);
int result = (int) staticMethod.invoke(null, 3, 5);
```

### 获取方法信息

```java
Method method = clazz.getDeclaredMethod("findById", Long.class);

method.getName();             // "findById"
method.getReturnType();       // User.class
method.getParameterTypes();   // [Long.class]
method.getParameterCount();   // 1
method.getExceptionTypes();   // [DataAccessException.class, ...]
method.getModifiers();        // 修饰符
method.isAnnotationPresent(Transactional.class);  // 是否有注解
```

---

## 1.5 构造函数反射 API

```java
// ReflectionBasicDemo.java → createInstancesViaReflection()

// 获取构造函数
Constructor<User> defaultCtor = User.class.getDeclaredConstructor();  // 无参
Constructor<User> paramCtor = User.class.getDeclaredConstructor(String.class, int.class);

// 使用无参构造创建实例
paramCtor.setAccessible(true);
User user = paramCtor.newInstance("张三", 25);

// 便捷方式（只适合有 public 无参构造的类）
User user2 = User.class.newInstance();  // 已废弃（JDK 9+），用 getDeclaredConstructor().newInstance()
```

---

## 1.6 本章总结

- **获取 Class**：`.class`（编译时）、`.getClass()`（运行时）、`Class.forName()`（类名字符串）
- **Declared vs 非Declared**：`getDeclared*()` 返回本类所有（含 private），非 Declared 返回所有 public（含继承）
- **setAccessible(true)**：访问 private 成员的前提，JDK 9+ 模块化系统对其有更多限制
- **字段反射**：`get/set(obj)`，基本类型有专用方法（getInt/setInt 等）
- **方法反射**：`invoke(obj, args...)`，静态方法传 null 作第一个参数

> **本章对应演示代码**：`ReflectionBasicDemo.java`（Class 对象）、`FieldReflectionDemo.java`（字段反射）、`MethodReflectionDemo.java`（方法反射）

**继续阅读**：[02-反射操作字段与方法.md](./02-反射操作字段与方法.md)
