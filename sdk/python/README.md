# LabelHive Python SDK

Thin client over the LabelHive REST API. Single file, one dependency
(`httpx`).

## Install

```sh
cd sdk/python
pip install -e .
```

Or just copy `labelhive/client.py` into your project — it has no internal
imports beyond `httpx`.

## Quickstart

```python
from labelhive import LabelHive

with LabelHive("http://localhost:8080", user="demo") as hive:
    # Browse the catalog of registered template types and their schemas.
    for entry in hive.list_templates():
        schema = hive.get_template(entry["type"])
        print(schema["type"], "→ requires:", schema["payloadSchema"]["requiredFields"])

    # Build a sentiment job.
    job = hive.create_job(
        name="Beta sentiment dataset",
        template_type="MULTIPLE_CHOICE",
        template_config={"choices": ["positive", "neutral", "negative"]},
        gold_cadence=3,
        tasks_per_page=1,
    )

    # Upload some units.
    hive.upload_units(job["id"], [
        {"payload": {"text": "Loved the launch!"}, "goldAnswer": {"choice": "positive"}},
        {"payload": {"text": "Eh, fine I guess."}},
        {"payload": {"text": "Hated every minute."}, "goldAnswer": {"choice": "negative"}},
    ])

    # Later: pull labeled data for training.
    dump = hive.export_job(job["id"])
    for unit in dump["units"]:
        print(unit["payload"], "→", unit.get("goldAnswer"), unit["judgments"])
```

## Examples

| File | What it shows |
|---|---|
| [`examples/01_inspect_templates.py`](examples/01_inspect_templates.py) | Catalog templates and print payload schemas (use these to format datasets). |
| [`examples/02_build_job.py`](examples/02_build_job.py) | Create a job from scratch. |
| [`examples/03_upload_with_validation.py`](examples/03_upload_with_validation.py) | Validate a dataset against the template *before* uploading. |
| [`examples/04_run_as_worker.py`](examples/04_run_as_worker.py) | Pretend to be a worker — fetch a page, submit judgments. |
| [`examples/05_pull_reports.py`](examples/05_pull_reports.py) | Agreement / monitor / per-worker gold mistakes. |
| [`examples/06_export_for_training.py`](examples/06_export_for_training.py) | Pull every unit + every judgment for a job. |
| [`examples/07_clone_job.py`](examples/07_clone_job.py) | Clone with each combination of include-units / include-gold. |

## Auth model

In **debug profile** the server resolves identity from an `X-User-Id` header
(value can be a numeric id or a username). The SDK adds it for you when you
pass `user=`.

In **real-auth profile** pass `session_cookie=...` after a successful
form-login to `/login`.

## Type and payload schemas — your dataset contract

Every template advertises its config schema (knobs the job admin sets) and
its payload schema (the JSON shape each unit must conform to):

```python
schema = hive.get_template("SEARCH_RELEVANCE")
print(schema["payloadSchema"]["description"])
print("required fields:", schema["payloadSchema"]["requiredFields"])
print("example unit:", schema["payloadSchema"]["exampleUnit"])
```

The `validate_dataset` method runs a candidate dataset through the same
checks the upload endpoint will run — useful as a guardrail before a long
upload:

```python
result = hive.validate_dataset(
    "MULTIPLE_CHOICE",
    config={"choices": ["yes", "no"]},
    units=[
        {"payload": {"text": "ok"}, "goldAnswer": {"choice": "yes"}},
        {"payload": {"text": "bad"}, "goldAnswer": {"choice": "maybe"}},  # fails — not in choices
    ],
)
print(result["summary"])         # {'total': 2, 'valid': 1, 'invalid': 1, 'withGold': 1}
for err in result["errors"]:
    print(err)                    # {'index': 1, 'error': 'invalid choice: maybe'}
```
