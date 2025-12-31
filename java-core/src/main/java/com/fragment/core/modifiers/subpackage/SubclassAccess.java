package com.fragment.core.modifiers.subpackage;

import com.fragment.core.modifiers.AccessModifierDemo;

/**
 * 不同包的子类访问演示
 * 演示子类对父类成员的访问权限
 */
public class SubclassAccess extends AccessModifierDemo {
    
    public void testAccess() {
        System.out.println("\n=== Subclass (Different Package) Access Test ===");
        
        // ✓ 可以访问 public 成员
        System.out.println("Public field: " + this.publicField);
        this.publicMethod();
        
        // ✓ 可以访问 protected 成员（子类）
        System.out.println("Protected field: " + this.protectedField);
        this.protectedMethod();
        
        // ✗ 不能访问 default 成员（不同包）
        // System.out.println(this.defaultField); // 编译错误
        // this.defaultMethod(); // 编译错误
        
        // ✗ 不能访问 private 成员
        // System.out.println(this.privateField); // 编译错误
        // this.privateMethod(); // 编译错误
    }
    
    public void testParentInstanceAccess() {
        System.out.println("\n=== Access Parent Instance (Different Package) ===");
        
        // 创建父类实例
        AccessModifierDemo parent = new AccessModifierDemo();
        
        // ✓ 可以访问 public 成员
        System.out.println("Public field: " + parent.publicField);
        parent.publicMethod();
        
        // ✗ 不能通过父类实例访问 protected 成员（不同包）
        // 注意：只能通过 this 或 super 访问继承的 protected 成员
        // System.out.println(parent.protectedField); // 编译错误
        // parent.protectedMethod(); // 编译错误
        
        // ✗ 不能访问 default 成员
        // System.out.println(parent.defaultField); // 编译错误
        
        // ✗ 不能访问 private 成员
        // System.out.println(parent.privateField); // 编译错误
    }
    
    @Override
    public void publicMethod() {
        System.out.println("Overridden public method in subclass");
        super.publicMethod();
    }
    
    @Override
    protected void protectedMethod() {
        System.out.println("Overridden protected method in subclass");
        super.protectedMethod();
    }
    
    // ✗ 不能重写 default 方法（不同包，不可见）
    // void defaultMethod() { }
    
    // ✗ 不能重写 private 方法（不可见）
    // private void privateMethod() { }
    
    public static void main(String[] args) {
        SubclassAccess subclass = new SubclassAccess();
        subclass.testAccess();
        subclass.testParentInstanceAccess();
    }
}
