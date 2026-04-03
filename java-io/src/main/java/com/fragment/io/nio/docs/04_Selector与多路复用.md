# 第3章：Selector与多路复用深度剖析

> **本章目标**：深入理解I/O多路复用的原理、掌握Selector的使用方法、理解epoll的优势

---

## 一、多路复用的本质

### 1.1 问题：什么是I/O多路复用？为什么需要它？

#### 回顾：单线程如何处理多个连接？

```java
// 尝试1：阻塞式轮询（不可行）
List<SocketChannel> channels = new ArrayList<>();

while (true) {
    // 问题：accept()会阻塞
    SocketChannel newChannel = serverChannel.accept();
    channels.add(newChannel);
    
    // 问题：read()会阻塞，导致无法处理其他连接
    for (SocketChannel channel : channels) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.read(buffer);  // 阻塞！
    }
}
```

```java
// 尝试2：非阻塞轮询（CPU空转）
serverChannel.configureBlocking(false);

while (true) {
    SocketChannel newChannel = serverChannel.accept();  // 立即返回，可能为null
    if (newChannel != null) {
        newChannel.configureBlocking(false);
        channels.add(newChannel);
    }
    
    // 轮询所有连接
    for (SocketChannel channel : channels) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int len = channel.read(buffer);  // 立即返回，可能为0
        if (len > 0) {
            // 处理数据
        }
    }
}

// 问题：CPU一直在空转，利用率100%但没做有效工作
```

#### I/O多路复用的解决方案：

```
核心思想：让操作系统帮我们监听多个Socket，哪个有事件就通知我们

传统方式（一个线程一个连接）：
┌─────────────────────────────────────────────┐
│  线程1 → Socket1 (阻塞等待)                  │
│  线程2 → Socket2 (阻塞等待)                  │
│  线程3 → Socket3 (阻塞等待)                  │
│  ...                                        │
│  线程N → SocketN (阻塞等待)                  │
│                                             │
│  问题：N个连接需要N个线程                    │
└─────────────────────────────────────────────┘

多路复用方式（一个线程多个连接）：
┌─────────────────────────────────────────────┐
│              应用程序（单线程）              │
│                    ↓                        │
│              Selector.select()              │
│         (阻塞等待任一Socket就绪)             │
│                    ↓                        │
│         ┌──────────┴──────────┐             │
│         │   操作系统内核        │             │
│         │  监听多个Socket      │             │
│         └──────────┬──────────┘             │
│                    ↓                        │
│    [Socket1, Socket2, Socket3, ...]        │
│                                             │
│  优势：1个线程可以管理N个连接                │
└─────────────────────────────────────────────┘
```

### 1.2 问题：操作系统是如何实现多路复用的？

#### 通俗理解：三种实现方式的本质区别

想象你是一个餐厅服务员，需要照顾多张桌子的客人：

**select方式**：像一个新手服务员
```
你手里拿着一个小本子（最多记录1024张桌子）

每次巡视：
1. 把本子上的所有桌号告诉经理（拷贝到内核）
2. 经理挨个检查每张桌子是否需要服务（遍历所有）
3. 经理告诉你"有桌子需要服务了"
4. 你再挨个问每张桌子"是你需要服务吗？"（再次遍历）

问题：
- 本子太小，只能记1024张桌子
- 每次都要把整个本子给经理看（拷贝开销）
- 经理要检查所有桌子（O(n)复杂度）
- 你还要再问一遍所有桌子（O(n)复杂度）
```

**poll方式**：像一个有经验的服务员
```
你换了一个大本子（可以记录无限张桌子）

每次巡视：
1. 把本子给经理（还是要拷贝）
2. 经理挨个检查每张桌子（还是要遍历）
3. 经理在本子上标记哪些桌子需要服务
4. 你看本子就知道哪些桌子需要服务了

改进：
- 本子变大了，可以记录更多桌子
- 不用再问一遍了，本子上有标记

未改进：
- 还是要把本子给经理（拷贝开销）
- 经理还是要检查所有桌子（O(n)复杂度）
```

**epoll方式**：像一个智能服务员（推荐）⭐⭐⭐
```
你和经理建立了一个智能系统：

初始化（只做一次）：
1. 把所有桌号告诉经理，经理记在自己的系统里（红黑树）
2. 每张桌子装一个呼叫器
3. 客人需要服务时按呼叫器，桌号自动加入"需要服务列表"

每次巡视：
1. 你问经理"哪些桌子需要服务？"
2. 经理直接告诉你"需要服务列表"（只包含需要服务的桌子）
3. 你只需要服务这些桌子

优势：
- 不用每次都报告所有桌号（不需要拷贝）
- 经理不用检查所有桌子（不需要遍历）
- 直接得到需要服务的桌子列表（O(1)复杂度）
- 可以管理海量桌子（支持大规模并发）

这就是为什么epoll性能最好！
```

**形象对比**：
```
监听10000个连接（桌子）：

select：
- 每次把10000个号码抄给经理
- 经理检查10000个桌子
- 你再问10000个桌子
- 即使只有1个桌子需要服务，也要做10000次检查

poll：
- 每次把10000个号码抄给经理
- 经理检查10000个桌子并标记
- 你看标记就知道哪个需要服务
- 还是要检查10000次

epoll：
- 只在开始时告诉经理一次
- 之后只问"哪些需要服务？"
- 经理直接告诉你"3号、7号、9号"
- 只需要服务3个桌子，效率极高！
```

