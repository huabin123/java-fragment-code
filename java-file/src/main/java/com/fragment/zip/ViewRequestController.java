package com.fragment.zip;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
