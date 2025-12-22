# Java 反射深入学习

本模块提供了 Java 反射机制的全面学习资源，包括基础概念、高级特性、性能优化和实际应用案例。

## 📁 目录结构

```
reflection/
├── Person.java                    # 示例 POJO 类
├── BasicReflectionDemo.java       # 基础反射操作
├── GenericReflectionDemo.java     # 泛型反射处理
├── ArrayReflectionDemo.java       # 数组反射操作
├── PerformanceDemo.java          # 性能对比测试
├── BeanCopyUtil.java             # 实用工具示例
├── ReflectionDemoMain.java       # 统一演示入口
├── docs/                         # 文档目录
│   ├── 01-反射基础原理.md
│   ├── 02-字段和方法反射.md
│   ├── 03-泛型反射深入.md
│   ├── 04-数组反射操作.md
│   ├── 05-反射性能与优化.md
│   └── 06-实际应用案例.md
└── README.md                     # 本文件
```

## 🚀 快速开始

运行主演示程序：
```java
com.fragment.core.reflection.ReflectionDemoMain
```

或者单独运行各个演示：
- `BasicReflectionDemo` - 基础操作
- `GenericReflectionDemo` - 泛型处理  
- `ArrayReflectionDemo` - 数组操作
- `PerformanceDemo` - 性能测试

## 📚 学习路径

1. **基础入门** → `01-反射基础原理.md` + `BasicReflectionDemo.java`
2. **核心操作** → `02-字段和方法反射.md`
3. **高级特性** → `03-泛型反射深入.md` + `04-数组反射操作.md`
4. **性能优化** → `05-反射性能与优化.md` + `PerformanceDemo.java`
5. **实际应用** → `06-实际应用案例.md` + `BeanCopyUtil.java`

## 🎯 核心知识点

- **Class 对象获取**：三种方式及其适用场景
- **字段访问**：私有字段访问、静态字段操作
- **方法调用**：重载方法处理、异常处理
- **泛型反射**：Type 体系、泛型擦除、通配符处理
- **数组操作**：动态创建、多维数组、类型检查
- **性能优化**：缓存策略、替代方案、最佳实践

## ⚡ 性能对比

基于 `PerformanceDemo` 的测试结果：
- 直接调用：基准性能
- 反射调用：慢 50-100 倍
- 缓存反射：提升 10-20 倍

## 🛠️ 实用工具

- `BeanCopyUtil`：对象属性拷贝工具
- 性能监控：反射调用统计
- 缓存机制：Method/Field 对象缓存

## 📖 扩展阅读

- JVM 规范中的反射实现
- 字节码生成技术（ASM、CGLIB）
- MethodHandle 与 VarHandle
- 模块系统对反射的影响

---

**注意**：反射虽然强大，但应谨慎使用。在性能敏感的场景中，优先考虑直接调用或编译时代码生成等替代方案。
