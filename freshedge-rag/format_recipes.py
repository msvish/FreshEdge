import json, re

def parse_r_vector(text):
    """Converts R's c("a", "b") format into a Python list"""
    if not text:
        return []
    # Extract all quoted strings from c("...", "...")
    return re.findall(r'"([^"]*)"', str(text))

print("Loading raw recipes...")
with open("recipes_raw.json") as f:
    raw = json.load(f)

recipes = []
for row in raw:
    ingredients = parse_r_vector(row.get("RecipeIngredientParts", ""))
    instructions = parse_r_vector(row.get("RecipeInstructions", ""))
    
    if not ingredients:  # skip recipes with no ingredients
        continue

    recipes.append({
        "id":           str(int(row["RecipeId"])),
        "title":        row.get("Name", "Untitled"),
        "ingredients":  ingredients,
        "instructions": instructions,
        "category":     row.get("RecipeCategory", ""),
        "calories":     row.get("Calories"),
        "rating":       row.get("AggregatedRating"),
        # This is what we'll embed — a single searchable text blob
        "embed_text":   f"{row.get('Name', '')}. Ingredients: {', '.join(ingredients)}. {row.get('Description', '')}"
    })

with open("recipes.json", "w") as f:
    json.dump(recipes, f, indent=2)

print(f"✅ Cleaned {len(recipes)} recipes → recipes.json")
print(f"\nSample:")
print(f"  Title:       {recipes[0]['title']}")
print(f"  Ingredients: {recipes[0]['ingredients']}")
print(f"  Embed text:  {recipes[0]['embed_text'][:120]}...")
