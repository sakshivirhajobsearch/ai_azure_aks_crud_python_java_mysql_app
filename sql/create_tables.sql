CREATE DATABASE ai_azure_aks;
USE ai_azure_aks;

CREATE TABLE aks_nodes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    status VARCHAR(50),
    kubelet_version VARCHAR(50)
);

CREATE TABLE aks_pods (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    namespace VARCHAR(50),
    status VARCHAR(50)
);

CREATE TABLE scaling_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    deployment_name VARCHAR(100),
    namespace VARCHAR(50),
    old_replicas INT,
    new_replicas INT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
