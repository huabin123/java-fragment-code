# LinkedHashMap实现LRU缓存

## 1. 什么是LRU缓存？

### 1.1 LRU算法

**LRU（Least Recently Used）**：最近最少使用算法。

**核心思想**：
- 如果数据最近被访问过，那么将来被访问的几率也更高
- 当缓存满时，优先淘汰最久未使用的数据

**应用场景**：
- 操作系统的页面置换算法
- 数据库缓存
- Web缓存
- CPU缓存

---

### 1.2 LRU缓存的特性

**基本操作**：
1. **get(key)**：获取缓存中的数据
2. **put(key, value)**：添加数据到缓存

**核心特性**：
1. **固定容量**：缓存有最大容量限制
2. **自动淘汰**：当缓存满时，自动删除最久未使用的数据
3. **O(1)时间复杂度**：get和put操作都应该是O(1)

---

### 1.3 LRU缓存的工作原理

```
初始状态（容量=3）：
[]

put(1, "A"):
[1]

put(2, "B"):
[1, 2]

put(3, "C"):
[1, 2, 3]

get(1)（访问1，1移到最后）:
[2, 3, 1]

put(4, "D")（缓存满，删除最久未使用的2）:
[3, 1, 4]

get(3)（访问3，3移到最后）:
[1, 4, 3]

put(5, "E")（缓存满，删除最久未使用的1）:
[4, 3, 5]
```

---

## 2. 为什么LinkedHashMap适合实现LRU缓存？

### 2.1 LRU缓存的需求

1. **快速查找**：O(1)时间复杂度
2. **维护顺序**：按访问顺序排列
3. **快速删除**：删除最久未使用的元素
4. **快速插入**：添加新元素到最后

---

### 2.2 LinkedHashMap的优势

| 需求 | LinkedHashMap的实现 | 时间复杂度 |
|------|-------------------|-----------|
| **快速查找** | HashMap的hash表 | O(1) |
| **维护顺序** | 双向链表 + accessOrder=true | O(1) |
| **快速删除** | 删除head节点 | O(1) |
| **快速插入** | 添加到tail | O(1) |

**结论**：LinkedHashMap天然适合实现LRU缓存！

---

## 3. 基础LRU缓存实现

### 3.1 最简单的实现

```java
public class SimpleLRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    
    public SimpleLRUCache(int maxSize) {
        // accessOrder=true：按访问顺序
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        // 当size超过maxSize时，删除最老的元素
        return size() > maxSize;
    }
}
```

**使用示例**：

```java
SimpleLRUCache<Integer, String> cache = new SimpleLRUCache<>(3);

cache.put(1, "A");
cache.put(2, "B");
cache.put(3, "C");
System.out.println(cache);  // {1=A, 2=B, 3=C}

cache.get(1);  // 访问1
System.out.println(cache);  // {2=B, 3=C, 1=A}

cache.put(4, "D");  // 添加4，删除最久未使用的2
System.out.println(cache);  // {3=C, 1=A, 4=D}
```

---

### 3.2 工作原理

**关键点1：accessOrder=true**

```java
super(16, 0.75f, true);
```

- 按访问顺序维护链表
- get操作会将元素移到链表尾部

**关键点2：removeEldestEntry**

```java
@Override
protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maxSize;
}
```

- 在每次put后调用
- 如果返回true，删除最老的元素（head）

---

### 3.3 完整流程分析

```java
SimpleLRUCache<Integer, String> cache = new SimpleLRUCache<>(3);

// 步骤1：put(1, "A")
cache.put(1, "A");
// 链表：head → 1 ← tail
// size=1, maxSize=3, 不删除

// 步骤2：put(2, "B")
cache.put(2, "B");
// 链表：head → 1 ↔ 2 ← tail
// size=2, maxSize=3, 不删除

// 步骤3：put(3, "C")
cache.put(3, "C");
// 链表：head → 1 ↔ 2 ↔ 3 ← tail
// size=3, maxSize=3, 不删除

// 步骤4：get(1)
cache.get(1);
// 访问1，1移到尾部
// 链表：head → 2 ↔ 3 ↔ 1 ← tail

// 步骤5：put(4, "D")
cache.put(4, "D");
// 1. 插入4到尾部：head → 2 ↔ 3 ↔ 1 ↔ 4 ← tail
// 2. size=4 > maxSize=3，removeEldestEntry返回true
// 3. 删除head（2）：head → 3 ↔ 1 ↔ 4 ← tail
```

