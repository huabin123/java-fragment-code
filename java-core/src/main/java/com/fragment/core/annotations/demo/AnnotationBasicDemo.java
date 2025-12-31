package com.fragment.core.annotations.demo;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 注解基础演示
 * 
 * 演示内容：
 * 1. 注解的定义
 * 2. 注解的使用
 * 3. 通过反射读取注解
 * 4. 元注解的作用
 * 
 * @author fragment
 */
public class AnnotationBasicDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 注解基础演示 ===\n");
        
        // 演示1: 读取类上的注解
        demonstrateClassAnnotation();
        
        // 演示2: 读取字段上的注解
        demonstrateFieldAnnotation();
        
        // 演示3: 读取方法上的注解
        demonstrateMethodAnnotation();
        
        // 演示4: 注解的继承
        demonstrateAnnotationInheritance();
        
        // 演示5: 可重复注解
        demonstrateRepeatableAnnotation();
    }

    /**
     * 演示1: 读取类上的注解
     */
    private static void demonstrateClassAnnotation() {
        System.out.println("【演示1: 读取类上的注解】");
        
        Class<User> clazz = User.class;
        
        // 1. 检查是否有@Entity注解
        if (clazz.isAnnotationPresent(Entity.class)) {
            System.out.println("✓ User类有@Entity注解");
            
            Entity entity = clazz.getAnnotation(Entity.class);
            System.out.println("  - 描述: " + entity.description());
        }
        
        // 2. 检查是否有@Table注解
        if (clazz.isAnnotationPresent(Table.class)) {
            System.out.println("✓ User类有@Table注解");
            
            Table table = clazz.getAnnotation(Table.class);
            System.out.println("  - 表名: " + table.name());
            System.out.println("  - Schema: " + table.schema());
        }
        
        // 3. 获取所有注解
        System.out.println("\n所有注解:");
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
            System.out.println("  - " + annotation.annotationType().getSimpleName());
        }
        
        System.out.println();
    }

    /**
     * 演示2: 读取字段上的注解
     */
    private static void demonstrateFieldAnnotation() throws Exception {
        System.out.println("【演示2: 读取字段上的注解】");
        
        Class<User> clazz = User.class;
        
        // 遍历所有字段
        for (Field field : clazz.getDeclaredFields()) {
            System.out.println("字段: " + field.getName());
            
            // 检查@Id注解
            if (field.isAnnotationPresent(Id.class)) {
                System.out.println("  ✓ 这是主键字段");
            }
            
            // 检查@Column注解
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                System.out.println("  ✓ 列名: " + column.name());
                System.out.println("  ✓ 是否可空: " + column.nullable());
                System.out.println("  ✓ 长度: " + column.length());
            }
            
            System.out.println();
        }
    }

    /**
     * 演示3: 读取方法上的注解
     */
    private static void demonstrateMethodAnnotation() throws Exception {
        System.out.println("【演示3: 读取方法上的注解】");
        
        Class<UserService> clazz = UserService.class;
        
        // 遍历所有方法
        for (Method method : clazz.getDeclaredMethods()) {
            System.out.println("方法: " + method.getName());
            
            // 检查@Transactional注解
            if (method.isAnnotationPresent(Transactional.class)) {
                Transactional tx = method.getAnnotation(Transactional.class);
                System.out.println("  ✓ 需要事务");
                System.out.println("  ✓ 传播行为: " + tx.propagation());
                System.out.println("  ✓ 隔离级别: " + tx.isolation());
                System.out.println("  ✓ 超时时间: " + tx.timeout() + "秒");
            }
            
            // 检查@CacheEvict注解
            if (method.isAnnotationPresent(CacheEvict.class)) {
                CacheEvict cache = method.getAnnotation(CacheEvict.class);
                System.out.println("  ✓ 清除缓存");
                System.out.println("  ✓ 缓存名称: " + cache.value());
            }
            
            System.out.println();
        }
    }

    /**
     * 演示4: 注解的继承
     */
    private static void demonstrateAnnotationInheritance() {
        System.out.println("【演示4: 注解的继承】");
        
        // 父类有@InheritedAnnotation注解
        System.out.println("父类(Parent):");
        if (Parent.class.isAnnotationPresent(InheritedAnnotation.class)) {
            InheritedAnnotation annotation = Parent.class.getAnnotation(InheritedAnnotation.class);
            System.out.println("  ✓ 有@InheritedAnnotation注解");
            System.out.println("  ✓ 值: " + annotation.value());
        }
        
        // 子类会继承@InheritedAnnotation注解
        System.out.println("\n子类(Child):");
        if (Child.class.isAnnotationPresent(InheritedAnnotation.class)) {
            InheritedAnnotation annotation = Child.class.getAnnotation(InheritedAnnotation.class);
            System.out.println("  ✓ 继承了@InheritedAnnotation注解");
            System.out.println("  ✓ 值: " + annotation.value());
        }
        
        // 对比：非继承注解
        System.out.println("\n对比：非继承注解");
        System.out.println("父类有@NonInheritedAnnotation: " + 
            Parent.class.isAnnotationPresent(NonInheritedAnnotation.class));
        System.out.println("子类有@NonInheritedAnnotation: " + 
            Child.class.isAnnotationPresent(NonInheritedAnnotation.class));
        
        System.out.println();
    }

    /**
     * 演示5: 可重复注解（Java 8+）
     */
    private static void demonstrateRepeatableAnnotation() throws Exception {
        System.out.println("【演示5: 可重复注解】");
        
        Class<TaskScheduler> clazz = TaskScheduler.class;
        Method method = clazz.getMethod("backupDatabase");
        
        // 方式1: 获取容器注解
        System.out.println("方式1: 通过容器注解获取");
        Schedules schedules = method.getAnnotation(Schedules.class);
        if (schedules != null) {
            for (Schedule schedule : schedules.value()) {
                System.out.println("  - " + schedule.day() + " " + schedule.time());
            }
        }
        
        // 方式2: 直接获取可重复注解（推荐）
        System.out.println("\n方式2: 直接获取可重复注解（推荐）");
        Schedule[] scheduleArray = method.getAnnotationsByType(Schedule.class);
        for (Schedule schedule : scheduleArray) {
            System.out.println("  - " + schedule.day() + " " + schedule.time());
        }
        
        System.out.println();
    }

    // ========== 注解定义 ==========

    /**
     * 实体注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    public @interface Entity {
        String description() default "";
    }

    /**
     * 表注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    public @interface Table {
        String name();
        String schema() default "public";
    }

    /**
     * 主键注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Id {
    }

    /**
     * 列注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Column {
        String name();
        boolean nullable() default true;
        int length() default 255;
    }

    /**
     * 事务注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Transactional {
        String propagation() default "REQUIRED";
        String isolation() default "DEFAULT";
        int timeout() default 30;
    }

    /**
     * 缓存清除注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface CacheEvict {
        String value();
    }

    /**
     * 可继承注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Documented
    public @interface InheritedAnnotation {
        String value();
    }

    /**
     * 不可继承注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    public @interface NonInheritedAnnotation {
        String value();
    }

    /**
     * 调度注解（可重复）
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Schedules.class)
    @Documented
    public @interface Schedule {
        String day();
        String time() default "00:00";
    }

    /**
     * 调度容器注解
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Documented
    public @interface Schedules {
        Schedule[] value();
    }

    // ========== 测试类 ==========

    /**
     * 用户实体类
     */
    @Entity(description = "用户实体")
    @Table(name = "t_user", schema = "public")
    static class User {
        @Id
        @Column(name = "id", nullable = false)
        private Long id;

        @Column(name = "username", nullable = false, length = 50)
        private String username;

        @Column(name = "email", nullable = true, length = 100)
        private String email;

        @Column(name = "age", nullable = true)
        private Integer age;
    }

    /**
     * 用户服务类
     */
    static class UserService {
        @Transactional(propagation = "REQUIRED", isolation = "READ_COMMITTED", timeout = 60)
        public void createUser(User user) {
            // 创建用户
        }

        @Transactional
        @CacheEvict(value = "users")
        public void updateUser(User user) {
            // 更新用户
        }

        public User getUser(Long id) {
            // 获取用户
            return null;
        }
    }

    /**
     * 父类
     */
    @InheritedAnnotation("parent")
    @NonInheritedAnnotation("parent")
    static class Parent {
    }

    /**
     * 子类
     */
    static class Child extends Parent {
    }

    /**
     * 任务调度器
     */
    static class TaskScheduler {
        @Schedule(day = "Monday", time = "09:00")
        @Schedule(day = "Wednesday", time = "14:00")
        @Schedule(day = "Friday", time = "18:00")
        public void backupDatabase() {
            // 备份数据库
        }
    }
}
