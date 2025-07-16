#!/bin/bash
output_file="java_services.txt"
> "$output_file"  # 清空或创建输出文件

# 遍历每个 Java 进程
ps -ef | grep java | grep -v grep | while read -r line; do
  pid=$(echo "$line" | awk '{print $2}')
  cmd=$(echo "$line" | awk '{$1=$2=$3=$4=$5=$6=$7=""; print $0}' | sed 's/^ *//; s/ *$//')

  # 提取服务名（优先从 -jar 参数获取）
  service_name=$(echo "$cmd" | grep -oP -- '-jar \K[^ ]+' | sed 's/.*\///; s/\.jar$//')
  [[ -z "$service_name" ]] && service_name=$(echo "$cmd" | awk '{print $1}' | sed 's/.*\///')

  # 提取监听端口（IPv4/IPv6）
  ports=$(ss -ltnp 2>/dev/null | awk -v pid="$pid" '$7 == "pid="pid {split($5, a, ":"); print a[2]}' | sort -u | tr '\n' ' ')
  [[ -z "$ports" ]] && ports="N/A"

  # 输出到文件（一个端口一行）
  for port in $ports; do
    echo "${service_name} ${port}" >> "$output_file"
  done
done

echo "结果已保存到: $output_file"
