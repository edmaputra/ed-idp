#!/usr/bin/env python3
"""
Automated JaCoCo Code Coverage Summary Generator for AI Agent & CI Pipelines.

Parses single-module or multi-module JaCoCo CSV reports and outputs a consolidated
Markdown table suitable for GitHub Actions Step Summaries ($GITHUB_STEP_SUMMARY)
and Pull Request comments.

Usage:
    python3 generate-jacoco-summary.py [csv_path...] [output_path] [--strip-prefix PREFIX]
    python3 generate-jacoco-summary.py --csv path/to/jacoco.csv --output target/coverage-summary.md
"""

import argparse
import csv
import glob
import os
import sys
from typing import Dict, List, Optional, Tuple


def pct(covered: int, missed: int) -> float:
    total = covered + missed
    return (covered / total * 100.0) if total > 0 else 100.0


def status_badge(percentage: float) -> str:
    if percentage >= 80.0:
        return "🟢"
    elif percentage >= 60.0:
        return "🟡"
    else:
        return "🔴"


def find_csv_files(preferred_paths: Optional[List[str]] = None) -> List[str]:
    if preferred_paths:
        existing = [p for p in preferred_paths if os.path.exists(p)]
        if existing:
            return existing

    # 1. Check if a non-empty root aggregate CSV exists
    root_aggregate_candidates = [
        "target/site/jacoco-aggregate/jacoco.csv",
        "target/site/jacoco-aggregate-report/jacoco.csv",
        "build/reports/jacoco/aggregate/jacoco.csv",
        "build/reports/jacoco/jacoco-aggregate.csv",
    ]
    for c in root_aggregate_candidates:
        if os.path.exists(c):
            try:
                with open(c, mode="r", encoding="utf-8") as f:
                    lines = [line.strip() for line in f if line.strip()]
                    if len(lines) > 1:
                        return [c]
            except Exception:
                pass

    # 2. Find module-level JaCoCo CSV files
    all_csvs = (
        glob.glob("**/target/site/jacoco*/**/jacoco.csv", recursive=True)
        + glob.glob("**/build/reports/jacoco/**/jacoco.csv", recursive=True)
    )

    module_files: Dict[str, str] = {}
    for c in all_csvs:
        norm = os.path.normpath(c)
        # Skip submodule-level jacoco-aggregate reports as they only contain reactor dependencies
        if "jacoco-aggregate" in norm:
            continue

        # Ignore empty files
        try:
            with open(c, mode="r", encoding="utf-8") as f:
                lines = [line.strip() for line in f if line.strip()]
                if len(lines) <= 1:
                    continue
        except Exception:
            continue

        parts = norm.split(os.sep)
        if "target" in parts:
            idx = parts.index("target")
            module_key = os.sep.join(parts[:idx]) if idx > 0 else "root"
        elif "build" in parts:
            idx = parts.index("build")
            module_key = os.sep.join(parts[:idx]) if idx > 0 else "root"
        else:
            module_key = os.path.dirname(c)

        # For the same module, prefer the newest report by modification time
        if module_key not in module_files:
            module_files[module_key] = c
        elif os.path.getmtime(c) > os.path.getmtime(module_files[module_key]):
            module_files[module_key] = c

    if module_files:
        return sorted(module_files.values())

    return []


def clean_group_name(raw_group: str, strip_prefix: Optional[str] = None) -> str:
    group = raw_group.strip()
    if strip_prefix and group.startswith(strip_prefix):
        group = group[len(strip_prefix):].lstrip("/").lstrip("\\")

    # Clean common trailing or leading artifacts
    if "/" in group or "\\" in group:
        sep = "/" if "/" in group else "\\"
        parts = [p for p in group.split(sep) if p]
        if len(parts) > 1 and parts[0] in {"src", "target", "build"}:
            group = sep.join(parts[1:])
        elif len(parts) > 1:
            group = parts[-1]
    return group or "root"


