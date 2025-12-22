package com.fragment.excel.model;

import com.fragment.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户导出VO
 * 演示如何使用自定义注解配置Excel导出
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserExportVO {
    
    @ExcelColumn(name = "用户ID", order = 1, width = 15)
    private Long id;
    
    @ExcelColumn(name = "用户名", order = 2, width = 20)
    private String username;
    
    @ExcelColumn(name = "真实姓名", order = 3, width = 15)
    private String realName;
    
    @ExcelColumn(name = "邮箱地址", order = 4, width = 30)
    private String email;
    
    @ExcelColumn(name = "手机号码", order = 5, width = 20)
    private String phone;
    
    @ExcelColumn(name = "年龄", order = 6, width = 10)
    private Integer age;
    
    @ExcelColumn(name = "账户余额", order = 7, width = 15)
    private BigDecimal balance;
    
    @ExcelColumn(name = "注册时间", order = 8, dateFormat = "yyyy-MM-dd", width = 20)
    private Date registerTime;
    
    @ExcelColumn(name = "最后登录时间", order = 9, dateFormat = "yyyy-MM-dd HH:mm:ss", width = 25)
    private Date lastLoginTime;
    
    @ExcelColumn(name = "用户状态", order = 10, width = 15)
    private String status;
    
    // 这个字段不导出
    @ExcelColumn(name = "密码", order = 99, export = false)
    private String password;
    
    // 这个字段也不导出，用于演示开关控制
    @ExcelColumn(name = "内部备注", order = 100, export = false, width = 30)
    private String internalNote;
}
