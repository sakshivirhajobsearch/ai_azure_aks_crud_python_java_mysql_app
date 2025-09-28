def predict_replicas(cpu_usage: float) -> int:
    if cpu_usage <= 0:
        return 1
    return max(1, int(round(cpu_usage / 30)))
