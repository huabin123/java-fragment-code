# java.io 包设计模式详解

> **核心问题**：java.io 包是如何运用经典设计模式来构建一套灵活、可扩展的 I/O 体系的？

---

## 一、java.io 类体系总览

在深入设计模式之前，先看清楚 `java.io` 的整体类层次，这有助于理解每种模式的"用武之地"。

```
java.io 核心类层次（简化）

字节流（8-bit）
├── InputStream（抽象基类）
│   ├── FileInputStream
│   ├── ByteArrayInputStream
│   ├── PipedInputStream
│   └── FilterInputStream           ← 装饰器基类
│       ├── BufferedInputStream     ← 装饰器（缓冲）
│       ├── DataInputStream         ← 装饰器（基本类型读取）
│       ├── PushbackInputStream     ← 装饰器（回退）
│       └── LineNumberInputStream   ← 装饰器（行号跟踪，已废弃）
│
└── OutputStream（抽象基类）
    ├── FileOutputStream
    ├── ByteArrayOutputStream
    ├── PipedOutputStream
    └── FilterOutputStream          ← 装饰器基类
        ├── BufferedOutputStream    ← 装饰器（缓冲）
        ├── DataOutputStream        ← 装饰器（基本类型写入）
        └── PrintStream             ← 装饰器（格式化输出）

字符流（16-bit Unicode）
├── Reader（抽象基类）
│   ├── FileReader
│   ├── CharArrayReader
│   ├── StringReader
│   ├── PipedReader
│   ├── InputStreamReader           ← 适配器（字节流→字符流）
│   └── BufferedReader              ← 装饰器（缓冲 + 行读取）
│
└── Writer（抽象基类）
    ├── FileWriter
    ├── CharArrayWriter
    ├── StringWriter
    ├── PipedWriter
    ├── OutputStreamWriter          ← 适配器（字节流→字符流）
    └── BufferedWriter              ← 装饰器（缓冲 + 行分隔符）
```

---

## 二、装饰器模式（Decorator Pattern）

### 2.1 模式说明

**装饰器模式**：在不改变原有对象接口和结构的前提下，通过"包装"的方式动态地为对象添加新的功能。

**核心角色**：
- **Component（抽象组件）**：`InputStream` / `OutputStream` / `Reader` / `Writer`
- **ConcreteComponent（具体组件）**：`FileInputStream`、`ByteArrayInputStream` 等原始流
- **Decorator（抽象装饰器）**：`FilterInputStream` / `FilterOutputStream`
- **ConcreteDecorator（具体装饰器）**：`BufferedInputStream`、`DataInputStream` 等

### 2.2 类结构图

```
        InputStream（Component - 抽象组件）
              ↑
    ┌─────────┴──────────────────┐
    │                            │
FileInputStream          FilterInputStream（Decorator - 持有 InputStream 引用）
（ConcreteComponent）          ↑
                    ┌───────────┼───────────┐
                    │           │           │
            BufferedInputStream DataInputStream PushbackInputStream
          （ConcreteDecorator） （ConcreteDecorator）（ConcreteDecorator）
```

### 2.3 FilterInputStream 源码剖析

```java
// 装饰器基类：持有被装饰对象的引用，并实现相同接口
public class FilterInputStream extends InputStream {

    // 被装饰的 InputStream（可以是任意 InputStream 实现）
    protected volatile InputStream in;

    protected FilterInputStream(InputStream in) {
        this.in = in;
    }

    // 默认委托给被装饰对象
    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
    // ... 其他方法同样委托
}
```

```java
// 具体装饰器：在委托的基础上增加缓冲功能
public class BufferedInputStream extends FilterInputStream {

    // 内部缓冲区
    protected volatile byte[] buf;
    protected int count;    // 缓冲区中有效字节数
    protected int pos;      // 当前读取位置

    public BufferedInputStream(InputStream in, int size) {
        super(in);  // 持有被装饰对象
        buf = new byte[size];
    }

    @Override
    public synchronized int read() throws IOException {
        if (pos >= count) {
            // 缓冲区已空，从底层流批量读取（核心增强）
            fill();
            if (pos >= count) return -1;
        }
        return getBufIfOpen()[pos++] & 0xff;
    }
    // ...
}
```

### 2.4 使用示例与层层包装

