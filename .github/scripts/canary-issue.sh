#!/usr/bin/env bash
# The daily Facebook check's issues (facebook-canary.yml): one open issue per title.
#   canary-issue.sh close <title> <comment>   closes it, when open
#   canary-issue.sh comment <title> <body>    comments on it, or opens it
#   canary-issue.sh edit <title> <body>       rewrites its text, or opens it
# The open issues are listed and matched here, not with --search: search once missed an issue
# opened hours before, so a passing run didn't close it.
set -eu
ACTION=$1 TITLE=$2 TEXT=$3
OPEN=$(gh issue list --state open --limit 200 --json number,title \
  -q ".[] | select(.title == \"$TITLE\") | .number" | head -1)
case $ACTION in
  close) if [ -n "$OPEN" ]; then gh issue close "$OPEN" --comment "$TEXT"; fi ;;
  comment | edit)
    if [ -z "$OPEN" ]; then gh issue create --title "$TITLE" --body "$TEXT"
    elif [ "$ACTION" = comment ]; then gh issue comment "$OPEN" --body "$TEXT"
    else gh issue edit "$OPEN" --body "$TEXT"; fi ;;
  *) echo "usage: $0 close|comment|edit <title> <text>" >&2; exit 2 ;;
esac
