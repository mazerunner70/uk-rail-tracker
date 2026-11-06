import { IDLE_PROGRESS, type LoadProgress } from "./loadProgress.js";

export type LoadProgressStore = {
  get: () => LoadProgress;
  set: (next: LoadProgress) => LoadProgress;
};

export function createLoadProgressStore(
  initial: LoadProgress = IDLE_PROGRESS,
): LoadProgressStore {
  let current = initial;
  return {
    get: () => current,
    set: (next) => {
      current = next;
      return current;
    },
  };
}