```java
// 单层装饰：为文件流增加缓冲
InputStream buffered = new BufferedInputStream(new FileInputStream("data.bin"));

// 双层装饰：为缓冲文件流增加基本类型读取能力
DataInputStream dataIn = new DataInputStream(
    new BufferedInputStream(
        new FileInputStream("data.bin")
    )
);
int num = dataIn.readInt();     // 读取 4 字节整数
double d = dataIn.readDouble(); // 读取 8 字节浮点

// 三层装饰（写入方向）
PrintStream out = new PrintStream(
    new BufferedOutputStream(
        new FileOutputStream("log.txt")
    ),
    true,           // 自动 flush
    "UTF-8"
);
out.printf("Hello %s, age=%d%n", "World", 18);
```

**关键特性**：每一层装饰器都实现了相同的 `InputStream` 接口，因此可以无限嵌套，且对上层调用者完全透明。

### 2.5 为什么选装饰器而不是继承？

| 方案 | 问题 |
|------|------|
| **继承** | 功能组合爆炸：`BufferedFileInputStream`、`DataFileInputStream`、`BufferedDataFileInputStream`... 类数量指数增长 |
| **装饰器** | 任意组合，类数量线性增长；运行时动态增减功能 |

```java
// 继承方案的组合爆炸（假设有 M 种数据源 × N 种增强功能）
// 需要 M × N 个类 —— 不可维护

// 装饰器方案：M + N 个类即可覆盖所有组合
// 需要哪种功能，就用对应的装饰器包一层
```

---

## 三、适配器模式（Adapter Pattern）

### 3.1 模式说明

**适配器模式**：将一个接口转换成客户端期望的另一个接口，让原本因接口不兼容而无法协作的类可以一起工作。

在 `java.io` 中的体现：**字节流 → 字符流的桥接**。

**核心角色**：
- **Target（目标接口）**：`Reader` / `Writer`（字符流接口）
- **Adaptee（被适配者）**：`InputStream` / `OutputStream`（字节流）
- **Adapter（适配器）**：`InputStreamReader` / `OutputStreamWriter`

### 3.2 InputStreamReader 源码剖析

```java
// 适配器：将字节流 InputStream 适配为字符流 Reader
public class InputStreamReader extends Reader {

    // 持有字节流，内部使用 StreamDecoder 完成字节→字符的解码
    private final StreamDecoder sd;

    // 构造时接收 InputStream（被适配者），指定字符集
    public InputStreamReader(InputStream in, Charset cs) {
        super(in);
        sd = StreamDecoder.forInputStreamReader(in, this, cs);
    }

    // 实现 Reader 接口，内部委托给 StreamDecoder 解码字节为字符
    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        return sd.read(cbuf, offset, length);
    }

    @Override
    public boolean ready() throws IOException {
        return sd.ready();
    }

    // 获取实际使用的编码名称
    public String getEncoding() {
        return sd.getEncoding();
    }
}
```

### 3.3 使用示例

```java
// 将字节流 System.in 适配为字符流，并指定 UTF-8 编码
Reader reader = new InputStreamReader(System.in, StandardCharsets.UTF_8);

// 进一步包装为 BufferedReader（装饰器模式叠加使用）
BufferedReader br = new BufferedReader(reader);
String line = br.readLine();

// 读取 HTTP 响应体（字节流 → 字符流）
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
BufferedReader response = new BufferedReader(
    new InputStreamReader(conn.getInputStream(), "UTF-8")
);

// 写入方向：将字节流 FileOutputStream 适配为字符流
Writer writer = new OutputStreamWriter(new FileOutputStream("out.txt"), "GBK");
writer.write("中文内容");
writer.flush();
```

### 3.4 适配器 vs 装饰器的区别

| 维度 | 装饰器 | 适配器 |
|------|--------|--------|
| **目的** | 增强功能，接口不变 | 转换接口，以实现兼容 |
| **接口关系** | 输入输出同一个接口 | 输入输出不同接口 |
| **java.io 示例** | `BufferedInputStream` wraps `InputStream` | `InputStreamReader` wraps `InputStream` → `Reader` |

---

## 四、模板方法模式（Template Method Pattern）

### 4.1 模式说明

**模板方法模式**：在抽象基类中定义算法骨架，将某些步骤延迟到子类实现。子类可以重写特定步骤，但不能改变整体算法结构。

### 4.2 在 java.io 中的体现

