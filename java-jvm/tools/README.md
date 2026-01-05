# JVM工具使用指南

## 📚 模块简介

本模块详细介绍JVM监控和诊断工具的使用，包括命令行工具、可视化工具、Arthas诊断工具和APM监控系统，帮助你快速定位和解决生产环境问题。

## 🎯 学习目标

- ✅ 掌握JVM命令行工具的使用
- ✅ 熟练使用可视化监控工具
- ✅ 能够使用Arthas进行线上诊断
- ✅ 了解APM监控系统的原理和使用
- ✅ 具备完整的JVM问题排查能力

## 📂 目录结构

```
tools/
├── docs/                                      # 文档目录
│   ├── 01_命令行工具详解.md                    # JVM命令行工具
│   ├── 02_可视化工具使用.md                    # 可视化监控工具
│   ├── 03_Arthas实战.md                       # Arthas诊断工具
│   └── 04_APM监控系统.md                      # APM监控系统
├── scripts/                                   # 监控脚本
│   ├── jvm_monitor.sh                         # JVM监控脚本
│   └── gc_analysis.sh                         # GC日志分析脚本
└── README.md                                  # 本文件
```

## 🚀 快速开始

### 1. 命令行工具

#### 1.1 查看Java进程

```bash
# 查看所有Java进程
jps -l

# 查看进程及JVM参数
jps -v
```

#### 1.2 查看JVM统计信息

```bash
# 查看GC统计
jstat -gc <pid>

# 查看GC统计（每秒刷新）
jstat -gc <pid> 1000

# 查看内存使用百分比
jstat -gcutil <pid> 1000
```

#### 1.3 查看JVM配置

```bash
# 查看所有JVM参数
jinfo -flags <pid>

# 查看特定参数
jinfo -flag MaxHeapSize <pid>

# 动态修改参数
jinfo -flag +PrintGC <pid>
```

#### 1.4 生成堆转储

```bash
# 生成堆转储文件
jmap -dump:format=b,file=heap.hprof <pid>

# 查看堆内存统计
jmap -heap <pid>

# 查看对象统计
jmap -histo <pid>
```

#### 1.5 查看线程栈

```bash
# 查看线程栈
jstack <pid>

# 查看线程栈并输出到文件
jstack <pid> > thread.txt

# 检测死锁
jstack -l <pid>
```

**详细文档**：[命令行工具详解](./docs/01_命令行工具详解.md)

---

### 2. 可视化工具

#### 2.1 JConsole

```bash
# 启动JConsole
jconsole

# 连接到指定进程
jconsole <pid>

# 远程连接
jconsole <hostname>:<port>
```

**功能**：
- 内存监控
- 线程监控
- 类加载监控
- MBean管理

#### 2.2 VisualVM

```bash
# 启动VisualVM（JDK 8自带）
jvisualvm

# 独立版本
./visualvm
```

**功能**：
- 性能监控
- CPU/内存采样
- 性能分析（Profiler）
- 堆转储分析
- 线程分析

#### 2.3 JMC（Java Mission Control）

```bash
# 启动JMC
jmc
```

**功能**：
- JFR（Java Flight Recorder）分析
- 实时监控
- 自动化分析
- 规则引擎

#### 2.4 MAT（Memory Analyzer Tool）

**功能**：
- 堆转储分析
- 内存泄漏检测
- 对象引用分析
- OQL查询

**详细文档**：[可视化工具使用](./docs/02_可视化工具使用.md)

---

### 3. Arthas实战

#### 3.1 安装与启动

```bash
# 下载Arthas
curl -O https://arthas.aliyun.com/arthas-boot.jar

# 启动Arthas
java -jar arthas-boot.jar

# 选择要诊断的进程
```

#### 3.2 常用命令

```bash
# 查看系统面板
dashboard

# 查看线程
thread
thread -n 3          # 查看最忙的3个线程
thread -b            # 查看死锁

# 反编译类
jad com.example.UserService

# 观察方法调用
watch com.example.UserService getUser "{params,returnObj}" -x 2

# 追踪方法调用链
trace com.example.UserService getUser

# 监控方法调用统计
monitor -c 5 com.example.UserService getUser

# 热更新类
redefine /tmp/UserService.class
```

