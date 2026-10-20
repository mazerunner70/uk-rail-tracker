# Unit Dependencies

Topology only. This file does not pick a ship order or a critical path.

## Edge block

```yaml
units:
  - name: cfa-receipt-apply
    kind: spec
    depends_on: []
  - name: kafka-archive-overlay
    kind: spec
    depends_on: [cfa-receipt-apply]
  - name: cheap-lookups
    kind: spec
    depends_on: [cfa-receipt-apply, kafka-archive-overlay]
```

## Graph

```mermaid
flowchart LR
    cfa[cfa-receipt-apply]
    overlay[kafka-archive-overlay]
    lookups[cheap-lookups]
    overlay --> cfa
    lookups --> cfa
    lookups --> overlay
```

Text fallback: `kafka-archive-overlay` depends on `cfa-receipt-apply`. `cheap-lookups` depends on both. `cfa-receipt-apply` depends on nothing. No cycle.

Edges mean “A depends on B”: the dependent chapter needs grains or headings the other chapter owns.

- Overlay bind needs journey / service_day / LocationMap names from the CFA chapter (Q6 C).
- Cheap lookups need those plan grains and the overlay table / column projection the overlay chapter names (Q6 C).
- Shared file shell (environments, heading map, TOC extension, later package layering, Deferred) lives on `cfa-receipt-apply` (Q5 B), so both other chapters depend on that unit for the file and `{env}` pattern.

## Integration points

| From | To | Kind | Contract |
|------|----|------|----------|
| All three | one file | shared data | `docs/athena-timetable-lake.md`; heading ownership in `unit-of-work.md` |
| kafka-archive-overlay | cfa-receipt-apply | shared data | Journey, ServiceDay, LocationMap, `service_guid`, publish-set merge-preserve rules |
| cheap-lookups | cfa-receipt-apply | shared data | journey, calling_points, service_day Glue names, STP winner, partition keys for dates and TOCs |
| cheap-lookups | kafka-archive-overlay | shared data | overlay and service_aliases Glue names; forecast / deactivated / schedulePayload columns |
| cfa-receipt-apply | (later intents) | named, not an edge | later `server/` package as three independently deployable services |

No runtime API in this intent. Sync calls CfaIntake → PlanQuery and KafkaOverlay → PlanQuery stay Domain Design behaviour specified inside the chapters.

## Parallel sets

There is no pair of units with empty mutual `depends_on`. `cfa-receipt-apply` is the only source. `kafka-archive-overlay` and `cheap-lookups` are not independent of each other because lookups depend on overlay. Multiple valid topological orderings do not exist for this graph.

## Skeleton

Walking skeleton is off. No unit is a running intake-to-query slice.

## Sources

- `unit-of-work.md`
- `units-generation-questions.md` — Q5 B, Q6 C
- `components.md` — CfaIntake and KafkaOverlay call PlanQuery

## Assumptions & Open Questions

- None.
