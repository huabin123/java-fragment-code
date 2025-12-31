package com.fragment.convetor.json;

import com.fragment.convetor.model.User;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * JsonArrayToListConverter测试类
 * 
 * @author fragment
 */
public class JsonArrayToListConverterTest {
    
    @Test
    public void testConvert() {
        // 创建转换器
        JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
        
        // 准备JSON数组字符串
        String jsonArray = "[{\"name\":\"张三\",\"age\":20,\"email\":\"zhangsan@example.com\"}," +
                          "{\"name\":\"李四\",\"age\":25,\"email\":\"lisi@example.com\"}," +
                          "{\"name\":\"王五\",\"age\":30,\"email\":\"wangwu@example.com\"}]";
        
        // 执行转换
        List<User> users = converter.convert(jsonArray);
        
        // 验证结果
        assertNotNull(users);
        assertEquals(3, users.size());
        
        // 验证第一个用户
        User user1 = users.get(0);
        assertEquals("张三", user1.getName());
        assertEquals(Integer.valueOf(20), user1.getAge());
        assertEquals("zhangsan@example.com", user1.getEmail());
        
        // 验证第二个用户
        User user2 = users.get(1);
        assertEquals("李四", user2.getName());
        assertEquals(Integer.valueOf(25), user2.getAge());
        
        // 验证第三个用户
        User user3 = users.get(2);
        assertEquals("王五", user3.getName());
        assertEquals(Integer.valueOf(30), user3.getAge());
        
        System.out.println("转换成功！");
        for (User user : users) {
            System.out.println(user);
        }
    }
    
    @Test
    public void testConvertEmptyArray() {
        JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
        
        // 空数组
        String jsonArray = "[]";
        List<User> users = converter.convert(jsonArray);
        
        assertNotNull(users);
        assertEquals(0, users.size());
        System.out.println("空数组转换成功！");
    }
    
    @Test
    public void testConvertNull() {
        JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
        
        // null值
        List<User> users = converter.convert(null);
        
        assertNotNull(users);
        assertEquals(0, users.size());
        System.out.println("null值转换成功，返回空List！");
    }
    
    @Test
    public void testConvertBlankString() {
        JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
        
        // 空字符串
        List<User> users = converter.convert("   ");
        
        assertNotNull(users);
        assertEquals(0, users.size());
        System.out.println("空字符串转换成功，返回空List！");
    }
    
    @Test(expected = Exception.class)
    public void testConvertInvalidJson() {
        JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
        
        // 无效的JSON格式
        String invalidJson = "{\"name\":\"张三\"}";
        converter.convert(invalidJson);
    }
    
    @Test
    public void testGetName() {
        JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
        String name = converter.getName();
        
        assertEquals("JsonArrayToListConverter<User>", name);
        System.out.println("转换器名称: " + name);
    }
}
