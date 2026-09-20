# 🚀 Distributed In-Memory NoSQL Database

A high-performance, distributed, and thread-safe Key-Value NoSQL database built entirely in Java from scratch (Zero external dependencies).
It features a custom TCP protocol, Write-Ahead Logging (WAL) with snapshot compaction, Master-Replica replication, and advanced secondary indexing via Trie and AVL Trees.

## 📈 Performance Benchmarks
Tested on a standard local machine using a 100-thread concurrent stress test generating 1,000,000 operations.

| Operation | Concurrency | Throughput (ops/sec) | Time (ms) |
| :--- | :--- | :--- | :--- |
| **READ (GET)** | 100 Threads | **~3,472,222 ops/sec** | 288 ms |
| **WRITE (SET)** | 100 Threads | **~575,043 ops/sec** | 1739 ms |

*Note: The massive read throughput is achieved through optimized `ReentrantReadWriteLock` mechanisms, allowing completely parallel non-blocking reads.*

## 🧠 Core Features

* **Thread-Safe Core**: Designed for highly concurrent workloads without global bottlenecks.
* **LRU Eviction & TTL**: Automatic memory management based on capacity and Time-To-Live expiration.
* **Write-Ahead Logging (WAL) & Snapshotting**: Guarantees zero data loss on server crash. Automatically compacts the log into a state snapshot when the file exceeds 10,000 operations to optimize recovery time (RDB-style).
* **Distributed Replication**: Master/Replica architecture for read scalability and data redundancy.
* **Advanced Secondary Indexing**:
    * **Prefix Tree (Trie)**: Enables $O(K)$ auto-completion and prefix searches.
    * **AVL Tree**: Enables $O(\log N)$ self-balancing lexicographical range queries.
* **Custom TCP Protocol**: Supports complex string payloads (e.g., JSON objects) with space-aware parsing.

## 🏗️ Architectural Decisions & Trade-offs

* **Why no Automatic Failover?**
  Implementing a naive automatic Master promotion during a network partition leads to the "Split-Brain" problem (resulting in two active Masters and corrupted data). True failover requires a distributed consensus algorithm (like Raft or Paxos). I chose to prioritize data consistency over availability in this scope, keeping failover manual.
* **Why ReentrantReadWriteLock over ConcurrentHashMap?**
  While `ConcurrentHashMap` is fast, managing complex atomic operations across multiple secondary structures (Trie, AVL) and maintaining LRU Linked-List pointers requires strict structural locks. The Read-Write lock ensures the cache and indexes are never out of sync while maximizing read performance.

## 🛠️ Supported Commands (TCP Client)

| Command | Syntax | Description |
| :--- | :--- | :--- |
| **SET** | `SET <key> <value>` | Stores a key-value pair. (Supports JSON/spaces) |
| **SET_EX** | `SET_EX <key> <value> <sec>` | Stores a pair with a Time-To-Live (TTL). |
| **GET** | `GET <key>` | Retrieves the value of a key. |
| **DELETE**| `DELETE <key>` | Removes a key and its indexes. |
| **EXISTS**| `EXISTS <key>` | Returns 1 if the key exists, 0 otherwise. |
| **INCR** | `INCR <key>` | Atomically increments an integer value by 1. |
| **TTL** | `TTL <key>` | Returns the remaining time to live of a key. |
| **FLUSHALL**| `FLUSHALL` | Clears the entire database and indexes. |
| **PREFIX**| `PREFIX <string>` | Returns all keys starting with the string. |
| **RANGE** | `RANGE <start> <end>` | Returns keys falling in the alphabetical range. |

## 🧪 Testing & Quality Assurance (TDD)
The project is covered by a rigorous JUnit 5 test suite validating:
* **High Concurrency**: Proving atomic `INCR` integrity against 1,000 competing threads.
* **Index Integrity**: Validating complex AVL Tree rotations and Trie DFS algorithms.
* **Durability**: Injecting simulated disk corruption in the WAL to prove system resilience and automated Snapshot recovery.
* **TCP Integration**: End-to-end socket testing of the custom command parser.

## 🚀 Getting Started

### 1. Compile the Project
Ensure you have JDK 11+ installed.
```bash
javac -d bin src/store/*.java src/network/*.java