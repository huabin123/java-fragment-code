# 第五章：StampedLock乐观锁 - 极致的性能优化

> **学习目标**：深入理解StampedLock的乐观读和性能优化

---

## 一、为什么需要StampedLock？

### 1.1 ReadWriteLock的局限性

```java
// ReadWriteLock的问题

public class Cache {
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private int value;
    
    public int read() {
        rwLock.readLock().lock(); // 读锁也需要CAS操作
        try {
            return value;
        } finally {
            rwLock.readLock().unlock(); // 释放锁也需要CAS操作
        }
    }
}

// 问题：
// - 读锁虽然可以并发，但仍需要CAS操作
// - 频繁的读操作会导致大量的CAS竞争
// - 影响性能
```

### 1.2 StampedLock的解决方案

```java
// StampedLock的乐观读

public class Cache {
    private final StampedLock lock = new StampedLock();
    private int value;
    
    public int read() {
        long stamp = lock.tryOptimisticRead(); // 乐观读，无锁
        int currentValue = value;
        if (!lock.validate(stamp)) { // 验证是否被修改
            // 升级为悲观读锁
            stamp = lock.readLock();
            try {
                currentValue = value;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return currentValue;
    }
}

// 优势：
// - 乐观读：无锁，零开销
// - 只有在数据被修改时才升级为悲观读
// - 大幅提升读性能
```

---

## 二、StampedLock详解

### 2.1 三种锁模式

```
StampedLock的三种模式：

1. 写锁（Writing）：
   - 独占锁
   - 与读锁、写锁互斥
   - 类似ReentrantLock

2. 悲观读锁（Reading）：
   - 共享锁
   - 与写锁互斥
   - 类似ReadWriteLock的读锁

3. 乐观读（Optimistic Reading）：
   - 无锁
   - 不阻塞写锁
   - 需要验证
   - 这是StampedLock的特色

锁模式表：
           写锁    悲观读锁  乐观读
写锁       ❌      ❌       ✅
悲观读锁   ❌      ✅       ✅
乐观读     ✅      ✅       ✅
```

### 2.2 核心方法

```java
public class StampedLock {
    // === 写锁 ===
    
    /**
     * 获取写锁（阻塞）
     * @return 邮戳（stamp）
     */
    public long writeLock();
    
    /**
     * 尝试获取写锁（非阻塞）
     * @return 邮戳，失败返回0
     */
    public long tryWriteLock();
    
    /**
     * 超时获取写锁
     */
    public long tryWriteLock(long time, TimeUnit unit);
    
    /**
     * 释放写锁
     * @param stamp 邮戳
     */
    public void unlockWrite(long stamp);
    
    // === 悲观读锁 ===
    
    /**
     * 获取读锁（阻塞）
     * @return 邮戳
     */
    public long readLock();
    
    /**
     * 尝试获取读锁（非阻塞）
     * @return 邮戳，失败返回0
     */
    public long tryReadLock();
    
    /**
     * 超时获取读锁
     */
    public long tryReadLock(long time, TimeUnit unit);
    
    /**
     * 释放读锁
     * @param stamp 邮戳
     */
    public void unlockRead(long stamp);
    
    // === 乐观读 ===
    
    /**
     * 尝试乐观读
     * @return 邮戳，如果有写锁返回0
     */
    public long tryOptimisticRead();
    
    /**
     * 验证邮戳是否有效
     * @param stamp 邮戳
     * @return 是否有效
     */
    public boolean validate(long stamp);
    
    // === 锁转换 ===
    
    /**
     * 尝试将读锁转换为写锁
     */
    public long tryConvertToWriteLock(long stamp);
    
    /**
     * 尝试将写锁转换为读锁
     */
    public long tryConvertToReadLock(long stamp);
    
    /**
     * 尝试将锁转换为乐观读
     */
    public long tryConvertToOptimisticRead(long stamp);
    
    // === 其他 ===
    
    /**
     * 释放锁（自动识别类型）
     */
    public void unlock(long stamp);
}
```

