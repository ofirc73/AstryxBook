#!/bin/bash
# Cloud sessions start with git set to Claude's own identity (noreply@anthropic.com),
# which no GitHub account owns: commits made that way don't count on the maintainer's
# contributions graph. Commit as the maintainer instead (Claude stays a co-author through
# the Co-Authored-By line). Local sessions keep their own git setup.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

git config --global user.name "Ofir Cohen"
git config --global user.email ofirco@gmail.com
