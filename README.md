# PRISM — Intelligent LLM Gateway

PRISM is an OpenAI-compatible LLM gateway built as a capstone project. It provides a single `/v1/chat/completions` API in front of multiple LLM providers and adds virtual-key authentication, model allowlisting, automatic routing, provider fallback, rate limiting, budget protection, semantic caching, cost tracking, and a frontend dashboard/playground for demonstration.

---

## 1. Project Overview

The gateway receives an OpenAI-compatible chat-completion request and applies the following flow:

```text
Client / Frontend
       |
       v
POST /v1/chat/completions
       |
       +--> Virtual-key authentication
       |
       +--> Model allowlist validation
       |
       +--> Rate-limit / budget checks
       |
       +--> Semantic-cache lookup
       |        |
       |        +--> HIT ----> Redis cached response
       |        |
       |        +--> MISS
       |
       +--> Model routing
       |
       +--> Provider selection
       |        |
       |        +--> Alpha
       |        |
       |        +--> Beta fallback
       |
       +--> Cost accounting
       |
       +--> Semantic-cache write
       |
       v
OpenAI-compatible response
```

The project uses:

- **Java / Spring Boot** for the gateway backend
- **React + Vite** for the frontend
- **Redis** for rate limiting and cached responses
- **ChromaDB** for semantic-cache vector search
- **Docker** for infrastructure services
- Two mock OpenAI-compatible providers: **Alpha** and **Beta**

---

# 2. Repository Structure

```text
prism-capstone/
│
├── gateway/                  # Spring Boot backend
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── frontend/                 # React + Vite frontend
│   ├── src/
│   ├── package.json
│   └── ...
│
├── scripts/                  # Mock provider / verification scripts
│
├── data/                     # Seed configuration and project data
│   ├── seed_keys.json
│   ├── model_pricing.json
│   ├── gateway_config.sample.json
│   ├── routing_eval.jsonl
│   └── sample_requests.jsonl
│
├── docs/                     # Assignment/API/data-model documentation
│
├── PRISM_PROBLEM_STATEMENT.md
├── README.md
└── .gitignore
```

---

# 3. Prerequisites

Install the following before running the project:

### Required

- Git
- Java 21
- Maven or the Maven Wrapper included with the gateway
- Node.js and npm
- Docker Desktop

### Recommended versions

```text
Java       21
Node.js    20 or 22 LTS
npm        compatible with the installed Node.js version
Docker     Docker Desktop with Docker Engine running
```

> Node.js 21 is not recommended for this project because Vite may report an unsupported-engine warning. Use Node.js 20 LTS or 22 LTS for a clean setup.

Verify installations:

```powershell
java -version
node -v
npm -v
docker --version
git --version
```

---

# 4. Clone the Repository

```powershell
git clone https://github.com/YOUR_USERNAME/prism-capstone.git
cd prism-capstone
```

Replace `YOUR_USERNAME` with the GitHub account that owns the repository.

---

# 5. Start Infrastructure

The gateway requires Redis and ChromaDB.

## 5.1 Start Redis

If the container does not already exist:

```powershell
docker run -d `
  --name prism-redis `
  -p 6379:6379 `
  redis:latest
```

If the container already exists but is stopped:

```powershell
docker start prism-redis
```

Verify:

```powershell
docker ps
```

Test Redis:

```powershell
docker exec -it prism-redis redis-cli ping
```

Expected:

```text
PONG
```

---

## 5.2 Start ChromaDB

If the container does not already exist:

```powershell
docker run -d `
  --name prism-chroma `
  -p 8000:8000 `
  chromadb/chroma:0.5.20
```

If it already exists:

```powershell
docker start prism-chroma
```

Verify:

```powershell
docker ps
```

Both infrastructure containers should be running:

```text
prism-redis
prism-chroma
```

---

# 6. Start Mock LLM Providers

PRISM uses two mock OpenAI-compatible providers so that provider selection and fallback can be demonstrated without requiring paid external APIs.

The mock provider supports:

