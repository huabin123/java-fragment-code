# Excel 文件夹比对工具

本工具会读取 `resources` 下两个目录中的 Excel 文件并进行比对：

- `excel_diff/left`
- `excel_diff/right`

## 比对规则

1. 先按文件名匹配（例如 `a.xlsx` 对 `a.xlsx`）。
2. 对匹配上的文件，按 `sheet` 名依次对比。
3. 对匹配 `sheet` 逐行逐列比对单元格值（空单元格按空串处理）。
4. 输出：
   - 仅左侧存在的文件
   - 仅右侧存在的文件
   - 仅左/右存在的 sheet
   - 单元格差异（sheet、行、列、左右值）

## 使用方式

入口类：`com.fragment.excel.ExcelFolderDiffMain`

### 方式1：使用默认 resources 目录

默认读取：

- `src/main/resources/excel_diff/left`
- `src/main/resources/excel_diff/right`

直接运行 `main` 即可。

### 方式2：命令行传入两个目录

- `args[0]`: 左目录
- `args[1]`: 右目录

例如：

```bash
mvn -pl java-excel -DskipTests exec:java \
  -Dexec.mainClass=com.fragment.excel.ExcelFolderDiffMain \
  -Dexec.args="/path/to/left /path/to/right"
```
