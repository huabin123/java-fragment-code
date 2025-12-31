package com.fragment.convetor.json;

import cn.hutool.json.JSONUtil;
import com.fragment.convetor.core.AbstractConverter;

/**
 * Bean转JSON字符串转换器
 * 
 * <p>使用Hutool工具类实现Bean到JSON字符串的转换
 * 
 * <p>使用示例：
 * <pre>
 * // 创建转换器
 * BeanToJsonConverter<User> converter = new BeanToJsonConverter<>(User.class);
 * 
 * // 执行转换
 * User user = new User("张三", 20);
 * String json = converter.convert(user);
 * </pre>
 * 
 * @param <T> Bean类型
 * @author fragment
 */
public class BeanToJsonConverter<T> extends AbstractConverter<T, String> {
    
    /**
     * Bean的Class对象
     */
    private final Class<T> beanClass;
    
    /**
     * 是否格式化输出
     */
    private boolean prettyPrint = false;
    
    /**
     * 构造函数
     * 
     * @param beanClass Bean的Class对象
     */
    public BeanToJsonConverter(Class<T> beanClass) {
        super(beanClass, String.class);
        this.beanClass = beanClass;
    }
    
    /**
     * 构造函数
     * 
     * @param beanClass Bean的Class对象
     * @param prettyPrint 是否格式化输出
     */
    public BeanToJsonConverter(Class<T> beanClass, boolean prettyPrint) {
        super(beanClass, String.class);
        this.beanClass = beanClass;
        this.prettyPrint = prettyPrint;
    }
    
    @Override
    protected String doConvert(T source) throws Exception {
        if (prettyPrint) {
            // 格式化输出
            return JSONUtil.toJsonPrettyStr(source);
        } else {
            // 紧凑输出
            return JSONUtil.toJsonStr(source);
        }
    }
    
    @Override
    protected String handleNull() {
        // null值返回"null"字符串
        return "null";
    }
    
    @Override
    public String getName() {
        return "BeanToJsonConverter<" + beanClass.getSimpleName() + ">";
    }
    
    /**
     * 设置是否格式化输出
     * 
     * @param prettyPrint 是否格式化输出
     * @return 当前转换器实例
     */
    public BeanToJsonConverter<T> setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }
    
    /**
     * 获取Bean的Class对象
     * 
     * @return Bean的Class对象
     */
    public Class<T> getBeanClass() {
        return beanClass;
    }
}