---

## 4. 增强版LRU缓存实现

### 4.1 添加统计功能

```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    private int hitCount = 0;      // 命中次数
    private int missCount = 0;     // 未命中次数
    private int evictionCount = 0; // 淘汰次数
    
    public LRUCache(int maxSize) {
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }
    
    @Override
    public V get(Object key) {
        V value = super.get(key);
        if (value != null) {
            hitCount++;
        } else {
            missCount++;
        }
        return value;
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean shouldRemove = size() > maxSize;
        if (shouldRemove) {
            evictionCount++;
            System.out.println("淘汰: " + eldest.getKey() + "=" + eldest.getValue());
        }
        return shouldRemove;
    }
    
    // 获取命中率
    public double getHitRate() {
        int total = hitCount + missCount;
        return total == 0 ? 0 : (double) hitCount / total;
    }
    
    // 打印统计信息
    public void printStats() {
        System.out.println("缓存统计:");
        System.out.println("  命中次数: " + hitCount);
        System.out.println("  未命中次数: " + missCount);
        System.out.println("  命中率: " + String.format("%.2f%%", getHitRate() * 100));
        System.out.println("  淘汰次数: " + evictionCount);
        System.out.println("  当前大小: " + size());
    }
}
```

**使用示例**：

```java
LRUCache<Integer, String> cache = new LRUCache<>(3);

cache.put(1, "A");
cache.put(2, "B");
cache.put(3, "C");

cache.get(1);  // 命中
cache.get(4);  // 未命中

cache.put(4, "D");  // 淘汰2

cache.printStats();
// 缓存统计:
//   命中次数: 1
//   未命中次数: 1
//   命中率: 50.00%
//   淘汰次数: 1
//   当前大小: 3
```

---

### 4.2 添加过期时间

```java
public class LRUCacheWithExpiration<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    private final long expireTime;  // 过期时间（毫秒）
    private final Map<K, Long> timestamps = new HashMap<>();
    
    public LRUCacheWithExpiration(int maxSize, long expireTime) {
        super(16, 0.75f, true);
        this.maxSize = maxSize;
        this.expireTime = expireTime;
    }
    
    @Override
    public V put(K key, V value) {
        timestamps.put(key, System.currentTimeMillis());
        return super.put(key, value);
    }
    
    @Override
    public V get(Object key) {
        // 检查是否过期
        Long timestamp = timestamps.get(key);
        if (timestamp != null) {
            long age = System.currentTimeMillis() - timestamp;
            if (age > expireTime) {
                // 过期，删除
                remove(key);
                timestamps.remove(key);
                return null;
            }
        }
        return super.get(key);
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        if (size() > maxSize) {
            timestamps.remove(eldest.getKey());
            return true;
        }
        return false;
    }
    
    // 清理过期元素
    public void cleanUp() {
        long now = System.currentTimeMillis();
        List<K> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<K, Long> entry : timestamps.entrySet()) {
            if (now - entry.getValue() > expireTime) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (K key : expiredKeys) {
            remove(key);
            timestamps.remove(key);
        }
    }
}
```

**使用示例**：

```java
// 最大容量3，过期时间5秒
LRUCacheWithExpiration<Integer, String> cache = 
    new LRUCacheWithExpiration<>(3, 5000);

cache.put(1, "A");
cache.put(2, "B");

Thread.sleep(6000);  // 等待6秒

String value = cache.get(1);  // null，已过期
```

---

