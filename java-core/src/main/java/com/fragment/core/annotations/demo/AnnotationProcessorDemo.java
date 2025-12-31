package com.fragment.core.annotations.demo;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注解处理器演示
 * 
 * 演示如何实现类似框架的注解处理功能：
 * 1. 依赖注入（类似Spring的@Autowired）
 * 2. ORM映射（类似JPA的@Entity）
 * 3. 数据验证（类似Hibernate Validator）
 * 4. 缓存（类似Spring Cache）
 * 
 * @author fragment
 */
public class AnnotationProcessorDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 注解处理器演示 ===\n");
        
        // 演示1: 依赖注入
        demonstrateDependencyInjection();
        
        // 演示2: ORM映射
        demonstrateORMMapping();
        
        // 演示3: 数据验证
        demonstrateValidation();
        
        // 演示4: 缓存
        demonstrateCache();
    }

    /**
     * 演示1: 依赖注入
     */
    private static void demonstrateDependencyInjection() throws Exception {
        System.out.println("【演示1: 依赖注入】\n");
        
        // 创建简单的IoC容器
        SimpleIoCContainer container = new SimpleIoCContainer();
        
        // 注册Bean
        container.register(UserDao.class);
        container.register(UserService.class);
        
        // 获取Bean（会自动注入依赖）
        UserService userService = container.getBean(UserService.class);
        
        // 使用Bean
        userService.createUser("张三");
        
        System.out.println();
    }

    /**
     * 演示2: ORM映射
     */
    private static void demonstrateORMMapping() throws Exception {
        System.out.println("【演示2: ORM映射】\n");
        
        // 创建实体对象
        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone 15");
        product.setPrice(5999.0);
        product.setStock(100);
        
        // 生成SQL
        SimpleSQLGenerator sqlGenerator = new SimpleSQLGenerator();
        
        String insertSql = sqlGenerator.generateInsertSQL(product);
        System.out.println("INSERT语句:");
        System.out.println(insertSql);
        
        String updateSql = sqlGenerator.generateUpdateSQL(product);
        System.out.println("\nUPDATE语句:");
        System.out.println(updateSql);
        
        String selectSql = sqlGenerator.generateSelectSQL(Product.class, 1L);
        System.out.println("\nSELECT语句:");
        System.out.println(selectSql);
        
        String deleteSql = sqlGenerator.generateDeleteSQL(Product.class, 1L);
        System.out.println("\nDELETE语句:");
        System.out.println(deleteSql);
        
        System.out.println();
    }

    /**
     * 演示3: 数据验证
     */
    private static void demonstrateValidation() throws Exception {
        System.out.println("【演示3: 数据验证】\n");
        
        SimpleValidator validator = new SimpleValidator();
        
        // 测试有效数据
        System.out.println("测试1: 有效数据");
        RegisterForm validForm = new RegisterForm();
        validForm.setUsername("zhangsan");
        validForm.setPassword("123456");
        validForm.setEmail("zhangsan@example.com");
        validForm.setAge(25);
        
        List<String> errors1 = validator.validate(validForm);
        if (errors1.isEmpty()) {
            System.out.println("✓ 验证通过");
        } else {
            System.out.println("✗ 验证失败:");
            errors1.forEach(error -> System.out.println("  - " + error));
        }
        
        // 测试无效数据
        System.out.println("\n测试2: 无效数据");
        RegisterForm invalidForm = new RegisterForm();
        invalidForm.setUsername("ab");  // 太短
        invalidForm.setPassword("123");  // 太短
        invalidForm.setEmail("invalid");  // 格式错误
        invalidForm.setAge(15);  // 太小
        
        List<String> errors2 = validator.validate(invalidForm);
        if (errors2.isEmpty()) {
            System.out.println("✓ 验证通过");
        } else {
            System.out.println("✗ 验证失败:");
            errors2.forEach(error -> System.out.println("  - " + error));
        }
        
        System.out.println();
    }

    /**
     * 演示4: 缓存
     */
    private static void demonstrateCache() throws Exception {
        System.out.println("【演示4: 缓存】\n");
        
        ProductService productService = new ProductService();
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        // 创建代理对象（支持缓存）
        ProductService cachedService = cacheManager.createProxy(productService);
        
        // 第一次调用（会执行方法）
        System.out.println("第一次调用 getProduct(1):");
        Product product1 = cachedService.getProduct(1L);
        System.out.println("结果: " + product1.getName());
        
        // 第二次调用（从缓存获取）
        System.out.println("\n第二次调用 getProduct(1):");
        Product product2 = cachedService.getProduct(1L);
        System.out.println("结果: " + product2.getName());
        
        // 更新产品（清除缓存）
        System.out.println("\n调用 updateProduct:");
        product1.setName("iPhone 15 Pro");
        cachedService.updateProduct(product1);
        
        // 再次调用（缓存已清除，会重新执行方法）
        System.out.println("\n第三次调用 getProduct(1):");
        Product product3 = cachedService.getProduct(1L);
        System.out.println("结果: " + product3.getName());
        
        System.out.println();
    }

    // ========== 注解定义 ==========

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Component {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Autowired {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Entity {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Table {
        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Id {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Column {
        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface NotNull {
        String message() default "不能为空";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Size {
        int min() default 0;
        int max() default Integer.MAX_VALUE;
        String message() default "长度不符合要求";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Email {
        String message() default "邮箱格式不正确";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Min {
        int value();
        String message() default "值太小";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Cacheable {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface CacheEvict {
        String value();
    }

    // ========== 简单IoC容器实现 ==========

    static class SimpleIoCContainer {
        private Map<Class<?>, Object> beans = new ConcurrentHashMap<>();

        public void register(Class<?> clazz) throws Exception {
            // 检查是否有@Component注解
            if (!clazz.isAnnotationPresent(Component.class)) {
                throw new IllegalArgumentException(clazz + " 没有@Component注解");
            }

            // 创建实例
            Object bean = clazz.newInstance();
            beans.put(clazz, bean);

            // 注入依赖
            injectDependencies(bean);

            System.out.println("✓ 注册Bean: " + clazz.getSimpleName());
        }

        private void injectDependencies(Object bean) throws Exception {
            Class<?> clazz = bean.getClass();

            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    Class<?> fieldType = field.getType();
                    Object dependency = beans.get(fieldType);

                    if (dependency == null) {
                        throw new RuntimeException("找不到类型为 " + fieldType + " 的Bean");
                    }

                    field.setAccessible(true);
                    field.set(bean, dependency);

                    System.out.println("  ✓ 注入依赖: " + field.getName() + " -> " + fieldType.getSimpleName());
                }
            }
        }

        public <T> T getBean(Class<T> clazz) {
            return (T) beans.get(clazz);
        }
    }

    // ========== 简单SQL生成器实现 ==========

    static class SimpleSQLGenerator {
        public String generateInsertSQL(Object entity) throws Exception {
            Class<?> clazz = entity.getClass();
            String tableName = getTableName(clazz);

            List<String> columns = new ArrayList<>();
            List<String> values = new ArrayList<>();

            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class)) {
                    Column column = field.getAnnotation(Column.class);
                    columns.add(column.name());

                    field.setAccessible(true);
                    Object value = field.get(entity);
                    values.add(formatValue(value));
                }
            }

            return String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName,
                String.join(", ", columns),
                String.join(", ", values));
        }

        public String generateUpdateSQL(Object entity) throws Exception {
            Class<?> clazz = entity.getClass();
            String tableName = getTableName(clazz);

            List<String> sets = new ArrayList<>();
            String idColumn = null;
            Object idValue = null;

            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(entity);

                if (field.isAnnotationPresent(Id.class)) {
                    Column column = field.getAnnotation(Column.class);
                    idColumn = column.name();
                    idValue = value;
                } else if (field.isAnnotationPresent(Column.class)) {
                    Column column = field.getAnnotation(Column.class);
                    sets.add(column.name() + " = " + formatValue(value));
                }
            }

            return String.format("UPDATE %s SET %s WHERE %s = %s",
                tableName,
                String.join(", ", sets),
                idColumn,
                formatValue(idValue));
        }

        public String generateSelectSQL(Class<?> clazz, Object id) {
            String tableName = getTableName(clazz);
            String idColumn = getIdColumn(clazz);
            return String.format("SELECT * FROM %s WHERE %s = %s",
                tableName, idColumn, formatValue(id));
        }

        public String generateDeleteSQL(Class<?> clazz, Object id) {
            String tableName = getTableName(clazz);
            String idColumn = getIdColumn(clazz);
            return String.format("DELETE FROM %s WHERE %s = %s",
                tableName, idColumn, formatValue(id));
        }

        private String getTableName(Class<?> clazz) {
            if (clazz.isAnnotationPresent(Table.class)) {
                return clazz.getAnnotation(Table.class).name();
            }
            return clazz.getSimpleName().toLowerCase();
        }

        private String getIdColumn(Class<?> clazz) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class) && field.isAnnotationPresent(Column.class)) {
                    return field.getAnnotation(Column.class).name();
                }
            }
            return "id";
        }

        private String formatValue(Object value) {
            if (value == null) return "NULL";
            if (value instanceof String) return "'" + value + "'";
            return value.toString();
        }
    }

    // ========== 简单验证器实现 ==========

    static class SimpleValidator {
        public List<String> validate(Object obj) throws Exception {
            List<String> errors = new ArrayList<>();
            Class<?> clazz = obj.getClass();

            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(obj);

                // @NotNull验证
                if (field.isAnnotationPresent(NotNull.class)) {
                    if (value == null) {
                        NotNull annotation = field.getAnnotation(NotNull.class);
                        errors.add(field.getName() + ": " + annotation.message());
                    }
                }

                // @Size验证
                if (field.isAnnotationPresent(Size.class) && value != null) {
                    Size annotation = field.getAnnotation(Size.class);
                    int length = value.toString().length();
                    if (length < annotation.min() || length > annotation.max()) {
                        errors.add(field.getName() + ": " + annotation.message() + 
                            " (当前长度: " + length + ", 要求: " + annotation.min() + "-" + annotation.max() + ")");
                    }
                }

                // @Email验证
                if (field.isAnnotationPresent(Email.class) && value != null) {
                    String email = value.toString();
                    if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                        Email annotation = field.getAnnotation(Email.class);
                        errors.add(field.getName() + ": " + annotation.message());
                    }
                }

                // @Min验证
                if (field.isAnnotationPresent(Min.class) && value != null) {
                    Min annotation = field.getAnnotation(Min.class);
                    int intValue = ((Number) value).intValue();
                    if (intValue < annotation.value()) {
                        errors.add(field.getName() + ": " + annotation.message() + 
                            " (当前值: " + intValue + ", 最小值: " + annotation.value() + ")");
                    }
                }
            }

            return errors;
        }
    }

    // ========== 简单缓存管理器实现 ==========

    static class SimpleCacheManager {
        private Map<String, Object> cache = new ConcurrentHashMap<>();

        public <T> T createProxy(T target) {
            return (T) java.lang.reflect.Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                (proxy, method, args) -> {
                    // 检查@Cacheable注解
                    if (method.isAnnotationPresent(Cacheable.class)) {
                        Cacheable cacheable = method.getAnnotation(Cacheable.class);
                        String cacheKey = cacheable.value() + ":" + Arrays.toString(args);

                        // 从缓存获取
                        if (cache.containsKey(cacheKey)) {
                            System.out.println("  ✓ 从缓存获取: " + cacheKey);
                            return cache.get(cacheKey);
                        }

                        // 执行方法
                        System.out.println("  ✓ 执行方法并缓存: " + cacheKey);
                        Object result = method.invoke(target, args);
                        cache.put(cacheKey, result);
                        return result;
                    }

                    // 检查@CacheEvict注解
                    if (method.isAnnotationPresent(CacheEvict.class)) {
                        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
                        String cachePrefix = cacheEvict.value() + ":";

                        // 清除缓存
                        cache.keySet().removeIf(key -> key.startsWith(cachePrefix));
                        System.out.println("  ✓ 清除缓存: " + cachePrefix + "*");
                    }

                    // 执行方法
                    return method.invoke(target, args);
                }
            );
        }
    }

    // ========== 测试类 ==========

    @Component
    static class UserDao {
        public void save(String username) {
            System.out.println("  ✓ UserDao.save: " + username);
        }
    }

    @Component
    static class UserService {
        @Autowired
        private UserDao userDao;

        public void createUser(String username) {
            System.out.println("✓ UserService.createUser: " + username);
            userDao.save(username);
        }
    }

    @Entity
    @Table(name = "t_product")
    static class Product {
        @Id
        @Column(name = "id")
        private Long id;

        @Column(name = "name")
        private String name;

        @Column(name = "price")
        private Double price;

        @Column(name = "stock")
        private Integer stock;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
    }

    static class RegisterForm {
        @NotNull(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
        private String username;

        @NotNull(message = "密码不能为空")
        @Size(min = 6, max = 20, message = "密码长度必须在6-20之间")
        private String password;

        @NotNull(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        @NotNull(message = "年龄不能为空")
        @Min(value = 18, message = "年龄必须大于等于18岁")
        private Integer age;

        // Getters and Setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    interface ProductServiceInterface {
        Product getProduct(Long id);
        void updateProduct(Product product);
    }

    static class ProductService implements ProductServiceInterface {
        private Map<Long, Product> database = new ConcurrentHashMap<>();

        public ProductService() {
            // 初始化数据
            Product product = new Product();
            product.setId(1L);
            product.setName("iPhone 15");
            product.setPrice(5999.0);
            product.setStock(100);
            database.put(1L, product);
        }

        @Cacheable("products")
        public Product getProduct(Long id) {
            System.out.println("  ✓ 从数据库查询产品: " + id);
            return database.get(id);
        }

        @CacheEvict("products")
        public void updateProduct(Product product) {
            System.out.println("  ✓ 更新数据库中的产品: " + product.getId());
            database.put(product.getId(), product);
        }
    }
}
