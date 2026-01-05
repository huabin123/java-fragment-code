package com.fragment.jvm.memory.demo;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.vm.VM;

/**
 * 对象内存布局演示
 * 
 * 需要添加JOL依赖：
 * <dependency>
 *     <groupId>org.openjdk.jol</groupId>
 *     <artifactId>jol-core</artifactId>
 *     <version>0.16</version>
 * </dependency>
 * 
 * JVM参数：
 * -XX:+UseCompressedOops（开启指针压缩，默认）
 * -XX:-UseCompressedOops（关闭指针压缩）
 * 
 * @author huabin
 */
public class ObjectLayoutDemo {
    
    /**
     * 示例1：空对象布局
     */
    static class EmptyObject {
    }
    
    /**
     * 示例2：包含基本类型字段的对象
     */
    static class PrimitiveFieldsObject {
        private byte a;      // 1字节
        private short b;     // 2字节
        private int c;       // 4字节
        private long d;      // 8字节
        private float e;     // 4字节
        private double f;    // 8字节
        private boolean g;   // 1字节
        private char h;      // 2字节
    }
    
    /**
     * 示例3：包含引用类型字段的对象
     */
    static class ReferenceFieldsObject {
        private String name;
        private Object obj;
        private int[] array;
    }
    
    /**
     * 示例4：继承关系的对象
     */
    static class Parent {
        private int parentField;
    }
    
    static class Child extends Parent {
        private int childField;
    }
    
    /**
     * 示例5：字段对齐演示
     */
    static class UnalignedObject {
        private byte a;      // 1字节
        private long b;      // 8字节
        private byte c;      // 1字节
        private long d;      // 8字节
    }
    
    static class AlignedObject {
        private long b;      // 8字节
        private long d;      // 8字节
        private byte a;      // 1字节
        private byte c;      // 1字节
    }
    
    public static void main(String[] args) {
        System.out.println("对象内存布局演示");
        System.out.println("注意：需要添加JOL依赖才能运行\n");
        
        try {
            // 打印JVM信息
            System.out.println("========== JVM信息 ==========");
            System.out.println(VM.current().details());
            System.out.println();
            
            // 示例1：空对象
            testEmptyObject();
            
            // 示例2：基本类型字段
            testPrimitiveFields();
            
            // 示例3：引用类型字段
            testReferenceFields();
            
            // 示例4：继承关系
            testInheritance();
            
            // 示例5：数组对象
            testArray();
            
            // 示例6：字段对齐
            testFieldAlignment();
            
            // 示例7：对象大小计算
            testObjectSize();
            
        } catch (NoClassDefFoundError e) {
            System.out.println("错误：未找到JOL依赖");
            System.out.println("请添加以下依赖到pom.xml：");
            System.out.println("<dependency>");
            System.out.println("    <groupId>org.openjdk.jol</groupId>");
            System.out.println("    <artifactId>jol-core</artifactId>");
            System.out.println("    <version>0.16</version>");
            System.out.println("</dependency>");
        }
    }
    
    /**
     * 测试空对象布局
     */
    private static void testEmptyObject() {
        System.out.println("========== 示例1：空对象布局 ==========");
        EmptyObject obj = new EmptyObject();
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        
        System.out.println("分析：");
        System.out.println("- 对象头：12字节（Mark Word 8字节 + 类型指针 4字节）");
        System.out.println("- 实例数据：0字节");
        System.out.println("- 对齐填充：4字节（补齐到16字节）");
        System.out.println("- 总大小：16字节\n");
    }
    
    /**
     * 测试基本类型字段
     */
    private static void testPrimitiveFields() {
        System.out.println("========== 示例2：基本类型字段 ==========");
        PrimitiveFieldsObject obj = new PrimitiveFieldsObject();
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        
        System.out.println("分析：");
        System.out.println("- 对象头：12字节");
        System.out.println("- 实例数据：30字节（8+8+4+4+2+2+1+1）");
        System.out.println("- 对齐填充：2字节");
        System.out.println("- 总大小：44字节");
        System.out.println("- 字段按大小排序：long/double > int/float > short/char > byte/boolean\n");
    }
    
