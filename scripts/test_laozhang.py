#!/usr/bin/env python3
"""
Тест laozhang.ai API — запусти локально.
pip install requests
LAOZHANG_API_KEY=твой_ключ python3 test_laozhang.py
"""
import requests, os, json

API_KEY = os.environ.get("LAOZHANG_API_KEY", "")
BASE    = "https://api.laozhang.ai/v1"
MODEL   = os.environ.get("LAOZHANG_MODEL", "chatgpt-5.2")
H       = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}

def test_models():
    print("\n1️⃣  Доступные модели...")
    r = requests.get(f"{BASE}/models", headers=H, timeout=10)
    print(f"   GET /models → {r.status_code}")
    if r.ok:
        models = [m["id"] for m in r.json().get("data", [])]
        print(f"   ✅ Моделей: {len(models)}")
        gpt   = [m for m in models if "gpt" in m.lower()]
        claude= [m for m in models if "claude" in m.lower()]
        print(f"   GPT: {gpt[:5]}")
        print(f"   Claude: {claude[:3]}")
        return True
    print(f"   ❌ {r.text[:200]}")
    return False

def test_caption():
    print(f"\n2️⃣  Тест генерации текста поста (модель: {MODEL})...")
    r = requests.post(f"{BASE}/chat/completions", headers=H, json={
        "model": MODEL,
        "messages": [{"role": "user", "content":
            "Напиши короткий текст для поста в Instagram о приготовлении пасты карбонара. "
            "Дружелюбный тон, 2-3 предложения, 3 хэштега. Только текст, без пояснений."
        }],
        "max_tokens": 300,
        "temperature": 0.8,
    }, timeout=30)
    print(f"   POST /chat/completions → {r.status_code}")
    if r.ok:
        content = r.json()["choices"][0]["message"]["content"]
        print(f"   ✅ Ответ:\n   {content}")
        return True
    print(f"   ❌ {r.text[:300]}")
    return False

def test_ideas():
    print(f"\n3️⃣  Тест генерации 5 идей контента...")
    r = requests.post(f"{BASE}/chat/completions", headers=H, json={
        "model": MODEL,
        "messages": [{"role": "user", "content":
            'Придумай 5 идей для постов в Instagram для кулинарного блога. '
            'Верни JSON: {"ideas": ["идея1", "идея2", ...]}. Без markdown.'
        }],
        "max_tokens": 500,
    }, timeout=30)
    print(f"   POST /chat/completions → {r.status_code}")
    if r.ok:
        content = r.json()["choices"][0]["message"]["content"]
        try:
            ideas = json.loads(content)["ideas"]
            for i, idea in enumerate(ideas, 1):
                print(f"   {i}. {idea}")
            print(f"   ✅ Идей сгенерировано: {len(ideas)}")
        except:
            print(f"   ✅ Ответ: {content[:200]}")
        return True
    print(f"   ❌ {r.text[:300]}")
    return False

def main():
    if not API_KEY:
        print("❌ LAOZHANG_API_KEY не задан")
        print("   export LAOZHANG_API_KEY=твой_ключ")
        return

    print("🤖 laozhang.ai API Test")
    print(f"   Base: {BASE}")
    print(f"   Model: {MODEL}")

    ok1 = test_models()
    ok2 = test_caption()
    ok3 = test_ideas()

    print("\n" + "="*50)
    if ok1 and ok2 and ok3:
        print("✅ laozhang.ai работает! Готово к продакшену.")
        print(f"   Модель {MODEL} генерирует тексты на русском")
    else:
        print("⚠️ Часть тестов не прошла — проверь ключ и модель")
    print("="*50)

if __name__ == "__main__":
    main()
