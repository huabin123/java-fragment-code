# 第四章：InheritableThreadLocal 父子线程传递

## 4.1 ThreadLocal 的父子线程问题

普通 ThreadLocal 无法跨线程传递：

```java
// ThreadLocalInheritanceDemo.java → 演示问题
ThreadLocal<String> threadLocal = new ThreadLocal<>();
threadLocal.set("parent-value");

Thread child = new Thread(() -> {
    // ❌ 子线程无法获取父线程的 ThreadLocal 值
    System.out.println(threadLocal.get());  // null
});
child.start();
```

**场景**：Web 请求中设置了 userId，但需要开启子线程异步处理，子线程需要知道当前用户是谁。

---

## 4.2 InheritableThreadLocal：父子线程值继承

```java
// ThreadLocalInheritanceDemo.java → basicInheritance()
InheritableThreadLocal<String> itl = new InheritableThreadLocal<>();
itl.set("parent-value");

Thread child = new Thread(() -> {
    // ✅ 子线程继承了父线程的值
    System.out.println(itl.get());  // "parent-value"

    // 子线程可以修改自己的副本，不影响父线程
    itl.set("child-value");
    System.out.println(itl.get());  // "child-value"
});
child.start();
child.join();

// 父线程的值不受子线程修改的影响
System.out.println(itl.get());  // "parent-value"
```

**继承时机**：子线程在 `new Thread()` 创建时（不是 `start()` 时），会把父线程的 `ThreadLocalMap` 中的所有 `InheritableThreadLocal` 值拷贝一份。

---

## 4.3 InheritableThreadLocal 的致命缺陷：线程池场景失效

```java
// ThreadLocalInheritanceDemo.java → demonstratePoolProblem()

InheritableThreadLocal<String> itl = new InheritableThreadLocal<>();
ExecutorService pool = Executors.newFixedThreadPool(2);

// 请求1：userId = "user-A"
itl.set("user-A");
pool.submit(() -> {
    System.out.println("Task1: " + itl.get());  // 第一次可能是 "user-A"（线程刚创建）
});

// 请求2：userId = "user-B"
itl.set("user-B");
pool.submit(() -> {
    // ❌ 线程池中的线程是复用的，不会重新创建
    // 线程在处理请求1时已经建立了，不会再继承请求2的值
    // 可能还是 "user-A"！或者是其他历史值
    System.out.println("Task2: " + itl.get());  // 不确定！
});
```

**根本原因**：`InheritableThreadLocal` 只在**线程创建时**拷贝一次，线程池中的线程创建于应用启动时，之后一直被复用，不会重新继承父线程的值。

---

## 4.4 解决方案：手动传递上下文

### 方案一：显式参数传递

```java
// 简单但破坏了 ThreadLocal 隐式传递的优势
pool.submit(() -> {
    // 提交任务前，手动捕获当前值并传入
    String userId = UserContextHolder.getCurrentUserId();
    return () -> {
        UserContextHolder.setUser(userId);  // 在任务中设置
        try {
            doWork();
        } finally {
            UserContextHolder.clear();
        }
    };
});
```

### 方案二：包装 Runnable/Callable（推荐）

```java
// ThreadLocalInheritanceDemo.java → ContextAwareRunnable
public class ContextAwareRunnable implements Runnable {
    private final Runnable delegate;
    private final String userId;  // 提交时捕获的上下文
    private final String traceId;

    public ContextAwareRunnable(Runnable delegate) {
        this.delegate = delegate;
        // 提交任务时（在父线程中）捕获当前上下文
        this.userId = UserContextHolder.getCurrentUserId();
        this.traceId = TraceContext.getTraceId();
    }

    @Override
    public void run() {
        // 执行任务时（在子线程中）恢复上下文
        UserContextHolder.setUser(new UserContext(userId));
        TraceContext.setTraceId(traceId);
        try {
            delegate.run();
        } finally {
            UserContextHolder.clear();
            TraceContext.clear();
        }
    }
}

// 使用
pool.submit(new ContextAwareRunnable(() -> {
    // 这里可以直接用 UserContextHolder.getCurrentUserId()
    processOrder();
}));
```

### 方案三：TransmittableThreadLocal（阿里开源，推荐生产使用）

```java
// 阿里巴巴开源的 TTL 库，专门解决线程池场景的 ThreadLocal 传递问题
// <dependency>
//   <groupId>com.alibaba</groupId>
//   <artifactId>transmittable-thread-local</artifactId>
// </dependency>

TransmittableThreadLocal<String> ttl = new TransmittableThreadLocal<>();
ttl.set("user-A");

// TtlRunnable.get() 包装 Runnable，在提交时自动捕获当前 TTL 值
ExecutorService ttlPool = TtlExecutors.getTtlExecutorService(pool);
ttlPool.submit(() -> {
    System.out.println(ttl.get());  // ✅ 正确获取 "user-A"
});
```

---

## 4.5 继承时的值拷贝：childValue 方法

`InheritableThreadLocal` 允许自定义子线程继承时的值转换：

```java
// 场景：子线程继承父线程的权限列表，但子线程只有只读权限
InheritableThreadLocal<Set<String>> permissions = new InheritableThreadLocal<>() {
    @Override
    protected Set<String> childValue(Set<String> parentValue) {
        if (parentValue == null) return new HashSet<>();
        // 子线程只继承读权限
        return parentValue.stream()
            .filter(p -> p.startsWith("READ"))
            .collect(Collectors.toSet());
    }
};

permissions.set(new HashSet<>(Arrays.asList("READ_USER", "WRITE_USER", "DELETE_USER")));

Thread child = new Thread(() -> {
    System.out.println(permissions.get());  // [READ_USER]，只有读权限
});
```

---

## 4.6 本章总结

- **InheritableThreadLocal**：子线程创建时继承父线程的值，适合非线程池场景
- **线程池失效**：线程池线程只创建一次，之后不再继承，InheritableThreadLocal 在线程池中无效
- **解决方案**：包装 Runnable（提交时捕获，执行时恢复）；或用阿里 TTL 库（生产推荐）
- **childValue**：可以覆盖自定义继承时的值转换逻辑
- **核心原则**：ThreadLocal 系列都要在任务结束时 remove，避免内存泄漏

> **本章对应演示代码**：`ThreadLocalInheritanceDemo.java`（继承基础、线程池失效、ContextAwareRunnable 解决方案）

**继续阅读**：[05_ThreadLocal最佳实践总结.md](./05_ThreadLocal最佳实践总结.md)
