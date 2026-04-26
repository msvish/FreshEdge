import chromadb
from sentence_transformers import SentenceTransformer
import json

# ── Load model + DB (reuses what's already on disk) ──────────────
model  = SentenceTransformer("all-MiniLM-L6-v2")
client = chromadb.PersistentClient(path="./chroma_db")
collection = client.get_collection("recipes")

def retrieve_recipes(llava_output: str, top_k: int = 3) -> list[dict]:
    """
    Takes LLaVA's ingredient text and returns top_k matching recipes.
    
    Args:
        llava_output: e.g. "eggs, spinach, milk, leftover rice"
        top_k:        how many recipes to return (3 for Phi-3 context)
    
    Returns:
        list of dicts with title, ingredients, and match score
    """
    # Embed the query the same way we embedded recipes
    query_vector = model.encode(llava_output).tolist()

    results = collection.query(
        query_embeddings=[query_vector],
        n_results=top_k,
        include=["metadatas", "documents", "distances"]
    )

    recipes = []
    for i in range(len(results["ids"][0])):
        meta     = results["metadatas"][0][i]
        distance = results["distances"][0][i]
        recipes.append({
            "rank":        i + 1,
            "title":       meta["title"],
            "ingredients": meta["ingredients"],
            "category":    meta["category"],
            "calories":    meta["calories"],
            "rating":      meta["rating"],
            "similarity":  round(1 - distance, 3),   # cosine: 1=perfect, 0=unrelated
        })
    return recipes

def format_for_phi3(llava_output: str, recipes: list[dict]) -> str:
    """
    Formats retrieved recipes as context for Phi-3 Mini.
    This string gets sent to Phi-3 to generate the final suggestion.
    """
    context = "\n\n".join([
        f"Recipe {r['rank']}: {r['title']}\n"
        f"Ingredients: {r['ingredients']}\n"
        f"Category: {r['category']} | Calories: {r['calories']} | Rating: {r['rating']}"
        for r in recipes
    ])

    return f"""You are a helpful meal planning assistant.

The user's fridge contains: {llava_output}

Here are the top matching recipes from the knowledge base:

{context}

Based on the available ingredients and these recipes, suggest the best meal option. 
Be concise — this will be sent as a phone notification."""


# ── Test it with a simulated LLaVA output ────────────────────────
if __name__ == "__main__":
    # Simulate what LLaVA would return after scanning the fridge
    test_inputs = [
        "eggs, spinach, milk, cheese",
        "chicken breast, broccoli, garlic, olive oil",
        "blueberries, yogurt, sugar",
    ]

    for llava_output in test_inputs:
        print(f"\n{'='*60}")
        print(f"🔍 LLaVA output: '{llava_output}'")
        print(f"{'='*60}")

        recipes = retrieve_recipes(llava_output, top_k=3)

        for r in recipes:
            print(f"\n  #{r['rank']} {r['title']}")
            print(f"     Similarity : {r['similarity']}")
            print(f"     Ingredients: {r['ingredients'][:80]}...")
            print(f"     Category   : {r['category']} | ⭐ {r['rating']}")

        print(f"\n📝 Phi-3 prompt preview:")
        print(format_for_phi3(llava_output, recipes)[:400] + "...")