```text
POST /v1/chat/completions
GET  /health
GET  /admin/config
POST /admin/config
```

The two providers run on different ports.

## Provider Alpha

Open a terminal in the directory containing `mock_provider.py`:

```powershell
python mock_provider.py --port 9001 --name alpha
```

Alpha exposes:

```text
alpha-small
alpha-large
```

## Provider Beta

Open another terminal:

```powershell
python mock_provider.py --port 9002 --name beta
```

Beta exposes:

```text
beta-small
beta-large
```

Keep both terminals running.

---

# 7. Start the Spring Boot Gateway

Open another terminal:

```powershell
cd gateway
```

On Windows, use the Maven Wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

The gateway should start on:

```text
http://localhost:8080
```

The configured infrastructure endpoints are:

```text
Redis:
localhost:6379

ChromaDB:
localhost:8000

Provider Alpha:
localhost:9001

Provider Beta:
localhost:9002

Gateway:
localhost:8080
```

---

# 8. Start the Frontend

Open another terminal:

```powershell
cd frontend
```

Install dependencies:

```powershell
npm install
```

Start Vite:

```powershell
npm run dev
```

The frontend normally starts at:

```text
http://localhost:5173
```

Open that address in the browser.

The frontend uses the Vite development proxy so that requests to:

```text
/v1/*
```

are forwarded to:

```text
http://localhost:8080
```

This means the browser does not need to call the backend using a hard-coded cross-origin URL.

---

# 9. Configuration

The backend provider/infrastructure configuration is located in the Spring Boot configuration.

The expected local infrastructure configuration is:

```yaml
spring:
  application:
    name: gateway
  data:
    redis:
      host: localhost
      port: 6379

prism:
  providers:
    alpha:
      base-url: http://localhost:9001
      timeout: 5s
    beta:
      base-url: http://localhost:9002
      timeout: 5s
  chroma:
    url: http://localhost:8000
    similarity-threshold: 0.20
```

The semantic-cache threshold can also be configured per virtual key.

---

# 10. Virtual Keys

The seed configuration contains four demonstration tenants:

| Team        | Virtual Key                     | Allowed Models                | Cache    |
| ----------- | ------------------------------- | ----------------------------- | -------- |
| search      | `prism-sk-search-1a2b3c`      | `fast`                      | Enabled  |
| research    | `prism-sk-research-4d5e6f`    | `fast`, `smart`, `auto` | Enabled  |
| free-tier   | `prism-sk-free-7g8h9i`        | `fast`                      | Enabled  |
| budget-demo | `prism-sk-budget-demo-0j1k2l` | `fast`                      | Disabled |

Example seed configuration:

```json
{
  "team": "research",
  "virtual_key": "prism-sk-research-4d5e6f",
  "monthly_budget_usd": 500,
  "rate_limit": {
    "requests_per_minute": 300,
    "tokens_per_minute": 1000000
  },
  "model_allowlist": [
    "fast",
    "smart",
    "auto"
  ],
  "semantic_cache": {
    "enabled": true,
    "similarity_threshold": 0.85
  }
}
```

The virtual key determines what the client is allowed to request.

For example, the `search` key only allows:

```text
fast
```

Therefore:

```text
model = auto
```

with the search key should be rejected.

The `research` key supports:

```text
fast
smart
auto
```

---

# 11. API Usage

The primary API is:

```text
POST http://localhost:8080/v1/chat/completions
```

Example request:

```powershell
curl.exe -X POST "http://localhost:8080/v1/chat/completions" `
  -H "Authorization: Bearer prism-sk-research-4d5e6f" `
  -H "Content-Type: application/json" `
  --data-binary '{"model":"auto","messages":[{"role":"user","content":"What is the capital of France?"}],"stream":false}'
```

The gateway returns an OpenAI-compatible response.

---

# 12. Frontend Playground

The frontend provides a playground for demonstrating the gateway.

The user can select:

- Virtual key
- Model
- Prompt
- Provider/fallback configuration where applicable

The model selector reflects the capabilities of the selected virtual key.

For example:

```text
Search key:
  fast       selectable
  smart      unavailable
  auto       unavailable

