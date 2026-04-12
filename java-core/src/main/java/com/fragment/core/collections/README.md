# Java集合框架核心数据结构深度学习指南

## 📚 目录结构

```
collections/
├── hashmap/                                          # HashMap专题
│   ├── docs/                                         # 文档目录
│   │   ├── 01_HashMap的必要性与演进历史.md            # 第一章：为什么需要HashMap
│   │   ├── 02_HashMap核心原理与数据结构.md            # 第二章：数组+链表+红黑树
│   │   ├── 03_HashMap源码深度剖析.md                 # 第三章：put/get/resize源码
│   │   ├── 04_HashMap线程安全问题与解决方案.md        # 第四章：并发问题、ConcurrentHashMap
│   │   └── 05_HashMap最佳实践与性能优化.md           # 第五章：最佳实践、性能调优
│   ├── demo/                                         # 演示代码
│   │   ├── HashMapBasicDemo.java                    # HashMap基础使用演示
│   │   ├── HashMapCollisionDemo.java                # Hash冲突演示
│   │   ├── HashMapResizeDemo.java                   # 扩容机制演示
│   │   └── HashMapThreadSafetyDemo.java             # 线程安全问题演示
│   └── project/                                      # 实际项目Demo
│       ├── LRUCache.java                            # LRU缓存实现
│       └── UserSessionManager.java                  # 用户会话管理器
│
├── linkedlist/                                       # LinkedList专题
│   ├── docs/                                         # 文档目录
│   │   ├── 01_LinkedList的必要性与应用场景.md         # 第一章：为什么需要LinkedList
│   │   ├── 02_LinkedList核心原理与数据结构.md         # 第二章：双向链表结构
│   │   ├── 03_LinkedList源码深度剖析.md              # 第三章：add/remove/get源码
│   │   ├── 04_LinkedList与ArrayList对比分析.md       # 第四章：性能对比、选型指南
│   │   └── 05_LinkedList最佳实践与高级应用.md         # 第五章：队列、栈、双端队列
│   ├── demo/                                         # 演示代码
│   │   ├── LinkedListBasicDemo.java                 # LinkedList基础使用演示
│   │   ├── LinkedListPerformanceDemo.java           # 性能对比演示
│   │   ├── LinkedListAsQueueDemo.java               # 作为队列使用演示
│   │   └── LinkedListAsStackDemo.java               # 作为栈使用演示
│   └── project/                                      # 实际项目Demo
│       ├── TaskQueue.java                           # 任务队列实现
│       └── BrowserHistoryManager.java               # 浏览器历史记录管理
│
├── linkedhashmap/                                    # LinkedHashMap专题
│   ├── docs/                                         # 文档目录
│   │   ├── 01_LinkedHashMap的必要性与应用场景.md      # 第一章：为什么需要LinkedHashMap
│   │   ├── 02_LinkedHashMap核心原理与数据结构.md      # 第二章：HashMap+双向链表
│   │   ├── 03_LinkedHashMap源码深度剖析.md           # 第三章：访问顺序、插入顺序
│   │   ├── 04_LinkedHashMap实现LRU缓存.md            # 第四章：LRU缓存原理与实现
│   │   └── 05_LinkedHashMap最佳实践与性能分析.md      # 第五章：最佳实践、性能对比
│   ├── demo/                                         # 演示代码
│   │   ├── LinkedHashMapBasicDemo.java              # LinkedHashMap基础使用演示
│   │   ├── LinkedHashMapOrderDemo.java              # 顺序演示
│   │   ├── LinkedHashMapAccessOrderDemo.java        # 访问顺序演示
│   │   └── LinkedHashMapLRUDemo.java                # LRU缓存演示
│   └── project/                                      # 实际项目Demo
│       ├── DatabaseQueryCache.java                  # 数据库查询缓存
│       └── ConfigurationManager.java                # 配置管理器
│
├── queue/                                            # Queue专题（ArrayDeque / PriorityQueue / BlockingQueue）
│   ├── demo/                                         # 演示代码
│   │   ├── ArrayDequeDemo.java                      # ArrayDeque基础使用演示（队列/栈/双端队列）
│   │   ├── PriorityQueueDemo.java                   # PriorityQueue演示（最小堆、最大堆、TopK）
│   │   ├── BlockingQueueDemo.java                   # BlockingQueue系列演示（ABQ/LBQ/PBQ/SQ）
│   │   └── ConcurrentLinkedQueueDemo.java           # 无锁并发队列演示
│   ├── docs/
│   │   ├── 01_Queue体系与设计思想.md
│   │   ├── 02_ArrayDeque原理与实现.md
│   │   ├── 03_PriorityQueue堆结构与应用.md
│   │   ├── 04_BlockingQueue并发设计与选型.md
│   │   └── 05_Queue最佳实践与实战场景.md
│   └── project/                                      # 实际项目Demo
│       ├── DelayTaskScheduler.java                  # 延迟任务调度器（DelayQueue实战）
│       └── ProducerConsumerPool.java                # 生产者-消费者线程池（ArrayBlockingQueue实战）
│
├── arraylist/                                        # ArrayList专题
│   ├── demo/
│   │   ├── ArrayListBasicDemo.java                  # 增删改查、遍历、subList
│   │   ├── ArrayListInternalsDemo.java              # 扩容机制、trimToSize、ensureCapacity
│   │   ├── ArrayListPerformanceDemo.java            # ArrayList vs LinkedList 性能对比
│   │   └── ArrayListConcurrentDemo.java             # 线程不安全演示、synchronizedList、CopyOnWriteArrayList
│   ├── docs/
│   │   ├── 01_ArrayList的必要性与应用场景.md
│   │   ├── 02_ArrayList核心原理与扩容机制.md
│   │   ├── 03_ArrayList性能分析与选型.md
│   │   ├── 04_ArrayList并发安全.md
│   │   └── 05_ArrayList最佳实践.md
│   └── project/
│       ├── StudentGradeManager.java                 # 学生成绩管理系统
│       └── EventBus.java                            # 简单事件总线（CopyOnWriteArrayList）
│
├── treemap/                                          # TreeMap专题
│   ├── demo/
│   │   ├── TreeMapBasicDemo.java                    # 自然排序、自定义Comparator、导航方法、范围视图
│   │   ├── TreeMapInternalsDemo.java                # 红黑树平衡性、key要求、性能对比
│   │   └── TreeMapApplicationDemo.java              # 词频统计、时间线、区间映射、排行榜
│   ├── docs/
│   │   ├── 01_TreeMap的必要性与应用场景.md
│   │   ├── 02_TreeMap核心原理与红黑树.md
│   │   ├── 03_TreeMap导航与范围查询.md
│   │   ├── 04_TreeMap_vs_HashMap_vs_LinkedHashMap.md
│   │   └── 05_TreeMap最佳实践.md
│   └── project/
│       ├── RangePriceIndex.java                     # 价格区间索引（floorKey区间映射）
│       └── ScheduleManager.java                     # 日程管理器（时间线+冲突检测）
│
├── concurrenthashmap/                               # ConcurrentHashMap专题
│   ├── demo/
│   │   ├── ConcurrentHashMapBasicDemo.java          # 基础CRUD、原子操作、批量操作
│   │   ├── ConcurrentHashMapInternalsDemo.java      # 并发安全验证、性能对比、size弱一致
│   │   └── ConcurrentHashMapPatternDemo.java        # 计数、分组统计、懒加载缓存、去重
│   ├── docs/
│   │   ├── 01_ConcurrentHashMap的必要性与应用场景.md
│   │   ├── 02_ConcurrentHashMap内部原理.md
│   │   ├── 03_ConcurrentHashMap原子操作详解.md
│   │   ├── 04_ConcurrentHashMap_vs_其他并发Map.md
│   │   └── 05_ConcurrentHashMap最佳实践.md
│   └── project/
│       ├── OnlineUserRegistry.java                  # 在线用户注册表
│       └── RequestRateLimiter.java                  # 请求限流器（ConcurrentHashMap + LongAdder）
│
├── set/                                              # Set专题（HashSet / LinkedHashSet / TreeSet）
│   ├── demo/
│   │   ├── SetBasicDemo.java                        # 三种Set对比、集合运算、contains性能
│   │   ├── SetInternalsDemo.java                    # hashCode/equals陷阱、TreeSet排序、可变对象陷阱
│   │   └── SetApplicationDemo.java                  # 去重、权限检查、差异分析、UV统计
│   ├── docs/
│   │   ├── 01_Set的必要性与三种实现.md
│   │   ├── 02_Set的工作原理与hashCode陷阱.md
│   │   ├── 03_Set的集合运算与实战应用.md
│   │   ├── 04_TreeSet导航与排序.md
│   │   └── 05_Set最佳实践.md
│   └── project/
│       ├── TagSystem.java                           # 标签系统（交集查询、Jaccard相似度）
│       └── AccessControlList.java                  # 访问控制列表（RBAC权限控制）
│
└── README.md                                         # 本文件
```

