# 第四章：ReadWriteLock读写锁 - 读写分离的性能优化

> **学习目标**：深入理解读写锁的原理和使用场景

---

## 一、为什么需要读写锁？

### 1.1 普通锁的问题

```java
// 使用ReentrantLock

public class Cache {
    private final Map<String, Object> map = new HashMap<>();
    private final Lock lock = new ReentrantLock();
    
    public Object get(String key) {
        lock.lock();
        try {
            return map.get(key); // 读操作也需要加锁
        } finally {
            lock.unlock();
        }
    }
    
    public void put(String key, Object value) {
        lock.lock();
        try {
            map.put(key, value);
        } finally {
            lock.unlock();
        }
    }
}

// 问题：
// - 读操作不会修改数据，理论上可以并发
// - 但ReentrantLock是独占锁，读操作也会互斥
// - 降低了并发性能
```

### 1.2 读写锁的解决方案

```java
// 使用ReadWriteLock

public class Cache {
    private final Map<String, Object> map = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    public Object get(String key) {
        readLock.lock();
        try {
            return map.get(key); // 读操作可以并发
        } finally {
            readLock.unlock();
        }
    }
    
    public void put(String key, Object value) {
        writeLock.lock();
        try {
            map.put(key, value); // 写操作独占
        } finally {
            writeLock.unlock();
        }
    }
}

// 优势：
// - 读-读：不互斥，可以并发
// - 读-写：互斥
// - 写-写：互斥
// - 提高了读多写少场景的性能
```

---

## 二、ReadWriteLock接口详解

### 2.1 接口定义

```java
public interface ReadWriteLock {
    /**
     * 返回读锁
     */
    Lock readLock();
    
    /**
     * 返回写锁
     */
    Lock writeLock();
}
```

### 2.2 锁的规则

```
读写锁的规则：

1. 读-读：不互斥
   - 多个线程可以同时持有读锁
   - 提高并发性能

2. 读-写：互斥
   - 读锁和写锁互斥
   - 保证数据一致性

3. 写-写：互斥
   - 写锁是独占的
   - 保证数据一致性

规则表：
         读锁    写锁
读锁     ✅     ❌
写锁     ❌     ❌
```

---

## 三、ReentrantReadWriteLock详解

### 3.1 构造函数

```java
// 非公平锁（默认）
ReadWriteLock rwLock = new ReentrantReadWriteLock();

// 公平锁
ReadWriteLock rwLock = new ReentrantReadWriteLock(true);

// 非公平锁（显式）
ReadWriteLock rwLock = new ReentrantReadWriteLock(false);
```

### 3.2 特有方法

```java
public class ReentrantReadWriteLock implements ReadWriteLock {
    // 查询读锁的持有数量
    public int getReadLockCount();
    
    // 查询写锁的重入次数
    public int getWriteHoldCount();
    
    // 查询当前线程持有读锁的次数
    public int getReadHoldCount();
    
    // 查询是否有线程在等待写锁
    public boolean hasQueuedWriters();
    
    // 查询是否有线程在等待读锁
    public boolean hasQueuedReaders();
    
    // 查询是否有线程在等待
    public boolean hasQueuedThreads();
    
    // 查询等待队列的长度
    public int getQueueLength();
    
    // 查询写锁是否被持有
    public boolean isWriteLocked();
    
    // 查询当前线程是否持有写锁
    public boolean isWriteLockedByCurrentThread();
}
```

---

## 四、锁降级

### 4.1 什么是锁降级？

```
锁降级（Lock Downgrading）：
持有写锁的线程，在不释放写锁的情况下，
获取读锁，然后释放写锁。

写锁 → 写锁+读锁 → 读锁

为什么需要锁降级？
- 保证数据的可见性
- 避免其他线程在中间修改数据
```

### 4.2 锁降级示例

```java
public class CachedData {
    private Object data;
    private volatile boolean cacheValid;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public void processCachedData() {
        // 1. 获取读锁
        rwLock.readLock().lock();
        if (!cacheValid) {
            // 2. 释放读锁，准备获取写锁
            rwLock.readLock().unlock();
            
            // 3. 获取写锁
            rwLock.writeLock().lock();
            try {
                // 4. 再次检查（双重检查）
                if (!cacheValid) {
                    data = loadData();
                    cacheValid = true;
                }
                
                // 5. 锁降级：获取读锁
                rwLock.readLock().lock();
            } finally {
                // 6. 释放写锁
                rwLock.writeLock().unlock();
            }
        }
        
        // 7. 使用数据（持有读锁）
        try {
            useData(data);
        } finally {
            // 8. 释放读锁
            rwLock.readLock().unlock();
        }
    }
    
    private Object loadData() {
        // 加载数据
        return new Object();
    }
    
    private void useData(Object data) {
        // 使用数据
    }
}

// 锁降级的流程：
// 读锁 → 释放读锁 → 写锁 → 写锁+读锁 → 释放写锁 → 读锁 → 释放读锁
```

### 4.3 为什么不支持锁升级？

