import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  clampCamera,
  fitCamera,
  frameCamera,
  screenScale,
  stackOffset,
  zoomCamera,
} from "../src/mapCamera.js";

describe("map camera", () => {
  it("fits a wide map inside a taller view with vertical slack", () => {
    const camera = fitCamera(200, 50, 1);
    assert.equal(camera.width, 200);
    assert.ok(camera.height > 50);
    assert.ok(camera.y < 0);
  });

  it("leaves a margin around the whole network", () => {
    const camera = frameCamera(200, 100, 400, 300);
    assert.ok(camera.x < 0);
    assert.ok(camera.y < 0);
    assert.ok(camera.x + camera.width > 200);
    assert.ok(camera.y + camera.height > 100);
  });

  it("zooms toward the anchor without moving that point", () => {
    const start = { x: 0, y: 0, width: 200, height: 100 };
    const anchorX = 50;
    const anchorY = 25;
    const next = zoomCamera(start, 0.5, anchorX, anchorY, 20, 200);
    assert.equal(next.width, 100);
    const before = (anchorX - start.x) / start.width;
    const after = (anchorX - next.x) / next.width;
    assert.ok(Math.abs(before - after) < 1e-9);
  });

  it("keeps the view centred on the map", () => {
    const camera = clampCamera({ x: 500, y: -200, width: 40, height: 20 }, 100, 80);
    const cx = camera.x + camera.width / 2;
    const cy = camera.y + camera.height / 2;
    assert.ok(cx >= 0 && cx <= 100);
    assert.ok(cy >= 0 && cy <= 80);
  });

  it("sizes screen marks from the view and separates stacked trains", () => {
    assert.equal(screenScale(800, 400), 2);
    assert.equal(screenScale(800, 0), 1);
    const alone = stackOffset(0, 1, true, 0);
    const opposite = stackOffset(0, 1, true, Math.PI);
    assert.deepEqual(alone, { x: 16, y: 0 });
    assert.ok(Math.abs(alone.x + opposite.x) < 1e-9);
    assert.ok(Math.abs(alone.y + opposite.y) < 1e-9);
    const left = stackOffset(0, 2, false, 0);
    const right = stackOffset(1, 2, false, 0);
    assert.ok(Math.hypot(left.x - right.x, left.y - right.y) >= 16);
  });
});
