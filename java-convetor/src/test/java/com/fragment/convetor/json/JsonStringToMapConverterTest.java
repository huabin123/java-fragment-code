package com.fragment.convetor.json;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * JsonStringToMapConverter测试类
 * 
 * @author fragment
 */
public class JsonStringToMapConverterTest {
    
    @Test
    public void testConvertStringValue() {
        // 创建转换器（String类型的值）
        JsonStringToMapConverter<String> converter = new JsonStringToMapConverter<>(String.class);
        
        // 准备JSON字符串
        String json = "{\"小明\":\"3\",\"小红\":\"4\",\"小刚\":\"5\"}";
        
        // 执行转换
        Map<String, String> map = converter.convert(json);
        
        // 验证结果
        assertNotNull(map);
        assertEquals(3, map.size());
        assertEquals("3", map.get("小明"));
        assertEquals("4", map.get("小红"));
        assertEquals("5", map.get("小刚"));
        
        System.out.println("转换成功（String类型）：");
        map.forEach((key, value) -> System.out.println("  " + key + " = " + value));
    }
    
    @Test
    public void testConvertIntegerValue() {
        // 创建转换器（Integer类型的值）
        JsonStringToMapConverter<Integer> converter = new JsonStringToMapConverter<>(Integer.class);
        
        // 准备JSON字符串（数字类型）
        String json = "{\"小明\":3,\"小红\":4,\"小刚\":5}";
        
        // 执行转换
        Map<String, Integer> map = converter.convert(json);
        
        // 验证结果
        assertNotNull(map);
        assertEquals(3, map.size());
        assertEquals(Integer.valueOf(3), map.get("小明"));
        assertEquals(Integer.valueOf(4), map.get("小红"));
        assertEquals(Integer.valueOf(5), map.get("小刚"));
        
        System.out.println("转换成功（Integer类型）：");
        map.forEach((key, value) -> System.out.println("  " + key + " = " + value));
    }
    
    @Test
    public void testConvertEmptyObject() {
        JsonStringToMapConverter<String> converter = new JsonStringToMapConverter<>(String.class);
        
        // 空对象
        String json = "{}";
        Map<String, String> map = converter.convert(json);
        
        assertNotNull(map);
        assertEquals(0, map.size());
        System.out.println("空对象转换成功！");
    }
    
    @Test
    public void testConvertNull() {
        JsonStringToMapConverter<String> converter = new JsonStringToMapConverter<>(String.class);
        
        // null值
        Map<String, String> map = converter.convert(null);
        
        assertNotNull(map);
        assertEquals(0, map.size());
        System.out.println("null值转换成功，返回空Map！");
    }
    
    @Test
    public void testConvertBlankString() {
        JsonStringToMapConverter<String> converter = new JsonStringToMapConverter<>(String.class);
        
        // 空字符串
        Map<String, String> map = converter.convert("   ");
        
        assertNotNull(map);
        assertEquals(0, map.size());
        System.out.println("空字符串转换成功，返回空Map！");
    }
    
    @Test(expected = Exception.class)
    public void testConvertInvalidJson() {
        JsonStringToMapConverter<String> converter = new JsonStringToMapConverter<>(String.class);
        
        // 无效的JSON格式（数组格式）
        String invalidJson = "[\"小明\",\"小红\"]";
        converter.convert(invalidJson);
    }
    
    @Test
    public void testGetName() {
        JsonStringToMapConverter<String> converter = new JsonStringToMapConverter<>(String.class);
        String name = converter.getName();
        
        assertEquals("JsonStringToMapConverter<String, String>", name);
        System.out.println("转换器名称: " + name);
    }
}
