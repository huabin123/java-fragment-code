package com.fragment.zip;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

 

/**
 * 视图需求Controller
 * 接收前端上传的zip文件
 */
@RestController
@RequestMapping("/api/view-request")
public class ViewRequestController {

    /**
     * 上传并处理视图需求zip文件
     *
     * @param file 前端上传的zip文件
     * @return 处理结果
     */
    @PostMapping("/upload")
    public ViewRequestResult uploadViewRequest(@RequestParam("file") MultipartFile file) {
        try {
            // 处理上传的zip文件
            ViewRequestResult result = ViewRequestProcessor.processZipFile(file);
            return result;

        } catch (IllegalArgumentException e) {
            // 参数验证异常
            ViewRequestResult result = new ViewRequestResult();
            result.setSuccess(false);
            result.setMessage("参数错误: " + e.getMessage());
            return result;

        } catch (Exception e) {
            // 其他异常
            ViewRequestResult result = new ViewRequestResult();
            result.setSuccess(false);
            result.setMessage("处理失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 验证DDL语句
     *
     * @param ddl DDL语句
     * @return 验证结果
     */
    @PostMapping("/validate-ddl")
    public ValidationResult validateDdl(@RequestBody String ddl) {
        try {
            SqlValidator.validateOrThrow(ddl);
            return new ValidationResult(true, "DDL验证通过");
        } catch (IllegalArgumentException e) {
            return new ValidationResult(false, e.getMessage());
        }
    }

    /**
     * 导出视图需求zip文件
     *
     * @return 视图需求zip文件
     */
    @GetMapping("/export")
    public ResponseEntity<Resource> exportViewRequest() {
        try {
            // 从resources目录读取
            Resource resource = new ClassPathResource("视图需求001.zip");

            if (!resource.exists()) {
                throw new IllegalArgumentException("文件不存在");
            }

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=视图需求001.zip");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");

            // 返回文件
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            // 处理异常
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 模拟读取数据库字段，动态生成并返回包含 DDL.txt、DML.txt、condition.txt 的 zip
     *
     * @return zip文件（文件名：view.ziip）
     */
    @GetMapping("/export-mock")
    public ResponseEntity<Resource> exportMockZip() {
        try {
            // 1. 模拟读取数据库字段
            List<Map<String, String>> columns = mockReadDbColumns();
            String tableName = "view_table";

            // 2. 生成 DDL 内容
            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE TABLE ").append(tableName).append(" (\n");
            for (int i = 0; i < columns.size(); i++) {
                Map<String, String> col = columns.get(i);
                ddl.append("  ").append(col.get("name")).append(" ").append(col.get("type"));
                if (i < columns.size() - 1) {
                    ddl.append(",");
                }
                ddl.append("\n");
            }
            ddl.append(");\n");

            // 3. 生成 DML 内容（示例：查询语句）
            StringBuilder colNames = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) colNames.append(", ");
                colNames.append(columns.get(i).get("name"));
            }
            String dml = "SELECT " + colNames + " FROM source_" + tableName + " WHERE status = 'ACTIVE';\n";

            // 4. 生成 condition 内容
            String condition = ""
                    + "/* 示例条件，按需修改 */\n"
                    + "name LIKE CONCAT('%', ${keyword}, '%')\n"
                    + "AND created_at BETWEEN ${start} AND ${end}\n";

            // 5. 打包为 zip
            byte[] zipBytes;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos)) {
                addEntry(zos, "DDL.txt", ddl.toString());
                addEntry(zos, "DML.txt", dml);
                addEntry(zos, "condition.txt", condition);
                zos.finish();
                zos.flush();
                zipBytes = baos.toByteArray();
            }

            // 6. 构造响应
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=view.ziip");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");

            ByteArrayResource resource = new ByteArrayResource(zipBytes);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(zipBytes.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        try {
            zos.putNextEntry(entry);
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            zos.write(data, 0, data.length);
        } finally {
            zos.closeEntry();
        }
    }

    private static List<Map<String, String>> mockReadDbColumns() {
        return Arrays.asList(
                col("id", "BIGINT"),
                col("name", "VARCHAR(64)"),
                col("created_at", "TIMESTAMP")
        );
    }

    private static Map<String, String> col(String name, String type) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        return m;
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private boolean valid;
        private String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
