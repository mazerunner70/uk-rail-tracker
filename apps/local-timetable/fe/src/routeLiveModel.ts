import { placeTrain, type TrainPlace } from "./networkMap.js";
import {
  journeyProgress,
  placeOnSpine,
  stationAggregates,
  stripLayout,
  trainsOnSpine,
  type StationAggregate,
  type StripLayout,
} from "./routeStrip.js";
import { reachedAndNextCrs, scheduledHhmm, type StopRow } from "./stopTime.js";
import type {
  NetworkStop,
  NetworkTrain,
  ServiceDetail,
  StatusLabel,
} from "./types.js";

export type RouteLiveTrain = {
  serviceGuid: string;
  originCrs: string | null;
  destCrs: string | null;
  problem: boolean;
  statusLabel: StatusLabel | undefined;
  delayMinutes: number | null;
  scheduledTime: string | null;
  stops: StopRow[];
  /** Forecast-preferring arr/dep for placeTrain / strip geometry. */
  placementStops: NetworkStop[];
  place: TrainPlace | null;
  reachedCrs: string | null;
  nextCrs: string | null;
  journeyProgress: number | null;
  windowProgress: number | null;
  trainLength: number | null;
};

export type RouteLiveModel = {
  nowHhmm: string;
  window: string[];
  windowKey: string;
  selectedServiceGuid: string | null;
  trainsOnStrip: RouteLiveTrain[];
  selected: RouteLiveTrain | null;
  selectedDetail: ServiceDetail | null;
  aggregates: StationAggregate[];
  layout: StripLayout;
};

export type ResolvedRouteWindow = {
  window: string[];
  windowKey: string;
  selectedServiceGuid: string | null;
};

export function networkStopsAsRows(stops: readonly NetworkStop[]): StopRow[] {
  return stops.map((stop, seq) => ({
    seq,
    crs: stop.crs ?? null,
    publicArr: stop.arr,
    publicDep: stop.dep,
    forecastArr: null,
    forecastDep: null,
  }));
}

export function detailStopsAsRows(detail: ServiceDetail): StopRow[] {
  return detail.stops.map((stop, seq) => ({
    seq,
    crs: stop.crs ?? null,
    publicArr: stop.publicArr,
    publicDep: stop.publicDep,
    forecastArr: stop.forecastArr,
    forecastDep: stop.forecastDep,
  }));
}

/** Prefer forecast times so placement matches Live/Late calling points. */
export function placementStopsFromRows(rows: readonly StopRow[]): NetworkStop[] {
  const out: NetworkStop[] = [];
  for (const row of rows) {
    if (!row.crs) continue;
    out.push({
      crs: row.crs,
      arr: row.forecastArr ?? row.publicArr,
      dep: row.forecastDep ?? row.publicDep,
    });
  }
  return out;
}

export function rowsForTrain(
  train: NetworkTrain,
  detail: ServiceDetail | null,
): StopRow[] {
  if (detail && detail.serviceGuid === train.serviceGuid && detail.stops.length > 0) {
    return detailStopsAsRows(detail);
  }
  return networkStopsAsRows(train.stops ?? []);
}

function trainLengthFrom(
  train: NetworkTrain,
  detail: ServiceDetail | null,
): number | null {
  if (detail?.serviceGuid === train.serviceGuid && detail.trainLength != null) {
    return detail.trainLength;
  }
  return train.stops?.map((stop) => stop.trainLength).find((n) => n != null) ?? null;
}

export function normalizeRouteTrain(
  train: NetworkTrain,
  nowHhmm: string,
  window: readonly string[],
  detail: ServiceDetail | null = null,
): RouteLiveTrain {
  const stops = rowsForTrain(train, detail);
  const placementStops = placementStopsFromRows(stops);
  const place = placeTrain(placementStops, nowHhmm);
  const { reachedCrs, nextCrs } =
    stops.length > 0
      ? reachedAndNextCrs(stops, nowHhmm)
      : { reachedCrs: train.reachedCrs ?? null, nextCrs: train.nextCrs ?? null };
  const useDetail =
    detail && detail.serviceGuid === train.serviceGuid ? detail : null;
  return {
    serviceGuid: train.serviceGuid,
    originCrs: useDetail?.originCrs ?? train.originCrs ?? null,
    destCrs: useDetail?.destCrs ?? train.destCrs ?? null,
    problem: useDetail?.problem ?? train.problem,
    statusLabel: useDetail?.statusLabel ?? train.statusLabel,
    delayMinutes: useDetail?.delayMinutes ?? train.delayMinutes ?? null,
    scheduledTime:
      (stops.length > 0 ? scheduledHhmm(stops, nowHhmm) : null)
      ?? useDetail?.scheduledTime
      ?? train.scheduledTime
      ?? null,
    stops,
    placementStops,
    place,
    reachedCrs: useDetail?.reachedCrs ?? reachedCrs,
    nextCrs: useDetail?.nextCrs ?? nextCrs,
    journeyProgress: journeyProgress({ stops: placementStops }, nowHhmm),
    windowProgress: place ? placeOnSpine(place, window)?.progress ?? null : null,
    trainLength: trainLengthFrom(train, useDetail),
  };
}

