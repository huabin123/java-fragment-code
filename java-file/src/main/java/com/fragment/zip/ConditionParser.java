package com.fragment.zip;

import cn.hutool.core.io.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 条件文件解析器
 */
public class ConditionParser {
    
    /**
     * 将中文标点符号转换为英文标点符号
     * 
     * @param content 原始内容
     * @return 转换后的内容
     */
    private static String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        // 将中文逗号转换为英文逗号
        content = content.replace("，", ",");
        // 将中文冒号转换为英文冒号
        content = content.replace("：", ":");
        return content;
    }
    
    /**
     * 解析condition.txt文件
     * 格式: 字段a:1,字段b:2
     * 
     * @param conditionFilePath 条件文件路径
     * @return 条件规则列表
     */
    public static List<ConditionRule> parseConditionFile(String conditionFilePath) {
        File conditionFile = new File(conditionFilePath);
        String content = FileUtil.readUtf8String(conditionFile);
        return parseConditionContent(content);
    }
    
    /**
     * 解析条件内容
     * 格式: 字段a:1,字段b:2 或 字段a：1，字段b：2（兼容中文标点）
     * 
     * @param content 条件内容
     * @return 条件规则列表
     */
    public static List<ConditionRule> parseConditionContent(String content) {
        List<ConditionRule> rules = new ArrayList<>();
        
        if (content == null || content.trim().isEmpty()) {
            return rules;
        }
        
        // 去除空白字符
        content = content.trim();
        
        // 将中文标点转换为英文标点
        content = normalizeContent(content);
        
        // 按逗号分割
        String[] pairs = content.split(",");
        
        for (String pair : pairs) {
            pair = pair.trim();
            if (pair.isEmpty()) {
                continue;
            }
            
            // 按冒号分割字段名和规则类型
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                String colName = parts[0].trim();
                String ruleType = parts[1].trim();
                rules.add(new ConditionRule(colName, ruleType));
            }
        }
        
        return rules;
    }
    
    /**
     * 解析条件文件并验证字段是否存在于DDL中
     * 
     * @param conditionFilePath 条件文件路径
     * @param tableInfo 表信息（从DDL解析得到）
     * @return 条件规则列表
     * @throws IllegalArgumentException 如果字段不存在于DDL中
     */
    public static List<ConditionRule> parseAndValidate(String conditionFilePath, TableInfo tableInfo) {
        List<ConditionRule> rules = parseConditionFile(conditionFilePath);
        validateFields(rules, tableInfo);
        return rules;
    }
    
    /**
     * 验证条件规则中的字段是否都存在于DDL中
     * 
     * @param rules 条件规则列表
     * @param tableInfo 表信息
     * @throws IllegalArgumentException 如果字段不存在于DDL中
     */
    public static void validateFields(List<ConditionRule> rules, TableInfo tableInfo) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        
        if (tableInfo == null || tableInfo.getFieldMap() == null) {
            throw new IllegalArgumentException("表信息或字段映射为空");
        }
        
        Map<String, String> fieldMap = tableInfo.getFieldMap();
        List<String> invalidFields = new ArrayList<>();
        
        for (ConditionRule rule : rules) {
            String colName = rule.getColName();
            // 检查字段名是否存在于DDL的字段映射中
            if (!fieldMap.containsKey(colName)) {
                invalidFields.add(colName);
            }
        }
        
        if (!invalidFields.isEmpty()) {
            throw new IllegalArgumentException("以下字段不存在于DDL中: " + invalidFields);
        }
    }
    
    /**
     * 将条件规则列表转换为JSON格式字符串
     * 
     * @param rules 条件规则列表
     * @return JSON格式字符串
     */
    public static String toJsonString(List<ConditionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(rules.get(i).toString());
        }
        sb.append("]");
        
        return sb.toString();
    }
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        try {
            // 1. 解析DDL
            String zipFilePath = "java-file/src/main/resources/视图需求001.zip";
            TableInfo tableInfo = DdlParser.parseDdlFromZip(zipFilePath);
            
            System.out.println("=== DDL解析结果 ===");
            System.out.println("Schema: " + tableInfo.getSchema());
            System.out.println("表名: " + tableInfo.getTableName());
            System.out.println("字段映射: " + tableInfo.getFieldMap());
            System.out.println();
            
            // 2. 解析condition.txt
            String conditionFilePath = "java-file/src/main/resources/视图需求001/condition.txt";
            List<ConditionRule> rules = parseConditionFile(conditionFilePath);
            
            System.out.println("=== Condition解析结果 ===");
            System.out.println("条件规则数量: " + rules.size());
            for (ConditionRule rule : rules) {
                System.out.println(rule);
            }
            System.out.println();
            
            // 3. 转换为JSON格式
            String jsonString = toJsonString(rules);
            System.out.println("=== JSON格式 ===");
            System.out.println(jsonString);
            System.out.println();
            
            // 4. 验证字段
            System.out.println("=== 字段验证 ===");
            try {
                validateFields(rules, tableInfo);
                System.out.println("✓ 所有字段验证通过");
            } catch (IllegalArgumentException e) {
                System.out.println("✗ 字段验证失败: " + e.getMessage());
            }
            System.out.println();
            
            // 5. 测试不存在的字段
            System.out.println("=== 测试不存在的字段 ===");
            List<ConditionRule> invalidRules = new ArrayList<>();
            invalidRules.add(new ConditionRule("字段a", "1"));
            invalidRules.add(new ConditionRule("不存在的字段", "3"));
            
            try {
                validateFields(invalidRules, tableInfo);
                System.out.println("✓ 验证通过");
            } catch (IllegalArgumentException e) {
                System.out.println("✗ 验证失败: " + e.getMessage());
            }
            System.out.println();
            
            // 6. 测试中文标点符号
            System.out.println("=== 测试中文标点符号 ===");
            String chineseContent1 = "字段a：1，字段b：2";  // 中文冒号和逗号
            String chineseContent2 = "字段a:1，字段b:2";   // 混合
            String chineseContent3 = "字段a：1,字段b：2";   // 混合
            
            List<ConditionRule> rules1 = parseConditionContent(chineseContent1);
            List<ConditionRule> rules2 = parseConditionContent(chineseContent2);
            List<ConditionRule> rules3 = parseConditionContent(chineseContent3);
            
            System.out.println("中文标点: " + chineseContent1);
            System.out.println("解析结果: " + toJsonString(rules1));
            System.out.println();
            
            System.out.println("混合标点1: " + chineseContent2);
            System.out.println("解析结果: " + toJsonString(rules2));
            System.out.println();
            
            System.out.println("混合标点2: " + chineseContent3);
            System.out.println("解析结果: " + toJsonString(rules3));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
