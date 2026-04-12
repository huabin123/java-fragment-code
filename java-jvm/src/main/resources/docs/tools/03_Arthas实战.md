# Arthas实战

## 📚 概述

Arthas是阿里巴巴开源的Java诊断工具，无需修改代码即可实时查看应用运行状态、诊断问题。本文深入讲解Arthas的核心功能和实战应用。

## 🎯 核心问题

- ❓ Arthas是什么？解决什么问题？
- ❓ Arthas的工作原理是什么？
- ❓ 如何使用Arthas诊断线上问题？
- ❓ Arthas有哪些核心命令？
- ❓ 如何在生产环境安全使用Arthas？
- ❓ Arthas与其他工具的区别？

---

## 一、Arthas简介

### 1.1 什么是Arthas

```
Arthas（阿尔萨斯）：
阿里巴巴开源的Java诊断工具

核心特点：
1. 无需修改代码
2. 无需重启应用
3. 实时诊断
4. 功能强大
5. 易于使用

解决的问题：
- 这个类从哪个jar包加载的？
- 为什么会出现ClassNotFoundException？
- 我改的代码为什么没有执行？
- 方法执行很慢，慢在哪里？
- 这个方法被谁调用了？
- 线上遇到问题无法debug怎么办？
```

### 1.2 工作原理

```
Arthas工作原理：

1. Attach机制
   - 使用JVM Attach API
   - 动态attach到目标进程
   - 加载agent

2. Instrumentation
   - 使用Java Instrumentation
   - 字节码增强
   - 动态修改类

3. JVMTI
   - JVM Tool Interface
   - 获取JVM内部信息
   - 监控JVM状态

工作流程：
用户 → Arthas客户端 → Attach → 目标JVM → Agent → 字节码增强 → 执行诊断
```

---

## 二、安装与启动

### 2.1 快速安装

```bash
# 方式1：在线安装（推荐）
curl -O https://arthas.aliyun.com/arthas-boot.jar
java -jar arthas-boot.jar

# 方式2：全量安装
wget https://github.com/alibaba/arthas/releases/download/arthas-all-3.6.7/arthas-bin.zip
unzip arthas-bin.zip
cd arthas-bin
./as.sh

# 方式3：Spring Boot Starter
<dependency>
    <groupId>com.taobao.arthas</groupId>
    <artifactId>arthas-spring-boot-starter</artifactId>
    <version>3.6.7</version>
</dependency>
```

### 2.2 启动流程

```bash
# 1. 启动Arthas
$ java -jar arthas-boot.jar

# 2. 选择进程
[INFO] arthas-boot version: 3.6.7
[INFO] Found existing java process, please choose one and input the serial number.
* [1]: 12345 com.example.Application
  [2]: 67890 org.apache.catalina.startup.Bootstrap

# 3. 输入进程编号
1

# 4. 连接成功
[INFO] Attach process 12345 success.
[INFO] arthas-client connect 127.0.0.1 3658
  ,---.  ,------. ,--------.,--.  ,--.  ,---.   ,---.
 /  O  \ |  .--. ''--.  .--'|  '--'  | /  O  \ '   .-'
|  .-.  ||  '--'.'   |  |   |  .--.  ||  .-.  |`.  `-.
|  | |  ||  |\  \    |  |   |  |  |  ||  | |  |.-'    |
`--' `--'`--' '--'   `--'   `--'  `--'`--' `--'`-----'

wiki      https://arthas.aliyun.com/doc
tutorials https://arthas.aliyun.com/doc/arthas-tutorials.html
version   3.6.7
pid       12345
time      2024-01-01 10:00:00

[arthas@12345]$
```

### 2.3 退出与停止

```bash
# 退出当前客户端
quit

# 完全停止Arthas
stop

# 重置所有增强的类
reset
```

---

## 三、基础命令

### 3.1 信息查看命令

#### dashboard

