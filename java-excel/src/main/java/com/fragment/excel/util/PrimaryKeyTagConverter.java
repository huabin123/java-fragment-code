package com.fragment.excel.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 主键标签转换工具。
 *
 * <p>业务背景：存在两个主键 A 和 B，且关系为 A:B = 1:N（一个 A 可对应多个 B，每个 B 只属于唯一一个 A）。
 * 用户上传的 Excel 数据格式为 List&lt;List&lt;String&gt;&gt;：
 * <pre>
 *   表头：[主键, 标签1, 标签2, ...]
 *   数据行：[主键值, 标签值1, 标签值2, ...]
 * </pre>
 *
 * <p>本工具按映射关系将数据从一个主键转换为另一个主键：
 * <ul>
 *   <li>A → B（拆行）：一个 A 对应多个 B 时，复制标签值拆成多行。</li>
 *   <li>B → A（合并）：多个 B 落到同一个 A 时，按列将标签值用逗号拼接合并为一行。</li>
 * </ul>
 */
public class PrimaryKeyTagConverter {

    /** A、B 主键的映射关系 VO。 */
    public static class Mapping {
        private String a;
        private String b;

        public Mapping() {
        }

        public Mapping(String a, String b) {
            this.a = a;
            this.b = b;
        }

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }
    }

    /** 转换方向。 */
    public enum Direction {
        /** 输入表头第 0 列是 A，转换为 B（拆行）。 */
        A_TO_B,
        /** 输入表头第 0 列是 B，转换为 A（合并）。 */
        B_TO_A
    }

    /** 转换选项。 */
    public static class Options {
        /**
         * 合并时（B→A）是否对每列标签值去重。默认 true：只要具体标签值集合相同就视为一致，避免出现重复值。
         */
        private boolean dedupOnMerge = true;
        /**
         * 合并时（B→A）是否对每列标签值排序。默认 true：保证语义等价（集合相同）的结果稳定一致，
         * 不受原行顺序影响；使用字符串自然序。
         */
        private boolean sortOnMerge = true;
        /** 合并/拼接使用的分隔符。 */
        private String joinSeparator = ",";
        /** 新主键列的表头名称；为空时复用原表头第 0 列名称。 */
        private String newKeyHeader;
        /** 未匹配到映射的数据行是否保留（保留时主键列置为空字符串）。默认 false，直接丢弃。 */
        private boolean keepUnmapped = false;

        public boolean isDedupOnMerge() {
            return dedupOnMerge;
        }

        public Options setDedupOnMerge(boolean dedupOnMerge) {
            this.dedupOnMerge = dedupOnMerge;
            return this;
        }

        public boolean isSortOnMerge() {
            return sortOnMerge;
        }

        public Options setSortOnMerge(boolean sortOnMerge) {
            this.sortOnMerge = sortOnMerge;
            return this;
        }

        public String getJoinSeparator() {
            return joinSeparator;
        }

        public Options setJoinSeparator(String joinSeparator) {
            this.joinSeparator = joinSeparator;
            return this;
        }

        public String getNewKeyHeader() {
            return newKeyHeader;
        }

        public Options setNewKeyHeader(String newKeyHeader) {
            this.newKeyHeader = newKeyHeader;
            return this;
        }

        public boolean isKeepUnmapped() {
            return keepUnmapped;
        }

        public Options setKeepUnmapped(boolean keepUnmapped) {
            this.keepUnmapped = keepUnmapped;
            return this;
        }
    }

    /**
     * 使用默认选项进行转换。
     */
    public static List<List<String>> convert(List<List<String>> rows,
                                             List<Mapping> mappings,
                                             Direction direction) {
        return convert(rows, mappings, direction, new Options());
    }

    /**
     * 按映射关系把 Excel 数据从一个主键转换为另一个主键。
     *
     * @param rows       Excel 原始数据，第 0 行为表头，第 0 列为主键列
     * @param mappings   A-B 映射关系（A:B = 1:N，B 唯一）
     * @param direction  转换方向
     * @param options    转换选项，可为 null
     * @return 转换后的数据，第 0 行表头，第 0 列为新主键
     */
    public static List<List<String>> convert(List<List<String>> rows,
                                             List<Mapping> mappings,
                                             Direction direction,
                                             Options options) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(mappings, "mappings");
        Objects.requireNonNull(direction, "direction");
        if (options == null) {
            options = new Options();
        }
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> header = rows.get(0);
        List<List<String>> dataRows = rows.subList(1, rows.size());
        int tagColumnCount = Math.max(0, header.size() - 1);

        // 构建新表头
        List<String> newHeader = new ArrayList<>(header.size());
        newHeader.add(options.getNewKeyHeader() != null && !options.getNewKeyHeader().isEmpty()
                ? options.getNewKeyHeader()
                : header.get(0));
        for (int i = 1; i < header.size(); i++) {
            newHeader.add(header.get(i));
        }

        List<List<String>> result = new ArrayList<>();
        result.add(newHeader);

        if (direction == Direction.A_TO_B) {
            // A -> 多个 B：拆行
            Map<String, List<String>> aToBs = new LinkedHashMap<>();
            for (Mapping m : mappings) {
                if (m == null || m.getA() == null) {
                    continue;
                }
                aToBs.computeIfAbsent(m.getA(), k -> new ArrayList<>()).add(m.getB());
            }
            for (List<String> row : dataRows) {
                if (row == null || row.isEmpty()) {
                    continue;
                }
                String aKey = row.get(0);
                List<String> bs = aToBs.get(aKey);
                if (bs == null || bs.isEmpty()) {
                    if (options.isKeepUnmapped()) {
                        result.add(buildRow("", row, tagColumnCount));
                    }
                    continue;
                }
                for (String b : bs) {
                    result.add(buildRow(b == null ? "" : b, row, tagColumnCount));
                }
            }
        } else {
            // 多个 B -> 同一个 A：合并（B 唯一）
            Map<String, String> bToA = new LinkedHashMap<>();
            for (Mapping m : mappings) {
                if (m == null || m.getB() == null) {
                    continue;
                }
                bToA.put(m.getB(), m.getA());
            }
            // aKey -> 每列的有序值集合
            Map<String, List<List<String>>> grouped = new LinkedHashMap<>();
            List<List<String>> unmappedRows = new ArrayList<>();
            for (List<String> row : dataRows) {
                if (row == null || row.isEmpty()) {
                    continue;
                }
                String bKey = row.get(0);
                String aKey = bToA.get(bKey);
                if (aKey == null) {
                    if (options.isKeepUnmapped()) {
                        unmappedRows.add(buildRow("", row, tagColumnCount));
                    }
                    continue;
                }
                List<List<String>> perColumn = grouped.computeIfAbsent(aKey, k -> {
                    List<List<String>> init = new ArrayList<>(tagColumnCount);
                    for (int i = 0; i < tagColumnCount; i++) {
                        init.add(new ArrayList<>());
                    }
                    return init;
                });
                for (int i = 0; i < tagColumnCount; i++) {
                    String value = (i + 1) < row.size() ? row.get(i + 1) : "";
                    perColumn.get(i).add(value == null ? "" : value);
                }
            }
            String sep = options.getJoinSeparator() == null ? "," : options.getJoinSeparator();
            for (Map.Entry<String, List<List<String>>> e : grouped.entrySet()) {
                List<String> merged = new ArrayList<>(tagColumnCount + 1);
                merged.add(e.getKey());
                for (List<String> colValues : e.getValue()) {
                    merged.add(joinColumnValues(colValues, options, sep));
                }
                result.add(merged);
            }
            result.addAll(unmappedRows);
        }

        return result;
    }

    /** 按选项对一列的标签值做去重 / 排序 / 拼接。 */
    private static String joinColumnValues(List<String> colValues, Options options, String sep) {
        Iterable<String> toJoin;
        if (options.isSortOnMerge() && options.isDedupOnMerge()) {
            toJoin = new TreeSet<>(colValues);
        } else if (options.isDedupOnMerge()) {
            toJoin = new LinkedHashSet<>(colValues);
        } else if (options.isSortOnMerge()) {
            List<String> sorted = new ArrayList<>(colValues);
            Collections.sort(sorted);
            toJoin = sorted;
        } else {
            toJoin = colValues;
        }
        return String.join(sep, toJoin);
    }

    /** 构造一行新数据：第 0 列为新主键，其余复制原行的标签值并补齐长度。 */
    private static List<String> buildRow(String newKey, List<String> originalRow, int tagColumnCount) {
        List<String> out = new ArrayList<>(tagColumnCount + 1);
        out.add(newKey);
        for (int i = 0; i < tagColumnCount; i++) {
            int idx = i + 1;
            out.add(idx < originalRow.size() && originalRow.get(idx) != null ? originalRow.get(idx) : "");
        }
        return out;
    }

    // ---------- 简单演示 ----------
    public static void main(String[] args) {
        List<Mapping> mappings = new ArrayList<>();
        // A1 -> {B1, B2, B3}
        mappings.add(new Mapping("A1", "B1"));
        mappings.add(new Mapping("A1", "B2"));
        mappings.add(new Mapping("A1", "B3"));
        // A2 -> {B4}
        mappings.add(new Mapping("A2", "B4"));

        // 场景 1：A 主键的 Excel -> 转成 B 主键（拆行）
        List<List<String>> aRows = new ArrayList<>();
        aRows.add(java.util.Arrays.asList("A", "标签1", "标签2"));
        aRows.add(java.util.Arrays.asList("A1", "红色", "甜"));
        aRows.add(java.util.Arrays.asList("A2", "绿色", "酸"));
        System.out.println("== A_TO_B 拆行 ==");
        print(convert(aRows, mappings, Direction.A_TO_B,
                new Options().setNewKeyHeader("B")));

        // 场景 2：B 主键的 Excel -> 转成 A 主键（合并）
        List<List<String>> bRows = new ArrayList<>();
        bRows.add(java.util.Arrays.asList("B", "标签1", "标签2"));
        bRows.add(java.util.Arrays.asList("B1", "红色", "甜"));
        bRows.add(java.util.Arrays.asList("B2", "黄色", "甜"));
        bRows.add(java.util.Arrays.asList("B3", "红色", "酸"));
        bRows.add(java.util.Arrays.asList("B4", "绿色", "酸"));
        System.out.println("== B_TO_A 合并（默认：去重 + 排序）==");
        print(convert(bRows, mappings, Direction.B_TO_A,
                new Options().setNewKeyHeader("A")));

        System.out.println("== B_TO_A 合并（关闭去重、关闭排序）==");
        print(convert(bRows, mappings, Direction.B_TO_A,
                new Options().setNewKeyHeader("A")
                        .setDedupOnMerge(false)
                        .setSortOnMerge(false)));
    }

    private static void print(List<List<String>> rows) {
        for (List<String> row : rows) {
            System.out.println(String.join(" | ", row));
        }
        System.out.println();
    }
}
