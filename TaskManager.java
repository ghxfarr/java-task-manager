import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class TaskManager extends JFrame {
    private static final String[] TASK_STATUSES = { "Pending", "In Progress", "Completed", "Canceled" };
    private static final String FILTER_ALL = "All";
    private JTable taskTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> filterComboBox;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private final Timer alarmTimer = new Timer(true);
    private final Map<String, TimerTask> scheduledAlarms = new ConcurrentHashMap<>();
    private final Map<Long, Thread> activeAlarms = new ConcurrentHashMap<>(); // Track active alarm threads by task ID

    public TaskManager() {
        setTitle("Task Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        initComponents();
        setMinimumSize(new Dimension(600, 400));
        styleComponents();
        setLayout(new BorderLayout());

        JPanel buttonPanel = createButtonPanel();
        JPanel filterPanel = createFilterPanel();
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(buttonPanel, BorderLayout.NORTH);
        topPanel.add(filterPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(taskTable), BorderLayout.CENTER);
        refreshTable();
        enforceBlockStatusOnStartup();
    }

    private void styleComponents() {
        setUIFont(getUIFont());
        styleTableHeader(taskTable.getTableHeader());
        styleTableBody(taskTable);
    }

    private void setUIFont(Font font) {
        UIManager.put("Label.font", font);
        UIManager.put("Button.font", font);
        UIManager.put("Table.font", font);
        UIManager.put("ComboBox.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("TextArea.font", font);
        UIManager.put("TitledBorder.font", font);
    }

    private Font getUIFont() {
        return new Font("SansSerif", Font.PLAIN, 14);
    }

    private Font getTableHeaderFont() {
        return new Font("SansSerif", Font.BOLD, 14);
    }

    private void styleTableHeader(JTableHeader header) {
        header.setBackground(new Color(0, 51, 102));
        header.setForeground(Color.WHITE);
        header.setFont(getTableHeaderFont());
    }

    private void styleTableBody(JTable table) {
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(new Color(173, 216, 230));
        table.setSelectionForeground(Color.BLACK);
        table.setRowHeight(24);
        table.setGridColor(new Color(220, 220, 220));
    }

    private Insets getButtonMargin() {
        return new Insets(5, 10, 5, 10);
    }

    private void initComponents() {
        String[] columns = { "Task Name", "Description", "Start Date", "End Date", "Status" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        taskTable = new JTable(tableModel);
        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        taskTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        taskTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        taskTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JButton addButton = new JButton("Add Task");
        JButton updateButton = new JButton("Update Task");
        JButton deleteButton = new JButton("Delete Task");
        JButton markCompletedButton = new JButton("Mark as Completed");

        Insets margin = getButtonMargin();
        Font buttonFont = getUIFont();
        for (JButton btn : new JButton[] { addButton, updateButton, deleteButton, markCompletedButton }) {
            btn.setMargin(margin);
            btn.setFont(buttonFont);
        }
        addButton.addActionListener(e -> showTaskDialog(null, false));
        updateButton.addActionListener(e -> {
            int selectedRow = taskTable.getSelectedRow();
            if (selectedRow < 0) {
                JOptionPane.showMessageDialog(this, "Please select a task to update", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Task task = getTaskAtRow(selectedRow);
            showTaskDialog(task, true);
        });
        deleteButton.addActionListener(e -> showDeleteTaskDialog());
        markCompletedButton.addActionListener(e -> markAsCompleted());

        panel.add(addButton);
        panel.add(updateButton);
        panel.add(deleteButton);
        panel.add(markCompletedButton);

        panel.setBorder(BorderFactory.createTitledBorder("Task Actions"));
        return panel;
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        JLabel filterLabel = new JLabel("Filter by Status:");
        String[] filters = new String[TASK_STATUSES.length + 1];
        filters[0] = FILTER_ALL;
        System.arraycopy(TASK_STATUSES, 0, filters, 1, TASK_STATUSES.length);
        filterComboBox = new JComboBox<>(filters);
        filterComboBox.setFont(getUIFont());
        filterComboBox.addActionListener(e -> refreshTable());
        panel.add(filterLabel);
        panel.add(filterComboBox);

        panel.setBorder(BorderFactory.createTitledBorder("Filter Tasks"));
        return panel;
    }

    private void showTaskDialog(Task task, boolean isUpdate) {
        boolean updating = isUpdate && task != null;
        String dialogTitle = updating ? "Update Task" : "Add Task";
        JDialog dialog = new JDialog(this, dialogTitle, true);
        dialog.setMinimumSize(new Dimension(400, 375));
        dialog.setPreferredSize(new Dimension(500, 450));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        dialog.getContentPane().setBackground(new Color(245, 245, 250));
        ((JComponent) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy = 0;

        JLabel nameLabel = new JLabel("Task Name:");
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        nameLabel.setForeground(new Color(33, 33, 33));
        JTextField nameField = new JTextField(updating ? task.getName() : "", 20);
        nameField.setBackground(Color.WHITE);
        nameField.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true));
        formPanel.add(nameLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(nameField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        JLabel descLabel = new JLabel("Description:");
        descLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        descLabel.setForeground(new Color(33, 33, 33));
        formPanel.add(descLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextArea descArea = new JTextArea(updating ? task.getDescription() : "", 10, 20);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBackground(Color.WHITE);
        descArea.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true));
        descArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        javax.swing.text.AbstractDocument doc = (javax.swing.text.AbstractDocument) descArea.getDocument();
        doc.setDocumentFilter(new DocumentFilter() {
            private final int maxLength = 500;

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                int currentLength = fb.getDocument().getLength();
                int overLimit = (currentLength + string.length()) - maxLength;
                if (overLimit > 0) {
                    string = string.substring(0, string.length() - overLimit);
                }
                if (string.length() > 0 && (currentLength < maxLength)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                int currentLength = fb.getDocument().getLength();
                int newLength = currentLength - length + (text != null ? text.length() : 0);
                if (text == null) {
                    super.replace(fb, offset, length, text, attrs);
                    return;
                }
                int overLimit = newLength - maxLength;
                if (overLimit > 0) {
                    text = text.substring(0, text.length() - overLimit);
                }
                if (text.length() > 0 && (currentLength - length < maxLength)) {
                    super.replace(fb, offset, length, text, attrs);
                } else if (text.length() == 0) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        JScrollPane descScrollPane = new JScrollPane(descArea);
        descScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descScrollPane.setBorder(BorderFactory.createEmptyBorder());
        descScrollPane.setPreferredSize(new Dimension(200, 150));
        descScrollPane.setMinimumSize(new Dimension(150, 100));
        formPanel.add(descScrollPane, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        JLabel startDateLabel = new JLabel("Start Date & Time (dd/MM/yyyy HH:mm):");
        startDateLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        startDateLabel.setForeground(new Color(33, 33, 33));
        formPanel.add(startDateLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JSpinner startDateSpinner = new JSpinner(
                new SpinnerDateModel(updating ? task.getStartDate() : new Date(), null, null, java.util.Calendar.MINUTE));
        startDateSpinner.setEditor(new JSpinner.DateEditor(startDateSpinner, "dd/MM/yyyy HH:mm"));
        ((JComponent) startDateSpinner).setBackground(Color.WHITE);
        ((JComponent) startDateSpinner).setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true));
        formPanel.add(startDateSpinner, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        JLabel endDateLabel = new JLabel("End Date & Time (dd/MM/yyyy HH:mm):");
        endDateLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        endDateLabel.setForeground(new Color(33, 33, 33));
        formPanel.add(endDateLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JSpinner endDateSpinner = new JSpinner(
                new SpinnerDateModel(updating ? task.getEndDate() : new Date(), null, null, java.util.Calendar.MINUTE));
        endDateSpinner.setEditor(new JSpinner.DateEditor(endDateSpinner, "dd/MM/yyyy HH:mm"));
        ((JComponent) endDateSpinner).setBackground(Color.WHITE);
        ((JComponent) endDateSpinner).setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true));
        formPanel.add(endDateSpinner, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        JLabel statusLabel = new JLabel("Status:");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusLabel.setForeground(new Color(33, 33, 33));
        formPanel.add(statusLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JComboBox<String> statusComboBox = new JComboBox<>(TASK_STATUSES);
        if (updating) {
            statusComboBox.setSelectedItem(task.getStatus());
        }
        formPanel.add(statusComboBox, gbc);

        JButton submitButton = new JButton(updating ? "Update" : "Add");
        JButton cancelButton = new JButton("Cancel");
        submitButton.setBackground(new Color(0, 123, 255));
        submitButton.setForeground(Color.BLACK);
        cancelButton.setBackground(new Color(220, 53, 69));
        cancelButton.setForeground(Color.BLACK);
        for (JButton btn : new JButton[] { submitButton, cancelButton }) {
            btn.setFocusPainted(false);
            btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        }

        submitButton.addActionListener(e -> {
            try {
                startDateSpinner.commitEdit();
                endDateSpinner.commitEdit();
            } catch (ParseException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid date format", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String taskName = nameField.getText().trim();
            String description = descArea.getText().trim();
            String status = (String) statusComboBox.getSelectedItem();

            if (taskName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter a task name", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Date startDate = (Date) startDateSpinner.getValue();
            Date endDate = (Date) endDateSpinner.getValue();

            if (endDate.before(startDate)) {
                JOptionPane.showMessageDialog(dialog, "End Date cannot be before Start Date", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (updating) {
                updateTask(task.getId(), taskName, description, startDate, endDate, status);
            } else {
                addTask(taskName, description, startDate, endDate, status);
            }
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showDeleteTaskDialog() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a task to delete", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Task task = getTaskAtRow(selectedRow);
        JDialog dialog = new JDialog(this, "Delete Task", true);
        dialog.setMinimumSize(new Dimension(300, 150));
        dialog.setPreferredSize(new Dimension(350, 200));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridLayout(3, 1, 10, 10));
        dialog.getContentPane().setBackground(new Color(250, 245, 245));
        ((JComponent) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel confirmLabel = new JLabel("Delete task: " + task.getName() + "?");
        confirmLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        confirmLabel.setForeground(new Color(183, 28, 28));
        JButton deleteButton = new JButton("Delete");
        JButton cancelButton = new JButton("Cancel");
        deleteButton.setBackground(new Color(220, 53, 69));
        deleteButton.setForeground(Color.BLACK);
        cancelButton.setBackground(new Color(108, 117, 125));
        cancelButton.setForeground(Color.BLACK);
        for (JButton btn : new JButton[] { deleteButton, cancelButton }) {
            btn.setFocusPainted(false);
            btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        }

        deleteButton.addActionListener(e -> {
            deleteTask(task.getId());
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(confirmLabel);
        dialog.add(deleteButton);
        dialog.add(cancelButton);
        dialog.setVisible(true);
    }

    private void markAsCompleted() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow >= 0) {
            Task task = getTaskAtRow(selectedRow);
            // Matikan alarm sebelum update status
            stopContinuousAlarm(task.getId());
            updateTask(task.getId(), task.getName(), task.getDescription(), task.getStartDate(), task.getEndDate(), "Completed");
        } else {
            JOptionPane.showMessageDialog(this, "Please select a task to mark as completed", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        String filter = (String) filterComboBox.getSelectedItem();
        List<Task> tasks = getTasks(filter);
        for (Task task : tasks) {
            tableModel.addRow(new Object[] {
                    task.getName(),
                    task.getDescription(),
                    dateFormat.format(task.getStartDate()),
                    dateFormat.format(task.getEndDate()),
                    task.getStatus()
            });
        }
        scheduleAlarms(tasks);
    }

    private void scheduleAlarms(List<Task> tasks) {
        for (TimerTask task : scheduledAlarms.values()) {
            task.cancel();
        }
        scheduledAlarms.clear();

        long now = System.currentTimeMillis();

        // Matikan alarm thread untuk task yang statusnya "Completed"
        for (Task t : tasks) {
            if ("Completed".equalsIgnoreCase(t.getStatus())) {
                stopContinuousAlarm(t.getId());
            }
        }

        for (Task t : tasks) {
            if ("Completed".equalsIgnoreCase(t.getStatus())) continue; // Jangan buat alarm untuk task completed

            String startKey = t.getId() + "-start";
            String endKey = t.getId() + "-end";
            long startMillis = t.getStartDate().getTime();
            long endMillis = t.getEndDate().getTime();

            if (endMillis <= now) continue;

            long delayToStart = Math.max(0, startMillis - now);
            long delayToEnd = Math.max(0, endMillis - now);

            if (startMillis > now) {
                TimerTask startTask = new TimerTask() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(() -> {
                            showAlarmPopup("Task started: " + t.getName(), "Task Start");
                            startContinuousAlarm(t.getId(), endMillis);
                        });
                    }
                };
                scheduledAlarms.put(startKey, startTask);
                alarmTimer.schedule(startTask, delayToStart);
            }

            TimerTask endTask = new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        showAlarmPopup("Task ended: " + t.getName(), "Task End");
                        stopContinuousAlarm(t.getId());
                    });
                }
            };
            scheduledAlarms.put(endKey, endTask);
            alarmTimer.schedule(endTask, delayToEnd);

            if (startMillis <= now && endMillis > now && !activeAlarms.containsKey(t.getId())) {
                startContinuousAlarm(t.getId(), endMillis);
            }
        }
    }

    private void showAlarmPopup(String message, String title) {
        Toolkit.getDefaultToolkit().beep();
        JFrame topFrame = new JFrame();
        topFrame.setType(Window.Type.UTILITY);
        topFrame.setAlwaysOnTop(true);
        topFrame.setUndecorated(true);
        topFrame.setLocationRelativeTo(null);
        topFrame.setResizable(true);
        topFrame.setVisible(true);
        topFrame.toFront();
        topFrame.requestFocus();
        JLabel msgLabel = new JLabel(message);
        msgLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        msgLabel.setForeground(new Color(33, 33, 33));
        JOptionPane.showMessageDialog(topFrame, msgLabel, title, JOptionPane.INFORMATION_MESSAGE, UIManager.getIcon("OptionPane.informationIcon"));
        topFrame.dispose();
    }

    private void cancelAlarmsForTask(long taskId) {
        String startKey = taskId + "-start";
        String endKey = taskId + "-end";

        TimerTask st = scheduledAlarms.remove(startKey);
        if (st != null) st.cancel();
        TimerTask et = scheduledAlarms.remove(endKey);
        if (et != null) et.cancel();
    }

    private List<Task> getTasks(String filter) {
        List<Task> tasks = new ArrayList<>();
        String query = filter.equals(FILTER_ALL)
                ? "SELECT t.task_id, t.name, t.description, t.start_date, t.end_date, s.status_name, t.updated_at FROM tasks t JOIN task_status s ON t.status_id = s.status_id ORDER BY t.updated_at DESC"
                : "SELECT t.task_id, t.name, t.description, t.start_date, t.end_date, s.status_name, t.updated_at FROM tasks t JOIN task_status s ON t.status_id = s.status_id WHERE s.status_name = ? ORDER BY t.updated_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            if (!filter.equals(FILTER_ALL)) {
                stmt.setString(1, filter);
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                tasks.add(new Task(rs.getLong("task_id"), rs.getString("name"),
                        rs.getString("description") != null ? rs.getString("description") : "",
                        rs.getTimestamp("start_date"), rs.getTimestamp("end_date"), rs.getString("status_name")));
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return tasks;
    }

    private void addTask(String name, String description, Date startDate, Date endDate, String status) {
        String query = "INSERT INTO tasks (name, description, start_date, end_date, status_id, created_at, updated_at) VALUES (?, ?, ?, ?, (SELECT status_id FROM task_status WHERE status_name = ?), NOW(), NOW())";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, description.isEmpty() ? null : description);
            stmt.setTimestamp(3, new java.sql.Timestamp(startDate.getTime()));
            stmt.setTimestamp(4, new java.sql.Timestamp(endDate.getTime()));
            stmt.setString(5, status);
            stmt.executeUpdate();
            refreshTable();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateTask(long taskId, String name, String description, Date startDate, Date endDate, String status) {
        // Matikan alarm jika status menjadi Completed
        if ("Completed".equalsIgnoreCase(status)) {
            stopContinuousAlarm(taskId);
        }
        String query = "UPDATE tasks SET name = ?, description = ?, start_date = ?, end_date = ?, status_id = (SELECT status_id FROM task_status WHERE status_name = ?), updated_at = NOW() WHERE task_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, description.isEmpty() ? null : description);
            stmt.setTimestamp(3, new java.sql.Timestamp(startDate.getTime()));
            stmt.setTimestamp(4, new java.sql.Timestamp(endDate.getTime()));
            stmt.setString(5, status);
            stmt.setLong(6, taskId);
            stmt.executeUpdate();
            refreshTable();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteTask(long taskId) {
        // Matikan alarm sebelum hapus task
        stopContinuousAlarm(taskId);
        String query = "DELETE FROM tasks WHERE task_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, taskId);
            stmt.executeUpdate();
            cancelAlarmsForTask(taskId);
            refreshTable();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startContinuousAlarm(long taskId, long endMillis) {
        Thread alarmThread = new Thread(() -> {
            try {
                while (System.currentTimeMillis() < endMillis && !Thread.currentThread().isInterrupted()) {
                    Toolkit.getDefaultToolkit().beep();
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ignored) {
            }
        });
        alarmThread.setDaemon(true);
        alarmThread.start();
        activeAlarms.put(taskId, alarmThread);
    }

    private void stopContinuousAlarm(long taskId) {
        Thread t = activeAlarms.remove(taskId);
        if (t != null) t.interrupt();
    }

    private void enforceBlockStatusOnStartup() {
        List<Task> tasks = getTasks(FILTER_ALL);
        long now = System.currentTimeMillis();
        for (Task t : tasks) {
            // Matikan alarm jika status sudah Completed
            if ("Completed".equalsIgnoreCase(t.getStatus())) {
                stopContinuousAlarm(t.getId());
                continue;
            }
            long startMillis = t.getStartDate().getTime();
            long endMillis = t.getEndDate().getTime();
            if (startMillis <= now && endMillis > now && !activeAlarms.containsKey(t.getId())) {
                startContinuousAlarm(t.getId(), endMillis);
            }
            if (endMillis <= now) {
                stopContinuousAlarm(t.getId());
            }
        }
    }

    private Task getTaskAtRow(int row) {
        String filter = (String) filterComboBox.getSelectedItem();
        List<Task> tasks = getTasks(filter);
        return tasks.get(row);
    }

    private static class Task {
        private long id;
        private String name;
        private String description;
        private Date startDate;
        private Date endDate;
        private String status;

        public Task(long id, String name, String description, Date startDate, Date endDate, String status) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Date getStartDate() {
            return startDate;
        }

        public Date getEndDate() {
            return endDate;
        }

        public String getStatus() {
            return status;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TaskManager().setVisible(true));
    }
}