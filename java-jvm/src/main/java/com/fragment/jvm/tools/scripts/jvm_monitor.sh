#!/bin/bash

################################################################################
# JVM监控脚本
# 功能：实时监控JVM运行状态，包括内存、GC、线程、CPU等
# 作者：Java Fragment Code
# 版本：1.0
################################################################################

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
INTERVAL=5
OUTPUT_FILE=""
PID=""
DURATION=0

# 打印帮助信息
print_help() {
    echo "JVM监控脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -p PID          指定Java进程ID（必需）"
    echo "  -i INTERVAL     监控间隔（秒），默认5秒"
    echo "  -d DURATION     监控时长（秒），0表示持续监控，默认0"
    echo "  -o FILE         输出到文件"
    echo "  -h              显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -p 12345                    # 监控进程12345"
    echo "  $0 -p 12345 -i 10              # 每10秒监控一次"
    echo "  $0 -p 12345 -d 60 -o jvm.log   # 监控60秒并输出到文件"
    echo ""
}

# 解析命令行参数
while getopts "p:i:d:o:h" opt; do
    case $opt in
        p) PID=$OPTARG ;;
        i) INTERVAL=$OPTARG ;;
        d) DURATION=$OPTARG ;;
        o) OUTPUT_FILE=$OPTARG ;;
        h) print_help; exit 0 ;;
        *) print_help; exit 1 ;;
    esac
done

# 检查PID是否提供
if [ -z "$PID" ]; then
    echo -e "${RED}错误: 必须指定Java进程ID${NC}"
    echo ""
    echo "当前运行的Java进程："
    jps -l
    echo ""
    print_help
    exit 1
fi

# 检查进程是否存在
if ! ps -p $PID > /dev/null 2>&1; then
    echo -e "${RED}错误: 进程 $PID 不存在${NC}"
    exit 1
fi

# 检查是否为Java进程
if ! jps | grep -q "^$PID "; then
    echo -e "${RED}错误: 进程 $PID 不是Java进程${NC}"
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

# 获取进程信息
get_process_info() {
    local process_name=$(jps -l | grep "^$PID " | awk '{print $2}')
    output "${BLUE}进程信息${NC}"
    output "PID: $PID"
    output "进程名: $process_name"
    output "监控时间: $(date '+%Y-%m-%d %H:%M:%S')"
    print_separator
}

# 获取JVM内存信息
get_memory_info() {
    output "${BLUE}内存信息${NC}"
    
    # 使用jstat获取内存信息
    local jstat_output=$(jstat -gc $PID)
    
    if [ $? -eq 0 ]; then
        # 解析jstat输出
        local header=$(echo "$jstat_output" | head -1)
        local values=$(echo "$jstat_output" | tail -1)
        
        # 提取各个区域的值（单位：KB）
        local s0c=$(echo $values | awk '{print $1}')
        local s0u=$(echo $values | awk '{print $2}')
        local s1c=$(echo $values | awk '{print $3}')
        local s1u=$(echo $values | awk '{print $4}')
        local ec=$(echo $values | awk '{print $5}')
        local eu=$(echo $values | awk '{print $6}')
        local oc=$(echo $values | awk '{print $7}')
        local ou=$(echo $values | awk '{print $8}')
        local mc=$(echo $values | awk '{print $9}')
        local mu=$(echo $values | awk '{print $10}')
        
        # 计算使用率
        local eden_usage=$(awk "BEGIN {printf \"%.2f\", ($eu/$ec)*100}")
        local old_usage=$(awk "BEGIN {printf \"%.2f\", ($ou/$oc)*100}")
        local meta_usage=$(awk "BEGIN {printf \"%.2f\", ($mu/$mc)*100}")
        
        # 转换为MB
        local eden_used_mb=$(awk "BEGIN {printf \"%.2f\", $eu/1024}")
        local eden_total_mb=$(awk "BEGIN {printf \"%.2f\", $ec/1024}")
        local old_used_mb=$(awk "BEGIN {printf \"%.2f\", $ou/1024}")
        local old_total_mb=$(awk "BEGIN {printf \"%.2f\", $oc/1024}")
        local meta_used_mb=$(awk "BEGIN {printf \"%.2f\", $mu/1024}")
        local meta_total_mb=$(awk "BEGIN {printf \"%.2f\", $mc/1024}")
        
        output "Eden区:      ${eden_used_mb}MB / ${eden_total_mb}MB (${eden_usage}%)"
        output "Old区:       ${old_used_mb}MB / ${old_total_mb}MB (${old_usage}%)"
        output "Metaspace:   ${meta_used_mb}MB / ${meta_total_mb}MB (${meta_usage}%)"
        
        # 告警检查
        if (( $(echo "$eden_usage > 80" | bc -l) )); then
            output "${YELLOW}⚠ Eden区使用率超过80%${NC}"
        fi
        if (( $(echo "$old_usage > 80" | bc -l) )); then
            output "${RED}⚠ Old区使用率超过80%，可能存在内存泄漏${NC}"
        fi
        if (( $(echo "$meta_usage > 90" | bc -l) )); then
            output "${RED}⚠ Metaspace使用率超过90%${NC}"
        fi
    else
        output "${RED}无法获取内存信息${NC}"
    fi
    
    print_separator
}

