# 第三章：ThreadLocal 实际应用场景

## 3.1 Web 请求上下文传递

最经典的 ThreadLocal 应用：在整个请求处理链路中传递用户信息，避免每个方法都需要 `userId` 参数。

`ThreadLocalApplicationDemo.java → UserContextHolder` 演示了完整实现：

```java
// 用户上下文持有者
public class UserContextHolder {
    private static final ThreadLocal<UserContext> CONTEXT =
        ThreadLocal.withInitial(UserContext::new);

    public static void setUser(UserContext ctx) {
        CONTEXT.set(ctx);
    }

    public static UserContext getUser() {
        return CONTEXT.get();
    }

    public static String getCurrentUserId() {
        return CONTEXT.get().getUserId();
    }

    public static void clear() {
        CONTEXT.remove();  // 必须调用！
    }
}

// 拦截器：请求进来时设置，请求结束时清除
public class UserContextInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String userId = extractUserFromToken(request.getHeader("Authorization"));
        UserContextHolder.setUser(new UserContext(userId, request.getRemoteAddr()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, ...) {
        UserContextHolder.clear();  // 请求结束，必须清除
    }
}

// 任意层级的代码（Controller/Service/DAO）都可以直接获取当前用户
public class OrderService {
    public Order createOrder(OrderRequest request) {
        String userId = UserContextHolder.getCurrentUserId();  // 无需从参数传递
        // 业务逻辑...
    }
}
```

---

## 3.2 分布式链路追踪（TraceId 传递）

```java
// ThreadLocalApplicationDemo.java → TraceContext
public class TraceContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public static String getTraceId() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = generateTraceId();
            TRACE_ID.set(traceId);
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}

// 日志自动携带 TraceId（配置 MDC，底层也用了 ThreadLocal）
// log.info("处理订单 {}", orderId);
// → [TraceId: abc-123] 处理订单 456

// HTTP 请求过来时，从 Header 中读取 TraceId 或生成新的
String traceId = request.getHeader("X-Trace-Id");
if (traceId != null) {
    TraceContext.setTraceId(traceId);
} else {
    TraceContext.getTraceId();  // 自动生成
}
```

---

## 3.3 数据库事务连接绑定

Spring 的 `@Transactional` 用 ThreadLocal 保证同一事务中的所有 DAO 操作使用同一个数据库连接：

```java
// ThreadLocalApplicationDemo.java → TransactionManager 原理演示

public class SimpleTransactionManager {
    // 把当前线程的数据库连接绑定到 ThreadLocal
    private static final ThreadLocal<Connection> CONNECTION_HOLDER = new ThreadLocal<>();

    public void beginTransaction() throws Exception {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        CONNECTION_HOLDER.set(conn);  // 绑定到当前线程
    }

    public void commit() throws Exception {
        Connection conn = CONNECTION_HOLDER.get();
        conn.commit();
    }

    public void rollback() throws Exception {
        Connection conn = CONNECTION_HOLDER.get();
        conn.rollback();
    }

    public void close() {
        Connection conn = CONNECTION_HOLDER.get();
        if (conn != null) {
            try { conn.close(); } catch (Exception e) { /* ignore */ }
            CONNECTION_HOLDER.remove();  // 归还连接后必须清除
        }
    }

    // 所有 DAO 通过这个方法获取连接，自动得到同一个事务连接
    public static Connection getCurrentConnection() {
        return CONNECTION_HOLDER.get();
    }
}

// UserDAO 和 OrderDAO 在同一事务中都用同一个连接
public class UserDAO {
    public void save(User user) throws Exception {
        Connection conn = SimpleTransactionManager.getCurrentConnection();
        // 用 conn 执行 SQL，与 OrderDAO 在同一事务中
    }
}
```

---

## 3.4 多租户数据隔离

```java
// ThreadLocalApplicationDemo.java → TenantContextHolder
public class TenantContextHolder {
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}

// MyBatis 拦截器：自动在 SQL 中添加租户过滤条件
@Intercepts({@Signature(type = Executor.class, method = "query", ...)})
public class TenantInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String tenantId = TenantContextHolder.getTenantId();
        // 修改 SQL，自动添加 WHERE tenant_id = #{tenantId}
        // ...
        return invocation.proceed();
    }
}
```

---

## 3.5 请求级别的性能统计

```java
// ThreadLocalApplicationDemo.java → RequestMetrics
public class RequestMetrics {
    private static final ThreadLocal<Map<String, Long>> METRICS =
        ThreadLocal.withInitial(HashMap::new);

    public static void record(String key, long value) {
        METRICS.get().put(key, value);
    }

    public static void startTimer(String key) {
        METRICS.get().put(key + "_start", System.currentTimeMillis());
    }

    public static void endTimer(String key) {
        Map<String, Long> m = METRICS.get();
        Long start = m.get(key + "_start");
        if (start != null) {
            m.put(key + "_elapsed", System.currentTimeMillis() - start);
        }
    }

    public static Map<String, Long> getMetrics() {
        return Collections.unmodifiableMap(METRICS.get());
    }

    public static void clear() {
        METRICS.remove();  // 必须清除！
    }
}

// 在请求处理的各个阶段记录耗时
RequestMetrics.startTimer("db_query");
userDAO.findById(userId);
RequestMetrics.endTimer("db_query");

// 请求结束时，打印本次请求的性能报告
Map<String, Long> metrics = RequestMetrics.getMetrics();
log.info("请求性能报告: {}", metrics);
```

---

## 3.6 本章总结

- **用户上下文**：请求进来设置，拦截器 `afterCompletion` 清除；所有层级无参数传递
- **链路追踪**：TraceId 存入 ThreadLocal，结合 Slf4j MDC 自动加到日志
- **事务连接绑定**：Spring `@Transactional` 底层用 ThreadLocal 保证同一线程用同一连接
- **多租户隔离**：请求级别 tenantId 存入 ThreadLocal，持久层自动过滤
- **统一规律**：所有场景都是「请求进来时 set → 业务处理中 get → 请求结束时 remove」

> **本章对应演示代码**：`ThreadLocalApplicationDemo.java`（用户上下文、TraceId、事务管理、多租户、性能统计五个完整案例）

**继续阅读**：[04_InheritableThreadLocal父子线程传递.md](./04_InheritableThreadLocal父子线程传递.md)
