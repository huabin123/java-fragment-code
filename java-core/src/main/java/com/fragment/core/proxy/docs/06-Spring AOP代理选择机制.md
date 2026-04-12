# 第六章：Spring AOP 代理选择机制

## 6.1 Spring 的代理选择逻辑

`SpringProxyFactory.java` 展示了 Spring AOP 在选择代理方式时的完整决策树：

```
目标类是否实现了接口？
    ├── 是 → 是否强制使用 CGLIB（proxyTargetClass=true）？
    │         ├── 是 → 使用 CGLIB 代理
    │         └── 否 → 使用 JDK 动态代理
    └── 否 → 目标类是否是 final 类？
              ├── 是 → 无法代理，抛出异常
              └── 否 → 使用 CGLIB 代理
```

```java
// SpringProxyFactory.java → createProxy() 的核心逻辑
public Object createProxy(Object target, boolean proxyTargetClass) {
    Class<?> targetClass = target.getClass();
    boolean hasInterfaces = targetClass.getInterfaces().length > 0;

    if (!proxyTargetClass && hasInterfaces) {
        // JDK 动态代理：基于接口
        return Proxy.newProxyInstance(
            targetClass.getClassLoader(),
            targetClass.getInterfaces(),
            new SpringInvocationHandler(target, advisors)
        );
    } else {
        // CGLIB 代理：基于子类
        if (Modifier.isFinal(targetClass.getModifiers())) {
            throw new IllegalStateException("无法为 final 类创建代理: " + targetClass);
        }
        // 使用 CGLIB Enhancer 创建子类代理
        return createCglibProxy(target);
    }
}
```

---

## 6.2 Spring Boot 2.x 的默认变化

**Spring Boot 1.x**：有接口 → JDK 代理（默认）

**Spring Boot 2.x 起**：**默认全部使用 CGLIB 代理**（`spring.aop.proxy-target-class=true`）

理由：
1. 避免接口注入（`@Autowired private UserServiceImpl service`）时类型不匹配问题
2. CGLIB 性能更好（FastClass 机制）
3. 避免 JDK 代理 + CGLIB 混用导致的奇怪问题

```yaml
# application.yml（如果想恢复 JDK 代理默认行为）
spring:
  aop:
    proxy-target-class: false
```

---

## 6.3 代理带来的类型问题

理解代理选择机制能帮助排查 Spring 中常见的类型错误：

```java
// 场景：UserServiceImpl 实现了 UserService 接口
@Service
public class UserServiceImpl implements UserService { }

// Spring Boot 2.x（默认 CGLIB）：
// 注入的 Bean 类型是 UserServiceImpl$$EnhancerByCGLIB$$xxx
// 是 UserServiceImpl 的子类，也是 UserService 的实现

@Autowired
UserService service;        // ✅ 接口注入，两种代理都支持
@Autowired
UserServiceImpl service;    // ✅ Spring Boot 2.x CGLIB 模式下正常（是其子类）
                            // ❌ Spring Boot 1.x JDK 代理模式下报错（代理不是 UserServiceImpl）

// 场景：有人这样写（反模式）
@Autowired
UserServiceImpl$$EnhancerByCGLIB$$xxx service;  // ❌ 永远不要这样写
```

**最佳实践**：始终通过接口注入，不依赖具体实现类型。

---

## 6.4 `@Scope(proxyMode)` 与代理

Spring 的 Scoped Proxy 是动态代理的另一个重要应用场景：

```java
// 问题：单例 Bean 注入 Request Scope 的 Bean
@Service  // 单例
public class UserService {
    @Autowired
    private RequestContext context;  // Request Scope：每个请求不同
    // 问题：UserService 是单例，context 只在 Spring 启动时注入一次
    // 后续所有请求都用同一个 context 对象 → Bug！
}

// 解决：Request Scope Bean 使用代理模式
@Component
@RequestScope  // 等价于 @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    private String userId;  // 每个请求独立
}

// 实际注入的是一个代理，每次访问代理时，代理从当前 Request Scope 获取真实对象
@Service
public class UserService {
    @Autowired
    private RequestContext context;  // 注入的是 Scoped Proxy
    // context.getUserId() 实际上是：
    // → proxy.getUserId()
    // → 从当前请求的 ApplicationContext 获取真实的 RequestContext
    // → 调用其 getUserId()
}
```

---

## 6.5 常见 Spring AOP 代理问题排查

### 问题一：`@Transactional` 在同类方法调用中失效

```
原因：自调用问题（见第五章 5.5）
排查：在目标方法打断点，查看 this 是代理对象还是原始对象
解决：拆分到不同类，或通过 ApplicationContext.getBean() 获取代理
```

### 问题二：`@Async` 失效

```
原因1：自调用问题
原因2：目标类没有被 Spring 管理（@Service 等注解缺失）
原因3：没有启用 @EnableAsync
排查：检查 Bean 是否被 Spring 管理，方法是否通过代理调用
```

### 问题三：接口注入报 `NoUniqueBeanDefinitionException`

```java
// 多个实现类时 Spring 不知道注入哪个
@Autowired
UserService service;  // UserServiceImpl1? UserServiceImpl2?

// 解决：@Qualifier 指定
@Autowired
@Qualifier("userServiceImpl1")
UserService service;

// 或者：@Primary 标记默认实现
@Primary
@Service
public class UserServiceImpl1 implements UserService { }
```

### 问题四：CGLIB 代理找不到无参构造函数

```
原因：CGLIB 生成子类时需要调用父类构造函数
错误信息：No default constructor found

解决：添加无参构造函数（即使不需要也要加）
或使用 @Inject（JSR-330）而非 @Autowired（某些版本有差异）
```

---

## 6.6 本章总结

- **Spring 选择逻辑**：有接口 + 未强制 → JDK 代理；无接口或强制 proxyTargetClass → CGLIB
- **Spring Boot 2.x**：默认全部 CGLIB，避免 JDK 代理的接口类型限制
- **类型注入**：始终用接口类型注入，不依赖具体实现类，与代理类型无关
- **Scoped Proxy**：Request/Session Scope Bean 注入到单例时，必须通过 ScopedProxy 解决作用域不匹配问题
- **AOP 四大问题**：@Transactional 自调用失效、@Async 自调用失效、多 Bean 冲突、CGLIB 无参构造

> **本章对应演示代码**：`SpringProxyFactory.java`（代理选择策略）、`SpringAopProxyDemo.java`（自调用问题演示与解决方案）

**返回目录**：[README.md](../README.md)
