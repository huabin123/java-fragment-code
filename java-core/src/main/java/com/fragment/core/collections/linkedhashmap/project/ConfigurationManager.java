package com.fragment.core.collections.linkedhashmap.project;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 配置管理器
 * 
 * 使用LinkedHashMap保持配置项的插入顺序
 * 
 * 特性：
 * 1. 保持配置项的插入顺序
 * 2. 支持从Properties文件加载
 * 3. 支持保存到Properties文件
 * 4. 支持配置项的增删改查
 * 5. 支持配置项的验证
 * 
 * @author huabin
 */
public class ConfigurationManager {
    
    private final Map<String, String> config = new LinkedHashMap<>();
    private String configFile;

    /**
     * 构造函数
     */
    public ConfigurationManager() {
    }

    /**
     * 构造函数
     * 
     * @param configFile 配置文件路径
     */
    public ConfigurationManager(String configFile) {
        this.configFile = configFile;
    }

    /**
     * 从Properties文件加载配置
     * 
     * @param filename 文件名
     * @throws IOException IO异常
     */
    public void loadFromFile(String filename) throws IOException {
        this.configFile = filename;
        Properties props = new Properties();
        
        try (FileInputStream fis = new FileInputStream(filename)) {
            props.load(fis);
            
            // 按顺序加载配置项
            for (String key : props.stringPropertyNames()) {
                config.put(key, props.getProperty(key));
            }
            
            System.out.println("[Config] 从文件加载配置: " + filename);
            System.out.println("[Config] 加载了 " + config.size() + " 个配置项");
        }
    }

    /**
     * 保存配置到Properties文件
     * 
     * @param filename 文件名
     * @throws IOException IO异常
     */
    public void saveToFile(String filename) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            // 按插入顺序写入配置项
            for (Map.Entry<String, String> entry : config.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
            
            System.out.println("[Config] 保存配置到文件: " + filename);
            System.out.println("[Config] 保存了 " + config.size() + " 个配置项");
        }
    }

    /**
     * 获取配置值
     * 
     * @param key 配置键
     * @return 配置值，如果不存在返回null
     */
    public String get(String key) {
        return config.get(key);
    }

    /**
     * 获取配置值（带默认值）
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值，如果不存在返回默认值
     */
    public String get(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    /**
     * 获取整数配置值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public int getInt(String key, int defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.out.println("[Config] 配置项 " + key + " 不是有效的整数，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取布尔配置值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 设置配置值
     * 
     * @param key 配置键
     * @param value 配置值
     */
    public void set(String key, String value) {
        config.put(key, value);
        System.out.println("[Config] 设置配置: " + key + " = " + value);
    }

    /**
     * 删除配置项
     * 
     * @param key 配置键
     * @return 被删除的值，如果不存在返回null
     */
    public String remove(String key) {
        String value = config.remove(key);
        if (value != null) {
            System.out.println("[Config] 删除配置: " + key);
        }
        return value;
    }

    /**
     * 判断配置项是否存在
     * 
     * @param key 配置键
     * @return 是否存在
     */
    public boolean contains(String key) {
        return config.containsKey(key);
    }

    /**
     * 获取所有配置项
     */
    public Map<String, String> getAll() {
        return new LinkedHashMap<>(config);
    }

    /**
     * 清空所有配置
     */
    public void clear() {
        config.clear();
        System.out.println("[Config] 清空所有配置");
    }

    /**
     * 获取配置项数量
     */
    public int size() {
        return config.size();
    }

    /**
     * 打印所有配置（按插入顺序）
     */
    public void printConfig() {
        System.out.println("\n========== 配置项（按插入顺序） ==========");
        if (config.isEmpty()) {
            System.out.println("（无配置项）");
        } else {
            config.forEach((key, value) -> 
                System.out.println("  " + key + " = " + value));
        }
        System.out.println("========================================\n");
    }

    /**
     * 测试示例
     */
    public static void main(String[] args) {
        System.out.println("========== 配置管理器测试 ==========\n");
        
        ConfigurationManager config = new ConfigurationManager();
        
        // 添加配置项
        config.set("app.name", "MyApp");
        config.set("app.version", "1.0.0");
        config.set("server.host", "localhost");
        config.set("server.port", "8080");
        config.set("database.url", "jdbc:mysql://localhost:3306/mydb");
        config.set("database.username", "root");
        config.set("database.password", "password");
        
        // 打印配置
        config.printConfig();
        
        // 获取配置
        System.out.println("获取配置:");
        System.out.println("  app.name: " + config.get("app.name"));
        System.out.println("  server.port: " + config.getInt("server.port", 80));
        System.out.println("  app.debug: " + config.getBoolean("app.debug", false));
        
        System.out.println();
        
        // 修改配置
        config.set("server.port", "9090");
        System.out.println("修改后的配置:");
        config.printConfig();
        
        // 删除配置
        config.remove("database.password");
        System.out.println("删除密码后的配置:");
        config.printConfig();
        
        // 保存到文件
        try {
            String tempFile = System.getProperty("java.io.tmpdir") + "/app.properties";
            config.saveToFile(tempFile);
            
            // 从文件加载
            ConfigurationManager config2 = new ConfigurationManager();
            config2.loadFromFile(tempFile);
            config2.printConfig();
            
            // 删除临时文件
            new File(tempFile).delete();
        } catch (IOException e) {
            System.out.println("文件操作失败: " + e.getMessage());
        }
        
        System.out.println("测试完成");
    }
}
