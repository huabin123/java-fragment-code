package com.fragment.zip;

import java.util.List;

/**
 * 视图需求处理结果
 */
public class ViewRequestResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 表信息
     */
    private TableInfo tableInfo;
    
    /**
     * 条件规则列表
     */
    private List<ConditionRule> conditionRules;
    
    /**
     * DDL内容
     */
    private String ddlContent;
    
    /**
     * DML内容
     */
    private String dmlContent;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TableInfo getTableInfo() {
        return tableInfo;
    }

    public void setTableInfo(TableInfo tableInfo) {
        this.tableInfo = tableInfo;
    }

    public List<ConditionRule> getConditionRules() {
        return conditionRules;
    }

    public void setConditionRules(List<ConditionRule> conditionRules) {
        this.conditionRules = conditionRules;
    }

    public String getDdlContent() {
        return ddlContent;
    }

    public void setDdlContent(String ddlContent) {
        this.ddlContent = ddlContent;
    }

    public String getDmlContent() {
        return dmlContent;
    }

    public void setDmlContent(String dmlContent) {
        this.dmlContent = dmlContent;
    }

    @Override
    public String toString() {
        return "ViewRequestResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", tableInfo=" + tableInfo +
                ", conditionRules=" + conditionRules +
                ", ddlContent='" + ddlContent + '\'' +
                ", dmlContent='" + dmlContent + '\'' +
                '}';
    }
}
