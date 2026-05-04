import { useEffect, useMemo, useState } from "react";
import { Activity, AlertTriangle, ArrowRight, BarChart3, CheckCircle2, Gauge, History, Leaf, LineChart, Loader2, LogIn, LogOut, RefreshCw, Search, ShieldCheck, Sprout, TrendingDown, TrendingUp, UserPlus } from "lucide-react";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
const STATES = ["Punjab", "Haryana", "Uttar Pradesh", "Madhya Pradesh", "Rajasthan", "Maharashtra", "West Bengal", "Gujarat", "Karnataka", "Tamil Nadu", "Andhra Pradesh", "Telangana", "Bihar", "Odisha"];
const DISTRICTS = {
  Punjab: ["Amritsar", "Ludhiana", "Patiala", "Jalandhar"],
  Haryana: ["Karnal", "Hisar", "Rohtak", "Ambala"],
  "Uttar Pradesh": ["Lucknow", "Kanpur", "Varanasi", "Meerut"],
  Maharashtra: ["Nashik", "Pune", "Nagpur", "Aurangabad"],
  "West Bengal": ["Kolkata", "Burdwan", "Hooghly", "Siliguri"],
  Gujarat: ["Ahmedabad", "Rajkot", "Surat", "Junagadh"],
  Karnataka: ["Bengaluru", "Mysuru", "Dharwad", "Belagavi"],
  Rajasthan: ["Jaipur", "Kota", "Jodhpur", "Bikaner"]
};

const price = (v) => v === null || v === undefined || Number.isNaN(Number(v)) ? "--" : `Rs ${Math.round(Number(v)).toLocaleString("en-IN")}`;
const when = (v) => {
  if (!v) return "--";
  const d = Array.isArray(v) ? new Date(v[0], v[1] - 1, v[2], v[3] || 0, v[4] || 0) : new Date(v);
  return Number.isNaN(d.getTime()) ? String(v).slice(0, 16) : d.toLocaleString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
};

function useApi(token, logout) {
  return useMemo(() => async (path, options = {}) => {
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetch(`${API_URL}${path}`, { ...options, headers });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (res.status === 401) logout?.();
    if (!res.ok) throw new Error(data?.error || data?.message || `Request failed: ${res.status}`);
    return data;
  }, [token, logout]);
}

