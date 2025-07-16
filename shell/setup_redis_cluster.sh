#!/bin/bash

# Redis集群节点IP配置（根据实际修改）
NODES=("192.168.1.101" "192.168.1.102" "192.168.1.103" "192.168.1.104" "192.168.1.105" "192.168.1.106")
REDIS_PORT=7001  # 所有节点使用相同端口（不同IP不影响）
REDIS_VERSION="6.2.19"
INSTALL_DIR="/opt/redis-${REDIS_VERSION}"
TAR_FILE="/opt/redis-${REDIS_VERSION}.tar.gz"  # 安装包在/opt目录
PASSWORD=""  # 服务器密码变量
REDIS_PASSWORD="aml@Is%A43!"  # Redis访问密码

# 获取服务器密码
get_password() {
    read -s -p "请输入所有服务器的root密码: " PASSWORD
    echo
    if [ -z "$PASSWORD" ]; then
        echo "错误：密码不能为空！"
        exit 1
    fi
}

# 执行远程命令函数
remote_exec() {
    local ip=$1
    local cmd=$2
    sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no root@$ip "$cmd"
}

# 复制文件到远程服务器（强制覆盖）
remote_copy() {
    local src=$1
    local ip=$2
    local dest=$3
    sshpass -p "$PASSWORD" scp -o StrictHostKeyChecking=no -r $src root@$ip:$dest
}

# 停止旧版Redis
stop_old_redis() {
    local ip=$1
    echo "在 $ip 停止旧版Redis-4.0.12..."
    remote_exec $ip "redis-cli -p 6379 shutdown 2>/dev/null || echo '未运行或已停止'"
}

# 安装编译依赖
install_deps() {
    local ip=$1
    echo "在 $ip 安装编译依赖..."
    remote_exec $ip "yum install -y gcc tcl >/dev/null 2>&1 || { echo '安装依赖失败'; exit 1; }"
}

# 编译安装Redis（在/opt目录操作）
install_redis() {
    local ip=$1
    echo "在 $ip 解压并编译Redis（在/opt目录操作）..."

    if [ "$ip" == "localhost" ]; then
        # 本地安装（在/opt目录）
        cd /opt
        # 删除旧目录（如果存在）
        rm -rf redis-$REDIS_VERSION >/dev/null 2>&1
        rm -rf $INSTALL_DIR >/dev/null 2>&1

        tar -zxvf $TAR_FILE >/dev/null || exit 1
        cd redis-$REDIS_VERSION || exit 1
        make >/dev/null 2>&1 || { echo "编译失败"; exit 1; }
        mkdir -p $INSTALL_DIR
        cp -f src/{redis-server,redis-cli} $INSTALL_DIR/
        cp -f redis.conf $INSTALL_DIR/
        cd ..
        rm -rf redis-$REDIS_VERSION
    else
        # 远程安装（在/opt目录）
        remote_copy $TAR_FILE $ip "/opt/"
        remote_exec $ip "
            cd /opt
            # 删除旧目录（如果存在）
            rm -rf redis-$REDIS_VERSION >/dev/null 2>&1
            rm -rf $INSTALL_DIR >/dev/null 2>&1

            tar -zxf /opt/redis-$REDIS_VERSION.tar.gz -C /opt/ >/dev/null || exit 1;
            cd /opt/redis-$REDIS_VERSION;
            make >/dev/null 2>&1 || { echo '编译失败'; exit 1; };
            mkdir -p $INSTALL_DIR;
            cp -f src/{redis-server,redis-cli} $INSTALL_DIR/;
            cp -f redis.conf $INSTALL_DIR/;
            cd /opt;
            rm -rf redis-$REDIS_VERSION;
            # 保留安装包以备后续需要
        "
    fi
}

