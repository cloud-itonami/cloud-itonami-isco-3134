# Security Policy

## Reporting Vulnerabilities

If you discover a security vulnerability, please email security@example.com instead of using the issue tracker.

Please include:
- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested fixes

We will acknowledge receipt of your report within 48 hours and provide updates as we work on a fix.

## Security Considerations

### Hard Invariants

This actor enforces hard safety invariants that cannot be overridden:

1. **Process-control prohibition** — any proposal mentioning furnace, reactor, valve, flow, pressure, temperature control, or emergency-shutdown is permanently blocked, even with human approval
2. **Emissions escalation** — emissions threshold exceedances MUST escalate to human (never silent log)
3. **Plant/batch verification** — all operations require registered plant/batch records
4. **Spec-basis requirement** — all operations require official regulation citations

These are *structural* safety gates, not policy flags that can be disabled.

### Audit Ledger

All operations are logged to an append-only audit ledger. No past actions can be modified or deleted.

### Human Review Gates

High-stakes operations (maintenance scheduling, emissions reporting) require explicit human approval before execution, by construction.
