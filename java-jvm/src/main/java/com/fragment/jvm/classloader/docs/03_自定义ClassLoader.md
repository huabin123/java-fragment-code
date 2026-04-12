# 第三章：自定义 ClassLoader

## 3.1 为什么需要自定义 ClassLoader？

标准的 AppClassLoader 只能从 classpath 加载 .class 文件。以下场景需要自定义：

```
1. 加密类文件：.class 文件加密存储，运行时解密后加载
2. 网络加载：从远程服务器动态下载类
3. 热部署：不重启 JVM 更新某个类的实现
4. 隔离：同一 JVM 中运行多个版本的同名类（如 Tomcat 的 WebApp 隔离）
5. 字节码增强：在加载时动态修改字节码（如 AOP 织入）
```

---

## 3.2 正确的自定义方式：重写 findClass()

```java
// CustomClassLoaderDemo.java 的核心实现

public class FileClassLoader extends ClassLoader {

    private final String classPath;

    public FileClassLoader(String classPath) {
        super();  // 父加载器 = AppClassLoader（双亲委派）
        this.classPath = classPath;
    }

    // ✅ 只重写 findClass()，不重写 loadClass()
    // loadClass() 维护了双亲委派逻辑，不应被破坏
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String fileName = classPath + File.separator
            + name.replace('.', File.separatorChar) + ".class";

        try (FileInputStream fis = new FileInputStream(fileName)) {
            byte[] bytes = fis.readAllBytes();
            // 可在此处解密 bytes
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException("找不到类: " + name, e);
        }
    }
}

// 使用
ClassLoader loader = new FileClassLoader("/custom/classes");
Class<?> clazz = loader.loadClass("com.example.HelloWorld");
Object instance = clazz.getDeclaredConstructor().newInstance();
```

---

## 3.3 加密类加载器

`CustomClassLoaderDemo.java` 演示了加密类的加载流程：

```java
// 加密：用简单 XOR 加密 .class 文件
public static void encryptClass(byte[] classBytes, String outputPath, byte key) throws IOException {
    byte[] encrypted = new byte[classBytes.length];
    for (int i = 0; i < classBytes.length; i++) {
        encrypted[i] = (byte) (classBytes[i] ^ key);  // XOR 加密
    }
    Files.write(Paths.get(outputPath), encrypted);
}

// 解密类加载器
public class EncryptedClassLoader extends ClassLoader {
    private final byte xorKey;

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] encrypted = readEncryptedFile(name);
        // 解密
        byte[] decrypted = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            decrypted[i] = (byte) (encrypted[i] ^ xorKey);
        }
        return defineClass(name, decrypted, 0, decrypted.length);
    }
}
```

---

## 3.4 类隔离：同 JVM 多版本共存

```java
// 不同 ClassLoader 加载的同名类，JVM 认为是不同的类
// Tomcat 利用此机制实现多 WebApp 隔离

ClassLoader loader1 = new FileClassLoader("/webapps/app1/WEB-INF/classes");
ClassLoader loader2 = new FileClassLoader("/webapps/app2/WEB-INF/classes");

// app1 和 app2 都有 com.example.Config，但加载后是独立的 Class 对象
Class<?> config1 = loader1.loadClass("com.example.Config");
Class<?> config2 = loader2.loadClass("com.example.Config");

config1 == config2;  // false！完全独立，互不影响
```

---

## 3.5 常见陷阱

```java
// 陷阱1：重写 loadClass() 破坏双亲委派
// ❌ 直接加载，跳过父加载器 → 可能加载到攻击者替换的核心类
@Override
public Class<?> loadClass(String name) {
    return findClass(name);  // 危险！
}

// 陷阱2：ClassLoader 内存泄漏
// 自定义 ClassLoader 被持有 → 它加载的所有 Class 对象无法卸载 → 方法区撑爆
// 确保 ClassLoader 在不需要时能被 GC：
// - 不要将 ClassLoader 存入静态变量
// - 热部署后旧 ClassLoader 要能被 GC 回收（无强引用）

// 陷阱3：ClassCastException
// 不同 ClassLoader 加载的同名类无法互转
Object obj = customLoader.loadClass("com.example.Foo").newInstance();
Foo foo = (Foo) obj;  // ClassCastException！Foo 由 AppClassLoader 加载，obj 由 customLoader 加载
// 解决：通过接口或反射操作，接口由父 ClassLoader 加载，实现类由自定义 ClassLoader 加载
```

---

## 3.6 本章总结

- **自定义场景**：加密加载、网络加载、热部署、多版本隔离、字节码增强
- **正确方式**：只重写 `findClass()`，不重写 `loadClass()`（保持双亲委派）
- **类隔离原理**：不同 ClassLoader 加载的同名类是不同的 Class 对象
- **三大陷阱**：破坏双亲委派（安全风险）、ClassLoader 泄漏（Metaspace OOM）、ClassCastException（类型不匹配）

> **本章对应演示代码**：`CustomClassLoaderDemo.java`（文件加载、加密加载、类隔离）、`HotDeployDemo.java`（热部署实现）

**继续阅读**：[04_热部署与模块化.md](./04_热部署与模块化.md)
