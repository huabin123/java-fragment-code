package com.fragment.excel.util;

import java.io.IOException;
import java.io.PrintWriter;
import org.apache.poi.ss.usermodel.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Excel 文件夹比对工具。
 *
 * <p>功能：
 * 1. 读取 resources 下两个目录中的 Excel 文件
 * 2. 先按文件名匹配
 * 3. 对匹配上的文件按 sheet 名进行对比
 * 4. 输出差异明细（sheet、行、列、左右值）
 */
public class ExcelFolderDiffTool {

    private static final Set<String> EXCEL_EXTENSIONS = new HashSet<>(Arrays.asList(".xlsx", ".xls"));

    private final DataFormatter dataFormatter = new DataFormatter();

    public DiffSummary compareFolders(Path leftFolder, Path rightFolder) {
        if (!Files.isDirectory(leftFolder)) {
            throw new IllegalArgumentException("左侧目录不存在或不是目录: " + leftFolder);
        }
        if (!Files.isDirectory(rightFolder)) {
            throw new IllegalArgumentException("右侧目录不存在或不是目录: " + rightFolder);
        }

        Map<String, Path> leftFiles = listExcelFiles(leftFolder);
        Map<String, Path> rightFiles = listExcelFiles(rightFolder);

        DiffSummary summary = new DiffSummary(leftFolder, rightFolder);

        Set<String> leftOnlyFiles = new TreeSet<>(leftFiles.keySet());
        leftOnlyFiles.removeAll(rightFiles.keySet());
        summary.leftOnlyFiles.addAll(leftOnlyFiles);

        Set<String> rightOnlyFiles = new TreeSet<>(rightFiles.keySet());
        rightOnlyFiles.removeAll(leftFiles.keySet());
        summary.rightOnlyFiles.addAll(rightOnlyFiles);

        Set<String> matchedFiles = new TreeSet<>(leftFiles.keySet());
        matchedFiles.retainAll(rightFiles.keySet());

        for (String fileName : matchedFiles) {
            Path leftFile = leftFiles.get(fileName);
            Path rightFile = rightFiles.get(fileName);
            FileDiffResult fileResult = compareFile(fileName, leftFile, rightFile);
            summary.fileResults.add(fileResult);
        }

        return summary;
    }

