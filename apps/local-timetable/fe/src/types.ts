export type LoadState = "idle" | "running" | "succeeded" | "failed";

export type LoadProgress = {
  state: LoadState;
  howFar?: string;
  sentence: string;
};

export type NetworkStop = {
  crs?: string;
  arr: string | null;
  dep: string | null;
};

export type StatusLabel = "cancelled" | "deactivated" | "delayed" | "on time";

/** Matches server EventTag / kafka-parsing §6. */
export type EventTag =
  | "arrived"
  | "departed"
  | "pass_forecast"
  | "passed_location"
  | "call_forecast"
  | "delayed"
  | "actual_removed"
  | "cancelled"
  | "cancel_cleared"
  | "deactivated"
  | "platform_set"
  | "platform_suppressed"
  | "length_reported"
  | "formation_changed"
  | "rerouted"
  | "associated"
  | "alert";

export type NetworkTrain = {
  serviceGuid: string;
  originCrs?: string | null;
  destCrs?: string | null;
  reachedCrs?: string | null;
  nextCrs?: string | null;
  scheduledTime?: string | null;
  problem: boolean;
  statusLabel?: StatusLabel;
  delayMinutes?: number | null;
  stops?: NetworkStop[];
  eventTags?: EventTag[];
};

export type RemovedTrain = {
  serviceGuid: string;
  trainUid: string;
  eventTags?: EventTag[];
};

export type NetworkAnswer = {
  calendarDate: string;
  trains: NetworkTrain[];
  generation: string;
  revision: number;
  full: boolean;
  removed: RemovedTrain[];
};

export type StationLayout = {
  crs: string;
  x: number;
  y: number;
  rank: number;
  lane: number;
};

export type CorridorLayout = {
  from: string;
  to: string;
  via: string[];
  points: { x: number; y: number }[];
  color: string;
};

export type SchematicLine = {
  id: string;
  stations: string[];
};

export type NetworkDiagram = {
  loadDay: string;
  hdSequence: string;
  stations: StationLayout[];
  corridors: CorridorLayout[];
  /** Ordered CRS paths for tracking; may be [] on older schematics. */
  lines?: SchematicLine[];
  width: number;
  height: number;
};

export type PairTrain = {
  serviceGuid?: string | null;
  trainUid?: string;
  cancellationLabel?: string;
  status: string;
  reachedCrs?: string | null;
  nextCrs?: string | null;
  issue: boolean;
};

export type StationPairAnswer = {
  calendarDate: string;
  earlierCrs: string;
  laterCrs: string;
  trains: PairTrain[];
};

export type FailureBody = { reason: "missing" | "deleted" | "locked" | "older-schema" };

export type AskResult<T> =
  | { ok: true; data: T }
  | { ok: false; kind: "comms" | "train-failure" | "parse"; reason?: string };
