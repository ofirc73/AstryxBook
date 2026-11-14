#!/usr/bin/env bash
# Commits and pushes the files named after the message ($1) in the `stats` branch's checkout (the
# current directory), as the Actions bot; nothing when they didn't change. Shared by
# download-stats.yml and download-badge.yml.
set -euo pipefail
message=$1
shift
git add "$@"
if git diff --cached --quiet; then exit 0; fi
git config user.name "github-actions[bot]"
git config user.email "41898283+github-actions[bot]@users.noreply.github.com"
git commit -q -m "$message"
git push -q