def generate_summary(csv_paths: List[str], strip_prefix: Optional[str] = None) -> str:
    modules: Dict[str, Dict[str, int]] = {}
    total = {
        "inst_m": 0, "inst_c": 0,
        "br_m": 0, "br_c": 0,
        "line_m": 0, "line_c": 0,
        "meth_m": 0, "meth_c": 0,
        "classes": 0,
    }
    class_missed: List[Tuple[int, int, float, str, str]] = []
    seen_classes = set()

    for csv_path in csv_paths:
        if not os.path.exists(csv_path):
            continue

        # In case GROUP column is 'default' or missing, derive fallback from path
        parts = os.path.normpath(csv_path).split(os.sep)
        fallback_group = "default"
        if "target" in parts:
            idx = parts.index("target")
            if idx > 0:
                fallback_group = parts[idx - 1]
        elif "build" in parts:
            idx = parts.index("build")
            if idx > 0:
                fallback_group = parts[idx - 1]

        with open(csv_path, mode="r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                raw_group = row.get("GROUP")
                if not raw_group or raw_group == "default":
                    raw_group = fallback_group

                group = clean_group_name(raw_group, strip_prefix)
                pkg = row.get("PACKAGE", "")
                cls_name = row.get("CLASS", "")
                full_class = f"{pkg}.{cls_name}" if pkg else cls_name
                class_key = (group, full_class)
                if class_key in seen_classes:
                    continue
                seen_classes.add(class_key)

                if group not in modules:
                    modules[group] = {
                        "inst_m": 0, "inst_c": 0,
                        "br_m": 0, "br_c": 0,
                        "line_m": 0, "line_c": 0,
                        "meth_m": 0, "meth_c": 0,
                        "classes": 0,
                    }

                inst_m = int(row.get("INSTRUCTION_MISSED", 0))
                inst_c = int(row.get("INSTRUCTION_COVERED", 0))
                br_m = int(row.get("BRANCH_MISSED", 0))
                br_c = int(row.get("BRANCH_COVERED", 0))
                line_m = int(row.get("LINE_MISSED", 0))
                line_c = int(row.get("LINE_COVERED", 0))
                meth_m = int(row.get("METHOD_MISSED", 0))
                meth_c = int(row.get("METHOD_COVERED", 0))

                modules[group]["inst_m"] += inst_m
                modules[group]["inst_c"] += inst_c
                modules[group]["br_m"] += br_m
                modules[group]["br_c"] += br_c
                modules[group]["line_m"] += line_m
                modules[group]["line_c"] += line_c
                modules[group]["meth_m"] += meth_m
                modules[group]["meth_c"] += meth_c
                modules[group]["classes"] += 1

                total["inst_m"] += inst_m
                total["inst_c"] += inst_c
                total["br_m"] += br_m
                total["br_c"] += br_c
                total["line_m"] += line_m
                total["line_c"] += line_c
                total["meth_m"] += meth_m
                total["meth_c"] += meth_c
                total["classes"] += 1

                if inst_m > 0:
                    c_pct = pct(inst_c, inst_m)
                    class_missed.append((inst_m, inst_c, c_pct, group, full_class))

    if total["classes"] == 0:
        return "## 📊 JaCoCo Code Coverage Summary\n\n> [!WARNING]\n> No JaCoCo coverage reports found.\n"

    tot_line_pct = pct(total["line_c"], total["line_m"])
    tot_br_pct = pct(total["br_c"], total["br_m"])
    tot_inst_pct = pct(total["inst_c"], total["inst_m"])
    tot_meth_pct = pct(total["meth_c"], total["meth_m"])

    lines = [
        "## 📊 Unified JaCoCo Code Coverage Summary",
        "",
        f"> **Overall Coverage**: {status_badge(tot_line_pct)} **{tot_line_pct:.1f}%** Lines ({total['line_c']}/{total['line_c'] + total['line_m']}) | "
        f"{status_badge(tot_br_pct)} **{tot_br_pct:.1f}%** Branches ({total['br_c']}/{total['br_c'] + total['br_m']}) | "
        f"{status_badge(tot_inst_pct)} **{tot_inst_pct:.1f}%** Instructions ({total['inst_c']}/{total['inst_c'] + total['inst_m']})",
        "",
        "### 📦 Module Breakdown",
        "",
        "| Module | Line Coverage | Branch Coverage | Instruction Coverage | Method Coverage | Classes | Status |",
        "| :--- | :--- | :--- | :--- | :--- | :---: | :---: |",
    ]

    for mod, data in sorted(modules.items()):
        l_pct = pct(data["line_c"], data["line_m"])
        b_pct = pct(data["br_c"], data["br_m"])
        i_pct = pct(data["inst_c"], data["inst_m"])
        m_pct = pct(data["meth_c"], data["meth_m"])
        badge = status_badge(l_pct)
        status_text = "Pass" if l_pct >= 80.0 else ("Warning" if l_pct >= 60.0 else "Fail")

        lines.append(
            f"| `{mod}` | {badge} {l_pct:.1f}% ({data['line_c']}/{data['line_c'] + data['line_m']}) | "
            f"{b_pct:.1f}% ({data['br_c']}/{data['br_c'] + data['br_m']}) | "
            f"{i_pct:.1f}% ({data['inst_c']}/{data['inst_c'] + data['inst_m']}) | "
            f"{m_pct:.1f}% ({data['meth_c']}/{data['meth_c'] + data['meth_m']}) | "
            f"{data['classes']} | {badge} {status_text} |"
        )

    lines.append(
        f"| **Total** | {status_badge(tot_line_pct)} **{tot_line_pct:.1f}%** ({total['line_c']}/{total['line_c'] + total['line_m']}) | "
        f"**{tot_br_pct:.1f}%** ({total['br_c']}/{total['br_c'] + total['br_m']}) | "
        f"**{tot_inst_pct:.1f}%** ({total['inst_c']}/{total['inst_c'] + total['inst_m']}) | "
        f"**{tot_meth_pct:.1f}%** ({total['meth_c']}/{total['meth_c'] + total['meth_m']}) | "
        f"**{total['classes']}** | {status_badge(tot_line_pct)} **{'Pass' if tot_line_pct >= 80.0 else 'Review'}** |"
    )

    if class_missed:
        class_missed.sort(key=lambda x: x[0], reverse=True)
        top_missed = class_missed[:10]
        lines.extend([
            "",
            "<details>",
            f"<summary>🔍 <b>Top {len(top_missed)} Classes with Missed Instructions (click to expand)</b></summary>",
            "",
            "| Module | Class | Coverage | Missed Instructions |",
            "| :--- | :--- | :--- | :---: |",
        ])
        for m_inst, c_inst, c_pct, mod, cls in top_missed:
            lines.append(f"| `{mod}` | `{cls}` | {c_pct:.1f}% ({c_inst}/{c_inst + m_inst}) | {m_inst} |")
        lines.append("</details>")

    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Generate Markdown Summary from JaCoCo CSV")
    parser.add_argument("csv_pos", nargs="*", default=[], help="JaCoCo CSV file paths (positional)")
    parser.add_argument("--csv", dest="csv_flag", action="append", default=[], help="JaCoCo CSV file path")
    parser.add_argument("--output", "-o", dest="out_flag", default=None, help="Output markdown file path")
    parser.add_argument("--strip-prefix", dest="strip_prefix", default=None, help="Prefix to remove from module names")

    args = parser.parse_args()

    # Determine output path and CSV candidates
    output_path = args.out_flag
    raw_pos = list(args.csv_pos)

    # If --output wasn't specified and the last positional argument looks like an output path
    if not output_path and len(raw_pos) >= 2 and (raw_pos[-1].endswith(".md") or "summary" in raw_pos[-1]):
        output_path = raw_pos.pop()

    candidate_csvs = [c for c in (args.csv_flag + raw_pos) if c]
    csv_files = find_csv_files(candidate_csvs if candidate_csvs else None)

    content = generate_summary(csv_files, args.strip_prefix)

    # Print to stdout
    print(content)

    # Append to GitHub Step Summary if available
    summary_path = os.getenv("GITHUB_STEP_SUMMARY")
    if summary_path:
        try:
            with open(summary_path, mode="a", encoding="utf-8") as f:
                f.write(content)
        except Exception as e:
            sys.stderr.write(f"Warning: Could not write to GITHUB_STEP_SUMMARY: {e}\n")

    # Write to target output file if specified
    if output_path:
        out_dir = os.path.dirname(os.path.abspath(output_path))
        os.makedirs(out_dir, exist_ok=True)
        with open(output_path, mode="w", encoding="utf-8") as f:
            f.write(content)


if __name__ == "__main__":
    main()
