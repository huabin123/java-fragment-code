package com.fragment.zip;

/**
 * 条件规则实体类
 */
public class ConditionRule {
    /**
     * 字段名（中文）
     */
    private String colName;
    
    /**
     * 规则类型
     */
    private String ruleType;

    public ConditionRule() {
    }

    public ConditionRule(String colName, String ruleType) {
        this.colName = colName;
        this.ruleType = ruleType;
    }

    public String getColName() {
        return colName;
    }

    public void setColName(String colName) {
        this.colName = colName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    @Override
    public String toString() {
        return "{" +
                "\"colName\":\"" + colName + '\"' +
                ", \"ruleType\":\"" + ruleType + '\"' +
                '}';
    }
}
