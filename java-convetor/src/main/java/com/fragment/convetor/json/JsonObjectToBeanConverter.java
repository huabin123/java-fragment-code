package com.fragment.convetor.json;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fragment.convetor.core.AbstractConverter;
import com.fragment.convetor.core.ConvertException;

/**
 * JSON对象字符串转Bean转换器
 * 
 * <p>使用Hutool工具类实现JSON对象字符串到Bean的转换
 * 
 * <p>使用示例：
 * <pre>
 * // 创建转换器
 * JsonObjectToBeanConverter<User> converter = new JsonObjectToBeanConverter<>(User.class);
 * 
 * // 执行转换
 * String jsonObject = "{\"name\":\"张三\",\"age\":20}";
 * User user = converter.convert(jsonObject);
 * </pre>
 * 
 * @param <T> Bean类型
 * @author fragment
 */
public class JsonObjectToBeanConverter<T> extends AbstractConverter<String, T> {
    
    /**
     * Bean的Class对象
     */
    private final Class<T> beanClass;
    
    /**
     * 构造函数
     * 
     * @param beanClass Bean的Class对象
     */
    public JsonObjectToBeanConverter(Class<T> beanClass) {
        super(String.class, beanClass);
        this.beanClass = beanClass;
    }
    
    @Override
    protected T doConvert(String source) throws Exception {
        // 1. 校验JSON字符串
        if (StrUtil.isBlank(source)) {
            throw new ConvertException("JSON字符串不能为空");
        }
        
        // 2. 去除首尾空格
        source = source.trim();
        
        // 3. 校验是否是JSON对象格式
        if (!source.startsWith("{") || !source.endsWith("}")) {
            throw new ConvertException("不是有效的JSON对象格式，必须以{开头并以}结尾");
        }
        
        // 4. 使用Hutool解析JSON对象
        JSONObject jsonObject = JSONUtil.parseObj(source);
        
        // 5. 转换为Bean
        return jsonObject.toBean(beanClass);
    }
    
    @Override
    public String getName() {
        return "JsonObjectToBeanConverter<" + beanClass.getSimpleName() + ">";
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
