# 03: Connection pool size

## Question
With 64 concurrent clients, should the HikariCP pool have 64 connections?

## Setup
Uniform traffic, pessimistic locking, 64 clients. Only `DB_POOL_SIZE` changes. PostgreSQL is
limited to **2 CPUs**. Runs `e3-pool-*`.

## Results

| Pool size | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|
| 4 | 1,257 | 49.0 | 63.0 | 88.8 |
| **8** | **1,506** | 33.1 | 70.9 | **80.6** |
| 16 | 1,444 | 27.6 | 78.1 | 86.1 |
| 32 | 1,358 | 26.2 | 87.8 | 96.5 |
| 64 | 1,040 | 73.8 | 117.1 | 199.6 |

## What happened
- **4 connections** are not enough to keep 2 CPUs busy while other queries wait on I/O, locks
  or the network.
- **8 to 16** is the sweet spot, about 4 to 8 connections per database core.
- **Beyond that, throughput drops**: 64 connections serve 31% fewer requests than 8, and p99
  latency is 2.5x worse. More connections do not create more CPU. They add context switching,
  contention on locks, buffers and WAL inside PostgreSQL, and memory per backend. The queue
  moves from the pool, where waiting is cheap, into the database, where it is not.
- The median keeps improving up to 32 connections while the tail gets worse. Looking only at
  averages would have picked the wrong size.

## Decision
Keep the pool small (Hikari's default of 10 is close to optimal here) and size it from the
database's cores, not from the number of clients or threads. This is also why
[04-virtual-threads](04-virtual-threads.md) shows no gain.
