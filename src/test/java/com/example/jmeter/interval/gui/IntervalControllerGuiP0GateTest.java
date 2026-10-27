package com.example.jmeter.interval.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jorphan.collections.HashTree;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.finder.JFileChooserFinder;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JFileChooserFixture;
import org.assertj.swing.fixture.JPopupMenuFixture;
import org.assertj.swing.fixture.JTreeFixture;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.jmeter.interval.IntervalController;
import com.example.jmeter.interval.IntervalControllerGui;
import com.example.jmeter.interval.IntervalStartMode;
import com.example.jmeter.interval.JitterMode;
import com.example.jmeter.interval.TimeUnitOption;
import com.example.jmeter.interval.ExpirationFormat;

/**
 * Release-gate P0 GUI_E2E:
 * 1) Context menu Add → Logic Controller → Interval Controller
 * 2) File → Save Test Plan as / Open via real Swing JFileChooser
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IntervalControllerGuiP0GateTest {

    private static final Logger log = LoggerFactory.getLogger(IntervalControllerGuiP0GateTest.class);
    private static final StringBuilder RESULT = new StringBuilder();

    private static MainFrame mainFrame;
    private static Robot robot;
    private static FrameFixture frame;
    private static File evidence;
    private static File jmxDir;
    private static File savedJmx;

    private static void record(String id, String level, String status, String detail) {
        String line = id + "\t" + level + "\t" + status + "\t" + detail;
        RESULT.append(line).append('\n');
        log.info("P0 {}", line);
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        File gate = new File("verification-1.1.2/release-gate").getAbsoluteFile();
        jmxDir = new File(gate, "jmx");
        Files.createDirectories(jmxDir.toPath());
        Files.createDirectories(new File(gate, "results").toPath());
        System.setProperty("ic.gui.evidence.dir", new File(gate, "gui-evidence").getAbsolutePath());
        evidence = JMeterGuiTestSupport.evidenceDir();
        Files.createDirectories(new File(evidence, "screenshots").toPath());

        mainFrame = JMeterGuiTestSupport.startJMeterGui();
        robot = BasicRobot.robotWithCurrentAwtHierarchy();
        frame = new FrameFixture(robot, mainFrame);
        frame.show();
        record("BOOT", "GUI_E2E", "PASS", mainFrame.getTitle());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try {
            File out = new File(new File(evidence.getParentFile(), "results"), "P0_GUI_RESULT.tsv");
            // evidence may be gui-evidence; parent=release-gate
            File gateResults = new File(evidence.getParentFile(), "results");
            if (!"results".equals(evidence.getParentFile().getName())) {
                gateResults = new File(evidence.getParentFile(), "results");
            }
            Files.createDirectories(gateResults.toPath());
            out = new File(gateResults, "P0_GUI_RESULT.tsv");
            Files.write(out.toPath(), RESULT.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            try {
                if (frame != null) {
                    frame.cleanUp();
                }
            } catch (RuntimeException ignored) {
                // ignore
            }
            JMeterGuiTestSupport.shutdownGui(mainFrame);
        }
    }

    @Test
    public void p01_contextMenuAddIntervalController() throws Exception {
        GuiPackage gp = GuiPackage.getInstance();
        // Clean plan — avoids nested TestPlan nodes that make Save All As write an empty plan
        try {
            frame.menuItemWithPath("File", "New").click();
            dismissOptionalYesNoDialogPreferNo(2000L);
            Thread.sleep(500L);
        } catch (RuntimeException ex) {
            log.warn("File New: {}", ex.toString());
        }

        JMeterTreeModel model = gp.getTreeModel();
        JMeterTreeNode tg = ensureThreadGroup(model);
        selectNode(tg);
        Thread.sleep(300L);
        int before = model.getNodesOfType(IntervalController.class).size();

        Thread.sleep(200L);

        // Authentic JMeter path: selected node's JMeterGUIComponent.createPopupMenu()
        // (AssertJ JTree.showPopupMenu does not use JMeterTreeListener / createPopupMenu).
        File menuDump = new File(evidence, "p0-context-menu-dump.txt");
        boolean addedViaMenu = addViaJMeterCreatePopupMenu(tg, menuDump);
        String detail = "JMeterGUIComponent.createPopupMenu → Add/Logic Controller/Interval Controller"
                + " dump=" + menuDump.getName() + " clicked=" + addedViaMenu;

        if (!addedViaMenu) {
            record("p01_context_menu_add", "GUI_E2E", "FAIL", detail + " before=" + before);
            fail("Context-menu Add did not activate Interval Controller. See " + menuDump.getAbsolutePath());
        }

        JMeterGuiTestSupport.awaitCondition(
                () -> model.getNodesOfType(IntervalController.class).size() > before,
                15_000L,
                "Interval Controller node after context-menu Add");

        List<JMeterTreeNode> ics = model.getNodesOfType(IntervalController.class);
        assertTrue("IC must appear in tree after Add menu", ics.size() > before);
        JMeterTreeNode icNode = ics.get(ics.size() - 1);
        selectNode(icNode);
        Thread.sleep(400L);

        Object gui = gp.getCurrentGui();
        assertTrue("Current GUI must be IntervalControllerGui", gui instanceof IntervalControllerGui);
        IntervalControllerGui icGui = (IntervalControllerGui) gui;
        assertEquals("Interval Controller", icGui.getStaticLabel());
        assertTrue(SwingComponentFinder.hasTitledBorder(icGui, "Basic Settings"));

        JMeterGuiTestSupport.screenshot(mainFrame, "p0-01-context-menu-add");
        record("p01_context_menu_add", "GUI_E2E", "PASS",
                detail + " nodes=" + ics.size() + " gui=" + gui.getClass().getSimpleName());
    }

    @Test
    public void p02_editThenSaveAsAndOpenViaDialogs() throws Exception {
        GuiPackage gp = GuiPackage.getInstance();
        List<JMeterTreeNode> ics = gp.getTreeModel().getNodesOfType(IntervalController.class);
        if (ics == null || ics.isEmpty()) {
            // Self-contained if p01 failed: still require a real context-menu Add attempt first
            p01_contextMenuAddIntervalController();
            ics = gp.getTreeModel().getNodesOfType(IntervalController.class);
        }
        assertFalseEmpty(ics);
        JMeterTreeNode icNode = ics.get(ics.size() - 1);
        selectNode(icNode);
        Thread.sleep(300L);
        IntervalControllerGui gui = (IntervalControllerGui) gp.getCurrentGui();
        assertNotNull(gui);

        // Set distinctive values via Swing widgets (commit to model)
        JMeterGuiTestSupport.onEdtRun(() -> {
            SwingComponentFinder.textFieldByTooltip(gui, "Static number or ${variable}").setText("77");
            SwingComponentFinder.allCombos(gui).get(0).setSelectedItem(TimeUnitOption.SECONDS.getLabel());
            SwingComponentFinder.checkBoxByText(gui, "Dynamic Interval").setSelected(true);
            SwingComponentFinder.checkBoxByText(gui, "Retry on Failure").setSelected(true);
            SwingComponentFinder.textFieldByTooltip(gui, "Delay before a retry").setText("9");
            SwingComponentFinder.textFieldByTooltip(gui, "0 = unlimited").setText("2");
            SwingComponentFinder.textFieldByTooltip(gui, "Random offset").setText("1");
            SwingComponentFinder.checkBoxByText(gui, "Execute on first").setSelected(false);
            SwingComponentFinder.radioByText(gui, "Execution Start").setSelected(true);
            SwingComponentFinder.textFieldByTooltip(gui, "Optional variable with token expiry")
                    .setText("gate_ttl");
            SwingComponentFinder.textFieldByTooltip(gui, "Wall-clock Unix ms when the TTL token was issued")
                    .setText("gate_issued");
            SwingComponentFinder.textFieldByTooltip(gui, "FORCE").setText("gate_force");
            for (javax.swing.JComboBox cb : SwingComponentFinder.allCombos(gui)) {
                for (int i = 0; i < cb.getItemCount(); i++) {
                    if (JitterMode.SYMMETRIC.getLabel().equals(String.valueOf(cb.getItemAt(i)))) {
                        cb.setSelectedItem(JitterMode.SYMMETRIC.getLabel());
                    }
                    if (ExpirationFormat.TTL_SECONDS.getLabel().equals(String.valueOf(cb.getItemAt(i)))) {
                        cb.setSelectedItem(ExpirationFormat.TTL_SECONDS.getLabel());
                    }
                }
            }
            gui.modifyTestElement(gp.getCurrentElement());
        });

        IntervalController modelBefore = (IntervalController) gp.getCurrentElement();
        assertEquals("77", modelBefore.getIntervalExpression());
        assertTrue("tree must contain IC before Save As",
                gp.getTreeModel().getNodesOfType(IntervalController.class).size() >= 1);
        assertTrue("tree must contain ThreadGroup before Save As",
                gp.getTreeModel().getNodesOfType(ThreadGroup.class).size() >= 1);

        savedJmx = new File(jmxDir, "p0-gui-saveas-roundtrip.jmx").getAbsoluteFile();
        if (savedJmx.exists()) {
            Files.delete(savedJmx.toPath());
        }

        // Dump tree + SaveService baseline (proves model has IC; GUI Save As is still required)
        File treeDump = new File(evidence, "p0-tree-before-saveas.txt");
        StringBuilder td = new StringBuilder();
        td.append("IC=").append(gp.getTreeModel().getNodesOfType(IntervalController.class).size())
                .append(" TG=").append(gp.getTreeModel().getNodesOfType(ThreadGroup.class).size())
                .append('\n');
        dumpTree(gp.getTreeModel().getRoot() instanceof JMeterTreeNode
                ? (JMeterTreeNode) gp.getTreeModel().getRoot()
                : null, 0, td);
        Files.write(treeDump.toPath(), td.toString().getBytes(StandardCharsets.UTF_8));

        // Select root TestPlan (not a nested duplicate) before Save Test Plan as
        JMeterTreeNode rootPlan = (JMeterTreeNode) gp.getTreeModel().getRoot();
        selectNode(rootPlan);
        Thread.sleep(200L);

        frame.menuItemWithPath("File", "Save Test Plan as").click();
        JFileChooserFixture chooser = JFileChooserFinder.findFileChooser().withTimeout(20, TimeUnit.SECONDS)
                .using(robot);
        chooser.setCurrentDirectory(savedJmx.getParentFile());
        // Full path is more reliable than name-only on Windows JFileChooser
        chooser.fileNameTextBox().setText(savedJmx.getAbsolutePath());
        chooser.approve();
        dismissOptionalYesDialog(2500L);
        JMeterGuiTestSupport.awaitCondition(
                () -> savedJmx.isFile() && savedJmx.length() > 200,
                15_000L,
                "Save As wrote non-trivial file");

        String xmlAfterSave = new String(Files.readAllBytes(savedJmx.toPath()), StandardCharsets.UTF_8);
        assertTrue("Save As JMX must contain IntervalController, got:\n" + xmlAfterSave,
                xmlAfterSave.contains("IntervalController") || xmlAfterSave.contains("Interval Controller"));
        assertTrue("Save As JMX must contain intervalValue 77",
                xmlAfterSave.contains(">77<") || xmlAfterSave.contains("\"77\""));
        JMeterGuiTestSupport.screenshot(mainFrame, "p0-02-after-saveas");
        record("p02_saveas_dialog", "GUI_E2E", "PASS",
                "size=" + savedJmx.length() + " path=" + savedJmx.getName());

        // Mutate live GUI so Open must restore from disk
        List<JMeterTreeNode> icsNow = gp.getTreeModel().getNodesOfType(IntervalController.class);
        selectNode(icsNow.get(0));
        JMeterGuiTestSupport.onEdtRun(() -> {
            IntervalControllerGui g = (IntervalControllerGui) GuiPackage.getInstance().getCurrentGui();
            SwingComponentFinder.textFieldByTooltip(g, "Static number or ${variable}").setText("1");
            g.modifyTestElement(GuiPackage.getInstance().getCurrentElement());
        });

        // File → New (discard) then File → Open — clean Open path
        try {
            frame.menuItemWithPath("File", "New").click();
            dismissOptionalYesNoDialogPreferNo(2500L);
            Thread.sleep(500L);
        } catch (RuntimeException ignored) {
            // some locales / builds use different New label — continue to Open
        }

        frame.menuItemWithPath("File", "Open").click();
        dismissOptionalYesNoDialogPreferNo(2500L);
        JFileChooserFixture openChooser = JFileChooserFinder.findFileChooser().withTimeout(20, TimeUnit.SECONDS)
                .using(robot);
        openChooser.setCurrentDirectory(savedJmx.getParentFile());
        openChooser.selectFile(savedJmx);
        openChooser.approve();
        Thread.sleep(1500L);
        dismissOptionalYesDialog(1500L);

        JMeterGuiTestSupport.awaitCondition(
                () -> !GuiPackage.getInstance().getTreeModel().getNodesOfType(IntervalController.class).isEmpty(),
                20_000L,
                "IC present after Open");
        List<JMeterTreeNode> after = GuiPackage.getInstance().getTreeModel().getNodesOfType(IntervalController.class);
        selectNode(after.get(0));
        Thread.sleep(500L);

        IntervalController restored = (IntervalController) GuiPackage.getInstance().getCurrentElement();
        assertNotNull(restored);
        assertEquals("77", restored.getIntervalExpression());
        assertEquals(TimeUnitOption.SECONDS, restored.getTimeUnit());
        assertTrue(restored.isDynamicIntervalEnabled());
        assertTrue(restored.isRetryOnFailure());
        assertEquals("9", restored.getRetryDelayExpression());
        assertEquals(2L, restored.getMaxRetries());
        assertEquals("1", restored.getJitterExpression());
        assertEquals(JitterMode.SYMMETRIC, restored.getJitterMode());
        assertEquals(false, restored.isExecuteOnFirstIteration());
        assertEquals(IntervalStartMode.EXECUTION_START, restored.getIntervalStartMode());
        assertEquals("gate_ttl", restored.getTokenExpirationVar());
        assertEquals("gate_issued", restored.getTokenIssuedAtVar());
        assertEquals(ExpirationFormat.TTL_SECONDS, restored.getExpirationFormat());
        assertEquals("gate_force", restored.getForceRefreshVar());

        assertTrue(GuiPackage.getInstance().getCurrentGui() instanceof IntervalControllerGui);

        HashTree loaded = SaveService.loadTree(savedJmx);
        IntervalController fromFile = findIc(loaded);
        assertNotNull(fromFile);
        assertEquals("77", fromFile.getIntervalExpression());

        JMeterGuiTestSupport.screenshot(mainFrame, "p0-03-after-open");
        record("p02_saveas_open", "GUI_E2E", "PASS",
                "file=" + savedJmx.getName() + " size=" + savedJmx.length());
    }

    // ---- helpers ----

    private static void assertFalseEmpty(List<?> list) {
        assertNotNull(list);
        assertTrue("expected non-empty", !list.isEmpty());
    }

    private static String pathLabel(JMeterTreeNode node) {
        Object[] path = node.getPath();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            Object n = path[i];
            if (n instanceof JMeterTreeNode) {
                sb.append(((JMeterTreeNode) n).getName());
            } else {
                sb.append(n);
            }
        }
        // AssertJ JTreeFixture often wants path without root depending on model — try leaf-relative
        return sb.toString();
    }

    private static int countNestedTestPlans(JMeterTreeNode root) {
        int n = 0;
        for (int i = 0; i < root.getChildCount(); i++) {
            JMeterTreeNode ch = (JMeterTreeNode) root.getChildAt(i);
            if (ch.getTestElement() instanceof TestPlan) {
                n++;
            }
        }
        return n;
    }

    private static JMeterTreeNode ensureThreadGroup(JMeterTreeModel model) throws Exception {
        List<JMeterTreeNode> tgs = model.getNodesOfType(ThreadGroup.class);
        if (tgs != null && !tgs.isEmpty()) {
            return tgs.get(0);
        }
        final JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        StringBuilder bootDump = new StringBuilder();
        bootDump.append("rootClass=").append(root.getTestElement().getClass().getName())
                .append(" name=").append(root.getName())
                .append(" children=").append(root.getChildCount()).append('\n');
        for (int i = 0; i < root.getChildCount(); i++) {
            JMeterTreeNode ch = (JMeterTreeNode) root.getChildAt(i);
            bootDump.append("  child[").append(i).append("]=")
                    .append(ch.getTestElement().getClass().getName())
                    .append(" name=").append(ch.getName()).append('\n');
        }
        try {
            Files.write(new File(evidence, "p0-tree-after-new.txt").toPath(),
                    bootDump.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception io) {
            log.warn("dump failed: {}", io.toString());
        }
        // Prefer adding under the child TestPlan if JMeter uses a proxy/root wrapper
        final JMeterTreeNode planParent;
        if (root.getChildCount() == 1
                && ((JMeterTreeNode) root.getChildAt(0)).getTestElement() instanceof TestPlan) {
            planParent = (JMeterTreeNode) root.getChildAt(0);
        } else if (root.getTestElement() instanceof TestPlan) {
            planParent = root;
        } else {
            throw new IllegalStateException("Cannot locate TestPlan node:\n" + bootDump);
        }

        ThreadGroup tg = new ThreadGroup();
        tg.setName("TG-Gate");
        tg.setProperty(org.apache.jmeter.testelement.TestElement.GUI_CLASS,
                "org.apache.jmeter.threads.gui.ThreadGroupGui");
        tg.setProperty(org.apache.jmeter.testelement.TestElement.TEST_CLASS, ThreadGroup.class.getName());
        org.apache.jmeter.control.LoopController lc = new org.apache.jmeter.control.LoopController();
        lc.setProperty(org.apache.jmeter.testelement.TestElement.GUI_CLASS,
                "org.apache.jmeter.control.gui.LoopControlPanel");
        lc.setProperty(org.apache.jmeter.testelement.TestElement.TEST_CLASS, lc.getClass().getName());
        lc.setLoops(1);
        lc.setFirst(true);
        tg.setSamplerController(lc);
        tg.setNumThreads(1);
        JMeterTreeNode added = JMeterGuiTestSupport.onEdt(() -> model.addComponent(tg, planParent));
        return added;
    }

    private static void selectNode(JMeterTreeNode node) throws Exception {
        GuiPackage gp = GuiPackage.getInstance();
        JMeterGuiTestSupport.onEdtRun(() -> {
            JTree tree = gp.getMainFrame().getTree();
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        });
        Thread.sleep(250L);
        JMeterGuiTestSupport.onEdtRun(() -> {
            try {
                GuiPackage.class.getMethod("updateCurrentNode").invoke(gp);
            } catch (ReflectiveOperationException ignored) {
                // ok
            }
        });
    }

    /**
     * Same mechanism as JMeterTreeListener.displayPopUp: gui.createPopupMenu() then show on tree.
     * Then activate Add → Logic Controller → Interval Controller via doClick (real JMenuItem).
     */
    private boolean addViaJMeterCreatePopupMenu(JMeterTreeNode tg, File dumpFile) throws Exception {
        GuiPackage gp = GuiPackage.getInstance();
        selectNode(tg);
        JTree tree = gp.getMainFrame().getTree();

        final StringBuilder dump = new StringBuilder();
        Boolean clicked = JMeterGuiTestSupport.onEdt(() -> {
            org.apache.jmeter.gui.JMeterGUIComponent guiComp = gp.getCurrentGui();
            if (guiComp == null) {
                dump.append("currentGui=null\n");
                return Boolean.FALSE;
            }
            dump.append("currentGui=").append(guiComp.getClass().getName()).append('\n');
            JPopupMenu popup = guiComp.createPopupMenu();
            if (popup == null) {
                dump.append("createPopupMenu=null\n");
                return Boolean.FALSE;
            }
            dumpMenu(popup, 0, dump);
            TreePath path = new TreePath(tg.getPath());
            java.awt.Rectangle bounds = tree.getPathBounds(path);
            if (bounds == null) {
                dump.append("pathBounds=null\n");
                return Boolean.FALSE;
            }
            popup.show(tree, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
            boolean ok = clickMenuPath(popup, "Add", "Logic Controller", "Interval Controller");
            dump.append("clickOk=").append(ok).append('\n');
            return Boolean.valueOf(ok);
        });
        Files.write(dumpFile.toPath(), dump.toString().getBytes(StandardCharsets.UTF_8));
        Thread.sleep(600L);
        return Boolean.TRUE.equals(clicked);
    }

    private static void dumpMenu(MenuElement el, int depth, StringBuilder dump) {
        Component c = el.getComponent();
        String pad = repeat(' ', depth * 2);
        if (c instanceof JMenuItem) {
            dump.append(pad).append(((JMenuItem) c).getText())
                    .append(" [name=").append(c.getName()).append("]\n");
        } else if (c != null) {
            dump.append(pad).append(c.getClass().getSimpleName()).append('\n');
        }
        for (MenuElement child : el.getSubElements()) {
            dumpMenu(child, depth + 1, dump);
        }
    }

    private static String repeat(char ch, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    private static JPopupMenu findShowingPopup() {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (!w.isShowing()) {
                continue;
            }
            JPopupMenu found = findPopup(w);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static JPopupMenu findPopup(Container c) {
        if (c instanceof JPopupMenu && c.isShowing()) {
            return (JPopupMenu) c;
        }
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                JPopupMenu p = findPopup((Container) child);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean clickMenuPath(MenuElement root, String... labels) {
        MenuElement current = root;
        for (int i = 0; i < labels.length; i++) {
            String want = labels[i];
            MenuElement next = findMenuChild(current, want);
            if (next == null) {
                return false;
            }
            Component comp = next.getComponent();
            if (i == labels.length - 1) {
                ((JMenuItem) comp).doClick();
                return true;
            }
            if (comp instanceof javax.swing.JMenu) {
                javax.swing.JMenu menu = (javax.swing.JMenu) comp;
                menu.setPopupMenuVisible(true);
                current = menu.getPopupMenu();
            } else {
                current = next;
            }
        }
        return false;
    }

    private static MenuElement findMenuChild(MenuElement parent, String want) {
        for (MenuElement child : parent.getSubElements()) {
            Component c = child.getComponent();
            if (c instanceof JMenuItem) {
                String text = ((JMenuItem) c).getText();
                if (text != null && text.replace("&", "").equalsIgnoreCase(want)) {
                    return child;
                }
            }
            // Some LAFs wrap submenu items; also scan nested popup shells
            if (c instanceof javax.swing.JMenu) {
                MenuElement deeper = findMenuChild(((javax.swing.JMenu) c).getPopupMenu(), want);
                if (deeper != null && want.equalsIgnoreCase(
                        String.valueOf(((JMenuItem) deeper.getComponent()).getText()))) {
                    // only return if exact leaf match requested at this level — skip
                }
            }
        }
        return null;
    }

    private void dismissOptionalYesDialog(long waitMs) throws Exception {
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (!(w instanceof javax.swing.JDialog) || !w.isShowing()) {
                    continue;
                }
                javax.swing.JDialog d = (javax.swing.JDialog) w;
                javax.swing.JButton yes = findButton(d, "Yes", "OK", "Да", "Overwrite", "Replace");
                if (yes != null) {
                    robot.click(yes);
                    Thread.sleep(300L);
                    return;
                }
            }
            Thread.sleep(100L);
        }
    }

    private void dismissOptionalYesNoDialogPreferNo(long waitMs) throws Exception {
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (!(w instanceof javax.swing.JDialog) || !w.isShowing()) {
                    continue;
                }
                javax.swing.JDialog d = (javax.swing.JDialog) w;
                // Unsaved changes: prefer No/Don't save so Open chooser appears
                javax.swing.JButton no = findButton(d, "No", "Нет", "Don't Save", "Discard");
                if (no != null) {
                    robot.click(no);
                    Thread.sleep(300L);
                    return;
                }
            }
            Thread.sleep(100L);
        }
    }

    private static javax.swing.JButton findButton(Container root, String... texts) {
        for (Component c : SwingComponentFinder.all(root)) {
            if (c instanceof javax.swing.JButton) {
                String t = ((javax.swing.JButton) c).getText();
                if (t == null) {
                    continue;
                }
                for (String want : texts) {
                    if (t.equalsIgnoreCase(want) || t.contains(want)) {
                        return (javax.swing.JButton) c;
                    }
                }
            }
        }
        return null;
    }

    private static void dumpTree(JMeterTreeNode node, int depth, StringBuilder out) {
        if (node == null) {
            return;
        }
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append(node.getName()).append(" (").append(node.getTestElement().getClass().getSimpleName())
                .append(")\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            dumpTree((JMeterTreeNode) node.getChildAt(i), depth + 1, out);
        }
    }

    private static IntervalController findIc(HashTree tree) {
        final IntervalController[] ref = new IntervalController[1];
        tree.traverse(new org.apache.jorphan.collections.HashTreeTraverser() {
            @Override
            public void addNode(Object node, HashTree subTree) {
                if (node instanceof IntervalController) {
                    ref[0] = (IntervalController) node;
                }
            }

            @Override
            public void processPath() {
            }

            @Override
            public void subtractNode() {
            }
        });
        return ref[0];
    }
}
