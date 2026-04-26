
import requests
import base64
import json

LAPTOP_IP  = "10.0.0.115"
OLLAMA_URL = f"http://{LAPTOP_IP}:11434/api/generate"

# ── Ground truth ──────────────────────────────────────────────────
# For each image, list exactly what items are actually in the fridge
# Edit these to match YOUR actual fridge photos
GROUND_TRUTH = {
    "ss1.png": [
    "lettuce", "broccoli", "cauliflower", "bell pepper",
    "parsley", "zucchini", "beetroot", "radish",
    "red cabbage", "pomegranate", "tomato",
    "chili pepper", "orange", "lemon",
    "grapefruit", "carrot", "melon",
    "cucumber", "custard apple"
],
    "ss5.png": ["apples", "oranges"],
    "ss6.png": ["carrots", "cabbage"],
}

def encode_image(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")

def normalize(text):
    """lowercase, strip punctuation for fair comparison"""
    import re
    return re.sub(r'[^a-z\s]', '', text.lower()).split()

def evaluate_image(image_path, ground_truth_items):
    resp = requests.post(OLLAMA_URL, json={
        "model":  "minicpm-v",
        "prompt": "List the visible food ingredients in this fridge clearly, separated by commas.",
        "stream": False,
        "images": [encode_image(image_path)]
    }, timeout=120)

    raw_output = resp.json().get("response", "").strip()
    detected_words = normalize(raw_output)

    # Check each ground truth item
    tp = 0  # correctly detected
    fp = 0  # detected but not in fridge
    fn = 0  # in fridge but not detected

    results = []
    for item in ground_truth_items:
        item_words = normalize(item)
        # Check if any word of the item appears in detected output
        found = any(w in detected_words for w in item_words)
        if found:
            tp += 1
            results.append(f"  ✅ {item}")
        else:
            fn += 1
            results.append(f"  ❌ {item} (missed)")

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0
    recall    = tp / (tp + fn) if (tp + fn) > 0 else 0
    f1        = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0

    return {
        "image":     image_path,
        "raw":       raw_output[:200],
        "tp":        tp,
        "fn":        fn,
        "precision": round(precision, 3),
        "recall":    round(recall, 3),
        "f1":        round(f1, 3),
        "details":   results
    }

# ── Run evaluation ────────────────────────────────────────────────
print("Running LLaVA accuracy evaluation...\n")
all_results = []

for image, truth in GROUND_TRUTH.items():
    print(f"Image: {image}")
    print(f"Ground truth: {truth}")
    r = evaluate_image(image, truth)
    print(f"LLaVA output: {r['raw']}")
    for d in r["details"]:
        print(d)
    print(f"Precision: {r['precision']} | Recall: {r['recall']} | F1: {r['f1']}\n")
    all_results.append(r)

# ── Summary ───────────────────────────────────────────────────────
avg_precision = sum(r["precision"] for r in all_results) / len(all_results)
avg_recall    = sum(r["recall"]    for r in all_results) / len(all_results)
avg_f1        = sum(r["f1"]        for r in all_results) / len(all_results)

print(f"{'='*50}")
print(f"LLAVA ACCURACY SUMMARY ({len(all_results)} images)")
print(f"{'='*50}")
print(f"  Avg Precision : {avg_precision:.3f}")
print(f"  Avg Recall    : {avg_recall:.3f}")
print(f"  Avg F1 Score  : {avg_f1:.3f}")