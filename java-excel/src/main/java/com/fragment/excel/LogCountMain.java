package com.fragment.excel;

import com.fragment.excel.util.LogCountProcessor;

/**
 * 日志计数处理主类
 * 用于执行日志计数的填充任务
 */
public class LogCountMain {
    
    public static void main(String[] args) {
        System.out.println("开始处理日志计数...");
        
        LogCountProcessor processor = new LogCountProcessor();
        processor.process();
        
        System.out.println("处理完成！");
    }
}
