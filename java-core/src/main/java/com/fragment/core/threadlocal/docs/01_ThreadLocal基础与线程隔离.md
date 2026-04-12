# 第一章：ThreadLocal 基础与线程隔离

## 1.1 ThreadLocal 解决的核心问题

多线程共享数据需要加锁，但有一类数据天然不需要共享——**每个线程自己的上下文信息**：

```
Web 请求处理中：
  线程1 → 用户A的请求 → 需要携带：用户A的 userId、traceId、locale...
  线程2 → 用户B的请求 → 需要携带：用户B的 userId、traceId、locale...
```

如果把这些信息放在共享变量里，每次访问都需要加锁，而且线程间会相互干扰。ThreadLocal 为每个线程提供独立的变量副本，线程只操作自己的副本，完全隔离。

---

## 1.2 ThreadLocal 的基本用法

`ThreadLocalBasicDemo.java` 演示了 ThreadLocal 的核心 API：

```java
// ThreadLocalBasicDemo.java → basicUsage()
ThreadLocal<String> userContext = new ThreadLocal<>();

// 为当前线程设置值
userContext.set("user-123");

// 获取当前线程的值（其他线程无法获取到这个值）
String userId = userContext.get();  // "user-123"

// 删除当前线程的值（重要！避免内存泄漏）
userContext.remove();

// 带初始值的 ThreadLocal
ThreadLocal<List<String>> listContext = ThreadLocal.withInitial(ArrayList::new);
// 第一次 get() 时，如果没有 set，会调用 initialValue() 返回初始值
```

---

## 1.3 线程隔离效果演示

```java
// ThreadLocalBasicDemo.java → demonstrateIsolation()
ThreadLocal<String> threadLocal = new ThreadLocal<>();

// 主线程设置值
threadLocal.set("main-thread-value");

Thread thread1 = new Thread(() -> {
    // ❌ 子线程无法看到主线程设置的值
    System.out.println("Thread1 get: " + threadLocal.get());  // null

    threadLocal.set("thread1-value");
    System.out.println("Thread1 get: " + threadLocal.get());  // "thread1-value"
});

Thread thread2 = new Thread(() -> {
    System.out.println("Thread2 get: " + threadLocal.get());  // null（不是 thread1-value）
    threadLocal.set("thread2-value");
    System.out.println("Thread2 get: " + threadLocal.get());  // "thread2-value"
});

thread1.start();
thread2.start();
thread1.join();
thread2.join();

// 主线程的值不受影响
System.out.println("Main get: " + threadLocal.get());  // "main-thread-value"
```

---

## 1.4 ThreadLocal 的内部原理

```
每个 Thread 对象内部有一个 ThreadLocalMap：
Thread.threadLocals → ThreadLocalMap {
    ThreadLocal1 → value1,
    ThreadLocal2 → value2,
    ...
}
```

```java
// ThreadLocal.get() 的简化实现
public T get() {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = t.threadLocals;  // 获取当前线程的 Map
    if (map != null) {
        ThreadLocalMap.Entry e = map.getEntry(this);  // this = ThreadLocal 对象作为 key
        if (e != null) return (T) e.value;
    }
    return initialValue();
}

// ThreadLocal.set() 的简化实现
public void set(T value) {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = t.threadLocals;
    if (map != null) {
        map.set(this, value);  // key=ThreadLocal对象, value=值
    } else {
        t.threadLocals = new ThreadLocalMap(this, value);  // 首次创建
    }
}
```

**关键**：数据存储在 `Thread` 对象内部，而不是 `ThreadLocal` 对象内部。`ThreadLocal` 只是访问各线程数据的"钥匙"。

---

## 1.5 SimpleDateFormat 的线程安全化

`ThreadLocalBasicDemo.java` 中的经典应用——解决 `SimpleDateFormat` 线程不安全问题：

```java
// ❌ 共享 SimpleDateFormat：线程不安全！
private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

public String format(Date date) {
    return SDF.format(date);  // 多线程并发调用，结果错乱！
}

// ✅ 用 ThreadLocal 每个线程一个实例
private static final ThreadLocal<SimpleDateFormat> SDF_LOCAL =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

public String format(Date date) {
    return SDF_LOCAL.get().format(date);  // 每个线程用自己的实例，线程安全
}

// 现代做法：用 DateTimeFormatter（Java 8+，天然线程安全）
private static final DateTimeFormatter FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd");
public String format(LocalDate date) {
    return FORMATTER.format(date);  // 无需 ThreadLocal
}
```

---

## 1.6 本章总结

- **ThreadLocal 解决的问题**：线程私有上下文数据的存储，避免方法参数透传
- **三个核心方法**：`set(value)`、`get()`、`remove()`（**remove 必须调用！**）
- **withInitial**：第一次 `get()` 时自动初始化，避免 `NullPointerException`
- **内部原理**：数据存在 `Thread.threadLocals`（`ThreadLocalMap`）中，`ThreadLocal` 对象是 key
- **经典应用**：`SimpleDateFormat` 线程安全化（现代用 `DateTimeFormatter` 替代）

> **本章对应演示代码**：`ThreadLocalBasicDemo.java`（基础 API、线程隔离演示、SimpleDateFormat 安全化）

**继续阅读**：[02_ThreadLocal内存泄漏与正确使用.md](./02_ThreadLocal内存泄漏与正确使用.md)
