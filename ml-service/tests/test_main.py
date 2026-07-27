from fastapi.testclient import TestClient

from src.main import _warmup_hmm, app

client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy", "service": "ml-service"}


def test_metrics():
    response = client.get("/metrics")
    assert response.status_code == 200


def test_warmup_hmm_completes_without_error():
    _warmup_hmm()


def test_lifespan_runs_warmup_and_serves_requests():
    with TestClient(app) as warm_client:
        response = warm_client.get("/health")
        assert response.status_code == 200
