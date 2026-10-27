package com.example.jmeter.interval;

import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;

public class CountingSampler extends AbstractSampler {
    private static final long serialVersionUID = 1L;

    private int executions;
    private long workNanos;
    private NanoClock clock;
    private boolean fail;
    private SampleListener listener;

    public CountingSampler(String name) {
        setName(name);
    }

    public void setSimulatedWork(NanoClock clock, long workNanos) {
        this.clock = clock;
        this.workNanos = workNanos;
    }

    public void setFail(boolean fail) {
        this.fail = fail;
    }

    public void setSampleListener(SampleListener listener) {
        this.listener = listener;
    }

    @Override
    public SampleResult sample(Entry e) {
        executions++;
        if (clock != null && workNanos > 0L && clock instanceof MutableNanoClock) {
            ((MutableNanoClock) clock).advanceNanos(workNanos);
        }
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setSuccessful(!fail);
        result.sampleStart();
        result.sampleEnd();
        if (listener != null) {
            listener.sampleOccurred(new SampleEvent(result, "TG"));
        }
        return result;
    }

    public int getExecutions() {
        return executions;
    }

    public void resetCount() {
        executions = 0;
    }
}
