package com.fragment.excel;

import com.fragment.excel.model.ProductExportVO;
import com.fragment.excel.model.UserExportVO;
import com.fragment.excel.util.ExcelExportUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 高级Excel导出演示
 * 演示多种数据类型的导出和注解配置的灵活性
 */
public class AdvancedExcelExportDemo {
    
    public static void main(String[] args) {
        System.out.println("=== 高级 Excel 导出演示 ===");
        
        // 1. 导出用户数据
        exportUserData();
        
        // 2. 导出商品数据
        exportProductData();
        
        // 3. 演示注解开关控制
        demonstrateSwitchControl();
    }
    
    /**
     * 导出用户数据
     */
    private static void exportUserData() {
        System.out.println("\n1. 导出用户数据...");
        
        List<UserExportVO> users = new ArrayList<>();
        
        // 创建多样化的测试数据
        users.add(new UserExportVO(1L, "admin", "管理员", "admin@company.com", "13800138001", 
                35, new BigDecimal("50000.00"), 
                new Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000), // 一年前注册
                new Date(), "正常", "admin123", "VIP用户"));
        
        users.add(new UserExportVO(2L, "zhangsan", "张三", "zhangsan@example.com", "13800138002", 
                28, new BigDecimal("8500.50"), 
                new Date(System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000), // 半年前注册
                new Date(System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000), // 2天前登录
                "正常", "pass123", "普通用户"));
        
        users.add(new UserExportVO(3L, "lisi", "李四", "lisi@example.com", "13800138003", 
                32, new BigDecimal("12300.75"), 
                new Date(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000), // 3个月前注册
                new Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000), // 一周前登录
                "禁用", "pass456", "问题用户"));
        
        String fileName = "用户数据详细导出_" + System.currentTimeMillis() + ".xlsx";
        ExcelExportUtil.exportExcel(users, UserExportVO.class, fileName);
        
        System.out.println("用户数据导出完成：" + fileName);
    }
    
    /**
     * 导出商品数据
     */
    private static void exportProductData() {
        System.out.println("\n2. 导出商品数据...");
        
        List<ProductExportVO> products = createProductData();
        
        String fileName = "商品数据导出_" + System.currentTimeMillis() + ".xlsx";
        ExcelExportUtil.exportExcel(products, ProductExportVO.class, fileName);
        
        System.out.println("商品数据导出完成：" + fileName);
    }
    
    /**
     * 创建商品测试数据
     */
    private static List<ProductExportVO> createProductData() {
        List<ProductExportVO> products = new ArrayList<>();
        
        // 电子产品
        products.add(new ProductExportVO("P001", "iPhone 14 Pro", "手机", "Apple", 
                new BigDecimal("7999.00"), new BigDecimal("6500.00"), 50, "在售",
                new Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000),
                new Date(), "苹果官方", new BigDecimal("6000.00"), "热销商品"));
        
        products.add(new ProductExportVO("P002", "MacBook Pro 14", "笔记本", "Apple", 
                new BigDecimal("15999.00"), new BigDecimal("13000.00"), 20, "在售",
                new Date(System.currentTimeMillis() - 45L * 24 * 60 * 60 * 1000),
                new Date(), "苹果官方", new BigDecimal("12500.00"), "专业设备"));
        
        // 服装
        products.add(new ProductExportVO("P003", "经典牛仔裤", "服装", "Levi's", 
                new BigDecimal("599.00"), new BigDecimal("300.00"), 100, "在售",
                new Date(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000),
                new Date(), "李维斯中国", new BigDecimal("280.00"), "经典款式"));
        
        // 图书
        products.add(new ProductExportVO("P004", "Java编程思想", "图书", "机械工业出版社", 
                new BigDecimal("108.00"), new BigDecimal("65.00"), 200, "在售",
                new Date(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000),
                new Date(), "机械工业出版社", new BigDecimal("60.00"), "技术图书"));
        
        // 停售商品
        products.add(new ProductExportVO("P005", "旧款手机壳", "配件", "Generic", 
                new BigDecimal("29.90"), new BigDecimal("15.00"), 0, "停售",
                new Date(System.currentTimeMillis() - 120L * 24 * 60 * 60 * 1000),
                new Date(), "通用供应商", new BigDecimal("12.00"), "已停产"));
        
        return products;
    }
    
    /**
     * 演示注解开关控制
     */
    private static void demonstrateSwitchControl() {
        System.out.println("\n3. 演示注解开关控制...");
        
        System.out.println("当前配置下，以下字段不会导出：");
        System.out.println("UserExportVO:");
        System.out.println("  - password (export = false)");
        System.out.println("  - internalNote (export = false)");
        
        System.out.println("ProductExportVO:");
        System.out.println("  - internalCost (export = false)");
        System.out.println("  - remarks (export = false)");
        
        System.out.println("\n如需导出这些字段，请修改对应注解的 export 属性为 true");
        
        System.out.println("\n日期格式化演示：");
        System.out.println("  - registerTime: yyyy-MM-dd (只显示日期)");
        System.out.println("  - lastLoginTime: yyyy-MM-dd HH:mm:ss (显示完整时间)");
        System.out.println("  - createTime: yyyy-MM-dd (只显示日期)");
        System.out.println("  - updateTime: yyyy-MM-dd HH:mm (显示到分钟)");
        
        System.out.println("\n列宽配置演示：");
        System.out.println("  - 商品名称: width = 30 (较宽，适合长文本)");
        System.out.println("  - 年龄: width = 10 (较窄，适合数字)");
        System.out.println("  - 邮箱地址: width = 30 (较宽，适合邮箱格式)");
    }
}
