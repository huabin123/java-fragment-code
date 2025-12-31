package com.fragment.core.annotations.project;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 实际项目Demo：数据验证框架
 * 
 * 这是一个完整的、可以在实际项目中使用的数据验证框架
 * 类似于Hibernate Validator，但更简单易懂
 * 
 * 功能：
 * 1. 支持多种验证注解
 * 2. 支持自定义验证器
 * 3. 支持分组验证
 * 4. 支持级联验证
 * 5. 详细的错误信息
 * 
 * @author fragment
 */
public class ValidationFramework {

    public static void main(String[] args) {
        System.out.println("=== 数据验证框架演示 ===\n");
        
        // 创建验证器
        Validator validator = new Validator();
        
        // 测试1: 用户注册表单验证
        testUserRegistration(validator);
        
        // 测试2: 订单验证
        testOrderValidation(validator);
        
        // 测试3: 级联验证
        testCascadeValidation(validator);
    }

    /**
     * 测试1: 用户注册表单验证
     */
    private static void testUserRegistration(Validator validator) {
        System.out.println("【测试1: 用户注册表单验证】\n");
        
        // 有效数据
        System.out.println("1. 测试有效数据:");
        UserRegisterForm validForm = new UserRegisterForm();
        validForm.setUsername("zhangsan");
        validForm.setPassword("123456");
        validForm.setConfirmPassword("123456");
        validForm.setEmail("zhangsan@example.com");
        validForm.setPhone("13800138000");
        validForm.setAge(25);
        validForm.setGender("male");
        
        ValidationResult result1 = validator.validate(validForm);
        printResult(result1);
        
        // 无效数据
        System.out.println("\n2. 测试无效数据:");
        UserRegisterForm invalidForm = new UserRegisterForm();
        invalidForm.setUsername("ab");  // 太短
        invalidForm.setPassword("123");  // 太短
        invalidForm.setConfirmPassword("456");  // 不匹配
        invalidForm.setEmail("invalid");  // 格式错误
        invalidForm.setPhone("12345");  // 格式错误
        invalidForm.setAge(15);  // 太小
        invalidForm.setGender("unknown");  // 不在选项中
        
        ValidationResult result2 = validator.validate(invalidForm);
        printResult(result2);
        
        System.out.println();
    }

    /**
     * 测试2: 订单验证
     */
    private static void testOrderValidation(Validator validator) {
        System.out.println("【测试2: 订单验证】\n");
        
        Order order = new Order();
        order.setOrderNo("ORD20240101001");
        order.setAmount(99.99);
        order.setQuantity(5);
        order.setStatus("pending");
        
        ValidationResult result = validator.validate(order);
        printResult(result);
        
        System.out.println();
    }

    /**
     * 测试3: 级联验证
     */
    private static void testCascadeValidation(Validator validator) {
        System.out.println("【测试3: 级联验证】\n");
        
        // 创建地址
        Address address = new Address();
        address.setProvince("北京市");
        address.setCity("北京市");
        address.setDistrict("朝阳区");
        address.setDetail("某某街道123号");
        address.setZipCode("100000");
        
        // 创建用户（包含地址）
        User user = new User();
        user.setUsername("zhangsan");
        user.setEmail("zhangsan@example.com");
        user.setAge(25);
        user.setAddress(address);
        
        ValidationResult result = validator.validate(user);
        printResult(result);
        
        System.out.println();
    }

    private static void printResult(ValidationResult result) {
        if (result.isValid()) {
            System.out.println("✓ 验证通过");
        } else {
            System.out.println("✗ 验证失败，错误信息:");
            for (ValidationError error : result.getErrors()) {
                System.out.println("  - " + error.getField() + ": " + error.getMessage());
            }
        }
    }

    // ========== 验证注解定义 ==========

    /**
     * 非空验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface NotNull {
        String message() default "不能为空";
        Class<?>[] groups() default {};
    }

    /**
     * 非空字符串验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface NotBlank {
        String message() default "不能为空字符串";
        Class<?>[] groups() default {};
    }

    /**
     * 长度验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Size {
        int min() default 0;
        int max() default Integer.MAX_VALUE;
        String message() default "长度不符合要求";
        Class<?>[] groups() default {};
    }

    /**
     * 邮箱验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Email {
        String message() default "邮箱格式不正确";
        Class<?>[] groups() default {};
    }

    /**
     * 手机号验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Phone {
        String message() default "手机号格式不正确";
        Class<?>[] groups() default {};
    }

    /**
     * 最小值验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Min {
        int value();
        String message() default "值太小";
        Class<?>[] groups() default {};
    }

    /**
     * 最大值验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Max {
        int value();
        String message() default "值太大";
        Class<?>[] groups() default {};
    }

    /**
     * 范围验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Range {
        double min();
        double max();
        String message() default "值不在范围内";
        Class<?>[] groups() default {};
    }

    /**
     * 正则表达式验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Pattern {
        String value();
        String message() default "格式不正确";
        Class<?>[] groups() default {};
    }

    /**
     * 选项验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface In {
        String[] value();
        String message() default "值不在允许的选项中";
        Class<?>[] groups() default {};
    }

    /**
     * 字段相等验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    public @interface FieldEquals {
        String field1();
        String field2();
        String message() default "两个字段的值必须相等";
        Class<?>[] groups() default {};
    }

    /**
     * 级联验证
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Documented
    public @interface Valid {
    }

    // ========== 验证器实现 ==========

    /**
     * 验证器
     */
    static class Validator {
        
