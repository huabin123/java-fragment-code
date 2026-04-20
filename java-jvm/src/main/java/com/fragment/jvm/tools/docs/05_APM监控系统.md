# 第五章：APM 监控系统

APM（Application Performance Management）是企业级应用的全链路监控方案，覆盖性能监控、链路追踪、指标采集和告警通知。与前几章的单机诊断工具不同，APM 面向分布式系统的全局可观测性。

---

## 5.1 为什么需要 APM

```
单机工具的局限：
  jstat/jmap/Arthas → 只看一个 JVM
  分布式系统中一个请求跨 5~10 个服务
  问题在哪个服务？慢在哪一跳？→ 无法判断

APM 解决的核心问题：
  1. 全链路追踪：请求从入口到数据库的完整调用链
  2. 服务拓扑：自动发现服务依赖关系
  3. 性能指标：响应时间 / 吞吐量 / 错误率 / Apdex
  4. JVM 监控：堆内存 / GC / 线程（每个节点）
  5. 告警通知：阈值告警 + 多渠道通知
```

---

## 5.2 主流 APM 系统对比

```
特性           SkyWalking    Pinpoint     Zipkin     CAT        Elastic APM
开源            ✓            ✓            ✓          ✓          ✓
无侵入          ✓            ✓            ✗          ✗          ✓
性能开销        低            低           中          低         中
存储依赖        ES/MySQL     HBase        ES/MySQL   MySQL      ES
中文文档        ✓            ✗            ✗          ✓          ✓
功能完整性      高            高           中          高         高
社区活跃度      高            中           高          中         高

选型建议：
  小型项目        → Zipkin（轻量）/ Elastic APM（已有 ELK）
  中型项目        → SkyWalking（功能全、中文友好）/ CAT（国内成熟）
  大规模集群      → Pinpoint / SkyWalking
  已有 ELK 栈    → Elastic APM
```

---

## 5.3 SkyWalking（推荐）

Apache 顶级项目，国内使用最广泛的开源 APM。

### 架构

```
┌───────────────┐
│  应用服务      │
│  + Agent      │  ← Java Agent 字节码增强，无侵入
└──────┬────────┘
       │ gRPC
       ↓
┌───────────────┐
│     OAP       │  ← 分析处理（聚合、告警规则）
└──────┬────────┘
       │
  ┌────┴────┐
  ↓         ↓
┌──────┐  ┌──────┐
│ ES   │  │  UI  │
└──────┘  └──────┘
```

### 快速部署

```bash
# 1. 下载
wget https://archive.apache.org/dist/skywalking/9.x.x/apache-skywalking-apm-9.x.x.tar.gz
tar -zxvf apache-skywalking-apm-*.tar.gz && cd apache-skywalking-apm-*

# 2. 启动 OAP + UI
bin/oapService.sh
bin/webappService.sh

# 3. 应用集成 Agent（无需改代码）
java -javaagent:/path/to/skywalking-agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=user-service \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar application.jar
```

### 核心功能

```
1. 服务拓扑图
   → 自动发现服务间调用关系 + 流量可视化

2. 链路追踪
   → 每个请求的完整调用树 + 每层耗时 + 异常标记
   → 类似 Arthas 的 trace，但覆盖跨服务调用

3. 性能指标
   → 响应时间（P50/P90/P99）
   → 吞吐量（CPM = Calls Per Minute）
   → 成功率（SLA）/ Apdex 分数

4. JVM 监控
   → 每个服务实例的堆内存 / GC / 线程数 / CPU

5. 告警
   → 响应时间 > 1s 持续 3 次 → 告警
   → 成功率 < 95% → 告警
   → JVM 堆使用 > 80% → 告警
```

### 自定义追踪

```java
// 添加依赖
// <artifactId>apm-toolkit-trace</artifactId>

// 注解方式
@Trace
@Tag(key = "userId", value = "arg[0]")
public User getUser(Long userId) {
    return userRepository.findById(userId);
}

// 手动追踪
ActiveSpan.tag("order.id", orderId.toString());
ActiveSpan.info("Processing order: " + orderId);
```

