# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| 1.1.2   | Yes |
| < 1.1.2 | Please upgrade |

## Reporting a vulnerability

Please **do not** open a public GitHub Issue for security-sensitive reports.

Report vulnerabilities privately via GitHub Security Advisories for:

https://github.com/GeorgeKalyaev/jmeter-interval-controller

If Advisories are unavailable, contact the repository owner through their GitHub profile.

Include:

- Affected version and JAR SHA-256 if known
- JMeter and Java versions
- Steps to reproduce
- Impact assessment

You should receive an acknowledgement when the owner is available. Please allow reasonable time for assessment before public disclosure.

## Scope notes

- Interval Controller evaluates scheduling when a thread encounters it; it does not run a privileged background service.
- Token-related settings read JMeter variables you configure. Treat tokens as secrets in your test plans and CI logs.
- Do not commit real credentials, private keys, or production endpoints into examples or issues.