---

## 🎯 学习路径

### 第一部分：HashMap深度学习

HashMap是Java中最常用的集合类之一，理解其原理对于Java开发至关重要。

#### 阶段1：理解HashMap的必要性（第1章）

**核心问题**：

- ❓ 为什么需要HashMap？数组和链表有什么局限性？
- ❓ HashMap解决了什么核心问题？
- ❓ HashMap的演进历史是怎样的？（JDK 1.7 vs 1.8）
- ❓ HashMap在实际开发中有哪些典型应用场景？
- ❓ HashMap出现之前如何解决键值对存储问题？
- ❓ HashMap与HashTable、TreeMap的区别是什么？

**学习方式**：

1. 阅读 `hashmap/docs/01_HashMap的必要性与演进历史.md`
2. 理解数组、链表、哈希表的优缺点
3. 了解HashMap的演进历史
4. 学习典型应用场景

**关键收获**：

- ✅ 理解HashMap的必要性和核心价值
- ✅ 掌握HashMap的演进历史
- ✅ 了解HashMap的典型应用场景
- ✅ 知道何时使用HashMap

---

#### 阶段2：掌握HashMap核心原理（第2章）

**核心问题**：

- ❓ HashMap的底层数据结构是什么？
- ❓ 为什么采用"数组+链表+红黑树"的结构？
- ❓ Hash函数是如何设计的？为什么要扰动？
- ❓ 什么是Hash冲突？如何解决？
- ❓ 为什么链表长度超过8要转红黑树？
- ❓ 为什么红黑树节点少于6要退化为链表？
- ❓ 为什么HashMap的容量必须是2的幂次方？
- ❓ 负载因子0.75是如何确定的？