        public ValidationResult validate(Object obj) {
            return validate(obj, new Class<?>[0]);
        }
        
        public ValidationResult validate(Object obj, Class<?>... groups) {
            ValidationResult result = new ValidationResult();
            
            if (obj == null) {
                return result;
            }
            
            Class<?> clazz = obj.getClass();
            
            // 验证类级别的注解
            validateClassAnnotations(obj, clazz, result, groups);
            
            // 验证字段级别的注解
            validateFieldAnnotations(obj, clazz, result, groups);
            
            return result;
        }
        
        private void validateClassAnnotations(Object obj, Class<?> clazz, 
                                             ValidationResult result, Class<?>[] groups) {
            // @FieldEquals验证
            if (clazz.isAnnotationPresent(FieldEquals.class)) {
                FieldEquals annotation = clazz.getAnnotation(FieldEquals.class);
                if (matchesGroup(annotation.groups(), groups)) {
                    try {
                        Field field1 = clazz.getDeclaredField(annotation.field1());
                        Field field2 = clazz.getDeclaredField(annotation.field2());
                        field1.setAccessible(true);
                        field2.setAccessible(true);
                        
                        Object value1 = field1.get(obj);
                        Object value2 = field2.get(obj);
                        
                        if (!Objects.equals(value1, value2)) {
                            result.addError(annotation.field1() + "," + annotation.field2(), 
                                          annotation.message());
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        }
        
        private void validateFieldAnnotations(Object obj, Class<?> clazz, 
                                             ValidationResult result, Class<?>[] groups) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                
                try {
                    Object value = field.get(obj);
                    String fieldName = field.getName();
                    
                    // @NotNull验证
                    if (field.isAnnotationPresent(NotNull.class)) {
                        NotNull annotation = field.getAnnotation(NotNull.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            if (value == null) {
                                result.addError(fieldName, annotation.message());
                                continue;
                            }
                        }
                    }
                    
                    // @NotBlank验证
                    if (field.isAnnotationPresent(NotBlank.class)) {
                        NotBlank annotation = field.getAnnotation(NotBlank.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            if (value == null || value.toString().trim().isEmpty()) {
                                result.addError(fieldName, annotation.message());
                                continue;
                            }
                        }
                    }
                    
                    if (value == null) {
                        continue;
                    }
                    
                    // @Size验证
                    if (field.isAnnotationPresent(Size.class)) {
                        Size annotation = field.getAnnotation(Size.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            int length = value.toString().length();
                            if (length < annotation.min() || length > annotation.max()) {
                                result.addError(fieldName, annotation.message() + 
                                    " (当前: " + length + ", 要求: " + annotation.min() + "-" + annotation.max() + ")");
                            }
                        }
                    }
                    
                    // @Email验证
                    if (field.isAnnotationPresent(Email.class)) {
                        Email annotation = field.getAnnotation(Email.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            String email = value.toString();
                            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                                result.addError(fieldName, annotation.message());
                            }
                        }
                    }
                    
                    // @Phone验证
                    if (field.isAnnotationPresent(Phone.class)) {
                        Phone annotation = field.getAnnotation(Phone.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            String phone = value.toString();
                            if (!phone.matches("^1[3-9]\\d{9}$")) {
                                result.addError(fieldName, annotation.message());
                            }
                        }
                    }
                    
                    // @Min验证
                    if (field.isAnnotationPresent(Min.class)) {
                        Min annotation = field.getAnnotation(Min.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            int intValue = ((Number) value).intValue();
                            if (intValue < annotation.value()) {
                                result.addError(fieldName, annotation.message() + 
                                    " (当前: " + intValue + ", 最小: " + annotation.value() + ")");
                            }
                        }
                    }
                    
                    // @Max验证
                    if (field.isAnnotationPresent(Max.class)) {
                        Max annotation = field.getAnnotation(Max.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            int intValue = ((Number) value).intValue();
                            if (intValue > annotation.value()) {
                                result.addError(fieldName, annotation.message() + 
                                    " (当前: " + intValue + ", 最大: " + annotation.value() + ")");
                            }
                        }
                    }
                    
                    // @Range验证
                    if (field.isAnnotationPresent(Range.class)) {
                        Range annotation = field.getAnnotation(Range.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            double doubleValue = ((Number) value).doubleValue();
                            if (doubleValue < annotation.min() || doubleValue > annotation.max()) {
                                result.addError(fieldName, annotation.message() + 
                                    " (当前: " + doubleValue + ", 范围: " + annotation.min() + "-" + annotation.max() + ")");
                            }
                        }
                    }
                    
                    // @Pattern验证
                    if (field.isAnnotationPresent(Pattern.class)) {
                        Pattern annotation = field.getAnnotation(Pattern.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            String str = value.toString();
                            if (!str.matches(annotation.value())) {
                                result.addError(fieldName, annotation.message());
                            }
                        }
                    }
                    
                    // @In验证
                    if (field.isAnnotationPresent(In.class)) {
                        In annotation = field.getAnnotation(In.class);
                        if (matchesGroup(annotation.groups(), groups)) {
                            String str = value.toString();
                            boolean found = false;
                            for (String option : annotation.value()) {
                                if (option.equals(str)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                result.addError(fieldName, annotation.message() + 
                                    " (允许的值: " + String.join(", ", annotation.value()) + ")");
                            }
                        }
                    }
                    
                    // @Valid级联验证
                    if (field.isAnnotationPresent(Valid.class)) {
                        ValidationResult cascadeResult = validate(value, groups);
                        for (ValidationError error : cascadeResult.getErrors()) {
                            result.addError(fieldName + "." + error.getField(), error.getMessage());
                        }
                    }
                    
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
        
        private boolean matchesGroup(Class<?>[] annotationGroups, Class<?>[] requestedGroups) {
            if (requestedGroups.length == 0) {
                return annotationGroups.length == 0;
            }
            for (Class<?> requestedGroup : requestedGroups) {
                for (Class<?> annotationGroup : annotationGroups) {
                    if (requestedGroup.equals(annotationGroup)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * 验证结果
     */
    static class ValidationResult {
        private List<ValidationError> errors = new ArrayList<>();
        
        public void addError(String field, String message) {
            errors.add(new ValidationError(field, message));
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<ValidationError> getErrors() {
            return errors;
        }
    }

    /**
     * 验证错误
     */
    static class ValidationError {
        private String field;
        private String message;
        
        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
    }

    // ========== 测试类 ==========

    /**
     * 用户注册表单
     */
    @FieldEquals(field1 = "password", field2 = "confirmPassword", message = "两次输入的密码不一致")
    static class UserRegisterForm {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 20, message = "密码长度必须在6-20之间")
        private String password;

        @NotBlank(message = "确认密码不能为空")
        private String confirmPassword;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        @NotBlank(message = "手机号不能为空")
        @Phone(message = "手机号格式不正确")
        private String phone;

        @NotNull(message = "年龄不能为空")
        @Min(value = 18, message = "年龄必须大于等于18岁")
        @Max(value = 100, message = "年龄必须小于等于100岁")
        private Integer age;

        @NotBlank(message = "性别不能为空")
        @In(value = {"male", "female"}, message = "性别只能是male或female")
        private String gender;

        // Getters and Setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
    }

    /**
     * 订单
     */
    static class Order {
        @NotBlank(message = "订单号不能为空")
        @Pattern(value = "^ORD\\d{11}$", message = "订单号格式不正确")
        private String orderNo;

        @NotNull(message = "金额不能为空")
        @Range(min = 0.01, max = 999999.99, message = "金额必须在0.01-999999.99之间")
        private Double amount;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于等于1")
        private Integer quantity;

        @NotBlank(message = "状态不能为空")
        @In(value = {"pending", "paid", "shipped", "completed", "cancelled"}, 
            message = "状态不正确")
        private String status;

        // Getters and Setters
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * 用户
     */
    static class User {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @Email(message = "邮箱格式不正确")
        private String email;

        @Min(value = 18, message = "年龄必须大于等于18岁")
        private Integer age;

        @Valid  // 级联验证
        private Address address;

        // Getters and Setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    /**
     * 地址
     */
    static class Address {
        @NotBlank(message = "省份不能为空")
        private String province;

        @NotBlank(message = "城市不能为空")
        private String city;

        @NotBlank(message = "区县不能为空")
        private String district;

        @NotBlank(message = "详细地址不能为空")
        private String detail;

        @Pattern(value = "^\\d{6}$", message = "邮编格式不正确")
        private String zipCode;

        // Getters and Setters
        public String getProvince() { return province; }
        public void setProvince(String province) { this.province = province; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
        public String getZipCode() { return zipCode; }
        public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    }
}
