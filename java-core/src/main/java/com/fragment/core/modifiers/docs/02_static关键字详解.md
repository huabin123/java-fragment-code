# Java static 关键字详解

## 概述

`static` 是 Java 中的一个关键字，用于表示"静态"或"类级别"的成员。static 成员属于类本身，而不是类的实例。

## static 的应用场景

### 1. 静态变量（类变量）

**特点：**
- 属于类，所有实例共享
- 在类加载时初始化，只初始化一次
- 存储在方法区（JDK 8+ 在堆中）
- 可以通过类名直接访问

**使用场景：**

#### 1.1 常量定义
```java
public class MathConstants {
    public static final double PI = 3.14159265359;
    public static final double E = 2.71828182846;
    
    // 配置常量
    public static final int MAX_RETRY_COUNT = 3;
    public static final long TIMEOUT_MS = 5000L;
}

// 使用
double area = MathConstants.PI * radius * radius;
```

#### 1.2 计数器
```java
public class User {
    private static int userCount = 0; // 用户总数
    private int userId;
    
    public User() {
        userCount++;
        this.userId = userCount;
    }
    
    public static int getUserCount() {
        return userCount;
    }
}

// 使用
User u1 = new User();
User u2 = new User();
System.out.println(User.getUserCount()); // 输出: 2
```

#### 1.3 共享缓存
```java
public class ConfigManager {
    // 所有实例共享同一份配置
    private static Map<String, String> configCache = new HashMap<>();
    
    static {
        // 静态初始化块
        configCache.put("app.name", "MyApp");
        configCache.put("app.version", "1.0.0");
    }
    
    public static String getConfig(String key) {
        return configCache.get(key);
    }
}
```

### 2. 静态方法（类方法）

**特点：**
- 属于类，不需要创建实例即可调用
- 不能访问非静态成员（实例变量和实例方法）
- 不能使用 this 和 super 关键字
- 可以访问静态成员

**使用场景：**

#### 2.1 工具方法
```java
public class StringUtils {
    // 工具方法不需要实例状态
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

// 使用
if (StringUtils.isEmpty(username)) {
    // ...
}
```

#### 2.2 工厂方法
```java
public class User {
    private String username;
    private String email;
    
    private User(String username, String email) {
        this.username = username;
        this.email = email;
    }
    
    // 静态工厂方法
    public static User createUser(String username, String email) {
        // 可以添加验证逻辑
        if (username == null || email == null) {
            throw new IllegalArgumentException("Username and email cannot be null");
        }
        return new User(username, email);
    }
    
    public static User createGuestUser() {
        return new User("guest", "guest@example.com");
    }
}

// 使用
User user = User.createUser("john", "john@example.com");
User guest = User.createGuestUser();
```

#### 2.3 单例模式
```java
public class DatabaseConnection {
    private static DatabaseConnection instance;
    
    private DatabaseConnection() {
        // 私有构造器
    }
    
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }
}
```

### 3. 静态代码块

**特点：**
- 在类加载时执行，只执行一次
- 在构造器之前执行
- 可以有多个，按顺序执行
- 用于复杂的静态初始化

**使用场景：**

#### 3.1 静态资源初始化
```java
public class DatabaseConfig {
    private static Properties properties;
    private static Connection connection;
    
    static {
        // 加载配置文件
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class
                .getResourceAsStream("/db.properties")) {
            properties.load(input);
            
            // 初始化数据库连接
            String url = properties.getProperty("db.url");
            String user = properties.getProperty("db.user");
            String password = properties.getProperty("db.password");
            connection = DriverManager.getConnection(url, user, password);
            
            System.out.println("Database initialized successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    public static Connection getConnection() {
        return connection;
    }
}
```

#### 3.2 注册驱动
```java
public class JdbcDriver {
    static {
        try {
            // 注册 JDBC 驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL Driver registered");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL Driver not found", e);
        }
    }
}
```

#### 3.3 初始化集合
```java
public class ErrorCodes {
    private static final Map<Integer, String> ERROR_MESSAGES;
    
    static {
        Map<Integer, String> temp = new HashMap<>();
        temp.put(400, "Bad Request");
        temp.put(401, "Unauthorized");
        temp.put(403, "Forbidden");
        temp.put(404, "Not Found");
        temp.put(500, "Internal Server Error");
        ERROR_MESSAGES = Collections.unmodifiableMap(temp);
    }
    
    public static String getMessage(int code) {
        return ERROR_MESSAGES.getOrDefault(code, "Unknown Error");
    }
}
```

### 4. 静态内部类

**特点：**
- 不持有外部类的引用
- 可以访问外部类的静态成员
- 更节省内存
- 可以独立实例化

**使用场景：**

