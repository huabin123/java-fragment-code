package com.fragment.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excel列配置注解
 * 用于配置导出Excel时的列属性
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {
    
    /**
     * 列的中文名称
     */
    String name();
    
    /**
     * 列的排序顺序，数字越小越靠前
     */
    int order() default 0;
    
    /**
     * 是否导出该列，true-导出，false-不导出
     */
    boolean export() default true;
    
    /**
     * 日期格式化模式，仅对Date类型字段有效
     */
    String dateFormat() default "yyyy-MM-dd";
    
    /**
     * 列宽度
     */
    int width() default 20;
}
