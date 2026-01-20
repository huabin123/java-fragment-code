package com.fragment.core.serialization.examples;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单实体类 - 版本兼容性示例
 * 
 * 演示内容：
 * 1. 版本演进过程
 * 2. 向后兼容性处理
 * 3. 数据版本号管理
 * 
 * @author fragment
 * @since 1.0.0
 */
public class Order implements Serializable {
    
    /**
     * 序列化版本号
     * 
     * 版本历史：
     * 1L - 2024-01-01 v1.0.0 初始版本（orderId, amount）
     * 1L - 2024-02-01 v1.1.0 添加createTime字段（兼容）
     * 1L - 2024-03-01 v1.2.0 添加userId字段（兼容）
     * 1L - 2024-04-01 v1.3.0 添加remark字段（兼容）
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * 数据版本号（用于运行时版本判断）
     */
    private int dataVersion = 3;
    
    /**
     * 订单ID
     */
    private Long orderId;
    
    /**
     * 订单金额
     */
    private BigDecimal amount;
    
    /**
     * 创建时间（v1.1.0新增）
     */
    private Date createTime;
    
    /**
     * 用户ID（v1.2.0新增）
     */
    private Long userId;
    
    /**
     * 备注（v1.3.0新增）
     */
    private String remark;
    
    /**
     * 无参构造函数
     */
    public Order() {
    }
    
    /**
     * 构造函数（v1.0.0）
     */
    public Order(Long orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
        this.createTime = new Date();
        this.dataVersion = 3;
    }
    
    /**
     * 完整构造函数（v1.3.0）
     */
    public Order(Long orderId, BigDecimal amount, Long userId, String remark) {
        this.orderId = orderId;
        this.amount = amount;
        this.createTime = new Date();
        this.userId = userId;
        this.remark = remark;
        this.dataVersion = 3;
    }
    
    /**
     * 自定义序列化
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
    
    /**
     * 自定义反序列化 - 处理版本兼容性
     */
    private void readObject(ObjectInputStream in) 
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        
        // 根据数据版本号处理兼容性
        if (dataVersion < 1) {
            // v1.0.0数据，没有createTime
            if (createTime == null) {
                createTime = new Date();
            }
        }
        
        if (dataVersion < 2) {
            // v1.1.0数据，没有userId
            if (userId == null) {
                userId = 0L; // 默认用户ID
            }
        }
        
        if (dataVersion < 3) {
            // v1.2.0数据，没有remark
            if (remark == null) {
                remark = "";
            }
        }
        
        // 更新到最新版本
        this.dataVersion = 3;
    }
    
    // Getter和Setter方法
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public int getDataVersion() {
        return dataVersion;
    }
    
    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", amount=" + amount +
                ", createTime=" + createTime +
                ", userId=" + userId +
                ", remark='" + remark + '\'' +
                ", dataVersion=" + dataVersion +
                '}';
    }
}
