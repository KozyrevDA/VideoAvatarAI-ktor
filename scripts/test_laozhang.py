#!/usr/bin/env python3
"""
Тест laozhang.ai API с GPT-5.2.
pip install requests
LAOZHANG_API_KEY=твой_ключ python3 test_laozhang.py
"""
import requests, os, json, sys

KEY   = os.environ.get("LAOZHANG_API_KEY", "")
BASE  = "https://api.laozhang.ai/v1"
MODEL = os.environ.get("LAOZHANG_MODEL", "chatgpt-5.2")
H     = {"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"}

SYSTEM = (
    "Ты — эксперт по контент-маркетингу для русскоязычных соцсетей. "
    "ВАЖНО: отвечай ТОЛЬКО валидным JSON без markdown-обёрток. "
    "Первый символ — {, последний — }."
)

def chat_json(user_prompt, max_tokens=800, temp=0.85):
    r = requests.post(f"{BASE}/chat/completions", headers=H, json={
        "model": MODEL,
        "messages": [
            {"role": "system",  "content": SYSTEM},
            {"role": "user",    "content": user_prompt},
        ],
        "max_tokens": max_tokens,
        "temperature": temp,
        "response_format": {"type": "json_object"},  # GPT-5.2 фича
    }, timeout=30)
    if not r.ok:
        raise Exception(f"HTTP {r.status_code}: {r.text[:200]}")
    return r.json()["choices"][0]["message"]["content"]

def test_available_models():
    print("\n1️⃣  Доступные модели...")
    r = requests.get(f"{BASE}/models", headers=H, timeout=10)
    print(f"   GET /models → {r.status_code}")
    if r.ok:
        models = sorted(m["id"] for m in r.json().get("data", []))
        gpt    = [m for m in models if "gpt" in m or "chatgpt" in m]
        claude = [m for m in models if "claude" in m]
        gemini = [m for m in models if "gemini" in m]
        print(f"   GPT/ChatGPT ({len(gpt)}): {gpt[:6]}")
        print(f"   Claude ({len(claude)}):     {claude[:4]}")
        print(f"   Gemini ({len(gemini)}):     {gemini[:3]}")
        has_model = MODEL in models
        print(f"   Модель '{MODEL}': {'✅ Есть' if has_model else '❌ Не найдена'}")
        if not has_model and gpt:
            print(f"   💡 Попробуй: {gpt[0]}")
        return True
    print(f"   ❌ {r.text[:200]}")
    return False

def test_text_post():
    print(f"\n2️⃣  Текст поста (Instagram, дружелюбный тон)...")
    try:
        raw = chat_json(
            'Напиши текст для поста. '
            'Платформа: INSTAGRAM — 150–300 символов, 5–10 хэштегов, эмодзи приветствуются. '
            'Тема: рецепт пасты карбонара за 20 минут. '
            'Тон: дружелюбный, тёплый, как разговор с другом. '
            'Призыв к действию в конце. '
            'JSON: {"text":"...","hashtags":["слово1","слово2"]}',
        )
        data = json.loads(raw)
        print(f"   ✅ Текст ({len(data['text'])} симв):\n      {data['text'][:200]}")
        print(f"   Хэштеги: {data.get('hashtags', [])[:6]}")
        return True
    except Exception as e:
        print(f"   ❌ {e}")
        return False

def test_ideas():
    print(f"\n3️⃣  30 идей контента (ниша: кулинария)...")
    try:
        raw = chat_json(
            'Придумай ровно 10 идей для контента. '
            'Ниша: кулинария. Платформа: Instagram Reels и посты. '
            'Аудитория: русскоязычная. '
            'Каждая идея — конкретная тема, 1 предложение до 100 символов. '
            'JSON: {"ideas":["идея 1","идея 2",...,"идея 10"]}',
            max_tokens=1200,
            temp=0.92,
        )
        data = json.loads(raw)
        ideas = data.get("ideas", [])
        print(f"   ✅ Идей: {len(ideas)}")
        for i, idea in enumerate(ideas[:5], 1):
            print(f"   {i}. {idea}")
        if len(ideas) > 5:
            print(f"   ... и ещё {len(ideas)-5}")
        return True
    except Exception as e:
        print(f"   ❌ {e}")
        return False

def test_translate():
    print(f"\n4️⃣  Перевод текста на английский...")
    try:
        raw = chat_json(
            'Переведи на English. Сохрани стиль, эмодзи и структуру. '
            'Верни JSON: {"translation":"..."}. '
            'Текст: Привет! Сегодня покажу рецепт пасты карбонара 🍝 За 20 минут и без заморочек!',
            temp=0.3,
        )
        data = json.loads(raw)
        print(f"   ✅ Перевод: {data.get('translation', '')}")
        return True
    except Exception as e:
        print(f"   ❌ {e}")
        return False

def main():
    if not KEY:
        print("❌ Ключ не найден")
        print("   export LAOZHANG_API_KEY=твой_ключ_с_laozhang.ai")
        sys.exit(1)

    print("🤖 laozhang.ai × GPT-5.2 Test Suite")
    print(f"   URL: {BASE}")
    print(f"   Модель: {MODEL}")
    print(f"   Ключ: {KEY[:8]}...{KEY[-4:]}")

    ok = [
        test_available_models(),
        test_text_post(),
        test_ideas(),
        test_translate(),
    ]

    print("\n" + "=" * 55)
    passed = sum(ok)
    print(f"Результат: {passed}/{len(ok)} тестов прошло")
    if passed == len(ok):
        print("✅ Всё работает! Можно вставлять ключ в .env и деплоить.")
    else:
        print("⚠️ Проверь ключ и название модели на api.laozhang.ai")
    print("=" * 55)

if __name__ == "__main__":
    main()