**学习方式**：

1. 阅读 `hashmap/docs/02_HashMap核心原理与数据结构.md`
2. 运行 `demo/HashMapBasicDemo.java`
3. 运行 `demo/HashMapCollisionDemo.java`
4. 理解数据结构关系图

**关键收获**：

- ✅ 理解HashMap的底层数据结构
- ✅ 掌握Hash函数的设计原理
- ✅ 理解链表转红黑树的阈值设计
- ✅ 掌握负载因子的作用

---

#### 阶段3：深入HashMap源码（第3章）

**核心问题**：

- ❓ put方法的完整流程是什么？
- ❓ get方法是如何快速定位元素的？
- ❓ resize扩容的完整流程是什么？
- ❓ 为什么扩容时容量要翻倍？
- ❓ JDK 1.8的扩容优化是什么？
- ❓ remove方法的实现细节是什么？
- ❓ 红黑树的插入和删除是如何实现的？

**学习方式**：

1. 阅读 `hashmap/docs/03_HashMap源码深度剖析.md`
2. 运行 `demo/HashMapResizeDemo.java`
3. 调试源码，理解关键流程
4. 分析时间复杂度

**关键收获**：

- ✅ 掌握put/get/remove的完整流程
- ✅ 理解resize扩容机制
- ✅ 理解JDK 1.8的优化
- ✅ 掌握红黑树的操作

---

#### 阶段4：掌握HashMap线程安全问题（第4章）

**核心问题**：

- ❓ HashMap为什么不是线程安全的？
- ❓ 并发场景下会出现什么问题？
- ❓ JDK 1.7的死循环问题是如何产生的？
- ❓ JDK 1.8解决了死循环问题吗？
- ❓ 如何让HashMap线程安全？
- ❓ HashTable、Collections.synchronizedMap、ConcurrentHashMap的区别？
- ❓ ConcurrentHashMap的实现原理是什么？

**学习方式**：

1. 阅读 `hashmap/docs/04_HashMap线程安全问题与解决方案.md`
2. 运行 `demo/HashMapThreadSafetyDemo.java`
3. 理解并发问题的根源
4. 学习线程安全的解决方案

**关键收获**：

- ✅ 理解HashMap的线程安全问题
- ✅ 掌握死循环问题的原理
- ✅ 了解各种线程安全方案
- ✅ 理解ConcurrentHashMap的优势

---

#### 阶段5：掌握HashMap最佳实践（第5章）

**核心问题**：

