import { start, progress } from "./index.js";
import { createTimetableHttpServer } from "./httpServer.js";

const port = Number(process.env.PORT ?? "8080");

const server = createTimetableHttpServer({
  load: {
    startLoad: start,
    getLoadProgress: progress,
  },
});

server.listen(port, () => {
  console.log(`timetable-query listening on ${port}`);
});
