package com.fragment.core.bitwise.demo;

/**
 * 位运算技巧演示
 * 
 * 演示内容：
 * 1. 15个实用技巧的代码实现
 * 2. 位掩码的使用
 * 3. 性能对比测试
 * 
 * @author fragment
 */
public class BitwiseSkillsDemo {
    
    public static void main(String[] args) {
        System.out.println("=== 位运算技巧演示 ===\n");
        
        // 15个实用技巧
        skill01_CheckOddEven();
        skill02_SwapNumbers();
        skill03_IsPowerOfTwo();
        skill04_CountOnes();
        skill05_GetRightmostOne();
        skill06_ClearRightmostOne();
        skill07_GetBit();
        skill08_SetBit();
        skill09_ClearBit();
        skill10_ToggleBit();
        skill11_FastMultiplyDivide();
        skill12_AbsoluteValue();
        skill13_Average();
        skill14_SameSign();
        skill15_FindUnique();
        
        // 位掩码演示
        demonstrateBitmask();
        
        // 性能对比
        performanceComparison();
    }
    
    /**
     * 技巧1：判断奇偶
     */
    private static void skill01_CheckOddEven() {
        System.out.println("【技巧1：判断奇偶】");
        
        int num = 7;
        
        // 传统方法
        boolean isEven1 = (num % 2 == 0);
        
        // 位运算
        boolean isEven2 = (num & 1) == 0;
        
        System.out.println(num + " 是" + (isEven2 ? "偶数" : "奇数"));
        System.out.println("原理：奇数的二进制最后一位是1，偶数是0");
        System.out.println();
    }
    
    /**
     * 技巧2：交换两个数（不用临时变量）
     */
    private static void skill02_SwapNumbers() {
        System.out.println("【技巧2：交换两个数（不用临时变量）】");
        
        int a = 5, b = 3;
        System.out.println("交换前: a=" + a + ", b=" + b);
        
        a = a ^ b;
        b = a ^ b;  // b = (a ^ b) ^ b = a
        a = a ^ b;  // a = (a ^ b) ^ a = b
        
        System.out.println("交换后: a=" + a + ", b=" + b);
        System.out.println("原理：a ^ b ^ b = a");
        System.out.println("注意：这个技巧只是炫技，实际开发中不推荐（可读性差）");
        System.out.println();
    }
    
    /**
     * 技巧3：判断是否为2的幂次
     */
    private static void skill03_IsPowerOfTwo() {
        System.out.println("【技巧3：判断是否为2的幂次】");
        
        int[] nums = {1, 2, 4, 8, 16, 6, 10};
        
        for (int num : nums) {
            boolean isPowerOfTwo = (num > 0) && ((num & (num - 1)) == 0);
            System.out.println(num + " 是2的幂次: " + isPowerOfTwo);
        }
        
        System.out.println("原理：2的幂次的二进制只有一个1");
        System.out.println("例如：8 = 1000, 7 = 0111, 8 & 7 = 0");
        System.out.println();
    }
    
    /**
     * 技巧4：计算二进制中1的个数
     */
    private static void skill04_CountOnes() {
        System.out.println("【技巧4：计算二进制中1的个数】");
        
        int num = 13;  // 1101
        
        // 方法1：逐位检查
        int count1 = 0;
        int temp1 = num;
        while (temp1 != 0) {
            count1 += temp1 & 1;
            temp1 >>= 1;
        }
        
        // 方法2：Brian Kernighan算法（更快）
        int count2 = 0;
        int temp2 = num;
        while (temp2 != 0) {
            temp2 &= (temp2 - 1);  // 每次消除最右边的1
            count2++;
        }
        
        System.out.println(num + " 的二进制: " + Integer.toBinaryString(num));
        System.out.println("1的个数: " + count2);
        System.out.println("原理：num & (num - 1) 会消除最右边的1");
        System.out.println();
    }
    
    /**
     * 技巧5：获取最右边的1
     */
    private static void skill05_GetRightmostOne() {
        System.out.println("【技巧5：获取最右边的1】");
        
        int num = 12;  // 1100
        int rightmostOne = num & (-num);
        
        System.out.println(num + " 的二进制: " + String.format("%8s", Integer.toBinaryString(num)).replace(' ', '0'));
        System.out.println("最右边的1: " + rightmostOne + " (二进制: " + Integer.toBinaryString(rightmostOne) + ")");
        System.out.println("原理：负数是补码表示");
        System.out.println();
    }
    
