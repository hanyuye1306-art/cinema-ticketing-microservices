"""Verify the timeout, short circuit and recovery of the local demo profile."""

import json
import os
import sys
import time
from urllib.request import Request, urlopen


def verify(base_url, movie_id, token):
    def request(path):
        start = time.monotonic()
        with urlopen(Request(base_url + path, headers={"Token": token}), timeout=10) as response:
            result = json.load(response)
        return result, round((time.monotonic() - start) * 1000)

    def status():
        result, _ = request("/sysBill/hystrix/movie")
        if result.get("code") != 200 or "state" not in result:
            raise RuntimeError("Hystrix 状态接口不可用，请确认 Bill 已重启到 hystrix-demo")
        return result

    def movie():
        result, elapsed = request("/sysBill/movie/" + movie_id)
        if result.get("fallback") is True and result.get("code") == 503:
            return result, elapsed
        if result.get("code") != 200 or result["data"]["movieId"] != int(movie_id):
            raise RuntimeError("Bill 电影查询没有返回预期电影或降级结果")
        return result, elapsed

    attempts = 0
    timeout_seen = False
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        before = status()
        result, elapsed = movie()
        attempts += bool(result.get("fallback"))
        current = status()
        if result.get("failureType") == "HystrixTimeoutException" and not timeout_seen:
            print(f"[PASS] Hystrix Timeout: {elapsed} ms ({result['failureType']})", file=sys.stderr)
            timeout_seen = True
        if (timeout_seen and current["state"] == "OPEN"
                and current.get("cumulativeShortCircuited", 0) > before.get("cumulativeShortCircuited", 0)
                and result.get("fallback") is True):
            print(f"[PASS] Hystrix OPEN: 短路 {elapsed} ms，累计短路 {current['cumulativeShortCircuited']} 次",
                  file=sys.stderr)
            break
    else:
        raise RuntimeError("25 秒内未同时观察到超时、OPEN 和实际短路")

    # Bill's demo configuration uses a 3000 ms sleep window. If the first
    # probe hits the slow Movie instance, the next window permits another.
    for _ in range(4):
        time.sleep(3.2)
        result, elapsed = movie()
        attempts += bool(result.get("fallback"))
        current = status()
        if result.get("code") == 200 and current["state"] == "CLOSED":
            print(f"[PASS] Hystrix CLOSED: 探测 {elapsed} ms，{result['servedBy']}，movieId={movie_id}",
                  file=sys.stderr)
            return attempts
    raise RuntimeError("4 个探测窗口内未观察到成功响应和 CLOSED")


if __name__ == "__main__":
    try:
        print(verify(sys.argv[1].rstrip("/"), sys.argv[2], os.environ["GOUYAN_SMOKE_TOKEN"]))
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        sys.exit(f"[FAIL] {error}")