    /**
     * 测试引用类型字段
     */
    private static void testReferenceFields() {
        System.out.println("========== 示例3：引用类型字段 ==========");
        ReferenceFieldsObject obj = new ReferenceFieldsObject();
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        
        System.out.println("分析：");
        System.out.println("- 对象头：12字节");
        System.out.println("- 实例数据：12字节（3个引用，每个4字节，开启指针压缩）");
        System.out.println("- 对齐填充：0字节");
        System.out.println("- 总大小：24字节");
        System.out.println("- 注意：引用只占4字节（开启指针压缩），实际对象在堆中\n");
    }
    
    /**
     * 测试继承关系
     */
    private static void testInheritance() {
        System.out.println("========== 示例4：继承关系 ==========");
        Child obj = new Child();
        System.out.println(ClassLayout.parseInstance(obj).toPrintable());
        
        System.out.println("分析：");
        System.out.println("- 对象头：12字节");
        System.out.println("- 父类字段：4字节（parentField）");
        System.out.println("- 子类字段：4字节（childField）");
        System.out.println("- 对齐填充：0字节");
        System.out.println("- 总大小：20字节");
        System.out.println("- 父类字段在前，子类字段在后\n");
    }
    
    /**
     * 测试数组对象
     */
    private static void testArray() {
        System.out.println("========== 示例5：数组对象 ==========");
        int[] array = new int[5];
        System.out.println(ClassLayout.parseInstance(array).toPrintable());
        
        System.out.println("分析：");
        System.out.println("- 对象头：16字节（Mark Word 8字节 + 类型指针 4字节 + 数组长度 4字节）");
        System.out.println("- 实例数据：20字节（5个int，每个4字节）");
        System.out.println("- 对齐填充：4字节");
        System.out.println("- 总大小：40字节");
        System.out.println("- 数组对象比普通对象多4字节（存储数组长度）\n");
    }
    
    /**
     * 测试字段对齐
     */
    private static void testFieldAlignment() {
        System.out.println("========== 示例6：字段对齐优化 ==========");
        
        System.out.println("未优化的对象：");
        UnalignedObject unaligned = new UnalignedObject();
        System.out.println(ClassLayout.parseInstance(unaligned).toPrintable());
        
        System.out.println("优化后的对象：");
        AlignedObject aligned = new AlignedObject();
        System.out.println(ClassLayout.parseInstance(aligned).toPrintable());
        
        System.out.println("对比：");
        System.out.println("- 未优化：字段交替排列，产生更多填充");
        System.out.println("- 优化后：相同大小字段排列在一起，减少填充");
        System.out.println("- 建议：手动调整字段顺序，减少内存占用\n");
    }
    
    /**
     * 测试对象大小计算
     */
    private static void testObjectSize() {
        System.out.println("========== 示例7：对象大小计算 ==========");
        
        // 不同类型对象的大小
        Object obj1 = new Object();
        String obj2 = new String("hello");
        int[] obj3 = new int[10];
        
        System.out.println("Object对象：");
        System.out.println(ClassLayout.parseInstance(obj1).toPrintable());
        
        System.out.println("String对象：");
        System.out.println(ClassLayout.parseInstance(obj2).toPrintable());
        
        System.out.println("int数组[10]：");
        System.out.println(ClassLayout.parseInstance(obj3).toPrintable());
        
        System.out.println("计算公式：");
        System.out.println("对象大小 = 对象头 + 实例数据 + 对齐填充");
        System.out.println("- 对象头：普通对象12字节，数组对象16字节");
        System.out.println("- 实例数据：根据字段类型累加");
        System.out.println("- 对齐填充：补齐到8字节的倍数\n");
    }
}