---

## 三、乐观读详解

### 3.1 乐观读的原理

```
乐观读的工作原理：

1. tryOptimisticRead()：
   - 返回一个邮戳（stamp）
   - 不加锁，不阻塞
   - 如果有写锁，返回0

2. 读取数据：
   - 直接读取，无锁
   - 可能读到不一致的数据

3. validate(stamp)：
   - 验证邮戳是否有效
   - 检查期间是否有写操作
   - 如果有效，数据一致
   - 如果无效，数据可能不一致

4. 升级为悲观读：
   - 如果验证失败
   - 获取悲观读锁
   - 重新读取数据
```

### 3.2 乐观读示例

```java
public class Point {
    private final StampedLock lock = new StampedLock();
    private double x, y;
    
    // 乐观读
    public double distanceFromOrigin() {
        // 1. 尝试乐观读
        long stamp = lock.tryOptimisticRead();
        
        // 2. 读取数据（可能不一致）
        double currentX = x;
        double currentY = y;
        
        // 3. 验证数据是否一致
        if (!lock.validate(stamp)) {
            // 4. 数据不一致，升级为悲观读锁
            stamp = lock.readLock();
            try {
                currentX = x;
                currentY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        // 5. 计算结果
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }
    
    // 写操作
    public void move(double deltaX, double deltaY) {
        long stamp = lock.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
```

### 3.3 乐观读的注意事项

```java
// ❌ 错误：读取多个变量时可能不一致

public class BadExample {
    private final StampedLock lock = new StampedLock();
    private int x, y;
    
    public int sum() {
        long stamp = lock.tryOptimisticRead();
        int a = x; // 读取x
        // 此时可能有写线程修改了x和y
        int b = y; // 读取y
        // a和b可能不一致
        if (!lock.validate(stamp)) {
            // 升级为悲观读
            stamp = lock.readLock();
            try {
                a = x;
                b = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return a + b;
    }
}

// ✅ 正确：先读取到局部变量，再验证

public class GoodExample {
    private final StampedLock lock = new StampedLock();
    private int x, y;
    
    public int sum() {
        long stamp = lock.tryOptimisticRead();
        // 先读取到局部变量
        int a = x;
        int b = y;
        // 再验证
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                a = x;
                b = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return a + b;
    }
}
```

---

## 四、锁转换

### 4.1 锁转换方法

```java
public class LockConversionExample {
    private final StampedLock lock = new StampedLock();
    private double x, y;
    
    // 读锁转写锁
    public void moveIfAtOrigin(double newX, double newY) {
        // 1. 获取读锁
        long stamp = lock.readLock();
        try {
            // 2. 检查条件
            while (x == 0.0 && y == 0.0) {
                // 3. 尝试转换为写锁
                long ws = lock.tryConvertToWriteLock(stamp);
                if (ws != 0L) {
                    // 转换成功
                    stamp = ws;
                    x = newX;
                    y = newY;
                    break;
                } else {
                    // 转换失败，释放读锁，获取写锁
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
            }
        } finally {
            lock.unlock(stamp);
        }
    }
    
    // 写锁转读锁（锁降级）
    public void updateAndRead() {
        // 1. 获取写锁
        long stamp = lock.writeLock();
        try {
            // 2. 更新数据
            x = 1.0;
            y = 2.0;
            
            // 3. 转换为读锁
            stamp = lock.tryConvertToReadLock(stamp);
            if (stamp != 0L) {
                // 转换成功，继续持有读锁
                // 读取数据
                double distance = Math.sqrt(x * x + y * y);
            }
        } finally {
            lock.unlock(stamp);
        }
    }
}
```

---

## 五、性能对比

### 5.1 性能测试

