package com.fragment.core.modifiers;

/**
 * 访问修饰符演示
 * 演示 public, protected, default, private 的使用
 */
public class AccessModifierDemo {
    
    // public - 所有类都可以访问
    public String publicField = "Public Field";
    
    // protected - 同包 + 子类可以访问
    protected String protectedField = "Protected Field";
    
    // default (package-private) - 仅同包可以访问
    String defaultField = "Default Field";
    
    // private - 仅当前类可以访问
    private String privateField = "Private Field";
    
    // ========== 构造器访问修饰符 ==========
    
    // public 构造器
    public AccessModifierDemo() {
        System.out.println("Public constructor called");
    }
    
    // protected 构造器
    protected AccessModifierDemo(String message) {
        System.out.println("Protected constructor: " + message);
    }
    
    // default 构造器
    AccessModifierDemo(int value) {
        System.out.println("Default constructor: " + value);
    }
    
    // private 构造器（常用于单例模式）
    private AccessModifierDemo(boolean flag) {
        System.out.println("Private constructor: " + flag);
    }
    
    // ========== 方法访问修饰符 ==========
    
    // public 方法 - 对外提供的 API
    public void publicMethod() {
        System.out.println("Public method - accessible from anywhere");
        // 可以访问所有成员
        System.out.println(publicField);
        System.out.println(protectedField);
        System.out.println(defaultField);
        System.out.println(privateField);
    }
    
    // protected 方法 - 供子类使用
    protected void protectedMethod() {
        System.out.println("Protected method - accessible from subclass");
    }
    
    // default 方法 - 包内使用
    void defaultMethod() {
        System.out.println("Default method - accessible within package");
    }
    
    // private 方法 - 内部实现
    private void privateMethod() {
        System.out.println("Private method - only accessible within this class");
    }
    
    // ========== 内部类访问修饰符 ==========
    
    // public 内部类
    public class PublicInnerClass {
        public void display() {
            System.out.println("Public inner class");
            // 内部类可以访问外部类的所有成员
            System.out.println(privateField);
        }
    }
    
    // protected 内部类
    protected class ProtectedInnerClass {
        public void display() {
            System.out.println("Protected inner class");
        }
    }
    
    // default 内部类
    class DefaultInnerClass {
        public void display() {
            System.out.println("Default inner class");
        }
    }
    
    // private 内部类
    private class PrivateInnerClass {
        public void display() {
            System.out.println("Private inner class");
        }
    }
    
    // ========== 演示方法 ==========
    
    public void demonstrateAccess() {
        System.out.println("\n=== Access Modifier Demo ===");
        
        // 当前类可以访问所有成员
        publicMethod();
        protectedMethod();
        defaultMethod();
        privateMethod();
        
        // 可以创建所有内部类的实例
        PublicInnerClass publicInner = new PublicInnerClass();
        ProtectedInnerClass protectedInner = new ProtectedInnerClass();
        DefaultInnerClass defaultInner = new DefaultInnerClass();
        PrivateInnerClass privateInner = new PrivateInnerClass();
        
        publicInner.display();
        protectedInner.display();
        defaultInner.display();
        privateInner.display();
    }
    
    public static void main(String[] args) {
        AccessModifierDemo demo = new AccessModifierDemo();
        demo.demonstrateAccess();
        
        // 测试字段访问
        System.out.println("\n=== Field Access ===");
        System.out.println("Public: " + demo.publicField);
        System.out.println("Protected: " + demo.protectedField);
        System.out.println("Default: " + demo.defaultField);
        System.out.println("Private: " + demo.privateField); // 同一类内可以访问
    }
}
