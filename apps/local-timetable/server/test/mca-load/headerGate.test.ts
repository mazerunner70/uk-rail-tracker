import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { acceptHeader } from "../../src/headerGate.js";
import { makeBadHd, makeHd, makeHdRecord } from "./helpers.js";

describe("HeaderGate", () => {
  it("accepts an HD with three digits in columns 33-39", () => {
    const result = acceptHeader(makeHd("991"));
    assert.deepEqual(result, { ok: true, sequence: "991" });
  });

  it("extracts the three-digit run from a TTIS prefix", () => {
    const result = acceptHeader(makeHd("007"));
    assert.deepEqual(result, { ok: true, sequence: "007" });
  });

  it("accepts DFTTISD when the update indicator is valid", () => {
    const result = acceptHeader(makeBadHd());
    assert.deepEqual(result, { ok: true, sequence: "DFTTISD" });
  });

  it("refuses a non-HD first line", () => {
    const result = acceptHeader("BSNC169222610272610271111111");
    assert.equal(result.ok, false);
  });

  it("refuses a short line", () => {
    const result = acceptHeader("HD");
    assert.equal(result.ok, false);
  });

  it("accepts a non-digit unique file reference", () => {
    const result = acceptHeader(makeHdRecord({ currentRef: "TTISXXX" }));
    assert.deepEqual(result, { ok: true, sequence: "TTISXXX" });
  });

  it("does not take the sequence from a suffix on the header line", () => {
    const line = `${makeBadHd()}RJTTC991`;
    const result = acceptHeader(line);
    assert.deepEqual(result, { ok: true, sequence: "DFTTISD" });
  });

  it("accepts a full-extract file reference with no digits", () => {
    const line = "HDTPS.UCFCATE.PD1107191907112108DFTTISS       FA190711300912".padEnd(
      80,
      " ",
    );
    const result = acceptHeader(line);
    assert.deepEqual(result, { ok: true, sequence: "DFTTISS" });
  });

  it("reads the DTD sequence from an RJTTF filename, not the date suffix", () => {
    const line = "HDTPS.UCFCATE.PD1107191907112108DFTTISS       FA190711300912".padEnd(
      80,
      " ",
    );
    const result = acceptHeader(line, "RJTTF007MCA-20261101.txt");
    assert.deepEqual(result, { ok: true, sequence: "007" });
  });

  it("prefers the DTD filename sequence over header digits", () => {
    const result = acceptHeader(makeHd("991"), "RJTTF007MCA-20261027.mca");
    assert.deepEqual(result, { ok: true, sequence: "007" });
  });
});
