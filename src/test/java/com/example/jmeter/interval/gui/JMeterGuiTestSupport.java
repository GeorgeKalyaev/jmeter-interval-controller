package com.example.jmeter.interval.gui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

import org.apache.jmeter.JMeterGuiLauncher;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process JMeter MainFrame bootstrap for AssertJ / EDT-based GUI automation.
 * Does not rely on external desktop automation or OS focus tricks.
 */
public final class JMeterGuiTestSupport {

    private static final Logger log = LoggerFactory.getLogger(JMeterGuiTestSupport.class);
    private static volatile boolean started;

    private JMeterGuiTestSupport() {
    }

    public static File evidenceDir() {
        String override = System.getProperty("ic.gui.evidence.dir");
        if (override != null && !override.isEmpty()) {
            return new File(override);
        }
        return new File("verification-1.1.2/gui-automation");
    }

    public static synchronized MainFrame startJMeterGui() throws Exception {
        if (started) {
            MainFrame existing = GuiPackage.getInstance() != null
                    ? GuiPackage.getInstance().getMainFrame() : null;
            if (existing != null) {
                return existing;
            }
        }
        String home = System.getProperty("jmeter.home");
        if (home == null || home.isEmpty()) {
            home = System.getenv("JMETER_HOME");
        }
        if (home == null || home.isEmpty()) {
            throw new IllegalStateException("jmeter.home / JMETER_HOME must be set");
        }
        File homeDir = new File(home).getAbsoluteFile();
        if (!homeDir.isDirectory()) {
            throw new IllegalStateException("Invalid jmeter.home: " + homeDir);
        }
        File props = new File(homeDir, "bin/jmeter.properties");
        if (!props.isFile()) {
            throw new IllegalStateException("Missing " + props);
        }

        System.setProperty("jmeter.home", homeDir.getAbsolutePath());
        // Avoid some LAF / HiDPI surprises in automation
        System.setProperty("java.awt.headless", "false");

        log.info("Bootstrapping JMeter GUI home={}", homeDir);
        JMeterUtils.setJMeterHome(homeDir.getAbsolutePath());
        JMeterUtils.loadJMeterProperties(props.getAbsolutePath());
        JMeterUtils.initLocale();

        File evidence = evidenceDir();
        Files.createDirectories(evidence.toPath());

        long t0 = System.currentTimeMillis();
        JMeterGuiLauncher.startGui(null);
        MainFrame frame = waitForMainFrame(120_000L);
        started = true;
        log.info("MainFrame ready in {} ms title='{}'",
                System.currentTimeMillis() - t0, frame.getTitle());
        return frame;
    }

    public static MainFrame waitForMainFrame(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            GuiPackage gp = GuiPackage.getInstance();
            if (gp != null) {
                MainFrame mf = gp.getMainFrame();
                if (mf != null && mf.isDisplayable()) {
                    // Wait until menu bar exists (UI finished constructing)
                    JMenuBar bar = onEdt(mf::getJMenuBar);
                    if (bar != null && bar.getMenuCount() > 0) {
                        return mf;
                    }
                }
            }
            Thread.sleep(200L);
        }
        throw new IllegalStateException("Timed out waiting for JMeter MainFrame");
    }

    public static <T> T onEdt(Callable<T> call) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return call.call();
        }
        AtomicReference<T> ref = new AtomicReference<T>();
        AtomicReference<Exception> err = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                ref.set(call.call());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) {
            throw err.get();
        }
        return ref.get();
    }

    public static void onEdtRun(Runnable r) throws Exception {
        onEdt(() -> {
            r.run();
            return null;
        });
    }

    public static JMenu findMenu(MainFrame frame, String name) throws Exception {
        return onEdt(() -> {
            JMenuBar bar = frame.getJMenuBar();
            if (bar == null) {
                return null;
            }
            for (int i = 0; i < bar.getMenuCount(); i++) {
                JMenu m = bar.getMenu(i);
                if (m == null) {
                    continue;
                }
                String text = m.getText();
                if (text != null && text.replace("&", "").equalsIgnoreCase(name)) {
                    return m;
                }
            }
            return null;
        });
    }

    public static File screenshot(Component component, String name) throws Exception {
        File dir = new File(evidenceDir(), "screenshots");
        Files.createDirectories(dir.toPath());
        File out = new File(dir, name + ".png");
        Rectangle bounds = onEdt(() -> {
            if (!component.isShowing()) {
                // Still capture bounds if realized
                return component.getBounds();
            }
            return new Rectangle(component.getLocationOnScreen(), component.getSize());
        });
        if (bounds.width <= 0 || bounds.height <= 0) {
            // Fallback: whole frame
            JFrame f = (JFrame) SwingUtilities.getWindowAncestor(component);
            if (f == null && component instanceof JFrame) {
                f = (JFrame) component;
            }
            if (f != null) {
                JFrame frame = f;
                bounds = onEdt(() -> new Rectangle(frame.getLocationOnScreen(), frame.getSize()));
            }
        }
        BufferedImage img = new Robot().createScreenCapture(bounds);
        ImageIO.write(img, "png", out);
        log.info("Screenshot written {}", out.getAbsolutePath());
        return out;
    }

    public static void shutdownGui(MainFrame frame) throws Exception {
        if (frame == null) {
            return;
        }
        onEdtRun(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
        // Give Swing a moment to tear down; do not System.exit (would kill Surefire)
        Thread.sleep(500L);
        started = false;
        log.info("MainFrame disposed");
    }

    public static void awaitCondition(Callable<Boolean> condition, long timeoutMs, String label)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new IllegalStateException("Timeout waiting for: " + label);
    }

    public static void logEnv() {
        log.info("java.version={} jmeter.home={} evidence={}",
                System.getProperty("java.version"),
                System.getProperty("jmeter.home"),
                evidenceDir().getAbsolutePath());
    }
}
