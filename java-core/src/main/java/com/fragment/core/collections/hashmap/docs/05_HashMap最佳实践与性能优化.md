# HashMap最佳实践与性能优化

## 1. 初始容量与负载因子的选择

### 1.1 为什么要设置初始容量？

**问题**：如果不设置初始容量，HashMap会频繁扩容。

```java
// 默认初始容量：16
Map<String, String> map = new HashMap<>();

// 添加1000个元素
for (int i = 0; i < 1000; i++) {
    map.put("key" + i, "value" + i);
}

// 扩容次数：
// 16 → 32 → 64 → 128 → 256 → 512 → 1024
// 共扩容6次
```

**扩容的成本**：
1. 创建新数组
2. 重新计算所有元素的位置
3. 迁移所有元素
4. 时间复杂度：O(n)

---

### 1.2 如何计算合适的初始容量？

**公式**：

```
初始容量 = 预期元素数量 / 负载因子 + 1

默认负载因子 = 0.75
初始容量 = 预期元素数量 / 0.75 + 1
```

**示例**：

```java
// 预期存储1000个元素
int expectedSize = 1000;

// ❌ 错误：使用默认容量
Map<String, String> map1 = new HashMap<>();
// 会扩容多次

// ❌ 错误：直接使用预期大小
Map<String, String> map2 = new HashMap<>(1000);
// 实际容量：1024（大于等于1000的最小2的幂次方）
// 扩容阈值：1024 * 0.75 = 768
// 当元素数量达到768时，仍然会扩容

// ✅ 正确：根据公式计算
int initialCapacity = (int) (expectedSize / 0.75f + 1);
Map<String, String> map3 = new HashMap<>(initialCapacity);
// initialCapacity = 1334
// 实际容量：2048（大于等于1334的最小2的幂次方）
// 扩容阈值：2048 * 0.75 = 1536
// 可以容纳1000个元素而不扩容
```

**工具方法**：

```java
public class HashMapUtils {
    /**
     * 计算HashMap的初始容量
     * @param expectedSize 预期元素数量
     * @return 初始容量
     */
    public static int calculateInitialCapacity(int expectedSize) {
        return (int) (expectedSize / 0.75f + 1);
    }
    
    /**
     * 创建HashMap，指定预期大小
     * @param expectedSize 预期元素数量
     * @return HashMap实例
     */
    public static <K, V> Map<K, V> newHashMap(int expectedSize) {
        return new HashMap<>(calculateInitialCapacity(expectedSize));
    }
}

// 使用
Map<String, String> map = HashMapUtils.newHashMap(1000);
```

---

### 1.3 负载因子的选择

**默认值**：0.75

**选择建议**：

| 负载因子 | 空间利用率 | 查找效率 | 扩容频率 | 适用场景 |
|---------|----------|---------|---------|---------|
| **0.5** | 低（50%） | 高（冲突少） | 高 | 内存充足，追求性能 |
| **0.75** | 中（75%） | 中（冲突适中） | 中 | 一般场景（推荐） |
| **1.0** | 高（100%） | 低（冲突多） | 低 | 内存紧张，可接受性能损失 |

**自定义负载因子**：

```java
// 追求性能，内存充足
Map<String, String> map1 = new HashMap<>(16, 0.5f);

// 节省内存，可接受性能损失
Map<String, String> map2 = new HashMap<>(16, 1.0f);

// 一般场景，使用默认值
Map<String, String> map3 = new HashMap<>(16);  // 默认0.75f
```

---

## 2. 自定义对象作为key的注意事项

### 2.1 必须正确实现hashCode和equals

**问题**：如果不正确实现，HashMap无法正常工作。

```java
// ❌ 错误：没有重写hashCode和equals
class User {
    private String name;
    private int age;
    
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

Map<User, String> map = new HashMap<>();
User user1 = new User("张三", 20);
map.put(user1, "value1");

User user2 = new User("张三", 20);
String value = map.get(user2);  // null，找不到！

// 原因：user1和user2的hashCode不同，equals返回false
```

