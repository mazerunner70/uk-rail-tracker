import { Worker } from "node:worker_threads";
import { CONTAINER_SQLITE_PATH } from "./paths.js";
import type { WorkerInbound, WorkerOutbound } from "./kafkaWorker.js";

export type KafkaOverlay = {
  pause: () => Promise<void>;
  resume: () => Promise<void>;
  stop: () => Promise<void>;
};

type Pending = {
  resolve: () => void;
  reject: (err: Error) => void;
  kind: "pause" | "resume";
};

export type KafkaOverlayOptions = {
  sqlitePath?: string;
  workerUrl?: URL;
};

export function createKafkaOverlay(
  options: KafkaOverlayOptions = {},
): KafkaOverlay {
  const sqlitePath = options.sqlitePath ?? CONTAINER_SQLITE_PATH;
  const workerUrl =
    options.workerUrl ?? new URL("./kafkaWorker.js", import.meta.url);

  let worker: Worker | null = null;
  let pending: Pending | null = null;

  function ensureWorker(): Worker {
    if (worker) {
      return worker;
    }
    worker = new Worker(workerUrl, {
      workerData: { sqlitePath },
    });
    worker.on("message", (message: WorkerOutbound) => {
      if (!pending) {
        return;
      }
      if (message.type === "paused" && pending.kind === "pause") {
        const done = pending;
        pending = null;
        done.resolve();
        return;
      }
      if (
        (message.type === "resumed" || message.type === "idle") &&
        pending.kind === "resume"
      ) {
        const done = pending;
        pending = null;
        done.resolve();
      }
    });
    worker.on("error", (err) => {
      if (pending) {
        const done = pending;
        pending = null;
        done.reject(err);
      }
    });
    worker.on("exit", () => {
      worker = null;
      if (pending) {
        const done = pending;
        pending = null;
        done.reject(new Error("kafka overlay worker exited"));
      }
    });
    return worker;
  }

  function send(message: WorkerInbound): void {
    ensureWorker().postMessage(message);
  }

  return {
    pause: () =>
      new Promise<void>((resolve, reject) => {
        pending = { resolve, reject, kind: "pause" };
        send({ type: "pause" });
      }),
    resume: () =>
      new Promise<void>((resolve, reject) => {
        pending = { resolve, reject, kind: "resume" };
        send({ type: "resume" });
      }),
    stop: async () => {
      if (!worker) {
        return;
      }
      const current = worker;
      worker = null;
      await current.terminate();
    },
  };
}

/** No-op overlay for tests that never open a broker. */
export function createIdleKafkaOverlay(): KafkaOverlay {
  return {
    pause: async () => undefined,
    resume: async () => undefined,
    stop: async () => undefined,
  };
}
