package com.fragment.core.collections.hashmap.project;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户会话管理器
 * 
 * 使用ConcurrentHashMap管理用户会话
 * 
 * 功能：
 * 1. 创建会话
 * 2. 验证会话
 * 3. 销毁会话
 * 4. 会话过期检查
 * 5. 线程安全
 * 
 * @author huabin
 */
public class UserSessionManager {
    
    // 使用ConcurrentHashMap保证线程安全
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    // 会话过期时间（毫秒）
    private final long sessionTimeout;
    
    /**
     * 构造函数
     * 
     * @param sessionTimeout 会话过期时间（毫秒）
     */
    public UserSessionManager(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        // 启动后台线程，定期清理过期会话
        startCleanupTask();
    }

    /**
     * 创建会话
     * 
     * @param userId 用户ID
     * @return 会话ID
     */
    public String createSession(String userId) {
        String sessionId = generateSessionId();
        Session session = new Session(userId, System.currentTimeMillis());
        sessions.put(sessionId, session);
        System.out.println("[Session] 创建会话: userId=" + userId + ", sessionId=" + sessionId);
        return sessionId;
    }

    /**
     * 验证会话
     * 
     * @param sessionId 会话ID
     * @return 用户ID，如果会话无效返回null
     */
    public String validateSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            System.out.println("[Session] 会话不存在: sessionId=" + sessionId);
            return null;
        }
        
        // 检查是否过期
        long age = System.currentTimeMillis() - session.createTime;
        if (age > sessionTimeout) {
            System.out.println("[Session] 会话已过期: sessionId=" + sessionId + ", age=" + age + "ms");
            sessions.remove(sessionId);
            return null;
        }
        
        // 更新最后访问时间
        session.lastAccessTime = System.currentTimeMillis();
        System.out.println("[Session] 会话有效: userId=" + session.userId + ", sessionId=" + sessionId);
        return session.userId;
    }

    /**
     * 销毁会话
     * 
     * @param sessionId 会话ID
     */
    public void destroySession(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session != null) {
            System.out.println("[Session] 销毁会话: userId=" + session.userId + ", sessionId=" + sessionId);
        }
    }

    /**
     * 获取会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int count = 0;
        
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            long age = now - session.lastAccessTime;
            if (age > sessionTimeout) {
                sessions.remove(entry.getKey());
                count++;
                System.out.println("[Session] 清理过期会话: userId=" + session.userId + 
                                 ", sessionId=" + entry.getKey() + ", age=" + age + "ms");
            }
        }
        
        if (count > 0) {
            System.out.println("[Session] 清理完成，共清理 " + count + " 个过期会话");
        }
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 启动后台清理任务
     */
    private void startCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(sessionTimeout / 2);  // 每半个过期时间清理一次
                    cleanupExpiredSessions();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("SessionCleanupThread");
        cleanupThread.start();
    }

    /**
     * 会话类
     */
    private static class Session {
        String userId;
        long createTime;
        long lastAccessTime;

        Session(String userId, long createTime) {
            this.userId = userId;
            this.createTime = createTime;
            this.lastAccessTime = createTime;
        }
    }

    /**
     * 测试示例
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 用户会话管理器测试 ==========\n");
        
        // 创建会话管理器，过期时间5秒
        UserSessionManager manager = new UserSessionManager(5000);
        
        // 创建会话
        String sessionId1 = manager.createSession("user001");
        String sessionId2 = manager.createSession("user002");
        String sessionId3 = manager.createSession("user003");
        
        System.out.println("\n当前会话数: " + manager.getSessionCount());
        
        // 验证会话
        System.out.println("\n验证会话:");
        manager.validateSession(sessionId1);
        manager.validateSession(sessionId2);
        manager.validateSession("invalid-session-id");
        
        // 等待3秒
        System.out.println("\n等待3秒...");
        Thread.sleep(3000);
        
        // 再次验证（会更新lastAccessTime）
        System.out.println("\n再次验证会话:");
        manager.validateSession(sessionId1);
        
        // 等待4秒（sessionId2和sessionId3会过期）
        System.out.println("\n等待4秒...");
        Thread.sleep(4000);
        
        // 验证会话（sessionId1仍然有效，sessionId2和sessionId3已过期）
        System.out.println("\n验证会话:");
        manager.validateSession(sessionId1);  // 有效
        manager.validateSession(sessionId2);  // 过期
        manager.validateSession(sessionId3);  // 过期
        
        System.out.println("\n当前会话数: " + manager.getSessionCount());
        
        // 销毁会话
        System.out.println("\n销毁会话:");
        manager.destroySession(sessionId1);
        
        System.out.println("\n当前会话数: " + manager.getSessionCount());
        
        // 等待清理任务执行
        System.out.println("\n等待清理任务执行...");
        Thread.sleep(3000);
        
        System.out.println("\n测试完成");
    }
}
