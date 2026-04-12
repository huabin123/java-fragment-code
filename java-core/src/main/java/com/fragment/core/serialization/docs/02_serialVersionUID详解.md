# 第二章：serialVersionUID 详解

## 2.1 serialVersionUID 的作用

`serialVersionUID` 是序列化版本号，用于在反序列化时**验证字节流中的类与当前 JVM 中的类是否兼容**。

反序列化时，JVM 比较：
- 字节流中记录的 `serialVersionUID`
- 当前 JVM 中该类的 `serialVersionUID`

两者不一致 → 抛出 `InvalidClassException`，拒绝反序列化。

---

## 2.2 不声明 serialVersionUID 会发生什么？

```java
// ❌ 没有声明 serialVersionUID
public class User implements Serializable {
    private String username;
    private String email;
}

// 场景复现：
// 1. 用旧版 User 类序列化一个对象，保存到文件
// 2. 给 User 类新增一个字段 private int age;
// 3. 用新版 User 类反序列化旧文件 → 爆炸！
//    java.io.InvalidClassException: User;
//    local class incompatible: stream classdesc serialVersionUID = 1234567890123456789,
//    local class serialVersionUID = 9876543210987654321
```

**原因**：JVM 会根据类的字段、方法等信息**自动计算** `serialVersionUID`。增删字段后，计算结果发生变化，与旧数据不兼容。

---

## 2.3 SerializationVersionDemo：版本升级演示

`SerializationVersionDemo.java` 用代码演示了三种版本升级场景：

### 场景一：新增字段（向后兼容）

```java
// 旧版本（已序列化到文件）
public class UserV1 implements Serializable {
    private static final long serialVersionUID = 1L;  // 显式声明！
    private String username;
    private String email;
}

// 新版本（新增了 age 字段）
public class UserV2 implements Serializable {
    private static final long serialVersionUID = 1L;  // 保持不变！
    private String username;
    private String email;
    private int age;  // 新增字段
}

// 结果：用新版本反序列化旧数据 → 成功！
// age 字段会被设置为默认值（int → 0）
// ✅ 新增字段是兼容的
```

### 场景二：删除字段（向后兼容）

```java
// 新版本（删除了 email 字段）
public class UserV3 implements Serializable {
    private static final long serialVersionUID = 1L;  // 保持不变
    private String username;
    // email 被删除
}

// 结果：用新版本反序列化旧数据 → 成功！
// 旧数据中的 email 字段值被忽略
// ✅ 删除字段是兼容的
```

### 场景三：修改字段类型（不兼容）

```java
// 新版本（把 age 从 int 改为 String）
public class UserV4 implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;
    private String age;  // 类型从 int → String
}

// 结果：反序列化旧数据 → 失败！
// java.io.InvalidClassException: incompatible types for field age
// ❌ 修改字段类型不兼容
```

---

## 2.4 serialVersionUID 的推荐值

```java
// 推荐：始终显式声明，值为 1L（最简单，足够用）
private static final long serialVersionUID = 1L;

// 如果类已经发布过且没有声明，用 IDE 或 serialver 工具生成原来的值
// 避免已有序列化数据失效
// IDE（IntelliJ IDEA）：在类名上 Alt+Enter → Add serialVersionUID field

// 何时修改 serialVersionUID（主动设置不兼容）：
// - 类的结构变化太大，旧的序列化数据已无意义
// - 主动断绝向后兼容，强制客户端更新
private static final long serialVersionUID = 2L;  // 改为 2，所有旧数据作废
```

---

## 2.5 serialVersionUID 的陷阱

### 陷阱一：内部类的 serialVersionUID

```java
public class Outer implements Serializable {
    private static final long serialVersionUID = 1L;

    // ❌ 内部类（非静态）序列化时会隐式持有外部类引用
    // 如果 Outer 不可序列化，序列化 Inner 会失败
    class Inner implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    // ✅ 静态内部类没有外部类引用，序列化更安全
    static class StaticInner implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
```

### 陷阱二：枚举的序列化

```java
// 枚举的序列化方式特殊：只序列化枚举名称，反序列化时通过名称查找
// 不依赖 serialVersionUID，改变枚举值不会影响兼容性
// 但：删除已序列化的枚举常量 → 反序列化时报 InvalidObjectException
public enum Status {
    ACTIVE,
    INACTIVE,
    PENDING  // 如果删除这个，旧数据中是 PENDING 的对象无法反序列化
}
```

### 陷阱三：serialVersionUID 不是加密

```java
// ❌ 错误认知：serialVersionUID 能防止篡改
// 实际上：serialVersionUID 只是一个版本号，攻击者可以任意修改字节流
// 序列化数据完全没有加密或完整性保护

// ✅ 如果需要传输安全，使用：
// 1. HTTPS 传输（网络层加密）
// 2. 数据签名（HMAC-SHA256 等）
// 3. 换用 JSON + JWT 等现代方案
```

---

## 2.6 本章总结

- **作用**：版本验证标识，反序列化时校验字节流与类定义是否兼容
- **不声明的风险**：JVM 自动计算，改动类后自动值变化，旧数据无法反序列化
- **兼容性规则**：新增/删除字段 ✅ 兼容；修改字段类型 ❌ 不兼容
- **最佳实践**：始终显式声明 `private static final long serialVersionUID = 1L;`
- **修改时机**：类结构变化太大，主动断绝旧版数据兼容时才改

> **本章对应演示代码**：`SerializationVersionDemo.java`（三种版本升级场景演示）

**继续阅读**：[03_自定义序列化.md](./03_自定义序列化.md)
