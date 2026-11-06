export type Camera = { x: number; y: number; width: number; height: number };

const FRAME_MARGIN_PX = 56;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function fitCamera(mapWidth: number, mapHeight: number, viewAspect: number): Camera {
  const aspect = viewAspect > 0 ? viewAspect : mapWidth / mapHeight;
  const mapAspect = mapWidth / mapHeight;
  if (mapAspect > aspect) {
    const height = mapWidth / aspect;
    return { x: 0, y: (mapHeight - height) / 2, width: mapWidth, height };
  }
  const width = mapHeight * aspect;
  return { x: (mapWidth - width) / 2, y: 0, width, height: mapHeight };
}

export function frameCamera(
  mapWidth: number,
  mapHeight: number,
  pixelWidth: number,
  pixelHeight: number,
): Camera {
  if (pixelWidth < 2 || pixelHeight < 2) {
    return { x: -40, y: -40, width: mapWidth + 80, height: mapHeight + 80 };
  }
  const innerWidth = Math.max(1, pixelWidth - FRAME_MARGIN_PX * 2);
  const innerHeight = Math.max(1, pixelHeight - FRAME_MARGIN_PX * 2);
  const inner = fitCamera(mapWidth, mapHeight, innerWidth / innerHeight);
  const userPerPx = inner.width / innerWidth;
  return {
    x: inner.x - userPerPx * FRAME_MARGIN_PX,
    y: inner.y - userPerPx * FRAME_MARGIN_PX,
    width: userPerPx * pixelWidth,
    height: userPerPx * pixelHeight,
  };
}

export function zoomCamera(
  camera: Camera,
  factor: number,
  anchorX: number,
  anchorY: number,
  minWidth: number,
  maxWidth: number,
): Camera {
  const width = clamp(camera.width * factor, minWidth, maxWidth);
  const height = width * (camera.height / camera.width);
  return {
    x: anchorX - (anchorX - camera.x) * (width / camera.width),
    y: anchorY - (anchorY - camera.y) * (height / camera.height),
    width,
    height,
  };
}

export function panCamera(camera: Camera, dxUser: number, dyUser: number): Camera {
  return { ...camera, x: camera.x - dxUser, y: camera.y - dyUser };
}

export function clampCamera(camera: Camera, mapWidth: number, mapHeight: number): Camera {
  const cx = clamp(camera.x + camera.width / 2, 0, mapWidth);
  const cy = clamp(camera.y + camera.height / 2, 0, mapHeight);
  return { ...camera, x: cx - camera.width / 2, y: cy - camera.height / 2 };
}

export function sameFrame(left: Camera, right: Camera): boolean {
  return (
    Math.abs(left.x - right.x) < 0.5 &&
    Math.abs(left.y - right.y) < 0.5 &&
    Math.abs(left.width - right.width) < 0.5 &&
    Math.abs(left.height - right.height) < 0.5
  );
}

export function screenScale(viewWidth: number, pixelWidth: number): number {
  if (pixelWidth < 2 || viewWidth <= 0) return 1;
  return viewWidth / pixelWidth;
}

const DEPARTURE_GAP = 16;

export function stackOffset(
  index: number,
  count: number,
  atStation: boolean,
  angle: number,
): { x: number; y: number } {
  if (atStation) {
    return { x: Math.cos(angle) * DEPARTURE_GAP, y: Math.sin(angle) * DEPARTURE_GAP };
  }
  if (count < 2) return { x: 0, y: 0 };
  const offset = (index - (count - 1) / 2) * 16;
  const perp = angle + Math.PI / 2;
  return { x: Math.cos(perp) * offset, y: Math.sin(perp) * offset };
}

export function formatViewBox(camera: Camera): string {
  return `${camera.x.toFixed(1)} ${camera.y.toFixed(1)} ${camera.width.toFixed(1)} ${camera.height.toFixed(1)}`;
}
