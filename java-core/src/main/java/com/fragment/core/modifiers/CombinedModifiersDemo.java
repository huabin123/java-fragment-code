package com.fragment.core.modifiers;

import java.util.*;

/**
 * 组合修饰符演示
 * 演示 public/protected/private + static/final 的组合使用
 */
public class CombinedModifiersDemo {
    
    // ========== 组合1: public static final（公共静态常量） ==========
    
    public static final String PUBLIC_CONSTANT = "Public Constant";
    public static final int MAX_RETRY = 3;
    
    // ========== 组合2: private static final（私有静态常量） ==========
    
    private static final String PRIVATE_CONSTANT = "Private Constant";
    private static final int BUFFER_SIZE = 1024;
    
    // ========== 组合3: public static（公共静态变量） ==========
    
    public static int publicStaticCounter = 0;
    
    // ========== 组合4: private static（私有静态变量） ==========
    
    private static int instanceCount = 0;
    private static final List<CombinedModifiersDemo> instances = new ArrayList<>();
    
    // ========== 组合5: public final（公共实例常量） ==========
    
    public final String publicFinalId = UUID.randomUUID().toString();
    
    // ========== 组合6: private final（私有实例常量） ==========
    
    private final String name;
    private final int instanceId;
    
    // ========== 组合7: protected final（受保护实例常量） ==========
    
    protected final String protectedFinalValue;
    
    // ========== 静态代码块 ==========
    
    static {
        System.out.println("Static block: Initializing class");
    }
    
    // ========== 构造器 ==========
    
    public CombinedModifiersDemo(String name) {
        this.name = name;
        this.instanceId = ++instanceCount;
        this.protectedFinalValue = "Protected-" + instanceId;
        instances.add(this);
        System.out.println("Created instance #" + instanceId + ": " + name);
    }
    
    // ========== 组合8: public static final 方法（不存在） ==========
    // 注意：方法不能同时是 static 和 final（没有意义）
    // static 方法不能被重写，所以 final 是多余的
    
    // ========== 组合9: public static 方法（公共静态方法） ==========
    
    /**
     * 公共静态方法：工具方法
     */
    public static String formatMessage(String message) {
        return "[" + PUBLIC_CONSTANT + "] " + message;
    }
    
    /**
     * 公共静态方法：工厂方法
     */
    public static CombinedModifiersDemo createInstance(String name) {
        return new CombinedModifiersDemo(name);
    }
    
    /**
     * 公共静态方法：获取实例数
     */
    public static int getInstanceCount() {
        return instanceCount;
    }
    
    /**
     * 公共静态方法：获取所有实例
     */
    public static List<CombinedModifiersDemo> getAllInstances() {
        return Collections.unmodifiableList(instances);
    }
    
    // ========== 组合10: private static 方法（私有静态方法） ==========
    
    /**
     * 私有静态方法：内部辅助方法
     */
    private static String getPrivateConstant() {
        return PRIVATE_CONSTANT;
    }
    
    /**
     * 私有静态方法：验证
     */
    private static boolean validateName(String name) {
        return name != null && !name.trim().isEmpty();
    }
    
    // ========== 组合11: public final 方法（公共最终方法） ==========
    
    /**
     * 公共 final 方法：不能被子类重写
     */
    public final void publicFinalMethod() {
        System.out.println("Public final method - cannot be overridden");
    }
    
    /**
     * 公共 final 方法：模板方法
     */
    public final void execute() {
        beforeExecute();
        doExecute();
        afterExecute();
    }
    
    // ========== 组合12: protected final 方法（受保护最终方法） ==========
    
    /**
     * 受保护 final 方法：子类可以调用但不能重写
     */
    protected final void protectedFinalMethod() {
        System.out.println("Protected final method");
    }
    
    // ========== 组合13: private final 方法（私有最终方法） ==========
    // 注意：private 方法本身就不能被重写，所以 final 是多余的
    
    private final void privateFinalMethod() {
        System.out.println("Private final method (final is redundant)");
    }
    
    // ========== 组合14: public 方法（可重写） ==========
    
    public void publicMethod() {
        System.out.println("Public method - can be overridden");
    }
    
    protected void beforeExecute() {
        System.out.println("Before execute");
    }
    
    protected void doExecute() {
        System.out.println("Executing");
    }
    
    protected void afterExecute() {
        System.out.println("After execute");
    }
    
    // ========== 组合15: protected 方法（可重写） ==========
    
    protected void protectedMethod() {
        System.out.println("Protected method - can be overridden by subclass");
    }
    
    // ========== 组合16: private 方法 ==========
    
    private void privateMethod() {
        System.out.println("Private method - only accessible within this class");
    }
    
    // ========== Getter 方法 ==========
    
    public String getName() {
        return name;
    }
    
    public int getInstanceId() {
        return instanceId;
    }
    
    public String getProtectedFinalValue() {
        return protectedFinalValue;
    }
    
    // ========== 静态内部类 ==========
    