```bash
# 实时查看系统面板
dashboard

输出示例：
ID   NAME                   GROUP          PRIORITY  STATE    %CPU     DELTA_TIME TIME     
1    main                   main           5         RUNNABLE 0.0      0.000      0:0.234
2    Reference Handler      system         10        WAITING  0.0      0.000      0:0.000
3    Finalizer              system         8         WAITING  0.0      0.000      0:0.000

Memory                    used     total    max      usage    GC
heap                      128M     256M     1024M    12.50%   gc.ps_scavenge.count  10
ps_eden_space            64M      128M     512M     12.50%   gc.ps_scavenge.time   100ms
ps_survivor_space        8M       16M      64M      12.50%   gc.ps_marksweep.count 2
ps_old_gen               56M      112M     448M     12.50%   gc.ps_marksweep.time  200ms

Runtime
os.name                  Linux
os.version               3.10.0
java.version             1.8.0_191
java.home                /usr/java/jdk1.8.0_191
```

#### thread

```bash
# 查看所有线程
thread

# 查看最忙的3个线程
thread -n 3

# 查看指定线程
thread 1

# 查看线程栈
thread 1 | grep 'com.example'

# 查看死锁
thread -b

# 统计线程
thread --state WAITING
```

#### jvm

```bash
# 查看JVM信息
jvm

输出：
RUNTIME
MACHINE-NAME                    localhost
JVM-START-TIME                  2024-01-01 09:00:00
MANAGEMENT-SPEC-VERSION         1.2
SPEC-NAME                       Java Virtual Machine Specification
SPEC-VENDOR                     Oracle Corporation
SPEC-VERSION                    1.8
VM-NAME                         Java HotSpot(TM) 64-Bit Server VM
VM-VENDOR                       Oracle Corporation
VM-VERSION                      25.191-b12

CLASS-LOADING
LOADED-CLASS-COUNT              5678
TOTAL-LOADED-CLASS-COUNT        5678
UNLOADED-CLASS-COUNT            0

MEMORY
HEAP-MEMORY-USAGE               128M/256M/1024M
NO-HEAP-MEMORY-USAGE            64M/128M/256M
```

---

## 四、核心诊断命令

### 4.1 类相关命令

#### sc (Search Class)

```bash
# 查找类
sc com.example.*

# 查看类详情
sc -d com.example.UserService

输出：
class-info        com.example.UserService
code-source       /app/lib/user-service.jar
name              com.example.UserService
isInterface       false
isAnnotation      false
isEnum            false
isAnonymousClass  false
isArray           false
isLocalClass      false
isMemberClass     false
isPrimitive       false
isSynthetic       false
simple-name       UserService
modifier          public
annotation
interfaces
super-class       java.lang.Object
class-loader      sun.misc.Launcher$AppClassLoader@18b4aac2
classLoaderHash   18b4aac2

# 查看类的字段
sc -d -f com.example.UserService
```

#### sm (Search Method)

```bash
# 查找方法
sm com.example.UserService

# 查看方法详情
sm -d com.example.UserService getUser

输出：
declaring-class  com.example.UserService
method-name      getUser
modifier         public
annotation       @org.springframework.web.bind.annotation.GetMapping
parameters       java.lang.Long
return           com.example.User
exceptions
classLoaderHash  18b4aac2
```

#### jad

```bash
# 反编译类
jad com.example.UserService

# 反编译指定方法
jad com.example.UserService getUser

输出：
ClassLoader:
sun.misc.Launcher$AppClassLoader@18b4aac2

Location:
/app/lib/user-service.jar

public User getUser(Long id) {
    User user = this.userRepository.findById(id);
    if (user == null) {
        throw new UserNotFoundException("User not found: " + id);
    }
    return user;
}
```

### 4.2 方法监控命令

#### watch

```bash
# 观察方法调用
watch com.example.UserService getUser "{params,returnObj,throwExp}" -x 2

# 观察方法入参
watch com.example.UserService getUser "{params}" -x 2

# 观察方法返回值
watch com.example.UserService getUser "{returnObj}" -x 2

# 观察异常
watch com.example.UserService getUser "{throwExp}" -e -x 2

# 条件过滤
watch com.example.UserService getUser "{params,returnObj}" "params[0] > 100" -x 2

输出示例：
method=com.example.UserService.getUser location=AtExit
ts=2024-01-01 10:00:00; [cost=15.234ms] result=@ArrayList[
    @Object[][
        @Long[123],
    ],
    @User[
        id=@Long[123],
        name=@String[张三],
        age=@Integer[25],
    ],
]
```

