# 第三章：Arthas 实战

阿里巴巴开源的 Java 在线诊断工具，无需修改代码、无需重启应用，直接 attach 到运行中的 JVM 进行实时诊断。

```
Arthas 解决的核心问题：
  - 这个类从哪个 jar 包加载的？为什么 ClassNotFoundException？
  - 我改的代码为什么没生效？线上跑的到底是哪个版本？
  - 方法执行很慢，慢在哪一步？
  - 方法被谁调用了？入参是什么？返回了什么？
  - 线上问题无法 debug，怎么办？
```

---

## 3.1 安装与启动

```bash
# 方式1：在线安装（推荐）
curl -O https://arthas.aliyun.com/arthas-boot.jar
java -jar arthas-boot.jar

# 方式2：全量安装
wget https://github.com/alibaba/arthas/releases/download/arthas-all-3.6.7/arthas-bin.zip
unzip arthas-bin.zip && cd arthas-bin && ./as.sh

# 方式3：Spring Boot Starter（内嵌）
# <dependency>
#     <groupId>com.taobao.arthas</groupId>
#     <artifactId>arthas-spring-boot-starter</artifactId>
#     <version>3.6.7</version>
# </dependency>
```

```bash
# 启动后选择进程
$ java -jar arthas-boot.jar
[INFO] Found existing java process:
* [1]: 12345 com.example.Application
  [2]: 67890 org.apache.catalina.startup.Bootstrap
1    # 输入序号

# 退出 / 停止
quit    # 退出客户端（agent 仍在）
stop    # 完全停止 Arthas
reset   # 重置所有增强的类（重要！用完必做）
```

---

## 3.2 工作原理

```
用户 → Arthas 客户端 → JVM Attach API → 加载 Agent → 字节码增强 → 诊断

核心技术栈：
  1. Attach API    — 动态 attach 到目标进程
  2. Instrumentation — 字节码增强（ASM）
  3. JVMTI         — 获取 JVM 内部信息
```

---

## 3.3 10 个核心命令速查

### dashboard — 全局概览

```bash
dashboard   # 5 秒刷新一次

# 上半部分：线程列表，按 CPU 排序
# 下半部分：JVM 内存区使用率 + GC 统计
# 一个屏幕同时看到：哪个线程烧 CPU、Old 区是否逼近上限、Full GC 频率
```

### thread — 线程分析

```bash
thread          # 所有线程
thread -n 3     # CPU 最高的 3 个线程（含完整堆栈）
thread -b       # 找到正在阻塞其他线程的持锁线程
thread <ID>     # 查看指定线程堆栈
thread --state WAITING  # 按状态过滤
```

> `thread -n 3` = `top -Hp` + 十六进制换算 + `jstack | grep` 三步合一。

### watch — 观察方法入参/返回值/异常

```bash
# 同时看入参、返回值、异常（展开 3 层嵌套）
watch com.example.service.OrderService createOrder '{params, returnObj, throwExp}' -x 3

# 只看耗时 > 500ms 的调用
watch com.example.service.OrderService createOrder '{params, returnObj}' '#cost > 500'

# 只在抛异常时触发
watch com.example.service.OrderService createOrder 'throwExp' -e
```

### trace — 定位方法内哪一步最慢

```bash
trace com.example.service.OrderService createOrder

# 输出调用树 + 每层耗时：
# `---[2031ms] OrderService:createOrder()
#     +---[0.3ms]  UserMapper:selectById()
#     +---[2028ms] NotifyClient:send()        ← 瓶颈在这里
#     `---[0.04ms] OrderMapper:insert()

# 展开 JDK 内部调用
trace com.example.service.OrderService createOrder --skipJDKMethod false
```

### stack — 反向追踪调用来源

```bash
stack com.example.service.OrderService createOrder

# 输出从当前方法到线程入口的完整调用链
# 某个方法被意外调用、调用次数异常 → stack 直接告诉你是谁发起的
```

### monitor — 统计成功率/平均耗时

```bash
monitor -c 10 com.example.service.OrderService createOrder

# 每 10 秒输出一次统计：
# timestamp    class          method       total  success  fail  avg-rt(ms)  fail-rate
# 14:23:11     OrderService   createOrder  127    124      3     48.3        2.36%

# fail-rate 不为零 → 有异常在被吞掉
# avg-rt 偏高但某次 trace 很快 → 偶发慢请求
```

### jad — 反编译运行中的类

```bash
jad com.example.service.OrderService              # 反编译整个类
jad com.example.service.OrderService createOrder   # 只看指定方法

# 确认线上跑的是哪个版本代码
# 排查：代码跟本地不一样、热更新没更新干净、多版本 jar 冲突
```

### ognl / vmtool — 执行表达式

```bash
# 读取静态变量
ognl '@com.example.config.AppConfig@maxRetry'

# 调用静态方法
ognl '@java.lang.System@currentTimeMillis()'

# 通过 vmtool 获取 Spring Bean（推荐，避免 ContextLoader 返回 null）
vmtool --action getInstances \
  --className org.springframework.context.ApplicationContext \
  --express 'instances[0].getBean("orderService").getMaxConcurrent()'
