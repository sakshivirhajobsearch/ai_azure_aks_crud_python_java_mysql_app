package com.ai.azure.aks.repository;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AKSRepository {
	private Connection conn;
	private PreparedStatement ps;

	public AKSRepository() throws SQLException, IOException {
		Properties props = new Properties();
		props.load(new FileInputStream("frontend-java/config.properties"));

		String url = props.getProperty("mysql_url");
		String user = props.getProperty("mysql_user");
		String pass = props.getProperty("mysql_password");

		conn = DriverManager.getConnection(url, user, pass);
	}

	public List<String> getNodes() throws SQLException {
		List<String> nodes = new ArrayList<>();
		ps = conn.prepareStatement("SELECT name FROM aks_nodes");
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			nodes.add(rs.getString("name"));
		}
		return nodes;
	}

	public List<String> getPods() throws SQLException {
		List<String> pods = new ArrayList<>();
		ps = conn.prepareStatement("SELECT name FROM aks_pods");
		ResultSet rs = ps.executeQuery();
		while (rs.next()) {
			pods.add(rs.getString("name"));
		}
		return pods;
	}

	public void addNode(String name, String status, String version) throws SQLException {
		ps = conn.prepareStatement("INSERT INTO aks_nodes (name, status, kubelet_version) VALUES (?, ?, ?)");
		ps.setString(1, name);
		ps.setString(2, status);
		ps.setString(3, version);
		ps.executeUpdate();
	}

	public void addPod(String name, String namespace, String status) throws SQLException {
		ps = conn.prepareStatement("INSERT INTO aks_pods (name, namespace, status) VALUES (?, ?, ?)");
		ps.setString(1, name);
		ps.setString(2, namespace);
		ps.setString(3, status);
		ps.executeUpdate();
	}

	public void close() throws SQLException {
		if (ps != null)
			ps.close();
		if (conn != null)
			conn.close();
	}
}
