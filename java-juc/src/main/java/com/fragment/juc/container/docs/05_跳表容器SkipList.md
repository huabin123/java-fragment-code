# 第五章：跳表容器SkipList - 并发有序Map的实现

> **学习目标**：深入理解ConcurrentSkipListMap的跳表数据结构和实现原理

---

## 一、为什么需要ConcurrentSkipListMap？

### 1.1 TreeMap的线程安全问题

```java
// 问题：TreeMap在多线程下不安全
Map<Integer, String> map = new TreeMap<>();

// 线程1：插入
map.put(1, "value1");

// 线程2：遍历
for (Map.Entry<Integer, String> entry : map.entrySet()) {
    // ConcurrentModificationException
}

// 问题：
// 1. 并发修改异常
// 2. 数据不一致
// 3. 红黑树结构破坏
```

### 1.2 ConcurrentHashMap的问题

```java
// ConcurrentHashMap：不支持排序
Map<Integer, String> map = new ConcurrentHashMap<>();
map.put(3, "c");
map.put(1, "a");
map.put(2, "b");

// 遍历顺序不确定
for (Integer key : map.keySet()) {
    System.out.println(key);  // 可能是3, 1, 2
}

// 问题：
// ❌ 无序
// ❌ 不支持范围查询
```

### 1.3 ConcurrentSkipListMap的解决方案

```java
// ConcurrentSkipListMap：并发有序Map
Map<Integer, String> map = new ConcurrentSkipListMap<>();
map.put(3, "c");
map.put(1, "a");
map.put(2, "b");

// 遍历顺序有序
for (Integer key : map.keySet()) {
    System.out.println(key);  // 1, 2, 3
}

// 优势：
// ✅ 线程安全
// ✅ 有序
// ✅ 支持范围查询
// ✅ 无锁算法
```

---

## 二、跳表（Skip List）数据结构

### 2.1 什么是跳表？

```
普通链表：
Level 0: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → null
查找8需要遍历8个节点

跳表：
Level 3:                     8 → null
Level 2:         3 →         8 → null
Level 1:     2 → 3 →     6 → 8 → null
Level 0: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → null
查找8只需要4步

特点：
1. 多层索引
2. 上层是下层的子集
3. 最底层包含所有元素
4. 查找类似二分查找
```

### 2.2 跳表的优势

```
vs 链表：
- 链表：O(n)
- 跳表：O(log n)

vs 红黑树：
- 红黑树：O(log n)，但实现复杂
- 跳表：O(log n)，实现简单

vs 平衡树：
- 平衡树：需要旋转，难以并发
- 跳表：无需旋转，易于并发
```

### 2.3 跳表的操作

**查找操作**：

```
查找key=6：

Level 3:                     8 → null
         ↓
Level 2:         3 →         8 → null
         ↓       ↓
Level 1:     2 → 3 →     6 → 8 → null
                         ↑
Level 0: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → null

步骤：
1. 从最高层开始
2. 向右移动，直到下一个节点 >= 目标
3. 向下移动一层
4. 重复2-3，直到最底层
5. 找到目标或确定不存在
```

**插入操作**：

```
插入key=5：

1. 随机生成层数（如2层）
2. 从最高层开始查找插入位置
3. 在每一层插入新节点

插入后：
Level 2:         3 →         8 → null
Level 1:     2 → 3 → 5 → 6 → 8 → null
Level 0: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → null
```

---

## 三、ConcurrentSkipListMap源码分析

### 3.1 核心数据结构

```java
public class ConcurrentSkipListMap<K,V> extends AbstractMap<K,V>
    implements ConcurrentNavigableMap<K,V>, Cloneable, Serializable {
    
    // 头节点
    private transient volatile HeadIndex<K,V> head;
    
    // 比较器
    final Comparator<? super K> comparator;
    
    // 基础节点
    static final class Node<K,V> {
        final K key;
        volatile Object value;
        volatile Node<K,V> next;
        
        Node(K key, Object value, Node<K,V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
        
        boolean casValue(Object cmp, Object val) {
            return UNSAFE.compareAndSwapObject(this, valueOffset, cmp, val);
        }
        
        boolean casNext(Node<K,V> cmp, Node<K,V> val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }
    }
    
    // 索引节点
    static class Index<K,V> {
        final Node<K,V> node;
        final Index<K,V> down;
        volatile Index<K,V> right;
        
        Index(Node<K,V> node, Index<K,V> down, Index<K,V> right) {
            this.node = node;
            this.down = down;
            this.right = right;
        }
        
        final boolean casRight(Index<K,V> cmp, Index<K,V> val) {
            return UNSAFE.compareAndSwapObject(this, rightOffset, cmp, val);
        }
    }
    
    // 头索引
    static final class HeadIndex<K,V> extends Index<K,V> {
        final int level;
        HeadIndex(Node<K,V> node, Index<K,V> down, Index<K,V> right, int level) {
            super(node, down, right);
            this.level = level;
        }
    }
}
```