#### trace

```bash
# 追踪方法调用路径
trace com.example.UserService getUser

输出：
`---ts=2024-01-01 10:00:00;thread_name=http-nio-8080-exec-1;id=1f;is_daemon=true;priority=5;TCCL=org.apache.catalina.loader.ParallelWebappClassLoader
    `---[15.234ms] com.example.UserService:getUser()
        +---[0.123ms] com.example.UserRepository:findById() #12
        +---[0.045ms] com.example.User:<init>() #13
        `---[14.956ms] com.example.CacheService:put() #15
            `---[14.890ms] redis.clients.jedis.Jedis:set() #23
```

#### monitor

```bash
# 监控方法调用统计
monitor -c 5 com.example.UserService getUser

输出：
Timestamp            Class                    Method    Total  Success  Fail  Avg RT(ms)  Fail Rate
2024-01-01 10:00:05  com.example.UserService  getUser   100    98       2     15.234      2.00%
2024-01-01 10:00:10  com.example.UserService  getUser   120    118      2     16.123      1.67%
```

#### stack

```bash
# 查看方法调用堆栈
stack com.example.UserService getUser

输出：
ts=2024-01-01 10:00:00;thread_name=http-nio-8080-exec-1;id=1f;is_daemon=true;priority=5;TCCL=org.apache.catalina.loader.ParallelWebappClassLoader
    @com.example.UserController.getUser()
        at com.example.UserService.getUser(UserService.java:25)
        at com.example.UserController.getUser(UserController.java:35)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
```

#### tt (Time Tunnel)

```bash
# 记录方法调用
tt -t com.example.UserService getUser

# 查看记录
tt -l

# 重放调用
tt -i 1000 -p

# 查看调用详情
tt -i 1000

输出：
INDEX   TIMESTAMP            COST(ms)  IS-RET  IS-EXP   OBJECT         CLASS                    METHOD
1000    2024-01-01 10:00:00  15.234    true    false    0x7f8a4c001000 UserService              getUser
1001    2024-01-01 10:00:01  16.123    true    false    0x7f8a4c001000 UserService              getUser
```

---

## 五、高级功能

### 5.1 热更新

#### redefine

```bash
# 热更新类
redefine /tmp/UserService.class

# 批量热更新
redefine /tmp/*.class

注意事项：
1. 不能添加/删除字段
2. 不能添加/删除方法
3. 只能修改方法体
4. 需要编译后的class文件
```

### 5.2 性能分析

#### profiler

```bash
# 开始采样
profiler start

# 查看采样状态
profiler getSamples

# 停止采样并生成火焰图
profiler stop --format html

# 指定采样事件
profiler start --event cpu
profiler start --event alloc

# 生成的文件
/tmp/arthas-output/20240101-100000.html
```

### 5.3 日志管理

#### logger

```bash
# 查看日志配置
logger

# 查看指定logger
logger -n com.example.UserService

# 修改日志级别
logger --name com.example.UserService --level debug

# 查看日志文件
logger -c 18b4aac2
```

---

## 六、实战案例

### 6.1 案例1：CPU飙高排查

```bash
# 问题：CPU使用率突然飙升到100%

# 步骤1：查看最忙的线程
thread -n 3

# 输出：
"http-nio-8080-exec-10" Id=123 RUNNABLE
    at com.example.UserService.calculateScore(UserService.java:156)
    at com.example.UserService.getUser(UserService.java:45)

# 步骤2：反编译方法
jad com.example.UserService calculateScore

# 发现问题：死循环
public int calculateScore() {
    int score = 0;
    while (true) {  // 死循环！
        score++;
    }
}

# 步骤3：追踪方法调用
trace com.example.UserService calculateScore

# 步骤4：修复代码并热更新
redefine /tmp/UserService.class

# 步骤5：验证
thread -n 3
```

### 6.2 案例2：接口响应慢排查

```bash
# 问题：/api/user接口响应时间从50ms增加到5000ms

# 步骤1：监控方法耗时
monitor -c 5 com.example.UserController getUser

# 输出：平均耗时5234ms

# 步骤2：追踪方法调用链
trace com.example.UserController getUser '#cost > 1000'

# 输出：
`---[5234ms] com.example.UserController:getUser()
    +---[0.5ms] com.example.UserService:getUser()
    `---[5230ms] com.example.CacheService:get()
        `---[5228ms] redis.clients.jedis.Jedis:get()