export function asNetworkTrain(live: RouteLiveTrain): NetworkTrain {
  return {
    serviceGuid: live.serviceGuid,
    originCrs: live.originCrs,
    destCrs: live.destCrs,
    reachedCrs: live.reachedCrs,
    nextCrs: live.nextCrs,
    scheduledTime: live.scheduledTime,
    problem: live.problem,
    statusLabel: live.statusLabel,
    delayMinutes: live.delayMinutes ?? undefined,
    stops: live.placementStops,
  };
}

/**
 * Apply forecast-aware placement stops onto list trains so spine / marker
 * filters and stripLayout share one place path with the entity card.
 */
export function withPlacementStops(
  trains: readonly NetworkTrain[],
  selectedServiceGuid: string | null,
  detail: ServiceDetail | null,
): NetworkTrain[] {
  return trains.map((train) => {
    const useDetail =
      detail
      && selectedServiceGuid
      && train.serviceGuid === selectedServiceGuid
      && detail.serviceGuid === selectedServiceGuid
        ? detail
        : null;
    const rows = rowsForTrain(train, useDetail);
    return { ...train, stops: placementStopsFromRows(rows) };
  });
}

/** Assemble the route snapshot from an already-resolved window and visible list. */
export function buildRouteLiveModel(
  nowHhmm: string,
  resolved: ResolvedRouteWindow,
  routeVisible: readonly NetworkTrain[],
  markerFilter: (
    trains: readonly NetworkTrain[],
    nowHhmm: string,
    alwaysIncludeGuid: string | null,
  ) => NetworkTrain[],
  selectedHeld: NetworkTrain | null,
  detail: ServiceDetail | null,
): RouteLiveModel | null {
  if (resolved.window.length === 0) return null;

  const selectedDetail =
    detail
    && resolved.selectedServiceGuid
    && detail.serviceGuid === resolved.selectedServiceGuid
      ? detail
      : null;

  const placed = withPlacementStops(
    routeVisible,
    resolved.selectedServiceGuid,
    selectedDetail,
  );
  const onWindow = trainsOnSpine(placed, resolved.window, nowHhmm);
  const visible = markerFilter(
    onWindow,
    nowHhmm,
    resolved.selectedServiceGuid,
  );

  const normalized = visible.map((train) =>
    normalizeRouteTrain(
      train,
      nowHhmm,
      resolved.window,
      selectedDetail?.serviceGuid === train.serviceGuid ? selectedDetail : null,
    ),
  );
  const networkViews = normalized.map(asNetworkTrain);
  const layout = stripLayout(
    resolved.window,
    networkViews,
    nowHhmm,
    resolved.selectedServiceGuid,
  );
  const onStripGuids = new Set(layout.markers.map((marker) => marker.serviceGuid));
  const trainsOnStrip = normalized.filter((train) =>
    onStripGuids.has(train.serviceGuid),
  );
  const aggregates = stationAggregates(
    resolved.window,
    trainsOnStrip.map(asNetworkTrain),
    nowHhmm,
  );

  let selected: RouteLiveTrain | null = null;
  if (resolved.selectedServiceGuid) {
    selected =
      trainsOnStrip.find((t) => t.serviceGuid === resolved.selectedServiceGuid)
      ?? null;
    if (!selected && selectedHeld) {
      selected = normalizeRouteTrain(
        selectedHeld,
        nowHhmm,
        resolved.window,
        selectedDetail,
      );
    }
  }

  return {
    nowHhmm,
    window: resolved.window,
    windowKey: resolved.windowKey,
    selectedServiceGuid: resolved.selectedServiceGuid,
    trainsOnStrip,
    selected,
    selectedDetail,
    aggregates,
    layout: {
      ...layout,
      selectedProgress: selected?.journeyProgress ?? layout.selectedProgress,
    },
  };
}
