#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <CONFIG_FILE> <aws arguments...>" >&2
  exit 1
fi

config_file="$1"
if [[ ! -f "$config_file" ]]; then
  echo "Config file not found: $config_file" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$config_file"
shift

if [[ $# -eq 0 ]]; then
  echo "No AWS CLI command supplied." >&2
  exit 1
fi

if [[ -n "${trk_oidc_jwt_token:-}" ]]; then
  if [[ -z "${trk_oidc_role_arn:-}" ]]; then
    echo "Missing trk_oidc_role_arn for web identity credentials." >&2
    exit 1
  fi

  session_name="${trk_oidc_role_session_name:-trk-sync-$(date +%s)}"
  region_args=()
  if [[ -n "${trk_oidc_sts_region:-}" ]]; then
    region_args+=(--region "$trk_oidc_sts_region")
  fi

  unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN

  read -r derived_access_key derived_secret_key derived_session_token <<<"$(
    aws sts assume-role-with-web-identity \
      --role-arn "$trk_oidc_role_arn" \
      --role-session-name "$session_name" \
      --web-identity-token "$trk_oidc_jwt_token" \
      --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \
      --output text \
      "${region_args[@]}"
  )"

  export AWS_ACCESS_KEY_ID="$derived_access_key"
  export AWS_SECRET_ACCESS_KEY="$derived_secret_key"
  export AWS_SESSION_TOKEN="$derived_session_token"
elif [[ -n "${trk_aws_access_key_id:-}" && -n "${trk_aws_secret_access_key:-}" ]]; then
  export AWS_ACCESS_KEY_ID="$trk_aws_access_key_id"
  export AWS_SECRET_ACCESS_KEY="$trk_aws_secret_access_key"
  if [[ -n "${trk_aws_session_token:-}" ]]; then
    export AWS_SESSION_TOKEN="$trk_aws_session_token"
  else
    unset AWS_SESSION_TOKEN 2>/dev/null || true
  fi
else
  echo "No supported credentials found in $config_file." >&2
  exit 1
fi

aws "$@"
