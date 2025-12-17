package com.fragment.zip;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ZipUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 视图需求处理器 - 处理前端上传的文件
 */
public class ViewRequestProcessor {
    
    /**
     * 处理前端上传的zip文件
     * 
     * @param zipFile 前端上传的zip文件
     * @return 处理结果
     * @throws IOException 文件处理异常
     */
    public static ViewRequestResult processZipFile(MultipartFile zipFile) throws IOException {
        // 1. 验证文件
        validateZipFile(zipFile);
        
        // 2. 保存临时文件
        File tempZipFile = saveTempFile(zipFile);
        
        try {
            // 3. 解压zip文件
            File unzipDir = ZipUtil.unzip(tempZipFile);
            
            // 4. 读取并解析DDL.txt
            File ddlFile = new File(unzipDir, "DDL.txt");
            if (!ddlFile.exists()) {
                throw new IllegalArgumentException("zip文件中缺少DDL.txt文件");
            }
            String ddlContent = FileUtil.readUtf8String(ddlFile);
            
            // 5. 验证DDL中不包含删除和更新语句
            SqlValidator.validateOrThrow(ddlContent);
            
            // 6. 解析DDL
            TableInfo tableInfo = DdlParser.parseDdl(ddlContent);
            
            // 7. 读取并解析condition.txt（可选）
            List<ConditionRule> conditionRules = null;
            File conditionFile = new File(unzipDir, "condition.txt");
            if (conditionFile.exists()) {
                String conditionContent = FileUtil.readUtf8String(conditionFile);
                conditionRules = ConditionParser.parseConditionContent(conditionContent);
                
                // 8. 验证字段
                ConditionParser.validateFields(conditionRules, tableInfo);
            }
            
            // 9. 读取DML.txt（可选）
            String dmlContent = null;
            File dmlFile = new File(unzipDir, "DML.txt");
            if (dmlFile.exists()) {
                dmlContent = FileUtil.readUtf8String(dmlFile);
                // 验证DML中不包含删除和更新语句
                SqlValidator.validateOrThrow(dmlContent);
            }
            
            // 10. 构建返回结果
            ViewRequestResult result = new ViewRequestResult();
            result.setTableInfo(tableInfo);
            result.setConditionRules(conditionRules);
            result.setDdlContent(ddlContent);
            result.setDmlContent(dmlContent);
            result.setSuccess(true);
            result.setMessage("处理成功");
            
            return result;
            
        } finally {
            // 清理临时文件
            FileUtil.del(tempZipFile);
        }
    }
    
    /**
     * 验证上传的zip文件
     * 
     * @param zipFile 上传的文件
     */
    private static void validateZipFile(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("上传的文件为空");
        }
        
        String originalFilename = zipFile.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("只支持zip格式的文件");
        }
        
        // 限制文件大小（例如：10MB）
        long maxSize = 10 * 1024 * 1024;
        if (zipFile.getSize() > maxSize) {
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }
    }
    
    /**
     * 保存临时文件
     * 
     * @param multipartFile 上传的文件
     * @return 临时文件
     * @throws IOException IO异常
     */
    private static File saveTempFile(MultipartFile multipartFile) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".zip";
        
        // 创建临时文件
        File tempFile = File.createTempFile("upload_", suffix);
        multipartFile.transferTo(tempFile);
        
        return tempFile;
    }
}