# 发现问题：Redis调用很慢

# 步骤3：观察Redis调用参数
watch com.example.CacheService get "{params,returnObj}" -x 2

# 发现：缓存key过长，导致网络传输慢

# 步骤4：修复并验证
```

### 6.3 案例3：内存泄漏排查

```bash
# 问题：堆内存持续增长

# 步骤1：查看堆内存
dashboard

# 步骤2：查看对象分布
memory

# 步骤3：找出大对象
heapdump /tmp/heap.hprof

# 步骤4：观察对象创建
watch com.example.CacheManager put "{params}" -x 2

# 发现：缓存一直添加，从不清理

# 步骤5：查看缓存大小
ognl '@com.example.CacheManager@getInstance().size()'

# 输出：1234567（过大！）

# 步骤6：修复代码
```

### 6.4 案例4：ClassLoader问题

```bash
# 问题：ClassNotFoundException

# 步骤1：查找类
sc -d com.example.UserService

# 输出：找不到类

# 步骤2：查看所有ClassLoader
classloader

# 步骤3：在指定ClassLoader中查找
sc -c 18b4aac2 com.example.*

# 步骤4：查看类加载路径
classloader -c 18b4aac2

# 发现：jar包路径错误

# 步骤5：修复配置
```

---

## 七、生产环境使用

### 7.1 安全建议

```
生产环境使用注意事项：

1. 权限控制
   - 限制访问IP
   - 设置密码
   - 使用tunnel server

2. 性能影响
   - watch/trace有性能开销
   - 避免在高峰期使用
   - 设置条件过滤

3. 操作规范
   - 记录操作日志
   - 及时退出
   - 重置增强类

4. 应急预案
   - 准备回滚方案
   - 监控系统指标
   - 保留现场信息
```

### 7.2 配置建议

```bash
# 启动参数
java -jar arthas-boot.jar \
  --tunnel-server 'ws://tunnel-server:7777/ws' \
  --agent-id 'app-server-1' \
  --username admin \
  --password 'your-password'

# 配置文件 arthas.properties
arthas.telnetPort=3658
arthas.httpPort=8563
arthas.ip=127.0.0.1
arthas.sessionTimeout=1800
```

---

## 八、常用技巧

### 8.1 OGNL表达式

```bash
# 调用静态方法
ognl '@java.lang.System@getProperty("java.version")'

# 获取Spring Bean
ognl '#context=@org.springframework.web.context.ContextLoader@getCurrentWebApplicationContext(), #context.getBean("userService")'

# 调用实例方法
ognl '@com.example.CacheManager@getInstance().get("key")'

# 修改字段值
ognl '@com.example.Config@getInstance().setDebug(true)'
```

### 8.2 管道与重定向

```bash
# 输出到文件
thread > /tmp/thread.txt

# 管道过滤
thread | grep 'http-nio'

# 分页显示
sc com.example.* | less
```

### 8.3 批处理

```bash
# 创建脚本文件 commands.as
dashboard
thread -n 3
jvm

# 执行脚本
java -jar arthas-boot.jar -c commands.as
```

---

## 九、与其他工具对比

### 9.1 工具对比

| 特性 | Arthas | JProfiler | VisualVM | JConsole |
|------|--------|-----------|----------|----------|
| 无需重启 | ✓ | ✗ | ✗ | ✗ |
| 热更新 | ✓ | ✗ | ✗ | ✗ |
| 方法追踪 | ✓ | ✓ | ✓ | ✗ |
| 条件过滤 | ✓ | ✓ | ✗ | ✗ |
| 命令行 | ✓ | ✗ | ✗ | ✗ |
| 免费 | ✓ | ✗ | ✓ | ✓ |
| 易用性 | 高 | 中 | 中 | 低 |

### 9.2 使用场景

```
Arthas适用场景：
1. 线上问题排查
2. 快速诊断
3. 无法重启应用
4. 需要热更新
5. 命令行环境

