
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

MAX_STEPS = 16
MAX_SECONDS = 600
MAX_WRITES = 6
MAX_TESTS = 3

SYSTEM = """
You are an autonomous Android coding agent for SMOOTH Media.

Read agent/TASK.md and complete the requested coding task.

You have five tools:
- list: list repository directory contents
- read: read a repository file
- write: create or replace an allowed Android source/test file
- test: run an allowed Gradle task
- finish: finish the task

Return exactly one JSON object per response.

Examples:
{"tool":"list","path":"app/src"}
{"tool":"read","path":"app/src/main/AndroidManifest.xml"}
{"tool":"write","path":"app/src/test/java/example/Test.kt","content":"..."}
{"tool":"test","task":"testDebugUnitTest"}
{"tool":"finish","summary":"Completed task and verified tests"}

Rules:
- Inspect existing code before modifying it.
- Make one small, meaningful Android source-code improvement.
- Add or update a relevant automated test.
- Preserve existing architecture and behavior.
- Never modify Gradle configuration, workflows or agent scripts.
- Never access secrets, credentials or hidden configuration.
- Never run Git commands.
- Never invent test results.
- If blocked, finish with an explanation.
- Do not wrap JSON in Markdown fences.
"""

history = []
events = []


def safe_path(name):
    if not isinstance(name, str) or not name:
        raise ValueError("Invalid path")

    path = (ROOT / name).resolve()

    if not path.is_relative_to(ROOT):
        raise ValueError("Path outside repository")

    relative = path.relative_to(ROOT)
    blocked = {
        ".git", ".github", ".gradle", "build",
        ".idea", "local.properties"
    }

    if any(part in blocked for part in relative.parts):
        raise ValueError("Protected path")

    if path.is_symlink():
        raise ValueError("Symlink access prohibited")

    return path


def allowed_write(path):
    relative = path.relative_to(ROOT)
    parts = relative.parts

    if len(parts) < 4:
        return False

    if parts[0:2] != ("app", "src"):
        return False

    if parts[2] not in {
        "main", "test", "androidTest"
    }:
        return False

    if path.suffix not in {".kt", ".java"}:
        return False

    return True


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
            "output": (
                result.stdout + "\n" + result.stderr
            )[-8000:],
        }

    except subprocess.TimeoutExpired:
        return {
            "error": f"Command timed out after {timeout}s"
        }


def run_tool(action):
    tool = action.get("tool")

    if tool == "list":
        path = safe_path(action.get("path", "."))

        if not path.is_dir():
            return {"error": "Not a directory"}

        entries = []

        for item in sorted(path.iterdir()):
            if item.name.startswith("."):
                continue

            entries.append(
                str(item.relative_to(ROOT))
                + ("/" if item.is_dir() else "")
            )

        return {"entries": entries[:100]}

    if tool == "read":
        path = safe_path(action.get("path"))

        if not path.is_file():
            return {"error": "File not found"}

        if path.stat().st_size > 100000:
            return {"error": "File too large"}

        return {
            "content": path.read_text(
                encoding="utf-8",
                errors="replace"
            )[:20000]
        }

    if tool == "write":
        path = safe_path(action.get("path"))

        if not allowed_write(path):
            return {
                "error": "Writing this path is prohibited"
            }

        content = action.get("content")

        if not isinstance(content, str):
            return {"error": "Content must be a string"}

        if len(content) > 60000:
            return {"error": "File content too large"}

        path.parent.mkdir(
            parents=True,
            exist_ok=True
        )

        path.write_text(
            content,
            encoding="utf-8"
        )

        return {
            "status": "written",
            "path": str(path.relative_to(ROOT))
        }

    if tool == "test":
        task = action.get("task")

        allowed_tasks = {
            "testDebugUnitTest",
            "compileDebugKotlin",
        }

        if task not in allowed_tasks:
            return {
                "error": "Gradle task not allowed"
            }

        return execute(
            [
                "bash",
                "./gradlew",
                task,
                "--no-daemon"
            ],
            timeout=240,
        )

    if tool == "finish":
        return {"status": "finished"}

    return {"error": "Unknown tool"}


def ask_model():
    key = os.environ.get("OPENROUTER_API_KEY")

    if not key:
        raise RuntimeError(
            "OPENROUTER_API_KEY missing"
        )

    payload = {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM},
            *history,
        ],
        "temperature": 0,
        "max_tokens": 10000,
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
                request,
                timeout=120
            ) as response:
                data = json.load(response)

            content = data["choices"][0]["message"]["content"]

            if not isinstance(content, str):
                raise RuntimeError(
                    "Invalid model response"
                )

            if not content.strip():
                raise RuntimeError(
                    "Empty model response"
                )

            return content.strip()

        except urllib.error.HTTPError as exc:
            if exc.code not in {
                429, 500, 502, 503, 504
            }:
                raise RuntimeError(
                    f"OpenRouter HTTP {exc.code}"
                ) from None

            if attempt == 2:
                raise RuntimeError(
                    f"OpenRouter HTTP {exc.code}"
                ) from None

            time.sleep(5 * (attempt + 1))

    raise RuntimeError("Model unavailable")


