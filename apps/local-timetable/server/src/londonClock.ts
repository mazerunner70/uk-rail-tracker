/** Europe/London calendar and clock for train views (FR4). */

export function londonYyyymmdd(now = new Date()): string {
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = fmt.formatToParts(now);
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  if (!year || !month || !day) {
    throw new Error("could not format Europe/London calendar date");
  }
  return `${year}${month}${day}`;
}

/** `service_day.calendar_date` token (YYMMDD). */
export function londonYyymmdd(now = new Date()): string {
  return londonYyyymmdd(now).slice(2);
}

export function londonHhmm(now = new Date()): string {
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = fmt.formatToParts(now);
  const hour = parts.find((part) => part.type === "hour")?.value;
  const minute = parts.find((part) => part.type === "minute")?.value;
  if (hour === undefined || minute === undefined) {
    throw new Error("could not format Europe/London time");
  }
  return `${hour.padStart(2, "0")}${minute.padStart(2, "0")}`;
}

export type LondonClock = {
  calendarYyymmdd: () => string;
  nowHhmm: () => string;
};

export function defaultLondonClock(now = new Date()): LondonClock {
  return {
    calendarYyymmdd: () => londonYyymmdd(now),
    nowHhmm: () => londonHhmm(now),
  };
}
