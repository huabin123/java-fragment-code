package com.fragment.core.bitwise.demo;

import java.util.BitSet;

/**
 * 位运算实战应用演示
 * 
 * 演示内容：
 * 1. 权限管理系统
 * 2. 状态管理器
 * 3. 布隆过滤器
 * 4. BitSet的使用
 * 
 * @author fragment
 */
public class BitwisePracticalDemo {
    
    public static void main(String[] args) {
        System.out.println("=== 位运算实战应用演示 ===\n");
        
        // 1. 权限管理系统
        demonstratePermissionManager();
        
        // 2. 角色权限管理
        demonstrateRolePermissionManager();
        
        // 3. 文本样式管理
        demonstrateTextStyle();
        
        // 4. 订单状态管理
        demonstrateOrderStatus();
        
        // 5. 布隆过滤器
        demonstrateBloomFilter();
        
        // 6. BitSet的使用
        demonstrateBitSet();
    }
    
    /**
     * 演示权限管理系统
     */
    private static void demonstratePermissionManager() {
        System.out.println("【1. 权限管理系统】");
        
        PermissionManager pm = new PermissionManager();
        
        // 授予读和写权限
        pm.grant(PermissionManager.READ | PermissionManager.WRITE);
        System.out.println("授予读和写权限");
        System.out.println("有读权限: " + pm.hasPermission(PermissionManager.READ));
        System.out.println("有写权限: " + pm.hasPermission(PermissionManager.WRITE));
        System.out.println("有执行权限: " + pm.hasPermission(PermissionManager.EXECUTE));
        
        // 撤销写权限
        pm.revoke(PermissionManager.WRITE);
        System.out.println("\n撤销写权限");
        System.out.println("有写权限: " + pm.hasPermission(PermissionManager.WRITE));
        
        // 切换执行权限
        pm.toggle(PermissionManager.EXECUTE);
        System.out.println("\n切换执行权限");
        System.out.println("有执行权限: " + pm.hasPermission(PermissionManager.EXECUTE));
        
        pm.toggle(PermissionManager.EXECUTE);
        System.out.println("再次切换执行权限");
        System.out.println("有执行权限: " + pm.hasPermission(PermissionManager.EXECUTE));
        System.out.println();
    }
    
    /**
     * 演示角色权限管理
     */
    private static void demonstrateRolePermissionManager() {
        System.out.println("【2. 角色权限管理】");
        
        // 创建不同角色
        RolePermissionManager guest = new RolePermissionManager(RolePermissionManager.ROLE_GUEST);
        RolePermissionManager user = new RolePermissionManager(RolePermissionManager.ROLE_USER);
        RolePermissionManager admin = new RolePermissionManager(RolePermissionManager.ROLE_ADMIN);
        
        System.out.println("访客权限:");
        guest.printPermissions();
        
        System.out.println("\n普通用户权限:");
        user.printPermissions();
        
        System.out.println("\n管理员权限:");
        admin.printPermissions();
        
        // 给管理员添加审核权限
        admin.addPermission(RolePermissionManager.AUDIT);
        System.out.println("\n给管理员添加审核权限后:");
        admin.printPermissions();
        System.out.println();
    }
    
    /**
     * 演示文本样式管理
     */
    private static void demonstrateTextStyle() {
        System.out.println("【3. 文本样式管理】");
        
        TextStyle style = new TextStyle();
        
        // 应用粗体和斜体
        style.applyStyle(TextStyle.BOLD | TextStyle.ITALIC);
        System.out.println("应用粗体和斜体:");
        System.out.println(style.toHtml("Hello World"));
        
        // 添加下划线
        style.applyStyle(TextStyle.UNDERLINE);
        System.out.println("\n添加下划线:");
        System.out.println(style.toHtml("Hello World"));
        
        // 移除斜体
        style.removeStyle(TextStyle.ITALIC);
        System.out.println("\n移除斜体:");
        System.out.println(style.toHtml("Hello World"));
        
        // 切换删除线
        style.toggleStyle(TextStyle.STRIKEOUT);
        System.out.println("\n切换删除线:");
        System.out.println(style.toHtml("Hello World"));
        System.out.println();
    }
    
    /**
     * 演示订单状态管理
     */
    private static void demonstrateOrderStatus() {
        System.out.println("【4. 订单状态管理】");
        
        OrderStatus order = new OrderStatus();
        System.out.println("初始状态: " + order.getStatusDescription());
        
        order.pay();
        System.out.println("支付后: " + order.getStatusDescription());
        
        order.ship();
        System.out.println("发货后: " + order.getStatusDescription());
        
        order.complete();
        System.out.println("完成后: " + order.getStatusDescription());
        
        // 创建另一个订单并取消
        OrderStatus order2 = new OrderStatus();
        order2.cancel();
        System.out.println("\n取消的订单: " + order2.getStatusDescription());
        System.out.println();
    }
    