`InputStream` 抽象类中，`read(byte[] b, int off, int len)` 是模板方法，它的算法骨架已经实现，依赖抽象方法 `read()` 来读取单个字节。

```java
// 抽象基类 InputStream
public abstract class InputStream implements Closeable {

    // 抽象方法：子类必须实现，读取单个字节
    public abstract int read() throws IOException;

    // 模板方法：定义了"循环读取多个字节"的算法骨架
    // 依赖 read() 抽象方法，无需子类重复实现
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) return 0;

        int c = read();  // ← 调用抽象方法（钩子）
        if (c == -1) return -1;
        b[off] = (byte) c;

        int i = 1;
        try {
            for (; i < len; i++) {
                c = read();  // ← 继续调用抽象方法
                if (c == -1) break;
                b[off + i] = (byte) c;
            }
        } catch (IOException ee) {
            // 忽略中途 IOException，已读到的数据正常返回
        }
        return i;
    }

    // 模板方法：skip 的实现骨架也依赖 read()
    public long skip(long n) throws IOException {
        long remaining = n;
        // ... 循环调用 read() 跳过字节
        byte[] skipBuffer = new byte[(int) Math.min(remaining, 2048)];
        while (remaining > 0) {
            int nr = read(skipBuffer, 0, (int) Math.min(remaining, skipBuffer.length));
            if (nr < 0) break;
            remaining -= nr;
        }
        return n - remaining;
    }
}
```

### 4.3 子类只需实现核心方法

```java
// FileInputStream 只需实现 read()，其余继承自 InputStream 的模板
public class FileInputStream extends InputStream {
    @Override
    public native int read() throws IOException;  // native 实现，读取单字节

    // read(byte[], int, int) 由父类模板方法提供，无需重写
    // skip() 也可继承父类模板（虽然 FileInputStream 重写了 skip 以提高效率）
}
```

```java
// 自定义流时只需实现 read()，其余功能"免费"获得
public class CountingInputStream extends InputStream {
    private final InputStream in;
    private long count = 0;

    public CountingInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) count++;
        return b;
    }

    public long getCount() { return count; }

    // read(byte[])、skip()、available() 均由父类模板方法提供
}
```

### 4.4 Writer 中的模板方法

```java
public abstract class Writer implements Appendable, Closeable, Flushable {

    // 抽象方法：子类必须实现
    abstract public void write(char[] cbuf, int off, int len) throws IOException;

    // 模板方法：单字符写入
    public void write(int c) throws IOException {
        synchronized (lock) {
            writeBuffer[0] = (char) c;
            write(writeBuffer, 0, 1);  // ← 调用抽象方法
        }
    }

    // 模板方法：字符串写入
    public void write(String str, int off, int len) throws IOException {
        synchronized (lock) {
            char[] cbuf = str.toCharArray();  // 转数组
            write(cbuf, off, len);  // ← 调用抽象方法
        }
    }

    // 模板方法：Appendable 接口的 append
    public Writer append(CharSequence csq) throws IOException {
        write(String.valueOf(csq));  // 复用 write
        return this;
    }
}
```

---

## 五、策略模式（Strategy Pattern）

### 5.1 模式说明

**策略模式**：定义一系列算法，将每个算法封装起来，并使它们可以互相替换。

在 `java.io` 中体现为：**不同的流实现代表不同的 I/O 策略**，调用者只依赖抽象接口（`InputStream`/`OutputStream`）。

### 5.2 在 java.io 中的体现

```java
// 策略接口：InputStream
public abstract class InputStream { ... }

// 策略实现1：从文件读取
InputStream strategy1 = new FileInputStream("data.bin");

// 策略实现2：从内存字节数组读取
InputStream strategy2 = new ByteArrayInputStream(bytes);

// 策略实现3：从网络读取
InputStream strategy3 = socket.getInputStream();

// 策略实现4：从 classpath 资源读取
InputStream strategy4 = getClass().getResourceAsStream("/config.properties");
```

```java
// 上下文：依赖抽象，不关心具体 I/O 来源
public class DataParser {
    private final InputStream source;  // 策略引用

    public DataParser(InputStream source) {
        this.source = source;
    }

    public List<Record> parse() throws IOException {
        // 只调用 InputStream 接口，无需关心数据来源是文件、网络还是内存
        DataInputStream dis = new DataInputStream(source);
        // ...
    }
}

// 调用方可以灵活切换策略（数据源）
DataParser fileParser    = new DataParser(new FileInputStream("data.bin"));
DataParser networkParser = new DataParser(socket.getInputStream());
DataParser testParser    = new DataParser(new ByteArrayInputStream(mockData));
```