**✅ 正确实现**：

```java
class User {
    private String name;
    private int age;
    
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return age == user.age && Objects.equals(name, user.name);
    }
}

Map<User, String> map = new HashMap<>();
User user1 = new User("张三", 20);
map.put(user1, "value1");

User user2 = new User("张三", 20);
String value = map.get(user2);  // "value1"，找到了！
```

---

### 2.2 hashCode和equals的契约

**规则1**：如果两个对象equals返回true，则hashCode必须相同

```java
// ✅ 正确
if (obj1.equals(obj2)) {
    assert obj1.hashCode() == obj2.hashCode();
}

// ❌ 错误：违反契约
class BadUser {
    private String name;
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BadUser) {
            return name.equals(((BadUser) obj).name);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return 1;  // 总是返回1，但equals可能返回false
    }
}
```

**规则2**：如果两个对象hashCode相同，equals不一定返回true

```java
// ✅ 允许：hash冲突
User user1 = new User("张三", 20);
User user2 = new User("李四", 25);

// 可能：user1.hashCode() == user2.hashCode()
// 但是：user1.equals(user2) == false
```

**规则3**：如果重写了equals，必须重写hashCode

```java
// ❌ 错误：只重写equals，不重写hashCode
class BadUser {
    private String name;
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BadUser) {
            return name.equals(((BadUser) obj).name);
        }
        return false;
    }
    
    // 没有重写hashCode，使用Object的默认实现
    // 导致equals相同的对象，hashCode不同
}
```

---

### 2.3 hashCode的实现建议

**方法1**：使用Objects.hash（推荐）

```java
@Override
public int hashCode() {
    return Objects.hash(field1, field2, field3);
}
```

**方法2**：手动实现

```java
@Override
public int hashCode() {
    int result = 17;  // 初始值，任意非零值
    result = 31 * result + (field1 != null ? field1.hashCode() : 0);
    result = 31 * result + field2;
    result = 31 * result + (field3 != null ? field3.hashCode() : 0);
    return result;
}
```

**为什么使用31？**

1. **质数**：减少冲突
2. **性能**：31 * i = (i << 5) - i，可以优化为位运算
3. **经验值**：经过大量测试，31是一个较好的选择

---

### 2.4 不可变对象作为key（推荐）

**问题**：如果key是可变的，修改key后无法找到对应的value。

```java
// ❌ 错误：使用可变对象作为key
class MutableUser {
    private String name;
    
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MutableUser) {
            return name.equals(((MutableUser) obj).name);
        }
        return false;
    }
}

Map<MutableUser, String> map = new HashMap<>();
MutableUser user = new MutableUser();
user.setName("张三");
map.put(user, "value1");

// 修改key
user.setName("李四");

// 无法找到
String value = map.get(user);  // null，找不到！

// 原因：hashCode变了，索引位置变了
```

**✅ 正确：使用不可变对象作为key**

```java
class ImmutableUser {
    private final String name;  // final，不可变
    
    public ImmutableUser(String name) {
        this.name = name;
    }
    
    // 没有setter方法
    
    @Override
    public int hashCode() {
        return name.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ImmutableUser) {
            return name.equals(((ImmutableUser) obj).name);
        }
        return false;
    }
}

Map<ImmutableUser, String> map = new HashMap<>();
ImmutableUser user = new ImmutableUser("张三");
map.put(user, "value1");

// 无法修改key，安全
String value = map.get(user);  // "value1"，找到了！
```

**推荐的不可变key类型**：
- String
- Integer、Long等包装类
- 自定义的不可变类

---

## 3. 遍历方式的选择

### 3.1 四种遍历方式

