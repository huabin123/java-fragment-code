# EasyExcel 日期转换器问题解决方案

## 问题描述

使用 EasyExcel 的 `ExcelWriterBuilder` 导出包含 `Date` 类型字段的数据时，报错：

```
Can not find 'converter' support class Date
```

## 问题原因

当使用 `EasyExcel.write()` 导出 `Map<String, Object>` 格式的数据时，如果 Map 中包含 `Date` 类型的值，EasyExcel 需要一个对应的类型转换器（Converter）来处理 `Date` 到 Excel 单元格的转换。

### 常见错误做法

```java
// ❌ 错误：手动将 Date 转换为 String，但没有注册转换器
if (value instanceof Date) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    value = sdf.format((Date) value);  // 转换为 String
}
rowData.put("日期字段", value);

// 导出时没有注册 Date 转换器
EasyExcel.write(fileName)
    .head(headers)
    .doWrite(exportData);  // ❌ 报错：Can not find 'converter' support class Date
```

## 解决方案

### 方案1：注册自定义日期转换器（推荐）

#### 步骤1：创建自定义日期转换器

```java
package com.fragment.excel.converter;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

import java.text.SimpleDateFormat;
import java.util.Date;

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
    
    @Override
    public WriteCellData<?> convertToExcelData(Date value, ExcelContentProperty contentProperty,
                                                 GlobalConfiguration globalConfiguration) {
        if (value == null) {
            return new WriteCellData<>("");
        }
        
        String pattern = DEFAULT_PATTERN;
        if (contentProperty != null && contentProperty.getDateTimeFormatProperty() != null) {
            pattern = contentProperty.getDateTimeFormatProperty().getFormat();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return new WriteCellData<>(sdf.format(value));
    }
    
    @Override
    public Date convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                   GlobalConfiguration globalConfiguration) throws Exception {
        if (cellData == null || cellData.getStringValue() == null) {
            return null;
        }
        
        String pattern = DEFAULT_PATTERN;
        if (contentProperty != null && contentProperty.getDateTimeFormatProperty() != null) {
            pattern = contentProperty.getDateTimeFormatProperty().getFormat();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.parse(cellData.getStringValue());
    }
}
```

#### 步骤2：注册转换器并导出

```java
import com.fragment.excel.converter.CustomDateConverter;

// ✅ 正确：保持 Date 类型，注册自定义转换器
Map<String, Object> rowData = new LinkedHashMap<>();
rowData.put("日期字段", new Date());  // 保持 Date 类型

// 导出时注册转换器
EasyExcel.write(fileName)
    .head(headers)
    .registerConverter(new CustomDateConverter())  // ✅ 注册转换器
    .doWrite(exportData);
```

### 方案2：使用 EasyExcel 内置转换器

```java
import com.alibaba.excel.converters.date.DateStringConverter;

// 注册 EasyExcel 内置的日期转换器
EasyExcel.write(fileName)
    .head(headers)
    .registerConverter(new DateStringConverter())
    .doWrite(exportData);
```

### 方案3：手动转换为字符串（不推荐）

如果确实要手动转换，需要确保所有 Date 都转换为 String：

```java
// 手动转换所有 Date 为 String
if (value instanceof Date) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    value = sdf.format((Date) value);  // 转换为 String
}
rowData.put("日期字段", value);  // 此时 value 是 String 类型

// 不需要注册 Date 转换器，因为已经是 String 了
EasyExcel.write(fileName)
    .head(headers)
    .doWrite(exportData);
```

**注意**：这种方案不推荐，因为：
- 需要手动处理所有 Date 字段
- 失去了 EasyExcel 的类型安全性
- 无法利用 `@DateTimeFormat` 注解

## 本项目的实现

本项目采用**方案3的改进版**：在数据转换阶段手动格式化日期。

### 为什么选择这个方案？

由于 `ExcelExportUtil` 使用 `Map<String, Object>` 格式导出数据，EasyExcel 无法获取原始字段的注解信息（如 `@ExcelColumn` 的 `dateFormat`）。因此，我们在数据转换阶段就完成日期格式化。

### 实现代码

```java
// ExcelExportUtil.java
private static <T> List<Map<String, Object>> convertToMapData(List<T> data, List<FieldInfo> fieldInfos) {
    List<Map<String, Object>> result = new ArrayList<>();
    
    for (T item : data) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        
        for (FieldInfo fieldInfo : fieldInfos) {
            fieldInfo.field.setAccessible(true);
            Object value = fieldInfo.field.get(item);
            
            // 处理日期格式化
            if (value instanceof Date) {
                Date dateValue = (Date) value;
                String dateFormat = fieldInfo.annotation.dateFormat();
                SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
                value = sdf.format(dateValue);  // 转换为字符串
            }
            
            rowData.put(fieldInfo.annotation.name(), value);
        }
        
        result.add(rowData);
    }
    
    return result;
}

// 导出时不需要注册转换器
EasyExcel.write(fileName)
    .head(headers)
    .sheet("数据导出")
    .registerWriteHandler(styleStrategy)
    .doWrite(exportData);  // ✅ 不会报错，因为Date已转为String
```

### 使用示例

```java
// 1. 定义数据模型
public class UserExportVO {
    @ExcelColumn(name = "用户名", order = 1)
    private String username;
    
    @ExcelColumn(name = "注册时间", order = 2, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date registerTime;
    
    // getters and setters
}

// 2. 导出数据
List<UserExportVO> users = getUserList();
ExcelExportUtil.exportExcel(users, UserExportVO.class, "users.xlsx");
```

## 关键要点

1. **在数据转换阶段格式化**：将 Date 转换为 String，避免 EasyExcel 找不到转换器
2. **使用注解配置格式**：通过 `@ExcelColumn` 的 `dateFormat` 属性指定格式
3. **默认格式**：如果没有指定格式，使用 `yyyy-MM-dd`（注解默认值）
4. **不需要注册转换器**：因为已经转换为 String 类型

## 相关文件

- `CustomDateConverter.java` - 自定义日期转换器实现
- `ExcelExportUtil.java` - 已集成转换器的导出工具类
- `README_EASYEXCEL_EXPORT.md` - EasyExcel 导出功能详细说明

## 参考资料

- [EasyExcel 官方文档 - 自定义转换器](https://easyexcel.opensource.alibaba.com/docs/current/quickstart/write#%E8%87%AA%E5%AE%9A%E4%B9%89%E8%BD%AC%E6%8D%A2%E5%99%A8)
- [EasyExcel GitHub Issues](https://github.com/alibaba/easyexcel/issues)
