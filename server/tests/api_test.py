#!/usr/bin/env python3
"""Checks the Wonder Box API against a running server.

    python3 tests/api_test.py http://127.0.0.1:8090 admin@example.com somepassword

Creates a throwaway parent account through the superuser API, exercises every route
(children, videos, photos, linking a TV), then deletes the account. Checking videos needs the
server to reach YouTube.
"""
import base64
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE, ADMIN, ADMIN_PASSWORD = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]

# Well-known, embeddable videos: "Me at the zoo" and Big Buck Bunny.
ZOO = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
BUNNY = "https://youtu.be/aqz-KE-bpKQ"

# A 2x2 yellow JPEG, standing in for a photo the phone app has shrunk.
JPEG = base64.b64decode(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDABALDA4MChAODQ4SERATGCgaGBYWGDEjJR0oOjM9PDkzODdASFxOQERXRTc4UG1RV19iZ2hnPk1xeXBkeFxl"
    "Z2P/2wBDARESEhgVGC8aGi9jQjhCY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2NjY2P/wAARCAACAAIDASIAAhEB"
    "AxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS"
    "0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKz"
    "tLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgEC"
    "BAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpj"
    "ZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6"
    "/9oADAMBAAIRAxEAPwDqKKKK+ZPQP//Z"
)


