# HashMap的必要性与演进历史

## 1. 为什么需要HashMap？

### 1.1 数据存储的基本需求

在实际开发中，我们经常需要存储和查找数据。最基本的需求包括：

- **存储**：将数据保存起来
- **查找**：快速找到需要的数据
- **更新**：修改已存储的数据
- **删除**：移除不需要的数据

### 1.2 传统数据结构的局限性

#### 问题1：数组的局限性

**数组的优势**：
- ✅ 随机访问快：通过索引访问元素，时间复杂度O(1)
- ✅ 内存连续：缓存友好，访问效率高

**数组的劣势**：
- ❌ 查找慢：如果不知道索引，需要遍历整个数组，时间复杂度O(n)
- ❌ 插入删除慢：需要移动大量元素，时间复杂度O(n)
- ❌ 容量固定：需要提前确定大小，扩容成本高

**示例场景**：

```java
// 场景：存储用户信息，需要根据用户ID快速查找
String[] users = new String[1000];
users[0] = "user_001:张三";
users[1] = "user_002:李四";
// ...

// 问题：如何快速找到ID为"user_888"的用户？
// 只能遍历整个数组，效率低下
for (int i = 0; i < users.length; i++) {
    if (users[i] != null && users[i].startsWith("user_888:")) {
        // 找到了
        break;
    }
}
// 时间复杂度：O(n)
```

---

#### 问题2：链表的局限性

**链表的优势**：
- ✅ 插入删除快：只需修改指针，时间复杂度O(1)（已知位置）
- ✅ 动态扩展：无需提前确定大小

**链表的劣势**：
- ❌ 查找慢：必须从头遍历，时间复杂度O(n)
- ❌ 内存占用大：需要额外存储指针
- ❌ 缓存不友好：内存不连续

**示例场景**：

```java
// 场景：使用链表存储用户信息
class Node {
    String userId;
    String userName;
    Node next;
}

Node head = new Node();
// 添加用户很方便
Node newNode = new Node();
newNode.userId = "user_888";
newNode.userName = "王五";
newNode.next = head.next;
head.next = newNode;

// 问题：查找用户仍然需要遍历
Node current = head.next;
while (current != null) {
    if ("user_888".equals(current.userId)) {
        // 找到了
        break;
    }
    current = current.next;
}
// 时间复杂度：O(n)
```

---

#### 问题3：二叉搜索树的局限性

**二叉搜索树的优势**：
- ✅ 查找较快：平均时间复杂度O(log n)
- ✅ 有序存储：可以按顺序遍历

**二叉搜索树的劣势**：
- ❌ 可能退化：最坏情况下退化为链表，时间复杂度O(n)
- ❌ 实现复杂：需要维护树的平衡
- ❌ 内存占用大：需要存储左右子节点指针

---

### 1.3 理想的数据结构需求

基于以上分析，我们需要一种数据结构，能够：

1. **快速查找**：时间复杂度接近O(1)
2. **快速插入**：时间复杂度接近O(1)
3. **快速删除**：时间复杂度接近O(1)
4. **动态扩展**：无需提前确定大小
5. **键值对存储**：通过key快速找到value

**这就是HashMap的设计目标！**

---

## 2. HashMap解决了什么核心问题？

### 2.1 核心问题：如何实现O(1)的查找效率？

**HashMap的核心思想**：

```
通过Hash函数，将key映射到数组的索引位置
查找时，通过Hash函数计算索引，直接访问数组元素
```

**示意图**：

```
key: "user_888"
  ↓ Hash函数
hash: 1234567890
  ↓ 取模运算
index: 10
  ↓ 直接访问
array[10] = "王五"
```

**时间复杂度**：
- Hash计算：O(1)
- 数组访问：O(1)
- **总计：O(1)**

---

### 2.2 核心挑战：Hash冲突

**什么是Hash冲突？**

不同的key经过Hash函数计算后，可能得到相同的索引位置。

```
key1: "user_888" → hash → index: 10
key2: "user_999" → hash → index: 10  // 冲突！
```

**HashMap的解决方案**：

1. **链地址法（Separate Chaining）**：
   - 数组的每个位置存储一个链表
   - 冲突的元素添加到链表中
   - JDK 1.7采用此方案

2. **链表+红黑树优化**：
   - 当链表长度超过8时，转换为红黑树
   - 提高查找效率（从O(n)优化到O(log n)）
   - JDK 1.8采用此方案

---

### 2.3 HashMap的核心优势

