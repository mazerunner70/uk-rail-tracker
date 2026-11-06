const DIGITS_RE = /(\d{3})/;
const NAME_SEQUENCE_RE = /RJTT[FC](\d{3})/i;

export type HeaderResult =
  | { ok: true; sequence: string }
  | { ok: false };

/**
 * RSPS5046 §5.5.1: an HD record is valid when its identity is HD and its
 * update indicator (column 47) is F (full extract) or U (update). Columns
 * 33–39 are a unique file reference, often DFTTISS on a full MCA, and need
 * not contain digits. The DTD sequence nnn is the RJTTFnnn / RJTTCnnn
 * filename token (§4.2). A three-digit run in the file reference is used
 * only when the filename has no token.
 */
export function acceptHeader(hdLine: string, filename?: string): HeaderResult {
  const line = hdLine.replace(/[\r\n]+$/g, "");
  if (!line.startsWith("HD") || line.length < 47) {
    return { ok: false };
  }
  const padded = line.padEnd(80, " ");
  const update = padded.slice(46, 47);
  if (update !== "F" && update !== "U") {
    return { ok: false };
  }
  const fromName = sequenceFromMcaName(filename);
  if (fromName) {
    return { ok: true, sequence: fromName };
  }
  const currentRef = padded.slice(32, 39).trim();
  const digits = DIGITS_RE.exec(currentRef)?.[1];
  if (digits) {
    return { ok: true, sequence: digits };
  }
  if (!currentRef) {
    return { ok: false };
  }
  return { ok: true, sequence: currentRef };
}

function sequenceFromMcaName(filename: string | undefined): string | undefined {
  if (!filename) {
    return undefined;
  }
  const base = filename.split(/[/\\]/).pop() ?? filename;
  return NAME_SEQUENCE_RE.exec(base)?.[1];
}
