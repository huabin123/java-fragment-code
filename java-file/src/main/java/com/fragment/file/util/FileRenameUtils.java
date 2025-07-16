package com.fragment.file.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @Author huabin
 * @DateTime 2024-12-24 14:08
 * @Desc 文件重命名
 */
public class FileRenameUtils {


    public static void main(String[] args) {
        String pattern = "(?<=第).*?(?=【)";
        String filePath = "/Volumes/T7/19薛兆丰的北大经济学课（完结）";
        String destPath = "/Users/huabin/workspace/薛兆丰的北大经济学课/";
        FileUtil.walkFiles(Paths.get(filePath), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                // 获取当前的文件名
                String fileName = filePath.getFileName().toString();

                if (StrUtil.startWith(fileName, ".") || !StrUtil.equals(FileUtil.extName(filePath.toString()), "mp3")) {
                    return FileVisitResult.CONTINUE;
                }

                // 使用正则表达式替换文件名中内容
                String matchedContent = ReUtil.get(pattern, fileName, 0);

                // 如果匹配内容不为空，进行重命名
                if (matchedContent != null && !matchedContent.isEmpty()) {
                    String newFileName = matchedContent + "." + FileUtil.extName(filePath.toString());
                    System.out.println(newFileName + "  <===  " + fileName);
                    FileUtil.copy(filePath.toString(), destPath + newFileName, true);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
