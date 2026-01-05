#!/bin/bash

################################################################################
# GC日志分析脚本
# 功能：分析GC日志，提供详细的GC统计和优化建议
# 作者：Java Fragment Code
# 版本：1.0
################################################################################

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 默认配置
GC_LOG_FILE=""
OUTPUT_FILE=""
DETAIL_MODE=0

# 打印帮助信息
print_help() {
    echo "GC日志分析脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -f FILE         指定GC日志文件（必需）"
    echo "  -o FILE         输出分析结果到文件"
    echo "  -d              详细模式，显示更多信息"
    echo "  -h              显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -f gc.log                    # 分析gc.log文件"
    echo "  $0 -f gc.log -d                 # 详细分析"
    echo "  $0 -f gc.log -o report.txt      # 输出到文件"
    echo ""
    echo "生成GC日志的JVM参数示例:"
    echo "  -XX:+PrintGCDetails"
    echo "  -XX:+PrintGCDateStamps"
    echo "  -XX:+PrintGCTimeStamps"
    echo "  -Xloggc:gc.log"
    echo ""
}

# 解析命令行参数
while getopts "f:o:dh" opt; do
    case $opt in
        f) GC_LOG_FILE=$OPTARG ;;
        o) OUTPUT_FILE=$OPTARG ;;
        d) DETAIL_MODE=1 ;;
        h) print_help; exit 0 ;;
        *) print_help; exit 1 ;;
    esac
done

# 检查GC日志文件是否提供
if [ -z "$GC_LOG_FILE" ]; then
    echo -e "${RED}错误: 必须指定GC日志文件${NC}"
    echo ""
    print_help
    exit 1
fi

# 检查文件是否存在
if [ ! -f "$GC_LOG_FILE" ]; then
    echo -e "${RED}错误: 文件 $GC_LOG_FILE 不存在${NC}"
    exit 1
fi

# 输出函数
output() {
    if [ -n "$OUTPUT_FILE" ]; then
        echo "$1" | tee -a "$OUTPUT_FILE"
    else
        echo -e "$1"
    fi
}

# 打印分隔线
print_separator() {
    output "================================================================================"
}

# 打印标题
print_title() {
    output ""
    output "${GREEN}==================== $1 ====================${NC}"
    output ""
}

# 分析基本信息
analyze_basic_info() {
    print_title "基本信息"
    
    output "${BLUE}文件信息${NC}"
    output "文件路径: $GC_LOG_FILE"
    output "文件大小: $(du -h "$GC_LOG_FILE" | awk '{print $1}')"
    output "总行数:   $(wc -l < "$GC_LOG_FILE")"
    
    # 检测GC日志格式
    if grep -q "PrintGCDetails" "$GC_LOG_FILE" 2>/dev/null; then
        output "日志格式: 详细模式"
    else
        output "日志格式: 简单模式"
    fi
    
    # 获取时间范围
    local first_timestamp=$(grep -oP '\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}' "$GC_LOG_FILE" | head -1)
    local last_timestamp=$(grep -oP '\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}' "$GC_LOG_FILE" | tail -1)
    
    if [ -n "$first_timestamp" ]; then
        output "开始时间: $first_timestamp"
        output "结束时间: $last_timestamp"
    fi
    
    print_separator
}

