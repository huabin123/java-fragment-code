package com.fragment.convetor.core;

/**
 * 转换器核心接口
 * 
 * <p>所有转换器都应该实现此接口
 * 
 * @param <S> 源类型
 * @param <T> 目标类型
 * @author fragment
 */
public interface Converter<S, T> {
    
    /**
     * 执行转换
     * 
     * @param source 源对象
     * @return 转换后的目标对象
     * @throws ConvertException 转换异常
     */
    T convert(S source) throws ConvertException;
    
    /**
     * 获取转换器名称
     * 
     * @return 转换器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 获取源类型
     * 
     * @return 源类型的Class对象
     */
    Class<S> getSourceType();
    
    /**
     * 获取目标类型
     * 
     * @return 目标类型的Class对象
     */
    Class<T> getTargetType();
}
