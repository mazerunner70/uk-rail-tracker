import { createLoadController } from "./start.js";

export type { LoadProgress, LoadState } from "./loadProgress.js";
export type { LoadController, LoadControllerOptions, OverlayHooks } from "./start.js";
export { createLoadController } from "./start.js";
export {
  createKafkaOverlay,
  createIdleKafkaOverlay,
} from "./kafkaOverlay.js";
export {
  CONTAINER_DB_DIR,
  CONTAINER_MCA_DIR,
  CONTAINER_SQLITE_PATH,
} from "./paths.js";

/** Optional dev override: filename must still contain this YYYYMMDD (Europe/London). */
function calendarDateFromEnv(): string | undefined {
  const value = process.env.MCA_CALENDAR_DATE?.trim();
  if (!value) {
    return undefined;
  }
  if (!/^\d{8}$/.test(value)) {
    return undefined;
  }
  return value;
}

const envCalendarDate = calendarDateFromEnv();

const production = createLoadController(
  envCalendarDate ? { calendarDate: envCalendarDate } : {},
);

/** In-process start. Returns at once. The later query unit imports this. */
export const start = production.start;

/** In-process progress. Does not start a load. */
export const progress = production.progress;
