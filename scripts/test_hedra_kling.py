#!/usr/bin/env python3
"""
Тест Hedra Platform API + Kling AI Avatar v2 Standard.

pip install requests
HEDRA_API_KEY=твой_ключ python3 test_hedra_kling.py
"""
import requests, os, sys, json, base64, time

KEY   = os.environ.get("HEDRA_API_KEY", "")
BASE  = "https://api.hedra.com/web-app/public"
MODEL = os.environ.get("HEDRA_AVATAR_MODEL", "kling_ai_avatar_v2_standard")
H     = {"X-API-Key": KEY}

# Минимальный JPEG 1x1 (для теста загрузки)
TEST_JPEG = base64.b64decode(
    "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkS"
    "Ew8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAARCAAB"
    "AAEDASIAAhEBAxEB/8QAFgABAQAAAAAAAAAAAAAAAAAAAAb/xAAUEAEAAAAAAAAAAAAA"
    "AAAAAAAA/8QAFAEBAAAAAAAAAAAAAAAAAAAAAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/"
    "2gAMAwEAAhEDEQA/AJAA/9k="
)

# Минимальный WAV (тишина 0.5 сек)
TEST_WAV = b"RIFF$\x00\x00\x00WAVEfmt \x10\x00\x00\x00\x01\x00\x01\x00\x80>\x00\x00\x00}\x00\x00\x02\x00\x10\x00data\x00\x00\x00\x00"

def test_models():
    print("\n1️⃣  Доступные модели...")
    r = requests.get(f"{BASE}/models", headers=H, timeout=10)
    print(f"   GET /models → {r.status_code}")
    if r.ok:
        models = r.json() if isinstance(r.json(), list) else r.json().get("models", [])
        avatar_models = [m for m in models if "avatar" in str(m).lower() or "kling" in str(m).lower()]
        print(f"   Всего моделей: {len(models)}")
        print(f"   Avatar/Kling модели: {avatar_models[:5]}")
        has_model = any(MODEL in str(m) for m in models)
        print(f"   '{MODEL}': {'✅ найдена' if has_model else '⚠️  уточни название'}")
        return True
    print(f"   ❌ {r.text[:200]}")
    return False

def test_upload_image():
    print("\n2️⃣  Загрузка изображения...")
    r = requests.post(
        f"{BASE}/assets",
        headers=H,
        files={"file": ("photo.jpg", TEST_JPEG, "image/jpeg")},
        timeout=30,
    )
    print(f"   POST /assets (image) → {r.status_code}")
    if r.ok:
        data = r.json()
        url = data.get("url", "")
        print(f"   ✅ URL: {url[:80]}...")
        return url
    print(f"   ❌ {r.text[:300]}")
    return None

def test_upload_audio():
    print("\n3️⃣  Загрузка аудио...")
    r = requests.post(
        f"{BASE}/assets",
        headers=H,
        files={"file": ("voice.wav", TEST_WAV, "audio/wav")},
        timeout=30,
    )
    print(f"   POST /assets (audio) → {r.status_code}")
    if r.ok:
        data = r.json()
        url = data.get("url", "")
        print(f"   ✅ URL: {url[:80]}...")
        return url
    print(f"   ❌ {r.text[:300]}")
    return None

def test_generate(image_url, audio_url):
    print(f"\n4️⃣  Создаём аватар (модель: {MODEL})...")
    payload = {
        "model": MODEL,
        "image_url": image_url,
        "audio_url": audio_url,
        "aspect_ratio": "9:16",
    }
    r = requests.post(
        f"{BASE}/characters",
        headers={**H, "Content-Type": "application/json"},
        json=payload,
        timeout=30,
    )
    print(f"   POST /characters → {r.status_code}")
    if r.ok:
        data = r.json()
        job_id = data.get("id") or data.get("jobId") or data.get("job_id", "")
        print(f"   ✅ Job ID: {job_id}")
        return job_id
    print(f"   ❌ {r.text[:300]}")
    return None

def test_status(job_id):
    print(f"\n5️⃣  Проверяем статус (job: {job_id})...")
    for i in range(3):
        time.sleep(3)
        r = requests.get(f"{BASE}/generations/{job_id}", headers=H, timeout=10)
        print(f"   Попытка {i+1}: GET /generations/{job_id} → {r.status_code}")
        if r.ok:
            data = r.json()
            status = data.get("status", "?")
            print(f"   Статус: {status}")
            if status in ("complete", "completed"):
                print(f"   ✅ Видео: {data.get('video_url') or data.get('url', '')}")
                return True
            elif status in ("error", "failed"):
                print(f"   ❌ Ошибка: {data.get('error_message') or data.get('error', '')}")
                return False
    print("   ⏳ Генерация идёт (норм для реального видео, не тестового)")
    return True

def main():
    if not KEY:
        print("❌ HEDRA_API_KEY не задан")
        print("   export HEDRA_API_KEY=твой_ключ")
        sys.exit(1)

    print("🎬 Hedra Platform API × Kling v2 Standard Test")
    print(f"   Base: {BASE}")
    print(f"   Model: {MODEL}")
    print(f"   Key: {KEY[:8]}...{KEY[-4:]}")

    test_models()
    image_url = test_upload_image()
    audio_url = test_upload_audio()

    if image_url and audio_url:
        job_id = test_generate(image_url, audio_url)
        if job_id:
            test_status(job_id)

    print("\n" + "=" * 55)
    print("Тест завершён. Если все шаги прошли ✅ — всё готово к деплою.")
    print(f"Модель: {MODEL} | Цена: 8 кредитов/сек через Hedra")
    print("=" * 55)

if __name__ == "__main__":
    main()
