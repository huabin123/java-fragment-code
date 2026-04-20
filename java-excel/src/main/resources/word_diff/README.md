# Word 文件夹比对工具

本工具会读取 `resources` 下两个目录中的 Word 文件并进行比对：

- `word_diff/left`
- `word_diff/right`

## 比对规则

1. 先按文件名匹配（例如 `a.docx` 对 `a.docx`）。
2. 对匹配文件，比较 **段落**（按段落序号）。
3. 比较 **表格**（按表序号，再按行列单元格）。
4. 输出：
   - 仅左侧存在的文件
   - 仅右侧存在的文件
   - 段落差异（段落序号、左右文本）
   - 表格差异（表序号、行、列、左右文本）

## 当前支持格式

- `.docx`

> 说明：`.doc` 老格式暂未支持。如需支持，可后续增加 `poi-scratchpad` 并扩展实现。

## 使用方式

入口类：`com.fragment.excel.WordFolderDiffMain`

### 方式1：默认 resources 目录

默认读取：

- `src/main/resources/word_diff/left`
- `src/main/resources/word_diff/right`

直接运行 `main` 即可。

### 方式2：命令行传入目录

- `args[0]`: 左目录
- `args[1]`: 右目录

例如：

```bash
mvn -pl java-excel -DskipTests exec:java \
  -Dexec.mainClass=com.fragment.excel.WordFolderDiffMain \
  -Dexec.args="/path/to/left /path/to/right"
```
