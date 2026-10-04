import React, { useEffect, useMemo, useState } from "react";
import {
  Activity,
  ArrowRight,
  BarChart3,
  Bot,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  Database,
  Gauge,
  KeyRound,
  LayoutDashboard,
  Menu,
  MessageSquare,
  Network,
  Play,
  RefreshCw,
  Server,
  Sparkles,
  XCircle,
  Zap,
} from "lucide-react";

const DEFAULT_KEY = "prism-sk-search-1a2b3c";
const START =
  "Design a scalable distributed payment processing system using microservices, Kafka, PostgreSQL and Redis. Explain the architecture, failure handling, consistency model and database design.";
const nav = [
  ["dashboard", "Dashboard", LayoutDashboard],
  ["playground", "Playground", MessageSquare],
  ["keys", "API Keys", KeyRound],
  ["providers", "Providers", Server],
  ["routing", "Routing", Network],
  ["cache", "Semantic Cache", Database],
  ["limits", "Rate Limits", Gauge],
];

const VIRTUAL_KEYS = [
  {
    value: "prism-sk-search-1a2b3c",
    team: "search",
    budget: 50,
    rpm: 60,
    tpm: 100000,
    cache: true,
    threshold: 0.92,
    allowedModels: ["fast"],
  },
  {
    value: "prism-sk-research-4d5e6f",
    team: "research",
    budget: 500,
    rpm: 300,
    tpm: 1000000,
    cache: true,
    threshold: null,
    allowedModels: ["fast", "smart", "auto"],
  },
  {
    value: "prism-sk-free-7g8h9i",
    team: "free-tier",
    budget: 5,
    rpm: 10,
    tpm: 20000,
    cache: true,
    threshold: 0.85,
    allowedModels: ["fast"],
  },
  {
    value: "prism-sk-budget-demo-0j1k2l",
    team: "budget-demo",
    budget: 0.00001,
    rpm: 60,
    tpm: 20000,
    cache: false,
    threshold: null,
    allowedModels: ["fast"],
  },
];

