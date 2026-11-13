#!/usr/bin/env bash
# create-release.yml's last job: a release run that failed says so in the "Release run failed"
# issue (opened, or commented on while it's open), and a run that released closes it. A failed
# release run used to show only on the Actions page: PR 176's two features sat on main,
# unreleased, until someone looked (2026-11-11).
#   release-report.sh <tag, or ""> <job>=<result>...
# Needs gh (GH_TOKEN) and GitHub's own run variables.
set -eu
TITLE="Release run failed"
TAG=$1
shift
RUN="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
FAILED=
RELEASED=
for JOB in "$@"; do
  case $JOB in
    *=failure) FAILED="$FAILED${FAILED:+, }${JOB%=failure}" ;;
    release=success) RELEASED=true ;;
  esac
done
ISSUE="$(dirname "$0")/canary-issue.sh"
if [ -n "$FAILED" ]; then
  bash "$ISSUE" comment "$TITLE" "The release run for ${TAG:-a version it never got to name}, at $(echo "$GITHUB_SHA" | cut -c1-7), failed in: $FAILED (attempt $GITHUB_RUN_ATTEMPT).

$RUN

What's on main since the last release isn't out until a release run passes. If a test was flaky, run the failed jobs again: \`gh run rerun $GITHUB_RUN_ID --failed\`. This issue closes by itself when a release goes out."
elif [ -n "$RELEASED" ]; then
  bash "$ISSUE" close "$TITLE" "Released $TAG: $RUN"
fi
