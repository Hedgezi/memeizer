import argparse
import base64
import importlib.machinery
import importlib.util
import json
from pathlib import Path
import shlex
import subprocess
import unittest
from unittest.mock import patch


loader = importlib.machinery.SourceFileLoader("backend", str(Path(__file__).with_name("backend")))
spec = importlib.util.spec_from_loader(loader.name, loader)
backend = importlib.util.module_from_spec(spec)
loader.exec_module(backend)


class BackendTest(unittest.TestCase):
    def args(self, **overrides):
        values = dict(serial="emulator-5554", command="index", force=False, wait=True, timeout=30)
        values.update(overrides)
        return argparse.Namespace(**values)

    def test_unicode_and_shell_metacharacters_round_trip_as_data(self):
        query = "кот ' $(touch /tmp/never) ; \" \\ \n"
        response = {"ok": True, "data": {"query": query}}
        encoded = base64.b64encode(json.dumps(response).encode()).decode()
        with patch.object(backend.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, f"Result: Bundle[{{result={encoded}}}]", "")) as run:
            self.assertEqual(response, backend.call("device", "search", {"query": query}))
        command = run.call_args.args[0]
        self.assertEqual(["adb", "-s", "device", "shell"], command[:4])
        self.assertEqual({"query": query}, json.loads(base64.b64decode(shlex.split(command[4])[-1])))

    def test_wait_tracks_returned_id_and_reports_failure(self):
        responses = [
            {"ok": True, "data": {"work_id": "specific-job"}},
            {"ok": True, "data": {"state": "RUNNING"}},
            {"ok": True, "data": {"state": "FAILED", "error": "database error"}},
        ]
        with patch.object(backend, "call", side_effect=responses) as call, patch.object(backend.time, "sleep"):
            response, code = backend.run(self.args())
        self.assertEqual(1, code)
        self.assertFalse(response["ok"])
        self.assertEqual("database error", response["data"]["error"])
        self.assertEqual({"work_id": "specific-job"}, call.call_args.args[2])

    def test_timeout_preserves_job_id_without_cancelling(self):
        with patch.object(backend, "call", return_value={"ok": True, "data": {"work_id": "job"}}) as call, patch.object(backend.time, "monotonic", side_effect=[0, 31]):
            response, code = backend.run(self.args())
        self.assertEqual(124, code)
        self.assertEqual("job", response["work_id"])
        self.assertEqual(1, call.call_count)

    def test_non_waiting_index_returns_immediately(self):
        with patch.object(backend, "call", return_value={"ok": True, "data": {"work_id": "job"}}) as call:
            response, code = backend.run(self.args(wait=False))
        self.assertEqual(0, code)
        self.assertEqual(1, call.call_count)

    def test_adb_error_even_when_content_exits_zero(self):
        with patch.object(backend.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "Error while accessing provider", "")):
            with self.assertRaises(backend.HarnessError):
                backend.call(None, "status", {})


if __name__ == "__main__":
    unittest.main()
