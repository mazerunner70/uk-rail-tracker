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

export type NetworkTrain = {
  serviceGuid: string;
  originCrs?: string | null;
  destCrs?: string | null;
  reachedCrs?: string | null;
  nextCrs?: string | null;
  scheduledTime?: string | null;
  problem: boolean;
  stops?: NetworkStop[];
};

export type NetworkAnswer = {
  calendarDate: string;
  trains: NetworkTrain[];
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

export type NetworkDiagram = {
  loadDay: string;
  hdSequence: string;
  stations: StationLayout[];
  corridors: CorridorLayout[];
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