- ❓ 如何选择合适的初始容量？
- ❓ 如何选择合适的负载因子？
- ❓ HashMap的常见陷阱有哪些？
- ❓ 如何优化HashMap的性能？
- ❓ 自定义对象作为key需要注意什么？
- ❓ HashMap的内存占用如何计算？
- ❓ 如何避免Hash冲突？

**学习方式**：

1. 阅读 `hashmap/docs/05_HashMap最佳实践与性能优化.md`
2. 运行 `project/LRUCache.java`
3. 运行 `project/UserSessionManager.java`
4. 学习最佳实践清单

**关键收获**：

- ✅ 掌握HashMap的最佳实践
- ✅ 学会性能优化技巧
- ✅ 了解常见陷阱和解决方案
- ✅ 掌握实际项目应用

---

### 第二部分：LinkedList深度学习

LinkedList是基于双向链表实现的集合类，适合频繁插入删除的场景。

#### 阶段1：理解LinkedList的必要性（第1章）

**核心问题**：

- ❓ 为什么需要LinkedList？ArrayList有什么局限性？
- ❓ LinkedList解决了什么核心问题？
- ❓ LinkedList的典型应用场景有哪些？
- ❓ LinkedList与ArrayList的本质区别是什么？
- ❓ 什么时候应该使用LinkedList？

**学习方式**：

1. 阅读 `linkedlist/docs/01_LinkedList的必要性与应用场景.md`
2. 理解数组和链表的优缺点
3. 了解LinkedList的应用场景

**关键收获**：

- ✅ 理解LinkedList的必要性
- ✅ 掌握LinkedList的核心价值
- ✅ 了解典型应用场景
- ✅ 知道何时使用LinkedList

---

#### 阶段2：掌握LinkedList核心原理（第2章）

**核心问题**：

- ❓ LinkedList的底层数据结构是什么？
- ❓ 为什么采用双向链表而不是单向链表？
- ❓ Node节点的结构是怎样的？
- ❓ first和last指针的作用是什么？
- ❓ LinkedList如何实现快速访问头尾节点？
- ❓ LinkedList的内存结构是怎样的？

**学习方式**：

1. 阅读 `linkedlist/docs/02_LinkedList核心原理与数据结构.md`
2. 运行 `demo/LinkedListBasicDemo.java`
3. 理解双向链表结构
4. 分析内存布局

**关键收获**：

- ✅ 理解LinkedList的底层数据结构
- ✅ 掌握双向链表的优势
- ✅ 理解Node节点的设计
- ✅ 掌握first/last指针的作用

---

#### 阶段3：深入LinkedList源码（第3章）

**核心问题**：

- ❓ add方法的完整流程是什么？
- ❓ addFirst和addLast的实现细节？
- ❓ get方法是如何定位元素的？
- ❓ remove方法是如何删除节点的？
- ❓ LinkedList如何实现二分查找优化？
- ❓ 迭代器的实现原理是什么？

**学习方式**：

1. 阅读 `linkedlist/docs/03_LinkedList源码深度剖析.md`
2. 调试源码，理解关键流程
3. 分析时间复杂度

**关键收获**：

- ✅ 掌握add/get/remove的完整流程
- ✅ 理解二分查找优化
- ✅ 理解迭代器的实现
- ✅ 掌握各操作的时间复杂度

---

#### 阶段4：LinkedList与ArrayList对比（第4章）

**核心问题**：

- ❓ LinkedList和ArrayList的性能差异是什么？
- ❓ 什么场景下LinkedList更优？
- ❓ 什么场景下ArrayList更优？
- ❓ 如何选择合适的List实现？
- ❓ 内存占用的差异是什么？

**学习方式**：

1. 阅读 `linkedlist/docs/04_LinkedList与ArrayList对比分析.md`
2. 运行 `demo/LinkedListPerformanceDemo.java`
3. 理解性能差异
4. 学习选型指南

**关键收获**：

- ✅ 理解LinkedList和ArrayList的差异
- ✅ 掌握性能对比结果
- ✅ 学会正确选型
- ✅ 了解内存占用差异

---

#### 阶段5：LinkedList高级应用（第5章）

**核心问题**：

- ❓ LinkedList如何实现队列？
- ❓ LinkedList如何实现栈？
- ❓ LinkedList如何实现双端队列？
- ❓ LinkedList的最佳实践是什么？
- ❓ LinkedList的常见陷阱有哪些？

**学习方式**：