| 操作 | 时间复杂度 | 说明 |
|------|-----------|------|
| **put** | O(1) | 平均情况，无Hash冲突 |
| **get** | O(1) | 平均情况，无Hash冲突 |
| **remove** | O(1) | 平均情况，无Hash冲突 |
| **containsKey** | O(1) | 平均情况，无Hash冲突 |

**注意**：
- 最坏情况下（所有key都冲突），时间复杂度为O(n)或O(log n)
- 但通过良好的Hash函数和负载因子控制，可以保证平均O(1)

---

## 3. HashMap的演进历史

### 3.1 JDK 1.2：HashMap诞生

**背景**：
- JDK 1.0引入了HashTable
- HashTable是线程安全的，但性能较差（所有方法都加synchronized）
- 需要一个高性能的非线程安全版本

**HashMap的特点**：
- 非线程安全
- 允许null key和null value
- 性能更好

---

### 3.2 JDK 1.7：经典实现

**数据结构**：数组 + 链表

```
HashMap内部结构（JDK 1.7）：

table数组
[0] → Entry → Entry → Entry → null
[1] → null
[2] → Entry → null
[3] → Entry → Entry → null
...
```

**Entry节点结构**：

```java
static class Entry<K,V> implements Map.Entry<K,V> {
    final K key;
    V value;
    Entry<K,V> next;  // 指向下一个节点
    int hash;         // 缓存hash值
}
```

**核心特点**：
- 使用链表解决Hash冲突
- 头插法添加新节点（JDK 1.7）
- 扩容时需要重新计算所有元素的位置

**存在的问题**：
1. **链表过长**：Hash冲突严重时，链表很长，查找效率退化为O(n)
2. **扩容死循环**：多线程并发扩容时，可能形成环形链表，导致死循环
3. **头插法问题**：扩容时使用头插法，会导致链表顺序反转

---

### 3.3 JDK 1.8：重大优化

**数据结构**：数组 + 链表 + 红黑树

```
HashMap内部结构（JDK 1.8）：

table数组
[0] → Node → Node → Node → null  (链表)
[1] → null
[2] → TreeNode (红黑树根节点)
      ├── TreeNode
      ├── TreeNode
      └── TreeNode
[3] → Node → null
...
```

**Node节点结构**：

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;  // 指向下一个节点
}
```

**TreeNode节点结构**：

```java
static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
    TreeNode<K,V> parent;  // 父节点
    TreeNode<K,V> left;    // 左子节点
    TreeNode<K,V> right;   // 右子节点
    TreeNode<K,V> prev;    // 前一个节点（维护插入顺序）
    boolean red;           // 红黑树颜色
}
```

**核心优化**：

#### 优化1：链表转红黑树

```java
// 链表长度阈值
static final int TREEIFY_THRESHOLD = 8;

// 红黑树退化为链表的阈值
static final int UNTREEIFY_THRESHOLD = 6;

// 转红黑树的最小数组容量
static final int MIN_TREEIFY_CAPACITY = 64;
```

**转换规则**：
1. 当链表长度 > 8 且数组容量 >= 64 时，链表转红黑树
2. 当红黑树节点数 < 6 时，红黑树退化为链表

**为什么是8和6？**
- 8：根据泊松分布，链表长度达到8的概率非常小（约0.00000006）
- 6：避免频繁转换（如果也是8，可能会频繁在链表和树之间转换）

---

#### 优化2：尾插法

**JDK 1.7的头插法**：

```java
// 新节点插入到链表头部
void addEntry(int hash, K key, V value, int bucketIndex) {
    Entry<K,V> e = table[bucketIndex];
    table[bucketIndex] = new Entry<>(hash, key, value, e);  // 头插法
}
```

**问题**：
- 扩容时会导致链表顺序反转
- 多线程并发扩容时可能形成环形链表

**JDK 1.8的尾插法**：

```java
// 新节点插入到链表尾部
Node<K,V> newNode = new Node<>(hash, key, value, null);
if (p.next == null) {
    p.next = newNode;  // 尾插法
}
```

**优势**：
- 保持链表顺序
- 避免扩容时的死循环问题（但仍然不是线程安全的）

---

#### 优化3：扩容优化

**JDK 1.7的扩容**：
- 需要重新计算每个元素的hash值
- 需要重新计算每个元素的索引位置
- 时间复杂度：O(n)

**JDK 1.8的扩容优化**：

```java
// 扩容后，元素的新位置只有两种可能：
// 1. 保持原位置
// 2. 原位置 + 原数组长度

