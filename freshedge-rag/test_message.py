import sys
sys.path.insert(0, ".")

import requests, base64, json
import firebase_admin
from firebase_admin import credentials, firestore, messaging
import chromadb
from sentence_transformers import SentenceTransformer
import datetime

# ── Init ──────────────────────────────────────────────────────────
LAPTOP_IP  = "10.0.0.93"
OLLAMA_URL = f"http://{LAPTOP_IP}:11434/api/generate"

cred = credentials.Certificate("serviceaccount.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

model = SentenceTransformer("all-MiniLM-L6-v2")
chroma = chromadb.PersistentClient(path="./chroma_db")
collection = chroma.get_collection("recipes")

print(f"✅ RAG ready — {collection.count()} recipes")


# ── Step 1: Image → Ingredients ───────────────────────────────────
IMAGE = "ss1.png"
print(f"\n📸 Step 1: Analyzing {IMAGE}...")

with open(IMAGE, "rb") as f:
    image_b64 = base64.b64encode(f.read()).decode("utf-8")

resp = requests.post(OLLAMA_URL, json={
    "model": "minicpm-v",
    "prompt": "List the visible food ingredients in this fridge clearly, separated by commas.",
    "stream": False,
    "images": [image_b64]
}, timeout=120)

ingredients = resp.json().get("response", "").strip()
print(f"🥦 Detected ingredients: {ingredients}")


# ── Step 2: RAG ───────────────────────────────────────────────────
print(f"\n🔍 Step 2: Retrieving recipes...")

query_vector = model.encode(ingredients).tolist()

results = collection.query(
    query_embeddings=[query_vector],
    n_results=3,
    include=["metadatas", "distances"]
)

recipes = []

for i in range(len(results["ids"][0])):
    meta = results["metadatas"][0][i]

    formatted_steps = "\n".join(
        [f"{idx+1}. {step}" for idx, step in enumerate(meta["instructions"])]
    )

    r = {
        "rank": i + 1,
        "title": meta["title"],
        "ingredients": ", ".join(meta["ingredients"]),
        "instructions": formatted_steps,
        "similarity": round(1 - results["distances"][0][i], 3)
    }

    recipes.append(r)
    print(f"   #{r['rank']} {r['title']} ({r['similarity']})")


# ── Step 3: LLM Selection ─────────────────────────────────────────
print(f"\n💭 Step 3: Generating suggestion...")

context = "\n\n".join([
    f"Recipe {r['rank']}:\n"
    f"Title: {r['title']}\n"
    f"Ingredients: {r['ingredients']}\n"
    f"Instructions:\n{r['instructions']}"
    for r in recipes
])

prompt = f"""
You are a cooking assistant.

Fridge ingredients:
{ingredients}

Recipes:
{context}

Select the best recipe.

Return ONLY JSON in this format:
{{
  "title": "...",
  "instructions": "...",
  "suggestion": "Short 1-line recommendation to the user"
}}

IMPORTANT:
- Use only provided recipes
- Do NOT invent anything
"""

resp = requests.post(OLLAMA_URL, json={
    "model": "phi3",
    "prompt": prompt,
    "stream": False
}, timeout=180)

raw_output = resp.json().get("response", "").strip()


# ── Step 3.1: Safe JSON Parse ─────────────────────────────────────
try:
    suggestion_json = json.loads(raw_output)
except:
    print("⚠️ JSON parsing failed — using fallback")

    suggestion_json = {
        "title": recipes[0]["title"],
        "instructions": recipes[0]["instructions"],
        "suggestion": raw_output[:120]
    }

print("\n✅ Final Output:")
print(suggestion_json)


# ── Step 4: Send Notification ─────────────────────────────────────
print(f"\n📱 Sending notification...")

token_doc = db.collection("fcm_tokens").document("user_1").get()
fcm_token = token_doc.to_dict().get("token")

# message = messaging.Message(
#     notification=messaging.Notification(
#         title=f"🍽️ {suggestion_json['title']}",
#         body=suggestion_json["suggestion"]
#     ),
#     data={"screen": "current"},
#     token=fcm_token
# )

# response = messaging.send(message)
# print(f"✅ Notification sent: {response}")


# ── Step 5: Store in Firestore ────────────────────────────────────
now = datetime.datetime.now()
date_str = now.strftime("%Y-%m-%d")
day_str  = now.strftime("%A")

hour = now.hour
meal_type = "Breakfast" if hour < 11 else "Lunch" if hour < 16 else "Dinner"

db.collection("recipe_history") \
  .document("user_1") \
  .collection("days") \
  .document(date_str) \
  .collection(meal_type) \
  .add({
      "title": suggestion_json["title"],
      "instructions": suggestion_json["instructions"],
      "suggestion": suggestion_json["suggestion"],
      "meal_type": meal_type,
      "date": date_str,
      "day_label": day_str,
      "timestamp": firestore.SERVER_TIMESTAMP
  })

print("\n🎉 Pipeline complete — check your phone!")