package com.fragment.excel.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * sys_log.xlsx 数据模型
 */
@Data
public class SysLogData {
    
    @ExcelProperty("请求连接")
    private String requestUrl;
    
    @ExcelProperty("计数")
    private Integer count;
}
