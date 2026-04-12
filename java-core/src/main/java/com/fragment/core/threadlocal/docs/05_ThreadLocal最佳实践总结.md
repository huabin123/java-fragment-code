# 第五章：ThreadLocal 最佳实践总结

## 5.1 核心使用规范

### 规范一：声明为 private static final

```java
// ✅ 正确：静态常量，全局只有一个 ThreadLocal 实例
public class RequestContext {
    private static final ThreadLocal<UserInfo> USER_CONTEXT =
        ThreadLocal.withInitial(UserInfo::empty);
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
}

// ❌ 错误：实例变量
public class UserService {
    private ThreadLocal<String> context = new ThreadLocal<>();
    // 多个 UserService 实例各自持有 ThreadLocal，且销毁时 key 变为 null → 泄漏
}
```

### 规范二：必须在 finally 中 remove

```java
// ✅ 标准模式
public void processRequest(Request request) {
    CONTEXT.set(buildContext(request));
    try {
        doProcess();
    } finally {
        CONTEXT.remove();  // 无论如何都要清除
    }
}

// ✅ 更优雅：AutoCloseable 封装
try (var ctx = RequestContext.bind(request)) {
    doProcess();
}  // 自动 remove
```

### 规范三：线程池中不用 InheritableThreadLocal，用包装或 TTL

```java
// ❌ 线程池场景 InheritableThreadLocal 失效
// ✅ 包装 Runnable 手动传递
executor.submit(ContextSnapshot.capture().wrap(() -> {
    doAsyncWork();  // 可以读取到提交时的上下文
}));

// ✅ 生产环境：用阿里 TransmittableThreadLocal
```

### 规范四：withInitial 避免空指针

```java
// ❌ 容易 NullPointerException
ThreadLocal<List<String>> tl = new ThreadLocal<>();
tl.get().add("item");  // NPE！首次 get 返回 null

// ✅ withInitial 提供默认值
ThreadLocal<List<String>> tl = ThreadLocal.withInitial(ArrayList::new);
tl.get().add("item");  // ✅ 安全
```

---

## 5.2 Spring 框架中的 ThreadLocal 最佳实践

### Spring MVC 拦截器模板

```java
@Component
public class ContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) {
        // 1. 从 Token/Header 提取用户信息
        String token = request.getHeader("Authorization");
        UserInfo user = jwtService.parseToken(token);

        // 2. 设置到 ThreadLocal
        UserContextHolder.set(user);
        TraceContext.setTraceId(
            Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .orElse(UUID.randomUUID().toString())
        );
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // 3. 请求结束，必须清除（即使有异常）
        UserContextHolder.clear();
        TraceContext.clear();
    }
}
```

### Spring 异步任务的上下文传递

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");

        // 配置上下文传递装饰器
        executor.setTaskDecorator(runnable -> {
            // 在提交线程（父线程）中捕获上下文
            UserInfo user = UserContextHolder.get();
            String traceId = TraceContext.getTraceId();

            return () -> {
                // 在执行线程（子线程）中恢复上下文
                UserContextHolder.set(user);
                TraceContext.setTraceId(traceId);
                try {
                    runnable.run();
                } finally {
                    UserContextHolder.clear();
                    TraceContext.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }
}

// 使用：@Async 方法中可以直接访问上下文
@Service
public class NotificationService {
    @Async
    public void sendEmail(String content) {
        String userId = UserContextHolder.get().getUserId();  // ✅ 正确获取
        emailClient.send(userId, content);
    }
}
```

---

## 5.3 常见错误与修复速查

| 错误 | 现象 | 修复 |
|------|------|------|
| 忘记 `remove()` | 线程池场景内存泄漏，旧值污染下次请求 | `finally` 中调用 `remove()` |
| 实例变量声明 | 对象销毁后 key=null，value 无法回收 | 改为 `private static final` |
| 线程池用 `InheritableThreadLocal` | 子线程读到历史值或 null | 包装 Runnable 或用 TTL |
| 未用 `withInitial` | 首次 `get()` 返回 null，NPE | 改用 `ThreadLocal.withInitial()` |
| 子线程修改影响父线程 | 数据被意外修改 | 误解：ITL 子线程修改不影响父线程，检查是否用了共享对象 |

---

## 5.4 ThreadLocal 与其他方案对比

| 场景 | ThreadLocal | 方法参数传递 | 全局静态变量 |
|------|------------|------------|------------|
| 线程隔离 | ✅ 天然隔离 | ✅（值拷贝）| ❌ 需要加锁 |
| 调用链透明传递 | ✅ 无需修改签名 | ❌ 每层都要加参数 | ✅ 直接访问 |
| 清晰度 | 一般（隐式）| ✅ 显式，易理解 | ❌ 全局状态，难追踪 |
| 异步场景 | ❌ 需额外处理 | ✅ 参数随调用传递 | ❌ 竞态问题 |
| 适用场景 | 请求上下文、事务绑定 | 简单调用链 | 避免使用 |

**选择原则**：
- **调用链深且参数多**：用 ThreadLocal（Spring 的 SecurityContext、事务管理都是这个场景）
- **调用链浅且简单**：用方法参数（更清晰）
- **需要跨异步边界**：用包装 Runnable 或 TTL 手动传递

---

## 5.5 本章总结（全模块回顾）

**ThreadLocal 五条核心规则**：

1. **声明为 `private static final`**：避免实例变量的 key 被 GC 导致 stale entry
2. **始终在 `finally` 中 `remove()`**：线程池场景不清除会导致内存泄漏和值污染
3. **用 `withInitial` 提供默认值**：避免 `get()` 返回 null 引发 NPE
4. **线程池中不用 InheritableThreadLocal**：包装 Runnable 或使用阿里 TTL
5. **封装为专用 Holder 类**：`UserContextHolder`、`TraceContext` 等，隐藏 ThreadLocal 实现细节

**ThreadLocal 的本质**：每个 Thread 对象内部维护一个 `ThreadLocalMap`，数据存在线程中不跨线程共享。清理的职责在使用方，不在 JVM——这是内存泄漏的根本原因。

> **本章对应演示代码**：`ThreadLocalBestPracticeDemo.java`（Spring 拦截器模板、异步上下文传递、错误案例对比）

**返回目录**：[README.md](../README.md)
