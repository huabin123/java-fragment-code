package com.fragment.convetor.example;

import com.fragment.convetor.core.ConverterFactory;
import com.fragment.convetor.json.JsonStringToMapConverter;

import java.util.Map;

/**
 * JSON字符串转Map示例
 * 
 * @author fragment
 */
public class JsonToMapExample {
    
    public static void main(String[] args) {
        System.out.println("========== JSON字符串转Map示例 ==========\n");
        
        // 示例1：使用快捷方法（推荐）
        example1();
        
        System.out.println();
        
        // 示例2：使用工厂模式
        example2();
        
        System.out.println();
        
        // 示例3：直接创建转换器
        example3();
        
        System.out.println();
        
        // 示例4：不同类型的值
        example4();
    }
    
    /**
     * 示例1：使用快捷方法（推荐）
     */
    private static void example1() {
        System.out.println("===== 示例1：使用快捷方法（推荐） =====");
        
        // 准备JSON字符串
        String json = "{\"小明\":\"3\",\"小红\":\"4\",\"小刚\":\"5\"}";
        
        System.out.println("原始JSON字符串:");
        System.out.println(json);
        
        // 一行代码转换
        Map<String, String> map = ConverterFactory.jsonStringToMap(json, String.class);
        
        System.out.println("\n转换结果:");
        map.forEach((key, value) -> System.out.println("  " + key + " = " + value));
    }
    
    /**
     * 示例2：使用工厂模式
     */
    private static void example2() {
        System.out.println("===== 示例2：使用工厂模式 =====");
        
        // 获取转换器（会自动缓存）
        JsonStringToMapConverter<String> converter = 
            ConverterFactory.getJsonStringToMapConverter(String.class);
        
        // 准备JSON字符串
        String json = "{\"语文\":\"90\",\"数学\":\"85\",\"英语\":\"92\"}";
        
        System.out.println("原始JSON字符串:");
        System.out.println(json);
        
        // 执行转换
        Map<String, String> scores = converter.convert(json);
        
        System.out.println("\n转换结果:");
        scores.forEach((subject, score) -> 
            System.out.println("  " + subject + ": " + score + "分"));
    }
    
    /**
     * 示例3：直接创建转换器
     */
    private static void example3() {
        System.out.println("===== 示例3：直接创建转换器 =====");
        
        // 直接创建转换器
        JsonStringToMapConverter<String> converter = 
            new JsonStringToMapConverter<>(String.class);
        
        // 准备JSON字符串
        String json = "{\"北京\":\"晴\",\"上海\":\"多云\",\"广州\":\"雨\"}";
        
        System.out.println("原始JSON字符串:");
        System.out.println(json);
        
        // 执行转换
        Map<String, String> weather = converter.convert(json);
        
        System.out.println("\n转换结果:");
        weather.forEach((city, condition) -> 
            System.out.println("  " + city + ": " + condition));
    }
    
    /**
     * 示例4：不同类型的值
     */
    private static void example4() {
        System.out.println("===== 示例4：不同类型的值 =====");
        
        // 1. Integer类型
        System.out.println("1. Integer类型:");
        String jsonInt = "{\"小明\":3,\"小红\":4,\"小刚\":5}";
        Map<String, Integer> intMap = ConverterFactory.jsonStringToMap(jsonInt, Integer.class);
        intMap.forEach((name, count) -> 
            System.out.println("  " + name + ": " + count + "个"));
        
        System.out.println();
        
        // 2. Double类型
        System.out.println("2. Double类型:");
        String jsonDouble = "{\"苹果\":5.5,\"香蕉\":3.8,\"橙子\":4.2}";
        Map<String, Double> doubleMap = ConverterFactory.jsonStringToMap(jsonDouble, Double.class);
        doubleMap.forEach((fruit, price) -> 
            System.out.println("  " + fruit + ": " + price + "元/斤"));
        
        System.out.println();
        
        // 3. Boolean类型
        System.out.println("3. Boolean类型:");
        String jsonBoolean = "{\"小明\":true,\"小红\":false,\"小刚\":true}";
        Map<String, Boolean> booleanMap = ConverterFactory.jsonStringToMap(jsonBoolean, Boolean.class);
        booleanMap.forEach((name, passed) -> 
            System.out.println("  " + name + ": " + (passed ? "通过" : "未通过")));
        
        System.out.println();
        
        // 4. 字符串类型的数字（会自动转换）
        System.out.println("4. 字符串类型转Integer:");
        String jsonStringNum = "{\"小明\":\"3\",\"小红\":\"4\",\"小刚\":\"5\"}";
        Map<String, Integer> convertedMap = ConverterFactory.jsonStringToMap(jsonStringNum, Integer.class);
        convertedMap.forEach((name, count) -> 
            System.out.println("  " + name + ": " + count + "个"));
    }
}
