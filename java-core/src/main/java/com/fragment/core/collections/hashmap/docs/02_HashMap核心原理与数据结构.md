# HashMap核心原理与数据结构

## 1. HashMap的底层数据结构

### 1.1 整体架构

HashMap在JDK 1.8中采用了**数组 + 链表 + 红黑树**的混合结构。

```
HashMap内部结构：

table数组（Node<K,V>[] table）
┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
│  0  │  1  │  2  │  3  │  4  │  5  │  6  │  7  │ ...
└──┬──┴─────┴──┬──┴─────┴─────┴──┬──┴─────┴─────┘
   │           │                 │
   ↓           ↓                 ↓
  Node        Node            TreeNode (红黑树根节点)
   ↓           ↓                 ├── TreeNode
  Node        Node               ├── TreeNode
   ↓           ↓                 └── TreeNode
  Node        null
   ↓
  null
```

**三种数据结构的作用**：

1. **数组**：存储桶（bucket），通过hash值快速定位
2. **链表**：解决hash冲突，当冲突较少时使用
3. **红黑树**：优化链表过长的情况，当冲突较多时使用

---

### 1.2 核心字段

```java
public class HashMap<K,V> extends AbstractMap<K,V>
    implements Map<K,V>, Cloneable, Serializable {
    
    // 默认初始容量：16
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // 16
    
    // 最大容量：2^30
    static final int MAXIMUM_CAPACITY = 1 << 30;
    
    // 默认负载因子：0.75
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    
    // 链表转红黑树的阈值：8
    static final int TREEIFY_THRESHOLD = 8;
    
    // 红黑树退化为链表的阈值：6
    static final int UNTREEIFY_THRESHOLD = 6;
    
    // 转红黑树的最小数组容量：64
    static final int MIN_TREEIFY_CAPACITY = 64;
    
    // 存储数据的数组
    transient Node<K,V>[] table;
    
    // 键值对数量
    transient int size;
    
    // 修改次数（用于fail-fast机制）
    transient int modCount;
    
    // 扩容阈值：capacity * loadFactor
    int threshold;
    
    // 负载因子
    final float loadFactor;
}
```

---

### 1.3 Node节点结构

**普通链表节点**：

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;    // hash值（缓存，避免重复计算）
    final K key;       // 键
    V value;           // 值
    Node<K,V> next;    // 指向下一个节点
    
    Node(int hash, K key, V value, Node<K,V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }
    
    public final K getKey()        { return key; }
    public final V getValue()      { return value; }
    public final String toString() { return key + "=" + value; }
    
    public final int hashCode() {
        return Objects.hashCode(key) ^ Objects.hashCode(value);
    }
    
    public final V setValue(V newValue) {
        V oldValue = value;
        value = newValue;
        return oldValue;
    }
    
    public final boolean equals(Object o) {
        if (o == this)
            return true;
        if (o instanceof Map.Entry) {
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            if (Objects.equals(key, e.getKey()) &&
                Objects.equals(value, e.getValue()))
                return true;
        }
        return false;
    }
}
```

**红黑树节点**：

```java
static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
    TreeNode<K,V> parent;  // 父节点
    TreeNode<K,V> left;    // 左子节点
    TreeNode<K,V> right;   // 右子节点
    TreeNode<K,V> prev;    // 前一个节点（维护插入顺序，方便退化为链表）
    boolean red;           // 红黑树颜色：true=红色，false=黑色
    
    TreeNode(int hash, K key, V val, Node<K,V> next) {
        super(hash, key, val, next);
    }
    
    // 返回根节点
    final TreeNode<K,V> root() {
        for (TreeNode<K,V> r = this, p;;) {
            if ((p = r.parent) == null)
                return r;
            r = p;
        }
    }
}
```

---

## 2. Hash函数设计

### 2.1 为什么需要Hash函数？

**目标**：将任意类型的key映射到数组索引。

```
key (任意类型) → hash值 (int) → 数组索引 (0 ~ capacity-1)
```

**挑战**：
1. 不同的key应该尽可能映射到不同的索引（减少冲突）
2. 相同的key必须映射到相同的索引（保证正确性）
3. 计算要快（保证性能）

---

### 2.2 Hash函数实现

#### 步骤1：计算key的hashCode

```java
// 调用key的hashCode()方法
int h = key.hashCode();
```

**示例**：

```java
String key = "hello";
int hashCode = key.hashCode();  // 99162322