const MODELS = [
  {
    value: "fast",
    label: "fast",
    description: "Low-latency / general requests",
  },
  {
    value: "smart",
    label: "smart",
    description: "Complex reasoning and technical requests",
  },
  {
    value: "auto",
    label: "auto",
    description: "Gateway chooses fast or smart",
  },
];
async function callGateway(key, model, prompt, provider = "", fallback = true) {
  const t = performance.now();

  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${key}`,
    "x-prism-fallback": String(fallback),
  };

  // Only send provider when the user explicitly selected one.
  if (provider) {
    headers["x-prism-provider"] = provider;
  }

  console.log("Gateway request:", {
    key,
    model,
    provider,
    fallback,
    headers,
    prompt,
  });

  const r = await fetch("/v1/chat/completions", {
    method: "POST",
    headers,
    body: JSON.stringify({
      model,
      messages: [
        {
          role: "user",
          content: prompt,
        },
      ],
      stream: false,
    }),
  });

  const responseHeaders = Object.fromEntries(r.headers.entries());
  const text = await r.text();

  let data;

  try {
    data = JSON.parse(text);
  } catch {
    data = {
      raw: text,
    };
  }

  console.log("Gateway response:", {
    status: r.status,
    ok: r.ok,
    data,
    headers: responseHeaders,
  });

  if (!r.ok) {
    throw Object.assign(
      new Error(
        data?.error?.message ||
        data?.message ||
        data?.error ||
        data?.raw ||
        `HTTP ${r.status}`
      ),
      {
        status: r.status,
        response: data,
      }
    );
  }

  const get = (...names) =>
    names
      .map((name) => responseHeaders[name])
      .find(Boolean);

  return {
    data,
    h: responseHeaders,
    latency: Math.round(performance.now() - t),

    cache: (
      get(
        "x-prism-cache",
        "x-prism-cache-status",
        "x-cache-status"
      ) || "UNKNOWN"
    ).toUpperCase(),

    provider:
      get(
        "x-prism-provider",
        "x-provider"
      ) || "—",

    route:
      get(
        "x-prism-route",
        "x-prism-routing",
        "x-prism-routing-decision"
      ) || model,

    routedModel:
      get(
        "x-prism-model",
        "x-prism-selected-model"
      ) || data?.model || model,

    cost:
      parseFloat(
        get(
          "x-prism-cost-usd",
          "x-prism-cost",
          "x-prism-request-cost",
          "x-request-cost"
        ) || "0"
      ) || 0,

    fallback:
      get(
        "x-prism-fallback",
        "x-prism-fallback-used"
      ) || "false",
  };
}

function App() {
  const [page, setPage] = useState("dashboard"),
    [mobile, setMobile] = useState(false),
    [history, setHistory] = useState(() =>
      JSON.parse(localStorage.getItem("prism-history") || "[]"),
    );
  useEffect(
    () =>
      localStorage.setItem(
        "prism-history",
        JSON.stringify(history.slice(0, 100)),
      ),
    [history],
  );
  const stats = useMemo(() => {
    let n = history.length,
      h = history.filter((x) => x.cache.includes("HIT")).length;
    return {
      n,
      h,
      rate: n ? Math.round((h / n) * 100) : 0,
      lat: n ? Math.round(history.reduce((a, x) => a + x.lat, 0) / n) : 0,
      cost: history.reduce((a, x) => a + x.cost, 0),
    };
  }, [history]);
  return (
    <div className="shell">
      <aside className={mobile ? "side open" : "side"}>
        <div className="brand">
          <b>✦ PRISM</b>
          <small>AI Gateway</small>
        </div>
        <div className="label">CONTROL PLANE</div>
        {nav.map(([id, name, I]) => (
          <button
            className={page === id ? "nav active" : "nav"}
            onClick={() => {
              setPage(id);
              setMobile(false);
            }}
            key={id}
          >
            <I size={17} />
            {name}
          </button>
        ))}
        <div className="sidefoot">
          ● Gateway UI
          <br />
          <small>Spring Boot backend</small>
        </div>
      </aside>
      <main>
        <header>
          <button className="hamb" onClick={() => setMobile(!mobile)}>
            <Menu />
          </button>
          <span>
            PRISM / <b>{nav.find((x) => x[0] === page)?.[1]}</b>
          </span>
          <span className="online">● Gateway</span>
        </header>
        <div className="content">
          {page === "dashboard" && (
            <Dashboard stats={stats} history={history} go={setPage} />
          )}
          {page === "playground" && (
            <Playground
              add={(x) => setHistory((h) => [x, ...h].slice(0, 100))}
            />
          )}
          {page === "keys" && <Keys />}
          {page === "providers" && <Providers />}
          {page === "routing" && <Routing />}
          {page === "cache" && <Cache stats={stats} />}
          {page === "limits" && <Limits />}
        </div>
      </main>
    </div>
  );
}

function Head({ title, desc, action }) {
  return (
    <div className="head">
      <div>
        <small>PRISM GATEWAY</small>
        <h1>{title}</h1>
        <p>{desc}</p>
      </div>
      {action}
    </div>
  );
}
function Card({ icon: I, label, value, detail }) {
  return (
    <div className="stat">
      <span>
        <I size={18} />
      </span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
        <em>{detail}</em>
      </div>
    </div>
  );
}
function Dashboard({ stats, history, go }) {
  return (
    <>
      <Head
        title="Gateway Overview"
        desc="Live request telemetry from this browser session."
        action={
          <button className="primary" onClick={() => go("playground")}>
            <Play size={15} /> Playground
          </button>
        }
      />
      <div className="stats">
        <Card
          icon={Activity}
          label="Requests"
          value={stats.n}
          detail="This session"
        />
        <Card
          icon={Database}
          label="Cache hit rate"
          value={stats.rate + "%"}
          detail={`${stats.h} semantic hits`}
        />
        <Card
          icon={Clock3}
          label="Avg latency"
          value={stats.lat + " ms"}
          detail="Client measured"
        />
        <Card
          icon={CircleDollarSign}
          label="Tracked cost"
          value={"$" + stats.cost.toFixed(6)}
          detail="Gateway headers"
        />
      </div>
      <div className="grid2">
        <section className="panel">
          <h2>Recent Requests</h2>
          {history.length ? (
            <div>
              {history.slice(0, 8).map((x, i) => (
                <div className="row" key={i}>
                  <MessageSquare size={15} />
                  <div>
                    <b>{x.model}</b>
                    <small>{x.prompt}</small>
                  </div>
                  <mark className={x.cache.includes("HIT") ? "hit" : ""}>
                    {x.cache}
                  </mark>
                  <span>{x.lat}ms</span>
                </div>
              ))}
            </div>
          ) : (
            <Empty text="Send a request from Playground." />
          )}
        </section>
        <section className="panel">
          <h2>Gateway Capabilities</h2>
          {[
            "OpenAI-compatible /v1/chat/completions",
            "Virtual-key authentication",
            "fast / smart / auto routing",
            "Chroma + Redis semantic cache",
            "RPM + TPM rate limits",
            "Provider fallback",
          ].map((x) => (
            <div className="cap" key={x}>
              <CheckCircle2 size={15} />
              {x}
            </div>
          ))}
        </section>
      </div>
    </>
  );
}

function Playground({ add }) {
  const [key, setKey] = useState(DEFAULT_KEY),
    [model, setModel] = useState("fast"),
    [prompt, setPrompt] = useState(START),
    [out, setOut] = useState(""),
    [meta, setMeta] = useState(null),
    [err, setErr] = useState(null),
    [busy, setBusy] = useState(false);
  const selectedKey = useMemo(
    () => VIRTUAL_KEYS.find((x) => x.value === key) || VIRTUAL_KEYS[0],
    [key],
  );
  const allowedModels = selectedKey.allowedModels;
  const modelAllowed = allowedModels.includes(model);

  const selectKey = (newKey) => {
    const config =
      VIRTUAL_KEYS.find((x) => x.value === newKey) || VIRTUAL_KEYS[0];
    setKey(config.value);
    if (!config.allowedModels.includes(model))
      setModel(config.allowedModels[0]);
    setErr(null);
    setOut("");
    setMeta(null);
  };

  const send = async () => {
    setErr(null);
    setOut("");
    setMeta(null);
    if (!modelAllowed) {
      setErr({
        status: 403,
        title: "Model not allowed",
        message: `Model "${model}" is not allowed for virtual key "${key}". Allowed models: ${allowedModels.join(", ")}.`,
      });
      return;
    }
    if (!prompt.trim()) {
      setErr({
        status: 400,
        title: "Prompt required",
        message: "Enter a prompt before sending the request.",
      });
      return;
    }
    setBusy(true);
    try {
      const r = await callGateway(key, model, prompt),
        d = r.data,
        c =
          d?.choices?.[0]?.message?.content ||
          d?.response ||
          d?.raw ||
          JSON.stringify(d, null, 2),
        m = {
          ...r,
          cache: r.cache.includes("HIT")
            ? "HIT"
            : r.cache.includes("MISS")
              ? "MISS"
              : "UNKNOWN",
        };
      setOut(c);
      setMeta(m);
      add({
        model: r.routedModel,
        prompt,
        cache: m.cache,
        lat: r.latency,
        cost: r.cost,
      });
    } catch (e) {
      let message = e.message;
      if (e.status === 403 && !modelAllowed)
        message = `Model "${model}" is not allowed for virtual key "${key}". Allowed models: ${allowedModels.join(", ")}.`;
      else if (
        e.status === 403 &&
        (!message || message.toLowerCase() === "forbidden")
      )
        message = `The gateway rejected this request. Check the virtual key and model allowlist. Selected key: ${key}; selected model: ${model}.`;
      setErr({
        status: e.status || 500,
        title:
          e.status === 403 ? "Request forbidden" : "Gateway request failed",
        message,
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Head
        title="API Playground"
        desc="Make a real request through your Spring Boot gateway."
      />
      <div className="play">
        <section className="panel">
          <h2>Request</h2>
          <label>
            Virtual Key
            <select value={key} onChange={(e) => selectKey(e.target.value)}>
              {VIRTUAL_KEYS.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.value} — {item.team}
                </option>
              ))}
            </select>
          </label>
          <div className="selection-info">
            <KeyRound size={14} />
            <span>
              <b>{selectedKey.team}</b> · {selectedKey.allowedModels.join(", ")}{" "}
              model{selectedKey.allowedModels.length === 1 ? "" : "s"} allowed
            </span>
          </div>
          <label>
            Model
            <select value={model} onChange={(e) => setModel(e.target.value)}>
              {MODELS.map((item) => (
                <option
                  key={item.value}
                  value={item.value}
                  disabled={!allowedModels.includes(item.value)}
                >
                  {item.label}
                  {!allowedModels.includes(item.value)
                    ? " — Not allowed for this key"
                    : ""}
                </option>
              ))}
            </select>
          </label>
          <p className="field-help">
            {MODELS.find((x) => x.value === model)?.description}
            {!modelAllowed && (
              <span className="warning-text">
                {" "}
                · Select an allowed model to send
              </span>
            )}
          </p>
          <label>
            Prompt
            <textarea
              rows="13"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
            />
          </label>
          <button
            className="primary send"
            disabled={busy || !modelAllowed || !prompt.trim()}
            onClick={send}
          >
            {busy ? (
              <>
                <RefreshCw className="spin" size={16} />
                Sending...
              </>
            ) : (
              <>
                <Play size={16} />
                Send Request
              </>
            )}
          </button>
        </section>
        <section className="panel">
          <div className="resulthead">
            <h2>Response</h2>
            {meta && (
              <mark
                className={meta.status >= 200 && meta.status < 300 ? "hit" : ""}
              >
                HTTP {meta.status || 200}
              </mark>
            )}
          </div>
          {err ? (
            <div className="error">
              <XCircle size={20} />
              <div>
                <b>
                  {err.title}
                  {err.status ? ` · HTTP ${err.status}` : ""}
                </b>
                <p>{err.message}</p>
              </div>
            </div>
          ) : !out ? (
            <Empty text="Your model response will appear here." />
          ) : (
            <>
              <div className="telemetry">
                {[
                  ["Cache", meta.cache],
                  ["Route", meta.route],
                  ["Provider", meta.provider],
                  ["Model", meta.routedModel],
                  ["Latency", meta.latency + " ms"],
                  ["Cost", "$" + meta.cost.toFixed(7)],
                ].map((x) => (
                  <div key={x[0]}>
                    <small>{x[0]}</small>
                    <b className={x[1] === "HIT" ? "green" : ""}>{x[1]}</b>
                  </div>
                ))}
              </div>
              <pre>{out}</pre>
            </>
          )}
        </section>
      </div>
      <div className="tip">
        <Sparkles size={17} />
        <span>
          <b>Demo:</b> send the same prompt twice for MISS → HIT, then change
          wording to demonstrate semantic matching.
        </span>
      </div>
    </>
  );
}

function Empty({ text }) {
  return (
    <div className="empty">
      <Bot size={24} />
      <span>{text}</span>
    </div>
  );
}
function Keys() {
  return (
    <>
      <Head
        title="API Keys"
        desc="Virtual-key configuration used by the demo."
      />
      <section className="panel table">
        <table>
          <thead>
            <tr>
              <th>Virtual Key</th>
              <th>Team</th>
              <th>Budget</th>
              <th>RPM</th>
              <th>TPM</th>
              <th>Models</th>
              <th>Cache</th>
            </tr>
          </thead>
          <tbody>
            {VIRTUAL_KEYS.map((k) => (
              <tr key={k.value}>
                <td>{k.value}</td>
                <td>{k.team}</td>
                <td>${k.budget}</td>
                <td>{k.rpm.toLocaleString()}</td>
                <td>{k.tpm.toLocaleString()}</td>
                <td>{k.allowedModels.join(", ")}</td>
                <td>{k.cache ? "Enabled" : "Disabled"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="note">
          These rows mirror the seeded demo configuration; the current backend
          does not expose key-management REST APIs.
        </p>
      </section>
    </>
  );
}
function Providers() {
  return (
    <>
      <Head
        title="Providers"
        desc="Provider topology represented by the current gateway."
      />
      <div className="grid2">
        <section className="panel">
          <Server />
          <h2>Alpha</h2>
          <p>alpha-small · alpha-large</p>
          <mark className="hit">Configured</mark>
        </section>
        <section className="panel">
          <Server />
          <h2>Beta</h2>
          <p>beta-small · beta-large</p>
          <mark className="hit">Configured</mark>
        </section>
      </div>
    </>
  );
}
function Routing() {
  return (
    <>
      <Head title="Routing" desc="Semantic routing modes supported by Prism." />
      <div className="routegrid">
        {[
          ["FAST", Zap, "Low-latency/general requests"],
          ["SMART", Sparkles, "Complex reasoning and technical requests"],
          ["AUTO", Network, "Gateway chooses the appropriate category"],
        ].map((x) => {
          const Icon = x[1];
          return (
            <section className="panel route" key={x[0]}>
              <Icon />
              <h2>{x[0]}</h2>
              <p>{x[2]}</p>
            </section>
          );
        })}
      </div>
      <section className="panel flow">
        <b>Request</b>
        <ArrowRight />
        <b>Semantic classifier</b>
        <ArrowRight />
        <b>Model/provider</b>
        <ArrowRight />
        <b>Fallback</b>
      </section>
    </>
  );
}
function Cache({ stats }) {
  return (
    <>
      <Head
        title="Semantic Cache"
        desc="Chroma performs semantic matching; Redis stores cached responses."
      />
      <div className="stats">
        <Card
          icon={Database}
          label="Hits"
          value={stats.h}
          detail="Semantic matches"
        />
        <Card
          icon={BarChart3}
          label="Hit rate"
          value={stats.rate + "%"}
          detail="This session"
        />
        <Card
          icon={Clock3}
          label="Latency"
          value={stats.lat + " ms"}
          detail="Average"
        />
      </div>
      <section className="panel flow">
        <b>Prompt</b>
        <ArrowRight />
        <b>Chroma embeddings</b>
        <ArrowRight />
        <b>Similarity match</b>
        <ArrowRight />
        <b>Redis response</b>
      </section>
    </>
  );
}
function Limits() {
  return (
    <>
      <Head
        title="Rate Limits"
        desc="Per-key RPM, TPM and budget protection."
      />
      <div className="routegrid">
        {[
          ["RPM", Gauge, "Requests per minute"],
          ["TPM", Activity, "Tokens per minute"],
          ["Budget", CircleDollarSign, "Monthly USD budget"],
        ].map((x) => {
          const Icon = x[1];
          return (
            <section className="panel route" key={x[0]}>
              <Icon />
              <h2>{x[0]}</h2>
              <p>{x[2]}</p>
            </section>
          );
        })}
      </div>
    </>
  );
}

export default App;