#### 设计思想：epoll体现了控制反转（IoC）

**传统方式（select/poll）：主动轮询**
```
应用程序主动控制流程：

while (true) {
    // 我主动问："哪些Socket有数据？"
    for (每个Socket) {
        检查是否有数据
    }
    处理有数据的Socket
}

特点：
- 应用程序主动遍历
- 应用程序控制检查逻辑
- 应用程序承担遍历成本
- 拉模式（Pull）：我去拉取数据
```

**epoll方式：事件驱动（控制反转）⭐**
```
操作系统控制流程：

应用程序：
    注册感兴趣的事件 → 操作系统
    等待通知
    
操作系统：
    监听所有Socket
    当Socket就绪时 → 主动通知应用程序
    
应用程序：
    收到通知
    处理就绪的Socket

特点：
- 操作系统控制检查逻辑（控制权反转）
- 应用程序被动接收通知（依赖注入）
- "不要调用我，我会调用你"（Hollywood原则）
- 推模式（Push）：内核推送通知给我
```

**用代码类比理解**：

```java
// 传统方式（没有IoC）：应用程序主动控制
class Application {
    void checkSockets() {
        for (Socket socket : allSockets) {
            if (socket.hasData()) {  // 主动检查
                process(socket);
            }
        }
    }
}

// epoll方式（IoC）：控制反转
class Application {
    void init() {
        // 注册回调（依赖注入）
        os.registerCallback(this::onSocketReady);
    }
    
    // 被动接收通知（控制反转）
    void onSocketReady(Socket socket) {
        process(socket);
    }
}

class OperatingSystem {
    void monitorSockets() {
        // 操作系统控制流程
        when (socket.hasData()) {
            callback.onSocketReady(socket);  // 主动调用你
        }
    }
}
```

**控制反转对比表**：

| 维度 | select/poll（主动） | epoll（控制反转） |
|------|-------------------|------------------|
| **控制权** | 应用程序主动遍历 | 操作系统主动通知 |
| **调用关系** | 应用 → 内核（轮询） | 内核 → 应用（回调） |
| **设计模式** | 拉模式（Pull） | 推模式（Push） |
| **职责** | 应用承担检查成本 | 内核承担检查成本 |
| **类比** | 你去餐厅问"好了吗？" | 餐厅给你呼叫器 |

**控制反转带来的好处**：
```
1. 按需通知：只在有事件时才通知，不做无用功
2. 职责分离：应用专注业务，内核专注监听
3. 扩展性好：轻松支持海量连接
4. 资源高效：避免无谓的遍历和检查
```

**类似的IoC应用**：
- **Spring IoC**：框架控制对象创建和依赖注入
- **事件驱动架构**：事件源主动通知监听者
- **观察者模式**：被观察者主动通知观察者
- **Reactor模式**：Reactor主动分发事件
- **Node.js事件循环**：事件循环主动调用回调函数

---

#### 三种实现方式：select、poll、epoll

#### 方式1：select（最早的实现）

```c
// select系统调用的原型
int select(
    int maxfd,              // 最大文件描述符+1
    fd_set *readfds,        // 可读文件描述符集合
    fd_set *writefds,       // 可写文件描述符集合
    fd_set *exceptfds,      // 异常文件描述符集合
    struct timeval *timeout // 超时时间
);
```

**工作流程**：

```
select的工作流程：

1. 应用程序准备fd_set
┌─────────────────────────────────────────┐
│ fd_set readfds;                         │
│ FD_ZERO(&readfds);                      │
│ FD_SET(socket1, &readfds);              │
│ FD_SET(socket2, &readfds);              │
│ FD_SET(socket3, &readfds);              │
└─────────────────────────────────────────┘

2. 调用select，进入内核态
┌─────────────────────────────────────────┐
│ select(maxfd, &readfds, NULL, NULL, NULL);│
│   ↓                                     │
│ 内核遍历所有fd，检查是否就绪            │
│   ↓                                     │
│ 如果没有就绪的，进程休眠                │
│   ↓                                     │
│ 有fd就绪时，唤醒进程                    │
└─────────────────────────────────────────┘

3. 返回用户态，遍历fd_set
┌─────────────────────────────────────────┐
│ for (int i = 0; i < maxfd; i++) {      │
│     if (FD_ISSET(i, &readfds)) {       │
│         // fd i 就绪，处理               │
│     }                                   │
│ }                                       │
└─────────────────────────────────────────┘
```

**select的限制**：

```
限制1：fd数量限制
┌─────────────────────────────────────────┐
│ - 默认最多1024个fd（FD_SETSIZE）        │
│ - 受限于fd_set的位图大小                │
│ - 无法支持大规模并发                    │
└─────────────────────────────────────────┘

限制2：性能问题
┌─────────────────────────────────────────┐
│ - 每次调用都要拷贝fd_set到内核          │
│ - 内核需要遍历所有fd检查是否就绪        │
│ - 返回后需要遍历所有fd找出就绪的        │
│ - 时间复杂度：O(n)                      │
└─────────────────────────────────────────┘

限制3：不可移植
┌─────────────────────────────────────────┐
│ - 不同系统的实现不同                    │
│ - Windows的实现与Unix不同               │
└─────────────────────────────────────────┘
```

