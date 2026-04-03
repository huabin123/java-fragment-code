package com.fragment.core.collections.linkedhashmap.project;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库查询缓存
 * 
 * 使用LinkedHashMap实现数据库查询结果的LRU缓存
 * 
 * 特性：
 * 1. LRU淘汰策略
 * 2. 缓存命中率统计
 * 3. 缓存大小限制
 * 4. 线程不安全（如需线程安全，需要外部同步）
 * 
 * @author huabin
 */
public class DatabaseQueryCache {
    
    private final Map<String, QueryResult> cache;
    private final int maxSize;
    private int hitCount = 0;
    private int missCount = 0;
    private int evictionCount = 0;

    /**
     * 构造函数
     * 
     * @param maxSize 最大缓存数量
     */
    public DatabaseQueryCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<String, QueryResult>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, QueryResult> eldest) {
                boolean shouldRemove = size() > maxSize;
                if (shouldRemove) {
                    evictionCount++;
                    System.out.println("[Cache] 淘汰查询: " + eldest.getKey());
                }
                return shouldRemove;
            }
        };
    }

    /**
     * 执行查询（带缓存）
     * 
     * @param sql SQL语句
     * @param params 参数
     * @return 查询结果
     */
    public QueryResult query(String sql, Object... params) {
        // 生成缓存key
        String cacheKey = generateCacheKey(sql, params);
        
        // 先从缓存获取
        QueryResult result = cache.get(cacheKey);
        if (result != null) {
            hitCount++;
            System.out.println("[Cache] 命中: " + cacheKey);
            return result;
        }
        
        // 缓存未命中，执行查询
        missCount++;
        System.out.println("[Cache] 未命中，执行查询: " + sql);
        result = executeQuery(sql, params);
        
        // 放入缓存
        cache.put(cacheKey, result);
        
        return result;
    }

    /**
     * 清除缓存
     */
    public void clear() {
        cache.clear();
        System.out.println("[Cache] 清空缓存");
    }

    /**
     * 清除指定SQL的缓存
     * 
     * @param sql SQL语句
     */
    public void invalidate(String sql) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(sql));
        System.out.println("[Cache] 清除SQL缓存: " + sql);
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }

    /**
     * 获取命中率
     */
    public double getHitRate() {
        int total = hitCount + missCount;
        return total == 0 ? 0 : (double) hitCount / total;
    }

    /**
     * 打印统计信息
     */
    public void printStats() {
        System.out.println("\n========== 缓存统计 ==========");
        System.out.println("最大容量: " + maxSize);
        System.out.println("当前大小: " + cache.size());
        System.out.println("命中次数: " + hitCount);
        System.out.println("未命中次数: " + missCount);
        System.out.println("命中率: " + String.format("%.2f%%", getHitRate() * 100));
        System.out.println("淘汰次数: " + evictionCount);
        System.out.println("============================\n");
    }

    /**
     * 生成缓存key
     */
    private String generateCacheKey(String sql, Object... params) {
        StringBuilder sb = new StringBuilder(sql);
        for (Object param : params) {
            sb.append(":").append(param);
        }
        return sb.toString();
    }

    /**
     * 执行数据库查询（模拟）
     */
    private QueryResult executeQuery(String sql, Object... params) {
        // 模拟数据库查询耗时
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 返回模拟结果
        return new QueryResult(sql, "查询结果数据");
    }

    /**
     * 查询结果类
     */
    public static class QueryResult {
        private final String sql;
        private final Object data;
        private final long timestamp;

        public QueryResult(String sql, Object data) {
            this.sql = sql;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getSql() {
            return sql;
        }

        public Object getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "QueryResult{sql='" + sql + "', data=" + data + "}";
        }
    }

    /**
     * 测试示例
     */
    public static void main(String[] args) {
        System.out.println("========== 数据库查询缓存测试 ==========\n");
        
        // 创建容量为3的缓存
        DatabaseQueryCache cache = new DatabaseQueryCache(3);
        
        // 执行查询
        cache.query("SELECT * FROM users WHERE id = ?", 1);
        cache.query("SELECT * FROM users WHERE id = ?", 2);
        cache.query("SELECT * FROM users WHERE id = ?", 3);
        
        System.out.println();
        
        // 重复查询（命中缓存）
        cache.query("SELECT * FROM users WHERE id = ?", 1);
        cache.query("SELECT * FROM users WHERE id = ?", 2);
        
        System.out.println();
        
        // 新查询（淘汰最久未使用的）
        cache.query("SELECT * FROM users WHERE id = ?", 4);
        
        System.out.println();
        
        // 再次查询id=3（已被淘汰）
        cache.query("SELECT * FROM users WHERE id = ?", 3);
        
        System.out.println();
        
        // 打印统计信息
        cache.printStats();
        
        // 清除缓存
        cache.clear();
        cache.printStats();
        
        System.out.println("测试完成");
    }
}
