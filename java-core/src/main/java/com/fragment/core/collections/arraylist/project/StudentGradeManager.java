package com.fragment.core.collections.arraylist.project;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生成绩管理系统
 *
 * 使用 ArrayList 管理学生成绩数据，演示真实业务场景下的增删改查、排序、统计。
 */
public class StudentGradeManager {

    private final List<Student> students = new ArrayList<>();

    public static void main(String[] args) {
        StudentGradeManager manager = new StudentGradeManager();

        manager.addStudent(new Student(1L, "张三", 85.5));
        manager.addStudent(new Student(2L, "李四", 92.0));
        manager.addStudent(new Student(3L, "王五", 78.5));
        manager.addStudent(new Student(4L, "赵六", 95.5));
        manager.addStudent(new Student(5L, "钱七", 88.0));

        System.out.println("=== 全部学生 ===");
        manager.printAll();

        System.out.println("\n=== 按成绩降序 ===");
        manager.printSortedByScore();

        System.out.println("\n=== 成绩统计 ===");
        manager.printStatistics();

        System.out.println("\n=== 90分以上学生 ===");
        manager.findByMinScore(90.0).forEach(System.out::println);

        System.out.println("\n=== 更新张三成绩为91 ===");
        manager.updateScore(1L, 91.0);
        manager.findById(1L).ifPresent(System.out::println);

        System.out.println("\n=== 删除王五 ===");
        manager.removeStudent(3L);
        manager.printAll();
    }

    public void addStudent(Student student) {
        students.add(student);
    }

    public void removeStudent(Long id) {
        students.removeIf(s -> s.getId().equals(id));
    }

    public void updateScore(Long id, double newScore) {
        students.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .ifPresent(s -> s.setScore(newScore));
    }

    public Optional<Student> findById(Long id) {
        return students.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public List<Student> findByMinScore(double minScore) {
        return students.stream()
                .filter(s -> s.getScore() >= minScore)
                .collect(Collectors.toList());
    }

    public void printAll() {
        students.forEach(System.out::println);
    }

    public void printSortedByScore() {
        students.stream()
                .sorted(Comparator.comparingDouble(Student::getScore).reversed())
                .forEach(System.out::println);
    }

    public void printStatistics() {
        DoubleSummaryStatistics stats = students.stream()
                .mapToDouble(Student::getScore)
                .summaryStatistics();
        System.out.printf("人数: %d, 最高: %.1f, 最低: %.1f, 平均: %.2f%n",
                stats.getCount(), stats.getMax(), stats.getMin(), stats.getAverage());
    }

    public static class Student {
        private final Long id;
        private final String name;
        private double score;

        public Student(Long id, String name, double score) {
            this.id = id;
            this.name = name;
            this.score = score;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }

        @Override
        public String toString() {
            return String.format("Student{id=%d, name='%s', score=%.1f}", id, name, score);
        }
    }
}
