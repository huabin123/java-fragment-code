package com.fragment.core.serialization.examples;

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * 单例模式 - 序列化安全示例
 * 
 * 演示内容：
 * 1. 单例模式的序列化问题
 * 2. 使用readResolve()保护单例
 * 
 * @author fragment
 * @since 1.0.0
 */
public class Singleton implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 单例实例
     */
    private static final Singleton INSTANCE = new Singleton();
    
    /**
     * 私有构造函数
     */
    private Singleton() {
        // 防止反射攻击
        if (INSTANCE != null) {
            throw new IllegalStateException("单例已存在，不能创建新实例");
        }
    }
    
    /**
     * 获取单例实例
     */
    public static Singleton getInstance() {
        return INSTANCE;
    }
    
    /**
     * 反序列化时返回单例实例
     * 
     * 说明：
     * 如果不实现此方法，反序列化会创建新的实例，破坏单例模式
     * 实现此方法后，反序列化时会返回INSTANCE，保持单例
     * 
     * @return 单例实例
     * @throws ObjectStreamException 对象流异常
     */
    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }
    
    /**
     * 业务方法示例
     */
    public void doSomething() {
        System.out.println("单例方法执行: " + this);
    }
}
