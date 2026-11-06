import assert from "node:assert/strict";
import { chmod, mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { describe, it } from "node:test";
import { locateDayMca, londonYyyymmdd } from "../../src/dayMcaLocator.js";
import { tempDir } from "./helpers.js";

describe("DayMcaLocator", () => {
  it("returns the unique filename containing the injected date", async () => {
    const dir = await tempDir();
    await writeFile(join(dir, "RJTTF20261027.MCA"), "x");
    const found = locateDayMca(dir, "20261027");
    assert.equal(found.ok, true);
    if (found.ok) {
      assert.equal(found.path, join(dir, "RJTTF20261027.MCA"));
    }
  });

  it("fails when no filename contains the date", async () => {
    const dir = await tempDir();
    await writeFile(join(dir, "RJTTF261026.MCA"), "x");
    const found = locateDayMca(dir, "20261027");
    assert.deepEqual(found, { ok: false, reason: "no-match" });
  });

  it("fails when more than one filename contains the date", async () => {
    const dir = await tempDir();
    await writeFile(join(dir, "a-20261027.mca"), "x");
    await writeFile(join(dir, "b-20261027.mca"), "y");
    const found = locateDayMca(dir, "20261027");
    assert.deepEqual(found, { ok: false, reason: "duplicate" });
  });

  it("fails when the directory is missing", () => {
    const found = locateDayMca("/tmp/mca-load-missing-dir-does-not-exist", "20261027");
    assert.deepEqual(found, { ok: false, reason: "missing-dir" });
  });

  it("ignores files that do not contain the injected date", async () => {
    const dir = await tempDir();
    await writeFile(join(dir, "notes.txt"), "no date");
    await writeFile(join(dir, "full-20261027-mca.txt"), "ok");
    const found = locateDayMca(dir, "20261027");
    assert.equal(found.ok, true);
    if (found.ok) {
      assert.ok(found.path.endsWith("full-20261027-mca.txt"));
    }
  });

  it("does not depend on the wall clock when a calendar date is injected", async () => {
    const dir = await tempDir();
    const today = londonYyyymmdd();
    const other = today === "20260101" ? "20260102" : "20260101";
    await writeFile(join(dir, `file-${other}.mca`), "x");
    const found = locateDayMca(dir, other);
    assert.equal(found.ok, true);
    const wrong = locateDayMca(dir, today === other ? "19990101" : today);
    assert.equal(wrong.ok, false);
  });

  it("fails when the directory cannot be read", async (t) => {
    const parent = await tempDir();
    const dir = join(parent, "locked");
    await mkdir(dir);
    await writeFile(join(dir, "20261027.mca"), "x");
    await chmod(dir, 0);
    try {
      const found = locateDayMca(dir, "20261027");
      if (found.ok) {
        t.skip("process can still read a chmod 000 directory");
        return;
      }
      assert.equal(found.ok, false);
    } finally {
      await chmod(dir, 0o700);
    }
  });
});