// 示例：
// 原数组长度：16 (10000)
// 扩容后长度：32 (100000)
// 
// 元素hash值：10101
// 原索引：10101 & 01111 = 00101 = 5
// 新索引：10101 & 11111 = 10101 = 21 = 5 + 16

// 判断方法：
if ((e.hash & oldCap) == 0) {
    // 保持原位置
} else {
    // 原位置 + oldCap
}
```

**优势**：
- 无需重新计算hash值
- 通过位运算快速确定新位置
- 性能更好

---

#### 优化4：Hash函数优化

**JDK 1.7的Hash函数**：

```java
static int hash(int h) {
    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
}
```

**JDK 1.8的Hash函数**：

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

**优化点**：
- 简化了扰动函数
- 只做一次异或运算
- 性能更好

**为什么要扰动？**

```
原因：数组长度通常不大，取模时只使用hash值的低位
问题：如果只使用低位，高位信息丢失，容易冲突
解决：将高16位与低16位异或，让高位也参与运算
```

**示例**：

```
假设数组长度为16（二进制：10000）

key1.hashCode() = 0x12345678
  高16位：0x1234
  低16位：0x5678
  异或后：0x1234 ^ 0x5678 = 0x444C
  
取模：0x444C & 0x000F = 0x000C = 12

如果不扰动，直接使用低16位：
  0x5678 & 0x000F = 0x0008 = 8
  
扰动后，高位信息也参与了运算，减少冲突
```

---

### 3.4 JDK 1.7 vs JDK 1.8 对比

| 特性 | JDK 1.7 | JDK 1.8 |
|------|---------|---------|
| **数据结构** | 数组+链表 | 数组+链表+红黑树 |
| **插入方式** | 头插法 | 尾插法 |
| **Hash函数** | 4次扰动 | 1次扰动 |
| **扩容优化** | 重新计算hash | 位运算快速定位 |
| **链表查找** | O(n) | O(log n)（转树后） |
| **死循环问题** | 存在 | 不存在（但仍非线程安全） |

---

## 4. HashMap的典型应用场景

### 4.1 缓存

**场景**：将数据库查询结果缓存到内存中，提高访问速度。

```java
// 用户信息缓存
Map<Long, User> userCache = new HashMap<>();

public User getUser(Long userId) {
    // 先从缓存获取
    User user = userCache.get(userId);
    if (user == null) {
        // 缓存未命中，从数据库查询
        user = userDao.queryById(userId);
        // 放入缓存
        userCache.put(userId, user);
    }
    return user;
}
```

---

### 4.2 去重

**场景**：统计文本中不重复的单词数量。

```java
public int countUniqueWords(String text) {
    Map<String, Integer> wordCount = new HashMap<>();
    String[] words = text.split("\\s+");
    
    for (String word : words) {
        wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
    }
    
    return wordCount.size();  // 不重复单词数量
}
```

---

### 4.3 索引

**场景**：根据ID快速查找对象。

```java
// 订单索引
Map<String, Order> orderIndex = new HashMap<>();

// 批量加载订单
List<Order> orders = orderDao.queryAll();
for (Order order : orders) {
    orderIndex.put(order.getOrderId(), order);
}

// 快速查找
Order order = orderIndex.get("ORDER_12345");  // O(1)
```

---

### 4.4 分组

**场景**：将数据按某个字段分组。

```java
// 按部门分组员工
Map<String, List<Employee>> employeesByDept = new HashMap<>();

for (Employee emp : employees) {
    String dept = emp.getDepartment();
    employeesByDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(emp);
}
```

---

### 4.5 配置管理

**场景**：存储应用配置项。

```java
// 配置项
Map<String, String> config = new HashMap<>();
config.put("db.url", "jdbc:mysql://localhost:3306/test");
config.put("db.username", "root");
config.put("db.password", "123456");

// 读取配置
String dbUrl = config.get("db.url");
```

---

## 5. HashMap出现之前如何解决问题？

### 5.1 使用数组

**方案**：使用数组存储数据，通过遍历查找。

```java
String[] users = new String[1000];
// 查找时需要遍历
for (String user : users) {
    if (user != null && user.startsWith(targetId)) {
        // 找到了
    }
}
```

**缺点**：
- 查找慢：O(n)
- 容量固定

---

### 5.2 使用HashTable

**方案**：使用HashTable（JDK 1.0引入）。

```java
Hashtable<String, User> users = new Hashtable<>();
users.put("user_001", user1);
User user = users.get("user_001");
```

**缺点**：
- 线程安全，但性能差（所有方法都加synchronized）
- 不允许null key和null value
- 已被HashMap取代

---

### 5.3 使用Properties

**方案**：使用Properties（继承自Hashtable）。

```java
Properties props = new Properties();
props.setProperty("db.url", "jdbc:mysql://localhost:3306/test");
String dbUrl = props.getProperty("db.url");
```

**缺点**：
- 只能存储String类型
- 性能差（继承自Hashtable）

---

### 5.4 自定义Hash表

**方案**：自己实现简单的Hash表。

```java
class SimpleHashMap {
    private Entry[] table;
    