```

### logger — 动态修改日志级别

```bash
logger --name com.example --level DEBUG   # 临时开 DEBUG
logger --name com.example --level INFO    # 排查完改回来
logger                                     # 查看所有 logger 级别
```

### heapdump — 生成堆 dump

```bash
heapdump /tmp/heap.hprof              # 完整堆 dump
heapdump --live /tmp/heap-live.hprof  # 只 dump 存活对象（文件更小，推荐先用）
```

---

## 3.4 类相关命令

```bash
# sc (Search Class) — 查找类
sc com.example.*                      # 模糊搜索
sc -d com.example.UserService         # 详情：jar 路径、ClassLoader、注解

# sm (Search Method) — 查找方法
sm com.example.UserService            # 列出所有方法
sm -d com.example.UserService getUser # 方法详情：参数、返回值、注解

# classloader — 查看类加载器
classloader                           # 列出所有 ClassLoader
classloader -c <hash>                 # 查看指定 ClassLoader 加载的路径
```

---

## 3.5 高级功能

### 热更新（redefine）

```bash
redefine /tmp/UserService.class       # 热更新类

# 限制：
#   ✅ 可以修改方法体
#   ❌ 不能添加/删除字段或方法
#   ❌ 不能修改类继承关系
```

### 火焰图（profiler）

```bash
profiler start                        # 开始 CPU 采样
profiler start --event alloc          # 内存分配采样
profiler getSamples                   # 查看采样数
profiler stop --format html           # 停止并生成火焰图

# 输出文件：/tmp/arthas-output/20240101-100000.html
# 浏览器打开 → 横轴=调用栈宽度（占比），纵轴=调用深度
```

### 时空隧道（tt — Time Tunnel）

```bash
tt -t com.example.service.OrderService createOrder  # 录制方法调用
tt -l                                                # 查看录制列表
tt -i 1000                                           # 查看第 1000 次调用详情
tt -i 1000 -p                                        # 重放第 1000 次调用
```

---

## 3.6 实战案例

### 案例1：CPU 飙高

```bash
# 1. 查看最忙线程
thread -n 3
# → "http-nio-8080-exec-10" RUNNABLE
#     at com.example.UserService.calculateScore(UserService.java:156)

# 2. 反编译确认代码
jad com.example.UserService calculateScore
# → 发现死循环

# 3. 修复代码并热更新
redefine /tmp/UserService.class

# 4. 验证
thread -n 3
```

### 案例2：接口响应慢

```bash
# 1. 监控方法耗时
monitor -c 5 com.example.UserController getUser
# → 平均 5234ms

# 2. 追踪调用链
trace com.example.UserController getUser '#cost > 1000'
# → NotifyClient.send() 耗时 5230ms

# 3. 观察慢方法的参数
watch com.example.CacheService get "{params,returnObj}" -x 2
# → 发现缓存 key 过长，网络传输慢
```

### 案例3：内存泄漏

```bash
# 1. 全局概览
dashboard
# → Old 区持续增长

# 2. 观察对象创建
watch com.example.CacheManager put "{params}" -x 2
# → 缓存一直添加，从不清理

# 3. 查看缓存大小
ognl '@com.example.CacheManager@getInstance().size()'
# → 1234567（过大）

# 4. heapdump 下载分析
heapdump --live /tmp/heap.hprof
```

---

## 3.7 生产环境安全使用

```
安全原则：
  1. 权限控制：限制访问 IP、设置密码、使用 tunnel server
  2. 性能意识：watch/trace 有开销，避免高峰期 + 设条件过滤
  3. 用完即走：诊断完 → reset → quit/stop
  4. 记录操作：保留操作日志，团队可追溯
```

```bash
# 安全启动
java -jar arthas-boot.jar \
  --tunnel-server 'ws://tunnel-server:7777/ws' \
  --agent-id 'app-server-1' \
  --username admin \
  --password 'your-password'
```

---

## 3.8 10 个命令速查表

```
命令          核心用途                    典型场景
dashboard     全局概览（线程/内存/GC）     出事第一眼
thread -n 3   CPU 最高线程堆栈            CPU 飙高
thread -b     找持锁阻塞线程              死锁
watch         观察入参/返回值/异常         返回错误数据、异常被吞
trace         方法调用树 + 耗时            接口慢定位
stack         反向追调用来源               方法被意外调用
monitor       统计成功率/平均耗时          偶发错误、偶发慢
jad           反编译运行中的类             确认线上代码版本
ognl/vmtool   执行表达式/读 Bean 属性     配置验证、缓存查看
logger        动态修改日志级别             临时开 DEBUG
```

---

## 3.9 本章总结

- Arthas 的核心价值：**不改代码、不重启、实时诊断**
- `dashboard` 先扫全局 → `thread/trace/watch` 定位具体问题
- 生产环境：注意性能开销，用完 `reset` + `quit`
- 与 JDK 命令行工具互补：Arthas 强在方法级诊断，jstat/jmap 强在 GC/内存全局视角

**继续阅读**：[04_Debug断点实现原理.md](./04_Debug断点实现原理.md)
