package com.fragment.excel.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Word Folder Comparison Tool (.docx).
 *
 * <p>Functions:
 * 1. Read Word files from two directories under resources
 * 2. Match files by name
 * 3. Compare matched files for paragraphs and tables
 * 4. Output difference details (paragraph index, table row/column position, left and right values)
 */
public class WordFolderDiffTool {

    private static final Set<String> WORD_EXTENSIONS = new TreeSet<>(Arrays.asList(".docx"));

    public DiffSummary compareFolders(Path leftFolder, Path rightFolder) {
        if (!Files.isDirectory(leftFolder)) {
            throw new IllegalArgumentException("Left directory does not exist or is not a directory: " + leftFolder);
        }
        if (!Files.isDirectory(rightFolder)) {
            throw new IllegalArgumentException("Right directory does not exist or is not a directory: " + rightFolder);
        }

        Map<String, Path> leftFiles = listWordFiles(leftFolder);
        Map<String, Path> rightFiles = listWordFiles(rightFolder);

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
            FileDiffResult fileResult = compareFile(fileName, leftFiles.get(fileName), rightFiles.get(fileName));
            summary.fileResults.add(fileResult);
        }

        return summary;
    }

    private Map<String, Path> listWordFiles(Path folder) {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isWordFile)
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

    private boolean isWordFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : WORD_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private FileDiffResult compareFile(String fileName, Path leftFile, Path rightFile) {
        FileDiffResult result = new FileDiffResult(fileName, leftFile, rightFile);

        try (InputStream leftInput = Files.newInputStream(leftFile);
             InputStream rightInput = Files.newInputStream(rightFile);
             XWPFDocument leftDoc = new XWPFDocument(leftInput);
             XWPFDocument rightDoc = new XWPFDocument(rightInput)) {

            compareParagraphs(leftDoc, rightDoc, result);
            compareTables(leftDoc, rightDoc, result);

        } catch (IOException e) {
            result.errorMessage = "File comparison failed: " + e.getMessage();
        }

        return result;
    }

    private void compareParagraphs(XWPFDocument leftDoc, XWPFDocument rightDoc, FileDiffResult result) {
        List<String> leftParagraphs = extractParagraphTexts(leftDoc);
        List<String> rightParagraphs = extractParagraphTexts(rightDoc);

        int maxParagraphCount = Math.max(leftParagraphs.size(), rightParagraphs.size());
        for (int i = 0; i < maxParagraphCount; i++) {
            String leftText = i < leftParagraphs.size() ? leftParagraphs.get(i) : "";
            String rightText = i < rightParagraphs.size() ? rightParagraphs.get(i) : "";
            if (!Objects.equals(leftText, rightText)) {
                result.paragraphDiffs.add(new ParagraphDiff(i + 1, leftText, rightText));
            }
        }
    }

    private List<String> extractParagraphTexts(XWPFDocument doc) {
        List<String> texts = new ArrayList<>();
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            texts.add(normalizeText(paragraph.getText()));
        }
        return texts;
    }

    private void compareTables(XWPFDocument leftDoc, XWPFDocument rightDoc, FileDiffResult result) {
        List<XWPFTable> leftTables = leftDoc.getTables();
        List<XWPFTable> rightTables = rightDoc.getTables();

        int maxTableCount = Math.max(leftTables.size(), rightTables.size());
        for (int tableIndex = 0; tableIndex < maxTableCount; tableIndex++) {
            XWPFTable leftTable = tableIndex < leftTables.size() ? leftTables.get(tableIndex) : null;
            XWPFTable rightTable = tableIndex < rightTables.size() ? rightTables.get(tableIndex) : null;
            compareSingleTable(tableIndex + 1, leftTable, rightTable, result);
        }
    }

    private void compareSingleTable(int tableNo, XWPFTable leftTable, XWPFTable rightTable, FileDiffResult result) {
        int maxRows = Math.max(getRowCount(leftTable), getRowCount(rightTable));
        for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
            XWPFTableRow leftRow = getRow(leftTable, rowIndex);
            XWPFTableRow rightRow = getRow(rightTable, rowIndex);

            int maxCells = Math.max(getCellCount(leftRow), getCellCount(rightRow));
            for (int cellIndex = 0; cellIndex < maxCells; cellIndex++) {
                String leftText = getCellText(leftRow, cellIndex);
                String rightText = getCellText(rightRow, cellIndex);
                if (!Objects.equals(leftText, rightText)) {
                    result.tableCellDiffs.add(new TableCellDiff(tableNo, rowIndex + 1, cellIndex + 1, leftText, rightText));
                }
            }
        }
    }

    private int getRowCount(XWPFTable table) {
        return table == null ? 0 : table.getRows().size();
    }

    private XWPFTableRow getRow(XWPFTable table, int rowIndex) {
        if (table == null || rowIndex < 0 || rowIndex >= table.getRows().size()) {
            return null;
        }
        return table.getRow(rowIndex);
    }

    private int getCellCount(XWPFTableRow row) {
        return row == null ? 0 : row.getTableCells().size();
    }

    private String getCellText(XWPFTableRow row, int cellIndex) {
        if (row == null || cellIndex < 0 || cellIndex >= row.getTableCells().size()) {
            return "";
        }
        XWPFTableCell cell = row.getCell(cellIndex);
        if (cell == null) {
            return "";
        }
        return normalizeText(cell.getText());
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim().replace("\u00A0", " ");
    }

    public void printSummary(DiffSummary summary) {
        PrintWriter printWriter = new PrintWriter(System.out, true);
        printSummary(summary, printWriter, PrintOptions.defaultOptions());
        printWriter.flush();
    }

    public void printSummary(DiffSummary summary, Appendable out, PrintOptions options) {
        writeLine(out, "================ Word Comparison Result ================");
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

        int totalRawDiffs = 0;
        int totalPrintedDiffs = 0;

        for (FileDiffResult fileResult : summary.fileResults) {
            writeLine(out, "File: " + fileResult.fileName);
            if (fileResult.errorMessage != null) {
                writeLine(out, "  [ERROR] " + fileResult.errorMessage);
                continue;
            }

            int fileRawDiffs = fileResult.paragraphDiffs.size() + fileResult.tableCellDiffs.size();
            if (fileRawDiffs == 0) {
                writeLine(out, "  No differences");
                writeLine(out, "");
                continue;
            }

            totalRawDiffs += fileRawDiffs;
            writeLine(out, "  Total differences: " + fileRawDiffs);

            int printedInFile = 0;
            int omittedByFileLimit = 0;
            Map<String, Integer> printedPerGroup = new HashMap<>();
            Map<String, Integer> omittedPerGroup = new TreeMap<>();

            for (ParagraphDiff diff : fileResult.paragraphDiffs) {
                String group = "Paragraphs";
                if (printedInFile >= options.maxErrorsPerFile) {
                    omittedByFileLimit++;
                    continue;
                }
                int printedInGroup = printedPerGroup.getOrDefault(group, 0);
                if (printedInGroup >= options.maxErrorsPerSheet) {
                    omittedPerGroup.put(group, omittedPerGroup.getOrDefault(group, 0) + 1);
                    continue;
                }

                writeLine(out, String.format("    - group=%s Paragraph#%d | left=[%s] right=[%s]",
                        group, diff.paragraphNo, diff.leftText, diff.rightText));
                printedInFile++;
                printedPerGroup.put(group, printedInGroup + 1);
            }

            for (TableCellDiff diff : fileResult.tableCellDiffs) {
                String group = "Table" + diff.tableNo;
                if (printedInFile >= options.maxErrorsPerFile) {
                    omittedByFileLimit++;
                    continue;
                }
                int printedInGroup = printedPerGroup.getOrDefault(group, 0);
                if (printedInGroup >= options.maxErrorsPerSheet) {
                    omittedPerGroup.put(group, omittedPerGroup.getOrDefault(group, 0) + 1);
                    continue;
                }

                writeLine(out, String.format("    - group=%s Row=%d Col=%d | left=[%s] right=[%s]",
                        group, diff.row, diff.col, diff.leftText, diff.rightText));
                printedInFile++;
                printedPerGroup.put(group, printedInGroup + 1);
            }

            totalPrintedDiffs += printedInFile;

            if (!omittedPerGroup.isEmpty()) {
                for (Map.Entry<String, Integer> entry : omittedPerGroup.entrySet()) {
                    writeLine(out, "    [LIMIT] group=" + entry.getKey() + " additional differences omitted: " + entry.getValue());
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
        writeLine(out, "Total differences (raw): " + totalRawDiffs);
        writeLine(out, "Total differences (output): " + totalPrintedDiffs);
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
        private final List<ParagraphDiff> paragraphDiffs = new ArrayList<>();
        private final List<TableCellDiff> tableCellDiffs = new ArrayList<>();

        public FileDiffResult(String fileName, Path leftFile, Path rightFile) {
            this.fileName = fileName;
            this.leftFile = leftFile;
            this.rightFile = rightFile;
        }
    }

    public static class ParagraphDiff {
        private final int paragraphNo;
        private final String leftText;
        private final String rightText;

        public ParagraphDiff(int paragraphNo, String leftText, String rightText) {
            this.paragraphNo = paragraphNo;
            this.leftText = leftText;
            this.rightText = rightText;
        }
    }

    public static class TableCellDiff {
        private final int tableNo;
        private final int row;
        private final int col;
        private final String leftText;
        private final String rightText;

        public TableCellDiff(int tableNo, int row, int col, String leftText, String rightText) {
            this.tableNo = tableNo;
            this.row = row;
            this.col = col;
            this.leftText = leftText;
            this.rightText = rightText;
        }
    }
}
