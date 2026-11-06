export type ScheduleWindow = {
  serviceGuid: string;
  trainUid: string;
  dateRunsFrom: string;
  dateRunsTo: string;
  daysRun: string;
  stpIndicator: string;
};

export type ServiceDayRow = {
  trainUid: string;
  calendarDate: string;
  serviceGuid: string | null;
  cancelled: boolean;
};

export function parseYymmdd(value: string): Date {
  const digits = [...value].filter((ch) => ch >= "0" && ch <= "9").join("");
  if (digits.length !== 6) {
    throw new Error(`expected YYMMDD, got ${value}`);
  }
  const yy = Number(digits.slice(0, 2));
  const mm = Number(digits.slice(2, 4));
  const dd = Number(digits.slice(4, 6));
  const year = yy >= 69 ? 1900 + yy : 2000 + yy;
  return new Date(Date.UTC(year, mm - 1, dd));
}

export function formatYymmdd(value: Date): string {
  const yy = String(value.getUTCFullYear()).slice(-2);
  const mm = String(value.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(value.getUTCDate()).padStart(2, "0");
  return `${yy}${mm}${dd}`;
}

function cifWeekdayMon0(value: Date): number {
  return (value.getUTCDay() + 6) % 7;
}

export function iterMatchingDates(
  dateRunsFrom: string,
  dateRunsTo: string,
  daysRun: string,
): string[] {
  const start = parseYymmdd(dateRunsFrom);
  const end = parseYymmdd(dateRunsTo);
  if (end < start) {
    return [];
  }
  const bits = (daysRun || "").padEnd(7, "0").slice(0, 7);
  const out: string[] = [];
  const cursor = new Date(start.getTime());
  while (cursor <= end) {
    if (bits[cifWeekdayMon0(cursor)] === "1") {
      out.push(formatYymmdd(cursor));
    }
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return out;
}

export function coversDate(schedule: ScheduleWindow, calendarDate: string): boolean {
  try {
    const day = parseYymmdd(calendarDate);
    const start = parseYymmdd(schedule.dateRunsFrom);
    const end = parseYymmdd(schedule.dateRunsTo);
    if (day < start || day > end) {
      return false;
    }
    const bits = (schedule.daysRun || "").padEnd(7, "0").slice(0, 7);
    return bits[cifWeekdayMon0(day)] === "1";
  } catch {
    return false;
  }
}

function firstByStp(
  byStp: Map<string, ScheduleWindow[]>,
  stp: string,
): ScheduleWindow | null {
  const rows = byStp.get(stp) ?? [];
  if (rows.length === 0) {
    return null;
  }
  return [...rows].sort((a, b) => a.serviceGuid.localeCompare(b.serviceGuid))[0];
}

export function pickWinner(
  candidates: ScheduleWindow[],
): { serviceGuid: string | null; cancelled: boolean } {
  if (candidates.length === 0) {
    return { serviceGuid: null, cancelled: false };
  }
  const byStp = new Map<string, ScheduleWindow[]>();
  for (const schedule of candidates) {
    const stp = schedule.stpIndicator || "P";
    const rows = byStp.get(stp) ?? [];
    rows.push(schedule);
    byStp.set(stp, rows);
  }
  if (firstByStp(byStp, "C") !== null) {
    return { serviceGuid: null, cancelled: true };
  }
  const overlay = firstByStp(byStp, "O");
  if (overlay) {
    return { serviceGuid: overlay.serviceGuid, cancelled: false };
  }
  const permanent = firstByStp(byStp, "P");
  if (permanent) {
    return { serviceGuid: permanent.serviceGuid, cancelled: false };
  }
  const neu = firstByStp(byStp, "N");
  if (neu) {
    return { serviceGuid: neu.serviceGuid, cancelled: false };
  }
  const fallback = [...candidates].sort((a, b) =>
    a.serviceGuid.localeCompare(b.serviceGuid),
  )[0];
  return { serviceGuid: fallback.serviceGuid, cancelled: false };
}

export function resolveServiceDays(schedules: ScheduleWindow[]): ServiceDayRow[] {
  const byUid = new Map<string, ScheduleWindow[]>();
  for (const schedule of schedules) {
    const rows = byUid.get(schedule.trainUid) ?? [];
    rows.push(schedule);
    byUid.set(schedule.trainUid, rows);
  }
  const resolved: ServiceDayRow[] = [];
  for (const [trainUid, rows] of byUid) {
    const dates = new Set<string>();
    for (const schedule of rows) {
      for (const day of iterMatchingDates(
        schedule.dateRunsFrom,
        schedule.dateRunsTo,
        schedule.daysRun,
      )) {
        dates.add(day);
      }
    }
    for (const calendarDate of [...dates].sort()) {
      const candidates = rows.filter((schedule) => coversDate(schedule, calendarDate));
      const winner = pickWinner(candidates);
      resolved.push({
        trainUid,
        calendarDate,
        serviceGuid: winner.serviceGuid,
        cancelled: winner.cancelled,
      });
    }
  }
  return resolved;
}
