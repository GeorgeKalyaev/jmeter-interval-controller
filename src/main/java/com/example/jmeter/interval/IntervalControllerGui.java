package com.example.jmeter.interval;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import org.apache.jmeter.control.gui.AbstractControllerGui;
import org.apache.jmeter.testelement.TestElement;

/**
 * Swing GUI for Interval Controller 1.1.2.
 */
public class IntervalControllerGui extends AbstractControllerGui {

    private static final long serialVersionUID = 3L;

    // Basic
    private JTextField intervalField;
    private JComboBox<String> timeUnitCombo;
    private JCheckBox executeOnFirstCheck;
    private JRadioButton startModeRadio;
    private JRadioButton completionModeRadio;

    // Retry
    private JCheckBox retryOnFailureCheck;
    private JTextField retryDelayField;
    private JComboBox<String> retryDelayUnitCombo;
    private JTextField maxRetriesField;

    // Advanced
    private JTextField jitterField;
    private JComboBox<String> jitterUnitCombo;
    private JComboBox<String> jitterModeCombo;
    private JCheckBox dynamicIntervalCheck;
    private JTextField tokenExpirationVarField;
    private JTextField tokenIssuedAtVarField;
    private JComboBox<String> expirationFormatCombo;
    private JTextField refreshBeforeField;
    private JComboBox<String> refreshBeforeUnitCombo;
    private JTextField forceRefreshVarField;

    public IntervalControllerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(4, 0, 4, 0);

        gbc.gridy = 0;
        body.add(createBasicPanel(), gbc);
        gbc.gridy = 1;
        body.add(createRetryPanel(), gbc);
        gbc.gridy = 2;
        body.add(createAdvancedPanel(), gbc);

