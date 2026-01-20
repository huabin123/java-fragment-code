package com.fragment.core.serialization.examples;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体类 - 序列化示例
 * 
 * 演示内容：
 * 1. 正确声明serialVersionUID
 * 2. 使用transient处理敏感字段
 * 3. 自定义序列化和反序列化
 * 4. 版本兼容性处理
 * 
 * @author fragment
 * @since 1.0.0
 */
public class User implements Serializable {
    
    /**
     * 序列化版本号
     * 
     * 版本历史：
     * 1L - 2024-01-01 v1.0.0 初始版本（userId, username, password）
     * 1L - 2024-03-01 v1.1.0 添加email字段（向后兼容）
     * 1L - 2024-06-01 v1.2.0 添加createTime字段（向后兼容）
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码（明文，不序列化）
     */
    private transient String password;
    
    /**
     * 加密后的密码（序列化）
     */
    private String encryptedPassword;
    
    /**
     * 邮箱（v1.1.0新增）
     */
    private String email;
    
    /**
     * 创建时间（v1.2.0新增）
     */
    private Date createTime;
    
    /**
     * 无参构造函数
     */
    public User() {
    }
    
    /**
     * 全参构造函数
     */
    public User(Long userId, String username, String password) {
        this.userId = userId;
        this.username = username;
        setPassword(password);
        this.createTime = new Date();
    }
    
    /**
     * 设置密码（自动加密）
     */
    public void setPassword(String password) {
        this.password = password;
        this.encryptedPassword = encrypt(password);
    }
    
    /**
     * 获取密码
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * 简单加密（实际应使用AES等算法）
     */
    private String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        // 简单示例：Base64编码（实际应使用真正的加密算法）
        return java.util.Base64.getEncoder().encodeToString(plain.getBytes());
    }
    
    /**
     * 简单解密
     */
    private String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        return new String(java.util.Base64.getDecoder().decode(encrypted));
    }
    
    /**
     * 自定义序列化
     * 
     * 说明：
     * 1. 使用defaultWriteObject()序列化非transient字段
     * 2. 可以添加自定义逻辑
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        // 序列化非transient字段
        out.defaultWriteObject();
        
        // 可以在这里添加自定义序列化逻辑
        // 例如：写入版本号、额外数据等
    }
    
    /**
     * 自定义反序列化
     * 
     * 说明：
     * 1. 使用defaultReadObject()反序列化非transient字段
     * 2. 为新增字段设置默认值（兼容旧版本）
     * 3. 重新初始化transient字段
     */
    private void readObject(ObjectInputStream in) 
            throws IOException, ClassNotFoundException {
        // 反序列化非transient字段
        in.defaultReadObject();
        
        // 为v1.1.0新增的email字段设置默认值
        if (email == null) {
            email = "";
        }
        
        // 为v1.2.0新增的createTime字段设置默认值
        if (createTime == null) {
            createTime = new Date();
        }
        
        // 重新初始化transient字段
        if (encryptedPassword != null) {
            this.password = decrypt(encryptedPassword);
        }
    }
    
    // Getter和Setter方法
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
