import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { serviceShortLabel } from "../src/serviceLabel.js";

describe("serviceShortLabel", () => {
  it("formats origin dest and origin departure as ORGDST-HHMM", () => {
    assert.equal(
      serviceShortLabel({
        originCrs: "EUS",
        destCrs: "CRE",
        stops: [
          { crs: "EUS", arr: null, dep: "10:53" },
          { crs: "CRE", arr: "13:58", dep: null },
        ],
      }),
      "EUSCRE-1053",
    );
  });

  it("falls back to stops and scheduledTime when OD fields are missing", () => {
    assert.equal(
      serviceShortLabel({
        scheduledTime: "1436",
        stops: [
          { crs: "MKC", arr: null, dep: null },
          { crs: "EUS", arr: "1500", dep: null },
        ],
      }),
      "MKCEUS-1436",
    );
  });
});
