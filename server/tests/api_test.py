#!/usr/bin/env python3
"""Checks the Wonder Box API against a running server.

    python3 tests/api_test.py http://127.0.0.1:8090 admin@example.com somepassword

Creates a throwaway parent account through the superuser API, exercises every route
(children, videos, channels, linking a TV), then deletes the account. Checking videos and
channels needs the server to reach YouTube.
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
# A pop video, as YouTube's share link with a playlist and radio attached.
POP = "https://www.youtube.com/watch?v=ekr2nIex040&list=RDekr2nIex040&start_radio=1"
# A children's channel, by handle and by ID.
NUMBERBLOCKS = "https://www.youtube.com/@Numberblocks"
NUMBERBLOCKS_ID = "UCPlwvN0w4qFSP1FllALB92w"


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
    assert call("GET", "/api/wonder/library", token=token) == {"children": [], "videos": [], "channels": []}

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

    # Game settings: defaults, then clamped to the levels the TV has.
    assert lib["children"][0]["games"] == {"maths": 1, "spelling": 1, "roundSize": 10}
    lib = call("POST", "/api/wonder/children/games", {"id": jo, "maths": 3, "spelling": 99, "roundSize": 1}, token)
    assert lib["children"][0]["games"] == {"maths": 3, "spelling": 5, "roundSize": 3}, lib["children"][0]
    lib = call("POST", "/api/wonder/children/games", {"id": jo, "roundSize": 12}, token)
    assert lib["children"][0]["games"] == {"maths": 3, "spelling": 5, "roundSize": 12}

    # Checking videos
    call("POST", "/api/wonder/videos/check", {"url": "https://example.com"}, token, expect=400)
    info = call("POST", "/api/wonder/videos/check", {"url": ZOO}, token)
    assert info["kind"] == "video" and info["youtubeId"] == "jNQXAC9IVRw" and info["title"], info
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

    # Videos are free unless they're treats.
    assert lib["videos"][0]["tier"] == "free"
    lib = call("POST", "/api/wonder/videos/update", {"id": bunny, "tier": "treat"}, token)
    assert lib["videos"][0]["tier"] == "treat" and len(lib["videos"][0]["children"]) == 2
    lib = call("POST", "/api/wonder/videos/update", {"id": bunny, "tier": "nonsense"}, token)
    assert lib["videos"][0]["tier"] == "free"

    # Music: a link with a playlist attached is just the video.
    assert call("POST", "/api/wonder/videos/check", {"url": POP}, token)["youtubeId"] == "ekr2nIex040"
    lib = call("POST", "/api/wonder/videos/add", {"url": POP, "children": [sam], "section": "music"}, token)
    pop = lib["saved"]
    assert lib["videos"][0]["section"] == "music" and lib["videos"][1]["section"] == "videos"
    lib = call("POST", "/api/wonder/videos/remove", {"id": pop}, token)

    lib = call("POST", "/api/wonder/videos/move", {"id": zoo, "delta": -1, "child": sam}, token)
    assert [v["id"] for v in lib["videos"]] == [zoo, bunny]
    lib = call("POST", "/api/wonder/videos/update", {"id": zoo, "children": [jo]}, token)
    assert lib["videos"][0]["children"] == [jo]

    # Channels: the check finds it by handle and lists its latest videos, without Shorts.
    info = call("POST", "/api/wonder/videos/check", {"url": NUMBERBLOCKS}, token)
    assert info["kind"] == "channel" and info["channelId"] == NUMBERBLOCKS_ID and info["title"], info
    assert info["videos"] and all(len(v["youtubeId"]) == 11 for v in info["videos"]), info
    call("POST", "/api/wonder/channels/add", {"url": NUMBERBLOCKS, "children": []}, token, expect=400)
    call("POST", "/api/wonder/channels/add", {"url": "https://example.com/@x", "children": [sam]}, token, expect=400)
    lib = call("POST", "/api/wonder/channels/add", {"url": NUMBERBLOCKS, "children": [jo], "tier": "treat"}, token)
    channel = lib["saved"]
    [ch] = lib["channels"]
    assert ch["channelId"] == NUMBERBLOCKS_ID and ch["tier"] == "treat" and ch["children"] == [jo] and ch["videos"], ch
    assert 0 < len(ch["videos"]) <= 50 and all(len(v["youtubeId"]) == 11 and v["title"] for v in ch["videos"]), ch["videos"]
    # Adding it again by ID adds the child, and doesn't make a second one.
    lib = call("POST", "/api/wonder/channels/add", {"url": f"https://www.youtube.com/channel/{NUMBERBLOCKS_ID}", "children": [sam]}, token)
    assert len(lib["channels"]) == 1 and sorted(lib["channels"][0]["children"]) == sorted([sam, jo])
    assert lib["channels"][0]["tier"] == "treat"
    before = [v["youtubeId"] for v in ch["videos"]]
    lib = call("POST", "/api/wonder/channels/refresh", {"id": channel}, token)
    assert [v["youtubeId"] for v in lib["channels"][0]["videos"]] == before
    lib = call("POST", "/api/wonder/channels/update", {"id": channel, "tier": "free", "section": "music", "children": [jo]}, token)
    ch = lib["channels"][0]
    assert ch["tier"] == "free" and ch["section"] == "music" and ch["children"] == [jo], ch

    # Removing a child removes videos and channels that were only theirs.
    lib = call("POST", "/api/wonder/children/remove", {"id": jo}, token)
    assert [v["id"] for v in lib["videos"]] == [bunny] and lib["videos"][0]["children"] == [sam]
    assert lib["channels"] == []
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
    print("All API checks passed.")
finally:
    req = urllib.request.Request(f"{BASE}/api/collections/users/records/{user['id']}", headers={"Authorization": admin}, method="DELETE")
    urllib.request.urlopen(req, timeout=30)
