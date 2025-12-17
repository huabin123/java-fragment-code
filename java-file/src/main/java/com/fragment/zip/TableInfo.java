package com.fragment.zip;

import java.util.Map;

/**
 * 表信息实体类
 */
public class TableInfo {
    /**
     * schema信息
     */
    private String schema;
    
    /**
     * 英文表名
     */
    private String tableName;
    
    /**
     * 中文表名
     */
    private String tableComment;
    
    /**
     * 字段映射 (中文字段名 -> 英文字段名)
     */
    private Map<String, String> fieldMap;

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableComment() {
        return tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public Map<String, String> getFieldMap() {
        return fieldMap;
    }

    public void setFieldMap(Map<String, String> fieldMap) {
        this.fieldMap = fieldMap;
    }

    @Override
    public String toString() {
        return "TableInfo{" +
                "schema='" + schema + '\'' +
                ", tableName='" + tableName + '\'' +
                ", tableComment='" + tableComment + '\'' +
                ", fieldMap=" + fieldMap +
                '}';
    }
}
