package com.fragment.excel.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 自定义日期转换器
 * 解决 EasyExcel 报错: Can not find 'converter' support class Date
 */
public class CustomDateConverter implements Converter<Date> {
    
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    
    @Override
    public Class<Date> supportJavaTypeKey() {
        return Date.class;
    }
    
    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }
    
    /**
     * 写入Excel时的转换逻辑
     */
    @Override
    public WriteCellData<?> convertToExcelData(Date value, ExcelContentProperty contentProperty,
                                                 GlobalConfiguration globalConfiguration) {
        if (value == null) {
            return new WriteCellData<>("");
        }
        
        String pattern = DEFAULT_PATTERN;
        
        // 如果有自定义格式，使用自定义格式
        if (contentProperty != null && contentProperty.getDateTimeFormatProperty() != null) {
            pattern = contentProperty.getDateTimeFormatProperty().getFormat();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        String formattedDate = sdf.format(value);
        return new WriteCellData<>(formattedDate);
    }
    
    /**
     * 读取Excel时的转换逻辑
     */
    @Override
    public Date convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                   GlobalConfiguration globalConfiguration) throws Exception {
        if (cellData == null || cellData.getStringValue() == null) {
            return null;
        }
        
        String pattern = DEFAULT_PATTERN;
        
        // 如果有自定义格式，使用自定义格式
        if (contentProperty != null && contentProperty.getDateTimeFormatProperty() != null) {
            pattern = contentProperty.getDateTimeFormatProperty().getFormat();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.parse(cellData.getStringValue());
    }
}
