#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch


SCRIPT_PATH = Path(__file__).with_name("check-codecov-coverage.py")
SPEC = importlib.util.spec_from_file_location("check_codecov_coverage", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CheckCodecovCoverageTest(unittest.TestCase):
    def test_lcov_counts_partial_lines_as_covered(self) -> None:
        report = """\
TN:
SF:src/example.js
DA:1,1
DA:2,1
DA:3,0
BRDA:2,0,0,1
BRDA:2,0,1,0
end_of_record
"""

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lcov.info"
            path.write_text(report, encoding="utf-8")

            self.assertEqual(MODULE.read_lcov(path), (1, 1, 1))

    def test_jacoco_counts_partial_lines_as_covered(self) -> None:
        report = """\
<?xml version="1.0" encoding="UTF-8"?>
<report name="test">
  <package name="example">
    <sourcefile name="Example.java">
      <line nr="1" ci="1" mb="0"/>
      <line nr="2" ci="1" mb="1"/>
      <line nr="3" ci="0" mb="0"/>
    </sourcefile>
  </package>
</report>
"""

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "jacoco.xml"
            path.write_text(report, encoding="utf-8")

            self.assertEqual(MODULE.read_jacoco(path), (1, 1, 1))

    def test_main_excludes_partial_lines_from_covered_line_count(self) -> None:
        report = """\
TN:
SF:src/example.js
DA:1,1
DA:2,1
DA:3,0
BRDA:2,0,0,1
BRDA:2,0,1,0
end_of_record
"""

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lcov.info"
            path.write_text(report, encoding="utf-8")

            stdout = io.StringIO()
            stderr = io.StringIO()
            with patch.object(
                sys,
                "argv",
                [
                    str(SCRIPT_PATH),
                    "--format",
                    "lcov",
                    "--input",
                    str(path),
                    "--minimum",
                    "33.33",
                ],
            ), redirect_stdout(stdout), redirect_stderr(stderr):
                exit_code = MODULE.main()

            self.assertEqual(exit_code, 0)
            self.assertEqual(json.loads(stdout.getvalue())["coverage"], 33.33)
            self.assertEqual(stderr.getvalue(), "")

            with patch.object(
                sys,
                "argv",
                [
                    str(SCRIPT_PATH),
                    "--format",
                    "lcov",
                    "--input",
                    str(path),
                    "--minimum",
                    "33.34",
                ],
            ), redirect_stdout(stdout := io.StringIO()), redirect_stderr(stderr := io.StringIO()):
                exit_code = MODULE.main()

            self.assertEqual(exit_code, 1)
            self.assertIn("coverage 33.33% is below required 33.34%", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
