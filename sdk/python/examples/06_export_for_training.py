"""
Pull every unit + every judgment for a job. Ready to fold into a training
dataset (HuggingFace datasets, pandas, whatever).
"""
import csv, json
from labelhive import LabelHive

JOB_ID = 4
OUT_PATH = "labeled.jsonl"

with LabelHive("http://localhost:8080", user="demo") as hive:
    dump = hive.export_job(JOB_ID)
    print(f"Job '{dump['name']}': {dump['totalUnits']} units, {dump['totalJudgments']} judgments")

    # Choose a per-row consensus answer: majority of non-gold judgments,
    # falling back to gold answer where present.
    with open(OUT_PATH, "w") as f:
        for u in dump["units"]:
            judgments = [j for j in u["judgments"] if not j["wasGold"]]
            consensus = None
            if judgments:
                from collections import Counter
                ans = Counter(json.dumps(j["answer"], sort_keys=True) for j in judgments)
                consensus = json.loads(ans.most_common(1)[0][0])
            row = {
                "unit_id": u["id"],
                "external_id": u.get("externalId"),
                "payload": u["payload"],
                "gold_answer": u.get("goldAnswer"),
                "consensus_answer": consensus,
                "judgments": u["judgments"],
            }
            f.write(json.dumps(row) + "\n")

    print(f"Wrote {OUT_PATH}")
