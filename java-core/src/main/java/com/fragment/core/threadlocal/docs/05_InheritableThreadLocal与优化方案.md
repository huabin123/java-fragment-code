# 第五章：InheritableThreadLocal与优化方案

## 引言

本章将深入分析InheritableThreadLocal的实现原理，探讨父子线程值传递机制，以及Netty的FastThreadLocal等优化方案。

---

## 1. InheritableThreadLocal原理

### 1.1 问题1：InheritableThreadLocal是什么？

**定义**：

```java
public class InheritableThreadLocal<T> extends ThreadLocal<T> {
    /**
     * 子线程继承父线程的值时调用
     * 可以重写此方法自定义继承逻辑
     */
    protected T childValue(T parentValue) {
        return parentValue;
    }

    /**
     * 获取ThreadLocalMap（重写父类方法）
     */
    ThreadLocalMap getMap(Thread t) {
        return t.inheritableThreadLocals;
    }

    /**
     * 创建ThreadLocalMap（重写父类方法）
     */
    void createMap(Thread t, T firstValue) {
        t.inheritableThreadLocals = new ThreadLocalMap(this, firstValue);
    }
}
```

**核心区别**：


| 维度         | ThreadLocal         | InheritableThreadLocal         |
| ------------ | ------------------- | ------------------------------ |
| **存储位置** | Thread.threadLocals | Thread.inheritableThreadLocals |
| **父子线程** | 不传递              | 自动传递                       |
| **使用场景** | 线程隔离            | 父子线程共享                   |

---

### 1.2 问题2：父子线程值传递的原理是什么？

**Thread类的相关字段**：

```java
public class Thread implements Runnable {
    // ThreadLocal的存储
    ThreadLocal.ThreadLocalMap threadLocals = null;

    // InheritableThreadLocal的存储
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;
}
```

**Thread构造函数中的继承逻辑**：

```java
public class Thread implements Runnable {
    private void init(ThreadGroup g, Runnable target, String name,
                      long stackSize, AccessControlContext acc,
                      boolean inheritThreadLocals) {
        // ...

        // 获取父线程
        Thread parent = currentThread();

        // 如果父线程有inheritableThreadLocals，则继承
        if (inheritThreadLocals && parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals =
                ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);

        // ...
    }
}
```

**createInheritedMap()源码**：

```java
static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
    return new ThreadLocalMap(parentMap);
}

// ThreadLocalMap的拷贝构造函数
private ThreadLocalMap(ThreadLocalMap parentMap) {
    Entry[] parentTable = parentMap.table;
    int len = parentTable.length;
    setThreshold(len);
    table = new Entry[len];

    // 遍历父线程的ThreadLocalMap
    for (int j = 0; j < len; j++) {
        Entry e = parentTable[j];
        if (e != null) {
            @SuppressWarnings("unchecked")
            ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
            if (key != null) {
                // 调用childValue()获取子线程的值
                Object value = key.childValue(e.value);
                Entry c = new Entry(key, value);
                int h = key.threadLocalHashCode & (len - 1);
                while (table[h] != null)
                    h = nextIndex(h, len);
                table[h] = c;
                size++;
            }
        }
    }
}
```

**继承流程图**：

```
父线程创建子线程
    ↓
调用Thread构造函数
    ↓
检查parent.inheritableThreadLocals
    ↓
不为null
    ↓
调用createInheritedMap()
    ↓
创建新的ThreadLocalMap
    ↓
遍历父线程的Entry
    ↓
调用childValue()获取值
    ↓
复制到子线程的ThreadLocalMap
    ↓
子线程启动
```

---

### 1.3 问题3：InheritableThreadLocal的使用示例

**基本使用**：

```java
public class InheritableThreadLocalDemo {
    private static InheritableThreadLocal<String> holder = new InheritableThreadLocal<>();

    public static void main(String[] args) {
        // 父线程设置值
        holder.set("parent value");
        System.out.println("父线程: " + holder.get());

        // 创建子线程
        new Thread(() -> {
            // 子线程可以获取父线程的值
            System.out.println("子线程: " + holder.get()); // "parent value"

            // 子线程修改值
            holder.set("child value");
            System.out.println("子线程修改后: " + holder.get()); // "child value"
        }).start();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 父线程的值不受影响
        System.out.println("父线程: " + holder.get()); // "parent value"
    }
}
```

