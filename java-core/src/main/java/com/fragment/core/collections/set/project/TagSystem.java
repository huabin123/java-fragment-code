package com.fragment.core.collections.set.project;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签系统
 *
 * 使用 Set 管理内容标签，支持：
 * 1. 内容打标签/移除标签
 * 2. 按标签查找内容（交集查询）
 * 3. 相关内容推荐（Jaccard 相似度）
 * 4. 热门标签统计
 */
public class TagSystem {

    // contentId → tags
    private final Map<String, Set<String>> contentTags = new HashMap<>();
    // tag → contentIds（倒排索引）
    private final Map<String, Set<String>> tagIndex = new HashMap<>();

    public static void main(String[] args) {
        TagSystem ts = new TagSystem();

        ts.tag("article-1", "Java", "并发", "JVM", "性能");
        ts.tag("article-2", "Java", "Spring", "微服务");
        ts.tag("article-3", "Python", "机器学习", "数据科学");
        ts.tag("article-4", "Java", "JVM", "GC");
        ts.tag("article-5", "Spring", "Spring Boot", "微服务");

        System.out.println("=== 标签查询 ===");
        System.out.println("同时含 Java 和 JVM: " + ts.findByAllTags("Java", "JVM"));
        System.out.println("含 Java 或 Spring:  " + ts.findByAnyTag("Java", "Spring"));

        System.out.println("\n=== 相似内容推荐（基于 article-1）===");
        ts.findSimilar("article-1", 3).forEach((id, score) ->
            System.out.printf("  %s (相似度=%.2f, 共同标签=%s)%n",
                id, score, ts.commonTags("article-1", id)));

        System.out.println("\n=== 热门标签 Top3 ===");
        ts.topTags(3).forEach(e ->
            System.out.println("  " + e.getKey() + ": " + e.getValue() + " 篇"));

        System.out.println("\n=== 移除标签 ===");
        ts.untag("article-1", "性能");
        System.out.println("article-1 的标签: " + ts.getTags("article-1"));
    }

    public void tag(String contentId, String... tags) {
        Set<String> tagSet = contentTags.computeIfAbsent(contentId, k -> new LinkedHashSet<>());
        for (String tag : tags) {
            tagSet.add(tag);
            tagIndex.computeIfAbsent(tag, k -> new HashSet<>()).add(contentId);
        }
    }

    public void untag(String contentId, String... tags) {
        Set<String> tagSet = contentTags.get(contentId);
        if (tagSet == null) return;
        for (String tag : tags) {
            tagSet.remove(tag);
            Set<String> indexed = tagIndex.get(tag);
            if (indexed != null) indexed.remove(contentId);
        }
    }

    /** 同时包含所有指定标签的内容（交集查询）*/
    public Set<String> findByAllTags(String... tags) {
        Set<String> result = null;
        for (String tag : tags) {
            Set<String> ids = tagIndex.getOrDefault(tag, Collections.emptySet());
            if (result == null) result = new HashSet<>(ids);
            else result.retainAll(ids);  // 交集
        }
        return result != null ? result : Collections.emptySet();
    }

    /** 包含任意一个指定标签的内容（并集查询）*/
    public Set<String> findByAnyTag(String... tags) {
        Set<String> result = new HashSet<>();
        for (String tag : tags) {
            result.addAll(tagIndex.getOrDefault(tag, Collections.emptySet()));
        }
        return result;
    }

    /** Jaccard 相似度：|A ∩ B| / |A ∪ B| */
    public Map<String, Double> findSimilar(String contentId, int topN) {
        Set<String> myTags = contentTags.getOrDefault(contentId, Collections.emptySet());
        return contentTags.entrySet().stream()
            .filter(e -> !e.getKey().equals(contentId))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> jaccardSimilarity(myTags, e.getValue())
            ))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topN)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> a, LinkedHashMap::new));
    }

    public Set<String> commonTags(String id1, String id2) {
        Set<String> common = new HashSet<>(contentTags.getOrDefault(id1, Collections.emptySet()));
        common.retainAll(contentTags.getOrDefault(id2, Collections.emptySet()));
        return common;
    }

    public List<Map.Entry<String, Integer>> topTags(int n) {
        return tagIndex.entrySet().stream()
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().size()))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(n)
            .collect(Collectors.toList());
    }

    public Set<String> getTags(String contentId) {
        return contentTags.getOrDefault(contentId, Collections.emptySet());
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
