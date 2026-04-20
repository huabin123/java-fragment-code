package com.fragment.excel;

import com.fragment.excel.util.ExcelFolderDiffTool;
import com.fragment.excel.util.WordFolderDiffTool;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一的文件夹比对启动类。
 *
 * <p>参数说明：
 * args[0] = 模式（excel/word）
 * args[1] = 左目录（必填）
 * args[2] = 右目录（必填）
 * args[3..n] = 可选参数
 *   --output=结果文件路径（默认 diff-result.txt）
 *   --max-errors-per-file=每个文件最多输出差异条数（默认 200）
 *   --max-errors-per-sheet=每个sheet/分组最多输出差异条数（默认 50）
 *
 * <p>示例：
 * java -jar app.jar excel /path/left /path/right --output=result.txt
 * java -jar app.jar word /path/left /path/right --max-errors-per-file=100
 */
public class FolderDiffMain {

    private static final String MODE_EXCEL = "excel";
    private static final String MODE_WORD = "word";
    private static final int DEFAULT_MAX_ERRORS_PER_FILE = 200;
    private static final int DEFAULT_MAX_ERRORS_PER_SHEET = 50;
    private static final String DEFAULT_OUTPUT_FILE = "diff-result.txt";

    public static void main(String[] args) {
        CliOptions options = parseCliOptions(args);

        try {
            Path outputPath = options.outputPath;
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                if (MODE_WORD.equals(options.mode)) {
                    WordFolderDiffTool tool = new WordFolderDiffTool();
                    WordFolderDiffTool.DiffSummary summary = tool.compareFolders(options.leftFolder, options.rightFolder);
                    tool.printSummary(summary, writer, new WordFolderDiffTool.PrintOptions(options.maxErrorsPerFile, options.maxErrorsPerSheet));
                } else {
                    ExcelFolderDiffTool tool = new ExcelFolderDiffTool();
                    ExcelFolderDiffTool.DiffSummary summary = tool.compareFolders(options.leftFolder, options.rightFolder);
                    tool.printSummary(summary, writer, new ExcelFolderDiffTool.PrintOptions(options.maxErrorsPerFile, options.maxErrorsPerSheet));
                }
            }

            System.out.println("Comparison completed, result file: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write result file: " + e.getMessage(), e);
        }
    }

    static void runExcel(String[] args) {
        String[] delegatedArgs = toModeArgs(MODE_EXCEL, args, "excel-result.txt");
        main(delegatedArgs);
    }

    static void runWord(String[] args) {
        String[] delegatedArgs = toModeArgs(MODE_WORD, args, "word-result.txt");
        main(delegatedArgs);
    }

    private static String[] toModeArgs(String mode, String[] args, String defaultOutputFile) {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException("Old entry point also requires arguments: <leftDir> <rightDir>");
        }

        if (args.length == 2) {
            return new String[]{mode, args[0], args[1], "--output=" + defaultOutputFile};
        }

        String[] delegatedArgs = new String[args.length + 1];
        delegatedArgs[0] = mode;
        System.arraycopy(args, 0, delegatedArgs, 1, args.length);
        return delegatedArgs;
    }

    private static CliOptions parseCliOptions(String[] args) {
        if (args == null || args.length < 3) {
            throw usageException();
        }

        String mode = args[0] == null ? "" : args[0].trim().toLowerCase();
        if (!MODE_EXCEL.equals(mode) && !MODE_WORD.equals(mode)) {
            throw usageException();
        }

        Path leftFolder = Paths.get(args[1]);
        Path rightFolder = Paths.get(args[2]);
        if (!Files.isDirectory(leftFolder) || !Files.isDirectory(rightFolder)) {
            throw new IllegalArgumentException("Both left and right directories must exist and be directories. left=" + leftFolder + ", right=" + rightFolder);
        }

        Path outputPath = Paths.get(DEFAULT_OUTPUT_FILE);
        int maxErrorsPerFile = DEFAULT_MAX_ERRORS_PER_FILE;
        int maxErrorsPerSheet = DEFAULT_MAX_ERRORS_PER_SHEET;

        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.trim().isEmpty()) {
                continue;
            }
            if (arg.startsWith("--output=")) {
                outputPath = Paths.get(arg.substring("--output=".length()));
                continue;
            }
            if (arg.startsWith("--max-errors-per-file=")) {
                maxErrorsPerFile = parsePositiveInt(arg, "--max-errors-per-file=");
                continue;
            }
            if (arg.startsWith("--max-errors-per-sheet=")) {
                maxErrorsPerSheet = parsePositiveInt(arg, "--max-errors-per-sheet=");
                continue;
            }
            throw new IllegalArgumentException("Unknown parameter: " + arg);
        }

        return new CliOptions(mode, leftFolder, rightFolder, outputPath, maxErrorsPerFile, maxErrorsPerSheet);
    }

    private static int parsePositiveInt(String arg, String prefix) {
        try {
            int value = Integer.parseInt(arg.substring(prefix.length()));
            if (value <= 0) {
                throw new IllegalArgumentException("Parameter must be a positive integer: " + arg);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter is not a valid integer: " + arg);
        }
    }

    private static IllegalArgumentException usageException() {
        String usage = "Usage: java -jar app.jar <excel|word> <leftDir> <rightDir> " +
                "[--output=result_txt] [--max-errors-per-file=N] [--max-errors-per-sheet=N]";
        return new IllegalArgumentException(usage);
    }

    private static class CliOptions {
        private final String mode;
        private final Path leftFolder;
        private final Path rightFolder;
        private final Path outputPath;
        private final int maxErrorsPerFile;
        private final int maxErrorsPerSheet;

        private CliOptions(
                String mode,
                Path leftFolder,
                Path rightFolder,
                Path outputPath,
                int maxErrorsPerFile,
                int maxErrorsPerSheet
        ) {
            this.mode = mode;
            this.leftFolder = leftFolder;
            this.rightFolder = rightFolder;
            this.outputPath = outputPath;
            this.maxErrorsPerFile = maxErrorsPerFile;
            this.maxErrorsPerSheet = maxErrorsPerSheet;
        }
    }
}