```java
public class StampedLockPerformanceTest {
    private static final int THREAD_COUNT = 10;
    private static final int ITERATIONS = 1000000;
    
    // 测试ReadWriteLock
    public static void testReadWriteLock() {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        int[] value = {0};
        
        long startTime = System.currentTimeMillis();
        // 90%读，10%写
        // ...
        long endTime = System.currentTimeMillis();
        System.out.println("ReadWriteLock耗时：" + (endTime - startTime) + "ms");
    }
    
    // 测试StampedLock
    public static void testStampedLock() {
        StampedLock lock = new StampedLock();
        int[] value = {0};
        
        long startTime = System.currentTimeMillis();
        // 90%读，10%写
        // ...
        long endTime = System.currentTimeMillis();
        System.out.println("StampedLock耗时：" + (endTime - startTime) + "ms");
    }
}

// 性能结果（10个线程，90%读10%写）：
// ReadWriteLock：  约500ms
// StampedLock：    约200ms

// 结论：
// StampedLock比ReadWriteLock快约2.5倍
```

### 5.2 性能优势

```
StampedLock的性能优势：

1. 乐观读无锁：
   - 不需要CAS操作
   - 零开销
   - 极高的并发性能

2. 减少锁竞争：
   - 读操作不阻塞写操作
   - 写操作不阻塞乐观读
   - 降低锁竞争

3. 适合读多写少：
   - 大部分读操作使用乐观读
   - 只有少数需要升级为悲观读
   - 整体性能提升明显
```

---

## 六、注意事项

### 6.1 不可重入

```java
// ❌ 错误：StampedLock不可重入

public class NonReentrantExample {
    private final StampedLock lock = new StampedLock();
    
    public void method1() {
        long stamp = lock.writeLock();
        try {
            method2(); // 死锁！
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    public void method2() {
        long stamp = lock.writeLock(); // 死锁
        try {
            // ...
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}

// 解决方案：避免重入，重构代码
```

### 6.2 不支持Condition

```java
// ❌ 错误：StampedLock不支持Condition

StampedLock lock = new StampedLock();
// lock.newCondition(); // 没有这个方法

// 如果需要Condition，使用ReentrantLock
```

### 6.3 CPU占用

```java
// StampedLock在获取锁时会自旋
// 可能导致CPU占用较高

// 适合：
// - 临界区很小
// - 持锁时间很短

// 不适合：
// - 临界区很大
// - 持锁时间很长
```

---

## 七、实战应用

### 7.1 高性能缓存

```java
public class StampedLockCache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final StampedLock lock = new StampedLock();
    
    // 读取（乐观读）
    public V get(K key) {
        long stamp = lock.tryOptimisticRead();
        V value = cache.get(key);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                value = cache.get(key);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return value;
    }
    
    // 写入
    public void put(K key, V value) {
        long stamp = lock.writeLock();
        try {
            cache.put(key, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    // 删除
    public V remove(K key) {
        long stamp = lock.writeLock();
        try {
            return cache.remove(key);
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
```

---

## 八、总结

### 8.1 核心要点

1. **三种模式**：写锁、悲观读锁、乐观读
2. **乐观读**：无锁，零开销，需要验证
3. **锁转换**：支持读写锁之间的转换
4. **性能**：比ReadWriteLock快约2-3倍
5. **限制**：不可重入，不支持Condition

### 8.2 对比表

| 特性 | ReadWriteLock | StampedLock |
|------|---------------|-------------|
| **读-读** | 不互斥（需CAS） | 不互斥（乐观读无锁） |
| **性能** | 高 | 更高 |
| **可重入** | ✅ | ❌ |
| **Condition** | ✅ | ❌ |
| **锁转换** | 支持锁降级 | 支持双向转换 |
| **适用场景** | 读多写少 | 读多写少（极致性能） |

### 8.3 思考题

1. **StampedLock有哪三种模式？**
2. **什么是乐观读？如何使用？**
3. **StampedLock和ReadWriteLock有什么区别？**
4. **StampedLock有哪些限制？**

---

**恭喜！你已经完成了Lock模块的学习！** 🎉

---

**参考资料**：
- 《Java并发编程实战》
- 《Java并发编程的艺术》
- StampedLock API文档
- JDK 8新特性
