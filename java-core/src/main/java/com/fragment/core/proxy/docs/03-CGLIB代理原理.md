# 第三章：CGLIB 代理原理

## 3.1 CGLIB 为什么存在？

JDK 动态代理要求目标类必须实现接口。但实际项目中大量的类没有接口（如 Controller、Service 实现类等）。CGLIB 通过**字节码增强技术生成目标类的子类**来解决这个问题。

```
JDK 代理：$Proxy0 extends Proxy implements UserService
CGLIB 代理：UserServiceImpl$$EnhancerByCGLIB$$xxxx extends UserServiceImpl
```

---

## 3.2 CGLIB 的工作原理

`CglibProxyDemo.java → demonstrateCglibConcepts()` 和 `demonstrateManualSubclassProxy()` 展示了 CGLIB 的核心思想：

**CGLIB 的实际使用（需要添加依赖）**：

```java
// 需要 cglib 或 asm 依赖
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(Calculator.class);  // 目标类（无需接口）
enhancer.setCallback(new MethodInterceptor() {
    @Override
    public Object intercept(Object obj, Method method, Object[] args,
                            MethodProxy proxy) throws Throwable {
        System.out.println("[CGLIB] 方法调用前: " + method.getName());
        Object result = proxy.invokeSuper(obj, args);  // 调用父类方法（目标方法）
        System.out.println("[CGLIB] 方法调用后: " + method.getName());
        return result;
    }
});
Calculator proxy = (Calculator) enhancer.create();
```

**`CglibProxyDemo.java` 中的手动模拟**（子类代理的原理）：

```java
// demonstrateManualSubclassProxy()
// 手动创建子类代理，模拟 CGLIB 的思路
class CalculatorProxy extends Calculator {  // 继承目标类

    @Override
    public int add(int a, int b) {
        System.out.println("[代理] add 方法调用前");
        int result = super.add(a, b);  // 调用父类（目标类）方法
        System.out.println("[代理] add 方法调用后, 结果: " + result);
        return result;
    }

    @Override
    public int multiply(int a, int b) {
        System.out.println("[代理] multiply 方法调用前");
        int result = super.multiply(a, b);
        System.out.println("[代理] multiply 方法调用后, 结果: " + result);
        return result;
    }
}
```

CGLIB 做的事情与上面完全一样，只不过是在**运行时动态生成**这个子类字节码，而不是手工编写。

---

## 3.3 MethodProxy vs Method：性能关键差异

CGLIB 的 `MethodInterceptor.intercept()` 有四个参数：

```java
public Object intercept(Object obj,        // 代理对象（子类实例）
                        Method method,     // 反射的 Method 对象
                        Object[] args,     // 调用参数
                        MethodProxy proxy) // CGLIB 的方法代理
    throws Throwable {

    // 方式1：通过 Method 反射调用（较慢，有反射开销）
    Object result1 = method.invoke(obj, args);  // ⚠️ 会调用自身（代理），可能递归！

    // 方式2：通过 MethodProxy 调用父类方法（推荐，无反射开销）
    Object result2 = proxy.invokeSuper(obj, args);  // ✅ 直接调用父类字节码

    // 方式3：通过 MethodProxy 调用具体对象的方法
    Object result3 = proxy.invoke(target, args);  // 如果有单独持有 target 的话
}
```

**必须用 `proxy.invokeSuper(obj, args)`**：
- `method.invoke(obj, args)` 中的 `obj` 是代理对象，调用会再次触发拦截器，造成无限递归
- `proxy.invokeSuper()` 直接调用父类（目标类）的方法，绕过拦截器

---

## 3.4 CGLIB 的限制

```java
// ❌ final 类无法被 CGLIB 代理（无法继承）
public final class EncryptionUtil { }
// CGLIB enhancer.create() 抛出异常：Cannot subclass final class

// ❌ final 方法不会被代理（无法 override）
public class UserService {
    public final User findById(Long id) { ... }  // 这个方法代理无效
    public User save(User user) { ... }           // 这个方法正常代理
}

// ❌ 私有方法不会被代理（子类无法访问父类私有方法）
public class UserService {
    private void internalValidate() { }  // 私有方法不会被代理拦截
}

// ❌ 需要无参构造函数（或通过 ReflectUtils 绕过）
// CGLIB 生成的子类需要调用父类构造函数
```

---

## 3.5 `SpringProxyFactory.java`：CGLIB 在 Spring 中的应用

`SpringProxyFactory.java` 展示了 Spring AOP 选择代理策略的完整逻辑：

```java
// Spring 的代理选择策略（简化版）
public static Object createProxy(Object target, List<Advisor> advisors) {
    Class<?> targetClass = target.getClass();

    // 策略1：目标类实现了接口 → 优先使用 JDK 代理
    if (targetClass.getInterfaces().length > 0) {
        return Proxy.newProxyInstance(
            targetClass.getClassLoader(),
            targetClass.getInterfaces(),
            new SpringInvocationHandler(target, advisors)
        );
    }

    // 策略2：目标类没有接口 → 使用 CGLIB 代理
    // 如果设置了 proxyTargetClass=true，即使有接口也强制用 CGLIB
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(targetClass);
    enhancer.setCallback(new SpringMethodInterceptor(target, advisors));
    return enhancer.create();
}
```

---

## 3.6 本章总结

- **存在原因**：JDK 代理要求有接口，CGLIB 通过继承（子类化）代理无接口的类
- **核心原理**：运行时用 ASM 生成目标类的子类字节码，override 所有非 final 方法插入拦截逻辑
- **MethodProxy.invokeSuper()**：必须用这个调用目标方法，用 `method.invoke(proxy, args)` 会无限递归
- **四大限制**：final 类、final 方法、private 方法、需要可继承的构造函数
- **Spring 策略**：有接口 → JDK 代理；无接口或强制 proxyTargetClass → CGLIB

> **本章对应演示代码**：`CglibProxyDemo.java`（CGLIB 核心概念 + 手动子类代理原理演示）、`SpringProxyFactory.java`（Spring 代理策略选择）

**继续阅读**：[04-代理性能分析.md](./04-代理性能分析.md)