        add(new JScrollPane(body), BorderLayout.CENTER);
    }

    private JPanel createBasicPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Basic Settings"));
        GridBagConstraints gbc = newGrid();

        intervalField = new JTextField("10", 12);
        intervalField.setToolTipText(
                "Static number or ${variable} when Dynamic Interval is enabled. Evaluated on each new entry.");
        timeUnitCombo = unitCombo(TimeUnitOption.MINUTES);
        timeUnitCombo.setToolTipText("Unit for Execution Interval.");

        executeOnFirstCheck = new JCheckBox("Execute on first iteration", true);
        executeOnFirstCheck.setToolTipText(
                "If unchecked, first entries are skipped until the interval elapses from first sight.");

        startModeRadio = new JRadioButton(IntervalStartMode.EXECUTION_START.getLabel(), false);
        startModeRadio.setToolTipText("Next window = start time + interval (+ jitter).");
        completionModeRadio = new JRadioButton(IntervalStartMode.EXECUTION_COMPLETION.getLabel(), true);
        completionModeRadio.setToolTipText("Next window = completion time + interval (+ jitter).");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(startModeRadio);
        modeGroup.add(completionModeRadio);

        int row = 0;
        row = addRow(panel, gbc, row, "Execution Interval:", intervalField, timeUnitCombo);
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 3;
        panel.add(executeOnFirstCheck, gbc);
        gbc.gridy = row++;
        panel.add(new JLabel("Interval starts from:"), gbc);
        gbc.gridy = row++;
        panel.add(startModeRadio, gbc);
        gbc.gridy = row;
        panel.add(completionModeRadio, gbc);
        return panel;
    }

    private JPanel createRetryPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Retry Settings"));
        GridBagConstraints gbc = newGrid();

        retryOnFailureCheck = new JCheckBox("Retry on Failure", false);
        retryOnFailureCheck.setToolTipText(
                "On nested failure, allow re-entry after Retry Delay instead of the full Execution Interval.");
        retryDelayField = new JTextField("5", 8);
        retryDelayField.setToolTipText("Delay before a retry entry is allowed (no thread sleep).");
        retryDelayUnitCombo = unitCombo(TimeUnitOption.SECONDS);
        maxRetriesField = new JTextField("0", 8);
        maxRetriesField.setToolTipText("0 = unlimited retries. When exhausted, fall back to normal interval.");

        int row = 0;
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 3;
        panel.add(retryOnFailureCheck, gbc);
        gbc.gridwidth = 1;
        row = addRow(panel, gbc, row, "Retry Delay:", retryDelayField, retryDelayUnitCombo);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Maximum Retries:"), gbc);
        gbc.gridx = 1;
        panel.add(maxRetriesField, gbc);
        return panel;
    }

    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Advanced Settings"));
        GridBagConstraints gbc = newGrid();

        jitterField = new JTextField("0", 8);
        jitterField.setToolTipText("Random offset applied once per new interval window (per thread).");
        jitterUnitCombo = unitCombo(TimeUnitOption.SECONDS);
        jitterModeCombo = new JComboBox<>(Arrays.stream(JitterMode.values())
                .map(JitterMode::getLabel).toArray(String[]::new));
        jitterModeCombo.setToolTipText("Positive: 0..+N. Symmetric: −N..+N (clamped so wait ≥ 0).");

        dynamicIntervalCheck = new JCheckBox("Dynamic Interval (resolve ${vars} / functions)", false);
        dynamicIntervalCheck.setToolTipText(
                "When enabled, Execution Interval is expanded via JMeter variables/functions on each entry.");

        tokenExpirationVarField = new JTextField("", 16);
        tokenExpirationVarField.setToolTipText(
                "Optional variable with token expiry. Empty = disabled. Uses wall-clock, not nanoTime. "
                        + "For TTL Seconds: lifetime in seconds; issued-at is bound after successful refresh.");
        tokenIssuedAtVarField = new JTextField("", 16);
        tokenIssuedAtVarField.setToolTipText(
                "Optional. Wall-clock Unix ms when the TTL token was issued. "
                        + "Empty → default companion name <tokenVar>__issued_at_ms (auto-written on successful refresh).");
        expirationFormatCombo = new JComboBox<>(Arrays.stream(ExpirationFormat.values())
                .map(ExpirationFormat::getLabel).toArray(String[]::new));
        refreshBeforeField = new JTextField("30", 8);
        refreshBeforeField.setToolTipText("Allow refresh when within this window before expiration.");
        refreshBeforeUnitCombo = unitCombo(TimeUnitOption.SECONDS);

        forceRefreshVarField = new JTextField("interval_controller_force_refresh", 20);
        forceRefreshVarField.setToolTipText(
                "If this variable is true/1/yes, next entry executes (FORCE) and the variable is cleared. "
                        + "Scoped override: <name>__<instanceId>.");

        int row = 0;
        row = addRow(panel, gbc, row, "Random Jitter:", jitterField, jitterUnitCombo);
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Jitter Mode:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(jitterModeCombo, gbc);
        row++;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = row++;
        panel.add(dynamicIntervalCheck, gbc);
        gbc.gridwidth = 1;
        row = addRow(panel, gbc, row, "Token Expiration Variable:", tokenExpirationVarField, null);
        row = addRow(panel, gbc, row, "Token Issued-At Variable:", tokenIssuedAtVarField, null);
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel("Expiration Format:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(expirationFormatCombo, gbc);
        row++;
        gbc.gridwidth = 1;
        row = addRow(panel, gbc, row, "Refresh Before Expiration:", refreshBeforeField, refreshBeforeUnitCombo);
        addRow(panel, gbc, row, "Force Refresh Variable:", forceRefreshVarField, null);
        return panel;
    }

    private static GridBagConstraints newGrid() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private static JComboBox<String> unitCombo(TimeUnitOption selected) {
        JComboBox<String> combo = new JComboBox<>(Arrays.stream(TimeUnitOption.values())
                .map(TimeUnitOption::getLabel).toArray(String[]::new));
        combo.setSelectedItem(selected.getLabel());
        return combo;
    }

    private static int addRow(JPanel panel, GridBagConstraints gbc, int row,
                              String label, JTextField field, JComboBox<String> unit) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        if (unit != null) {
            gbc.gridx = 2;
            gbc.weightx = 0;
            panel.add(unit, gbc);
        }
        return row + 1;
    }

    @Override
    public String getLabelResource() {
        return null;
    }

    @Override
    public String getStaticLabel() {
        return "Interval Controller";
    }

    @Override
    public TestElement createTestElement() {
        IntervalController controller = new IntervalController();
        modifyTestElement(controller);
        return controller;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        if (!(element instanceof IntervalController)) {
            return;
        }
        IntervalController c = (IntervalController) element;
        c.setIntervalExpression(intervalField.getText());
        c.setTimeUnit(TimeUnitOption.fromLabel(String.valueOf(timeUnitCombo.getSelectedItem())));
        c.setExecuteOnFirstIteration(executeOnFirstCheck.isSelected());
        c.setIntervalStartMode(completionModeRadio.isSelected()
                ? IntervalStartMode.EXECUTION_COMPLETION
                : IntervalStartMode.EXECUTION_START);

        c.setRetryOnFailure(retryOnFailureCheck.isSelected());
        c.setRetryDelayExpression(retryDelayField.getText());
        c.setRetryDelayUnit(TimeUnitOption.fromLabel(String.valueOf(retryDelayUnitCombo.getSelectedItem())));
        c.setMaxRetries(parseLongSafe(maxRetriesField.getText(), 0L));

        c.setJitterExpression(jitterField.getText());
        c.setJitterUnit(TimeUnitOption.fromLabel(String.valueOf(jitterUnitCombo.getSelectedItem())));
        c.setJitterMode(JitterMode.fromLabel(String.valueOf(jitterModeCombo.getSelectedItem())));
        c.setDynamicIntervalEnabled(dynamicIntervalCheck.isSelected());
        c.setTokenExpirationVar(tokenExpirationVarField.getText());
        c.setTokenIssuedAtVar(tokenIssuedAtVarField.getText());
        c.setExpirationFormat(ExpirationFormat.fromLabel(String.valueOf(expirationFormatCombo.getSelectedItem())));
        c.setRefreshBeforeExpression(refreshBeforeField.getText());
        c.setRefreshBeforeUnit(TimeUnitOption.fromLabel(String.valueOf(refreshBeforeUnitCombo.getSelectedItem())));
        c.setForceRefreshVar(forceRefreshVarField.getText());
        c.ensureInstanceId();
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (!(element instanceof IntervalController)) {
            return;
        }
        IntervalController c = (IntervalController) element;
        intervalField.setText(c.getIntervalExpression());
        timeUnitCombo.setSelectedItem(c.getTimeUnit().getLabel());
        executeOnFirstCheck.setSelected(c.isExecuteOnFirstIteration());
        if (c.getIntervalStartMode() == IntervalStartMode.EXECUTION_START) {
            startModeRadio.setSelected(true);
        } else {
            completionModeRadio.setSelected(true);
        }

        retryOnFailureCheck.setSelected(c.isRetryOnFailure());
        retryDelayField.setText(c.getRetryDelayExpression());
        retryDelayUnitCombo.setSelectedItem(c.getRetryDelayUnit().getLabel());
        maxRetriesField.setText(Long.toString(c.getMaxRetries()));

        jitterField.setText(c.getJitterExpression());
        jitterUnitCombo.setSelectedItem(c.getJitterUnit().getLabel());
        jitterModeCombo.setSelectedItem(c.getJitterMode().getLabel());
        dynamicIntervalCheck.setSelected(c.isDynamicIntervalEnabled());
        tokenExpirationVarField.setText(c.getTokenExpirationVar());
        tokenIssuedAtVarField.setText(c.getTokenIssuedAtVar());
        expirationFormatCombo.setSelectedItem(c.getExpirationFormat().getLabel());
        refreshBeforeField.setText(c.getRefreshBeforeExpression());
        refreshBeforeUnitCombo.setSelectedItem(c.getRefreshBeforeUnit().getLabel());
        forceRefreshVarField.setText(c.getForceRefreshVar());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        intervalField.setText("10");
        timeUnitCombo.setSelectedItem(TimeUnitOption.MINUTES.getLabel());
        executeOnFirstCheck.setSelected(true);
        completionModeRadio.setSelected(true);
        retryOnFailureCheck.setSelected(false);
        retryDelayField.setText("5");
        retryDelayUnitCombo.setSelectedItem(TimeUnitOption.SECONDS.getLabel());
        maxRetriesField.setText("0");
        jitterField.setText("0");
        jitterUnitCombo.setSelectedItem(TimeUnitOption.SECONDS.getLabel());
        jitterModeCombo.setSelectedItem(JitterMode.POSITIVE.getLabel());
        dynamicIntervalCheck.setSelected(false);
        tokenExpirationVarField.setText("");
        tokenIssuedAtVarField.setText("");
        expirationFormatCombo.setSelectedItem(ExpirationFormat.UNIX_MILLISECONDS.getLabel());
        refreshBeforeField.setText("30");
        refreshBeforeUnitCombo.setSelectedItem(TimeUnitOption.SECONDS.getLabel());
        forceRefreshVarField.setText("interval_controller_force_refresh");
    }

    private static long parseLongSafe(String raw, long defaultValue) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