#### 3.3 实战案例

**案例1：CPU飙高排查**
```bash
# 1. 查看最忙的线程
thread -n 3

# 2. 反编译可疑方法
jad com.example.Service method

# 3. 追踪方法调用
trace com.example.Service method
```

**案例2：接口响应慢**
```bash
# 1. 监控方法耗时
monitor -c 5 com.example.Controller api

# 2. 追踪调用链
trace com.example.Controller api '#cost > 1000'

# 3. 观察方法参数和返回值
watch com.example.Service method "{params,returnObj}" -x 2
```

**详细文档**：[Arthas实战](./docs/03_Arthas实战.md)

---

### 4. APM监控系统

#### 4.1 SkyWalking

```bash
# 启动OAP Server
bin/oapService.sh

# 启动UI
bin/webappService.sh

# 应用集成Agent
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=user-service \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar application.jar
```

**功能**：
- 服务拓扑
- 链路追踪
- 性能指标
- JVM监控
- 告警

#### 4.2 Pinpoint

**功能**：
- 调用链可视化
- 服务器地图
- 代码级定位
- 实时监控

#### 4.3 Zipkin

**功能**：
- 分布式追踪
- 链路分析
- 依赖关系图

#### 4.4 Elastic APM

```bash
# 启动参数
java -javaagent:/path/to/elastic-apm-agent.jar \
     -Delastic.apm.service_name=user-service \
     -Delastic.apm.server_urls=http://localhost:8200 \
     -jar application.jar
```

**详细文档**：[APM监控系统](./docs/04_APM监控系统.md)

---

### 5. 监控脚本

#### 5.1 JVM监控脚本

```bash
# 赋予执行权限
chmod +x scripts/jvm_monitor.sh

# 监控指定进程
./scripts/jvm_monitor.sh -p 12345

# 每10秒监控一次
./scripts/jvm_monitor.sh -p 12345 -i 10

# 监控60秒并输出到文件
./scripts/jvm_monitor.sh -p 12345 -d 60 -o jvm.log
```

**功能**：
- 实时监控JVM状态
- 内存使用情况
- GC统计
- 线程信息
- CPU使用率
- 类加载信息
- 自动告警

#### 5.2 GC日志分析脚本

```bash
# 赋予执行权限
chmod +x scripts/gc_analysis.sh

# 分析GC日志
./scripts/gc_analysis.sh -f gc.log

# 详细分析
./scripts/gc_analysis.sh -f gc.log -d

# 输出到文件
./scripts/gc_analysis.sh -f gc.log -o report.txt
```

**功能**：
- Young GC分析
- Full GC分析
- 堆内存使用分析
- GC原因分析
- 性能评估
- 优化建议

---

## 📊 工具对比

### 命令行工具 vs 可视化工具

| 特性 | 命令行工具 | 可视化工具 |
|------|-----------|-----------|
| 使用场景 | 生产环境、快速诊断 | 开发测试、深度分析 |
| 学习成本 | 高 | 低 |
| 功能完整性 | 基础 | 全面 |
| 性能开销 | 极小 | 中等 |
| 实时性 | 好 | 很好 |
| 数据可视化 | 无 | 强 |

### APM系统对比

| 工具 | 开源 | 性能开销 | 功能 | 适用场景 |
|------|------|---------|------|---------|
| SkyWalking | ✓ | 低 | 全面 | 中大型项目 |
| Pinpoint | ✓ | 低 | 强大 | 大规模集群 |
| Zipkin | ✓ | 中 | 基础 | 小型项目 |
| CAT | ✓ | 低 | 实时 | 国内项目 |
| Elastic APM | ✓ | 中 | 完善 | ELK用户 |

---

## 💡 最佳实践

### 1. 工具选择

```
场景 → 推荐工具

快速诊断：
- jps + jstat + jstack

内存问题：
- jmap + MAT

性能分析：
- VisualVM + Profiler

生产监控：
- JMC + JFR
- APM系统

线上诊断：
- Arthas
```

### 2. 监控策略