#### 方式2：poll（select的改进）

```c
// poll系统调用的原型
int poll(
    struct pollfd *fds,     // 文件描述符数组
    nfds_t nfds,           // 数组大小
    int timeout            // 超时时间
);

struct pollfd {
    int fd;                // 文件描述符
    short events;          // 关注的事件
    short revents;         // 实际发生的事件
};
```

**poll vs select**：

```
改进1：没有fd数量限制
┌─────────────────────────────────────────┐
│ - 使用数组而不是位图                    │
│ - 理论上可以监听任意数量的fd            │
└─────────────────────────────────────────┘

改进2：更灵活的事件类型
┌─────────────────────────────────────────┐
│ - POLLIN：可读                          │
│ - POLLOUT：可写                         │
│ - POLLERR：错误                         │
│ - POLLHUP：挂断                         │
└─────────────────────────────────────────┘

未改进的问题：
┌─────────────────────────────────────────┐
│ - 仍然需要拷贝fd数组到内核              │
│ - 仍然需要遍历所有fd                    │
│ - 时间复杂度仍是O(n)                    │
└─────────────────────────────────────────┘
```

#### 方式3：epoll（Linux的高性能实现）⭐⭐⭐

```c
// epoll的三个系统调用

// 1. 创建epoll实例
int epoll_create(int size);

// 2. 注册/修改/删除fd
int epoll_ctl(
    int epfd,              // epoll实例
    int op,                // 操作：ADD/MOD/DEL
    int fd,                // 文件描述符
    struct epoll_event *event  // 事件
);

// 3. 等待事件
int epoll_wait(
    int epfd,              // epoll实例
    struct epoll_event *events,  // 返回的事件数组
    int maxevents,         // 最多返回的事件数
    int timeout            // 超时时间
);
```

**epoll的工作原理**：

```
epoll的核心数据结构：

1. 红黑树：存储所有监听的fd
┌─────────────────────────────────────────┐
│          红黑树（内核维护）              │
│                                         │
│         ┌─────┐                         │
│         │ fd1 │                         │
│        /       \                        │
│    ┌─────┐  ┌─────┐                    │
│    │ fd2 │  │ fd3 │                    │
│                                         │
│  - 插入/删除：O(log n)                  │
│  - 查找：O(log n)                       │
└─────────────────────────────────────────┘

2. 就绪列表：存储就绪的fd
┌─────────────────────────────────────────┐
│        就绪列表（双向链表）              │
│                                         │
│  ┌─────┐  ┌─────┐  ┌─────┐            │
│  │ fd1 │→│ fd5 │→│ fd9 │             │
│  └─────┘  └─────┘  └─────┘            │
│                                         │
│  - 只包含就绪的fd                       │
│  - epoll_wait直接返回这个列表           │
└─────────────────────────────────────────┘
```

**epoll的工作流程**：

```
完整流程：

1. 创建epoll实例
┌─────────────────────────────────────────┐
│ int epfd = epoll_create(1024);         │
│   ↓                                     │
│ 内核创建：                              │
│ - 红黑树（空）                          │
│ - 就绪列表（空）                        │
└─────────────────────────────────────────┘

2. 注册fd（只需一次）
┌─────────────────────────────────────────┐
│ epoll_ctl(epfd, EPOLL_CTL_ADD, fd1, &event);│
│   ↓                                     │
│ 内核操作：                              │
│ - 将fd1添加到红黑树                     │
│ - 注册回调函数                          │
│ - 当fd1就绪时，回调函数将fd1加入就绪列表│
└─────────────────────────────────────────┘

3. 等待事件
┌─────────────────────────────────────────┐
│ int n = epoll_wait(epfd, events, max, timeout);│
│   ↓                                     │
│ 内核操作：                              │
│ - 检查就绪列表是否为空                  │
│ - 如果为空，进程休眠                    │
│ - 有fd就绪时，回调函数将其加入就绪列表  │
│ - 唤醒进程，返回就绪列表                │
└─────────────────────────────────────────┘

4. 处理就绪的fd
┌─────────────────────────────────────────┐
│ for (int i = 0; i < n; i++) {          │
│     int fd = events[i].data.fd;        │
│     // 处理fd                           │
│ }                                       │
│   ↓                                     │
│ 只需遍历就绪的fd，不需要遍历所有fd      │
└─────────────────────────────────────────┘
```

**epoll的两种触发模式**：

```
水平触发（Level Triggered, LT）：
┌─────────────────────────────────────────┐
│ 特点：                                  │
│ - 只要fd就绪，就会一直通知              │
│ - 即使没有处理完，下次epoll_wait仍会返回│
│                                         │
│ 示例：                                  │
│ 1. Socket有100字节数据                  │
│ 2. epoll_wait返回可读事件               │
│ 3. 只读取50字节                         │
│ 4. 下次epoll_wait仍会返回可读事件       │
│ 5. 继续读取剩余50字节                   │
│                                         │
│ 优点：不会丢失事件，编程简单            │
│ 缺点：可能产生大量重复通知              │
└─────────────────────────────────────────┘

边缘触发（Edge Triggered, ET）：
┌─────────────────────────────────────────┐
│ 特点：                                  │
│ - 只在状态变化时通知一次                │
│ - 必须一次性处理完所有数据              │
│                                         │
│ 示例：                                  │
│ 1. Socket有100字节数据                  │
│ 2. epoll_wait返回可读事件（只通知一次） │
│ 3. 必须读取完所有100字节                │
│ 4. 如果只读50字节，剩余数据不会再通知   │
│                                         │
│ 优点：减少通知次数，效率更高            │
│ 缺点：必须配合非阻塞I/O，编程复杂       │
└─────────────────────────────────────────┘
```

