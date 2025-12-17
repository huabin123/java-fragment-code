package com.fragment.zip;

import java.util.regex.Pattern;

/**
 * SQL语句校验器
 */
public class SqlValidator {
    
    /**
     * 删除语句的正则表达式（大小写不敏感）
     * 匹配: DELETE FROM, DROP TABLE, DROP DATABASE, TRUNCATE TABLE等
     */
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "\\b(DELETE|DROP|TRUNCATE)\\s+(FROM|TABLE|DATABASE|SCHEMA|INDEX|VIEW)",
            Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 更新语句的正则表达式（大小写不敏感）
     * 匹配: UPDATE ... SET
     */
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "\\bUPDATE\\s+.*\\s+SET\\s+",
            Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 校验SQL语句是否包含删除或更新操作
     * 
     * @param sql SQL语句
     * @return true-校验通过（不包含删除和更新），false-校验失败（包含删除或更新）
     */
    public static boolean validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return true;
        }
        
        // 检查是否包含删除语句
        if (DELETE_PATTERN.matcher(sql).find()) {
            return false;
        }
        
        // 检查是否包含更新语句
        if (UPDATE_PATTERN.matcher(sql).find()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 校验SQL语句，如果包含删除或更新操作则抛出异常
     * 
     * @param sql SQL语句
     * @throws IllegalArgumentException 如果SQL包含删除或更新操作
     */
    public static void validateOrThrow(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }
        
        // 检查是否包含删除语句
        if (DELETE_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("SQL语句中不允许包含删除操作（DELETE/DROP/TRUNCATE）");
        }
        
        // 检查是否包含更新语句
        if (UPDATE_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("SQL语句中不允许包含更新操作（UPDATE）");
        }
    }
    
    /**
     * 获取SQL语句中包含的非法操作类型
     * 
     * @param sql SQL语句
     * @return 非法操作类型描述，如果没有非法操作则返回null
     */
    public static String getViolationType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return null;
        }
        
        // 检查是否包含删除语句
        if (DELETE_PATTERN.matcher(sql).find()) {
            return "包含删除操作（DELETE/DROP/TRUNCATE）";
        }
        
        // 检查是否包含更新语句
        if (UPDATE_PATTERN.matcher(sql).find()) {
            return "包含更新操作（UPDATE）";
        }
        
        return null;
    }
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        // 测试用例
        String[] testSqls = {
            "SELECT * FROM users",
            "INSERT INTO users (name, age) VALUES ('张三', 20)",
            "UPDATE users SET age = 21 WHERE id = 1",
            "DELETE FROM users WHERE id = 1",
            "DROP TABLE users",
            "TRUNCATE TABLE users",
            "CREATE TABLE test (id INT)",
            "update users set name='李四' where id=2",
            "delete from users",
            "drop database test_db"
        };
        
        System.out.println("=== SQL语句校验测试 ===\n");
        
        for (String sql : testSqls) {
            boolean isValid = validate(sql);
            String violationType = getViolationType(sql);
            
            System.out.println("SQL: " + sql);
            System.out.println("校验结果: " + (isValid ? "✓ 通过" : "✗ 失败"));
            if (violationType != null) {
                System.out.println("失败原因: " + violationType);
            }
            System.out.println();
        }
        
        // 测试异常抛出
        System.out.println("=== 测试异常抛出 ===\n");
        try {
            validateOrThrow("UPDATE users SET age = 30");
        } catch (IllegalArgumentException e) {
            System.out.println("捕获异常: " + e.getMessage());
        }
    }
}