```
开发环境：
- 使用VisualVM
- 启用详细GC日志
- 使用Profiler分析

测试环境：
- 使用JMC
- 压测时监控
- 记录JFR

生产环境：
- 部署APM系统
- 启用JFR（低开销）
- 定期分析GC日志
- 准备Arthas应急
```

### 3. 问题排查流程

```
1. 发现问题
   ↓
2. 初步定位（APM/监控）
   ↓
3. 数据采集
   - jstack（线程问题）
   - jmap（内存问题）
   - JFR（性能问题）
   ↓
4. 深度分析
   - MAT（内存泄漏）
   - VisualVM（性能瓶颈）
   - Arthas（实时诊断）
   ↓
5. 修复验证
   ↓
6. 总结归档
```

---

## 🎓 学习路径

### 阶段1：基础工具（1周）

**目标**：掌握基本的JVM命令行工具

**学习内容**：
1. 阅读 [命令行工具详解](./docs/01_命令行工具详解.md)
2. 实践 jps、jstat、jinfo、jmap、jstack
3. 使用监控脚本 `jvm_monitor.sh`

**练习**：
- 监控一个Java应用
- 生成堆转储并分析
- 查看线程栈定位问题

---

### 阶段2：可视化工具（1周）

**目标**：熟练使用可视化监控工具

**学习内容**：
1. 阅读 [可视化工具使用](./docs/02_可视化工具使用.md)
2. 使用 JConsole 监控应用
3. 使用 VisualVM 进行性能分析
4. 使用 MAT 分析内存泄漏

**练习**：
- 使用VisualVM采样分析CPU热点
- 使用MAT分析堆转储
- 使用JMC分析JFR记录

---

### 阶段3：Arthas实战（1周）

**目标**：能够使用Arthas进行线上诊断

**学习内容**：
1. 阅读 [Arthas实战](./docs/03_Arthas实战.md)
2. 安装和启动 Arthas
3. 学习核心命令
4. 实战案例演练

**练习**：
- 排查CPU飙高问题
- 排查接口响应慢问题
- 使用热更新修复问题

---

### 阶段4：APM监控（1-2周）

**目标**：了解APM系统的原理和使用

**学习内容**：
1. 阅读 [APM监控系统](./docs/04_APM监控系统.md)
2. 部署 SkyWalking
3. 集成应用
4. 配置告警

**练习**：
- 部署SkyWalking
- 查看服务拓扑
- 分析链路追踪
- 配置告警规则

---

## 🔧 工具安装

### JDK工具（自带）

```bash
# 验证JDK安装
java -version
javac -version

# 验证工具可用
jps
jstat -help
jmap -help
jstack -help
```

### VisualVM

```bash
# JDK 8自带
jvisualvm

# 独立下载
# https://visualvm.github.io/
```

### MAT

```bash
# 下载地址
# https://www.eclipse.org/mat/

# 启动
./MemoryAnalyzer
```

### Arthas

```bash
# 在线安装
curl -O https://arthas.aliyun.com/arthas-boot.jar

# 启动
java -jar arthas-boot.jar
```

### SkyWalking

```bash
# 下载
wget https://archive.apache.org/dist/skywalking/8.9.1/apache-skywalking-apm-8.9.1.tar.gz

# 解压
tar -zxvf apache-skywalking-apm-8.9.1.tar.gz

# 启动
cd apache-skywalking-apm-8.9.1
bin/oapService.sh
bin/webappService.sh
```

---

## 📚 核心知识点

### 1. JVM监控指标

```
内存指标：
- 堆内存使用率
- Eden区使用率
- Old区使用率
- Metaspace使用率

GC指标：
- Young GC次数/时间
- Full GC次数/时间
- GC暂停时间
- GC吞吐量

线程指标：
- 线程总数
- 活动线程数
- 阻塞线程数
- 死锁检测

性能指标：
- CPU使用率
- 响应时间
- 吞吐量
- 错误率
```

### 2. 问题诊断技巧

