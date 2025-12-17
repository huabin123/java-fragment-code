# 日志计数处理工具

## 功能说明

该工具用于读取 `log_count` 目录下的文件，完成对 `result.xlsx` 中 sheet1 中"调用次数"列的填充。

## 匹配逻辑

1. **第一步匹配**：使用 `sys_log.xlsx` 进行匹配
   - `result.xlsx` 中的"接口调用url（一个接口一行）"列与 `sys_log.xlsx` 中的"请求连接"列进行匹配
   - 由于 `sys_log.xlsx` 中的"请求连接"列比 `result.xlsx` 中的 URL 多了类似 `/bocwm` 这样的前缀
   - 匹配时会去除第二个 `/` 之前的内容再进行匹配
   - 匹配成功后，将 `sys_log.xlsx` 中的"计数"列的内容填充到 `result.xlsx` 的"调用次数"中

2. **第二步匹配**：使用 `method.csv` 进行匹配
   - 对于无法在 `sys_log.xlsx` 中匹配的 URL，使用 `method.csv` 中的"Top values of tranCode.keyword"列进行匹配
   - 该列中只有 URL 最后部分的内容，只要 URL 包含该内容就算匹配成功
   - 匹配成功后，将 `method.csv` 中的"Count"列的内容填充到 `result.xlsx` 的"调用次数"中

## 文件结构

```
java-excel/
├── src/main/java/com/fragment/excel/
│   ├── LogCountMain.java                    # 主程序入口
│   ├── model/
│   │   ├── ResultData.java                  # result.xlsx 数据模型
│   │   ├── SysLogData.java                  # sys_log.xlsx 数据模型
│   │   └── MethodData.java                  # method.csv 数据模型
│   └── util/
│       └── LogCountProcessor.java           # 日志计数处理器
└── src/main/resources/log_count/
    ├── result.xlsx                          # 待填充的结果文件
    ├── sys_log.xlsx                         # 系统日志文件
    └── method.csv                           # 方法调用统计文件
```

## 使用方法

1. 确保输入文件已放置在 `src/main/resources/log_count/` 目录下：
   - `result.xlsx`
   - `sys_log.xlsx`
   - `method.csv`

2. 运行主程序：
   ```bash
   mvn clean compile
   mvn exec:java -Dexec.mainClass="com.fragment.excel.LogCountMain"
   ```

3. 或者在 IDE 中直接运行 `LogCountMain` 类的 `main` 方法

4. 处理完成后，结果会输出到 `src/main/resources/log_count/result_output.xlsx`

## 输出说明

程序运行时会输出以下信息：
- 读取的各文件记录数
- 匹配统计（从 sys_log 匹配的数量、从 method.csv 匹配的数量、未匹配的数量）
- 未匹配的 URL 列表
- 输出文件路径

## 依赖说明

- **EasyExcel 3.1.1**：用于读写 Excel 文件
- **Lombok 1.18.24**：用于简化数据模型代码
- **Apache POI 4.1.2**：EasyExcel 的底层依赖

## 注意事项

1. 输入文件的列名必须与程序中定义的完全一致
2. 程序会创建新文件 `result_output.xlsx`，不会修改原始的 `result.xlsx`
3. 如果 URL 无法匹配，"调用次数"列将保持为空（null）