# 获取GC信息
get_gc_info() {
    output "${BLUE}GC信息${NC}"
    
    # 使用jstat获取GC统计
    local jstat_output=$(jstat -gcutil $PID)
    
    if [ $? -eq 0 ]; then
        local values=$(echo "$jstat_output" | tail -1)
        
        local ygc=$(echo $values | awk '{print $12}')
        local ygct=$(echo $values | awk '{print $13}')
        local fgc=$(echo $values | awk '{print $14}')
        local fgct=$(echo $values | awk '{print $15}')
        local gct=$(echo $values | awk '{print $16}')
        
        # 计算平均GC时间
        local avg_ygc_time=0
        local avg_fgc_time=0
        
        if [ "$ygc" != "0" ]; then
            avg_ygc_time=$(awk "BEGIN {printf \"%.3f\", $ygct/$ygc}")
        fi
        
        if [ "$fgc" != "0" ]; then
            avg_fgc_time=$(awk "BEGIN {printf \"%.3f\", $fgct/$fgc}")
        fi
        
        output "Young GC次数:    $ygc"
        output "Young GC总时间:  ${ygct}s"
        output "Young GC平均:    ${avg_ygc_time}s"
        output "Full GC次数:     $fgc"
        output "Full GC总时间:   ${fgct}s"
        output "Full GC平均:     ${avg_fgc_time}s"
        output "GC总时间:        ${gct}s"
        
        # 告警检查
        if (( $(echo "$avg_ygc_time > 0.1" | bc -l) )); then
            output "${YELLOW}⚠ Young GC平均时间超过100ms${NC}"
        fi
        if (( $(echo "$avg_fgc_time > 1" | bc -l) )); then
            output "${RED}⚠ Full GC平均时间超过1秒${NC}"
        fi
        if [ "$fgc" != "0" ]; then
            output "${YELLOW}⚠ 发生了Full GC，需要关注${NC}"
        fi
    else
        output "${RED}无法获取GC信息${NC}"
    fi
    
    print_separator
}

# 获取线程信息
get_thread_info() {
    output "${BLUE}线程信息${NC}"
    
    # 获取线程数
    local thread_count=$(jstack $PID 2>/dev/null | grep "^\"" | wc -l)
    
    if [ $? -eq 0 ]; then
        output "线程总数: $thread_count"
        
        # 统计线程状态
        local runnable=$(jstack $PID 2>/dev/null | grep "java.lang.Thread.State: RUNNABLE" | wc -l)
        local waiting=$(jstack $PID 2>/dev/null | grep "java.lang.Thread.State: WAITING" | wc -l)
        local timed_waiting=$(jstack $PID 2>/dev/null | grep "java.lang.Thread.State: TIMED_WAITING" | wc -l)
        local blocked=$(jstack $PID 2>/dev/null | grep "java.lang.Thread.State: BLOCKED" | wc -l)
        
        output "  RUNNABLE:      $runnable"
        output "  WAITING:       $waiting"
        output "  TIMED_WAITING: $timed_waiting"
        output "  BLOCKED:       $blocked"
        
        # 检查死锁
        if jstack $PID 2>/dev/null | grep -q "Found one Java-level deadlock"; then
            output "${RED}⚠ 检测到死锁！${NC}"
        fi
        
        # 告警检查
        if [ $blocked -gt 10 ]; then
            output "${YELLOW}⚠ BLOCKED线程数较多($blocked)，可能存在锁竞争${NC}"
        fi
    else
        output "${RED}无法获取线程信息${NC}"
    fi
    
    print_separator
}

# 获取CPU信息
get_cpu_info() {
    output "${BLUE}CPU信息${NC}"
    
    # 获取进程CPU使用率
    local cpu_usage=$(ps -p $PID -o %cpu | tail -1 | tr -d ' ')
    
    output "CPU使用率: ${cpu_usage}%"
    
    # 告警检查
    if (( $(echo "$cpu_usage > 80" | bc -l) )); then
        output "${RED}⚠ CPU使用率超过80%${NC}"
        
        # 显示CPU占用最高的线程
        output ""
        output "CPU占用最高的5个线程:"
        ps -mp $PID -o THREAD,tid,time | sort -k2 -r | head -6 | tail -5
    fi
    
    print_separator
}

# 获取类加载信息
get_class_info() {
    output "${BLUE}类加载信息${NC}"
    
    local jstat_output=$(jstat -class $PID)
    
    if [ $? -eq 0 ]; then
        local values=$(echo "$jstat_output" | tail -1)
        
        local loaded=$(echo $values | awk '{print $1}')
        local bytes=$(echo $values | awk '{print $2}')
        local unloaded=$(echo $values | awk '{print $3}')
        
        local bytes_mb=$(awk "BEGIN {printf \"%.2f\", $bytes/1024}")
        
        output "已加载类数: $loaded"
        output "已卸载类数: $unloaded"
        output "类占用空间: ${bytes_mb}MB"
    else
        output "${RED}无法获取类加载信息${NC}"
    fi
    
    print_separator
}

# 主监控循环
monitor_loop() {
    local start_time=$(date +%s)
    local count=0
    
    while true; do
        clear
        
        output ""
        output "${GREEN}==================== JVM监控报告 ====================${NC}"
        output ""
        
        get_process_info
        get_memory_info
        get_gc_info
        get_thread_info
        get_cpu_info
        get_class_info
        
        count=$((count + 1))
        
        # 检查是否达到监控时长
        if [ $DURATION -gt 0 ]; then
            local current_time=$(date +%s)
            local elapsed=$((current_time - start_time))
            
            if [ $elapsed -ge $DURATION ]; then
                output "${GREEN}监控完成，共监控 $count 次${NC}"
                break
            fi
            
            local remaining=$((DURATION - elapsed))
            output "${YELLOW}剩余监控时间: ${remaining}秒${NC}"
        fi
        
        output ""
        output "${YELLOW}按 Ctrl+C 停止监控...${NC}"
        
        sleep $INTERVAL
    done
}

# 信号处理
trap 'echo -e "\n${GREEN}监控已停止${NC}"; exit 0' INT TERM

# 开始监控
echo -e "${GREEN}开始监控JVM进程 $PID...${NC}"
echo ""

monitor_loop

exit 0
