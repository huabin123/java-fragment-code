# Java 核心技术关联图谱：泛型、反射、注解、代理

> 本文档深入剖析泛型、反射、注解、动态代理四大核心技术之间的内在联系和协同工作机制

## 目录
- [技术概览](#技术概览)
- [核心联系](#核心联系)
- [技术协同](#技术协同)
- [实战案例](#实战案例)
- [框架应用](#框架应用)
- [最佳实践](#最佳实践)

---

## 技术概览

### 1. 泛型（Generics）
**本质**：编译时类型安全机制  
**作用**：提供类型参数化，避免类型转换  
**实现**：类型擦除（Type Erasure）  
**位置**：`java-core/src/main/java/com/fragment/core/generics/`

### 2. 反射（Reflection）
**本质**：运行时类型信息访问机制  
**作用**：动态获取类信息、调用方法、访问字段  
**实现**：基于JVM的元数据和Method Area  
**位置**：`java-core/src/main/java/com/fragment/core/reflection/`

### 3. 注解（Annotation）
**本质**：元数据标记机制  
**作用**：为代码元素添加结构化信息  
**实现**：编译器处理 + 反射读取  
**位置**：`java-core/src/main/java/com/fragment/core/annotations/`

### 4. 动态代理（Dynamic Proxy）
**本质**：运行时代理对象生成机制  
**作用**：在不修改原代码的情况下增强功能  
**实现**：JDK反射 + CGLIB字节码生成  
**位置**：`java-core/src/main/java/com/fragment/core/proxy/`

---

## 核心联系

### 一、反射是桥梁

反射是连接其他三项技术的核心桥梁：

```
         反射（Reflection）
            /    |    \
           /     |     \
          /      |      \
      泛型      注解     代理
   (运行时)   (读取)   (实现)
```

#### 1.1 反射 ↔ 泛型

**关系**：反射可以获取泛型的运行时信息

```java
// 泛型在编译时擦除，但某些场景下可通过反射获取
public class GenericHolder<T> {
    private List<String> items;  // 字段声明保留泛型信息
}

// 反射获取泛型信息
Field field = GenericHolder.class.getDeclaredField("items");
Type genericType = field.getGenericType();

if (genericType instanceof ParameterizedType) {
    ParameterizedType pt = (ParameterizedType) genericType;
    Type[] actualTypes = pt.getActualTypeArguments();
    // actualTypes[0] = String.class
}
```

**关键点**：
- 字段声明、方法参数、方法返回值的泛型信息会保留在字节码中
- 局部变量的泛型信息会被完全擦除
- 通过 `Type` 体系可以获取泛型的实际类型参数

**应用场景**：
- JSON 序列化框架（Jackson、Gson）
- ORM 框架（Hibernate、MyBatis）
- 依赖注入容器（Spring）

#### 1.2 反射 ↔ 注解

**关系**：反射是读取运行时注解的唯一方式

```java
// 定义运行时注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Transactional {
    String value() default "";
}

// 使用注解
public class UserService {
    @Transactional("readWrite")
    public void saveUser(User user) { }
}

// 通过反射读取注解
Method method = UserService.class.getMethod("saveUser", User.class);
Transactional annotation = method.getAnnotation(Transactional.class);
String txType = annotation.value(); // "readWrite"
```

**关键点**：
- 注解必须声明 `@Retention(RetentionPolicy.RUNTIME)` 才能被反射读取
- 反射提供了完整的注解读取API：`getAnnotation()`、`getDeclaredAnnotations()` 等
- 注解本身也是通过动态代理实现的

**应用场景**：
- Spring 的 `@Autowired`、`@Transactional`
- JUnit 的 `@Test`、`@Before`
- JAX-RS 的 `@Path`、`@GET`

#### 1.3 反射 ↔ 代理

**关系**：JDK动态代理完全基于反射实现

```java
// JDK动态代理的核心
public class LoggingHandler implements InvocationHandler {
    private final Object target;
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("调用方法: " + method.getName());
        // 使用反射调用目标方法
        return method.invoke(target, args);
    }
}

// 创建代理对象
UserService proxy = (UserService) Proxy.newProxyInstance(
    classLoader,
    new Class[]{UserService.class},
    new LoggingHandler(target)
);
```

**关键点**：
- `InvocationHandler.invoke()` 的 `Method` 参数就是反射的 `Method` 对象
- 代理对象的方法调用最终通过 `Method.invoke()` 反射调用
- CGLIB 虽然基于字节码，但也大量使用反射获取类信息

**应用场景**：
- Spring AOP
- MyBatis Mapper 接口
- RPC 框架（Dubbo、gRPC）

---

### 二、注解驱动的协同

注解作为元数据，将其他技术串联起来：

```
注解标记 → 反射读取 → 代理增强 → 泛型安全
```

#### 2.1 注解 + 反射 + 代理的经典组合

**场景**：Spring 的声明式事务

```java
// 1. 注解标记
@Service
public class UserService {
    @Transactional  // 注解标记需要事务
    public void transferMoney(Long from, Long to, BigDecimal amount) {
        // 业务逻辑
    }
}

// 2. 反射读取注解
Method method = UserService.class.getMethod("transferMoney", Long.class, Long.class, BigDecimal.class);
Transactional tx = method.getAnnotation(Transactional.class);

// 3. 代理增强
if (tx != null) {
    UserService proxy = (UserService) Proxy.newProxyInstance(
        classLoader,
        new Class[]{UserService.class},
        new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // 开启事务
                TransactionManager.begin();
                try {
                    Object result = method.invoke(target, args);
                    TransactionManager.commit();
                    return result;
                } catch (Exception e) {
                    TransactionManager.rollback();
                    throw e;
                }
            }
        }
    );
}
```

**工作流程**：
1. 开发者用注解标记方法
2. 框架启动时通过反射扫描注解
3. 为带注解的类创建代理对象
4. 代理对象在方法调用前后添加增强逻辑

#### 2.2 注解 + 反射 + 泛型的协同

**场景**：JSON 序列化框架

```java
// 1. 使用泛型 + 注解
public class User {
    @JsonProperty("user_id")
    private Long id;
    
    @JsonIgnore
    private String password;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date birthday;
    
    private List<String> roles;  // 泛型字段
}

// 2. 序列化框架的实现
public class JsonSerializer {
    public <T> String serialize(T obj) {
        Class<?> clazz = obj.getClass();
        StringBuilder json = new StringBuilder("{");
        
        for (Field field : clazz.getDeclaredFields()) {
            // 反射读取注解
            if (field.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
            
            field.setAccessible(true);
            Object value = field.get(obj);
            
            // 获取字段名（可能被注解修改）
            String fieldName = field.getName();
            JsonProperty jsonProp = field.getAnnotation(JsonProperty.class);
            if (jsonProp != null) {
                fieldName = jsonProp.value();
            }
            
            // 处理泛型类型
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                // 处理 List<String> 等泛型集合
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] actualTypes = pt.getActualTypeArguments();
                // 根据实际类型参数进行序列化
            }
            
            json.append("\"").append(fieldName).append("\":").append(serializeValue(value));
        }
        
        return json.append("}").toString();
    }
}
```

**协同机制**：
- **注解**：提供序列化配置（字段名映射、忽略字段、格式化）
- **反射**：遍历字段、读取注解、获取值
- **泛型**：确保类型安全，正确处理集合元素

---

### 三、泛型与代理的结合

#### 3.1 泛型代理工厂

```java
public class GenericProxyFactory {
    /**
     * 创建类型安全的代理对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(T target, Class<T> interfaceType, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[]{interfaceType},
            handler
        );
    }
    
    /**
     * 创建带泛型信息的代理
     */
    public static <T> T createTypedProxy(Class<T> interfaceType, InvocationHandler handler) {
        // 利用泛型确保类型安全
        return createProxy(null, interfaceType, handler);
    }
}

// 使用
UserService proxy = GenericProxyFactory.createTypedProxy(
    UserService.class,
    new LoggingHandler()
);
```

#### 3.2 泛型方法的代理处理

```java
public interface Repository<T, ID> {
    T findById(ID id);
    List<T> findAll();
    void save(T entity);
}

// 代理需要处理泛型方法
public class RepositoryProxyHandler<T, ID> implements InvocationHandler {
    private final Class<T> entityClass;
    private final Class<ID> idClass;
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 根据方法名和泛型类型参数进行处理
        if ("findById".equals(method.getName())) {
            ID id = (ID) args[0];  // 类型安全的转换
            return findEntityById(id);
        }
        
        // 获取方法的泛型返回类型
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType) {
            // 处理 List<T> 等泛型返回值
            ParameterizedType pt = (ParameterizedType) returnType;
            Type actualType = pt.getActualTypeArguments()[0];
            // 根据实际类型进行处理
        }
        
        return null;
    }
}
```

---

## 技术协同

### 协同模式一：注解驱动的泛型代理

**典型应用**：MyBatis Mapper 接口

```java
// 1. 定义泛型 Mapper 接口
public interface UserMapper extends BaseMapper<User, Long> {
    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(Long id);
    
    @Insert("INSERT INTO users (name, email) VALUES (#{name}, #{email})")
    void insert(User user);
    
    @Select("SELECT * FROM users WHERE age > #{age}")
    List<User> findByAgeGreaterThan(@Param("age") Integer age);
}

// 2. 框架实现（简化版）
public class MapperProxyFactory<T> {
    private final Class<T> mapperInterface;
    
    @SuppressWarnings("unchecked")
    public T newInstance() {
        return (T) Proxy.newProxyInstance(
            mapperInterface.getClassLoader(),
            new Class[]{mapperInterface},
            new MapperProxy<>(mapperInterface)
        );
    }
}

class MapperProxy<T> implements InvocationHandler {
    private final Class<T> mapperInterface;
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 通过反射读取注解
        Select selectAnnotation = method.getAnnotation(Select.class);
        Insert insertAnnotation = method.getAnnotation(Insert.class);
        
        String sql = null;
        if (selectAnnotation != null) {
            sql = selectAnnotation.value()[0];
        } else if (insertAnnotation != null) {
            sql = insertAnnotation.value()[0];
        }
        
        // 2. 获取泛型信息
        Type returnType = method.getGenericReturnType();
        Class<?> resultType = null;
        
        if (returnType instanceof Class) {
            resultType = (Class<?>) returnType;
        } else if (returnType instanceof ParameterizedType) {
            // 处理 List<User> 等泛型返回值
            ParameterizedType pt = (ParameterizedType) returnType;
            Type actualType = pt.getActualTypeArguments()[0];
            resultType = (Class<?>) actualType;
        }
        
        // 3. 执行SQL并映射结果
        return executeQuery(sql, args, resultType);
    }
    
    private Object executeQuery(String sql, Object[] args, Class<?> resultType) {
        // 执行SQL，使用反射将结果映射到对象
        return null;
    }
}
```

**技术协同点**：
- **泛型**：`BaseMapper<User, Long>` 提供类型安全
- **注解**：`@Select`、`@Insert` 提供SQL配置
- **反射**：读取注解、获取泛型信息、映射结果
- **代理**：动态实现接口方法

---

### 协同模式二：反射驱动的注解处理器

**典型应用**：依赖注入容器

```java
// 1. 定义注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Autowired {
    boolean required() default true;
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
    String value() default "";
}

// 2. 使用注解和泛型
@Component
public class UserService {
    @Autowired
    private UserRepository repository;
    
    @Autowired
    private List<UserValidator> validators;  // 泛型集合注入
    
    public void saveUser(User user) {
        validators.forEach(v -> v.validate(user));
        repository.save(user);
    }
}

// 3. 容器实现
public class SimpleIocContainer {
    private final Map<Class<?>, Object> beans = new ConcurrentHashMap<>();
    
    public void scan(String basePackage) throws Exception {
        // 扫描包下的所有类
        Set<Class<?>> classes = scanClasses(basePackage);
        
        for (Class<?> clazz : classes) {
            // 反射读取注解
            if (clazz.isAnnotationPresent(Component.class)) {
                // 创建实例
                Object instance = clazz.getDeclaredConstructor().newInstance();
                beans.put(clazz, instance);
            }
        }
        
        // 依赖注入
        for (Object bean : beans.values()) {
            injectDependencies(bean);
        }
    }
    
    private void injectDependencies(Object bean) throws Exception {
        Class<?> clazz = bean.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            // 反射读取 @Autowired 注解
            if (field.isAnnotationPresent(Autowired.class)) {
                field.setAccessible(true);
                
                // 获取字段的泛型类型
                Type genericType = field.getGenericType();
                
                if (genericType instanceof ParameterizedType) {
                    // 处理泛型集合注入 List<UserValidator>
                    ParameterizedType pt = (ParameterizedType) genericType;
                    Type rawType = pt.getRawType();
                    
                    if (rawType == List.class) {
                        Type actualType = pt.getActualTypeArguments()[0];
                        List<Object> instances = findAllInstancesOf((Class<?>) actualType);
                        field.set(bean, instances);
                    }
                } else {
                    // 普通类型注入
                    Object dependency = beans.get(field.getType());
                    field.set(bean, dependency);
                }
            }
        }
    }
    
    private List<Object> findAllInstancesOf(Class<?> type) {
        return beans.values().stream()
            .filter(bean -> type.isAssignableFrom(bean.getClass()))
            .collect(Collectors.toList());
    }
}
```

**技术协同点**：
- **注解**：`@Component`、`@Autowired` 标记组件和依赖
- **反射**：扫描类、读取注解、创建实例、注入依赖
- **泛型**：`List<UserValidator>` 类型安全的集合注入
- **代理**：可以为Bean创建代理实现AOP

---

### 协同模式三：泛型安全的AOP框架

```java
// 1. 定义切面注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cacheable {
    String key() default "";
    int ttl() default 3600;
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Logged {
    String value() default "";
}

// 2. 泛型AOP框架
public class GenericAopFramework {
    
    /**
     * 创建类型安全的AOP代理
     */
    @SuppressWarnings("unchecked")
    public static <T> T createAopProxy(T target, Class<T> interfaceType) {
        return (T) Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[]{interfaceType},
            new AopInvocationHandler<>(target, interfaceType)
        );
    }
}

class AopInvocationHandler<T> implements InvocationHandler {
    private final T target;
    private final Class<T> targetInterface;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    
    public AopInvocationHandler(T target, Class<T> targetInterface) {
        this.target = target;
        this.targetInterface = targetInterface;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 反射读取注解
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        Logged logged = method.getAnnotation(Logged.class);
        
        // 2. 处理缓存切面
        if (cacheable != null) {
            String cacheKey = generateCacheKey(method, args);
            
            if (cache.containsKey(cacheKey)) {
                System.out.println("[CACHE] 缓存命中: " + cacheKey);
                return cache.get(cacheKey);
            }
            
            Object result = method.invoke(target, args);
            
            // 3. 利用泛型信息验证返回值类型
            Type returnType = method.getGenericReturnType();
            if (returnType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) returnType;
                // 可以根据泛型类型进行特殊处理
            }
            
            cache.put(cacheKey, result);
            return result;
        }
        
        // 4. 处理日志切面
        if (logged != null) {
            System.out.println("[LOG] 调用方法: " + method.getName());
            long start = System.currentTimeMillis();
            
            try {
                Object result = method.invoke(target, args);
                System.out.println("[LOG] 方法执行成功，耗时: " + (System.currentTimeMillis() - start) + "ms");
                return result;
            } catch (Exception e) {
                System.out.println("[LOG] 方法执行失败: " + e.getMessage());
                throw e;
            }
        }
        
        return method.invoke(target, args);
    }
    
    private String generateCacheKey(Method method, Object[] args) {
        return method.getName() + "_" + Arrays.toString(args);
    }
}

// 3. 使用示例
public interface UserService {
    @Cacheable(key = "user", ttl = 600)
    @Logged("查询用户")
    User findById(Long id);
    
    @Logged("保存用户")
    void save(User user);
}

// 创建代理
UserService service = new UserServiceImpl();
UserService proxy = GenericAopFramework.createAopProxy(service, UserService.class);
```

**技术协同点**：
- **泛型**：`<T>` 确保代理对象类型安全
- **注解**：`@Cacheable`、`@Logged` 声明式配置切面
- **反射**：读取注解、调用方法、获取泛型信息
- **代理**：动态织入切面逻辑

---

## 实战案例

### 案例一：通用 DAO 框架

```java
// 1. 定义泛型基础接口
public interface BaseRepository<T, ID> {
    T findById(ID id);
    List<T> findAll();
    void save(T entity);
    void delete(ID id);
}

// 2. 定义注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
}

// 3. 实体类
@Table("users")
public class User {
    @Id
    @Column("id")
    private Long id;
    
    @Column("user_name")
    private String name;
    
    @Column("email")
    private String email;
    
    // getters and setters
}

// 4. 通用 DAO 实现
public class GenericDaoProxy<T, ID> implements InvocationHandler {
    private final Class<T> entityClass;
    private final Class<ID> idClass;
    
    public GenericDaoProxy(Class<T> entityClass, Class<ID> idClass) {
        this.entityClass = entityClass;
        this.idClass = idClass;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        // 根据方法名和泛型信息生成SQL
        if ("findById".equals(methodName)) {
            return findById((ID) args[0]);
        } else if ("findAll".equals(methodName)) {
            return findAll();
        } else if ("save".equals(methodName)) {
            save((T) args[0]);
            return null;
        } else if ("delete".equals(methodName)) {
            delete((ID) args[0]);
            return null;
        }
        
        return null;
    }
    
    private T findById(ID id) throws Exception {
        // 1. 通过反射读取 @Table 注解获取表名
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        String tableName = tableAnnotation.value();
        
        // 2. 通过反射找到 @Id 字段
        Field idField = null;
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idField = field;
                break;
            }
        }
        
        Column idColumn = idField.getAnnotation(Column.class);
        String idColumnName = idColumn.value();
        
        // 3. 生成SQL
        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumnName + " = ?";
        
        // 4. 执行查询并映射结果
        return executeQueryAndMap(sql, id);
    }
    
    private List<T> findAll() throws Exception {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        String tableName = tableAnnotation.value();
        
        String sql = "SELECT * FROM " + tableName;
        return executeQueryAndMapList(sql);
    }
    
    private void save(T entity) throws Exception {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        String tableName = tableAnnotation.value();
        
        // 通过反射获取所有字段和值
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                columns.add(column.value());
                
                field.setAccessible(true);
                values.add(field.get(entity));
            }
        }
        
        // 生成INSERT SQL
        String sql = "INSERT INTO " + tableName + " (" + 
                    String.join(", ", columns) + ") VALUES (" +
                    String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        
        executeUpdate(sql, values.toArray());
    }
    
    private void delete(ID id) throws Exception {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        String tableName = tableAnnotation.value();
        
        Field idField = null;
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idField = field;
                break;
            }
        }
        
        Column idColumn = idField.getAnnotation(Column.class);
        String idColumnName = idColumn.value();
        
        String sql = "DELETE FROM " + tableName + " WHERE " + idColumnName + " = ?";
        executeUpdate(sql, id);
    }
    
    private T executeQueryAndMap(String sql, Object... params) throws Exception {
        // 执行SQL并通过反射映射结果到对象
        // 模拟实现
        T instance = entityClass.getDeclaredConstructor().newInstance();
        
        // 假设从数据库获取了ResultSet
        // 通过反射设置字段值
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                String columnName = column.value();
                
                field.setAccessible(true);
                // Object value = resultSet.getObject(columnName);
                // field.set(instance, value);
            }
        }
        
        return instance;
    }
    
    private List<T> executeQueryAndMapList(String sql, Object... params) throws Exception {
        // 类似 executeQueryAndMap，但返回List
        return new ArrayList<>();
    }
    
    private void executeUpdate(String sql, Object... params) {
        // 执行更新SQL
    }
}

// 5. 工厂类
public class RepositoryFactory {
    @SuppressWarnings("unchecked")
    public static <T, ID> BaseRepository<T, ID> create(Class<T> entityClass, Class<ID> idClass) {
        return (BaseRepository<T, ID>) Proxy.newProxyInstance(
            BaseRepository.class.getClassLoader(),
            new Class<?>[]{BaseRepository.class},
            new GenericDaoProxy<>(entityClass, idClass)
        );
    }
}

// 6. 使用
BaseRepository<User, Long> userRepository = RepositoryFactory.create(User.class, Long.class);
User user = userRepository.findById(1L);
List<User> allUsers = userRepository.findAll();
```

**四大技术协同**：
- **泛型**：`BaseRepository<T, ID>` 提供类型安全的CRUD接口
- **注解**：`@Table`、`@Column`、`@Id` 提供ORM映射配置
- **反射**：读取注解、创建实例、设置字段值
- **代理**：动态实现Repository接口，生成SQL并执行

---

### 案例二：声明式缓存框架

```java
// 完整的声明式缓存实现
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cacheable {
    String key() default "";
    int ttl() default 3600;
    Class<?> keyGenerator() default DefaultKeyGenerator.class;
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheEvict {
    String key() default "";
    boolean allEntries() default false;
}

// 缓存代理工厂
public class CacheProxyFactory {
    private static final Map<String, Object> cache = new ConcurrentHashMap<>();
    
    @SuppressWarnings("unchecked")
    public static <T> T createCacheProxy(T target, Class<T> interfaceType) {
        return (T) Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[]{interfaceType},
            new CacheInvocationHandler<>(target)
        );
    }
    
    static class CacheInvocationHandler<T> implements InvocationHandler {
        private final T target;
        
        public CacheInvocationHandler(T target) {
            this.target = target;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理 @Cacheable
            Cacheable cacheable = method.getAnnotation(Cacheable.class);
            if (cacheable != null) {
                String cacheKey = generateKey(cacheable, method, args);
                
                if (cache.containsKey(cacheKey)) {
                    System.out.println("[CACHE] 命中缓存: " + cacheKey);
                    return cache.get(cacheKey);
                }
                
                Object result = method.invoke(target, args);
                cache.put(cacheKey, result);
                System.out.println("[CACHE] 存入缓存: " + cacheKey);
                
                // 设置过期时间（简化实现）
                scheduleEviction(cacheKey, cacheable.ttl());
                
                return result;
            }
            
            // 处理 @CacheEvict
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            if (cacheEvict != null) {
                if (cacheEvict.allEntries()) {
                    cache.clear();
                    System.out.println("[CACHE] 清空所有缓存");
                } else {
                    String cacheKey = generateKey(cacheEvict.key(), method, args);
                    cache.remove(cacheKey);
                    System.out.println("[CACHE] 移除缓存: " + cacheKey);
                }
            }
            
            return method.invoke(target, args);
        }
        
        private String generateKey(Cacheable cacheable, Method method, Object[] args) {
            if (!cacheable.key().isEmpty()) {
                return cacheable.key();
            }
            
            // 使用方法名和参数生成key
            return method.getName() + "_" + Arrays.toString(args);
        }
        
        private String generateKey(String key, Method method, Object[] args) {
            if (!key.isEmpty()) {
                return key;
            }
            return method.getName() + "_" + Arrays.toString(args);
        }
        
        private void scheduleEviction(String key, int ttl) {
            // 简化实现：实际应使用ScheduledExecutorService
            new Thread(() -> {
                try {
                    Thread.sleep(ttl * 1000L);
                    cache.remove(key);
                    System.out.println("[CACHE] 缓存过期: " + key);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}

// 使用示例
public interface ProductService {
    @Cacheable(key = "product", ttl = 600)
    Product findById(Long id);
    
    @Cacheable(key = "products", ttl = 300)
    List<Product> findAll();
    
    @CacheEvict(key = "product", allEntries = true)
    void updateProduct(Product product);
}
```

---

## 框架应用

### Spring 框架中的四大技术协同

#### 1. Spring Bean 容器

```java
// Spring 如何整合四大技术

// 注解定义
@Component  // 标记为Spring组件
@Scope("singleton")  // 作用域
public class UserService {
    
    @Autowired  // 依赖注入
    private UserRepository repository;
    
    @Transactional  // 声明式事务
    @Cacheable("users")  // 声明式缓存
    public User findById(Long id) {
        return repository.findById(id);
    }
}

// Spring 内部实现（简化）
class SpringBeanFactory {
    private Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    
    public Object getBean(Class<?> beanClass) {
        // 1. 检查单例缓存
        if (singletons.containsKey(beanClass)) {
            return singletons.get(beanClass);
        }
        
        // 2. 创建实例
        Object instance = createInstance(beanClass);
        
        // 3. 依赖注入（反射 + 注解）
        injectDependencies(instance);
        
        // 4. 创建代理（如果需要AOP）
        Object proxy = createProxyIfNeeded(instance, beanClass);
        
        // 5. 缓存单例
        singletons.put(beanClass, proxy);
        
        return proxy;
    }
    
    private Object createInstance(Class<?> beanClass) throws Exception {
        return beanClass.getDeclaredConstructor().newInstance();
    }
    
    private void injectDependencies(Object instance) throws Exception {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                field.setAccessible(true);
                Object dependency = getBean(field.getType());
                field.set(instance, dependency);
            }
        }
    }
    
    private Object createProxyIfNeeded(Object instance, Class<?> beanClass) {
        boolean needsProxy = false;
        
        // 检查是否有需要代理的注解
        for (Method method : beanClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Transactional.class) ||
                method.isAnnotationPresent(Cacheable.class)) {
                needsProxy = true;
                break;
            }
        }
        
        if (needsProxy) {
            return Proxy.newProxyInstance(
                beanClass.getClassLoader(),
                beanClass.getInterfaces(),
                new SpringAopHandler(instance)
            );
        }
        
        return instance;
    }
}

class SpringAopHandler implements InvocationHandler {
    private final Object target;
    
    public SpringAopHandler(Object target) {
        this.target = target;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 @Transactional
        if (method.isAnnotationPresent(Transactional.class)) {
            return handleTransaction(method, args);
        }
        
        // 处理 @Cacheable
        if (method.isAnnotationPresent(Cacheable.class)) {
            return handleCache(method, args);
        }
        
        return method.invoke(target, args);
    }
    
    private Object handleTransaction(Method method, Object[] args) throws Throwable {
        System.out.println("[TX] 开启事务");
        try {
            Object result = method.invoke(target, args);
            System.out.println("[TX] 提交事务");
            return result;
        } catch (Exception e) {
            System.out.println("[TX] 回滚事务");
            throw e;
        }
    }
    
    private Object handleCache(Method method, Object[] args) throws Throwable {
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        String cacheKey = cacheable.value()[0] + "_" + Arrays.toString(args);
        
        // 缓存逻辑
        return method.invoke(target, args);
    }
}
```

#### 2. MyBatis 中的应用

```java
// MyBatis Mapper 接口
public interface UserMapper {
    @Select("SELECT * FROM users WHERE id = #{id}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "name", column = "user_name"),
        @Result(property = "orders", column = "id", 
                many = @Many(select = "findOrdersByUserId"))
    })
    User findById(@Param("id") Long id);
    
    @Insert("INSERT INTO users (name, email) VALUES (#{name}, #{email})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);
}

// MyBatis 内部实现（简化）
class MapperProxyFactory {
    public static <T> T getMapper(Class<T> mapperInterface, SqlSession sqlSession) {
        return (T) Proxy.newProxyInstance(
            mapperInterface.getClassLoader(),
            new Class[]{mapperInterface},
            new MapperProxy<>(mapperInterface, sqlSession)
        );
    }
}

class MapperProxy<T> implements InvocationHandler {
    private final Class<T> mapperInterface;
    private final SqlSession sqlSession;
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 读取SQL注解
        Select select = method.getAnnotation(Select.class);
        Insert insert = method.getAnnotation(Insert.class);
        
        String sql = null;
        if (select != null) {
            sql = select.value()[0];
        } else if (insert != null) {
            sql = insert.value()[0];
        }
        
        // 2. 解析参数（处理 @Param 注解）
        Map<String, Object> paramMap = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Param param = parameters[i].getAnnotation(Param.class);
            if (param != null) {
                paramMap.put(param.value(), args[i]);
            }
        }
        
        // 3. 获取返回类型（可能是泛型）
        Type returnType = method.getGenericReturnType();
        Class<?> resultType = null;
        
        if (returnType instanceof Class) {
            resultType = (Class<?>) returnType;
        } else if (returnType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) returnType;
            resultType = (Class<?>) pt.getActualTypeArguments()[0];
        }
        
        // 4. 执行SQL并映射结果
        if (select != null) {
            return sqlSession.selectOne(sql, paramMap, resultType);
        } else if (insert != null) {
            sqlSession.insert(sql, paramMap);
            
            // 处理 @Options(useGeneratedKeys = true)
            Options options = method.getAnnotation(Options.class);
            if (options != null && options.useGeneratedKeys()) {
                // 通过反射设置生成的主键
                String keyProperty = options.keyProperty();
                Field field = args[0].getClass().getDeclaredField(keyProperty);
                field.setAccessible(true);
                field.set(args[0], getGeneratedKey());
            }
            
            return null;
        }
        
        return null;
    }
    
    private Long getGeneratedKey() {
        // 获取数据库生成的主键
        return 1L;
    }
}
```

---

## 最佳实践

### 1. 设计原则

#### 原则一：注解优于配置
```java
// 好的做法：使用注解
@Service
@Transactional
public class UserService {
    @Autowired
    private UserRepository repository;
}

// 避免：XML配置
// <bean id="userService" class="UserService">
//     <property name="repository" ref="userRepository"/>
// </bean>
```

#### 原则二：泛型确保类型安全
```java
// 好的做法：使用泛型
public interface Repository<T, ID> {
    T findById(ID id);
    List<T> findAll();
}

// 避免：使用Object
public interface Repository {
    Object findById(Object id);  // 类型不安全
    List findAll();  // 原始类型
}
```

#### 原则三：反射用于框架，避免业务代码
```java
// 框架代码：可以使用反射
public class Framework {
    public void processAnnotations(Object bean) {
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                // 反射注入
            }
        }
    }
}

// 业务代码：避免反射
public class UserService {
    // 好的做法：直接调用
    public void saveUser(User user) {
        repository.save(user);
    }
    
    // 避免：使用反射
    public void saveUser(User user) throws Exception {
        Method method = repository.getClass().getMethod("save", User.class);
        method.invoke(repository, user);
    }
}
```

#### 原则四：代理用于横切关注点
```java
// 适合使用代理的场景
@Transactional  // 事务管理
@Cacheable  // 缓存
@Logged  // 日志
@Secured  // 安全
public void businessMethod() {
    // 业务逻辑
}

// 不适合使用代理的场景
public void simpleGetter() {
    return this.field;  // 简单方法不需要代理
}
```

### 2. 性能优化

#### 优化一：缓存反射对象
```java
public class ReflectionCache {
    private static final Map<String, Method> methodCache = new ConcurrentHashMap<>();
    private static final Map<String, Field> fieldCache = new ConcurrentHashMap<>();
    
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        String key = clazz.getName() + "#" + methodName + "#" + Arrays.toString(paramTypes);
        return methodCache.computeIfAbsent(key, k -> {
            try {
                return clazz.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
```

#### 优化二：减少代理层级
```java
// 避免：多层代理嵌套
Object proxy1 = createProxy(target);
Object proxy2 = createProxy(proxy1);  // 性能损失
Object proxy3 = createProxy(proxy2);  // 更多损失

// 推荐：合并切面
Object proxy = createProxyWithMultipleAspects(target, aspect1, aspect2, aspect3);
```

#### 优化三：选择合适的代理类型
```java
// 有接口：使用JDK代理
public interface UserService { }
UserService proxy = (UserService) Proxy.newProxyInstance(...);

// 无接口且性能敏感：使用CGLIB
public class Calculator { }
Calculator proxy = (Calculator) enhancer.create();

// 编译时确定：考虑APT或AspectJ
@Aspect
public class LoggingAspect {
    @Around("execution(* com.example..*(..))")
    public Object log(ProceedingJoinPoint pjp) { }
}
```

### 3. 常见陷阱

#### 陷阱一：泛型擦除
```java
// 问题：运行时无法获取泛型参数
public <T> void process(List<T> list) {
    // 无法获取T的实际类型
}

// 解决方案1：传递Class参数
public <T> void process(List<T> list, Class<T> clazz) {
    // 可以使用clazz
}

// 解决方案2：使用TypeToken（Gson方式）
Type type = new TypeToken<List<String>>(){}.getType();
```

#### 陷阱二：注解继承
```java
// 问题：注解默认不继承
@Transactional
public class BaseService { }

public class UserService extends BaseService {
    // @Transactional 不会被继承
}

// 解决方案：使用 @Inherited
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Transactional { }
```

#### 陷阱三：代理的自调用问题
```java
@Service
public class UserService {
    @Transactional
    public void methodA() {
        this.methodB();  // 问题：直接调用不会触发代理
    }
    
    @Transactional
    public void methodB() {
        // 事务不会生效
    }
}

// 解决方案1：注入自己
@Service
public class UserService {
    @Autowired
    private UserService self;  // Spring会注入代理对象
    
    @Transactional
    public void methodA() {
        self.methodB();  // 通过代理调用
    }
}

// 解决方案2：使用AopContext
@Transactional
public void methodA() {
    ((UserService) AopContext.currentProxy()).methodB();
}
```

---

## 总结

### 技术关系图

```
                    ┌─────────────┐
                    │   Java 字节码  │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
         ┌────▼────┐  ┌───▼────┐  ┌───▼────┐
         │  泛型    │  │  注解   │  │  反射   │
         │(编译时)  │  │(元数据) │  │(运行时) │
         └────┬────┘  └───┬────┘  └───┬────┘
              │            │            │
              └────────────┼────────────┘
                           │
                      ┌────▼────┐
                      │  动态代理 │
                      │(运行时)  │
                      └─────────┘
```

### 核心要点

1. **反射是桥梁**：连接泛型、注解、代理的核心技术
2. **注解是配置**：声明式编程的基础，通过反射读取
3. **泛型是约束**：编译时类型安全，运行时部分可通过反射获取
4. **代理是增强**：基于反射实现，结合注解实现AOP

### 应用建议

1. **框架开发**：充分利用四大技术的协同
2. **业务开发**：优先使用注解，避免直接使用反射
3. **性能优化**：缓存反射对象，选择合适的代理类型
4. **代码质量**：遵循设计原则，避免常见陷阱

### 学习路径

1. **基础学习**：分别掌握四大技术的基本概念
2. **深入理解**：学习底层实现原理
3. **协同应用**：理解技术之间的联系
4. **框架源码**：阅读Spring、MyBatis等框架源码
5. **实战项目**：在项目中应用这些技术

---

## 参考资料

### 本项目示例代码
- **泛型**：`java-core/src/main/java/com/fragment/core/generics/`
- **反射**：`java-core/src/main/java/com/fragment/core/reflection/`
- **注解**：`java-core/src/main/java/com/fragment/core/annotations/`
- **代理**：`java-core/src/main/java/com/fragment/core/proxy/`

### 推荐阅读
- 《Effective Java》- Joshua Bloch
- 《深入理解Java虚拟机》- 周志明
- 《Spring源码深度解析》- 郝佳
- Java官方文档：Reflection API、Proxy、Generics

---

**最后更新时间**：2025-12-21  
**文档版本**：v1.0
