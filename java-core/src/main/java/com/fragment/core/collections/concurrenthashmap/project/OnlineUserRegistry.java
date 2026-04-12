package com.fragment.core.collections.concurrenthashmap.project;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线用户注册表
 *
 * 使用 ConcurrentHashMap 实现高并发在线用户管理：
 * 登录、登出、心跳更新、在线状态查询。
 */
public class OnlineUserRegistry {

    // userId → UserSession
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    // 利用 ConcurrentHashMap 的 keySet 作为并发 Set
    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        OnlineUserRegistry registry = new OnlineUserRegistry();

        // 模拟多用户登录
        registry.login("user001", "192.168.1.1");
        registry.login("user002", "192.168.1.2");
        registry.login("user003", "192.168.1.3");

        System.out.println("在线人数: " + registry.onlineCount());
        System.out.println("user001 在线: " + registry.isOnline("user001"));

        // 心跳更新
        registry.heartbeat("user001");
        System.out.println("user001 最后活跃: " + registry.getLastActive("user001"));

        // 登出
        registry.logout("user002");
        System.out.println("user002 登出后在线人数: " + registry.onlineCount());

        // 并发登录同一账号（后登录覆盖旧 Session）
        registry.login("user001", "192.168.1.100");  // 换 IP 重新登录
        System.out.println("user001 新 IP: " + registry.getSession("user001").getIp());

        System.out.println("\n当前所有在线用户: " + registry.onlineUsers);
    }

    public void login(String userId, String ip) {
        UserSession session = new UserSession(userId, ip, LocalDateTime.now());
        sessions.put(userId, session);
        onlineUsers.add(userId);
        System.out.println("[登录] " + userId + " from " + ip);
    }

    public void logout(String userId) {
        sessions.remove(userId);
        onlineUsers.remove(userId);
        System.out.println("[登出] " + userId);
    }

    public void heartbeat(String userId) {
        // computeIfPresent：只有 key 存在时才更新，原子操作
        sessions.computeIfPresent(userId, (id, session) -> {
            session.updateLastActive();
            return session;
        });
    }

    public boolean isOnline(String userId) {
        return onlineUsers.contains(userId);
    }

    public int onlineCount() {
        return onlineUsers.size();
    }

    public UserSession getSession(String userId) {
        return sessions.get(userId);
    }

    public LocalDateTime getLastActive(String userId) {
        UserSession s = sessions.get(userId);
        return s != null ? s.getLastActive() : null;
    }

    public static class UserSession {
        private final String userId;
        private final String ip;
        private final LocalDateTime loginTime;
        private volatile LocalDateTime lastActive;

        public UserSession(String userId, String ip, LocalDateTime loginTime) {
            this.userId = userId;
            this.ip = ip;
            this.loginTime = loginTime;
            this.lastActive = loginTime;
        }

        public void updateLastActive() {
            this.lastActive = LocalDateTime.now();
        }

        public String getIp() { return ip; }
        public LocalDateTime getLastActive() { return lastActive; }

        @Override
        public String toString() {
            return "Session{user=" + userId + ", ip=" + ip + ", lastActive=" + lastActive + "}";
        }
    }
}