```java
Map<String, String> map = new HashMap<>();
map.put("key1", "value1");
map.put("key2", "value2");
map.put("key3", "value3");

// 方式1：遍历keySet
for (String key : map.keySet()) {
    String value = map.get(key);
    System.out.println(key + " = " + value);
}

// 方式2：遍历entrySet（推荐）
for (Map.Entry<String, String> entry : map.entrySet()) {
    String key = entry.getKey();
    String value = entry.getValue();
    System.out.println(key + " = " + value);
}

// 方式3：遍历values
for (String value : map.values()) {
    System.out.println(value);
}

// 方式4：使用迭代器
Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
while (iterator.hasNext()) {
    Map.Entry<String, String> entry = iterator.next();
    String key = entry.getKey();
    String value = entry.getValue();
    System.out.println(key + " = " + value);
}

// 方式5：JDK 8的forEach（推荐）
map.forEach((key, value) -> {
    System.out.println(key + " = " + value);
});
```

---

### 3.2 性能对比

**测试代码**：

```java
Map<String, String> map = new HashMap<>();
for (int i = 0; i < 1000000; i++) {
    map.put("key" + i, "value" + i);
}

// 方式1：keySet + get
long start1 = System.currentTimeMillis();
for (String key : map.keySet()) {
    String value = map.get(key);
}
long end1 = System.currentTimeMillis();
System.out.println("keySet + get: " + (end1 - start1) + "ms");

// 方式2：entrySet
long start2 = System.currentTimeMillis();
for (Map.Entry<String, String> entry : map.entrySet()) {
    String key = entry.getKey();
    String value = entry.getValue();
}
long end2 = System.currentTimeMillis();
System.out.println("entrySet: " + (end2 - start2) + "ms");
```

**结果**：

```
keySet + get: 150ms
entrySet: 50ms
```

**结论**：
- **entrySet比keySet快3倍**
- 原因：keySet需要调用get方法，entrySet直接获取Entry

**推荐**：
- ✅ 需要key和value：使用entrySet
- ✅ 只需要key：使用keySet
- ✅ 只需要value：使用values

---

### 3.3 遍历时删除元素

```java
Map<String, String> map = new HashMap<>();
map.put("key1", "value1");
map.put("key2", "value2");
map.put("key3", "value3");

// ❌ 错误：直接删除
for (String key : map.keySet()) {
    map.remove(key);  // ConcurrentModificationException
}

// ✅ 正确：使用迭代器
Iterator<String> iterator = map.keySet().iterator();
while (iterator.hasNext()) {
    String key = iterator.next();
    iterator.remove();  // 使用迭代器的remove方法
}

// ✅ 正确：JDK 8的removeIf
map.keySet().removeIf(key -> key.startsWith("key"));

// ✅ 正确：先收集要删除的key，再删除
List<String> keysToRemove = new ArrayList<>();
for (String key : map.keySet()) {
    if (shouldRemove(key)) {
        keysToRemove.add(key);
    }
}
for (String key : keysToRemove) {
    map.remove(key);
}
```

---

## 4. 常见陷阱与解决方案

### 4.1 陷阱1：使用可变对象作为key

**问题**：修改key后无法找到对应的value。

**解决方案**：使用不可变对象作为key。

---

### 4.2 陷阱2：没有设置初始容量

**问题**：频繁扩容，性能损失。

**解决方案**：根据预期大小设置初始容量。

```java
// ❌ 错误
Map<String, String> map = new HashMap<>();

// ✅ 正确
int expectedSize = 1000;
Map<String, String> map = new HashMap<>((int) (expectedSize / 0.75f + 1));
```

---

### 4.3 陷阱3：在多线程环境下使用HashMap

**问题**：数据不一致、死循环等。

**解决方案**：使用ConcurrentHashMap。

```java
// ❌ 错误
Map<String, String> map = new HashMap<>();

// ✅ 正确
Map<String, String> map = new ConcurrentHashMap<>();
```

---

### 4.4 陷阱4：使用keySet遍历并获取value