**输出**：

```
父线程: parent value
子线程: parent value
子线程修改后: child value
父线程: parent value
```

---

**自定义childValue()**：

```java
public class CustomInheritableThreadLocal extends InheritableThreadLocal<List<String>> {
    @Override
    protected List<String> childValue(List<String> parentValue) {
        // 深拷贝，避免父子线程共享同一个List
        return new ArrayList<>(parentValue);
    }
}

// 使用示例
public class CustomDemo {
    private static CustomInheritableThreadLocal holder = new CustomInheritableThreadLocal();

    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("item1");
        holder.set(list);

        new Thread(() -> {
            List<String> childList = holder.get();
            childList.add("item2"); // 不会影响父线程的list
            System.out.println("子线程: " + childList);
        }).start();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("父线程: " + holder.get()); // 只有item1
    }
}
```

---

### 1.4 问题4：InheritableThreadLocal的局限性是什么？

**局限性1：线程池场景下失效**

```java
public class InheritableThreadLocalProblem {
    private static InheritableThreadLocal<String> holder = new InheritableThreadLocal<>();
    private static ExecutorService executor = Executors.newFixedThreadPool(1);

    public static void main(String[] args) throws InterruptedException {
        // 第1次提交任务
        holder.set("value1");
        executor.execute(() -> {
            System.out.println("任务1: " + holder.get()); // "value1"
        });

        Thread.sleep(100);

        // 第2次提交任务（复用同一个线程）
        holder.set("value2");
        executor.execute(() -> {
            System.out.println("任务2: " + holder.get()); // "value1"（错误！）
        });
    }
}
```

**问题分析**：

```
线程池的线程是预先创建的：
1. 线程池创建Worker线程时，继承了主线程的值
2. 后续任务复用Worker线程，不会重新继承
3. 导致获取到的是旧值

解决方案：
- 使用TransmittableThreadLocal（阿里开源）
```

---

**局限性2：内存占用增加**

```java
// 每个子线程都会拷贝父线程的所有InheritableThreadLocal
// 如果父线程有很多InheritableThreadLocal，内存占用会增加

public class MemoryIssue {
    private static InheritableThreadLocal<byte[]> holder1 = new InheritableThreadLocal<>();
    private static InheritableThreadLocal<byte[]> holder2 = new InheritableThreadLocal<>();
    private static InheritableThreadLocal<byte[]> holder3 = new InheritableThreadLocal<>();

    public static void main(String[] args) {
        // 父线程设置大对象
        holder1.set(new byte[1024 * 1024]); // 1MB
        holder2.set(new byte[1024 * 1024]); // 1MB
        holder3.set(new byte[1024 * 1024]); // 1MB

        // 创建100个子线程
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                // 每个子线程都会拷贝3MB数据
                // 总内存占用：100 * 3MB = 300MB
            }).start();
        }
    }
}
```

---

## 2. TransmittableThreadLocal

### 2.1 问题5：TransmittableThreadLocal是什么？

**TransmittableThreadLocal（TTL）**是阿里开源的ThreadLocal增强库，解决了InheritableThreadLocal在线程池场景下的问题。

**Maven依赖**：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
    <version>2.14.2</version>
</dependency>
```

**基本使用**：

```java
import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.threadpool.TtlExecutors;

public class TTLDemo {
    private static TransmittableThreadLocal<String> context = new TransmittableThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        // 使用TtlExecutors包装线程池
        ExecutorService executor = TtlExecutors.getTtlExecutorService(
            Executors.newFixedThreadPool(1)
        );

        // 第1次提交任务
        context.set("value1");
        executor.execute(() -> {
            System.out.println("任务1: " + context.get()); // "value1"
        });

        Thread.sleep(100);

        // 第2次提交任务
        context.set("value2");
        executor.execute(() -> {
            System.out.println("任务2: " + context.get()); // "value2"（正确！）
        });

