"""
Pull the three flavors of report a job produces:
- Agreement (inter-rater agreement, gold trust, NDCG for search jobs)
- Monitor (live completeness + per-worker leaderboard)
- Per-worker gold mistakes (where someone disagreed with gold)
"""
import json
from labelhive import LabelHive

JOB_ID = 4

with LabelHive("http://localhost:8080", user="demo") as hive:
    print("=== agreement ===")
    print(json.dumps(hive.agreement_report(JOB_ID), indent=2)[:1200], "...")

    print("\n=== monitor ===")
    monitor = hive.monitor_report(JOB_ID)
    pct = monitor["percentComplete"] * 100
    print(f"{pct:.1f}% complete · {monitor['judgmentsCollected']}/{monitor['judgmentsNeeded']} judgments")
    for w in monitor["workers"]:
        acc = w["accuracy"]
        print(f"  user {w['userId']}: {w['judgmentsTotal']:4} judgments · "
              f"gold acc {acc} · {w['throughputPerHour']:.0f}/h · {w['status']}")

    print("\n=== gold mistakes for user 2 ===")
    for m in hive.gold_mistakes(JOB_ID, 2):
        print(f"  unit #{m['unitId']}: their={m['yourAnswer']} gold={m['goldAnswer']}")
