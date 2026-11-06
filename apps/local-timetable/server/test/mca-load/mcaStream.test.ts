import assert from "node:assert/strict";
import { chmod, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { describe, it } from "node:test";
import { howFarText, iterateMcaLines } from "../../src/mcaStream.js";
import { makeHd, makeMca, tempDir } from "./helpers.js";

describe("McaStream", () => {
  it("yields lines from a synthetic MCA without requiring a trailing newline on the last line", async () => {
    const dir = await tempDir();
    const path = join(dir, "tiny.mca");
    await writeFile(path, `${makeHd("991")}\nTIHELLO\nZZ`, "utf8");
    const lines: string[] = [];
    for await (const line of iterateMcaLines(path)) {
      lines.push(line);
    }
    assert.equal(lines[0]?.slice(0, 2), "HD");
    assert.equal(lines.at(-1), "ZZ");
  });

  it("reports howFar 100 after the stream finishes", async () => {
    const dir = await tempDir();
    const path = join(dir, "progress.mca");
    await writeFile(path, makeMca({}), "utf8");
    let last = "0";
    for await (const _line of iterateMcaLines(path, (readProgress) => {
      last = readProgress.howFar;
    })) {
      // drain
    }
    assert.equal(last, "100");
  });

  it("fails when the file is missing", async () => {
    await assert.rejects(() =>
      iterateMcaLines("/tmp/mca-load-missing-file-does-not-exist.mca").next(),
    );
  });

  it("fails when the file cannot be read", async (t) => {
    const dir = await tempDir();
    const path = join(dir, "locked.mca");
    await writeFile(path, makeMca({}), "utf8");
    await chmod(path, 0);
    try {
      await iterateMcaLines(path).next();
      t.skip("process can still read a chmod 000 file");
    } catch {
      assert.ok(true);
    } finally {
      await chmod(path, 0o644);
    }
  });

  it("keeps howFar as percent text between 0 and 100", () => {
    assert.equal(howFarText(0, 100), "0");
    assert.equal(howFarText(50, 100), "50");
    assert.equal(howFarText(100, 100), "100");
    assert.equal(howFarText(0, 0), "100");
  });

  it("does not hold the whole file as one leftover string", async () => {
    const dir = await tempDir();
    const path = join(dir, "chunked.mca");
    const body = `${"HD".padEnd(80, "A")}\n${"TI".padEnd(80, "B")}\nZZ\n`;
    await writeFile(path, body, "utf8");
    const seen: string[] = [];
    for await (const line of iterateMcaLines(path)) {
      seen.push(line);
      assert.ok(line.length < body.length);
    }
    assert.equal(seen.length, 3);
  });

  it("updates howFar as bytes are read", async () => {
    const dir = await tempDir();
    const path = join(dir, "bytes.mca");
    await writeFile(path, `${"x".repeat(8000)}\n`, "utf8");
    const marks: string[] = [];
    for await (const _line of iterateMcaLines(path, (readProgress) => {
      marks.push(readProgress.howFar);
    })) {
      // drain
    }
    assert.ok(marks.length >= 1);
    assert.equal(marks.at(-1), "100");
  });
});