    /**
     * 技巧6：消除最右边的1
     */
    private static void skill06_ClearRightmostOne() {
        System.out.println("【技巧6：消除最右边的1】");
        
        int num = 12;  // 1100
        int result = num & (num - 1);  // 1000
        
        System.out.println(num + " 的二进制: " + String.format("%8s", Integer.toBinaryString(num)).replace(' ', '0'));
        System.out.println("消除最右边的1后: " + result + " (二进制: " + String.format("%8s", Integer.toBinaryString(result)).replace(' ', '0') + ")");
        System.out.println();
    }
    
    /**
     * 技巧7：获取第n位的值
     */
    private static void skill07_GetBit() {
        System.out.println("【技巧7：获取第n位的值】");
        
        int num = 13;  // 1101
        System.out.println(num + " 的二进制: " + String.format("%8s", Integer.toBinaryString(num)).replace(' ', '0'));
        
        for (int i = 0; i < 4; i++) {
            int bit = (num >> i) & 1;
            System.out.println("第" + i + "位: " + bit);
        }
        System.out.println();
    }
    
    /**
     * 技巧8：设置第n位为1
     */
    private static void skill08_SetBit() {
        System.out.println("【技巧8：设置第n位为1】");
        
        int num = 5;  // 0101
        int n = 1;
        int result = num | (1 << n);  // 0111
        
        System.out.println("原始值: " + num + " (二进制: " + String.format("%8s", Integer.toBinaryString(num)).replace(' ', '0') + ")");
        System.out.println("设置第" + n + "位为1后: " + result + " (二进制: " + String.format("%8s", Integer.toBinaryString(result)).replace(' ', '0') + ")");
        System.out.println();
    }
    
    /**
     * 技巧9：设置第n位为0
     */
    private static void skill09_ClearBit() {
        System.out.println("【技巧9：设置第n位为0】");
        
        int num = 7;  // 0111
        int n = 1;
        int result = num & ~(1 << n);  // 0101
        
        System.out.println("原始值: " + num + " (二进制: " + String.format("%8s", Integer.toBinaryString(num)).replace(' ', '0') + ")");
        System.out.println("设置第" + n + "位为0后: " + result + " (二进制: " + String.format("%8s", Integer.toBinaryString(result)).replace(' ', '0') + ")");
        System.out.println();
    }
    
    /**
     * 技巧10：切换第n位（0变1，1变0）
     */
    private static void skill10_ToggleBit() {
        System.out.println("【技巧10：切换第n位】");
        
        int num = 5;  // 0101
        int n = 1;
        int result = num ^ (1 << n);  // 0111
        
        System.out.println("原始值: " + num + " (二进制: " + String.format("%8s", Integer.toBinaryString(num)).replace(' ', '0') + ")");
        System.out.println("切换第" + n + "位后: " + result + " (二进制: " + String.format("%8s", Integer.toBinaryString(result)).replace(' ', '0') + ")");
        
        result = result ^ (1 << n);  // 再切换回来
        System.out.println("再切换第" + n + "位后: " + result + " (二进制: " + String.format("%8s", Integer.toBinaryString(result)).replace(' ', '0') + ")");
        System.out.println();
    }
    
    /**
     * 技巧11：快速乘除2的幂次
     */
    private static void skill11_FastMultiplyDivide() {
        System.out.println("【技巧11：快速乘除2的幂次】");
        
        int num = 5;
        
        System.out.println("乘法:");
        System.out.println(num + " × 2 = " + (num << 1));
        System.out.println(num + " × 4 = " + (num << 2));
        System.out.println(num + " × 8 = " + (num << 3));
        
        System.out.println("\n除法:");
        num = 20;
        System.out.println(num + " ÷ 2 = " + (num >> 1));
        System.out.println(num + " ÷ 4 = " + (num >> 2));
        System.out.println(num + " ÷ 8 = " + (num >> 3));
        System.out.println();
    }
    
    /**
     * 技巧12：取绝对值（不用if）
     */
    private static void skill12_AbsoluteValue() {
        System.out.println("【技巧12：取绝对值（不用if）】");
        
        int num = -5;
        int mask = num >> 31;  // 负数为-1，正数为0
        int abs = (num + mask) ^ mask;
        
        System.out.println(num + " 的绝对值: " + abs);
        System.out.println("原理：正数 mask=0，(num+0)^0=num；负数 mask=-1，(num-1)^-1=-num");
        System.out.println("注意：这个技巧只是炫技，实际开发中用 Math.abs() 更好");
        System.out.println();
    }
    
    /**
     * 技巧13：求两个数的平均值（防溢出）
     */
    private static void skill13_Average() {
        System.out.println("【技巧13：求两个数的平均值（防溢出）】");
        
        int a = Integer.MAX_VALUE - 1;
        int b = Integer.MAX_VALUE;
        
        // 传统方法（可能溢出）
        // int avg1 = (a + b) / 2;  // 溢出！
        
        // 位运算（不会溢出）
        int avg2 = (a & b) + ((a ^ b) >> 1);
        
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        System.out.println("平均值 = " + avg2);
        System.out.println("原理：(a & b) 是两个数都是1的位，(a ^ b) >> 1 是不同位的平均值");
        System.out.println();
    }
    
