import json
import chromadb
from sentence_transformers import SentenceTransformer
from tqdm import tqdm

# ── 1. Load recipes ──────────────────────────────────────────────
print("📂 Loading recipes...")
with open("recipes.json") as f:
    recipes = json.load(f)
print(f"   {len(recipes)} recipes loaded")

# ── 2. Load the embedding model ──────────────────────────────────
print("\n🤖 Loading all-MiniLM-L6-v2 (downloads once, ~90MB)...")
model = SentenceTransformer("all-MiniLM-L6-v2")
print("   Model ready")

# ── 3. Set up ChromaDB (local, stored on disk) ───────────────────
print("\n🗄️  Setting up ChromaDB...")
client = chromadb.PersistentClient(path="./chroma_db")

# Delete collection if it exists (clean slate)
try:
    client.delete_collection("recipes")
    print("   Cleared existing collection")
except:
    pass

collection = client.create_collection(
    name="recipes",
    metadata={"hnsw:space": "cosine"}   # cosine similarity = best for text
)
print("   Collection created")

# ── 4. Embed in batches of 100 ───────────────────────────────────
print(f"\n⚡ Embedding {len(recipes)} recipes...")
BATCH = 100

for i in tqdm(range(0, len(recipes), BATCH), desc="Embedding batches"):
    batch = recipes[i : i + BATCH]

    ids        = [r["id"]         for r in batch]
    texts      = [r["embed_text"] for r in batch]
    metadatas  = [
        {
            "title":       r["title"],
            "category":    r["category"],
            "ingredients": ", ".join(r["ingredients"]),
            "instructions":       r["instructions"],
            "calories":    str(r["calories"] or ""),
            "rating":      str(r["rating"]   or ""),
        }
        for r in batch
    ]

    embeddings = model.encode(texts, show_progress_bar=False).tolist()

    collection.add(
        ids        = ids,
        embeddings = embeddings,
        metadatas  = metadatas,
        documents  = texts
    )

print(f"\n✅ Done! {collection.count()} recipes stored in ChromaDB")
