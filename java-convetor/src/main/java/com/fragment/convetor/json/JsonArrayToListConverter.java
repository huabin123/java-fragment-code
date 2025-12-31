package com.fragment.convetor.json;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.fragment.convetor.core.AbstractConverter;
import com.fragment.convetor.core.ConvertException;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON数组字符串转List转换器
 * 
 * <p>使用Hutool工具类实现JSON数组字符串到List<Bean>的转换
 * 
 * <p>使用示例：
 * <pre>
 * // 创建转换器
 * JsonArrayToListConverter<User> converter = new JsonArrayToListConverter<>(User.class);
 * 
 * // 执行转换
 * String jsonArray = "[{\"name\":\"张三\",\"age\":20},{\"name\":\"李四\",\"age\":25}]";
 * List<User> users = converter.convert(jsonArray);
 * </pre>
 * 
 * @param <T> Bean类型
 * @author fragment
 */
public class JsonArrayToListConverter<T> extends AbstractConverter<String, List<T>> {
    
    /**
     * Bean的Class对象
     */
    private final Class<T> beanClass;
    
    /**
     * 构造函数
     * 
     * @param beanClass Bean的Class对象
     */
    public JsonArrayToListConverter(Class<T> beanClass) {
        super(String.class, (Class<List<T>>) (Class<?>) List.class);
        this.beanClass = beanClass;
    }
    
    @Override
    protected List<T> doConvert(String source) throws Exception {
        // 1. 校验JSON字符串
        if (StrUtil.isBlank(source)) {
            return new ArrayList<>();
        }
        
        // 2. 去除首尾空格
        source = source.trim();
        
        // 3. 校验是否是JSON数组格式
        if (!source.startsWith("[") || !source.endsWith("]")) {
            throw new ConvertException("不是有效的JSON数组格式，必须以[开头并以]结尾");
        }
        
        // 4. 使用Hutool解析JSON数组
        JSONArray jsonArray = JSONUtil.parseArray(source);
        
        // 5. 转换为List<Bean>
        return jsonArray.toList(beanClass);
    }
    
    @Override
    protected List<T> handleNull() {
        // null值返回空List
        return new ArrayList<>();
    }
    
    @Override
    public String getName() {
        return "JsonArrayToListConverter<" + beanClass.getSimpleName() + ">";
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
