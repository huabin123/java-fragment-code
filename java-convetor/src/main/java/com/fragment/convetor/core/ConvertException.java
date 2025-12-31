package com.fragment.convetor.core;

/**
 * 转换异常
 * 
 * @author fragment
 */
public class ConvertException extends RuntimeException {
    
    public ConvertException(String message) {
        super(message);
    }
    
    public ConvertException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ConvertException(Throwable cause) {
        super(cause);
    }
}
