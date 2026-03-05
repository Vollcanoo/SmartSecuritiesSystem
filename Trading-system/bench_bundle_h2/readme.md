# 鍚炲悙閲忔祴璇勮剼鏈紙bench_bundle_h2锛?
鏈洰褰曞寘鍚袱涓剼鏈細

- `bench_runner.py`锛氭墽琛屽悶鍚愰噺鍘嬫祴锛屾敮鎸?`core`锛圚TTP锛夊拰 `gateway`锛圱CP锛変袱绉嶆ā寮忋€?- `visualize_results.py`锛氬皢 JSON 缁撴灉鐢熸垚 SVG + HTML 鎶ュ憡銆?
## 鎬ц兘娴嬭瘎鏋舵瀯

### 1) core 妯″紡閾捐矾

`鍘嬫祴鑴氭湰 -> Core(POST /api/order) -> Core鍐呴儴涓氬姟澶勭悊 -> 杩斿洖鍝嶅簲`

璇存槑锛?- 閫傚悎娴?Core HTTP 鎺ュ彛鐨勫悶鍚愩€佸欢杩熴€佹垚鍔熺巼銆?- 涓嶇洿鎺ョ粡杩?Gateway TCP 灞傘€?
### 2) gateway 妯″紡閾捐矾

`鍘嬫祴鑴氭湰(TCP) -> Gateway:9000 -> Core(HTTP) -> Gateway -> 鍘嬫祴鑴氭湰`

璇存槑锛?- 閫傚悎娴嬧€滄帴鍏ュ眰+Core鈥濇暣浣撻摼璺€?- 鍙互瑙傚療 Gateway 鎺ュ叆寮€閿€銆侀摼璺閲忎笂闄愩€?
### 3) 鍘嬫祴鎵ц娴佺▼

`warmup` 棰勭儹 -> 鎸?`plan` 閫愮粍鎵ц -> 杈撳嚭 JSON -> 鐢熸垚鍙鍖栨姤鍛娿€?
## 鍓嶇疆鏉′欢

1. 鏈嶅姟宸插惎鍔細
- admin锛歚http://localhost:8080`
- core锛歚http://localhost:8081`
- gateway锛歚localhost:9000`锛圱CP锛?
2. 鏈満鍙繍琛?Python锛圵indows 涓嬮€氬父鐢?`py`锛夈€?
## 鍛戒护琛屽啓娉?
### 鍩烘湰璇硶

```powershell
py .\bench_bundle_h2\bench_runner.py `
  --target <core|gateway> `
  --plan <total>x<conc>,<total>x<conc>,... `
  --warmup <total>x<conc> `
  --timeout-ms <姣> `
  --market <甯傚満> `
  --security-id <璇佸埜浠ｇ爜> `
  --side <B|S> `
  --qty <鏁伴噺> `
  --price <浠锋牸> `
  [--shareholder-id <鑲′笢鍙? | --auto-create-user] `
  --output <缁撴灉JSON璺緞>
```

### `plan` 鍙傛暟瑙勫垯

- 鏍煎紡锛歚<鎬昏姹傛暟>x<骞跺彂鏁?`
- 澶氱粍鐢ㄨ嫳鏂囬€楀彿鍒嗛殧
- 绀轰緥锛歚3000x20,6000x40,12000x80`

### 鍏抽敭鍙傛暟璇存槑

| 鍙傛暟 | 璇存槑 | 榛樿鍊?|
|---|---|---|
| `--target` | 娴嬭瘯鐩爣锛歚core` 鎴?`gateway` | 蹇呭～ |
| `--plan` | 姝ｅ紡鍘嬫祴鍒嗙粍 | `core: 5000x20,10000x50,12000x80`锛沗gateway: 2000x20,5000x50,8000x80` |
| `--warmup` | 棰勭儹鍒嗙粍 | `200x10` |
| `--timeout-ms` | 鍗曡姹傝秴鏃讹紙姣锛?| `10000` |
| `--core-url` | Core 涓嬪崟鍦板潃 | `http://localhost:8081/api/order` |
| `--gateway-host` | Gateway 涓绘満 | `127.0.0.1` |
| `--gateway-port` | Gateway 绔彛 | `9000` |
| `--shareholder-id` | 鍥哄畾鑲′笢鍙?| 绌?|
| `--auto-create-user` | 鑷姩鍒涘缓鐢ㄦ埛骞朵娇鐢ㄥ叾鑲′笢鍙凤紙core 妯″紡甯哥敤锛?| 鍏抽棴 |
| `--output` | 杈撳嚭 JSON 鏂囦欢璺緞 | 鑷姩鎸夋椂闂存埑鍛藉悕 |

## 甯哥敤鍛戒护绀轰緥

### A. core 鍚炲悙娴嬭瘯

```powershell
py .\bench_bundle_h2\bench_runner.py `
  --target core `
  --auto-create-user `
  --plan 3000x20,6000x40,12000x80 `
  --warmup 500x10 `
  --timeout-ms 10000 `
  --side S `
  --qty 1 `
  --price 1.0 `
  --security-id 600030 `
  --market XSHG `
  --output .\bench_bundle_h2\result_core_same_params.json
```

### B. gateway 鍚炲悙娴嬭瘯

```powershell
py .\bench_bundle_h2\bench_runner.py `
  --target gateway `
  --shareholder-id SH00010001 `
  --plan 3000x20,6000x40,12000x80 `
  --warmup 500x10 `
  --timeout-ms 10000 `
  --side S `
  --qty 1 `
  --price 1.0 `
  --security-id 600030 `
  --market XSHG `
  --output .\bench_bundle_h2\result_gateway_same_params.json
```

## 杈撳嚭鎸囨爣璇存槑

姣忕粍 case 浼氱粰鍑轰互涓嬫牳蹇冩寚鏍囷細

- `ok / fail`
- `biz_reject_or_error`
- `rps`
- `effective_rps`
- `success_rate_pct / fail_rate_pct / biz_reject_rate_pct`
- `avg/p50/p90/p95/p99/p999/max/std`锛堟绉掞級
- `reject_codes`锛堥珮棰戞嫆缁?閿欒鐮佸垎甯冿級

## 鍙鍖栧懡浠よ鍐欐硶

### 鍩烘湰璇硶

```powershell
py .\bench_bundle_h2\visualize_results.py `
  --inputs <缁撴灉1.json> <缁撴灉2.json> ... `
  --out-dir <鎶ュ憡杈撳嚭鐩綍>
```

### 绀轰緥

```powershell
py .\bench_bundle_h2\visualize_results.py `
  --inputs .\bench_bundle_h2\result_core_same_params.json .\bench_bundle_h2\result_gateway_same_params.json `
  --out-dir .\bench_bundle_h2\reports\same_params_compare
```

濡傛灉涓嶄紶 `--inputs`锛岃剼鏈細鑷姩鎵弿锛?- `bench_bundle_h2/result_*.json`

## 鎶ュ憡浜х墿

榛樿浼氳緭鍑猴細

- `report.html`锛堟€昏鎶ュ憡锛?- `summary.csv`锛堟槑缁嗚〃锛?- `all_rps.svg`
- `all_effective_rps.svg`
- `all_p95.svg`
- `all_fail_rate.svg`
- `all_biz_reject_rate.svg`

寤鸿锛氬厛鍥哄畾鍚屼竴濂?`plan/warmup/payload` 鍐嶅姣斾笉鍚岀洰鏍囷紝閬垮厤鍙傛暟婕傜Щ瀵艰嚧缁撹澶辩湡銆?
