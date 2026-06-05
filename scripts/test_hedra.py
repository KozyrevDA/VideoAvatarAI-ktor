#!/usr/bin/env python3
"""
Тест Hedra API — запусти локально на своей машине.

pip install requests
python3 test_hedra.py

Что тестирует:
  1. Аутентификацию (GET /v1/characters)
  2. Загрузку тестового фото
  3. Загрузку тестового аудио
  4. Создание lip-sync job
  5. Поллинг статуса
"""

import requests, base64, time, sys, os

API_KEY = os.environ.get("HEDRA_API_KEY",
    "sk_hedra_9gAWsYNNwEQA_ueGtzxEELuOO8B5NVPG17UlhSSFV5MFZzmWtVsiITA0TKEhjgu-"
)
BASE = "https://mercury.dev.dream-ai.com/api/v1"
HEADERS = {"X-API-Key": API_KEY}

def log(msg): print(f"  {msg}")
def ok(r): return r.status_code in (200, 201)

def test_auth():
    print("\n1️⃣  Проверяем аутентификацию...")
    r = requests.get(f"{BASE}/characters", headers=HEADERS, timeout=10)
    log(f"GET /characters → {r.status_code}")
    if ok(r):
        data = r.json()
        log(f"✅ Аутентификация OK. Данные: {str(data)[:120]}")
        return True
    else:
        log(f"❌ Ошибка: {r.text[:200]}")
        return False

def test_upload_audio():
    """Загружаем тестовое аудио (1 секунда тишины в WAV)"""
    print("\n2️⃣  Тест загрузки аудио...")
    # Минимальный WAV: 44 байта заголовок + 1 секунда тишины (16kHz mono)
    wav_header = bytes([
        0x52,0x49,0x46,0x46,  # RIFF
        0x24,0x7D,0x00,0x00,  # chunk size
        0x57,0x41,0x56,0x45,  # WAVE
        0x66,0x6D,0x74,0x20,  # fmt
        0x10,0x00,0x00,0x00,  # subchunk size = 16
        0x01,0x00,             # PCM
        0x01,0x00,             # 1 channel
        0x80,0x3E,0x00,0x00,  # 16000 Hz
        0x00,0x7D,0x00,0x00,  # byte rate
        0x02,0x00,             # block align
        0x10,0x00,             # bits per sample = 16
        0x64,0x61,0x74,0x61,  # data
        0x00,0x7D,0x00,0x00,  # data size
    ])
    audio_data = wav_header + bytes(32000)  # 1 сек тишины

    files = {"file": ("test.wav", audio_data, "audio/wav")}
    r = requests.post(f"{BASE}/audio", headers=HEADERS, files=files, timeout=30)
    log(f"POST /audio → {r.status_code}")
    if ok(r):
        data = r.json()
        audio_url = data.get("url") or data.get("audio_url", "")
        log(f"✅ Аудио загружено. URL: {audio_url[:80]}...")
        return audio_url
    else:
        log(f"❌ Ошибка: {r.text[:200]}")
        return None

def test_upload_portrait():
    """Загружаем тестовое фото (1x1 чёрный пиксель JPEG)"""
    print("\n3️⃣  Тест загрузки фото...")
    # Минимальный JPEG (1×1 чёрный пиксель)
    jpeg_data = base64.b64decode(
        "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsL"
        "DBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/"
        "2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy"
        "MjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFgAB"
        "AQAAAAAAAAAAAAAAAAAACP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/xAAUAQEAAAA"
        "AAAAAAAAAAAAAAAAD/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEA"
        "PwCwABmX/9k="
    )

    files = {"file": ("portrait.jpg", jpeg_data, "image/jpeg")}
    r = requests.post(f"{BASE}/portrait", headers=HEADERS, files=files, timeout=30)
    log(f"POST /portrait → {r.status_code}")
    if ok(r):
        data = r.json()
        photo_url = data.get("url") or data.get("portrait_url", "")
        log(f"✅ Фото загружено. URL: {photo_url[:80]}...")
        return photo_url
    else:
        log(f"❌ Ошибка: {r.text[:200]}")
        return None

def test_create_job(audio_url: str, photo_url: str):
    """Создаём lip-sync job"""
    print("\n4️⃣  Создаём lip-sync job...")
    payload = {
        "text": "",  # уже в аудио
        "voice_url": audio_url,
        "avatar_image_input": {"url": photo_url, "type": "url"},
        "aspect_ratio": "9:16",
    }
    r = requests.post(f"{BASE}/characters", headers={**HEADERS, "Content-Type": "application/json"},
                      json=payload, timeout=30)
    log(f"POST /characters → {r.status_code}")
    if ok(r):
        data = r.json()
        job_id = data.get("jobId") or data.get("job_id") or data.get("id", "")
        log(f"✅ Job создан. ID: {job_id}")
        return job_id
    else:
        log(f"❌ Ошибка: {r.text[:200]}")
        return None

def test_poll_status(job_id: str, max_attempts: int = 5):
    """Поллим статус job"""
    print(f"\n5️⃣  Поллинг статуса (job_id={job_id})...")
    for i in range(max_attempts):
        time.sleep(3)
        r = requests.get(f"{BASE}/projects/{job_id}", headers=HEADERS, timeout=10)
        log(f"  Attempt {i+1}: GET /projects/{job_id} → {r.status_code}")
        if ok(r):
            data = r.json()
            status = data.get("status", "unknown")
            log(f"  Status: {status}")
            if status in ("completed", "ready", "succeeded"):
                video_url = data.get("url") or data.get("video_url", "")
                log(f"  ✅ Видео готово! URL: {video_url}")
                return True
            elif status in ("error", "failed"):
                log(f"  ❌ Job упал: {data.get('error', '')}")
                return False
    log(f"  ⏳ За {max_attempts} попыток не завершился — это нормально для реального видео")
    return None

def main():
    print("🎬 Hedra API Test Suite")
    print(f"   Key: {API_KEY[:20]}...{API_KEY[-4:]}")
    print(f"   Base: {BASE}")

    # Шаг 1: Аутентификация
    if not test_auth():
        print("\n❌ Аутентификация провалилась — проверь ключ")
        sys.exit(1)

    # Шаг 2–3: Загрузка файлов
    audio_url = test_upload_audio()
    photo_url = test_upload_portrait()

    if audio_url and photo_url:
        # Шаг 4: Создаём job
        job_id = test_create_job(audio_url, photo_url)
        if job_id:
            # Шаг 5: Поллинг
            test_poll_status(job_id)

    print("\n" + "="*50)
    print("✅ Тест завершён. API ключ рабочий!")
    print("⚠️  ЗАМЕНИ КЛЮЧ: app.hedra.com → API Keys → Revoke")
    print("="*50)

if __name__ == "__main__":
    main()
