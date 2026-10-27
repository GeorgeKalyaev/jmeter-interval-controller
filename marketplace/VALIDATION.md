# Marketplace Descriptor Validation

## Upstream references

- https://jmeter-plugins.org/wiki/SubmitYourPlugin/
- https://jmeter-plugins.org/wiki/PluginRepositoryDescriptorFormat/
- https://github.com/undera/jmeter-plugins

## Target file

Append one object to `site/dat/repo/various.json` (do not create a new JSON file).

## Descriptor identity

| Field | Value |
|-------|-------|
| id | `interval-controller` |
| vendor | `GeorgeKalyaev` |
| helpUrl | https://github.com/GeorgeKalyaev/jmeter-interval-controller |
| screenshotUrl | planned raw URL on tag `v1.1.2` |
| downloadUrl | planned GitHub Release asset URL for `v1.1.2` |

## Local validation performed

| Step | Result |
|------|--------|
| Inspected `format_repo.py` | Supports `python3 format_repo.py` and `python3 format_repo.py --check` |
| Inserted snippet into a local checkout of `various.json` | Done previously (temporary; reverted) |
| `python format_repo.py --check` | **PASS** |
| Overlap scan | No existing Plugins Manager entry with the same Logic Controller role |

## Dependencies

No `libs` or `depends` entries — the JAR is self-contained for install; JMeter provides runtime APIs.

## Before opening the upstream PR

1. Publish GitHub Release `v1.1.2` with the canonical JAR.
2. Confirm the planned `downloadUrl` and `screenshotUrl` return HTTP 200.
3. Re-run `python3 format_repo.py --check`.
