package com.fragment.core.modifiers;

/**
 * 同包访问演示
 * 演示同一包内对 AccessModifierDemo 的访问权限
 */
public class SamePackageAccess {
    
    public void testAccess() {
        System.out.println("\n=== Same Package Access Test ===");
        
        AccessModifierDemo demo = new AccessModifierDemo();
        
        // ✓ 可以访问 public 成员
        System.out.println("Public field: " + demo.publicField);
        demo.publicMethod();
        
        // ✓ 可以访问 protected 成员（同包）
        System.out.println("Protected field: " + demo.protectedField);
        demo.protectedMethod();
        
        // ✓ 可以访问 default 成员（同包）
        System.out.println("Default field: " + demo.defaultField);
        demo.defaultMethod();
        
        // ✗ 不能访问 private 成员
        // System.out.println(demo.privateField); // 编译错误
        // demo.privateMethod(); // 编译错误
        
        // 测试构造器访问
        AccessModifierDemo demo1 = new AccessModifierDemo(); // public
        AccessModifierDemo demo2 = new AccessModifierDemo("test"); // protected
        AccessModifierDemo demo3 = new AccessModifierDemo(123); // default
        // AccessModifierDemo demo4 = new AccessModifierDemo(true); // private - 编译错误
        
        // 测试内部类访问
        AccessModifierDemo.PublicInnerClass publicInner = demo.new PublicInnerClass();
        AccessModifierDemo.ProtectedInnerClass protectedInner = demo.new ProtectedInnerClass();
        AccessModifierDemo.DefaultInnerClass defaultInner = demo.new DefaultInnerClass();
        // AccessModifierDemo.PrivateInnerClass privateInner = demo.new PrivateInnerClass(); // 编译错误
        
        publicInner.display();
        protectedInner.display();
        defaultInner.display();
    }
    
    public static void main(String[] args) {
        SamePackageAccess test = new SamePackageAccess();
        test.testAccess();
    }
}
