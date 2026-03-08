#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
压测结果可视化脚本（无第三方依赖）。

输入：压测 JSON 结果文件
输出：SVG 图表、汇总 CSV、HTML 报告
"""

from __future__ import annotations

import argparse
import csv
import glob
import html
import json
import math
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Sequence, Tuple


COLORS = [
    "#2563eb",  # 蓝
    "#dc2626",  # 红
    "#16a34a",  # 绿
    "#9333ea",  # 紫
    "#ea580c",  # 橙
    "#0891b2",  # 青
    "#7c2d12",  # 棕
]

OUTCOME_COLORS = {
    "ok": "#16a34a",
    "fail": "#dc2626",
    "biz_reject_or_error": "#f59e0b",
}


@dataclass
class RunRow:
    dataset: str
    file_name: str
    target: str
    run_name: str
    case: str
    repeat: int
    total: int
    concurrency: int
    ok: int
    fail: int
    biz_reject_or_error: int
    elapsed_sec: float
    rps: float
    effective_ok: int
    effective_rps: float
    success_rate_pct: float
    fail_rate_pct: float
    biz_reject_rate_pct: float
    avg_ms: float
    p50_ms: float
    p90_ms: float
    p95_ms: float
    p99_ms: float
    max_ms: float
    std_ms: float
    reject_codes: Dict[str, int]


@dataclass
class Dataset:
    id: str
    file_name: str
    target: str
    started_at_utc: str
    rows: List[RunRow]


def as_num(value, default=0.0):
    try:
        return float(value)
    except Exception:
        return float(default)


def as_int(value, default=0):
    try:
        return int(value)
    except Exception:
        return int(default)


def infer_repeat(run_name: str) -> int:
    m = re.search(r"_r(\d+)$", run_name)
    if not m:
        return 0
    try:
        return int(m.group(1))
    except Exception:
        return 0


def series_by_repeat(
    rows: List[RunRow],
    value_fn: Callable[[RunRow], float],
    label_prefix: str = "",
) -> List[Tuple[str, List[Tuple[float, float]]]]:
    grouped: Dict[int, List[Tuple[float, float]]] = {}
    for r in rows:
        rep = r.repeat if r.repeat > 0 else infer_repeat(r.run_name)
        grouped.setdefault(rep, []).append((float(r.total), float(value_fn(r))))

    out: List[Tuple[str, List[Tuple[float, float]]]] = []
    for rep in sorted(grouped.keys()):
        points = sorted(grouped[rep], key=lambda x: x[0])
        name = f"r{rep}" if rep > 0 else "r?"
        if label_prefix:
            name = f"{label_prefix}_{name}"
        out.append((name, points))
    return out


def load_dataset(path: Path) -> Dataset:
    data = json.loads(path.read_text(encoding="utf-8"))
    target = str(data.get("target", "unknown"))
    started = str(data.get("started_at_utc", ""))
    rows: List[RunRow] = []
    dataset_id = path.stem
    for item in data.get("results", []):
        total = as_int(item.get("total", 0))
        ok = as_int(item.get("ok", 0))
        fail = as_int(item.get("fail", 0))
        biz = as_int(item.get("biz_reject_or_error", 0))
        elapsed = as_num(item.get("elapsed_sec", 0))
        effective_ok = as_int(item.get("effective_ok", max(0, ok - biz)))
        success_rate = as_num(item.get("success_rate_pct", (ok / total * 100.0) if total > 0 else 0.0))
        fail_rate = as_num(item.get("fail_rate_pct", (fail / total * 100.0) if total > 0 else 0.0))
        biz_rate = as_num(item.get("biz_reject_rate_pct", (biz / ok * 100.0) if ok > 0 else 0.0))
        effective_rps = as_num(item.get("effective_rps", (effective_ok / elapsed) if elapsed > 0 else 0.0))
        reject_codes_raw = item.get("reject_codes", {})
        reject_codes = reject_codes_raw if isinstance(reject_codes_raw, dict) else {}
        rows.append(
            RunRow(
                dataset=dataset_id,
                file_name=path.name,
                target=target,
                run_name=str(item.get("name", "")),
                case=str(item.get("case", "")),
                repeat=as_int(item.get("repeat", infer_repeat(str(item.get("name", ""))))),
                total=total,
                concurrency=as_int(item.get("concurrency", 0)),
                ok=ok,
                fail=fail,
                biz_reject_or_error=biz,
                elapsed_sec=elapsed,
                rps=as_num(item.get("rps", 0)),
                effective_ok=effective_ok,
                effective_rps=effective_rps,
                success_rate_pct=success_rate,
                fail_rate_pct=fail_rate,
                biz_reject_rate_pct=biz_rate,
                avg_ms=as_num(item.get("avg_ms", 0)),
                p50_ms=as_num(item.get("p50_ms", 0)),
                p90_ms=as_num(item.get("p90_ms", item.get("p95_ms", 0))),
                p95_ms=as_num(item.get("p95_ms", 0)),
                p99_ms=as_num(item.get("p99_ms", 0)),
                max_ms=as_num(item.get("max_ms", item.get("p99_ms", 0))),
                std_ms=as_num(item.get("std_ms", 0)),
                reject_codes=reject_codes,
            )
        )
    rows.sort(key=lambda r: (r.repeat, r.total, r.run_name))
    return Dataset(
        id=dataset_id,
        file_name=path.name,
        target=target,
        started_at_utc=started,
        rows=rows,
    )


def ensure_nonempty(items: Sequence):
    if not items:
        raise ValueError("No data rows to visualize.")


def nice_max(value: float) -> float:
    if value <= 0:
        return 1.0
    exp = 10 ** math.floor(math.log10(value))
    norm = value / exp
    if norm <= 1:
        n = 1
    elif norm <= 2:
        n = 2
    elif norm <= 5:
        n = 5
    else:
        n = 10
    return n * exp


def svg_line_chart(
    title: str,
    x_label: str,
    y_label: str,
    series: List[Tuple[str, List[Tuple[float, float]]]],
    width: int = 920,
    height: int = 420,
) -> str:
    left = 70
    right = 20
    top = 45
    bottom = 55
    pw = width - left - right
    ph = height - top - bottom

    pts = [p for _, arr in series for p in arr]
    ensure_nonempty(pts)
    xs = [x for x, _ in pts]
    ys = [y for _, y in pts]
    x_min, x_max = min(xs), max(xs)
    y_min, y_max = 0.0, max(ys)
    if x_min == x_max:
        x_min -= 1
        x_max += 1
    y_max = nice_max(y_max * 1.1 if y_max > 0 else 1.0)

    def px(x: float) -> float:
        return left + (x - x_min) * pw / (x_max - x_min)

    def py(y: float) -> float:
        return top + ph - (y - y_min) * ph / (y_max - y_min)

    parts: List[str] = []
    parts.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">')
    parts.append('<rect width="100%" height="100%" fill="#ffffff"/>')
    parts.append(f'<text x="{left}" y="24" font-size="18" font-family="Arial" fill="#111827">{html.escape(title)}</text>')

    # 网格与 Y 轴刻度
    for i in range(6):
        yv = y_min + (y_max - y_min) * i / 5
        ypix = py(yv)
        parts.append(f'<line x1="{left}" y1="{ypix:.2f}" x2="{left+pw}" y2="{ypix:.2f}" stroke="#e5e7eb"/>')
        parts.append(
            f'<text x="{left-8}" y="{ypix+4:.2f}" text-anchor="end" font-size="11" font-family="Arial" fill="#374151">{yv:.0f}</text>'
        )

    # X 轴刻度
    x_ticks = sorted(set(xs))
    for xv in x_ticks:
        xx = px(xv)
        parts.append(f'<line x1="{xx:.2f}" y1="{top}" x2="{xx:.2f}" y2="{top+ph}" stroke="#f3f4f6"/>')
        parts.append(
            f'<text x="{xx:.2f}" y="{top+ph+18}" text-anchor="middle" font-size="11" font-family="Arial" fill="#374151">{xv:.0f}</text>'
        )

    # 坐标轴
    parts.append(f'<line x1="{left}" y1="{top+ph}" x2="{left+pw}" y2="{top+ph}" stroke="#111827"/>')
    parts.append(f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top+ph}" stroke="#111827"/>')
    parts.append(
        f'<text x="{left+pw/2:.2f}" y="{height-14}" text-anchor="middle" font-size="12" font-family="Arial" fill="#111827">{html.escape(x_label)}</text>'
    )
    parts.append(
        f'<text x="16" y="{top+ph/2:.2f}" transform="rotate(-90 16 {top+ph/2:.2f})" text-anchor="middle" font-size="12" font-family="Arial" fill="#111827">{html.escape(y_label)}</text>'
    )

    # 折线与点
    for i, (name, arr) in enumerate(series):
        if not arr:
            continue
        color = COLORS[i % len(COLORS)]
        d = " ".join(f"{px(x):.2f},{py(y):.2f}" for x, y in arr)
        parts.append(f'<polyline fill="none" stroke="{color}" stroke-width="2.2" points="{d}"/>')
        for x, y in arr:
            parts.append(f'<circle cx="{px(x):.2f}" cy="{py(y):.2f}" r="3.2" fill="{color}"/>')

    # 图例
    lx = left + pw - 210
    ly = top + 8
    parts.append(f'<rect x="{lx}" y="{ly-14}" width="205" height="{24+len(series)*18}" fill="#ffffff" stroke="#e5e7eb"/>')
    parts.append(f'<text x="{lx+8}" y="{ly}" font-size="12" font-family="Arial" fill="#111827">Legend</text>')
    for i, (name, _) in enumerate(series):
        color = COLORS[i % len(COLORS)]
        y = ly + 16 + i * 18
        parts.append(f'<line x1="{lx+10}" y1="{y-4}" x2="{lx+26}" y2="{y-4}" stroke="{color}" stroke-width="3"/>')
        parts.append(f'<text x="{lx+32}" y="{y}" font-size="11" font-family="Arial" fill="#111827">{html.escape(name)}</text>')

    parts.append("</svg>")
    return "\n".join(parts)


def svg_stacked_outcome_chart(
    title: str,
    categories: List[str],
    values: List[Tuple[int, int, int]],  # 成功、失败、业务拒绝
    width: int = 920,
    height: int = 420,
) -> str:
    left = 70
    right = 20
    top = 45
    bottom = 85
    pw = width - left - right
    ph = height - top - bottom

    ensure_nonempty(categories)
    totals = [max(1, sum(v)) for v in values]
    y_max = nice_max(max(totals) * 1.1)

    def py(y: float) -> float:
        return top + ph - (y * ph / y_max)

    n = len(categories)
    slot = pw / n
    bar_w = min(56, slot * 0.6)

    parts: List[str] = []
    parts.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">')
    parts.append('<rect width="100%" height="100%" fill="#ffffff"/>')
    parts.append(f'<text x="{left}" y="24" font-size="18" font-family="Arial" fill="#111827">{html.escape(title)}</text>')

    for i in range(6):
        yv = y_max * i / 5
        ypix = py(yv)
        parts.append(f'<line x1="{left}" y1="{ypix:.2f}" x2="{left+pw}" y2="{ypix:.2f}" stroke="#e5e7eb"/>')
        parts.append(
            f'<text x="{left-8}" y="{ypix+4:.2f}" text-anchor="end" font-size="11" font-family="Arial" fill="#374151">{yv:.0f}</text>'
        )

    parts.append(f'<line x1="{left}" y1="{top+ph}" x2="{left+pw}" y2="{top+ph}" stroke="#111827"/>')
    parts.append(f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top+ph}" stroke="#111827"/>')

    for i, (cat, (ok, fail, biz)) in enumerate(zip(categories, values)):
        x_center = left + slot * (i + 0.5)
        x0 = x_center - bar_w / 2
        y_base = top + ph

        segs = [
            ("ok", ok),
            ("fail", fail),
            ("biz_reject_or_error", biz),
        ]
        for key, val in segs:
            if val <= 0:
                continue
            h = (val / y_max) * ph
            y = y_base - h
            parts.append(
                f'<rect x="{x0:.2f}" y="{y:.2f}" width="{bar_w:.2f}" height="{h:.2f}" fill="{OUTCOME_COLORS[key]}" opacity="0.9"/>'
            )
            y_base = y

        parts.append(
            f'<text x="{x_center:.2f}" y="{top+ph+16}" text-anchor="middle" font-size="10" font-family="Arial" fill="#111827">{html.escape(cat)}</text>'
        )

    # 图例
    lx = left + pw - 220
    ly = top + 8
    parts.append(f'<rect x="{lx}" y="{ly-14}" width="215" height="72" fill="#ffffff" stroke="#e5e7eb"/>')
    parts.append(f'<text x="{lx+8}" y="{ly}" font-size="12" font-family="Arial" fill="#111827">Outcome</text>')
    labels = [
        ("ok", "HTTP/TCP OK"),
        ("fail", "Request Fail"),
        ("biz_reject_or_error", "Biz Reject/Error"),
    ]
    for i, (key, text) in enumerate(labels):
        y = ly + 16 + i * 18
        parts.append(f'<rect x="{lx+10}" y="{y-10}" width="12" height="12" fill="{OUTCOME_COLORS[key]}"/>')
        parts.append(f'<text x="{lx+28}" y="{y}" font-size="11" font-family="Arial" fill="#111827">{text}</text>')

    parts.append("</svg>")
    return "\n".join(parts)


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_csv(path: Path, rows: Iterable[RunRow]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "dataset",
        "file_name",
        "target",
        "run_name",
        "case",
        "repeat",
        "total",
        "concurrency",
        "ok",
        "fail",
        "biz_reject_or_error",
        "elapsed_sec",
        "rps",
        "effective_ok",
        "effective_rps",
        "success_rate_pct",
        "fail_rate_pct",
        "biz_reject_rate_pct",
        "avg_ms",
        "p50_ms",
        "p90_ms",
        "p95_ms",
        "p99_ms",
        "max_ms",
        "std_ms",
        "reject_codes",
    ]
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        for r in rows:
            row = {
                "dataset": r.dataset,
                "file_name": r.file_name,
                "target": r.target,
                "run_name": r.run_name,
                "case": r.case,
                "repeat": r.repeat,
                "total": r.total,
                "concurrency": r.concurrency,
                "ok": r.ok,
                "fail": r.fail,
                "biz_reject_or_error": r.biz_reject_or_error,
                "elapsed_sec": r.elapsed_sec,
                "rps": r.rps,
                "effective_ok": r.effective_ok,
                "effective_rps": r.effective_rps,
                "success_rate_pct": r.success_rate_pct,
                "fail_rate_pct": r.fail_rate_pct,
                "biz_reject_rate_pct": r.biz_reject_rate_pct,
                "avg_ms": r.avg_ms,
                "p50_ms": r.p50_ms,
                "p90_ms": r.p90_ms,
                "p95_ms": r.p95_ms,
                "p99_ms": r.p99_ms,
                "max_ms": r.max_ms,
                "std_ms": r.std_ms,
                "reject_codes": json.dumps(r.reject_codes, ensure_ascii=False),
            }
            w.writerow(row)


def build_report_html(
    datasets: List[Dataset],
    global_rps_svg: str,
    global_effective_rps_svg: str,
    global_p95_svg: str,
    global_fail_rate_svg: str,
    global_biz_reject_rate_svg: str,
) -> str:
    parts: List[str] = []
    parts.append("<!doctype html><html><head><meta charset='utf-8'/>")
    parts.append("<title>Throughput Report</title>")
    parts.append(
        """
