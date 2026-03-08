#!/usr/bin/env python3
"""
Simple throughput benchmark for this trading system.

Supports:
- core HTTP endpoint: POST /api/order
- gateway TCP endpoint: line-delimited JSON on port 9000
"""

from __future__ import annotations

import argparse
import json
import random
import socket
import statistics
import string
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple


@dataclass
class CaseResult:
    name: str
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
    p999_ms: float
    max_ms: float
    std_ms: float
    reject_codes: Dict[str, int]
    errors: Dict[str, int]


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def percentile(sorted_values: List[float], p: float) -> float:
    if not sorted_values:
        return 0.0
    if p <= 0:
        return sorted_values[0]
    if p >= 100:
        return sorted_values[-1]
    idx = (len(sorted_values) - 1) * (p / 100.0)
    lo = int(idx)
    hi = min(lo + 1, len(sorted_values) - 1)
    if lo == hi:
        return sorted_values[lo]
    w = idx - lo
    return sorted_values[lo] * (1.0 - w) + sorted_values[hi] * w


def parse_case(text: str) -> Tuple[int, int]:
    raw = text.strip().lower()
    if "x" not in raw:
        raise ValueError(f"Bad case format: {text!r}, expected <total>x<concurrency>")
    total_s, conc_s = raw.split("x", 1)
    total = int(total_s)
    conc = int(conc_s)
    if total <= 0 or conc <= 0:
        raise ValueError(f"Case must be positive: {text!r}")
    return total, conc


def parse_plan(plan: str) -> List[Tuple[int, int]]:
    return [parse_case(x) for x in plan.split(",") if x.strip()]


def case_label(total: int, concurrency: int) -> str:
    return f"{total}x{concurrency}"


def short_table(results: List[CaseResult]) -> str:
    headers = [
        "name",
        "case",
        "repeat",
        "ok/fail",
        "biz_reject",
        "rps",
        "eff_rps",
        "fail_%",
        "avg_ms",
        "p95_ms",
        "p99_ms",
    ]
    rows = []
    for r in results:
        rows.append(
            [
                r.name,
                r.case,
                str(r.repeat),
                f"{r.ok}/{r.fail}",
                str(r.biz_reject_or_error),
                f"{r.rps:.2f}",
                f"{r.effective_rps:.2f}",
                f"{r.fail_rate_pct:.2f}",
                f"{r.avg_ms:.2f}",
                f"{r.p95_ms:.2f}",
                f"{r.p99_ms:.2f}",
            ]
        )
    widths = [len(h) for h in headers]
    for row in rows:
        for i, col in enumerate(row):
            widths[i] = max(widths[i], len(col))
    sep = " | "
    out = []
    out.append(sep.join(h.ljust(widths[i]) for i, h in enumerate(headers)))
    out.append("-+-".join("-" * w for w in widths))
    for row in rows:
        out.append(sep.join(row[i].ljust(widths[i]) for i in range(len(headers))))
    return "\n".join(out)


def summary_by_case_table(results: List[CaseResult]) -> str:
    headers = [
        "case",
        "runs",
        "avg_rps",
        "avg_eff_rps",
        "avg_p95_ms",
        "avg_p99_ms",
        "avg_fail_%",
    ]
    grouped: Dict[str, List[CaseResult]] = {}
    for r in results:
        grouped.setdefault(r.case, []).append(r)

    rows: List[List[str]] = []
    for case in sorted(grouped.keys(), key=lambda x: parse_case(x)):
        values = grouped[case]
        rows.append(
            [
                case,
                str(len(values)),
                f"{statistics.mean(v.rps for v in values):.2f}",
                f"{statistics.mean(v.effective_rps for v in values):.2f}",
                f"{statistics.mean(v.p95_ms for v in values):.2f}",
                f"{statistics.mean(v.p99_ms for v in values):.2f}",
                f"{statistics.mean(v.fail_rate_pct for v in values):.2f}",
            ]
        )

    widths = [len(h) for h in headers]
    for row in rows:
        for i, col in enumerate(row):
            widths[i] = max(widths[i], len(col))
    sep = " | "
    out = []
    out.append(sep.join(h.ljust(widths[i]) for i, h in enumerate(headers)))
    out.append("-+-".join("-" * w for w in widths))
    for row in rows:
        out.append(sep.join(row[i].ljust(widths[i]) for i in range(len(headers))))
    return "\n".join(out)


