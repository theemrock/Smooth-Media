import importlib.util
import io
import json
import os
import tempfile
import unittest
import urllib.error
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

    def test_json_text_fallback_becomes_agent_action(self):
        response_data = {
            "id": "generation-text",
            "object": "chat.completion",
            "choices": [{
                "index": 0,
                "finish_reason": "stop",
                "message": {
                    "role": "assistant",
                    "content": '{"tool":"finish","summary":"done"}',
                },
            }],
        }

        def fake_urlopen(request, timeout):
            return io.BytesIO(json.dumps(response_data).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen):
            reply = run_agent.ask_model([])

        self.assertEqual({"tool": "finish", "summary": "done"}, json.loads(reply["action"]))
        self.assertIsNone(reply["tool_call_id"])

    def test_multiturn_native_history_preserves_protocol_order(self):
        first = self.native_response()
        second = {
            "id": "generation-second",
            "object": "chat.completion",
            "choices": [{
                "index": 0,
                "finish_reason": "stop",
                "message": {
                    "role": "assistant",
                    "content": '{"tool":"finish","summary":"done"}',
                },
            }],
        }
        responses = iter([first, second])
        requests = []

        def fake_urlopen(request, timeout):
            requests.append(json.loads(request.data))
            return io.BytesIO(json.dumps(next(responses)).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen):
            first_reply = run_agent.ask_model([])
            history = []
            run_agent.append_history(history, first_reply, {"entries": ["app/src/Main.kt"]})
            second_reply = run_agent.ask_model(history)

        self.assertEqual({"tool": "finish", "summary": "done"}, json.loads(second_reply["action"]))
        self.assertEqual("assistant", requests[1]["messages"][-2]["role"])
        self.assertEqual("call-test", requests[1]["messages"][-2]["tool_calls"][0]["id"])
        self.assertEqual("tool", requests[1]["messages"][-1]["role"])
        self.assertEqual("call-test", requests[1]["messages"][-1]["tool_call_id"])
        self.assertEqual(
            {"entries": ["app/src/Main.kt"]},
            json.loads(requests[1]["messages"][-1]["content"]),
        )

    def test_http_400_reports_safe_provider_detail(self):
        body = json.dumps({
            "error": {
                "message": "Invalid tool result: Bearer super-secret-value malformed message sequence",
                "code": 400,
                "metadata": {"raw": "Bearer super-secret-value"},
            },
        }).encode("utf-8")
        error = urllib.error.HTTPError(
            "https://openrouter.ai/api/v1/chat/completions",
            400,
            "Bad Request",
            {},
            io.BytesIO(body),
        )

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=error):
            with self.assertRaisesRegex(RuntimeError, "HTTP 400.*category=tool-message-validation") as caught:
                run_agent.ask_model([])

        self.assertNotIn("malformed message sequence", str(caught.exception))
        self.assertNotIn("super-secret-value", str(caught.exception))
        self.assertNotIn("Authorization", str(caught.exception))

    def test_http_400_does_not_echo_unlabelled_provider_message(self):
        body = json.dumps({
            "error": {
                "message": "request rejected: user canary UNLABELLED-SECRET-1234567890",
                "code": "invalid_request_error",
            },
        }).encode("utf-8")
        error = urllib.error.HTTPError(
            "https://openrouter.ai/api/v1/chat/completions",
            400,
            "Bad Request",
            {},
            io.BytesIO(body),
        )

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=error):
            with self.assertRaises(RuntimeError) as caught:
                run_agent.ask_model([])

        self.assertNotIn("UNLABELLED-SECRET-1234567890", str(caught.exception))
        self.assertIn("code=invalid_request_error", str(caught.exception))

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

    def test_malformed_native_call_is_retried(self):
        malformed = self.native_response()
        malformed["choices"][0]["message"]["tool_calls"] = [None]
        attempts = iter([malformed, self.native_response()])

        def fake_urlopen(request, timeout):
            return io.BytesIO(json.dumps(next(attempts)).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen), \
                patch.object(run_agent.time, "sleep"):
            reply = run_agent.ask_model([])

        self.assertEqual("call-test", reply["tool_call_id"])

    def test_non_string_tool_call_id_is_retried(self):
        malformed = self.native_response()
        malformed["choices"][0]["message"]["tool_calls"][0]["id"] = 123
        attempts = iter([malformed, self.native_response()])

        def fake_urlopen(request, timeout):
            return io.BytesIO(json.dumps(next(attempts)).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen), \
                patch.object(run_agent.time, "sleep"):
            reply = run_agent.ask_model([])

        self.assertEqual("call-test", reply["tool_call_id"])

    def test_malformed_response_shape_is_retried(self):
        attempts = iter([{"error": {"message": "provider failed"}}, self.native_response()])

        def fake_urlopen(request, timeout):
            return io.BytesIO(json.dumps(next(attempts)).encode("utf-8"))

        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen), \
                patch.object(run_agent.time, "sleep"):
            reply = run_agent.ask_model([])

        self.assertEqual("call-test", reply["tool_call_id"])

    def test_model_request_respects_agent_deadline(self):
        observed_timeouts = []

        def fake_urlopen(request, timeout):
            observed_timeouts.append(timeout)
            raise TimeoutError("timed out")

        clock = iter([580.0, 580.0, 590.0])
        with patch.dict(os.environ, {"OPENROUTER_API_KEY": "dummy-test-value"}), \
                patch.object(run_agent.urllib.request, "urlopen", side_effect=fake_urlopen), \
                patch.object(run_agent.time, "monotonic", side_effect=lambda: next(clock)), \
                patch.object(run_agent.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "time limit"):
                run_agent.ask_model([], deadline=600.0)

        self.assertEqual([5], observed_timeouts)

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

    def test_gradle_runs_in_sanitized_repository_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "repository"
            (root / "app" / "src" / "test").mkdir(parents=True)
            (root / "app" / "src" / "test" / "ExampleTest.kt").write_text(
                "class ExampleTest", encoding="utf-8"
            )
            (root / ".git").mkdir()
            (root / ".git" / "config").write_text("credential", encoding="utf-8")
            (root / ".github").mkdir()
            (root / "agent").mkdir()
            (root / "gradlew").write_text("#!/bin/sh", encoding="utf-8")

            def fake_run(command, cwd, capture_output, text, timeout, env):
                workspace = Path(cwd)
                self.assertNotEqual(root, workspace)
                self.assertTrue((workspace / "app/src/test/ExampleTest.kt").is_file())
                self.assertFalse((workspace / ".git").exists())
                self.assertFalse((workspace / ".github").exists())
                self.assertFalse((workspace / "agent").exists())
                self.assertNotIn("OPENROUTER_API_KEY", env)
                self.assertNotIn("GITHUB_TOKEN", env)
                return run_agent.subprocess.CompletedProcess(command, 0, "passed", "")

            with patch.object(run_agent, "ROOT", root), \
                    patch.dict(os.environ, {
                        "OPENROUTER_API_KEY": "secret",
                        "GITHUB_TOKEN": "credential",
                        "PATH": os.environ.get("PATH", ""),
                    }, clear=True), \
                    patch.object(run_agent.subprocess, "run", side_effect=fake_run):
                result = run_agent.run_test("testDebugUnitTest", timeout=30)

        self.assertEqual(0, result["exit_code"])

    def test_source_inventory_is_recursive_bounded_and_protected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            source = root / "app/src/main/java/example/deep/Feature.kt"
            test = root / "app/src/test/java/example/FeatureTest.kt"
            hidden = root / "app/src/main/java/.private/Secret.kt"
            blocked = root / "app/src/main/build/Generated.kt"
            for path in (source, test, hidden, blocked):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("class Example", encoding="utf-8")

            with patch.object(run_agent, "ROOT", root), \
                    patch.object(run_agent, "MAX_INVENTORY_FILES", 1, create=True):
                result = run_agent.safe_list("app/src")

        self.assertEqual(
            ["app/src/main/java/example/deep/Feature.kt"],
            result["entries"],
        )
        self.assertTrue(result["truncated"])
        self.assertNotIn("Secret.kt", "\n".join(result["entries"]))
        self.assertNotIn("Generated.kt", "\n".join(result["entries"]))

    @staticmethod
    def text_reply(action):
        content = json.dumps(action)
        return {
            "action": content,
            "assistant": {"role": "assistant", "content": content},
            "tool_call_id": None,
        }

    def test_discovery_limit_preserves_delivery_turns(self):
        actions = [
            {"tool": "list", "path": "app/src"},
            {"tool": "read", "path": "app/src/main/java/example/Feature.kt"},
            {"tool": "list", "path": "app/src/test"},
            {
                "tool": "write",
                "path": "app/src/main/java/example/Feature.kt",
                "content": "package example\nclass Feature(val enabled: Boolean = true)\n",
            },
            {
                "tool": "write",
                "path": "app/src/test/java/example/FeatureTest.kt",
                "content": "package example\nclass FeatureTest\n",
            },
            {"tool": "test", "task": "testDebugUnitTest"},
            {"tool": "finish", "summary": "Implemented and tested Feature."},
        ]
        replies = iter(self.text_reply(action) for action in actions)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            task = root / "agent/TASK.md"
            report = root / "agent/REPORT.md"
            source = root / "app/src/main/java/example/Feature.kt"
            task.parent.mkdir(parents=True)
            source.parent.mkdir(parents=True)
            task.write_text("Improve Feature safely.", encoding="utf-8")
            source.write_text("package example\nclass Feature\n", encoding="utf-8")

            with patch.object(run_agent, "ROOT", root), \
                    patch.object(run_agent, "TASK", task), \
                    patch.object(run_agent, "REPORT", report), \
                    patch.object(run_agent, "MAX_DISCOVERY_STEPS", 2, create=True), \
                    patch.object(run_agent, "MAX_DELIVERY_STEPS", 5, create=True), \
                    patch.object(
                        run_agent,
                        "ask_model",
                        side_effect=lambda history, deadline, tools: next(replies),
                    ), \
                    patch.object(run_agent, "run_test", return_value={"exit_code": 0, "output": "PASS"}):
                run_agent.main()

            content = report.read_text(encoding="utf-8")

        self.assertIn("Status: **FINISHED**", content)
        self.assertIn("Discovery limit reached", content)
        self.assertIn("`testDebugUnitTest`: PASS", content)

    def test_write_does_not_discard_remaining_discovery_budget(self):
        actions = [
            {"tool": "read", "path": "app/src/main/java/example/Feature.kt"},
            {
                "tool": "write",
                "path": "app/src/main/java/example/Feature.kt",
                "content": "package example\nclass Feature(val enabled: Boolean = true)\n",
            },
            {"tool": "read", "path": "app/src/test/java/example/FeatureTest.kt"},
            {
                "tool": "write",
                "path": "app/src/test/java/example/FeatureTest.kt",
                "content": "package example\nclass FeatureTest { val expected = true }\n",
            },
            {"tool": "test", "task": "testDebugUnitTest"},
            {"tool": "finish", "summary": "Updated source and its existing test."},
        ]
        replies = iter(self.text_reply(action) for action in actions)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            task = root / "agent/TASK.md"
            report = root / "agent/REPORT.md"
            source = root / "app/src/main/java/example/Feature.kt"
            test = root / "app/src/test/java/example/FeatureTest.kt"
            task.parent.mkdir(parents=True)
            source.parent.mkdir(parents=True)
            test.parent.mkdir(parents=True)
            task.write_text("Improve Feature safely.", encoding="utf-8")
            source.write_text("package example\nclass Feature\n", encoding="utf-8")
            test.write_text("package example\nclass FeatureTest\n", encoding="utf-8")

            with patch.object(run_agent, "ROOT", root), \
                    patch.object(run_agent, "TASK", task), \
                    patch.object(run_agent, "REPORT", report), \
                    patch.object(run_agent, "MAX_DISCOVERY_STEPS", 3), \
                    patch.object(run_agent, "MAX_DELIVERY_STEPS", 4), \
                    patch.object(
                        run_agent,
                        "ask_model",
                        side_effect=lambda history, deadline, tools: next(replies),
                    ), \
                    patch.object(run_agent, "run_test", return_value={"exit_code": 0, "output": "PASS"}):
                run_agent.main()

            content = report.read_text(encoding="utf-8")

        self.assertIn("Status: **FINISHED**", content)
        self.assertNotIn("Read existing file before modifying it", content)

    def test_large_inventory_history_remains_valid_json(self):
        result = {
            "entries": [
                f"app/src/main/java/example/feature{i:03d}/VeryLongFeatureName{i:03d}.kt"
                for i in range(200)
            ],
            "truncated": False,
        }
        reply = self.text_reply({"tool": "list", "path": "app/src"})
        history = []

        run_agent.append_history(history, reply, result)

        serialized = history[1]["content"]
        delivered = json.loads(serialized)
        self.assertLessEqual(len(serialized), run_agent.MAX_HISTORY_RESULT_CHARS)
        self.assertTrue(delivered["truncated"])
        self.assertEqual(200, delivered["total_entries"])
        self.assertLess(len(delivered["entries"]), 200)
        self.assertTrue(all(path.endswith(".kt") for path in delivered["entries"]))

    def test_report_only_finish_remains_a_failure(self):
        reply = self.text_reply({"tool": "finish", "summary": "No change."})
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            task = root / "agent/TASK.md"
            report = root / "agent/REPORT.md"
            task.parent.mkdir(parents=True)
            task.write_text("Make a source improvement.", encoding="utf-8")

            with patch.object(run_agent, "ROOT", root), \
                    patch.object(run_agent, "TASK", task), \
                    patch.object(run_agent, "REPORT", report), \
                    patch.object(run_agent, "ask_model", return_value=reply):
                with self.assertRaises(SystemExit) as exit_info:
                    run_agent.main()

            content = report.read_text(encoding="utf-8")

        self.assertEqual(1, exit_info.exception.code)
        self.assertIn("Status: **NO SOURCE CHANGES**", content)
        self.assertIn("- None", content)


if __name__ == "__main__":
    unittest.main()