# 分析Young GC
analyze_young_gc() {
    print_title "Young GC分析"
    
    # 统计Young GC次数
    local young_gc_count=$(grep -c "GC pause (young)" "$GC_LOG_FILE" 2>/dev/null || \
                          grep -c "\[GC (Allocation Failure)" "$GC_LOG_FILE" 2>/dev/null || \
                          grep -c "PSYoungGen" "$GC_LOG_FILE" 2>/dev/null)
    
    if [ "$young_gc_count" -eq 0 ]; then
        output "${YELLOW}未检测到Young GC记录${NC}"
        print_separator
        return
    fi
    
    output "${BLUE}Young GC统计${NC}"
    output "Young GC次数: $young_gc_count"
    
    # 提取Young GC时间（这里使用简化的正则，实际可能需要根据具体日志格式调整）
    local total_time=0
    local max_time=0
    local min_time=999999
    
    # 尝试提取GC暂停时间
    while IFS= read -r line; do
        # 匹配类似 "0.123 secs" 或 "123ms" 的模式
        local time=$(echo "$line" | grep -oP '\d+\.\d+(?= secs)' | head -1)
        
        if [ -z "$time" ]; then
            time=$(echo "$line" | grep -oP '\d+(?=ms)' | head -1)
            if [ -n "$time" ]; then
                time=$(awk "BEGIN {printf \"%.3f\", $time/1000}")
            fi
        fi
        
        if [ -n "$time" ]; then
            total_time=$(awk "BEGIN {printf \"%.3f\", $total_time + $time}")
            
            if (( $(echo "$time > $max_time" | bc -l) )); then
                max_time=$time
            fi
            
            if (( $(echo "$time < $min_time" | bc -l) )); then
                min_time=$time
            fi
        fi
    done < <(grep -E "GC pause|GC \(Allocation Failure\)|PSYoungGen" "$GC_LOG_FILE")
    
    if (( $(echo "$total_time > 0" | bc -l) )); then
        local avg_time=$(awk "BEGIN {printf \"%.3f\", $total_time / $young_gc_count}")
        
        output "总暂停时间: ${total_time}s"
        output "平均暂停:   ${avg_time}s (${avg_time}000ms)"
        output "最大暂停:   ${max_time}s (${max_time}000ms)"
        output "最小暂停:   ${min_time}s (${min_time}000ms)"
        
        # 性能评估
        output ""
        output "${CYAN}性能评估${NC}"
        
        if (( $(echo "$avg_time < 0.05" | bc -l) )); then
            output "${GREEN}✓ Young GC平均暂停时间优秀 (<50ms)${NC}"
        elif (( $(echo "$avg_time < 0.1" | bc -l) )); then
            output "${GREEN}✓ Young GC平均暂停时间良好 (<100ms)${NC}"
        elif (( $(echo "$avg_time < 0.2" | bc -l) )); then
            output "${YELLOW}⚠ Young GC平均暂停时间一般 (<200ms)${NC}"
        else
            output "${RED}✗ Young GC平均暂停时间过长 (>200ms)${NC}"
        fi
        
        if (( $(echo "$max_time > 0.5" | bc -l) )); then
            output "${RED}✗ 存在超过500ms的Young GC暂停${NC}"
        fi
    fi
    
    print_separator
}

# 分析Full GC
analyze_full_gc() {
    print_title "Full GC分析"
    
    # 统计Full GC次数
    local full_gc_count=$(grep -c "Full GC" "$GC_LOG_FILE" 2>/dev/null || \
                         grep -c "GC pause (young) (initial-mark)" "$GC_LOG_FILE" 2>/dev/null)
    
    output "${BLUE}Full GC统计${NC}"
    output "Full GC次数: $full_gc_count"
    
    if [ "$full_gc_count" -eq 0 ]; then
        output "${GREEN}✓ 未发生Full GC，表现良好${NC}"
        print_separator
        return
    fi
    
    # 提取Full GC时间
    local total_time=0
    local max_time=0
    
    while IFS= read -r line; do
        local time=$(echo "$line" | grep -oP '\d+\.\d+(?= secs)' | head -1)
        
        if [ -z "$time" ]; then
            time=$(echo "$line" | grep -oP '\d+(?=ms)' | head -1)
            if [ -n "$time" ]; then
                time=$(awk "BEGIN {printf \"%.3f\", $time/1000}")
            fi
        fi
        
        if [ -n "$time" ]; then
            total_time=$(awk "BEGIN {printf \"%.3f\", $total_time + $time}")
            
            if (( $(echo "$time > $max_time" | bc -l) )); then
                max_time=$time
            fi
        fi
    done < <(grep "Full GC" "$GC_LOG_FILE")
    
    if (( $(echo "$total_time > 0" | bc -l) )); then
        local avg_time=$(awk "BEGIN {printf \"%.3f\", $total_time / $full_gc_count}")
        
        output "总暂停时间: ${total_time}s"
        output "平均暂停:   ${avg_time}s"
        output "最大暂停:   ${max_time}s"
        
        # 性能评估
        output ""
        output "${CYAN}性能评估${NC}"
        
        if [ "$full_gc_count" -gt 10 ]; then
            output "${RED}✗ Full GC次数过多，可能存在内存泄漏或堆内存不足${NC}"
        elif [ "$full_gc_count" -gt 5 ]; then
            output "${YELLOW}⚠ Full GC次数较多，建议优化${NC}"
        else
            output "${YELLOW}⚠ 发生了Full GC，需要关注${NC}"
        fi
        
        if (( $(echo "$avg_time > 1" | bc -l) )); then
            output "${RED}✗ Full GC平均暂停时间超过1秒${NC}"
        fi
    fi
    
    print_separator
}

