package com.fragment.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fragment.excel.model.ResultData;
import com.fragment.excel.model.SysLogData;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试工具：查看sys_log和result中的URL格式
 */
public class DebugLogCount {
    
    private static final String RESOURCE_PATH = "/Users/huabin/workspace/playground/my-github/java-fragment-code/java-excel/src/main/resources/log_count";
    private static final String SYS_LOG_FILE = RESOURCE_PATH + "/sys_log.xlsx";
    private static final String RESULT_FILE = RESOURCE_PATH + "/result.xlsx";
    
    public static void main(String[] args) {
        System.out.println("=== SYS_LOG.XLSX 前10条数据 ===");
        List<SysLogData> sysLogList = new ArrayList<>();
        EasyExcel.read(SYS_LOG_FILE, SysLogData.class, new AnalysisEventListener<SysLogData>() {
            @Override
            public void invoke(SysLogData data, AnalysisContext context) {
                sysLogList.add(data);
            }
            
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        
        for (int i = 0; i < Math.min(10, sysLogList.size()); i++) {
            SysLogData data = sysLogList.get(i);
            System.out.println("原始URL: " + data.getRequestUrl());
            System.out.println("标准化后: " + normalizeUrl(data.getRequestUrl()));
            System.out.println("计数: " + data.getCount());
            System.out.println("---");
        }
        
        System.out.println("\n=== RESULT.XLSX 前10条数据 ===");
        List<ResultData> resultList = new ArrayList<>();
        EasyExcel.read(RESULT_FILE, ResultData.class, new AnalysisEventListener<ResultData>() {
            @Override
            public void invoke(ResultData data, AnalysisContext context) {
                resultList.add(data);
            }
            
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet("Sheet1-产品已提供服务的接口清单").doRead();
        
        for (int i = 0; i < Math.min(10, resultList.size()); i++) {
            ResultData data = resultList.get(i);
            System.out.println("URL: " + data.getUrl());
            System.out.println("---");
        }
        
        // 检查是否有匹配
        System.out.println("\n=== 匹配检查 ===");
        int matchCount = 0;
        for (ResultData result : resultList) {
            for (SysLogData sysLog : sysLogList) {
                String normalizedSysLogUrl = normalizeUrl(sysLog.getRequestUrl());
                if (result.getUrl() != null && result.getUrl().equals(normalizedSysLogUrl)) {
                    matchCount++;
                    System.out.println("匹配成功:");
                    System.out.println("  result URL: " + result.getUrl());
                    System.out.println("  sys_log原始: " + sysLog.getRequestUrl());
                    System.out.println("  sys_log标准化: " + normalizedSysLogUrl);
                    break;
                }
            }
        }
        System.out.println("总匹配数: " + matchCount);
    }
    
    private static String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        url = url.trim();
        
        if (!url.startsWith("/")) {
            return url;
        }
        
        int secondSlashIndex = url.indexOf('/', 1);
        if (secondSlashIndex > 0) {
            return url.substring(secondSlashIndex);
        }
        
        return url;
    }
}
