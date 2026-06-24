package com.photomanager.ui;

import com.photomanager.db.DatabaseManager;
import com.photomanager.model.Project;
import com.photomanager.model.Project.Status;
import com.photomanager.service.ScheduleSettings;
import com.photomanager.util.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 工期排单面板
 * 展示所有项目的 DDL / 状态，并提供快速操作
 */
public class SchedulePanel extends JPanel {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 状态对应的前景色（显示在色块上） */
    private static final Color CLR_URGENT  = new Color(220, 50,  50);
    private static final Color CLR_PENDING = new Color(220, 160, 30);
    private static final Color CLR_IDLE    = new Color(50,  120, 220);
    private static final Color CLR_OTHER   = new Color(160, 160, 160);
    private static final Color CLR_DONE    = new Color(50,  170, 80);

    private final DatabaseManager db;
    private JTable table;
    private DefaultTableModel tableModel;

    // 列索引常量
    private static final int COL_NAME      = 0;
    private static final int COL_DEADLINE  = 1;
    private static final int COL_DAYS_LEFT = 2;
    private static final int COL_STATUS    = 3;
    private static final int COL_ACTION    = 4;

    private List<Project> projects;

    public SchedulePanel(DatabaseManager db) {
        this.db = db;
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(6, 6, 6, 6));
        initUI();
    }

    private void initUI() {
        // ---- 顶部工具栏 ----
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        JButton refreshBtn = new JButton(I18n.get("schedule.refresh"));
        refreshBtn.addActionListener(e -> refresh());
        toolbar.add(refreshBtn);

        JButton settingsBtn = new JButton(I18n.get("schedule.settings_btn"));
        settingsBtn.addActionListener(e -> openSettingsDialog());
        toolbar.add(settingsBtn);

        // 图例
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(makeLegend(CLR_URGENT,  I18n.get("schedule.legend.urgent")));
        toolbar.add(makeLegend(CLR_PENDING, I18n.get("schedule.legend.pending")));
        toolbar.add(makeLegend(CLR_IDLE,    I18n.get("schedule.legend.idle")));
        toolbar.add(makeLegend(CLR_OTHER,   I18n.get("schedule.legend.other")));
        toolbar.add(makeLegend(CLR_DONE,    I18n.get("schedule.legend.done")));

        add(toolbar, BorderLayout.NORTH);

        // ---- 表格 ----
        tableModel = new DefaultTableModel(
                new String[]{I18n.get("schedule.col.name"), I18n.get("schedule.col.deadline"),
                             I18n.get("schedule.col.days_left"), I18n.get("schedule.col.status"),
                             I18n.get("schedule.col.action")}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    Status s = getStatusForRow(row);
                    Color bg = getRowBackground(s);
                    c.setBackground(bg);
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(getSelectionBackground());
                    c.setForeground(getSelectionForeground());
                }
                return c;
            }
        };
        table.setRowHeight(32);
        table.setShowGrid(true);
        table.setGridColor(new Color(220, 220, 220));
        table.getTableHeader().setReorderingAllowed(false);

        // 设置列宽
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(COL_NAME).setPreferredWidth(220);
        cm.getColumn(COL_DEADLINE).setPreferredWidth(120);
        cm.getColumn(COL_DAYS_LEFT).setPreferredWidth(80);
        cm.getColumn(COL_STATUS).setPreferredWidth(80);
        cm.getColumn(COL_ACTION).setPreferredWidth(200);

        // 状态列使用自定义渲染器显示色块
        table.getColumnModel().getColumn(COL_STATUS).setCellRenderer(new StatusBadgeRenderer());

        // 双击行 -> 打开 DDL 设置对话框
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                if (e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) != COL_ACTION) {
                    openDdlDialog(row);
                }
                if (e.isPopupTrigger()) showContextMenu(e, row);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e, table.rowAtPoint(e.getPoint()));
            }
        });

        // "快速操作"列显示按钮组
        table.getColumnModel().getColumn(COL_ACTION).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(COL_ACTION).setCellEditor(new ActionButtonEditor());

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    // ========== 数据 ==========

    public void refresh() {
        projects = db.getAllProjects();
        tableModel.setRowCount(0);
        for (Project p : projects) {
            String ddlStr  = p.getDeadline() != null ? p.getDeadline().format(DATE_FMT) : "—";
            String daysStr;
            if (p.getDeadline() != null) {
                long d = ChronoUnit.DAYS.between(LocalDate.now(), p.getDeadline());
                if (d < 0) daysStr = "已逾期 " + Math.abs(d) + " 天";
                else if (d == 0) daysStr = "今天到期";
                else daysStr = d + " 天";
            } else {
                daysStr = "—";
            }
            tableModel.addRow(new Object[]{
                    p.getName(),
                    ddlStr,
                    daysStr,
                    p.getEffectiveStatus().getLabel(),
                    "操作"
            });
        }
        tableModel.fireTableDataChanged();
    }

    private Status getStatusForRow(int row) {
        if (projects == null || row >= projects.size()) return Status.OTHER;
        return projects.get(row).getEffectiveStatus();
    }

    private Color getRowBackground(Status s) {
        switch (s) {
            case URGENT:  return new Color(255, 235, 235);
            case PENDING: return new Color(255, 250, 220);
            case IDLE:    return new Color(230, 242, 255);
            case DONE:    return new Color(230, 255, 235);
            default:      return new Color(248, 248, 248);
        }
    }

    // ========== 对话框 ==========

    /** 由 MainFrame 的项目树右键菜单调用 */
    public void openDdlDialogForProject(Project target) {
        if (projects == null) refresh();
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).getId() == target.getId()) {
                openDdlDialog(i);
                return;
            }
        }
        // 如果找不到则刷新后重试
        refresh();
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).getId() == target.getId()) {
                openDdlDialog(i);
                return;
            }
        }
    }

    /** 由 MainFrame 菜单调用 */
    public void openSettingsDialogPublic() {
        openSettingsDialog();
    }

    /** 打开 DDL 设置对话框（双击行触发） */
    private void openDdlDialog(int row) {
        if (projects == null || row >= projects.size()) return;
        Project project = projects.get(row);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), I18n.get("schedule.dialog.title") + project.getName(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(420, 340);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(16, 20, 8, 20));

        // ---- 快捷天数选项 ----
        JPanel quickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        quickPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("schedule.dialog.quick")));
        ScheduleSettings ss = ScheduleSettings.getInstance();
        JButton urgentBtn  = new JButton(String.format(I18n.get("schedule.urgent"), ss.getUrgentDefaultDays()));
        JButton pendingBtn = new JButton(String.format(I18n.get("schedule.pending"), ss.getPendingDefaultDays()));
        JButton idleBtn    = new JButton(String.format(I18n.get("schedule.idle"), ss.getIdleDefaultDays()));
        JButton doneBtn    = new JButton(I18n.get("schedule.done"));

        urgentBtn.setBackground(new Color(255, 200, 200));
        pendingBtn.setBackground(new Color(255, 240, 170));
        idleBtn.setBackground(new Color(200, 225, 255));
        doneBtn.setBackground(new Color(200, 240, 210));

        quickPanel.add(urgentBtn);
        quickPanel.add(pendingBtn);
        quickPanel.add(idleBtn);
        quickPanel.add(doneBtn);

        // ---- 自定义日期 ----
        JPanel customPanel = new JPanel(new GridBagLayout());
        customPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("schedule.dialog.custom")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        LocalDate today = LocalDate.now();
        LocalDate initDate = project.getDeadline() != null ? project.getDeadline() : today;

        // 相对天数
        gbc.gridx = 0; gbc.gridy = 0;
        customPanel.add(new JLabel(I18n.get("schedule.label.days")), gbc);
        gbc.gridx = 1;
        JSpinner daysSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        customPanel.add(daysSpinner, gbc);
        gbc.gridx = 2;
        JButton applyDaysBtn = new JButton(I18n.get("schedule.btn.apply_days"));
        customPanel.add(applyDaysBtn, gbc);

        // 绝对日期
        gbc.gridx = 0; gbc.gridy = 1;
        customPanel.add(new JLabel(I18n.get("schedule.label.year")), gbc);
        gbc.gridx = 1;
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(today.getYear(), 2000, 2099, 1));
        yearSpinner.setEditor(new JSpinner.NumberEditor(yearSpinner, "####"));
        customPanel.add(yearSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        customPanel.add(new JLabel(I18n.get("schedule.label.month")), gbc);
        gbc.gridx = 1;
        JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel(initDate.getMonthValue(), 1, 12, 1));
        customPanel.add(monthSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        customPanel.add(new JLabel(I18n.get("schedule.label.day")), gbc);
        gbc.gridx = 1;
        JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(initDate.getDayOfMonth(), 1, 31, 1));
        customPanel.add(daySpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        JButton applyDateBtn = new JButton(I18n.get("schedule.btn.apply_date"));
        applyDateBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        customPanel.add(applyDateBtn, gbc);

        // ---- 清除 DDL ----
        JPanel clearPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearBtn = new JButton(I18n.get("schedule.btn.clear_ddl"));
        clearPanel.add(clearBtn);

        content.add(quickPanel);
        content.add(Box.createVerticalStrut(6));
        content.add(customPanel);
        content.add(Box.createVerticalStrut(6));
        content.add(clearPanel);

        dialog.add(content, BorderLayout.CENTER);

        // ---- 底部按钮 ----
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        // ---- 事件 ----
        urgentBtn.addActionListener(e -> {
            saveSchedule(project, LocalDate.now().plusDays(ss.getUrgentDefaultDays()), null);
            dialog.dispose(); refresh();
        });
        pendingBtn.addActionListener(e -> {
            saveSchedule(project, LocalDate.now().plusDays(ss.getPendingDefaultDays()), null);
            dialog.dispose(); refresh();
        });
        idleBtn.addActionListener(e -> {
            saveSchedule(project, LocalDate.now().plusDays(ss.getIdleDefaultDays()), null);
            dialog.dispose(); refresh();
        });
        doneBtn.addActionListener(e -> {
            saveSchedule(project, project.getDeadline(), Status.DONE);
            DoneEffectDialog.showAt(this, getWidth() / 2, getHeight() / 2);
            dialog.dispose(); refresh();
        });

        applyDaysBtn.addActionListener(e -> {
            int days = (Integer) daysSpinner.getValue();
            saveSchedule(project, LocalDate.now().plusDays(days), null);
            dialog.dispose(); refresh();
        });

        applyDateBtn.addActionListener(e -> {
            try {
                int y = (Integer) yearSpinner.getValue();
                int m = (Integer) monthSpinner.getValue();
                int d = (Integer) daySpinner.getValue();
                LocalDate date = LocalDate.of(y, m, d);
                saveSchedule(project, date, null);
                dialog.dispose(); refresh();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, I18n.get("schedule.msg.invalid_date"), I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        });

        clearBtn.addActionListener(e -> {
            saveSchedule(project, null, null);
            dialog.dispose(); refresh();
        });

        dialog.setVisible(true);
    }

    private void saveSchedule(Project project, LocalDate deadline, Status manualStatus) {
        project.setDeadline(deadline);
        project.setManualStatus(manualStatus);
        db.updateProjectSchedule(project.getId(), deadline, manualStatus);
    }

    /** 右键菜单 */
    private void showContextMenu(MouseEvent e, int row) {
        if (row < 0 || projects == null || row >= projects.size()) return;
        table.setRowSelectionInterval(row, row);
        Project project = projects.get(row);
        ScheduleSettings ss = ScheduleSettings.getInstance();

        JPopupMenu menu = new JPopupMenu();

        JMenuItem setDdl = new JMenuItem(I18n.get("schedule.ctx.set_ddl"));
        setDdl.addActionListener(ev -> openDdlDialog(row));
        menu.add(setDdl);

        menu.addSeparator();

        JMenuItem urgentItem = new JMenuItem(String.format(I18n.get("schedule.ctx.urgent"), ss.getUrgentDefaultDays()));
        urgentItem.setForeground(CLR_URGENT);
        urgentItem.addActionListener(ev -> {
            saveSchedule(project, LocalDate.now().plusDays(ss.getUrgentDefaultDays()), null);
            refresh();
        });
        menu.add(urgentItem);

        JMenuItem pendingItem = new JMenuItem(String.format(I18n.get("schedule.ctx.pending"), ss.getPendingDefaultDays()));
        pendingItem.setForeground(CLR_PENDING);
        pendingItem.addActionListener(ev -> {
            saveSchedule(project, LocalDate.now().plusDays(ss.getPendingDefaultDays()), null);
            refresh();
        });
        menu.add(pendingItem);

        JMenuItem idleItem = new JMenuItem(String.format(I18n.get("schedule.ctx.idle"), ss.getIdleDefaultDays()));
        idleItem.setForeground(CLR_IDLE);
        idleItem.addActionListener(ev -> {
            saveSchedule(project, LocalDate.now().plusDays(ss.getIdleDefaultDays()), null);
            refresh();
        });
        menu.add(idleItem);

        JMenuItem doneItem = new JMenuItem(I18n.get("schedule.ctx.done"));
        doneItem.setForeground(CLR_DONE);
        doneItem.addActionListener(ev -> {
            saveSchedule(project, project.getDeadline(), Status.DONE);
            DoneEffectDialog.showAt(this, getWidth() / 2, getHeight() / 2);
            refresh();
        });
        menu.add(doneItem);

        menu.addSeparator();

        JMenuItem clearItem = new JMenuItem(I18n.get("schedule.ctx.clear_ddl"));
        clearItem.addActionListener(ev -> {
            saveSchedule(project, null, null);
            refresh();
        });
        menu.add(clearItem);

        menu.show(table, e.getX(), e.getY());
    }

    /** 打开默认值设置对话框 */
    private void openSettingsDialog() {
        ScheduleSettings ss = ScheduleSettings.getInstance();

        // 使用自定义面板布局
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // 说明文字
        JLabel infoLabel = new JLabel("<html>" + I18n.get("schedule.settings.info") + "<br>"
                + I18n.get("schedule.status.urgent") + "  " + ScheduleSettings.URGENT_MIN + "~" + ScheduleSettings.URGENT_MAX
                + "  " + I18n.get("schedule.col.days_left").replace("剩余天数", "天") + " | "
                + I18n.get("schedule.status.pending") + "  " + ScheduleSettings.PENDING_MIN + "~" + ScheduleSettings.PENDING_MAX
                + "  " + I18n.get("schedule.col.days_left").replace("剩余天数", "天") + " | "
                + I18n.get("schedule.status.idle") + "  " + ScheduleSettings.IDLE_MIN + "~" + ScheduleSettings.IDLE_MAX + "  "
                + I18n.get("schedule.col.days_left").replace("剩余天数", "天") + "</html>");
        infoLabel.setFont(infoLabel.getFont().deriveFont(11f));
        infoLabel.setForeground(Color.GRAY);
        panel.add(infoLabel);
        panel.add(Box.createVerticalStrut(10));

        // 紧急
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row1.add(new JLabel(I18n.get("schedule.settings.urgent_days")));
        JSpinner urgentSp = new JSpinner(new SpinnerNumberModel(
                ss.getUrgentDefaultDays(), ScheduleSettings.URGENT_MIN, ScheduleSettings.URGENT_MAX, 1));
        row1.add(urgentSp);
        JLabel urgentRange = new JLabel(I18n.get("schedule.settings.range")
                .replace("{0}", String.valueOf(ScheduleSettings.URGENT_MIN))
                .replace("{1}", String.valueOf(ScheduleSettings.URGENT_MAX)));
        urgentRange.setFont(urgentRange.getFont().deriveFont(11f));
        urgentRange.setForeground(Color.GRAY);
        row1.add(urgentRange);
        panel.add(row1);

        // 暂缓
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.add(new JLabel(I18n.get("schedule.settings.pending_days")));
        JSpinner pendingSp = new JSpinner(new SpinnerNumberModel(
                ss.getPendingDefaultDays(), ScheduleSettings.PENDING_MIN, ScheduleSettings.PENDING_MAX, 1));
        row2.add(pendingSp);
        JLabel pendingRange = new JLabel(I18n.get("schedule.settings.range")
                .replace("{0}", String.valueOf(ScheduleSettings.PENDING_MIN))
                .replace("{1}", String.valueOf(ScheduleSettings.PENDING_MAX)));
        pendingRange.setFont(pendingRange.getFont().deriveFont(11f));
        pendingRange.setForeground(Color.GRAY);
        row2.add(pendingRange);
        panel.add(row2);

        // 闲置
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row3.add(new JLabel(I18n.get("schedule.settings.idle_days")));
        JSpinner idleSp = new JSpinner(new SpinnerNumberModel(
                ss.getIdleDefaultDays(), ScheduleSettings.IDLE_MIN, ScheduleSettings.IDLE_MAX, 1));
        row3.add(idleSp);
        JLabel idleRange = new JLabel(I18n.get("schedule.settings.range")
                .replace("{0}", String.valueOf(ScheduleSettings.IDLE_MIN))
                .replace("{1}", String.valueOf(ScheduleSettings.IDLE_MAX)));
        idleRange.setFont(idleRange.getFont().deriveFont(11f));
        idleRange.setForeground(Color.GRAY);
        row3.add(idleRange);
        panel.add(row3);

        int result = JOptionPane.showConfirmDialog(this, panel,
                I18n.get("schedule.settings.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            ss.setUrgentDefaultDays((Integer) urgentSp.getValue());
            ss.setPendingDefaultDays((Integer) pendingSp.getValue());
            ss.setIdleDefaultDays((Integer) idleSp.getValue());
            ss.save();
            JOptionPane.showMessageDialog(this, I18n.get("schedule.msg.saved"), I18n.get("msg.success"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ========== 辅助组件 ==========

    private JLabel makeLegend(Color color, String text) {
        JLabel lbl = new JLabel("  " + text + "  ");
        lbl.setOpaque(true);
        lbl.setBackground(lighten(color));
        lbl.setForeground(color.darker());
        lbl.setBorder(BorderFactory.createLineBorder(color, 1));
        lbl.setFont(lbl.getFont().deriveFont(11f));
        return lbl;
    }

    private Color lighten(Color c) {
        int r = Math.min(255, c.getRed()   + 80);
        int g = Math.min(255, c.getGreen() + 80);
        int b = Math.min(255, c.getBlue()  + 80);
        return new Color(r, g, b);
    }

    // ========== 自定义渲染器 ==========

    /** 状态列：圆角色块 */
    private class StatusBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            if (!isSelected) {
                Status s = getStatusForRow(row);
                Color fg = getStatusColor(s);
                lbl.setForeground(fg);
            }
            return lbl;
        }
    }

    private Color getStatusColor(Status s) {
        switch (s) {
            case URGENT:  return CLR_URGENT;
            case PENDING: return CLR_PENDING;
            case IDLE:    return CLR_IDLE;
            case DONE:    return CLR_DONE;
            default:      return CLR_OTHER;
        }
    }

    /** 操作列：渲染为按钮提示文字 */
    private class ActionButtonRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
            panel.setOpaque(true);
            Status s = getStatusForRow(row);
            panel.setBackground(isSelected ? table.getSelectionBackground() : getRowBackground(s));

            JButton editBtn = new JButton(I18n.get("schedule.action.set_ddl"));
            editBtn.setFont(editBtn.getFont().deriveFont(11f));
            editBtn.setMargin(new Insets(1, 4, 1, 4));

            JButton doneBtn = new JButton(I18n.get("schedule.action.done"));
            doneBtn.setFont(doneBtn.getFont().deriveFont(11f));
            doneBtn.setMargin(new Insets(1, 4, 1, 4));
            doneBtn.setBackground(new Color(200, 240, 210));

            panel.add(editBtn);
            panel.add(doneBtn);
            return panel;
        }
    }

    /** 操作列编辑器：点击时触发真实操作 */
    private class ActionButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        private int currentRow = -1;

        ActionButtonEditor() {
            JButton editBtn = new JButton(I18n.get("schedule.action.set_ddl"));
            editBtn.setFont(editBtn.getFont().deriveFont(11f));
            editBtn.setMargin(new Insets(1, 4, 1, 4));
            editBtn.addActionListener(e -> {
                fireEditingStopped();
                if (currentRow >= 0) openDdlDialog(currentRow);
            });

            JButton doneBtn = new JButton(I18n.get("schedule.action.done"));
            doneBtn.setFont(doneBtn.getFont().deriveFont(11f));
            doneBtn.setMargin(new Insets(1, 4, 1, 4));
            doneBtn.setBackground(new Color(200, 240, 210));
            doneBtn.addActionListener(e -> {
                fireEditingStopped();
                if (currentRow >= 0 && projects != null && currentRow < projects.size()) {
                    Project p = projects.get(currentRow);
                    saveSchedule(p, p.getDeadline(), Status.DONE);
                    DoneEffectDialog.showAt(table, table.getWidth() / 2, table.getHeight() / 2);
                    refresh();
                }
            });
            panel.add(editBtn);
            panel.add(doneBtn);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            currentRow = row;
            panel.setOpaque(true);
            panel.setBackground(table.getSelectionBackground());
            return panel;
        }

        @Override
        public Object getCellEditorValue() { return "操作"; }
    }
}
