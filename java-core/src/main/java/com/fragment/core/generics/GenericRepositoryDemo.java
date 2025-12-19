package com.fragment.core.generics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 泛型在实际应用中的示例：通用仓库模式
 * 演示如何使用泛型创建一个通用的仓库接口和实现
 */
public class GenericRepositoryDemo {

    public static void main(String[] args) {
        // 创建用户仓库
        Repository<User, Long> userRepository = new InMemoryRepository<>();
        
        // 添加用户
        User user1 = new User(1L, "张三", "zhangsan@example.com", 25);
        User user2 = new User(2L, "李四", "lisi@example.com", 30);
        User user3 = new User(3L, "王五", "wangwu@example.com", 22);
        
        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);
        
        // 查找用户
        System.out.println("===== 用户仓库示例 =====");
        System.out.println("查找 ID 为 2 的用户:");
        userRepository.findById(2L).ifPresent(System.out::println);
        
        System.out.println("\n所有用户:");
        userRepository.findAll().forEach(System.out::println);
        
        System.out.println("\n年龄大于 25 的用户:");
        userRepository.findByPredicate(user -> user.getAge() > 25).forEach(System.out::println);
        
        // 创建产品仓库
        Repository<Product, String> productRepository = new InMemoryRepository<>();
        
        // 添加产品
        Product product1 = new Product("P001", "手机", 5999.99, "电子产品");
        Product product2 = new Product("P002", "笔记本电脑", 8999.99, "电子产品");
        Product product3 = new Product("P003", "书籍", 59.99, "图书");
        
        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);
        
        // 查找产品
        System.out.println("\n===== 产品仓库示例 =====");
        System.out.println("查找 ID 为 P002 的产品:");
        productRepository.findById("P002").ifPresent(System.out::println);
        
        System.out.println("\n所有产品:");
        productRepository.findAll().forEach(System.out::println);
        
        System.out.println("\n价格大于 6000 的产品:");
        productRepository.findByPredicate(product -> product.getPrice() > 6000).forEach(System.out::println);
        
        System.out.println("\n电子产品类别的产品:");
        productRepository.findByPredicate(product -> "电子产品".equals(product.getCategory())).forEach(System.out::println);
        
        // 演示服务层使用
        System.out.println("\n===== 服务层示例 =====");
        UserService userService = new UserService(userRepository);
        
        System.out.println("查找邮箱包含 'example.com' 的用户:");
        userService.findUsersByEmailDomain("example.com").forEach(System.out::println);
        
        System.out.println("\n更新用户年龄:");
        User updatedUser = userService.updateUserAge(1L, 26);
        System.out.println("更新后的用户: " + updatedUser);
    }

    /**
     * 泛型仓库接口
     * @param <T> 实体类型
     * @param <ID> ID 类型
     */
    interface Repository<T, ID> {
        /**
         * 保存实体
         * @param entity 要保存的实体
         * @return 保存后的实体
         */
        T save(T entity);
        
        /**
         * 根据 ID 查找实体
         * @param id 实体 ID
         * @return 包含实体的 Optional，如果未找到则为空
         */
        Optional<T> findById(ID id);
        
        /**
         * 查找所有实体
         * @return 实体列表
         */
        List<T> findAll();
        
        /**
         * 根据谓词查找实体
         * @param predicate 过滤谓词
         * @return 符合条件的实体列表
         */
        List<T> findByPredicate(Predicate<T> predicate);
        
        /**
         * 删除实体
         * @param id 要删除的实体 ID
         */
        void delete(ID id);
    }

    /**
     * 内存仓库实现
     * @param <T> 实体类型
     * @param <ID> ID 类型
     */
    static class InMemoryRepository<T extends Entity<ID>, ID> implements Repository<T, ID> {
        private final Map<ID, T> storage = new HashMap<>();
        
        @Override
        public T save(T entity) {
            storage.put(entity.getId(), entity);
            return entity;
        }
        
        @Override
        public Optional<T> findById(ID id) {
            return Optional.ofNullable(storage.get(id));
        }
        
        @Override
        public List<T> findAll() {
            return new ArrayList<>(storage.values());
        }
        
        @Override
        public List<T> findByPredicate(Predicate<T> predicate) {
            return storage.values().stream()
                    .filter(predicate)
                    .collect(Collectors.toList());
        }
        
        @Override
        public void delete(ID id) {
            storage.remove(id);
        }
    }

    /**
     * 实体接口
     * @param <ID> ID 类型
     */
    interface Entity<ID> {
        /**
         * 获取实体 ID
         * @return 实体 ID
         */
        ID getId();
    }

    /**
     * 用户实体
     */
    static class User implements Entity<Long> {
        private final Long id;
        private final String name;
        private final String email;
        private int age;
        
        public User(Long id, String name, String email, int age) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.age = age;
        }
        
        @Override
        public Long getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getEmail() {
            return email;
        }
        
        public int getAge() {
            return age;
        }
        
        public void setAge(int age) {
            this.age = age;
        }
        
        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", age=" + age +
                    '}';
        }
    }

    /**
     * 产品实体
     */
    static class Product implements Entity<String> {
        private final String id;
        private final String name;
        private final double price;
        private final String category;
        
        public Product(String id, String name, double price, String category) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.category = category;
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public double getPrice() {
            return price;
        }
        
        public String getCategory() {
            return category;
        }
        
        @Override
        public String toString() {
            return "Product{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", price=" + price +
                    ", category='" + category + '\'' +
                    '}';
        }
    }

    /**
     * 用户服务类
     * 演示如何在服务层使用泛型仓库
     */
    static class UserService {
        private final Repository<User, Long> userRepository;
        
        public UserService(Repository<User, Long> userRepository) {
            this.userRepository = userRepository;
        }
        
        /**
         * 根据邮箱域名查找用户
         * @param domain 邮箱域名
         * @return 符合条件的用户列表
         */
        public List<User> findUsersByEmailDomain(String domain) {
            return userRepository.findByPredicate(user -> user.getEmail().contains(domain));
        }
        
        /**
         * 更新用户年龄
         * @param userId 用户 ID
         * @param newAge 新年龄
         * @return 更新后的用户
         */
        public User updateUserAge(Long userId, int newAge) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
            
            user.setAge(newAge);
            return userRepository.save(user);
        }
    }
}