    static class Entry {
        String key;
        Object value;
        Entry next;
    }
    
    public void put(String key, Object value) {
        int index = key.hashCode() % table.length;
        // 链地址法处理冲突
        Entry entry = new Entry();
        entry.key = key;
        entry.value = value;
        entry.next = table[index];
        table[index] = entry;
    }
}
```

**缺点**：
- 实现复杂
- 功能不完善
- 性能不佳

---

## 6. HashMap与其他Map实现的对比

### 6.1 HashMap vs HashTable

| 特性 | HashMap | HashTable |
|------|---------|-----------|
| **线程安全** | 否 | 是（synchronized） |
| **null key** | 允许1个 | 不允许 |
| **null value** | 允许多个 | 不允许 |
| **性能** | 高 | 低 |
| **推荐使用** | 是 | 否（已过时） |

**结论**：
- 单线程场景：使用HashMap
- 多线程场景：使用ConcurrentHashMap（不要用HashTable）

---

### 6.2 HashMap vs TreeMap

| 特性 | HashMap | TreeMap |
|------|---------|---------|
| **底层结构** | 数组+链表+红黑树 | 红黑树 |
| **是否有序** | 无序 | 按key排序 |
| **put性能** | O(1) | O(log n) |
| **get性能** | O(1) | O(log n) |
| **适用场景** | 快速查找 | 需要排序 |

**选择建议**：
- 需要快速查找：使用HashMap
- 需要排序：使用TreeMap
- 需要保持插入顺序：使用LinkedHashMap

---

### 6.3 HashMap vs LinkedHashMap

| 特性 | HashMap | LinkedHashMap |
|------|---------|---------------|
| **底层结构** | 数组+链表+红黑树 | HashMap+双向链表 |
| **是否有序** | 无序 | 有序（插入/访问顺序） |
| **性能** | 高 | 稍低 |
| **内存占用** | 中 | 高 |
| **适用场景** | 一般场景 | 需要保持顺序、LRU缓存 |

---

### 6.4 HashMap vs ConcurrentHashMap

| 特性 | HashMap | ConcurrentHashMap |
|------|---------|-------------------|
| **线程安全** | 否 | 是 |
| **锁机制** | 无 | 分段锁/CAS |
| **性能** | 高 | 中 |
| **null key** | 允许 | 不允许 |
| **适用场景** | 单线程 | 多线程 |

---

## 7. 总结

### 7.1 HashMap的核心价值

1. **高效查找**：平均O(1)的时间复杂度
2. **动态扩展**：无需提前确定大小
3. **灵活使用**：支持null key和null value
4. **性能优异**：JDK 1.8的优化使性能更好

---

### 7.2 HashMap的演进

```
JDK 1.2：HashMap诞生
  ↓
JDK 1.7：数组+链表，头插法
  ↓
JDK 1.8：数组+链表+红黑树，尾插法，扩容优化
  ↓
未来：持续优化
```

---

### 7.3 何时使用HashMap

**适合使用HashMap的场景**：
- ✅ 需要快速查找（O(1)）
- ✅ 键值对存储
- ✅ 单线程环境
- ✅ 不需要排序
- ✅ 不需要保持插入顺序

**不适合使用HashMap的场景**：
- ❌ 多线程并发访问（使用ConcurrentHashMap）
- ❌ 需要排序（使用TreeMap）
- ❌ 需要保持插入顺序（使用LinkedHashMap）
- ❌ 需要线程安全（使用ConcurrentHashMap）

---

### 7.4 下一步学习

在理解了HashMap的必要性和演进历史后，接下来我们将深入学习：

1. **HashMap核心原理与数据结构**：深入理解数组+链表+红黑树的设计
2. **HashMap源码深度剖析**：分析put/get/resize的实现细节
3. **HashMap线程安全问题**：理解并发问题和解决方案
4. **HashMap最佳实践**：掌握正确的使用方式和性能优化技巧

---

**继续阅读**：[02_HashMap核心原理与数据结构.md](./02_HashMap核心原理与数据结构.md)
