package com.fragment.excel.model;

import com.fragment.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品导出VO
 * 另一个演示自定义注解的示例
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductExportVO {
    
    @ExcelColumn(name = "商品编码", order = 1, width = 20)
    private String productCode;
    
    @ExcelColumn(name = "商品名称", order = 2, width = 30)
    private String productName;
    
    @ExcelColumn(name = "商品分类", order = 3, width = 15)
    private String category;
    
    @ExcelColumn(name = "品牌", order = 4, width = 15)
    private String brand;
    
    @ExcelColumn(name = "销售价格", order = 5, width = 15)
    private BigDecimal salePrice;
    
    @ExcelColumn(name = "成本价格", order = 6, width = 15)
    private BigDecimal costPrice;
    
    @ExcelColumn(name = "库存数量", order = 7, width = 12)
    private Integer stockQuantity;
    
    @ExcelColumn(name = "商品状态", order = 8, width = 12)
    private String status;
    
    @ExcelColumn(name = "创建时间", order = 9, dateFormat = "yyyy-MM-dd", width = 20)
    private Date createTime;
    
    @ExcelColumn(name = "更新时间", order = 10, dateFormat = "yyyy-MM-dd HH:mm", width = 25)
    private Date updateTime;
    
    // 供应商信息，演示开关控制
    @ExcelColumn(name = "供应商", order = 11, export = true, width = 20)
    private String supplier;
    
    // 内部成本信息，不导出
    @ExcelColumn(name = "内部成本", order = 99, export = false)
    private BigDecimal internalCost;
    
    // 备注信息，可以通过修改export值来控制是否导出
    @ExcelColumn(name = "备注信息", order = 12, export = false, width = 40)
    private String remarks;
}