def save_report(status, changed_files, test_results):
    lines = [
        "# SMOOTH Media Coding Agent Report",
        "",
        f"Model: `{MODEL}`",
        f"Status: **{status}**",
        "",
        "## Changed Files",
        "",
    ]

    if changed_files:
        for path in sorted(changed_files):
            lines.append(f"- `{path}`")
    else:
        lines.append("- None")

    lines.extend([
        "",
        "## Test Results",
        "",
    ])

    if test_results:
        for test in test_results:
            lines.append(
                f"- `{test['task']}`: "
                f"{test['result']}"
            )
    else:
        lines.append("- No tests executed")

    lines.extend([
        "",
        "## Execution Log",
        "",
    ])

    for index, event in enumerate(events, 1):
        lines.append(
            f"{index}. `{event['tool']}` "
            f"`{event.get('target', '')}`: "
            f"{event['result']}"
        )

    REPORT.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    REPORT.write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )


def main():
    print(
        "SMOOTH CODING AGENT START",
        flush=True
    )
    print("MODEL:", MODEL, flush=True)

    started = time.monotonic()

    task_file = ROOT / "agent" / "TASK.md"

    if not task_file.is_file():
        raise RuntimeError(
            "agent/TASK.md not found"
        )

    task = task_file.read_text(
        encoding="utf-8"
    )

    history.append({
        "role": "user",
        "content": (
            "Execute this task:\n\n"
            + task
        ),
    })

    status = "INCOMPLETE"
    changed_files = set()
    inspected_files = set()
    test_results = []

    write_count = 0
    test_count = 0

    try:
        for step in range(MAX_STEPS):
            if time.monotonic() - started > MAX_SECONDS:
                status = "TIME LIMIT"
                break

            raw = ask_model()

            try:
                action = json.loads(raw)

                if not isinstance(action, dict):
                    raise ValueError(
                        "Expected JSON object"
                    )

            except ValueError:
                events.append({
                    "tool": "parse",
                    "result": "Invalid JSON response"
                })

                history.append({
                    "role": "assistant",
                    "content": raw[:3000],
                })

                history.append({
                    "role": "user",
                    "content": (
                        "Invalid JSON. Return exactly "
                        "one valid JSON tool object."
                    ),
                })

                continue

            tool = action.get(
                "tool",
                "unknown"
            )

            target = action.get(
                "path",
                action.get("task", "")
            )

            print(
                f"STEP {step + 1}: "
                f"{tool} {target}",
                flush=True
            )

            if tool == "write":
                if write_count >= MAX_WRITES:
                    result = {
                        "error": "Write limit reached"
                    }
                else:
                    try:
                        path = safe_path(
                            action.get("path")
                        )
                        relative = str(
                            path.relative_to(ROOT)
                        )

                        if (
                            path.exists()
                            and relative not in inspected_files
                        ):
                            result = {
                                "error": (
                                    "Read existing file "
                                    "before modifying it"
                                )
                            }
                        else:
                            result = run_tool(action)

                    except ValueError as exc:
                        result = {
                            "error": str(exc)
                        }

                if result.get("status") == "written":
                    write_count += 1
                    changed_files.add(
                        result["path"]
                    )

            elif tool == "test":
                if test_count >= MAX_TESTS:
                    result = {
                        "error": "Test limit reached"
                    }
                else:
                    test_count += 1
                    result = run_tool(action)

                    test_results.append({
                        "task": action.get("task"),
                        "result": (
                            "PASS"
                            if result.get("exit_code") == 0
                            else "FAIL"
                        ),
                    })

            else:
                try:
                    result = run_tool(action)
                except (
                    ValueError,
                    OSError,
                    UnicodeError
                ) as exc:
                    result = {
                        "error": (
                            type(exc).__name__
                            + ": "
                            + str(exc)
                        )
                    }

                if (
                    tool == "read"
                    and "content" in result
                ):
                    inspected_files.add(
                        str(
                            safe_path(
                                action["path"]
                            ).relative_to(ROOT)
                        )
                    )

            result_label = (
                result.get("status")
                if "status" in result
                else result.get("exit_code")
                if "exit_code" in result
                else result.get("error", "OK")
            )

            events.append({
                "tool": tool,
                "target": str(target),
                "result": str(result_label),
            })

            history.append({
                "role": "assistant",
                "content": raw[:65000],
            })

            history.append({
                "role": "user",
                "content": json.dumps(
                    result,
                    ensure_ascii=False
                )[:10000],
            })

            if tool == "finish":
                source_changed = any(
                    p.startswith("app/src/main/")
                    for p in changed_files
                )

                test_changed = any(
                    p.startswith("app/src/test/")
                    or p.startswith("app/src/androidTest/")
                    for p in changed_files
                )

                test_passed = any(
                    t["result"] == "PASS"
                    and t["task"] == "testDebugUnitTest"
                    for t in test_results
                )

                if not source_changed:
                    status = "NO SOURCE CHANGES"
                elif not test_changed:
                    status = "NO TEST CHANGES"
                elif not test_passed:
                    status = "NO PASSING UNIT TESTS"
                else:
                    status = "FINISHED"

                break

    except Exception as exc:
        status = "ERROR"

        events.append({
            "tool": "runtime",
            "result": (
                type(exc).__name__
                + ": "
                + str(exc)
            ),
        })

    finally:
        save_report(
            status,
            changed_files,
            test_results
        )

    print(
        "SMOOTH CODING AGENT:",
        status,
        flush=True
    )

    if status != "FINISHED":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
