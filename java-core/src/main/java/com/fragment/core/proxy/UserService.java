package com.fragment.core.proxy;

/**
 * 用户服务接口 - 用于演示JDK动态代理
 */
public interface UserService {
    
    /**
     * 根据ID查询用户
     */
    User findById(Long id);
    
    /**
     * 保存用户
     */
    void save(User user);
    
    /**
     * 删除用户
     */
    boolean deleteById(Long id);
    
    /**
     * 更新用户信息
     */
    void update(User user);
    
    /**
     * 统计用户数量
     */
    long count();
}
