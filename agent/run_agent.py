#!/usr/bin/env python3
"""Constrained SMOOTH Media coding agent for GitHub Actions."""

import json
import os
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODEL = "cohere/north-mini-code:free"
REPORT = ROOT / "agent" / "REPORT.md"
TASK = ROOT / "agent" / "TASK.md"

MAX_DISCOVERY_STEPS = 8
MAX_DELIVERY_STEPS = 10
MAX_INVALID_STEPS = 4
MAX_SECONDS = 600
MAX_WRITES = 6
MAX_TESTS = 3
MAX_INVENTORY_FILES = 200
MAX_FILE_BYTES = 100_000
MAX_CONTENT_CHARS = 60_000
MAX_HISTORY_RESULT_CHARS = 10_000
MAX_REQUEST_BYTES = 48_000
MAX_OUTPUT_TOKENS = 2048

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
Use exactly one provided function tool per response.
If native function tools are unavailable, return exactly one JSON object (no markdown).
Tools:
{"tool":"list","path":"app/src"}  (one call returns a recursive source inventory)
{"tool":"read","path":"app/src/main/AndroidManifest.xml"}
{"tool":"write","path":"app/src/test/java/example/ExampleTest.kt","content":"..."}
{"tool":"test","task":"testDebugUnitTest"}
{"tool":"finish","summary":"..."}
Rules:
- Inspect relevant code before modifying it.
- Start with one list call for app/src. Do not walk package directories one level at a time.
- Discovery is limited. Select and read only the source and test files relevant to the task.
- Make a small change, plus a meaningful automated test.
- Preserve the existing architecture and behavior.
- Do not modify Gradle configuration, workflows or agent scripts.
- Do not request or reveal secrets, tokens, credentials or hidden configuration.
- Do not run Git or shell commands.
- After your FINAL write, run testDebugUnitTest and verify it passes.
- If blocked, finish with a clear explanation. Never invent test results.
"""

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "list",
            "description": (
                "Return a bounded recursive inventory of readable files below an allowed "
                "repository directory. One app/src call replaces package-by-package listing."
            ),
            "parameters": {
                "type": "object",
                "properties": {"path": {"type": "string"}},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "read",
            "description": "Read an allowed repository file.",
            "parameters": {
                "type": "object",
                "properties": {"path": {"type": "string"}},
                "required": ["path"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "write",
            "description": "Write one allowed Kotlin or Java source or test file.",
            "parameters": {
                "type": "object",
                "properties": {
                    "path": {"type": "string"},
                    "content": {"type": "string"},
                },
                "required": ["path", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "test",
            "description": "Run one allowed Gradle verification task.",
            "parameters": {
                "type": "object",
                "properties": {
                    "task": {
                        "type": "string",
                        "enum": ["testDebugUnitTest", "compileDebugKotlin"],
                    },
                },
                "required": ["task"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "finish",
            "description": "Finish the task with a concise summary or blocker.",
            "parameters": {
                "type": "object",
                "properties": {"summary": {"type": "string"}},
                "required": ["summary"],
            },
        },
    },
]
TOOL_NAMES = {tool["function"]["name"] for tool in TOOLS}


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
    for current, directories, files in os.walk(path, followlinks=False):
        current_path = Path(current)
        directories[:] = sorted(
            directory for directory in directories
            if not directory.startswith(".")
            and directory not in BLOCKED_NAMES
            and not (current_path / directory).is_symlink()
            and listable((current_path / directory).relative_to(ROOT).as_posix())
        )
        for filename in sorted(files):
            item = current_path / filename
            if (filename.startswith(".") or filename in BLOCKED_NAMES
                    or item.is_symlink()):
                continue
            child = item.relative_to(ROOT).as_posix()
            if readable(child):
                entries.append(child)
                if len(entries) > MAX_INVENTORY_FILES:
                    return {
                        "entries": entries[:MAX_INVENTORY_FILES],
                        "truncated": True,
                    }
    return {"entries": entries, "truncated": False}


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

    def ignore_protected(source, names):
        source_path = Path(source)
        return [
            name for name in names
            if name.startswith(".") or name in BLOCKED_NAMES
            or name in {"agent", ".github"}
            or (source_path / name).is_symlink()
        ]

    allowed_environment = {
        "ANDROID_HOME", "ANDROID_SDK_ROOT", "CI", "COMSPEC", "GRADLE_USER_HOME",
        "HOME", "JAVA_HOME", "LANG", "LC_ALL", "PATH", "PATHEXT", "SystemRoot",
        "TEMP", "TERM", "TMP", "USERPROFILE",
    }
    environment = {key: value for key, value in os.environ.items() if key in allowed_environment}
    try:
        with tempfile.TemporaryDirectory(prefix="smooth-agent-test-") as directory:
            workspace = Path(directory) / "repository"
            shutil.copytree(ROOT, workspace, ignore=ignore_protected)
            proc = subprocess.run(
                ["bash", "./gradlew", task, "--no-daemon"],
                cwd=workspace, capture_output=True, text=True, timeout=timeout,
                env=environment,
            )
        return {"exit_code": proc.returncode,
                "output": (proc.stdout + "\n" + proc.stderr)[-8000:]}
    except subprocess.TimeoutExpired:
        return {"error": f"Gradle timed out after {timeout}s"}


def native_tool_action(message):
    calls = message.get("tool_calls")
    if not isinstance(calls, list) or len(calls) != 1:
        return None
    call = calls[0]
    if (not isinstance(call, dict) or call.get("type") != "function"
            or not isinstance(call.get("id"), str) or not call["id"].strip()):
        return None
    function = call.get("function")
    if not isinstance(function, dict):
        return None
    name = function.get("name")
    arguments = function.get("arguments")
    if name not in TOOL_NAMES or not isinstance(arguments, str):
        return None
    try:
        values = json.loads(arguments)
    except (json.JSONDecodeError, TypeError):
        return None
    if not isinstance(values, dict) or "tool" in values:
        return None
    return json.dumps({"tool": name, **values}, ensure_ascii=False)


def ask_model(history, deadline=None, tools=None):
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY missing")
    payload = {
        "model": MODEL,
        "messages": [{"role": "system", "content": SYSTEM}, *history],
        "temperature": 0,
        "max_tokens": MAX_OUTPUT_TOKENS,
        "tools": TOOLS if tools is None else tools,
        "tool_choice": "auto",
        "parallel_tool_calls": False,
    }
    encoded_payload = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    if len(encoded_payload) > MAX_REQUEST_BYTES:
        raise RuntimeError(
            f"Request budget exceeded: bytes={len(encoded_payload)} "
            f"messages={len(payload['messages'])} limit={MAX_REQUEST_BYTES}"
        )
    print(f"REQUEST: bytes={len(encoded_payload)} messages={len(payload['messages'])} "
          f"tools={len(payload['tools'])} max_tokens={MAX_OUTPUT_TOKENS}", flush=True)
    for attempt in range(3):
        if deadline is None:
            request_timeout = 120
        else:
            remaining = deadline - time.monotonic()
            if remaining <= 15:
                raise RuntimeError("Agent time limit reached before model response")
            request_timeout = max(1, min(120, int(remaining - 15)))
        request = urllib.request.Request(
            "https://openrouter.ai/api/v1/chat/completions",
            data=encoded_payload,
            headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=request_timeout) as response:
                data = json.load(response)
            choices = data.get("choices") if isinstance(data, dict) else None
            choice = choices[0] if isinstance(choices, list) and choices else None
            message = choice.get("message") if isinstance(choice, dict) else None
            content = message.get("content") if isinstance(message, dict) else None
            native_action = native_tool_action(message) if isinstance(message, dict) else None
            if native_action is not None:
                return {
                    "action": native_action,
                    "assistant": message,
                    "tool_call_id": message["tool_calls"][0]["id"],
                }
            if not isinstance(content, str) or not content.strip():
                if attempt == 2:
                    calls = message.get("tool_calls") if isinstance(message, dict) else None
                    call_count = len(calls) if isinstance(calls, list) else 0
                    raise RuntimeError(
                        "OpenRouter returned no usable action "
                        f"(finish_reason={choice.get('finish_reason') if isinstance(choice, dict) else None}, "
                        f"content_type={type(content).__name__}, "
                        f"tool_calls={call_count})"
                    )
                retry_delay = 5 * (attempt + 1)
                if deadline is not None and time.monotonic() + retry_delay + 15 >= deadline:
                    raise RuntimeError("Agent time limit reached while retrying model response")
                time.sleep(retry_delay)
                continue
            return {
                "action": content.strip(),
                "assistant": message,
                "tool_call_id": None,
            }
        except urllib.error.HTTPError as exc:
            if exc.code not in {429, 500, 502, 503, 504} or attempt == 2:
                detail = safe_http_error_detail(exc)
                suffix = f": {detail}" if detail else ""
                raise RuntimeError(f"OpenRouter HTTP {exc.code}{suffix}") from None
            retry_delay = 5 * (attempt + 1)
            if deadline is not None and time.monotonic() + retry_delay + 15 >= deadline:
                raise RuntimeError("Agent time limit reached while retrying OpenRouter") from None
            time.sleep(retry_delay)
        except (urllib.error.URLError, TimeoutError) as exc:
            if attempt == 2:
                raise RuntimeError(
                    f"OpenRouter network error: {type(exc).__name__}"
                ) from None
            retry_delay = 5 * (attempt + 1)
            if deadline is not None and time.monotonic() + retry_delay + 15 >= deadline:
                raise RuntimeError("Agent time limit reached while retrying OpenRouter") from None
            time.sleep(retry_delay)
    raise RuntimeError("Model unavailable")


def safe_http_error_detail(error):
    """Extract structural provider details without exposing response text or payloads."""
    try:
        raw = error.read(8192)
    except (OSError, ValueError):
        return ""
    if not raw:
        return ""
    try:
        payload = json.loads(raw.decode("utf-8", errors="replace"))
    except (json.JSONDecodeError, UnicodeError):
        return ""
    provider_error = payload.get("error") if isinstance(payload, dict) else None
    if not isinstance(provider_error, dict):
        return ""
    message = provider_error.get("message")
    if not isinstance(message, str) or not message.strip():
        return ""
    lower_message = message.lower()
    if "tool" in lower_message and ("message" in lower_message
                                     or "call" in lower_message
                                     or "sequence" in lower_message):
        category = "tool-message-validation"
    elif "context" in lower_message:
        category = "context-limit"
    elif "model" in lower_message:
        category = "model-request"
    elif "invalid" in lower_message or "parameter" in lower_message:
        category = "invalid-request"
    else:
        category = "provider-rejected-request"

    fields = [f"category={category}"]
    for key in ("code", "type"):
        value = provider_error.get(key)
        if isinstance(value, (int, float)):
            fields.append(f"{key}={value}")
        elif isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", value):
            fields.append(f"{key}={value}")
    parameter = provider_error.get("param", provider_error.get("field"))
    if parameter in {"messages", "tools", "model", "max_tokens", "temperature", "tool_choice"}:
        fields.append(f"param={parameter}")
    return " ".join(fields)


def serialize_history_result(result):
    result_content = json.dumps(result, ensure_ascii=False)
    if len(result_content) <= MAX_HISTORY_RESULT_CHARS:
        return result_content
    if isinstance(result, dict) and isinstance(result.get("entries"), list):
        entries = result["entries"]
        compact = {
            **{key: value for key, value in result.items() if key != "entries"},
            "entries": [],
            "truncated": True,
            "total_entries": len(entries),
        }
        for entry in entries:
            compact["entries"].append(entry)
            candidate = json.dumps(compact, ensure_ascii=False)
            if len(candidate) > MAX_HISTORY_RESULT_CHARS:
                compact["entries"].pop()
                break
        return json.dumps(compact, ensure_ascii=False)
    if isinstance(result, dict):
        for field in ("content", "output"):
            value = result.get(field)
            if not isinstance(value, str):
                continue
            compact = {
                **result,
                field: "",
                "truncated": True,
                "original_chars": len(value),
            }
            overhead = len(json.dumps(compact, ensure_ascii=False))
            compact[field] = value[:max(0, MAX_HISTORY_RESULT_CHARS - overhead)]
            candidate = json.dumps(compact, ensure_ascii=False)
            if len(candidate) > MAX_HISTORY_RESULT_CHARS:
                compact[field] = compact[field][:
                    max(0, len(compact[field]) - (len(candidate) - MAX_HISTORY_RESULT_CHARS))
                ]
            return json.dumps(compact, ensure_ascii=False)
    return json.dumps({
        "error": "Tool result exceeded history limit",
        "truncated": True,
    })


def append_history(history, reply, result):
    result_content = serialize_history_result(result)
    if reply["tool_call_id"]:
        history.extend([
            reply["assistant"],
            {
                "role": "tool",
                "tool_call_id": reply["tool_call_id"],
                "content": result_content,
            },
        ])
    else:
        history.extend([
            {"role": "assistant", "content": reply["action"][:65000]},
            {"role": "user", "content": result_content},
        ])


def save_report(status, changed, tests, events, summary):
    lines = [
        "# SMOOTH Media Coding Agent Report", "", f"Model: `{MODEL}`",
        f"Status: **{status}**", "", "## Summary", "",
        summary or "- No summary provided", "", "## Changed Files", "",
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
    discovery_steps = delivery_steps = invalid_steps = 0
    discovery_notice_sent = False
    read_cache = set()
    status = "INCOMPLETE"
    summary = ""
    change_revision = 0
    passed_revision = None
    try:
        if not TASK.is_file():
            raise RuntimeError("agent/TASK.md not found")
        task = TASK.read_text(encoding="utf-8")
        history.append({"role": "user", "content": "Execute this task:\n\n" + task})
        step = 0
        while True:
            remaining = MAX_SECONDS - (time.monotonic() - started)
            if remaining <= 15:
                status = "TIME LIMIT"
                break
            if (delivery_steps >= MAX_DELIVERY_STEPS
                    or invalid_steps >= MAX_INVALID_STEPS):
                break
            discovery_available = discovery_steps < MAX_DISCOVERY_STEPS
            if not discovery_available and not discovery_notice_sent:
                history.append({
                    "role": "user",
                    "content": (
                        "Discovery budget exhausted. Use the files already inspected and "
                        "continue with write, test, or finish."
                    ),
                })
                discovery_notice_sent = True
            active_names = {"write", "test", "finish"}
            if discovery_available:
                active_names.update({"list", "read"})
            active_tools = [
                tool for tool in TOOLS
                if tool["function"]["name"] in active_names
            ]
            step += 1
            reply = ask_model(
                history,
                deadline=started + MAX_SECONDS,
                tools=active_tools,
            )
            raw = reply["action"]
            try:
                action = json.loads(raw)
                if not isinstance(action, dict):
                    raise ValueError("Expected object")
            except (ValueError, TypeError):
                invalid_steps += 1
                events.append({"tool": "parse", "result": "Invalid JSON"})
                history.extend([
                    {"role": "assistant", "content": raw[:3000]},
                    {"role": "user", "content": "Return exactly one valid JSON tool object."},
                ])
                continue
            tool = action.get("tool")
            target = action.get("path", action.get("task", ""))
            print(f"STEP {step}: {tool} {target}", flush=True)
            try:
                if tool == "list":
                    if discovery_steps >= MAX_DISCOVERY_STEPS:
                        invalid_steps += 1
                        result = {"error": "Discovery limit reached; use write, test, or finish"}
                    else:
                        discovery_steps += 1
                        result = safe_list(action.get("path", "."))
                elif tool == "read":
                    if discovery_steps >= MAX_DISCOVERY_STEPS:
                        invalid_steps += 1
                        result = {"error": "Discovery limit reached; use write, test, or finish"}
                    else:
                        discovery_steps += 1
                        rel, _ = relative_path(action.get("path"))
                        if rel in read_cache:
                            result = {"status": "already_read", "path": rel,
                                      "instruction": "Use the earlier file content; do not read this path again."}
                        else:
                            result = safe_read(action.get("path"))
                            if "content" in result:
                                inspected.add(rel)
                                read_cache.add(rel)
                elif tool == "write":
                    delivery_steps += 1
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
                    delivery_steps += 1
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
                    delivery_steps += 1
                    result = {"status": "finished"}
                    if isinstance(action.get("summary"), str):
                        summary = action["summary"].strip()[:2000]
                else:
                    invalid_steps += 1
                    result = {"error": "Unknown tool"}
            except (ValueError, OSError, UnicodeError) as exc:
                result = {"error": f"{type(exc).__name__}: {exc}"}
            label = result.get("status", result.get("exit_code", result.get("error", "OK")))
            events.append({"tool": str(tool), "target": str(target), "result": str(label)})
            append_history(history, reply, result)
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
        save_report(status, changed, tests, events, summary)
    print("SMOOTH CODING AGENT:", status, flush=True)
    if status != "FINISHED":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
