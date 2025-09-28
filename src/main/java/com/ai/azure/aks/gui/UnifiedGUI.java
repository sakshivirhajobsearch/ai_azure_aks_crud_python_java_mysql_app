package com.ai.azure.aks.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class UnifiedGUI extends JFrame {
	
	private static final long serialVersionUID = 1L;

	private JTable nodeTable;
	private JTable podTable;
	private DefaultTableModel nodeModel;
	private DefaultTableModel podModel;
	private Connection conn;

	public UnifiedGUI() {
		setTitle("AI + Azure AKS CRUD & Autoscaler");
		setSize(1000, 600);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout());

		// -----------------------------
		// Database Connection
		// -----------------------------
		try {
			conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/ai_azure_aks", "root", "admin");
		} catch (SQLException e) {
			JOptionPane.showMessageDialog(this, "DB Connection failed: " + e.getMessage());
			System.exit(1);
		}

		// -----------------------------
		// Node Table
		// -----------------------------
		nodeModel = new DefaultTableModel(new String[] { "ID", "Name", "Status", "Kubelet Version" }, 0);
		nodeTable = new JTable(nodeModel);
		JScrollPane nodeScroll = new JScrollPane(nodeTable);

		JButton refreshNodes = new JButton("Refresh Nodes");
		refreshNodes.addActionListener(e -> loadNodes());

		JButton addNodeBtn = new JButton("Add Node");
		addNodeBtn.addActionListener(e -> addNodeDialog());

		JButton deleteNodeBtn = new JButton("Delete Node");
		deleteNodeBtn.addActionListener(e -> deleteNode());

		JPanel nodePanel = new JPanel(new BorderLayout());
		nodePanel.setBorder(BorderFactory.createTitledBorder("AKS Nodes"));
		nodePanel.add(nodeScroll, BorderLayout.CENTER);

		JPanel nodeBtnPanel = new JPanel();
		nodeBtnPanel.add(refreshNodes);
		nodeBtnPanel.add(addNodeBtn);
		nodeBtnPanel.add(deleteNodeBtn);
		nodePanel.add(nodeBtnPanel, BorderLayout.SOUTH);

		// -----------------------------
		// Pod Table
		// -----------------------------
		podModel = new DefaultTableModel(new String[] { "ID", "Name", "Namespace", "Status" }, 0);
		podTable = new JTable(podModel);
		JScrollPane podScroll = new JScrollPane(podTable);

		JButton refreshPods = new JButton("Refresh Pods");
		refreshPods.addActionListener(e -> loadPods());

		JButton addPodBtn = new JButton("Add Pod");
		addPodBtn.addActionListener(e -> addPodDialog());

		JButton deletePodBtn = new JButton("Delete Pod");
		deletePodBtn.addActionListener(e -> deletePod());

		JPanel podPanel = new JPanel(new BorderLayout());
		podPanel.setBorder(BorderFactory.createTitledBorder("AKS Pods"));
		podPanel.add(podScroll, BorderLayout.CENTER);

		JPanel podBtnPanel = new JPanel();
		podBtnPanel.add(refreshPods);
		podBtnPanel.add(addPodBtn);
		podBtnPanel.add(deletePodBtn);
		podPanel.add(podBtnPanel, BorderLayout.SOUTH);

		// -----------------------------
		// Autoscaler Panel
		// -----------------------------
		JPanel scalePanel = new JPanel(new FlowLayout());
		scalePanel.setBorder(BorderFactory.createTitledBorder("Autoscale Deployment"));

		JTextField depNameField = new JTextField(15);
		JTextField nsField = new JTextField(10);
		JTextField cpuField = new JTextField(5);
		JButton scaleBtn = new JButton("Scale");

		scaleBtn.addActionListener(e -> {
			String depName = depNameField.getText().trim();
			String ns = nsField.getText().trim();
			String cpu = cpuField.getText().trim();

			try {
				double cpuVal = Double.parseDouble(cpu);
				String result = runPythonScaler(depName, ns, cpuVal);
				JOptionPane.showMessageDialog(this, result);
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "CPU must be a number.");
			}
		});

		scalePanel.add(new JLabel("Deployment:"));
		scalePanel.add(depNameField);
		scalePanel.add(new JLabel("Namespace:"));
		scalePanel.add(nsField);
		scalePanel.add(new JLabel("CPU %:"));
		scalePanel.add(cpuField);
		scalePanel.add(scaleBtn);

		// -----------------------------
		// Add Panels
		// -----------------------------
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, nodePanel, podPanel);
		split.setDividerLocation(300);

		add(split, BorderLayout.CENTER);
		add(scalePanel, BorderLayout.SOUTH);

		loadNodes();
		loadPods();
	}

	// -----------------------------
	// Node CRUD Methods
	// -----------------------------
	private void loadNodes() {
		nodeModel.setRowCount(0);
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM aks_nodes")) {
			while (rs.next()) {
				nodeModel.addRow(new Object[] { rs.getInt("id"), rs.getString("name"), rs.getString("status"),
						rs.getString("kubelet_version") });
			}
		} catch (SQLException e) {
			showError(e);
		}
	}

	private void addNodeDialog() {
		String name = JOptionPane.showInputDialog("Node Name:");
		String status = JOptionPane.showInputDialog("Status:");
		String kv = JOptionPane.showInputDialog("Kubelet Version:");
		if (name != null && status != null && kv != null) {
			try (PreparedStatement ps = conn
					.prepareStatement("INSERT INTO aks_nodes (name,status,kubelet_version) VALUES (?,?,?)")) {
				ps.setString(1, name);
				ps.setString(2, status);
				ps.setString(3, kv);
				ps.executeUpdate();
				loadNodes();
			} catch (SQLException e) {
				showError(e);
			}
		}
	}

	private void deleteNode() {
		int row = nodeTable.getSelectedRow();
		if (row >= 0) {
			int id = (int) nodeModel.getValueAt(row, 0);
			try (PreparedStatement ps = conn.prepareStatement("DELETE FROM aks_nodes WHERE id=?")) {
				ps.setInt(1, id);
				ps.executeUpdate();
				loadNodes();
			} catch (SQLException e) {
				showError(e);
			}
		}
	}

	// -----------------------------
	// Pod CRUD Methods
	// -----------------------------
	private void loadPods() {
		podModel.setRowCount(0);
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM aks_pods")) {
			while (rs.next()) {
				podModel.addRow(new Object[] { rs.getInt("id"), rs.getString("name"), rs.getString("namespace"),
						rs.getString("status") });
			}
		} catch (SQLException e) {
			showError(e);
		}
	}

	private void addPodDialog() {
		String name = JOptionPane.showInputDialog("Pod Name:");
		String ns = JOptionPane.showInputDialog("Namespace:");
		String status = JOptionPane.showInputDialog("Status:");
		if (name != null && ns != null && status != null) {
			try (PreparedStatement ps = conn
					.prepareStatement("INSERT INTO aks_pods (name, namespace, status) VALUES (?,?,?)")) {
				ps.setString(1, name);
				ps.setString(2, ns);
				ps.setString(3, status);
				ps.executeUpdate();
				loadPods();
			} catch (SQLException e) {
				showError(e);
			}
		}
	}

	private void deletePod() {
		int row = podTable.getSelectedRow();
		if (row >= 0) {
			int id = (int) podModel.getValueAt(row, 0);
			try (PreparedStatement ps = conn.prepareStatement("DELETE FROM aks_pods WHERE id=?")) {
				ps.setInt(1, id);
				ps.executeUpdate();
				loadPods();
			} catch (SQLException e) {
				showError(e);
			}
		}
	}

	// -----------------------------
	// Run Python Autoscaler
	// -----------------------------
	private String runPythonScaler(String depName, String ns, double cpu) {
		try {
			String command = String.format("python aks_autoscaler.py \"%s\" \"%s\" %f", depName, ns, cpu);
			ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null)
				output.append(line).append("\n");
			process.waitFor();
			return output.toString();
		} catch (Exception e) {
			return "Error running Python scaler: " + e.getMessage();
		}
	}

	private void showError(SQLException e) {
		JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new UnifiedGUI().setVisible(true));
	}
}
