"""Latency benchmark: GET /products/popular served from DB (cold, cache flushed each call)
versus Redis (warm). Also measures the equivalent /search path for reference.
Run with the app on :8080 and redis on :6379."""
import json
import statistics
import subprocess
import time
import urllib.request

BASE = "http://localhost:8080"
N = 200


def get(path):
    t0 = time.perf_counter()
    with urllib.request.urlopen(BASE + path, timeout=5) as r:
        r.read()
    return (time.perf_counter() - t0) * 1000.0


def flush():
    subprocess.run(["redis-cli", "DEL", "popular_products", "popular_products:ids"],
                   check=True, stdout=subprocess.DEVNULL)


def stats(samples):
    s = sorted(samples)
    return {
        "p50": round(statistics.median(s), 2),
        "p95": round(s[int(len(s) * 0.95) - 1], 2),
        "p99": round(s[int(len(s) * 0.99) - 1], 2),
        "mean": round(statistics.mean(s), 2),
    }


# warm JVM / JIT first
for _ in range(50):
    get("/products/popular")
    flush()

cold = []
for _ in range(N):
    flush()
    cold.append(get("/products/popular"))

get("/products/popular")  # warm cache
warm = [get("/products/popular") for _ in range(N)]

search = [get("/search?q=iphone") for _ in range(N)]

result = {"iterations": N, "db_cold_ms": stats(cold), "redis_warm_ms": stats(warm),
          "search_like_query_ms": stats(search)}
print(json.dumps(result, indent=2))
