# Release Verification Summary — v1.1.2

Statuses use: **PASS**, **PASS FROM EXISTING EVIDENCE**, **NOT TESTED**, **FAIL**.

| Check | Status | Notes |
|-------|--------|-------|
| Canonical JAR SHA-256 matches approved value | PASS | `94F834F68B6825240C6179D15352BF693583B444B1C3F7D441CD190E5EDF591C` |
| Unit tests (IntervalControllerTest, JmxPropertyPreservationTest) | PASS FROM EXISTING EVIDENCE | Verified during release-gate / cross-platform work |
| Bytecode major version 52 | PASS FROM EXISTING EVIDENCE | Java 8 target |
| Windows Non-GUI scenarios | PASS FROM EXISTING EVIDENCE | Release gate |
| Windows GUI (AssertJ / manual evidence) | PASS FROM EXISTING EVIDENCE | GUI automation + screenshots |
| JDK 17 host | PASS FROM EXISTING EVIDENCE | Cross-platform verification |
| Linux Non-GUI (Docker) | PASS FROM EXISTING EVIDENCE | Cross-platform verification |
| Linux GUI | NOT TESTED | |
| Live GitHub Actions on public repo | NOT TESTED | Workflow prepared; not executed on published remote |
| Fresh rebuild SHA equals canonical | NOT TESTED / may differ | Canonical JAR must be distributed as-is |

Evidence folders used during preparation (private / not required in public clone):

- `verification-1.1.2/release-gate/`
- `verification-1.1.2/cross-platform/`
- `verification-1.1.2/gui-automation/`

## Install check (end user)

1. Clean JMeter 5.6.3
2. Single canonical JAR in `lib/ext`
3. Restart → Add → Logic Controller → Interval Controller
