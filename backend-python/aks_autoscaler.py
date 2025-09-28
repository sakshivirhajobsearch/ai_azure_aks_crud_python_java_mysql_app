from kubernetes import client, config
from ai_scaler import predict_replicas
from ai_aks import db_cursor, db
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
        print("⚠️ Kubernetes config not found. Running in MOCK mode.")
        use_real_k8s = False

if use_real_k8s:
    apps_v1 = client.AppsV1Api()
else:
    # Mock client for local testing
    class MockDeployment:
        spec = type("spec", (), {"replicas": 1})()
    class MockAppsV1Api:
        def read_namespaced_deployment(self, name, namespace):
            print(f"[MOCK] Reading deployment {name} in namespace {namespace}")
            return MockDeployment()
        def patch_namespaced_deployment(self, name, namespace, deployment):
            print(f"[MOCK] Patched deployment {name} to {deployment.spec.replicas} replicas")
    apps_v1 = MockAppsV1Api()

# ---------------------------
# Deployment Autoscaling
# ---------------------------
def scale_deployment(deployment_name: str, namespace: str, cpu_usage: float):
    if not namespace:
        namespace = "default"

    recommended_replicas = predict_replicas(cpu_usage)
    deployment = apps_v1.read_namespaced_deployment(deployment_name, namespace)
    current_replicas = deployment.spec.replicas

    if current_replicas != recommended_replicas:
        deployment.spec.replicas = recommended_replicas
        apps_v1.patch_namespaced_deployment(deployment_name, namespace, deployment)

        db_cursor.execute(
            "INSERT INTO scaling_logs (deployment_name, namespace, old_replicas, new_replicas) VALUES (%s, %s, %s, %s)",
            (deployment_name, namespace, current_replicas, recommended_replicas)
        )
        db.commit()
        return f"Scaled '{deployment_name}' from {current_replicas} → {recommended_replicas} replicas"
    else:
        return f"No scaling needed for '{deployment_name}'. Current replicas: {current_replicas}"

# ---------------------------
# MAIN
# ---------------------------
if __name__ == "__main__":
    dep_name = input("Deployment name: ").strip()
    ns = input("Namespace (leave empty for default): ").strip()
    cpu = input("Current CPU usage (%): ").strip()

    try:
        cpu_val = float(cpu)
        result = scale_deployment(dep_name, ns, cpu_val)
        print(result)
    except ValueError:
        print("Invalid CPU value. Please enter a number.")
    except Exception as e:
        print(f"Error: {e}")
