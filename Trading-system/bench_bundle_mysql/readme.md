# MySQL 涓氬姟閾捐矾鍘嬫祴鑴氭湰锛坢ysql-business-bench锛?
鏈洰褰曠敤浜庤瘎浼扳€滀笟鍔¤姹?+ MySQL 钀藉簱鈥濈殑缁煎悎鎬ц兘銆?
鑴氭湰锛?- `bench_runner.py`

## 鎬ц兘娴嬭瘎鏋舵瀯

璇ヨ剼鏈笉鏄彧娴?HTTP 鍚炲悙锛屽畠浼氬悓鏃舵祴鈥滆姹傚眰鈥濆拰鈥滆惤搴撳眰鈥濄€?
### 1) 涓讳笟鍔￠摼璺?
`鍘嬫祴鑴氭湰 -> Core(POST /api/order) -> Core鎺ㄩ€丄dmin(/internal/order-events) -> Admin鍐橫ySQL(t_order_history)`

### 2) 鏃佽矾閲囨牱閾捐矾

`閲囨牱绾跨▼ -> mysql CLI -> COUNT(t_order_history where shareholder_id=娴嬭瘯鐢ㄦ埛) -> 鍛ㄦ湡閲囨牱`

### 3) 涓轰粈涔堣鍙岄摼璺?
- 璇锋眰灞傛寚鏍囧憡璇変綘鈥滃叆鍙ｅ悶鍚愪笌寤惰繜鈥濄€?- MySQL 鎸囨爣鍛婅瘔浣犫€滅湡姝ｈ惤搴撻€熷害涓庣ǔ瀹氭€р€濄€?- 涓よ€呯粨鍚堣兘瀹氫綅鐡堕鏄惁鍦ㄥ簲鐢ㄥ眰銆佽皟鐢ㄩ摼璺繕鏄暟鎹簱灞傘€?
## 鍓嶇疆鏉′欢

1. 鏈嶅姟宸插惎鍔細
- admin锛歚http://localhost:8080`
- core锛歚http://localhost:8081`

2. MySQL 鍙敤涓?`mysql` 鍛戒护鍙墽琛岋細
- 搴擄細`trading_admin`
- 琛細`t_order_history`

3. Python 鍙繍琛岋紙Windows 甯哥敤 `py`锛夈€?
## 鍛戒护琛屽啓娉?
### 鍩烘湰璇硶

```powershell
py .\bench_bundle_mysql\bench_runner.py `
  --plan <total>x<conc>,<total>x<conc>,... `
  --warmup <total>x<conc> `
  --timeout-ms <姣> `
  --settle-ms <姣> `
  --sample-interval-ms <姣> `
  --core-url <Core涓嬪崟鍦板潃> `
  [--shareholder-id <鑲′笢鍙? | --auto-create-user] `
  --market <甯傚満> `
  --security-id <璇佸埜浠ｇ爜> `
  --side <B|S> `
  --qty <鏁伴噺> `
  --price <浠锋牸> `
  --mysql-host <涓绘満> `
  --mysql-port <绔彛> `
  --mysql-user <鐢ㄦ埛鍚? `
  [--mysql-password <瀵嗙爜>] `
  --mysql-db <搴撳悕> `
  --output <缁撴灉JSON璺緞>
```

### `plan` 鍙傛暟瑙勫垯

- 鍗曠粍鏍煎紡锛歚<鎬昏姹傛暟>x<骞跺彂鏁?`
- 澶氱粍鍒嗛殧锛氳嫳鏂囬€楀彿
- 绀轰緥锛歚3000x20,6000x40,12000x80`

### 鍙傛暟璇﹁В锛堥噸鐐癸級