### 4.3 线程安全的LRU缓存

```java
public class ThreadSafeLRUCache<K, V> {
    private final LinkedHashMap<K, V> cache;
    private final int maxSize;
    
    public ThreadSafeLRUCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }
    
    public synchronized V get(K key) {
        return cache.get(key);
    }
    
    public synchronized V put(K key, V value) {
        return cache.put(key, value);
    }
    
    public synchronized V remove(K key) {
        return cache.remove(key);
    }
    
    public synchronized int size() {
        return cache.size();
    }
    
    public synchronized void clear() {
        cache.clear();
    }
}
```

**注意**：
- 所有方法都加synchronized，保证线程安全
- 性能较差，高并发场景不推荐
- 推荐使用Guava的Cache或Caffeine

---

## 5. 实际项目应用

### 5.1 数据库查询缓存

```java
public class DatabaseQueryCache {
    private final LRUCache<String, Object> cache;
    
    public DatabaseQueryCache(int maxSize) {
        this.cache = new LRUCache<>(maxSize);
    }
    
    public Object query(String sql, Object... params) {
        // 生成缓存key
        String cacheKey = generateKey(sql, params);
        
        // 先从缓存获取
        Object result = cache.get(cacheKey);
        if (result != null) {
            System.out.println("缓存命中: " + sql);
            return result;
        }
        
        // 缓存未命中，执行查询
        System.out.println("缓存未命中，执行查询: " + sql);
        result = executeQuery(sql, params);
        
        // 放入缓存
        cache.put(cacheKey, result);
        
        return result;
    }
    
    private String generateKey(String sql, Object... params) {
        StringBuilder sb = new StringBuilder(sql);
        for (Object param : params) {
            sb.append(":").append(param);
        }
        return sb.toString();
    }
    
    private Object executeQuery(String sql, Object... params) {
        // 模拟数据库查询
        try {
            Thread.sleep(100);  // 模拟查询耗时
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "查询结果";
    }
    
    public void printStats() {
        cache.printStats();
    }
}

// 使用示例
public class DatabaseQueryCacheDemo {
    public static void main(String[] args) {
        DatabaseQueryCache cache = new DatabaseQueryCache(3);
        
        cache.query("SELECT * FROM users WHERE id = ?", 1);  // 未命中
        cache.query("SELECT * FROM users WHERE id = ?", 2);  // 未命中
        cache.query("SELECT * FROM users WHERE id = ?", 1);  // 命中
        cache.query("SELECT * FROM users WHERE id = ?", 3);  // 未命中
        cache.query("SELECT * FROM users WHERE id = ?", 4);  // 未命中，淘汰id=2
        
        cache.printStats();
    }
}
```

---

### 5.2 图片缓存

```java
public class ImageCache {
    private final LRUCache<String, byte[]> cache;
    
    public ImageCache(int maxSize) {
        this.cache = new LRUCache<>(maxSize);
    }
    
    public byte[] getImage(String url) {
        // 先从缓存获取
        byte[] image = cache.get(url);
        if (image != null) {
            System.out.println("从缓存获取图片: " + url);
            return image;
        }
        
        // 缓存未命中，下载图片
        System.out.println("下载图片: " + url);
        image = downloadImage(url);
        
        // 放入缓存
        cache.put(url, image);
        
        return image;
    }
    
    private byte[] downloadImage(String url) {
        // 模拟下载图片
        try {
            Thread.sleep(500);  // 模拟下载耗时
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new byte[1024];  // 模拟图片数据
    }
}
```

---

### 5.3 API响应缓存

