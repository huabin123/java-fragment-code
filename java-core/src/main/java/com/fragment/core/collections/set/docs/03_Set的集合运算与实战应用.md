# 第三章：Set 的集合运算与实战应用

## 3.1 四种集合运算

`SetBasicDemo.java → demonstrateSetOperations()` 演示了标准集合运算：

```java
Set<Integer> A = {1, 2, 3, 4, 5};
Set<Integer> B = {3, 4, 5, 6, 7};

// 并集（A ∪ B）：A 和 B 的所有元素
Set<Integer> union = new HashSet<>(A);
union.addAll(B);           // {1, 2, 3, 4, 5, 6, 7}

// 交集（A ∩ B）：同时属于 A 和 B 的元素
Set<Integer> intersection = new HashSet<>(A);
intersection.retainAll(B); // {3, 4, 5}

// 差集（A - B）：属于 A 但不属于 B 的元素
Set<Integer> difference = new HashSet<>(A);
difference.removeAll(B);   // {1, 2}

// 对称差（A △ B）：只属于一个集合的元素
Set<Integer> symDiff = new HashSet<>(union);
symDiff.removeAll(intersection); // {1, 2, 6, 7}
```

**注意**：`addAll`、`retainAll`、`removeAll` 都会修改调用者，所以要先拷贝（`new HashSet<>(A)`）再操作，保留原始集合。

---

## 3.2 版本差异分析

`SetApplicationDemo.java → demonstrateDiffAnalysis()` 展示了集合运算在工程中的典型用法：

```java
// 分析两个版本之间依赖的变化
Set<String> v1 = {"spring-core", "mybatis", "mysql-connector", "commons-lang3"};
Set<String> v2 = {"spring-core", "spring-data-jpa", "postgresql", "commons-lang3", "lombok"};

// 新增 = v2 - v1
Set<String> added = new HashSet<>(v2);
added.removeAll(v1);  // {spring-data-jpa, postgresql, lombok}

// 删除 = v1 - v2
Set<String> removed = new HashSet<>(v1);
removed.removeAll(v2);  // {mybatis, mysql-connector}

// 保留 = v1 ∩ v2
Set<String> retained = new HashSet<>(v1);
retained.retainAll(v2);  // {spring-core, commons-lang3}
```

同样的模式可用于：代码变更对比、配置项变化、权限变更审计等。

---

## 3.3 标签系统（交集查询）

`TagSystem.java → findByAllTags()` 实现了基于 Set 交集的多标签过滤：

```java
// 倒排索引：tag → Set<contentId>
Map<String, Set<String>> tagIndex = new HashMap<>();

// 查询同时含 "Java" 和 "JVM" 的文章（交集）
Set<String> result = null;
for (String tag : new String[]{"Java", "JVM"}) {
    Set<String> ids = tagIndex.get(tag);
    if (result == null) result = new HashSet<>(ids);
    else result.retainAll(ids);  // 逐步取交集，每次缩小范围
}
// 只需扫描标签索引，不需要遍历所有文章

// Jaccard 相似度（TagSystem.jaccardSimilarity）：
// 衡量两篇文章的标签相似程度 = |A ∩ B| / |A ∪ B|
// 用于相关内容推荐
```

---

## 3.4 去重的正确姿势

```java
// SetApplicationDemo.java → demonstrateDeduplication()

List<String> withDups = Arrays.asList("a", "b", "a", "c", "b", "d");

// 方案1：去重，不保证顺序（最快）
List<String> deduped = new ArrayList<>(new HashSet<>(withDups));

// 方案2：去重，保持第一次出现的顺序
List<String> dedupedOrdered = new ArrayList<>(new LinkedHashSet<>(withDups));

// 方案3：去重，按自然顺序排序
List<String> dedupedSorted = new ArrayList<>(new TreeSet<>(withDups));

// 方案4：Stream.distinct()（保持遇到顺序，底层也是 LinkedHashSet）
List<String> dedupedStream = withDups.stream().distinct().collect(Collectors.toList());
```

---

## 3.5 权限控制（RBAC）

`AccessControlList.java` 展示了 Set 在权限系统中的应用：

```java
// 角色 → 权限集合（Set 保证权限唯一）
Map<String, Set<String>> rolePermissions = new HashMap<>();
rolePermissions.put("editor", new HashSet<>(Arrays.asList(
    "READ_ARTICLE", "WRITE_ARTICLE", "EDIT_ARTICLE"
)));

// 权限检查：O(1)
boolean canWrite = rolePermissions.get("editor").contains("WRITE_ARTICLE");

// 权限继承（角色继承父角色的权限）：
// 递归合并父角色的 Set → 子角色拥有所有祖先的权限
```

---

## 3.6 本章总结

- **四种集合运算**：并（addAll）、交（retainAll）、差（removeAll）、对称差（union - intersection）
- **操作前先拷贝**：`addAll/retainAll/removeAll` 修改调用者，保留原集合需先 `new HashSet<>(set)`
- **实战场景**：
  - 版本差异分析（差集 + 交集）
  - 多标签过滤（逐步 retainAll 取交集）
  - Jaccard 相似度（内容推荐）
  - 去重（HashSet / LinkedHashSet / TreeSet 各有侧重）
  - 权限控制（RBAC 中权限的存储和继承）

> **本章对应演示代码**：`SetApplicationDemo.java`（去重、权限检查、差异分析、UV 统计）、`TagSystem.java`（交集查询、Jaccard 相似度）

**继续阅读**：[04_TreeSet导航与排序.md](./04_TreeSet导航与排序.md)