    private Map<String, Path> listExcelFiles(Path folder) {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isExcelFile)
                    .collect(Collectors.toMap(
                            path -> path.getFileName().toString(),
                            path -> path,
                            (a, b) -> a,
                            TreeMap::new
                    ));
        } catch (IOException e) {
            throw new RuntimeException("读取目录失败: " + folder, e);
        }
    }

    private boolean isExcelFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : EXCEL_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private FileDiffResult compareFile(String fileName, Path leftFile, Path rightFile) {
        FileDiffResult result = new FileDiffResult(fileName, leftFile, rightFile);

        try (Workbook leftWorkbook = WorkbookFactory.create(leftFile.toFile());
             Workbook rightWorkbook = WorkbookFactory.create(rightFile.toFile())) {

            Map<String, Sheet> leftSheets = getSheetsByName(leftWorkbook);
            Map<String, Sheet> rightSheets = getSheetsByName(rightWorkbook);

            Set<String> leftOnlySheets = new TreeSet<>(leftSheets.keySet());
            leftOnlySheets.removeAll(rightSheets.keySet());
            result.leftOnlySheets.addAll(leftOnlySheets);

            Set<String> rightOnlySheets = new TreeSet<>(rightSheets.keySet());
            rightOnlySheets.removeAll(leftSheets.keySet());
            result.rightOnlySheets.addAll(rightOnlySheets);

            List<String> orderedSheetNames = getOrderedMatchedSheetNames(leftWorkbook, rightSheets);
            for (String sheetName : orderedSheetNames) {
                Sheet leftSheet = leftSheets.get(sheetName);
                Sheet rightSheet = rightSheets.get(sheetName);
                compareSheet(sheetName, leftSheet, rightSheet, result);
            }

        } catch (IOException e) {
            result.errorMessage = "文件比对失败: " + e.getMessage();
        }

        return result;
    }

    private Map<String, Sheet> getSheetsByName(Workbook workbook) {
        Map<String, Sheet> map = new LinkedHashMap<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            map.put(sheet.getSheetName(), sheet);
        }
        return map;
    }

    /**
     * 按左侧工作簿的 sheet 顺序进行比对（满足“依次按 sheet 名比对”）。
     */
    private List<String> getOrderedMatchedSheetNames(Workbook leftWorkbook, Map<String, Sheet> rightSheets) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < leftWorkbook.getNumberOfSheets(); i++) {
            String sheetName = leftWorkbook.getSheetName(i);
            if (rightSheets.containsKey(sheetName)) {
                names.add(sheetName);
            }
        }
        return names;
    }

    private void compareSheet(String sheetName, Sheet leftSheet, Sheet rightSheet, FileDiffResult fileResult) {
        int maxRow = Math.max(leftSheet.getLastRowNum(), rightSheet.getLastRowNum());

        for (int rowIdx = 0; rowIdx <= maxRow; rowIdx++) {
            Row leftRow = leftSheet.getRow(rowIdx);
            Row rightRow = rightSheet.getRow(rowIdx);

            int maxCell = Math.max(getLastCellNum(leftRow), getLastCellNum(rightRow));
            for (int cellIdx = 0; cellIdx < maxCell; cellIdx++) {
                String leftValue = getCellValue(leftRow, cellIdx);
                String rightValue = getCellValue(rightRow, cellIdx);
                if (!Objects.equals(leftValue, rightValue)) {
                    fileResult.cellDiffs.add(new CellDiff(sheetName, rowIdx + 1, cellIdx + 1, leftValue, rightValue));
                }
            }
        }
    }

    private int getLastCellNum(Row row) {
        if (row == null || row.getLastCellNum() < 0) {
            return 0;
        }
        return row.getLastCellNum();
    }

    private String getCellValue(Row row, int cellIdx) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(cellIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell).trim();
    }

    public void printSummary(DiffSummary summary) {
        PrintWriter printWriter = new PrintWriter(System.out, true);
        printSummary(summary, printWriter, PrintOptions.defaultOptions());
        printWriter.flush();
    }

    public void printSummary(DiffSummary summary, Appendable out, PrintOptions options) {
        writeLine(out, "================ Excel 比对结果 ================");
        writeLine(out, "左侧目录: " + summary.leftFolder);
        writeLine(out, "右侧目录: " + summary.rightFolder);
        writeLine(out, "");

        if (!summary.leftOnlyFiles.isEmpty()) {
            writeLine(out, "仅左侧存在的文件:");
            for (String name : summary.leftOnlyFiles) {
                writeLine(out, "  - " + name);
            }
            writeLine(out, "");
        }

        if (!summary.rightOnlyFiles.isEmpty()) {
            writeLine(out, "仅右侧存在的文件:");
            for (String name : summary.rightOnlyFiles) {
                writeLine(out, "  - " + name);
            }
            writeLine(out, "");
        }

        int totalPrintedDiffs = 0;
        int totalRawDiffs = 0;

        for (FileDiffResult fileResult : summary.fileResults) {
            writeLine(out, "文件: " + fileResult.fileName);

            if (fileResult.errorMessage != null) {
                writeLine(out, "  [ERROR] " + fileResult.errorMessage);
                continue;
            }

            if (!fileResult.leftOnlySheets.isEmpty()) {
                writeLine(out, "  仅左侧存在的 sheet: " + fileResult.leftOnlySheets);
            }
            if (!fileResult.rightOnlySheets.isEmpty()) {
                writeLine(out, "  仅右侧存在的 sheet: " + fileResult.rightOnlySheets);
            }

            if (fileResult.cellDiffs.isEmpty()) {
                writeLine(out, "  无差异");
                writeLine(out, "");
                continue;
            }

            totalRawDiffs += fileResult.cellDiffs.size();
            writeLine(out, "  总差异数: " + fileResult.cellDiffs.size());

            int printedInFile = 0;
            int omittedByFileLimit = 0;
            Map<String, Integer> printedPerSheet = new HashMap<>();
            Map<String, Integer> omittedPerSheet = new TreeMap<>();

            for (CellDiff diff : fileResult.cellDiffs) {
                if (printedInFile >= options.maxErrorsPerFile) {
                    omittedByFileLimit++;
                    continue;
                }

                int printedInSheet = printedPerSheet.getOrDefault(diff.sheetName, 0);
                if (printedInSheet >= options.maxErrorsPerSheet) {
                    omittedPerSheet.put(diff.sheetName, omittedPerSheet.getOrDefault(diff.sheetName, 0) + 1);
                    continue;
                }

                writeLine(out, String.format("    - sheet=%s row=%d col=%d | left=[%s] right=[%s]",
                        diff.sheetName,
                        diff.row,
                        diff.col,
                        diff.leftValue,
                        diff.rightValue));

                printedInFile++;
                printedPerSheet.put(diff.sheetName, printedInSheet + 1);
            }

            totalPrintedDiffs += printedInFile;

            if (!omittedPerSheet.isEmpty()) {
                for (Map.Entry<String, Integer> entry : omittedPerSheet.entrySet()) {
                    writeLine(out, "    [LIMIT] sheet=" + entry.getKey() + " 额外差异已省略: " + entry.getValue());
                }
            }
            if (omittedByFileLimit > 0) {
                writeLine(out, "    [LIMIT] 文件级别额外差异已省略: " + omittedByFileLimit);
            }
            writeLine(out, "");
        }

        writeLine(out, "================ 统计 ================");
        writeLine(out, "匹配文件数: " + summary.fileResults.size());
        writeLine(out, "仅左文件数: " + summary.leftOnlyFiles.size());
        writeLine(out, "仅右文件数: " + summary.rightOnlyFiles.size());
        writeLine(out, "总差异单元格数(原始): " + totalRawDiffs);
        writeLine(out, "总差异单元格数(输出): " + totalPrintedDiffs);
    }

    private void writeLine(Appendable out, String text) {
        try {
            out.append(text).append(System.lineSeparator());
        } catch (IOException e) {
            throw new RuntimeException("写入比对结果失败", e);
        }
    }

    public static class PrintOptions {
        private final int maxErrorsPerFile;
        private final int maxErrorsPerSheet;

        public PrintOptions(int maxErrorsPerFile, int maxErrorsPerSheet) {
            if (maxErrorsPerFile <= 0 || maxErrorsPerSheet <= 0) {
                throw new IllegalArgumentException("maxErrorsPerFile 和 maxErrorsPerSheet 必须大于0");
            }
            this.maxErrorsPerFile = maxErrorsPerFile;
            this.maxErrorsPerSheet = maxErrorsPerSheet;
        }

        public static PrintOptions defaultOptions() {
            return new PrintOptions(200, 50);
        }
    }

    public static class DiffSummary {
        private final Path leftFolder;
        private final Path rightFolder;

        private final List<String> leftOnlyFiles = new ArrayList<>();
        private final List<String> rightOnlyFiles = new ArrayList<>();
        private final List<FileDiffResult> fileResults = new ArrayList<>();

        public DiffSummary(Path leftFolder, Path rightFolder) {
            this.leftFolder = leftFolder;
            this.rightFolder = rightFolder;
        }
    }

    public static class FileDiffResult {
        private final String fileName;
        private final Path leftPath;
        private final Path rightPath;

        private String errorMessage;
        private final List<String> leftOnlySheets = new ArrayList<>();
        private final List<String> rightOnlySheets = new ArrayList<>();
        private final List<CellDiff> cellDiffs = new ArrayList<>();

        public FileDiffResult(String fileName, Path leftPath, Path rightPath) {
            this.fileName = fileName;
            this.leftPath = leftPath;
            this.rightPath = rightPath;
        }
    }

    public static class CellDiff {
        private final String sheetName;
        private final int row;
        private final int col;
        private final String leftValue;
        private final String rightValue;

        public CellDiff(String sheetName, int row, int col, String leftValue, String rightValue) {
            this.sheetName = sheetName;
            this.row = row;
            this.col = col;
            this.leftValue = leftValue;
            this.rightValue = rightValue;
        }
    }
}
