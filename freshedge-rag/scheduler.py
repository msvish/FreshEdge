import time
import schedule
import datetime
import requests
import base64
import json
import firebase_admin
from firebase_admin import credentials, firestore, messaging
import chromadb
from sentence_transformers import SentenceTransformer

# ── Config ────────────────────────────────────────────────────────
LAPTOP_IP       = "10.0.0.115"
OLLAMA_URL      = f"http://{LAPTOP_IP}:11434/api/generate"
FRIDGE_IMAGE    = "/home/raspberry/fridge.jpg"          # swap ss1.png → real camera output
SERVICE_ACCOUNT = "serviceaccount.json"
TOP_K           = 3

# ── Init Firebase ─────────────────────────────────────────────────
cred = credentials.Certificate(SERVICE_ACCOUNT)
firebase_admin.initialize_app(cred)
db   = firestore.client()

# ── Init RAG ──────────────────────────────────────────────────────
print("Loading RAG engine...")
model      = SentenceTransformer("all-MiniLM-L6-v2")
chroma     = chromadb.PersistentClient(path="./chroma_db")
collection = chroma.get_collection("recipes")
print(f"✅ {collection.count()} recipes ready")

# ─────────────────────────────────────────────────────────────────
def capture_image():
    """Capture fridge image using Pi Camera."""
    import subprocess
    print("📸 Capturing fridge image...")
    result = subprocess.run(
        ["rpicam-still", "-o", FRIDGE_IMAGE, "-t", "2000", "--nopreview"],
        capture_output=True, text=True
    )
    if result.returncode == 0:
        print("✅ Image captured from camera")
        return FRIDGE_IMAGE
    else:
        print(f"⚠️ Camera failed: {result.stderr}")
        print("📸 Falling back to ss5.png")
        return "ss5.png"

def analyze_fridge(image_path):
    """Send image to LLaVA on laptop, get ingredient list."""
    with open(image_path, "rb") as f:
        image_b64 = base64.b64encode(f.read()).decode("utf-8")

    try:
        resp = requests.post(OLLAMA_URL, json={
            "model":  "minicpm-v",
            "prompt": "List the visible food ingredients in this fridge clearly, separated by commas.",
            "stream": False,
            "images": [image_b64]
        }, timeout=120)
        if resp.status_code == 200:
            ingredients = resp.json().get("response", "").strip()
            print(f"🥦 Detected: {ingredients}")
            return ingredients
        else:
            print(f"❌ LLaVA error: {resp.status_code}")
            return None
    except Exception as e:
        print(f"❌ Cannot reach Ollama at {LAPTOP_IP}: {e}")
        return None

def retrieve_recipes(ingredients_text):
    """Step 2: RAG — embed query, find top-k recipes"""
    query_vector = model.encode(ingredients_text).tolist()
    results = collection.query(
        query_embeddings=[query_vector],
        n_results=TOP_K,
        include=["metadatas", "distances"]
    )
    recipes = []
    for i in range(len(results["ids"][0])):
        meta = results["metadatas"][0][i]
        formatted_steps = "\n".join(
            [f"{idx+1}. {step}" for idx, step in enumerate(meta["instructions"])]
        ) if meta.get("instructions") else ""

        r = {
            "rank":         i + 1,
            "title":        meta["title"],
            "ingredients":  meta["ingredients"],
            "instructions": formatted_steps,
            "category":     meta.get("category", ""),
            "calories":     meta.get("calories", ""),
            "similarity":   round(1 - results["distances"][0][i], 3)
        }
        recipes.append(r)
        print(f"   #{r['rank']} {r['title']} ({r['similarity']})")
    return recipes

def generate_suggestion(ingredients, recipes):
    """Step 3: Phi-3 selects best recipe and returns structured JSON"""
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
    print("💭 Generating suggestion with Phi-3...")

    try:
        resp = requests.post(OLLAMA_URL, json={
            "model":  "phi3:latest",
            "prompt": prompt,
            "stream": False
        }, timeout=180)
    except Exception as e:
        print(f"❌ Cannot reach Ollama: {e}")
        return None

    raw_output = resp.json().get("response", "").strip()

    # Safe JSON parse with fallback
    try:
        suggestion_json = json.loads(raw_output)
    except Exception:
        print("⚠️  JSON parsing failed — using fallback")
        suggestion_json = {
            "title":        recipes[0]["title"],
            "instructions": recipes[0]["instructions"],
            "suggestion":   raw_output[:120]
        }

    print(f"✅ Suggestion: {suggestion_json.get('suggestion', '')[:100]}")
    return suggestion_json