其他工具适用场景：
1. 深度性能分析 → JProfiler
2. 开发调试 → VisualVM
3. 快速监控 → JConsole
4. 内存分析 → MAT
```

---

## 十、总结

### 10.1 核心要点

```
1. Arthas特点
   - 无需修改代码
   - 无需重启应用
   - 功能强大
   - 易于使用

2. 核心命令
   - dashboard: 系统面板
   - thread: 线程分析
   - jad: 反编译
   - watch: 观察方法
   - trace: 追踪调用
   - monitor: 监控统计

3. 使用场景
   - CPU飙高
   - 接口慢
   - 内存泄漏
   - ClassLoader问题

4. 注意事项
   - 性能开销
   - 安全控制
   - 及时退出
   - 重置增强
```

### 10.2 学习建议

```
学习路径：
1. 基础命令（dashboard, thread, jvm）
2. 类相关命令（sc, sm, jad）
3. 方法监控（watch, trace, monitor）
4. 高级功能（redefine, profiler）
5. 实战演练

推荐资源：
- 官方文档：https://arthas.aliyun.com/doc/
- 在线教程：https://arthas.aliyun.com/doc/arthas-tutorials.html
- GitHub：https://github.com/alibaba/arthas
```

---

## 十一、线上诊断实战：10个核心命令速查

> 来源：《Arthas线上诊断你只需要这10个命令》——接口变慢、日志正常、P99 从 50ms 涨到 2 秒，Arthas 能跳过"加日志→部署→再加→再部署"的循环，不改代码、不重启，直接看运行中进程里发生了什么。

### 11.1 连上进程

```bash
# 输出进程列表，输入序号选目标进程
java -jar arthas-boot.jar

# 或者指定 PID 直接连
java -jar arthas-boot.jar 12345
```

### 11.2 dashboard — 全局概览

进来之后第一条命令，不用参数，5 秒刷新一次：
- **上半部分**：线程列表，按 CPU 占用排序
- **下半部分**：JVM 各内存区使用率 + GC 统计

一个屏幕同时能看到：哪个线程在烧 CPU、Old 区有没有逼近上限、Full GC 最近多久触发一次。出事时先 `dashboard` 扫一眼，比盲目 `jstack` 省时间。

### 11.3 thread — 定位具体线程

```bash
# CPU 占用最高的前 3 个线程，带完整堆栈
thread -n 3

# 找到正在阻塞其他线程的那个线程（直接定位持锁线程）
thread -b

# 查看指定线程的堆栈
thread 12378
```

> `thread -n 3` 等于 `top -Hp` + 十六进制换算 + `jstack | grep` 三步合一；`thread -b` 直接找到持有锁同时有其他线程排队等它的那个，不用手动搜锁地址。

### 11.4 watch — 观察方法入参/返回值/异常

用得最多的命令，不加日志、不改代码：

```bash
# 同时观察入参、返回值、异常，展开 3 层嵌套对象
watch com.example.service.OrderService createOrder '{params, returnObj, throwExp}' -x 3

# 只看耗时超过 500ms 的调用
watch com.example.service.OrderService createOrder '{params, returnObj}' '#cost > 500'

# 只在方法抛异常时触发，拿到异常对象和触发异常时的入参
watch com.example.service.OrderService createOrder 'throwExp' -e
```

> 线上遇到"返回了错误数据但日志里没异常"，第一反应就是 `watch` 看返回值，比猜更快。

### 11.5 trace — 定位方法内哪一步最慢

```bash
trace com.example.service.OrderService createOrder

# 输出调用树，每层都有耗时：
# `---[2031.815ms] com.example.service.OrderService:createOrder()
#     +---[0.322ms]   com.example.mapper.UserMapper:selectById()
#     +---[2028.112ms] com.example.client.NotifyClient:send()
#     `---[0.041ms]   com.example.mapper.OrderMapper:insert()

# 展开 JDK 内部调用（如 JSON 序列化开销）
trace com.example.service.OrderService createOrder --skipJDKMethod false
```

> 两秒的接口，一眼看出是 `NotifyClient.send()` 吃掉的，数据库查询 0.3ms，问题根本不在那里。

### 11.6 stack — 反向追踪：谁在调这个方法

```bash
stack com.example.service.OrderService createOrder

