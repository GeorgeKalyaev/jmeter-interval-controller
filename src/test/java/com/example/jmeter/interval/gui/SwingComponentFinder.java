package com.example.jmeter.interval.gui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

/**
 * Locate Swing widgets by tooltip / button text / titled border — no production code changes.
 */
public final class SwingComponentFinder {

    private SwingComponentFinder() {
    }

    public static List<Component> all(Container root) {
        List<Component> out = new ArrayList<Component>();
        collect(root, out);
        return out;
    }

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container) {
                collect((Container) child, out);
            }
        }
    }

    public static JTextField textFieldByTooltip(Container root, String tipContains) {
        for (Component c : all(root)) {
            if (c instanceof JTextField) {
                String tip = ((JTextField) c).getToolTipText();
                if (tip != null && tip.contains(tipContains)) {
                    return (JTextField) c;
                }
            }
        }
        throw new IllegalStateException("No JTextField tip containing: " + tipContains);
    }

    public static JCheckBox checkBoxByText(Container root, String textContains) {
        for (Component c : all(root)) {
            if (c instanceof JCheckBox) {
                String t = ((JCheckBox) c).getText();
                if (t != null && t.contains(textContains)) {
                    return (JCheckBox) c;
                }
            }
        }
        throw new IllegalStateException("No JCheckBox text containing: " + textContains);
    }

    public static JRadioButton radioByText(Container root, String textContains) {
        for (Component c : all(root)) {
            if (c instanceof JRadioButton) {
                String t = ((JRadioButton) c).getText();
                if (t != null && t.contains(textContains)) {
                    return (JRadioButton) c;
                }
            }
        }
        throw new IllegalStateException("No JRadioButton text containing: " + textContains);
    }

    @SuppressWarnings("rawtypes")
    public static JComboBox comboNearLabelTooltip(Container root, String tipContains) {
        // Prefer combos whose tooltip matches; else first unlabeled combo after a matching field
        for (Component c : all(root)) {
            if (c instanceof JComboBox) {
                String tip = ((JComboBox) c).getToolTipText();
                if (tip != null && tip.contains(tipContains)) {
                    return (JComboBox) c;
                }
            }
        }
        throw new IllegalStateException("No JComboBox tip containing: " + tipContains);
    }

    @SuppressWarnings("rawtypes")
    public static List<JComboBox> allCombos(Container root) {
        List<JComboBox> list = new ArrayList<JComboBox>();
        for (Component c : all(root)) {
            if (c instanceof JComboBox) {
                list.add((JComboBox) c);
            }
        }
        return list;
    }

    public static boolean hasTitledBorder(Container root, String title) {
        for (Component c : all(root)) {
            if (c instanceof javax.swing.JComponent) {
                javax.swing.border.Border b = ((javax.swing.JComponent) c).getBorder();
                if (b instanceof TitledBorder) {
                    String t = ((TitledBorder) b).getTitle();
                    if (title.equals(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static AbstractButton buttonByText(Container root, String textContains) {
        for (Component c : all(root)) {
            if (c instanceof AbstractButton) {
                String t = ((AbstractButton) c).getText();
                if (t != null && t.contains(textContains)) {
                    return (AbstractButton) c;
                }
            }
        }
        throw new IllegalStateException("No AbstractButton text containing: " + textContains);
    }
}
