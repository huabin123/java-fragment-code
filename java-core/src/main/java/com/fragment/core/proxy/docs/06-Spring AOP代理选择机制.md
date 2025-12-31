# Spring AOP 代理选择机制

## 概述

Spring AOP 是 Spring 框架的核心功能之一，它同时支持 **JDK 动态代理** 和 **CGLIB 代理**。Spring 会根据目标对象的特征**自动选择**合适的代理方式。

## Spring AOP 代理选择策略

### 默认策略（Spring 5.x 之前）

```
如果目标对象实现了接口 → 使用 JDK 动态代理
如果目标对象没有实现接口 → 使用 CGLIB 代理
```

### 新策略（Spring Boot 2.x / Spring 5.x+）

从 **Spring Boot 2.0** 开始，默认使用 **CGLIB 代理**，即使目标类实现了接口。

**原因**：
1. **性能更好**：CGLIB 代理在方法调用性能上优于 JDK 动态代理
2. **更灵活**：可以代理类的所有 public 方法，不仅限于接口方法
3. **避免类型转换问题**：注入时可以使用实现类类型，而不必使用接口类型

## Spring 源码中的实现

### 1. 核心类：DefaultAopProxyFactory

Spring AOP 通过 `DefaultAopProxyFactory` 类决定使用哪种代理方式：

```java
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

    @Override
    public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
        // 判断是否强制使用 CGLIB 或者优化代理
        if (config.isOptimize() || 
            config.isProxyTargetClass() || 
            hasNoUserSuppliedProxyInterfaces(config)) {
            
            Class<?> targetClass = config.getTargetClass();
            if (targetClass == null) {
                throw new AopConfigException("TargetSource cannot determine target class");
            }
            
            // 如果目标类是接口或者已经是 JDK 代理类，使用 JDK 代理
            if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
                return new JdkDynamicAopProxy(config);
            }
            
            // 否则使用 CGLIB 代理
            return new ObjenesisCglibAopProxy(config);
        } else {
            // 默认使用 JDK 动态代理
            return new JdkDynamicAopProxy(config);
        }
    }
    
    private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
        Class<?>[] ifcs = config.getProxiedInterfaces();
        return (ifcs.length == 0 || 
                (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
    }
}
```

### 2. 关键判断条件

Spring 使用 **CGLIB 代理** 的条件（满足任一即可）：

1. **`optimize = true`**：优化标志，启用 CGLIB
2. **`proxyTargetClass = true`**：强制使用 CGLIB（最常用）
3. **目标类没有实现接口**：无法使用 JDK 代理

Spring 使用 **JDK 动态代理** 的条件：

1. 目标类实现了接口
2. 没有设置 `proxyTargetClass = true`
3. 目标类本身就是接口或已经是代理类

### 3. 核心配置属性

#### `proxyTargetClass` 属性

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)  // 强制使用 CGLIB
public class AppConfig {
    // ...
}
```

或在 Spring Boot 配置文件中：

```properties
# application.properties
spring.aop.proxy-target-class=true   # 默认就是 true（Spring Boot 2.x+）
```

```yaml
# application.yml
spring:
  aop:
    proxy-target-class: true
```

## 两种代理方式的实现类

### JDK 动态代理：JdkDynamicAopProxy

```java
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {
    
    private final AdvisedSupport advised;
    
    @Override
    public Object getProxy(@Nullable ClassLoader classLoader) {
        Class<?>[] proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(this.advised, true);
        return Proxy.newProxyInstance(classLoader, proxiedInterfaces, this);
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 获取拦截器链
        List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
        
        if (chain.isEmpty()) {
            // 没有拦截器，直接调用目标方法
            return AopUtils.invokeJoinpointUsingReflection(target, method, args);
        } else {
            // 创建方法调用对象，执行拦截器链
            MethodInvocation invocation = new ReflectiveMethodInvocation(
                proxy, target, method, args, targetClass, chain);
            return invocation.proceed();
        }
    }
}
```

### CGLIB 代理：CglibAopProxy

```java
class CglibAopProxy implements AopProxy, Serializable {
    
    @Override
    public Object getProxy(@Nullable ClassLoader classLoader) {
        Class<?> rootClass = this.advised.getTargetClass();
        
        // 创建 Enhancer
        Enhancer enhancer = createEnhancer();
        enhancer.setSuperclass(rootClass);
        enhancer.setInterfaces(AopProxyUtils.completeProxiedInterfaces(this.advised));
        enhancer.setCallbackFilter(new ProxyCallbackFilter(this.advised));
        enhancer.setCallbacks(getCallbacks(rootClass));
        
        // 生成代理类
        return enhancer.create();
    }
    
    private Callback[] getCallbacks(Class<?> rootClass) {
        // DynamicAdvisedInterceptor 是核心拦截器
        Callback aopInterceptor = new DynamicAdvisedInterceptor(this.advised);
        
        // ... 其他回调
        return new Callback[] {
            aopInterceptor,  // AOP 方法拦截
            targetInterceptor,  // 目标方法直接调用
            new SerializableNoOp(),  // 无操作
            // ...
        };
    }
    
