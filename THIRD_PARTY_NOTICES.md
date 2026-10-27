# Third-Party Notices

## This plugin artifact

The published `jmeter-interval-controller-1.1.2.jar` contains **only** Interval Controller classes and Maven metadata. It does **not** bundle third-party library JARs.

At runtime, the plugin uses APIs provided by the Apache JMeter installation (`provided` Maven dependencies), including but not limited to:

| Component | Typical license | Notes |
|-----------|-----------------|-------|
| Apache JMeter (`ApacheJMeter_core`, `ApacheJMeter_components`, `jorphan`) | Apache License 2.0 | Required host application |
| SLF4J API | MIT | Provided by JMeter |
| Apache Commons Lang | Apache License 2.0 | Provided by JMeter |

Install and comply with the licenses of your Apache JMeter distribution separately. This plugin’s Apache License 2.0 grant does **not** re-license Apache JMeter.

## Build / test only dependencies

The following appear on the **test** or **build** classpath and are not required in `lib/ext` for end users:

| Component | License (as commonly published) | Scope |
|-----------|----------------------------------|-------|
| JUnit 4 | Eclipse Public License 1.0 | test |
| Hamcrest | BSD | test |
| AssertJ Swing | Apache License 2.0 | test |

## Notices

No additional third-party source files are vendored inside this repository’s production `src/main` tree.

If you redistribute a modified build that shades or bundles other libraries, you must add corresponding notices to this file.
