package com.fragment.excel;

import com.fragment.excel.model.UserExportVO;
import com.fragment.excel.util.ExcelExportUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * EasyExcel导出演示
 * 演示如何使用自定义注解配置Excel导出
 */
public class EasyExcelExportDemo {
    
    public static void main(String[] args) {
        System.out.println("=== EasyExcel 导出演示 ===");
        
        // 1. 准备测试数据
        List<UserExportVO> users = createTestData();
        
        // 2. 导出Excel
        String fileName = "用户数据导出_" + System.currentTimeMillis() + ".xlsx";
        ExcelExportUtil.exportExcel(users, UserExportVO.class, fileName);
        
        // 3. 输出导出信息
        System.out.println("导出完成！");
        System.out.println("文件名：" + fileName);
        System.out.println("数据条数：" + users.size());
        
        // 4. 演示注解配置效果
        demonstrateAnnotationFeatures();
    }
    
    /**
     * 创建测试数据
     */
    private static List<UserExportVO> createTestData() {
        List<UserExportVO> users = new ArrayList<>();
        
        // 创建10条测试数据
        for (int i = 1; i <= 10; i++) {
            UserExportVO user = new UserExportVO();
            user.setId((long) i);
            user.setUsername("user" + String.format("%03d", i));
            user.setRealName("用户" + i);
            user.setEmail("user" + i + "@example.com");
            user.setPhone("138" + String.format("%08d", i));
            user.setAge(20 + (i % 30));
            user.setBalance(new BigDecimal(String.valueOf(1000 + i * 100)));
            
            // 注册时间：过去30天内的随机时间
            long registerTime = System.currentTimeMillis() - (long) (Math.random() * 30 * 24 * 60 * 60 * 1000);
            user.setRegisterTime(new Date(registerTime));
            
            // 最后登录时间：过去7天内的随机时间
            long lastLoginTime = System.currentTimeMillis() - (long) (Math.random() * 7 * 24 * 60 * 60 * 1000);
            user.setLastLoginTime(new Date(lastLoginTime));
            
            user.setStatus(i % 3 == 0 ? "禁用" : "正常");
            user.setPassword("password" + i); // 这个字段不会导出
            user.setInternalNote("内部备注" + i); // 这个字段也不会导出
            
            users.add(user);
        }
        
        return users;
    }
    
    /**
     * 演示注解配置的各种特性
     */
    private static void demonstrateAnnotationFeatures() {
        System.out.println("\n=== 注解配置特性演示 ===");
        
        System.out.println("1. 列名配置：");
        System.out.println("   - @ExcelColumn(name = \"用户ID\") -> 显示为中文列名");
        System.out.println("   - @ExcelColumn(name = \"用户名\") -> 显示为中文列名");
        
        System.out.println("\n2. 排序配置：");
        System.out.println("   - order = 1: 用户ID（最前面）");
        System.out.println("   - order = 2: 用户名");
        System.out.println("   - order = 3: 真实姓名");
        System.out.println("   - ... 按order值升序排列");
        
        System.out.println("\n3. 导出开关：");
        System.out.println("   - export = true: 正常导出的字段");
        System.out.println("   - export = false: password、internalNote字段不会导出");
        
        System.out.println("\n4. 日期格式化：");
        System.out.println("   - registerTime: dateFormat = \"yyyy-MM-dd\"");
        System.out.println("   - lastLoginTime: dateFormat = \"yyyy-MM-dd HH:mm:ss\"");
        
        System.out.println("\n5. 列宽配置：");
        System.out.println("   - 邮箱地址: width = 30（较宽）");
        System.out.println("   - 年龄: width = 10（较窄）");
        System.out.println("   - 其他字段根据内容设置合适宽度");
        
        System.out.println("\n6. 样式配置：");
        System.out.println("   - 表头：灰色背景，宋体12号粗体，居中对齐");
        System.out.println("   - 内容：宋体11号，左对齐");
    }
}