### 5.3 ObjectStreamField 中的字段序列化策略

```java
// ObjectOutputStream 内部使用策略模式来处理不同类型字段的序列化
// 不同类型（int、long、Object 等）使用不同的写入策略
private void writeObject0(Object obj, boolean unshared) throws IOException {
    // 根据对象类型选择不同序列化策略
    if (obj instanceof String)       writeString((String) obj, unshared);
    else if (obj instanceof Class)   writeClass((Class<?>) obj, unshared);
    else if (obj instanceof Enum)    writeEnum((Enum<?>) obj, desc, unshared);
    else if (obj instanceof Serializable) writeOrdinaryObject(obj, desc, unshared);
    else throw new NotSerializableException(obj.getClass().getName());
}
```

---

## 六、工厂方法模式（Factory Method Pattern）

### 6.1 模式说明

**工厂方法模式**：定义创建对象的接口，由子类决定实例化哪个类。

### 6.2 在 java.io 中的体现：File.toPath() 与流工厂

```java
// Files 工具类：工厂方法创建各种流，屏蔽底层实现细节
public final class Files {

    // 工厂方法：创建 BufferedReader（内部自动组合 FileInputStream + InputStreamReader + BufferedReader）
    public static BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
        CharsetDecoder decoder = cs.newDecoder();
        Reader reader = new InputStreamReader(newInputStream(path), decoder);
        return new BufferedReader(reader);
    }

    // 工厂方法：创建 BufferedWriter
    public static BufferedWriter newBufferedWriter(Path path, Charset cs, OpenOption... options)
            throws IOException {
        CharsetEncoder encoder = cs.newEncoder();
        Writer writer = new OutputStreamWriter(newOutputStream(path, options), encoder);
        return new BufferedWriter(writer);
    }

    // 工厂方法：创建字节输入流
    public static InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return path.getFileSystem().provider().newInputStream(path, options);
    }
}
```

```java
// 使用工厂方法，一行代码替代多层手动包装
BufferedReader reader = Files.newBufferedReader(Paths.get("data.txt"), StandardCharsets.UTF_8);
BufferedWriter writer = Files.newBufferedWriter(Paths.get("out.txt"), StandardCharsets.UTF_8);
```

### 6.3 Charset 中的工厂方法

```java
// Charset 抽象类提供工厂方法
public abstract class Charset {

    // 工厂方法：创建 CharsetDecoder（由子类决定具体实现）
    public abstract CharsetDecoder newDecoder();

    // 工厂方法：创建 CharsetEncoder
    public abstract CharsetEncoder newEncoder();
}

// UTF-8 实现
class UTF_8 extends Unicode {
    @Override
    public CharsetDecoder newDecoder() {
        return new Decoder(this);  // 返回具体的 Decoder 子类
    }
    @Override
    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }
}
```

---

## 七、代理模式（Proxy Pattern）

### 7.1 模式说明

**代理模式**：为目标对象提供一种代理，以控制对该对象的访问。代理类与目标类实现相同的接口。

在 `java.io` 中，`FilterInputStream`/`FilterOutputStream` 在结构上与代理模式高度重合（同时也是装饰器的基础）。

### 7.2 ObjectInputStream / ObjectOutputStream 中的序列化代理

**序列化代理模式（Serialization Proxy Pattern）** 是 Effective Java 推荐的 `java.io` 使用技巧：

```java
// 使用序列化代理模式安全地序列化不可变类
public final class Period implements Serializable {
    private final Date start;
    private final Date end;

    public Period(Date start, Date end) {
        this.start = new Date(start.getTime());
        this.end   = new Date(end.getTime());
        if (this.start.compareTo(this.end) > 0)
            throw new IllegalArgumentException("start after end");
    }

    // 序列化代理：内部静态类
    private static class SerializationProxy implements Serializable {
        private final Date start;
        private final Date end;

        SerializationProxy(Period p) {
            this.start = p.start;
            this.end   = p.end;
        }

        // 反序列化时，通过正规构造器重建对象（确保不变量）
        private Object readResolve() {
            return new Period(start, end);
        }

        private static final long serialVersionUID = 1234567890L;
    }

    // writeReplace：序列化时用代理替换本对象
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    // readObject：防止直接反序列化绕过代理
    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
        throw new InvalidObjectException("Use Serialization Proxy");
    }
}
```

