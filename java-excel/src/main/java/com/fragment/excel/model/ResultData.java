package com.fragment.excel.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * result.xlsx 数据模型
 */
@Data
public class ResultData {
    
    @ExcelProperty("接口调用url（一个接口一行）")
    private String url;
    
    @ExcelProperty("调用次数")
    private Integer callCount;
}
