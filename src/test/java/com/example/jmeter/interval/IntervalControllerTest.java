package com.example.jmeter.interval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.jmeter.control.GenericController;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.OnceOnlyController;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.LongProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.junit.Before;
import org.junit.Test;

public class IntervalControllerTest {

    private static final long SEC = 1_000_000_000L;
    private static final long MIN = 60L * SEC;

    private MutableNanoClock clock;
    private MutableWallClock wall;
    private JMeterVariables vars;

    @Before
    public void setUp() {
        clock = new MutableNanoClock(1_000_000_000_000L);
        wall = new MutableWallClock(1_700_000_000_000L);
        vars = new JMeterVariables();
        JMeterContextService.getContext().setVariables(vars);
        JMeterContextService.getContext().setThreadNum(1);
    }

    private IntervalController base(long interval, TimeUnitOption unit) {
        IntervalController c = new IntervalController();
        c.setClock(clock);
        c.setWallClock(wall);
        c.setIntervalValue(interval);
        c.setTimeUnit(unit);
        c.setExecuteOnFirstIteration(true);
        c.setIntervalStartMode(IntervalStartMode.EXECUTION_COMPLETION);
        c.setDynamicIntervalEnabled(false);
        c.setJitterExpression("0");
        c.setRetryOnFailure(false);
        c.setRunningVersion(true);
        c.initialize();
        return c;
    }

    private List<String> runVisit(IntervalController controller) {
        controller.iterationStart(new LoopIterationEvent(controller, 1));
        List<String> executed = new ArrayList<String>();
        Sampler sampler;
        while ((sampler = controller.next()) != null) {
            executed.add(sampler.getName());
            sampler.sample(new Entry());
        }
        return executed;
    }

    private CountingSampler child(IntervalController c, String name) {
        CountingSampler s = new CountingSampler(name);
        s.setSampleListener(c);
        c.addTestElement(s);
        return s;
    }

    @Test
    public void test1_firstExecution() {
        IntervalController c = base(10, TimeUnitOption.MINUTES);
        CountingSampler s = child(c, "Login");
        assertEquals(1, runVisit(c).size());
        assertEquals(1, s.getExecutions());
        assertEquals(DecisionReason.FIRST_ITERATION, c.getLastReason());
    }

    @Test
    public void test2_skipBeforeInterval() {
        IntervalController c = base(10, TimeUnitOption.MINUTES);
        CountingSampler s = child(c, "Login");
        runVisit(c);
        clock.advanceMillis(30_000);
        assertTrue(runVisit(c).isEmpty());
        assertEquals(1, s.getExecutions());
        assertEquals(DecisionReason.SKIP_INTERVAL, c.getLastReason());
    }

    @Test
    public void test3_executeAfterInterval() {
        IntervalController c = base(10, TimeUnitOption.MINUTES);
        CountingSampler s = child(c, "Login");
        runVisit(c);
        clock.advanceNanos(MIN * 10 + 1);
        assertEquals(1, runVisit(c).size());
        assertEquals(2, s.getExecutions());
    }

    @Test
    public void test4_executionStartAndCompletion() {
        IntervalController start = new IntervalController();
        start.setClock(clock);
        start.setIntervalValue(10);
        start.setTimeUnit(TimeUnitOption.MINUTES);
        start.setIntervalStartMode(IntervalStartMode.EXECUTION_START);
        start.setJitterExpression("0");
        start.setRunningVersion(true);
        CountingSampler s1 = child(start, "W");
        s1.setSimulatedWork(clock, MIN * 2);
        start.initialize();
        long t0 = clock.nanoTime();
        runVisit(start);
        clock.setNanoTime(t0 + MIN * 10);
        assertEquals(1, runVisit(start).size());

        clock.setNanoTime(t0);
        IntervalController end = new IntervalController();
        end.setClock(clock);
        end.setIntervalValue(10);
        end.setTimeUnit(TimeUnitOption.MINUTES);
        end.setIntervalStartMode(IntervalStartMode.EXECUTION_COMPLETION);
        end.setJitterExpression("0");
        end.setRunningVersion(true);
        CountingSampler s2 = child(end, "W2");
        s2.setSimulatedWork(clock, MIN * 2);
        end.initialize();
        runVisit(end);
        clock.setNanoTime(t0 + MIN * 10);
        assertTrue(runVisit(end).isEmpty());
        clock.setNanoTime(t0 + MIN * 12);
        assertEquals(1, runVisit(end).size());
    }