1. 阅读 `linkedlist/docs/05_LinkedList最佳实践与高级应用.md`
2. 运行 `demo/LinkedListAsQueueDemo.java`
3. 运行 `demo/LinkedListAsStackDemo.java`
4. 运行 `project/TaskQueue.java`
5. 运行 `project/BrowserHistoryManager.java`

**关键收获**：

- ✅ 掌握LinkedList作为队列的使用
- ✅ 掌握LinkedList作为栈的使用
- ✅ 理解双端队列的应用
- ✅ 掌握最佳实践

---

### 第三部分：LinkedHashMap深度学习

LinkedHashMap结合了HashMap和LinkedList的优点，保持插入顺序或访问顺序。

#### 阶段1：理解LinkedHashMap的必要性（第1章）

**核心问题**：

- ❓ 为什么需要LinkedHashMap？HashMap有什么局限性？
- ❓ LinkedHashMap解决了什么核心问题？
- ❓ LinkedHashMap的典型应用场景有哪些？
- ❓ LinkedHashMap与HashMap的区别是什么？
- ❓ 什么时候应该使用LinkedHashMap？

**学习方式**：

1. 阅读 `linkedhashmap/docs/01_LinkedHashMap的必要性与应用场景.md`
2. 理解HashMap的无序性问题
3. 了解LinkedHashMap的应用场景

**关键收获**：

- ✅ 理解LinkedHashMap的必要性
- ✅ 掌握LinkedHashMap的核心价值
- ✅ 了解典型应用场景
- ✅ 知道何时使用LinkedHashMap

---

#### 阶段2：掌握LinkedHashMap核心原理（第2章）

**核心问题**：

- ❓ LinkedHashMap的底层数据结构是什么？
- ❓ 如何在HashMap基础上维护顺序？
- ❓ Entry节点的结构是怎样的？
- ❓ before和after指针的作用是什么？
- ❓ 插入顺序和访问顺序的区别是什么？
- ❓ accessOrder参数的作用是什么？

**学习方式**：

1. 阅读 `linkedhashmap/docs/02_LinkedHashMap核心原理与数据结构.md`
2. 运行 `demo/LinkedHashMapBasicDemo.java`
3. 运行 `demo/LinkedHashMapOrderDemo.java`
4. 理解数据结构关系图

**关键收获**：

- ✅ 理解LinkedHashMap的底层数据结构
- ✅ 掌握双向链表的维护机制
- ✅ 理解插入顺序和访问顺序
- ✅ 掌握accessOrder的作用

---

#### 阶段3：深入LinkedHashMap源码（第3章）

**核心问题**：

- ❓ put方法如何维护顺序？
- ❓ get方法如何更新访问顺序？
- ❓ remove方法如何维护链表？
- ❓ afterNodeAccess方法的作用是什么？
- ❓ afterNodeInsertion方法的作用是什么？
- ❓ removeEldestEntry方法的作用是什么？

**学习方式**：

1. 阅读 `linkedhashmap/docs/03_LinkedHashMap源码深度剖析.md`
2. 运行 `demo/LinkedHashMapAccessOrderDemo.java`
3. 调试源码，理解关键流程

**关键收获**：

- ✅ 掌握put/get/remove的完整流程
- ✅ 理解顺序维护机制
- ✅ 理解回调方法的作用
- ✅ 掌握removeEldestEntry的用途

---

#### 阶段4：LinkedHashMap实现LRU缓存（第4章）

**核心问题**：

- ❓ 什么是LRU缓存？
- ❓ LRU缓存的核心原理是什么？
- ❓ 如何使用LinkedHashMap实现LRU缓存？
- ❓ removeEldestEntry如何实现自动淘汰？
- ❓ LRU缓存的性能如何？
- ❓ LRU缓存的线程安全问题如何解决？

**学习方式**：

1. 阅读 `linkedhashmap/docs/04_LinkedHashMap实现LRU缓存.md`
2. 运行 `demo/LinkedHashMapLRUDemo.java`
3. 理解LRU算法原理
4. 学习LRU缓存实现

**关键收获**：

- ✅ 理解LRU缓存原理
- ✅ 掌握LinkedHashMap实现LRU
- ✅ 理解removeEldestEntry的妙用
- ✅ 了解线程安全解决方案

---

#### 阶段5：LinkedHashMap最佳实践（第5章）

**核心问题**：

- ❓ LinkedHashMap的最佳实践是什么？
- ❓ LinkedHashMap的性能如何？
- ❓ LinkedHashMap与HashMap的性能差异？
- ❓ LinkedHashMap的内存占用如何？
- ❓ LinkedHashMap的常见陷阱有哪些？

