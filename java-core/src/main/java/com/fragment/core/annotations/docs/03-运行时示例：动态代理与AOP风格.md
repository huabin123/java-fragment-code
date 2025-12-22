# 03 运行时示例：动态代理与 AOP 风格

## 目标
使用 JDK 动态代理基于方法注解 `@LogExecutionTime` 打印方法耗时，体现“注解 + 反射”的典型组合用法。

## 关键点
- **动态代理**：`java.lang.reflect.Proxy` + `InvocationHandler`。
- **注解检测**：`implMethod.isAnnotationPresent(LogExecutionTime.class)`。
- **拦截逻辑**：调用前后统计耗时，并打印结果。

对应文件：
- `annotations/LogExecutionTime.java`
- `annotations/ProxyFactory.java`
- `annotations/ExampleService.java`, `annotations/ExampleServiceImpl.java`
- `annotations/AnnotationDemoMain.java`

## 代码走读（核心逻辑）
```java
Method implMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
boolean enabled = implMethod.isAnnotationPresent(LogExecutionTime.class) || method.isAnnotationPresent(LogExecutionTime.class);
long start = System.nanoTime();
Object ret = implMethod.invoke(target, args);
System.out.println("[LogExecutionTime] ... took ...");
```

## 局限与扩展
- JDK 动态代理仅支持接口；对类的代理通常用 CGLIB/ByteBuddy。
- 真实项目可将“切点表达式 + 拦截器”抽象出来，构建轻量 AOP。
- 与 Bean 容器（如 Spring）结合，可在实例创建时统一织入代理。
