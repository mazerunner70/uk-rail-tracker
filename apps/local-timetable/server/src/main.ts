import { createKafkaOverlay } from "./kafkaOverlay.js";
import { createLoadController } from "./start.js";
import { createTimetableHttpServer } from "./httpServer.js";
import { CONTAINER_SQLITE_PATH } from "./paths.js";

const port = Number(process.env.PORT ?? "8080");

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

const overlay = createKafkaOverlay({ sqlitePath: CONTAINER_SQLITE_PATH });
const envCalendarDate = calendarDateFromEnv();
const load = createLoadController({
  overlay,
  ...(envCalendarDate ? { calendarDate: envCalendarDate } : {}),
});

const server = createTimetableHttpServer({
  load: {
    startLoad: load.start,
    getLoadProgress: load.progress,
  },
});

server.listen(port, () => {
  console.log(`timetable-query listening on ${port}`);
  void overlay.resume();
});
