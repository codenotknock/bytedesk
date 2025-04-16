# 使用Ubuntu作为基础镜像
FROM ubuntu:22.04

# 维护者信息
LABEL maintainer="13781678921@163.com"

# 避免安装过程中的交互
ENV DEBIAN_FRONTEND=noninteractive

# 设置工作目录
WORKDIR /app

# 安装必要的软件包
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    mysql-server \
    curl \
    gnupg \
    lsb-release \
    ca-certificates \
    netcat \
    && rm -rf /var/lib/apt/lists/*

# 安装Redis Stack
RUN curl -fsSL https://packages.redis.io/gpg | gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" > /etc/apt/sources.list.d/redis.list \
    && apt-get update \
    && apt-get install -y redis-stack-server \
    && rm -rf /var/lib/apt/lists/*

# 复制 MySQL 初始化脚本和应用程序 JAR 包
COPY mysql-init.sql /docker-entrypoint-initdb.d/
COPY target/*.jar app.jar

# 暴露端口
EXPOSE 9003 6379 8001 3306

# 配置 MySQL 和 Redis 环境变量
ENV MYSQL_ROOT_PASSWORD=r8FqfdbWUaN3
ENV MYSQL_DATABASE=bytedesk
ENV REDIS_PASSWORD=qfRxz3tVT8Nh

# 创建启动脚本
RUN echo '#!/bin/bash' > /start.sh && \
    echo '' >> /start.sh && \
    echo '# 初始化 MySQL' >> /start.sh && \
    echo 'echo "正在配置 MySQL..."' >> /start.sh && \
    echo 'mkdir -p /var/run/mysqld' >> /start.sh && \
    echo 'chown -R mysql:mysql /var/run/mysqld' >> /start.sh && \
    echo 'usermod -d /var/lib/mysql/ mysql' >> /start.sh && \
    echo 'echo "初始化 MySQL..."' >> /start.sh && \
    echo 'mysqld --initialize-insecure --user=mysql --datadir=/var/lib/mysql || true' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 启动 MySQL 服务' >> /start.sh && \
    echo 'echo "正在启动 MySQL..."' >> /start.sh && \
    echo 'service mysql start' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 设置 MySQL root 密码' >> /start.sh && \
    echo 'echo "设置 MySQL root 密码..."' >> /start.sh && \
    echo 'mysql -u root -e "ALTER USER \"root\"@\"localhost\" IDENTIFIED WITH mysql_native_password BY \"${MYSQL_ROOT_PASSWORD}\"; FLUSH PRIVILEGES;"' >> /start.sh && \
    echo 'mysql -u root -p${MYSQL_ROOT_PASSWORD} -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DATABASE};"' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 导入 MySQL 初始化脚本' >> /start.sh && \
    echo 'echo "导入 MySQL 初始化脚本..."' >> /start.sh && \
    echo 'mysql -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} < /docker-entrypoint-initdb.d/mysql-init.sql || true' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 启动 Redis 服务' >> /start.sh && \
    echo 'echo "正在启动 Redis..."' >> /start.sh && \
    echo 'sed -i "s/^# requirepass .*/requirepass ${REDIS_PASSWORD}/" /etc/redis-stack.conf' >> /start.sh && \
    echo 'service redis-stack-server start' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 等待 MySQL 启动并准备好接受连接' >> /start.sh && \
    echo 'echo "等待 MySQL 服务就绪..."' >> /start.sh && \
    echo 'until nc -z -v -w30 127.0.0.1 3306' >> /start.sh && \
    echo 'do' >> /start.sh && \
    echo '  echo "等待 MySQL 启动...休眠 5 秒"' >> /start.sh && \
    echo '  sleep 5' >> /start.sh && \
    echo 'done' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 确保数据库可以连接' >> /start.sh && \
    echo 'echo "检查 MySQL 连接..."' >> /start.sh && \
    echo 'MYSQL_READY=false' >> /start.sh && \
    echo 'for i in {1..30}; do' >> /start.sh && \
    echo '  if mysql -h127.0.0.1 -uroot -p${MYSQL_ROOT_PASSWORD} -e "SELECT 1" >/dev/null 2>&1; then' >> /start.sh && \
    echo '    MYSQL_READY=true' >> /start.sh && \
    echo '    break' >> /start.sh && \
    echo '  fi' >> /start.sh && \
    echo '  echo "等待 MySQL 准备就绪...($i/30)"' >> /start.sh && \
    echo '  sleep 2' >> /start.sh && \
    echo 'done' >> /start.sh && \
    echo '' >> /start.sh && \
    echo 'if [ "$MYSQL_READY" = false ]; then' >> /start.sh && \
    echo '  echo "MySQL 未能在指定时间内准备就绪，启动失败"' >> /start.sh && \
    echo '  exit 1' >> /start.sh && \
    echo 'fi' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 等待 Redis 启动完成' >> /start.sh && \
    echo 'echo "等待 Redis 服务就绪..."' >> /start.sh && \
    echo 'sleep 5' >> /start.sh && \
    echo 'until redis-cli -a ${REDIS_PASSWORD} ping | grep -q PONG' >> /start.sh && \
    echo 'do' >> /start.sh && \
    echo '  echo "等待 Redis 启动...休眠 2 秒"' >> /start.sh && \
    echo '  sleep 2' >> /start.sh && \
    echo 'done' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# 启动 Java 应用' >> /start.sh && \
    echo 'echo "所有服务已启动，正在启动 Java 应用..."' >> /start.sh && \
    echo 'java -jar /app/app.jar' >> /start.sh && \
    chmod +x /start.sh

# 启动容器时执行启动脚本
CMD ["/start.sh"]