**问题**：性能差。

**解决方案**：使用entrySet。

```java
// ❌ 错误
for (String key : map.keySet()) {
    String value = map.get(key);  // 每次都要查找
}

// ✅ 正确
for (Map.Entry<String, String> entry : map.entrySet()) {
    String key = entry.getKey();
    String value = entry.getValue();  // 直接获取
}
```

---

### 4.5 陷阱5：频繁使用containsKey + get

**问题**：两次查找，性能损失。

**解决方案**：直接使用get，判断返回值。

```java
// ❌ 错误
if (map.containsKey("key")) {
    String value = map.get("key");  // 第二次查找
    // 处理value
}

// ✅ 正确
String value = map.get("key");
if (value != null) {
    // 处理value
}

// ✅ 更好：JDK 8的getOrDefault
String value = map.getOrDefault("key", "defaultValue");
```

---

### 4.6 陷阱6：没有正确实现hashCode

**问题**：hash冲突严重，性能退化。

**解决方案**：使用Objects.hash或正确实现hashCode。

```java
// ❌ 错误：所有对象的hashCode都相同
@Override
public int hashCode() {
    return 1;  // 所有元素都在同一个桶中，性能退化为O(n)
}

// ✅ 正确
@Override
public int hashCode() {
    return Objects.hash(field1, field2, field3);
}
```

---

## 5. 性能优化技巧

### 5.1 减少hash冲突

**技巧1**：实现良好的hashCode方法

```java
// ✅ 好的hashCode：分布均匀
@Override
public int hashCode() {
    return Objects.hash(name, age, email);
}

// ❌ 坏的hashCode：分布不均
@Override
public int hashCode() {
    return age;  // 如果age的范围很小，冲突严重
}
```

**技巧2**：选择合适的初始容量

```java
// 初始容量太小，频繁扩容
Map<String, String> map1 = new HashMap<>(2);

// 初始容量合适，避免扩容
Map<String, String> map2 = new HashMap<>(1024);
```

---

### 5.2 减少扩容次数

**技巧**：根据预期大小设置初始容量。

```java
// 预期存储10000个元素
int expectedSize = 10000;
int initialCapacity = (int) (expectedSize / 0.75f + 1);
Map<String, String> map = new HashMap<>(initialCapacity);
```

---

### 5.3 使用合适的遍历方式

**技巧**：使用entrySet而不是keySet。

```java
// ❌ 慢
for (String key : map.keySet()) {
    String value = map.get(key);
}

// ✅ 快
for (Map.Entry<String, String> entry : map.entrySet()) {
    String key = entry.getKey();
    String value = entry.getValue();
}
```

---

### 5.4 使用JDK 8的新方法

**computeIfAbsent**：如果key不存在，则计算并put

```java
// ❌ 旧方法
List<String> list = map.get("key");
if (list == null) {
    list = new ArrayList<>();
    map.put("key", list);
}
list.add("value");

// ✅ 新方法
map.computeIfAbsent("key", k -> new ArrayList<>()).add("value");
```

**merge**：合并值

```java
// ❌ 旧方法
Integer count = map.get("key");
if (count == null) {
    map.put("key", 1);
} else {
    map.put("key", count + 1);
}

// ✅ 新方法
map.merge("key", 1, Integer::sum);
```

---

### 5.5 避免装箱拆箱

**问题**：使用包装类作为value，频繁装箱拆箱。

```java
// ❌ 性能差：频繁装箱拆箱
Map<String, Integer> map = new HashMap<>();
for (int i = 0; i < 1000000; i++) {
    Integer count = map.get("key");  // 拆箱
    if (count == null) {
        count = 0;
    }
    map.put("key", count + 1);  // 装箱
}

// ✅ 性能好：使用专门的库
// 使用fastutil、trove等库，提供基本类型的Map
// Int2IntMap map = new Int2IntOpenHashMap();
```

---

## 6. 内存占用分析

