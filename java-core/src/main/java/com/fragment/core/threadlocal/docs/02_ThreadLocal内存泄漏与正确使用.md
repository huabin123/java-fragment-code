# 第二章：ThreadLocal 内存泄漏与正确使用

## 2.1 内存泄漏的根源

`ThreadLocalMap.Entry` 使用**弱引用（WeakReference）**存储 key（ThreadLocal 对象），但 value 是**强引用**：

```
ThreadLocalMap.Entry extends WeakReference<ThreadLocal<?>> {
    Object value;  // 强引用
}
```

```
GC Root → Thread → ThreadLocalMap → Entry → (弱引用)ThreadLocal对象
                                          → (强引用)value对象
```

**内存泄漏场景**：

```
1. ThreadLocal 对象被置为 null（外部强引用消失）
2. GC 发现 Entry.key 是弱引用，回收 ThreadLocal 对象
   → Entry.key = null（变成 stale entry）
3. 但 Entry.value 仍然是强引用，无法被 GC！
4. 如果使用的是线程池，线程不会结束
   → value 对象永远无法被回收 = 内存泄漏
```

---

## 2.2 泄漏复现

`ThreadLocalMemoryLeakDemo.java` 演示了内存泄漏的复现过程：

```java
// ThreadLocalMemoryLeakDemo.java → demonstrateMemoryLeak()
ExecutorService pool = Executors.newFixedThreadPool(2);  // 线程会被复用

// 模拟 Web 请求处理：每次请求创建 ThreadLocal，但忘记 remove
for (int i = 0; i < 10000; i++) {
    pool.submit(() -> {
        // ❌ 每次请求都 new 一个 ThreadLocal（不常见但最危险的用法）
        ThreadLocal<byte[]> threadLocal = new ThreadLocal<>();
        threadLocal.set(new byte[1024 * 1024]);  // 1MB 数据

        // 处理业务...

        // ❌ 忘记 threadLocal.remove()
        // ThreadLocal 对象在方法结束后失去引用，被 GC 后 key=null
        // 但 value（1MB 数据）仍然被 ThreadLocalMap 持有！
    });
}

// 线程池的 2 个线程：每次提交任务时，旧的 stale entry 越堆越多
// 最终 OOM
```

---

## 2.3 正确使用模式：必须 remove

```java
// ThreadLocalMemoryLeakDemo.java → correctUsage()

// ✅ 正确模式1：try-finally 保证 remove 必定执行
ThreadLocal<UserContext> userContext = new ThreadLocal<>();

public void handleRequest(HttpRequest request) {
    userContext.set(new UserContext(request));
    try {
        processRequest();  // 业务处理
    } finally {
        userContext.remove();  // 无论成功还是异常，必须清除
    }
}

// ✅ 正确模式2：用 AutoCloseable 封装（更优雅）
public class ThreadLocalScope<T> implements AutoCloseable {
    private final ThreadLocal<T> threadLocal;

    public ThreadLocalScope(ThreadLocal<T> tl, T value) {
        this.threadLocal = tl;
        tl.set(value);
    }

    @Override
    public void close() {
        threadLocal.remove();  // try-with-resources 自动调用
    }
}

// 使用
try (ThreadLocalScope<String> scope = new ThreadLocalScope<>(userIdLocal, userId)) {
    processRequest();
}  // 自动 remove
```

---

## 2.4 ThreadLocal 的 stale entry 自动清理

ThreadLocal 内部有一定的自清理机制，但**不能依赖它**：

```java
// ThreadLocalMap 在以下操作时会清理 key=null 的 stale entry：
// 1. set() 时：探测式清理（expungeStaleEntry）
// 2. get() 时：遇到 stale entry 时清理
// 3. remove() 时：清理当前及相邻的 stale entry

// 但问题是：
// - 如果不再调用 set/get，stale entry 永远不会被清理
// - 线程池中的线程很少结束，ThreadLocalMap 也不会被 GC
// - 结论：不能依赖自动清理，必须手动 remove
```

---

## 2.5 静态 ThreadLocal 的正确声明

```java
// ❌ 错误：实例变量（每次创建对象都 new 一个 ThreadLocal）
public class UserService {
    private ThreadLocal<String> context = new ThreadLocal<>();  // 危险！
    // 如果 UserService 是多例的，每个实例一个 ThreadLocal
    // UserService 对象被 GC 后，ThreadLocal key 变为 null → 泄漏
}

// ✅ 正确：静态变量（全局只有一个 ThreadLocal 实例）
public class UserService {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    // ThreadLocal 对象的引用永远存在（通过 static 持有）
    // key 永远不会变为 null
    // 但仍然需要 remove！（否则线程池线程会持有旧值）
}
```

---

## 2.6 本章总结

- **泄漏根源**：Entry.key 是弱引用，GC 后 key=null；但 Entry.value 是强引用，在线程池场景下永远无法回收
- **触发条件**：线程池（线程不会结束）+ ThreadLocal 未 remove
- **必须 remove**：在 `finally` 块或 `AutoCloseable.close()` 中调用 `threadLocal.remove()`
- **自动清理不可靠**：ThreadLocalMap 的 stale entry 清理只在 set/get/remove 时顺带触发
- **声明为 static**：ThreadLocal 变量应声明为 `private static final`，避免 key 被 GC

> **本章对应演示代码**：`ThreadLocalMemoryLeakDemo.java`（泄漏复现、正确使用模式、AutoCloseable 封装）

**继续阅读**：[03_ThreadLocal实际应用场景.md](./03_ThreadLocal实际应用场景.md)
