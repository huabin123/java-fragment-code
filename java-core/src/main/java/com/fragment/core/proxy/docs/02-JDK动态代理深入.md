# 第二章：JDK 动态代理深入

## 2.1 四种 InvocationHandler 实现模式

`JdkProxyDemo.java` 演示了四种典型的 Handler 实现：

### 模式一：日志代理（LoggingInvocationHandler）

```java
// JdkProxyDemo.java → demonstrateLoggingProxy()
class LoggingInvocationHandler implements InvocationHandler {
    private final Object target;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("[LOG] 开始调用: " + method.getName()
            + ", 参数: " + Arrays.toString(args));

        Object result = method.invoke(target, args);

        System.out.println("[LOG] 调用完成: " + method.getName()
            + ", 返回: " + result);
        return result;
    }
}
```

### 模式二：性能监控代理（PerformanceInvocationHandler）

```java
// JdkProxyDemo.java → demonstratePerformanceProxy()
class PerformanceInvocationHandler implements InvocationHandler {
    private final Object target;
    private static final long SLOW_THRESHOLD_MS = 100;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return method.invoke(target, args);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > SLOW_THRESHOLD_MS) {
                System.out.println("[PERF] 慢方法告警: " + method.getName()
                    + " 耗时 " + cost + "ms");
            }
        }
    }
}
```

**用 `try-finally` 的重要性**：确保无论方法是否抛出异常，性能计时逻辑都会执行。

### 模式三：缓存代理（CacheInvocationHandler）

```java
// JdkProxyDemo.java → demonstrateCacheProxy()
class CacheInvocationHandler implements InvocationHandler {
    private final Object target;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 只缓存查询方法（方法名以 find/get/query 开头）
        if (!method.getName().startsWith("find")
                && !method.getName().startsWith("get")) {
            return method.invoke(target, args);
        }

        String key = method.getName() + ":" + Arrays.toString(args);
        return cache.computeIfAbsent(key, k -> {
            try {
                return method.invoke(target, args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
```

### 模式四：组合多个 Handler（责任链）

```java
// AopFramework.java 中的组合模式
class ChainedInvocationHandler implements InvocationHandler {
    private final Object target;
    private final List<InvocationHandler> handlers;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 依次执行每个 handler 的前置逻辑
        for (InvocationHandler handler : handlers) {
            // 实际的责任链需要更复杂的设计，这里简化展示思路
        }
        return method.invoke(target, args);
    }
}
```

---

## 2.2 异常处理的陷阱

通过反射调用目标方法时，所有异常都会被包装成 `InvocationTargetException`：

```java
@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
        return method.invoke(target, args);
    } catch (InvocationTargetException e) {
        // ❌ 直接抛出 InvocationTargetException：调用方看到的是包装后的异常，不友好
        throw e;

        // ✅ 解包：把原始异常抛出去，让调用方看到真正的异常
        throw e.getCause();

        // ✅ 或者：保留原始异常类型
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new RuntimeException(cause);
    }
}
```

**原理**：`method.invoke()` 把目标方法的所有异常（包括 checked exception）包装进 `InvocationTargetException`，必须通过 `getCause()` 解包才能还原原始异常。

---

## 2.3 代理对象的类型检查

```java
UserService proxy = (UserService) Proxy.newProxyInstance(...);

// 代理对象实现了 UserService 接口
System.out.println(proxy instanceof UserService);  // true

// 代理对象是 Proxy 的子类
System.out.println(proxy instanceof Proxy);  // true

// 代理对象不是 UserServiceImpl 类型
System.out.println(proxy instanceof UserServiceImpl);  // false！

// 获取代理类名
System.out.println(proxy.getClass().getName());  // com.sun.proxy.$Proxy0

// 从代理对象获取 InvocationHandler
InvocationHandler handler = Proxy.getInvocationHandler(proxy);
```

---

## 2.4 哪些方法不会经过 InvocationHandler

不是所有方法调用都会经过代理。以下方法直接在代理类本身实现，不转发给 Handler：

```java
// Object 的三个方法直接实现，不转发
proxy.hashCode();   // Proxy 父类直接实现
proxy.equals(obj);  // Proxy 父类直接实现
proxy.toString();   // Proxy 父类直接实现

// 接口的 default 方法：在 JDK 8 中不转发，JDK 9+ 修复可以选择转发
```

---

## 2.5 动态代理在 Spring 中的应用

理解 `JdkProxyDemo.java` 中的模式，就理解了 Spring AOP 的本质：

```
@Transactional 注解 → Spring 检测到方法有事务注解
    → 生成 JDK 代理对象（目标类有接口）
    → 代理的 InvocationHandler = TransactionInterceptor
    → invoke() 中：开事务 → 调用 method.invoke(target, args) → 提交/回滚
```

```java
// Spring TransactionInterceptor 的简化原理（非源码，仅示意）
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    TransactionStatus status = transactionManager.getTransaction(txDef);
    try {
        Object result = method.invoke(target, args);
        transactionManager.commit(status);
        return result;
    } catch (RuntimeException e) {
        transactionManager.rollback(status);
        throw e;
    }
}
```

---

## 2.6 本章总结

- **四种 Handler 模式**：日志（前后打印）、性能监控（try-finally 计时）、缓存（方法名+参数作 key）、责任链（多 Handler 组合）
- **异常解包**：`method.invoke()` 抛出的是 `InvocationTargetException`，必须 `getCause()` 解包
- **不转发的方法**：`hashCode`、`equals`、`toString` 直接在 Proxy 父类实现
- **Spring AOP 本质**：`@Transactional` = JDK 代理 + `TransactionInterceptor` 作为 InvocationHandler

> **本章对应演示代码**：`JdkProxyDemo.java`（四种 Handler）、`AopFramework.java`（Handler 组合）

**继续阅读**：[03-CGLIB代理原理.md](./03-CGLIB代理原理.md)
