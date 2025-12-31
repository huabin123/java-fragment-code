package com.fragment.convetor.core;

/**
 * 抽象转换器基类
 * 
 * <p>提供通用的转换器实现，子类只需实现具体的转换逻辑
 * 
 * @param <S> 源类型
 * @param <T> 目标类型
 * @author fragment
 */
public abstract class AbstractConverter<S, T> implements Converter<S, T> {
    
    private final Class<S> sourceType;
    private final Class<T> targetType;
    
    /**
     * 构造函数
     * 
     * @param sourceType 源类型
     * @param targetType 目标类型
     */
    protected AbstractConverter(Class<S> sourceType, Class<T> targetType) {
        this.sourceType = sourceType;
        this.targetType = targetType;
    }
    
    @Override
    public T convert(S source) throws ConvertException {
        if (source == null) {
            return handleNull();
        }
        
        try {
            return doConvert(source);
        } catch (Exception e) {
            throw new ConvertException("转换失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行具体的转换逻辑
     * 
     * @param source 源对象（非null）
     * @return 转换后的目标对象
     * @throws Exception 转换过程中的异常
     */
    protected abstract T doConvert(S source) throws Exception;
    
    /**
     * 处理null值
     * 
     * <p>默认返回null，子类可以重写此方法自定义null值处理逻辑
     * 
     * @return 处理后的值
     */
    protected T handleNull() {
        return null;
    }
    
    @Override
    public Class<S> getSourceType() {
        return sourceType;
    }
    
    @Override
    public Class<T> getTargetType() {
        return targetType;
    }
}
