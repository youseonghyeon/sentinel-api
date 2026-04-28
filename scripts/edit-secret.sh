#!/usr/bin/env bash
set -euo pipefail

# SOPS로 암호화된 Secret 파일을 편집
# Usage:
#   ./scripts/edit-secret.sh           # secrets/prod.yaml 편집
#   ./scripts/edit-secret.sh prod      # 동일
#   ./scripts/edit-secret.sh staging   # secrets/staging.yaml 편집

ENV="${1:-prod}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRET_FILE="${REPO_ROOT}/secrets/${ENV}.yaml"

if ! command -v sops >/dev/null 2>&1; then
  echo "❌ sops 미설치. 'brew install sops' 실행" >&2
  exit 1
fi

if [[ ! -f "$SECRET_FILE" ]]; then
  echo "❌ Secret 파일 없음: $SECRET_FILE" >&2
  exit 1
fi

if [[ -z "${SOPS_AGE_KEY_FILE:-}" ]] && [[ ! -f "$HOME/.config/sops/age/keys.txt" ]]; then
  echo "⚠️  SOPS_AGE_KEY_FILE 미설정 + 기본 경로($HOME/.config/sops/age/keys.txt)에도 키 없음" >&2
  exit 1
fi

echo "▶ 편집: $SECRET_FILE"
sops "$SECRET_FILE"

echo "✅ 편집 완료 (자동으로 재암호화됨)"
echo
read -rp "변경사항을 클러스터에 바로 적용할까요? [y/N] " ANSWER
if [[ "$ANSWER" =~ ^[yY]$ ]]; then
  exec "${REPO_ROOT}/scripts/apply-secret.sh" "$ENV"
fi
