# Java转换器工具库

## 📚 项目简介

这是一个基于Hutool工具类的Java转换器工具库，提供各种常用的数据转换功能。

**技术栈**：
- JDK 1.8
- Hutool 5.8.23
- JUnit 4.13.2

---

## 📁 目录结构

```
java-convetor/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/fragment/convetor/
│   │           ├── core/                          # 核心接口和基类
│   │           │   ├── Converter.java             # 转换器接口
│   │           │   ├── AbstractConverter.java     # 抽象转换器基类
│   │           │   └── ConvertException.java      # 转换异常
│   │           ├── json/                          # JSON转换器
│   │           │   ├── JsonArrayToListConverter.java    # JSON数组→List<Bean>
│   │           │   ├── JsonObjectToBeanConverter.java   # JSON对象→Bean
│   │           │   └── BeanToJsonConverter.java         # Bean→JSON字符串
│   │           ├── xml/                           # XML转换器（待扩展）
│   │           ├── csv/                           # CSV转换器（待扩展）
│   │           ├── map/                           # Map转换器（待扩展）
│   │           └── example/                       # 使用示例
│   │               └── JsonConvertExample.java    # JSON转换示例
│   └── test/
│       └── java/
│           └── com/fragment/convetor/
│               ├── model/                         # 测试用实体类
│               │   └── User.java
│               └── json/                          # JSON转换器测试
│                   └── JsonArrayToListConverterTest.java
├── pom.xml
└── README.md
```

---

## 🚀 快速开始

### 1. Maven依赖

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.23</version>
</dependency>
```

### 2. 基本使用

#### 2.1 JSON数组字符串转List<Bean>

```java
// 1. 创建转换器
JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);

// 2. 准备JSON数组字符串
String jsonArray = "[{\"name\":\"张三\",\"age\":20},{\"name\":\"李四\",\"age\":25}]";

// 3. 执行转换
List<User> users = converter.convert(jsonArray);

// 4. 使用结果
for (User user : users) {
    System.out.println(user);
}
```

#### 2.2 JSON对象字符串转Bean

```java
// 1. 创建转换器
JsonObjectToBeanConverter<User> converter = new JsonObjectToBeanConverter<>(User.class);

// 2. 准备JSON对象字符串
String jsonObject = "{\"name\":\"张三\",\"age\":20}";

// 3. 执行转换
User user = converter.convert(jsonObject);
```

#### 2.3 Bean转JSON字符串

```java
// 1. 创建转换器（格式化输出）
BeanToJsonConverter<User> converter = new BeanToJsonConverter<>(User.class, true);

// 2. 创建Bean对象
User user = new User("张三", 20);

// 3. 执行转换
String json = converter.convert(user);
```

---

## 📖 详细文档

### 核心接口

#### Converter<S, T>

所有转换器的核心接口。

```java
public interface Converter<S, T> {
    /**
     * 执行转换
     */
    T convert(S source) throws ConvertException;
    
    /**
     * 获取转换器名称
     */
    String getName();
    
    /**
     * 获取源类型
     */
    Class<S> getSourceType();
    
    /**
     * 获取目标类型
     */
    Class<T> getTargetType();
}
```

#### AbstractConverter<S, T>

抽象转换器基类，提供通用实现。

**特性**：
- ✅ 自动处理null值
- ✅ 统一异常处理
- ✅ 简化子类实现

**使用方式**：

```java
public class MyConverter extends AbstractConverter<String, Integer> {
    
    public MyConverter() {
        super(String.class, Integer.class);
    }
    
    @Override
    protected Integer doConvert(String source) throws Exception {
        return Integer.parseInt(source);
    }
}
```

---

### JSON转换器

#### JsonArrayToListConverter<T>

将JSON数组字符串转换为List<Bean>。

**构造函数**：

```java
public JsonArrayToListConverter(Class<T> beanClass)
```

**特性**：
- ✅ 自动校验JSON格式
- ✅ null值返回空List
- ✅ 空字符串返回空List
- ✅ 详细的异常信息

**示例**：

```java
JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);

// 正常转换
String json = "[{\"name\":\"张三\",\"age\":20}]";
List<User> users = converter.convert(json);

// null值处理
List<User> emptyList = converter.convert(null); // 返回空List

// 空数组处理
List<User> emptyList2 = converter.convert("[]"); // 返回空List
```

---

#### JsonObjectToBeanConverter<T>

将JSON对象字符串转换为Bean。

**构造函数**：

```java
public JsonObjectToBeanConverter(Class<T> beanClass)
```

**特性**：
- ✅ 自动校验JSON格式
- ✅ 支持嵌套对象
- ✅ 详细的异常信息

**示例**：

```java
JsonObjectToBeanConverter<User> converter = new JsonObjectToBeanConverter<>(User.class);

String json = "{\"name\":\"张三\",\"age\":20}";
User user = converter.convert(json);
```

---

#### BeanToJsonConverter<T>

将Bean转换为JSON字符串。

**构造函数**：

```java
// 紧凑格式
public BeanToJsonConverter(Class<T> beanClass)

// 格式化输出
public BeanToJsonConverter(Class<T> beanClass, boolean prettyPrint)
```

**特性**：
- ✅ 支持格式化输出
- ✅ null值返回"null"字符串
- ✅ 链式调用

**示例**：

```java
BeanToJsonConverter<User> converter = new BeanToJsonConverter<>(User.class);

User user = new User("张三", 20);

// 紧凑格式
String json = converter.convert(user);
// 输出：{"name":"张三","age":20}