### 告警配置

```yaml
rules:
  service_resp_time_rule:
    metrics-name: service_resp_time
    op: ">"
    threshold: 1000       # 响应时间 > 1 秒
    period: 10             # 10 分钟窗口
    count: 3               # 触发 3 次
    message: "服务响应时间超过 1 秒"

  service_sla_rule:
    metrics-name: service_sla
    op: "<"
    threshold: 95          # 成功率 < 95%
    period: 10
    count: 2
    message: "服务成功率低于 95%"
```

---

## 5.4 链路追踪原理

```
分布式追踪的核心：Trace ID 贯穿整个调用链

请求入口生成 Trace ID → 通过 HTTP Header / RPC Context / MQ Message 传递

用户 → Gateway → User Service → Order Service → DB
Trace: abc123
  Span:1 (Gateway)
    Span:2 (UserService, parent=1)
      Span:3 (OrderService, parent=2)
        Span:4 (DB, parent=3)

每个 Span 记录：
  开始/结束时间 → 耗时
  服务名/方法名 → 位置
  父 Span ID   → 调用关系
  标签/日志     → 上下文信息
```

### 数据采集方式

```
1. 字节码增强（主流）— SkyWalking / Pinpoint
   Java Agent + ASM/Javassist
   无侵入、自动拦截 HTTP/RPC/DB/Cache 调用
   → 推荐

2. 手动埋点 — CAT
   代码中显式调用 SDK
   灵活但维护成本高

3. 日志采集
   解析结构化日志
   延迟高、信息有限
```

---

## 5.5 性能优化与最佳实践

### 降低 Agent 开销

```
1. 采样策略
   正常请求：按比例采样（如 10%）
   慢请求（>1s）：100% 采样
   错误请求：100% 采样

2. 过滤配置
   忽略静态资源：.jpg, .js, .css
   过滤健康检查：/health, /actuator
   关闭不需要的插件

3. 异步上报
   批量发送 + 缓冲队列 + 失败重试
```

### 数据保留策略

```
Trace 数据（原始调用链）：保留 7 天
指标数据（聚合统计）：保留 30 天
聚合报表：保留 1 年
存储优化：冷热分离（热数据 SSD / 冷数据 HDD）
```

### 部署建议

```
1. OAP/Collector 集群部署（高可用）
2. Agent 资源预算：CPU ~5%，内存 ~100MB
3. Agent → Collector 走内网
4. 监控 APM 自身（APM 挂了 = 盲区）
```

---

## 5.6 实战：接口性能分析

```
问题：某接口响应时间从 50ms 涨到 5 秒

步骤：
1. SkyWalking UI → 服务列表 → 找到响应时间异常的服务
2. 查看 Trace 列表 → 筛选慢请求（>1s）
3. 点击具体 Trace → 查看调用链
4. 发现：OrderService.query() 耗时 4.8s
5. 展开 Span 详情 → SQL: SELECT * FROM orders WHERE ... （全表扫描）
6. 添加索引 → 响应时间降至 50ms

对比手动排查：
  传统：登录每台机器 → jstack/jstat → 猜测 → 加日志 → 部署 → 再看
  APM：打开 UI → 看 Trace → 定位 SQL → 完成
```

---

## 5.7 本章总结

- APM 解决**分布式系统**的全链路可观测性问题
- **SkyWalking** 是国内最推荐的开源方案（无侵入、功能全、中文友好）
- 核心原理：**Java Agent 字节码增强** + **Trace ID 传递** + **异步上报**
- 工具链定位：
  - **jstat/jmap/jstack**：单机 JVM 诊断
  - **Arthas**：单机方法级实时诊断
  - **APM**：分布式全链路监控 + 告警
- 生产必备：APM + 告警 + HeapDumpOnOutOfMemoryError + GC 日志

---

**相关文档**：
- [01_命令行工具详解.md](./01_命令行工具详解.md)
- [02_可视化工具使用.md](./02_可视化工具使用.md)
- [03_Arthas实战.md](./03_Arthas实战.md)
- [04_Debug断点实现原理.md](./04_Debug断点实现原理.md)
