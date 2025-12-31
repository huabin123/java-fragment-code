package com.fragment.convetor.core;

import com.fragment.convetor.json.BeanToJsonConverter;
import com.fragment.convetor.json.JsonArrayToListConverter;
import com.fragment.convetor.json.JsonObjectToBeanConverter;
import com.fragment.convetor.json.JsonStringToMapConverter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 转换器工厂
 * 
 * <p>提供转换器的创建和管理功能，支持转换器复用
 * 
 * <p>使用示例：
 * <pre>
 * // 获取JSON数组转List转换器
 * JsonArrayToListConverter<User> converter = ConverterFactory.getJsonArrayToListConverter(User.class);
 * List<User> users = converter.convert(jsonArray);
 * 
 * // 快捷转换
 * List<User> users = ConverterFactory.jsonArrayToList(jsonArray, User.class);
 * </pre>
 * 
 * @author fragment
 */
public class ConverterFactory {
    
    /**
     * 转换器缓存
     * key: 转换器类型 + Bean类型
     * value: 转换器实例
     */
    private static final Map<String, Converter<?, ?>> CONVERTER_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 获取JSON数组转List转换器
     * 
     * @param beanClass Bean的Class对象
     * @param <T> Bean类型
     * @return JSON数组转List转换器
     */
    @SuppressWarnings("unchecked")
    public static <T> JsonArrayToListConverter<T> getJsonArrayToListConverter(Class<T> beanClass) {
        String key = "JsonArrayToList_" + beanClass.getName();
        return (JsonArrayToListConverter<T>) CONVERTER_CACHE.computeIfAbsent(key, 
            k -> new JsonArrayToListConverter<>(beanClass));
    }
    
    /**
     * 获取JSON对象转Bean转换器
     * 
     * @param beanClass Bean的Class对象
     * @param <T> Bean类型
     * @return JSON对象转Bean转换器
     */
    @SuppressWarnings("unchecked")
    public static <T> JsonObjectToBeanConverter<T> getJsonObjectToBeanConverter(Class<T> beanClass) {
        String key = "JsonObjectToBean_" + beanClass.getName();
        return (JsonObjectToBeanConverter<T>) CONVERTER_CACHE.computeIfAbsent(key, 
            k -> new JsonObjectToBeanConverter<>(beanClass));
    }
    
    /**
     * 获取Bean转JSON转换器
     * 
     * @param beanClass Bean的Class对象
     * @param <T> Bean类型
     * @return Bean转JSON转换器
     */
    @SuppressWarnings("unchecked")
    public static <T> BeanToJsonConverter<T> getBeanToJsonConverter(Class<T> beanClass) {
        String key = "BeanToJson_" + beanClass.getName();
        return (BeanToJsonConverter<T>) CONVERTER_CACHE.computeIfAbsent(key, 
            k -> new BeanToJsonConverter<>(beanClass));
    }
    
    /**
     * 获取Bean转JSON转换器（格式化输出）
     * 
     * @param beanClass Bean的Class对象
     * @param <T> Bean类型
     * @return Bean转JSON转换器
     */
    @SuppressWarnings("unchecked")
    public static <T> BeanToJsonConverter<T> getBeanToJsonConverter(Class<T> beanClass, boolean prettyPrint) {
        String key = "BeanToJson_" + beanClass.getName() + "_" + prettyPrint;
        return (BeanToJsonConverter<T>) CONVERTER_CACHE.computeIfAbsent(key, 
            k -> new BeanToJsonConverter<>(beanClass, prettyPrint));
    }
    
    /**
     * 获取JSON字符串转Map转换器
     * 
     * @param valueClass Map值的Class对象
     * @param <V> Map值的类型
     * @return JSON字符串转Map转换器
     */
    @SuppressWarnings("unchecked")
    public static <V> JsonStringToMapConverter<V> getJsonStringToMapConverter(Class<V> valueClass) {
        String key = "JsonStringToMap_" + valueClass.getName();
        return (JsonStringToMapConverter<V>) CONVERTER_CACHE.computeIfAbsent(key, 
            k -> new JsonStringToMapConverter<>(valueClass));
    }
    
    // ==================== 快捷转换方法 ====================
    
    /**
     * JSON数组字符串转List（快捷方法）
     * 
     * @param jsonArray JSON数组字符串
     * @param beanClass Bean的Class对象
     * @param <T> Bean类型
     * @return List<Bean>
     */
    public static <T> java.util.List<T> jsonArrayToList(String jsonArray, Class<T> beanClass) {
        return getJsonArrayToListConverter(beanClass).convert(jsonArray);
    }
    
    /**
     * JSON对象字符串转Bean（快捷方法）
     * 
     * @param jsonObject JSON对象字符串
     * @param beanClass Bean的Class对象
     * @param <T> Bean类型
     * @return Bean对象
     */
    public static <T> T jsonObjectToBean(String jsonObject, Class<T> beanClass) {
        return getJsonObjectToBeanConverter(beanClass).convert(jsonObject);
    }
    
    /**
     * Bean转JSON字符串（快捷方法）
     * 
     * @param bean Bean对象
     * @param <T> Bean类型
     * @return JSON字符串
     */
    public static <T> String beanToJson(T bean) {
        if (bean == null) {
            return "null";
        }
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) bean.getClass();
        return getBeanToJsonConverter(beanClass).convert(bean);
    }
    
    /**
     * Bean转JSON字符串（快捷方法，格式化输出）
     * 
     * @param bean Bean对象
     * @param prettyPrint 是否格式化输出
     * @param <T> Bean类型
     * @return JSON字符串
     */
    public static <T> String beanToJson(T bean, boolean prettyPrint) {
        if (bean == null) {
            return "null";
        }
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) bean.getClass();
        return getBeanToJsonConverter(beanClass, prettyPrint).convert(bean);
    }
    
    /**
     * JSON字符串转Map（快捷方法）
     * 
     * <p>示例：
     * <pre>
     * // 转换为Map<String, String>
     * String json = "{\"小明\":\"3\",\"小红\":\"4\"}";
     * Map<String, String> map = ConverterFactory.jsonStringToMap(json, String.class);
     * 
     * // 转换为Map<String, Integer>
     * String json2 = "{\"小明\":3,\"小红\":4}";
     * Map<String, Integer> map2 = ConverterFactory.jsonStringToMap(json2, Integer.class);
     * </pre>
     * 
     * @param jsonString JSON字符串
     * @param valueClass Map值的Class对象
     * @param <V> Map值的类型
     * @return Map<String, V>
     */
    public static <V> Map<String, V> jsonStringToMap(String jsonString, Class<V> valueClass) {
        return getJsonStringToMapConverter(valueClass).convert(jsonString);
    }
    
    /**
     * 清除转换器缓存
     */
    public static void clearCache() {
        CONVERTER_CACHE.clear();
    }
    
    /**
     * 获取缓存的转换器数量
     * 
     * @return 缓存的转换器数量
     */
    public static int getCacheSize() {
        return CONVERTER_CACHE.size();
    }
}
