package com.fragment.core.modifiers.subpackage;

import com.fragment.core.modifiers.AccessModifierDemo;

/**
 * 不同包的非子类访问演示
 * 演示不同包的普通类对 AccessModifierDemo 的访问权限
 */
public class DifferentPackageAccess {
    
    public void testAccess() {
        System.out.println("\n=== Different Package (Non-Subclass) Access Test ===");
        
        AccessModifierDemo demo = new AccessModifierDemo();
        
        // ✓ 可以访问 public 成员
        System.out.println("Public field: " + demo.publicField);
        demo.publicMethod();
        
        // ✗ 不能访问 protected 成员（不同包且非子类）
        // System.out.println(demo.protectedField); // 编译错误
        // demo.protectedMethod(); // 编译错误
        
        // ✗ 不能访问 default 成员（不同包）
        // System.out.println(demo.defaultField); // 编译错误
        // demo.defaultMethod(); // 编译错误
        
        // ✗ 不能访问 private 成员
        // System.out.println(demo.privateField); // 编译错误
        // demo.privateMethod(); // 编译错误
        
        // 只能使用 public 构造器
        AccessModifierDemo demo1 = new AccessModifierDemo();
        // AccessModifierDemo demo2 = new AccessModifierDemo("test"); // 编译错误
        // AccessModifierDemo demo3 = new AccessModifierDemo(123); // 编译错误
        
        // 只能访问 public 内部类
        AccessModifierDemo.PublicInnerClass publicInner = demo.new PublicInnerClass();
        publicInner.display();
        
        // ✗ 不能访问其他内部类
        // AccessModifierDemo.ProtectedInnerClass protectedInner = demo.new ProtectedInnerClass(); // 编译错误
        // AccessModifierDemo.DefaultInnerClass defaultInner = demo.new DefaultInnerClass(); // 编译错误
        // AccessModifierDemo.PrivateInnerClass privateInner = demo.new PrivateInnerClass(); // 编译错误
    }
    
    public static void main(String[] args) {
        DifferentPackageAccess test = new DifferentPackageAccess();
        test.testAccess();
    }
}
