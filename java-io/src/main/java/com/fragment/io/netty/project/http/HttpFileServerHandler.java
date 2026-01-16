package com.fragment.io.netty.project.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * HTTP文件服务器处理器
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class HttpFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    
    private final String fileRoot;
    
    public HttpFileServerHandler(String fileRoot) {
        this.fileRoot = fileRoot;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        // 只支持GET方法
        if (request.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        
        // 获取请求路径
        String uri = request.uri();
        String path = sanitizeUri(uri);
        
        if (path == null) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }
        
        File file = new File(path);
        
        // 检查文件是否存在
        if (!file.exists()) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        
        // 如果是目录，列出文件
        if (file.isDirectory()) {
            sendListing(ctx, file, uri);
            return;
        }
        
        // 如果不是普通文件
        if (!file.isFile()) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }
        
        // 发送文件
        sendFile(ctx, file);
    }
    
    /**
     * 发送文件
     */
    private void sendFile(ChannelHandlerContext ctx, File file) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        long fileLength = raf.length();
        
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        
        ctx.write(response);
        
        // 使用零拷贝发送文件
        ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
        
        // 发送结束标记
        ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        future.addListener(ChannelFutureListener.CLOSE);
        
        System.out.println("[下载] " + file.getName() + " (" + fileLength + " 字节)");
    }
    
    /**
     * 发送目录列表
     */
    private void sendListing(ChannelHandlerContext ctx, File dir, String uri) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        
        StringBuilder buf = new StringBuilder();
        buf.append("<!DOCTYPE html>\r\n");
        buf.append("<html><head><meta charset='UTF-8'><title>文件列表</title>");
        buf.append("<style>");
        buf.append("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }");
        buf.append("h1 { color: #333; }");
        buf.append("table { width: 100%; border-collapse: collapse; background: white; }");
        buf.append("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }");
        buf.append("th { background: #667eea; color: white; }");
        buf.append("tr:hover { background: #f5f5f5; }");
        buf.append("a { color: #667eea; text-decoration: none; }");
        buf.append("a:hover { text-decoration: underline; }");
        buf.append(".dir { color: #ff9800; font-weight: bold; }");
        buf.append(".file { color: #4caf50; }");
        buf.append("</style>");
        buf.append("</head><body>\r\n");
        
        String dirPath = uri.equals("/") ? "/" : uri;
        buf.append("<h1>📁 ").append(dirPath).append("</h1>\r\n");
        
        buf.append("<table>\r\n");
        buf.append("<tr><th>名称</th><th>大小</th><th>类型</th></tr>\r\n");
        
        // 返回上级目录
        if (!uri.equals("/")) {
            String parent = uri.substring(0, uri.lastIndexOf('/'));
            if (parent.isEmpty()) parent = "/";
            buf.append("<tr><td><a href='").append(parent).append("'>📁 ..</a></td>");
            buf.append("<td>-</td><td>目录</td></tr>\r\n");
        }
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String link = uri.endsWith("/") ? uri + name : uri + "/" + name;
                
                if (file.isDirectory()) {
                    buf.append("<tr><td class='dir'><a href='").append(link).append("'>📁 ")
                        .append(name).append("</a></td>");
                    buf.append("<td>-</td><td>目录</td></tr>\r\n");
                } else {
                    buf.append("<tr><td class='file'><a href='").append(link).append("'>📄 ")
                        .append(name).append("</a></td>");
                    buf.append("<td>").append(formatFileSize(file.length())).append("</td>");
                    buf.append("<td>文件</td></tr>\r\n");
                }
            }
        }
        
        buf.append("</table>\r\n");
        buf.append("</body></html>\r\n");
        
        response.content().writeBytes(Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8));
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
    
    /**
     * 清理URI
     */
    private String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
        
        // 移除查询参数
        int queryIndex = uri.indexOf('?');
        if (queryIndex > 0) {
            uri = uri.substring(0, queryIndex);
        }
        
        // 安全检查：防止目录遍历攻击
        if (uri.contains("..") || uri.contains("./") || uri.contains("/.")) {
            return null;
        }
        
        return fileRoot + File.separator + uri.replace('/', File.separatorChar);
    }
    
    /**
     * 设置Content-Type
     */
    private void setContentTypeHeader(HttpResponse response, File file) {
        String fileName = file.getName().toLowerCase();
        
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        } else if (fileName.endsWith(".txt")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        } else if (fileName.endsWith(".css")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/css; charset=UTF-8");
        } else if (fileName.endsWith(".js")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/javascript; charset=UTF-8");
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/jpeg");
        } else if (fileName.endsWith(".png")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
        } else if (fileName.endsWith(".gif")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/gif");
        } else if (fileName.endsWith(".pdf")) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/pdf");
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status,
            Unpooled.copiedBuffer("错误: " + status + "\r\n", CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[异常] " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}
