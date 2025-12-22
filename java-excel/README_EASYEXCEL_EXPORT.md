# EasyExcel 导出演示

基于自定义注解的 EasyExcel 导出功能演示，支持中文列名、排序、开关控制和日期格式化。

## 功能特性

### 1. 自定义注解 `@ExcelColumn`

```java
@ExcelColumn(
    name = "用户ID",           // 列的中文名称
    order = 1,                // 排序顺序，数字越小越靠前
    export = true,            // 是否导出该列
    dateFormat = "yyyy-MM-dd", // 日期格式化模式
    width = 15                // 列宽度
)
private Long id;
```

### 2. 支持的配置项

- **name**: 列的中文名称，支持中文显示
- **order**: 列的排序顺序，按数字升序排列
- **export**: 导出开关，false 时该列不会导出
- **dateFormat**: 日期格式化，仅对 Date 类型字段有效
- **width**: 列宽度设置

### 3. 日期格式化

支持多种日期格式：
- `yyyy-MM-dd`: 只显示日期
- `yyyy-MM-dd HH:mm:ss`: 显示完整时间
- `yyyy-MM-dd HH:mm`: 显示到分钟
- 其他自定义格式

## 文件结构

```
src/main/java/com/fragment/excel/
├── annotation/
│   └── ExcelColumn.java              # 自定义注解
├── model/
│   ├── UserExportVO.java             # 用户导出VO
│   └── ProductExportVO.java          # 商品导出VO
├── util/
│   └── ExcelExportUtil.java          # Excel导出工具类
├── EasyExcelExportDemo.java          # 基础导出演示
└── AdvancedExcelExportDemo.java      # 高级导出演示
```

## 使用示例

### 1. 定义 VO 类

```java
@Data
public class UserExportVO {
    @ExcelColumn(name = "用户ID", order = 1, width = 15)
    private Long id;
    
    @ExcelColumn(name = "用户名", order = 2, width = 20)
    private String username;
    
    @ExcelColumn(name = "注册时间", order = 8, dateFormat = "yyyy-MM-dd", width = 20)
    private Date registerTime;
    
    // 不导出的字段
    @ExcelColumn(name = "密码", order = 99, export = false)
    private String password;
}
```

### 2. 导出 Excel

```java
List<UserExportVO> users = createTestData();
String fileName = "用户数据导出.xlsx";
ExcelExportUtil.exportExcel(users, UserExportVO.class, fileName);
```

## 运行演示

### 基础演示
```bash
java com.fragment.excel.EasyExcelExportDemo
```

### 高级演示
```bash
java com.fragment.excel.AdvancedExcelExportDemo
```

## 演示效果

### 1. 列名配置
- 原字段名 `id` → Excel 列名 `用户ID`
- 原字段名 `username` → Excel 列名 `用户名`
- 原字段名 `registerTime` → Excel 列名 `注册时间`

### 2. 排序控制
按 `order` 值升序排列：
1. 用户ID (order=1)
2. 用户名 (order=2)
3. 真实姓名 (order=3)
4. ...

### 3. 导出开关
- `export = true`: 正常导出
- `export = false`: 不导出（如密码、内部备注等敏感信息）

### 4. 日期格式化
- 注册时间: `2024-01-15` (yyyy-MM-dd)
- 最后登录时间: `2024-01-15 14:30:25` (yyyy-MM-dd HH:mm:ss)

### 5. 样式配置
- **表头**: 灰色背景，宋体12号粗体，居中对齐
- **内容**: 宋体11号，左对齐
- **列宽**: 根据内容类型设置合适宽度

## 核心实现

### 1. 注解解析
```java
// 获取需要导出的字段
Field[] fields = clazz.getDeclaredFields();
for (Field field : fields) {
    ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
    if (annotation != null && annotation.export()) {
        // 处理导出字段
    }
}
```

### 2. 日期格式化
```java
if (value instanceof Date) {
    Date dateValue = (Date) value;
    SimpleDateFormat sdf = new SimpleDateFormat(fieldInfo.annotation.dateFormat());
    value = sdf.format(dateValue);
}
```

### 3. 排序处理
```java
// 按 order 排序
fieldInfos.sort(Comparator.comparingInt(f -> f.annotation.order()));
```

## 扩展功能

### 1. 添加新的配置项
可以在 `@ExcelColumn` 注解中添加更多配置：
- 数字格式化
- 单元格颜色
- 字体样式
- 数据验证

### 2. 支持更多数据类型
- 枚举类型的中文显示
- 布尔值的是/否显示
- 数字的千分位格式化

### 3. 多 Sheet 导出
扩展工具类支持多个 Sheet 的导出功能。

## 注意事项

1. **依赖要求**: 需要 EasyExcel 3.1.1+ 和 Lombok
2. **日期处理**: Date 类型字段会自动按配置格式化
3. **字段访问**: 工具类会自动设置字段可访问性
4. **性能考虑**: 大数据量导出时建议分批处理
5. **文件路径**: 默认导出到项目根目录，可修改为指定路径

## 测试数据

演示程序包含两种测试数据：
- **用户数据**: 包含 ID、姓名、邮箱、手机、余额、注册时间等
- **商品数据**: 包含编码、名称、价格、库存、创建时间等

每种数据都演示了不同的注解配置和导出效果。
