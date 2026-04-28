#!/usr/bin/env bash
set -euo pipefail

# SOPS로 암호화된 Secret을 복호화하여 클러스터에 적용
# Usage:
#   ./scripts/apply-secret.sh                  # 기본값(prod, app ns)으로 적용
#   ./scripts/apply-secret.sh prod app         # 환경, 네임스페이스 명시
#   ./scripts/apply-secret.sh prod app --restart  # 적용 후 deployment rollout

ENV="${1:-prod}"
NAMESPACE="${2:-app}"
RESTART_FLAG="${3:-}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRET_FILE="${REPO_ROOT}/secrets/${ENV}.yaml"
DEPLOYMENT_NAME="sentinel"

if [[ ! -f "$SECRET_FILE" ]]; then
  echo "❌ Secret 파일 없음: $SECRET_FILE" >&2
  exit 1
fi

if ! command -v sops >/dev/null 2>&1; then
  echo "❌ sops 미설치. 'brew install sops' 실행" >&2
  exit 1
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "❌ kubectl 미설치" >&2
  exit 1
fi

CONTEXT="$(kubectl config current-context)"
echo "▶ context : $CONTEXT"
echo "▶ env     : $ENV"
echo "▶ ns      : $NAMESPACE"
echo "▶ file    : $SECRET_FILE"
read -rp "위 설정으로 적용할까요? [y/N] " ANSWER
[[ "$ANSWER" =~ ^[yY]$ ]] || { echo "취소됨"; exit 0; }

kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || \
  kubectl create namespace "$NAMESPACE"

sops -d "$SECRET_FILE" | kubectl apply -n "$NAMESPACE" -f -

echo "✅ Secret 적용 완료"

if [[ "$RESTART_FLAG" == "--restart" ]]; then
  echo "▶ deployment/${DEPLOYMENT_NAME} rollout restart"
  kubectl rollout restart "deployment/${DEPLOYMENT_NAME}" -n "$NAMESPACE"
  kubectl rollout status  "deployment/${DEPLOYMENT_NAME}" -n "$NAMESPACE"
fi
