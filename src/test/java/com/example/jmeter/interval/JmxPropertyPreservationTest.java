package com.example.jmeter.interval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Non-GUI proof that committed JMX retains critical Interval Controller property literals
 * (including ${...} dynamic expressions). Does not replace interactive GUI testing.
 */
public class JmxPropertyPreservationTest {

    @Test
    public void guiRoundtripTemplatePreservesDynamicAndAdvancedProps() throws Exception {
        File jmx = new File("integration-tests/jmx/59-gui-roundtrip-template.jmx");
        assertTrue("missing " + jmx.getAbsolutePath(), jmx.isFile());
        String xml = new String(Files.readAllBytes(jmx.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("${refresh_ms}"));
        assertTrue(xml.contains("IntervalController.dynamicInterval\">true"));
        assertTrue(xml.contains("IntervalController.retryOnFailure\">true"));
        assertTrue(xml.contains("IntervalController.jitterValue\">10"));
        assertTrue(xml.contains("TTL_SECONDS"));
        assertTrue(xml.contains("token_ttl"));
        assertTrue(xml.contains("interval_controller_force_refresh"));
        assertEquals("guiclass must point at plugin GUI", true,
                xml.contains("guiclass=\"com.example.jmeter.interval.IntervalControllerGui\""));
    }
}
