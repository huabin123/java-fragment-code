package com.fragment.zip;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ZipUtil;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DDL解析器
 */
public class DdlParser {
    
    /**
     * 解析zip文件中的DDL.txt
     * 
     * @param zipFilePath zip文件路径
     * @return 表信息
     */
    public static TableInfo parseDdlFromZip(String zipFilePath) {
        // 使用hutool解压zip文件到临时目录
        File zipFile = new File(zipFilePath);
        File unzipDir = ZipUtil.unzip(zipFile);
        
        // 读取DDL.txt文件内容
        File ddlFile = new File(unzipDir, "DDL.txt");
        String ddlContent = FileUtil.readUtf8String(ddlFile);
        
        // 解析DDL内容
        return parseDdl(ddlContent);
    }
    
    /**
     * 解析DDL语句
     * 
     * @param ddlContent DDL内容
     * @return 表信息
     * @throws IllegalArgumentException 当字段缺少comment或comment重复时抛出异常
     */
    public static TableInfo parseDdl(String ddlContent) {
        TableInfo tableInfo = new TableInfo();
        
        // 1. 解析schema和表名
        // 匹配: create table `dp_view`.`test_001`
        Pattern tablePattern = Pattern.compile("create\\s+table\\s+`([^`]+)`\\.`([^`]+)`", Pattern.CASE_INSENSITIVE);
        Matcher tableMatcher = tablePattern.matcher(ddlContent);
        if (tableMatcher.find()) {
            tableInfo.setSchema(tableMatcher.group(1));
            tableInfo.setTableName(tableMatcher.group(2));
        }
        
        // 2. 解析中文表名(comment)
        // 匹配: comment='测试001'
        Pattern commentPattern = Pattern.compile("comment\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
        Matcher commentMatcher = commentPattern.matcher(ddlContent);
        if (commentMatcher.find()) {
            tableInfo.setTableComment(commentMatcher.group(1));
        }
        
        // 3. 解析字段信息
        // 先提取所有字段定义（包括有comment和无comment的）
        List<String> allFields = new ArrayList<>();
        Pattern allFieldPattern = Pattern.compile("\"([^\"]+)\"\\s+\\w+", Pattern.CASE_INSENSITIVE);
        Matcher allFieldMatcher = allFieldPattern.matcher(ddlContent);
        while (allFieldMatcher.find()) {
            String fieldName = allFieldMatcher.group(1);
            // 过滤掉主键约束等非字段定义
            if (!fieldName.toLowerCase().startsWith("primary") && 
                !fieldName.toLowerCase().startsWith("key") &&
                !fieldName.toLowerCase().startsWith("index") &&
                !fieldName.toLowerCase().startsWith("unique") &&
                !fieldName.toLowerCase().startsWith("foreign")) {
                allFields.add(fieldName);
            }
        }
        
        // 匹配: "col_a" varchar(40) not null comment '字段a'
        Map<String, String> fieldMap = new HashMap<>();
        Set<String> commentSet = new HashSet<>();
        List<String> fieldsWithComment = new ArrayList<>();
        
        Pattern fieldPattern = Pattern.compile("\"([^\"]+)\".*?comment\\s+['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
        Matcher fieldMatcher = fieldPattern.matcher(ddlContent);
        while (fieldMatcher.find()) {
            String englishFieldName = fieldMatcher.group(1);
            String chineseFieldName = fieldMatcher.group(2);
            
            fieldsWithComment.add(englishFieldName);
            
            // 检查comment是否重复
            if (commentSet.contains(chineseFieldName)) {
                throw new IllegalArgumentException("字段comment重复: " + chineseFieldName);
            }
            commentSet.add(chineseFieldName);
            
            // map的key是中文字段名，value是英文字段名
            fieldMap.put(chineseFieldName, englishFieldName);
        }
        
        // 检查是否所有字段都有comment
        List<String> fieldsWithoutComment = new ArrayList<>();
        for (String field : allFields) {
            if (!fieldsWithComment.contains(field)) {
                fieldsWithoutComment.add(field);
            }
        }
        
        if (!fieldsWithoutComment.isEmpty()) {
            throw new IllegalArgumentException("以下字段缺少comment: " + String.join(", ", fieldsWithoutComment));
        }
        
        tableInfo.setFieldMap(fieldMap);
        
        return tableInfo;
    }
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        String zipFilePath = "java-file/src/main/resources/视图需求001.zip";
        
        try {
            TableInfo tableInfo = parseDdlFromZip(zipFilePath);
            
            System.out.println("Schema: " + tableInfo.getSchema());
            System.out.println("英文表名: " + tableInfo.getTableName());
            System.out.println("中文表名: " + tableInfo.getTableComment());
            System.out.println("字段映射: " + tableInfo.getFieldMap());
            System.out.println("\n完整信息:");
            System.out.println(tableInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