# 输出从当前调用一路到线程入口的调用链：
# ts=2026-04-09;thread_name=http-nio-8080-exec-3
# @com.example.service.OrderService.createOrder()
#     at com.example.controller.OrderController.submit(OrderController.java:45)
#     ...
```

> 某个写操作被意外触发、某个方法调用次数异常，但日志里找不到是哪个接口发起的，`stack` 直接告诉你调用链。

### 11.7 monitor — 统计一段时间内的调用情况

```bash
# 每 10 秒输出一次统计
monitor -c 10 com.example.service.OrderService createOrder

# 输出：
# timestamp    class          method       total  success  fail  avg-rt(ms)  fail-rate
# 14:23:11     OrderService   createOrder  127    124      3     48.3        2.36%
```

> - `fail-rate` 不为零：有异常在被吞掉，日志里不见得有记录
> - `avg-rt` 偏高但某次 `trace` 很快：是偶发慢请求，需继续加条件过滤

### 11.8 jad — 反编译正在运行的类

```bash
# 反编译整个类
jad com.example.service.OrderService

# 只看某个方法
jad com.example.service.OrderService createOrder
```

> 线上偶尔遇到"代码跟本地不一样"——有人单独上传了某个 class、热更新没更新干净、多个版本 jar 在 classpath 上冲突。`jad` 直接看运行中字节码的反编译结果，确认跑的是哪个版本。

### 11.9 ognl — 在运行中的 JVM 里执行 Java 表达式

```bash
# 读取静态变量
ognl '@com.example.config.AppConfig@maxRetry'

# 调用静态方法
ognl '@java.lang.System@currentTimeMillis()'

# 查 Spring Bean 的属性（推荐用 vmtool，避免 ContextLoader 返回 null）
vmtool --action getInstances \
  --className org.springframework.context.ApplicationContext \
  --express 'instances[0].getBean("orderService").getMaxConcurrent()'
```

> `vmtool` 从 JVM 堆里找到所有 `ApplicationContext` 实例，`instances[0]` 取第一个（通常就是 Spring Boot 的主 context），然后调 `getBean` 获取 Bean 并执行方法。线上排查配置有没有生效、Feature Flag 的值是否正确、某个缓存里存了什么，直接读出来，不用加日志不用重启。

### 11.10 logger — 动态修改日志级别

```bash
# 把 com.example 包的日志改成 DEBUG
logger --name com.example --level DEBUG

# 问题定位完改回 INFO
logger --name com.example --level INFO

# 查看当前所有 logger 的级别（确认没有忘记改回去的 DEBUG）
logger
```

> 排查问题时开 DEBUG 捞详细日志，捞完改回 INFO，不影响线上流量，也不会把磁盘打满。

### 11.11 heapdump — 生成堆 dump

```bash
# 完整堆 dump
heapdump /tmp/heap.hprof

# 只 dump 存活对象，文件更小（推荐先用这个）
heapdump --live /tmp/heap-live.hprof
```

> `--live` 只抓存活对象，同一个堆加了这个选项有时候文件从几 GB 缩到几百 MB，传出来分析方便得多。如果 MAT 分析发现问题不在存活对象里，再 dump 完整版。

### 11.12 10个命令速查表

| 命令 | 核心用途 | 典型场景 |
|------|----------|----------|
| `dashboard` | 全局概览（线程/内存/GC） | 出事第一眼 |
| `thread -n 3` | CPU 最高的线程堆栈 | CPU 飙高 |
| `thread -b` | 找持锁阻塞线程 | 线程死锁 |
| `watch` | 观察方法入参/返回值/异常 | 返回错误数据、异常被吞 |
| `trace` | 方法内调用树 + 耗时 | 接口慢、定位慢在哪步 |
| `stack` | 反向追调用来源 | 方法被意外调用 |
| `monitor` | 统计成功率/平均耗时 | 偶发错误、偶发慢请求 |
| `jad` | 反编译运行中的类 | 确认线上代码版本 |
| `ognl` / `vmtool` | 执行表达式/读 Bean 属性 | 配置验证、缓存查看 |
| `logger` | 动态修改日志级别 | 临时开 DEBUG |
| `heapdump` | 生成堆 dump | 内存泄漏分析 |

---

**下一篇**：[APM监控系统](./04_APM监控系统.md)
