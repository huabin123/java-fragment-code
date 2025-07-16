package com.fragment.file.util;

import org.apache.tika.Tika;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @Author huabin
 * @DateTime 2025-03-27 15:38
 * @Desc
 */
public class ExcelChecker {

    public static void main(String[] args) throws IOException {
        Tika tika = new Tika();
        Parser parser = new OOXMLParser(); // 直接实例化XLSX解析器

        String path = "/Users/huabin/workspace/interview/huawei/考试题型最新.xlsx";

        // 示例：检测XLSX文件
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(Paths.get(path)))) {
            String mimeType = tika.detect(stream);
            System.out.println(mimeType); // 正确输出 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
        }
//        System.out.println(mimeType.equals("application/vnd.ms-excel") ||  // .xls
//                mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));  // .xlsx
    }
}