    @Test
    public void test5_hundredThreadsIndependent() {
        CountingSampler[] samples = new CountingSampler[100];
        IntervalController[] ctrls = new IntervalController[100];
        for (int i = 0; i < 100; i++) {
            ctrls[i] = base(10, TimeUnitOption.MINUTES);
            samples[i] = child(ctrls[i], "T" + i);
            // stagger first execution by advancing clock only for later threads via separate visits
        }
        for (int i = 0; i < 100; i++) {
            assertEquals(1, runVisit(ctrls[i]).size());
        }
        clock.advanceNanos(MIN * 5);
        for (int i = 0; i < 100; i++) {
            assertTrue(runVisit(ctrls[i]).isEmpty());
            assertEquals(1, samples[i].getExecutions());
        }
        clock.advanceNanos(MIN * 5 + 1);
        for (int i = 0; i < 100; i++) {
            assertEquals(1, runVisit(ctrls[i]).size());
            assertEquals(2, samples[i].getExecutions());
        }
    }

    @Test
    public void test6_multipleControllersSameThread() {
        IntervalController a = base(10, TimeUnitOption.MINUTES);
        IntervalController b = base(1, TimeUnitOption.MINUTES);
        CountingSampler sa = child(a, "A");
        CountingSampler sb = child(b, "B");
        runVisit(a);
        runVisit(b);
        clock.advanceNanos(MIN + 1);
        assertTrue(runVisit(a).isEmpty());
        assertEquals(1, runVisit(b).size());
        assertEquals(1, sa.getExecutions());
        assertEquals(2, sb.getExecutions());
    }

