# 快速开始指南

## 🚀 5分钟上手

### 1. 最简单的使用方式

```java
import com.fragment.convetor.core.ConverterFactory;
import java.util.List;

public class QuickStart {
    public static void main(String[] args) {
        // JSON数组字符串
        String jsonArray = "[{\"name\":\"张三\",\"age\":20},{\"name\":\"李四\",\"age\":25}]";
        
        // 一行代码转换
        List<User> users = ConverterFactory.jsonArrayToList(jsonArray, User.class);
        
        // 输出结果
        users.forEach(System.out::println);
    }
}
```

**就是这么简单！** 🎉

---

## 📖 三种使用方式

### 方式1：快捷方法（推荐）⭐

**最简单，一行代码搞定**

```java
// JSON数组 → List<Bean>
List<User> users = ConverterFactory.jsonArrayToList(jsonArray, User.class);

// JSON对象 → Bean
User user = ConverterFactory.jsonObjectToBean(jsonObject, User.class);

// Bean → JSON字符串
String json = ConverterFactory.beanToJson(user);

// Bean → JSON字符串（格式化）
String prettyJson = ConverterFactory.beanToJson(user, true);
```

**优点**：
- ✅ 代码最简洁
- ✅ 自动缓存转换器
- ✅ 性能最优

---

### 方式2：工厂模式

**适合需要复用转换器的场景**

```java
// 获取转换器（会自动缓存）
JsonArrayToListConverter<User> converter = 
    ConverterFactory.getJsonArrayToListConverter(User.class);

// 多次使用
List<User> users1 = converter.convert(jsonArray1);
List<User> users2 = converter.convert(jsonArray2);
List<User> users3 = converter.convert(jsonArray3);
```

**优点**：
- ✅ 转换器复用
- ✅ 性能好
- ✅ 代码清晰

---

### 方式3：直接创建

**适合需要自定义配置的场景**

```java
// 直接创建转换器
JsonArrayToListConverter<User> converter = 
    new JsonArrayToListConverter<>(User.class);

// 使用转换器
List<User> users = converter.convert(jsonArray);
```

**优点**：
- ✅ 灵活性高
- ✅ 可自定义配置

---

## 💡 常见场景

### 场景1：接口返回JSON数组

```java
// 假设从API获取到JSON数组字符串
String response = httpClient.get("https://api.example.com/users");

// 一行代码转换为List
List<User> users = ConverterFactory.jsonArrayToList(response, User.class);

// 使用数据
for (User user : users) {
    System.out.println(user.getName() + " - " + user.getAge());
}
```

---

### 场景2：读取JSON配置文件

```java
// 读取JSON配置文件
String configJson = FileUtil.readUtf8String("config.json");

// 转换为配置对象
Config config = ConverterFactory.jsonObjectToBean(configJson, Config.class);

// 使用配置
System.out.println("数据库地址: " + config.getDbUrl());
```

---

### 场景3：对象序列化

```java
// 创建对象
User user = new User("张三", 20, "zhangsan@example.com");

// 转换为JSON字符串（用于存储或传输）
String json = ConverterFactory.beanToJson(user);

// 保存到文件
FileUtil.writeUtf8String(json, "user.json");
```

---

### 场景4：日志输出

```java
// 对象转JSON（格式化输出）
User user = getUser();
String prettyJson = ConverterFactory.beanToJson(user, true);

// 输出到日志
logger.info("用户信息:\n{}", prettyJson);
```

---

## ⚠️ 注意事项

### 1. Bean类必须有无参构造函数

```java
// ✅ 正确
public class User {
    private String name;
    private Integer age;
    
    // 必须有无参构造函数
    public User() {
    }
    
    public User(String name, Integer age) {
        this.name = name;
        this.age = age;
    }
    
    // getter和setter
}

// ❌ 错误
public class User {
    private String name;
    private Integer age;
    
    // 只有有参构造函数，没有无参构造函数
    public User(String name, Integer age) {
        this.name = name;
        this.age = age;
    }
}
```

---

### 2. JSON字段名与Bean属性名对应

```java
// JSON字符串
{
    "name": "张三",
    "age": 20
}

// Bean类
public class User {
    private String name;  // 对应JSON的name字段
    private Integer age;  // 对应JSON的age字段
    
    // getter和setter
}
```

**如果字段名不一致怎么办？**

使用Hutool的`@Alias`注解：

```java
import cn.hutool.core.annotation.Alias;

public class User {
    @Alias("user_name")  // JSON中的字段名是user_name
    private String name;
    
    @Alias("user_age")   // JSON中的字段名是user_age
    private Integer age;
    
    // getter和setter
}
```

---

### 3. 异常处理

```java
try {
    List<User> users = ConverterFactory.jsonArrayToList(jsonArray, User.class);
} catch (ConvertException e) {
    // 转换失败
    System.err.println("转换失败: " + e.getMessage());
    e.printStackTrace();
}
```

---

## 🎯 完整示例

```java
package com.example;

import com.fragment.convetor.core.ConverterFactory;
import java.util.List;

public class CompleteExample {
    
    // 用户实体类
    public static class User {
        private String name;
        private Integer age;
        private String email;
        
        public User() {
        }
        
        public User(String name, Integer age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
        
        // getter和setter省略...
        
        @Override
        public String toString() {
            return "User{name='" + name + "', age=" + age + ", email='" + email + "'}";
        }
    }
    
    public static void main(String[] args) {
        // 1. JSON数组转List
        String jsonArray = "[" +
                "{\"name\":\"张三\",\"age\":20,\"email\":\"zhangsan@example.com\"}," +
                "{\"name\":\"李四\",\"age\":25,\"email\":\"lisi@example.com\"}" +
                "]";
        
        List<User> users = ConverterFactory.jsonArrayToList(jsonArray, User.class);
        System.out.println("用户列表:");
        users.forEach(System.out::println);
        
        System.out.println();
        
        // 2. JSON对象转Bean
        String jsonObject = "{\"name\":\"王五\",\"age\":30,\"email\":\"wangwu@example.com\"}";
        User user = ConverterFactory.jsonObjectToBean(jsonObject, User.class);
        System.out.println("单个用户: " + user);
        
        System.out.println();
        
        // 3. Bean转JSON
        User newUser = new User("赵六", 35, "zhaoliu@example.com");
        String json = ConverterFactory.beanToJson(newUser, true);
        System.out.println("JSON输出:");
        System.out.println(json);
    }
}
```

**运行结果**：

```
用户列表:
User{name='张三', age=20, email='zhangsan@example.com'}
User{name='李四', age=25, email='lisi@example.com'}

单个用户: User{name='王五', age=30, email='wangwu@example.com'}

JSON输出:
{
  "name": "赵六",
  "age": 35,
  "email": "zhaoliu@example.com"
}
```

---

## 📚 更多文档

- [完整文档](README.md)
- [API文档](docs/API.md)（待补充）
- [常见问题](docs/FAQ.md)（待补充）

---

## 🎉 开始使用

现在你已经掌握了基本用法，开始在你的项目中使用吧！

如有问题，请查看[完整文档](README.md)或提交Issue。

**Happy Coding! 🚀**
