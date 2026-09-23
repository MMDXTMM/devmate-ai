#!/bin/zsh

set -euo pipefail

script_dir="${0:A:h}"
project_dir="${script_dir:h}"
secrets_file="${project_dir}/.devmate-local-secrets"

if [[ ! -f "${secrets_file}" ]]; then
  umask 077
  {
    print -r -- "DEVMATE_JWT_SECRET=$(openssl rand -hex 32)"
    print -r -- "DEVMATE_MODEL_ENCRYPTION_SECRET=$(openssl rand -hex 32)"
  } > "${secrets_file}"
fi

set -a
source "${secrets_file}"
set +a

if [[ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
  read -rs "database_password?请输入本机 MySQL 密码（仅首次，输入不会显示）："
  print
  printf 'SPRING_DATASOURCE_PASSWORD=%q\n' "${database_password}" >> "${secrets_file}"
  export SPRING_DATASOURCE_PASSWORD="${database_password}"
  unset database_password
fi

cd "${project_dir}"
exec ./mvnw spring-boot:run
