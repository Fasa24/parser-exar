package com.app.view;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionListener;

public class MainView extends JFrame {
    private JTextArea inputTextArea;
    private JScrollPane inputScroll;
    private DefaultTableModel tableModel;
    private JButton parseButton;
    private JButton themeToggleButton;
    private boolean isDarkMode = true;

    public MainView() {
        configureWindow();
        initComponents();
    }

    private void configureWindow() {
        setTitle("Parser EXAR - IDSTM");
        setSize(950, 900);
        setMinimumSize(new Dimension(900, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        JPanel topHeaderPanel = new JPanel(new BorderLayout());
        topHeaderPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 0, 15));

        JLabel titleLabel = new JLabel("Parser EXAR");
        titleLabel.setFont(new Font("AtkinsonHyperlegibleNext", Font.BOLD, 20));

        themeToggleButton = new JButton("Light Mode");
        themeToggleButton.setFont(new Font("AtkinsonHyperlegibleNext", Font.PLAIN, 14));
        themeToggleButton.setFocusPainted(false);
        themeToggleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        themeToggleButton.setPreferredSize(new Dimension(140, 35));
        themeToggleButton.addActionListener(_ -> toggleTheme());

        topHeaderPanel.add(titleLabel, BorderLayout.WEST);
        topHeaderPanel.add(themeToggleButton, BorderLayout.EAST);
        add(topHeaderPanel, BorderLayout.NORTH);

        inputTextArea = new JTextArea();
        inputTextArea.setLineWrap(true);
        inputTextArea.setWrapStyleWord(true);
        inputTextArea.setFont(new Font("AtkinsonHyperlegibleNext", Font.PLAIN, 16));
        inputTextArea.setMargin(new Insets(10, 10, 10, 10));

        inputTextArea.setText("""
                (X1+B2);
                (Y1+B3*C4)+D;
                (((VAR2+X1)));
                (PESO+ (CARGO*DIF2));
                ((X2+45.78)* (CARGO/ABONO) - (PORC*12.55))-INT;
                456.78* (12.34*3.56E45) +B2;""");

        inputTextArea.setCaretPosition(0);

        inputScroll = new JScrollPane(inputTextArea);
        TitledBorder inputBorder = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5), "Input Module");
        inputBorder.setTitleFont(new Font("AtkinsonHyperlegibleNext", Font.BOLD, 16));
        inputScroll.setBorder(inputBorder);

        parseButton = new JButton("Parse Expressions");
        parseButton.setFont(new Font("AtkinsonHyperlegibleNext", Font.BOLD, 16));
        parseButton.setBackground(new Color(70, 170, 95));
        parseButton.setForeground(Color.WHITE);
        parseButton.setFocusPainted(false);
        parseButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        buttonPanel.add(parseButton);

        String[] columns = {"No.", "Line No.", "Expression", "Code", "Message"};

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable resultsTable = createResultsTable();

        JScrollPane resultsScroll = new JScrollPane(resultsTable);
        TitledBorder resultsBorder = BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5), "Results & Errors Module");
        resultsBorder.setTitleFont(new Font("AtkinsonHyperlegibleNext", Font.BOLD, 16));
        resultsScroll.setBorder(resultsBorder);

        JPanel lowerPanel = new JPanel(new BorderLayout());
        lowerPanel.add(buttonPanel, BorderLayout.NORTH);
        lowerPanel.add(resultsScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputScroll, lowerPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(splitPane, BorderLayout.CENTER);
    }

    private JTable createResultsTable() {
        JTable table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component comp = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    Color altColor = UIManager.getColor("Table.alternateRowColor");
                    if (altColor == null) {
                        Color bg = getBackground();
                        int offset = isDarkMode ? 12 : -12;
                        altColor = new Color(
                                Math.clamp(bg.getRed() + offset, 0, 255),
                                Math.clamp(bg.getGreen() + offset, 0, 255),
                                Math.clamp(bg.getBlue() + offset, 0, 255)
                        );
                    }
                    comp.setBackground(row % 2 == 1 ? altColor : getBackground());
                }
                return comp;
            }
        };
        table.setFillsViewportHeight(true);
        table.setRowHeight(36);
        table.setFont(new Font("AtkinsonHyperlegibleNext", Font.PLAIN, 15));
        table.getTableHeader().setFont(new Font("AtkinsonHyperlegibleNext", Font.BOLD, 16));
        table.getTableHeader().setReorderingAllowed(true);
        table.getTableHeader().setResizingAllowed(true);

        configureColumnWidths(table);
        return table;
    }

    private void configureColumnWidths(JTable table) {
        TableColumn noCol = table.getColumnModel().getColumn(0);
        noCol.setPreferredWidth(55);
        noCol.setMinWidth(40);
        noCol.setMaxWidth(90);

        TableColumn lineCol = table.getColumnModel().getColumn(1);
        lineCol.setPreferredWidth(100);
        lineCol.setMinWidth(85);
        lineCol.setMaxWidth(140);

        TableColumn exprCol = table.getColumnModel().getColumn(2);
        exprCol.setPreferredWidth(240);

        TableColumn codeCol = table.getColumnModel().getColumn(3);
        codeCol.setPreferredWidth(75);
        codeCol.setMinWidth(55);
        codeCol.setMaxWidth(110);

        TableColumn msgCol = table.getColumnModel().getColumn(4);
        msgCol.setPreferredWidth(360);
    }

    private void toggleTheme() {
        try {
            int currentScrollPosition = inputScroll.getVerticalScrollBar().getValue();
            if (isDarkMode) {
                FlatLightLaf.setup();
                themeToggleButton.setText("Dark Mode");
            } else {
                FlatDarkLaf.setup();
                themeToggleButton.setText("Light Mode");
            }
            isDarkMode = !isDarkMode;
            FlatLaf.updateUI();

            SwingUtilities.invokeLater(() ->
                    inputScroll.getVerticalScrollBar().setValue(currentScrollPosition)
            );
        } catch (Exception ex) {
            System.err.println("Failed to switch theme: " + ex.getMessage());
        }
    }

    public String getInputText() {
        return inputTextArea.getText();
    }

    public void setParseButtonListener(ActionListener listener) {
        parseButton.addActionListener(listener);
    }

    public void clearResults() {
        tableModel.setRowCount(0);
    }

    public void addResultRow(Object[] rowData) {
        tableModel.addRow(rowData);
    }

    public void displayWarning(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.WARNING_MESSAGE);
    }
}