function AuthPanel({ onAuth }) {
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ name: "Demo Farmer", email: "demo@farm.com", password: "demo123", state: "Punjab", language: "en" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault(); setBusy(true); setError("");
    try {
      const payload = mode === "login" ? { email: form.email, password: form.password } : form;
      const res = await fetch(`${API_URL}/api/auth/${mode === "login" ? "login" : "signup"}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || data.message || "Authentication failed");
      onAuth(data);
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }
  return <main className="auth-screen">
    <section className="auth-panel">
      <div className="brand-lockup"><div className="brand-mark"><Sprout size={28} /></div><div><p className="eyebrow">KrishiMandi AI</p><h1>Crop Price Workspace</h1></div></div>
      <div className="auth-tabs"><button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} type="button"><LogIn size={16}/>Login</button><button className={mode === "signup" ? "active" : ""} onClick={() => setMode("signup")} type="button"><UserPlus size={16}/>Sign up</button></div>
      <form onSubmit={submit} className="auth-form">
        {mode === "signup" && <label>Name<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>}
        <label>Email<input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></label>
        <label>Password<input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required /></label>
        {mode === "signup" && <div className="form-grid two"><label>State<select value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })}>{STATES.map((s) => <option key={s}>{s}</option>)}</select></label><label>Language<select value={form.language} onChange={(e) => setForm({ ...form, language: e.target.value })}><option value="en">English</option><option value="hi">Hindi</option><option value="bn">Bengali</option></select></label></div>}
        {error && <div className="alert error"><AlertTriangle size={16}/>{error}</div>}
        <button className="primary full" disabled={busy}>{busy ? <Loader2 className="spin" size={16}/> : mode === "login" ? <LogIn size={16}/> : <UserPlus size={16}/>}{mode === "login" ? "Login" : "Create Account"}</button>
      </form>
    </section>
    <aside className="status-strip"><div><ShieldCheck size={18}/>JWT secured API</div><div><Activity size={18}/>FastAPI ML service</div><div><LineChart size={18}/>Daily forecast curve</div></aside>
  </main>;
}

function Metric({ icon, label, value, tone = "" }) { return <article className={`metric ${tone}`}><div className="metric-icon">{icon}</div><p>{label}</p><strong>{value}</strong></article>; }

function ForecastChart({ points = [] }) {
  const data = points.filter((p) => Number.isFinite(Number(p.price))).slice(0, 30);
  if (!data.length) return <div className="empty-chart">No forecast yet</div>;
  const w = 720, h = 240, pad = 28, vals = data.map((p) => Number(p.price));
  const min = Math.min(...vals), max = Math.max(...vals), span = Math.max(max - min, 1);
  const coords = data.map((p, i) => ({ x: pad + (i / Math.max(data.length - 1, 1)) * (w - pad * 2), y: h - pad - ((Number(p.price) - min) / span) * (h - pad * 2), p }));
  const line = coords.map((c) => `${c.x},${c.y}`).join(" ");
  return <svg className="forecast-chart" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Daily price forecast">
    <defs><linearGradient id="fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#2f9e44" stopOpacity="0.28"/><stop offset="1" stopColor="#2f9e44" stopOpacity="0.02"/></linearGradient></defs>
    {[0,1,2,3].map((t) => <line key={t} x1={pad} x2={w-pad} y1={pad+t*48} y2={pad+t*48} className="gridline"/>)}
    <polygon points={`${pad},${h-pad} ${line} ${w-pad},${h-pad}`} fill="url(#fill)"/><polyline points={line} className="chart-line" fill="none"/>
    {coords.map((c, i) => <circle key={i} cx={c.x} cy={c.y} r="4" className="chart-dot"><title>{`Day ${c.p.day}: ${price(c.p.price)}`}</title></circle>)}
    <text x={pad} y="18" className="axis-label">{price(max)}</text><text x={pad} y={h-pad-4} className="axis-label">{price(min)}</text>
  </svg>;
}

function FactorBars({ factors = {} }) {
  const items = Object.entries(factors || {});
  return items.length ? <div className="factor-list">{items.map(([k, v]) => <div className="factor" key={k}><span>{k.replace(/([A-Z])/g, " $1")}</span><div className="bar"><i style={{ width: `${Math.min(Number(v) * 10, 100)}%` }}/></div><b>{Number(v).toFixed(1)}</b></div>)}</div> : null;
}

function Dashboard({ session, logout }) {
  const api = useApi(session.token, logout);
  const [crops, setCrops] = useState([]), [history, setHistory] = useState([]), [analytics, setAnalytics] = useState(null), [market, setMarket] = useState({});
  const [result, setResult] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [form, setForm] = useState({ cropName: "Wheat", state: session.state || "Punjab", district: "Amritsar", predictionRangeDays: 14, modelPreference: "ensemble" });
  async function refresh() {
    setError("");
    try {
      const [cropData, historyData, analyticsData, marketData] = await Promise.all([
        api("/api/predict/crops"), api("/api/predict/history?size=8"), api("/api/predict/analytics"), api(`/api/predict/market?crop=${encodeURIComponent(form.cropName)}&state=${encodeURIComponent(form.state)}`)
      ]);
      setCrops(cropData || []); setHistory(historyData?.content || []); setAnalytics(analyticsData || null); setMarket(marketData || {});
    } catch (err) { setError(err.message); }
  }
  useEffect(() => { refresh(); }, []);
  useEffect(() => { const d = DISTRICTS[form.state] || [form.state]; if (!d.includes(form.district)) setForm((f) => ({ ...f, district: d[0] })); }, [form.state]);
  async function predict(e) {
    e.preventDefault(); setBusy(true); setError("");
    try { const data = await api("/api/predict", { method: "POST", body: JSON.stringify(form) }); setResult(data); await refresh(); }
    catch (err) { setError(err.message); } finally { setBusy(false); }
  }
  const marketInfo = market?.[form.cropName];
  const trendIcon = result?.trend === "falling" ? <TrendingDown size={18}/> : <TrendingUp size={18}/>;
  return <main className="app-shell">
    <aside className="sidebar"><div className="brand-lockup compact"><div className="brand-mark"><Leaf size={24}/></div><div><p className="eyebrow">KrishiMandi AI</p><h1>Mandi Console</h1></div></div><nav className="nav-list"><a href="#predict"><Search size={17}/>Prediction</a><a href="#forecast"><LineChart size={17}/>Forecast</a><a href="#history"><History size={17}/>History</a><a href="#market"><BarChart3 size={17}/>Market</a></nav><button className="ghost full" onClick={logout}><LogOut size={16}/>Logout</button></aside>
    <section className="workspace"><header className="topbar"><div><p className="eyebrow">Signed in as {session.name || session.email}</p><h2>Price prediction dashboard</h2></div><button className="ghost" onClick={refresh}><RefreshCw size={16}/>Refresh</button></header>{error && <div className="alert error"><AlertTriangle size={16}/>{error}</div>}
      <section className="metrics-grid"><Metric icon={<Gauge size={20}/>} label="Predictions" value={analytics?.totalPredictions ?? 0}/><Metric icon={<CheckCircle2 size={20}/>} label="Model Accuracy" value={`${analytics?.modelAccuracy ?? 0}%`} tone="blue"/><Metric icon={trendIcon} label="Latest Trend" value={result?.trend || "Ready"} tone="amber"/><Metric icon={<Activity size={20}/>} label="Confidence" value={result ? `${result.confidenceScore}%` : "--"} tone="green"/></section>
      <section className="main-grid"><form id="predict" className="panel prediction-panel" onSubmit={predict}><div className="panel-head"><h3>New Prediction</h3><span>{marketInfo ? `${price(marketInfo.price)} / quintal` : "Live model"}</span></div><div className="form-grid"><label>Crop<select value={form.cropName} onChange={(e)=>setForm({...form,cropName:e.target.value})}>{(crops.length ? crops : [{name:"Wheat"},{name:"Rice"},{name:"Tomato"}]).map((c)=><option key={c.name}>{c.name}</option>)}</select></label><label>State<select value={form.state} onChange={(e)=>setForm({...form,state:e.target.value})}>{STATES.map((s)=><option key={s}>{s}</option>)}</select></label><label>District<select value={form.district} onChange={(e)=>setForm({...form,district:e.target.value})}>{(DISTRICTS[form.state] || [form.state]).map((d)=><option key={d}>{d}</option>)}</select></label><label>Range<select value={form.predictionRangeDays} onChange={(e)=>setForm({...form,predictionRangeDays:Number(e.target.value)})}><option value={7}>7 days</option><option value={14}>14 days</option><option value={30}>30 days</option></select></label></div><button className="primary" disabled={busy}>{busy ? <Loader2 className="spin" size={16}/> : <ArrowRight size={16}/>}Predict Price</button></form>
        <article className="panel result-panel"><div className="panel-head"><h3>Result</h3><span>{result ? when(result.generatedAt) : "No prediction yet"}</span></div>{result ? <><div className="result-price"><span>{result.cropName} in {result.state}</span><strong>{price(result.predictedPrice)}</strong><small>{price(result.priceRange?.low)} - {price(result.priceRange?.high)}</small></div><div className="recommendation">{result.recommendation}</div><FactorBars factors={result.factorScores}/></> : <div className="empty-state">Run a prediction to see price, confidence, and selling window.</div>}</article></section>
      <section id="forecast" className="panel wide-panel"><div className="panel-head"><h3>Daily Forecast</h3><span>{result?.modelUsed || "Waiting for prediction"}</span></div><ForecastChart points={result?.dailyForecast || []}/></section>
      <section className="bottom-grid"><article id="history" className="panel"><div className="panel-head"><h3>Recent History</h3><History size={18}/></div><div className="history-list">{history.length ? history.map((p)=><div className="history-row" key={p.id || p.predictionId}><span>{p.cropName}</span><b>{price(p.predictedPrice)}</b><small>{p.state} - {when(p.createdAt || p.generatedAt)}</small></div>) : <div className="empty-state small">No saved predictions yet.</div>}</div></article><article id="market" className="panel"><div className="panel-head"><h3>Market Snapshot</h3><BarChart3 size={18}/></div><div className="market-list">{Object.entries(market).slice(0,6).map(([crop, info])=><div className="market-row" key={crop}><span>{crop}</span><b>{price(info.price)}</b><small>{info.season}</small></div>)}{!Object.keys(market).length && <div className="empty-state small">Market data will appear after refresh.</div>}</div></article></section>
    </section>
  </main>;
}

export default function App() {
  const [session, setSession] = useState(() => { const raw = localStorage.getItem("krishimandi.session"); return raw ? JSON.parse(raw) : null; });
  const auth = (data) => { localStorage.setItem("krishimandi.session", JSON.stringify(data)); setSession(data); };
  const logout = () => { localStorage.removeItem("krishimandi.session"); setSession(null); };
  return session?.token ? <Dashboard session={session} logout={logout}/> : <AuthPanel onAuth={auth}/>;
}