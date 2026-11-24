# 04: Virtual threads vs platform threads

## Question
Spring Boot can serve each request on a virtual thread (`spring.threads.virtual.enabled`). Does
that increase throughput for this service?

## Setup
400 concurrent clients (more than Tomcat's 200 platform threads), uniform traffic, pool of 10
connections. Runs `e4-virtual-*`.

## Results

| Threads | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|
| Virtual | 1,249 | 297.1 | 496.6 | 751.6 |
| Platform (Tomcat max 200) | 1,268 | 293.4 | 494.5 | 972.2 |

## What happened
No meaningful difference in throughput: both are within noise. With 400 clients, every request
spends most of its time **waiting for one of the 10 database connections**. Virtual threads make
waiting cheap, but they do not create database capacity. Little's law gives the same picture:
about 1,250 req/s at about 300 ms means about 375 requests in flight, nearly all of them queued.
The only visible difference, a lower p99 with virtual threads, is consistent with less queueing
in Tomcat's accept backlog, but it comes from a single run and should not be over-read.

## Takeaway
Virtual threads help when requests block on many independent resources (HTTP calls, slow I/O).
When the shared bottleneck is the database, the gains come from the pool size, query cost and
lock time ([01](01-locking-strategies.md), [03](03-connection-pool-size.md)). Virtual threads
stay enabled because they cost nothing here, and they protect the service when downstream calls
are added.
