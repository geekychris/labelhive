"""
Inspect every registered template — what knobs and what payload shape.

Use this to figure out *how to format a dataset* before you build a job.
"""
from labelhive import LabelHive

with LabelHive("http://localhost:8080", user="demo") as hive:
    for entry in hive.list_templates():
        t = hive.get_template(entry["type"])
        print(f"\n=== {t['type']} ===")
        print(t["payloadSchema"]["description"])
        print("required payload fields:", t["payloadSchema"]["requiredFields"])
        print("example unit:")
        import json; print(json.dumps(t["payloadSchema"]["exampleUnit"], indent=2))
        print("variant config knobs:")
        for f in t["variantSchema"]["fields"]:
            req = "required" if f.get("required") else "optional"
            print(f"  {f['name']:<20} ({f['kind']}, {req})")
