# Java 动态代理深入学习

本模块提供了 Java 动态代理机制的全面学习资源，包括 JDK 动态代理、CGLIB 代理、性能分析和实际应用案例。

## 📁 目录结构

```
proxy/
├── User.java                     # 用户实体类
├── UserService.java              # 用户服务接口
├── UserServiceImpl.java          # 用户服务实现
├── Calculator.java               # 计算器类（CGLIB演示）
├── JdkProxyDemo.java             # JDK动态代理演示
├── CglibProxyDemo.java           # CGLIB代理演示
├── ProxyPerformanceDemo.java     # 代理性能对比
├── AopFramework.java             # 简单AOP框架
├── ProxyDemoMain.java            # 统一演示入口
├── docs/                         # 文档目录
│   ├── 01-动态代理基础原理.md
│   ├── 02-JDK动态代理深入.md
│   ├── 03-CGLIB代理原理.md
│   ├── 04-代理性能分析.md
│   └── 05-AOP与实际应用.md
└── README.md                     # 本文件
```

## 🚀 快速开始

运行主演示程序：
```java
com.fragment.core.proxy.ProxyDemoMain
```

或者单独运行各个演示：
- `JdkProxyDemo` - JDK动态代理
- `CglibProxyDemo` - CGLIB代理概念
- `ProxyPerformanceDemo` - 性能对比测试

## 📚 学习路径

1. **基础概念** → `01-动态代理基础原理.md` + `JdkProxyDemo.java`
2. **JDK代理深入** → `02-JDK动态代理深入.md`
3. **CGLIB原理** → `03-CGLIB代理原理.md` + `CglibProxyDemo.java`
4. **性能分析** → `04-代理性能分析.md` + `ProxyPerformanceDemo.java`
5. **实际应用** → `05-AOP与实际应用.md` + `AopFramework.java`

## 🎯 核心知识点

### JDK动态代理
- **基于接口**：只能代理实现了接口的类
- **反射机制**：使用 `InvocationHandler` 处理方法调用
- **代理类生成**：运行时动态生成继承自 `Proxy` 的代理类

### CGLIB代理
- **基于继承**：创建目标类的子类实现代理
- **字节码生成**：使用 ASM 框架生成字节码
- **无接口限制**：可以代理普通类

### 性能对比
- **直接调用**：基准性能
- **CGLIB代理**：约慢 1.5-3 倍
- **JDK代理**：约慢 10-50 倍

## 🛠️ 实用功能

### 多种代理模式
- 基础代理：方法调用拦截
- 日志代理：方法执行日志记录
- 性能代理：方法执行时间监控
- 缓存代理：查询结果缓存

### AOP框架
- 切面定义：`Aspect` 接口
- 通知类型：前置、后置、异常、最终通知
- 多切面支持：一个代理对象可应用多个切面

## 📖 应用场景

1. **AOP编程**
   - 日志记录
   - 性能监控
   - 事务管理
   - 权限控制

2. **框架开发**
   - Spring Bean 代理
   - MyBatis Mapper 接口
   - RPC 远程调用

3. **设计模式**
   - 装饰器模式
   - 适配器模式
   - 外观模式

## ⚠️ 注意事项

### JDK代理限制
- 只能代理接口
- 性能相对较低
- 避免在 `invoke` 方法中调用代理对象

### CGLIB代理限制
- 不能代理 `final` 类和方法
- 需要无参构造器
- 依赖第三方库

## 🔧 优化建议

1. **缓存代理对象**：避免重复创建
2. **选择合适的代理类型**：根据场景选择 JDK 或 CGLIB
3. **减少拦截逻辑**：避免复杂的切面逻辑
4. **使用 MethodHandle**：Java 7+ 的高性能替代方案

---

**提示**：动态代理是 AOP 编程的基础，理解其原理有助于更好地使用 Spring 等框架。在性能敏感的场景中，考虑使用编译时代理或直接的设计模式实现。
