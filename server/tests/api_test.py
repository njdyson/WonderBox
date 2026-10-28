#!/usr/bin/env python3
"""Checks the Wonder Box API against a running server.

    python3 tests/api_test.py http://127.0.0.1:8090 admin@example.com somepassword

Creates a throwaway parent account through the superuser API, exercises every route
(children, videos, linking a TV), then deletes the account. Checking videos needs the
server to reach YouTube.
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE, ADMIN, ADMIN_PASSWORD = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]

# Well-known, embeddable videos: "Me at the zoo" and Big Buck Bunny.
ZOO = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
BUNNY = "https://youtu.be/aqz-KE-bpKQ"


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


def upload(path, fields, file_field, filename, content, token=None, expect=200):
    """A multipart POST, as the phone app sends a picture."""
    boundary = uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode() for k, v in fields.items()]
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'
                 f"Content-Type: image/png\r\n\r\n".encode() + content + b"\r\n")
    data = b"".join(parts) + f"--{boundary}--\r\n".encode()
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}", **({"Authorization": token} if token else {})}
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            status, text = res.status, res.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    assert status == expect, f"POST {path}: expected {expect}, got {status}: {text[:300]}"
    return json.loads(text)


# A 1x1 transparent PNG.
TINY_PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000d49444154789c6360000002000005000165ea4e400000000049454e44ae426082"
)

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

    # A child's picture: uploaded, kept when their details change, then removed.
    lib = upload("/api/wonder/children/picture", {"id": jo}, "picture", "jo.png", TINY_PNG, token)
    picture = lib["children"][0]["picture"]
    assert picture.startswith(f"/api/files/children/{jo}/"), picture
    with urllib.request.urlopen(BASE + picture, timeout=30) as res:
        assert res.read() == TINY_PNG
    lib = call("POST", "/api/wonder/children/save", {"id": jo, "name": "Joanna", "avatar": "🐢"}, token)
    assert lib["children"][0]["picture"] == picture
    call("POST", "/api/wonder/children/picture", {"id": jo}, token, expect=400)
    upload("/api/wonder/children/picture", {"id": jo}, "picture", "jo.png", TINY_PNG, expect=401)
    lib = call("POST", "/api/wonder/children/picture", {"id": jo, "remove": True}, token)
    assert lib["children"][0]["picture"] == ""

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
    tvs = call("GET", "/api/wonder/devices", token=token)
    assert [t["name"] for t in tvs] == ["Living room"]
    call("POST", "/api/wonder/devices/remove", {"id": tvs[0]["id"]}, token)
    call("GET", "/api/wonder/library", device_key=key, expect=401)
    print("All API checks passed.")
finally:
    req = urllib.request.Request(f"{BASE}/api/collections/users/records/{user['id']}", headers={"Authorization": admin}, method="DELETE")
    urllib.request.urlopen(req, timeout=30)
