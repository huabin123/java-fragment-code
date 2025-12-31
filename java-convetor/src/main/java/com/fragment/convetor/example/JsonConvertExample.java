package com.fragment.convetor.example;

import com.fragment.convetor.json.BeanToJsonConverter;
import com.fragment.convetor.json.JsonArrayToListConverter;
import com.fragment.convetor.json.JsonObjectToBeanConverter;

import java.util.List;

/**
 * JSON转换示例
 * 
 * @author fragment
 */
public class JsonConvertExample {
    
    /**
     * 示例用户类
     */
    public static class User {
        private String name;
        private Integer age;
        private String email;
        
        public User() {
        }
        
        public User(String name, Integer age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
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
            return "User{name='" + name + "', age=" + age + ", email='" + email + "'}";
        }
    }
    
    public static void main(String[] args) {
        System.out.println("========== JSON转换器使用示例 ==========\n");
        
        // 示例1：JSON数组字符串转List<Bean>
        example1();
        
        System.out.println();
        
        // 示例2：JSON对象字符串转Bean
        example2();
        
        System.out.println();
        
        // 示例3：Bean转JSON字符串
        example3();
    }
    
    /**
     * 示例1：JSON数组字符串转List<Bean>
     */
    private static void example1() {
        System.out.println("===== 示例1：JSON数组字符串转List<Bean> =====");
        
        // 1. 创建转换器
        JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
        
        // 2. 准备JSON数组字符串
        String jsonArray = "[" +
                "{\"name\":\"张三\",\"age\":20,\"email\":\"zhangsan@example.com\"}," +
                "{\"name\":\"李四\",\"age\":25,\"email\":\"lisi@example.com\"}," +
                "{\"name\":\"王五\",\"age\":30,\"email\":\"wangwu@example.com\"}" +
                "]";
        
        System.out.println("原始JSON数组:");
        System.out.println(jsonArray);
        
        // 3. 执行转换
        List<User> users = converter.convert(jsonArray);
        
        // 4. 输出结果
        System.out.println("\n转换结果:");
        for (int i = 0; i < users.size(); i++) {
            System.out.println((i + 1) + ". " + users.get(i));
        }
    }
    
    /**
     * 示例2：JSON对象字符串转Bean
     */
    private static void example2() {
        System.out.println("===== 示例2：JSON对象字符串转Bean =====");
        
        // 1. 创建转换器
        JsonObjectToBeanConverter<User> converter = new JsonObjectToBeanConverter<>(User.class);
        
        // 2. 准备JSON对象字符串
        String jsonObject = "{\"name\":\"赵六\",\"age\":35,\"email\":\"zhaoliu@example.com\"}";
        
        System.out.println("原始JSON对象:");
        System.out.println(jsonObject);
        
        // 3. 执行转换
        User user = converter.convert(jsonObject);
        
        // 4. 输出结果
        System.out.println("\n转换结果:");
        System.out.println(user);
    }
    
    /**
     * 示例3：Bean转JSON字符串
     */
    private static void example3() {
        System.out.println("===== 示例3：Bean转JSON字符串 =====");
        
        // 1. 创建转换器（格式化输出）
        BeanToJsonConverter<User> converter = new BeanToJsonConverter<>(User.class, true);
        
        // 2. 创建User对象
        User user = new User("孙七", 28, "sunqi@example.com");
        
        System.out.println("原始Bean对象:");
        System.out.println(user);
        
        // 3. 执行转换
        String json = converter.convert(user);
        
        // 4. 输出结果
        System.out.println("\n转换结果（格式化）:");
        System.out.println(json);
        
        // 5. 紧凑格式
        converter.setPrettyPrint(false);
        String compactJson = converter.convert(user);
        System.out.println("\n转换结果（紧凑）:");
        System.out.println(compactJson);
    }
}
