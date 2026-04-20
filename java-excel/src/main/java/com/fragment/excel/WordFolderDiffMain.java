package com.fragment.excel;

/**
 * Word 文件夹比对启动类。
 *
 * <p>发布版需要通过参数指定：
 * args[0] = 左目录
 * args[1] = 右目录
 * args[2..n] = 可选参数（如 --output, --max-errors-per-file, --max-errors-per-sheet）
 */
public class WordFolderDiffMain {

    public static void main(String[] args) {
        FolderDiffMain.runWord(args);
    }
}
