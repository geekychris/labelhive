"""
Validate a dataset against a template *before* uploading it.

Catches bad-shape units (missing fields, gold answers that don't match the
template) up front so you don't get a partial upload.
"""
from labelhive import LabelHive, LabelHiveError

DATASET = [
    {"payload": {"text": "Just shipped!"},          "goldAnswer": {"choice": "positive"}},
    {"payload": {"text": "Open to feedback."}},
    {"payload": {"text": "Why is everything broken"}, "goldAnswer": {"choice": "maybe"}},  # bad
]

CONFIG = {"choices": ["positive", "neutral", "negative"]}

with LabelHive("http://localhost:8080", user="demo") as hive:
    result = hive.validate_dataset("MULTIPLE_CHOICE", config=CONFIG, units=DATASET)
    print("summary:", result["summary"])
    if not result["ok"]:
        for err in result["errors"]:
            print(f"  unit[{err['index']}]: {err['error']}")
        print("Fix the dataset and re-run before uploading.")
        raise SystemExit(1)

    job = hive.create_job(name="Validated upload demo",
                          template_type="MULTIPLE_CHOICE",
                          template_config=CONFIG)
    hive.upload_units(job["id"], DATASET[:2])  # only the valid rows
    print(f"Uploaded {len(DATASET[:2])} units onto job #{job['id']}")
