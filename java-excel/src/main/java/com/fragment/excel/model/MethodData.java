package com.fragment.excel.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * method.csv 数据模型
 */
@Data
public class MethodData {

    @ExcelProperty("name")
    private String tranCode;

    @ExcelProperty("count")
    private String count;
}
