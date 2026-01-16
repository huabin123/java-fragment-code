package com.fragment.core.modifiers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * final 关键字演示
 * 演示 final 变量、final 方法、final 类的使用
 */
public class FinalDemo {
    
    // ========== final 静态变量（类常量） ==========
    
    // 编译时常量
    public static final int MAX_SIZE = 100;
    public static final String APP_NAME = "Final Demo";
    public static final double PI = 3.14159265359;
    
    // 运行时常量
    public static final String TIMESTAMP = LocalDateTime.now().toString();
    public static final Random RANDOM = new Random();
    
    // 不可变集合
    public static final List<String> IMMUTABLE_LIST = 
        Collections.unmodifiableList(Arrays.asList("A", "B", "C"));
    
    public static final Set<String> IMMUTABLE_SET = 
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList("X", "Y", "Z")));
    
    public static final Map<String, Integer> IMMUTABLE_MAP;
    
    static {
        Map<String, Integer> temp = new HashMap<>();
        temp.put("ONE", 1);
        temp.put("TWO", 2);
        temp.put("THREE", 3);
        IMMUTABLE_MAP = Collections.unmodifiableMap(temp);
    }
    
    // ========== final 实例变量 ==========
    
    // 声明时初始化
    private final String id = UUID.randomUUID().toString();
    
    // 构造器中初始化
    private final String name;
    private final LocalDateTime createdAt;
    
    // final 引用类型
    private final List<String> items = new ArrayList<>();
    private final StringBuilder builder = new StringBuilder();
    
    // ========== 构造器 ==========
    
    public FinalDemo(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
        // final 变量必须在构造器结束前初始化
    }
    
    // ========== final 局部变量 ==========
    
    public void finalLocalVariableDemo() {
        System.out.println("\n=== Final Local Variable Demo ===");
        
        // final 局部变量
        final int count = 10;
        final String message = "Hello";
        
        // ✗ 不能修改
        // count = 20; // 编译错误
        // message = "World"; // 编译错误
        
        System.out.println("Count: " + count);
        System.out.println("Message: " + message);
        
        // final 引用类型
        final List<String> list = new ArrayList<>();
        list.add("Item 1"); // ✓ 可以修改对象内容
        list.add("Item 2");
        // list = new ArrayList<>(); // ✗ 不能修改引用
        
        System.out.println("List: " + list);
    }
    
    /**
     * final 参数演示
     */
    public void processData(final String input, final int value) {
        System.out.println("\n=== Final Parameter Demo ===");
        
        // ✗ 不能修改 final 参数
        // input = "modified"; // 编译错误
        // value = 100; // 编译错误
        
        System.out.println("Input: " + input);
        System.out.println("Value: " + value);
    }
    
    /**
     * final 引用类型参数演示 - 重要概念
     * final 只限制引用不可变，不限制对象内容
     */
    public void processCollection(final List<String> items) {
        System.out.println("\n=== Final Reference Parameter Demo ===");
        System.out.println("原始集合: " + items);
        
        // ✓ 可以修改集合内容
        items.add("新增项1");
        items.add("新增项2");
        System.out.println("添加元素后: " + items);
        
        items.remove(0);
        System.out.println("删除元素后: " + items);
        
        // ✗ 不能修改引用
        // items = new ArrayList<>(); // 编译错误
        
        System.out.println("\n关键点：final 参数可以修改集合内容，只是不能重新赋值！");
    }
    
    /**
     * 演示 final 的三个作用层次
     */
    public void demonstrateFinalScopes() {
        System.out.println("\n=== Final 的三个作用层次 ===");
        
        // 1. final 修饰类 - 防止继承
        System.out.println("1. final 修饰类：防止继承");
        System.out.println("   例如：String、Integer 等类都是 final 的");
        
        // 2. final 修饰方法 - 防止重写
        System.out.println("\n2. final 修饰方法：防止重写");
        System.out.println("   子类不能重写 final 方法");
        
        // 3. final 修饰变量 - 防止重新赋值（但不防止内容修改）
        System.out.println("\n3. final 修饰变量：防止重新赋值");
        final List<String> list = new ArrayList<>();
        list.add("可以修改内容");
        // list = new ArrayList<>(); // 但不能重新赋值
        System.out.println("   final 集合可以修改内容: " + list);
    }
    
    /**
     * 有效 final（Effectively Final）演示
     */
    public void effectivelyFinalDemo() {
        System.out.println("\n=== Effectively Final Demo ===");
        
        // 没有 final 关键字，但是没有被重新赋值
        String message = "Hello";
        int count = 10;
        
        // Lambda 表达式可以访问有效 final 变量
        Runnable task = () -> {
            System.out.println("Message: " + message);
            System.out.println("Count: " + count);
        };
        
        task.run();
        
        // 如果取消下面的注释，上面的 lambda 会编译错误
        // message = "World";
        // count = 20;
    }
    
    // ========== final 方法 ==========
    
    /**
     * final 方法不能被子类重写
     */
    public final void finalMethod() {
        System.out.println("This is a final method - cannot be overridden");
    }
    
    /**
     * final 方法：模板方法模式
     */
    public final void process() {
        beforeProcess();
        doProcess();
        afterProcess();
    }
    
    protected void beforeProcess() {
        System.out.println("Before processing");
    }
    
    protected void doProcess() {
        System.out.println("Processing");
    }
    
    protected void afterProcess() {
        System.out.println("After processing");
    }
    
    // ========== final 引用类型演示 ==========
    
    public void finalReferenceDemo() {
        System.out.println("\n=== Final Reference Demo ===");
        
        // ✓ 可以修改集合内容
        items.add("Item 1");
        items.add("Item 2");
        items.remove(0);
        System.out.println("Items: " + items);
        
        // ✗ 不能修改引用
        // items = new ArrayList<>(); // 编译错误
        
        // ✓ 可以修改 StringBuilder 内容
        builder.append("Hello");
        builder.append(" World");
        System.out.println("Builder: " + builder);
        
        // ✗ 不能修改引用
        // builder = new StringBuilder(); // 编译错误
    }
    
    // ========== Getter 方法 ==========
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public List<String> getItems() {
        // 返回不可修改的视图
        return Collections.unmodifiableList(items);
    }
    
    // ========== 主方法 ==========
    
    public static void main(String[] args) {
        System.out.println("========== Final Demo Start ==========\n");
        
        // 1. 测试静态常量
        System.out.println("=== Static Final Variables ===");
        System.out.println("MAX_SIZE: " + MAX_SIZE);
        System.out.println("APP_NAME: " + APP_NAME);
        System.out.println("PI: " + PI);
        System.out.println("TIMESTAMP: " + TIMESTAMP);
        System.out.println("IMMUTABLE_LIST: " + IMMUTABLE_LIST);
        
        // ✗ 不能修改常量
        // MAX_SIZE = 200; // 编译错误
        
        // ✗ 不能修改不可变集合
        // IMMUTABLE_LIST.add("D"); // 运行时异常
        
        // 2. 创建实例
        FinalDemo demo = new FinalDemo("Demo Instance");
        
        System.out.println("\n=== Instance Final Variables ===");
        System.out.println("ID: " + demo.getId());
        System.out.println("Name: " + demo.getName());
        System.out.println("Created At: " + demo.getCreatedAt());
        
        // 3. 测试局部变量
        demo.finalLocalVariableDemo();
        
        // 4. 测试参数
        demo.processData("Test Input", 42);
        
        // 4.5 测试 final 引用类型参数（重要概念）
        List<String> testList = new ArrayList<>(Arrays.asList("原始项1", "原始项2"));
        System.out.println("\n调用前的集合: " + testList);
        demo.processCollection(testList);
        System.out.println("调用后的集合: " + testList + " (集合内容被修改了！)");
        
        // 4.6 演示 final 的三个作用层次
        demo.demonstrateFinalScopes();
        
        // 5. 测试有效 final
        demo.effectivelyFinalDemo();
        
        // 6. 测试 final 方法
        System.out.println("\n=== Final Method ===");
        demo.finalMethod();
        demo.process();
        
        // 7. 测试 final 引用
        demo.finalReferenceDemo();
        
        // 8. 测试不可变类
        System.out.println("\n=== Immutable Class ===");
        ImmutablePerson person = new ImmutablePerson(
            "John", 
            25, 
            Arrays.asList("Reading", "Coding")
        );
        System.out.println("Person: " + person);
        
        ImmutablePerson updatedPerson = person.withAge(26);
        System.out.println("Updated Person: " + updatedPerson);
        System.out.println("Original Person: " + person); // 原对象未改变
        
        // 9. 测试 final 类
        System.out.println("\n=== Final Class ===");
        Money money1 = new Money(new BigDecimal("100.00"), "USD");
        Money money2 = new Money(new BigDecimal("50.00"), "USD");
        Money total = money1.add(money2);
        System.out.println("Total: " + total);
        
        System.out.println("\n========== Final Demo End ==========");
    }
}

