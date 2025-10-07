#!/usr/bin/env bash
# use_temp_creds.sh

source "$1"

export AWS_ACCESS_KEY_ID="$trk_aws_access_key_id"
export AWS_SECRET_ACCESS_KEY="$trk_aws_secret_access_key"
export AWS_SESSION_TOKEN="$trk_aws_session_token"

shift
aws "$@"