```
CPU飙高：
1. top -Hp <pid> 找到CPU高的线程
2. printf "%x\n" <tid> 转换为16进制
3. jstack <pid> | grep <hex-tid>
4. 分析线程栈

内存泄漏：
1. jmap -dump 生成堆转储
2. MAT分析 Leak Suspects
3. 查看 Dominator Tree
4. 分析引用链

死锁：
1. jstack <pid>
2. 查找 "Found one Java-level deadlock"
3. 分析锁依赖关系

接口慢：
1. APM查看调用链
2. trace追踪方法
3. 定位慢的节点
4. 优化代码
```

### 3. GC日志分析

```
关键信息：
- GC类型（Young/Full）
- GC原因（Allocation Failure等）
- 内存变化（before->after）
- 暂停时间
- GC频率

分析重点：
- Full GC是否频繁
- GC暂停时间是否过长
- 内存回收率是否正常
- 是否存在内存泄漏
```

---

## 🎯 实战演练

### 练习1：CPU飙高排查

**场景**：应用CPU使用率突然达到100%

**步骤**：
1. 使用 `top` 查看进程CPU
2. 使用 `jstack` 查看线程栈
3. 使用 Arthas `thread -n 3` 查看最忙线程
4. 使用 `trace` 追踪方法调用
5. 定位问题代码
6. 修复并验证

---

### 练习2：内存泄漏排查

**场景**：应用内存持续增长，最终OOM

**步骤**：
1. 使用 `jstat -gcutil` 观察内存趋势
2. 使用 `jmap -dump` 生成堆转储
3. 使用 MAT 分析堆转储
4. 查看 Leak Suspects 报告
5. 分析 Dominator Tree
6. 定位泄漏对象
7. 修复代码

---

### 练习3：接口性能优化

**场景**：某接口响应时间从50ms增加到5s

**步骤**：
1. APM查看调用链
2. 使用 `monitor` 监控方法耗时
3. 使用 `trace` 追踪调用链
4. 定位慢的节点
5. 使用 `watch` 观察参数
6. 优化代码
7. 验证效果

---

## 📖 参考资料

### 官方文档

- [JDK Tools Reference](https://docs.oracle.com/javase/8/docs/technotes/tools/)
- [VisualVM Documentation](https://visualvm.github.io/documentation.html)
- [Arthas Documentation](https://arthas.aliyun.com/doc/)
- [SkyWalking Documentation](https://skywalking.apache.org/docs/)

### 推荐工具

- **GCEasy**：在线GC日志分析 https://gceasy.io/
- **GCViewer**：GC日志可视化工具
- **JProfiler**：商业性能分析工具
- **Async-profiler**：低开销性能分析工具

---

## ❓ 常见问题

### Q1: 生产环境可以使用哪些工具？

**A**: 
- ✅ 推荐：jstat、jstack、JFR、APM系统
- ⚠️ 谨慎：jmap（会触发Full GC）
- ⚠️ 谨慎：Arthas（有性能开销）
- ❌ 不推荐：Profiler（开销大）

### Q2: 如何选择合适的APM系统？

**A**:
- 小型项目：Zipkin
- 中型项目：SkyWalking
- 大型项目：Pinpoint
- 已有ELK：Elastic APM
- 国内项目：CAT

### Q3: 工具使用会影响应用性能吗？

**A**:
- jps/jstat/jinfo：几乎无影响
- jstack：影响很小
- jmap：会触发Full GC，影响较大
- JFR：<1%开销
- Arthas：watch/trace有5-10%开销
- Profiler：10-50%开销

### Q4: 如何学习这些工具？

**A**:
1. 先学命令行工具（必须掌握）
2. 再学可视化工具（提高效率）
3. 深入学习Arthas（线上诊断）
4. 了解APM系统（全局监控）
5. 实战演练（解决真实问题）

---

## 🚀 下一步

完成本模块学习后，建议：

1. **实战演练**：在真实项目中使用这些工具
2. **问题总结**：记录遇到的问题和解决方案
3. **深入学习**：阅读工具源码，理解实现原理
4. **知识分享**：向团队分享经验

---

**返回**：[JVM深度学习指南](../README.md)

**相关模块**：
- [性能调优](../tuning/README.md)
- [高级主题](../advanced/README.md)
