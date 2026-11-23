#!/usr/bin/env python3
"""A stand-in for Gemini's generateContent, for api_test.py's photo quiz checks.

    python3 tests/fake_gemini.py 9099 &
    GEMINI_API_KEY=test GEMINI_URL=http://127.0.0.1:9099 ./pocketbase serve ...

It answers every request with the same nine photo quiz questions (plus a few the server must
drop), after checking that a photo came with it as base64 JPEG or PNG. A request to draw the
photo quiz's made-up picture (a memory game picture) gets a small PNG. Text-to-speech requests
(the Interactions API) in the voice Riko (looked up in the fake voice library) get a short beep as
bare PCM, like the older TTS models send. Anything
else, such as the story maker, gets a 400, so those checks see Gemini as failing.
"""
import math
import os
import time
import base64
import json
import sys
import struct
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def png(width=4, height=3):
    """A small plain PNG, standing in for a drawn picture."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\x00" + b"\x40\xa0\xff" * width for _ in range(height))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


PICTURE = png()

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
        if self.path.endswith("/interactions"):
            return self.speak(body)
        parts = (body.get("contents") or [{}])[0].get("parts") or []
        if (body.get("generationConfig") or {}).get("responseModalities") == ["IMAGE"]:
            return self.draw(body, parts)
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

    def draw(self, body, parts):
        prompt = " ".join(p.get("text", "") for p in parts)
        if "memory game" not in prompt or not body.get("safetySettings") or not self.headers.get("x-goog-api-key"):
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error": "not a photo quiz picture"}')
            return
        image = {"inlineData": {"mimeType": "image/png", "data": base64.b64encode(PICTURE).decode()}}
        reply = {"candidates": [{"content": {"parts": [image]}}]}
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(reply).encode())

    def do_GET(self):
        # The voice library: knows Riko, by another ID.
        if self.path.startswith("/v1beta/voices") and "search=Riko" in self.path:
            reply = {"voices": [{"id": "riko-library-id", "displayName": "Riko"}, {"id": "x", "displayName": "Rikodo"}]}
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(reply).encode())
            return
        self.send_response(404)
        self.end_headers()

    def speak(self, body):
        text = (((body.get("input") or [{}])[0].get("content") or [{}])[0]).get("text")
        voice = ((body.get("generation_config") or {}).get("speech_config") or [{}])[0].get("voice")
        if not (text and voice == "riko-library-id" and "tts" in body.get("model", "") and (body.get("response_format") or {}).get("mime_type") == "audio/l16" and self.headers.get("x-goog-api-key")):
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error": "not a speech request"}')
            return
        # A tenth of a second, or FAKE_TTS_SECONDS (to time a long story part), after FAKE_TTS_DELAY seconds.
        time.sleep(float(os.environ.get("FAKE_TTS_DELAY", "0")))
        samples = int(24000 * float(os.environ.get("FAKE_TTS_SECONDS", "0.1")))
        pcm = b"".join(int(8000 * math.sin(i / 10)).to_bytes(2, "little", signed=True) for i in range(samples))
        if os.environ.get("FAKE_TTS_WAV"):
            # A WAV file instead, with a bogus length and a chunk after the samples, like some encoders.
            pcm = b"RIFF\xff\xff\xff\xffWAVEfmt " + (16).to_bytes(4, "little") + bytes.fromhex("01000100c05d0000803b000002001000") \
                + b"data\xff\xff\xff\xff" + pcm + b"LIST" + (64).to_bytes(4, "little") + bytes(range(64))
        reply = {"steps": [{"type": "model_output", "content": [{"type": "audio", "data": base64.b64encode(pcm).decode()}]}]}
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(reply).encode())

    def log_message(self, *args):
        pass


ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