    // 核心拦截器
    private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {
        
        @Override
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) 
                throws Throwable {
            // 获取拦截器链
            List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(
                method, targetClass);
            
            if (chain.isEmpty() && Modifier.isPublic(method.getModifiers())) {
                // 没有拦截器，使用 MethodProxy 直接调用（性能更好）
                return methodProxy.invoke(target, args);
            } else {
                // 执行拦截器链
                return new CglibMethodInvocation(proxy, target, method, args, 
                    targetClass, chain, methodProxy).proceed();
            }
        }
    }
}
```

## 实际应用示例

### 示例 1：接口代理（默认 JDK）

```java
public interface UserService {
    User findById(Long id);
}

@Service
public class UserServiceImpl implements UserService {
    @Override
    public User findById(Long id) {
        return new User(id, "User" + id);
    }
}

@Aspect
@Component
public class LogAspect {
    @Before("execution(* com.example.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("Before: " + joinPoint.getSignature());
    }
}
```

**Spring 5.x 之前**：使用 JDK 动态代理  
**Spring Boot 2.x+**：使用 CGLIB 代理（默认 `proxyTargetClass=true`）

### 示例 2：强制使用 CGLIB

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)  // 强制 CGLIB
public class AppConfig {
}
```

### 示例 3：无接口类（必须 CGLIB）

```java
@Service
public class OrderService {  // 没有实现接口
    
    public void createOrder(Order order) {
        // ...
    }
}
```

这种情况下，Spring **只能使用 CGLIB 代理**。

## 如何判断使用了哪种代理？

### 方法 1：通过类名判断

```java
@Autowired
private UserService userService;

public void checkProxyType() {
    System.out.println(userService.getClass().getName());
    
    // JDK 代理输出：com.sun.proxy.$Proxy23
    // CGLIB 代理输出：com.example.UserServiceImpl$$EnhancerBySpringCGLIB$$12345678
}
```

### 方法 2：通过 AopUtils 判断

```java
import org.springframework.aop.support.AopUtils;

public void checkProxyType() {
    boolean isJdkProxy = Proxy.isProxyClass(userService.getClass());
    boolean isCglibProxy = AopUtils.isCglibProxy(userService);
    
    System.out.println("JDK Proxy: " + isJdkProxy);
    System.out.println("CGLIB Proxy: " + isCglibProxy);
}
```

## 性能对比

### 代理创建性能

- **JDK 动态代理**：创建速度快（使用反射）
- **CGLIB 代理**：创建速度慢（需要生成字节码）

### 方法调用性能

- **JDK 动态代理**：每次调用都通过反射 `Method.invoke()`，性能较低
- **CGLIB 代理**：使用 `FastClass` 机制和 `MethodProxy`，性能接近直接调用

**结论**：对于长期运行的应用，CGLIB 的方法调用性能优势更明显。

## 选择建议

### 使用 JDK 动态代理的场景

1. 目标类已经实现了接口，且接口设计良好
2. 需要代理多个接口
3. 不希望引入 CGLIB 依赖（虽然 Spring 已内置）

### 使用 CGLIB 代理的场景

1. 目标类没有实现接口
2. 需要代理类的所有 public 方法（不仅限于接口方法）
3. 追求更好的运行时性能
4. Spring Boot 应用（默认就是 CGLIB）

### Spring Boot 默认配置

Spring Boot 2.x+ 默认使用 CGLIB，这是经过权衡的最佳实践：

```properties
spring.aop.proxy-target-class=true  # 默认值
```

如果要切换回 JDK 代理：

```properties
spring.aop.proxy-target-class=false
```

## 常见问题

### 1. CGLIB 代理的限制

- **不能代理 final 类**：CGLIB 基于继承，final 类无法被继承
- **不能代理 final 方法**：final 方法无法被重写
- **不能代理 private 方法**：private 方法对子类不可见
- **需要无参构造器**：CGLIB 需要调用父类构造器

### 2. 类型转换问题

使用 CGLIB 代理时，可以注入实现类：

```java
@Autowired
private UserServiceImpl userService;  // CGLIB 可以，JDK 不行
```

使用 JDK 代理时，只能注入接口：

```java
@Autowired
private UserService userService;  // 必须使用接口类型
```

### 3. 事务失效问题

Spring 事务基于 AOP 实现，内部方法调用不会触发代理：

```java
@Service
public class UserService {
    
    @Transactional
    public void methodA() {
        methodB();  // 直接调用，不经过代理，事务失效！
    }
    
    @Transactional
    public void methodB() {
        // ...
    }
}
```

**解决方案**：
1. 将 `methodB` 移到另一个 Bean
2. 注入自身代理：`@Autowired private UserService self;`
3. 使用 `AopContext.currentProxy()`

## 总结

| 特性 | JDK 动态代理 | CGLIB 代理 |
|------|-------------|-----------|
| **实现方式** | 基于接口，使用反射 | 基于继承，生成字节码 |
| **要求** | 必须实现接口 | 不能是 final 类 |
| **创建速度** | 快 | 慢 |
| **调用性能** | 慢（反射） | 快（FastClass） |
| **Spring 默认** | 5.x 之前有接口时使用 | Boot 2.x+ 默认使用 |
| **代理范围** | 仅接口方法 | 所有 public 方法 |

**Spring 的智能选择**：
- Spring 会根据目标类的特征自动选择最合适的代理方式
- Spring Boot 2.x+ 默认使用 CGLIB，性能更好
- 可以通过 `@EnableAspectJAutoProxy(proxyTargetClass=true/false)` 强制指定

**最佳实践**：
- 使用 Spring Boot 默认配置（CGLIB）
- 依赖注入时优先使用接口类型
- 理解两种代理的限制，避免踩坑