#### 4.1 Builder 模式
```java
public class User {
    private final String username;
    private final String email;
    private final int age;
    private final String address;
    
    private User(Builder builder) {
        this.username = builder.username;
        this.email = builder.email;
        this.age = builder.age;
        this.address = builder.address;
    }
    
    // 静态内部类 Builder
    public static class Builder {
        private String username;
        private String email;
        private int age;
        private String address;
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder age(int age) {
            this.age = age;
            return this;
        }
        
        public Builder address(String address) {
            this.address = address;
            return this;
        }
        
        public User build() {
            return new User(this);
        }
    }
}

// 使用
User user = new User.Builder()
    .username("john")
    .email("john@example.com")
    .age(25)
    .address("New York")
    .build();
```

#### 4.2 线程安全的单例模式
```java
public class Singleton {
    private Singleton() {}
    
    // 静态内部类持有单例
    private static class SingletonHolder {
        private static final Singleton INSTANCE = new Singleton();
    }
    
    public static Singleton getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
```

## static 导入

**使用场景：** 简化静态成员的调用

```java
// 传统方式
import java.lang.Math;

public class Calculator {
    public double calculate(double x, double y) {
        return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
    }
}

// 静态导入
import static java.lang.Math.*;

public class Calculator {
    public double calculate(double x, double y) {
        return sqrt(pow(x, 2) + pow(y, 2));
    }
}
```

## 内存分析

```java
public class MemoryDemo {
    private static int staticVar = 100;  // 方法区/元空间
    private int instanceVar = 200;        // 堆
    
    public static void main(String[] args) {
        MemoryDemo obj1 = new MemoryDemo();
        MemoryDemo obj2 = new MemoryDemo();
        
        // staticVar 只有一份，所有实例共享
        // instanceVar 每个实例都有一份
    }
}
```

**内存布局：**
- **静态变量**：存储在方法区（JDK 8+ 在堆中的元空间）
- **实例变量**：存储在堆中的对象内
- **局部变量**：存储在栈中

## 最佳实践

### 1. 合理使用静态变量
```java
// ✓ 好的做法 - 常量
public class Constants {
    public static final String APP_NAME = "MyApp";
}

// ✓ 好的做法 - 计数器
public class RequestCounter {
    private static final AtomicInteger count = new AtomicInteger(0);
    
    public static int increment() {
        return count.incrementAndGet();
    }
}

// ❌ 不好的做法 - 可变的静态状态
public class UserService {
    private static User currentUser; // 多线程不安全
}
```

### 2. 工具类设计
```java
public class FileUtils {
    // 私有构造器，防止实例化
    private FileUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    public static String readFile(String path) throws IOException {
        return Files.readString(Paths.get(path));
    }
    
    public static void writeFile(String path, String content) throws IOException {
        Files.writeString(Paths.get(path), content);
    }
}
```

### 3. 避免静态方法访问实例成员
```java
// ❌ 错误
public class Example {
    private int value = 10;
    
    public static void printValue() {
        System.out.println(value); // 编译错误
    }
}

// ✓ 正确
public class Example {
    private int value = 10;
    
    public void printValue() {
        System.out.println(value);
    }
    
    public static void printStaticValue(Example example) {
        System.out.println(example.value); // 通过参数传递
    }
}
```

### 4. 线程安全考虑
```java
// ❌ 线程不安全
public class Counter {
    private static int count = 0;
    
    public static void increment() {
        count++; // 非原子操作
    }
}

// ✓ 线程安全
public class Counter {
    private static final AtomicInteger count = new AtomicInteger(0);
    
    public static void increment() {
        count.incrementAndGet();
    }
}

// ✓ 线程安全（使用 synchronized）
public class Counter {
    private static int count = 0;
    
    public static synchronized void increment() {
        count++;
    }
}
```

## 常见陷阱

### 1. 静态变量的生命周期
```java
public class MemoryLeak {
    private static List<Object> cache = new ArrayList<>();
    
    public void addToCache(Object obj) {
        cache.add(obj); // 可能导致内存泄漏
    }
}
```

### 2. 静态方法的继承
```java
public class Parent {
    public static void staticMethod() {
        System.out.println("Parent static method");
    }
}

public class Child extends Parent {
    public static void staticMethod() {
        System.out.println("Child static method");
    }
}

// 使用
Parent p = new Child();
p.staticMethod(); // 输出: Parent static method（不是多态）
```

### 3. 初始化顺序
```java
public class InitOrder {
    private static int x = 1;
    
    static {
        x = 2;
        y = 3; // 可以赋值
    }
    
    private static int y = 4; // 最终 y = 4
    
    public static void main(String[] args) {
        System.out.println(x); // 2
        System.out.println(y); // 4
    }
}
```

## 总结

1. **静态变量**：类级别共享，用于常量、计数器、缓存
2. **静态方法**：不依赖实例状态，用于工具方法、工厂方法
3. **静态代码块**：类加载时初始化，用于复杂初始化逻辑
4. **静态内部类**：不持有外部类引用，用于 Builder、单例
5. **注意线程安全**：静态成员在多线程环境下需要特别注意
6. **避免滥用**：过多静态成员会降低可测试性和灵活性