### 6.1 HashMap的内存结构

```
HashMap对象：
├── table数组引用：8字节
├── size：4字节
├── modCount：4字节
├── threshold：4字节
├── loadFactor：4字节
├── 对象头：12字节（压缩指针）
└── 对齐：4字节
总计：40字节

Node节点：
├── hash：4字节
├── key引用：8字节
├── value引用：8字节
├── next引用：8字节
├── 对象头：12字节
└── 对齐：4字节
总计：44字节

table数组：
├── 数组对象头：16字节
└── 元素引用：capacity * 8字节
总计：16 + capacity * 8字节
```

---

### 6.2 内存占用计算

**示例**：存储1000个键值对

```
假设：
- capacity = 2048（大于等于1000/0.75的最小2的幂次方）
- 每个key和value都是String，平均长度10

计算：
HashMap对象：40字节
table数组：16 + 2048 * 8 = 16400字节
Node节点：1000 * 44 = 44000字节
key字符串：1000 * (24 + 10 * 2) = 44000字节（对象头24字节，char数组10*2字节）
value字符串：1000 * (24 + 10 * 2) = 44000字节

总计：40 + 16400 + 44000 + 44000 + 44000 = 148440字节 ≈ 145KB

平均每个键值对：145KB / 1000 = 148字节
```

---

### 6.3 内存优化建议

**建议1**：使用合适的初始容量，避免浪费

```java
// ❌ 浪费：capacity = 2048，只存储100个元素
Map<String, String> map = new HashMap<>(2048);

// ✅ 合适：capacity = 256
Map<String, String> map = new HashMap<>(128);
```

**建议2**：如果value是基本类型，考虑使用专门的库

```java
// ❌ 内存占用大：Integer对象占用16字节
Map<String, Integer> map = new HashMap<>();

// ✅ 内存占用小：int占用4字节
// 使用fastutil等库
// Object2IntMap<String> map = new Object2IntOpenHashMap<>();
```

**建议3**：如果不需要null key，考虑使用ConcurrentHashMap

```java
// HashMap允许null key，需要特殊处理
Map<String, String> map = new HashMap<>();

// ConcurrentHashMap不允许null key，实现更简单，内存占用稍小
Map<String, String> map = new ConcurrentHashMap<>();
```

---

## 7. 实际项目应用

### 7.1 缓存实现

```java
public class UserCache {
    private final Map<Long, User> cache;
    private final int maxSize;
    
    public UserCache(int maxSize) {
        this.maxSize = maxSize;
        // 使用LinkedHashMap实现LRU缓存
        this.cache = new LinkedHashMap<Long, User>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, User> eldest) {
                return size() > maxSize;
            }
        };
    }
    
    public User get(Long userId) {
        User user = cache.get(userId);
        if (user == null) {
            // 从数据库加载
            user = loadFromDatabase(userId);
            cache.put(userId, user);
        }
        return user;
    }
    
    private User loadFromDatabase(Long userId) {
        // 从数据库加载用户
        return null;
    }
}
```

---

### 7.2 分组统计

```java
public class OrderStatistics {
    /**
     * 按用户分组统计订单
     */
    public Map<Long, List<Order>> groupByUser(List<Order> orders) {
        Map<Long, List<Order>> result = new HashMap<>();
        
        for (Order order : orders) {
            result.computeIfAbsent(order.getUserId(), k -> new ArrayList<>())
                  .add(order);
        }
        
        return result;
    }
    
    /**
     * 统计每个用户的订单数量
     */
    public Map<Long, Integer> countByUser(List<Order> orders) {
        Map<Long, Integer> result = new HashMap<>();
        
        for (Order order : orders) {
            result.merge(order.getUserId(), 1, Integer::sum);
        }
        
        return result;
    }
}
```

---

### 7.3 去重

