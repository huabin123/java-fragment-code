package com.fragment.core.collections.set.demo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Set 典型应用场景演示
 *
 * 演示内容：
 * 1. 快速去重
 * 2. 权限检查（白名单/黑名单）
 * 3. 两个集合的差异分析
 * 4. 唯一访客统计（UV）
 */
public class SetApplicationDemo {

    public static void main(String[] args) {
        demonstrateDeduplication();
        demonstratePermissionCheck();
        demonstrateDiffAnalysis();
        demonstrateUniqueVisitors();
    }

    /**
     * 快速去重：List → Set → List
     */
    private static void demonstrateDeduplication() {
        System.out.println("=== 1. 快速去重 ===");

        List<String> withDuplicates = Arrays.asList(
            "apple", "banana", "apple", "cherry", "banana", "date", "apple"
        );

        // 去重（不保证顺序）
        List<String> deduped = new ArrayList<>(new HashSet<>(withDuplicates));
        System.out.println("去重（无序）: " + deduped);

        // 去重并保持原始顺序
        List<String> dedupedOrdered = new ArrayList<>(new LinkedHashSet<>(withDuplicates));
        System.out.println("去重（保持顺序）: " + dedupedOrdered);

        // 去重并排序
        List<String> dedupedSorted = new ArrayList<>(new TreeSet<>(withDuplicates));
        System.out.println("去重（排序）: " + dedupedSorted);

        // Stream 方式（保持顺序）
        List<String> dedupedStream = withDuplicates.stream()
            .distinct()
            .collect(Collectors.toList());
        System.out.println("Stream.distinct: " + dedupedStream);
        System.out.println();
    }

    /**
     * 权限检查：用 Set 做白名单/黑名单，O(1) 查找
     */
    private static void demonstratePermissionCheck() {
        System.out.println("=== 2. 权限检查（白名单）===");

        // 管理员白名单（TreeSet 便于审计时按字母顺序查看）
        Set<String> adminWhitelist = new TreeSet<>(Arrays.asList(
            "alice", "bob", "carol"
        ));

        // 黑名单（HashSet 查找最快）
        Set<String> blacklist = new HashSet<>(Arrays.asList(
            "mallory", "eve"
        ));

        String[] users = {"alice", "dave", "mallory", "bob", "eve", "frank"};
        for (String user : users) {
            boolean isAdmin = adminWhitelist.contains(user);
            boolean isBanned = blacklist.contains(user);
            String status = isBanned ? "❌ 封禁" : isAdmin ? "✅ 管理员" : "👤 普通用户";
            System.out.println("  " + user + " → " + status);
        }
        System.out.println();
    }

    /**
     * 差异分析：找出两个版本之间新增/删除的元素
     */
    private static void demonstrateDiffAnalysis() {
        System.out.println("=== 3. 版本差异分析 ===");

        Set<String> v1Dependencies = new HashSet<>(Arrays.asList(
            "spring-core", "spring-mvc", "mybatis", "mysql-connector", "commons-lang3"
        ));

        Set<String> v2Dependencies = new HashSet<>(Arrays.asList(
            "spring-core", "spring-mvc", "spring-data-jpa", "postgresql", "commons-lang3", "lombok"
        ));

        // 新增的依赖（在 v2 但不在 v1）
        Set<String> added = new HashSet<>(v2Dependencies);
        added.removeAll(v1Dependencies);
        System.out.println("新增依赖: " + new TreeSet<>(added));

        // 删除的依赖（在 v1 但不在 v2）
        Set<String> removed = new HashSet<>(v1Dependencies);
        removed.removeAll(v2Dependencies);
        System.out.println("删除依赖: " + new TreeSet<>(removed));

        // 保留的依赖（两者都有）
        Set<String> retained = new HashSet<>(v1Dependencies);
        retained.retainAll(v2Dependencies);
        System.out.println("保留依赖: " + new TreeSet<>(retained));
        System.out.println();
    }

    /**
     * 唯一访客统计（UV）：Set 自动去重，只统计唯一用户
     */
    private static void demonstrateUniqueVisitors() {
        System.out.println("=== 4. 唯一访客统计（UV）===");

        // 模拟一天的访问日志（同一用户可能多次访问）
        String[] accessLog = {
            "user_001", "user_002", "user_001", "user_003",
            "user_002", "user_001", "user_004", "user_003",
            "user_005", "user_001", "user_002"
        };

        Set<String> uniqueVisitors = new HashSet<>();
        int totalVisits = 0;

        for (String userId : accessLog) {
            uniqueVisitors.add(userId);
            totalVisits++;
        }

        System.out.println("总访问次数（PV）: " + totalVisits);
        System.out.println("唯一访客数（UV）: " + uniqueVisitors.size());
        System.out.printf("人均访问次数: %.1f%n", (double) totalVisits / uniqueVisitors.size());
    }
}