### 7.3 PipedInputStream / PipedOutputStream 的相互代理

```java
// PipedInputStream 与 PipedOutputStream 相互持有引用，形成代理关系
PipedOutputStream out = new PipedOutputStream();
PipedInputStream  in  = new PipedInputStream(out);  // in 代理 out 的写入缓冲

// 写端写入数据（生产者线程）
new Thread(() -> {
    try {
        out.write("Hello from pipe".getBytes());
        out.close();
    } catch (IOException e) { e.printStackTrace(); }
}).start();

// 读端读取数据（消费者线程）
int b;
while ((b = in.read()) != -1) {
    System.out.print((char) b);
}
```

---

## 八、观察者模式（Observer Pattern）

### 8.1 模式说明

**观察者模式**：定义对象间的一对多依赖，当一个对象状态改变时，所有依赖它的对象都会得到通知并自动更新。

### 8.2 在 java.io 中的体现：FileSystem 监听

`java.io` 本身对观察者模式的使用较少，但在其兄弟包 `java.nio.file` 中有完整的 `WatchService` 实现。不过 `java.io.File` 通过回调机制间接使用了类观察者思想：

```java
// FilenameFilter：过滤器（观察者接口）
public interface FilenameFilter {
    boolean accept(File dir, String name);
}

// File.list(FilenameFilter) 是对"文件变化"的过滤回调
File dir = new File("/tmp");
String[] javaFiles = dir.list((d, name) -> name.endsWith(".java"));

// FileFilter 是另一种回调形式
FileFilter sizeFilter = file -> file.length() > 1024 * 1024; // > 1MB
File[] largeFiles = dir.listFiles(sizeFilter);
```

### 8.3 序列化生命周期回调（类观察者）

`ObjectInputStream` / `ObjectOutputStream` 在序列化/反序列化过程中，会回调对象上的特定方法，这是观察者思想的体现：

```java
public class MyData implements Serializable {
    private transient String cache;  // 不参与序列化

    // 序列化前的回调（由 ObjectOutputStream "通知"）
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();  // 写入非 transient 字段
        out.writeObject(computeCache()); // 额外写入派生数据
    }

    // 反序列化后的回调（由 ObjectInputStream "通知"）
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();  // 读取非 transient 字段
        this.cache = (String) in.readObject();  // 恢复 transient 字段
    }

    // 反序列化完成后的回调（所有字段就绪后触发）
    private Object readResolve() throws ObjectStreamException {
        // 可以在此返回单例或进行对象替换
        return this;
    }
}
```

---

## 九、责任链模式（Chain of Responsibility）

### 9.1 在 java.io 中的体现

虽然责任链模式在 `java.io` 中不如在 `javax.servlet.Filter` 中典型，但多层装饰器的调用链本质上就是一条责任链：**每个装饰器处理自己负责的部分（如缓冲、编码转换），然后将调用传递给下一层**。

```
调用 read() 时的责任链传递：

BufferedReader.readLine()
    ↓ 调用
InputStreamReader.read(char[], 0, n)
    ↓ 委托给 StreamDecoder，StreamDecoder 调用
BufferedInputStream.read(byte[], 0, n)
    ↓ 缓冲区命中则直接返回；未命中则调用
FileInputStream.read(byte[], 0, n)
    ↓ native 系统调用
操作系统内核 read()
```

```java
// 构造一条"责任链"：每个节点处理不同职责
BufferedReader chain = new BufferedReader(          // 职责：行读取、字符缓冲
    new InputStreamReader(                          // 职责：字节→字符解码
        new BufferedInputStream(                    // 职责：字节缓冲，减少系统调用
            new FileInputStream("data.txt")         // 职责：实际磁盘 I/O
        ),
        StandardCharsets.UTF_8
    )
);
```

---

## 十、各设计模式汇总对比