Research key:
  fast       selectable
  smart      selectable
  auto       selectable
```

The playground displays gateway information such as:

- Request status
- Cache hit/miss
- Provider
- Route
- Routed model
- Latency
- Request cost
- Response

Invalid requests display the backend error rather than presenting a misleading generic success/failure state.

---

# 13. Semantic Cache

PRISM implements a semantic cache using:

```text
ChromaDB
   +
Redis
```

The architecture is:

```text
Incoming prompt
      |
      v
Generate embedding
      |
      v
ChromaDB similarity search
      |
      +---- distance <= threshold ----> Cache candidate
      |                                      |
      |                                      v
      |                               Redis response
      |
      +---- distance > threshold ----> Cache MISS
```

ChromaDB stores the semantic representation and cache-entry metadata.

Redis stores the actual cached response.

The cache is isolated by virtual key using the `virtual_key` metadata filter.

---

# 14. Semantic Cache Test

Use the same virtual key for both requests.

### Request 1

```text
What is the capital of France?
```

Expected:

```text
CACHE MISS
```

The request is sent to the provider and the response is stored.

### Request 2

Send exactly:

```text
can you tell me capital of France?
```

Expected:

```text
CACHE HIT
```

The provider should not need to generate another response.

### Important

Semantic caching is intentionally based on vector similarity rather than exact string equality.

Therefore, changing the wording can still result in a cache hit if the embedding distance is below the configured threshold.

The threshold should be tuned carefully. A threshold that is too permissive can produce false semantic matches.

---

# 15. Automatic Routing

When:

```json
"model": "auto"
```

is requested, the gateway determines an appropriate route.

The gateway can resolve:

```text
auto -> fast
```

for simple/factual requests, while more complex requests can be routed toward the appropriate model category according to the implemented routing logic.

The gateway logs the routing decision, including the resolved model and routing reason/confidence when available.

---

# 16. Provider Selection and Fallback

PRISM supports multiple providers.

Example:

```text
Primary:
Alpha

Fallback:
Beta
```

If the primary provider fails and fallback is enabled, the gateway can retry using the configured fallback provider.

This allows the project to demonstrate resilience without depending on real external LLM APIs.

---

# 17. Cost Tracking

Model pricing is maintained in:

```text
data/model_pricing.json
```

Example:

```json
{
  "alpha-small": {
    "input_per_1m": 0.15,
    "output_per_1m": 0.60
  },
  "alpha-large": {
    "input_per_1m": 3.00,
    "output_per_1m": 15.00
  },
  "beta-small": {
    "input_per_1m": 0.10,
    "output_per_1m": 0.40
  },
  "beta-large": {
    "input_per_1m": 2.50,
    "output_per_1m": 10.00
  }
}
```

The gateway calculates request cost based on token usage and the selected provider model.

The frontend displays the cost for the request.

---

# 18. Rate Limiting

Each virtual key has its own request and token limits.

Example:

```text
search
RPM: 60
TPM: 100000

research
RPM: 300
TPM: 1000000

free-tier
RPM: 10
TPM: 20000
```

Redis is used to maintain the rate-limit state.

The gateway should reject requests that exceed the configured limits.

---

# 19. Monthly Budget Protection

Each virtual key also has a monthly USD budget.

Example:

```text
search       $50
research     $500
free-tier    $5
budget-demo  $0.00001
```

The `budget-demo` key is intentionally configured with a very small budget so that budget rejection can be demonstrated during evaluation.

---

# 20. Recommended Demo Test Cases

For evaluation/demo purposes, demonstrate the following scenarios.

## Test 1 — Normal request

Use:

```text
Key: research
Model: auto
Prompt: What is the capital of France?
```

Expected:

```text
Successful response
Cache MISS on first request
```

---

## Test 2 — Semantic cache hit

Send the same request again.

Expected:

```text
Cache HIT
```

---

## Test 3 — Automatic routing

Use:

```text
Key: research
Model: auto
```

with a simple factual request.

Show the gateway log:

```text
AUTO ROUTING
requestedModel=auto
resolvedModel=fast
```

---

## Test 4 — Provider fallback

Configure the primary provider to fail and send a request with fallback enabled.

Expected:

```text
Primary provider fails
        ↓
