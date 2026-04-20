package com.fragment.excel.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Excel Folder Comparison Tool.
 *
 * <p>Functions:
 * 1. Read Excel files from two directories under resources
 * 2. Match files by name
 * 3. Compare matched files by sheet name
 * 4. Output difference details (sheet, row, column, left and right values)
 */
public class ExcelFolderDiffTool {

    private static final Set<String> EXCEL_EXTENSIONS = new HashSet<>(Arrays.asList(".xlsx", ".xls"));

    private final DataFormatter dataFormatter = new DataFormatter();

    public DiffSummary compareFolders(Path leftFolder, Path rightFolder) {
        if (!Files.isDirectory(leftFolder)) {
            throw new IllegalArgumentException("Left directory does not exist or is not a directory: " + leftFolder);
        }
        if (!Files.isDirectory(rightFolder)) {
            throw new IllegalArgumentException("Right directory does not exist or is not a directory: " + rightFolder);
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
            throw new RuntimeException("Failed to read directory: " + folder, e);
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
            result.errorMessage = "File comparison failed: " + e.getMessage();
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
     * Compare sheets in the order of the left workbook (to meet the requirement of comparing by sheet name sequentially).
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
        PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        printSummary(summary, printWriter, PrintOptions.defaultOptions());
        printWriter.flush();
    }

    public void printSummary(DiffSummary summary, Appendable out, PrintOptions options) {
        writeLine(out, "================ Excel Comparison Result ================");
        writeLine(out, "Left Directory: " + summary.leftFolder);
        writeLine(out, "Right Directory: " + summary.rightFolder);
        writeLine(out, "");

        if (!summary.leftOnlyFiles.isEmpty()) {
            writeLine(out, "Files only in left directory:");
            for (String name : summary.leftOnlyFiles) {
                writeLine(out, "  - " + name);
            }
            writeLine(out, "");
        }

        if (!summary.rightOnlyFiles.isEmpty()) {
            writeLine(out, "Files only in right directory:");
            for (String name : summary.rightOnlyFiles) {
                writeLine(out, "  - " + name);
            }
            writeLine(out, "");
        }

        int totalPrintedDiffs = 0;
        int totalRawDiffs = 0;

        for (FileDiffResult fileResult : summary.fileResults) {
            writeLine(out, "File: " + fileResult.fileName);

            if (fileResult.errorMessage != null) {
                writeLine(out, "  [ERROR] " + fileResult.errorMessage);
                continue;
            }

            if (!fileResult.leftOnlySheets.isEmpty()) {
                writeLine(out, "  Sheets only in left: " + fileResult.leftOnlySheets);
            }
            if (!fileResult.rightOnlySheets.isEmpty()) {
                writeLine(out, "  Sheets only in right: " + fileResult.rightOnlySheets);
            }

            if (fileResult.cellDiffs.isEmpty()) {
                writeLine(out, "  No differences");
                writeLine(out, "");
                continue;
            }

            totalRawDiffs += fileResult.cellDiffs.size();
            writeLine(out, "  Total differences: " + fileResult.cellDiffs.size());

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
                    writeLine(out, "    [LIMIT] sheet=" + entry.getKey() + " additional differences omitted: " + entry.getValue());
                }
            }
            if (omittedByFileLimit > 0) {
                writeLine(out, "    [LIMIT] file-level additional differences omitted: " + omittedByFileLimit);
            }
            writeLine(out, "");
        }

        writeLine(out, "================ Statistics ================");
        writeLine(out, "Matched files: " + summary.fileResults.size());
        writeLine(out, "Left-only files: " + summary.leftOnlyFiles.size());
        writeLine(out, "Right-only files: " + summary.rightOnlyFiles.size());
        writeLine(out, "Total cell differences (raw): " + totalRawDiffs);
        writeLine(out, "Total cell differences (output): " + totalPrintedDiffs);
    }

    private void writeLine(Appendable out, String text) {
        try {
            out.append(text).append(System.lineSeparator());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write comparison result", e);
        }
    }

    public static class PrintOptions {
        private final int maxErrorsPerFile;
        private final int maxErrorsPerSheet;

        public PrintOptions(int maxErrorsPerFile, int maxErrorsPerSheet) {
            if (maxErrorsPerFile <= 0 || maxErrorsPerSheet <= 0) {
                throw new IllegalArgumentException("maxErrorsPerFile and maxErrorsPerSheet must be greater than 0");
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
        private final Path leftFile;
        private final Path rightFile;
        private String errorMessage;

        private final List<String> leftOnlySheets = new ArrayList<>();
        private final List<String> rightOnlySheets = new ArrayList<>();
        private final List<CellDiff> cellDiffs = new ArrayList<>();

        public FileDiffResult(String fileName, Path leftFile, Path rightFile) {
            this.fileName = fileName;
            this.leftFile = leftFile;
            this.rightFile = rightFile;
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
