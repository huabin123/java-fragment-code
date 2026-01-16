package com.fragment.core.bitwise.demo;

/**
 * 位运算基础演示
 * 
 * 演示内容：
 * 1. 六种位运算符的使用
 * 2. 二进制可视化输出
 * 3. 负数的补码演示
 * 4. 常见陷阱演示
 * 
 * @author fragment
 */
public class BitwiseBasicDemo {
    
    public static void main(String[] args) {
        System.out.println("=== 位运算基础演示 ===\n");
        
        // 1. 二进制基础
        demonstrateBinary();
        
        // 2. 六种位运算符
        demonstrateAndOperator();
        demonstrateOrOperator();
        demonstrateXorOperator();
        demonstrateNotOperator();
        demonstrateLeftShift();
        demonstrateRightShift();
        
        // 3. 负数的补码
        demonstrateNegativeNumbers();
        
        // 4. 常见陷阱
        demonstrateCommonPitfalls();
    }
    
    /**
     * 演示二进制基础
     */
    private static void demonstrateBinary() {
        System.out.println("【1. 二进制基础】");
        
        int num = 13;
        System.out.println("十进制: " + num);
        System.out.println("二进制: " + toBinaryString(num));
        System.out.println("十六进制: 0x" + Integer.toHexString(num));
        System.out.println();
        
        // 演示二进制转十进制
        String binary = "1101";
        int decimal = Integer.parseInt(binary, 2);
        System.out.println(binary + "(二进制) = " + decimal + "(十进制)");
        System.out.println();
    }
    
    /**
     * 演示按位与（&）
     */
    private static void demonstrateAndOperator() {
        System.out.println("【2. 按位与（&）- 两个都是1才是1】");
        
        int a = 5;  // 0101
        int b = 3;  // 0011
        int result = a & b;  // 0001
        
        System.out.println("  " + toBinaryString(a) + "  (" + a + ")");
        System.out.println("& " + toBinaryString(b) + "  (" + b + ")");
        System.out.println("  " + "--------");
        System.out.println("  " + toBinaryString(result) + "  (" + result + ")");
        System.out.println();
        
        // 应用：判断奇偶
        System.out.println("应用：判断奇偶");
        int num = 7;
        if ((num & 1) == 0) {
            System.out.println(num + " 是偶数");
        } else {
            System.out.println(num + " 是奇数");
        }
        System.out.println();
    }
    
    /**
     * 演示按位或（|）
     */
    private static void demonstrateOrOperator() {
        System.out.println("【3. 按位或（|）- 有一个是1就是1】");
        
        int a = 5;  // 0101
        int b = 3;  // 0011
        int result = a | b;  // 0111
        
        System.out.println("  " + toBinaryString(a) + "  (" + a + ")");
        System.out.println("| " + toBinaryString(b) + "  (" + b + ")");
        System.out.println("  " + "--------");
        System.out.println("  " + toBinaryString(result) + "  (" + result + ")");
        System.out.println();
        
        // 应用：权限管理
        System.out.println("应用：权限管理");
        int READ = 1;      // 0001
        int WRITE = 2;     // 0010
        int EXECUTE = 4;   // 0100
        
        int permission = READ | WRITE;  // 0011 = 3
        System.out.println("权限: " + toBinaryString(permission) + " (" + permission + ")");
        System.out.println("有读权限: " + ((permission & READ) != 0));
        System.out.println("有写权限: " + ((permission & WRITE) != 0));
        System.out.println("有执行权限: " + ((permission & EXECUTE) != 0));
        System.out.println();
    }
    
    /**
     * 演示按位异或（^）
     */
    private static void demonstrateXorOperator() {
        System.out.println("【4. 按位异或（^）- 相同为0，不同为1】");
        
        int a = 5;  // 0101
        int b = 3;  // 0011
        int result = a ^ b;  // 0110
        
        System.out.println("  " + toBinaryString(a) + "  (" + a + ")");
        System.out.println("^ " + toBinaryString(b) + "  (" + b + ")");
        System.out.println("  " + "--------");
        System.out.println("  " + toBinaryString(result) + "  (" + result + ")");
        System.out.println();
        
        // 特性演示
        System.out.println("特性演示：");
        System.out.println("a ^ a = " + (a ^ a) + " (自己和自己异或等于0)");
        System.out.println("a ^ 0 = " + (a ^ 0) + " (和0异或等于自己)");
        System.out.println("a ^ b ^ b = " + (a ^ b ^ b) + " (异或两次等于没异或)");
        System.out.println();
        
        // 应用：交换两个数
        System.out.println("应用：交换两个数（不用临时变量）");
        int x = 5, y = 3;
        System.out.println("交换前: x=" + x + ", y=" + y);
        x = x ^ y;
        y = x ^ y;
        x = x ^ y;
        System.out.println("交换后: x=" + x + ", y=" + y);
        System.out.println();
    }
    
    /**
     * 演示按位取反（~）
     */
    private static void demonstrateNotOperator() {
        System.out.println("【5. 按位取反（~）- 0变1，1变0】");
        
        int a = 5;  // 00000000000000000000000000000101
        int result = ~a;  // 11111111111111111111111111111010 = -6
        
        System.out.println("  " + toBinaryString32(a) + "  (" + a + ")");
        System.out.println("~ " + toBinaryString32(result) + "  (" + result + ")");
        System.out.println();
        
        System.out.println("规律: ~n = -n - 1");
        System.out.println("~5 = " + (~5));
        System.out.println("~(-5) = " + (~(-5)));
        System.out.println();
    }
    
