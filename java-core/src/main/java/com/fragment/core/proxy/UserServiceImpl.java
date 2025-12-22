package com.fragment.core.proxy;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务实现类
 */
public class UserServiceImpl implements UserService {
    
    private final Map<Long, User> users = new HashMap<>();
    private Long nextId = 1L;

    public UserServiceImpl() {
        // 初始化一些测试数据
        save(new User(null, "张三", "zhangsan@example.com", 25));
        save(new User(null, "李四", "lisi@example.com", 30));
        save(new User(null, "王五", "wangwu@example.com", 28));
    }

    @Override
    public User findById(Long id) {
        // 模拟数据库查询延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        User user = users.get(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + id);
        }
        return user;
    }

    @Override
    public void save(User user) {
        // 模拟数据库保存延迟
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (user.getId() == null) {
            user.setId(nextId++);
        }
        users.put(user.getId(), user);
        System.out.println("保存用户: " + user);
    }

    @Override
    public boolean deleteById(Long id) {
        // 模拟数据库删除延迟
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        User removed = users.remove(id);
        if (removed != null) {
            System.out.println("删除用户: " + removed);
            return true;
        }
        return false;
    }

    @Override
    public void update(User user) {
        // 模拟数据库更新延迟
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (!users.containsKey(user.getId())) {
            throw new RuntimeException("用户不存在: " + user.getId());
        }
        users.put(user.getId(), user);
        System.out.println("更新用户: " + user);
    }

    @Override
    public long count() {
        // 模拟数据库统计延迟
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return users.size();
    }
}
