package com.fragment.core.serialization.examples;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义序列化示例
 * 
 * 演示内容：
 * 1. 使用writeObject和readObject自定义序列化
 * 2. 优化序列化性能
 * 3. 处理复杂对象
 * 
 * @author fragment
 * @since 1.0.0
 */
public class CustomSerializationExample implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 大数组（可能有很多null元素）
     */
    private String[] largeArray;
    
    /**
     * 列表数据
     */
    private List<String> dataList;
    
    /**
     * 缓存数据（不需要序列化）
     */
    private transient int cacheSize;
    
    public CustomSerializationExample() {
        this.largeArray = new String[1000];
        this.dataList = new ArrayList<>();
    }
    
    public CustomSerializationExample(String username) {
        this();
        this.username = username;
    }
    
    /**
     * 自定义序列化
     * 
     * 优化策略：
     * 1. 只序列化非null的数组元素
     * 2. 压缩数据
     * 3. 添加版本控制
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        // 1. 写入版本号
        out.writeInt(1); // 版本号
        
        // 2. 写入基本字段
        out.writeUTF(username != null ? username : "");
        
        // 3. 优化数组序列化：只写入非null元素
        int nonNullCount = 0;
        for (String s : largeArray) {
            if (s != null) nonNullCount++;
        }
        
        out.writeInt(largeArray.length); // 数组长度
        out.writeInt(nonNullCount); // 非null元素数量
        
        for (int i = 0; i < largeArray.length; i++) {
            if (largeArray[i] != null) {
                out.writeInt(i); // 索引
                out.writeUTF(largeArray[i]); // 值
            }
        }
        
        // 4. 序列化列表
        out.writeInt(dataList.size());
        for (String data : dataList) {
            out.writeUTF(data);
        }
        
        System.out.println("自定义序列化完成 - 非null元素: " + nonNullCount);
    }
    
    /**
     * 自定义反序列化
     */
    private void readObject(ObjectInputStream in) 
            throws IOException, ClassNotFoundException {
        // 1. 读取版本号
        int version = in.readInt();
        
        // 2. 读取基本字段
        this.username = in.readUTF();
        
        // 3. 读取数组
        int arrayLength = in.readInt();
        int nonNullCount = in.readInt();
        
        this.largeArray = new String[arrayLength];
        for (int i = 0; i < nonNullCount; i++) {
            int index = in.readInt();
            String value = in.readUTF();
            largeArray[index] = value;
        }
        
        // 4. 读取列表
        int listSize = in.readInt();
        this.dataList = new ArrayList<>(listSize);
        for (int i = 0; i < listSize; i++) {
            dataList.add(in.readUTF());
        }
        
        // 5. 初始化transient字段
        this.cacheSize = 0;
        
        System.out.println("自定义反序列化完成 - 版本: " + version + ", 非null元素: " + nonNullCount);
    }
    
    /**
     * 添加数组元素
     */
    public void setArrayElement(int index, String value) {
        if (index >= 0 && index < largeArray.length) {
            largeArray[index] = value;
        }
    }
    
    /**
     * 获取数组元素
     */
    public String getArrayElement(int index) {
        if (index >= 0 && index < largeArray.length) {
            return largeArray[index];
        }
        return null;
    }
    
    /**
     * 添加列表元素
     */
    public void addData(String data) {
        dataList.add(data);
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public List<String> getDataList() {
        return dataList;
    }
    
    /**
     * 演示自定义序列化
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== 自定义序列化演示 ===\n");
        
        // 创建对象
        CustomSerializationExample obj = new CustomSerializationExample("testuser");
        
        // 添加少量数组元素（大部分为null）
        obj.setArrayElement(10, "元素10");
        obj.setArrayElement(100, "元素100");
        obj.setArrayElement(500, "元素500");
        
        // 添加列表数据
        obj.addData("数据1");
        obj.addData("数据2");
        obj.addData("数据3");
        
        System.out.println("原始对象:");
        System.out.println("  用户名: " + obj.getUsername());
        System.out.println("  数组元素[10]: " + obj.getArrayElement(10));
        System.out.println("  数组元素[100]: " + obj.getArrayElement(100));
        System.out.println("  列表数据: " + obj.getDataList());
        
        // 序列化
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        byte[] data = baos.toByteArray();
        System.out.println("\n序列化后的字节数: " + data.length);
        
        // 反序列化
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        CustomSerializationExample deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (CustomSerializationExample) ois.readObject();
        }
        
        System.out.println("\n反序列化对象:");
        System.out.println("  用户名: " + deserialized.getUsername());
        System.out.println("  数组元素[10]: " + deserialized.getArrayElement(10));
        System.out.println("  数组元素[100]: " + deserialized.getArrayElement(100));
        System.out.println("  数组元素[500]: " + deserialized.getArrayElement(500));
        System.out.println("  列表数据: " + deserialized.getDataList());
        
        // 对比：如果使用默认序列化，会序列化整个1000元素的数组
        System.out.println("\n性能优化说明:");
        System.out.println("  默认序列化会序列化1000个数组元素（包括null）");
        System.out.println("  自定义序列化只序列化3个非null元素");
        System.out.println("  大大减少了序列化数据的大小");
    }
}
