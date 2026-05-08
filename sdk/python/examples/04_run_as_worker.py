"""
Act as a worker — fetch a page, label every task, submit. Great for
hybrid workflows where an LLM provides initial labels and humans review.
"""
import time
from labelhive import LabelHive

JOB_ID = 4  # the seeded sentiment job in debug mode

# Toy "labeler": always picks "positive" (replace with your model).
def model_label(payload):
    return {"choice": "positive"}

with LabelHive("http://localhost:8080", user="alice") as hive:
    while True:
        page = hive.next_page(JOB_ID)
        if not page["tasks"]:
            print("Done:", page.get("message"))
            break
        for task in page["tasks"]:
            answer = model_label(task["payload"])
            hive.submit_judgment(JOB_ID, task["id"], answer, time_ms=200)
            print(f"  unit #{task['id']}: {task['payload']} → {answer}")
        time.sleep(0.1)  # be polite