| 鍙傛暟 | 鍚箟 | 榛樿鍊?|
|---|---|---|
| `--plan` | 姝ｅ紡鍘嬫祴鍒嗙粍 | `3000x20,6000x40,12000x80` |
| `--warmup` | 棰勭儹鍒嗙粍 | `200x10` |
| `--timeout-ms` | 璇锋眰瓒呮椂 | `10000` |
| `--settle-ms` | 姣忕粍缁撴潫鍚庨澶栫瓑寰咃紝缁?DB 钀藉簱缂撳啿 | `1500` |
| `--sample-interval-ms` | MySQL 閲囨牱鍛ㄦ湡 | `500` |
| `--core-url` | Core 涓嬪崟 URL | `http://localhost:8081/api/order` |
| `--shareholder-id` | 鎸囧畾娴嬭瘯鑲′笢鍙?| 绌?|
| `--auto-create-user` | 鑷姩鍒涘缓闅旂娴嬭瘯鐢ㄦ埛 | 鍏抽棴 |
| `--mysql-host` | MySQL 涓绘満 | `127.0.0.1` |
| `--mysql-port` | MySQL 绔彛 | `3306` |
| `--mysql-user` | MySQL 鐢ㄦ埛 | `root` |
| `--mysql-password` | MySQL 瀵嗙爜 | 璇诲彇 `DB_PASSWORD` |
| `--mysql-db` | 鏁版嵁搴撳悕 | `trading_admin` |
| `--output` | 缁撴灉 JSON 杈撳嚭璺緞 | 鑷姩鎸夋椂闂存埑鍛藉悕 |

## 甯哥敤鍛戒护绀轰緥

### A. 鏈湴榛樿鍙ｄ护鍦烘櫙

```powershell
py .\bench_bundle_mysql\bench_runner.py `
  --auto-create-user `
  --plan 3000x20,6000x40,12000x80 `
  --warmup 500x10 `
  --timeout-ms 10000 `
  --settle-ms 1500 `
  --sample-interval-ms 500 `
  --market XSHG `
  --security-id 600030 `
  --side S `
  --qty 1 `
  --price 1.0 `
  --mysql-host 127.0.0.1 `
  --mysql-port 13306 `
  --mysql-user root `
  --output .\bench_bundle_mysql\result_mysql_business_same_params.json
```

### B. 鏄惧紡浼?MySQL 瀵嗙爜

```powershell
py .\bench_bundle_mysql\bench_runner.py `
  --auto-create-user `
  --mysql-user root `
  --mysql-password your_password
```

### C. 浣跨敤鐜鍙橀噺瀵嗙爜

```powershell
$env:DB_PASSWORD="your_password"
py .\bench_bundle_mysql\bench_runner.py --auto-create-user
```

## 杈撳嚭鎸囨爣璇存槑

姣忕粍 case 浼氳緭鍑轰袱绫绘寚鏍囷細

### 1) 璇锋眰灞傛寚鏍囷紙HTTP锛?
- `ok / fail`
- `biz_reject_or_error`
- `rps / effective_rps`
- `success_rate_pct / fail_rate_pct`
- `avg/p50/p90/p95/p99/p999/max/std`锛堟绉掞級

### 2) MySQL 钀藉簱鎸囨爣

- `db_rows_before / db_rows_after`
- `db_rows_added`
- `db_elapsed_sec`
- `db_rps`锛歚db_rows_added / db_elapsed_sec`
- `db_peak_rps`锛氶噰鏍风獥鍙ｄ腑鐨勬渶澶у閫?- `db_sample_errors`锛氶噰鏍峰け璐ユ鏁?
## 缁撴灉鏂囦欢

榛樿杈撳嚭锛?- `bench_bundle_mysql/result_mysql_business_<timestamp>.json`

缁堢鎽樿浼氬睍绀猴細
- `rps`銆乣effective_rps`
- `db_rows_added`銆乣db_rps`銆乣db_peak_rps`
- `p95_ms`

## 涓?bench_bundle_h2 鐨勫尯鍒?
- `bench_bundle_h2`锛氬亸璇锋眰灞傚悶鍚愶紙鍙祴 core/gateway锛夈€?- `bench_bundle_mysql`锛氬亸涓氬姟閾捐矾鍚炲悙锛圕ore 鍒?MySQL 钀藉簱锛夈€?
寤鸿锛?- 鍏堢敤 `bench_bundle_h2` 鎵惧叆鍙ｆ€ц兘涓婇檺銆?- 鍐嶇敤 `bench_bundle_mysql`纭鏁版嵁搴撲晶鏄惁鎴愪负鐡堕銆?
