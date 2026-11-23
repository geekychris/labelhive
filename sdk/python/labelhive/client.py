"""
LabelHive client — single-file wrapper over the REST API.

Designed to be readable: every method maps 1:1 to a REST endpoint, the
response is the parsed JSON. Examples in `examples/` show the full
"build a job → upload data → pull labels" loop.

Auth model
----------
LabelHive's debug profile resolves identity from an ``X-User-Id`` header (the
value can be a numeric id or a username). Pass ``user="alice"`` and the
client adds the header on every request. Deployed instances sign users in
with OpenID Connect and ignore the header; SDK use against them is pending
(see docs/AUTH.md).

Quickstart
----------
>>> hive = LabelHive("http://localhost:8080", user="demo")
>>> hive.list_templates()
[{'type': 'BOUNDING_BOX'}, {'type': 'FREE_TEXT'}, ...]
"""

from __future__ import annotations

import json
from typing import Any, Iterable, Mapping, Optional
from urllib.parse import urljoin

try:
    import httpx
except ImportError as e:  # pragma: no cover
    raise ImportError("LabelHive SDK requires `httpx`. Install with `pip install httpx`.") from e


class LabelHiveError(RuntimeError):
    """Raised when the server responds with a non-2xx status."""

    def __init__(self, status: int, body: str):
        self.status = status
        self.body = body
        super().__init__(f"LabelHive {status}: {body}")


