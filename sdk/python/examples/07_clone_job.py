"""
The three flavors of job cloning:

- Settings only — fresh dataset, same template/thresholds.
- Settings + units (no gold) — same data to be re-labeled from scratch.
- Settings + units + gold — full carbon copy.
"""
from labelhive import LabelHive

JOB_ID = 4

with LabelHive("http://localhost:8080", user="demo") as hive:
    settings_only = hive.clone_job(JOB_ID)
    print("settings only      →", settings_only["id"], settings_only["name"])

    units_no_gold = hive.clone_job(JOB_ID, include_units=True)
    print("units (no gold)    →", units_no_gold["id"])

    full = hive.clone_job(JOB_ID, include_units=True, include_gold=True)
    print("everything         →", full["id"])
