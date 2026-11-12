#!/bin/sh
# 本地一键启动 DevMate AI 后端。
# 兼容 sh / bash / zsh；启动前做环境预检，失败时给出可直接执行的中文提示。

set -eu

script_path=$0
case $script_path in
  */*) script_dir=$(cd "${script_path%/*}" && pwd) ;;
  *) script_dir=$(pwd) ;;
esac
project_dir=$(cd "$script_dir/.." && pwd)
secrets_file="$project_dir/.devmate-local-secrets"
local_config="$project_dir/src/main/resources/application-local.yml"
local_example="$local_config.example"

info() { printf '[检查] %s\n' "$1"; }
note() { printf '        %s\n' "$1"; }

die() {
  printf '\n[启动失败] %s\n' "$1" >&2
  shift
  for hint in "$@"; do
    printf '  → %s\n' "$hint" >&2
  done
  printf '\n修好上面这一项后，重新运行 ./scripts/start-local.sh 即可。\n' >&2
  exit 1
}

random_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    LC_ALL=C tr -dc 'a-f0-9' < /dev/urandom | dd bs=64 count=1 2>/dev/null
    printf '\n'
  fi
}

# 1. JDK 版本：项目要求 Java 21 及以上
if ! command -v java >/dev/null 2>&1; then
  die "没有找到 java 命令。" \
    "本项目需要 JDK 21 或更高版本。" \
    "macOS 可用 Homebrew 安装：brew install openjdk@21"
fi
java_major=$(java -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
if [ -n "${java_major}" ] && [ "${java_major}" -lt 21 ] 2>/dev/null; then
  die "当前 Java 版本是 ${java_major}，低于项目要求的 21。" \
    "请安装 JDK 21+ 并把 JAVA_HOME 指向它。"
fi
info "Java 版本可用（${java_major}）"

# 2. 本地数据库配置文件
if [ ! -f "$local_config" ]; then
  if [ -f "$local_example" ]; then
    cp "$local_example" "$local_config"
    info "已从模板生成 application-local.yml"
    note "该文件已被 Git 忽略，请按本机情况修改数据库端口和密码。"
  else
    die "缺少 src/main/resources/application-local.yml，且找不到 .example 模板。" \
      "请先补齐本地数据库配置再启动。"
  fi
else
  info "本地数据库配置已存在"
fi

# 3. 从配置里读出真实的数据库主机和端口，避免 3306/3307 这类端口不一致
jdbc_line=$(grep -m1 'jdbc:mysql://' "$local_config" 2>/dev/null || true)
db_host=localhost
db_port=3306
if [ -n "$jdbc_line" ]; then
  hostport=$(printf '%s' "$jdbc_line" | sed -n 's|.*jdbc:mysql://\([^/]*\)/.*|\1|p')
  case $hostport in
    *:*)
      db_host=${hostport%%:*}
      db_port=${hostport##*:}
      ;;
    ?*) db_host=$hostport ;;
  esac
fi

# 连通性预检只作为提示：部分环境禁止本机回环探测，探测失败不代表数据库真的不可用，
# 因此这里只提醒，真正的连接结果交给 Spring Boot 启动时的报错。
db_probe=skipped
if command -v nc >/dev/null 2>&1; then
  if nc -z "${db_host}" "${db_port}" >/dev/null 2>&1; then
    db_probe=ok
  else
    db_probe=failed
  fi
fi
if [ "$db_probe" = ok ]; then
  info "数据库端口可连接（${db_host}:${db_port}）"
elif [ "$db_probe" = failed ]; then
  info "没能探测到数据库端口（${db_host}:${db_port}），继续启动"
  note "如果后端随后报数据库连接失败，按下面三步检查："
  note "1. 确认 MySQL 已启动；macOS 可用：brew services start mysql"
  note "2. 确认端口一致：模板默认 3306，本机若用其他端口请改 application-local.yml"
  note "3. 确认库已创建：CREATE DATABASE devmate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
else
  note "没有 nc 命令，跳过数据库连通性预检"
fi

# 4. 本地密钥：首次生成后长期复用，避免已保存的模型 API Key 失效
if [ ! -f "$secrets_file" ]; then
  old_umask=$(umask)
  umask 077
  {
    printf 'DEVMATE_JWT_SECRET=%s\n' "$(random_hex)"
    printf 'DEVMATE_MODEL_ENCRYPTION_SECRET=%s\n' "$(random_hex)"
  } > "$secrets_file"
  umask "$old_umask"
  info "已生成本地密钥文件 .devmate-local-secrets（已被 Git 忽略，请勿提交或分享）"
else
  info "本地密钥文件已存在，继续复用"
fi

set -a
. "$secrets_file"
set +a

# 判断依据是密钥文件里有没有这一项，而不是密码值是否为空：
# 空密码也是合法配置，按值判断会导致每次启动都重复询问并追加重复行。
if grep -q '^SPRING_DATASOURCE_PASSWORD=' "$secrets_file" 2>/dev/null; then
  info "数据库密码已配置，继续复用"
else
  printf '请输入本机 MySQL 密码（仅首次，输入不会显示，直接回车表示空密码）：'
  stty -echo 2>/dev/null || true
  IFS= read -r database_password || database_password=''
  stty echo 2>/dev/null || true
  printf '\n'
  # 密钥文件会被 shell source，密码里的空格、$ 和引号必须先做单引号转义再写入
  escaped_password=$(printf '%s' "$database_password" | sed "s/'/'\\\\''/g")
  printf "SPRING_DATASOURCE_PASSWORD='%s'\n" "$escaped_password" >> "$secrets_file"
  export SPRING_DATASOURCE_PASSWORD="$database_password"
  unset database_password
  unset escaped_password
  info "数据库密码已保存到 .devmate-local-secrets"
fi

printf '\n预检通过，正在启动后端……\n'
printf '启动成功后可访问：\n'
printf '  健康检查   http://localhost:8080/actuator/health\n'
printf '  接口文档   http://localhost:8080/swagger-ui.html\n'
printf '前端请另开终端执行：cd frontend && npm install && npm run dev\n'
printf '停止服务：在本终端按 Ctrl+C\n\n'

cd "$project_dir"
exec ./mvnw spring-boot:run
