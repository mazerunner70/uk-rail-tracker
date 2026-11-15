import { locateDayMca, londonYyyymmdd } from "./dayMcaLocator.js";
import { LOAD_FAIL_SENTENCE, locateFailureSentence } from "./loadSentences.js";
import type { LoadProgress } from "./loadProgress.js";
import {
  createLoadProgressStore,
  type LoadProgressStore,
} from "./loadProgressStore.js";
import {
  createMcaBodyState,
  flushBodyDraft,
  seekHeaderRecord,
  stepMcaBodyLine,
} from "./mcaLoadSteps.js";
import { iterateMcaLines } from "./mcaStream.js";
import { writeNetworkDiagram } from "./networkSchematicFile.js";
import { CONTAINER_MCA_DIR, CONTAINER_SQLITE_PATH } from "./paths.js";
import {
  openReplacement,
  type OpenDatabase,
  type ReplacementSession,
} from "./sqliteReplacer.js";

export type OverlayHooks = {
  pause: () => Promise<void>;
  resume: () => Promise<void>;
};

export type LoadController = {
  start: () => LoadProgress;
  progress: () => LoadProgress;
};

export type LoadControllerOptions = {
  mcaDir?: string;
  sqlitePath?: string;
  store?: LoadProgressStore;
  calendarDate?: string;
  beforeWork?: () => Promise<void>;
  openDatabase?: OpenDatabase;
  overlay?: OverlayHooks;
};

type LoadFailReason = keyof typeof LOAD_FAIL_SENTENCE;

function failLoad(store: LoadProgressStore, sentence: string): void {
  store.set({ state: "failed", sentence });
}

function failLoadReason(store: LoadProgressStore, reason: LoadFailReason): void {
  failLoad(store, LOAD_FAIL_SENTENCE[reason]);
}

function setRunningProgress(
  store: LoadProgressStore,
  howFar: string,
  headerAccepted: boolean,
  bytesRead?: number,
  fileBytes?: number,
): void {
  if (store.get().state !== "running") {
    return;
  }
  const sentence = headerAccepted
    ? "Reading today's MCA."
    : "Looking for today's MCA.";
  store.set({
    state: "running",
    howFar,
    sentence,
    ...(bytesRead === undefined
      ? {}
      : { bytesRead, fileBytes: fileBytes ?? store.get().fileBytes }),
  });
}

function insertIfReady(
  session: ReplacementSession,
  journey: ReturnType<typeof flushBodyDraft>,
): void {
  if (journey) {
    session.insertJourney(journey);
  }
}

async function runLoad(options: {
  mcaDir: string;
  sqlitePath: string;
  store: LoadProgressStore;
  calendarDate: string;
  beforeWork?: () => Promise<void>;
  openDatabase?: OpenDatabase;
  overlay?: OverlayHooks;
}): Promise<void> {
  const { store, overlay } = options;
  await overlay?.pause();
  await options.beforeWork?.();

  const located = locateDayMca(options.mcaDir, options.calendarDate);
  if (!located.ok) {
    failLoad(
      store,
      locateFailureSentence(located.reason, options.calendarDate),
    );
    await overlay?.resume();
    return;
  }

  let headerAccepted = false;
  let sequence = "";
  let session: ReplacementSession | null = null;
  const body = createMcaBodyState();

  try {
    for await (const line of iterateMcaLines(located.path, (readProgress) => {
      setRunningProgress(
        store,
        readProgress.howFar,
        headerAccepted,
        readProgress.bytesRead,
        readProgress.fileBytes,
      );
    })) {
      if (!headerAccepted) {
        const header = seekHeaderRecord(line, located.path);
        if (header.kind === "blank") {
          continue;
        }
        if (header.kind === "invalid") {
          failLoadReason(store, "headerRefused");
          await overlay?.resume();
          return;
        }
        headerAccepted = true;
        sequence = header.sequence;
        setRunningProgress(store, store.get().howFar ?? "0", true);
        const afterHeader = store.get();
        store.set({
          state: "running",
          howFar: afterHeader.howFar ?? "0",
          sentence: "Writing the timetable file.",
          bytesRead: afterHeader.bytesRead,
          fileBytes: afterHeader.fileBytes,
        });
        session = openReplacement(options.sqlitePath, {
          openDatabase: options.openDatabase,
        });
        continue;
      }
      if (!session) {
        break;
      }
      const step = stepMcaBodyLine(body, line);
      insertIfReady(session, step.flush);
      if (step.stop) {
        break;
      }
    }

    if (!headerAccepted) {
      failLoadReason(store, "headerRefused");
      await overlay?.resume();
      return;
    }
    if (!session) {
      failLoadReason(store, "mcaFileUnreadable");
      await overlay?.resume();
      return;
    }
    insertIfReady(session, flushBodyDraft(body));
    session.finalize({
      loadDay: options.calendarDate,
      hdSequence: sequence,
    });
    const afterWrite = store.get();
    store.set({
      state: "running",
      howFar: afterWrite.howFar ?? "100",
      sentence: "Laying out the network map.",
      bytesRead: afterWrite.bytesRead,
      fileBytes: afterWrite.fileBytes,
    });
    try {
      await writeNetworkDiagram(options.sqlitePath);
    } catch (layoutErr) {
      const message = layoutErr instanceof Error ? layoutErr.message : String(layoutErr);
      console.error("network schematic layout failed", message);
      failLoad(store, "The network map could not be laid out.");
      // Timetable file remains; overlay resumes so live data can still attach.
      await overlay?.resume();
      return;
    }
    store.set({
      state: "succeeded",
      howFar: "100",
      sentence: "Today's timetable is loaded.",
      bytesRead: store.get().bytesRead ?? store.get().fileBytes,
      fileBytes: store.get().fileBytes,
    });
    await overlay?.resume();
  } catch (err) {
    session?.abortMissing();
    failLoadReason(store, headerAccepted ? "writeFailed" : "mcaFileUnreadable");
    if (headerAccepted) {
      const message = err instanceof Error ? err.message : String(err);
      console.error("mca-load failed", message);
      // File deleted by abortMissing — stay paused.
    } else {
      await overlay?.resume();
    }
  }
}

export function createLoadController(
  options: LoadControllerOptions = {},
): LoadController {
  const mcaDir = options.mcaDir ?? CONTAINER_MCA_DIR;
  const sqlitePath = options.sqlitePath ?? CONTAINER_SQLITE_PATH;
  const store = options.store ?? createLoadProgressStore();

  return {
    start: () => {
      if (store.get().state === "running") {
        return store.get();
      }
      const calendarDate = options.calendarDate ?? londonYyyymmdd();
      store.set({
        state: "running",
        howFar: "0",
        sentence: "Looking for today's MCA.",
      });
      void runLoad({
        mcaDir,
        sqlitePath,
        store,
        calendarDate,
        beforeWork: options.beforeWork,
        openDatabase: options.openDatabase,
        overlay: options.overlay,
      });
      return store.get();
    },
    progress: () => store.get(),
  };
}