// String的hashCode实现：
// s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
// 'h'*31^4 + 'e'*31^3 + 'l'*31^2 + 'l'*31^1 + 'o'*31^0
```

---

#### 步骤2：扰动函数（高位参与运算）

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

**为什么要扰动？**

```
问题：数组长度通常不大，取模时只使用hash值的低位
      如果只使用低位，高位信息丢失，容易冲突

解决：将高16位与低16位异或，让高位也参与运算
```

**示例**：

```
假设key.hashCode() = 0x12345678

原始hash值：
  0001 0010 0011 0100 0101 0110 0111 1000
  
右移16位：
  0000 0000 0000 0000 0001 0010 0011 0100
  
异或运算：
  0001 0010 0011 0100 0101 0110 0111 1000  (原始)
^ 0000 0000 0000 0000 0001 0010 0011 0100  (右移16位)
= 0001 0010 0011 0100 0100 0100 0100 1100  (结果)

结果：高16位的信息也参与到了低16位的运算中
```

**为什么是异或而不是其他运算？**

- **异或**：位运算，速度快；分布均匀
- **加法**：可能溢出
- **乘法**：计算慢
- **或运算**：倾向于1，分布不均
- **与运算**：倾向于0，分布不均

---

#### 步骤3：计算数组索引

```java
// 方法1：取模运算（慢）
int index = hash % capacity;

// 方法2：位运算（快，HashMap采用）
int index = hash & (capacity - 1);
```

**为什么可以用位运算代替取模？**

```
前提：capacity必须是2的幂次方

证明：
  假设capacity = 16 = 2^4 = 0b10000
  capacity - 1 = 15 = 0b01111
  
  hash & (capacity - 1) 等价于 hash % capacity
  
示例：
  hash = 53 = 0b110101
  capacity = 16 = 0b10000
  capacity - 1 = 15 = 0b01111
  
  取模：53 % 16 = 5
  位运算：0b110101 & 0b01111 = 0b00101 = 5
  
结果相同，但位运算更快！
```

---

### 2.3 完整的Hash过程

```java
// 完整示例
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

static final int hash(Object key) {
    int h;
    // 步骤1：计算hashCode
    // 步骤2：高位参与运算（扰动）
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}

final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
    Node<K,V>[] tab;
    int n, i;
    
    // 步骤3：计算数组索引
    // i = hash & (n - 1)，等价于 hash % n
    i = (n - 1) & hash;
    
    // ...
}
```

**流程图**：

```
key: "hello"
  ↓
hashCode(): 99162322
  ↓
扰动函数: 99162322 ^ (99162322 >>> 16) = 99164834
  ↓
位运算: 99164834 & 15 = 2
  ↓
数组索引: 2
```

---

## 3. 为什么容量必须是2的幂次方？

### 3.1 原因1：位运算代替取模

```java
// 只有当capacity是2的幂次方时，以下等式才成立：
hash & (capacity - 1) == hash % capacity

// 位运算比取模快得多
```

---

### 3.2 原因2：扩容时的优化

**扩容规则**：容量翻倍（oldCap * 2）

```java
// 原容量：16 = 0b10000
// 新容量：32 = 0b100000

// 元素的新位置只有两种可能：
// 1. 保持原位置
// 2. 原位置 + oldCap
```

**判断方法**：

```java
if ((e.hash & oldCap) == 0) {
    // 保持原位置
    newTab[j] = loHead;
} else {
    // 原位置 + oldCap
    newTab[j + oldCap] = hiHead;
}
```

**原理**：

```
假设oldCap = 16 = 0b10000，newCap = 32 = 0b100000

元素hash值 = 0b10101 = 21

原索引：
  0b10101 & 0b01111 = 0b00101 = 5

