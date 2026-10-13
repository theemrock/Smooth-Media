#!/usr/bin/env python3
"""Constrained SMOOTH Media coding agent for GitHub Actions."""

import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODEL = "cohere/north-mini-code:free"
REPORT = ROOT / "agent" / "REPORT.md"
TASK = ROOT / "agent" / "TASK.md"

MAX_STEPS = 16
MAX_SECONDS = 600
MAX_WRITES = 6
MAX_TESTS = 3
MAX_FILE_BYTES = 100_000
MAX_CONTENT_CHARS = 60_000

# Read access is intentionally narrow: source, tests, task and build entrypoints.
READ_FILES = {
    "agent/TASK.md",
    "settings.gradle.kts",
    "settings.gradle",
    "build.gradle.kts",
    "build.gradle",
    "app/build.gradle.kts",
    "app/build.gradle",
    "gradle/libs.versions.toml",
    "app/src/main/AndroidManifest.xml",
}
SOURCE_AREAS = ("main", "test", "androidTest")
BLOCKED_NAMES = {
    ".git", ".github", ".gradle", ".idea", "build", "local.properties",
    "gradle.properties", "keystore.properties", "secrets.properties",
    "google-services.json", "google-services.xml", "credentials.json",
    "id_rsa", "id_ed25519",
}

SYSTEM = """You are a constrained Android coding agent for SMOOTH Media.
Read agent/TASK.md and complete ONLY the task specified there.
Return exactly one JSON object per response (no markdown).
Tools:
{"tool":"list","path":"app/src"}
{"tool":"read","path":"app/src/main/AndroidManifest.xml"}
{"tool":"write","path":"app/src/test/java/example/ExampleTest.kt","content":"..."}
{"tool":"test","task":"testDebugUnitTest"}
{"tool":"finish","summary":"..."}
Rules:
- Inspect relevant code before modifying it.
- Make a small change, plus a meaningful automated test.
- Preserve the existing architecture and behavior.
- Do not modify Gradle configuration, workflows or agent scripts.
- Do not request or reveal secrets, tokens, credentials or hidden configuration.
- Do not run Git or shell commands.
- After your FINAL write, run testDebugUnitTest and verify it passes.
- If blocked, finish with a clear explanation. Never invent test results.
"""


def relative_path(name):
    if not isinstance(name, str) or not name.strip():
        raise ValueError("Invalid path")
    raw = Path(name)
    if raw.is_absolute() or ".." in raw.parts:
        raise ValueError("Absolute paths and parent traversal prohibited")
    if any(part.startswith(".") or part in BLOCKED_NAMES for part in raw.parts):
        raise ValueError("Protected path")
    path = ROOT / raw
    cursor = ROOT
    for part in raw.parts:
        cursor = cursor / part
        if cursor.is_symlink():
            raise ValueError("Symlink access prohibited")
    if not path.resolve().is_relative_to(ROOT):
        raise ValueError("Path outside repository")
    return raw.as_posix(), path


def source_file(rel):
    parts = Path(rel).parts
    return (len(parts) >= 5 and parts[:2] == ("app", "src")
            and parts[2] in SOURCE_AREAS
            and Path(rel).suffix in {".kt", ".java"})


def readable(rel):
    if rel in READ_FILES:
        return True
    parts = Path(rel).parts
    return len(parts) >= 4 and parts[:2] == ("app", "src") and parts[2] in SOURCE_AREAS and (
        Path(rel).suffix in {".kt", ".java", ".xml"}
    )


def listable(rel):
    if rel == ".":
        return True
    parts = Path(rel).parts
    return rel == "app" or (parts and parts[0] == "app" and len(parts) >= 2 and parts[1] == "src")


def safe_list(name):
    if name == ".":
        path = ROOT
        rel = "."
    else:
        rel, path = relative_path(name)
    if not listable(rel) or not path.is_dir():
        return {"error": "Directory not allowed or not found"}
    entries = []
    for item in sorted(path.iterdir()):
        if item.is_symlink() or item.name.startswith(".") or item.name in BLOCKED_NAMES:
            continue
        child = item.relative_to(ROOT).as_posix()
        if item.is_dir():
            if listable(child):
                entries.append(child + "/")
        elif readable(child):
            entries.append(child)
    return {"entries": entries[:100]}


def safe_read(name):
    rel, path = relative_path(name)
    if not readable(rel):
        return {"error": "Reading this file is prohibited"}
    if not path.is_file():
        return {"error": "File not found"}
    if path.stat().st_size > MAX_FILE_BYTES:
        return {"error": "File too large"}
    return {"content": path.read_text(encoding="utf-8", errors="replace")[:20000]}


def safe_write(name, content):
    rel, path = relative_path(name)
    if not source_file(rel):
        return {"error": "Writing this file is prohibited"}
    if not isinstance(content, str) or len(content) > MAX_CONTENT_CHARS:
        return {"error": "Invalid content or content too large"}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return {"status": "written", "path": rel}


def run_test(task, timeout):
    if task not in {"testDebugUnitTest", "compileDebugKotlin"}:
        return {"error": "Gradle task not allowed"}
    try:
        proc = subprocess.run(
            ["bash", "./gradlew", task, "--no-daemon"],
            cwd=ROOT, capture_output=True, text=True, timeout=timeout,
        )
        return {"exit_code": proc.returncode,
                "output": (proc.stdout + "\n" + proc.stderr)[-8000:]}
    except subprocess.TimeoutExpired:
        return {"error": f"Gradle timed out after {timeout}s"}