```
锁升级（Lock Upgrading）：
持有读锁的线程，在不释放读锁的情况下，
获取写锁。

读锁 → 读锁+写锁 → 写锁

为什么不支持？
- 可能导致死锁

示例：
线程A：持有读锁，尝试获取写锁
线程B：持有读锁，尝试获取写锁
结果：死锁（两个线程都在等待对方释放读锁）

// ❌ 错误：尝试锁升级
rwLock.readLock().lock();
try {
    rwLock.writeLock().lock(); // 死锁！
    try {
        // ...
    } finally {
        rwLock.writeLock().unlock();
    }
} finally {
    rwLock.readLock().unlock();
}

// ✅ 正确：先释放读锁，再获取写锁
rwLock.readLock().lock();
try {
    // 读取数据
} finally {
    rwLock.readLock().unlock();
}

rwLock.writeLock().lock();
try {
    // 写入数据
} finally {
    rwLock.writeLock().unlock();
}
```

---

## 五、性能分析

### 5.1 性能测试

```java
public class ReadWriteLockPerformanceTest {
    private static final int THREAD_COUNT = 10;
    private static final int READ_RATIO = 9; // 90%读，10%写
    
    // 测试ReentrantLock
    public static void testReentrantLock() {
        Map<String, String> map = new HashMap<>();
        Lock lock = new ReentrantLock();
        
        long startTime = System.currentTimeMillis();
        // 创建线程...
        long endTime = System.currentTimeMillis();
        System.out.println("ReentrantLock耗时：" + (endTime - startTime) + "ms");
    }
    
    // 测试ReadWriteLock
    public static void testReadWriteLock() {
        Map<String, String> map = new HashMap<>();
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        
        long startTime = System.currentTimeMillis();
        // 创建线程...
        long endTime = System.currentTimeMillis();
        System.out.println("ReadWriteLock耗时：" + (endTime - startTime) + "ms");
    }
}

// 性能结果（10个线程，90%读10%写）：
// ReentrantLock：   约2000ms
// ReadWriteLock：   约500ms

// 结论：
// 读多写少场景，ReadWriteLock性能提升明显
```

### 5.2 适用场景

```
✅ 适合ReadWriteLock：

1. 读多写少：
   - 读操作远多于写操作
   - 读操作耗时较长

2. 数据结构：
   - 缓存
   - 配置信息
   - 统计信息

3. 性能要求：
   - 对读性能要求高
   - 可以接受写性能略低

❌ 不适合ReadWriteLock：

1. 写多读少：
   - 写操作多，读写锁退化为独占锁
   - 性能不如ReentrantLock

2. 读操作很短：
   - 读操作耗时很短
   - 锁的开销大于收益

3. 简单场景：
   - 不需要读写分离
   - 使用ReentrantLock更简单
```

---

## 六、实战应用

### 6.1 缓存实现

```java
public class ReadWriteCache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    // 读取缓存
    public V get(K key) {
        readLock.lock();
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    // 写入缓存
    public void put(K key, V value) {
        writeLock.lock();
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
    
    // 删除缓存
    public V remove(K key) {
        writeLock.lock();
        try {
            return cache.remove(key);
        } finally {
            writeLock.unlock();
        }
    }
    
    // 清空缓存
    public void clear() {
        writeLock.lock();
        try {
            cache.clear();
        } finally {
            writeLock.unlock();
        }
    }
    
    // 获取缓存大小
    public int size() {
        readLock.lock();
        try {
            return cache.size();
        } finally {
            readLock.unlock();
        }
    }
}
```

### 6.2 延迟初始化

```java
public class LazyInitCache {
    private volatile Map<String, Object> cache;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public Object get(String key) {
        // 1. 读锁检查
        rwLock.readLock().lock();
        if (cache != null) {
            try {
                return cache.get(key);
            } finally {
                rwLock.readLock().unlock();
            }
        }
        rwLock.readLock().unlock();
        
        // 2. 写锁初始化
        rwLock.writeLock().lock();
        try {
            // 双重检查
            if (cache == null) {
                cache = loadCache();
            }
            // 锁降级
            rwLock.readLock().lock();
        } finally {
            rwLock.writeLock().unlock();
        }
        
        // 3. 读锁使用
        try {
            return cache.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    private Map<String, Object> loadCache() {
        // 加载缓存
        return new HashMap<>();
    }
}
```

---

## 七、总结

### 7.1 核心要点

1. **读写锁**：读-读不互斥，读-写、写-写互斥
2. **性能**：读多写少场景性能提升明显
3. **锁降级**：写锁→读锁，保证数据可见性
4. **不支持锁升级**：读锁→写锁会死锁
5. **适用场景**：缓存、配置、统计信息

### 7.2 对比表

| 特性 | ReentrantLock | ReadWriteLock |
|------|---------------|---------------|
| **读-读** | 互斥 | 不互斥 |
| **读-写** | 互斥 | 互斥 |
| **写-写** | 互斥 | 互斥 |
| **性能（读多）** | 低 | 高 |
| **性能（写多）** | 高 | 低 |
| **复杂度** | 简单 | 复杂 |

### 7.3 思考题

1. **读写锁的规则是什么？**
2. **什么是锁降级？为什么需要？**
3. **为什么不支持锁升级？**
4. **什么场景适合使用读写锁？**

---

**下一章预告**：我们将学习StampedLock乐观锁的使用和性能优化。

---

**参考资料**：
- 《Java并发编程实战》第13章
- 《Java并发编程的艺术》第5章
- ReadWriteLock API文档
