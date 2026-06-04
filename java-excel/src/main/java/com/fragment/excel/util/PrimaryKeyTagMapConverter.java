package com.fragment.excel.util;

import com.fragment.excel.util.PrimaryKeyTagConverter.Direction;
import com.fragment.excel.util.PrimaryKeyTagConverter.Mapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 主键标签转换工具（Map 版）。
 *
 * <p>与 {@link PrimaryKeyTagConverter} 的语义完全一致，区别仅在数据结构：
 * 输入/输出为 {@code List<LinkedHashMap<String, Object>>}，map 的 key 是列名（其中一个是主键列），
 * value 是该列的值（包括主键值与标签值）。
 *
 * <p>约束：A:B = 1:N（一个 A 可对应多个 B，每个 B 只属于唯一一个 A）。
 *
 * <ul>
 *   <li>A → B（拆行）：一个 A 对应多个 B 时，复制标签值拆成多行。</li>
 *   <li>B → A（合并）：多个 B 落到同一个 A 时，按列将标签值用逗号拼接合并为一行。</li>
 * </ul>
 *
 * <p>输出 map 的列顺序：以输入第 1 行的 key 顺序为模板，把 {@code sourceKeyColumn} 替换为
 * {@code targetKeyColumn}（位置不变）。
 */
public class PrimaryKeyTagMapConverter {

    /** 转换选项。 */
    public static class Options {
        /** 合并时是否对每列标签值去重。默认 true。 */
        private boolean dedupOnMerge = true;
        /** 合并时是否对每列标签值按字符串自然序排序。默认 true。 */
        private boolean sortOnMerge = true;
        /** 合并/拼接使用的分隔符。 */
        private String joinSeparator = ",";
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

        public boolean isKeepUnmapped() {
            return keepUnmapped;
        }

        public Options setKeepUnmapped(boolean keepUnmapped) {
            this.keepUnmapped = keepUnmapped;
            return this;
        }
    }

    /**
     * 使用默认选项进行转换，且新主键列名复用原主键列名。
     */
    public static List<LinkedHashMap<String, Object>> convert(
            List<LinkedHashMap<String, Object>> rows,
            List<Mapping> mappings,
            Direction direction,
            String sourceKeyColumn) {
        return convert(rows, mappings, direction, sourceKeyColumn, sourceKeyColumn, new Options());
    }

    /**
     * 按映射关系把数据从一个主键转换为另一个主键。
     *
     * @param rows              原始数据，每个元素是一行；map 的 key 是列名，value 是单元格值
     * @param mappings          A-B 映射关系（A:B = 1:N，B 唯一）
     * @param direction         转换方向
     * @param sourceKeyColumn   输入数据中主键所在的列名（必填）
     * @param targetKeyColumn   输出数据中新主键所在的列名；为空时复用 {@code sourceKeyColumn}
     * @param options           转换选项，可为 null
     * @return 转换后的数据
     */
    public static List<LinkedHashMap<String, Object>> convert(
            List<LinkedHashMap<String, Object>> rows,
            List<Mapping> mappings,
            Direction direction,
            String sourceKeyColumn,
            String targetKeyColumn,
            Options options) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(mappings, "mappings");
        Objects.requireNonNull(direction, "direction");
        if (sourceKeyColumn == null || sourceKeyColumn.isEmpty()) {
            throw new IllegalArgumentException("sourceKeyColumn must not be empty");
        }
        String targetCol = (targetKeyColumn == null || targetKeyColumn.isEmpty())
                ? sourceKeyColumn : targetKeyColumn;
        if (options == null) {
            options = new Options();
        }

        List<LinkedHashMap<String, Object>> result = new ArrayList<>();
        if (rows.isEmpty()) {
            return result;
        }

        // 以首行的 key 顺序作为输出列顺序模板（把 sourceKeyColumn 位置换成 targetKeyColumn）
        List<String> columnOrder = buildOutputColumnOrder(rows.get(0).keySet(), sourceKeyColumn, targetCol);
        List<String> tagColumns = new ArrayList<>(columnOrder);
        tagColumns.remove(targetCol);

        if (direction == Direction.A_TO_B) {
            Map<String, List<String>> aToBs = new LinkedHashMap<>();
            for (Mapping m : mappings) {
                if (m == null || m.getA() == null) {
                    continue;
                }
                aToBs.computeIfAbsent(m.getA(), k -> new ArrayList<>()).add(m.getB());
            }
            for (LinkedHashMap<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                String aKey = toStringOrEmpty(row.get(sourceKeyColumn));
                List<String> bs = aToBs.get(aKey);
                if (bs == null || bs.isEmpty()) {
                    if (options.isKeepUnmapped()) {
                        result.add(buildRow("", row, columnOrder, targetCol, tagColumns));
                    }
                    continue;
                }
                for (String b : bs) {
                    result.add(buildRow(b == null ? "" : b, row, columnOrder, targetCol, tagColumns));
                }
            }
        } else {
            Map<String, String> bToA = new LinkedHashMap<>();
            for (Mapping m : mappings) {
                if (m == null || m.getB() == null) {
                    continue;
                }
                bToA.put(m.getB(), m.getA());
            }
            // aKey -> 每列名 -> 该列的值列表（按出现顺序）
            Map<String, Map<String, List<String>>> grouped = new LinkedHashMap<>();
            List<LinkedHashMap<String, Object>> unmappedRows = new ArrayList<>();
            for (LinkedHashMap<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                String bKey = toStringOrEmpty(row.get(sourceKeyColumn));
                String aKey = bToA.get(bKey);
                if (aKey == null) {
                    if (options.isKeepUnmapped()) {
                        unmappedRows.add(buildRow("", row, columnOrder, targetCol, tagColumns));
                    }
                    continue;
                }
                Map<String, List<String>> perColumn = grouped.computeIfAbsent(aKey, k -> {
                    Map<String, List<String>> init = new LinkedHashMap<>();
                    for (String col : tagColumns) {
                        init.put(col, new ArrayList<>());
                    }
                    return init;
                });
                for (String col : tagColumns) {
                    perColumn.get(col).add(toStringOrEmpty(row.get(col)));
                }
            }
            String sep = options.getJoinSeparator() == null ? "," : options.getJoinSeparator();
            for (Map.Entry<String, Map<String, List<String>>> e : grouped.entrySet()) {
                LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
                for (String col : columnOrder) {
                    if (col.equals(targetCol)) {
                        merged.put(col, e.getKey());
                    } else {
                        merged.put(col, joinColumnValues(e.getValue().get(col), options, sep));
                    }
                }
                result.add(merged);
            }
            result.addAll(unmappedRows);
        }

