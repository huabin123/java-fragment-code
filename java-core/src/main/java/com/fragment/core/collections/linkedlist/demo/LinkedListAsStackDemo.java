package com.fragment.core.collections.linkedlist.demo;

import java.util.Deque;
import java.util.LinkedList;

/**
 * LinkedList作为栈使用演示
 * 
 * 演示内容：
 * 1. Deque接口的栈操作
 * 2. 栈的LIFO特性
 * 3. 实际应用场景
 * 
 * @author huabin
 */
public class LinkedListAsStackDemo {

    public static void main(String[] args) {
        System.out.println("========== LinkedList作为栈使用演示 ==========\n");
        
        // 1. 栈的基本操作
        basicStackOperations();
        
        // 2. 实际应用：括号匹配
        bracketMatchingExample();
        
        // 3. 实际应用：表达式求值
        expressionEvaluationExample();
    }

    /**
     * 1. 栈的基本操作
     */
    private static void basicStackOperations() {
        System.out.println("1. 栈的基本操作");
        System.out.println("----------------------------------------");
        
        Deque<String> stack = new LinkedList<>();
        
        // push：入栈（添加到头部）
        stack.push("A");
        stack.push("B");
        stack.push("C");
        System.out.println("入栈后: " + stack);
        
        // peek：查看栈顶元素（不删除）
        String top = stack.peek();
        System.out.println("peek: " + top + ", 栈: " + stack);
        
        // pop：出栈（删除栈顶元素）
        String popped = stack.pop();
        System.out.println("pop: " + popped + ", 栈: " + stack);
        
        // size：栈大小
        System.out.println("size: " + stack.size());
        
        // isEmpty：是否为空
        System.out.println("isEmpty: " + stack.isEmpty());
        
        System.out.println("\nLIFO特性演示:");
        stack.clear();
        stack.push("1");
        stack.push("2");
        stack.push("3");
        System.out.println("入栈顺序: 1, 2, 3");
        System.out.print("出栈顺序: ");
        while (!stack.isEmpty()) {
            System.out.print(stack.pop() + " ");
        }
        System.out.println("（后进先出）");
        
        System.out.println();
    }

    /**
     * 2. 实际应用：括号匹配
     */
    private static void bracketMatchingExample() {
        System.out.println("2. 实际应用：括号匹配");
        System.out.println("----------------------------------------");
        
        String[] testCases = {
            "()",
            "()[]{}",
            "(]",
            "([)]",
            "{[]}",
            "((()))",
            "((())",
            "(){}}{",
        };
        
        for (String testCase : testCases) {
            boolean isValid = isValidBrackets(testCase);
            System.out.println("\"" + testCase + "\" -> " + (isValid ? "有效" : "无效"));
        }
        
        System.out.println();
    }

    /**
     * 判断括号是否匹配
     */
    private static boolean isValidBrackets(String s) {
        Deque<Character> stack = new LinkedList<>();
        
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                // 左括号入栈
                stack.push(c);
            } else {
                // 右括号，检查是否匹配
                if (stack.isEmpty()) {
                    return false;
                }
                char top = stack.pop();
                if ((c == ')' && top != '(') ||
                    (c == ']' && top != '[') ||
                    (c == '}' && top != '{')) {
                    return false;
                }
            }
        }
        
        return stack.isEmpty();
    }

    /**
     * 3. 实际应用：表达式求值
     */
    private static void expressionEvaluationExample() {
        System.out.println("3. 实际应用：表达式求值（逆波兰表达式）");
        System.out.println("----------------------------------------");
        
        // 逆波兰表达式（后缀表达式）
        String[][] testCases = {
            {"2", "1", "+", "3", "*"},  // (2 + 1) * 3 = 9
            {"4", "13", "5", "/", "+"},  // 4 + (13 / 5) = 6
            {"10", "6", "9", "3", "+", "-11", "*", "/", "*", "17", "+", "5", "+"}
        };
        
        for (String[] tokens : testCases) {
            int result = evalRPN(tokens);
            System.out.println("表达式: " + String.join(" ", tokens));
            System.out.println("结果: " + result);
            System.out.println();
        }
    }

    /**
     * 计算逆波兰表达式
     */
    private static int evalRPN(String[] tokens) {
        Deque<Integer> stack = new LinkedList<>();
        
        for (String token : tokens) {
            if (isOperator(token)) {
                // 操作符，弹出两个操作数
                int b = stack.pop();
                int a = stack.pop();
                int result = calculate(a, b, token);
                stack.push(result);
            } else {
                // 操作数，入栈
                stack.push(Integer.parseInt(token));
            }
        }
        
        return stack.pop();
    }

    /**
     * 判断是否是操作符
     */
    private static boolean isOperator(String token) {
        return "+".equals(token) || "-".equals(token) || 
               "*".equals(token) || "/".equals(token);
    }

    /**
     * 计算
     */
    private static int calculate(int a, int b, String operator) {
        switch (operator) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/": return a / b;
            default: throw new IllegalArgumentException("Invalid operator: " + operator);
        }
    }
}