**select vs poll vs epoll 对比**：

```
性能对比（监听10000个连接）：

┌──────────┬──────────┬──────────┬──────────┐
│  特性    │  select  │   poll   │  epoll   │
├──────────┼──────────┼──────────┼──────────┤
│ fd数量   │  1024    │  无限制  │  无限制  │
│ 时间复杂度│  O(n)    │  O(n)    │  O(1)    │
│ 内存拷贝 │  每次    │  每次    │  不需要  │
│ 遍历fd   │  所有    │  所有    │  只遍历  │
│          │          │          │  就绪的  │
│ 跨平台   │  是      │  是      │  Linux   │
│ 性能     │  差      │  中      │  优秀    │
└──────────┴──────────┴──────────┴──────────┘

性能测试结果：
- 100个连接：select ≈ poll ≈ epoll
- 1000个连接：epoll > poll > select
- 10000个连接：epoll >> poll > select
```

#### 问题：既然epoll这么好，为什么还要用select/poll？

**select/poll仍然有用的场景**：

**1. 跨平台需求**
```
epoll的局限：
┌─────────────────────────────────────────┐
│ - epoll只在Linux上可用                  │
│ - Windows不支持epoll                    │
│ - macOS不支持epoll（使用kqueue）        │
│ - 老版本Unix系统不支持                  │
└─────────────────────────────────────────┘

select的优势：
┌─────────────────────────────────────────┐
│ - POSIX标准，几乎所有系统都支持         │
│ - 代码可以在任何平台上运行              │
│ - 适合需要跨平台的应用                  │
└─────────────────────────────────────────┘

实际应用：
- 跨平台的网络库（如libevent）
- 需要在多种操作系统上运行的软件
- 嵌入式系统（可能不支持epoll）
```

**2. 连接数很少的场景**
```
当连接数 < 100时：
┌─────────────────────────────────────────┐
│ select性能：  ████████████ 95%          │
│ epoll性能：   ████████████ 100%         │
│                                         │
│ 性能差异：5%（几乎可以忽略）            │
│                                         │
│ 但是：                                  │
│ - select代码更简单                      │
│ - select更容易理解和维护                │
│ - select不需要额外的系统调用            │
└─────────────────────────────────────────┘

适用场景：
- 小型应用（如命令行工具）
- 连接数固定且很少的场景
- 对性能要求不高的场景
```

**3. 超时精度要求高的场景**
```
select的超时特性：
┌─────────────────────────────────────────┐
│ struct timeval timeout;                 │
│ timeout.tv_sec = 0;                     │
│ timeout.tv_usec = 100;  // 100微秒      │
│                                         │
│ - 支持微秒级超时                        │
│ - 超时时间可以精确控制                  │
└─────────────────────────────────────────┘

epoll的超时特性：
┌─────────────────────────────────────────┐
│ epoll_wait(epfd, events, max, 1);       │
│                                         │
│ - 只支持毫秒级超时                      │
│ - 精度相对较低                          │
└─────────────────────────────────────────┘

适用场景：
- 需要精确定时的应用
- 实时性要求极高的场景
```

**4. 可移植性和兼容性考虑**
```
历史遗留系统：
┌─────────────────────────────────────────┐
│ - 老代码使用select/poll                 │
│ - 迁移成本高                            │
│ - 如果性能够用，没必要改                │
└─────────────────────────────────────────┘

标准化考虑：
┌─────────────────────────────────────────┐
│ - select是POSIX标准                     │
│ - epoll是Linux特有的                    │
│ - 某些场景需要遵循标准                  │
└─────────────────────────────────────────┘
```

**5. 简单场景不需要epoll的复杂性**
```
select的简单性：
┌─────────────────────────────────────────┐
│ fd_set readfds;                         │
│ FD_ZERO(&readfds);                      │
│ FD_SET(fd, &readfds);                   │
│ select(fd+1, &readfds, NULL, NULL, NULL);│
│                                         │
│ - 代码简单直观                          │
│ - 容易理解和调试                        │
└─────────────────────────────────────────┘

epoll的复杂性：
┌─────────────────────────────────────────┐
│ int epfd = epoll_create(1);            │
│ struct epoll_event event;              │
│ epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &event);│
│ epoll_wait(epfd, events, max, timeout);│
│                                         │
│ - 需要更多代码                          │
│ - 需要管理epoll实例                     │
│ - 学习曲线更陡                          │
└─────────────────────────────────────────┘
```

**实际使用建议**：

```
选择决策树：

是否需要跨平台？
├─ 是 → 使用select或poll（或使用封装库如libevent）
└─ 否 → 继续判断

连接数是否 > 1000？
├─ 否 → select/poll足够（代码更简单）
└─ 是 → 继续判断

是否在Linux上？
├─ 是 → 使用epoll（性能最优）
└─ 否 → 使用对应平台的最优方案
         - Windows: IOCP
         - macOS: kqueue
         - FreeBSD: kqueue
```

