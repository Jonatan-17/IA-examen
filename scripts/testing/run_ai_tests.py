"""Runs the mandatory AI tests (1, 2, 3, 6, 7 and 8) through the Spring Boot API and prints the evidence.

Every call goes through Spring Boot, like the frontend: n8n, Qdrant and OpenRouter are never called directly.
The script creates its own incidents, and leaves the knowledge base and n8n as it found them.

Requirements: the whole stack running (postgres, qdrant, n8n with both workflows published, backend) and
the 25 articles loaded and indexed. Test 8 restarts the n8n container with an invalid OpenRouter key and
then restarts it again with the real configuration, so it needs `docker compose` from the repo root.

Usage (from the repo root):
    python3 -I scripts/testing/run_ai_tests.py [--api http://localhost:8080/api] [--skip-test-8] [--json results.json]
"""
import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

INTERNET_ARTICLE = "Sin acceso a internet en el equipo"
APP_ERROR_ARTICLE = "Error de ejecución en la aplicación de facturación"


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method,
                                         headers={"Content-Type": "application/json"} if data else {})
        started = time.time()
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        elapsed = round(time.time() - started, 1)
        return status, (json.loads(raw) if raw else None), elapsed


def find_or_create(api, path, key, value, body):
    status, items, _ = api.call("GET", path)
    for item in items or []:
        if str(item.get(key, "")).lower() == value.lower():
            return item
    status, created, _ = api.call("POST", path, body)
    if status != 201:
        sys.exit(f"Could not create {path} '{value}': {status} {created}")
    return created


def create_incident(api, title, description, category_id, user_id, priority):
    status, incident, _ = api.call("POST", "/incidents", {
        "title": title, "description": description, "categoryId": category_id,
        "userId": user_id, "priority": priority})
    if status != 201:
        sys.exit(f"Could not create incident '{title}': {status} {incident}")
    return incident


def article_id(api, title):
    _, articles, _ = api.call("GET", "/knowledge-articles")
    for article in articles or []:
        if article["title"] == title:
            return article["id"]
    sys.exit(f"Article '{title}' not found: load and index the 25 articles first")


def assist(api, incident_id, description):
    return api.call("POST", f"/incidents/{incident_id}/assistance", {"description": description})


def summarize(response):
    if not isinstance(response, dict) or "queryId" not in response:
        return response
    answer = response.get("answer") or {}
    return {
        "queryId": response["queryId"],
        "status": response["status"],
        "sources": [f"#{s['articleId']} {s['title']} ({s['similarity']:.3f})" for s in response.get("sources", [])],
        "steps": len(answer.get("steps") or []),
        "solution": (answer.get("solution") or "")[:160] or None,
        "errorMessage": response.get("errorMessage"),
    }


def wait_for_n8n(timeout=120):
    end = time.time() + timeout
    while time.time() < end:
        try:
            urllib.request.urlopen(urllib.request.Request(
                "http://localhost:5678/webhook/rag-assistant", data=b"{}", method="POST",
                headers={"Content-Type": "application/json"}), timeout=5)
        except urllib.error.HTTPError as error:
            if error.code == 401:  # webhook registered (and rejecting the missing secret)
                return
        except Exception:  # noqa: BLE001 - still starting
            pass
        time.sleep(2)
    sys.exit("n8n webhooks did not come up in time")


