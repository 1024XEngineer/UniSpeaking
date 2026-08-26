#!/usr/bin/env bash

set -euo pipefail

fail() {
  echo "配置打包失败：$*" >&2
  exit 1
}

[[ $# -eq 2 ]] || fail "用法：$0 <40 位 commit SHA> <输出 tar.gz 路径>"
release_sha="$1"
output_path="$2"
[[ "$release_sha" =~ ^[0-9a-f]{40}$ ]] || fail "commit SHA 格式错误"
command -v tar >/dev/null 2>&1 || fail "未找到 tar"

for path in \
  deploy/docker-compose.prod.yml \
  deploy/nginx/nginx.prod.conf \
  deploy/coturn/turnserver.conf
do
  [[ -f "$path" ]] || fail "文件不存在：$path"
  [[ ! -L "$path" ]] || fail "不允许打包符号链接：$path"
done

mkdir -p "$(dirname -- "$output_path")"
tar \
  --create \
  --gzip \
  --file "$output_path" \
  --sort=name \
  --mtime='UTC 1970-01-01' \
  --owner=0 \
  --group=0 \
  --numeric-owner \
  deploy/docker-compose.prod.yml \
  deploy/nginx/nginx.prod.conf \
  deploy/coturn/turnserver.conf

test -s "$output_path" || fail "输出归档为空"
echo "已生成 release 配置包：sha-$release_sha"
sha256sum "$output_path"
