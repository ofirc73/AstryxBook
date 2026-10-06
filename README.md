# Download stats

Weekly snapshot of GitHub release download counts, written by
`.github/workflows/download-stats.yml` on `main`. Each run appends one row per
release to `downloads.csv` (`date,tag,downloads`, date in UTC). Counts are
cumulative per release, so the weekly growth is the difference between two
snapshots.

This branch holds data only; it never merges into `main`.
