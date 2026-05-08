"""Create a bounding-box detection job, upload images with two exemplar gold answers,
and print the worker-facing instructions endpoint so you can see what labelers will see.

Run after `docker compose up` so the backend (and demo seed) is available, e.g.:

    python examples/08_create_bbox_job.py
"""
from labelhive import LabelHive


def main() -> None:
    with LabelHive("http://localhost:8080", user="demo") as hive:
        job = hive.create_job(
            name="SDK: Find the dogs",
            description=(
                "Draw a tight bounding box around every dog in each image. "
                "Use 'dog' for any dog and 'puppy' if it's clearly a puppy. "
                "If there are no dogs in the frame, leave the answer empty."
            ),
            template_type="BOUNDING_BOX",
            template_config={
                "labels": ["dog", "puppy"],
                "imageField": "imageUrl",
            },
            gold_cadence=4,
            min_judgments=3,
        )
        print("Created job:", job["id"], "—", job["name"])

        # Two exemplars (set exemplar=True so the worker's instructions page shows them)
        # plus a few unlabeled units. Coordinates are image-pixel space.
        units = [
            {
                "externalId": "sdk-bbox-1",
                "payload": {"imageUrl": "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=800&h=600&fit=crop"},
                "goldAnswer": {"boxes": [
                    {"x": 220, "y": 120, "w": 360, "h": 420, "label": "dog"},
                ]},
                "goldExplanation": "Single labrador, centered. Box hugs the body — exclude the grass.",
                "exemplar": True,
            },
            {
                "externalId": "sdk-bbox-2",
                "payload": {"imageUrl": "https://images.unsplash.com/photo-1561037404-61cd46aa615b?w=800&h=600&fit=crop"},
                "goldAnswer": {"boxes": [
                    {"x": 260, "y": 200, "w": 240, "h": 220, "label": "puppy"},
                ]},
                "goldExplanation": "Small puppy — use the 'puppy' label, not 'dog'.",
                "exemplar": True,
            },
            # Plain unlabeled units — workers will draw boxes on these.
            {"externalId": "sdk-bbox-u1", "payload": {"imageUrl": "https://images.unsplash.com/photo-1587300003388-59208cc962cb?w=800&h=600&fit=crop"}},
            {"externalId": "sdk-bbox-u2", "payload": {"imageUrl": "https://images.unsplash.com/photo-1518717758536-85ae29035b6d?w=800&h=600&fit=crop"}},
            {"externalId": "sdk-bbox-u3", "payload": {"imageUrl": "https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=800&h=600&fit=crop"}},
        ]
        created = hive.upload_units(job["id"], units)
        print(f"Uploaded {len(created)} units ({sum(1 for u in units if u.get('exemplar'))} exemplar)")

        # If you want to flip an existing gold unit to exemplar after the fact:
        # hive.set_exemplar(unit_id, exemplar=True)

        # Inspect what a worker will see on the instructions page.
        instructions = hive.job_instructions(job["id"])
        print(f"\nInstructions for job {instructions['jobId']} — {instructions['name']}")
        print(f"  description: {instructions['description'][:60]}...")
        print(f"  exemplars: {len(instructions['exemplars'])}")
        for ex in instructions["exemplars"]:
            n_boxes = len(ex["goldAnswer"].get("boxes", []))
            why = (ex.get("goldExplanation") or "")[:50]
            print(f"    unit {ex['unitId']}: {n_boxes} box(es) — {why}…")


if __name__ == "__main__":
    main()
