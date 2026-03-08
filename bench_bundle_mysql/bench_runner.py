#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MySQL 端到端吞吐压测脚本。

每个压测用例包含两类指标：
1) 请求层吞吐与延迟（并发 POST /api/order）
2) 落库层吞吐（采样 trading_admin.t_order_history 行数）
"""

from __future__ import annotations

import argparse
import json
import os
import random
import statistics
import string
import subprocess
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple


@dataclass
class RequestMetrics:
    total: int
    concurrency: int
    ok: int
    fail: int
    biz_reject_or_error: int
    elapsed_sec: float
    latencies_ms: List[float]
    reject_codes: Dict[str, int]
    errors: Dict[str, int]


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
    db_rows_before: int
    db_rows_after: int
    db_rows_added: int
    db_elapsed_sec: float
    db_rps: float
    db_peak_rps: float
    db_sample_errors: int
    reject_codes: Dict[str, int]
    errors: Dict[str, int]


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_case(text: str) -> Tuple[int, int]:
    raw = text.strip().lower()
    if "x" not in raw:
        raise ValueError(f"Bad case format: {text!r}, expected <total>x<concurrency>")
    total_s, conc_s = raw.split("x", 1)
    total = int(total_s)
    conc = int(conc_s)
    if total <= 0 or conc <= 0:
        raise ValueError(f"Case values must be positive: {text!r}")
    return total, conc


def parse_plan(plan: str) -> List[Tuple[int, int]]:
    return [parse_case(x) for x in plan.split(",") if x.strip()]


def case_label(total: int, concurrency: int) -> str:
    return f"{total}x{concurrency}"


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


def short_table(results: List[CaseResult]) -> str:
    headers = [
        "name",
        "case",
        "repeat",
        "ok/fail",
        "biz",
        "rps",
        "eff_rps",
        "db_add",
        "db_rps",
        "db_peak",
        "p95_ms",
    ]
    rows: List[List[str]] = []
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
                str(r.db_rows_added),
                f"{r.db_rps:.2f}",
                f"{r.db_peak_rps:.2f}",
                f"{r.p95_ms:.2f}",
            ]
        )
    widths = [len(h) for h in headers]
    for row in rows:
        for i, value in enumerate(row):
            widths[i] = max(widths[i], len(value))
    sep = " | "
    out = [sep.join(headers[i].ljust(widths[i]) for i in range(len(headers)))]
    out.append("-+-".join("-" * w for w in widths))
    for row in rows:
        out.append(sep.join(row[i].ljust(widths[i]) for i in range(len(row))))
    return "\n".join(out)


def summary_by_case_table(results: List[CaseResult]) -> str:
    headers = [
        "case",
        "runs",
        "avg_rps",
        "avg_eff_rps",
        "avg_db_rps",
        "avg_p95_ms",
        "avg_fail_%",
    ]
    grouped: Dict[str, List[CaseResult]] = {}
    for r in results:
        grouped.setdefault(r.case, []).append(r)

    rows: List[List[str]] = []
    for case in sorted(grouped.keys(), key=parse_case):
        values = grouped[case]
        rows.append(
            [
                case,
                str(len(values)),
                f"{statistics.mean(v.rps for v in values):.2f}",
                f"{statistics.mean(v.effective_rps for v in values):.2f}",
                f"{statistics.mean(v.db_rps for v in values):.2f}",
                f"{statistics.mean(v.p95_ms for v in values):.2f}",
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
            "avg_db_rps": round(statistics.mean(v.db_rps for v in values), 2),
            "avg_p95_ms": round(statistics.mean(v.p95_ms for v in values), 2),
            "avg_p99_ms": round(statistics.mean(v.p99_ms for v in values), 2),
            "avg_fail_rate_pct": round(statistics.mean(v.fail_rate_pct for v in values), 2),
        }
    return dict(sorted(summary.items(), key=lambda kv: parse_case(kv[0])))


def sql_quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


class MysqlClient:
    def __init__(
        self,
        mysql_bin: str,
        host: str,
        port: int,
        user: str,
        password: Optional[str],
        database: str,
        timeout_sec: int,
    ) -> None:
        self.mysql_bin = mysql_bin
        self.host = host
        self.port = port
        self.user = user
        self.password = password if password else None
        self.database = database
        self.timeout_sec = max(1, timeout_sec)

    def query_lines(self, sql: str) -> List[str]:
        cmd = [
            self.mysql_bin,
            "--protocol=tcp",
            "-h",
            self.host,
            "-P",
            str(self.port),
            "-u",
            self.user,
            "-N",
            "-B",
            "--raw",
            "--connect-timeout",
            str(self.timeout_sec),
            "-D",
            self.database,
        ]
        if self.password is not None:
            cmd.append(f"-p{self.password}")
        cmd.extend(["-e", sql])
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode != 0:
            detail = proc.stderr.strip() or proc.stdout.strip() or "unknown mysql error"
            raise RuntimeError(detail)
        text = proc.stdout.strip()
        if not text:
            return []
        return text.splitlines()

    def query_int(self, sql: str, default: int = 0) -> int:
        lines = self.query_lines(sql)
        if not lines:
            return default
        raw = lines[0].strip()
        try:
            return int(raw)
        except ValueError:
            return default


class DbRowSampler:
    def __init__(self, mysql_client: MysqlClient, shareholder_id: str, interval_sec: float) -> None:
        self.mysql_client = mysql_client
        self.shareholder_id = shareholder_id
        self.interval_sec = max(0.1, interval_sec)
        self.samples: List[Tuple[float, int]] = []
        self.sample_errors = 0
        self._lock = threading.Lock()
        self._stop_event = threading.Event()
        self._thread = threading.Thread(target=self._loop, daemon=True)
        self._start_t = 0.0

    def _query_rows(self) -> int:
        sql = (
            "SELECT COUNT(*) FROM t_order_history "
            f"WHERE shareholder_id = {sql_quote(self.shareholder_id)};"
        )
        return self.mysql_client.query_int(sql, default=0)

    def sample_now(self) -> None:
        now = time.perf_counter()
        try:
            rows = self._query_rows()
            rel_t = now - self._start_t
            with self._lock:
                self.samples.append((rel_t, rows))
        except Exception:
            with self._lock:
                self.sample_errors += 1

    def _loop(self) -> None:
        while not self._stop_event.wait(self.interval_sec):
            self.sample_now()

    def start(self) -> None:
        self._start_t = time.perf_counter()
        self.sample_now()
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        self._thread.join(timeout=max(1.0, self.interval_sec * 2.0))
        self.sample_now()

    def peak_rps(self) -> float:
        with self._lock:
            data = list(self.samples)
        if len(data) < 2:
            return 0.0
        peak = 0.0
        for i in range(1, len(data)):
            dt = data[i][0] - data[i - 1][0]
            dc = data[i][1] - data[i - 1][1]
            if dt <= 0:
                continue
            rps = max(0.0, dc / dt)
            if rps > peak:
                peak = rps
        return peak


def maybe_create_user(admin_base_url: str) -> str:
    name = "mysqltp_" + "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(10))
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


def run_http_load(
    url: str,
    payload_json: str,
    total: int,
    concurrency: int,
    timeout_sec: float,
) -> RequestMetrics:
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
        except Exception as e:
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
    return RequestMetrics(
        total=total,
        concurrency=concurrency,
        ok=ok,
        fail=fail,
        biz_reject_or_error=biz,
        elapsed_sec=elapsed,
        latencies_ms=latencies,
        reject_codes=dict(sorted(reject_codes.items(), key=lambda kv: kv[1], reverse=True)[:8]),
        errors=dict(sorted(errors.items(), key=lambda kv: kv[1], reverse=True)[:8]),
    )


def run_case_with_mysql(
    name: str,
    case: str,
    repeat: int,
    core_url: str,
    payload_json: str,
    total: int,
    concurrency: int,
    timeout_sec: float,
    settle_sec: float,
    mysql_client: MysqlClient,
    shareholder_id: str,
    sample_interval_sec: float,
) -> CaseResult:
    where_sql = f"WHERE shareholder_id = {sql_quote(shareholder_id)}"
    before_rows = mysql_client.query_int(f"SELECT COUNT(*) FROM t_order_history {where_sql};")

    sampler = DbRowSampler(mysql_client, shareholder_id, sample_interval_sec)
    sampler.start()
    req = run_http_load(
        url=core_url,
        payload_json=payload_json,
        total=total,
        concurrency=concurrency,
        timeout_sec=timeout_sec,
    )
    if settle_sec > 0:
        time.sleep(settle_sec)
    sampler.stop()

    after_rows = mysql_client.query_int(f"SELECT COUNT(*) FROM t_order_history {where_sql};")
    db_added = max(0, after_rows - before_rows)
    db_elapsed = req.elapsed_sec + max(0.0, settle_sec)
    latencies = sorted(req.latencies_ms)
    effective_ok = max(0, req.ok - req.biz_reject_or_error)
    success_rate = (req.ok / total * 100.0) if total > 0 else 0.0
    fail_rate = (req.fail / total * 100.0) if total > 0 else 0.0
    biz_rate = (req.biz_reject_or_error / req.ok * 100.0) if req.ok > 0 else 0.0

    return CaseResult(
        name=name,
        case=case,
        repeat=repeat,
        total=total,
        concurrency=concurrency,
        ok=req.ok,
        fail=req.fail,
        biz_reject_or_error=req.biz_reject_or_error,
        elapsed_sec=round(req.elapsed_sec, 3),
        rps=round(req.ok / req.elapsed_sec, 2) if req.elapsed_sec > 0 else 0.0,
        effective_ok=effective_ok,
        effective_rps=round(effective_ok / req.elapsed_sec, 2) if req.elapsed_sec > 0 else 0.0,
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
        db_rows_before=before_rows,
        db_rows_after=after_rows,
        db_rows_added=db_added,
        db_elapsed_sec=round(db_elapsed, 3),
        db_rps=round(db_added / db_elapsed, 2) if db_elapsed > 0 else 0.0,
        db_peak_rps=round(sampler.peak_rps(), 2),
        db_sample_errors=sampler.sample_errors,
        reject_codes=req.reject_codes,
        errors=req.errors,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="MySQL-backed business throughput and concurrency benchmark")
    parser.add_argument("--plan", default=None, help="Comma list of <total>x<concurrency>")
    parser.add_argument("--repeats", type=int, default=3, help="Repeat count for each plan case")
    parser.add_argument("--warmup", default="200x10", help="Warmup case <total>x<concurrency>")
    parser.add_argument("--timeout-ms", type=int, default=10000)
    parser.add_argument("--settle-ms", type=int, default=1500, help="Wait after each case to let DB catch up")
    parser.add_argument("--sample-interval-ms", type=int, default=500, help="MySQL row-count sampling interval")
    parser.add_argument("--output", default=None, help="Output JSON path")

    parser.add_argument("--core-url", default="http://localhost:8081/api/order")
    parser.add_argument("--admin-url", default="http://localhost:8080")
    parser.add_argument("--shareholder-id", default=None)
    parser.add_argument("--auto-create-user", action="store_true")

    parser.add_argument("--market", default="XSHG")
    parser.add_argument("--security-id", default="600030")
    parser.add_argument("--side", default="S")
    parser.add_argument("--qty", type=int, default=1)
    parser.add_argument("--price", type=float, default=1.0)

    parser.add_argument("--mysql-bin", default="mysql")
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default=os.getenv("DB_PASSWORD", None))
    parser.add_argument("--mysql-db", default="trading_admin")
    parser.add_argument("--mysql-timeout-ms", type=int, default=5000)

    args = parser.parse_args()
    timeout_sec = max(1.0, args.timeout_ms / 1000.0)
    if args.repeats <= 0:
        raise ValueError("--repeats must be positive")
    settle_sec = max(0.0, args.settle_ms / 1000.0)
    sample_interval_sec = max(0.1, args.sample_interval_ms / 1000.0)
    warmup_total, warmup_conc = parse_case(args.warmup)
    default_plan = "5000x20,10000x50,12000x80"
    plan = parse_plan(args.plan or default_plan)

    shareholder_id = args.shareholder_id
    if not shareholder_id and args.auto_create_user:
        shareholder_id = maybe_create_user(args.admin_url)
        print(f"[info] auto created shareholderId={shareholder_id}")
    if not shareholder_id:
        shareholder_id = "SH00010001"

    mysql_client = MysqlClient(
        mysql_bin=args.mysql_bin,
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_db,
        timeout_sec=max(1, int(args.mysql_timeout_ms / 1000)),
    )

    # 启动前先校验数据库连通性和表是否存在
    mysql_client.query_lines("SELECT 1;")
    mysql_client.query_lines("SELECT COUNT(*) FROM t_order_history LIMIT 1;")

    payload = {
        "market": args.market,
        "securityId": args.security_id,
        "side": args.side,
        "qty": args.qty,
        "price": args.price,
        "shareholderId": shareholder_id,
    }
    payload_json = json.dumps(payload, separators=(",", ":"))

    print(f"[info] warmup={warmup_total}x{warmup_conc} plan={plan} repeats={args.repeats}")
    print(f"[info] core_url={args.core_url}")
    print(
        f"[info] mysql={args.mysql_host}:{args.mysql_port}/{args.mysql_db} "
        f"user={args.mysql_user} shareholderId={shareholder_id}"
    )
    print(f"[info] payload={payload_json}")

    _ = run_http_load(
        url=args.core_url,
        payload_json=payload_json,
        total=warmup_total,
        concurrency=warmup_conc,
        timeout_sec=timeout_sec,
    )

    results: List[CaseResult] = []
    for repeat in range(1, args.repeats + 1):
        print(f"[info] repeat round {repeat}/{args.repeats}")
        for idx, (total, conc) in enumerate(plan, start=1):
            current_case = case_label(total, conc)
            name = f"run{idx}_{current_case}_r{repeat}"
            print(f"[info] running {name} ...")
            case = run_case_with_mysql(
                name=name,
                case=current_case,
                repeat=repeat,
                core_url=args.core_url,
                payload_json=payload_json,
                total=total,
                concurrency=conc,
                timeout_sec=timeout_sec,
                settle_sec=settle_sec,
                mysql_client=mysql_client,
                shareholder_id=shareholder_id,
                sample_interval_sec=sample_interval_sec,
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
        else Path(__file__).resolve().parent / f"result_mysql_business_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    )
    output = {
        "started_at_utc": utc_now_iso(),
        "kind": "mysql_business_benchmark",
        "target": "mysql",
        "settings": {
            "plan": args.plan or default_plan,
            "repeats": args.repeats,
            "warmup": args.warmup,
            "timeout_ms": args.timeout_ms,
            "settle_ms": args.settle_ms,
            "sample_interval_ms": args.sample_interval_ms,
            "core_url": args.core_url,
            "admin_url": args.admin_url,
            "payload": payload,
            "mysql": {
                "host": args.mysql_host,
                "port": args.mysql_port,
                "user": args.mysql_user,
                "database": args.mysql_db,
            },
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