    /**
     * 演示布隆过滤器
     */
    private static void demonstrateBloomFilter() {
        System.out.println("【5. 布隆过滤器】");
        
        BloomFilter filter = new BloomFilter(1000, 3);
        
        // 添加元素
        filter.add("apple");
        filter.add("banana");
        filter.add("orange");
        
        // 检查元素
        System.out.println("apple 可能存在: " + filter.mightContain("apple"));
        System.out.println("banana 可能存在: " + filter.mightContain("banana"));
        System.out.println("grape 可能存在: " + filter.mightContain("grape"));
        
        System.out.println("\n注意：布隆过滤器可能有误判（false positive），但不会漏判（false negative）");
        System.out.println("应用场景：网页URL去重、垃圾邮件过滤、缓存穿透防护");
        System.out.println();
    }
    
    /**
     * 演示BitSet的使用
     */
    private static void demonstrateBitSet() {
        System.out.println("【6. BitSet的使用】");
        
        BitSet bs1 = new BitSet();
        bs1.set(1);
        bs1.set(3);
        bs1.set(5);
        
        BitSet bs2 = new BitSet();
        bs2.set(2);
        bs2.set(3);
        bs2.set(4);
        
        System.out.println("bs1: " + bs1);
        System.out.println("bs2: " + bs2);
        
        // 并集
        BitSet union = (BitSet) bs1.clone();
        union.or(bs2);
        System.out.println("\n并集 (bs1 ∪ bs2): " + union);
        
        // 交集
        BitSet intersection = (BitSet) bs1.clone();
        intersection.and(bs2);
        System.out.println("交集 (bs1 ∩ bs2): " + intersection);
        
        // 差集
        BitSet difference = (BitSet) bs1.clone();
        difference.andNot(bs2);
        System.out.println("差集 (bs1 - bs2): " + difference);
        
        // 异或
        BitSet xor = (BitSet) bs1.clone();
        xor.xor(bs2);
        System.out.println("异或 (bs1 △ bs2): " + xor);
        
        System.out.println("\n内存节省：");
        System.out.println("传统方法（boolean[]）：1000个布尔值 = 1000字节");
        System.out.println("BitSet：1000个布尔值 ≈ 125字节（节省87.5%）");
    }
}

/**
 * 权限管理器
 */
class PermissionManager {
    // 定义权限（每个权限占一位）
    public static final int READ    = 1 << 0;  // 0001 = 1
    public static final int WRITE   = 1 << 1;  // 0010 = 2
    public static final int EXECUTE = 1 << 2;  // 0100 = 4
    public static final int DELETE  = 1 << 3;  // 1000 = 8
    
    private int permission = 0;  // 用户的权限
    
    /**
     * 授予权限
     */
    public void grant(int perm) {
        permission |= perm;
    }
    
    /**
     * 撤销权限
     */
    public void revoke(int perm) {
        permission &= ~perm;
    }
    
    /**
     * 检查权限
     */
    public boolean hasPermission(int perm) {
        return (permission & perm) == perm;
    }
    
    /**
     * 切换权限
     */
    public void toggle(int perm) {
        permission ^= perm;
    }
    
    /**
     * 清空所有权限
     */
    public void clear() {
        permission = 0;
    }
    
    /**
     * 获取所有权限
     */
    public int getPermission() {
        return permission;
    }
}

/**
 * 角色权限管理器
 */
class RolePermissionManager {
    // 定义权限
    public static final int VIEW   = 1 << 0;  // 查看
    public static final int EDIT   = 1 << 1;  // 编辑
    public static final int DELETE = 1 << 2;  // 删除
    public static final int AUDIT  = 1 << 3;  // 审核
    public static final int EXPORT = 1 << 4;  // 导出
    
    // 定义角色（角色是权限的组合）
    public static final int ROLE_GUEST  = VIEW;                           // 访客
    public static final int ROLE_USER   = VIEW | EDIT;                    // 普通用户
    public static final int ROLE_ADMIN  = VIEW | EDIT | DELETE | EXPORT;  // 管理员
    public static final int ROLE_SUPER  = VIEW | EDIT | DELETE | AUDIT | EXPORT;  // 超级管理员
    
    private int permission;
    
    public RolePermissionManager(int role) {
        this.permission = role;
    }
    
    /**
     * 检查是否有某个权限
     */
    public boolean can(int perm) {
        return (permission & perm) == perm;
    }
    
