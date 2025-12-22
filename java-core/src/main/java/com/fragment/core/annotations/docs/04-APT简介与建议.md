# 04 APT 简介与建议（编译期注解处理器）

> 本仓库示例聚焦运行时注解与反射。编译期处理器（APT）适合做“生成代码/校验约束”等任务，通常放在独立模块。

## 基本概念
- **APT（Annotation Processing Tool）**：在编译期扫描源代码注解，生成新代码或做校验。
- 关键 API：`javax.annotation.processing.Processor`、`RoundEnvironment`、`Elements`、`Types`、`Filer`。
- 常用生态：`google/auto-service` 帮助注册处理器；`javapoet` 辅助生成 Java 源码。

## 最小实现步骤
1. 新建独立 maven/gradle 模块（避免把生成源码混入业务源码）。
2. 实现 `Processor`：声明支持的注解与源码版本。
3. 在 `process` 中遍历元素（`TypeElement`/`ExecutableElement` 等），读取注解并生成代码。
4. 通过 `Filer` 写入生成的源码至 `generated-sources` 目录。

## 何时选择 APT
- 需要在编译期生成样板代码（如 Builder、Adapter、Mapper）。
- 需要在编译期执行强校验（如 Dagger/Hilt、Room、AutoValue 体系）。
- 运行时反射有性能/可见性限制，且无需在运行期动态决定行为。

## 建议
- 运行时元数据读取 → 反射更直接。
- 跨模块共享、自动生成类/方法/路由 → 更适合 APT。
- 二者不是对立：APT 生成的结构也可配合运行时注解使用。