def call(method, path, body=None, token=None, device_key=None, expect=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    if device_key:
        headers["X-Device-Key"] = device_key
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            status, text = res.status, res.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    assert status == expect, f"{method} {path}: expected {expect}, got {status}: {text[:300]}"
    try:
        return json.loads(text)
    except ValueError:
        raise AssertionError(f"{method} {path} returned no JSON (are the hooks loaded?): {text[:120]}")


def upload(path, files, fields, token, expect=200):
    """A multipart POST, as the phone app sends photos."""
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    for name, data in files.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{name}.jpg"\r\n'
                     f"Content-Type: image/jpeg\r\n\r\n".encode() + data + b"\r\n")
    body = b"".join(parts) + f"--{boundary}--\r\n".encode()
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}", "Authorization": token}
    req = urllib.request.Request(BASE + path, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            status, text = res.status, res.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    assert status == expect, f"POST {path}: expected {expect}, got {status}: {text[:300]}"
    return json.loads(text)


def fetch(path, token=None, device_key=None, expect=200):
    """Raw bytes, for the photo files."""
    headers = {"Authorization": token} if token else {"X-Device-Key": device_key} if device_key else {}
    req = urllib.request.Request(BASE + path, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            status, data = res.status, res.read()
    except urllib.error.HTTPError as e:
        status, data = e.code, e.read()
    assert status == expect, f"GET {path}: expected {expect}, got {status}: {data[:200]}"
    return data


admin = call("POST", "/api/collections/_superusers/auth-with-password", {"identity": ADMIN, "password": ADMIN_PASSWORD})["token"]
email, password = f"test-{uuid.uuid4().hex[:8]}@example.com", "test-password-123"
user = call("POST", "/api/collections/users/records", {"email": email, "password": password, "passwordConfirm": password}, admin)
try:
    token = call("POST", "/api/collections/users/auth-with-password", {"identity": email, "password": password})["token"]

    # No public sign-up, and the API needs a sign-in.
    call("POST", "/api/collections/users/records", {"email": "x@example.com", "password": password, "passwordConfirm": password}, expect=403)
    call("GET", "/api/wonder/library", expect=401)
    assert call("GET", "/api/wonder/library", token=token) == {"children": [], "videos": []}

    # Children
    lib = call("POST", "/api/wonder/children/save", {"name": "Sam", "avatar": "🦊", "color": "#FF6B9A"}, token)
    sam = lib["saved"]
    lib = call("POST", "/api/wonder/children/save", {"name": "Jo", "avatar": "🐢"}, token)
    jo = lib["saved"]
    assert [c["name"] for c in lib["children"]] == ["Sam", "Jo"]
    assert lib["children"][1]["color"] == "#FFD23F"
    call("POST", "/api/wonder/children/save", {"name": " "}, token, expect=400)
    lib = call("POST", "/api/wonder/children/move", {"id": jo, "delta": -1}, token)
    assert [c["name"] for c in lib["children"]] == ["Jo", "Sam"]
    lib = call("POST", "/api/wonder/children/save", {"id": jo, "name": "Joanna", "avatar": "🐢"}, token)
    assert lib["children"][0]["name"] == "Joanna"

    # Checking videos
    call("POST", "/api/wonder/videos/check", {"url": "https://example.com"}, token, expect=400)
    info = call("POST", "/api/wonder/videos/check", {"url": ZOO}, token)
    assert info["youtubeId"] == "jNQXAC9IVRw" and info["title"], info
    for url in ["jNQXAC9IVRw", "https://m.youtube.com/watch?feature=share&v=jNQXAC9IVRw", "https://youtube.com/shorts/jNQXAC9IVRw?si=x"]:
        assert call("POST", "/api/wonder/videos/check", {"url": url}, token)["youtubeId"] == "jNQXAC9IVRw"
    # A well-formed ID that doesn't exist.
    call("POST", "/api/wonder/videos/check", {"url": "https://youtu.be/aaaaaaaaaaa"}, token, expect=400)

    # Adding videos: new ones go first; adding again just adds the child.
    call("POST", "/api/wonder/videos/add", {"url": ZOO, "children": []}, token, expect=400)
    lib = call("POST", "/api/wonder/videos/add", {"url": ZOO, "children": [sam]}, token)
    zoo = lib["saved"]
    lib = call("POST", "/api/wonder/videos/add", {"url": BUNNY, "children": [sam, jo, "not-a-child"]}, token)
    bunny = lib["saved"]
    assert [v["id"] for v in lib["videos"]] == [bunny, zoo]
    assert sorted(lib["videos"][0]["children"]) == sorted([sam, jo])
    lib = call("POST", "/api/wonder/videos/add", {"url": ZOO, "children": [jo]}, token)
    assert len(lib["videos"]) == 2 and sorted(lib["videos"][1]["children"]) == sorted([sam, jo])

    lib = call("POST", "/api/wonder/videos/move", {"id": zoo, "delta": -1, "child": sam}, token)
    assert [v["id"] for v in lib["videos"]] == [zoo, bunny]
    lib = call("POST", "/api/wonder/videos/update", {"id": zoo, "children": [jo]}, token)
    assert lib["videos"][0]["children"] == [jo]

    # Removing a child removes videos that were only theirs.
    lib = call("POST", "/api/wonder/children/remove", {"id": jo}, token)
    assert [v["id"] for v in lib["videos"]] == [bunny] and lib["videos"][0]["children"] == [sam]
    lib = call("POST", "/api/wonder/videos/remove", {"id": bunny}, token)
    assert lib["videos"] == []

    # Photos: the phone uploads a shrunk picture and a thumbnail; newest first.
    call("GET", "/api/wonder/photos", expect=401)
    assert call("GET", "/api/wonder/photos", token=token) == {"photos": []}
    upload("/api/wonder/photos/add", {}, {}, token, expect=400)
    upload("/api/wonder/photos/add", {"image": b"not a picture"}, {}, token, expect=400)
    first = upload("/api/wonder/photos/add", {"image": JPEG}, {"width": 2, "height": 2}, token)["saved"]
    photos = upload("/api/wonder/photos/add", {"image": JPEG, "thumb": JPEG}, {"width": 2, "height": 2}, token)
    second = photos["saved"]
    assert [p["id"] for p in photos["photos"]] == [second, first], photos
    assert photos["photos"][0]["width"] == 2
    assert fetch(photos["photos"][0]["image"], token=token) == JPEG
    assert fetch(photos["photos"][0]["thumb"], token=token) == JPEG
    assert fetch(photos["photos"][1]["thumb"], token=token) == JPEG  # no thumb: the image instead
    fetch(photos["photos"][0]["image"], expect=401)
    fetch(f"/api/wonder/photos/{second}/other", token=token, expect=404)
    # PocketBase's own file and record APIs don't hand them out either.
    fetch(f"/api/collections/photos/records/{second}", token=token, expect=403)
    photos = call("POST", "/api/wonder/photos/remove", {"id": first}, token)
    assert [p["id"] for p in photos["photos"]] == [second]

    # Linking a TV
    start = call("POST", "/api/wonder/pair/start", {"name": "Living room"})
    code, secret = start["code"], start["secret"]
    assert call("GET", f"/api/wonder/pair/poll?code={code}&secret={secret}")["status"] == "pending"
    call("GET", f"/api/wonder/pair/poll?code={code}&secret=wrong", expect=410)
    assert call("GET", f"/api/wonder/pair/info?code={code.lower()}", token=token)["name"] == "Living room"
    call("POST", "/api/wonder/pair/approve", {"code": code}, token)
    key = call("GET", f"/api/wonder/pair/poll?code={code}&secret={secret}")["deviceKey"]
    call("GET", f"/api/wonder/pair/poll?code={code}&secret={secret}", expect=410)
    assert call("GET", "/api/wonder/library", device_key=key)["children"][0]["name"] == "Sam"
    # A TV can read but not edit.
    call("POST", "/api/wonder/children/save", {"name": "X"}, device_key=key, expect=401)
    tv_photos = call("GET", "/api/wonder/photos", device_key=key)["photos"]
    assert [p["id"] for p in tv_photos] == [second]
    assert fetch(tv_photos[0]["image"], device_key=key) == JPEG
    call("POST", "/api/wonder/photos/remove", {"id": second}, device_key=key, expect=401)

    # The activity log: the TV sends events, the phone app reads stats.
    call("POST", "/api/wonder/events", {"events": [{"type": "video.play"}]}, expect=401)
    batch = {"events": [
        {"id": "aaaaaaaaaaaaaa1", "type": "video.play", "child": sam, "subject": "jNQXAC9IVRw", "data": {"title": "Zoo", "channel": "jawed"}},
        {"id": "aaaaaaaaaaaaaa2", "type": "video.watch", "child": sam, "subject": "jNQXAC9IVRw", "value": 19.5, "data": {"title": "Zoo", "channel": "jawed", "finished": True}},
        {"type": "quiz.answer", "child": sam, "subject": "maths.add", "value": 1, "data": {"question": "2+2", "answer": "4"}},
        {"type": "quiz.answer", "child": sam, "subject": "maths.add", "value": 0},
        {"type": "quiz.finish", "child": sam, "subject": "maths.add", "value": 7, "data": {"total": 10}},
        {"type": "app.start"},
        {"type": "Bad Type", "child": sam},
        {"type": "video.play", "child": "notachild"},
    ]}
    assert call("POST", "/api/wonder/events", batch, device_key=key) == {"saved": 6, "skipped": 2}
    # Sending the same batch again (a retry) saves only the events without ids.
    assert call("POST", "/api/wonder/events", batch, device_key=key) == {"saved": 4, "skipped": 4}
    # One event on its own, from the phone app.
    assert call("POST", "/api/wonder/events", {"type": "photo.view", "child": sam, "subject": "p1", "value": 5}, token)["saved"] == 1
    call("GET", "/api/wonder/events", device_key=key, expect=401)
    events = call("GET", "/api/wonder/events?type=video.&child=" + sam, token=token)
    assert sorted(ev["type"] for ev in events) == ["video.play", "video.watch"], events
    assert call("GET", "/api/wonder/events?type=app.start", token=token)[0]["child"] == ""
    stats = call("GET", "/api/wonder/stats?days=7&tz=0", token=token)
    s = stats["children"][0]
    assert s["id"] == sam and s["plays"] == 1 and s["watchSeconds"] == 20, s
    assert s["answers"] == 4 and s["correct"] == 2, s
    assert s["topVideos"][0]["title"] == "Zoo" and s["topVideos"][0]["finished"] == 1
    assert s["topChannels"][0]["channel"] == "jawed"
    assert s["games"] == [{"game": "maths.add", "answers": 4, "correct": 2, "rounds": 2, "bestScore": 7}], s["games"]
    assert s["byType"]["photo.view"] == {"count": 1, "total": 5}
    assert len(s["daily"]) == 7 and s["daily"][-1]["plays"] == 1
    assert stats["other"]["byType"]["app.start"]["count"] == 2
    call("GET", "/api/wonder/stats", device_key=key, expect=401)

    tvs = call("GET", "/api/wonder/devices", token=token)
    assert [t["name"] for t in tvs] == ["Living room"]
    call("POST", "/api/wonder/devices/remove", {"id": tvs[0]["id"]}, token)
    call("GET", "/api/wonder/library", device_key=key, expect=401)
    fetch(tv_photos[0]["image"], device_key=key, expect=401)
    print("All API checks passed.")
finally:
    req = urllib.request.Request(f"{BASE}/api/collections/users/records/{user['id']}", headers={"Authorization": admin}, method="DELETE")
    urllib.request.urlopen(req, timeout=30)