# 配置Redis节点（绑定到本地IP并设置密码，强制覆盖）
configure_redis() {
    local ip=$1
    echo "在 $ip 配置Redis节点（绑定到本地IP并设置密码，强制覆盖）..."

    remote_exec $ip "
        # 创建配置文件（覆盖现有文件）
        cat > $INSTALL_DIR/redis.conf <<EOF
port $REDIS_PORT
bind $ip
daemonize yes
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
protected-mode yes
requirepass \"$REDIS_PASSWORD\"
masterauth \"$REDIS_PASSWORD\"
dir $INSTALL_DIR
logfile $INSTALL_DIR/redis.log
pidfile $INSTALL_DIR/redis.pid
EOF
    "
}

# 启动Redis服务（强制停止现有进程）
start_redis() {
    local ip=$1
    echo "在 $ip 启动Redis服务（强制停止现有进程）..."

    # 先尝试停止可能存在的旧实例
    remote_exec $ip "$INSTALL_DIR/redis-cli -h $ip -p $REDIS_PORT -a \"$REDIS_PASSWORD\" shutdown >/dev/null 2>&1 || echo"

    # 等待进程停止
    sleep 2

    # 启动新实例
    remote_exec $ip "$INSTALL_DIR/redis-server $INSTALL_DIR/redis.conf"
}

# 创建Redis集群（带密码认证，强制覆盖）
create_cluster() {
    echo "创建带密码认证的Redis集群（强制覆盖）..."

    # 构建集群创建命令（添加--cluster-replicas 1和--cluster-yes）
    CLUSTER_CMD="echo yes | $INSTALL_DIR/redis-cli --cluster create"
    for ip in "${NODES[@]}"; do
        CLUSTER_CMD+=" $ip:$REDIS_PORT"
    done
    CLUSTER_CMD+=" --cluster-replicas 1 -a \"$REDIS_PASSWORD\" --cluster-yes"

    # 执行集群创建命令
    eval $CLUSTER_CMD
}

### 主程序开始 ###

# 切换到/opt目录
cd /opt
echo "当前工作目录: $(pwd)"

# 检查当前目录是否有Redis安装包
if [ ! -f "$TAR_FILE" ]; then
    echo "错误: /opt目录未找到 redis-$REDIS_VERSION.tar.gz 文件!"
    exit 1
fi

# 获取服务器密码
get_password

# 安装sshpass（如果尚未安装）
echo "检查并安装sshpass（在/opt目录操作）..."
if ! command -v sshpass &> /dev/null; then
    yum install -y sshpass >/dev/null 2>&1 || { echo "安装sshpass失败"; exit 1; }
fi

# 获取当前服务器IP
CURRENT_IP=$(hostname -I | awk '{print $1}')
echo "当前服务器IP: $CURRENT_IP"
echo "Redis集群密码: $REDIS_PASSWORD"

# 处理所有节点
for ip in "${NODES[@]}"; do
    echo ""
    echo "======================================="
    echo "处理节点: $ip (在/opt目录操作)"
    echo "======================================="

    # 停止旧版Redis
    stop_old_redis $ip

    # 安装依赖
    install_deps $ip

    # 安装Redis（在/opt目录）
    if [ "$ip" == "$CURRENT_IP" ]; then
        install_redis "localhost"
    else
        install_redis $ip
    fi

    # 配置Redis（绑定到本地IP并设置密码）
    configure_redis $ip

    # 启动Redis（强制停止现有进程）
    start_redis $ip
done

# 等待节点启动
echo ""
echo "等待节点启动..."
sleep 10

# 创建带密码认证的集群（强制覆盖）
create_cluster

echo ""
echo "======================================="
echo "Redis集群搭建完成（在/opt目录操作）!"
echo "集群节点:"
for ip in "${NODES[@]}"; do
    echo "  - $ip:$REDIS_PORT"
done
echo "Redis密码: $REDIS_PASSWORD"
echo "安装目录: $INSTALL_DIR"
echo "安装包位置: $TAR_FILE"
echo ""
echo "验证命令: $INSTALL_DIR/redis-cli -c -h $CURRENT_IP -p $REDIS_PORT -a \"$REDIS_PASSWORD\" cluster nodes"
echo "停止节点命令: $INSTALL_DIR/redis-cli -h $CURRENT_IP -p $REDIS_PORT -a \"$REDIS_PASSWORD\" shutdown"
echo "======================================="
