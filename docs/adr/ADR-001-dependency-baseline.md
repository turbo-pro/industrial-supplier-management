# ADR-001 Dependency baseline

- Status: Accepted
- Date: 2026-09-14

## Decision

- Java source and bytecode: 17
- Spring Boot: 3.5.16
- MyBatis Spring Boot Starter: 3.0.5
- Flowable: 7.2.0
- Vue: 3.5 stable line
- Element Plus: 2.14 stable line

Spring Boot 4 and Flowable 8 are intentionally excluded from the first release. Dependency changes require a dedicated pull request and compatibility test evidence.

The local bootstrap machine may use a newer JDK, but Maven compiles with `--release 17`.