class LabelHive:
    """
    Parameters
    ----------
    base_url:
        e.g. ``http://localhost:8080``.
    user:
        Username (or numeric id) used in the ``X-User-Id`` header. Required for
        endpoints that resolve identity from the header (debug-auth profile).
    session_cookie:
        Optional ``JSESSIONID`` cookie value (not enough for a deployed OIDC
        instance yet: writes also need the CSRF token, and the site proxy its
        own sign-in; see docs/AUTH.md).
    timeout:
        Per-request timeout in seconds.
    """

    def __init__(
        self,
        base_url: str = "http://localhost:8080",
        *,
        user: Optional[str] = None,
        session_cookie: Optional[str] = None,
        timeout: float = 30.0,
    ):
        self._base = base_url.rstrip("/") + "/"
        headers = {"Content-Type": "application/json"}
        if user is not None:
            headers["X-User-Id"] = str(user)
        cookies = {"JSESSIONID": session_cookie} if session_cookie else {}
        self._client = httpx.Client(headers=headers, cookies=cookies, timeout=timeout)

    # ------------------------------------------------------------------ http

    def _do(self, method: str, path: str, *, json_body: Any = None) -> Any:
        url = urljoin(self._base, path.lstrip("/"))
        resp = self._client.request(method, url, json=json_body)
        if resp.status_code == 204 or not resp.content:
            return None
        if resp.status_code >= 400:
            raise LabelHiveError(resp.status_code, resp.text)
        return resp.json()

    def close(self):
        self._client.close()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()

    # ----------------------------------------------------------- templates

    def list_templates(self) -> list[dict]:
        """Available template types: ``[{'type': 'MULTIPLE_CHOICE'}, ...]``."""
        return self._do("GET", "/api/templates")

    def get_template(self, type_id: str) -> dict:
        """Variant + payload schema for a type (use these to build a config + dataset)."""
        return self._do("GET", f"/api/templates/{type_id}")

    def validate_dataset(self, type_id: str, *, config: Mapping[str, Any], units: Iterable[Mapping[str, Any]]) -> dict:
        """
        Dry-run a dataset against a template's validators *before* uploading.

        Returns ``{"ok": bool, "summary": {total,valid,invalid,withGold}, "errors": [{index, error}]}``.
        """
        return self._do("POST", f"/api/templates/{type_id}/validate-dataset",
                        json_body={"config": dict(config), "units": list(units)})

    # ---------------------------------------------------------------- jobs

    def list_jobs(self) -> list[dict]:
        return self._do("GET", "/api/jobs")

    def get_job(self, job_id: int) -> dict:
        return self._do("GET", f"/api/jobs/{job_id}")

    def create_job(
        self,
        *,
        name: str,
        template_type: str,
        template_config: Mapping[str, Any],
        description: Optional[str] = None,
        display_template: Optional[str] = None,
        gold_cadence: int = 3,
        tasks_per_page: int = 1,
        end_of_work_message: str = "COME_BACK_LATER",
        min_judgments: int = 3,
        max_judgments: Optional[int] = None,
        trust_floor: str = "0.70",
        promote_agreement_min: str = "0.90",
        auto_promote_to_gold: bool = False,
    ) -> dict:
        body = {
            "name": name,
            "description": description,
            "displayTemplate": display_template,
            "templateType": template_type,
            "templateConfig": dict(template_config),
            "goldCadence": gold_cadence,
            "tasksPerPage": tasks_per_page,
            "endOfWorkMessage": end_of_work_message,
            "minJudgments": min_judgments,
            "maxJudgments": max_judgments if max_judgments is not None else min_judgments,
            "trustFloor": trust_floor,
            "promoteAgreementMin": promote_agreement_min,
            "autoPromoteToGold": auto_promote_to_gold,
        }
        return self._do("POST", "/api/jobs", json_body=body)

    def update_job(self, job_id: int, **patch: Any) -> dict:
        # Snake → camel for known keys; raw keys pass through.
        camel = {
            "display_template": "displayTemplate",
            "template_config":  "templateConfig",
            "gold_cadence":     "goldCadence",
            "tasks_per_page":   "tasksPerPage",
            "end_of_work_message": "endOfWorkMessage",
            "min_judgments": "minJudgments",
            "max_judgments": "maxJudgments",
            "trust_floor":      "trustFloor",
            "promote_agreement_min": "promoteAgreementMin",
            "auto_promote_to_gold": "autoPromoteToGold",
        }
        body = {camel.get(k, k): v for k, v in patch.items()}
        return self._do("PUT", f"/api/jobs/{job_id}", json_body=body)

    def delete_job(self, job_id: int) -> None:
        self._do("DELETE", f"/api/jobs/{job_id}")

    def clone_job(self, job_id: int, *, include_units: bool = False, include_gold: bool = False) -> dict:
        return self._do("POST", f"/api/jobs/{job_id}/clone",
                        json_body={"includeUnits": include_units, "includeGold": include_gold})

    # --------------------------------------------------------------- units

    def list_units(self, job_id: int) -> list[dict]:
        return self._do("GET", f"/api/jobs/{job_id}/units")

    def upload_units(self, job_id: int, units: Iterable[Mapping[str, Any]]) -> list[dict]:
        return self._do("POST", f"/api/jobs/{job_id}/units", json_body=list(units))

    # --------------------------------------------------------- worker feed

    def next_page(self, job_id: int, n: Optional[int] = None) -> dict:
        path = f"/api/jobs/{job_id}/tasks/next"
        if n is not None:
            path += f"?n={n}"
        return self._do("GET", path)

    def job_instructions(self, job_id: int) -> dict:
        """Worker-facing instructions: description + exemplar units (gold + rationale)."""
        return self._do("GET", f"/api/jobs/{job_id}/tasks/instructions")

    def submit_judgment(self, job_id: int, unit_id: int, answer: Mapping[str, Any], *, time_ms: Optional[int] = None) -> dict:
        return self._do("POST", f"/api/jobs/{job_id}/tasks/{unit_id}/judgment",
                        json_body={"answer": dict(answer), "timeMs": time_ms})

    # ------------------------------------------------------------ reports

    def agreement_report(self, job_id: int) -> dict:
        return self._do("GET", f"/api/jobs/{job_id}/reports/agreement")

    def monitor_report(self, job_id: int) -> dict:
        return self._do("GET", f"/api/jobs/{job_id}/reports/monitor")

    def gold_mistakes(self, job_id: int, user_id: int) -> list[dict]:
        return self._do("GET", f"/api/jobs/{job_id}/reports/worker/{user_id}/gold-mistakes")

    # ------------------------------------------------------------- export

    def export_job(self, job_id: int) -> dict:
        """Full dump of a job's labeled data — units with all judgments."""
        return self._do("GET", f"/api/jobs/{job_id}/export")

    # --------------------------------------------------------------- admin

    def promote_to_gold(self, unit_id: int, *, answer: Optional[Mapping[str, Any]] = None, explanation: Optional[str] = None) -> dict:
        body = {}
        if answer is not None:      body["answer"] = dict(answer)
        if explanation is not None: body["explanation"] = explanation
        return self._do("POST", f"/api/admin/units/{unit_id}/promote-to-gold", json_body=body)

    def set_exemplar(self, unit_id: int, exemplar: bool = True) -> dict:
        """Mark a gold unit as a worker-facing exemplar (or unmark it)."""
        return self._do("POST", f"/api/admin/units/{unit_id}/exemplar",
                        json_body={"exemplar": bool(exemplar)})

    def promote_judgment_to_gold(self, judgment_id: int, *, explanation: Optional[str] = None) -> dict:
        return self._do("POST", f"/api/admin/judgments/{judgment_id}/promote-to-gold",
                        json_body={"explanation": explanation})

    def revert_worker(self, job_id: int, user_id: int) -> dict:
        return self._do("POST", f"/api/admin/jobs/{job_id}/workers/{user_id}/revert")

    def revert_judgment(self, judgment_id: int) -> dict:
        return self._do("POST", f"/api/admin/judgments/{judgment_id}/revert")

    def clear_quarantine(self, job_id: int, user_id: int) -> dict:
        return self._do("POST", f"/api/admin/jobs/{job_id}/workers/{user_id}/clear-quarantine")
