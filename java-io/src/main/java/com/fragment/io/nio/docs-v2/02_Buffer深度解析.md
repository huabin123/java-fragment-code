# 第二章：Buffer 深度解析

## 2.1 Buffer 的三个核心指针

```
Buffer 内部结构（以 ByteBuffer.allocate(10) 为例）：

初始状态（刚创建）：
 position=0                    limit=10   capacity=10
     ↓                             ↓           ↓
 [_][_][_][_][_][_][_][_][_][_]
  0  1  2  3  4  5  6  7  8  9

写入3字节后：
      position=3               limit=10
          ↓                        ↓
 [H][e][l][_][_][_][_][_][_][_]

调用 flip()（写→读切换）：
 position=0    limit=3
     ↓             ↓
 [H][e][l][_][_][_][_][_][_][_]
 ↑ 从这里开始读   ↑ 读到这里停

读取2字节后：
      position=2  limit=3
          ↓           ↓
 [H][e][l][_][_][_][_][_][_][_]
          ↑ 还有1字节未读

调用 clear()（重置，准备重新写）：
 position=0                    limit=10
     ↓                             ↓
 [H][e][l][_][_][_][_][_][_][_]  ← 数据还在，但指针重置了（可以覆盖写）

调用 compact()（保留未读数据，继续写）：
      position=1               limit=10
          ↓                        ↓
 [l][_][_][_][_][_][_][_][_][_]  ← 未读的 'l' 移到开头，position 在 'l' 之后
```

---

## 2.2 flip()、clear()、compact() 的区别

```java
// BufferDemo.java → demonstrateBufferOperations()

ByteBuffer buf = ByteBuffer.allocate(10);

// === 场景1：完整读完后重新写 → 用 clear() ===
buf.put("Hello".getBytes());  // 写入5字节
buf.flip();                   // 切换读模式：position=0, limit=5
byte[] data = new byte[buf.remaining()];
buf.get(data);                // 读完所有数据，position=5=limit
// 读完了，重新写
buf.clear();                  // position=0, limit=10，准备重新写

// === 场景2：读了一部分，未读完，继续往后写 → 用 compact() ===
buf.put("Hello World".getBytes()); // 写11字节（假设 capacity 够）
buf.flip();                        // 切换读模式
byte[] head = new byte[5];
buf.get(head);                     // 只读了"Hello"，"World"还没读
// 此时还想往 buf 里写更多数据
buf.compact();                     // 把未读的"World"移到开头，position 指向 World 之后
buf.put(" Netty".getBytes());      // 继续写
```

**三个操作的对比**：

| 操作 | position | limit | 数据 | 典型用途 |
|------|---------|-------|------|---------|
| `flip()` | 0 | 原 position | 保留 | 写完切读 |
| `clear()` | 0 | capacity | 保留（但会被覆盖）| 读完重新写 |
| `compact()` | 未读字节数 | capacity | 未读部分移到开头 | 部分读完继续写 |

---

## 2.3 Direct Buffer vs Heap Buffer

```java
// BufferDemo.java → compareDirectAndHeap()

// Heap Buffer（堆内存）
ByteBuffer heap = ByteBuffer.allocate(1024);
// 优点：受 GC 管理，无需手动释放
// 缺点：与 Socket 交互时需要额外拷贝（堆内存 → OS 内核缓冲区）

// Direct Buffer（直接内存 / 堆外内存）
ByteBuffer direct = ByteBuffer.allocateDirect(1024);
// 优点：直接在 OS 内存中分配，Socket 读写无需额外拷贝（零拷贝基础）
// 缺点：不受 GC 管理，分配/释放代价高，需要复用

// 数据流路径对比：
// Heap：   网卡 → 内核缓冲区 → JVM堆 → 应用处理
//                              ↑ 一次额外拷贝
// Direct： 网卡 → 内核缓冲区 / Direct内存（共享物理内存映射）→ 应用处理
//                              ↑ 省去这次拷贝

// 如何选择：
// 长期持有、大块数据 → Direct Buffer（减少 I/O 拷贝）
// 短期临时数据       → Heap Buffer（创建快，GC 自动管理）
```

---

## 2.4 mark() 与 reset()

```java
// BufferDemo.java → demonstrateMarkReset()

ByteBuffer buf = ByteBuffer.wrap("Hello World".getBytes());
buf.get();  // 读 'H'，position=1
buf.get();  // 读 'e'，position=2
buf.mark(); // 标记当前位置（mark=2）
buf.get();  // 读 'l'，position=3
buf.get();  // 读 'l'，position=4
buf.reset();// position 回到 mark=2
buf.get();  // 重新读 'l'（position=3）

// 典型用途：尝试解析协议，数据不够时回退重试
// （类似 Netty 的 markReaderIndex / resetReaderIndex）
int savedPosition = buf.position();  // 或者直接记录 position
// 解析失败：
buf.position(savedPosition);         // 手动回退
```

---

## 2.5 Buffer 的类型体系

```
Buffer（抽象基类）
  ├── ByteBuffer    ← 最常用，网络 I/O 全部用这个
  ├── CharBuffer
  ├── ShortBuffer
  ├── IntBuffer
  ├── LongBuffer
  ├── FloatBuffer
  └── DoubleBuffer

ByteBuffer 的创建方式：
  ByteBuffer.allocate(n)       → 堆内存，普通 Java 数组
  ByteBuffer.allocateDirect(n) → 直接内存，操作系统内存
  ByteBuffer.wrap(byte[])      → 包装已有数组，零拷贝
```

---

## 2.6 本章总结

- **三指针**：`position`（当前位置）、`limit`（读/写边界）、`capacity`（总容量）
- **flip()**：写完调，切到读模式（position=0, limit=写入量）
- **clear()**：读完调，重置为写模式（position=0, limit=capacity）
- **compact()**：部分读完继续写，保留未读数据移到开头
- **Direct Buffer**：省去堆→内核的拷贝，适合长期持有的网络 I/O 缓冲区；Heap Buffer 适合短期临时数据

> **本章对应演示代码**：`BufferDemo.java`（三指针状态可视化、flip/clear/compact 对比、Direct vs Heap 性能测试、mark/reset）

**继续阅读**：[03_Selector与多路复用.md](./03_Selector与多路复用.md)