        return result;
    }

    /** 以输入首行 key 顺序为模板，将 sourceKey 列名替换为 targetKey 列名（位置不变）。 */
    private static List<String> buildOutputColumnOrder(Iterable<String> sourceKeys,
                                                       String sourceKeyColumn,
                                                       String targetKeyColumn) {
        List<String> order = new ArrayList<>();
        boolean sourceSeen = false;
        for (String k : sourceKeys) {
            if (k.equals(sourceKeyColumn)) {
                order.add(targetKeyColumn);
                sourceSeen = true;
            } else if (k.equals(targetKeyColumn) && !sourceKeyColumn.equals(targetKeyColumn)) {
                // 输入中已经同时存在 targetKey 列，跳过避免重复
                continue;
            } else {
                order.add(k);
            }
        }
        if (!sourceSeen) {
            // 输入第一行没有主键列，仍把 targetKey 放到首列
            order.add(0, targetKeyColumn);
        }
        return order;
    }

    /** 构造一行新数据：targetKey 列填新主键值，其余列从原行按列名复制。 */
    private static LinkedHashMap<String, Object> buildRow(String newKey,
                                                          Map<String, Object> originalRow,
                                                          List<String> columnOrder,
                                                          String targetKeyColumn,
                                                          List<String> tagColumns) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (String col : columnOrder) {
            if (col.equals(targetKeyColumn)) {
                out.put(col, newKey);
            } else {
                out.put(col, originalRow.get(col));
            }
        }
        // tagColumns 仅用于校验/未来扩展，这里未直接使用
        if (tagColumns.isEmpty()) {
            // no-op
        }
        return out;
    }

    /** 按选项对一列的标签值做去重 / 排序 / 拼接。 */
    private static String joinColumnValues(List<String> colValues, Options options, String sep) {
        if (colValues == null || colValues.isEmpty()) {
            return "";
        }
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

    private static String toStringOrEmpty(Object v) {
        return v == null ? "" : v.toString();
    }

    // ---------- 简单演示 ----------
    public static void main(String[] args) {
        List<Mapping> mappings = new ArrayList<>();
        mappings.add(new Mapping("A1", "B1"));
        mappings.add(new Mapping("A1", "B2"));
        mappings.add(new Mapping("A1", "B3"));
        mappings.add(new Mapping("A2", "B4"));

        // 场景 1：A 主键的数据 -> 转成 B 主键（拆行）
        List<LinkedHashMap<String, Object>> aRows = new ArrayList<>();
        aRows.add(row(Arrays.asList("A", "标签1", "标签2"), Arrays.asList("A1", "红色", "甜")));
        aRows.add(row(Arrays.asList("A", "标签1", "标签2"), Arrays.asList("A2", "绿色", "酸")));
        System.out.println("== A_TO_B 拆行 ==");
        print(convert(aRows, mappings, Direction.A_TO_B, "A", "B", new Options()));

        // 场景 2：B 主键的数据 -> 转成 A 主键（合并）
        List<LinkedHashMap<String, Object>> bRows = new ArrayList<>();
        bRows.add(row(Arrays.asList("B", "标签1", "标签2"), Arrays.asList("B1", "红色", "甜")));
        bRows.add(row(Arrays.asList("B", "标签1", "标签2"), Arrays.asList("B2", "黄色", "甜")));
        bRows.add(row(Arrays.asList("B", "标签1", "标签2"), Arrays.asList("B3", "红色", "酸")));
        bRows.add(row(Arrays.asList("B", "标签1", "标签2"), Arrays.asList("B4", "绿色", "酸")));
        System.out.println("== B_TO_A 合并（默认：去重 + 排序）==");
        print(convert(bRows, mappings, Direction.B_TO_A, "B", "A", new Options()));

        System.out.println("== B_TO_A 合并（关闭去重、关闭排序）==");
        print(convert(bRows, mappings, Direction.B_TO_A, "B", "A",
                new Options().setDedupOnMerge(false).setSortOnMerge(false)));
    }

    private static LinkedHashMap<String, Object> row(List<String> keys, List<Object> values) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            m.put(keys.get(i), i < values.size() ? values.get(i) : null);
        }
        return m;
    }

    private static void print(List<LinkedHashMap<String, Object>> rows) {
        for (LinkedHashMap<String, Object> r : rows) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Object> e : r.entrySet()) {
                if (!first) {
                    sb.append(" | ");
                }
                sb.append(e.getKey()).append("=").append(e.getValue());
                first = false;
            }
            System.out.println(sb);
        }
        System.out.println();
    }
}
