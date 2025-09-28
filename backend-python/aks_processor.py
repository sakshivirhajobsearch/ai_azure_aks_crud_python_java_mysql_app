from kubernetes import client, config
from ai_aks import db_cursor, db
from ai_scaler import predict_replicas
import os

# ---------------------------
# Kubernetes API Initialization
# ---------------------------
use_real_k8s = False

kube_config_path = os.path.expanduser("~/.kube/config")
try:
    if os.path.exists(kube_config_path) and os.path.getsize(kube_config_path) > 0:
        config.load_kube_config()
        use_real_k8s = True
    else:
        raise FileNotFoundError
except:
    try:
        config.load_incluster_config()
        use_real_k8s = True
    except:
        print("⚠️ Kubernetes config not found. Running in mock mode.")
        use_real_k8s = False

if use_real_k8s:
    apps_v1 = client.AppsV1Api()
    core_v1 = client.CoreV1Api()
else:
    # Mock classes to allow testing locally
    class MockDeployment:
        spec = type("spec", (), {"replicas": 1})()

    class MockAppsV1Api:
        def read_namespaced_deployment(self, name, namespace):
            return MockDeployment()

        def patch_namespaced_deployment(self, name, namespace, deployment):
            print(f"[MOCK] Patched deployment '{name}' to {deployment.spec.replicas} replicas")

    apps_v1 = MockAppsV1Api()
    core_v1 = None  # Not used in mock mode

# ---------------------------
# NODE CRUD
# ---------------------------
def list_nodes():
    db_cursor.execute("SELECT * FROM aks_nodes")
    return db_cursor.fetchall()

def add_node(name: str, status: str, kubelet_version: str):
    db_cursor.execute(
        "INSERT INTO aks_nodes (name, status, kubelet_version) VALUES (%s, %s, %s)",
        (name, status, kubelet_version)
    )
    db.commit()

def update_node(node_id: int, name=None, status=None, kubelet_version=None):
    query = "UPDATE aks_nodes SET "
    params = []
    if name: query += "name=%s,"; params.append(name)
    if status: query += "status=%s,"; params.append(status)
    if kubelet_version: query += "kubelet_version=%s,"; params.append(kubelet_version)
    query = query.rstrip(",") + " WHERE id=%s"
    params.append(node_id)
    db_cursor.execute(query, tuple(params))
    db.commit()

def delete_node(node_id: int):
    db_cursor.execute("DELETE FROM aks_nodes WHERE id=%s", (node_id,))
    db.commit()

# ---------------------------
# POD CRUD
# ---------------------------
def list_pods():
    db_cursor.execute("SELECT * FROM aks_pods")
    return db_cursor.fetchall()

def add_pod(name: str, namespace: str, status: str):
    db_cursor.execute(
        "INSERT INTO aks_pods (name, namespace, status) VALUES (%s, %s, %s)",
        (name, namespace, status)
    )
    db.commit()

def update_pod(pod_id: int, name=None, namespace=None, status=None):
    query = "UPDATE aks_pods SET "
    params = []
    if name: query += "name=%s,"; params.append(name)
    if namespace: query += "namespace=%s,"; params.append(namespace)
    if status: query += "status=%s,"; params.append(status)
    query = query.rstrip(",") + " WHERE id=%s"
    params.append(pod_id)
    db_cursor.execute(query, tuple(params))
    db.commit()

def delete_pod(pod_id: int):
    db_cursor.execute("DELETE FROM aks_pods WHERE id=%s", (pod_id,))
    db.commit()

# ---------------------------
# AUTOSCALING
# ---------------------------
def autoscale_deployment(deployment_name: str, namespace: str, cpu_usage: float):
    if not namespace:
        namespace = "default"
    recommended_replicas = predict_replicas(cpu_usage)
    deployment = apps_v1.read_namespaced_deployment(deployment_name, namespace)
    current_replicas = deployment.spec.replicas

    if current_replicas != recommended_replicas:
        deployment.spec.replicas = recommended_replicas
        apps_v1.patch_namespaced_deployment(deployment_name, namespace, deployment)
        db_cursor.execute(
            "INSERT INTO scaling_logs (deployment_name, namespace, old_replicas, new_replicas) VALUES (%s,%s,%s,%s)",
            (deployment_name, namespace, current_replicas, recommended_replicas)
        )
        db.commit()
        return f"Scaled '{deployment_name}' from {current_replicas} → {recommended_replicas} replicas"
    else:
        return f"No scaling needed for '{deployment_name}'. Current replicas: {current_replicas}"

# ---------------------------
# MAIN (for testing)
# ---------------------------
if __name__ == "__main__":
    dep_name = input("Deployment name: ").strip()
    ns = input("Namespace (leave empty for default): ").strip()
    cpu = input("Current CPU usage (%): ").strip()

    try:
        cpu_val = float(cpu)
        result = autoscale_deployment(dep_name, ns, cpu_val)
        print(result)
    except ValueError:
        print("Invalid CPU value. Please enter a number.")
    except Exception as e:
        print(f"Error: {e}")