新索引：
  0b10101 & 0b11111 = 0b10101 = 21

判断：
  0b10101 & 0b10000 = 0b10000 ≠ 0
  说明新索引 = 原索引 + oldCap = 5 + 16 = 21 ✓

---

元素hash值 = 0b00101 = 5

原索引：
  0b00101 & 0b01111 = 0b00101 = 5

新索引：
  0b00101 & 0b11111 = 0b00101 = 5

判断：
  0b00101 & 0b10000 = 0b00000 = 0
  说明新索引 = 原索引 = 5 ✓
```

**优势**：
- 无需重新计算hash值
- 通过一次位运算即可确定新位置
- 性能大幅提升

---

### 3.3 如何保证容量是2的幂次方？

```java
// 方法1：构造函数中调整
public HashMap(int initialCapacity, float loadFactor) {
    // ...
    this.threshold = tableSizeFor(initialCapacity);
}

// 方法2：tableSizeFor方法
static final int tableSizeFor(int cap) {
    int n = cap - 1;
    n |= n >>> 1;
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}
```

**tableSizeFor的原理**：

```
目标：找到大于等于cap的最小2的幂次方

示例：cap = 10
  n = cap - 1 = 9 = 0b1001
  
  n |= n >>> 1:  0b1001 | 0b0100 = 0b1101
  n |= n >>> 2:  0b1101 | 0b0011 = 0b1111
  n |= n >>> 4:  0b1111 | 0b0000 = 0b1111
  n |= n >>> 8:  0b1111 | 0b0000 = 0b1111
  n |= n >>> 16: 0b1111 | 0b0000 = 0b1111
  
  n + 1 = 0b1111 + 1 = 0b10000 = 16

结果：16是大于等于10的最小2的幂次方
```

**为什么要先减1？**

```
如果cap本身就是2的幂次方，不减1会得到2倍的结果

示例：cap = 16 = 0b10000
  如果不减1：
    n = 0b10000
    n |= n >>> 1: 0b10000 | 0b01000 = 0b11000
    ...
    最终得到32，错误！
  
  减1后：
    n = 0b01111
    n |= n >>> 1: 0b01111 | 0b00111 = 0b01111
    ...
    n + 1 = 0b10000 = 16，正确！
```

---

## 4. 负载因子（Load Factor）

### 4.1 什么是负载因子？

```java
// 负载因子 = 元素数量 / 数组容量
loadFactor = size / capacity

// 扩容阈值 = 容量 * 负载因子
threshold = capacity * loadFactor

// 当size > threshold时，触发扩容
```

---

### 4.2 为什么默认是0.75？

**负载因子的权衡**：

| 负载因子 | 空间利用率 | 查找效率 | 扩容频率 |
|---------|----------|---------|---------|
| **0.5** | 低（50%） | 高（冲突少） | 高 |
| **0.75** | 中（75%） | 中（冲突适中） | 中 |
| **1.0** | 高（100%） | 低（冲突多） | 低 |

**0.75的优势**：

1. **空间与时间的平衡**：
   - 空间利用率：75%，比较合理
   - 查找效率：冲突概率适中

2. **泊松分布**：
   - 根据泊松分布，负载因子为0.75时，链表长度超过8的概率非常小（约0.00000006）
   - 这也是链表转红黑树阈值设为8的原因

3. **扩容成本**：
   - 扩容需要重新分配内存和rehash，成本较高
   - 0.75可以减少扩容次数

**公式推导**：

```
假设有n个元素，m个桶，负载因子α = n/m

根据泊松分布，桶中有k个元素的概率：
P(k) = (α^k * e^-α) / k!

当α = 0.75时：
P(0) = 0.472  // 47.2%的桶为空
P(1) = 0.354  // 35.4%的桶有1个元素
P(2) = 0.133  // 13.3%的桶有2个元素
P(8) = 0.00000006  // 链表长度达到8的概率极小
```

---

### 4.3 自定义负载因子

```java
// 构造函数允许自定义负载因子
public HashMap(int initialCapacity, float loadFactor) {
    // ...
    this.loadFactor = loadFactor;
    this.threshold = tableSizeFor(initialCapacity);
}