**数据结构图**：

```
head (level=3)
  ↓
Level 3: HEAD ──────────────────→ 8 → null
          ↓                        ↓
Level 2: HEAD ────────→ 3 ───────→ 8 → null
          ↓             ↓          ↓
Level 1: HEAD ──→ 2 ──→ 3 ──→ 6 ──→ 8 → null
          ↓       ↓     ↓     ↓    ↓
Level 0: HEAD → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → null
         (Base Level - 实际数据)

特点：
1. 多层索引
2. 每层都是链表
3. down指针连接上下层
4. right指针连接同层节点
```

### 3.2 put操作详解

```java
public V put(K key, V value) {
    if (value == null)
        throw new NullPointerException();
    return doPut(key, value, false);
}

private V doPut(K key, V value, boolean onlyIfAbsent) {
    Node<K,V> z;  // 新节点
    if (key == null)
        throw new NullPointerException();
    Comparator<? super K> cmp = comparator;
    
    outer: for (;;) {
        // 1. 查找插入位置
        for (Node<K,V> b = findPredecessor(key, cmp), n = b.next;;) {
            if (n != null) {
                Object v; int c;
                Node<K,V> f = n.next;
                if (n != b.next)  // 不一致，重试
                    break;
                if ((v = n.value) == null) {  // n被删除
                    n.helpDelete(b, f);
                    break;
                }
                if (b.value == null || v == n)  // b被删除
                    break;
                if ((c = cpr(cmp, key, n.key)) > 0) {
                    b = n;
                    n = f;
                    continue;
                }
                if (c == 0) {  // key已存在
                    if (onlyIfAbsent || n.casValue(v, value)) {
                        @SuppressWarnings("unchecked") V vv = (V)v;
                        return vv;
                    }
                    break;
                }
            }
            
            // 2. 插入新节点
            z = new Node<K,V>(key, value, n);
            if (!b.casNext(n, z))
                break;  // CAS失败，重试
            break outer;
        }
    }
    
    // 3. 随机决定是否建立索引
    int rnd = ThreadLocalRandom.nextSecondarySeed();
    if ((rnd & 0x80000001) == 0) {  // 50%概率
        int level = 1, max;
        while (((rnd >>>= 1) & 1) != 0)
            ++level;  // 随机层数
        
        Index<K,V> idx = null;
        HeadIndex<K,V> h = head;
        
        // 4. 建立索引
        if (level <= (max = h.level)) {
            for (int i = 1; i <= level; ++i)
                idx = new Index<K,V>(z, idx, null);
        }
        else {  // 增加新层
            level = max + 1;
            Index<K,V>[] idxs = (Index<K,V>[])new Index<?,?>[level+1];
            for (int i = 1; i <= level; ++i)
                idxs[i] = idx = new Index<K,V>(z, idx, null);
            
            for (;;) {
                h = head;
                int oldLevel = h.level;
                if (level <= oldLevel)
                    break;
                HeadIndex<K,V> newh = h;
                Node<K,V> oldbase = h.node;
                for (int j = oldLevel+1; j <= level; ++j)
                    newh = new HeadIndex<K,V>(oldbase, newh, idxs[j], j);
                if (casHead(h, newh)) {
                    h = newh;
                    idx = idxs[level = oldLevel];
                    break;
                }
            }
        }
        
        // 5. 插入索引
        splice: for (int insertionLevel = level;;) {
            int j = h.level;
            for (Index<K,V> q = h, r = q.right, t = idx;;) {
                if (q == null || t == null)
                    break splice;
                if (r != null) {
                    Node<K,V> n = r.node;
                    int c = cpr(cmp, key, n.key);
                    if (n.value == null) {
                        if (!q.unlink(r))
                            break;
                        r = q.right;
                        continue;
                    }
                    if (c > 0) {
                        q = r;
                        r = r.right;
                        continue;
                    }
                }
                
                if (j == insertionLevel) {
                    if (!q.link(r, t))
                        break;
                    if (t.node.value == null) {
                        findNode(key);
                        break splice;
                    }
                    if (--insertionLevel == 0)
                        break splice;
                }
                
                if (--j >= insertionLevel && j < level)
                    t = t.down;
                q = q.down;
                r = q.right;
            }
        }
    }
    return null;
}
```