    /**
     * public static final 内部类（不可变配置类）
     */
    public static final class Config {
        public static final String VERSION = "1.0.0";
        public static final int TIMEOUT = 5000;
        
        private Config() {
            throw new AssertionError("Cannot instantiate");
        }
    }
    
    /**
     * public static 内部类（Builder 模式）
     */
    public static class Builder {
        private String name;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public CombinedModifiersDemo build() {
            if (!validateName(name)) {
                throw new IllegalArgumentException("Invalid name");
            }
            return new CombinedModifiersDemo(name);
        }
    }
    
    // ========== 演示方法 ==========
    
    public void demonstrateAccess() {
        System.out.println("\n=== Demonstrate Access ===");
        
        // 访问各种组合
        System.out.println("Public constant: " + PUBLIC_CONSTANT);
        System.out.println("Private constant: " + PRIVATE_CONSTANT);
        System.out.println("Public final ID: " + publicFinalId);
        System.out.println("Private final name: " + name);
        System.out.println("Protected final value: " + protectedFinalValue);
        
        // 调用各种方法
        publicMethod();
        protectedMethod();
        privateMethod();
        publicFinalMethod();
        protectedFinalMethod();
        privateFinalMethod();
    }
    
    @Override
    public String toString() {
        return "CombinedModifiersDemo{" +
                "instanceId=" + instanceId +
                ", name='" + name + '\'' +
                ", publicFinalId='" + publicFinalId + '\'' +
                '}';
    }
    
    // ========== 主方法 ==========
    
    public static void main(String[] args) {
        System.out.println("========== Combined Modifiers Demo Start ==========\n");
        
        // 1. 测试静态常量
        System.out.println("=== Static Constants ===");
        System.out.println("Public constant: " + PUBLIC_CONSTANT);
        System.out.println("Config version: " + Config.VERSION);
        System.out.println("Config timeout: " + Config.TIMEOUT);
        
        // 2. 测试静态方法
        System.out.println("\n=== Static Methods ===");
        System.out.println(formatMessage("Hello World"));
        System.out.println("Instance count: " + getInstanceCount());
        
        // 3. 创建实例
        System.out.println("\n=== Creating Instances ===");
        CombinedModifiersDemo obj1 = new CombinedModifiersDemo("Object 1");
        CombinedModifiersDemo obj2 = createInstance("Object 2");
        CombinedModifiersDemo obj3 = new Builder().name("Object 3").build();
        
        System.out.println("\nTotal instances: " + getInstanceCount());
        
        // 4. 测试实例
        System.out.println("\n=== Instance Information ===");
        System.out.println(obj1);
        System.out.println(obj2);
        System.out.println(obj3);
        
        // 5. 测试访问
        obj1.demonstrateAccess();
        
        // 6. 测试 final 方法
        System.out.println("\n=== Final Methods ===");
        obj1.execute();
        
        // 7. 获取所有实例
        System.out.println("\n=== All Instances ===");
        List<CombinedModifiersDemo> allInstances = getAllInstances();
        allInstances.forEach(System.out::println);
        
        // 8. 测试子类
        System.out.println("\n=== Subclass Test ===");
        CombinedModifiersSubclass subclass = new CombinedModifiersSubclass("Subclass");
        subclass.demonstrateSubclass();
        
        System.out.println("\n========== Combined Modifiers Demo End ==========");
    }
}

/**
 * 子类：演示继承和重写
 */
class CombinedModifiersSubclass extends CombinedModifiersDemo {
    
    public CombinedModifiersSubclass(String name) {
        super(name);
    }
    
    // ✓ 可以重写 public 方法
    @Override
    public void publicMethod() {
        System.out.println("Overridden public method in subclass");
        super.publicMethod();
    }
    
    // ✓ 可以重写 protected 方法
    @Override
    protected void protectedMethod() {
        System.out.println("Overridden protected method in subclass");
        super.protectedMethod();
    }
    
    // ✓ 可以重写模板方法的钩子
    @Override
    protected void doExecute() {
        System.out.println("Subclass executing");
    }
    
    // ✗ 不能重写 final 方法
    // public void publicFinalMethod() { } // 编译错误
    // protected void protectedFinalMethod() { } // 编译错误
    // public void execute() { } // 编译错误
    
    // ✗ 不能访问 private 方法
    // private void privateMethod() { } // 这是新方法，不是重写
    
    public void demonstrateSubclass() {
        System.out.println("\n=== Subclass Demonstration ===");
        
        // ✓ 可以访问 public 成员
        System.out.println("Public constant: " + PUBLIC_CONSTANT);
        System.out.println("Public final ID: " + publicFinalId);
        
        // ✓ 可以访问 protected 成员
        System.out.println("Protected final value: " + protectedFinalValue);
        
        // ✗ 不能访问 private 成员
        // System.out.println(name); // 编译错误
        
        // 调用方法
        publicMethod();
        protectedMethod();
        publicFinalMethod();
        protectedFinalMethod();
        execute();
    }
}
