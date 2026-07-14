# ADR 0001: Architecture Decision Record

## Status
Accepted

## Context

ISCO-08 3134 (Petroleum and Natural Gas Refining Plant Operators) requires a coordination actor that:

1. Helps refinery operators manage plant operations safely
2. Never delegates process-control authority to an LLM
3. Maintains hard safety gates that cannot be overridden
4. Records all decisions in an append-only audit ledger
5. Escalates high-stakes operations (maintenance, emissions) to human review

The actor mirrors the established cloud-itonami pattern (from ISIC-1910 coke-oven operations).

## Decision

We structure the actor as:

### Core Layers

1. **Advisor** (`refinery.advisor`) — LLM-driven suggestion layer that proposes operations to the Governor
2. **Governor** (`refinery.governor`) — Independent compliance layer that evaluates proposals against hard and soft safety gates
3. **Store** (`refinery.store`) — Stateful registry of plants, equipment, batches, and audit ledger
4. **Phase** (`refinery.phase`) — State machine that defines which operations are allowed at each stage

### Safety Invariants (HARD)

- **No process-control**: Any proposal mentioning furnace, reactor, valve, flow, pressure, temperature control, or emergency-shutdown is permanently blocked
- **No silent emissions exceedance**: Threshold exceedances must escalate to human (never silent log)
- **Plant/batch registration**: All operations require registered plant/batch records (no unverified operations)
- **Spec-basis requirement**: All operations require official regulation citations

### Soft Gates (can escalate to human)

- Low confidence proposals
- High-stakes operations (maintenance, emissions)

### Proposal Operations

1. `:proposal/log-process-reading` — routine parameter logging
2. `:actuation/schedule-maintenance` — maintenance scheduling proposal
3. `:actuation/log-emissions-report` — emissions report (may include exceedances)
4. `:proposal/coordinate-shipment` — product shipment coordination

### Phase Machine

```
IDLE -> PROPOSAL -> REVIEW -> EXECUTE -> LOGGED
```

Only humans can move to EXECUTE phase. The actor never auto-promotes.

## Consequences

### Positive
- Hard safety gates prevent process-control delegation
- Audit trail is immutable
- Human review is *structurally* required, not optional
- Pattern is consistent with other cloud-itonami actors

### Negative
- More complex state machine than a simple stateless validator
- Requires careful implementation of phase transitions
- Must be deployed and tested in production-like environments before actual use

## Implementation Notes

- All source code is portable `.cljc` (no JVM-only constructs)
- Tests verify that hard invariants hold under all scenarios
- Mock advisor is sufficient for testing; real implementation would call an LLM
