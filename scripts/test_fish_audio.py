#!/usr/bin/env python3
"""
Тест Fish Audio API — запусти локально.
pip install requests
python3 test_fish_audio.py
"""
import requests, os, sys, json

API_KEY = os.environ.get("FISH_AUDIO_API_KEY",
    "b823baaecf8f43e090323ea682998dcb"
)
BASE = "https://api.fish.audio"
H    = {"Authorization": f"Bearer {API_KEY}"}

def test_models():
    print("\n1️⃣  Список голосов...")
    r = requests.get(f"{BASE}/model", headers=H, params={"page_size": 5}, timeout=10)
    print(f"   GET /model → {r.status_code}")
    if r.ok:
        data = r.json()
        items = data.get("items", [])
        print(f"   ✅ Голосов в библиотеке: {data.get('total', '?')}")
        for v in items[:3]:
            print(f"   • {v.get('title','?')} [{v.get('_id','?')[:8]}]  langs: {v.get('languages', [])}")
        return True
    print(f"   ❌ {r.text[:200]}")
    return False

def test_tts():
    print("\n2️⃣  Тест TTS (без клонирования)...")
    r = requests.post(f"{BASE}/v1/tts", headers={**H, "Content-Type": "application/json"},
        json={"text": "Привет! Это тест синтеза речи.", "format": "mp3", "latency": "normal"},
        timeout=30)
    print(f"   POST /v1/tts → {r.status_code}")
    if r.ok:
        size = len(r.content)
        print(f"   ✅ Аудио получено: {size:,} байт ({size//1024} KB)")
        with open("/tmp/fish_test.mp3", "wb") as f: f.write(r.content)
        print("   💾 Сохранено: /tmp/fish_test.mp3")
        return True
    print(f"   ❌ {r.text[:200]}")
    return False

def test_clone_voice():
    print("\n3️⃣  Тест клонирования голоса...")
    print("   ℹ️  Для теста нужен реальный MP3 файл с голосом")
    print("   Создаём голос из существующей записи...")
    # В реальном тесте передаёшь файл:
    # with open("my_voice.mp3", "rb") as f:
    #     r = requests.post(f"{BASE}/v1/model", headers=H,
    #         files={"voices": ("voice.mp3", f, "audio/mpeg")},
    #         data={"title": "My Voice", "visibility": "private"},
    #         timeout=60)
    print("   ⏭️  Пропущен (нужен аудио файл с записью голоса)")
    return None

def main():
    print("🎵 Fish Audio API Test")
    print(f"   Key: {API_KEY[:8]}...{API_KEY[-4:]}")
    print(f"   Base: {BASE}")

    ok1 = test_models()
    ok2 = test_tts()
    test_clone_voice()

    print("\n" + "="*50)
    if ok1 and ok2:
        print("✅ Fish Audio работает! Ключ валидный.")
        print("   TTS на русском — ОК")
        print("   Готово к интеграции в бэкенд")
    else:
        print("❌ Что-то пошло не так — проверь ключ")
    print("⚠️  ЗАМЕНИ КЛЮЧ: fish.audio → API Keys")
    print("="*50)

if __name__ == "__main__":
    main()