Fallback provider selected
        ↓
Successful response
```

---

## Test 5 — Rate limiting

Use a key with a deliberately low RPM limit, such as:

```text
free-tier
```

Send enough requests to exceed the configured limit.

Expected:

```text
Rate-limit rejection
```

---

## Test 6 — Budget protection

Use:

```text
prism-sk-budget-demo-0j1k2l
```

Expected:

```text
budget_exceeded
```

The budget-demo cache is disabled so that a cached response does not hide the budget rejection.

---

## Test 7 — Cache isolation

Store a cached request using one virtual key.

Then make the same request using another virtual key.

The second key should not receive the first tenant's cached response.

This verifies tenant isolation.

---

# 21. Useful Redis Commands

Check running Redis keys:

```powershell
docker exec -it prism-redis redis-cli
```

Inside Redis:

```redis
KEYS *
```

For rate-limit keys:

```redis
KEYS prism:rate-limit:*
```

For a particular key:

```redis
TTL prism:rate-limit:prism-sk-research-4d5e6f:requests
```

Exit Redis:

```redis
exit
```

---

# 22. Useful Docker Commands

List containers:

```powershell
docker ps
```

Stop infrastructure:

```powershell
docker stop prism-redis prism-chroma
```

Start infrastructure:

```powershell
docker start prism-redis prism-chroma
```

Remove containers:

```powershell
docker rm -f prism-redis prism-chroma
```

Then recreate them using the commands in the Infrastructure section.

---

# 23. Troubleshooting

## Port 6379 already in use

Check:

```powershell
netstat -ano | findstr :6379
```

## Port 8000 already in use

```powershell
netstat -ano | findstr :8000
```

## Port 8080 already in use

```powershell
netstat -ano | findstr :8080
```

## Port 5173 already in use

Vite will normally select another available port, but the configured frontend port is:

```text
5173
```

## Check Docker containers

```powershell
docker ps
```

Expected infrastructure:

```text
prism-redis
prism-chroma
```

## Check provider ports

```powershell
netstat -ano | findstr :9001
netstat -ano | findstr :9002
```

## Backend cannot connect to Redis

Verify:

```powershell
docker exec -it prism-redis redis-cli ping
```

Expected:

```text
PONG
```

## Backend cannot connect to ChromaDB

Verify the container:

```powershell
docker ps
```

and confirm port:

```text
8000 -> 8000
```

---

# 24. Clean Restart

To restart the infrastructure:

```powershell
docker stop prism-redis prism-chroma
docker start prism-redis prism-chroma
```

Then restart:

1. Mock provider Alpha
2. Mock provider Beta
3. Spring Boot gateway
4. React frontend

For a completely fresh infrastructure state:

```powershell
docker rm -f prism-redis prism-chroma
```

Then recreate the containers using the commands in Section 5.

---

# 25. Stopping the Application

Stop the frontend terminal with:

```text
Ctrl + C
```

Stop the Spring Boot gateway with:

```text
Ctrl + C
```

Stop the mock providers with:

```text
Ctrl + C
```

Stop Docker infrastructure:

```powershell
docker stop prism-redis prism-chroma
```

---

Focus on the implemented gateway behavior rather than spending time explaining every source file.

---

# 26. Technology Stack

### Backend

- Java 21
- Spring Boot
- Maven
- Reactive HTTP gateway components
- Redis
- ChromaDB

### Frontend

- React
- Vite
- JavaScript
- CSS

### Infrastructure

- Docker
- Redis
- ChromaDB

### Testing / Demonstration

- PowerShell / curl
- Postman
- Mock OpenAI-compatible providers

---

# 27. Key Features Demonstrated

PRISM demonstrates:

- OpenAI-compatible `/v1/chat/completions` API
- Virtual-key authentication
- Per-key model allowlists
- Tenant isolation
- Automatic model routing
- Multiple-provider routing
- Provider fallback
- Semantic caching
- Redis-backed response cache
- ChromaDB vector similarity search
- Configurable semantic-cache thresholds
- Request-rate limiting
- Token-rate limiting
- Monthly budget protection
- Cost calculation
- Frontend playground/dashboard
- Dockerized infrastructure
- Observability through gateway logs and frontend request metadata

---
# Prism Capstone Pack

This repository contains a self-contained capstone package for building **Prism**, an AI-first LLM gateway with smart difficulty-based routing, failover, streaming, per-tenant metering, budgets, and a semantic cache. The gateway makes two AI decisions on every request: how hard is this prompt (which model tier deserves it), and have we answered it before (semantic cache).

The capstone is language agnostic. You may implement it in Node.js, Java, Python, Go, Ruby, or any stack you are comfortable with, as long as you satisfy the API, correctness, and verification requirements.

## Optional Local Utilities

All scripts use only Python standard library modules (Python 3.9+). From this folder:

```bash
python3 scripts/validate_pack.py
```

Start two mock providers (two terminals):

```bash
python3 scripts/mock_provider.py --port 9001 --name alpha
python3 scripts/mock_provider.py --port 9002 --name beta
```

You now have 2 "providers" serving `alpha-small`, `alpha-large`, `beta-small`, `beta-large` — free, offline, and OpenAI-compatible. Try one:

```bash
curl -s http://localhost:9001/v1/chat/completions \
  -H "Authorization: Bearer anything" -H "Content-Type: application/json" \
  -d '{"model": "alpha-small", "messages": [{"role": "user", "content": "hello"}]}'