**学习方式**：

1. 阅读 `linkedhashmap/docs/05_LinkedHashMap最佳实践与性能分析.md`
2. 运行 `project/DatabaseQueryCache.java`
3. 运行 `project/ConfigurationManager.java`
4. 学习最佳实践清单

**关键收获**：

- ✅ 掌握LinkedHashMap的最佳实践
- ✅ 理解性能特点
- ✅ 了解常见陷阱和解决方案
- ✅ 掌握实际项目应用

---

## 🚀 快速开始

### 1. HashMap基础演示

```bash
# 编译
javac -d target/classes src/main/java/com/fragment/core/collections/hashmap/demo/HashMapBasicDemo.java

# 运行
java -cp target/classes com.fragment.core.collections.hashmap.demo.HashMapBasicDemo
```

**演示内容**：
- HashMap的基本使用
- put/get/remove操作
- 遍历方式
- 常用方法

---

### 2. LinkedList基础演示

```bash
# 编译
javac -d target/classes src/main/java/com/fragment/core/collections/linkedlist/demo/LinkedListBasicDemo.java

# 运行
java -cp target/classes com.fragment.core.collections.linkedlist.demo.LinkedListBasicDemo
```

**演示内容**：
- LinkedList的基本使用
- 头尾操作
- 作为队列使用
- 作为栈使用

---

### 3. LinkedHashMap基础演示

```bash
# 编译
javac -d target/classes src/main/java/com/fragment/core/collections/linkedhashmap/demo/LinkedHashMapBasicDemo.java

# 运行
java -cp target/classes com.fragment.core.collections.linkedhashmap.demo.LinkedHashMapBasicDemo
```

**演示内容**：
- LinkedHashMap的基本使用
- 插入顺序演示
- 访问顺序演示
- LRU缓存实现

---

### 4. Queue 演示

```bash
# ArrayDeque 演示（队列/栈/双端队列）
java -cp target/classes com.fragment.core.collections.queue.demo.ArrayDequeDemo

# PriorityQueue 演示（最小堆/最大堆/TopK）
java -cp target/classes com.fragment.core.collections.queue.demo.PriorityQueueDemo

# BlockingQueue 系列演示
java -cp target/classes com.fragment.core.collections.queue.demo.BlockingQueueDemo

# ConcurrentLinkedQueue 演示
java -cp target/classes com.fragment.core.collections.queue.demo.ConcurrentLinkedQueueDemo

# DelayQueue 延迟任务调度器
java -cp target/classes com.fragment.core.collections.queue.project.DelayTaskScheduler

# 生产者-消费者线程池
java -cp target/classes com.fragment.core.collections.queue.project.ProducerConsumerPool
```

---

## 💡 核心知识点

### 1. HashMap的核心设计

```
HashMap = 数组 + 链表 + 红黑树

数组：快速定位（O(1)）
链表：解决Hash冲突
红黑树：优化链表过长的情况（链表长度>8时转红黑树）

Hash函数：
1. 计算key的hashCode
2. 高16位与低16位异或（扰动函数）
3. 与数组长度-1进行与运算（定位索引）
```

---

### 2. LinkedList的核心设计

```
LinkedList = 双向链表

Node节点结构：
- item：存储数据
- next：指向下一个节点
- prev：指向上一个节点

优势：
- 插入删除O(1)（已知位置）
- 无需扩容

劣势：
- 随机访问O(n)
- 内存占用大（额外存储指针）
```

---

### 3. LinkedHashMap的核心设计

```
LinkedHashMap = HashMap + 双向链表

Entry节点结构：
- 继承HashMap.Node
- before：指向前一个节点
- after：指向后一个节点

维护两种顺序：
1. 插入顺序（默认）
2. 访问顺序（accessOrder=true）

应用：
- LRU缓存
- 保持插入顺序
```

---

### 4. Queue 体系的核心设计

