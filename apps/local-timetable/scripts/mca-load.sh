#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_compose.sh
source "${DIR}/_compose.sh"

compose exec ${MCA_CALENDAR_DATE:+-e "MCA_CALENDAR_DATE=${MCA_CALENDAR_DATE}"} server node --input-type=module -e "
import { start, progress } from './dist/index.js';
const loadStarted = Date.now();
const formatRate = (bytesPerSec) => {
  if (bytesPerSec >= 1_000_000) {
    return \`\${(bytesPerSec / 1_000_000).toFixed(1)} MB/s\`;
  }
  if (bytesPerSec >= 1_000) {
    return \`\${(bytesPerSec / 1_000).toFixed(1)} kB/s\`;
  }
  return \`\${bytesPerSec} B/s\`;
};
console.log('start:', start());
const t = setInterval(() => {
  const p = progress();
  const elapsedSec = (Date.now() - loadStarted) / 1000;
  const bytesRead = p.bytesRead ?? 0;
  const bps = elapsedSec > 0 ? Math.round(bytesRead / elapsedSec) : 0;
  console.log(p.state, p.howFar ?? '-', formatRate(bps), p.sentence);
  if (p.state === 'succeeded' || p.state === 'failed') clearInterval(t);
}, 500);
"