// ========== final 类示例 ==========

/**
 * 不可变类：final 类 + final 字段 + 无 setter
 */
final class ImmutablePerson {
    private final String name;
    private final int age;
    private final List<String> hobbies;
    
    public ImmutablePerson(String name, int age, List<String> hobbies) {
        this.name = name;
        this.age = age;
        // 防御性复制
        this.hobbies = Collections.unmodifiableList(new ArrayList<>(hobbies));
    }
    
    public String getName() {
        return name;
    }
    
    public int getAge() {
        return age;
    }
    
    public List<String> getHobbies() {
        return hobbies; // 已经是不可变的
    }
    
    // 修改操作返回新对象
    public ImmutablePerson withName(String newName) {
        return new ImmutablePerson(newName, this.age, new ArrayList<>(this.hobbies));
    }
    
    public ImmutablePerson withAge(int newAge) {
        return new ImmutablePerson(this.name, newAge, new ArrayList<>(this.hobbies));
    }
    
    @Override
    public String toString() {
        return "ImmutablePerson{name='" + name + "', age=" + age + ", hobbies=" + hobbies + "}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutablePerson)) return false;
        ImmutablePerson that = (ImmutablePerson) o;
        return age == that.age && 
               Objects.equals(name, that.name) && 
               Objects.equals(hobbies, that.hobbies);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, age, hobbies);
    }
}

/**
 * 值对象：Money 类
 */
final class Money {
    private final BigDecimal amount;
    private final String currency;
    
    public Money(BigDecimal amount, String currency) {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Amount and currency cannot be null");
        }
        this.amount = amount;
        this.currency = currency;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + 
                this.currency + " vs " + other.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
    
    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }
    
    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier), this.currency);
    }
    
    @Override
    public String toString() {
        return currency + " " + amount;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 && 
               currency.equals(money.currency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
}

/**
 * 子类演示：可以继承非 final 类
 */
class FinalDemoSubclass extends FinalDemo {
    
    public FinalDemoSubclass(String name) {
        super(name);
    }
    
    // ✗ 不能重写 final 方法
    // public void finalMethod() { } // 编译错误
    // public void process() { } // 编译错误
    
    // ✓ 可以重写非 final 方法
    @Override
    protected void doProcess() {
        System.out.println("Subclass processing");
    }
}

// ✗ 不能继承 final 类
// class ImmutablePersonSubclass extends ImmutablePerson { } // 编译错误
// class MoneySubclass extends Money { } // 编译错误
