import importlib.util
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


AGENT_PATH = Path(__file__).with_name("run_agent.py")
SPEC = importlib.util.spec_from_file_location("run_agent_under_test", AGENT_PATH)
run_agent = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(run_agent)


class AskModelTest(unittest.TestCase):
    @staticmethod
    def native_response():
        return {
            "id": "generation-test",
            "object": "chat.completion",
            "choices": [{
                "index": 0,
                "finish_reason": "tool_calls",
                "message": {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": [{
                        "id": "call-test",
                        "type": "function",
                        "function": {
                            "name": "read",
                            "arguments": '{"path":"agent/TASK.md"}',
                        },
                    }],
                },
            }],
        }

    def test_native_tool_call_becomes_agent_action(self):
        response_data = self.native_response()
        requests = []

        def fake_urlopen(request, timeout):
            requests.append(request)
            return io.BytesIO(json.dumps(response_data).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen), \
                patch.object(run_agent.time, "sleep"):
            reply = run_agent.ask_model([])

        self.assertEqual(
            {"tool": "read", "path": "agent/TASK.md"},
            json.loads(reply["action"]),
        )
        self.assertEqual("call-test", reply["tool_call_id"])
        self.assertEqual(response_data["choices"][0]["message"], reply["assistant"])
        payload = json.loads(requests[0].data)
        self.assertEqual(
            {"list", "read", "write", "test", "finish"},
            {tool["function"]["name"] for tool in payload["tools"]},
        )
        self.assertFalse(payload["parallel_tool_calls"])

    def test_timeout_is_retried_before_returning_action(self):
        attempts = iter([TimeoutError("timed out"), self.native_response()])

        def fake_urlopen(request, timeout):
            result = next(attempts)
            if isinstance(result, Exception):
                raise result
            return io.BytesIO(json.dumps(result).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen), \
                patch.object(run_agent.time, "sleep"):
            reply = run_agent.ask_model([])

        self.assertEqual(
            {"tool": "read", "path": "agent/TASK.md"},
            json.loads(reply["action"]),
        )

    def test_native_tool_result_uses_tool_role_in_history(self):
        assistant = self.native_response()["choices"][0]["message"]
        reply = {
            "action": '{"tool":"read","path":"agent/TASK.md"}',
            "assistant": assistant,
            "tool_call_id": "call-test",
        }
        history = []

        run_agent.append_history(history, reply, {"content": "task text"})

        self.assertEqual(assistant, history[0])
        self.assertEqual("tool", history[1]["role"])
        self.assertEqual("call-test", history[1]["tool_call_id"])
        self.assertEqual({"content": "task text"}, json.loads(history[1]["content"]))

    def test_unusable_response_reports_safe_shape(self):
        response_data = {
            "id": "generation-test",
            "object": "chat.completion",
            "choices": [{
                "index": 0,
                "finish_reason": "tool_calls",
                "message": {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": [{}, {}],
                },
            }],
        }

        def fake_urlopen(request, timeout):
            return io.BytesIO(json.dumps(response_data).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen), \
                patch.object(run_agent.time, "sleep"):
            with self.assertRaisesRegex(
                RuntimeError,
                "finish_reason=tool_calls, content_type=NoneType, tool_calls=2",
            ):
                run_agent.ask_model([])

    def test_report_includes_agent_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "REPORT.md"
            with patch.object(run_agent, "REPORT", report):
                run_agent.save_report(
                    "FINISHED",
                    {"app/src/main/java/example/Policy.kt"},
                    [{"task": "testDebugUnitTest", "result": "PASS"}],
                    [{"tool": "finish", "target": "", "result": "finished"}],
                    "Clarified the policy boundary and added regression coverage.",
                )

            content = report.read_text(encoding="utf-8")

        self.assertIn("## Summary", content)
        self.assertIn(
            "Clarified the policy boundary and added regression coverage.",
            content,
        )


if __name__ == "__main__":
    unittest.main()
