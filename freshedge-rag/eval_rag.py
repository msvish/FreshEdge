import chromadb
from sentence_transformers import SentenceTransformer

# ── Init RAG ──────────────────────────────────────────────────────
model      = SentenceTransformer("all-MiniLM-L6-v2")
chroma     = chromadb.PersistentClient(path="./chroma_db")
collection = chroma.get_collection("recipes")

# ── 50 test cases ─────────────────────────────────────────────────
# Format: ("ingredient query", "expected recipe title keyword")
TEST_CASES = [
    ("blueberries, yogurt, sugar",           "blueberry"),
    ("chicken breast, garlic, olive oil",    "chicken"),
    ("eggs, spinach, cheese",                "spinach"),
    ("carrots, cabbage",                     "cabbage"),
    ("apples, cinnamon, sugar",              "apple"),
    ("pasta, tomato, garlic",                "pasta"),
    ("salmon, lemon, dill",                  "salmon"),
    ("beef, onion, potato",                  "beef"),
    ("mushrooms, cream, butter",             "mushroom"),
    ("shrimp, garlic, butter",               "shrimp"),
    ("banana, milk, oats",                   "banana"),
    ("lemon, sugar, butter",                 "lemon"),
    ("broccoli, cheese, cream",              "broccoli"),
    ("turkey, cranberry, stuffing",          "turkey"),
    ("pork, apple, sage",                    "pork"),
    ("tofu, soy sauce, ginger",              "tofu"),
    ("zucchini, tomato, basil",              "zucchini"),
    ("rice, chicken, broth",                 "chicken"),
    ("flour, butter, sugar, eggs",           "cake"),
    ("oats, honey, banana",                  "oat"),
    ("tomato, mozzarella, basil",            "tomato"),
    ("lamb, rosemary, garlic",               "lamb"),
    ("cucumber, yogurt, dill",               "cucumber"),
    ("corn, butter, lime",                   "corn"),
    ("sweet potato, brown sugar, butter",    "sweet potato"),
    ("avocado, lime, cilantro",              "avocado"),
    ("chocolate, butter, sugar, flour",      "chocolate"),
    ("spinach, feta, eggs",                  "spinach"),
    ("potato, cream, cheese",                "potato"),
    ("tuna, mayo, celery",                   "tuna"),
    ("peach, sugar, butter",                 "peach"),
    ("cabbage, vinegar, sugar",              "cabbage"),
    ("ground beef, tomato, onion",           "beef"),
    ("cauliflower, cheese, cream",           "cauliflower"),
    ("asparagus, butter, lemon",             "asparagus"),
    ("eggplant, tomato, garlic",             "eggplant"),
    ("peanut butter, chocolate, oats",       "peanut butter"),
    ("strawberry, cream, sugar",             "strawberry"),
    ("coconut milk, curry, chicken",         "curry"),
    ("beet, goat cheese, walnut",            "beet"),
    ("leek, potato, cream",                  "leek"),
    ("kale, lemon, garlic",                  "kale"),
    ("mango, lime, chili",                   "mango"),
    ("chickpeas, tahini, lemon",             "chickpea"),
    ("pumpkin, cream, nutmeg",               "pumpkin"),
    ("cod, potato, cream",                   "cod"),
    ("apple, cheddar, mustard",              "apple"),
    ("quinoa, black beans, corn",            "quinoa"),
    ("ricotta, spinach, pasta",              "ricotta"),
    ("ham, cheese, eggs",                    "ham"),
]

# ── Evaluate Precision@3 ──────────────────────────────────────────
print(f"Running RAG Precision@3 evaluation ({len(TEST_CASES)} test cases)...\n")

hits   = 0
misses = 0
miss_list = []

for query, expected_keyword in TEST_CASES:
    vec = model.encode(query).tolist()
    results = collection.query(
        query_embeddings=[vec],
        n_results=3,
        include=["metadatas"]
    )
    top3_titles = [
        results["metadatas"][0][i]["title"].lower()
        for i in range(len(results["metadatas"][0]))
    ]
    # Check if expected keyword appears in any of top 3 titles
    matched = any(expected_keyword.lower() in t for t in top3_titles)
    if matched:
        hits += 1
        print(f"  ✅ '{query[:40]}' → {top3_titles[0][:40]}")
    else:
        misses += 1
        miss_list.append((query, expected_keyword, top3_titles))
        print(f"  ❌ '{query[:40]}' → expected '{expected_keyword}', got: {top3_titles[0][:30]}")

precision_at_3 = hits / len(TEST_CASES)

print(f"\n{'='*50}")
print(f"RAG PRECISION@3 RESULTS")
print(f"{'='*50}")
print(f"  Hits        : {hits}/{len(TEST_CASES)}")
print(f"  Misses      : {misses}/{len(TEST_CASES)}")
print(f"  Precision@3 : {precision_at_3:.3f} ({precision_at_3*100:.1f}%)")

if miss_list:
    print(f"\nMissed cases:")
    for q, kw, got in miss_list:
        print(f"  Query: {q[:50]}")
        print(f"  Expected keyword: '{kw}' | Got: {got[0][:40]}")