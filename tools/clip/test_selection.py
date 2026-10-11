"""Tests for the quality gates; no model dependencies required."""
import ast
from pathlib import Path
import unittest

# Load only the pure selector so this test runs without numpy/ONNX installations.
source = ast.parse(Path(__file__).with_name("evaluate.py").read_text())
selector = next(node for node in source.body if isinstance(node, ast.FunctionDef) and node.name == "select")
namespace = {}
exec(compile(ast.Module(body=[selector], type_ignores=[]), "evaluate.py", "exec"), namespace)
select = namespace["select"]


def variant(model, precision, hit, size, delay=1):
    return dict(model=model, precision=precision, hit_at_10=hit, encoder_bytes=size, host_search_p95_seconds=delay)


class SelectionTest(unittest.TestCase):
    def test_int8_losing_more_than_two_points_is_rejected(self):
        candidates = [variant("tiny", "fp32", .80, 337), variant("tiny", "int8", .77, 166),
                      variant("base", "fp32", .82, 605), variant("base", "int8", .81, 236)]
        self.assertEqual("base", select(candidates)["model"])
        self.assertEqual("int8", select(candidates)["precision"])

    def test_smallest_within_five_points_wins(self):
        candidates = [variant("tiny", "fp32", .80, 337), variant("tiny", "int8", .78, 166),
                      variant("base", "fp32", .83, 605)]
        self.assertEqual("int8", select(candidates)["precision"])

    def test_small_model_losing_more_than_five_points_is_rejected(self):
        candidates = [variant("tiny", "fp32", .70, 337), variant("tiny", "int8", .70, 166),
                      variant("base", "fp32", .80, 605)]
        self.assertEqual("base", select(candidates)["model"])

    def test_equal_sizes_use_latency(self):
        candidates = [variant("tiny", "fp32", .80, 100, 2), variant("base", "fp32", .80, 100, 1)]
        self.assertEqual("base", select(candidates)["model"])


if __name__ == "__main__":
    unittest.main()