**现代实践**：

```
1. 应用层：使用高级框架
┌─────────────────────────────────────────┐
│ Java:    Netty（自动选择最优实现）      │
│ Python:  asyncio（自动选择）            │
│ Node.js: libuv（自动选择）              │
│ Go:      net/http（自动选择）           │
│                                         │
│ 优势：框架会根据平台自动选择最优方案    │
└─────────────────────────────────────────┘

2. 底层库：使用抽象层
┌─────────────────────────────────────────┐
│ libevent:  跨平台事件库                 │
│ libev:     高性能事件库                 │
│ libuv:     Node.js的底层库              │
│                                         │
│ 优势：                                  │
│ - 自动选择最优实现（Linux用epoll）      │
│ - 提供统一的API                         │
│ - 处理平台差异                          │
└─────────────────────────────────────────┘

3. 直接使用系统调用：特定场景
┌─────────────────────────────────────────┐
│ 高性能服务器（如Nginx、Redis）：        │
│ - 直接使用epoll                         │
│ - 针对Linux优化                         │
│ - 追求极致性能                          │
└─────────────────────────────────────────┘
```

**总结**：

```
select/poll的现状：
✓ 不是被淘汰，而是有特定的使用场景
✓ 在跨平台、小规模连接场景仍然有价值
✓ 作为POSIX标准，具有广泛的兼容性

epoll的定位：
✓ Linux上的最优选择
✓ 大规模并发的首选方案
✓ 高性能服务器的标配

最佳实践：
✓ 使用高级框架（Netty、libevent等）
✓ 让框架自动选择最优实现
✓ 不要过早优化，先保证正确性
✓ 根据实际需求选择合适的方案
```

---

## 二、Java NIO的Selector

### 2.1 问题：Selector是如何封装底层多路复用的？

```
Selector的实现层次：

Java层：
┌─────────────────────────────────────────┐
│         Selector（抽象类）               │
│         - select()                      │
│         - selectedKeys()                │
└─────────────────┬───────────────────────┘
                  │
                  │ 继承
                  ↓
┌─────────────────────────────────────────┐
│      SelectorProvider（工厂类）          │
│      - openSelector()                   │
└─────────────────┬───────────────────────┘
                  │
                  │ 创建
                  ↓
┌─────────────────────────────────────────┐
│   平台相关的Selector实现                 │
│                                         │
│   Linux:   EPollSelectorImpl            │
│   Windows: WindowsSelectorImpl          │
│   Mac:     KQueueSelectorImpl           │
└─────────────────┬───────────────────────┘
                  │
                  │ JNI调用
                  ↓
┌─────────────────────────────────────────┐
│         操作系统层                       │
│                                         │
│   Linux:   epoll_create/epoll_wait      │
│   Windows: select                       │
│   Mac:     kqueue                       │
└─────────────────────────────────────────┘

Java会自动选择最优的实现：
- Linux → epoll
- Windows → select（IOCP在AIO中使用）
- Mac → kqueue
```

### 2.2 Selector的基本使用

#### 创建Selector：

```java
// 创建Selector
Selector selector = Selector.open();

// 底层实现（Linux）：
// 1. 调用epoll_create()创建epoll实例
// 2. 创建pipe用于唤醒selector
// 3. 初始化就绪事件集合
```

#### 注册Channel到Selector：

```java
// 1. 创建ServerSocketChannel
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.bind(new InetSocketAddress(8080));

// 2. 设置为非阻塞模式（必须）
serverChannel.configureBlocking(false);

// 3. 注册到Selector，关注ACCEPT事件
SelectionKey key = serverChannel.register(
    selector,
    SelectionKey.OP_ACCEPT  // 关注的事件
);

// 4. 可以附加对象到SelectionKey
key.attach(new ServerContext());
```

**为什么必须设置为非阻塞模式？**

```
原因分析：

阻塞模式 + Selector：
┌─────────────────────────────────────────┐
│ selector.select();  // 返回就绪的Channel│
│   ↓                                     │
│ channel.read(buffer);  // 如果阻塞...   │
│   ↓                                     │
│ 其他就绪的Channel无法处理！             │
└─────────────────────────────────────────┘

非阻塞模式 + Selector：
┌─────────────────────────────────────────┐
│ selector.select();  // 返回就绪的Channel│
│   ↓                                     │
│ channel.read(buffer);  // 立即返回      │
│   ↓                                     │
│ 继续处理其他Channel                     │
└─────────────────────────────────────────┘

结论：Selector的设计就是为了非阻塞I/O
```

#### SelectionKey的事件类型：

```java
// 四种事件类型
SelectionKey.OP_ACCEPT   // 接收连接就绪（ServerSocketChannel）
SelectionKey.OP_CONNECT  // 连接就绪（SocketChannel）
SelectionKey.OP_READ     // 读就绪（有数据可读）
SelectionKey.OP_WRITE    // 写就绪（可以写数据）

// 注册多个事件
channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

// 修改关注的事件
key.interestOps(SelectionKey.OP_READ);

// 获取当前关注的事件
int ops = key.interestOps();

// 检查事件类型
if ((ops & SelectionKey.OP_READ) != 0) {
    // 关注读事件
}
```