def summary_by_case(results: List[CaseResult]) -> Dict[str, Dict[str, float]]:
    grouped: Dict[str, List[CaseResult]] = {}
    for r in results:
        grouped.setdefault(r.case, []).append(r)

    summary: Dict[str, Dict[str, float]] = {}
    for case, values in grouped.items():
        summary[case] = {
            "runs": len(values),
            "avg_rps": round(statistics.mean(v.rps for v in values), 2),
            "avg_effective_rps": round(statistics.mean(v.effective_rps for v in values), 2),
            "avg_p95_ms": round(statistics.mean(v.p95_ms for v in values), 2),
            "avg_p99_ms": round(statistics.mean(v.p99_ms for v in values), 2),
            "avg_fail_rate_pct": round(statistics.mean(v.fail_rate_pct for v in values), 2),
        }
    return dict(sorted(summary.items(), key=lambda kv: parse_case(kv[0])))


def maybe_create_user(admin_base_url: str) -> str:
    name = "tp_" + "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(10))
    payload = json.dumps({"username": name}).encode("utf-8")
    req = urllib.request.Request(
        admin_base_url.rstrip("/") + "/api/users",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        if resp.status != 200:
            raise RuntimeError(f"Create user failed with HTTP {resp.status}")
        body = resp.read()
    obj = json.loads(body.decode("utf-8", errors="ignore"))
    if obj.get("code") != 0 or not obj.get("data"):
        raise RuntimeError(f"Create user failed, response: {obj}")
    shareholder_id = obj["data"].get("shareholderId")
    if not shareholder_id:
        raise RuntimeError(f"Create user missing shareholderId, response: {obj}")
    return shareholder_id


def run_http_case(
    name: str,
    case: str,
    repeat: int,
    url: str,
    payload_json: str,
    total: int,
    concurrency: int,
    timeout_sec: float,
) -> CaseResult:
    lock = threading.Lock()
    ok = 0
    fail = 0
    biz = 0
    latencies: List[float] = []
    errors: Dict[str, int] = {}
    reject_codes: Dict[str, int] = {}
    payload_bytes = payload_json.encode("utf-8")

    def one_request() -> Tuple[bool, bool, float, Optional[str], Optional[str]]:
        t0 = time.perf_counter()
        req = urllib.request.Request(
            url,
            data=payload_bytes,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
                body = resp.read()
                elapsed_ms = (time.perf_counter() - t0) * 1000.0
                if resp.status != 200:
                    return False, False, elapsed_ms, f"http_{resp.status}", None
                biz_reject = False
                reject_key = None
                if body:
                    try:
                        obj = json.loads(body.decode("utf-8", errors="ignore"))
                        if isinstance(obj, dict):
                            if "rejectCode" in obj:
                                biz_reject = True
                                reject_key = f"reject_{obj.get('rejectCode')}"
                            elif "error" in obj:
                                biz_reject = True
                                reject_key = f"error_{obj.get('error')}"
                    except Exception:
                        pass
                return True, biz_reject, elapsed_ms, None, reject_key
        except urllib.error.HTTPError as e:
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            return False, False, elapsed_ms, f"http_{e.code}", None
        except urllib.error.URLError:
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            return False, False, elapsed_ms, "url_error", None
        except Exception as e:  # pragma: no cover - broad safety for benchmark tool
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            return False, False, elapsed_ms, f"exc_{type(e).__name__}", None

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(one_request) for _ in range(total)]
        for f in as_completed(futures):
            success, biz_reject, latency_ms, err, reject_key = f.result()
            with lock:
                latencies.append(latency_ms)
                if success:
                    ok += 1
                    if biz_reject:
                        biz += 1
                        if reject_key:
                            reject_codes[reject_key] = reject_codes.get(reject_key, 0) + 1
                else:
                    fail += 1
                    if err:
                        errors[err] = errors.get(err, 0) + 1
    elapsed = time.perf_counter() - start
    latencies.sort()
    effective_ok = max(0, ok - biz)
    success_rate = (ok / total * 100.0) if total > 0 else 0.0
    fail_rate = (fail / total * 100.0) if total > 0 else 0.0
    biz_rate = (biz / ok * 100.0) if ok > 0 else 0.0

    return CaseResult(
        name=name,
        case=case,
        repeat=repeat,
        total=total,
        concurrency=concurrency,
        ok=ok,
        fail=fail,
        biz_reject_or_error=biz,
        elapsed_sec=round(elapsed, 3),
        rps=round(ok / elapsed, 2) if elapsed > 0 else 0.0,
        effective_ok=effective_ok,
        effective_rps=round(effective_ok / elapsed, 2) if elapsed > 0 else 0.0,
        success_rate_pct=round(success_rate, 2),
        fail_rate_pct=round(fail_rate, 2),
        biz_reject_rate_pct=round(biz_rate, 2),
        avg_ms=round(statistics.mean(latencies), 2) if latencies else 0.0,
        p50_ms=round(percentile(latencies, 50), 2),
        p90_ms=round(percentile(latencies, 90), 2),
        p95_ms=round(percentile(latencies, 95), 2),
        p99_ms=round(percentile(latencies, 99), 2),
        p999_ms=round(percentile(latencies, 99.9), 2),
        max_ms=round(max(latencies), 2) if latencies else 0.0,
        std_ms=round(statistics.pstdev(latencies), 2) if len(latencies) > 1 else 0.0,
        reject_codes=dict(sorted(reject_codes.items(), key=lambda kv: kv[1], reverse=True)[:8]),
        errors=dict(sorted(errors.items(), key=lambda kv: kv[1], reverse=True)[:5]),
    )


def run_tcp_case(
    name: str,
    case: str,
    repeat: int,
    host: str,
    port: int,
    payload_json: str,
    total: int,
    concurrency: int,
    timeout_sec: float,
) -> CaseResult:
    lock = threading.Lock()
    ok = 0
    fail = 0
    biz = 0
    latencies: List[float] = []
    errors: Dict[str, int] = {}
    reject_codes: Dict[str, int] = {}
    try:
        base_payload = json.loads(payload_json)
        if not isinstance(base_payload, dict):
            base_payload = {}
    except Exception:
        base_payload = {}

    counter_lock = threading.Lock()
    issued = 0

    def next_request_index() -> Optional[int]:
        nonlocal issued
        with counter_lock:
            if issued >= total:
                return None
            idx = issued
            issued += 1
            return idx

    def worker(worker_id: int) -> None:
        nonlocal ok, fail, biz
        sock: Optional[socket.socket] = None
        while True:
            req_idx = next_request_index()
            if req_idx is None:
                break

            t0 = time.perf_counter()
            err_key: Optional[str] = None
            reject_key: Optional[str] = None
            success = False
            biz_reject = False
            try:
                if sock is None:
                    sock = socket.create_connection((host, port), timeout=timeout_sec)
                    sock.settimeout(timeout_sec)

                req_obj = dict(base_payload)
                # For new orders, set explicit unique clOrderId to avoid ambiguity in gateway tests.
                if "origClOrderId" not in req_obj and not req_obj.get("clOrderId"):
                    req_obj["clOrderId"] = f"GW{worker_id:03d}{req_idx:09d}"

                payload_line = (json.dumps(req_obj, separators=(",", ":")) + "\n").encode("utf-8")
                sock.sendall(payload_line)

                data = b""
                while not data.endswith(b"\n"):
                    chunk = sock.recv(4096)
                    if not chunk:
                        break
                    data += chunk
                if not data:
                    raise RuntimeError("empty_reply")

                reply = data.decode("utf-8", errors="ignore").strip()
                success = True
                try:
                    obj = json.loads(reply)
                    if isinstance(obj, dict):
                        if "rejectCode" in obj:
                            biz_reject = True
                            reject_key = f"reject_{obj.get('rejectCode')}"
                        elif "error" in obj:
                            biz_reject = True
                            reject_key = f"error_{obj.get('error')}"
                except Exception:
                    biz_reject = True
                    reject_key = "error_bad_json"
            except socket.timeout:
                err_key = "timeout"
                if sock is not None:
                    try:
                        sock.close()
                    except Exception:
                        pass
                sock = None
            except ConnectionRefusedError:
                err_key = "conn_refused"
                if sock is not None:
                    try:
                        sock.close()
                    except Exception:
                        pass
                sock = None
            except Exception as e:  # pragma: no cover - broad safety for benchmark tool
                err_key = f"exc_{type(e).__name__}"
                if sock is not None:
                    try:
                        sock.close()
                    except Exception:
                        pass
                sock = None

            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            with lock:
                latencies.append(elapsed_ms)
                if success:
                    ok += 1
                    if biz_reject:
                        biz += 1
                        if reject_key:
                            reject_codes[reject_key] = reject_codes.get(reject_key, 0) + 1
                else:
                    fail += 1
                    if err_key:
                        errors[err_key] = errors.get(err_key, 0) + 1

        if sock is not None:
            try:
                sock.close()
            except Exception:
                pass

    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker, wid) for wid in range(concurrency)]
        for f in as_completed(futures):
            f.result()
    elapsed = time.perf_counter() - start
    latencies.sort()
    effective_ok = max(0, ok - biz)
    success_rate = (ok / total * 100.0) if total > 0 else 0.0
    fail_rate = (fail / total * 100.0) if total > 0 else 0.0
    biz_rate = (biz / ok * 100.0) if ok > 0 else 0.0

    return CaseResult(
        name=name,
        case=case,
        repeat=repeat,
        total=total,
        concurrency=concurrency,
        ok=ok,
        fail=fail,
        biz_reject_or_error=biz,
        elapsed_sec=round(elapsed, 3),
        rps=round(ok / elapsed, 2) if elapsed > 0 else 0.0,
        effective_ok=effective_ok,
        effective_rps=round(effective_ok / elapsed, 2) if elapsed > 0 else 0.0,
        success_rate_pct=round(success_rate, 2),
        fail_rate_pct=round(fail_rate, 2),
        biz_reject_rate_pct=round(biz_rate, 2),
        avg_ms=round(statistics.mean(latencies), 2) if latencies else 0.0,
        p50_ms=round(percentile(latencies, 50), 2),
        p90_ms=round(percentile(latencies, 90), 2),
        p95_ms=round(percentile(latencies, 95), 2),
        p99_ms=round(percentile(latencies, 99), 2),
        p999_ms=round(percentile(latencies, 99.9), 2),
        max_ms=round(max(latencies), 2) if latencies else 0.0,
        std_ms=round(statistics.pstdev(latencies), 2) if len(latencies) > 1 else 0.0,
        reject_codes=dict(sorted(reject_codes.items(), key=lambda kv: kv[1], reverse=True)[:8]),
        errors=dict(sorted(errors.items(), key=lambda kv: kv[1], reverse=True)[:5]),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Throughput benchmark runner for this project")
    parser.add_argument("--target", choices=["core", "gateway"], required=True)
    parser.add_argument("--plan", default=None, help="Comma list of <total>x<concurrency>")
    parser.add_argument("--repeats", type=int, default=3, help="Repeat count for each plan case")
    parser.add_argument("--warmup", default="200x10", help="Warmup case <total>x<concurrency>")
    parser.add_argument("--timeout-ms", type=int, default=10000)
    parser.add_argument("--output", default=None, help="Output JSON file path")

    parser.add_argument("--core-url", default="http://localhost:8081/api/order")
    parser.add_argument("--admin-url", default="http://localhost:8080")
    parser.add_argument("--shareholder-id", default=None)
    parser.add_argument("--auto-create-user", action="store_true")

    parser.add_argument("--gateway-host", default="127.0.0.1")
    parser.add_argument("--gateway-port", type=int, default=9000)

    parser.add_argument("--market", default="XSHG")
    parser.add_argument("--security-id", default="600030")
    parser.add_argument("--side", default="S")
    parser.add_argument("--qty", type=int, default=1)
    parser.add_argument("--price", type=float, default=1.0)

    args = parser.parse_args()
    timeout_sec = max(args.timeout_ms, 1000) / 1000.0
    if args.repeats <= 0:
        raise ValueError("--repeats must be positive")

    default_plan = "5000x20,10000x50,12000x80"
    plan = parse_plan(args.plan or default_plan)
    warmup_total, warmup_conc = parse_case(args.warmup)

    shareholder_id = args.shareholder_id
    if args.target == "core" and not shareholder_id and args.auto_create_user:
        shareholder_id = maybe_create_user(args.admin_url)
        print(f"[info] auto created shareholderId={shareholder_id}")
    if not shareholder_id:
        shareholder_id = "SH00010001"

    payload = {
        "market": args.market,
        "securityId": args.security_id,
        "side": args.side,
        "qty": args.qty,
        "price": args.price,
        "shareholderId": shareholder_id,
    }
    payload_json = json.dumps(payload, separators=(",", ":"))

    print(f"[info] target={args.target} warmup={warmup_total}x{warmup_conc} plan={plan}")
    print(f"[info] payload={payload_json}")

    if args.target == "core":
        _ = run_http_case(
            name=f"warmup_{warmup_total}x{warmup_conc}",
            case=case_label(warmup_total, warmup_conc),
            repeat=0,
            url=args.core_url,
            payload_json=payload_json,
            total=warmup_total,
            concurrency=warmup_conc,
            timeout_sec=timeout_sec,
        )
    else:
        _ = run_tcp_case(
            name=f"warmup_{warmup_total}x{warmup_conc}",
            case=case_label(warmup_total, warmup_conc),
            repeat=0,
            host=args.gateway_host,
            port=args.gateway_port,
            payload_json=payload_json,
            total=warmup_total,
            concurrency=warmup_conc,
            timeout_sec=timeout_sec,
        )

    results: List[CaseResult] = []
    for repeat in range(1, args.repeats + 1):
        print(f"[info] repeat round {repeat}/{args.repeats}")
        for i, (total, conc) in enumerate(plan, start=1):
            current_case = case_label(total, conc)
            name = f"run{i}_{current_case}_r{repeat}"
            print(f"[info] running {name} ...")
            if args.target == "core":
                case = run_http_case(
                    name=name,
                    case=current_case,
                    repeat=repeat,
                    url=args.core_url,
                    payload_json=payload_json,
                    total=total,
                    concurrency=conc,
                    timeout_sec=timeout_sec,
                )
            else:
                case = run_tcp_case(
                    name=name,
                    case=current_case,
                    repeat=repeat,
                    host=args.gateway_host,
                    port=args.gateway_port,
                    payload_json=payload_json,
                    total=total,
                    concurrency=conc,
                    timeout_sec=timeout_sec,
                )
            results.append(case)

    print()
    print(short_table(results))
    print()
    print("summary_by_case")
    print(summary_by_case_table(results))

    output_path = (
        Path(args.output)
        if args.output
        else Path(__file__).resolve().parent / f"result_{args.target}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    )
    output = {
        "started_at_utc": utc_now_iso(),
        "target": args.target,
        "settings": {
            "plan": args.plan or default_plan,
            "repeats": args.repeats,
            "warmup": args.warmup,
            "timeout_ms": args.timeout_ms,
            "core_url": args.core_url,
            "admin_url": args.admin_url,
            "gateway_host": args.gateway_host,
            "gateway_port": args.gateway_port,
            "payload": payload,
        },
        "summary_by_case": summary_by_case(results),
        "results": [asdict(r) for r in results],
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print()
    print(f"[info] wrote: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
