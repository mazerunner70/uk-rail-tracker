import { createApiClient } from "./apiClient.js";
import { renderApp, schedulePick } from "./dom.js";
import {
  createScreenController,
  formatLondonClock,
  isPairFilter,
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
      controller.setPage("network");
      refresh();
    }
    if (target.closest('[data-testid="clear-schedule-focus"]')) {
      controller.setScheduleFocus(null);
      refresh();
    }
    const map = target.closest(".network-map");
    if (map instanceof Element && !ev.shiftKey) {
      const pick = schedulePick(target);
      const current = controller.getView().scheduleFocus;
      const next = pick ? nextScheduleFocus(current, pick) : null;
      if (next !== current) {
        controller.setScheduleFocus(next);
        refresh();
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