### 2.3 问题：Selector的事件循环如何编写？

#### 标准的Selector事件循环：

```java
public class NIOServer {
    
    public static void main(String[] args) throws IOException {
        // 1. 创建Selector
        Selector selector = Selector.open();
        
        // 2. 创建ServerSocketChannel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(8080));
        serverChannel.configureBlocking(false);
        
        // 3. 注册ACCEPT事件
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("服务器启动在端口8080");
        
        // 4. 事件循环
        while (true) {
            // 阻塞等待事件（可设置超时时间）
            int readyCount = selector.select();  // 或 select(timeout)
            
            if (readyCount == 0) {
                continue;
            }
            
            // 5. 获取就绪的SelectionKey集合
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            
            // 6. 遍历处理每个就绪的事件
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                
                // 7. 移除已处理的key（重要！）
                iterator.remove();
                
                // 8. 检查key是否有效
                if (!key.isValid()) {
                    continue;
                }
                
                // 9. 根据事件类型分别处理
                if (key.isAcceptable()) {
                    handleAccept(key);
                } else if (key.isReadable()) {
                    handleRead(key);
                } else if (key.isWritable()) {
                    handleWrite(key);
                }
            }
        }
    }
    
    // 处理连接事件
    private static void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        
        // 接受连接
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        
        // 注册READ事件
        clientChannel.register(key.selector(), SelectionKey.OP_READ);
        
        System.out.println("接受连接: " + clientChannel.getRemoteAddress());
    }
    
    // 处理读事件
    private static void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            // 连接关闭
            System.out.println("连接关闭: " + channel.getRemoteAddress());
            key.cancel();
            channel.close();
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            
            // 处理数据
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String message = new String(data);
            
            System.out.println("收到消息: " + message);
            
            // 回写数据（注册WRITE事件）
            buffer.clear();
            buffer.put(("Echo: " + message).getBytes());
            buffer.flip();
            
            // 将buffer附加到key
            key.attach(buffer);
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }
    
    // 处理写事件
    private static void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        // 获取附加的buffer
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        
        // 写入数据
        channel.write(buffer);
        
        if (!buffer.hasRemaining()) {
            // 写完了，继续关注READ事件
            key.interestOps(SelectionKey.OP_READ);
            key.attach(null);  // 清除附件
        }
    }
}
```

### 2.4 问题：为什么必须调用iterator.remove()？

```java
// 问题代码：忘记remove()
Set<SelectionKey> selectedKeys = selector.selectedKeys();
Iterator<SelectionKey> iterator = selectedKeys.iterator();

while (iterator.hasNext()) {
    SelectionKey key = iterator.next();
    // 忘记调用 iterator.remove();
    
    if (key.isReadable()) {
        handleRead(key);
    }
}
```

**不调用remove()的后果**：

```
第1次select()：
┌─────────────────────────────────────────┐
│ selectedKeys = {key1, key2}             │
│   ↓                                     │
│ 处理key1, key2                          │
│   ↓                                     │
│ 但没有remove()，selectedKeys仍是{key1, key2}│
└─────────────────────────────────────────┘

第2次select()：
┌─────────────────────────────────────────┐
│ 新的就绪事件：key3                      │
│   ↓                                     │
│ selectedKeys = {key1, key2, key3}       │
│   ↓                                     │
│ 问题：key1和key2会被重复处理！          │
└─────────────────────────────────────────┘

原因：
- selectedKeys是一个Set，不会自动清空
- select()只会向Set中添加新的就绪key
- 必须手动remove()已处理的key
```

**正确做法**：

```java
// 方式1：使用Iterator.remove()（推荐）
Iterator<SelectionKey> iterator = selectedKeys.iterator();
while (iterator.hasNext()) {
    SelectionKey key = iterator.next();
    iterator.remove();  // 立即移除
    
    // 处理事件
}

// 方式2：使用Set.remove()
for (SelectionKey key : selectedKeys) {
    selectedKeys.remove(key);  // 会抛ConcurrentModificationException
    // 错误！不能在遍历时修改Set
}

// 方式3：先复制再遍历
Set<SelectionKey> copy = new HashSet<>(selectedKeys);
selectedKeys.clear();
for (SelectionKey key : copy) {
    // 处理事件
}
```

### 2.5 问题：如何正确处理OP_WRITE事件？

```java
// 错误做法：一直注册WRITE事件
channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

// 问题：
// - Socket的写缓冲区通常是空的（可写）
// - OP_WRITE会一直触发
// - 导致CPU空转
```

**正确的写事件处理**：

```java
public class WriteEventHandler {
    
    // 场景：发送大量数据
    public void sendLargeData(SelectionKey key, ByteBuffer data) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        // 尝试直接写入
        int written = channel.write(data);
        
        if (data.hasRemaining()) {
            // 写缓冲区满了，无法一次写完
            // 将剩余数据附加到key
            key.attach(data);
            
            // 注册WRITE事件，等待可写
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }
    
    // 处理WRITE事件
    public void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer data = (ByteBuffer) key.attachment();
        
        // 继续写入
        channel.write(data);
        
        if (!data.hasRemaining()) {
            // 写完了，取消WRITE事件
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            key.attach(null);
            
            System.out.println("数据发送完成");
        }
    }
}
```