        executor.shutdown();
    }
}
```

---

### 2.2 问题6：TransmittableThreadLocal的实现原理是什么？

**核心思想**：

```
1. 在任务提交时，捕获当前线程的所有TTL值
2. 在任务执行前，将捕获的值设置到Worker线程
3. 在任务执行后，恢复Worker线程的原始值
```

**实现原理**：

```java
// TtlRunnable包装原始Runnable
public final class TtlRunnable implements Runnable {
    private final AtomicReference<Object> capturedRef;
    private final Runnable runnable;

    private TtlRunnable(Runnable runnable) {
        // 捕获当前线程的所有TTL值
        this.capturedRef = new AtomicReference<>(capture());
        this.runnable = runnable;
    }

    @Override
    public void run() {
        Object captured = capturedRef.get();
        if (captured == null || releaseTtlValueReferenceAfterRun && !capturedRef.compareAndSet(captured, null)) {
            throw new IllegalStateException("TTL value reference is released after run!");
        }

        // 备份Worker线程的原始值
        Object backup = replay(captured);
        try {
            // 执行原始任务
            runnable.run();
        } finally {
            // 恢复Worker线程的原始值
            restore(backup);
        }
    }
}
```

**流程图**：

```
主线程提交任务
    ↓
TtlRunnable.capture()
    ↓
捕获主线程的所有TTL值
    ↓
任务提交到线程池
    ↓
Worker线程执行任务
    ↓
TtlRunnable.replay()
    ↓
将捕获的值设置到Worker线程
    ↓
执行原始任务
    ↓
TtlRunnable.restore()
    ↓
恢复Worker线程的原始值
```

---

## 3. Netty的FastThreadLocal

### 3.1 问题7：FastThreadLocal是什么？

**FastThreadLocal**是Netty优化的ThreadLocal实现，性能比JDK的ThreadLocal更好。

**性能对比**：

```
场景：100万次get/set操作

JDK ThreadLocal：
- get: 150ms
- set: 180ms

Netty FastThreadLocal：
- get: 80ms
- set: 100ms

性能提升：约2倍
```

---

### 3.2 问题8：FastThreadLocal的优化原理是什么？

**优化1：使用数组代替HashMap**

```java
// JDK ThreadLocal使用ThreadLocalMap（类似HashMap）
// 需要计算hash、处理冲突

// Netty FastThreadLocal使用数组
public class FastThreadLocal<V> {
    private final int index; // 数组索引

    public FastThreadLocal() {
        // 分配唯一的索引
        index = InternalThreadLocalMap.nextVariableIndex();
    }

    public final V get() {
        // 直接通过索引访问，O(1)时间复杂度
        return get(InternalThreadLocalMap.get());
    }
}
```

**优化2：使用FastThreadLocalThread**

```java
// 必须使用FastThreadLocalThread
public class FastThreadLocalThread extends Thread {
    private InternalThreadLocalMap threadLocalMap;

    public final InternalThreadLocalMap threadLocalMap() {
        return threadLocalMap;
    }

    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
        this.threadLocalMap = threadLocalMap;
    }
}
```

**优化3：InternalThreadLocalMap的设计**

```java
public final class InternalThreadLocalMap {
    // 使用数组存储，而非HashMap
    private Object[] indexedVariables;

    // 初始容量
    private static final int INITIAL_CAPACITY = 32;

    public Object indexedVariable(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length ? lookup[index] : UNSET;
    }

    public boolean setIndexedVariable(int index, Object value) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            lookup[index] = value;
            return true;
        } else {
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }
}
```

**性能优势**：

```
JDK ThreadLocal：
- 使用ThreadLocalMap（开放寻址法）
- 需要计算hash：hashCode & (len - 1)
- 需要处理冲突：线性探测
- 时间复杂度：O(1) ~ O(n)

Netty FastThreadLocal：
- 使用数组
- 直接通过索引访问：indexedVariables[index]
- 无需计算hash，无需处理冲突
- 时间复杂度：O(1)
```

---

### 3.3 问题9：FastThreadLocal的使用示例

**基本使用**：

```java
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;

public class FastThreadLocalDemo {
    private static final FastThreadLocal<String> context = new FastThreadLocal<>();