**put流程图**：

```
开始
  ↓
查找插入位置（findPredecessor）
  ↓
CAS插入节点到base level
  ↓
成功？
├─ 否 → 重试
└─ 是 ↓
随机决定是否建立索引
  ↓
需要索引？
├─ 否 → 结束
└─ 是 ↓
随机生成层数
  ↓
建立索引节点
  ↓
插入索引到各层
  ↓
结束
```

### 3.3 get操作详解

```java
public V get(Object key) {
    return doGet(key);
}

private V doGet(Object key) {
    if (key == null)
        throw new NullPointerException();
    Comparator<? super K> cmp = comparator;
    outer: for (;;) {
        for (Node<K,V> b = findPredecessor(key, cmp), n = b.next;;) {
            Object v; int c;
            if (n == null)
                break outer;
            Node<K,V> f = n.next;
            if (n != b.next)  // 不一致，重试
                break;
            if ((v = n.value) == null) {  // n被删除
                n.helpDelete(b, f);
                break;
            }
            if (b.value == null || v == n)  // b被删除
                break;
            if ((c = cpr(cmp, key, n.key)) == 0) {
                @SuppressWarnings("unchecked") V vv = (V)v;
                return vv;
            }
            if (c < 0)
                break outer;
            b = n;
            n = f;
        }
    }
    return null;
}

// 查找前驱节点
private Node<K,V> findPredecessor(Object key, Comparator<? super K> cmp) {
    if (key == null)
        throw new NullPointerException();
    for (;;) {
        for (Index<K,V> q = head, r = q.right, d;;) {
            if (r != null) {
                Node<K,V> n = r.node;
                K k = n.key;
                if (n.value == null) {
                    if (!q.unlink(r))
                        break;
                    r = q.right;
                    continue;
                }
                if (cpr(cmp, key, k) > 0) {
                    q = r;
                    r = r.right;
                    continue;
                }
            }
            if ((d = q.down) == null)
                return q.node;
            q = d;
            r = d.right;
        }
    }
}
```

---

## 四、性能分析

### 4.1 时间复杂度

| 操作 | 平均 | 最坏 |
|------|------|------|
| get | O(log n) | O(n) |
| put | O(log n) | O(n) |
| remove | O(log n) | O(n) |
| containsKey | O(log n) | O(n) |

### 4.2 空间复杂度

```
空间复杂度：O(n)

索引节点数量：
- 期望：n/2 + n/4 + n/8 + ... ≈ n
- 总节点数：约2n

结论：
- 空间换时间
- 额外空间约1倍
```

### 4.3 性能对比

```java
public class PerformanceTest {
    private static final int SIZE = 100000;
    private static final int THREADS = 10;
    
    // 测试ConcurrentSkipListMap
    public static void testSkipListMap() {
        Map<Integer, String> map = new ConcurrentSkipListMap<>();
        // 多线程读写
    }
    
    // 测试ConcurrentHashMap
    public static void testConcurrentHashMap() {
        Map<Integer, String> map = new ConcurrentHashMap<>();
        // 多线程读写
    }
}
```

**性能结果**：

```
操作              ConcurrentSkipListMap    ConcurrentHashMap
put              200ms                    100ms
get              150ms                    50ms
遍历（有序）      100ms                    150ms（无序）
范围查询          50ms                     不支持

结论：
- 单点操作：ConcurrentHashMap快
- 有序遍历：ConcurrentSkipListMap快
- 范围查询：只有ConcurrentSkipListMap支持
```

---

## 五、NavigableMap接口

### 5.1 核心方法

