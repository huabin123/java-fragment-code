# 05 AOP与实际应用

## AOP核心概念

### 基本术语
- **切面（Aspect）**：横切关注点的模块化
- **连接点（Join Point）**：程序执行的特定点
- **切点（Pointcut）**：连接点的集合
- **通知（Advice）**：切面在特定连接点执行的代码
- **织入（Weaving）**：将切面应用到目标对象的过程

### 通知类型
1. **前置通知（Before）**：方法执行前
2. **后置通知（After）**：方法执行后
3. **返回通知（AfterReturning）**：方法正常返回后
4. **异常通知（AfterThrowing）**：方法抛出异常后
5. **环绕通知（Around）**：包围方法执行

## 动态代理实现AOP

### 简单AOP框架
参考 `AopFramework.java`：
```java
UserService proxy = AopFramework.createProxy(target,
    new LoggingAspect(),
    new PerformanceAspect()
);
```

### 切面实现
```java
public class LoggingAspect implements Aspect {
    @Override
    public void before(Method method, Object[] args) {
        System.out.println("[LOG] 开始执行: " + method.getName());
    }
}
```

## 实际应用场景

### 1. 日志记录
```java
public class AuditAspect implements Aspect {
    @Override
    public void before(Method method, Object[] args) {
        String user = getCurrentUser();
        String operation = method.getName();
        auditLog.record(user, operation, args);
    }
}
```

### 2. 性能监控
```java
public class PerformanceMonitorAspect implements Aspect {
    private ThreadLocal<Long> startTime = new ThreadLocal<>();
    
    @Override
    public void before(Method method, Object[] args) {
        startTime.set(System.currentTimeMillis());
    }
    
    @Override
    public void afterFinally(Method method, Object[] args, Object result, Throwable exception) {
        long duration = System.currentTimeMillis() - startTime.get();
        if (duration > 1000) { // 超过1秒记录
            performanceLogger.warn("Slow method: {} took {}ms", method.getName(), duration);
        }
        startTime.remove();
    }
}
```

### 3. 缓存管理
```java
public class CacheAspect implements Aspect {
    private final Cache cache = new ConcurrentHashMap<>();
    
    @Override
    public Object around(Method method, Object[] args) throws Throwable {
        if (method.getName().startsWith("get")) {
            String key = generateKey(method, args);
            Object cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            
            Object result = method.invoke(target, args);
            cache.put(key, result);
            return result;
        }
        
        return method.invoke(target, args);
    }
}
```

### 4. 事务管理
```java
public class TransactionAspect implements Aspect {
    @Override
    public Object around(Method method, Object[] args) throws Throwable {
        if (method.isAnnotationPresent(Transactional.class)) {
            TransactionManager tm = getTransactionManager();
            tm.begin();
            try {
                Object result = method.invoke(target, args);
                tm.commit();
                return result;
            } catch (Exception e) {
                tm.rollback();
                throw e;
            }
        }
        
        return method.invoke(target, args);
    }
}
```

### 5. 权限控制
```java
public class SecurityAspect implements Aspect {
    @Override
    public void before(Method method, Object[] args) {
        RequiresRole roleAnnotation = method.getAnnotation(RequiresRole.class);
        if (roleAnnotation != null) {
            String requiredRole = roleAnnotation.value();
            if (!currentUser.hasRole(requiredRole)) {
                throw new SecurityException("Access denied");
            }
        }
    }
}
```

## 框架中的应用

### Spring AOP
- 基于代理的AOP实现
- 支持JDK代理和CGLIB代理
- 提供声明式事务管理

### AspectJ
- 编译时织入
- 更强大的切点表达式
- 支持字段访问拦截

## 最佳实践

### 1. 切面设计原则
- 单一职责：每个切面只关注一个横切关注点
- 最小侵入：尽量减少对业务代码的影响
- 性能考虑：避免在切面中执行耗时操作

### 2. 切点选择
- 精确匹配：避免过度拦截
- 性能优化：优先使用方法名匹配
- 灵活配置：支持运行时配置

### 3. 异常处理
```java
public class ExceptionHandlingAspect implements Aspect {
    @Override
    public void afterThrowing(Method method, Object[] args, Throwable exception) {
        // 记录异常
        logger.error("Method {} threw exception", method.getName(), exception);
        
        // 发送告警
        if (exception instanceof CriticalException) {
            alertService.sendAlert(exception);
        }
    }
}
```

## 示例代码位置
- `AopFramework.java`：简单AOP框架实现
- `JdkProxyDemo.java`：包含多种切面示例
- `ProxyDemoMain.java`：AOP框架使用演示