    public static void main(String[] args) {
        // 必须使用FastThreadLocalThread
        Thread thread = new FastThreadLocalThread(() -> {
            context.set("value");
            System.out.println(context.get());

            // 使用完后remove
            context.remove();
        });

        thread.start();
    }
}
```

**在Netty中使用**：

```java
// Netty的EventLoop默认使用FastThreadLocalThread
public class NettyServer {
    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     // EventLoop线程是FastThreadLocalThread
                     // 可以使用FastThreadLocal
                 }
             });

            ChannelFuture f = b.bind(8080).sync();
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
```

---

## 4. ThreadLocal的替代方案

### 4.1 问题10：ThreadLocal的替代方案有哪些？

**方案1：使用Request Scope（Spring）**

```java
// 使用Spring的RequestScope
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserContext {
    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

// 使用
@Service
public class UserService {
    @Autowired
    private UserContext userContext;

    public void process() {
        User user = userContext.getUser();
        // 使用user
    }
}
```

**优势**：

- Spring自动管理生命周期
- 无需手动remove
- 类型安全

---

**方案2：使用MDC（日志上下文）**

**MDC的本质**：MDC是基于ThreadLocal的高级封装，专门为日志上下文设计。它使用`ThreadLocal<Map<String, String>>`存储数据，提供了更便捷的键值对API，并与日志框架深度集成。

```java
import org.slf4j.MDC;

public class MDCDemo {
    public void handleRequest(String traceId) {
        try {
            // 设置MDC
            MDC.put("traceId", traceId);

            // 日志会自动包含traceId
            log.info("处理请求"); // [traceId=123] 处理请求

        } finally {
            // 清理MDC
            MDC.clear();
        }
    }
}
```

**优势**：

- 专门用于日志上下文
- 自动传递到日志框架
- 支持异步日志

#### MDC的实现原理深度剖析

**什么是MDC？**

MDC（Mapped Diagnostic Context，映射诊断上下文）是SLF4J提供的一种日志上下文管理机制，用于在多线程环境中区分不同请求的日志。

**MDC的核心实现**：

```java
// SLF4J的MDC类（org.slf4j.MDC）
public class MDC {
    // MDC的底层实现由具体的日志框架提供
    // 这里以Logback为例
    static MDCAdapter mdcAdapter;

    /**
     * 设置MDC值
     */
    public static void put(String key, String val) {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null");
        }
        mdcAdapter.put(key, val);
    }

    /**
     * 获取MDC值
     */
    public static String get(String key) {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null");
        }
        return mdcAdapter.get(key);
    }

    /**
     * 移除MDC值
     */
    public static void remove(String key) {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null");
        }
        mdcAdapter.remove(key);
    }

    /**
     * 清空MDC
     */
    public static void clear() {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null");
        }
        mdcAdapter.clear();
    }

    /**
     * 获取MDC的拷贝（用于线程传递）
     */
    public static Map<String, String> getCopyOfContextMap() {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null");
        }
        return mdcAdapter.getCopyOfContextMap();
    }

    /**
     * 设置MDC的上下文（用于线程传递）
     */
    public static void setContextMap(Map<String, String> contextMap) {
        if (mdcAdapter == null) {
            throw new IllegalStateException("MDCAdapter cannot be null");
        }
        mdcAdapter.setContextMap(contextMap);
    }
}
```

**Logback的MDCAdapter实现**：

```java
// Logback的LogbackMDCAdapter实现
public class LogbackMDCAdapter implements MDCAdapter {

    // ⭐ 核心：使用ThreadLocal存储MDC数据
    final ThreadLocal<Map<String, String>> copyOnThreadLocal = new ThreadLocal<>();

    // 使用InheritableThreadLocal支持父子线程传递
    private static final int WRITE_OPERATION = 1;
    private static final int MAP_COPY_OPERATION = 2;

    final ThreadLocal<Integer> lastOperation = new ThreadLocal<>();

