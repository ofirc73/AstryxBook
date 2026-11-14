#!/usr/bin/env bash
# Every release's tag and its assets' download count, tab-separated, one release a line, from every
# page of the releases: what the stats workflows read. Fails on any failed call, so neither writes a
# partial answer (download-stats.yml appended the pages read before a failure as a week's snapshot).
set -euo pipefail
gh api --paginate "repos/${GITHUB_REPOSITORY}/releases?per_page=100" \
  --jq '.[] | [.tag_name, ([.assets[].download_count] | add // 0)] | @tsv'
