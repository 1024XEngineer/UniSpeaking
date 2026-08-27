#!/usr/bin/env python3
"""Check coverage using Codecov's covered-line model."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


def classify(hit: bool, branches: list[bool] | None = None) -> str:
    if not hit:
        return "miss"
    if branches and not all(branches):
        return "partial"
    return "hit"


def read_lcov(path: Path) -> tuple[int, int, int]:
    hits = partials = misses = 0
    line_hits: dict[int, int] = {}
    line_branches: defaultdict[int, list[bool]] = defaultdict(list)
    current_file = False

    def finish_file() -> tuple[int, int, int]:
        file_hits = file_partials = file_misses = 0
        for line_number, execution_count in line_hits.items():
            category = classify(
                execution_count > 0,
                line_branches.get(line_number),
            )
            if category == "hit":
                file_hits += 1
            elif category == "partial":
                file_partials += 1
            else:
                file_misses += 1
        return file_hits, file_partials, file_misses

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if raw_line.startswith("SF:"):
            current_file = True
            line_hits = {}
            line_branches = defaultdict(list)
        elif raw_line.startswith("DA:") and current_file:
            fields = raw_line[3:].split(",")
            if len(fields) < 2:
                raise ValueError(f"Invalid LCOV DA record: {raw_line}")
            line_hits[int(fields[0])] = int(fields[1])
        elif raw_line.startswith("BRDA:") and current_file:
            fields = raw_line[5:].split(",")
            if len(fields) != 4:
                raise ValueError(f"Invalid LCOV BRDA record: {raw_line}")
            line_number = int(fields[0])
            line_branches[line_number].append(fields[3] not in {"0", "-"})
        elif raw_line == "end_of_record" and current_file:
            file_hits, file_partials, file_misses = finish_file()
            hits += file_hits
            partials += file_partials
            misses += file_misses
            current_file = False

    return hits, partials, misses


def read_jacoco(path: Path) -> tuple[int, int, int]:
    hits = partials = misses = 0
    root = ET.parse(path).getroot()
    for source_file in root.findall("./package/sourcefile"):
        for line in source_file.findall("./line"):
            instruction_covered = int(line.attrib.get("ci", "0"))
            missed_branches = int(line.attrib.get("mb", "0"))
            category = classify(instruction_covered > 0, [False] if missed_branches else [])
            if category == "hit":
                hits += 1
            elif category == "partial":
                partials += 1
            else:
                misses += 1
    return hits, partials, misses


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--format", choices=("lcov", "jacoco"), required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--minimum", type=float, required=True)
    args = parser.parse_args()

    if not args.input.is_file():
        print(f"coverage report does not exist: {args.input}", file=sys.stderr)
        return 2
    try:
        result = read_lcov(args.input) if args.format == "lcov" else read_jacoco(args.input)
    except (OSError, ValueError, ET.ParseError) as error:
        print(f"could not parse coverage report: {error}", file=sys.stderr)
        return 2

    hits, partials, misses = result
    total = hits + partials + misses
    # Codecov treats both hit and partial lines as covered lines.
    coverage = 100 * (hits + partials) / total if total else 0
    print(json.dumps({
        "hits": hits,
        "partials": partials,
        "misses": misses,
        "total": total,
        "coverage": round(coverage, 2),
    }, ensure_ascii=False, sort_keys=True))
    if not total:
        print("coverage report contains no executable lines", file=sys.stderr)
        return 2
    if coverage + 1e-9 < args.minimum:
        print(f"coverage {coverage:.2f}% is below required {args.minimum:.2f}%", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
