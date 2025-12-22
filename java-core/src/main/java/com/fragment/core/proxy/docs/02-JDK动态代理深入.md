# 02 JDK动态代理深入

## InvocationHandler详解

### 接口定义
```java
public interface InvocationHandler {
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
}
```

### 参数详解
- **proxy**：代理对象实例，注意避免在invoke方法中调用proxy的方法，会导致无限递归
- **method**：被调用的方法对象，包含方法名、参数类型、返回值类型等信息
- **args**：方法调用的参数数组，基本类型会被自动装箱

### 常见实现模式

#### 1. 基础代理模式
```java
public class BasicInvocationHandler implements InvocationHandler {
    private final Object target;
    
    public BasicInvocationHandler(Object target) {
        this.target = target;
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 前置处理
        System.out.println("调用方法: " + method.getName());
        
        // 调用目标方法
        Object result = method.invoke(target, args);
        
        // 后置处理
        System.out.println("方法调用完成");
        
        return result;
    }
}
```

#### 2. 条件代理模式
```java
public class ConditionalInvocationHandler implements InvocationHandler {
    private final Object target;
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 根据方法名或注解决定是否代理
        if (method.getName().startsWith("find")) {
            // 只对查询方法添加缓存逻辑
            return handleQuery(method, args);
        } else {
            // 其他方法直接调用
            return method.invoke(target, args);
        }
    }
}
```

## Proxy类详解

### 核心方法

#### newProxyInstance
```java
public static Object newProxyInstance(
    ClassLoader loader,
    Class<?>[] interfaces,
    InvocationHandler h
) throws IllegalArgumentException
```

**参数说明：**
- `loader`：定义代理类的类加载器
- `interfaces`：代理类要实现的接口列表
- `h`：分派方法调用的调用处理程序

#### getProxyClass
```java
public static Class<?> getProxyClass(
    ClassLoader loader,
    Class<?>... interfaces
) throws IllegalArgumentException
```
获取代理类的Class对象，但不创建实例。

#### isProxyClass
```java
public static boolean isProxyClass(Class<?> cl)
```
判断给定的类是否为代理类。

### 代理类的特性

1. **继承关系**：所有代理类都继承自`java.lang.reflect.Proxy`
2. **接口实现**：实现指定的所有接口
3. **final修饰**：代理类被final修饰，不能被继承
4. **public修饰**：代理类是public的
5. **包名规则**：
   - 如果所有接口都是public的，代理类在`com.sun.proxy`包中
   - 如果有非public接口，代理类在相同包中

## 高级特性

### 1. 多接口代理
```java
// 代理实现多个接口的对象
Class<?>[] interfaces = {UserService.class, Serializable.class, Cloneable.class};
Object proxy = Proxy.newProxyInstance(
    classLoader,
    interfaces,
    invocationHandler
);
```

### 2. 代理类缓存
JDK内部会缓存生成的代理类，相同接口组合的代理类只会生成一次。

### 3. 方法分派优化
```java
public class OptimizedInvocationHandler implements InvocationHandler {
    private final Object target;
    private final Map<Method, MethodHandler> methodHandlers;
    
    public OptimizedInvocationHandler(Object target) {
        this.target = target;
        this.methodHandlers = new HashMap<>();
        initMethodHandlers();
    }
    
    private void initMethodHandlers() {
        // 为不同方法预定义处理器
        methodHandlers.put(findMethod("findById"), this::handleQuery);
        methodHandlers.put(findMethod("save"), this::handleSave);
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodHandler handler = methodHandlers.get(method);
        if (handler != null) {
            return handler.handle(method, args);
        }
        return method.invoke(target, args);
    }
}
```

## 常见陷阱和注意事项

### 1. 避免无限递归
```java
// 错误示例 - 会导致StackOverflowError
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getName().equals("toString")) {
        return proxy.toString(); // 错误：会无限递归
    }
    return method.invoke(target, args);
}

// 正确示例
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (method.getName().equals("toString")) {
        return "Proxy of " + target.getClass().getSimpleName();
    }
    return method.invoke(target, args);
}
```

### 2. 处理Object方法
```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // 特殊处理Object类的方法
    if (method.getDeclaringClass() == Object.class) {
        if ("equals".equals(method.getName())) {
            return proxy == args[0];
        } else if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        } else if ("toString".equals(method.getName())) {
            return "Proxy@" + Integer.toHexString(System.identityHashCode(proxy));
        }
    }
    
    return method.invoke(target, args);
}
```

### 3. 异常处理
```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
        return method.invoke(target, args);
    } catch (InvocationTargetException e) {
        // 重新抛出原始异常
        throw e.getCause();
    } catch (Exception e) {
        // 处理其他异常
        throw new RuntimeException("代理调用失败", e);
    }
}
```

## 性能优化技巧

### 1. 缓存Method对象
```java
public class CachedInvocationHandler implements InvocationHandler {
    private static final Map<String, Method> methodCache = new ConcurrentHashMap<>();
    
    // 缓存常用方法，避免反射查找
}
```

### 2. 减少反射调用
```java
// 对于简单的getter/setter，可以直接操作字段
if (method.getName().startsWith("get")) {
    String fieldName = getFieldName(method.getName());
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
}
```

### 3. 使用MethodHandle（Java 7+）
```java
public class MethodHandleInvocationHandler implements InvocationHandler {
    private final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private final Map<Method, MethodHandle> handleCache = new ConcurrentHashMap<>();
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodHandle handle = handleCache.computeIfAbsent(method, m -> {
            try {
                return lookup.unreflect(m).bindTo(target);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        
        return handle.invokeWithArguments(args);
    }
}
```

## 实际应用示例

参考示例代码：
- `JdkProxyDemo.java`：包含基础代理、日志代理、性能监控代理、缓存代理等完整示例
- `AopFramework.java`：基于JDK代理实现的简单AOP框架
