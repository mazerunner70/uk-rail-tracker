export type CallingPointTimes = {
  publicArr: string | null;
  publicDep: string | null;
  forecastArr: string | null;
  forecastDep: string | null;
};

export function pickStopTimeHhmm(point: CallingPointTimes): string | null {
  return (
    point.forecastDep ??
    point.forecastArr ??
    point.publicDep ??
    point.publicArr ??
    null
  );
}

function minutes(hhmm: string): number {
  return Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(2, 4));
}

export function stopIsNextCalendarDay(
  stopHhmm: string,
  originStopHhmm: string,
): boolean {
  return minutes(stopHhmm) < minutes(originStopHhmm);
}

export function comparableMinutes(
  stopHhmm: string,
  originStopHhmm: string,
): number {
  return (
    minutes(stopHhmm) +
    (stopIsNextCalendarDay(stopHhmm, originStopHhmm) ? 24 * 60 : 0)
  );
}

export function nowComparableMinutes(
  originStopHhmm: string,
  nowHhmm: string,
): number {
  const nowMin = minutes(nowHhmm);
  const originMin = minutes(originStopHhmm);
  if (nowMin < originMin) {
    if (nowMin < 6 * 60 && originMin >= 18 * 60) {
      return nowMin + 24 * 60;
    }
    return nowMin;
  }
  return nowMin;
}

export type StopRow = CallingPointTimes & { seq: number; crs: string | null };

function reachedStopIndex(points: StopRow[], nowHhmm: string): number {
  const origin = pickStopTimeHhmm(points[0]!);
  if (!origin) {
    return -1;
  }
  const nowMin = nowComparableMinutes(origin, nowHhmm);
  let reachedIndex = -1;
  for (let i = 0; i < points.length; i++) {
    const stop = pickStopTimeHhmm(points[i]!);
    if (!stop) {
      continue;
    }
    if (comparableMinutes(stop, origin) <= nowMin) {
      reachedIndex = i;
    }
  }
  return reachedIndex;
}

function nextStop(points: StopRow[], nowHhmm: string): StopRow | undefined {
  const reachedIndex = reachedStopIndex(points, nowHhmm);
  if (reachedIndex === -1) {
    return points[0];
  }
  return points[reachedIndex + 1];
}

export function reachedAndNextCrs(
  points: StopRow[],
  nowHhmm: string,
): { reachedCrs: string | null; nextCrs: string | null } {
  if (points.length === 0) {
    return { reachedCrs: null, nextCrs: null };
  }
  const reachedIndex = reachedStopIndex(points, nowHhmm);
  if (reachedIndex === -1) {
    return { reachedCrs: null, nextCrs: points[0]?.crs ?? null };
  }
  const reachedCrs = points[reachedIndex]?.crs ?? null;
  const next = points[reachedIndex + 1];
  return { reachedCrs, nextCrs: next?.crs ?? null };
}

function terminusArrivalHhmm(point: CallingPointTimes): string | null {
  return point.forecastArr ?? point.publicArr ?? null;
}

export function scheduledHhmm(points: StopRow[], nowHhmm: string): string | null {
  if (points.length === 0) {
    return null;
  }
  const next = nextStop(points, nowHhmm);
  if (next) {
    return pickStopTimeHhmm(next);
  }
  const terminus = points[points.length - 1];
  return terminus ? terminusArrivalHhmm(terminus) : null;
}