def restart_n8n(env_override=None):
    env = None
    if env_override:
        import os
        env = {**os.environ, **env_override}
    subprocess.run(["docker", "compose", "up", "-d", "--force-recreate", "n8n"], cwd=ROOT, env=env,
                   check=True, capture_output=True)
    wait_for_n8n()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://localhost:8080/api")
    parser.add_argument("--skip-test-8", action="store_true")
    parser.add_argument("--json")
    args = parser.parse_args()
    api = Api(args.api)
    results = []

    def record(number, name, expected, passed, evidence):
        results.append({"test": number, "name": name, "expected": expected, "passed": passed, "evidence": evidence})
        print(f"\n[{'PASS' if passed else 'FAIL'}] Test {number}: {name}\n  expected: {expected}")
        print("  evidence: " + json.dumps(evidence, ensure_ascii=False, indent=2).replace("\n", "\n  "))

    print(f"Run started {datetime.now().isoformat(timespec='seconds')} against {args.api}")
    _, info, _ = api.call("GET", "/info")
    print(f"RAG configuration: threshold={info['similarityThreshold']} topK={info['topK']}")

    user = find_or_create(api, "/users", "email", "pruebas.ia@empresa.com",
                          {"name": "Pruebas IA", "email": "pruebas.ia@empresa.com", "area": "Soporte"})
    connectivity = find_or_create(api, "/categories", "name", "Conectividad",
                                  {"name": "Conectividad", "description": "Acceso a internet, red local, Wi-Fi y VPN"})
    applications = find_or_create(api, "/categories", "name", "Aplicaciones",
                                  {"name": "Aplicaciones", "description": "Software de negocio y sistemas internos"})
    hardware = find_or_create(api, "/categories", "name", "Hardware",
                              {"name": "Hardware", "description": "Equipos, impresoras y periféricos"})
    internet_id = article_id(api, INTERNET_ARTICLE)
    app_error_id = article_id(api, APP_ERROR_ARTICLE)

    # Test 1: connectivity incident answered from the right article
    description = "No hay conexión a internet en el piso 3 desde esta mañana."
    incident = create_incident(api, "Sin internet en el piso 3", description, connectivity["id"], user["id"], "P2")
    status, response, secs = assist(api, incident["id"], description)
    ids = [s["articleId"] for s in (response or {}).get("sources", [])]
    record(1, "Connectivity incident gets relevant solutions through RAG",
           f"ANSWERED with article #{internet_id} ({INTERNET_ARTICLE}) as a source",
           status == 200 and response["status"] == "ANSWERED" and internet_id in ids,
           {"incidentId": incident["id"], "http": status, "seconds": secs, **summarize(response)})

    # Test 2: application runtime error, check the retrieved articles
    description = "La aplicación de facturación se cierra con un error de ejecución al generar reportes."
    incident = create_incident(api, "Error de ejecución en facturación", description,
                               applications["id"], user["id"], "P2")
    status, response, secs = assist(api, incident["id"], description)
    ids = [s["articleId"] for s in (response or {}).get("sources", [])]
    record(2, "Application runtime error retrieves the right articles",
           f"ANSWERED with article #{app_error_id} ({APP_ERROR_ARTICLE}) as a source",
           status == 200 and response["status"] == "ANSWERED" and app_error_id in ids,
           {"incidentId": incident["id"], "http": status, "seconds": secs, **summarize(response)})

    # Test 3: topic without an article, the model must not make up a solution
    description = "La impresora 3D del laboratorio no completa la calibración de la cama."
    incident = create_incident(api, "Impresora 3D sin calibrar", description, hardware["id"], user["id"], "P3")
    status, response, secs = assist(api, incident["id"], description)
    record(3, "Incident without enough information: no invented solution",
           "NO_RESULTS, no answer and no sources",
           status == 200 and response["status"] == "NO_RESULTS" and not response.get("answer")
           and not response.get("sources"),
           {"incidentId": incident["id"], "http": status, "seconds": secs, **summarize(response)})

    # Test 6: deactivated article is never used as a source; reactivated afterwards
    description = "No hay conexión a internet en el piso 3 desde esta mañana."
    incident = create_incident(api, "Sin internet tras desactivar el artículo", description,
                               connectivity["id"], user["id"], "P2")
    deactivate_status, article, _ = api.call("PATCH", f"/knowledge-articles/{internet_id}/deactivate")
    try:
        status, response, secs = assist(api, incident["id"], description)
        ids = [s["articleId"] for s in (response or {}).get("sources", [])]
    finally:
        reactivate_status, _, _ = api.call("PATCH", f"/knowledge-articles/{internet_id}/activate")
    after_status, after, _ = assist(api, incident["id"], description)
    after_ids = [s["articleId"] for s in (after or {}).get("sources", [])]
    record(6, "Deactivated article is not used as a source",
           f"While #{internet_id} is inactive it is not a source (NO_RESULTS); once reactivated it is used again",
           deactivate_status == 200 and not article["active"] and status == 200 and internet_id not in ids
           and reactivate_status == 200 and internet_id in after_ids,
           {"incidentId": incident["id"], "deactivate": deactivate_status, "whileInactive": summarize(response),
            "reactivate": reactivate_status, "afterReactivation": summarize(after)})

    # Test 7: blank description, the AI flow is not executed
    status, response, secs = assist(api, incident["id"], "   ")
    record(7, "Blank description does not run the AI flow",
           "400 with a field error on description; no query is created",
           status == 400 and "description" in (response or {}).get("fields", {}),
           {"incidentId": incident["id"], "http": status, "seconds": secs,
            "message": (response or {}).get("message"), "fields": (response or {}).get("fields")})

    # Test 8: embedding service failure (invalid OpenRouter key), handled as ERROR
    if args.skip_test_8:
        print("\nTest 8 skipped (--skip-test-8)")
    else:
        description = "No hay conexión a internet en el piso 3 desde esta mañana."
        incident = create_incident(api, "Sin internet con fallo de embeddings", description,
                                   connectivity["id"], user["id"], "P2")
        print("\nTest 8: restarting n8n with an invalid OpenRouter key...")
        restart_n8n({"OPENROUTER_API_KEY": "invalid-key-for-test-8"})
        try:
            status, response, secs = assist(api, incident["id"], description)
        finally:
            print("Test 8: restoring n8n with the real configuration...")
            restart_n8n()
        record(8, "Embedding service failure is handled",
               "Query saved as ERROR with the reason; HTTP 200 with status ERROR, no exception, no answer",
               status == 200 and response["status"] == "ERROR" and bool(response.get("errorMessage"))
               and not response.get("answer"),
               {"incidentId": incident["id"], "http": status, "seconds": secs, **summarize(response)})

    passed = sum(r["passed"] for r in results)
    print(f"\n{passed}/{len(results)} tests passed")
    if args.json:
        Path(args.json).write_text(json.dumps({"runAt": datetime.now().isoformat(timespec="seconds"),
                                               "rag": info, "results": results}, ensure_ascii=False, indent=1),
                                   encoding="utf-8")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
