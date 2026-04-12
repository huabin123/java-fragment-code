# 第三章：TreeMap 导航与范围查询

## 3.1 六个导航方法速查

`TreeMapBasicDemo.java → demonstrateNavigationMethods()` 演示了所有导航方法：

| 方法 | 含义 | 包含等于 |
|------|------|---------|
| `floorKey(k)` | ≤ k 的最大 key | ✅ |
| `ceilingKey(k)` | ≥ k 的最小 key | ✅ |
| `lowerKey(k)` | < k 的最大 key | ❌ 严格小于 |
| `higherKey(k)` | > k 的最小 key | ❌ 严格大于 |
| `firstKey()` | 最小 key | — |
| `lastKey()` | 最大 key | — |

对应的 `Entry` 版本（返回 key-value 对）：`floorEntry`、`ceilingEntry`、`lowerEntry`、`higherEntry`、`firstEntry`、`lastEntry`。

```java
TreeMap<Integer, String> map = new TreeMap<>();
// map: {10, 20, 30, 40, 50}

map.floorKey(25);    // 20（≤25 的最大key）
map.ceilingKey(25);  // 30（≥25 的最小key）
map.floorKey(30);    // 30（30 本身）
map.lowerKey(30);    // 20（严格小于30）
map.higherKey(30);   // 40（严格大于30）
```

---

## 3.2 pollFirstEntry / pollLastEntry

```java
// 取出并删除最小/最大 entry，O(log n)
// 常用于优先处理最小/最大元素（类似优先队列）

TreeMap<Integer, String> map = new TreeMap<>();
// map: {1=a, 2=b, 3=c, 4=d, 5=e}

Map.Entry<Integer, String> min = map.pollFirstEntry();  // {1=a}，map 变为 {2,3,4,5}
Map.Entry<Integer, String> max = map.pollLastEntry();   // {5=e}，map 变为 {2,3,4}
```

---

## 3.3 三种范围视图对比

```java
TreeMap<Integer, String> map = new TreeMap<>();
// map keys: 1 2 3 4 5 6 7 8 9 10

// subMap(from, fromInclusive, to, toInclusive)：双边界
map.subMap(3, true,  7, true);   // [3,7]  keys: 3 4 5 6 7
map.subMap(3, false, 7, false);  // (3,7)  keys: 4 5 6
map.subMap(3, true,  7, false);  // [3,7)  keys: 3 4 5 6

// headMap(to, inclusive)：从头到 to
map.headMap(5, true);   // (-∞, 5]  keys: 1 2 3 4 5
map.headMap(5, false);  // (-∞, 5)  keys: 1 2 3 4

// tailMap(from, inclusive)：从 from 到尾
map.tailMap(7, true);   // [7, +∞)  keys: 7 8 9 10
map.tailMap(7, false);  // (7, +∞)  keys: 8 9 10
```

**范围视图的修改传播**（`TreeMapBasicDemo.java → demonstrateRangeViews()`）：

```java
NavigableMap<Integer, String> sub = map.subMap(3, true, 7, true);
sub.clear();  // 删除 [3,7] 范围内的 key
System.out.println(map.keySet());  // [1, 2, 8, 9, 10]——原 map 也变了
```

---

## 3.4 descendingMap / descendingKeySet：倒序视图

```java
// 获取倒序视图（不是拷贝，是视图）
NavigableMap<Integer, String> desc = map.descendingMap();
System.out.println(desc.keySet());  // [10, 9, 8, ..., 1]

// 只需要倒序 key
NavigableSet<Integer> descKeys = map.descendingKeySet();

// 等价于：new TreeMap<>(Comparator.reverseOrder())
// 但 descendingMap() 是视图，不占额外内存
```

---

## 3.5 实战：区间映射（RangePriceIndex.java）

`RangePriceIndex.java` 展示了 `floorKey` 在区间映射中的标准用法：

```java
// 价格 → 区间（0: 经济, 100: 实惠, 500: 中档, 1000: 高档, 5000: 奢华）
TreeMap<Double, List<Product>> index = new TreeMap<>();

// 查找价格 680 元属于哪个区间
Double key = index.floorKey(680.0);  // 返回 500.0（最后一个 ≤ 680 的区间下限）
List<Product> products = index.get(key);  // 中档商品列表
```

这个模式（`floorKey` + `get`）是 TreeMap 最常见的应用形式，广泛用于：
- 分数 → 等级映射
- 时间戳 → 事件查找
- IP 段 → 地区映射
- 价格段 → 类目映射

---

## 3.6 本章总结

- **六个导航方法**：floor（≤）、ceiling（≥）、lower（<）、higher（>）、first、last
- **Entry 版本**：返回 key-value 对；`pollFirst/Last` 取出并删除
- **三种范围视图**：subMap（双边界）、headMap（到上界）、tailMap（从下界）
- **视图修改传播**：修改范围视图 = 修改原 map，超出范围的操作抛 IllegalArgumentException
- **floorKey 模式**：区间映射的标准用法，`floorKey(x)` 找到包含 x 的区间下限

> **本章对应演示代码**：`TreeMapBasicDemo.java`（导航方法、范围视图）、`RangePriceIndex.java`（floorKey 区间映射）

**继续阅读**：[04_TreeMap vs HashMap vs LinkedHashMap.md](./04_TreeMap_vs_HashMap_vs_LinkedHashMap.md)
