package com.fragment.core.serialization.examples;

import java.io.*;

/**
 * Externalizable接口示例
 * 
 * 演示内容：
 * 1. Externalizable vs Serializable的区别
 * 2. 完全控制序列化过程
 * 3. 性能优化
 * 
 * @author fragment
 * @since 1.0.0
 */
public class ExternalizableExample implements Externalizable {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private int age;
    private String email;
    
    /**
     * 必须提供无参构造函数
     * Externalizable要求必须有public无参构造函数
     */
    public ExternalizableExample() {
        System.out.println("无参构造函数被调用");
    }
    
    public ExternalizableExample(String name, int age, String email) {
        this.name = name;
        this.age = age;
        this.email = email;
    }
    
    /**
     * 自定义序列化
     * 
     * 说明：
     * 1. 必须手动写入所有需要序列化的字段
     * 2. 完全控制序列化过程
     * 3. 可以实现更高效的序列化
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        System.out.println("writeExternal被调用");
        
        // 手动写入字段
        out.writeUTF(name != null ? name : "");
        out.writeInt(age);
        out.writeUTF(email != null ? email : "");
    }
    
    /**
     * 自定义反序列化
     * 
     * 说明：
     * 1. 必须手动读取所有字段
     * 2. 读取顺序必须与写入顺序一致
     * 3. 先调用无参构造函数，再调用readExternal
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        System.out.println("readExternal被调用");
        
        // 手动读取字段（顺序必须与writeExternal一致）
        this.name = in.readUTF();
        this.age = in.readInt();
        this.email = in.readUTF();
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getAge() {
        return age;
    }
    
    public void setAge(int age) {
        this.age = age;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    @Override
    public String toString() {
        return "ExternalizableExample{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", email='" + email + '\'' +
                '}';
    }
    
    /**
     * 演示Externalizable的使用
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Externalizable示例 ===\n");
        
        // 创建对象
        System.out.println("1. 创建对象:");
        ExternalizableExample obj = new ExternalizableExample("张三", 25, "zhangsan@example.com");
        System.out.println("原始对象: " + obj);
        
        // 序列化
        System.out.println("\n2. 序列化:");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        byte[] data = baos.toByteArray();
        System.out.println("序列化完成，字节数: " + data.length);
        
        // 反序列化
        System.out.println("\n3. 反序列化:");
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ExternalizableExample deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (ExternalizableExample) ois.readObject();
        }
        System.out.println("反序列化对象: " + deserialized);
        
        // 对比说明
        System.out.println("\n=== Externalizable vs Serializable ===");
        System.out.println("Externalizable:");
        System.out.println("  ✓ 必须提供无参构造函数");
        System.out.println("  ✓ 必须手动实现writeExternal和readExternal");
        System.out.println("  ✓ 完全控制序列化过程");
        System.out.println("  ✓ 性能更好（减少反射调用）");
        System.out.println("  ✓ 序列化数据更小");
        
        System.out.println("\nSerializable:");
        System.out.println("  ✓ 不需要无参构造函数");
        System.out.println("  ✓ 自动序列化所有非transient字段");
        System.out.println("  ✓ 使用简单");
        System.out.println("  ✓ 自动处理对象图和循环引用");
    }
}