    /**
     * 添加额外权限
     */
    public void addPermission(int perm) {
        permission |= perm;
    }
    
    /**
     * 移除权限
     */
    public void removePermission(int perm) {
        permission &= ~perm;
    }
    
    /**
     * 打印权限
     */
    public void printPermissions() {
        if (can(VIEW))   System.out.println("  - 查看");
        if (can(EDIT))   System.out.println("  - 编辑");
        if (can(DELETE)) System.out.println("  - 删除");
        if (can(AUDIT))  System.out.println("  - 审核");
        if (can(EXPORT)) System.out.println("  - 导出");
    }
}

/**
 * 文本样式管理器
 */
class TextStyle {
    // 定义样式
    public static final int BOLD      = 1 << 0;  // 粗体
    public static final int ITALIC    = 1 << 1;  // 斜体
    public static final int UNDERLINE = 1 << 2;  // 下划线
    public static final int STRIKEOUT = 1 << 3;  // 删除线
    
    private int style = 0;
    
    /**
     * 应用样式
     */
    public void applyStyle(int s) {
        style |= s;
    }
    
    /**
     * 移除样式
     */
    public void removeStyle(int s) {
        style &= ~s;
    }
    
    /**
     * 切换样式
     */
    public void toggleStyle(int s) {
        style ^= s;
    }
    
    /**
     * 检查样式
     */
    public boolean hasStyle(int s) {
        return (style & s) == s;
    }
    
    /**
     * 清空样式
     */
    public void clearStyle() {
        style = 0;
    }
    
    /**
     * 获取HTML标签
     */
    public String toHtml(String text) {
        StringBuilder sb = new StringBuilder(text);
        if (hasStyle(BOLD))      sb.insert(0, "<b>").append("</b>");
        if (hasStyle(ITALIC))    sb.insert(0, "<i>").append("</i>");
        if (hasStyle(UNDERLINE)) sb.insert(0, "<u>").append("</u>");
        if (hasStyle(STRIKEOUT)) sb.insert(0, "<s>").append("</s>");
        return sb.toString();
    }
}

/**
 * 订单状态管理器
 */
class OrderStatus {
    // 定义状态
    public static final int UNPAID    = 1 << 0;  // 待支付
    public static final int PAID      = 1 << 1;  // 已支付
    public static final int SHIPPED   = 1 << 2;  // 已发货
    public static final int COMPLETED = 1 << 3;  // 已完成
    public static final int CANCELLED = 1 << 4;  // 已取消
    
    private int status;
    
    public OrderStatus() {
        this.status = UNPAID;  // 初始状态：待支付
    }
    
    /**
     * 支付
     */
    public void pay() {
        if (hasStatus(UNPAID)) {
            status &= ~UNPAID;  // 移除待支付状态
            status |= PAID;     // 添加已支付状态
        }
    }
    
    /**
     * 发货
     */
    public void ship() {
        if (hasStatus(PAID)) {
            status |= SHIPPED;
        }
    }
    
    /**
     * 完成
     */
    public void complete() {
        if (hasStatus(SHIPPED)) {
            status |= COMPLETED;
        }
    }
    
    /**
     * 取消
     */
    public void cancel() {
        status = CANCELLED;
    }
    
    /**
     * 检查状态
     */
    public boolean hasStatus(int s) {
        return (status & s) == s;
    }
    
    /**
     * 获取状态描述
     */
    public String getStatusDescription() {
        if (hasStatus(CANCELLED)) return "已取消";
        if (hasStatus(COMPLETED)) return "已完成";
        if (hasStatus(SHIPPED))   return "已发货";
        if (hasStatus(PAID))      return "已支付";
        if (hasStatus(UNPAID))    return "待支付";
        return "未知状态";
    }
}

/**
 * 布隆过滤器
 */
class BloomFilter {
    private BitSet bitSet;
    private int size;
    private int hashFunctionCount;
    
    public BloomFilter(int size, int hashFunctionCount) {
        this.size = size;
        this.hashFunctionCount = hashFunctionCount;
        this.bitSet = new BitSet(size);
    }
    
    /**
     * 添加元素
     */
    public void add(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            bitSet.set(hash % size);
        }
    }
    
    /**
     * 检查元素是否存在
     */
    public boolean mightContain(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int hash = hash(element, i);
            if (!bitSet.get(hash % size)) {
                return false;  // 一定不存在
            }
        }
        return true;  // 可能存在
    }
    
    /**
     * 哈希函数
     */
    private int hash(String element, int seed) {
        int hash = 0;
        for (char c : element.toCharArray()) {
            hash = hash * 31 + c + seed;
        }
        return Math.abs(hash);
    }
}
