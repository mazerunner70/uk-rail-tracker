# Octilinear network layout (PuLP + HiGHS)

After each MCA load the server builds `network-schematic.json` with an
octilinear (Nöllenburg-style) metro-map layout solved by **HiGHS** through
**PuLP** (`pip install pulp highspy`). No commercial solver license is required.

## Requirements

- Python 3 with packages in `requirements.txt` (installed into `/opt/metro-layout` in Docker)

## Environment

| Variable | Default | Meaning |
|----------|---------|---------|
| `METRO_LAYOUT_ENGINE` | `milp` | `milp` (PuLP+HiGHS) or `geo` (in-process schematic; used by unit tests). Legacy value `optimods` is treated as `milp`. |
| `METRO_LAYOUT_TIME_LIMIT_SEC` | `1200` | HiGHS time limit |
| `METRO_LAYOUT_PYTHON` | `python3` | Interpreter that has PuLP + highspy |
| `METRO_LAYOUT_SCRIPT` | `layout/metro_layout.py` | Override script path |

## Local (non-Docker)

```bash
python3 -m venv .venv-layout
.venv-layout/bin/pip install -r layout/requirements.txt
export METRO_LAYOUT_PYTHON=$PWD/.venv-layout/bin/python
export METRO_LAYOUT_ENGINE=milp
```

## Compose

Defaults to `METRO_LAYOUT_ENGINE=milp`. For fast local tests without solving:

```bash
METRO_LAYOUT_ENGINE=geo docker compose up
```

Solver progress goes to server stderr (`pnpm logs:server` / `docker compose logs server`).

## Manual solver check

```bash
python3 layout/metro_layout.py --time-limit-sec 60 < layout-input.json > oct.json
```
