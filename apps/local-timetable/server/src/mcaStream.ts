import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";

export function howFarText(bytesRead: number, size: number): string {
  if (size <= 0) {
    return "100";
  }
  return String(Math.min(1000, Math.floor((bytesRead / size) * 1000) / 10));
}

export type McaStreamProgress = {
  howFar: string;
  bytesRead: number;
  fileBytes: number;
};

export async function* iterateMcaLines(
  filePath: string,
  onProgress?: (progress: McaStreamProgress) => void,
): AsyncGenerator<string> {
  const size = (await stat(filePath)).size;
  const stream = createReadStream(filePath, { highWaterMark: 1024 * 1024 });
  let read = 0;
  let leftover = "";
  const emitProgress = (): void => {
    onProgress?.({
      howFar: howFarText(read, size),
      bytesRead: read,
      fileBytes: size,
    });
  };
  try {
    for await (const chunk of stream) {
      const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
      read += buf.length;
      emitProgress();
      leftover += buf.toString("utf8");
      const parts = leftover.split(/\r?\n/);
      leftover = parts.pop() ?? "";
      for (const line of parts) {
        yield line;
      }
    }
    if (leftover.length > 0) {
      yield leftover;
    }
    emitProgress();
  } finally {
    stream.destroy();
  }
}
