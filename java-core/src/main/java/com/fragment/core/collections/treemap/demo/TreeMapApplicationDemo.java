package com.fragment.core.collections.treemap.demo;

import java.util.*;

/**
 * TreeMap 典型应用场景演示
 *
 * 演示内容：
 * 1. 词频统计并按字母排序
 * 2. 时间线事件管理
 * 3. 区间查找（floorEntry 实现范围映射）
 * 4. 排行榜（倒序 TreeMap）
 */
public class TreeMapApplicationDemo {

    public static void main(String[] args) {
        demonstrateWordFrequency();
        demonstrateTimeline();
        demonstrateRangeMapping();
        demonstrateLeaderboard();
    }

    /**
     * 词频统计：用 TreeMap 自动按字母顺序输出
     */
    private static void demonstrateWordFrequency() {
        System.out.println("=== 1. 词频统计（按字母排序）===");

        String text = "the quick brown fox jumps over the lazy dog the fox";
        TreeMap<String, Integer> freq = new TreeMap<>();

        for (String word : text.split(" ")) {
            freq.merge(word, 1, Integer::sum);  // JDK 8+ merge
        }

        freq.forEach((word, count) ->
            System.out.printf("  %-10s: %d%n", word, count));
        System.out.println("出现最多的词: " + freq.entrySet().stream()
            .max(Map.Entry.comparingByValue()).orElseThrow(RuntimeException::new));
        System.out.println();
    }

    /**
     * 时间线：用时间戳作 key，按时间顺序存储事件
     */
    private static void demonstrateTimeline() {
        System.out.println("=== 2. 时间线事件管理 ===");

        TreeMap<Long, String> timeline = new TreeMap<>();
        long base = System.currentTimeMillis();

        timeline.put(base + 1000, "用户登录");
        timeline.put(base + 3000, "提交订单");
        timeline.put(base + 5000, "支付完成");
        timeline.put(base + 2000, "浏览商品");
        timeline.put(base + 4000, "选择支付方式");

        System.out.println("按时间顺序的事件:");
        timeline.forEach((ts, event) ->
            System.out.printf("  +%dms: %s%n", ts - base, event));

        // 查询某时刻之前的最后一个事件
        long queryTime = base + 3500;
        Map.Entry<Long, String> lastBefore = timeline.floorEntry(queryTime);
        System.out.println("+" + (queryTime - base) + "ms 之前的最近事件: " + lastBefore.getValue());
        System.out.println();
    }

    /**
     * 区间映射：floorKey 实现分数→等级的范围查找
     */
    private static void demonstrateRangeMapping() {
        System.out.println("=== 3. 区间映射（分数→等级）===");

        // key = 区间下限，value = 该区间的等级
        TreeMap<Integer, String> gradeMap = new TreeMap<>();
        gradeMap.put(0,  "F");
        gradeMap.put(60, "D");
        gradeMap.put(70, "C");
        gradeMap.put(80, "B");
        gradeMap.put(90, "A");

        int[] scores = {55, 62, 78, 85, 93, 100};
        for (int score : scores) {
            // floorKey(score)：找到 ≤ score 的最大 key，即该分数所在区间的下限
            String grade = gradeMap.get(gradeMap.floorKey(score));
            System.out.printf("  分数 %3d → 等级 %s%n", score, grade);
        }
        System.out.println();
    }

    /**
     * 排行榜：分数倒序，同分按名字排序
     */
    private static void demonstrateLeaderboard() {
        System.out.println("=== 4. 排行榜（分数倒序）===");

        // Comparator：分数倒序，同分按名字升序
        TreeMap<String, Integer> board = new TreeMap<>(
            Comparator.<String, Integer>comparing(name -> -getScore(name))
                      .thenComparing(Comparator.naturalOrder())
        );

        Map<String, Integer> rawScores = new LinkedHashMap<>();
        rawScores.put("Alice", 95);
        rawScores.put("Bob",   87);
        rawScores.put("Carol", 95);
        rawScores.put("Dave",  72);
        rawScores.put("Eve",   87);

        // 把分数存入静态 map 以便 Comparator 引用
        SCORES.putAll(rawScores);
        rawScores.forEach(board::put);

        System.out.println("排行榜:");
        int rank = 1;
        for (Map.Entry<String, Integer> e : board.entrySet()) {
            System.out.printf("  第%d名: %-8s %d分%n", rank++, e.getKey(), e.getValue());
        }
    }

    private static final Map<String, Integer> SCORES = new HashMap<>();
    private static int getScore(String name) {
        return SCORES.getOrDefault(name, 0);
    }
}
