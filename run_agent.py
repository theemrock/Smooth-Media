
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
MAX_STEPS = 12
MAX_SECONDS = 600

SYSTEM = """
You are an autonomous Android coding agent for SMOOTH Media.

Read agent/TASK.md and perform the requested coding task.

Rules:
- Inspect files before modifying them.
- Make the smallest meaningful source-code improvement.
- Add or update a relevant automated test.
- Preserve architecture, package names, SDK and dependencies.
- Never access secrets or hidden configuration.
- Never modify GitHub workflows, Gradle configuration or agent scripts.
- Never run git commands.
- Never claim a test passed without actual evidence.
- If blocked, explain the blocker and stop.

Respond with exactly one JSON object per turn:
{"tool":"read","path":"relative/path"}
{"tool":"list","path":"relative/path"}
{"tool":"write","path":"relative/path","content":"full file content"}
{"tool":"test","task":"testDebugUnitTest"}
{"tool":"finish","summary":"what was done"}

No Markdown fences. No additional text.
"""

history = []
events = []
started = time.monotonic()


def safe_path(name):
    if not isinstance(name, str) or not name:
        raise ValueError("Invalid path")

    path = (ROOT / name).resolve()
    if not path.is_relative_to(ROOT):
        raise ValueError("Path outside repository")

    parts = path.relative_to(ROOT).parts
    blocked = {".git", ".github", ".gradle", "build"}
    if any(part in blocked for part in parts):
        raise ValueError("Protected path")

    return path


def allowed_write(path):
    relative = path.relative_to(ROOT)
    parts = relative.parts

    if len(parts) < 3 or parts[0] != "app":
        return False

    if parts[1] != "src":
        return False

    if "build" in parts:
        return False

    return path.suffix in {".kt", ".java"}


def execute(command, timeout):
    try:
        result = subprocess.run(
            command,
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        return {
            "exit_code": result.returncode,
            "output": (result.stdout + "\n" + result.stderr)[-6000:],
        }
    except subprocess.TimeoutExpired:
        return {"error": "Command timed out"}


def run_tool(action):
    tool = action.get("tool")

    if tool == "list":
        path = safe_path(action.get("path", "."))
        if not path.is_dir():
            return {"error": "Not a directory"}

        return {
            "entries": [
                str(p.relative_to(ROOT))
                for p in sorted(path.iterdir())
                if not p.name.startswith(".")
            ][:100]
        }

    if tool == "read":
        path = safe_path(action.get("path"))
        if not path.is_file():
            return {"error": "File not found"}

        if path.stat().st_size > 100000:
            return {"error": "File too large"}

        return {"content": path.read_text(
            encoding="utf-8", errors="replace"
        )[:20000]}

    if tool == "write":
        path = safe_path(action.get("path"))
        if not allowed_write(path):
            return {"error": "Writing this path is prohibited"}

        content = action.get("content")
        if not isinstance(content, str) or len(content) > 60000:
            return {"error": "Invalid file content"}

        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return {"status": "written", "path": str(path.relative_to(ROOT))}

    if tool == "test":
        task = action.get("task")
        if task not in {"testDebugUnitTest", "compileDebugKotlin"}:
            return {"error": "Gradle task not allowed"}

        return execute(
            ["bash", "./gradlew", task, "--no-daemon"],
            timeout=240,
        )

    if tool == "finish":
        return {"status": "finished"}

    return {"error": "Unknown tool"}


def ask_model():
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY missing")

    payload = {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM},
            *history,
        ],
        "temperature": 0,
        "max_tokens": 12000,
    }

    request = urllib.request.Request(
        "https://openrouter.ai/api/v1/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    for attempt in range(3):
        try:
            with urllib.request.urlopen(
                request, timeout=120
            ) as response:
                data = json.load(response)

            content = data["choices"][0]["message"]["content"]
            if not isinstance(content, str):
                raise RuntimeError("Invalid model response")

            return content.strip()

        except urllib.error.HTTPError as exc:
            if exc.code not in {429, 500, 502, 503, 504}:
                raise RuntimeError(
                    f"OpenRouter HTTP {exc.code}"
                ) from None

            if attempt == 2:
                raise RuntimeError(
                    f"OpenRouter HTTP {exc.code}"
                ) from None

            time.sleep(5 * (attempt + 1))

    raise RuntimeError("Model unavailable")


def save_report(status):
    lines = [
        "# SMOOTH Media Coding Agent Report",
        "",
        f"Model: `{MODEL}`",
        f"Status: **{status}**",
        "",
        "## Execution log",
        "",
    ]

    for index, event in enumerate(events, 1):
        lines.append(
            f"{index}. `{event['tool']}`: "
            f"{event.get('target', '')} "
            f"→ {event['result']}"
        )

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    print("SMOOTH CODING AGENT START", flush=True)

    task = (ROOT / "agent" / "TASK.md").read_text(
        encoding="utf-8"
    )

    history.append({
        "role": "user",
        "content": "Execute this task:\n\n" + task,
    })

    status = "INCOMPLETE"

    try:
        for step in range(MAX_STEPS):
            if time.monotonic() - started > MAX_SECONDS:
                status = "TIME LIMIT"
                break

            raw = ask_model()

            try:
                action = json.loads(raw)
                if not isinstance(action, dict):
                    raise ValueError("Expected JSON object")
            except (ValueError, json.JSONDecodeError):
                events.append({
                    "tool": "parse",
                    "result": "Invalid JSON response",
                })
                history.append({
                    "role": "assistant",
                    "content": raw[:3000],
                })
                history.append({
                    "role": "user",
                    "content": "Invalid JSON. Return one valid tool object.",
                })
                continue

            tool = action.get("tool", "unknown")
            target = action.get("path", action.get("task", ""))

            print(
                f"STEP {step + 1}: {tool} {target}",
                flush=True,
            )

            result = run_tool(action)

            events.append({
                "tool": tool,
                "target": target,
                "result": (
                    result.get("status")
                    or result.get("exit_code")
                    or result.get("error")
                    or "OK"
                ),
            })

            history.append({
                "role": "assistant",
                "content": raw,
            })

            history.append({
                "role": "user",
                "content": json.dumps(result, ensure_ascii=False)[:8000],
            })

            if tool == "finish":
                status = "FINISHED"
                break

    except Exception as exc:
        status = "ERROR"
        events.append({
            "tool": "runtime",
            "result": type(exc).__name__ + ": " + str(exc),
        })

    finally:
        save_report(status)

    print("SMOOTH CODING AGENT:", status, flush=True)

    if status != "FINISHED":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