    @Test
    public void test7_retryOnFailure() {
        IntervalController c = base(10, TimeUnitOption.MINUTES);
        c.setRunningVersion(false);
        c.setRetryOnFailure(true);
        c.setRetryDelayExpression("30");
        c.setRetryDelayUnit(TimeUnitOption.SECONDS);
        c.setMaxRetries(0);
        c.setRunningVersion(true);
        CountingSampler s = child(c, "Auth");
        s.setFail(true);
        assertEquals(1, runVisit(c).size());
        assertTrue(c.isRetryPending());
        clock.advanceMillis(10_000);
        assertTrue(runVisit(c).isEmpty()); // retry delay 30s
        clock.advanceMillis(25_000);
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.RETRY, c.getLastReason());
        assertEquals(2, s.getExecutions());
    }

    @Test
    public void test8_maximumRetriesThenCooldown() {
        IntervalController c = base(10, TimeUnitOption.MINUTES);
        c.setRunningVersion(false);
        c.setRetryOnFailure(true);
        c.setRetryDelayExpression("1");
        c.setRetryDelayUnit(TimeUnitOption.SECONDS);
        c.setMaxRetries(2);
        c.setRunningVersion(true);
        CountingSampler s = child(c, "Auth");
        s.setFail(true);

        runVisit(c); // fail 1
        clock.advanceMillis(1001);
        runVisit(c); // retry fail 2
        clock.advanceMillis(1001);
        runVisit(c); // retry fail 3 → consecutiveFailures=3 > max 2 → fallback to normal interval
        assertEquals(3, s.getExecutions());
        clock.advanceMillis(1001);
        // now under normal 10 min window
        assertTrue(runVisit(c).isEmpty());
        clock.advanceNanos(MIN * 10);
        assertEquals(1, runVisit(c).size());
    }

    @Test
    public void test9_jitterNonNegativeWait() {
        IntervalController c = base(10, TimeUnitOption.SECONDS);
        c.setRunningVersion(false);
        c.setJitterExpression("5");
        c.setJitterUnit(TimeUnitOption.SECONDS);
        c.setJitterMode(JitterMode.SYMMETRIC);
        c.setRunningVersion(true);
        child(c, "X");
        runVisit(c);
        long next = c.getNextAllowedNs();
        assertTrue(next >= c.getLastAnchorNs()); // wait >= 0 ⇒ nextAllowed >= anchor
    }

    @Test
    public void test10_dynamicIntervalInvalidSkips() {
        IntervalController c = base(10, TimeUnitOption.SECONDS);
        c.setRunningVersion(false);
        c.setDynamicIntervalEnabled(true);
        c.setIntervalExpression("${missing_or_bad}");
        c.setRunningVersion(true);
        vars.put("missing_or_bad", "not-a-number");
        CountingSampler s = child(c, "X");
        // expand leaves "not-a-number" → invalid → skip
        assertTrue(runVisit(c).isEmpty());
        assertEquals(0, s.getExecutions());
        assertEquals(DecisionReason.SKIP_INVALID_INTERVAL, c.getLastReason());
    }

    @Test
    public void test10b_dynamicIntervalFromVariable() {
        IntervalController c = new IntervalController();
        c.setClock(clock);
        c.setDynamicIntervalEnabled(true);
        c.setIntervalExpression("${refresh_interval}");
        c.setTimeUnit(TimeUnitOption.SECONDS);
        c.setJitterExpression("0");
        c.setRunningVersion(true);
        vars.put("refresh_interval", "5");
        CountingSampler s = child(c, "X");
        c.initialize();
        runVisit(c);
        clock.advanceMillis(4000);
        assertTrue(runVisit(c).isEmpty());
        clock.advanceMillis(2000);
        assertEquals(1, runVisit(c).size());
        assertEquals(2, s.getExecutions());
    }

    @Test
    public void test11_refreshBeforeExpiration() {
        IntervalController c = base(60, TimeUnitOption.MINUTES);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_expires_at");
        c.setExpirationFormat(ExpirationFormat.UNIX_MILLISECONDS);
        c.setRefreshBeforeExpression("30");
        c.setRefreshBeforeUnit(TimeUnitOption.SECONDS);
        c.setRunningVersion(true);
        CountingSampler s = child(c, "Refresh");
        runVisit(c); // first
        clock.advanceNanos(MIN); // still inside 60 min nano interval
        wall.setMillis(wall.currentTimeMillis());
        long expires = wall.currentTimeMillis() + 20_000L; // expires in 20s < 30s refresh window
        vars.put("token_expires_at", Long.toString(expires));
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.EXPIRED, c.getLastReason());
        assertEquals(2, s.getExecutions());
    }

    @Test
    public void test12_manualForceRefresh() {
        IntervalController c = base(10, TimeUnitOption.HOURS);
        CountingSampler s = child(c, "Auth");
        runVisit(c);
        assertTrue(runVisit(c).isEmpty());
        vars.put("interval_controller_force_refresh", "true");
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.FORCE, c.getLastReason());
        assertEquals(null, vars.get("interval_controller_force_refresh"));
        assertEquals(2, s.getExecutions());
    }

    @Test
    public void test12b_scopedForceRefreshDoesNotAffectOther() {
        IntervalController a = base(10, TimeUnitOption.HOURS);
        IntervalController b = base(10, TimeUnitOption.HOURS);
        child(a, "A");
        child(b, "B");
        runVisit(a);
        runVisit(b);
        vars.put(a.getForceRefreshVar() + "__" + a.ensureInstanceId(), "true");
        assertEquals(1, runVisit(a).size());
        assertTrue(runVisit(b).isEmpty());
    }

    @Test
    public void test13_nestedTransactionController() {
        IntervalController c = base(5, TimeUnitOption.MINUTES);
        TransactionController tx = new TransactionController();
        tx.setName("Authorization");
        tx.setRunningVersion(true);
        CountingSampler login = new CountingSampler("Login");
        login.setSampleListener(c);
        CountingSampler token = new CountingSampler("Extract");
        token.setSampleListener(c);
        tx.addTestElement(login);
        tx.addTestElement(token);
        c.addTestElement(tx);
        c.initialize();
        tx.initialize();
        List<String> names = runVisit(c);
        assertTrue(names.contains("Login"));
        assertTrue(names.contains("Extract"));
        clock.advanceMillis(1000);
        assertTrue(runVisit(c).isEmpty());
    }

    @Test
    public void test14_nestedLoopController() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        LoopController loop = new LoopController();
        loop.setLoops(3);
        loop.setContinueForever(false);
        loop.setRunningVersion(true);
        CountingSampler s = new CountingSampler("Inner");
        s.setSampleListener(c);
        loop.addTestElement(s);
        c.addTestElement(loop);
        c.initialize();
        loop.initialize();
        runVisit(c);
        assertEquals(3, s.getExecutions());
        assertTrue(runVisit(c).isEmpty());
        assertEquals(3, s.getExecutions());
    }

    @Test
    public void test15_emptyController() {
        IntervalController c = base(1, TimeUnitOption.MINUTES);
        assertTrue(runVisit(c).isEmpty()); // no children — completes immediately
        assertEquals(IntervalController.VisitState.COMPLETED, c.getVisitState());
        clock.advanceNanos(MIN + 1);
        runVisit(c);
        assertEquals(IntervalController.VisitState.COMPLETED, c.getVisitState());
    }

    @Test
    public void test16_propertyRoundTrip() {
        IntervalController original = new IntervalController();
        original.setName("Auth Interval");
        original.setIntervalExpression("15");
        original.setTimeUnit(TimeUnitOption.SECONDS);
        original.setExecuteOnFirstIteration(false);
        original.setIntervalStartMode(IntervalStartMode.EXECUTION_START);
        original.setRetryOnFailure(true);
        original.setRetryDelayExpression("2");
        original.setMaxRetries(3);
        original.setJitterExpression("1");
        original.setJitterMode(JitterMode.SYMMETRIC);
        original.setDynamicIntervalEnabled(true);
        original.setTokenExpirationVar("exp");
        original.setForceRefreshVar("force_me");
        String id = original.ensureInstanceId();

        IntervalController restored = new IntervalController();
        restored.setName(original.getName());
        restored.setProperty(new StringProperty(IntervalController.INTERVAL_VALUE, original.getIntervalExpression()));
        restored.setProperty(new StringProperty(IntervalController.TIME_UNIT, original.getTimeUnit().name()));
        restored.setProperty(new BooleanProperty(IntervalController.EXECUTE_ON_FIRST, false));
        restored.setProperty(new StringProperty(IntervalController.INTERVAL_MODE, IntervalStartMode.EXECUTION_START.name()));
        restored.setProperty(new BooleanProperty(IntervalController.RETRY_ON_FAILURE, true));
        restored.setProperty(new StringProperty(IntervalController.RETRY_DELAY, "2"));
        restored.setProperty(new LongProperty(IntervalController.MAX_RETRIES, 3L));
        restored.setProperty(new StringProperty(IntervalController.JITTER_VALUE, "1"));
        restored.setProperty(new StringProperty(IntervalController.JITTER_MODE, JitterMode.SYMMETRIC.name()));
        restored.setProperty(new BooleanProperty(IntervalController.DYNAMIC_INTERVAL, true));
        restored.setProperty(new StringProperty(IntervalController.TOKEN_EXPIRATION_VAR, "exp"));
        restored.setProperty(new StringProperty(IntervalController.FORCE_REFRESH_VAR, "force_me"));
        restored.setProperty(new StringProperty(IntervalController.INSTANCE_ID, id));

        assertEquals("15", restored.getIntervalExpression());
        assertFalse(restored.isExecuteOnFirstIteration());
        assertTrue(restored.isRetryOnFailure());
        assertEquals(3L, restored.getMaxRetries());
        assertEquals(JitterMode.SYMMETRIC, restored.getJitterMode());
        assertTrue(restored.isDynamicIntervalEnabled());
        assertEquals("force_me", restored.getForceRefreshVar());
        assertEquals(id, restored.ensureInstanceId());
    }

    @Test
    public void test17_compatWithLegacyLongProperty() {
        IntervalController c = new IntervalController();
        // Simulate 1.0.0 longProp on disk
        c.setProperty(new LongProperty(IntervalController.INTERVAL_VALUE, 42L));
        c.setProperty(new StringProperty(IntervalController.TIME_UNIT, "SECONDS"));
        assertEquals("42", c.getIntervalExpression());
        assertEquals(42L * SEC, c.getTimeUnit().toNanos(c.getIntervalValue()));
    }

    @Test
    public void test18_longLoopManyIterations() {
        IntervalController c = base(5, TimeUnitOption.SECONDS);
        CountingSampler s = child(c, "Ping");
        int executed = 0;
        for (int i = 0; i < 50; i++) {
            if (!runVisit(c).isEmpty()) {
                executed++;
            }
            clock.advanceMillis(1000);
        }
        assertTrue("expected multiple executions, got " + executed, executed >= 9);
        assertEquals(executed, s.getExecutions());
    }

    @Test
    public void testDisabledFirstExecution() {
        IntervalController c = new IntervalController();
        c.setClock(clock);
        c.setIntervalValue(10);
        c.setTimeUnit(TimeUnitOption.MINUTES);
        c.setExecuteOnFirstIteration(false);
        c.setJitterExpression("0");
        c.setRunningVersion(true);
        CountingSampler s = child(c, "Login");
        c.initialize();
        assertTrue(runVisit(c).isEmpty());
        clock.advanceNanos(MIN * 10 - 1);
        assertTrue(runVisit(c).isEmpty());
        clock.advanceNanos(2);
        assertEquals(1, runVisit(c).size());
        assertEquals(1, s.getExecutions());
    }

    @Test
    public void cloneCreatesIndependentInstanceIds() {
        IntervalController a = new IntervalController();
        String idA = a.ensureInstanceId();
        IntervalController b = (IntervalController) a.clone();
        assertNotNull(b.ensureInstanceId());
        assertNotEquals(idA, b.ensureInstanceId());
    }

    @Test
    public void successTreeDetectsNestedFailure() {
        org.apache.jmeter.samplers.SampleResult parent = new org.apache.jmeter.samplers.SampleResult();
        parent.setSuccessful(true);
        org.apache.jmeter.samplers.SampleResult child = new org.apache.jmeter.samplers.SampleResult();
        child.setSuccessful(false);
        parent.addSubResult(child);
        assertFalse(IntervalController.isSuccessfulTree(parent));
    }

    @Test
    public void ttlSecondsUnboundDoesNotForceRefresh() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("30");
        c.setRefreshBeforeUnit(TimeUnitOption.SECONDS);
        c.setRunningVersion(true);
        vars.put("token_ttl", "3600");
        child(c, "Refresh");
        runVisit(c); // first iteration — binds TTL after success
        assertTrue(c.getTokenIssuedAtMsForTest() > 0L);
        assertEquals(3600L, c.getBoundTtlSecondsForTest());
        clock.advanceNanos(MIN);
        // still far from expiry (3600s - 30s window)
        assertTrue(runVisit(c).isEmpty());
        assertEquals(DecisionReason.SKIP_INTERVAL, c.getLastReason());
    }

    @Test
    public void ttlSecondsRefreshBeforeWindow() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("30");
        c.setRefreshBeforeUnit(TimeUnitOption.SECONDS);
        c.setRunningVersion(true);
        vars.put("token_ttl", "60");
        CountingSampler s = child(c, "Refresh");
        runVisit(c); // bind issuedAt = wall now, ttl=60
        long issued = c.getTokenIssuedAtMsForTest();
        assertTrue(issued > 0L);
        // Advance wall into refresh window: expiresAt - 30s = issued+30s
        wall.setMillis(issued + 35_000L);
        clock.advanceNanos(MIN); // still inside 1h nano interval
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.EXPIRED, c.getLastReason());
        assertEquals(2, s.getExecutions());
        // successful refresh rebinds issuedAt
        assertTrue(c.getTokenIssuedAtMsForTest() >= issued + 35_000L);
    }

    @Test
    public void ttlZeroIsDueWhenBound() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("30");
        c.setRunningVersion(true);
        vars.put("token_ttl", "10");
        child(c, "R");
        runVisit(c);
        vars.put("token_ttl", "0");
        wall.setMillis(c.getTokenIssuedAtMsForTest() + 1L);
        clock.advanceNanos(MIN);
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.EXPIRED, c.getLastReason());
    }

    @Test
    public void ttlNegativeAndMissingIgnored() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRunningVersion(true);
        child(c, "R");
        runVisit(c);
        clock.advanceNanos(MIN);
        vars.put("token_ttl", "-5");
        assertTrue(runVisit(c).isEmpty());
        vars.remove("token_ttl");
        assertTrue(runVisit(c).isEmpty());
        vars.put("token_ttl", "abc");
        assertTrue(runVisit(c).isEmpty());
    }

    @Test
    public void ttlExplicitIssuedAtVariable() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setTokenIssuedAtVar("token_issued");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("10");
        c.setRefreshBeforeUnit(TimeUnitOption.SECONDS);
        c.setRunningVersion(true);
        // External issuance without IC bind yet
        long issued = wall.currentTimeMillis() - 55_000L;
        vars.put("token_ttl", "60");
        vars.put("token_issued", Long.toString(issued));
        CountingSampler s = child(c, "R");
        // first iteration still executes (FIRST), but EXPIRED also true — FORCE/EXPIRED order: force first, then expired
        // evaluate order: force, token, retry, interval — so EXPIRED before first if due
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.EXPIRED, c.getLastReason());
        assertEquals(1, s.getExecutions());
    }

    @Test
    public void ttlNoPermanentRefreshLoopAfterBind() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("30");
        c.setRunningVersion(true);
        vars.put("token_ttl", "3600");
        CountingSampler s = child(c, "R");
        runVisit(c);
        clock.advanceNanos(SEC);
        for (int i = 0; i < 5; i++) {
            assertTrue(runVisit(c).isEmpty());
        }
        assertEquals(1, s.getExecutions());
    }

    @Test
    public void skipDoesNotSetDoneTrue() {
        IntervalController c = base(10, TimeUnitOption.MINUTES);
        child(c, "X");
        runVisit(c);
        clock.advanceMillis(1000);
        runVisit(c);
        assertEquals(IntervalController.VisitState.SKIPPED, c.getVisitState());
        assertFalse(c.isDone());
    }

    @Test
    public void onceOnlyInsideIntervalRunsOncePerThreadAcrossIcExecutions() {
        IntervalController c = base(1, TimeUnitOption.SECONDS);
        OnceOnlyController once = new OnceOnlyController();
        once.setRunningVersion(true);
        CountingSampler s = new CountingSampler("OnceAuth");
        s.setSampleListener(c);
        once.addTestElement(s);
        c.addTestElement(once);
        c.initialize();
        once.initialize();

        assertEquals(1, runVisit(c).size());
        assertEquals(1, s.getExecutions());

        clock.advanceMillis(1001);
        // Second IC execution after interval — OnceOnly must NOT re-arm
        assertTrue("second IC visit should find no samplers (OnceOnly already spent)",
                runVisit(c).isEmpty());
        assertEquals(1, s.getExecutions());
    }

    @Test
    public void nestedLoopResetsOnEachIntervalExecution() {
        IntervalController c = base(1, TimeUnitOption.SECONDS);
        LoopController loop = new LoopController();
        loop.setLoops(3);
        // Finite loop (continueForever=false) sets done=true when finished — stock GenericController
        // would then remove it from the parent list. IC must keep it and re-init on next EXECUTE.
        loop.setContinueForever(false);
        loop.setRunningVersion(true);
        CountingSampler s = new CountingSampler("Inner");
        s.setSampleListener(c);
        loop.addTestElement(s);
        c.addTestElement(loop);
        c.initialize();
        loop.initialize();

        runVisit(c);
        assertEquals(3, s.getExecutions());
        assertEquals("nested finite Loop must remain in IC child list after done", 1, c.childCountForTest());
        clock.advanceMillis(1001);
        runVisit(c);
        assertEquals(6, s.getExecutions());
    }

    @Test
    public void ttlIssuedAtUnchangedOnFailedRefresh() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("30");
        c.setRetryOnFailure(false);
        c.setRunningVersion(true);
        vars.put("token_ttl", "60");
        CountingSampler s = child(c, "Refresh");
        runVisit(c); // success → bind
        long issued = c.getTokenIssuedAtMsForTest();
        assertTrue(issued > 0L);

        wall.setMillis(issued + 40_000L); // inside refresh window
        clock.advanceNanos(MIN);
        s.setFail(true);
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.EXPIRED, c.getLastReason());
        assertEquals("failed refresh must not rebind issuedAt", issued, c.getTokenIssuedAtMsForTest());
        assertEquals(60L, c.getBoundTtlSecondsForTest());
    }

    @Test
    public void ttlRebindOnlyAfterSuccessfulRetry() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("30");
        c.setRetryOnFailure(true);
        c.setRetryDelayExpression("0");
        c.setMaxRetries(3);
        c.setRunningVersion(true);
        vars.put("token_ttl", "60");
        CountingSampler s = child(c, "Refresh");
        runVisit(c);
        long issued = c.getTokenIssuedAtMsForTest();

        wall.setMillis(issued + 40_000L);
        clock.advanceNanos(MIN);
        s.setFail(true);
        runVisit(c); // fail → retry pending, no rebind
        assertEquals(issued, c.getTokenIssuedAtMsForTest());

        s.setFail(false);
        vars.put("token_ttl", "120");
        wall.setMillis(issued + 41_000L);
        assertEquals(1, runVisit(c).size());
        assertTrue(c.getTokenIssuedAtMsForTest() >= issued + 41_000L);
        assertEquals(120L, c.getBoundTtlSecondsForTest());
    }

    @Test
    public void siblingSamplerRunsAfterNestedFiniteLoopInSameVisit() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        LoopController loop = new LoopController();
        loop.setLoops(2);
        loop.setContinueForever(false);
        loop.setRunningVersion(true);
        CountingSampler inner = new CountingSampler("Inner");
        inner.setSampleListener(c);
        loop.addTestElement(inner);
        CountingSampler after = new CountingSampler("After");
        after.setSampleListener(c);
        c.addTestElement(loop);
        c.addTestElement(after);
        c.initialize();
        loop.initialize();

        List<String> names = runVisit(c);
        assertEquals(Arrays.asList("Inner", "Inner", "After"), names);
        assertEquals(2, inner.getExecutions());
        assertEquals(1, after.getExecutions());
        assertEquals(2, c.childCountForTest());
    }

    @Test
    public void siblingAfterOnceOnlyRunsOnFirstVisitOnlyOnceForOnce() {
        IntervalController c = base(1, TimeUnitOption.SECONDS);
        OnceOnlyController once = new OnceOnlyController();
        once.setRunningVersion(true);
        CountingSampler onceS = new CountingSampler("Once");
        onceS.setSampleListener(c);
        once.addTestElement(onceS);
        CountingSampler always = new CountingSampler("Always");
        always.setSampleListener(c);
        c.addTestElement(once);
        c.addTestElement(always);
        c.initialize();
        once.initialize();

        assertEquals(Arrays.asList("Once", "Always"), runVisit(c));
        clock.advanceMillis(1001);
        assertEquals(Arrays.asList("Always"), runVisit(c));
        assertEquals(1, onceS.getExecutions());
        assertEquals(2, always.getExecutions());
    }

    @Test
    public void ttlZeroBoundIsImmediatelyDue() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("0");
        c.setRunningVersion(true);
        vars.put("token_ttl", "0");
        CountingSampler s = child(c, "Refresh");
        runVisit(c);
        long issued = c.getTokenIssuedAtMsForTest();
        assertTrue(issued > 0L);
        assertTrue("TTL 0 must be due immediately after bind",
                c.isTtlRefreshDue(vars, 0L, issued, 0L));
    }

    @Test
    public void ttlNegativeAndMissingDoNotForceRefresh() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRunningVersion(true);
        child(c, "X");
        runVisit(c); // first
        vars.put("token_ttl", "-5");
        assertFalse(c.isTokenRefreshDue());
        vars.remove("token_ttl");
        assertFalse(c.isTokenRefreshDue());
        vars.put("token_ttl", "not-a-number");
        assertFalse(c.isTokenRefreshDue());
    }

    @Test
    public void ttlOverflowSecondsDoesNotWrapNegative() {
        assertEquals(Long.MAX_VALUE, IntervalController.safeSecondsToMillis(Long.MAX_VALUE / 500L));
        assertTrue(IntervalController.safeAdd(Long.MAX_VALUE - 10L, 100L) > 0L);
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRefreshBeforeExpression("0");
        c.setRunningVersion(true);
        long issued = wall.currentTimeMillis();
        c.setTokenIssuedAtMsForTest(issued);
        // Huge TTL must not become due due to signed overflow
        assertFalse(c.isTtlRefreshDue(vars, Long.MAX_VALUE / 500L, issued + 60_000L, 0L));
    }

    @Test
    public void skipDoesNotRebindTtlOrRollNewWindow() {
        IntervalController c = base(10, TimeUnitOption.MINUTES);
        c.setRunningVersion(false);
        c.setTokenExpirationVar("token_ttl");
        c.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        c.setRunningVersion(true);
        vars.put("token_ttl", "3600");
        child(c, "Tok");
        runVisit(c);
        long issued = c.getTokenIssuedAtMsForTest();
        long next = c.getNextAllowedNs();
        clock.advanceMillis(1000);
        assertTrue(runVisit(c).isEmpty());
        assertEquals(issued, c.getTokenIssuedAtMsForTest());
        assertEquals(next, c.getNextAllowedNs());
    }

    @Test
    public void forceRefreshClearsDuringRetryPending() {
        IntervalController c = base(1, TimeUnitOption.HOURS);
        c.setRunningVersion(false);
        c.setRetryOnFailure(true);
        c.setRetryDelayExpression("30");
        c.setRetryDelayUnit(TimeUnitOption.SECONDS);
        c.setMaxRetries(5);
        c.setRunningVersion(true);
        CountingSampler s = child(c, "Auth");
        s.setFail(true);
        runVisit(c);
        assertTrue(c.isRetryPending());
        vars.put("interval_controller_force_refresh", "true");
        s.setFail(false);
        assertEquals(1, runVisit(c).size());
        assertEquals(DecisionReason.FORCE, c.getLastReason());
        assertEquals(null, vars.get("interval_controller_force_refresh"));
    }

    @Test
    public void clonePreserves112ConfigurationProperties() {
        // Model/JMX property fidelity without Swing (GUI interactive checks remain NOT TESTED).
        IntervalController original = new IntervalController();
        original.setName("RoundTrip");
        original.setIntervalExpression("42");
        original.setTimeUnit(TimeUnitOption.SECONDS);
        original.setExecuteOnFirstIteration(false);
        original.setIntervalStartMode(IntervalStartMode.EXECUTION_START);
        original.setRetryOnFailure(true);
        original.setRetryDelayExpression("7");
        original.setMaxRetries(4);
        original.setJitterExpression("3");
        original.setJitterMode(JitterMode.SYMMETRIC);
        original.setDynamicIntervalEnabled(true);
        original.setTokenExpirationVar("ttl");
        original.setTokenIssuedAtVar("ttl_issued");
        original.setExpirationFormat(ExpirationFormat.TTL_SECONDS);
        original.setRefreshBeforeExpression("15");
        original.setForceRefreshVar("force_me");

        IntervalController restored = (IntervalController) original.clone();
        assertEquals("42", restored.getIntervalExpression());
        assertEquals(TimeUnitOption.SECONDS, restored.getTimeUnit());
        assertFalse(restored.isExecuteOnFirstIteration());
        assertEquals(IntervalStartMode.EXECUTION_START, restored.getIntervalStartMode());
        assertTrue(restored.isRetryOnFailure());
        assertEquals("7", restored.getRetryDelayExpression());
        assertEquals(4L, restored.getMaxRetries());
        assertEquals("3", restored.getJitterExpression());
        assertEquals(JitterMode.SYMMETRIC, restored.getJitterMode());
        assertTrue(restored.isDynamicIntervalEnabled());
        assertEquals("ttl", restored.getTokenExpirationVar());
        assertEquals("ttl_issued", restored.getTokenIssuedAtVar());
        assertEquals(ExpirationFormat.TTL_SECONDS, restored.getExpirationFormat());
        assertEquals("15", restored.getRefreshBeforeExpression());
        assertEquals("force_me", restored.getForceRefreshVar());
        assertNotEquals(original.ensureInstanceId(), restored.ensureInstanceId());
    }

    @Test
    public void jmxLegacyPropertiesStillRecognized() {
        IntervalController c = new IntervalController();
        c.setProperty(new StringProperty(IntervalController.INTERVAL_VALUE, "25"));
        c.setProperty(new StringProperty(IntervalController.TIME_UNIT, "SECONDS"));
        c.setProperty(new BooleanProperty(IntervalController.EXECUTE_ON_FIRST, false));
        c.setProperty(new StringProperty(IntervalController.INTERVAL_MODE, "EXECUTION_START"));
        assertEquals("25", c.getIntervalExpression());
        assertEquals(TimeUnitOption.SECONDS, c.getTimeUnit());
        assertFalse(c.isExecuteOnFirstIteration());
        assertEquals(IntervalStartMode.EXECUTION_START, c.getIntervalStartMode());
    }
}
