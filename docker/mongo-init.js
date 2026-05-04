// ═══════════════════════════════════════════════════════
// MongoDB Initialization Script
// Runs once on first container startup
// ═══════════════════════════════════════════════════════

db = db.getSiblingDB('krishimandi');

// ─── Create Collections ──────────────────────────────
db.createCollection('users');
db.createCollection('predictions');
db.createCollection('market_prices');
db.createCollection('crop_metadata');

// ─── Users: Indexes ──────────────────────────────────
db.users.createIndex({ email: 1 }, { unique: true });
db.users.createIndex({ createdAt: -1 });
db.users.createIndex({ state: 1, alertsEnabled: 1 });

// ─── Predictions: Indexes ────────────────────────────
db.predictions.createIndex({ userId: 1, createdAt: -1 });
db.predictions.createIndex({ cropName: 1, state: 1, createdAt: -1 });
db.predictions.createIndex({ createdAt: -1 });

// TTL: Auto-expire cached predictions after 24h
db.predictions.createIndex(
  { createdAt: 1 },
  { expireAfterSeconds: 86400, partialFilterExpression: { cached: true } }
);

// ─── Market Prices: Indexes ──────────────────────────
db.market_prices.createIndex({ cropName: 1, state: 1, date: -1 });
db.market_prices.createIndex({ date: -1 });

// ─── Seed: Crop Metadata ─────────────────────────────
db.crop_metadata.insertMany([
  { name: "Rice",      season: "Kharif", msp: 2183, unit: "₹/quintal", icon: "🌾", states: ["Punjab","West Bengal","Odisha","Andhra Pradesh","Telangana"] },
  { name: "Wheat",     season: "Rabi",   msp: 2275, unit: "₹/quintal", icon: "🌿", states: ["Punjab","Haryana","Uttar Pradesh","Madhya Pradesh","Rajasthan"] },
  { name: "Potato",    season: "Rabi",   msp: 0,    unit: "₹/quintal", icon: "🥔", states: ["Uttar Pradesh","West Bengal","Bihar","Gujarat"] },
  { name: "Onion",     season: "Kharif", msp: 0,    unit: "₹/quintal", icon: "🧅", states: ["Maharashtra","Madhya Pradesh","Karnataka","Rajasthan"] },
  { name: "Tomato",    season: "All",    msp: 0,    unit: "₹/quintal", icon: "🍅", states: ["Andhra Pradesh","Karnataka","Maharashtra","Uttar Pradesh"] },
  { name: "Maize",     season: "Kharif", msp: 2090, unit: "₹/quintal", icon: "🌽", states: ["Karnataka","Andhra Pradesh","Rajasthan","Madhya Pradesh"] },
  { name: "Soybean",   season: "Kharif", msp: 4600, unit: "₹/quintal", icon: "🫘", states: ["Madhya Pradesh","Maharashtra","Rajasthan"] },
  { name: "Cotton",    season: "Kharif", msp: 6620, unit: "₹/quintal", icon: "☁️", states: ["Gujarat","Maharashtra","Telangana","Punjab"] },
  { name: "Mustard",   season: "Rabi",   msp: 5650, unit: "₹/quintal", icon: "🌼", states: ["Rajasthan","Uttar Pradesh","Haryana","Madhya Pradesh"] },
  { name: "Chickpea",  season: "Rabi",   msp: 5440, unit: "₹/quintal", icon: "🫘", states: ["Madhya Pradesh","Rajasthan","Maharashtra","Uttar Pradesh"] },
  { name: "Lentil",    season: "Rabi",   msp: 6000, unit: "₹/quintal", icon: "🫘", states: ["Madhya Pradesh","Uttar Pradesh","Bihar","Rajasthan"] },
  { name: "Groundnut", season: "Kharif", msp: 6377, unit: "₹/quintal", icon: "🥜", states: ["Gujarat","Andhra Pradesh","Tamil Nadu","Karnataka"] },
  { name: "Bajra",     season: "Kharif", msp: 2500, unit: "₹/quintal", icon: "🌾", states: ["Rajasthan","Uttar Pradesh","Haryana","Gujarat"] },
  { name: "Jowar",     season: "Kharif", msp: 3180, unit: "₹/quintal", icon: "🌾", states: ["Maharashtra","Karnataka","Andhra Pradesh","Madhya Pradesh"] },
  { name: "Sugarcane", season: "All",    msp: 3150, unit: "₹/quintal", icon: "🎋", states: ["Uttar Pradesh","Maharashtra","Karnataka","Tamil Nadu"] },
]);

// ─── Seed: Demo User ─────────────────────────────────
// Password: demo123 → BCrypt hash
db.users.insertOne({
  _id: ObjectId(),
  name: "Demo Farmer",
  email: "demo@farm.com",
  password: "$2a$12$eybktGOB6UN57Wc2amBCWevdx4kVSqhrXhZUIy6eYye3CSUwPxzam",
  role: "FARMER",
  state: "Punjab",
  language: "en",
  alertsEnabled: true,
  watchlist: ["Wheat", "Rice", "Potato"],
  createdAt: new Date(),
  totalPredictions: 0
});

// ─── Seed: Sample Market Prices (last 7 days) ────────
const crops = ["Wheat","Rice","Potato","Onion","Tomato"];
const bases  = [2400, 2100, 1200, 2800, 3500];
const states = ["Punjab","Maharashtra","Uttar Pradesh"];

crops.forEach((crop, ci) => {
  states.forEach(state => {
    for (let d = 6; d >= 0; d--) {
      const date = new Date();
      date.setDate(date.getDate() - d);
      const price = bases[ci] * (0.95 + Math.random() * 0.1);
      db.market_prices.insertOne({
        cropName: crop, state: state,
        modalPrice: Math.round(price),
        minPrice: Math.round(price * 0.93),
        maxPrice: Math.round(price * 1.07),
        unit: "₹/quintal",
        date: date,
        source: "seed"
      });
    }
  });
});

print("✅ KrishiMandi DB initialized — collections, indexes, and seed data created.");
