#!/usr/bin/env python3
"""Aplica o deploy financeiro no namespace selecionado."""
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = os.environ.get("K8S_NAMESPACE", "default")
DEPLOYMENTS = ("provedor-mock", "financeiro-api")
CREDENTIALS = ("FINANCEIRO_API_KEY", "PROVEDOR_CLIENT_SECRET", "MOCK_ADMIN_KEY")


def kubectl(*args, capture=False, payload=None):
    return subprocess.run(
        ["kubectl", "-n", NAMESPACE, *args], check=True, text=True,
        input=payload, capture_output=capture,
    ).stdout


def required(name):
    value = os.environ.get(name, "")
    if not value.strip():
        raise ValueError(f"Configure {name} antes do deploy.")
    return value


def preflight():
    for name in CREDENTIALS:
        required(name)
    nodes = json.loads(kubectl("get", "nodes", "-o", "json", capture=True))["items"]
    if not any(
        not node.get("spec", {}).get("unschedulable", False)
        and any(condition["type"] == "Ready" and condition["status"] == "True"
                for condition in node.get("status", {}).get("conditions", []))
        for node in nodes
    ):
        raise ValueError("O EKS precisa de pelo menos um no Ready que aceite Pods.")
    for name, keys in (
        ("financeiro-database-secret", (
            "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD")),
        ("rabbitmq-secret", ("username", "password")),
    ):
        try:
            data = json.loads(kubectl("get", "secret", name, "-o", "json", capture=True)).get("data", {})
        except subprocess.CalledProcessError as error:
            raise ValueError(f"Nao foi possivel consultar Secret {name} no namespace {NAMESPACE}.") from error
        if any(not data.get(key) for key in keys):
            raise ValueError(f"Secret {name} incompleto no namespace {NAMESPACE}.")
    kubectl("get", "storageclass", "rabbitmq-gp3")
    kubectl("get", "service", "rabbitmq")
    kubectl("rollout", "status", "statefulset/rabbitmq", "--timeout=180s")


def apply_secret(name, keys):
    resource = {
        "apiVersion": "v1", "kind": "Secret", "type": "Opaque",
        "metadata": {"name": name},
        "data": {key: base64.b64encode(required(key).encode()).decode() for key in keys},
    }
    try:
        kubectl("apply", "--server-side", "--field-manager=financeiro-deploy",
                "-f", "-", capture=True, payload=json.dumps(resource))
    except subprocess.CalledProcessError as error:
        raise ValueError(f"Falha ao aplicar Secret {name}. Verifique RBAC e ownership no EKS.") from error


def apply_manifests():
    registry = required("ECR_REGISTRY")
    tag = required("IMAGE_TAG")
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory) / "k8s"
        shutil.copytree(ROOT / "k8s", target)
        overlay = target.parent / "kustomization.yaml"
        overlay.write_text(json.dumps({
            "apiVersion": "kustomize.config.k8s.io/v1beta1",
            "kind": "Kustomization",
            "resources": ["k8s"],
            "images": [
                {"name": "oficina-financeiro-api", "newName": f"{registry}/oficina-financeiro-dev-repo", "newTag": tag},
                {"name": "oficina-provedor-mock", "newName": f"{registry}/oficina-provedor-mock-dev-repo", "newTag": tag},
            ],
        }), encoding="utf-8")
        kubectl("apply", "-k", directory)


def deploy():
    # Valida imagens e credenciais antes de alterar Secrets.
    required("ECR_REGISTRY")
    required("IMAGE_TAG")
    for name in CREDENTIALS:
        required(name)
    apply_secret("financeiro-api-secret", ("FINANCEIRO_API_KEY",))
    apply_secret("provedor-mock-secret", ("PROVEDOR_CLIENT_SECRET", "MOCK_ADMIN_KEY"))
    apply_manifests()
    try:
        for name in DEPLOYMENTS:
            kubectl("rollout", "status", f"deployment/{name}", "--timeout=900s")
        kubectl("wait", "pvc/provedor-mock-data", "--for=jsonpath={.status.phase}=Bound", "--timeout=30s")
    except subprocess.CalledProcessError:
        for args in (
            ("get", "deploy,pods,pvc", "-l", "app.kubernetes.io/part-of=oficina-financeiro", "-o", "wide"),
            ("get", "events", "--field-selector", "type=Warning", "--sort-by=.lastTimestamp"),
        ):
            try:
                kubectl(*args)
            except subprocess.CalledProcessError:
                pass
        raise


if __name__ == "__main__":
    try:
        actions = {"preflight": preflight, "deploy": deploy}
        if len(sys.argv) != 2 or sys.argv[1] not in actions:
            raise ValueError("Uso: python3 scripts/deploy-k8s.py {preflight|deploy}")
        actions[sys.argv[1]]()
    except ValueError as error:
        print(error, file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as error:
        print(f"Falha no kubectl (exit {error.returncode}).", file=sys.stderr)
        sys.exit(1)
