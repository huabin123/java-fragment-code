package com.fragment.excel;

import cn.hutool.poi.excel.BigExcelWriter;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.style.StyleUtil;
import org.apache.poi.ss.usermodel.*;

import java.util.*;

/**
 * @Author huabin
 * @DateTime 2025-06-25 14:14
 * @Desc
 */
public class ExportVerifyErrorFile {

    public static void main(String[] args) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("colA", "xxx");
        map.put("colB", "列C,列E");
        map.put("colC", "1");
        map.put("colD", "2");
        map.put("colE", "3");
        List<LinkedHashMap<String, Object>> list = new ArrayList<>();
        list.add(map);

        Map<String, String> headerMap = new LinkedHashMap<>();
        headerMap.put("colA", "列A");
        headerMap.put("colB", "列B");
        headerMap.put("colC", "列C");
        headerMap.put("colD", "列D");
        headerMap.put("colE", "列E");

        List<LinkedHashMap<String, Object>> cnList = new ArrayList<>();
        for (LinkedHashMap<String, Object> map1 : list) {
            LinkedHashMap<String, Object> newMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map1.entrySet()) {
                newMap.put(headerMap.get(entry.getKey()), entry.getValue());
            }
            cnList.add(newMap);
        }


        String filePath = "/Users/huabin/workspace/playground/my-github/java-fragment-code/java-excel/src/main/resources/text.xlsx";
        BigExcelWriter writer = ExcelUtil.getBigWriter(filePath);
        writer.writeHeadRow(headerMap.values());

        // 创建红色字体样式
        Workbook workbook = writer.getWorkbook();
        CellStyle redStyle = workbook.createCellStyle();
        Font redFont = workbook.createFont();
        redFont.setColor(IndexedColors.RED.getIndex());
        redStyle.setFont(redFont);

        // 逐行写入数据并设置样式
        for (LinkedHashMap<String, Object> rowData : cnList) {
            // 1. 获取需要标红的列名（从"列B"的值中解析）
            String errorColumnsStr = (String) rowData.get("列B");
            Set<String> errorColumns = new HashSet<>(Arrays.asList(errorColumnsStr.split(",")));

            // 2. 按表头顺序获取当前行数据
            List<Object> rowValues = new ArrayList<>();
            for (String header : headerMap.values()) {
                rowValues.add(rowData.get(header));
            }

            // 3. 写入行数据
            int rowIndex = writer.getCurrentRow(); // 下一行索引
            writer.writeRow(rowValues, false);

            // 4. 遍历单元格设置红色样式
            for (int i = 0; i < rowValues.size(); i++) {
                String currentHeader = new ArrayList<>(headerMap.values()).get(i);
                if (errorColumns.contains(currentHeader)) {
                    Cell cell = writer.getCell(i, rowIndex);
                    CellStyle existingStyle = cell.getCellStyle();
                    CellStyle newStyle = workbook.createCellStyle();
                    newStyle.cloneStyleFrom(existingStyle); // 复制原有样式
                    newStyle.setFont(redFont);
                    cell.setCellStyle(newStyle);
                }
            }
        }

        writer.close();
    }

}