**OP_WRITE的使用原则**：

```
原则1：按需注册
┌─────────────────────────────────────────┐
│ - 只在写缓冲区满时注册OP_WRITE          │
│ - 写完后立即取消OP_WRITE                │
│ - 不要一直注册                          │
└─────────────────────────────────────────┘

原则2：先尝试直接写
┌─────────────────────────────────────────┐
│ 1. 先调用channel.write()                │
│ 2. 如果写不完，再注册OP_WRITE           │
│ 3. 在WRITE事件中继续写                  │
│ 4. 写完后取消OP_WRITE                   │
└─────────────────────────────────────────┘

原则3：使用写队列
┌─────────────────────────────────────────┐
│ - 将待发送数据放入队列                  │
│ - 在WRITE事件中从队列取数据发送         │
│ - 队列为空时取消OP_WRITE                │
└─────────────────────────────────────────┘
```

### 2.6 问题：NIO的空轮询Bug是什么？如何解决？

#### 空轮询Bug的表现：

```java
// 正常情况
int count = selector.select();  // 阻塞等待事件
// count > 0 时才返回

// Bug情况
int count = selector.select();  // 立即返回0
// 即使没有事件，也立即返回
// 导致while循环空转，CPU 100%
```

**Bug的原因**：

```
Linux epoll的Bug：
┌─────────────────────────────────────────┐
│ 在某些情况下，epoll_wait会立即返回0：   │
│                                         │
│ 1. 连接被远程关闭，但本地未检测到       │
│ 2. 网络异常导致的假唤醒                 │
│ 3. 内核Bug                              │
│                                         │
│ 结果：                                  │
│ - selector.select()立即返回0            │
│ - selectedKeys为空                      │
│ - while循环空转                         │
│ - CPU飙升到100%                         │
└─────────────────────────────────────────┘
```

**Netty的解决方案**：

```java
public class SelectorBugWorkaround {
    
    private Selector selector;
    private static final int SELECTOR_AUTO_REBUILD_THRESHOLD = 512;
    private int emptySelectCount = 0;
    
    public void eventLoop() throws IOException {
        while (true) {
            long startTime = System.nanoTime();
            
            int selectedKeys = selector.select(1000);  // 1秒超时
            
            long endTime = System.nanoTime();
            long elapsedTime = endTime - startTime;
            
            if (selectedKeys == 0) {
                // 检测空轮询
                if (elapsedTime < 1000_000) {  // 小于1ms就返回
                    emptySelectCount++;
                    
                    if (emptySelectCount >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
                        // 重建Selector
                        System.out.println("检测到空轮询Bug，重建Selector");
                        rebuildSelector();
                        emptySelectCount = 0;
                    }
                } else {
                    // 正常超时
                    emptySelectCount = 0;
                }
            } else {
                // 有事件，重置计数
                emptySelectCount = 0;
                
                // 处理事件
                processSelectedKeys();
            }
        }
    }
    
    // 重建Selector
    private void rebuildSelector() throws IOException {
        Selector newSelector = Selector.open();
        Selector oldSelector = selector;
        
        // 将所有Channel重新注册到新Selector
        for (SelectionKey key : oldSelector.keys()) {
            if (!key.isValid()) {
                continue;
            }
            
            SelectableChannel channel = key.channel();
            int ops = key.interestOps();
            Object attachment = key.attachment();
            
            // 取消旧的注册
            key.cancel();
            
            // 注册到新Selector
            SelectionKey newKey = channel.register(newSelector, ops, attachment);
        }
        
        // 替换Selector
        selector = newSelector;
        
        // 关闭旧Selector
        oldSelector.close();
    }
    
    private void processSelectedKeys() {
        // 处理就绪事件
    }
}
```

**解决方案总结**：

```
方案1：检测并重建Selector（Netty的方案）
┌─────────────────────────────────────────┐
│ 1. 记录空轮询次数                       │
│ 2. 超过阈值（如512次）时重建Selector    │
│ 3. 将所有Channel重新注册到新Selector    │
└─────────────────────────────────────────┘

方案2：设置超时时间
┌─────────────────────────────────────────┐
│ selector.select(1000);  // 1秒超时      │
│ - 即使空轮询，也会等待1秒               │
│ - 降低CPU使用率                         │
│ - 但增加了延迟                          │
└─────────────────────────────────────────┘

方案3：使用selectNow()
┌─────────────────────────────────────────┐
│ int count = selector.selectNow();       │
│ if (count == 0) {                       │
│     Thread.sleep(10);  // 主动休眠      │
│ }                                       │
└─────────────────────────────────────────┘
```

---

## 三、Selector的高级特性

### 3.1 唤醒阻塞的Selector

```java
// 场景：主线程在select()阻塞，其他线程需要注册新Channel

// 线程1：事件循环
public void eventLoop() throws IOException {
    while (true) {
        selector.select();  // 阻塞
        // 处理事件
    }
}

// 线程2：注册新Channel
public void registerChannel(SocketChannel channel) throws IOException {
    // 问题：selector正在阻塞，无法注册
    
    // 解决方案：唤醒selector
    selector.wakeup();  // 唤醒阻塞的select()
    
    channel.configureBlocking(false);
    channel.register(selector, SelectionKey.OP_READ);
}
```

**wakeup()的工作原理**：

