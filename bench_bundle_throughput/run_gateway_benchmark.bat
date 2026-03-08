@echo off
setlocal

set "PLAN=500x20,1000x50,1200x80"
set "REPEATS=1"

py "%~dp0bench_runner.py" --target gateway --plan %PLAN% --repeats %REPEATS% %*

