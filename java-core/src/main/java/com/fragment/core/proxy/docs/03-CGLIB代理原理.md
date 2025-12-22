# 03 CGLIB代理原理

## CGLIB简介

CGLIB（Code Generation Library）是一个强大的、高性能的代码生成库，它可以在运行时扩展Java类和实现接口。

### 核心特点
- **基于继承**：通过创建目标类的子类实现代理
- **字节码生成**：使用ASM字节码操作框架
- **无接口限制**：可以代理普通类，不需要实现接口
- **高性能**：生成的代理类性能接近直接调用

## CGLIB vs JDK动态代理

| 特性 | JDK动态代理 | CGLIB代理 |
|------|-------------|-----------|
| 实现方式 | 基于接口反射 | 基于继承字节码生成 |
| 代理对象 | 只能代理接口 | 可以代理类和接口 |
| 性能 | 较慢（反射调用） | 较快（直接调用） |
| 限制 | 必须有接口 | 不能代理final类/方法 |
| 依赖 | JDK内置 | 需要第三方库 |

## CGLIB核心API

### Enhancer类
`Enhancer`是CGLIB的核心类，用于生成代理类。

```java
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(Calculator.class);  // 设置父类
enhancer.setCallback(methodInterceptor);   // 设置回调
Calculator proxy = (Calculator) enhancer.create(); // 创建代理
```

### MethodInterceptor接口
```java
public interface MethodInterceptor extends Callback {
    Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) 
        throws Throwable;
}
```

**参数说明：**
- `obj`：代理对象实例
- `method`：被拦截的方法
- `args`：方法参数
- `proxy`：方法代理，用于调用父类方法

### MethodProxy类
`MethodProxy`提供了高效的方法调用机制：

```java
// 调用父类方法（推荐）
Object result = methodProxy.invokeSuper(obj, args);

// 调用原始方法（可能导致递归）
Object result = methodProxy.invoke(target, args);
```

## CGLIB代理的实现原理

### 1. 字节码生成过程

1. **分析目标类**：扫描目标类的方法和字段
2. **生成子类**：创建目标类的子类
3. **重写方法**：重写所有非final的public/protected方法
4. **织入拦截逻辑**：在重写的方法中调用MethodInterceptor
5. **加载类**：将生成的字节码加载到JVM

### 2. 生成的代理类结构

```java
// CGLIB生成的代理类示例
public class Calculator$$EnhancerByCGLIB$$12345678 extends Calculator {
    private MethodInterceptor CGLIB$CALLBACK_0;
    private static final Method CGLIB$add$0$Method;
    private static final MethodProxy CGLIB$add$0$Proxy;
    
    static {
        // 静态初始化Method和MethodProxy对象
        CGLIB$add$0$Method = Calculator.class.getMethod("add", int.class, int.class);
        CGLIB$add$0$Proxy = MethodProxy.create(Calculator.class, 
            Calculator$$EnhancerByCGLIB$$12345678.class, 
            "(II)I", "add", "CGLIB$add$0");
    }
    
    @Override
    public int add(int a, int b) {
        MethodInterceptor interceptor = this.CGLIB$CALLBACK_0;
        if (interceptor != null) {
            return (Integer) interceptor.intercept(this, 
                CGLIB$add$0$Method, 
                new Object[]{a, b}, 
                CGLIB$add$0$Proxy);
        }
        return super.add(a, b);
    }
    
    // 生成的快速调用方法
    final int CGLIB$add$0(int a, int b) {
        return super.add(a, b);
    }
}
```

### 3. FastClass机制

CGLIB使用FastClass机制避免反射调用：

```java
// 为目标类生成FastClass
public class Calculator$$FastClassByCGLIB$$87654321 extends FastClass {
    public Object invoke(int index, Object obj, Object[] args) {
        Calculator target = (Calculator) obj;
        switch (index) {
            case 0: return target.add((Integer)args[0], (Integer)args[1]);
            case 1: return target.subtract((Integer)args[0], (Integer)args[1]);
            // ... 其他方法
        }
        throw new IllegalArgumentException("Invalid method index");
    }
}
```

## CGLIB的限制

### 1. 不能代理的情况
- **final类**：无法创建子类
- **final方法**：无法重写
- **private方法**：子类无法访问
- **static方法**：属于类，不能重写

### 2. 构造器问题
```java
public class ProblematicClass {
    public ProblematicClass(String required) {
        // 没有无参构造器
    }
}

// CGLIB需要无参构造器，或者需要特殊处理
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(ProblematicClass.class);
enhancer.setCallback(interceptor);

// 需要指定构造器参数
Object proxy = enhancer.create(
    new Class[]{String.class}, 
    new Object[]{"parameter"}
);
```

## 高级特性

### 1. 多重回调
```java
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(Calculator.class);

// 设置多个回调
enhancer.setCallbacks(new Callback[]{
    new LoggingInterceptor(),    // 索引0
    new CachingInterceptor(),    // 索引1
    NoOp.INSTANCE               // 索引2：不拦截
});

// 设置回调过滤器
enhancer.setCallbackFilter(new CallbackFilter() {
    public int accept(Method method) {
        if (method.getName().startsWith("get")) {
            return 1; // 使用缓存拦截器
        } else if (method.getName().startsWith("set")) {
            return 0; // 使用日志拦截器
        }
        return 2; // 不拦截
    }
});
```

### 2. 接口实现
```java
// 让代理类实现额外的接口
enhancer.setInterfaces(new Class[]{Serializable.class, Cloneable.class});
```

### 3. 命名策略
```java
// 自定义代理类名称
enhancer.setNamingPolicy(new NamingPolicy() {
    public String getClassName(String prefix, String source, Object key, Predicate names) {
        return "MyProxy$" + source;
    }
});
```

## 性能优化

### 1. 缓存代理类
```java
public class CglibProxyFactory {
    private static final Map<Class<?>, Class<?>> proxyClassCache = new ConcurrentHashMap<>();
    
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> targetClass, MethodInterceptor interceptor) {
        Class<?> proxyClass = proxyClassCache.computeIfAbsent(targetClass, clazz -> {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(clazz);
            enhancer.setCallback(interceptor);
            return enhancer.createClass();
        });
        
        try {
            return (T) proxyClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建代理失败", e);
        }
    }
}
```

### 2. 使用MethodProxy
```java
public class OptimizedMethodInterceptor implements MethodInterceptor {
    private final Object target;
    
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) 
            throws Throwable {
        // 使用MethodProxy.invokeSuper，比反射快
        return proxy.invokeSuper(obj, args);
        
        // 避免使用method.invoke，会有反射开销
        // return method.invoke(target, args);
    }
}
```

## 实际应用场景

### 1. Spring AOP
Spring框架在以下情况使用CGLIB：
- 目标类没有实现接口
- 强制使用CGLIB（`proxy-target-class="true"`）

### 2. Hibernate延迟加载
```java
// Hibernate使用CGLIB创建实体代理
public class User$$EnhancerByHibernate extends User {
    @Override
    public Set<Order> getOrders() {
        if (!initialized) {
            // 延迟加载逻辑
            loadOrders();
        }
        return super.getOrders();
    }
}
```

### 3. Mock框架
Mockito等测试框架使用CGLIB创建Mock对象。

## 示例代码位置

- `CglibProxyDemo.java`：CGLIB概念演示和手动子类代理
- `Calculator.java`：用于CGLIB代理的目标类
- `AopFramework.java`：展示了类似CGLIB的拦截器模式

**注意**：由于CGLIB是第三方库，示例中提供了概念演示和手动实现的子类代理来说明原理。实际使用需要添加CGLIB依赖。
