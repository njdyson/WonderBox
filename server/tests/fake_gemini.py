#!/usr/bin/env python3
"""A stand-in for Gemini's generateContent, for api_test.py's photo quiz checks.

    python3 tests/fake_gemini.py 9099 &
    GEMINI_API_KEY=test GEMINI_URL=http://127.0.0.1:9099 ./pocketbase serve ...

It answers every request with the same nine photo quiz questions (plus a few the server must
drop), after checking that a photo came with it as base64 JPEG or PNG. Anything else, such as
the story maker, gets a 400, so those checks see Gemini as failing.
"""
import base64
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

QUESTIONS = [
    {"level": 1, "text": "What colour was the dress?", "right": "Red", "wrong": ["blue", "green"]},
    {"level": 1, "text": "Was there a dog?", "right": "yes", "wrong": ["no"]},
    {"level": 1, "text": "How many balloons were there", "right": "two", "wrong": ["one", "three"]},
    {"level": 2, "text": "What was the boy holding?", "right": "a kite", "wrong": ["a ball", "a book"]},
    {"level": 2, "text": "Where was the cat?", "right": "on the sofa", "wrong": ["under the table", "in a box"]},
    {"level": 2, "text": "What colour was the hat?", "right": "yellow", "wrong": ["pink", "purple"]},
    {"level": 3, "text": "How many windows were there?", "right": "six", "wrong": ["four", "five", "eight"]},
    {"level": 3, "text": "What was behind the girl?", "right": "a tree", "wrong": ["a car", "a wall"]},
    {"level": 3, "text": "Which hand held the cup?", "right": "left", "wrong": ["right"]},
    # Dropped: about looks, the same question twice, no wrong answers, a level that isn't one.
    {"level": 2, "text": "Was the man fat?", "right": "no", "wrong": ["yes"]},
    {"level": 1, "text": "What colour was the dress?", "right": "red", "wrong": ["blue"]},
    {"level": 1, "text": "What was there?", "right": "a dog", "wrong": ["a dog"]},
    {"level": 7, "text": "What was there?", "right": "a dog", "wrong": ["a cat"]},
]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        parts = (body.get("contents") or [{}])[0].get("parts") or []
        images = [p["inlineData"] for p in parts if "inlineData" in p]
        ok = bool(images) and self.headers.get("x-goog-api-key") and body.get("safetySettings")
        if ok:
            data = base64.b64decode(images[0]["data"])
            ok = data[:2] == b"\xff\xd8" or data[:4] == b"\x89PNG"
        if not ok:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error": "not a photo quiz request"}')
            return
        answer = {"usable": True, "questions": QUESTIONS}
        reply = {"candidates": [{"content": {"parts": [{"text": json.dumps(answer)}]}}]}
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(reply).encode())

    def log_message(self, *args):
        pass


HTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
