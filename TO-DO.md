# TO-DO

Items that are valid improvements identified during code review but are out of scope for the current ticket.

---

## Ticket 16 — Futures Support

### 1. Update stale futures seed data (Comment 6) ✅ DONE
Resolved in `V9__update_futures_contracts_to_2026.sql`:
- Deactivates all 2025 rows (`is_active = FALSE, is_front_month = FALSE`)
- Inserts current Q3/Q4 2026 contracts (ESU6, ESZ6, NQU6, NQZ6, MESU6, MNQU6, CLQ6, GCQ6)
- `infrastructure/db/init.sql` updated to seed 2026 data for fresh Docker environments

---

---

## Ticket 17 — Cryptocurrency Support

### 3. Open crypto positions without a stop loss counted as zero exposure (Comment 9) ✅ DONE
Resolved in `CryptoRiskValidator`:
- `validate()` now rejects any crypto signal that has no stop loss ("Crypto signals must include a stop loss") — this is the forward-facing guard ensuring every new position will carry a stop.
- `getCryptoExposurePct()` now uses a conservative fallback for any legacy position without a recorded stop: `entry × minStopLossPct% × quantity`. Such positions are never invisible to the exposure cap.
- `CryptoRiskValidatorTest` updated: `validate_noStopLoss_rejects` (was `_skipsTightnessCheck`) and `getCryptoExposurePct_positionsWithoutStop_usesMinStopFallback` (was `_excludedFromCalculation`).

---

---

## Ticket 18 — Backtesting Framework Integration

### 1. Loader cannot distinguish same-symbol listings across exchanges (Comment 5) ✅ DONE
Resolved: `HistoricalDataLoader.load()` now accepts `exchange: str | None = None`. The SQL uses `($3::text IS NULL OR exchange = $3)` to filter by exchange when provided. All 4 request models, the `compare` query param, `_load_data` helper, and all 6 endpoint call sites updated accordingly.

### 2. Backtrader engine returns empty trades DataFrame (Comment 7) ✅ DONE
Resolved: `_SignalStrategy` now implements `notify_order()` (tracks entry size) and `notify_trade()` (captures closed trade details). Trades are accumulated in `self_s.closed_trades` and returned as a properly-shaped DataFrame with columns `entry_time`, `exit_time`, `entry_price`, `exit_price`, `pnl`, `side`.

---

### 4. Timeframe-aware Sharpe/Sortino annualisation (Comment 3) ✅ DONE
Resolved: `calculate_metrics()` now accepts `timeframe: str = "1D"` and derives the correct annualisation factor from `_BARS_PER_YEAR` (e.g. `sqrt(1638)` for `1H`, `sqrt(252)` for `1D`). All call sites in the router (`run_backtest`, `compare_strategies`, `generate_report`), `VectorBTEngine._run_with_pandas`, `GridSearchOptimizer.optimize()`, and `WalkForwardAnalyzer.run()` now pass `timeframe` through. The previously hardcoded `sqrt(252)` is gone; intraday Sharpe and Sortino ratios are now correctly scaled.

---

### 3. UTC business-day rollover date calculation (Comment 19) ✅ DONE
Resolved in `RolloverService.java`:
- `Clock clock` injected via constructor; `FuturesConfig` provides `@Bean Clock clock()` returning `Clock.systemUTC()`
- All `LocalDate.now()` calls replaced with `LocalDate.now(clock)`
- `minusBusinessDays(LocalDate date, int businessDays)` helper added — skips Saturday and Sunday
- `RolloverServiceTest` uses `Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC)` with explicit `LocalDate.of(...)` dates so `daysRemaining` assertions are deterministic

---

## Ticket 21 — MT5 Bridge

### 1. Add API key / mTLS authentication to bridge routes (Comment 8)
The `/api/orders/*`, `/api/positions/*`, and `/api/account/*` routes have no authentication layer.
Currently mitigated by the fact that port 8002 should only be exposed on internal Docker networking.
For production hardening, add a header-based API key dependency injected via FastAPI `Depends()`.

### 2. Offload blocking MT5 SDK calls to asyncio thread pool (Comment 10)
All `MT5Client` methods perform synchronous native I/O called directly from `async def` route handlers.
Under a single Uvicorn worker, slow or retried MT5 calls (plus `time.sleep` in `_retry`) stall the event loop.
Fix: wrap all `MT5Client` method calls in `await asyncio.to_thread(...)` at the service layer.