    /**
     * 设置MDC值
     */
    public void put(String key, String val) throws IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }

        // 获取当前线程的Map
        Map<String, String> oldMap = copyOnThreadLocal.get();
        Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);

        // 如果是第一次写入或上次操作是拷贝，则创建新Map
        if (wasLastOpReadOrNull(lastOp) || oldMap == null) {
            Map<String, String> newMap = duplicateAndInsertNewMap(oldMap);
            newMap.put(key, val);
        } else {
            // 否则直接在现有Map上修改
            oldMap.put(key, val);
        }
    }

    /**
     * 获取MDC值
     */
    public String get(String key) {
        Map<String, String> map = copyOnThreadLocal.get();
        if ((map != null) && (key != null)) {
            return map.get(key);
        } else {
            return null;
        }
    }

    /**
     * 移除MDC值
     */
    public void remove(String key) {
        if (key == null) {
            return;
        }
        Map<String, String> oldMap = copyOnThreadLocal.get();
        if (oldMap == null)
            return;

        Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);

        if (wasLastOpReadOrNull(lastOp)) {
            Map<String, String> newMap = duplicateAndInsertNewMap(oldMap);
            newMap.remove(key);
        } else {
            oldMap.remove(key);
        }
    }

    /**
     * 清空MDC
     */
    public void clear() {
        lastOperation.set(WRITE_OPERATION);
        copyOnThreadLocal.remove();
    }

    /**
     * 获取MDC的拷贝
     */
    public Map<String, String> getCopyOfContextMap() {
        Map<String, String> hashMap = copyOnThreadLocal.get();
        if (hashMap == null) {
            return null;
        } else {
            return new HashMap<>(hashMap);
        }
    }

    /**
     * 设置MDC的上下文
     */
    public void setContextMap(Map<String, String> contextMap) {
        lastOperation.set(WRITE_OPERATION);

        Map<String, String> newMap = Collections.synchronizedMap(new HashMap<>());
        newMap.putAll(contextMap);

        copyOnThreadLocal.set(newMap);
    }

    /**
     * 复制并插入新Map
     */
    private Map<String, String> duplicateAndInsertNewMap(Map<String, String> oldMap) {
        Map<String, String> newMap = Collections.synchronizedMap(new HashMap<>());
        if (oldMap != null) {
            synchronized (oldMap) {
                newMap.putAll(oldMap);
            }
        }

        copyOnThreadLocal.set(newMap);
        return newMap;
    }

    private boolean wasLastOpReadOrNull(Integer lastOp) {
        return lastOp == null || lastOp == MAP_COPY_OPERATION;
    }

    private Integer getAndSetLastOperation(int op) {
        Integer lastOp = lastOperation.get();
        lastOperation.set(op);
        return lastOp;
    }
}
```

**MDC与ThreadLocal的异同**：


| 对比维度     | ThreadLocal                           | MDC                                 |
| ------------ | ------------------------------------- | ----------------------------------- |
| **底层实现** | JDK原生实现                           | 基于ThreadLocal封装                 |
| **存储结构** | Thread.threadLocals（ThreadLocalMap） | ThreadLocal<Map<String, String>>    |
| **数据类型** | 泛型T（任意类型）                     | Map<String, String>（键值对）       |
| **使用场景** | 通用的线程本地变量                    | 专门用于日志上下文                  |
| **API设计**  | set(T)/get()/remove()                 | put(key, val)/get(key)/remove(key)  |
| **多值存储** | 需要多个ThreadLocal实例               | 一个MDC存储多个键值对               |
| **日志集成** | 需要手动集成                          | 日志框架原生支持                    |
| **线程传递** | 不支持（需要InheritableThreadLocal）  | 需要手动传递（getCopyOfContextMap） |
| **内存管理** | 需要手动remove                        | 需要手动clear                       |

**MDC的数据结构对比**：

```java
// ThreadLocal的使用方式
ThreadLocal<String> userId = new ThreadLocal<>();
ThreadLocal<String> traceId = new ThreadLocal<>();
ThreadLocal<String> requestId = new ThreadLocal<>();

userId.set("user123");
traceId.set("trace456");
requestId.set("req789");

// 底层存储结构：
Thread.currentThread().threadLocals = {
    ThreadLocalMap: {
        Entry[0]: key=userId对象, value="user123"
        Entry[1]: key=traceId对象, value="trace456"
        Entry[2]: key=requestId对象, value="req789"
    }
}