    /**
     * 演示左移（<<）
     */
    private static void demonstrateLeftShift() {
        System.out.println("【6. 左移（<<）- 向左移动，右边补0】");
        
        int a = 5;  // 0101
        System.out.println("原始值: " + toBinaryString(a) + " (" + a + ")");
        System.out.println("左移1位: " + toBinaryString(a << 1) + " (" + (a << 1) + ") = " + a + " × 2");
        System.out.println("左移2位: " + toBinaryString(a << 2) + " (" + (a << 2) + ") = " + a + " × 4");
        System.out.println("左移3位: " + toBinaryString(a << 3) + " (" + (a << 3) + ") = " + a + " × 8");
        System.out.println();
        
        System.out.println("规律: a << n = a × 2^n");
        System.out.println();
    }
    
    /**
     * 演示右移（>>）
     */
    private static void demonstrateRightShift() {
        System.out.println("【7. 右移（>>）- 向右移动，左边补符号位】");
        
        // 正数右移
        int a = 10;  // 1010
        System.out.println("正数右移:");
        System.out.println("原始值: " + toBinaryString(a) + " (" + a + ")");
        System.out.println("右移1位: " + toBinaryString(a >> 1) + " (" + (a >> 1) + ") = " + a + " ÷ 2");
        System.out.println("右移2位: " + toBinaryString(a >> 2) + " (" + (a >> 2) + ") = " + a + " ÷ 4");
        System.out.println();
        
        // 负数右移
        int b = -10;
        System.out.println("负数右移:");
        System.out.println("原始值: " + toBinaryString32(b) + " (" + b + ")");
        System.out.println("算术右移(>>): " + toBinaryString32(b >> 1) + " (" + (b >> 1) + ")");
        System.out.println("逻辑右移(>>>): " + toBinaryString32(b >>> 1) + " (" + (b >>> 1) + ")");
        System.out.println();
        
        System.out.println("区别:");
        System.out.println(">> : 算术右移，左边补符号位（正数补0，负数补1）");
        System.out.println(">>>: 逻辑右移，左边补0（无论正负）");
        System.out.println();
    }
    
    /**
     * 演示负数的补码
     */
    private static void demonstrateNegativeNumbers() {
        System.out.println("【8. 负数的补码】");
        
        int positive = 5;
        int negative = -5;
        
        System.out.println(" 5的二进制: " + toBinaryString32(positive));
        System.out.println("-5的二进制: " + toBinaryString32(negative));
        System.out.println();
        
        System.out.println("计算过程:");
        System.out.println("1. 原码: " + toBinaryString32(positive));
        System.out.println("2. 反码: " + toBinaryString32(~positive) + " (符号位不变，其他位取反)");
        System.out.println("3. 补码: " + toBinaryString32(negative) + " (反码 + 1)");
        System.out.println();
        
        // 验证：5 + (-5) = 0
        System.out.println("验证: 5 + (-5) = 0");
        System.out.println("  " + toBinaryString32(positive));
        System.out.println("+ " + toBinaryString32(negative));
        System.out.println("  " + "--------------------------------");
        System.out.println(" " + toBinaryString32(positive + negative) + " (溢出的1被丢弃)");
        System.out.println();
    }
    
    /**
     * 演示常见陷阱
     */
    private static void demonstrateCommonPitfalls() {
        System.out.println("【9. 常见陷阱】");
        
        // 陷阱1：优先级问题
        System.out.println("陷阱1：优先级问题");
        int num = 5;
        // 错误写法：num & 1 == 0 实际是 num & (1 == 0)，会导致编译错误
        // 正确写法：(num & 1) == 0
        System.out.println("错误写法 'num & 1 == 0' 会导致编译错误：试图对int和boolean进行位运算");
        System.out.println("(num & 1) == 0 的结果: " + ((num & 1) == 0));  // false（正确）
        System.out.println("建议：位运算时多加括号！");
        System.out.println();
        
        // 陷阱2：移位超过32位
        System.out.println("陷阱2：移位超过32位");
        int a = 1;
        System.out.println("1 << 32 = " + (a << 32) + " (不是0！)");
        System.out.println("1 << 33 = " + (a << 33) + " (相当于 << 1)");
        System.out.println("原因：Java会对移位数取模（int: n % 32, long: n % 64）");
        System.out.println();
        
        // 陷阱3：~运算符的结果
        System.out.println("陷阱3：~运算符的结果");
        int b = 5;
        System.out.println("~5 = " + (~b) + " (不是-5，是-6！)");
        System.out.println("原因：~n = -n - 1");
        System.out.println();
    }
    
    /**
     * 将整数转换为8位二进制字符串
     */
    private static String toBinaryString(int num) {
        String binary = Integer.toBinaryString(num);
        // 只保留最后8位
        if (binary.length() > 8) {
            binary = binary.substring(binary.length() - 8);
        }
        return String.format("%8s", binary).replace(' ', '0');
    }
    
    /**
     * 将整数转换为32位二进制字符串
     */
    private static String toBinaryString32(int num) {
        String binary = Integer.toBinaryString(num);
        if (binary.length() > 32) {
            binary = binary.substring(binary.length() - 32);
        }
        return String.format("%32s", binary).replace(' ', '0');
    }
}
