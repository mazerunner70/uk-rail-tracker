import {
  applyBxToc,
  appendIntermediate,
  appendOrigin,
  appendTerminate,
  emptyDraft,
  parseTiplocCrs,
  prepareLmJourney,
  type PreparedJourney,
  type ScheduleDraft,
} from "./cif.js";
import { acceptHeader } from "./headerGate.js";

const TIPLOC_CODES = new Set(["TI", "TA", "AA"]);

export function recordCode(line: string): string {
  return line.length >= 2 ? line.slice(0, 2) : "";
}

export type SeekHeaderResult =
  | { kind: "blank" }
  | { kind: "invalid" }
  | { kind: "valid"; sequence: string };

export function seekHeaderRecord(line: string, mcaPath: string): SeekHeaderResult {
  if (line.trim().length === 0) {
    return { kind: "blank" };
  }
  const gate = acceptHeader(line, mcaPath);
  if (!gate.ok) {
    return { kind: "invalid" };
  }
  return { kind: "valid", sequence: gate.sequence };
}

export type McaBodyState = {
  tipCrs: Map<string, string>;
  draft: ScheduleDraft | null;
};

export function createMcaBodyState(): McaBodyState {
  return { tipCrs: new Map(), draft: null };
}

export type BodyLineStep = {
  stop: boolean;
  flush: PreparedJourney | null;
};

export function stepMcaBodyLine(state: McaBodyState, line: string): BodyLineStep {
  const rec = recordCode(line);

  if (TIPLOC_CODES.has(rec)) {
    const parsed = parseTiplocCrs(line);
    if (parsed.tiploc && parsed.crs) {
      state.tipCrs.set(parsed.tiploc, parsed.crs);
    }
    return { stop: false, flush: null };
  }
  if (rec === "BS") {
    const flush = state.draft
      ? prepareLmJourney(state.draft, state.tipCrs)
      : null;
    state.draft = emptyDraft(line);
    return { stop: false, flush };
  }
  if (state.draft === null) {
    if (rec === "ZZ") {
      return { stop: true, flush: null };
    }
    return { stop: false, flush: null };
  }
  if (rec === "BX") {
    applyBxToc(state.draft, line);
    return { stop: false, flush: null };
  }
  if (rec === "LO") {
    appendOrigin(state.draft, line, state.tipCrs);
    return { stop: false, flush: null };
  }
  if (rec === "LI") {
    appendIntermediate(state.draft, line, state.tipCrs);
    return { stop: false, flush: null };
  }
  if (rec === "LT") {
    appendTerminate(state.draft, line, state.tipCrs);
    return { stop: false, flush: null };
  }
  if (rec === "ZZ") {
    return { stop: true, flush: null };
  }
  return { stop: false, flush: null };
}

export function flushBodyDraft(state: McaBodyState): PreparedJourney | null {
  if (!state.draft) {
    return null;
  }
  return prepareLmJourney(state.draft, state.tipCrs);
}