// 格式化输出
converter.setPrettyPrint(true);
String prettyJson = converter.convert(user);
// 输出：
// {
//   "name": "张三",
//   "age": 20
// }
```

---

## 🎯 运行示例

### 运行JSON转换示例

```bash
# 编译
mvn clean compile

# 运行示例
mvn exec:java -Dexec.mainClass="com.fragment.convetor.example.JsonConvertExample"
```

**输出**：

```
========== JSON转换器使用示例 ==========

===== 示例1：JSON数组字符串转List<Bean> =====
原始JSON数组:
[{"name":"张三","age":20,"email":"zhangsan@example.com"},{"name":"李四","age":25,"email":"lisi@example.com"},{"name":"王五","age":30,"email":"wangwu@example.com"}]

转换结果:
1. User{name='张三', age=20, email='zhangsan@example.com'}
2. User{name='李四', age=25, email='lisi@example.com'}
3. User{name='王五', age=30, email='wangwu@example.com'}

===== 示例2：JSON对象字符串转Bean =====
原始JSON对象:
{"name":"赵六","age":35,"email":"zhaoliu@example.com"}

转换结果:
User{name='赵六', age=35, email='zhaoliu@example.com'}

===== 示例3：Bean转JSON字符串 =====
原始Bean对象:
User{name='孙七', age=28, email='sunqi@example.com'}

转换结果（格式化）:
{
  "name": "孙七",
  "age": 28,
  "email": "sunqi@example.com"
}

转换结果（紧凑）:
{"name":"孙七","age":28,"email":"sunqi@example.com"}
```

### 运行单元测试

```bash
# 运行所有测试
mvn test

# 运行指定测试
mvn test -Dtest=JsonArrayToListConverterTest
```

---

## 🔧 扩展开发

### 如何添加新的转换器？

#### 1. 创建转换器类

```java
package com.fragment.convetor.xxx;

import com.fragment.convetor.core.AbstractConverter;

public class MyConverter extends AbstractConverter<SourceType, TargetType> {
    
    public MyConverter() {
        super(SourceType.class, TargetType.class);
    }
    
    @Override
    protected TargetType doConvert(SourceType source) throws Exception {
        // 实现转换逻辑
        return ...;
    }
    
    @Override
    protected TargetType handleNull() {
        // 可选：自定义null值处理
        return super.handleNull();
    }
}
```

#### 2. 编写单元测试

```java
package com.fragment.convetor.xxx;

import org.junit.Test;
import static org.junit.Assert.*;

public class MyConverterTest {
    
    @Test
    public void testConvert() {
        MyConverter converter = new MyConverter();
        TargetType result = converter.convert(source);
        
        assertNotNull(result);
        // 添加更多断言
    }
}
```

#### 3. 添加使用示例

在`example`包下创建示例类，演示如何使用新的转换器。

---

## 📋 规划的转换器

### 已实现 ✅

- [x] JsonArrayToListConverter - JSON数组→List<Bean>
- [x] JsonObjectToBeanConverter - JSON对象→Bean
- [x] BeanToJsonConverter - Bean→JSON字符串

### 待实现 📝

#### JSON转换器
- [ ] ListToBeanArrayConverter - List<Bean>→JSON数组
- [ ] MapToJsonConverter - Map→JSON字符串
- [ ] JsonToMapConverter - JSON字符串→Map

#### XML转换器
- [ ] XmlToListConverter - XML→List<Bean>
- [ ] BeanToXmlConverter - Bean→XML字符串
- [ ] XmlToBeanConverter - XML→Bean

#### CSV转换器
- [ ] CsvToListConverter - CSV→List<Bean>
- [ ] ListToCsvConverter - List<Bean>→CSV
- [ ] CsvToBeanConverter - CSV行→Bean

#### Map转换器
- [ ] MapToBeanConverter - Map→Bean
- [ ] BeanToMapConverter - Bean→Map
- [ ] MapToMapConverter - Map类型转换

#### 集合转换器
- [ ] ListToSetConverter - List→Set
- [ ] SetToListConverter - Set→List
- [ ] ArrayToListConverter - 数组→List

#### 字符串转换器
- [ ] StringToDateConverter - 字符串→日期
- [ ] DateToStringConverter - 日期→字符串
- [ ] StringToNumberConverter - 字符串→数字

---

## ⚠️ 注意事项

### 1. 空值处理

所有转换器都会自动处理null值：
- `JsonArrayToListConverter`：null → 空List
- `JsonObjectToBeanConverter`：null → null
- `BeanToJsonConverter`：null → "null"字符串

### 2. 异常处理

转换失败时会抛出`ConvertException`：

```java
try {
    List<User> users = converter.convert(jsonArray);
} catch (ConvertException e) {
    System.err.println("转换失败: " + e.getMessage());
    e.printStackTrace();
}
```

### 3. 性能考虑

- 转换器实例可以复用，建议创建为单例
- 大批量数据转换时注意内存占用
- 复杂对象转换时注意性能

### 4. 线程安全

- 所有转换器都是线程安全的
- 可以在多线程环境下共享使用

---

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 开发规范

1. 代码风格：遵循阿里巴巴Java开发手册
2. 注释规范：使用JavaDoc注释
3. 测试覆盖：单元测试覆盖率 > 80%
4. 命名规范：见名知意，使用驼峰命名

---

## 📄 许可证

MIT License

---

## 📞 联系方式

如有问题或建议，请提交Issue。

---

**Happy Coding! 🚀**
