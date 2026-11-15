import { createApiClient } from "./apiClient.js";
import { renderApp, schedulePick } from "./dom.js";
import {
  createScreenController,
  findHeldTrain,
  formatLondonClock,
  isEventTag,
  isPairFilter,
  nextNetworkEventFilter,
  nextScheduleFocus,
  parseIntervalInput,
} from "./controller.js";

const meta = document.querySelector('meta[name="api-base"]');
const apiBase = meta?.getAttribute("content") ?? "";

const api = createApiClient(apiBase);
const controller = createScreenController(api);
const appRoot = document.getElementById("app");
if (!appRoot) {
  throw new Error("#app missing");
}
const root: HTMLElement = appRoot;

let timer: ReturnType<typeof setInterval> | null = null;
let controlsBound = false;

function scheduleTick(): void {
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    void controller.tick().then(refresh);
  }, controller.getView().intervalSeconds * 1000);
}

function refresh(): void {
  const active = document.activeElement;
  const testId =
    active instanceof HTMLElement && root.contains(active)
      ? active.getAttribute("data-testid")
      : null;
  const selection =
    active instanceof HTMLInputElement
      ? { start: active.selectionStart, end: active.selectionEnd }
      : null;
  renderApp(root, controller.getView());
  if (!testId) return;
  const next = root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  if (!next) return;
  next.focus();
  if (
    next instanceof HTMLInputElement &&
    selection &&
    selection.start !== null &&
    selection.end !== null
  ) {
    next.setSelectionRange(selection.start, selection.end);
  }
}

function readStations(): void {
  const earlier = root.querySelector<HTMLInputElement>('[data-testid="earlier-crs"]');
  const later = root.querySelector<HTMLInputElement>('[data-testid="later-crs"]');
  controller.setPairCrs(earlier?.value ?? "", later?.value ?? "");
}

function openTrainByGuid(serviceGuid: string): boolean {
  const train = findHeldTrain(controller.getView(), serviceGuid);
  if (!train) return false;
  return controller.openRouteFromTrain(train);
}

function firstServiceGuid(raw: string | null): string | null {
  if (!raw) return null;
  const ids = raw.split(",").filter((id) => id.length > 0);
  return ids[0] ?? null;
}

function bindControlsOnce(): void {
  if (controlsBound) return;
  controlsBound = true;

  root.addEventListener("click", (ev) => {
    const target = ev.target as HTMLElement;
    if (target.closest('[data-testid="load-button"]')) {
      void controller.startLoad().then(() => {
        refresh();
        scheduleTick();
      });
    }
    if (target.closest('[data-testid="swap-stations"]')) {
      readStations();
      controller.swapStations();
      refresh();
    }
    if (target.closest('[data-testid="open-station-pair"]')) {
      controller.setPage("pair");
      refresh();
    }
    if (target.closest('[data-testid="open-network"]')) {
      controller.clearRoute();
      controller.setPage("network");
      refresh();
    }
    if (target.closest('[data-testid="clear-schedule-focus"]')) {
      controller.setScheduleFocus(null);
      controller.setNetworkEventFilter(null);
      refresh();
    }
    const eventFilterRow = target.closest("[data-event-filter]");
    if (eventFilterRow instanceof HTMLElement) {
      const value = eventFilterRow.getAttribute("data-event-filter") ?? "";
      if (isEventTag(value)) {
        const current = controller.getView().networkEventFilter;
        controller.setNetworkEventFilter(nextNetworkEventFilter(current, value));
        refresh();
        return;
      }
    }
    const scheduleRow = target.closest(".schedule-row");
    if (scheduleRow instanceof HTMLElement) {
      const guid = scheduleRow.getAttribute("data-service");
      if (guid && openTrainByGuid(guid)) {
        refresh();
        return;
      }
    }
    const routeTrain = target.closest(".route-train");
    if (routeTrain instanceof Element) {
      const guid = firstServiceGuid(routeTrain.getAttribute("data-service"));
      if (guid) {
        controller.selectRouteTrain(guid);
        refresh();
        return;
      }
    }
    const map = target.closest(".network-map");
    if (map instanceof Element && !ev.shiftKey) {
      const train = target.closest(".train-marker");
      if (train) {
        const guid = firstServiceGuid(train.getAttribute("data-service"));
        if (guid && openTrainByGuid(guid)) {
          refresh();
          return;
        }
      }
      const pick = schedulePick(target);
      if (pick?.kind === "station") {
        const current = controller.getView().scheduleFocus;
        const next = nextScheduleFocus(current, pick);
        if (next !== current) {
          controller.setScheduleFocus(next);
          refresh();
        }
      }
    }
    const filterButton = target.closest("[data-pair-filter]");
    if (filterButton instanceof HTMLElement) {
      const value = filterButton.getAttribute("data-pair-filter") ?? "";
      if (isPairFilter(value)) {
        controller.setPairFilter(value);
        refresh();
      }
    }
  });

  root.addEventListener("pointerover", (ev) => {
    if (controller.getView().page !== "network") return;
    const target = ev.target;
    if (!(target instanceof Element)) return;
    const row = target.closest(".schedule-row");
    if (row) {
      const guid = row.getAttribute("data-service");
      if (guid && controller.setRoutePreview({ serviceGuid: guid })) refresh();
      return;
    }
    const train = target.closest(".train-marker");
    if (train) {
      const guid = firstServiceGuid(train.getAttribute("data-service"));
      if (guid && controller.setRoutePreview({ serviceGuid: guid })) refresh();
    }
  });

  root.addEventListener("pointerout", (ev) => {
    if (controller.getView().page !== "network") return;
    if (!controller.getView().routePreview) return;
    const related = ev.relatedTarget;
    if (related instanceof Element) {
      if (related.closest(".schedule-row")) return;
      if (related.closest(".train-marker")) return;
      if (related.closest('[data-testid="route-preview-card"]')) return;
    }
    const leaving = ev.target;
    if (!(leaving instanceof Element)) return;
    const fromPreviewSurface =
      leaving.closest(".schedule-row")
      || leaving.closest(".train-marker")
      || leaving.closest('[data-testid="route-preview-card"]');
    if (!fromPreviewSurface) return;
    if (controller.setRoutePreview(null)) refresh();
  });

  root.addEventListener("change", (ev) => {
    const target = ev.target as HTMLElement;
    if (!target.matches('[data-testid="interval-input"]')) return;
    const intervalInput = target as HTMLInputElement;
    const parsed = parseIntervalInput(
      intervalInput.value,
      controller.getView().intervalSeconds,
    );
    if (parsed === null) {
      intervalInput.value = String(controller.getView().intervalSeconds);
      return;
    }
    controller.setIntervalSeconds(parsed);
    scheduleTick();
  });

  root.addEventListener("input", (ev) => {
    const target = ev.target as HTMLElement;
    if (
      target.matches('[data-testid="earlier-crs"]') ||
      target.matches('[data-testid="later-crs"]')
    ) {
      readStations();
    }
    if (target.matches('[data-testid="search-input"]')) {
      controller.setSearchText((target as HTMLInputElement).value);
      refresh();
    }
  });
}

bindControlsOnce();
refresh();
scheduleTick();
setInterval(() => {
  const clock = root.querySelector('[data-testid="clock"]');
  if (clock) clock.textContent = formatLondonClock();
}, 1000);
void controller.tick().then(refresh);