// 示例
Map<String, String> map1 = new HashMap<>(16, 0.5f);  // 空间换时间
Map<String, String> map2 = new HashMap<>(16, 1.0f);  // 时间换空间
```

**选择建议**：

- **内存充足，追求性能**：使用较小的负载因子（如0.5）
- **内存紧张，可接受性能损失**：使用较大的负载因子（如1.0）
- **一般场景**：使用默认值0.75

---

## 5. Hash冲突解决

### 5.1 什么是Hash冲突？

```
不同的key经过hash函数计算后，得到相同的数组索引

key1: "Aa" → hash → index: 5
key2: "BB" → hash → index: 5  // 冲突！
```

**冲突是不可避免的**：
- key的数量是无限的
- 数组的容量是有限的
- 根据鸽巢原理，必然存在冲突

---

### 5.2 解决方案1：链地址法（JDK 1.7）

**原理**：数组的每个位置存储一个链表，冲突的元素添加到链表中。

```
table数组
[0] → null
[1] → Node("Aa", 1) → Node("BB", 2) → null
[2] → Node("key3", 3) → null
[3] → null
```

**优点**：
- 实现简单
- 不会因为冲突而导致数组满

**缺点**：
- 链表过长时，查找效率退化为O(n)
- 内存不连续，缓存不友好

---

### 5.3 解决方案2：链表+红黑树（JDK 1.8）

**原理**：当链表长度超过8且数组容量>=64时，链表转换为红黑树。

```
table数组
[0] → null
[1] → TreeNode (红黑树根节点)
      ├── TreeNode
      ├── TreeNode
      └── TreeNode
[2] → Node → Node → null  (链表)
[3] → null
```

**转换条件**：

```java
// 链表转红黑树的阈值
static final int TREEIFY_THRESHOLD = 8;

// 转红黑树的最小数组容量
static final int MIN_TREEIFY_CAPACITY = 64;

// 转换逻辑
if (binCount >= TREEIFY_THRESHOLD - 1) {
    if (tab == null || tab.length < MIN_TREEIFY_CAPACITY) {
        resize();  // 优先扩容
    } else {
        treeifyBin(tab, hash);  // 转红黑树
    }
}
```

**为什么链表长度阈值是8？**

1. **泊松分布**：
   - 负载因子为0.75时，链表长度达到8的概率约为0.00000006
   - 极少发生，但一旦发生，影响性能

2. **时间与空间的权衡**：
   - 红黑树节点占用空间约为链表节点的2倍
   - 只有在链表很长时，红黑树的性能优势才能抵消空间开销

3. **性能对比**：
   - 链表查找：O(n)
   - 红黑树查找：O(log n)
   - 当n=8时，红黑树的优势开始显现

---

### 5.4 红黑树退化为链表

**退化条件**：

```java
// 红黑树退化为链表的阈值
static final int UNTREEIFY_THRESHOLD = 6;

// 退化时机：
// 1. 扩容时，如果红黑树节点数 <= 6
// 2. 删除节点后，如果红黑树节点数 <= 6
```

**为什么退化阈值是6而不是8？**

```
避免频繁转换

如果也是8：
  添加第9个元素 → 转红黑树
  删除1个元素 → 退化为链表
  再添加1个元素 → 又转红黑树
  ...
  频繁转换，性能损失

使用6：
  添加第9个元素 → 转红黑树
  删除1个元素 → 仍是红黑树（8个节点）
  删除2个元素 → 仍是红黑树（7个节点）
  删除3个元素 → 退化为链表（6个节点）
  
  有一定的缓冲区，避免频繁转换
```

---

### 5.5 其他Hash冲突解决方案

#### 方案1：开放寻址法

**原理**：冲突时，寻找下一个空闲位置。

```
线性探测：
  hash(key) = 5，位置5已占用
  尝试位置6、7、8...直到找到空闲位置

二次探测：
  hash(key) = 5，位置5已占用
  尝试位置5+1^2、5+2^2、5+3^2...

双重散列：
  hash(key) = 5，位置5已占用
  使用第二个hash函数计算步长
