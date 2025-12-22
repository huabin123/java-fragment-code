package com.fragment.core.proxy;

/**
 * 计算器类 - 用于演示CGLIB代理（没有接口的类）
 */
public class Calculator {
    
    public int add(int a, int b) {
        System.out.println("执行加法运算: " + a + " + " + b);
        return a + b;
    }
    
    public int subtract(int a, int b) {
        System.out.println("执行减法运算: " + a + " - " + b);
        return a - b;
    }
    
    public int multiply(int a, int b) {
        System.out.println("执行乘法运算: " + a + " * " + b);
        return a * b;
    }
    
    public double divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("除数不能为0");
        }
        System.out.println("执行除法运算: " + a + " / " + b);
        return (double) a / b;
    }
    
    // final 方法不能被代理
    public final String getVersion() {
        return "Calculator v1.0";
    }
    
    // 私有方法不会被代理
    private void privateMethod() {
        System.out.println("这是私有方法");
    }
}
