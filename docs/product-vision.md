# Product Vision

## What we're building

UK Rail Tracker is a native Android app that gives rail passengers **contextual, live information** — the stations that matter right now, whether trains are running on time, and whether past delays qualify for compensation.

The app should feel like opening a dashboard, not filling in a form. Location, time of day, and saved routines do the heavy lifting.

## Audience

- **Daily commuters** who want a glanceable view of their usual stations and any disruption before leaving home
- **Occasional travellers** who need nearby stations, departure boards, and journey status without navigating National Rail websites
- **Delay Repay claimants** who want a record of disrupted journeys and a shortcut to operator claim portals

## Core problems we solve

1. **"Which station am I near?"** — GPS-sorted nearby stations with live departures
2. **"What's my usual situation right now?"** — contextual home screen for typical stations at this time of day
3. **"Is my journey OK?"** — live tracking between two stations with delay and disruption visibility
4. **"Can I claim money back?"** — automatic logging of qualifying delays with compensation estimates

## Design principles

### Context first

Surface the most relevant stations and journeys without requiring the user to search every time. Favourites, commute windows, and time-of-day weighting beat blank search screens.

### Live when it matters, offline when it doesn't

Station metadata (name, CRS, accessibility, maps) is bundled and always available. Live departures, disruptions, and journey status require network access but should show cached last-known data when offline.

### Neon data-viz aesthetic

Dark canvas (`#0B0F1A`), electric cyan primary (`#00F0FF`), magenta accents (`#FF2BD6`). Status colours should be immediately readable: on-time (green/cyan), delayed (amber), cancelled (magenta/red).

### Privacy by default

Location is used on-device to sort nearby stations and infer routines. Journey history stays local unless the user explicitly exports it. No account required for v1.

### Operator-aware compensation

Delay Repay rules differ by train operating company. The app applies the correct thresholds and refund percentages per operator — never a single generic rule.

## Non-goals (v1)

- Ticket purchasing or split-ticketing
- Multi-platform (iOS, web) — Android first
- Social features or leaderboards
- Real-time train GPS map (limited free data; backlog for later)

## Feature priority

| Priority | Feature | Milestone |
|----------|---------|-----------|
| P0 | Nearest stations + station detail + departures | M1 |
| P0 | Contextual "my stations now" + disruption | M2 |
| P1 | Journey tracking A→B with live status | M3 |
| P1 | Disruption history + compensation assist | M4 |
| P2 | Notifications, widgets, polish | M5 |
| P2 | Play Store release | M6 |

See [milestone-roadmap.md](milestone-roadmap.md) for full delivery schedule.

## Future backlog

Ideas beyond v1.0, roughly prioritised:

| Idea | Value | Notes |
|------|-------|-------|
| **Home-screen widget** | Glanceable next departures without opening the app | High impact; build after M2 |
| **Line / route status dashboard** | "Is my line disrupted?" overview across operators | Reuses disruption API from M2 |
| **Platform predictions** | Show likely platform before it's announced | Needs historical data or heuristics |
| **Live train map** | "Where is my train?" on a map | Limited free APIs; consider OpenTrainMap or Darwin TRUST |
| **Season ticket ROI tracker** | Compare cumulative Delay Repay against season ticket cost | Extension of M4 |
| **Accessibility-first mode** | Step-free routes, lift outages, accessible toilets | Rich data already in `stations.xml` |
| **Share journey status** | Send "running 12 min late" link to colleagues | Lightweight; pairs with M3 |
| **Wear OS complication** | Next departure on watch face | Post-release |
| **Carbon / miles travelled** | Gamify rail usage over time | Nice-to-have stats from journey log |
| **Split-ticket / fare hints** | Cheapest way to buy tickets | Out of scope — regulatory complexity |

## Success metrics (v1)

- App opens to relevant station(s) within 2 seconds on a typical commute morning
- Departure board data is no more than 60 seconds stale when foregrounded
- Disruption inbox correctly flags journeys meeting operator Delay Repay thresholds
- Zero crashes on core flows: nearby, station detail, home, journey view
