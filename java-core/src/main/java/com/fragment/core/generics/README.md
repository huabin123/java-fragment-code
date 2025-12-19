# Java 泛型学习指南

本目录包含了 Java 泛型的学习资料和示例代码，帮助你理解和掌握 Java 泛型的使用。

## 目录结构

### 示例代码

1. **[GenericWildcardDemo.java](./GenericWildcardDemo.java)** - 泛型通配符基础示例
   - 演示 `<? extends T>` 和 `<? super T>` 的基本用法和限制
   - 展示类型安全转换的重要性

2. **[PECSPrincipleDemo.java](./PECSPrincipleDemo.java)** - PECS 原则示例
   - Producer Extends, Consumer Super 原则的实际应用
   - 通过动物层次结构展示泛型通配符的使用场景

3. **[GenericPitfallsDemo.java](./GenericPitfallsDemo.java)** - 泛型常见陷阱和最佳实践
   - 类型擦除问题
   - 泛型数组创建问题
   - 原始类型的问题
   - 无界通配符的使用
   - 泛型方法的应用

4. **[GenericRepositoryDemo.java](./GenericRepositoryDemo.java)** - 泛型在实际应用中的示例
   - 通用仓库模式的实现
   - 泛型接口和实现类
   - 服务层中的泛型应用

5. **[GenericErasureAdvancedDemo.java](./GenericErasureAdvancedDemo.java)** - 泛型擦除进阶示例
   - 运行时类型相同（类型擦除）
   - 桥接方法（bridge method）
   - Type Token 与泛型数组创建
   - 通配符捕获（wildcard capture）
   - 使用 `@SafeVarargs` 处理 varargs 泛型

### 文档资料

1. **[docs/01_GenericsWildcards.md](./docs/01_GenericsWildcards.md)** - 泛型通配符详解
   - 泛型通配符的概念和用法
   - `<? extends T>` 和 `<? super T>` 的特点和使用场景
   - PECS 原则的解释
   - 类型安全转换的注意事项

2. **[docs/02_GenericsUsageSummary.md](./docs/02_GenericsUsageSummary.md)** - 泛型使用总结
   - 泛型通配符的选择原则
   - 常见泛型使用场景
   - 类型安全转换
   - 泛型的局限性
   - 泛型最佳实践
   - 实际应用示例

3. **[docs/03_GenericsErasure_FakeVsReal.md](./docs/03_GenericsErasure_FakeVsReal.md)** - 泛型擦除、假泛型与真泛型
   - 类型擦除的原理与限制
   - Java 的擦除式（非具体化）泛型为何被称为“假泛型”
   - 真泛型（reified）的概念与对比
   - Java 常见实践（Type Token、Supplier、Array.newInstance 等）

## 学习路径

1. 首先阅读 [docs/01_GenericsWildcards.md](./docs/01_GenericsWildcards.md) 了解泛型通配符的基本概念
2. 查看 [GenericWildcardDemo.java](./GenericWildcardDemo.java) 了解基本用法
3. 学习 [PECSPrincipleDemo.java](./PECSPrincipleDemo.java) 深入理解 PECS 原则
4. 通过 [GenericPitfallsDemo.java](./GenericPitfallsDemo.java) 了解泛型使用中的常见陷阱
5. 学习 [GenericRepositoryDemo.java](./GenericRepositoryDemo.java) 掌握泛型在实际项目中的应用
6. 阅读 [docs/03_GenericsErasure_FakeVsReal.md](./docs/03_GenericsErasure_FakeVsReal.md) 深入理解类型擦除、假泛型与真泛型
7. 最后阅读 [docs/02_GenericsUsageSummary.md](./docs/02_GenericsUsageSummary.md) 进行知识总结

## 关键点总结

- 泛型通配符 `<? extends T>` 适合从集合中读取数据，但不能添加元素
- 泛型通配符 `<? super T>` 适合向集合中写入数据，但读取时只能作为 Object 类型
- 在无泛型限制的集合赋值给泛型限制的集合时，需要进行类型检查以避免 ClassCastException
- 理解泛型类型擦除的概念及其影响
- 掌握泛型在实际项目中的应用模式