```java
public class DuplicateRemover {
    /**
     * 去除重复元素
     */
    public <T> List<T> removeDuplicates(List<T> list) {
        // 使用LinkedHashMap保持顺序
        Map<T, Boolean> map = new LinkedHashMap<>();
        for (T item : list) {
            map.put(item, Boolean.TRUE);
        }
        return new ArrayList<>(map.keySet());
    }
    
    /**
     * 找出重复的元素
     */
    public <T> Set<T> findDuplicates(List<T> list) {
        Map<T, Integer> countMap = new HashMap<>();
        for (T item : list) {
            countMap.merge(item, 1, Integer::sum);
        }
        
        Set<T> duplicates = new HashSet<>();
        for (Map.Entry<T, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() > 1) {
                duplicates.add(entry.getKey());
            }
        }
        return duplicates;
    }
}
```

---

## 8. 最佳实践清单

### 8.1 创建HashMap

- ✅ 如果知道元素数量，设置合适的初始容量
- ✅ 使用默认负载因子0.75（一般场景）
- ✅ 单线程使用HashMap，多线程使用ConcurrentHashMap

```java
// ✅ 推荐
int expectedSize = 1000;
Map<String, String> map = new HashMap<>((int) (expectedSize / 0.75f + 1));
```

---

### 8.2 使用自定义对象作为key

- ✅ 必须重写hashCode和equals
- ✅ 使用不可变对象作为key
- ✅ hashCode要分布均匀

```java
// ✅ 推荐
class User {
    private final String name;  // final，不可变
    private final int age;
    
    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }
    
    @Override
    public boolean equals(Object obj) {
        // 正确实现
    }
}
```

---

### 8.3 遍历HashMap

- ✅ 需要key和value：使用entrySet
- ✅ 只需要key：使用keySet
- ✅ 只需要value：使用values
- ✅ 使用JDK 8的forEach

```java
// ✅ 推荐
map.forEach((key, value) -> {
    System.out.println(key + " = " + value);
});
```

---

### 8.4 修改HashMap

- ✅ 遍历时删除：使用迭代器的remove方法
- ✅ 使用JDK 8的新方法：computeIfAbsent、merge等
- ✅ 避免频繁的containsKey + get

```java
// ✅ 推荐
map.computeIfAbsent("key", k -> new ArrayList<>()).add("value");
map.merge("key", 1, Integer::sum);
```

---

### 8.5 性能优化

- ✅ 设置合适的初始容量，减少扩容
- ✅ 实现良好的hashCode，减少冲突
- ✅ 使用entrySet遍历，而不是keySet
- ✅ 使用JDK 8的新方法，简化代码

---

## 9. 总结

### 9.1 核心要点

1. **初始容量**：根据预期大小设置，公式：expectedSize / 0.75 + 1
2. **负载因子**：默认0.75，平衡空间和时间
3. **自定义key**：必须重写hashCode和equals，使用不可变对象
4. **遍历方式**：使用entrySet，性能最好
5. **线程安全**：多线程使用ConcurrentHashMap
6. **JDK 8新方法**：computeIfAbsent、merge等，简化代码

---

### 9.2 性能优化总结

| 优化点 | 优化前 | 优化后 | 提升 |
|-------|-------|-------|------|
| **设置初始容量** | 频繁扩容 | 无扩容 | 50% |
| **使用entrySet** | keySet + get | entrySet | 3倍 |
| **实现良好的hashCode** | 严重冲突 | 均匀分布 | 10倍 |
| **使用computeIfAbsent** | containsKey + get + put | computeIfAbsent | 2倍 |

---

### 9.3 继续学习

完成HashMap的学习后，建议继续学习：

1. **LinkedList**：双向链表实现，适合频繁插入删除
2. **LinkedHashMap**：保持插入顺序，实现LRU缓存
3. **TreeMap**：红黑树实现，保持排序
4. **ConcurrentHashMap**：并发Map，高性能

---

**恭喜你完成了HashMap的深度学习！🎉**
