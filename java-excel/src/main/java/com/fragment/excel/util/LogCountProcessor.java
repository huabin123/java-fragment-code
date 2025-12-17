package com.fragment.excel.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fragment.excel.model.MethodData;
import com.fragment.excel.model.ResultData;
import com.fragment.excel.model.SysLogData;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志计数处理器
 * 用于读取sys_log.xlsx和method.csv，填充result.xlsx中的调用次数
 */
public class LogCountProcessor {

    private static final String RESOURCE_PATH = "/Users/huabin/workspace/playground/my-github/java-fragment-code/java-excel/src/main/resources/log_count";
    private static final String SYS_LOG_FILE = RESOURCE_PATH + "/sys_log.xlsx";
    private static final String METHOD_FILE = RESOURCE_PATH + "/method.csv";
    private static final String RESULT_FILE = RESOURCE_PATH + "/result.xlsx";

    /**
     * 处理日志计数
     */
    public void process() {
        // 1. 读取sys_log.xlsx，构建URL到计数的映射
        Map<String, Integer> sysLogMap = readSysLog();
        System.out.println("读取sys_log.xlsx完成，共 " + sysLogMap.size() + " 条记录");

        // 2. 读取method.csv，构建tranCode到计数的映射
        Map<String, Integer> methodMap = readMethodCsv();
        System.out.println("读取method.csv完成，共 " + methodMap.size() + " 条记录");

        // 3. 读取result.xlsx
        List<ResultData> resultList = readResult();
        System.out.println("读取result.xlsx完成，共 " + resultList.size() + " 条记录");

        // 4. 填充调用次数
        int matchedBySysLog = 0;
        int matchedByMethod = 0;
        int unmatched = 0;

        for (ResultData result : resultList) {
            String url = result.getUrl();
            if (url == null || url.trim().isEmpty()) {
                continue;
            }

            // 先尝试从sys_log中匹配
            Integer count = matchFromSysLog(url, sysLogMap);
            if (count != null) {
                result.setCallCount(count);
                matchedBySysLog++;
                continue;
            }

            // 如果sys_log中没有匹配，尝试从method.csv中匹配
            count = matchFromMethod(url, methodMap);
            if (count != null) {
                result.setCallCount(count);
                matchedByMethod++;
            } else {
                unmatched++;
                System.out.println("未匹配到URL: " + url);
            }
        }

        System.out.println("匹配统计：");
        System.out.println("  - 从sys_log匹配: " + matchedBySysLog);
        System.out.println("  - 从method.csv匹配: " + matchedByMethod);
        System.out.println("  - 未匹配: " + unmatched);

        // 5. 写入结果
        writeResult(resultList);
        System.out.println("结果已写入: " + RESULT_FILE);
    }

    /**
     * 读取sys_log.xlsx
     */
    private Map<String, Integer> readSysLog() {
        Map<String, Integer> map = new HashMap<>();

        EasyExcel.read(SYS_LOG_FILE, SysLogData.class, new AnalysisEventListener<SysLogData>() {
            @Override
            public void invoke(SysLogData data, AnalysisContext context) {
                if (data.getRequestUrl() != null && data.getCount() != null) {
                    // 去除第二个/之前的内容
                    String normalizedUrl = normalizeUrl(data.getRequestUrl());
                    map.put(normalizedUrl, data.getCount());
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 读取完成
            }
        }).sheet().doRead();

        return map;
    }

    /**
     * 读取method.csv
     */
    private Map<String, Integer> readMethodCsv() {
        Map<String, Integer> map = new HashMap<>();

        EasyExcel.read(METHOD_FILE, MethodData.class, new AnalysisEventListener<MethodData>() {
            @Override
            public void invoke(MethodData data, AnalysisContext context) {
                if (data.getTranCode() != null && data.getCount() != null) {
                    try {
                        // 去除逗号分隔符并转换为整数
                        String countStr = data.getCount().replace(",", "");
                        Integer count = Integer.parseInt(countStr);
                        map.put(data.getTranCode(), count);
                    } catch (NumberFormatException e) {
                        System.err.println("无法解析计数值: " + data.getCount() + " for tranCode: " + data.getTranCode());
                    }
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 读取完成
            }
        }).sheet().doRead();

        return map;
    }

    /**
     * 读取result.xlsx
     */
    private List<ResultData> readResult() {
        List<ResultData> list = new ArrayList<>();

        EasyExcel.read(RESULT_FILE, ResultData.class, new AnalysisEventListener<ResultData>() {
            @Override
            public void invoke(ResultData data, AnalysisContext context) {
                list.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 读取完成
            }
        }).sheet("Sheet1-产品已提供服务的接口清单").doRead();

        return list;
    }

    /**
     * 写入结果
     */
    private void writeResult(List<ResultData> resultList) {
        EasyExcel.write(RESULT_FILE, ResultData.class)
                .sheet("Sheet1-产品已提供服务的接口清单")
                .doWrite(resultList);
    }

    /**
     * 标准化URL：去除第二个/之前的内容
     * 例如：/bocwm/api/user -> /api/user
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        // 去除前后空格
        url = url.trim();

        // 如果不是以/开头，直接返回
        if (!url.startsWith("/")) {
            return url;
        }

        // 找到第二个/的位置
        int secondSlashIndex = url.indexOf('/', 1);
        if (secondSlashIndex > 0) {
            return url.substring(secondSlashIndex);
        }

        // 如果没有第二个/，返回原URL
        return url;
    }

    /**
     * 从sys_log中匹配URL
     */
    private Integer matchFromSysLog(String url, Map<String, Integer> sysLogMap) {
        // result中的URL不需要标准化，直接用原始URL匹配
        // 因为sys_log在读取时已经标准化过了
        return sysLogMap.get(url);
    }

    /**
     * 从method.csv中匹配URL
     * method.csv中只有URL的最后部分，需要与result URL的最后部分完全相等才匹配
     */
    private Integer matchFromMethod(String url, Map<String, Integer> methodMap) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        // 提取URL的最后部分（最后一个/之后的内容）
        String lastPart = url;
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < url.length() - 1) {
            lastPart = url.substring(lastSlashIndex + 1);
        }
        
        // 完全匹配
        return methodMap.get(lastPart);
    }
}
