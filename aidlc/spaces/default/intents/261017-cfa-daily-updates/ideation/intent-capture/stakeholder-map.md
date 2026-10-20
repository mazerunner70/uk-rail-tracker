# Stakeholder Map

## Key Stakeholders

| Stakeholder | Interest | Authority | Source |
|-------------|----------|-----------|--------|
| Author | Deliver and accept the CFA daily-update proof of concept | Decision-maker | [Q4] |
| Live-sched operator | Reliable sequence-checked incremental CFA loads from a watched folder | Operator / beneficiary | [Q1] |
| Downstream consumer | Trustworthy load report (total, new, overrides, duplicates) | Influencer / beneficiary — does not decide proof acceptance | [Q1] [Q4] |

## Decision-Makers vs Influencers

- **Decision-maker:** Author — decides whether the proof is good enough. [Q4]
- **Influencers / beneficiaries:** Live-sched operator and downstream consumer care about correct loads and the report; they do not hold acceptance authority for this proof. [Q1] [Q4]

## Communication Requirements

| Audience | Cadence / Channel | Source |
|----------|-------------------|--------|
| Author | One short written note when the proof is complete: what was proven plus a sample load report; then done (self-kept; no separate recipient) | [Q6] [Q9] |
| Ongoing reporting | None for this proof of concept | [Q6] |

## Assumptions & Open Questions

None.