# 分析堆内存使用
analyze_heap_usage() {
    print_title "堆内存使用分析"
    
    output "${BLUE}堆内存统计${NC}"
    
    # 提取堆内存信息（格式可能因GC收集器而异）
    # 示例：[PSYoungGen: 131072K->10752K(153088K)] 131072K->10752K(502784K)
    
    local heap_before_list=()
    local heap_after_list=()
    local heap_total_list=()
    
    while IFS= read -r line; do
        # 提取堆内存变化 before->after(total)
        local heap_info=$(echo "$line" | grep -oP '\d+K->\d+K\(\d+K\)' | tail -1)
        
        if [ -n "$heap_info" ]; then
            local before=$(echo "$heap_info" | grep -oP '^\d+')
            local after=$(echo "$heap_info" | grep -oP '->\d+' | grep -oP '\d+')
            local total=$(echo "$heap_info" | grep -oP '\(\d+' | grep -oP '\d+')
            
            heap_before_list+=($before)
            heap_after_list+=($after)
            heap_total_list+=($total)
        fi
    done < <(grep -E "GC|Full GC" "$GC_LOG_FILE")
    
    if [ ${#heap_before_list[@]} -gt 0 ]; then
        # 计算平均值
        local sum_before=0
        local sum_after=0
        local max_total=0
        
        for i in "${!heap_before_list[@]}"; do
            sum_before=$((sum_before + ${heap_before_list[$i]}))
            sum_after=$((sum_after + ${heap_after_list[$i]}))
            
            if [ ${heap_total_list[$i]} -gt $max_total ]; then
                max_total=${heap_total_list[$i]}
            fi
        done
        
        local count=${#heap_before_list[@]}
        local avg_before=$((sum_before / count))
        local avg_after=$((sum_after / count))
        
        # 转换为MB
        local avg_before_mb=$((avg_before / 1024))
        local avg_after_mb=$((avg_after / 1024))
        local max_total_mb=$((max_total / 1024))
        
        output "堆内存总大小: ${max_total_mb}MB"
        output "GC前平均使用: ${avg_before_mb}MB"
        output "GC后平均使用: ${avg_after_mb}MB"
        
        # 计算平均回收率
        if [ $avg_before -gt 0 ]; then
            local reclaim_rate=$(awk "BEGIN {printf \"%.2f\", (($avg_before - $avg_after) * 100.0 / $avg_before)}")
            output "平均回收率:   ${reclaim_rate}%"
            
            # 评估
            output ""
            output "${CYAN}性能评估${NC}"
            
            if (( $(echo "$reclaim_rate < 50" | bc -l) )); then
                output "${RED}✗ 回收率较低(<50%)，可能存在内存泄漏${NC}"
            elif (( $(echo "$reclaim_rate < 70" | bc -l) )); then
                output "${YELLOW}⚠ 回收率一般(50-70%)，建议优化${NC}"
            else
                output "${GREEN}✓ 回收率良好(>70%)${NC}"
            fi
        fi
    else
        output "${YELLOW}无法提取堆内存信息${NC}"
    fi
    
    print_separator
}

# 分析GC原因
analyze_gc_cause() {
    print_title "GC触发原因分析"
    
    output "${BLUE}GC原因统计${NC}"
    
    # 统计各种GC原因
    local allocation_failure=$(grep -c "Allocation Failure" "$GC_LOG_FILE" 2>/dev/null)
    local metadata_threshold=$(grep -c "Metadata GC Threshold" "$GC_LOG_FILE" 2>/dev/null)
    local system_gc=$(grep -c "System.gc()" "$GC_LOG_FILE" 2>/dev/null)
    local ergonomics=$(grep -c "Ergonomics" "$GC_LOG_FILE" 2>/dev/null)
    
    if [ $allocation_failure -gt 0 ]; then
        output "Allocation Failure:     $allocation_failure 次"
    fi
    
    if [ $metadata_threshold -gt 0 ]; then
        output "Metadata GC Threshold:  $metadata_threshold 次"
    fi
    
    if [ $system_gc -gt 0 ]; then
        output "System.gc():            $system_gc 次"
        output "${YELLOW}⚠ 检测到显式调用System.gc()，建议移除或使用-XX:+DisableExplicitGC${NC}"
    fi
    
    if [ $ergonomics -gt 0 ]; then
        output "Ergonomics:             $ergonomics 次"
    fi
    
    print_separator
}

# 生成优化建议
generate_recommendations() {
    print_title "优化建议"
    
    output "${CYAN}基于分析结果的优化建议：${NC}"
    output ""
    
    # 统计数据
    local young_gc_count=$(grep -c "GC pause (young)" "$GC_LOG_FILE" 2>/dev/null || \
                          grep -c "\[GC (Allocation Failure)" "$GC_LOG_FILE" 2>/dev/null || \
                          grep -c "PSYoungGen" "$GC_LOG_FILE" 2>/dev/null)
    
    local full_gc_count=$(grep -c "Full GC" "$GC_LOG_FILE" 2>/dev/null)
    local system_gc=$(grep -c "System.gc()" "$GC_LOG_FILE" 2>/dev/null)
    
    local recommendation_count=1
    
    # 建议1：Full GC
    if [ $full_gc_count -gt 10 ]; then
        output "${recommendation_count}. ${RED}Full GC次数过多${NC}"
        output "   原因分析："
        output "   - 堆内存不足"
        output "   - 存在内存泄漏"
        output "   - Old区空间不足"
        output ""
        output "   优化建议："
        output "   - 增加堆内存大小：-Xmx4g -Xms4g"
        output "   - 调整新生代大小：-Xmn2g"
        output "   - 使用MAT分析堆转储，排查内存泄漏"
        output "   - 考虑使用G1或ZGC收集器"
        output ""
        recommendation_count=$((recommendation_count + 1))
    fi
    
    # 建议2：Young GC频繁
    if [ $young_gc_count -gt 1000 ]; then
        output "${recommendation_count}. ${YELLOW}Young GC频率过高${NC}"
        output "   原因分析："
        output "   - 新生代空间过小"
        output "   - 对象分配速率过快"
        output ""
        output "   优化建议："
        output "   - 增加新生代大小：-Xmn2g"
        output "   - 调整Eden与Survivor比例：-XX:SurvivorRatio=8"
        output "   - 优化代码，减少对象创建"
        output ""
        recommendation_count=$((recommendation_count + 1))
    fi
    
    # 建议3：System.gc()
    if [ $system_gc -gt 0 ]; then
        output "${recommendation_count}. ${YELLOW}检测到显式GC调用${NC}"
        output "   优化建议："
        output "   - 移除代码中的System.gc()调用"
        output "   - 添加JVM参数：-XX:+DisableExplicitGC"
        output ""
        recommendation_count=$((recommendation_count + 1))
    fi
    
    # 建议4：通用优化
    output "${recommendation_count}. ${CYAN}通用优化建议${NC}"
    output "   JVM参数优化："
    output "   - 设置初始和最大堆相同：-Xms4g -Xmx4g"
    output "   - 打印详细GC日志："
    output "     -XX:+PrintGCDetails"
    output "     -XX:+PrintGCDateStamps"
    output "     -XX:+PrintGCTimeStamps"
    output "     -Xloggc:gc.log"
    output "   - 使用G1收集器（JDK 9+推荐）："
    output "     -XX:+UseG1GC"
    output "     -XX:MaxGCPauseMillis=200"
    output "   - 启用GC日志轮转："
    output "     -XX:+UseGCLogFileRotation"
    output "     -XX:NumberOfGCLogFiles=10"
    output "     -XX:GCLogFileSize=100M"
    output ""
    
    print_separator
}

# 详细分析模式
detailed_analysis() {
    if [ $DETAIL_MODE -eq 0 ]; then
        return
    fi
    
    print_title "详细分析"
    
    output "${BLUE}GC日志样本（最近10条）${NC}"
    output ""
    
    # 显示最近的GC记录
    grep -E "GC|Full GC" "$GC_LOG_FILE" | tail -10
    
    output ""
    print_separator
    
    # 显示GC时间分布
    output "${BLUE}GC暂停时间分布${NC}"
    output ""
    output "暂停时间范围统计："
    
    local count_0_50=0
    local count_50_100=0
    local count_100_200=0
    local count_200_500=0
    local count_500_plus=0
    
    while IFS= read -r line; do
        local time=$(echo "$line" | grep -oP '\d+\.\d+(?= secs)' | head -1)
        
        if [ -z "$time" ]; then
            time=$(echo "$line" | grep -oP '\d+(?=ms)' | head -1)
            if [ -n "$time" ]; then
                time=$(awk "BEGIN {printf \"%.3f\", $time/1000}")
            fi
        fi
        
        if [ -n "$time" ]; then
            local time_ms=$(awk "BEGIN {printf \"%.0f\", $time * 1000}")
            
            if [ $time_ms -lt 50 ]; then
                count_0_50=$((count_0_50 + 1))
            elif [ $time_ms -lt 100 ]; then
                count_50_100=$((count_50_100 + 1))
            elif [ $time_ms -lt 200 ]; then
                count_100_200=$((count_100_200 + 1))
            elif [ $time_ms -lt 500 ]; then
                count_200_500=$((count_200_500 + 1))
            else
                count_500_plus=$((count_500_plus + 1))
            fi
        fi
    done < <(grep -E "GC|Full GC" "$GC_LOG_FILE")
    
    output "  0-50ms:     $count_0_50 次"
    output "  50-100ms:   $count_50_100 次"
    output "  100-200ms:  $count_100_200 次"
    output "  200-500ms:  $count_200_500 次"
    output "  500ms+:     $count_500_plus 次"
    
    if [ $count_500_plus -gt 0 ]; then
        output ""
        output "${RED}⚠ 存在超过500ms的GC暂停，严重影响性能${NC}"
    fi
    
    output ""
    print_separator
}

# 生成摘要
generate_summary() {
    print_title "分析摘要"
    
    local young_gc=$(grep -c "GC pause (young)" "$GC_LOG_FILE" 2>/dev/null || \
                    grep -c "\[GC (Allocation Failure)" "$GC_LOG_FILE" 2>/dev/null || \
                    grep -c "PSYoungGen" "$GC_LOG_FILE" 2>/dev/null)
    
    local full_gc=$(grep -c "Full GC" "$GC_LOG_FILE" 2>/dev/null)
    
    output "${CYAN}总体评估${NC}"
    output ""
    output "Young GC总数: $young_gc"
    output "Full GC总数:  $full_gc"
    output ""
    
    # 综合评分
    local score=100
    
    if [ $full_gc -gt 20 ]; then
        score=$((score - 40))
        output "${RED}✗ Full GC次数过多 (-40分)${NC}"
    elif [ $full_gc -gt 10 ]; then
        score=$((score - 20))
        output "${YELLOW}⚠ Full GC次数较多 (-20分)${NC}"
    elif [ $full_gc -gt 0 ]; then
        score=$((score - 10))
        output "${YELLOW}⚠ 发生了Full GC (-10分)${NC}"
    else
        output "${GREEN}✓ 未发生Full GC (+0分)${NC}"
    fi
    
    if [ $young_gc -gt 2000 ]; then
        score=$((score - 20))
        output "${YELLOW}⚠ Young GC频率过高 (-20分)${NC}"
    fi
    
    output ""
    output "GC性能评分: ${score}/100"
    output ""
    
    if [ $score -ge 90 ]; then
        output "${GREEN}评级: 优秀 ⭐⭐⭐⭐⭐${NC}"
    elif [ $score -ge 80 ]; then
        output "${GREEN}评级: 良好 ⭐⭐⭐⭐${NC}"
    elif [ $score -ge 70 ]; then
        output "${YELLOW}评级: 一般 ⭐⭐⭐${NC}"
    elif [ $score -ge 60 ]; then
        output "${YELLOW}评级: 较差 ⭐⭐${NC}"
    else
        output "${RED}评级: 差 ⭐${NC}"
    fi
    
    print_separator
}

# 主函数
main() {
    # 清空输出文件
    if [ -n "$OUTPUT_FILE" ]; then
        > "$OUTPUT_FILE"
    fi
    
    output "${GREEN}==================== GC日志分析报告 ====================${NC}"
    output "分析时间: $(date '+%Y-%m-%d %H:%M:%S')"
    
    analyze_basic_info
    analyze_young_gc
    analyze_full_gc
    analyze_heap_usage
    analyze_gc_cause
    detailed_analysis
    generate_recommendations
    generate_summary
    
    output ""
    output "${GREEN}分析完成！${NC}"
    
    if [ -n "$OUTPUT_FILE" ]; then
        output ""
        output "报告已保存到: $OUTPUT_FILE"
    fi
}

# 执行主函数
main

exit 0