```

Inject failures live — no restarts needed:

```bash
# take alpha down; your gateway should fail over to beta
curl -s -X POST http://localhost:9001/admin/config -d '{"mode": "down"}'

# make alpha slow (3s) - do your timeouts hold?
curl -s -X POST http://localhost:9001/admin/config -d '{"mode": "ok", "latency_ms": 3000}'

# make alpha flaky - do your retries handle a 30% error rate?
curl -s -X POST http://localhost:9001/admin/config -d '{"latency_ms": 0, "fail_rate": 0.3}'

# back to healthy
curl -s -X POST http://localhost:9001/admin/config -d '{"mode": "ok", "fail_rate": 0}'
```

Verify your gateway once it is running:

```bash
python3 scripts/smoke_test.py --url http://localhost:8080 --key prism-sk-search-1a2b3c --model fast
python3 scripts/load_test.py  --url http://localhost:8080 --key prism-sk-free-7g8h9i --model fast --requests 30 --concurrency 10 --rpm-limit 10
```

The mock providers are the intended upstreams for development, demos, and load testing — they cost nothing and make failover deterministic. You may swap one for a real provider (or Ollama) if you want.

## Language-Agnostic Expectations

- An OpenAI-compatible `POST /v1/chat/completions` data plane matching `docs/API_CONTRACT.md`, including the `x-prism-*` header contract.
- Streaming pass-through without buffering.
- Virtual-key authentication with per-key allowlists, rate limits, and monthly budgets.
- Alias-based routing with retries and provider failover.
- An `auto` alias that classifies prompt difficulty and routes to a model tier, evaluated against `data/routing_eval.jsonl`.
- A per-tenant semantic response cache.
- Accurate cost metering, request logs, and a usage API.
- A lightweight ops console for usage, recent requests, and cache stats.
- A provider adapter that can be mocked in tests.

The console can be vibe-coded or AI-assisted. It does not need to be visually complex, but it should let you demonstrate usage by key, recent traffic, and cache hit rates.

## Recommended Reading Order

1. Read `PRISM_PROBLEM_STATEMENT.md`.
2. Follow `docs/IMPLEMENTATION_GUIDE.md` for the build path.
3. Use `docs/API_CONTRACT.md` and `docs/DATA_MODEL.md` while designing your implementation.
4. Use `docs/EVALUATION_GUIDE.md` before running the verification scripts and writing your report.
