import requests
import base64
import json

# Replace with your laptop's actual IP address
LAPTOP_IP = "10.0.0.93" 
OLLAMA_URL = f"http://{LAPTOP_IP}:11434/api/generate"

def encode_image(image_path):
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode('utf-8')

def analyze_fridge(image_path):
    image_base64 = encode_image(image_path)
    
    payload = {
        "model": "minicpm-v",
        "prompt": "List the visible food ingredients in this fridge clearly, separated by commas.",
        "stream": False,
        "images": [image_base64]
    }

    print("Sending image to Laptop for analysis for {}".format(payload["model"]))
    response = requests.post(OLLAMA_URL, json=payload)
    
    if response.status_code == 200:
        result = response.json()
        ingredients = result.get("response", "")
        print(f"Detected Ingredients: {ingredients}")
        return ingredients
    else:
        print(f"Error: {response.status_code} - {response.text}")
        return None

# Test with a sample image captured by your Pi Camera
analyze_fridge("ss5.png")

print("Analysing SS6 image")

analyze_fridge("ss6.png")