<style>
body { font-family: Arial, sans-serif; margin: 20px; color: #111827; background: #f9fafb; }
h1, h2, h3 { margin: 8px 0; }
.meta { color: #4b5563; font-size: 13px; }
.card { background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 14px; margin: 16px 0; }
table { border-collapse: collapse; width: 100%; font-size: 13px; background: white; }
th, td { border: 1px solid #e5e7eb; padding: 6px 8px; text-align: right; }
th { background: #f3f4f6; text-align: center; }
td.left { text-align: left; }
.svg-wrap { overflow-x: auto; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
</style>
"""
    )
    parts.append("</head><body>")
    parts.append("<h1>Throughput Evaluation Report</h1>")
    parts.append(f"<div class='meta'>Generated at: {html.escape(datetime.now().isoformat())}</div>")
    parts.append(f"<div class='meta'>Datasets: {len(datasets)}</div>")

    parts.append("<div class='card'><h2>Global Comparison</h2>")
    parts.append("<h3>RPS vs Concurrency</h3>")
    parts.append(f"<div class='svg-wrap'>{global_rps_svg}</div>")
    parts.append("<h3>Effective RPS vs Concurrency</h3>")
    parts.append(f"<div class='svg-wrap'>{global_effective_rps_svg}</div>")
    parts.append("<h3>P95 Latency (ms) vs Concurrency</h3>")
    parts.append(f"<div class='svg-wrap'>{global_p95_svg}</div>")
    parts.append("<h3>Fail Rate (%) vs Concurrency</h3>")
    parts.append(f"<div class='svg-wrap'>{global_fail_rate_svg}</div>")
    parts.append("<h3>Biz Reject Rate (%) vs Concurrency</h3>")
    parts.append(f"<div class='svg-wrap'>{global_biz_reject_rate_svg}</div>")
    parts.append("</div>")

    for ds in datasets:
        rows = ds.rows
        parts.append("<div class='card'>")
        parts.append(f"<h2>Dataset: {html.escape(ds.id)}</h2>")
        parts.append(f"<div class='meta'>File: {html.escape(ds.file_name)} | target: {html.escape(ds.target)} | started_at_utc: {html.escape(ds.started_at_utc)}</div>")

        parts.append("<table><thead><tr>")
        headers = [
            "run_name",
            "case",
            "repeat",
            "concurrency",
            "total",
            "ok",
            "fail",
            "biz_reject_or_error",
            "rps",
            "effective_rps",
            "success_rate_pct",
            "fail_rate_pct",
            "biz_reject_rate_pct",
            "avg_ms",
            "p50_ms",
            "p90_ms",
            "p95_ms",
            "p99_ms",
            "max_ms",
            "std_ms",
            "reject_codes(top)",
        ]
        for h in headers:
            parts.append(f"<th>{html.escape(h)}</th>")
        parts.append("</tr></thead><tbody>")
        for r in rows:
            parts.append("<tr>")
            parts.append(f"<td class='left'>{html.escape(r.run_name)}</td>")
            parts.append(f"<td class='left'>{html.escape(r.case)}</td>")
            parts.append(f"<td>{r.repeat}</td>")
            parts.append(f"<td>{r.concurrency}</td>")
            parts.append(f"<td>{r.total}</td>")
            parts.append(f"<td>{r.ok}</td>")
            parts.append(f"<td>{r.fail}</td>")
            parts.append(f"<td>{r.biz_reject_or_error}</td>")
            parts.append(f"<td>{r.rps:.2f}</td>")
            parts.append(f"<td>{r.effective_rps:.2f}</td>")
            parts.append(f"<td>{r.success_rate_pct:.2f}</td>")
            parts.append(f"<td>{r.fail_rate_pct:.2f}</td>")
            parts.append(f"<td>{r.biz_reject_rate_pct:.2f}</td>")
            parts.append(f"<td>{r.avg_ms:.2f}</td>")
            parts.append(f"<td>{r.p50_ms:.2f}</td>")
            parts.append(f"<td>{r.p90_ms:.2f}</td>")
            parts.append(f"<td>{r.p95_ms:.2f}</td>")
            parts.append(f"<td>{r.p99_ms:.2f}</td>")
            parts.append(f"<td>{r.max_ms:.2f}</td>")
            parts.append(f"<td>{r.std_ms:.2f}</td>")
            reject_top = "; ".join(f"{k}:{v}" for k, v in list(r.reject_codes.items())[:3])
            parts.append(f"<td class='left'>{html.escape(reject_top)}</td>")
            parts.append("</tr>")
        parts.append("</tbody></table>")

        # 单个数据集图表
        rps_svg = svg_line_chart(
            title=f"{ds.id} - RPS by Repeat (r1/r2/r3)",
            x_label="Total Requests",
            y_label="RPS",
            series=series_by_repeat(rows, lambda r: r.rps),
        )
        lat_svg = svg_line_chart(
            title=f"{ds.id} - P95 Latency by Repeat (r1/r2/r3)",
            x_label="Total Requests",
            y_label="P95 Latency (ms)",
            series=series_by_repeat(rows, lambda r: r.p95_ms),
        )
        quality_svg = svg_line_chart(
            title=f"{ds.id} - Effective RPS by Repeat (r1/r2/r3)",
            x_label="Total Requests",
            y_label="Effective RPS",
            series=series_by_repeat(rows, lambda r: r.effective_rps),
        )
        out_svg = svg_stacked_outcome_chart(
            title=f"{ds.id} - Outcome Stacked Counts",
            categories=[r.run_name for r in rows],
            values=[(r.ok, r.fail, r.biz_reject_or_error) for r in rows],
        )
        parts.append("<h3>RPS</h3>")
        parts.append(f"<div class='svg-wrap'>{rps_svg}</div>")
        parts.append("<h3>Latency</h3>")
        parts.append(f"<div class='svg-wrap'>{lat_svg}</div>")
        parts.append("<h3>Quality Rates</h3>")
        parts.append(f"<div class='svg-wrap'>{quality_svg}</div>")
        parts.append("<h3>Outcome</h3>")
        parts.append(f"<div class='svg-wrap'>{out_svg}</div>")
        parts.append("</div>")

    parts.append("</body></html>")
    return "\n".join(parts)


def main() -> int:
    parser = argparse.ArgumentParser(description="Visualize throughput JSON results into SVG + HTML report")
    parser.add_argument(
        "--inputs",
        nargs="*",
        default=None,
        help="Input JSON files. If empty, --glob is used.",
    )
    parser.add_argument(
        "--glob",
        dest="glob_pattern",
        default="throughput-tests/result_*.json",
        help="Glob pattern for input files when --inputs is not provided.",
    )
    parser.add_argument(
        "--out-dir",
        default=None,
        help="Output directory for report artifacts.",
    )
    args = parser.parse_args()

    input_paths: List[Path] = []
    if args.inputs:
        input_paths = [Path(p) for p in args.inputs]
    else:
        input_paths = [Path(p) for p in sorted(glob.glob(args.glob_pattern))]
    input_paths = [p for p in input_paths if p.exists()]

    if not input_paths:
        raise SystemExit("No input JSON files found. Use --inputs or --glob.")

    datasets = [load_dataset(p) for p in input_paths]
    datasets = [d for d in datasets if d.rows]
    if not datasets:
        raise SystemExit("Found input files but none had result rows.")

    out_dir = (
        Path(args.out_dir)
        if args.out_dir
        else Path("throughput-tests") / "reports" / datetime.now().strftime("%Y%m%d_%H%M%S")
    )
    out_dir.mkdir(parents=True, exist_ok=True)

    # 全局对比图（按数据集+重复轮次）
    rps_series = []
    effective_rps_series = []
    p95_series = []
    fail_rate_series = []
    biz_reject_rate_series = []
    all_rows: List[RunRow] = []
    for ds in datasets:
        arr = sorted(ds.rows, key=lambda r: (r.repeat, r.total))
        rps_series.extend(series_by_repeat(arr, lambda r: r.rps, label_prefix=ds.target))
        effective_rps_series.extend(series_by_repeat(arr, lambda r: r.effective_rps, label_prefix=ds.target))
        p95_series.extend(series_by_repeat(arr, lambda r: r.p95_ms, label_prefix=ds.target))
        fail_rate_series.extend(series_by_repeat(arr, lambda r: r.fail_rate_pct, label_prefix=ds.target))
        biz_reject_rate_series.extend(series_by_repeat(arr, lambda r: r.biz_reject_rate_pct, label_prefix=ds.target))
        all_rows.extend(arr)

    global_rps_svg = svg_line_chart(
        title="All Datasets - RPS by Repeat",
        x_label="Total Requests",
        y_label="RPS",
        series=rps_series,
    )
    global_effective_rps_svg = svg_line_chart(
        title="All Datasets - Effective RPS by Repeat",
        x_label="Total Requests",
        y_label="Effective RPS",
        series=effective_rps_series,
    )
    global_p95_svg = svg_line_chart(
        title="All Datasets - P95 Latency by Repeat",
        x_label="Total Requests",
        y_label="P95 Latency (ms)",
        series=p95_series,
    )
    global_fail_rate_svg = svg_line_chart(
        title="All Datasets - Fail Rate by Repeat",
        x_label="Total Requests",
        y_label="Fail Rate (%)",
        series=fail_rate_series,
    )
    global_biz_reject_rate_svg = svg_line_chart(
        title="All Datasets - Biz Reject Rate by Repeat",
        x_label="Total Requests",
        y_label="Biz Reject Rate (%)",
        series=biz_reject_rate_series,
    )

    write_text(out_dir / "all_rps.svg", global_rps_svg)
    write_text(out_dir / "all_effective_rps.svg", global_effective_rps_svg)
    write_text(out_dir / "all_p95.svg", global_p95_svg)
    write_text(out_dir / "all_fail_rate.svg", global_fail_rate_svg)
    write_text(out_dir / "all_biz_reject_rate.svg", global_biz_reject_rate_svg)
    write_csv(out_dir / "summary.csv", all_rows)

    html_text = build_report_html(
        datasets,
        global_rps_svg,
        global_effective_rps_svg,
        global_p95_svg,
        global_fail_rate_svg,
        global_biz_reject_rate_svg,
    )
    write_text(out_dir / "report.html", html_text)

    print(f"[info] report dir: {out_dir}")
    print(f"[info] report html: {out_dir / 'report.html'}")
    print(f"[info] summary csv: {out_dir / 'summary.csv'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