def save_to_history(meal_name, suggestion_json):
    """Step 5: Save result to Firestore history"""
    now      = datetime.datetime.now()
    date_str = now.strftime("%Y-%m-%d")
    day_str  = now.strftime("%A")

    db.collection("recipe_history") \
      .document("user_1") \
      .collection("days") \
      .document(date_str) \
      .collection(meal_name) \
      .add({
          "title":        suggestion_json["title"],
          "instructions": suggestion_json["instructions"],
          "suggestion":   suggestion_json.get("suggestion", ""),
          "meal_type":    meal_name,
          "date":         date_str,
          "day_label":    day_str,
          "timestamp":    firestore.SERVER_TIMESTAMP
      })
    print(f"💾 Saved → days/{date_str}/{meal_name}")

def send_notification(meal_name, suggestion_json):
    """Step 4: Fire FCM push notification"""
    token_doc = db.collection("fcm_tokens").document("user_1").get()
    if not token_doc.exists:
        print("❌ No FCM token in Firestore")
        return

    fcm_token = token_doc.to_dict().get("token")
    message = messaging.Message(
        notification=messaging.Notification(
            title=f"🍽️ {suggestion_json['title']}",
            body=suggestion_json["instructions"][:200]
        ),
        data={"screen": "current"},
        token=fcm_token
    )
    response = messaging.send(message)
    print(f"📱 Notification sent: {response}")

def run_pipeline(meal_name):
    """Full pipeline: camera → LLaVA → RAG → Phi-3 → Firestore → FCM"""
    print(f"\n{'='*60}")
    print(f"🚀 Pipeline: {meal_name} at {datetime.datetime.now()}")
    print(f"{'='*60}")

    # Step 1
    image_path  = capture_image()
    ingredients = analyze_fridge(image_path)
    if not ingredients:
        return

    # Step 2
    print(f"\n🔍 Retrieving recipes...")
    recipes = retrieve_recipes(ingredients)

    # Step 3
    suggestion_json = generate_suggestion(ingredients, recipes)
    if not suggestion_json:
        return

    # Step 4
    send_notification(meal_name, suggestion_json)

    # Step 5
    save_to_history(meal_name, suggestion_json)

    print(f"\n✅ {meal_name} pipeline complete")

def read_meal_times():
    """Read meal times from Firestore (set by Android app)"""
    doc = db.collection("meal_times").document("user_1").get()
    if not doc.exists:
        print("⚠️  No meal times in Firestore, using defaults")
        return "08:00", "12:00", "18:00"
    data = doc.to_dict()
    return data["breakfast"], data["lunch"], data["dinner"]

def subtract_one_hour(time_str):
    t  = datetime.datetime.strptime(time_str, "%H:%M")
    t -= datetime.timedelta(hours=1)
    return t.strftime("%H:%M")

def setup_schedule():
    breakfast, lunch, dinner = read_meal_times()
    print(f"\n📅 Meal times from Firestore:")
    print(f"   Breakfast : {breakfast} → trigger at {subtract_one_hour(breakfast)}")
    print(f"   Lunch     : {lunch} → trigger at {subtract_one_hour(lunch)}")
    print(f"   Dinner    : {dinner} → trigger at {subtract_one_hour(dinner)}")

    schedule.clear()
    schedule.every().day.at(subtract_one_hour(breakfast)).do(run_pipeline, "Breakfast")
    schedule.every().day.at(subtract_one_hour(lunch)).do(run_pipeline, "Lunch")
    schedule.every().day.at(subtract_one_hour(dinner)).do(run_pipeline, "Dinner")
    schedule.every(5).minutes.do(setup_schedule)

# ── Start ─────────────────────────────────────────────────────────
setup_schedule()
print("\n⏰ Scheduler running — waiting for meal windows...")
print("   Press Ctrl+C to stop\n")

while True:
    schedule.run_pending()
    time.sleep(30)