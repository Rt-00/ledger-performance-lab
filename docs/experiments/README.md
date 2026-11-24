# Experiments

Every number in these reports was measured on the setup below with the scripts in
[`load-tests/`](../../load-tests). Raw k6 summaries are in `load-tests/results/`.

| | |
|---|---|
| Machine | Laptop, Intel Core i7-1255U (10 cores / 12 threads), 15 GB RAM, Linux 7.3 |
| Isolation | Docker Compose: app 2 CPUs / 1 GB, PostgreSQL 18 2 CPUs / 1 GB, RabbitMQ 1 CPU; k6 on the host |
| App | Java 21, Spring Boot 4.2, virtual threads on, HikariCP pool 10 (unless the experiment varies it) |
| Workload | `POST /transfers` of 1.00 BRL, closed model (each virtual user sends the next request when the previous one returns) |
| Method | Fresh database per run, 15 s warm-up excluded from results, 45 s measured |

**Caveats.** Experiments 01, 03 and 04 ran in one session; experiment 02's write comparison ran
in another, where the same baseline was about 9% slower (the laptop had been rebooted). Numbers
are only compared within the same session. A laptop with turbo boost and a load generator on the same machine is not a
benchmark lab. Two rounds of the same configuration differed by up to about 5%, so differences
smaller than that are noise. What matters here are the large effects and the reasons behind them.

| # | Question | Short answer |
|---|---|---|
| [01](01-locking-strategies.md) | Pessimistic or optimistic locking for balances? | Pessimistic. Under contention, optimistic locking does more work and still fails about 26% of requests. |
| [02](02-pagination.md) | OFFSET or keyset pagination for statements? | Keyset: constant cost at any depth, once a planner trap on the last page was removed by changing the primary key. |
| [03](03-connection-pool-size.md) | How big should the connection pool be? | Small. 8 connections beat 64 by 45% in throughput, with lower tail latency. |
| [04](04-virtual-threads.md) | Do virtual threads make it faster? | Not here: the database pool is the bottleneck, not the threads. |