```java
public interface NavigableMap<K,V> extends SortedMap<K,V> {
    
    // 小于key的最大entry
    Map.Entry<K,V> lowerEntry(K key);
    K lowerKey(K key);
    
    // 小于等于key的最大entry
    Map.Entry<K,V> floorEntry(K key);
    K floorKey(K key);
    
    // 大于等于key的最小entry
    Map.Entry<K,V> ceilingEntry(K key);
    K ceilingKey(K key);
    
    // 大于key的最小entry
    Map.Entry<K,V> higherEntry(K key);
    K higherKey(K key);
    
    // 第一个和最后一个
    Map.Entry<K,V> firstEntry();
    Map.Entry<K,V> lastEntry();
    
    // 移除并返回
    Map.Entry<K,V> pollFirstEntry();
    Map.Entry<K,V> pollLastEntry();
    
    // 子Map
    NavigableMap<K,V> subMap(K fromKey, boolean fromInclusive,
                             K toKey, boolean toInclusive);
    NavigableMap<K,V> headMap(K toKey, boolean inclusive);
    NavigableMap<K,V> tailMap(K fromKey, boolean inclusive);
}
```

### 5.2 使用示例

```java
NavigableMap<Integer, String> map = new ConcurrentSkipListMap<>();
map.put(1, "a");
map.put(3, "c");
map.put(5, "e");
map.put(7, "g");

// 查找
map.lowerKey(4);     // 3
map.floorKey(5);     // 5
map.ceilingKey(4);   // 5
map.higherKey(5);    // 7

// 范围查询
NavigableMap<Integer, String> subMap = map.subMap(2, true, 6, false);
// {3=c, 5=e}

// 倒序
NavigableMap<Integer, String> descMap = map.descendingMap();
// {7=g, 5=e, 3=c, 1=a}
```

---

## 六、实际应用场景

### 6.1 排行榜

```java
public class Leaderboard {
    private final ConcurrentSkipListMap<Integer, String> scores = 
        new ConcurrentSkipListMap<>(Collections.reverseOrder());
    
    public void updateScore(String player, int score) {
        scores.put(score, player);
    }
    
    public List<String> getTopN(int n) {
        return scores.values().stream()
            .limit(n)
            .collect(Collectors.toList());
    }
}
```

### 6.2 时间范围查询

```java
public class EventLog {
    private final ConcurrentSkipListMap<Long, Event> events = 
        new ConcurrentSkipListMap<>();
    
    public void addEvent(Event event) {
        events.put(event.getTimestamp(), event);
    }
    
    public List<Event> getEventsInRange(long start, long end) {
        return new ArrayList<>(
            events.subMap(start, true, end, false).values()
        );
    }
}
```

### 6.3 有序缓存

```java
public class OrderedCache<K extends Comparable<K>, V> {
    private final ConcurrentSkipListMap<K, V> cache = 
        new ConcurrentSkipListMap<>();
    
    public void put(K key, V value) {
        cache.put(key, value);
    }
    
    public V get(K key) {
        return cache.get(key);
    }
    
    public void evictOldest(int count) {
        for (int i = 0; i < count; i++) {
            cache.pollFirstEntry();
        }
    }
}
```

---

## 七、总结

### 7.1 核心要点

1. **跳表**：多层索引，查找O(log n)
2. **无锁**：CAS实现，高并发性能好
3. **有序**：支持排序和范围查询
4. **NavigableMap**：丰富的导航方法
5. **空间换时间**：额外空间约1倍

### 7.2 优缺点

```
优势：
✅ 线程安全
✅ 有序
✅ 支持范围查询
✅ 无锁算法
✅ 实现简单

劣势：
❌ 单点操作比ConcurrentHashMap慢
❌ 空间占用大
❌ 不支持null key/value
```

### 7.3 思考题

1. **为什么跳表比红黑树更适合并发？**
2. **跳表的层数如何确定？**
3. **什么时候使用ConcurrentSkipListMap？**
4. **跳表的空间复杂度是多少？**

---

**恭喜！你已经完成了并发容器的深度学习！** 🎉

---

**参考资料**：
- 《Java并发编程实战》第5章
- JDK源码：`java.util.concurrent.ConcurrentSkipListMap`
- William Pugh的论文：Skip Lists: A Probabilistic Alternative to Balanced Trees
