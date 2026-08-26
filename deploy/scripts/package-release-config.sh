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

staging_dir="$(mktemp -d)"
cleanup() { rm -rf "$staging_dir"; }
trap cleanup EXIT

for path in \
  deploy/docker-compose.prod.yml \
  deploy/nginx/nginx.prod.conf \
  deploy/coturn/turnserver.conf
do
  [[ -f "$path" ]] || fail "文件不存在：$path"
  [[ ! -L "$path" ]] || fail "不允许打包符号链接：$path"
done

mkdir -p "$(dirname -- "$output_path")"
install -d "$staging_dir/deploy/nginx" "$staging_dir/deploy/coturn"
install -m 0644 deploy/docker-compose.prod.yml "$staging_dir/deploy/docker-compose.prod.yml"
install -m 0644 deploy/nginx/nginx.prod.conf "$staging_dir/deploy/nginx/nginx.prod.conf"
install -m 0644 deploy/coturn/turnserver.conf "$staging_dir/deploy/coturn/turnserver.conf"
printf '%s\n' "$release_sha" > "$staging_dir/.release-sha"
tar \
  --create \
  --gzip \
  --file "$output_path" \
  --sort=name \
  --mtime='UTC 1970-01-01' \
  --owner=0 \
  --group=0 \
  --numeric-owner \
  --directory "$staging_dir" \
  .release-sha \
  deploy/docker-compose.prod.yml \
  deploy/nginx/nginx.prod.conf \
  deploy/coturn/turnserver.conf

test -s "$output_path" || fail "输出归档为空"
echo "已生成 release 配置包：sha-$release_sha"
sha256sum "$output_path"