| 设计模式 | 代表类 | 解决的问题 | GoF 分类 |
|---------|--------|-----------|---------|
| **装饰器** | `FilterInputStream`、`BufferedInputStream`、`DataInputStream`、`PrintStream`、`BufferedReader` | 动态叠加功能，避免继承爆炸 | 结构型 |
| **适配器** | `InputStreamReader`、`OutputStreamWriter` | 字节流与字符流接口不兼容 | 结构型 |
| **模板方法** | `InputStream.read(byte[],int,int)`、`Writer.write(int)` | 定义算法骨架，子类实现细节 | 行为型 |
| **策略** | `InputStream` 多态（`FileInputStream`/`ByteArrayInputStream`/`SocketInputStream`） | 运行时切换 I/O 数据源 | 行为型 |
| **工厂方法** | `Files.newBufferedReader()`、`Charset.newDecoder()` | 封装复杂对象创建过程 | 创建型 |
| **代理** | `SerializationProxy`、`FilterInputStream` | 控制对象访问、保护不变量 | 结构型 |
| **观察者** | `FilenameFilter`、`writeObject()`/`readObject()` 回调 | 事件驱动、生命周期通知 | 行为型 |
| **责任链** | 多层装饰器调用链 | 解耦处理步骤，职责清晰 | 行为型 |

---

## 十一、模式协同：一个完整示例

下面这个例子综合运用了装饰器、适配器、模板方法、策略、工厂方法五种模式：

```java
public class CsvProcessor {

    // 策略接口：数据源可以是任意 InputStream
    public static List<String[]> process(InputStream source, String charset)
            throws IOException {

        // 适配器模式：字节流 → 字符流
        // 工厂方法：Files.newBufferedReader 内部也是这个组合
        InputStreamReader isr = new InputStreamReader(source, charset);

        // 装饰器模式：为字符流增加缓冲能力
        BufferedReader br = new BufferedReader(isr);

        // 模板方法：readLine() 在 BufferedReader 中定义算法骨架
        List<String[]> rows = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            rows.add(line.split(","));
        }
        return rows;
    }

    public static void main(String[] args) throws IOException {
        // 策略1：从文件读取
        process(new FileInputStream("data.csv"), "UTF-8");

        // 策略2：从内存读取（单元测试友好）
        String mockCsv = "a,b,c\n1,2,3";
        process(new ByteArrayInputStream(mockCsv.getBytes("UTF-8")), "UTF-8");

        // 策略3：从网络读取
        // process(socket.getInputStream(), "UTF-8");
    }
}
```

**代码中的模式分布**：
- `new InputStreamReader(source, charset)` → **适配器**：`InputStream` → `Reader`
- `new BufferedReader(isr)` → **装饰器**：为 `Reader` 叠加缓冲功能
- `br.readLine()` → **模板方法**：`BufferedReader` 定义行读取骨架
- `InputStream source` 参数 → **策略**：调用方自由切换数据来源
- `Files.newBufferedReader()` → **工厂方法**：封装上述组合

---

## 十二、设计启示

### 12.1 面向接口编程

`java.io` 的所有设计都建立在 `InputStream`/`OutputStream`/`Reader`/`Writer` 四个抽象接口之上，这体现了**依赖倒置原则（DIP）**：

```java
// ✅ 正确：依赖抽象
public void writeData(OutputStream out) throws IOException { ... }

// ❌ 错误：依赖具体实现（调用方无法切换到网络流、内存流等）
public void writeData(FileOutputStream out) throws IOException { ... }
```

### 12.2 组合优于继承

`java.io` 的装饰器体系是"组合优于继承"原则（**OCRP**）的教科书级实现。`FilterInputStream` 通过持有 `InputStream` 引用而非继承 `FileInputStream`，使功能可以自由组合。

### 12.3 开闭原则（OCP）

对扩展开放，对修改关闭：

- 新增数据源（如 `ZipInputStream`）：**扩展**，不修改 `BufferedInputStream`
- 新增功能（如加密流 `CipherInputStream`）：**扩展**，不修改已有装饰器
- 底层实现更换（`FileInputStream` → `MappedByteBuffer`）：调用方无感知

### 12.4 单一职责原则（SRP）

每个类只做一件事：
- `FileInputStream`：负责从文件系统读字节
- `BufferedInputStream`：负责字节缓冲
- `DataInputStream`：负责基本类型解析
- `InputStreamReader`：负责字节→字符编码转换

---

> **延伸阅读**：
> - 《Effective Java》第 3 版 第 16 条（复合优先于继承）、第 85-90 条（序列化）
> - 《设计模式：可复用面向对象软件的基础》（GoF）- 装饰器、适配器章节
> - OpenJDK 源码：`java.io.FilterInputStream`、`java.io.InputStreamReader`
