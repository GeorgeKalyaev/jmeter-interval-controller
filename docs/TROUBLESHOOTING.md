# Troubleshooting

## Interval Controller does not appear in the Add menu

1. Confirm the JAR is in `lib/ext/`.
2. Remove duplicate / older `jmeter-interval-controller-*.jar` files.
3. Restart JMeter completely.
4. Confirm you are using Apache JMeter 5.6.3 (other versions: not tested).

## Nested samplers never run after the first time

Expected when the interval has not elapsed. Check:

- Execution Interval and unit
- System clock / test duration
- Whether you are looking at siblings (should still run) vs nested samples (may SKIP)

## Nested samplers never run at all

- **Execute on first iteration** disabled → early encounters SKIP until the first wait elapses
- Controller or parent disabled
- Thread never reaches the controller (logic controllers above may exclude it)

## Retries do not happen

- Retry on Failure disabled
- Maximum Retries exhausted (`max > 0` and failures exceeded max)
- Thread Group On Sample Error stops the thread/test
- Retry Delay not yet elapsed on subsequent encounters (no sleep — must re-enter)

## Dynamic `${variable}` interval ignored

Enable **Dynamic Interval**. Otherwise the interval string is treated as a literal.

## Force Refresh seems sticky

Truthy values are cleared after a FORCE EXECUTE. If a sampler keeps writing `true` every iteration, FORCE will keep firing.

## Token TTL refreshes forever

- Ensure issued-at is bound (variable or successful prior refresh)
- Confirm Expiration Format = TTL seconds and the variable holds lifetime seconds, not a countdown
- Check Refresh Before Expiration window

## Multiple plugin JARs

Undefined behavior. Keep a single version in `lib/ext`.
