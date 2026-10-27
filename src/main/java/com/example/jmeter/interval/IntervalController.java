package com.example.jmeter.interval;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.jmeter.control.GenericController;
import org.apache.jmeter.control.NextIsNullException;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.LongProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interval Controller 1.1.2 — execute nested elements at most once per configured
 * interval <strong>per thread</strong>, only when the thread enters this controller
 * (variant A). Never sleeps; never schedules background work.
 *
 * <p>Backward compatible with JMX from 1.0.0 / 1.1.x (same property keys for basic settings).</p>
 */
public class IntervalController extends GenericController
        implements Serializable, LoopIterationListener, SampleListener {

    private static final long serialVersionUID = 2L;
    private static final Logger log = LoggerFactory.getLogger(IntervalController.class);

    // --- 1.0.0 properties (keep keys) ---
    public static final String INTERVAL_VALUE = "IntervalController.intervalValue";
    public static final String TIME_UNIT = "IntervalController.timeUnit";
    public static final String EXECUTE_ON_FIRST = "IntervalController.executeOnFirstIteration";
    public static final String INTERVAL_MODE = "IntervalController.intervalMode";
    public static final String INSTANCE_ID = "IntervalController.instanceId";

    // --- 1.1.0 properties ---
    public static final String RETRY_ON_FAILURE = "IntervalController.retryOnFailure";
    public static final String RETRY_DELAY = "IntervalController.retryDelay";
    public static final String RETRY_DELAY_UNIT = "IntervalController.retryDelayUnit";
    public static final String MAX_RETRIES = "IntervalController.maxRetries";
    public static final String JITTER_VALUE = "IntervalController.jitterValue";
    public static final String JITTER_UNIT = "IntervalController.jitterUnit";
    public static final String JITTER_MODE = "IntervalController.jitterMode";
    public static final String DYNAMIC_INTERVAL = "IntervalController.dynamicInterval";
    public static final String TOKEN_EXPIRATION_VAR = "IntervalController.tokenExpirationVar";
    /**
     * Optional wall-clock issuance time (Unix milliseconds) for {@link ExpirationFormat#TTL_SECONDS}.
     * Empty → default companion name {@code <tokenVar>__issued_at_ms}.
     */
    public static final String TOKEN_ISSUED_AT_VAR = "IntervalController.tokenIssuedAtVar";
    public static final String EXPIRATION_FORMAT = "IntervalController.expirationFormat";
    public static final String REFRESH_BEFORE = "IntervalController.refreshBefore";
    public static final String REFRESH_BEFORE_UNIT = "IntervalController.refreshBeforeUnit";
    public static final String FORCE_REFRESH_VAR = "IntervalController.forceRefreshVar";

    public enum VisitState {
        UNDECIDED,
        EXECUTING,
        SKIPPED,
        COMPLETED
    }

    private transient NanoClock clock = SystemNanoClock.INSTANCE;
    private transient WallClock wallClock = SystemWallClock.INSTANCE;

    private transient VisitState visitState = VisitState.UNDECIDED;
    private transient long lastAnchorNs;
    private transient boolean hasAnchoredExecution;
    private transient long firstSeenNs;
    private transient long currentExecutionStartNs;
    private transient boolean visitHadFailure;
    private transient DecisionReason lastReason;
    private transient long lastEffectiveIntervalNs;
    private transient long lastJitterNs;
    private transient long nextAllowedNs;

    /** Retry state (per thread clone). */
    private transient boolean retryPending;
    private transient int consecutiveFailures;
    private transient long retryReadyAtNs;
    private transient boolean maxRetriesExhausted;

    /**
     * TTL_SECONDS binding (per thread clone). Wall-clock only — never mixed with nanoTime.
     * Bound on successful visit completion when a TTL value is present; see README contract.
     */
    private transient long tokenIssuedAtMs;
    private transient long boundTtlSeconds = -1L;

    public IntervalController() {
        setName("Interval Controller");
        setIntervalExpression("10");
        setTimeUnit(TimeUnitOption.MINUTES);
        setExecuteOnFirstIteration(true);
        setIntervalStartMode(IntervalStartMode.EXECUTION_COMPLETION);
        setRetryOnFailure(false);
        setRetryDelayExpression("5");
        setRetryDelayUnit(TimeUnitOption.SECONDS);
        setMaxRetries(0L);
        setJitterExpression("0");
        setJitterUnit(TimeUnitOption.SECONDS);
        setJitterMode(JitterMode.POSITIVE);
        setDynamicIntervalEnabled(false);
        setTokenExpirationVar("");
        setTokenIssuedAtVar("");
        setExpirationFormat(ExpirationFormat.UNIX_MILLISECONDS);
        setRefreshBeforeExpression("30");
        setRefreshBeforeUnit(TimeUnitOption.SECONDS);
        setForceRefreshVar("interval_controller_force_refresh");
        ensureInstanceId();
    }

    // ========== configuration ==========

    /** Raw interval expression: number or {@code ${var}} (when dynamic enabled). */
    public void setIntervalExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            expression = "10";
        }
        setProperty(new StringProperty(INTERVAL_VALUE, expression.trim()));
    }

    public String getIntervalExpression() {
        return getPropertyAsString(INTERVAL_VALUE, "10");
    }

    /** @deprecated use {@link #setIntervalExpression(String)}; kept for tests/compat */
    public void setIntervalValue(long value) {
        setIntervalExpression(Long.toString(Math.max(0L, value)));
    }

    public long getIntervalValue() {
        try {
            return Long.parseLong(getIntervalExpression().trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    public void setTimeUnit(TimeUnitOption unit) {
        setProperty(new StringProperty(TIME_UNIT, (unit == null ? TimeUnitOption.MINUTES : unit).name()));
    }

    public TimeUnitOption getTimeUnit() {
        return TimeUnitOption.fromLabel(getPropertyAsString(TIME_UNIT));
    }

    public void setExecuteOnFirstIteration(boolean enabled) {
        setProperty(new BooleanProperty(EXECUTE_ON_FIRST, enabled));
    }

    public boolean isExecuteOnFirstIteration() {
        return getPropertyAsBoolean(EXECUTE_ON_FIRST, true);
    }

    public void setIntervalStartMode(IntervalStartMode mode) {
        setProperty(new StringProperty(INTERVAL_MODE,
                (mode == null ? IntervalStartMode.EXECUTION_COMPLETION : mode).name()));
    }

    public IntervalStartMode getIntervalStartMode() {
        return IntervalStartMode.fromLabel(getPropertyAsString(INTERVAL_MODE));
    }

    public void setRetryOnFailure(boolean enabled) {
        setProperty(new BooleanProperty(RETRY_ON_FAILURE, enabled));
    }

    public boolean isRetryOnFailure() {
        return getPropertyAsBoolean(RETRY_ON_FAILURE, false);
    }

    public void setRetryDelayExpression(String expression) {
        setProperty(new StringProperty(RETRY_DELAY, expression == null ? "5" : expression.trim()));
    }

    public String getRetryDelayExpression() {
        return getPropertyAsString(RETRY_DELAY, "5");
    }

    public void setRetryDelayUnit(TimeUnitOption unit) {
        setProperty(new StringProperty(RETRY_DELAY_UNIT,
                (unit == null ? TimeUnitOption.SECONDS : unit).name()));
    }

    public TimeUnitOption getRetryDelayUnit() {
        return TimeUnitOption.fromLabel(getPropertyAsString(RETRY_DELAY_UNIT, "SECONDS"));
    }

    public void setMaxRetries(long max) {
        setProperty(new LongProperty(MAX_RETRIES, Math.max(0L, max)));
    }

    public long getMaxRetries() {
        return getPropertyAsLong(MAX_RETRIES);
    }

    public void setJitterExpression(String expression) {
        setProperty(new StringProperty(JITTER_VALUE, expression == null ? "0" : expression.trim()));
    }

    public String getJitterExpression() {
        return getPropertyAsString(JITTER_VALUE, "0");
    }

    public void setJitterUnit(TimeUnitOption unit) {
        setProperty(new StringProperty(JITTER_UNIT,
                (unit == null ? TimeUnitOption.SECONDS : unit).name()));
    }

    public TimeUnitOption getJitterUnit() {
        return TimeUnitOption.fromLabel(getPropertyAsString(JITTER_UNIT, "SECONDS"));
    }

    public void setJitterMode(JitterMode mode) {
        setProperty(new StringProperty(JITTER_MODE, (mode == null ? JitterMode.POSITIVE : mode).name()));
    }

    public JitterMode getJitterMode() {
        return JitterMode.fromLabel(getPropertyAsString(JITTER_MODE));
    }

    public void setDynamicIntervalEnabled(boolean enabled) {
        setProperty(new BooleanProperty(DYNAMIC_INTERVAL, enabled));
    }

    public boolean isDynamicIntervalEnabled() {
        return getPropertyAsBoolean(DYNAMIC_INTERVAL, false);
    }

    public void setTokenExpirationVar(String name) {
        setProperty(new StringProperty(TOKEN_EXPIRATION_VAR, name == null ? "" : name.trim()));
    }

    public String getTokenExpirationVar() {
        return getPropertyAsString(TOKEN_EXPIRATION_VAR, "");
    }

    public void setTokenIssuedAtVar(String name) {
        setProperty(new StringProperty(TOKEN_ISSUED_AT_VAR, name == null ? "" : name.trim()));
    }

    /**
     * Configured issued-at variable name, or empty if unset (caller should use
     * {@link #resolveTokenIssuedAtVarName()}).
     */
    public String getTokenIssuedAtVar() {
        return getPropertyAsString(TOKEN_ISSUED_AT_VAR, "");
    }

    /**
     * Effective issued-at variable for TTL_SECONDS: explicit config, else {@code <tokenVar>__issued_at_ms}.
     */
    public String resolveTokenIssuedAtVarName() {
        String configured = getTokenIssuedAtVar();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        String tokenVar = getTokenExpirationVar();
        if (tokenVar == null || tokenVar.isEmpty()) {
            return "";
        }
        return tokenVar + "__issued_at_ms";
    }

    public void setExpirationFormat(ExpirationFormat format) {
        setProperty(new StringProperty(EXPIRATION_FORMAT,
                (format == null ? ExpirationFormat.UNIX_MILLISECONDS : format).name()));
    }

    public ExpirationFormat getExpirationFormat() {
        return ExpirationFormat.fromLabel(getPropertyAsString(EXPIRATION_FORMAT));
    }

    public void setRefreshBeforeExpression(String expression) {
        setProperty(new StringProperty(REFRESH_BEFORE, expression == null ? "30" : expression.trim()));
    }

    public String getRefreshBeforeExpression() {
        return getPropertyAsString(REFRESH_BEFORE, "30");
    }

    public void setRefreshBeforeUnit(TimeUnitOption unit) {
        setProperty(new StringProperty(REFRESH_BEFORE_UNIT,
                (unit == null ? TimeUnitOption.SECONDS : unit).name()));
    }

    public TimeUnitOption getRefreshBeforeUnit() {
        return TimeUnitOption.fromLabel(getPropertyAsString(REFRESH_BEFORE_UNIT, "SECONDS"));
    }

    public void setForceRefreshVar(String name) {
        setProperty(new StringProperty(FORCE_REFRESH_VAR,
                name == null || name.trim().isEmpty() ? "interval_controller_force_refresh" : name.trim()));
    }

    public String getForceRefreshVar() {
        String v = getPropertyAsString(FORCE_REFRESH_VAR, "interval_controller_force_refresh");
        return v == null || v.isEmpty() ? "interval_controller_force_refresh" : v;
    }

    public void setClock(NanoClock clock) {
        this.clock = clock == null ? SystemNanoClock.INSTANCE : clock;
    }

    public NanoClock getClock() {
        if (clock == null) {
            clock = SystemNanoClock.INSTANCE;
        }
        return clock;
    }

    public void setWallClock(WallClock wallClock) {
        this.wallClock = wallClock == null ? SystemWallClock.INSTANCE : wallClock;
    }

    public WallClock getWallClock() {
        if (wallClock == null) {
            wallClock = SystemWallClock.INSTANCE;
        }
        return wallClock;
    }

    public String ensureInstanceId() {
        String id = getPropertyAsString(INSTANCE_ID);
        if (id == null || id.isEmpty()) {
            id = Long.toHexString(System.identityHashCode(this)) + "-" + System.nanoTime();
            setProperty(new StringProperty(INSTANCE_ID, id));
        }
        return id;
    }

    String variablesKey(String suffix) {
        return "__IC." + ensureInstanceId() + "." + suffix;
    }

    // ========== lifecycle ==========

    @Override
    public void initialize() {
        super.initialize();
        visitState = VisitState.UNDECIDED;
        setDone(false);
        visitHadFailure = false;
    }

    @Override
    protected void reInitialize() {
        super.reInitialize();
        setDone(false);
        visitState = VisitState.UNDECIDED;
        visitHadFailure = false;
        // Do NOT call initializeSubControllers() here.
        // iterationStart fires on every parent Loop iteration (including visits we will SKIP).
        // Re-initializing children would re-arm OnceOnlyController and break its once-per-Thread contract
        // (JMeter OnceOnly only reInitialize()'s itself on the first loop iteration).
        // Nested Loop/Transaction controllers are reset only when this visit actually EXECUTES
        // — see prepareNestedControllersForExecution().
    }

    @Override
    public void iterationStart(LoopIterationEvent iterEvent) {
        reInitialize();
    }

    /**
     * Reset nested controllers for a new EXECUTING visit, without re-arming
     * {@link org.apache.jmeter.control.OnceOnlyController} (once per Thread).
     * <p>
     * {@link org.apache.jmeter.control.LoopController#initialize()} clears {@code done}
     * but does <em>not</em> reset its internal {@code loopCount}. After a finite loop
     * finishes, {@code loopCount >= loops} remains true, so the next {@code next()} would
     * immediately see end-of-loop again. We therefore also fire
     * {@link org.apache.jmeter.control.LoopController#iterationStart} which calls
     * {@code resetLoopCount()}.
     */
    void prepareNestedControllersForExecution() {
        for (Object element : getSubControllers()) {
            if (!(element instanceof org.apache.jmeter.control.GenericController)) {
                continue;
            }
            if (element instanceof org.apache.jmeter.control.OnceOnlyController) {
                continue;
            }
            org.apache.jmeter.control.Controller child =
                    (org.apache.jmeter.control.Controller) element;
            child.initialize();
            if (element instanceof org.apache.jmeter.control.LoopController) {
                org.apache.jmeter.control.LoopController loop =
                        (org.apache.jmeter.control.LoopController) element;
                loop.iterationStart(new LoopIterationEvent(loop, 0));
            }
        }
    }

    /** Package-visible for unit tests — child list size after a visit. */
    int childCountForTest() {
        return getSubControllers().size();
    }

    @Override
    public Sampler next() {
        ensureTransientState();
        if (!isRunningVersion()) {
            return super.next();
        }

        if (visitState == VisitState.UNDECIDED) {
            decideVisit();
        }

        if (visitState == VisitState.SKIPPED || visitState == VisitState.COMPLETED) {
            return null;
        }

        Sampler sampler = super.next();
        if (sampler == null && visitState == VisitState.EXECUTING) {
            onExecutionCompleted();
        }
        return sampler;
    }

    @Override
    protected Sampler nextIsNull() throws NextIsNullException {
        // Do not call GenericController.reInitialize() here: that would restart the child walk
        // inside the same logical visit. Completion is handled in next() via onExecutionCompleted().
        return null;
    }

    /**
     * Keep nested controllers in the execution list across visits.
     * <p>
     * Stock {@link GenericController#currentReturnedNull} removes a child when
     * {@code child.isDone()} — correct for OnceOnly under a plain Loop (consume once),
     * but fatal for Interval Controller: a nested finite {@link org.apache.jmeter.control.LoopController}
     * finishes with {@code done=true} and would be permanently dropped, so the next EXECUTE
     * visit would find no children (see stock JMeter Loop-in-Loop: only the first parent
     * iteration runs the inner loop). We always {@code incrementCurrent()} instead; the next
     * EXECUTE re-arms resettable children via {@link #prepareNestedControllersForExecution()}.
     */
    @Override
    protected void currentReturnedNull(org.apache.jmeter.control.Controller c) {
        incrementCurrent();
    }

    @Override
    public Object clone() {
        IntervalController clone = (IntervalController) super.clone();
        clone.clock = this.clock == null ? SystemNanoClock.INSTANCE : this.clock;
        clone.wallClock = this.wallClock == null ? SystemWallClock.INSTANCE : this.wallClock;
        clone.visitState = VisitState.UNDECIDED;
        clone.lastAnchorNs = 0L;
        clone.hasAnchoredExecution = false;
        clone.firstSeenNs = 0L;
        clone.currentExecutionStartNs = 0L;
        clone.visitHadFailure = false;
        clone.lastReason = null;
        clone.lastEffectiveIntervalNs = 0L;
        clone.lastJitterNs = 0L;
        clone.nextAllowedNs = 0L;
        clone.retryPending = false;
        clone.consecutiveFailures = 0;
        clone.retryReadyAtNs = 0L;
        clone.maxRetriesExhausted = false;
        clone.tokenIssuedAtMs = 0L;
        clone.boundTtlSeconds = -1L;
        clone.setProperty(new StringProperty(INSTANCE_ID,
                Long.toHexString(System.identityHashCode(clone)) + "-" + System.nanoTime()));
        return clone;
    }

    // ========== SampleListener (failure detection) ==========

    @Override
    public void sampleOccurred(SampleEvent e) {
        if (visitState != VisitState.EXECUTING || e == null || e.getResult() == null) {
            return;
        }
        if (!isSuccessfulTree(e.getResult())) {
            visitHadFailure = true;
        }
    }

    @Override
    public void sampleStarted(SampleEvent e) {
        // no-op
    }

    @Override
    public void sampleStopped(SampleEvent e) {
        // no-op
    }

    static boolean isSuccessfulTree(SampleResult result) {
        if (result == null) {
            return true;
        }
        if (!result.isSuccessful()) {
            return false;
        }
        SampleResult[] subs = result.getSubResults();
        if (subs != null) {
            for (SampleResult sub : subs) {
                if (!isSuccessfulTree(sub)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ========== decision ==========

    void decideVisit() {
        long now = getClock().nanoTime();
        Decision decision = evaluate(now);
        lastReason = decision.reason;
        lastEffectiveIntervalNs = decision.effectiveIntervalNs;
        nextAllowedNs = decision.nextAllowedNs;

        if (decision.execute) {
            visitState = VisitState.EXECUTING;
            setDone(false);
            visitHadFailure = false;
            currentExecutionStartNs = now;
            prepareNestedControllersForExecution();
            if (decision.reason == DecisionReason.FORCE) {
                clearForceRefreshVariable();
            }
            if (decision.reason == DecisionReason.RETRY) {
                // keep retryPending until we see success/failure outcome
            }
            if (getIntervalStartMode() == IntervalStartMode.EXECUTION_START
                    && decision.reason != DecisionReason.RETRY) {
                // Anchor at start; jitter is applied once when the visit completes successfully.
                lastAnchorNs = now;
                currentExecutionStartNs = now;
                hasAnchoredExecution = true;
                persistAnchorToVariables(lastAnchorNs);
            }
            logDecision("EXECUTE", decision);
        } else {
            visitState = VisitState.SKIPPED;
            // IMPORTANT: must NOT setDone(true) on skip.
            // GenericController.currentReturnedNull() removes a child Controller from the
            // parent's subControllers list when isDone()==true, which permanently drops this
            // IntervalController from subsequent parent-loop iterations (after the first skip).
            // Keep done=false so the parent only incrementCurrent()'s past us for this visit;
            // the next parent reInitialize/resetCurrent will enter us again to re-check the interval.
            setDone(false);
            logDecision("SKIP", decision);
        }
    }

    Decision evaluate(long nowNs) {
        // 1) Manual force refresh
        if (isForceRefreshRequested()) {
            long eff = resolveEffectiveIntervalNs(nowNs, true);
            if (eff < 0L) {
                return Decision.skip(DecisionReason.SKIP_INVALID_INTERVAL, 0L, nowNs);
            }
            return Decision.execute(DecisionReason.FORCE, eff, nowNs);
        }

        // 2) Token refresh-before-expiration (wall clock — never mix with nanoTime)
        if (isTokenRefreshDue()) {
            long eff = resolveEffectiveIntervalNs(nowNs, true);
            if (eff < 0L) {
                return Decision.skip(DecisionReason.SKIP_INVALID_INTERVAL, 0L, nowNs);
            }
            return Decision.execute(DecisionReason.EXPIRED, eff, nowNs);
        }

        // 3) Retry after failure
        if (retryPending) {
            long max = getMaxRetries();
            if (max > 0L && consecutiveFailures > max) {
                // Should have been cleared already; treat as normal interval
                retryPending = false;
                maxRetriesExhausted = true;
            } else if (nowNs >= retryReadyAtNs) {
                long eff = resolveEffectiveIntervalNs(nowNs, false);
                return Decision.execute(DecisionReason.RETRY, Math.max(0L, eff), retryReadyAtNs);
            } else {
                return Decision.skip(DecisionReason.SKIP_INTERVAL, lastEffectiveIntervalNs, retryReadyAtNs);
            }
        }

        // 4) First visit / interval
        long eff = resolveEffectiveIntervalNs(nowNs, true);
        if (eff < 0L) {
            return Decision.skip(DecisionReason.SKIP_INVALID_INTERVAL, 0L, nowNs);
        }

        if (!hasAnchoredExecution) {
            if (isExecuteOnFirstIteration()) {
                return Decision.execute(DecisionReason.FIRST_ITERATION, eff, nowNs);
            }
            if (firstSeenNs == 0L) {
                firstSeenNs = nowNs;
                nextAllowedNs = nowNs + eff;
                return Decision.skip(DecisionReason.SKIP_FIRST_WAIT, eff, nextAllowedNs);
            }
            if (nowNs - firstSeenNs >= eff) {
                return Decision.execute(DecisionReason.INTERVAL_ELAPSED, eff, nowNs);
            }
            return Decision.skip(DecisionReason.SKIP_FIRST_WAIT, eff, firstSeenNs + eff);
        }

        if (eff <= 0L) {
            return Decision.execute(DecisionReason.INTERVAL_ELAPSED, 0L, nowNs);
        }
        long allowedAt = nextAllowedNs > 0L ? nextAllowedNs : lastAnchorNs + eff;
        if (nowNs >= allowedAt) {
            return Decision.execute(DecisionReason.INTERVAL_ELAPSED, eff, nowNs);
        }
        return Decision.skip(DecisionReason.SKIP_INTERVAL, eff, allowedAt);
    }

    /** Convenience for tests / diagnostics. */
    public long getIntervalNanos() {
        long ns = resolveEffectiveIntervalNs(getClock().nanoTime(), false);
        return ns < 0L ? 0L : ns;
    }

    boolean shouldExecute(long nowNs) {
        return evaluate(nowNs).execute;
    }

    void onExecutionCompleted() {
        long now = getClock().nanoTime();
        boolean failed = visitHadFailure;

        if (failed && isRetryOnFailure()) {
            consecutiveFailures++;
            long max = getMaxRetries();
            if (max > 0L && consecutiveFailures > max) {
                maxRetriesExhausted = true;
                retryPending = false;
                logDebug("RETRY", "max retries exhausted (" + max + "), falling back to normal interval");
                consecutiveFailures = 0;
                finishSuccessfulWindow(now);
            } else {
                long delayNs = resolveRetryDelayNs();
                retryPending = true;
                retryReadyAtNs = now + Math.max(0L, delayNs);
                nextAllowedNs = retryReadyAtNs;
                logDebug("RETRY", "scheduled retry #" + consecutiveFailures
                        + " after delayNs=" + delayNs + " readyAt=" + retryReadyAtNs);
            }
        } else {
            if (!failed) {
                consecutiveFailures = 0;
                retryPending = false;
                maxRetriesExhausted = false;
            } else if (!isRetryOnFailure()) {
                // Failure without retry → still advance normal interval
                consecutiveFailures = 0;
                retryPending = false;
            }
            if (!retryPending) {
                finishSuccessfulWindow(now);
            }
        }

        visitState = VisitState.COMPLETED;
        currentExecutionStartNs = 0L;
        if (!failed) {
            bindTtlAfterSuccessfulVisit();
        }
        logDebug("COMPLETE", "failed=" + failed + " nextAllowedNs=" + nextAllowedNs);
    }

    /**
     * TTL_SECONDS contract: after a <strong>successful</strong> nested execution that (re)issues a token,
     * bind {@code issuedAt = wallClock.now} (or an explicit issued-at variable written by the sampler)
     * and remember the TTL seconds from {@link #getTokenExpirationVar()}.
     * Does not run on failure / retry-pending paths that did not finish successfully.
     */
    void bindTtlAfterSuccessfulVisit() {
        if (getExpirationFormat() != ExpirationFormat.TTL_SECONDS) {
            return;
        }
        String varName = getTokenExpirationVar();
        if (varName == null || varName.isEmpty()) {
            return;
        }
        JMeterVariables vars = currentVariables();
        if (vars == null) {
            return;
        }
        String raw = vars.get(varName);
        Long ttl = parseNonNegativeLong(raw);
        if (ttl == null) {
            return;
        }
        // Successful token-refresh visit: re-anchor issuance to wall-clock now.
        // Companion var is overwritten so the next decide() uses expiresAt = now + TTL.
        // External scripts may set the companion var without an IC visit; that path is
        // handled in isTtlRefreshDue() without requiring this bind.
        String issuedKey = resolveTokenIssuedAtVarName();
        long issuedAt = getWallClock().currentTimeMillis();
        tokenIssuedAtMs = issuedAt;
        boundTtlSeconds = ttl.longValue();
        if (!issuedKey.isEmpty()) {
            vars.put(issuedKey, Long.toString(issuedAt));
        }
        logDebug("TTL_BIND", "issuedAtMs=" + tokenIssuedAtMs + " ttlSec=" + boundTtlSeconds
                + " issuedVar=" + issuedKey);
    }

    private void finishSuccessfulWindow(long nowNs) {
        long eff = resolveEffectiveIntervalNs(nowNs, true);
        if (eff < 0L) {
            eff = 0L;
        }
        long jitter = rollJitterNs();
        lastJitterNs = jitter;
        lastEffectiveIntervalNs = eff;
        long wait = Math.max(0L, eff + jitter);

        if (getIntervalStartMode() == IntervalStartMode.EXECUTION_COMPLETION) {
            lastAnchorNs = nowNs;
            nextAllowedNs = nowNs + wait;
        } else {
            // START mode: anchor already set at begin; nextAllowed from start + wait
            if (currentExecutionStartNs > 0L) {
                lastAnchorNs = currentExecutionStartNs;
            }
            nextAllowedNs = lastAnchorNs + wait;
        }
        hasAnchoredExecution = true;
        persistAnchorToVariables(lastAnchorNs);
    }

    // ========== resolvers ==========

    /**
     * Resolves effective interval in nanos.
     * Dynamic expression is evaluated <strong>on each new controller entry</strong> (decideVisit).
     *
     * @return interval nanos, or {@code -1} if invalid when dynamic/required
     */
    long resolveEffectiveIntervalNs(long nowNs, boolean rollNewJitterIgnored) {
        String raw = getIntervalExpression();
        String resolved = raw;
        if (isDynamicIntervalEnabled() || looksLikeExpression(raw)) {
            resolved = expand(raw);
        }
        Long amount = parseNonNegativeLong(resolved);
        if (amount == null) {
            log.error("IntervalController '{}': invalid interval expression '{}' (resolved='{}'). "
                    + "Skipping this entry — not substituting a silent default.",
                    getName(), raw, resolved);
            return -1L;
        }
        try {
            return getTimeUnit().toNanos(amount.longValue());
        } catch (IllegalArgumentException ex) {
            log.error("IntervalController '{}': cannot convert interval {}", getName(), amount);
            return -1L;
        }
    }

    long resolveRetryDelayNs() {
        Long amount = parseNonNegativeLong(expand(getRetryDelayExpression()));
        if (amount == null) {
            log.warn("IntervalController '{}': invalid retry delay, using 0", getName());
            return 0L;
        }
        return getRetryDelayUnit().toNanos(amount.longValue());
    }

    long rollJitterNs() {
        Long amount = parseNonNegativeLong(expand(getJitterExpression()));
        if (amount == null || amount.longValue() <= 0L) {
            return 0L;
        }
        long maxNs = getJitterUnit().toNanos(amount.longValue());
        if (maxNs <= 0L) {
            return 0L;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (getJitterMode() == JitterMode.SYMMETRIC) {
            // [-max, +max]
            long delta = rnd.nextLong(-maxNs, maxNs + 1L);
            return delta;
        }
        // [0, +max]
        return rnd.nextLong(0L, maxNs + 1L);
    }

    boolean isForceRefreshRequested() {
        JMeterVariables vars = currentVariables();
        if (vars == null) {
            return false;
        }
        String key = getForceRefreshVar();
        // Per-controller override: <configured>__<instanceId>
        String scoped = key + "__" + ensureInstanceId();
        if (isTruthy(vars.get(scoped))) {
            return true;
        }
        return isTruthy(vars.get(key));
    }

    void clearForceRefreshVariable() {
        JMeterVariables vars = currentVariables();
        if (vars == null) {
            return;
        }
        String key = getForceRefreshVar();
        vars.remove(key);
        vars.remove(key + "__" + ensureInstanceId());
    }

    boolean isTokenRefreshDue() {
        String varName = getTokenExpirationVar();
        if (varName == null || varName.isEmpty()) {
            return false;
        }
        JMeterVariables vars = currentVariables();
        if (vars == null) {
            return false;
        }
        String raw = vars.get(varName);
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        // Negative / non-numeric → ignore (do not force refresh)
        Long numeric = parseNonNegativeLong(raw.trim());
        if (numeric == null) {
            log.warn("IntervalController '{}': token expiration var '{}' has non-numeric/negative value (ignored)",
                    getName(), varName);
            return false;
        }
        long nowWall = getWallClock().currentTimeMillis();
        Long refreshBefore = parseNonNegativeLong(expand(getRefreshBeforeExpression()));
        long refreshBeforeMs = refreshBefore == null ? 0L
                : getRefreshBeforeUnit().toNanos(refreshBefore.longValue()) / 1_000_000L;

        ExpirationFormat format = getExpirationFormat();
        long expiresAtMs;
        switch (format) {
            case UNIX_SECONDS:
                expiresAtMs = safeSecondsToMillis(numeric.longValue());
                break;
            case TTL_SECONDS:
                return isTtlRefreshDue(vars, numeric.longValue(), nowWall, refreshBeforeMs);
            case UNIX_MILLISECONDS:
            default:
                expiresAtMs = numeric.longValue();
                break;
        }
        return nowWall >= expiresAtMs - refreshBeforeMs;
    }

    /**
     * TTL_SECONDS: {@code expiresAt = issuedAt + ttlSeconds}. Refresh when
     * {@code now >= expiresAt - refreshBefore}.
     * <p>Issued-at resolution order:</p>
     * <ol>
     *   <li>JMeter variable from {@link #resolveTokenIssuedAtVarName()} if set and &gt; 0</li>
     *   <li>Else in-memory {@code tokenIssuedAtMs} bound after a successful visit</li>
     *   <li>Else unbound → not due (avoids permanent refresh loops on a bare TTL constant)</li>
     * </ol>
     * TTL {@code 0} means already expired → due once bound (or immediately if issued-at present).
     */
    boolean isTtlRefreshDue(JMeterVariables vars, long ttlSeconds, long nowWallMs, long refreshBeforeMs) {
        if (ttlSeconds < 0L) {
            return false;
        }
        String issuedKey = resolveTokenIssuedAtVarName();
        long issuedAt = 0L;
        if (vars != null && issuedKey != null && !issuedKey.isEmpty()) {
            Long fromVar = parseNonNegativeLong(vars.get(issuedKey));
            if (fromVar != null && fromVar.longValue() > 0L) {
                issuedAt = fromVar.longValue();
                // Keep memory in sync when sampler owns issued-at
                tokenIssuedAtMs = issuedAt;
                boundTtlSeconds = ttlSeconds;
            }
        }
        if (issuedAt <= 0L) {
            issuedAt = tokenIssuedAtMs;
        }
        if (issuedAt <= 0L) {
            // Unbound: do not treat a static TTL as "always refresh"
            return false;
        }
        long ttlMs = safeSecondsToMillis(ttlSeconds);
        long expiresAtMs = safeAdd(issuedAt, ttlMs);
        return nowWallMs >= expiresAtMs - refreshBeforeMs;
    }

    /**
     * Convert non-negative seconds to milliseconds without overflowing to a negative value.
     * Saturates at {@link Long#MAX_VALUE} on overflow (treat as "far future").
     */
    static long safeSecondsToMillis(long seconds) {
        if (seconds <= 0L) {
            return 0L;
        }
        if (seconds > Long.MAX_VALUE / 1000L) {
            return Long.MAX_VALUE;
        }
        return seconds * 1000L;
    }

    static long safeAdd(long a, long b) {
        long r = a + b;
        // Overflow if both positive and sum wrapped negative, or both negative and sum wrapped positive.
        if (((a ^ r) & (b ^ r)) < 0L) {
            return Long.MAX_VALUE;
        }
        return r;
    }

    long getTokenIssuedAtMsForTest() {
        return tokenIssuedAtMs;
    }

    long getBoundTtlSecondsForTest() {
        return boundTtlSeconds;
    }

    void setTokenIssuedAtMsForTest(long issuedAtMs) {
        this.tokenIssuedAtMs = issuedAtMs;
    }

    // ========== helpers ==========

    private void logDecision(String action, Decision decision) {
        if (!log.isDebugEnabled()) {
            return;
        }
        long now = getClock().nanoTime();
        long until = decision.nextAllowedNs > now ? decision.nextAllowedNs - now : 0L;
        log.debug("IntervalController name='{}' thread={} action={} reason={} intervalNs={} "
                        + "jitterNs={} untilNextNs={} nextAllowedNs={}",
                getName(), threadLabel(), action, decision.reason,
                decision.effectiveIntervalNs, lastJitterNs, until, decision.nextAllowedNs);
    }

    private void logDebug(String action, String detail) {
        if (log.isDebugEnabled()) {
            log.debug("IntervalController name='{}' thread={} action={} {}",
                    getName(), threadLabel(), action, detail);
        }
    }

    private static String threadLabel() {
        try {
            JMeterContext ctx = JMeterContextService.getContext();
            if (ctx != null && ctx.getThread() != null) {
                return ctx.getThread().getThreadName();
            }
            if (ctx != null) {
                return "threadNum=" + ctx.getThreadNum();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Thread.currentThread().getName();
    }

    private void ensureTransientState() {
        if (clock == null) {
            clock = SystemNanoClock.INSTANCE;
        }
        if (wallClock == null) {
            wallClock = SystemWallClock.INSTANCE;
        }
        if (visitState == null) {
            visitState = VisitState.UNDECIDED;
        }
    }

    private void persistAnchorToVariables(long anchorNs) {
        JMeterVariables vars = currentVariables();
        if (vars != null) {
            vars.put(variablesKey("lastAnchorNs"), Long.toString(anchorNs));
        }
    }

    private JMeterVariables currentVariables() {
        try {
            JMeterContext ctx = getThreadContext();
            if (ctx == null) {
                ctx = JMeterContextService.getContext();
            }
            return ctx == null ? null : ctx.getVariables();
        } catch (Exception ex) {
            return null;
        }
    }

    static String expand(String expression) {
        if (expression == null) {
            return "";
        }
        String trimmed = expression.trim();
        if (!looksLikeExpression(trimmed)) {
            return trimmed;
        }
        try {
            CompoundVariable cv = new CompoundVariable(trimmed);
            return cv.execute();
        } catch (Exception ex) {
            return trimmed;
        }
    }

    static boolean looksLikeExpression(String value) {
        return value != null && value.indexOf("${") >= 0;
    }

    static Long parseNonNegativeLong(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            long v = Long.parseLong(t);
            return v < 0L ? null : Long.valueOf(v);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String t = value.trim();
        return "true".equalsIgnoreCase(t) || "1".equals(t) || "yes".equalsIgnoreCase(t);
    }

    // package-visible for tests
    VisitState getVisitState() {
        return visitState;
    }

    long getLastAnchorNs() {
        return lastAnchorNs;
    }

    boolean isHasAnchoredExecution() {
        return hasAnchoredExecution;
    }

    DecisionReason getLastReason() {
        return lastReason;
    }

    long getNextAllowedNs() {
        return nextAllowedNs;
    }

    boolean isRetryPending() {
        return retryPending;
    }

    int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    void setVisitHadFailureForTest(boolean failed) {
        this.visitHadFailure = failed;
    }

    /** Immutable decision DTO. */
    static final class Decision {
        final boolean execute;
        final DecisionReason reason;
        final long effectiveIntervalNs;
        final long nextAllowedNs;

        private Decision(boolean execute, DecisionReason reason, long effectiveIntervalNs, long nextAllowedNs) {
            this.execute = execute;
            this.reason = reason;
            this.effectiveIntervalNs = effectiveIntervalNs;
            this.nextAllowedNs = nextAllowedNs;
        }

        static Decision execute(DecisionReason reason, long eff, long nextAllowed) {
            return new Decision(true, reason, eff, nextAllowed);
        }

        static Decision skip(DecisionReason reason, long eff, long nextAllowed) {
            return new Decision(false, reason, eff, nextAllowed);
        }
    }
}