```
Linux实现（使用pipe）：
┌─────────────────────────────────────────┐
│ 1. Selector创建时，创建一个pipe         │
│    int[] pipe = {readFd, writeFd};      │
│                                         │
│ 2. 将readFd注册到epoll                  │
│                                         │
│ 3. select()阻塞在epoll_wait             │
│                                         │
│ 4. wakeup()向writeFd写入一个字节        │
│    write(writeFd, &dummy, 1);           │
│                                         │
│ 5. readFd变为可读，epoll_wait返回       │
│                                         │
│ 6. select()返回，可以注册新Channel      │
└─────────────────────────────────────────┘
```

### 3.2 Selector的关闭

```java
// 关闭Selector
selector.close();

// 关闭流程：
// 1. 取消所有注册的SelectionKey
// 2. 关闭底层的epoll实例
// 3. 释放相关资源

// 注意：
// - 关闭Selector不会关闭注册的Channel
// - 需要手动关闭所有Channel
for (SelectionKey key : selector.keys()) {
    key.channel().close();
}
selector.close();
```

### 3.3 SelectionKey的状态管理

```java
// SelectionKey的状态
SelectionKey key = channel.register(selector, ops);

// 1. 有效性检查
boolean valid = key.isValid();
// 以下情况key无效：
// - Channel被关闭
// - Selector被关闭
// - key被取消

// 2. 取消注册
key.cancel();
// - 将key加入取消集合
// - 下次select()时才真正取消
// - 取消后key变为无效

// 3. 获取关联对象
SelectableChannel channel = key.channel();
Selector selector = key.selector();
Object attachment = key.attachment();

// 4. 修改关注的事件
key.interestOps(SelectionKey.OP_READ);
// 或
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);  // 添加事件
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); // 移除事件

// 5. 获取就绪的事件
int readyOps = key.readyOps();
if ((readyOps & SelectionKey.OP_READ) != 0) {
    // 可读
}
```

---

## 四、实战：完整的NIO服务器

```java
/**
 * 完整的NIO Echo服务器
 * 演示Selector的正确使用方式
 */
public class NIOEchoServer {
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    
    public void start(int port) throws IOException {
        // 1. 创建Selector
        selector = Selector.open();
        
        // 2. 创建ServerSocketChannel
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        
        // 3. 注册ACCEPT事件
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("NIO Echo服务器启动在端口: " + port);
        
        // 4. 事件循环
        eventLoop();
    }
    
    private void eventLoop() throws IOException {
        while (true) {
            // 阻塞等待事件，1秒超时
            int readyCount = selector.select(1000);
            
            if (readyCount == 0) {
                // 超时，可以做一些定时任务
                continue;
            }
            
            // 获取就绪的事件
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();  // 立即移除
                
                if (!key.isValid()) {
                    continue;
                }
                
                try {
                    // 分发事件
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    System.err.println("处理事件异常: " + e.getMessage());
                    closeChannel(key);
                }
            }
        }
    }
    
    // 处理连接事件
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        
        // 接受连接
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel == null) {
            return;
        }
        
        clientChannel.configureBlocking(false);
        
        // 注册READ事件
        clientChannel.register(selector, SelectionKey.OP_READ);
        
        System.out.println("接受连接: " + clientChannel.getRemoteAddress());
    }
    
    // 处理读事件
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);
        
        if (bytesRead == -1) {
            // 连接关闭
            System.out.println("连接关闭: " + channel.getRemoteAddress());
            closeChannel(key);
            return;
        }
        
        if (bytesRead > 0) {
            // 准备写回数据
            readBuffer.flip();
            
            // 创建写缓冲区（复制数据）
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytesRead);
            writeBuffer.put(readBuffer);
            writeBuffer.flip();
            
            // 尝试直接写入
            channel.write(writeBuffer);
            
            if (writeBuffer.hasRemaining()) {
                // 写缓冲区满，注册WRITE事件
                key.attach(writeBuffer);
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        }
    }
    
    // 处理写事件
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer writeBuffer = (ByteBuffer) key.attachment();
        
        // 继续写入
        channel.write(writeBuffer);
        
        if (!writeBuffer.hasRemaining()) {
            // 写完了，取消WRITE事件
            key.attach(null);
            key.interestOps(SelectionKey.OP_READ);
        }
    }
    
    // 关闭连接
    private void closeChannel(SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) throws IOException {
        new NIOEchoServer().start(8080);
    }
}
```

---

## 五、总结：Selector的设计哲学

### 核心设计原则：

```
1. 事件驱动
   - 只处理就绪的I/O
   - 不浪费CPU资源

2. 单线程多连接
   - 一个线程管理多个连接
   - 减少线程开销和上下文切换

3. 非阻塞I/O
   - 所有I/O操作立即返回
   - 配合Selector实现高并发

4. 零拷贝支持
   - 底层使用epoll等高效机制
   - 减少数据拷贝
```

### 最佳实践：

```
1. 必须设置非阻塞模式
2. 必须调用iterator.remove()
3. 按需注册OP_WRITE事件
4. 处理空轮询Bug
5. 正确处理连接关闭
6. 使用attachment传递上下文
7. 异常处理要完善
```

---

**下一章预告**：我们将学习零拷贝技术的完整实现，以及Reactor模式的三种形态，这是理解Netty的关键。
