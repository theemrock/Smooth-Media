
import json
import os
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "agent" / "REPORT.md"
MODEL = "cohere/north-mini-code:free"

FILES = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "app/build.gradle.kts",
    "agent/TASK.md",
]

def read_project():
    sections = []
    for name in FILES:
        path = ROOT / name
        if path.is_file():
            content = path.read_text(
                encoding="utf-8", errors="replace"
            )[:12000]
            sections.append(f"FILE: {name}\n{content}")
    return "\n\n".join(sections)

def check_gradle():
    wrapper = ROOT / "gradlew"
    if not wrapper.is_file():
        return "Gradle wrapper not found."

    try:
        result = subprocess.run(
            ["bash", "./gradlew", "help", "--no-daemon"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=180,
        )
        output = (result.stdout + "\n" + result.stderr)[-5000:]
        return f"Exit code: {result.returncode}\n{output}"
    except subprocess.TimeoutExpired:
        return "Gradle help timed out after 180 seconds."
    except Exception as exc:
        return f"Gradle execution failed: {type(exc).__name__}"

def call_model(project, gradle):
    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY missing")

    prompt = f"""
You are inspecting the SMOOTH Media Android repository.

Create a concise Markdown technical report.

Include:
1. Project structure
2. Android build configuration
3. Gradle test tasks, distinguishing verified from inferred
4. Gradle help execution result
5. Risks and recommended next steps

Use only the supplied evidence.
Do not invent test results.
Do not include secrets.
Do not use Markdown code fences around the entire report.

PROJECT FILES:
{project}

GRADLE RESULT:
{gradle}
"""

    payload = {
        "model": MODEL,
        "messages": [
            {"role": "user", "content": prompt}
        ],
        "max_tokens": 3000,
        "temperature": 0,
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

    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            data = json.load(response)
    except urllib.error.HTTPError as exc:
        raise RuntimeError(
            f"OpenRouter HTTP {exc.code}"
        ) from None

    content = data["choices"][0]["message"]["content"]
    if not isinstance(content, str) or not content.strip():
        raise RuntimeError("Model returned an empty report")

    return content

def main():
    print("SMOOTH AGENT START")
    print("MODEL:", MODEL)

    project = read_project()
    if not project:
        raise RuntimeError("No project files found")

    gradle = check_gradle()
    report = call_model(project, gradle)

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(
        "# SMOOTH Media Agent Report\n\n"
        + report.strip()
        + "\n",
        encoding="utf-8",
    )

    print("REPORT:", REPORT.relative_to(ROOT))
    print("SMOOTH AGENT PASS")

if __name__ == "__main__":
    main()
