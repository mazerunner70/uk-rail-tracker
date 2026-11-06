export type LoadState = "idle" | "running" | "succeeded" | "failed";

export type LoadProgress = {
  state: LoadState;
  howFar?: string;
  sentence: string;
  /** MCA bytes read so far while streaming (running / succeeded). */
  bytesRead?: number;
  /** Total MCA file size in bytes when streaming (running / succeeded). */
  fileBytes?: number;
};

export const IDLE_PROGRESS: LoadProgress = {
  state: "idle",
  sentence: "No load has been started.",
};