```

**优点**：
- 内存连续，缓存友好
- 不需要额外的指针

**缺点**：
- 容易产生聚集
- 删除操作复杂
- 负载因子不能太高

**应用**：ThreadLocalMap使用开放寻址法

---

#### 方案2：再哈希法

**原理**：使用多个hash函数，如果第一个冲突，使用第二个。

```
hash1(key) = 5，位置5已占用
hash2(key) = 8，位置8空闲，存储在位置8
```

**优点**：
- 减少冲突

**缺点**：
- 需要多个hash函数
- 计算开销大

---

#### 方案3：建立公共溢出区

**原理**：将冲突的元素存储在单独的溢出区。

```
基本表：
[0] → value1
[1] → value2
[2] → value3

溢出区：
[0] → value4 (冲突元素)
[1] → value5 (冲突元素)
```

**优点**：
- 基本表不会被冲突元素占用

**缺点**：
- 需要额外的空间
- 查找需要两次

---

## 6. 为什么选择链地址法+红黑树？

### 6.1 方案对比

| 方案 | 时间复杂度 | 空间复杂度 | 实现复杂度 | 缓存友好 |
|------|-----------|-----------|-----------|---------|
| **链地址法** | O(n) | 高 | 低 | 否 |
| **链地址法+红黑树** | O(log n) | 高 | 高 | 否 |
| **开放寻址法** | O(n) | 低 | 中 | 是 |
| **再哈希法** | O(1) | 低 | 高 | 是 |

---

### 6.2 HashMap选择链地址法+红黑树的原因

1. **时间复杂度优秀**：
   - 平均O(1)
   - 最坏O(log n)（红黑树）

2. **实现相对简单**：
   - 链表实现简单
   - 红黑树虽然复杂，但有成熟的实现

3. **不受负载因子限制**：
   - 开放寻址法要求负载因子不能太高
   - 链地址法可以超过1.0

4. **删除操作简单**：
   - 链表删除只需修改指针
   - 开放寻址法删除需要标记

5. **扩展性好**：
   - 可以优化为红黑树
   - 可以优化为跳表等其他结构

---

## 7. 数据结构演进总结

### 7.1 演进路径

```
JDK 1.7：数组 + 链表
  ↓
  问题：链表过长，查找效率低
  ↓
JDK 1.8：数组 + 链表 + 红黑树
  ↓
  优化：链表长度 > 8 时转红黑树
  ↓
  结果：查找效率从O(n)提升到O(log n)
```

---

### 7.2 核心设计思想

1. **空间换时间**：
   - 使用数组实现O(1)查找
   - 使用链表/红黑树解决冲突

2. **动态优化**：
   - 根据链表长度动态转换为红黑树
   - 根据元素数量动态扩容

3. **性能与复杂度的平衡**：
   - 链表实现简单，适合少量冲突
   - 红黑树复杂，但适合大量冲突

4. **位运算优化**：
   - 容量为2的幂次方
   - 使用位运算代替取模
   - 扩容时快速定位新位置

---

### 7.3 关键参数总结

| 参数 | 默认值 | 作用 | 原因 |
|------|-------|------|------|
| **初始容量** | 16 | 数组初始大小 | 2的幂次方，适中 |
| **负载因子** | 0.75 | 扩容阈值 | 空间与时间的平衡 |
| **链表转树阈值** | 8 | 链表转红黑树 | 泊松分布，概率极小 |
| **树退化阈值** | 6 | 红黑树转链表 | 避免频繁转换 |
| **最小树化容量** | 64 | 转树的最小容量 | 优先扩容，减少冲突 |

---

## 8. 下一步学习

在理解了HashMap的核心原理和数据结构后，接下来我们将深入学习：

1. **HashMap源码深度剖析**：分析put/get/resize的实现细节
2. **HashMap线程安全问题**：理解并发问题和解决方案
3. **HashMap最佳实践**：掌握正确的使用方式和性能优化技巧

---

**继续阅读**：[03_HashMap源码深度剖析.md](./03_HashMap源码深度剖析.md)
