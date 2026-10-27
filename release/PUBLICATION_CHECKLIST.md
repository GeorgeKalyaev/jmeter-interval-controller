# Manual Publication Checklist — Interval Controller v1.1.2

Repository: https://github.com/GeorgeKalyaev/jmeter-interval-controller

Do **not** perform remote steps until the owner explicitly approves.

## Pre-flight

- [x] Legal copyright holder: Georgii Kaliaev (2026)
- [x] Proposed license: Apache License 2.0
- [x] Git author email confirmed for commits
- [ ] Review the prepared public repository working tree before commit
- [ ] Confirm no private data or unnecessary tool references remain
- [ ] Verify canonical JAR SHA-256:
      `94F834F68B6825240C6179D15352BF693583B444B1C3F7D441CD190E5EDF591C`

## GitHub (Stage A)

1. [ ] Configure local git author for this repository
2. [ ] Ensure approved public project files are present in the working tree
3. [ ] Preserve the existing Initial commit history (do not rewrite published history)
4. [ ] Commit and push under the confirmed author identity
5. [ ] Create Git tag `v1.1.2`
6. [ ] Publish GitHub Release **Interval Controller v1.1.2 — Stable Release**
7. [ ] Attach `jmeter-interval-controller-1.1.2.jar` (canonical only)
8. [ ] Confirm public direct-download URL returns the JAR

## JMeter Plugins Manager (Stage B)

9. [ ] Confirm marketplace descriptor URLs resolve
10. [ ] Validate with upstream `python3 format_repo.py` / `--check`
11. [ ] Contribute to `undera/jmeter-plugins` → `site/dat/repo/various.json`
12. [ ] Submit PR for review (only with explicit approval)
13. [ ] Wait for acceptance
14. [ ] Verify the plugin appears in Plugins Manager
15. [ ] Test install on clean JMeter 5.6.3
16. [ ] Confirm no older conflicting plugin JARs remain
