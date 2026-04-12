package com.fragment.core.collections.set.project;

import java.util.*;

/**
 * 访问控制列表（ACL）
 *
 * 使用 Set 实现基于角色的权限控制（RBAC）：
 * 1. 角色与权限绑定
 * 2. 用户与角色绑定
 * 3. 权限检查
 * 4. 权限继承（角色继承）
 */
public class AccessControlList {

    // 角色 → 权限集合
    private final Map<String, Set<String>> rolePermissions = new HashMap<>();
    // 角色 → 父角色（继承）
    private final Map<String, String> roleParent = new HashMap<>();
    // 用户 → 角色集合
    private final Map<String, Set<String>> userRoles = new HashMap<>();

    public static void main(String[] args) {
        AccessControlList acl = new AccessControlList();

        // 定义角色权限体系
        acl.defineRole("guest",  "READ_ARTICLE", "READ_COMMENT");
        acl.defineRole("member", "READ_ARTICLE", "READ_COMMENT", "WRITE_COMMENT", "LIKE_ARTICLE");
        acl.defineRole("editor", "READ_ARTICLE", "READ_COMMENT", "WRITE_COMMENT",
                                 "WRITE_ARTICLE", "EDIT_ARTICLE", "DELETE_COMMENT");
        acl.defineRole("admin",  "*");  // 超级权限

        // 角色继承：editor 继承 member 的权限
        acl.setParentRole("editor", "member");

        // 分配用户角色
        acl.assignRole("alice", "admin");
        acl.assignRole("bob",   "editor");
        acl.assignRole("carol", "member");
        acl.assignRole("dave",  "guest");

        // 权限检查
        System.out.println("=== 权限检查 ===");
        String[] users = {"alice", "bob", "carol", "dave"};
        String[] perms = {"WRITE_ARTICLE", "DELETE_COMMENT", "WRITE_COMMENT", "READ_ARTICLE"};

        for (String user : users) {
            System.out.print(user + ": ");
            for (String perm : perms) {
                System.out.print(perm + "=" + (acl.hasPermission(user, perm) ? "✅" : "❌") + " ");
            }
            System.out.println();
        }

        System.out.println("\n=== bob 的所有权限 ===");
        System.out.println(new TreeSet<>(acl.getAllPermissions("bob")));

        System.out.println("\n=== 有 WRITE_ARTICLE 权限的用户 ===");
        System.out.println(acl.getUsersWithPermission("WRITE_ARTICLE"));
    }

    public void defineRole(String role, String... permissions) {
        rolePermissions.put(role, new HashSet<>(Arrays.asList(permissions)));
    }

    public void setParentRole(String role, String parent) {
        roleParent.put(role, parent);
    }

    public void assignRole(String userId, String... roles) {
        userRoles.computeIfAbsent(userId, k -> new HashSet<>())
                 .addAll(Arrays.asList(roles));
    }

    public boolean hasPermission(String userId, String permission) {
        Set<String> roles = userRoles.getOrDefault(userId, Collections.emptySet());
        for (String role : roles) {
            if (roleHasPermission(role, permission)) return true;
        }
        return false;
    }

    private boolean roleHasPermission(String role, String permission) {
        Set<String> perms = rolePermissions.getOrDefault(role, Collections.emptySet());
        if (perms.contains("*") || perms.contains(permission)) return true;
        // 检查父角色
        String parent = roleParent.get(role);
        return parent != null && roleHasPermission(parent, permission);
    }

    public Set<String> getAllPermissions(String userId) {
        Set<String> allPerms = new LinkedHashSet<>();
        Set<String> roles = userRoles.getOrDefault(userId, Collections.emptySet());
        for (String role : roles) {
            collectPermissions(role, allPerms);
        }
        return allPerms;
    }

    private void collectPermissions(String role, Set<String> result) {
        result.addAll(rolePermissions.getOrDefault(role, Collections.emptySet()));
        String parent = roleParent.get(role);
        if (parent != null) collectPermissions(parent, result);
    }

    public Set<String> getUsersWithPermission(String permission) {
        Set<String> result = new TreeSet<>();
        userRoles.forEach((user, roles) -> {
            if (hasPermission(user, permission)) result.add(user);
        });
        return result;
    }
}
