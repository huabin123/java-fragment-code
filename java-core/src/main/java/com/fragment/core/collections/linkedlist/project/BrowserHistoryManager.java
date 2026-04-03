package com.fragment.core.collections.linkedlist.project;

import java.util.LinkedList;

/**
 * 浏览器历史记录管理器
 * 
 * 使用LinkedList实现浏览器的前进、后退功能
 * 
 * 特性：
 * 1. 支持访问新页面
 * 2. 支持后退
 * 3. 支持前进
 * 4. 访问新页面时清除前进历史
 * 5. 支持查看历史记录
 * 
 * @author huabin
 */
public class BrowserHistoryManager {
    
    private final LinkedList<String> history = new LinkedList<>();
    private int currentIndex = -1;
    private final int maxSize;

    /**
     * 构造函数
     * 
     * @param maxSize 最大历史记录数
     */
    public BrowserHistoryManager(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * 访问新页面
     * 
     * @param url URL
     */
    public void visit(String url) {
        // 删除当前位置之后的所有记录
        while (history.size() > currentIndex + 1) {
            history.removeLast();
        }
        
        // 添加新页面
        history.addLast(url);
        currentIndex++;
        
        // 如果超过最大容量，删除最老的记录
        if (history.size() > maxSize) {
            history.removeFirst();
            currentIndex--;
        }
        
        System.out.println("[Browser] 访问: " + url);
    }

    /**
     * 后退
     * 
     * @return 后退到的URL，如果已经是第一页返回null
     */
    public String back() {
        if (currentIndex > 0) {
            currentIndex--;
            String url = history.get(currentIndex);
            System.out.println("[Browser] 后退到: " + url);
            return url;
        }
        System.out.println("[Browser] 已经是第一页，无法后退");
        return null;
    }

    /**
     * 前进
     * 
     * @return 前进到的URL，如果已经是最后一页返回null
     */
    public String forward() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            String url = history.get(currentIndex);
            System.out.println("[Browser] 前进到: " + url);
            return url;
        }
        System.out.println("[Browser] 已经是最后一页，无法前进");
        return null;
    }

    /**
     * 获取当前页面
     * 
     * @return 当前URL
     */
    public String current() {
        if (currentIndex >= 0 && currentIndex < history.size()) {
            return history.get(currentIndex);
        }
        return null;
    }

    /**
     * 判断是否可以后退
     */
    public boolean canGoBack() {
        return currentIndex > 0;
    }

    /**
     * 判断是否可以前进
     */
    public boolean canGoForward() {
        return currentIndex < history.size() - 1;
    }

    /**
     * 获取历史记录数量
     */
    public int size() {
        return history.size();
    }

    /**
     * 清空历史记录
     */
    public void clear() {
        history.clear();
        currentIndex = -1;
        System.out.println("[Browser] 清空历史记录");
    }

    /**
     * 打印历史记录
     */
    public void printHistory() {
        System.out.println("\n========== 浏览历史记录 ==========");
        if (history.isEmpty()) {
            System.out.println("（无历史记录）");
        } else {
            for (int i = 0; i < history.size(); i++) {
                String prefix = (i == currentIndex) ? "-> " : "   ";
                System.out.println(prefix + (i + 1) + ". " + history.get(i));
            }
        }
        System.out.println("================================\n");
    }

    /**
     * 测试示例
     */
    public static void main(String[] args) {
        System.out.println("========== 浏览器历史记录管理器测试 ==========\n");
        
        // 创建历史记录管理器，最大容量10
        BrowserHistoryManager browser = new BrowserHistoryManager(10);
        
        // 访问页面
        browser.visit("https://www.google.com");
        browser.visit("https://www.github.com");
        browser.visit("https://www.stackoverflow.com");
        browser.visit("https://www.baidu.com");
        
        browser.printHistory();
        
        // 后退
        browser.back();
        browser.back();
        browser.printHistory();
        
        // 前进
        browser.forward();
        browser.printHistory();
        
        // 访问新页面（清除前进历史）
        browser.visit("https://www.youtube.com");
        browser.printHistory();
        
        // 继续后退
        browser.back();
        browser.back();
        browser.back();
        browser.printHistory();
        
        // 尝试继续后退（已经是第一页）
        browser.back();
        
        // 前进到最后
        while (browser.canGoForward()) {
            browser.forward();
        }
        browser.printHistory();
        
        // 尝试继续前进（已经是最后一页）
        browser.forward();
        
        // 测试最大容量限制
        System.out.println("\n测试最大容量限制:");
        BrowserHistoryManager smallBrowser = new BrowserHistoryManager(3);
        smallBrowser.visit("Page1");
        smallBrowser.visit("Page2");
        smallBrowser.visit("Page3");
        smallBrowser.visit("Page4");  // 会删除Page1
        smallBrowser.printHistory();
        
        System.out.println("测试完成");
    }
}
