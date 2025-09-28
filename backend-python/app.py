from flask import Flask, request, jsonify
from aks_processor import list_nodes, list_pods, add_node, add_pod, autoscale_deployment

app = Flask(__name__)

# === Nodes ===
@app.route("/nodes", methods=["GET"])
def get_nodes():
    return jsonify(list_nodes())

@app.route("/nodes", methods=["POST"])
def create_node():
    data = request.json
    add_node(data["name"], data["status"], data["kubelet_version"])
    return jsonify({"message": "Node added"})

# === Pods ===
@app.route("/pods", methods=["GET"])
def get_pods():
    return jsonify(list_pods())

@app.route("/pods", methods=["POST"])
def create_pod():
    data = request.json
    add_pod(data["name"], data["namespace"], data["status"])
    return jsonify({"message": "Pod added"})

# === Autoscaling ===
@app.route("/autoscale", methods=["POST"])
def autoscale():
    data = request.json
    msg = autoscale_deployment(data["deployment_name"], data.get("namespace", "default"), float(data["cpu_usage"]))
    return jsonify({"message": msg})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)