```java
public class ApiResponseCache {
    private final LRUCacheWithExpiration<String, String> cache;
    
    public ApiResponseCache(int maxSize, long expireTime) {
        this.cache = new LRUCacheWithExpiration<>(maxSize, expireTime);
    }
    
    public String callApi(String endpoint, Map<String, String> params) {
        // 生成缓存key
        String cacheKey = generateKey(endpoint, params);
        
        // 先从缓存获取
        String response = cache.get(cacheKey);
        if (response != null) {
            System.out.println("缓存命中: " + endpoint);
            return response;
        }
        
        // 缓存未命中，调用API
        System.out.println("调用API: " + endpoint);
        response = invokeApi(endpoint, params);
        
        // 放入缓存
        cache.put(cacheKey, response);
        
        return response;
    }
    
    private String generateKey(String endpoint, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(endpoint);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(":").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
    
    private String invokeApi(String endpoint, Map<String, String> params) {
        // 模拟API调用
        try {
            Thread.sleep(200);  // 模拟网络延迟
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "{\"status\":\"success\"}";
    }
}
```

---

## 6. LRU缓存的性能分析

### 6.1 时间复杂度

| 操作 | 时间复杂度 | 说明 |
|------|-----------|------|
| **get** | O(1) | HashMap查找 + 链表移动 |
| **put** | O(1) | HashMap插入 + 链表插入 + 可能删除 |
| **remove** | O(1) | HashMap删除 + 链表删除 |

---

### 6.2 空间复杂度

```
空间复杂度：O(n)

其中n为缓存容量

额外空间：
- HashMap的table数组
- 双向链表的指针
- Entry节点
```

---

### 6.3 性能测试

```java
public class LRUCachePerformanceTest {
    public static void main(String[] args) {
        int cacheSize = 1000;
        int testCount = 100000;
        
        LRUCache<Integer, String> cache = new LRUCache<>(cacheSize);
        
        // 预热
        for (int i = 0; i < cacheSize; i++) {
            cache.put(i, "value" + i);
        }
        
        // 测试get性能
        long start = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            int key = (int) (Math.random() * cacheSize * 2);
            cache.get(key);
        }
        long end = System.currentTimeMillis();
        
        System.out.println("get操作 " + testCount + " 次，耗时: " + (end - start) + "ms");
        System.out.println("平均每次: " + ((end - start) * 1000000.0 / testCount) + "ns");
        
        cache.printStats();
    }
}
```

---

## 7. LRU缓存的优缺点

### 7.1 优点

1. **实现简单**：基于LinkedHashMap，代码简洁
2. **性能优异**：所有操作都是O(1)
3. **自动淘汰**：无需手动管理缓存
4. **符合局部性原理**：最近访问的数据更可能再次访问

---

### 7.2 缺点

1. **无法区分访问频率**：只考虑访问时间，不考虑访问频率
2. **缓存污染**：偶尔访问的数据可能淘汰频繁访问的数据
3. **不支持权重**：所有数据权重相同
4. **内存占用大**：需要维护双向链表

---

### 7.3 改进方案

**LRU的变种**：

1. **LRU-K**：考虑最近K次访问
2. **2Q**：使用两个队列
3. **LFU（Least Frequently Used）**：最少使用频率
4. **ARC（Adaptive Replacement Cache）**：自适应替换

**推荐的缓存库**：

1. **Guava Cache**：Google的缓存库
2. **Caffeine**：高性能缓存库
3. **Ehcache**：企业级缓存

---

## 8. 总结

### 8.1 核心要点

1. **LRU算法**：最近最少使用算法
2. **LinkedHashMap实现**：accessOrder=true + removeEldestEntry
3. **O(1)性能**：所有操作都是O(1)
4. **实际应用**：数据库缓存、图片缓存、API缓存

---

### 8.2 实现要点

```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    
    public LRUCache(int maxSize) {
        super(16, 0.75f, true);  // accessOrder=true
        this.maxSize = maxSize;
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;  // 超过容量时删除
    }
}
```

---

### 8.3 下一步学习

在理解了LinkedHashMap实现LRU缓存后，接下来我们将学习：

**LinkedHashMap最佳实践与性能分析**：掌握正确的使用方式和性能优化技巧

---

**继续阅读**：[05_LinkedHashMap最佳实践与性能分析.md](./05_LinkedHashMap最佳实践与性能分析.md)
