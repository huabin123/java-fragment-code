package com.fragment.core.collections.treemap.project;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 日程管理器
 *
 * 使用 TreeMap 按时间顺序管理日程，支持：
 * 1. 添加/删除日程
 * 2. 查询某时刻之后的下一个日程
 * 3. 查询某时间段内的所有日程
 * 4. 检测时间冲突
 */
public class ScheduleManager {

    private final TreeMap<LocalDateTime, Schedule> schedules = new TreeMap<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static void main(String[] args) {
        ScheduleManager manager = new ScheduleManager();
        LocalDateTime base = LocalDateTime.of(2026, 4, 10, 9, 0);

        manager.add(new Schedule(base.plusHours(0), base.plusHours(1),  "晨会"));
        manager.add(new Schedule(base.plusHours(2), base.plusHours(3),  "需求评审"));
        manager.add(new Schedule(base.plusHours(4), base.plusHours(5),  "代码评审"));
        manager.add(new Schedule(base.plusHours(7), base.plusHours(8),  "下午例会"));
        manager.add(new Schedule(base.plusHours(9), base.plusHours(10), "1-on-1"));

        System.out.println("=== 今日日程 ===");
        manager.printAll();

        LocalDateTime now = base.plusHours(3).plusMinutes(30);
        System.out.println("\n=== 当前时间: " + now.format(FMT) + " ===");
        manager.nextSchedule(now).ifPresent(s ->
            System.out.println("下一个日程: " + s));

        System.out.println("\n=== 10:00~15:00 的日程 ===");
        manager.getInRange(base.plusHours(1), base.plusHours(6))
               .forEach(System.out::println);

        System.out.println("\n=== 检测冲突 ===");
        Schedule conflict = new Schedule(base.plusHours(2).plusMinutes(30),
                                         base.plusHours(3).plusMinutes(30), "临时会议");
        System.out.println("添加 " + conflict.title + ": " +
            (manager.hasConflict(conflict) ? "❌ 有冲突" : "✅ 无冲突"));
    }

    public void add(Schedule schedule) {
        schedules.put(schedule.start, schedule);
    }

    public void remove(LocalDateTime start) {
        schedules.remove(start);
    }

    public Optional<Schedule> nextSchedule(LocalDateTime after) {
        Map.Entry<LocalDateTime, Schedule> entry = schedules.higherEntry(after);
        return Optional.ofNullable(entry).map(Map.Entry::getValue);
    }

    public List<Schedule> getInRange(LocalDateTime from, LocalDateTime to) {
        return new ArrayList<>(schedules.subMap(from, true, to, true).values());
    }

    public boolean hasConflict(Schedule newSchedule) {
        // 找到新日程开始时间之前的最后一个日程
        Map.Entry<LocalDateTime, Schedule> before = schedules.floorEntry(newSchedule.start);
        if (before != null && before.getValue().end.isAfter(newSchedule.start)) return true;

        // 找到新日程开始时间之后的第一个日程
        Map.Entry<LocalDateTime, Schedule> after = schedules.ceilingEntry(newSchedule.start);
        if (after != null && after.getKey().isBefore(newSchedule.end)) return true;

        return false;
    }

    public void printAll() {
        schedules.values().forEach(System.out::println);
    }

    public static class Schedule {
        final LocalDateTime start;
        final LocalDateTime end;
        final String title;

        public Schedule(LocalDateTime start, LocalDateTime end, String title) {
            this.start = start;
            this.end = end;
            this.title = title;
        }

        @Override
        public String toString() {
            return String.format("%s ~ %s  %s", start.format(FMT), end.format(FMT), title);
        }
    }
}