```
Java Queue 体系：

Queue（接口）
├── Deque（双端队列接口）
│   ├── ArrayDeque   ← 推荐：数组实现，无锁，作为栈/队列性能优于 LinkedList
│   └── LinkedList   ← 链表实现，有额外内存开销
│
├── PriorityQueue    ← 最小堆，poll() 始终返回最小元素，O(log n) 入队/出队
│
└── BlockingQueue（阻塞队列接口）
    ├── ArrayBlockingQueue    ← 有界，单锁，适合限流场景
    ├── LinkedBlockingQueue   ← 可选有界，双锁（读写分离），高吞吐
    ├── PriorityBlockingQueue ← 无界，优先级阻塞队列
    ├── SynchronousQueue      ← 零容量，线程间直接握手传递
    └── DelayQueue            ← 无界，延迟到期才可出队

ConcurrentLinkedQueue  ← 无锁 CAS，非阻塞，高并发读写

选型速查：
- 单线程 栈/队列   → ArrayDeque
- 优先级处理        → PriorityQueue
- 生产者-消费者     → LinkedBlockingQueue（高吞吐）/ ArrayBlockingQueue（严格限流）
- 延迟任务          → DelayQueue
- 线程间直接传递    → SynchronousQueue
- 高并发非阻塞      → ConcurrentLinkedQueue
```

---

## ⚠️ 常见陷阱

### 1. HashMap的线程安全问题

```java
// ❌ 错误：多线程并发修改HashMap
Map<String, String> map = new HashMap<>();
// 多个线程同时put，可能导致数据丢失、死循环等问题

// ✅ 正确：使用ConcurrentHashMap
Map<String, String> map = new ConcurrentHashMap<>();
```

---

### 2. LinkedList的随机访问性能问题

```java
// ❌ 错误：频繁使用get(index)
LinkedList<String> list = new LinkedList<>();
for (int i = 0; i < list.size(); i++) {
    String item = list.get(i); // O(n)，总复杂度O(n²)
}

// ✅ 正确：使用迭代器
for (String item : list) { // O(n)
    // 处理item
}
```

---

### 3. LinkedHashMap的内存占用问题

```java
// ❌ 错误：无限制添加元素
LinkedHashMap<String, Object> cache = new LinkedHashMap<>();
while (true) {
    cache.put(key, value); // 可能导致内存溢出
}

// ✅ 正确：实现LRU缓存，自动淘汰
LinkedHashMap<String, Object> cache = new LinkedHashMap<String, Object>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
        return size() > MAX_ENTRIES; // 超过容量自动删除最老的元素
    }
};
```

---

### 4. Queue 的常见陷阱

```java
// ❌ 陷阱1：用 Stack 类做栈（继承自 Vector，所有方法加 synchronized，单线程浪费）
Stack<String> stack = new Stack<>();

// ✅ 正确：用 ArrayDeque
Deque<String> stack = new ArrayDeque<>();

// ❌ 陷阱2：LinkedBlockingQueue 不指定容量（近似无界，队列堆积打爆内存）
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(); // 默认 Integer.MAX_VALUE

// ✅ 正确：指定合理容量，超出时阻塞生产者（反压）
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(1000);

// ❌ 陷阱3：PriorityQueue 的 forEach/迭代器不保证顺序
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(3); pq.offer(1); pq.offer(2);
pq.forEach(System.out::println); // 输出顺序不保证是 1,2,3！

// ✅ 正确：要有序输出必须用 poll()
while (!pq.isEmpty()) {
    System.out.println(pq.poll()); // 1, 2, 3
}

// ❌ 陷阱4：高并发下频繁调用 ConcurrentLinkedQueue.size()（O(n) 操作）
if (queue.size() == 0) { ... } // 性能差

// ✅ 正确：用 isEmpty()
if (queue.isEmpty()) { ... } // O(1)
```

---

## 📊 性能对比

### HashMap vs TreeMap vs LinkedHashMap

| 特性 | HashMap | TreeMap | LinkedHashMap |
|------|---------|---------|---------------|
| **底层结构** | 数组+链表+红黑树 | 红黑树 | HashMap+双向链表 |
| **是否有序** | 无序 | 按key排序 | 按插入/访问顺序 |
| **put性能** | O(1) | O(log n) | O(1) |
| **get性能** | O(1) | O(log n) | O(1) |
| **内存占用** | 中 | 中 | 高 |
| **线程安全** | 否 | 否 | 否 |
| **null key** | 允许1个 | 不允许 | 允许1个 |

---

### ArrayList vs LinkedList

| 操作 | ArrayList | LinkedList |
|------|-----------|------------|
| **随机访问** | O(1) | O(n) |
| **头部插入** | O(n) | O(1) |
| **尾部插入** | O(1) | O(1) |
| **中间插入** | O(n) | O(n) |
| **头部删除** | O(n) | O(1) |
| **尾部删除** | O(1) | O(1) |
| **内存占用** | 低 | 高 |
| **缓存友好** | 是 | 否 |

