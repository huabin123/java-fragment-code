#!/bin/bash
# 修复后的核心逻辑（示例）
while getopts "r:u:p:" opt; do
  case $opt in
    r) REPO_URL="$OPTARG";;
    u) USERNAME="$OPTARG";;
    p) PASSWORD="$OPTARG";;
    *) echo "Usage: $0 -r <repo_url> -u <user> -p <password>"; exit 1;;
  esac
done

find . -type f \
  -not -path './mavenimport.sh*' \
  -not -path '*/_*' \
  -not -path '*/archetype-catalog.xml' \
  -not -path '*/maven-metadata-local.xml' \
  -not -path '*/maven-metadata-deployment.xml' \
| sed 's/^\.\///' \
| xargs -I '{}' curl -u "$USERNAME:$PASSWORD" -X PUT -T {} "$REPO_URL/{}"