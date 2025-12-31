package com.fragment.convetor.example;

import com.fragment.convetor.core.ConverterFactory;

import java.util.List;

/**
 * 转换器工厂使用示例
 * 
 * @author fragment
 */
public class ConverterFactoryExample {
    
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
        System.out.println("========== 转换器工厂使用示例 ==========\n");
        
        // 示例1：使用工厂获取转换器
        example1();
        
        System.out.println();
        
        // 示例2：使用快捷转换方法
        example2();
        
        System.out.println();
        
        // 示例3：转换器复用
        example3();
    }
    
    /**
     * 示例1：使用工厂获取转换器
     */
    private static void example1() {
        System.out.println("===== 示例1：使用工厂获取转换器 =====");
        
        // 准备数据
        String jsonArray = "[" +
                "{\"name\":\"张三\",\"age\":20,\"email\":\"zhangsan@example.com\"}," +
                "{\"name\":\"李四\",\"age\":25,\"email\":\"lisi@example.com\"}" +
                "]";
        
        // 使用工厂获取转换器
        List<User> users = ConverterFactory.getJsonArrayToListConverter(User.class)
                .convert(jsonArray);
        
        System.out.println("转换结果:");
        for (User user : users) {
            System.out.println("  " + user);
        }
    }
    
    /**
     * 示例2：使用快捷转换方法
     */
    private static void example2() {
        System.out.println("===== 示例2：使用快捷转换方法 =====");
        
        // 1. JSON数组转List（一行代码搞定）
        String jsonArray = "[{\"name\":\"王五\",\"age\":30},{\"name\":\"赵六\",\"age\":35}]";
        List<User> users = ConverterFactory.jsonArrayToList(jsonArray, User.class);
        
        System.out.println("JSON数组转List:");
        for (User user : users) {
            System.out.println("  " + user);
        }
        
        System.out.println();
        
        // 2. JSON对象转Bean（一行代码搞定）
        String jsonObject = "{\"name\":\"孙七\",\"age\":28,\"email\":\"sunqi@example.com\"}";
        User user = ConverterFactory.jsonObjectToBean(jsonObject, User.class);
        
        System.out.println("JSON对象转Bean:");
        System.out.println("  " + user);
        
        System.out.println();
        
        // 3. Bean转JSON（一行代码搞定）
        User newUser = new User("周八", 32, "zhouba@example.com");
        String json = ConverterFactory.beanToJson(newUser);
        
        System.out.println("Bean转JSON（紧凑）:");
        System.out.println("  " + json);
        
        System.out.println();
        
        // 4. Bean转JSON（格式化）
        String prettyJson = ConverterFactory.beanToJson(newUser, true);
        
        System.out.println("Bean转JSON（格式化）:");
        System.out.println(prettyJson);
    }
    
    /**
     * 示例3：转换器复用
     */
    private static void example3() {
        System.out.println("===== 示例3：转换器复用 =====");
        
        System.out.println("初始缓存大小: " + ConverterFactory.getCacheSize());
        
        // 第1次调用，创建新的转换器
        ConverterFactory.jsonArrayToList("[]", User.class);
        System.out.println("第1次调用后缓存大小: " + ConverterFactory.getCacheSize());
        
        // 第2次调用，复用缓存的转换器
        ConverterFactory.jsonArrayToList("[]", User.class);
        System.out.println("第2次调用后缓存大小: " + ConverterFactory.getCacheSize());
        
        // 调用其他转换器
        ConverterFactory.jsonObjectToBean("{}", User.class);
        System.out.println("调用其他转换器后缓存大小: " + ConverterFactory.getCacheSize());
        
        // 清除缓存
        ConverterFactory.clearCache();
        System.out.println("清除缓存后大小: " + ConverterFactory.getCacheSize());
        
        System.out.println("\n说明:");
        System.out.println("1. 转换器会被缓存，避免重复创建");
        System.out.println("2. 相同类型的转换器会复用同一个实例");
        System.out.println("3. 提高性能，减少内存占用");
    }
}
