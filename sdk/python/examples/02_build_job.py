"""Create a job from scratch."""
from labelhive import LabelHive

with LabelHive("http://localhost:8080", user="demo") as hive:
    job = hive.create_job(
        name="Demo: AI vs human-written",
        description="Classify whether a passage was written by an LLM or a person.",
        template_type="MULTIPLE_CHOICE",
        template_config={
            "choices": ["human", "ai_generated", "uncertain"],
            "allowMultiple": False,
        },
        gold_cadence=3,
        tasks_per_page=1,
        end_of_work_message="COME_BACK_LATER",
        judgments_per_unit=3,
    )
    print("Created job:", job["id"], job["name"])