---

### Queue 系列对比

| 实现类 | 有界 | 阻塞 | 线程安全 | 有序 | 允许null | 适用场景 |
|--------|------|------|---------|------|---------|--------|
| **ArrayDeque** | 否（自动扩容） | 否 | 否 | FIFO/LIFO | ❌ | 单线程栈/队列 |
| **PriorityQueue** | 否 | 否 | 否 | 优先级 | ❌ | 单线程优先级处理 |
| **ArrayBlockingQueue** | ✅ 固定 | ✅ | ✅ | FIFO | ❌ | 严格限流 |
| **LinkedBlockingQueue** | 可选 | ✅ | ✅ | FIFO | ❌ | 高吞吐生产消费 |
| **PriorityBlockingQueue** | 否 | ✅(take) | ✅ | 优先级 | ❌ | 多线程优先级 |
| **SynchronousQueue** | 0（无容量） | ✅ | ✅ | - | ❌ | 线程间直接传递 |
| **DelayQueue** | 否 | ✅(take) | ✅ | 到期时间 | ❌ | 延迟/定时任务 |
| **ConcurrentLinkedQueue** | 否 | 否 | ✅（CAS） | FIFO | ❌ | 高并发非阻塞 |

---

## 📖 参考资料

### 官方文档

- [Java HashMap API](https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html)
- [Java LinkedList API](https://docs.oracle.com/javase/8/docs/api/java/util/LinkedList.html)
- [Java LinkedHashMap API](https://docs.oracle.com/javase/8/docs/api/java/util/LinkedHashMap.html)

### 推荐书籍

- 《Java核心技术 卷I》
- 《Effective Java》
- 《Java并发编程实战》
- 《算法导论》

### 推荐文章

- [HashMap源码分析](https://tech.meituan.com/2016/06/24/java-hashmap.html)
- [ConcurrentHashMap源码分析](https://tech.meituan.com/2018/07/13/java-concurrenthashmap.html)
- [Java BlockingQueue API](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/BlockingQueue.html)
- [Java ArrayDeque API](https://docs.oracle.com/javase/8/docs/api/java/util/ArrayDeque.html)

---

## 🤝 贡献

欢迎提交Issue和Pull Request！

---

## 📝 总结

通过本系列的学习，你应该掌握：

### HashMap

1. ✅ **必要性**：理解HashMap解决的核心问题
2. ✅ **核心原理**：掌握数组+链表+红黑树的设计
3. ✅ **源码分析**：理解put/get/resize的实现
4. ✅ **线程安全**：了解并发问题和解决方案
5. ✅ **最佳实践**：掌握正确的使用方式

### LinkedList

1. ✅ **必要性**：理解LinkedList的应用场景
2. ✅ **核心原理**：掌握双向链表的设计
3. ✅ **源码分析**：理解add/get/remove的实现
4. ✅ **性能对比**：了解与ArrayList的差异
5. ✅ **高级应用**：掌握队列、栈的实现

### LinkedHashMap

1. ✅ **必要性**：理解LinkedHashMap的价值
2. ✅ **核心原理**：掌握HashMap+双向链表的设计
3. ✅ **源码分析**：理解顺序维护机制
4. ✅ **LRU缓存**：掌握LRU缓存的实现
5. ✅ **最佳实践**：掌握正确的使用方式

**核心收获**：

- 🎯 理解Java集合框架的设计思想
- 🔍 掌握核心数据结构的实现原理
- 💡 知道如何正确选择和使用集合类
- 📚 学会分析和解决性能问题
- ✨ 了解实际项目中的应用场景

### Queue

1. ✅ **必要性**：理解 Queue/Deque/BlockingQueue 各自解决的问题
2. ✅ **ArrayDeque**：掌握数组循环双端队列，替代 Stack 和 LinkedList 的推荐方案
3. ✅ **PriorityQueue**：理解最小堆原理，掌握 TopK 等面试经典题
4. ✅ **BlockingQueue 系列**：掌握 ABQ/LBQ/PBQ/SQ 的区别与选型
5. ✅ **实战应用**：延迟任务调度、生产者-消费者模式

**继续学习**：

- 深入学习ConcurrentHashMap
- 研究TreeMap和红黑树
- 学习ArrayList和Vector
- 研究Java 8的Stream API

---

**Happy Coding! 🚀**