// MDC的使用方式
MDC.put("userId", "user123");
MDC.put("traceId", "trace456");
MDC.put("requestId", "req789");

// 底层存储结构：
Thread.currentThread().threadLocals = {
    ThreadLocalMap: {
        Entry[0]: key=MDC的ThreadLocal对象, value=Map {
            "userId" -> "user123",
            "traceId" -> "trace456",
            "requestId" -> "req789"
        }
    }
}
```

**核心区别图示**：

```
┌─────────────────────────────────────────────────────────────┐
│  ThreadLocal的存储方式                                        │
├─────────────────────────────────────────────────────────────┤
│  Thread.threadLocals (ThreadLocalMap)                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Entry[0]: ThreadLocal1 → "value1"                    │  │
│  │  Entry[1]: ThreadLocal2 → "value2"                    │  │
│  │  Entry[2]: ThreadLocal3 → "value3"                    │  │
│  └───────────────────────────────────────────────────────┘  │
│  特点：每个变量需要一个ThreadLocal实例                        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  MDC的存储方式                                                │
├─────────────────────────────────────────────────────────────┤
│  Thread.threadLocals (ThreadLocalMap)                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Entry[0]: MDC的ThreadLocal → Map {                   │  │
│  │      "key1" → "value1",                               │  │
│  │      "key2" → "value2",                               │  │
│  │      "key3" → "value3"                                │  │
│  │  }                                                    │  │
│  └───────────────────────────────────────────────────────┘  │
│  特点：只需一个ThreadLocal，存储Map                           │
└─────────────────────────────────────────────────────────────┘
```

**MDC在日志框架中的应用**：

```java
// Logback配置文件（logback.xml）
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <!-- 使用%X{key}获取MDC的值 -->
            <pattern>%d{HH:mm:ss.SSS} [%thread] [%X{traceId}] [%X{userId}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>

// Java代码
public class MDCLogDemo {
    private static final Logger log = LoggerFactory.getLogger(MDCLogDemo.class);

    public void handleRequest(String userId, String traceId) {
        try {
            // 设置MDC
            MDC.put("userId", userId);
            MDC.put("traceId", traceId);

            // 日志会自动包含MDC的值
            log.info("开始处理请求");
            processOrder();
            log.info("请求处理完成");

        } finally {
            // 清理MDC
            MDC.clear();
        }
    }

    private void processOrder() {
        // 这里的日志也会自动包含MDC的值
        log.info("处理订单");
    }
}

// 输出：
// 10:30:15.123 [http-nio-8080-exec-1] [trace-123] [user-456] INFO  c.e.MDCLogDemo - 开始处理请求
// 10:30:15.456 [http-nio-8080-exec-1] [trace-123] [user-456] INFO  c.e.MDCLogDemo - 处理订单
// 10:30:15.789 [http-nio-8080-exec-1] [trace-123] [user-456] INFO  c.e.MDCLogDemo - 请求处理完成
```

**MDC在异步场景下的使用**：

```java
// 问题：异步线程无法获取父线程的MDC
public class MDCAsyncProblem {
    private static final Logger log = LoggerFactory.getLogger(MDCAsyncProblem.class);
    private static ExecutorService executor = Executors.newFixedThreadPool(10);

    public void handleRequest(String traceId) {
        MDC.put("traceId", traceId);
        log.info("主线程处理"); // 有traceId

        executor.execute(() -> {
            log.info("异步线程处理"); // 没有traceId！
        });
    }
}

// 解决方案1：手动传递MDC
public class MDCAsyncSolution1 {
    private static final Logger log = LoggerFactory.getLogger(MDCAsyncSolution1.class);
    private static ExecutorService executor = Executors.newFixedThreadPool(10);

    public void handleRequest(String traceId) {
        MDC.put("traceId", traceId);
        log.info("主线程处理");

        // 获取当前线程的MDC
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        executor.execute(() -> {
            try {
                // 设置到异步线程
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                log.info("异步线程处理"); // 有traceId！
            } finally {
                MDC.clear();
            }
        });
    }
}

// 解决方案2：封装Runnable
public class MDCRunnable implements Runnable {
    private final Runnable runnable;
    private final Map<String, String> contextMap;

    public MDCRunnable(Runnable runnable) {
        this.runnable = runnable;
        // 捕获当前线程的MDC
        this.contextMap = MDC.getCopyOfContextMap();
    }

    @Override
    public void run() {
        // 备份当前线程的MDC
        Map<String, String> backup = MDC.getCopyOfContextMap();
        try {
            // 设置捕获的MDC
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            // 执行原始任务
            runnable.run();
        } finally {
            // 恢复原始MDC
            if (backup != null) {
                MDC.setContextMap(backup);
            } else {
                MDC.clear();
            }
        }
    }
}

// 使用MDCRunnable
public class MDCAsyncSolution2 {
    private static final Logger log = LoggerFactory.getLogger(MDCAsyncSolution2.class);
    private static ExecutorService executor = Executors.newFixedThreadPool(10);

    public void handleRequest(String traceId) {
        MDC.put("traceId", traceId);
        log.info("主线程处理");

        // 使用MDCRunnable包装
        executor.execute(new MDCRunnable(() -> {
            log.info("异步线程处理"); // 有traceId！
        }));
    }
}
```

**MDC的实现原理总结**：

```java
// MDC的核心实现原理
1. 底层使用ThreadLocal存储
   - ThreadLocal<Map<String, String>> copyOnThreadLocal
   - 每个线程有自己的Map

2. 提供键值对API
   - put(key, value)：设置键值对
   - get(key)：获取值
   - remove(key)：移除键值对
   - clear()：清空所有键值对

3. 支持上下文拷贝
   - getCopyOfContextMap()：获取当前线程的MDC拷贝
   - setContextMap(map)：设置MDC上下文
   - 用于父子线程或异步线程传递

4. 与日志框架集成
   - 日志框架在输出日志时，自动从MDC获取值
   - 通过%X{key}占位符在日志模式中引用MDC的值

5. 内存管理
   - 需要手动调用clear()清理
   - 通常在finally块中清理
   - 避免内存泄漏（特别是线程池场景）
```

**MDC vs ThreadLocal 使用场景对比**：

```java
// 场景1：日志追踪 → 使用MDC
public class LogTraceExample {
    public void handleRequest(String traceId, String userId) {
        try {
            MDC.put("traceId", traceId);
            MDC.put("userId", userId);

            log.info("处理请求");
            // 所有日志自动包含traceId和userId

        } finally {
            MDC.clear();
        }
    }
}

// 场景2：用户上下文 → 使用ThreadLocal
public class UserContextExample {
    private static ThreadLocal<User> userHolder = new ThreadLocal<>();

    public void handleRequest(User user) {
        try {
            userHolder.set(user);

            // 业务逻辑中可以获取user对象
            processOrder();

        } finally {
            userHolder.remove();
        }
    }

    private void processOrder() {
        User user = userHolder.get();
        // 使用user对象
    }
}

// 场景3：多个上下文变量 → MDC更方便
// 使用ThreadLocal（需要多个实例）
private static ThreadLocal<String> traceId = new ThreadLocal<>();
private static ThreadLocal<String> userId = new ThreadLocal<>();
private static ThreadLocal<String> requestId = new ThreadLocal<>();

// 使用MDC（只需一个实例）
MDC.put("traceId", "...");
MDC.put("userId", "...");
MDC.put("requestId", "...");
```

**MDC的优势与劣势**：

```
优势：
✓ 专门为日志设计，API简洁
✓ 一个实例存储多个键值对
✓ 与日志框架无缝集成
✓ 支持上下文拷贝和传递
✓ 广泛应用于分布式追踪

劣势：
✗ 只能存储String类型
✗ 底层仍是ThreadLocal，有内存泄漏风险
✗ 异步场景需要手动传递
✗ 性能略低于直接使用ThreadLocal（多一层Map）

ThreadLocal的优势：
✓ 可以存储任意类型
✓ 性能更好（直接存储）
✓ 更灵活

ThreadLocal的劣势：
✗ 每个变量需要一个实例
✗ 不与日志框架集成
✗ API相对简单
```

**最佳实践建议**：

```java
// 1. 日志追踪场景：使用MDC
@Component
public class RequestFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        try {
            String traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
            MDC.put("requestUri", ((HttpServletRequest) request).getRequestURI());

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}

// 2. 业务上下文场景：使用ThreadLocal
@Component
public class UserContextHolder {
    private static ThreadLocal<User> holder = new ThreadLocal<>();

    public static void setUser(User user) {
        holder.set(user);
    }

    public static User getUser() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}

// 3. 混合使用：日志用MDC，业务用ThreadLocal
public class HybridExample {
    private static ThreadLocal<User> userHolder = new ThreadLocal<>();

    public void handleRequest(User user, String traceId) {
        try {
            // MDC用于日志
            MDC.put("traceId", traceId);
            MDC.put("userId", user.getId());

            // ThreadLocal用于业务
            userHolder.set(user);

            log.info("处理请求");
            processOrder();

        } finally {
            MDC.clear();
            userHolder.remove();
        }
    }
}
```

---

**方案3：使用Context对象传递**

```java
// 显式传递Context对象
public class ContextDemo {
    public static class Context {
        private String userId;
        private String traceId;
        // getters and setters
    }

    public void handleRequest(Context context) {
        processOrder(context);
    }

    private void processOrder(Context context) {
        validateOrder(context);
        saveOrder(context);
    }

    private void validateOrder(Context context) {
        // 使用context
    }

    private void saveOrder(Context context) {
        // 使用context
    }
}
```

**优势**：

- 显式传递，易于理解
- 无内存泄漏风险
- 易于测试

**劣势**：

- 需要层层传递参数
- 代码冗长

---

## 5. 核心问题总结

### Q1: InheritableThreadLocal的原理是什么？

**A**: 在Thread构造函数中，拷贝父线程的inheritableThreadLocals到子线程。

### Q2: InheritableThreadLocal的局限性是什么？

**A**:

1. 线程池场景下失效（线程复用）
2. 内存占用增加（每个子线程都拷贝）

### Q3: TransmittableThreadLocal如何解决线程池问题？

**A**: 在任务提交时捕获值，执行前设置，执行后恢复。

### Q4: FastThreadLocal为什么更快？

**A**: 使用数组代替HashMap，直接通过索引访问，无需计算hash和处理冲突。

### Q5: FastThreadLocal的使用限制是什么？

**A**: 必须使用FastThreadLocalThread，不能用普通Thread。

### Q6: ThreadLocal的替代方案有哪些？

**A**:

1. Spring的Request Scope
2. MDC（日志上下文）
3. 显式传递Context对象

### Q7: 如何选择合适的方案？

**A**:

- 简单场景：ThreadLocal
- 父子线程传递：InheritableThreadLocal
- 线程池场景：TransmittableThreadLocal
- 高性能场景：FastThreadLocal
- Web应用：Request Scope
- 日志场景：MDC

---

## 总结

通过本系列的学习，我们深入理解了：

1. **ThreadLocal的必要性**：解决线程隔离问题
2. **核心原理**：数据存储在Thread对象中，使用ThreadLocalMap
3. **ThreadLocalMap**：斐波那契散列、开放寻址法、弱引用Entry
4. **内存泄漏**：使用后不remove导致，特别是线程池场景
5. **最佳实践**：try-finally保证remove、避免大对象、监控检测
6. **InheritableThreadLocal**：父子线程值传递
7. **优化方案**：TransmittableThreadLocal、FastThreadLocal

**核心收获**：

- ✅ 理解ThreadLocal的设计思想和实现原理
- ✅ 掌握ThreadLocalMap的精妙设计
- ✅ 知道如何避免内存泄漏
- ✅ 学会正确使用ThreadLocal
- ✅ 了解各种优化方案和替代方案

**继续学习**：

- 深入学习JUC并发包
- 研究Spring的Request Scope实现
- 学习分布式追踪系统（如Zipkin、SkyWalking）
- 了解Netty的线程模型

🚀 ThreadLocal系列完结！
