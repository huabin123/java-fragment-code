package com.fragment.convetor.json;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fragment.convetor.core.AbstractConverter;
import com.fragment.convetor.core.ConvertException;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON字符串转Map转换器
 * 
 * <p>使用Hutool工具类实现JSON字符串到Map的转换
 * 
 * <p>使用示例：
 * <pre>
 * // 创建转换器（String类型的值）
 * JsonStringToMapConverter<String> converter = new JsonStringToMapConverter<>(String.class);
 * 
 * // 执行转换
 * String json = "{\"小明\":\"3\",\"小红\":\"4\"}";
 * Map<String, String> map = converter.convert(json);
 * 
 * // 创建转换器（Integer类型的值）
 * JsonStringToMapConverter<Integer> converter2 = new JsonStringToMapConverter<>(Integer.class);
 * String json2 = "{\"小明\":3,\"小红\":4}";
 * Map<String, Integer> map2 = converter2.convert(json2);
 * </pre>
 * 
 * @param <V> Map的值类型
 * @author fragment
 */
public class JsonStringToMapConverter<V> extends AbstractConverter<String, Map<String, V>> {
    
    /**
     * Map值的Class对象
     */
    private final Class<V> valueClass;
    
    /**
     * 构造函数
     * 
     * @param valueClass Map值的Class对象
     */
    public JsonStringToMapConverter(Class<V> valueClass) {
        super(String.class, (Class<Map<String, V>>) (Class<?>) Map.class);
        this.valueClass = valueClass;
    }
    
    @Override
    protected Map<String, V> doConvert(String source) throws Exception {
        // 1. 校验JSON字符串
        if (StrUtil.isBlank(source)) {
            return new HashMap<>();
        }
        
        // 2. 去除首尾空格
        source = source.trim();
        
        // 3. 校验是否是JSON对象格式
        if (!source.startsWith("{") || !source.endsWith("}")) {
            throw new ConvertException("不是有效的JSON对象格式，必须以{开头并以}结尾");
        }
        
        // 4. 使用Hutool解析JSON对象
        JSONObject jsonObject = JSONUtil.parseObj(source);
        
        // 5. 转换为Map
        Map<String, V> resultMap = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            
            // 类型转换
            V convertedValue = convertValue(value);
            resultMap.put(key, convertedValue);
        }
        
        return resultMap;
    }
    
    /**
     * 转换值的类型
     * 
     * @param value 原始值
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    private V convertValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // 如果值已经是目标类型，直接返回
        if (valueClass.isInstance(value)) {
            return (V) value;
        }
        
        // 字符串类型
        if (valueClass == String.class) {
            return (V) value.toString();
        }
        
        // Integer类型
        if (valueClass == Integer.class) {
            if (value instanceof Number) {
                return (V) Integer.valueOf(((Number) value).intValue());
            }
            return (V) Integer.valueOf(value.toString());
        }
        
        // Long类型
        if (valueClass == Long.class) {
            if (value instanceof Number) {
                return (V) Long.valueOf(((Number) value).longValue());
            }
            return (V) Long.valueOf(value.toString());
        }
        
        // Double类型
        if (valueClass == Double.class) {
            if (value instanceof Number) {
                return (V) Double.valueOf(((Number) value).doubleValue());
            }
            return (V) Double.valueOf(value.toString());
        }
        
        // Boolean类型
        if (valueClass == Boolean.class) {
            if (value instanceof Boolean) {
                return (V) value;
            }
            return (V) Boolean.valueOf(value.toString());
        }
        
        // 其他类型，尝试直接转换
        try {
            return (V) value;
        } catch (ClassCastException e) {
            throw new ConvertException("无法将值 " + value + " 转换为类型 " + valueClass.getName(), e);
        }
    }
    
    @Override
    protected Map<String, V> handleNull() {
        // null值返回空Map
        return new HashMap<>();
    }
    
    @Override
    public String getName() {
        return "JsonStringToMapConverter<String, " + valueClass.getSimpleName() + ">";
    }
    
    /**
     * 获取Map值的Class对象
     * 
     * @return Map值的Class对象
     */
    public Class<V> getValueClass() {
        return valueClass;
    }
}
