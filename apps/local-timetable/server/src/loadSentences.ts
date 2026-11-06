export const LOAD_FAIL_SENTENCE = {
  headerRefused: "The MCA header was refused.",
  writeFailed: "The timetable file could not be written.",
  mcaFileUnreadable: "The MCA file cannot be read.",
} as const;

export function locateFailureSentence(
  reason: "missing-dir" | "no-match" | "duplicate",
  calendarDate: string,
): string {
  switch (reason) {
    case "missing-dir":
      return "The MCA directory cannot be read.";
    case "no-match":
      return `No MCA filename contains ${calendarDate}.`;
    case "duplicate":
      return `More than one MCA filename contains ${calendarDate}.`;
  }
}
