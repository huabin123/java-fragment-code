package com.fragment.io.netty.project.rpc;

import java.io.Serializable;

/**
 * RPC响应对象
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class RpcResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String requestId;    // 请求ID
    private Object result;       // 返回值
    private Throwable error;     // 异常信息
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public Object getResult() {
        return result;
    }
    
    public void setResult(Object result) {
        this.result = result;
    }
    
    public Throwable getError() {
        return error;
    }
    
    public void setError(Throwable error) {
        this.error = error;
    }
    
    public boolean isSuccess() {
        return error == null;
    }
    
    @Override
    public String toString() {
        return "RpcResponse{" +
                "requestId='" + requestId + '\'' +
                ", result=" + result +
                ", error=" + error +
                '}';
    }
}
