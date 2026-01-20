package com.fragment.core.serialization.examples;

import java.io.*;
import java.math.BigDecimal;

/**
 * 序列化演示类
 * 
 * 演示内容：
 * 1. 基本的序列化和反序列化操作
 * 2. transient字段的行为
 * 3. 版本兼容性
 * 4. 单例保护
 * 
 * @author fragment
 * @since 1.0.0
 */
public class SerializationDemo {
    
    /**
     * 序列化对象到字节数组
     */
    public static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }
    
    /**
     * 从字节数组反序列化对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }
    
    /**
     * 序列化对象到文件
     */
    public static void serializeToFile(Serializable obj, String filename) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(filename))) {
            oos.writeObject(obj);
            System.out.println("对象已序列化到文件: " + filename);
        }
    }
    
    /**
     * 从文件反序列化对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeFromFile(String filename) 
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(filename))) {
            T obj = (T) ois.readObject();
            System.out.println("对象已从文件反序列化: " + filename);
            return obj;
        }
    }
    
    /**
     * 演示1：基本序列化
     */
    public static void demo1_BasicSerialization() throws Exception {
        System.out.println("=== 演示1：基本序列化 ===");
        
        // 创建用户对象
        User user = new User(1L, "zhangsan", "123456");
        user.setEmail("zhangsan@example.com");
        
        System.out.println("原始对象: " + user);
        System.out.println("密码（明文）: " + user.getPassword());
        
        // 序列化
        byte[] data = serialize(user);
        System.out.println("序列化后的字节数: " + data.length);
        
        // 反序列化
        User deserialized = deserialize(data);
        System.out.println("反序列化对象: " + deserialized);
        System.out.println("密码（解密后）: " + deserialized.getPassword());
        
        System.out.println();
    }
    
    /**
     * 演示2：transient字段
     */
    public static void demo2_TransientField() throws Exception {
        System.out.println("=== 演示2：transient字段 ===");
        
        User user = new User(2L, "lisi", "secret");
        System.out.println("序列化前 - 密码: " + user.getPassword());
        
        // 序列化和反序列化
        byte[] data = serialize(user);
        User deserialized = deserialize(data);
        
        // transient字段password不会被序列化
        // 但通过readObject()方法，从encryptedPassword解密恢复
        System.out.println("反序列化后 - 密码: " + deserialized.getPassword());
        
        System.out.println();
    }
    
    /**
     * 演示3：版本兼容性
     */
    public static void demo3_VersionCompatibility() throws Exception {
        System.out.println("=== 演示3：版本兼容性 ===");
        
        // 创建订单对象（使用所有字段）
        Order order = new Order(1001L, new BigDecimal("999.99"), 1L, "测试订单");
        System.out.println("原始订单: " + order);
        
        // 序列化
        byte[] data = serialize(order);
        
        // 反序列化
        Order deserialized = deserialize(data);
        System.out.println("反序列化订单: " + deserialized);
        System.out.println("数据版本: " + deserialized.getDataVersion());
        
        // 模拟旧版本数据（手动创建只有部分字段的订单）
        Order oldOrder = new Order(1002L, new BigDecimal("199.99"));
        System.out.println("\n旧版本订单: " + oldOrder);
        
        byte[] oldData = serialize(oldOrder);
        Order deserializedOld = deserialize(oldData);
        System.out.println("反序列化旧订单: " + deserializedOld);
        System.out.println("新字段remark的默认值: '" + deserializedOld.getRemark() + "'");
        
        System.out.println();
    }
    
    /**
     * 演示4：单例保护
     */
    public static void demo4_SingletonProtection() throws Exception {
        System.out.println("=== 演示4：单例保护 ===");
        
        // 获取单例实例
        Singleton s1 = Singleton.getInstance();
        System.out.println("单例实例1: " + s1);
        
        // 序列化和反序列化
        byte[] data = serialize(s1);
        Singleton s2 = deserialize(data);
        System.out.println("单例实例2（反序列化）: " + s2);
        
        // 验证是否是同一个实例
        System.out.println("s1 == s2: " + (s1 == s2));
        System.out.println("单例保护成功: " + (s1 == s2 ? "是" : "否"));
        
        System.out.println();
    }
    
    /**
     * 演示5：文件序列化
     */
    public static void demo5_FileSerialization() throws Exception {
        System.out.println("=== 演示5：文件序列化 ===");
        
        // 创建用户对象
        User user = new User(3L, "wangwu", "password");
        user.setEmail("wangwu@example.com");
        
        // 序列化到文件
        String filename = "user.ser";
        serializeToFile(user, filename);
        
        // 从文件反序列化
        User deserialized = deserializeFromFile(filename);
        System.out.println("反序列化对象: " + deserialized);
        
        // 清理文件
        new File(filename).delete();
        System.out.println("临时文件已删除");
        
        System.out.println();
    }
    
    /**
     * 演示6：深拷贝
     */
    public static void demo6_DeepCopy() throws Exception {
        System.out.println("=== 演示6：通过序列化实现深拷贝 ===");
        
        // 创建原始对象
        User original = new User(4L, "zhaoliu", "123456");
        original.setEmail("zhaoliu@example.com");
        
        // 通过序列化实现深拷贝
        byte[] data = serialize(original);
        User copy = deserialize(data);
        
        System.out.println("原始对象: " + original);
        System.out.println("拷贝对象: " + copy);
        
        // 修改拷贝对象
        copy.setUsername("修改后的用户名");
        copy.setEmail("new@example.com");
        
        System.out.println("\n修改拷贝对象后:");
        System.out.println("原始对象: " + original);
        System.out.println("拷贝对象: " + copy);
        System.out.println("深拷贝成功: " + (!original.getUsername().equals(copy.getUsername())));
        
        System.out.println();
    }
    
    /**
     * 主方法 - 运行所有演示
     */
    public static void main(String[] args) {
        try {
            demo1_BasicSerialization();
            demo2_TransientField();
            demo3_VersionCompatibility();
            demo4_SingletonProtection();
            demo5_FileSerialization();
            demo6_DeepCopy();
            
            System.out.println("所有演示完成！");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