def ask_model(history):
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY missing")
    payload = {
        "model": MODEL,
        "messages": [{"role": "system", "content": SYSTEM}, *history],
        "temperature": 0,
        "max_tokens": 10000,
    }
    for attempt in range(3):
        request = urllib.request.Request(
            "https://openrouter.ai/api/v1/chat/completions",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                data = json.load(response)
            content = data["choices"][0]["message"]["content"]
            if not isinstance(content, str) or not content.strip():
                if attempt == 2:
                    raise RuntimeError("Empty or invalid model response")
                time.sleep(5 * (attempt + 1))
                continue
            return content.strip()
        except urllib.error.HTTPError as exc:
            if exc.code not in {429, 500, 502, 503, 504} or attempt == 2:
                raise RuntimeError(f"OpenRouter HTTP {exc.code}") from None
            time.sleep(5 * (attempt + 1))
    raise RuntimeError("Model unavailable")


def save_report(status, changed, tests, events):
    lines = [
        "# SMOOTH Media Coding Agent Report", "", f"Model: `{MODEL}`",
        f"Status: **{status}**", "", "## Changed Files", "",
        *([f"- `{p}`" for p in sorted(changed)] or ["- None"]),
        "", "## Test Results", "",
        *([f"- `{t['task']}`: {t['result']}" for t in tests] or ["- No tests executed"]),
        "", "## Execution Log", "",
        *[f"{i}. `{e['tool']}` `{e.get('target', '')}`: {e['result']}"
          for i, e in enumerate(events, 1)],
    ]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    print("SMOOTH CODING AGENT START", flush=True)
    print("MODEL:", MODEL, flush=True)
    started = time.monotonic()
    history, events, tests = [], [], []
    changed, inspected = set(), set()
    writes = test_count = 0
    status = "INCOMPLETE"
    change_revision = 0
    passed_revision = None
    try:
        if not TASK.is_file():
            raise RuntimeError("agent/TASK.md not found")
        task = TASK.read_text(encoding="utf-8")
        history.append({"role": "user", "content": "Execute this task:\n\n" + task})
        for step in range(MAX_STEPS):
            remaining = MAX_SECONDS - (time.monotonic() - started)
            if remaining <= 15:
                status = "TIME LIMIT"
                break
            raw = ask_model(history)
            try:
                action = json.loads(raw)
                if not isinstance(action, dict):
                    raise ValueError("Expected object")
            except (ValueError, TypeError):
                events.append({"tool": "parse", "result": "Invalid JSON"})
                history.extend([
                    {"role": "assistant", "content": raw[:3000]},
                    {"role": "user", "content": "Return exactly one valid JSON tool object."},
                ])
                continue
            tool = action.get("tool")
            target = action.get("path", action.get("task", ""))
            print(f"STEP {step + 1}: {tool} {target}", flush=True)
            try:
                if tool == "list":
                    result = safe_list(action.get("path", "."))
                elif tool == "read":
                    result = safe_read(action.get("path"))
                    if "content" in result:
                        inspected.add(relative_path(action["path"])[0])
                elif tool == "write":
                    if writes >= MAX_WRITES:
                        result = {"error": "Write limit reached"}
                    else:
                        rel, path = relative_path(action.get("path"))
                        if path.exists() and rel not in inspected:
                            result = {"error": "Read existing file before modifying it"}
                        else:
                            result = safe_write(action.get("path"), action.get("content"))
                            if result.get("status") == "written":
                                writes += 1
                                change_revision += 1
                                changed.add(rel)
                elif tool == "test":
                    if test_count >= MAX_TESTS:
                        result = {"error": "Test limit reached"}
                    else:
                        test_count += 1
                        remaining = MAX_SECONDS - (time.monotonic() - started)
                        result = run_test(action.get("task"), timeout=max(1, min(240, int(remaining - 10))))
                        ok = result.get("exit_code") == 0
                        tests.append({"task": action.get("task"), "result": "PASS" if ok else "FAIL"})
                        if ok and action.get("task") == "testDebugUnitTest":
                            passed_revision = change_revision
                elif tool == "finish":
                    result = {"status": "finished"}
                else:
                    result = {"error": "Unknown tool"}
            except (ValueError, OSError, UnicodeError) as exc:
                result = {"error": f"{type(exc).__name__}: {exc}"}
            label = result.get("status", result.get("exit_code", result.get("error", "OK")))
            events.append({"tool": str(tool), "target": str(target), "result": str(label)})
            history.extend([
                {"role": "assistant", "content": raw[:65000]},
                {"role": "user", "content": json.dumps(result, ensure_ascii=False)[:10000]},
            ])
            if tool == "finish":
                source_changed = any(p.startswith("app/src/main/") for p in changed)
                test_changed = any(p.startswith(("app/src/test/", "app/src/androidTest/")) for p in changed)
                if not source_changed:
                    status = "NO SOURCE CHANGES"
                elif not test_changed:
                    status = "NO TEST CHANGES"
                elif passed_revision != change_revision:
                    status = "NO PASSING TEST AFTER FINAL WRITE"
                else:
                    status = "FINISHED"
                break
    except Exception as exc:
        status = "ERROR"
        events.append({"tool": "runtime", "result": f"{type(exc).__name__}: {exc}"})
    finally:
        save_report(status, changed, tests, events)
    print("SMOOTH CODING AGENT:", status, flush=True)
    if status != "FINISHED":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
