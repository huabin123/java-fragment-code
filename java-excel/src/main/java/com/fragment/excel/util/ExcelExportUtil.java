package com.fragment.excel.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.fragment.excel.annotation.ExcelColumn;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel导出工具类
 * 基于自定义注解和EasyExcel实现
 */
public class ExcelExportUtil {
    
    /**
     * 导出Excel文件
     * 
     * @param data 要导出的数据列表
     * @param clazz 数据对象的Class
     * @param fileName 导出文件名
     * @param <T> 数据类型
     */
    public static <T> void exportExcel(List<T> data, Class<T> clazz, String fileName) {
        try {
            // 1. 获取需要导出的字段信息
            List<FieldInfo> fieldInfos = getExportFields(clazz);
            
            // 2. 转换数据为Map格式，便于EasyExcel处理
            List<Map<String, Object>> exportData = convertToMapData(data, fieldInfos);
            
            // 3. 创建表头
            List<List<String>> headers = createHeaders(fieldInfos);
            
            // 4. 设置样式
            HorizontalCellStyleStrategy styleStrategy = createCellStyle();
            
            // 5. 导出Excel
            EasyExcel.write(fileName)
                    .head(headers)
                    .sheet("数据导出")
                    .registerWriteHandler(styleStrategy)
                    .doWrite(exportData);
            
            System.out.println("Excel导出成功：" + fileName);
            
        } catch (Exception e) {
            System.err.println("Excel导出失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取需要导出的字段信息
     */
    private static <T> List<FieldInfo> getExportFields(Class<T> clazz) {
        List<FieldInfo> fieldInfos = new ArrayList<>();
        
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation != null && annotation.export()) {
                fieldInfos.add(new FieldInfo(field, annotation));
            }
        }
        
        // 按order排序
        fieldInfos.sort(Comparator.comparingInt(f -> f.annotation.order()));
        
        return fieldInfos;
    }
    
    /**
     * 转换数据为Map格式
     */
    private static <T> List<Map<String, Object>> convertToMapData(List<T> data, List<FieldInfo> fieldInfos) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (T item : data) {
            Map<String, Object> rowData = new LinkedHashMap<>();
            
            for (FieldInfo fieldInfo : fieldInfos) {
                try {
                    fieldInfo.field.setAccessible(true);
                    Object value = fieldInfo.field.get(item);
                    
                    // 处理日期格式化
                    if (value instanceof Date) {
                        Date dateValue = (Date) value;
                        SimpleDateFormat sdf = new SimpleDateFormat(fieldInfo.annotation.dateFormat());
                        value = sdf.format(dateValue);
                    }
                    
                    rowData.put(fieldInfo.annotation.name(), value);
                    
                } catch (IllegalAccessException e) {
                    System.err.println("获取字段值失败：" + fieldInfo.field.getName());
                    rowData.put(fieldInfo.annotation.name(), "");
                }
            }
            
            result.add(rowData);
        }
        
        return result;
    }
    
    /**
     * 创建表头
     */
    private static List<List<String>> createHeaders(List<FieldInfo> fieldInfos) {
        return fieldInfos.stream()
                .map(fieldInfo -> Collections.singletonList(fieldInfo.annotation.name()))
                .collect(Collectors.toList());
    }
    
    /**
     * 创建单元格样式
     */
    private static HorizontalCellStyleStrategy createCellStyle() {
        // 头部样式
        WriteCellStyle headWriteCellStyle = new WriteCellStyle();
        headWriteCellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        WriteFont headWriteFont = new WriteFont();
        headWriteFont.setFontName("宋体");
        headWriteFont.setFontHeightInPoints((short) 12);
        headWriteFont.setBold(true);
        headWriteCellStyle.setWriteFont(headWriteFont);
        headWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        
        // 内容样式
        WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
        WriteFont contentWriteFont = new WriteFont();
        contentWriteFont.setFontName("宋体");
        contentWriteFont.setFontHeightInPoints((short) 11);
        contentWriteCellStyle.setWriteFont(contentWriteFont);
        contentWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        
        return new HorizontalCellStyleStrategy(headWriteCellStyle, contentWriteCellStyle);
    }
    
    /**
     * 字段信息内部类
     */
    private static class FieldInfo {
        Field field;
        ExcelColumn annotation;
        
        FieldInfo(Field field, ExcelColumn annotation) {
            this.field = field;
            this.annotation = annotation;
        }
    }
}
