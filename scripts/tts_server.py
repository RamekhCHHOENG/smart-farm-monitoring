"""Speaks sentences with the Kokoro-82M neural voice for the demo video's voice-over.

Reads one JSON request per line on stdin: {"text": "...", "out": "file.wav"}
and answers one JSON line: {"ms": <length of the audio in milliseconds>}.
The model is loaded once, so each sentence takes about a second.

Needs: pip install kokoro-onnx soundfile, the model (kokoro-q8.onnx) and a voices .npz file.
    python tts_server.py <model.onnx> <voices.npz> <voice, e.g. af_heart>
"""
import json
import sys

import soundfile as sf
from kokoro_onnx import Kokoro
from kokoro_onnx.config import EspeakConfig

model, voices, voice = sys.argv[1], sys.argv[2], sys.argv[3]
# Use the system espeak-ng (apt install espeak-ng) for turning text into phonemes
kokoro = Kokoro(model, voices, espeak_config=EspeakConfig(
    lib_path="/usr/lib/x86_64-linux-gnu/libespeak-ng.so.1",
    data_path="/usr/lib/x86_64-linux-gnu/espeak-ng-data"))
print(json.dumps({"ready": True}), flush=True)

for line in sys.stdin:
    request = json.loads(line)
    audio, rate = kokoro.create(request["text"], voice=voice, speed=1.0, lang="en-us")
    sf.write(request["out"], audio, rate)
    print(json.dumps({"ms": round(len(audio) / rate * 1000)}), flush=True)
