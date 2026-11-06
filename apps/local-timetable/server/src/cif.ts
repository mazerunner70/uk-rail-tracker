const LM_TOC = "LM";

export function sliceField(line: string, start: number, end: number): string {
  const padded = line.padEnd(end, " ");
  return padded.slice(start, end).trim();
}

export function normalizeHhmm(raw: string): string {
  const digits = [...raw].filter((ch) => ch >= "0" && ch <= "9").join("");
  if (digits.length < 4) {
    throw new Error(`time must have at least HHMM digits, got ${raw}`);
  }
  return digits.slice(0, 4);
}

export function publicTime(
  line: string,
  workingStart = 10,
  publicStart = 15,
): string | null {
  const pub = sliceField(line, publicStart, publicStart + 4);
  if (pub && pub[0] >= "0" && pub[0] <= "9") {
    return normalizeHhmm(pub);
  }
  const working = sliceField(line, workingStart, workingStart + 4);
  if (working && working[0] >= "0" && working[0] <= "9") {
    return normalizeHhmm(working);
  }
  return null;
}

export function parseTiplocCrs(line: string): { tiploc: string; crs: string | null } {
  const tiploc = sliceField(line, 2, 9);
  let crs: string | null = sliceField(line, 53, 56) || null;
  if (crs && (crs.length !== 3 || !/^[A-Za-z]{3}$/.test(crs))) {
    crs = null;
  }
  return { tiploc, crs: crs ? crs.toUpperCase() : null };
}

export function isLmToc(toc: string): boolean {
  return toc === LM_TOC;
}

export function serviceGuid(
  trainUid: string,
  runDate: string,
  stpIndicator: string,
): string {
  return `${trainUid}_${runDate}_${stpIndicator}`;
}

export type LocationDraft = {
  tiploc: string;
  crs: string | null;
  publicArr: string | null;
  publicDep: string | null;
  activity: string;
};

export type ScheduleDraft = {
  trainUid: string;
  dateFrom: string;
  dateTo: string;
  daysRun: string;
  stpIndicator: string;
  toc: string;
  locations: LocationDraft[];
};

export function emptyDraft(line: string): ScheduleDraft {
  return {
    trainUid: sliceField(line, 3, 9),
    dateFrom: sliceField(line, 9, 15),
    dateTo: sliceField(line, 15, 21),
    daysRun: sliceField(line, 21, 28),
    stpIndicator: sliceField(line, 79, 80) || "P",
    toc: "",
    locations: [],
  };
}

export function applyBxToc(draft: ScheduleDraft, line: string): void {
  draft.toc = sliceField(line, 11, 13);
}

export function appendOrigin(draft: ScheduleDraft, line: string, tipCrs: Map<string, string>): void {
  const tiploc = sliceField(line, 2, 9);
  draft.locations.push({
    tiploc,
    crs: tipCrs.get(tiploc) ?? null,
    publicArr: null,
    publicDep: publicTime(line),
    activity: "origin",
  });
}

export function appendIntermediate(
  draft: ScheduleDraft,
  line: string,
  tipCrs: Map<string, string>,
): void {
  const tiploc = sliceField(line, 2, 9);
  const arr = publicTime(line, 10, 25);
  const dep = publicTime(line, 15, 29);
  const pubArr = sliceField(line, 25, 29);
  const pubDep = sliceField(line, 29, 33);
  const publicArr =
    pubArr && pubArr[0] >= "0" && pubArr[0] <= "9" ? normalizeHhmm(pubArr) : arr;
  const publicDep =
    pubDep && pubDep[0] >= "0" && pubDep[0] <= "9" ? normalizeHhmm(pubDep) : dep;
  const activity = publicArr === null && publicDep === null ? "pass" : "call";
  draft.locations.push({
    tiploc,
    crs: tipCrs.get(tiploc) ?? null,
    publicArr,
    publicDep,
    activity,
  });
}

export function appendTerminate(
  draft: ScheduleDraft,
  line: string,
  tipCrs: Map<string, string>,
): void {
  const tiploc = sliceField(line, 2, 9);
  draft.locations.push({
    tiploc,
    crs: tipCrs.get(tiploc) ?? null,
    publicArr: publicTime(line),
    publicDep: null,
    activity: "destination",
  });
}

export type PreparedJourney = {
  serviceGuid: string;
  trainUid: string;
  runDate: string;
  dateRunsTo: string;
  daysRun: string;
  originCrs: string;
  destCrs: string;
  originStd: string;
  toc: string;
  stpIndicator: string;
  status: string;
  callingPoints: Array<{
    seq: number;
    tiploc: string;
    crs: string | null;
    publicArr: string | null;
    publicDep: string | null;
    activity: string;
  }>;
};

export function prepareLmJourney(
  draft: ScheduleDraft,
  tipCrs: Map<string, string>,
): PreparedJourney | null {
  if (!isLmToc(draft.toc) || !draft.trainUid || draft.locations.length === 0) {
    return null;
  }
  const origin = draft.locations[0];
  const dest = draft.locations[draft.locations.length - 1];
  const originCrs = origin.crs ?? tipCrs.get(origin.tiploc) ?? null;
  const destCrs = dest.crs ?? tipCrs.get(dest.tiploc) ?? null;
  if (!originCrs || !destCrs || !origin.publicDep) {
    return null;
  }
  const runDate = draft.dateFrom;
  const stp = draft.stpIndicator || "P";
  const guid = serviceGuid(draft.trainUid, runDate, stp);
  return {
    serviceGuid: guid,
    trainUid: draft.trainUid,
    runDate,
    dateRunsTo: draft.dateTo || runDate,
    daysRun: draft.daysRun || "0000000",
    originCrs,
    destCrs,
    originStd: origin.publicDep,
    toc: draft.toc,
    stpIndicator: stp,
    status: "published",
    callingPoints: draft.locations.map((loc, idx) => ({
      seq: idx,
      tiploc: loc.tiploc,
      crs: loc.crs ?? tipCrs.get(loc.tiploc) ?? null,
      publicArr: loc.publicArr,
      publicDep: loc.publicDep,
      activity: loc.activity,
    })),
  };
}
