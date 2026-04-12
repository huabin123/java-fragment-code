# 第五章：AOP 与实际应用

## 5.1 AOP 的本质就是动态代理

AOP（面向切面编程）不是新技术，它的底层就是动态代理。`AopFramework.java` 展示了一个迷你 AOP 框架的实现：

```java
// AopFramework.java 的核心结构
public class AopFramework {

    // 切面接口
    interface Aspect {
        void before(Method method, Object[] args);
        void after(Method method, Object[] args, Object result);
        void onError(Method method, Object[] args, Throwable e);
    }

    // 创建代理（统一入口）
    public static <T> T createProxy(T target, List<Aspect> aspects) {
        return (T) Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            target.getClass().getInterfaces(),
            (proxy, method, args) -> {
                // 执行所有切面的 before
                aspects.forEach(a -> a.before(method, args));
                try {
                    Object result = method.invoke(target, args);
                    // 执行所有切面的 after
                    aspects.forEach(a -> a.after(method, args, result));
                    return result;
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    // 执行所有切面的 onError
                    aspects.forEach(a -> a.onError(method, args, cause));
                    throw cause;
                }
            }
        );
    }
}
```

---

## 5.2 四种 AOP 通知类型

对应 Spring AOP 的 `@Before`、`@After`、`@AfterReturning`、`@AfterThrowing`、`@Around`：

```java
// 所有类型的通知在 InvocationHandler.invoke() 中对应位置：

public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // @Before：方法执行前
    beforeAdvice(method, args);

    Object result;
    try {
        result = method.invoke(target, args);

        // @AfterReturning：方法正常返回后（不含异常）
        afterReturningAdvice(method, args, result);

    } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();

        // @AfterThrowing：方法抛出异常后
        afterThrowingAdvice(method, args, cause);
        throw cause;

    } finally {
        // @After（Finally）：无论成功还是异常都执行
        afterAdvice(method, args);
    }

    // @Around 包含了以上所有，通过 ProceedingJoinPoint.proceed() 控制是否执行目标方法
    return result;
}
```

---

## 5.3 实战场景一：统一异常处理代理

```java
// 基于 AopFramework.java 的实战扩展
Aspect exceptionAspect = new Aspect() {
    @Override
    public void before(Method method, Object[] args) {}

    @Override
    public void after(Method method, Object[] args, Object result) {}

    @Override
    public void onError(Method method, Object[] args, Throwable e) {
        if (e instanceof SQLException) {
            log.error("数据库异常，方法: {}, 参数: {}", method.getName(),
                Arrays.toString(args), e);
            throw new DataAccessException("数据库操作失败，请稍后重试", e);
        }
        if (e instanceof NetworkException) {
            // 重试逻辑
            retryService.schedule(method.getName());
        }
    }
};

UserService proxy = AopFramework.createProxy(userService, List.of(exceptionAspect));
```

---

## 5.4 实战场景二：方法级权限控制

```java
// 结合注解 + 代理实现声明式权限控制
Aspect securityAspect = new Aspect() {
    @Override
    public void before(Method method, Object[] args) {
        RequiresRole annotation = method.getAnnotation(RequiresRole.class);
        if (annotation != null) {
            String requiredRole = annotation.value();
            String currentRole = SecurityContext.getCurrentUserRole();
            if (!currentRole.equals(requiredRole)) {
                throw new AccessDeniedException(
                    "需要 " + requiredRole + " 角色，当前角色：" + currentRole);
            }
        }
    }
    // ...
};

// 在 UserService 的方法上标注权限要求
@RequiresRole("ADMIN")
public void deleteUser(Long id) { ... }
```

---

## 5.5 代理的自调用问题

AOP 最常见的 Bug：**同一个类内部方法调用不会触发代理**。

```java
// ❌ 自调用问题
@Service
public class OrderService {

    @Transactional
    public void createOrder(Order order) {
        // 业务逻辑...
        sendNotification(order);  // ← 自调用！不经过代理，@Async 失效！
    }

    @Async  // 希望异步执行
    public void sendNotification(Order order) {
        // 发送通知
    }
}
```

**原因**：Spring 注入的是代理对象，外部调用 `orderService.createOrder()` 经过代理。但在 `createOrder()` 内部调用 `sendNotification()` 时，`this` 是目标对象本身，不是代理，所以 `@Async` 失效。

```java
// ✅ 解决方案1：注入自身（Spring 会注入代理）
@Service
public class OrderService {
    @Autowired
    private OrderService self;  // 注入的是代理

    public void createOrder(Order order) {
        self.sendNotification(order);  // 经过代理，@Async 生效
    }

    @Async
    public void sendNotification(Order order) { ... }
}

// ✅ 解决方案2：拆分到不同的类（推荐）
@Service
public class NotificationService {
    @Async
    public void sendNotification(Order order) { ... }
}

@Service
public class OrderService {
    @Autowired
    private NotificationService notificationService;

    public void createOrder(Order order) {
        notificationService.sendNotification(order);  // 跨类调用，经过代理
    }
}
```

---

## 5.6 本章总结

- **AOP 本质**：动态代理 + 切面逻辑，没有魔法，就是 `method.invoke()` 的前后插入代码
- **四种通知**：Before（调用前）、AfterReturning（正常返回后）、AfterThrowing（异常后）、After/Finally（总是执行）
- **Around**：最强大，通过 `proceed()` 控制是否执行目标方法，可以修改参数和返回值
- **自调用问题**：同类内部调用不经过代理，`this.method()` 的 `this` 是目标对象而非代理

> **本章对应演示代码**：`AopFramework.java`（迷你 AOP 框架实现）、`SpringAopProxyDemo.java`（Spring AOP 场景演示）

**继续阅读**：[06-Spring AOP代理选择机制.md](./06-Spring AOP代理选择机制.md)
