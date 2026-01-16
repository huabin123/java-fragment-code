package com.fragment.excel;

import com.fragment.excel.annotation.ExcelColumn;
import com.fragment.excel.util.ExcelExportUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 日期转换器测试
 * 验证修复 "Can not find 'converter' support class Date" 问题
 */
public class DateConverterTest {
    
    public static void main(String[] args) {
        System.out.println("=== 测试 EasyExcel 日期转换器 ===\n");
        
        // 创建测试数据
        List<TestData> dataList = createTestData();
        
        // 导出Excel
        String fileName = "java-excel/src/main/resources/date_converter_test.xlsx";
        
        try {
            ExcelExportUtil.exportExcel(dataList, TestData.class, fileName);
            System.out.println("\n✅ 测试成功！日期转换器工作正常。");
            System.out.println("导出文件：" + fileName);
        } catch (Exception e) {
            System.err.println("\n❌ 测试失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建测试数据
     */
    private static List<TestData> createTestData() {
        List<TestData> list = new ArrayList<>();
        
        long now = System.currentTimeMillis();
        
        for (int i = 1; i <= 5; i++) {
            TestData data = new TestData();
            data.setId(i);
            data.setName("测试用户" + i);
            data.setCreateTime(new Date(now - i * 86400000L)); // 每条数据相差1天
            data.setUpdateTime(new Date());
            list.add(data);
        }
        
        return list;
    }
    
    /**
     * 测试数据模型
     */
    public static class TestData {
        
        @ExcelColumn(name = "ID", order = 1, export = true)
        private Integer id;
        
        @ExcelColumn(name = "姓名", order = 2, export = true)
        private String name;
        
        @ExcelColumn(name = "创建时间", order = 3, export = true, dateFormat = "yyyy-MM-dd HH:mm:ss")
        private Date createTime;
        
        @ExcelColumn(name = "更新时间", order = 4, export = true, dateFormat = "yyyy/MM/dd HH:mm")
        private Date updateTime;
        
        // Getters and Setters
        public Integer getId() {
            return id;
        }
        
        public void setId(Integer id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Date getCreateTime() {
            return createTime;
        }
        
        public void setCreateTime(Date createTime) {
            this.createTime = createTime;
        }
        
        public Date getUpdateTime() {
            return updateTime;
        }
        
        public void setUpdateTime(Date updateTime) {
            this.updateTime = updateTime;
        }
    }
}