    /**
     * 技巧14：判断符号是否相同
     */
    private static void skill14_SameSign() {
        System.out.println("【技巧14：判断符号是否相同】");
        
        int[][] pairs = {{5, 3}, {-5, -3}, {5, -3}};
        
        for (int[] pair : pairs) {
            int a = pair[0];
            int b = pair[1];
            boolean sameSign = (a ^ b) >= 0;
            System.out.println(a + " 和 " + b + " 符号" + (sameSign ? "相同" : "不同"));
        }
        
        System.out.println("原理：符号相同时最高位相同，异或后最高位为0（正数）");
        System.out.println();
    }
    
    /**
     * 技巧15：找出唯一的数（其他数都出现两次）
     */
    private static void skill15_FindUnique() {
        System.out.println("【技巧15：找出唯一的数（其他数都出现两次）】");
        
        int[] nums = {1, 2, 3, 2, 1};
        int result = 0;
        
        for (int num : nums) {
            result ^= num;
        }
        
        System.out.print("数组: [");
        for (int i = 0; i < nums.length; i++) {
            System.out.print(nums[i]);
            if (i < nums.length - 1) System.out.print(", ");
        }
        System.out.println("]");
        System.out.println("唯一的数: " + result);
        System.out.println("原理：a ^ a = 0, a ^ 0 = a");
        System.out.println();
    }
    
    /**
     * 演示位掩码的使用
     */
    private static void demonstrateBitmask() {
        System.out.println("【位掩码演示：权限管理】");
        
        // 定义权限
        int READ    = 1 << 0;  // 0001 = 1
        int WRITE   = 1 << 1;  // 0010 = 2
        int EXECUTE = 1 << 2;  // 0100 = 4
        int DELETE  = 1 << 3;  // 1000 = 8
        
        // 授予权限
        int permission = 0;
        permission |= READ;
        permission |= WRITE;
        
        System.out.println("当前权限: " + String.format("%4s", Integer.toBinaryString(permission)).replace(' ', '0'));
        System.out.println("有读权限: " + ((permission & READ) != 0));
        System.out.println("有写权限: " + ((permission & WRITE) != 0));
        System.out.println("有执行权限: " + ((permission & EXECUTE) != 0));
        System.out.println("有删除权限: " + ((permission & DELETE) != 0));
        
        // 撤销权限
        permission &= ~WRITE;
        System.out.println("\n撤销写权限后: " + String.format("%4s", Integer.toBinaryString(permission)).replace(' ', '0'));
        System.out.println("有写权限: " + ((permission & WRITE) != 0));
        
        // 切换权限
        permission ^= READ;
        System.out.println("\n切换读权限后: " + String.format("%4s", Integer.toBinaryString(permission)).replace(' ', '0'));
        System.out.println("有读权限: " + ((permission & READ) != 0));
        System.out.println();
    }
    
    /**
     * 性能对比测试
     */
    private static void performanceComparison() {
        System.out.println("【性能对比测试】");
        
        int iterations = 100_000_000;
        int num = 123456789;
        
        // 测试1：判断奇偶
        long start1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            boolean result = (num % 2 == 0);
        }
        long time1 = System.nanoTime() - start1;
        
        long start2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            boolean result = (num & 1) == 0;
        }
        long time2 = System.nanoTime() - start2;
        
        System.out.println("判断奇偶（" + iterations + "次）:");
        System.out.println("  传统方法: " + time1 / 1_000_000 + "ms");
        System.out.println("  位运算: " + time2 / 1_000_000 + "ms");
        System.out.println("  提升: " + String.format("%.2f", (double) time1 / time2) + "倍");
        
        // 测试2：乘以8
        start1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            int result = num * 8;
        }
        time1 = System.nanoTime() - start1;
        
        start2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            int result = num << 3;
        }
        time2 = System.nanoTime() - start2;
        
        System.out.println("\n乘以8（" + iterations + "次）:");
        System.out.println("  传统方法: " + time1 / 1_000_000 + "ms");
        System.out.println("  位运算: " + time2 / 1_000_000 + "ms");
        System.out.println("  提升: " + String.format("%.2f", (double) time1 / time2) + "倍");
        
        System.out.println("\n注意：现代编译器会自动优化，性能差距不大");
        System.out.println("可读性 > 性能（除非在性能关键路径上）");
    }
}
