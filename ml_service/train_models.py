"""
KrishiMandi AI — Model Training Script
=======================================
Trains Random Forest and Linear Regression models on
Agmarknet historical mandi price data.

Usage:
    python train_models.py --data data/agmarknet_prices.csv --output models/

Data format (CSV):
    date, crop, state, district, variety, min_price, max_price, modal_price
"""

import argparse
import os
import logging
import warnings
import numpy as np
import pandas as pd
import joblib
import json
from datetime import datetime
from typing import Tuple

from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor
from sklearn.linear_model import LinearRegression, Ridge, Lasso
from sklearn.model_selection import train_test_split, cross_val_score, TimeSeriesSplit
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer

warnings.filterwarnings("ignore")
logging.basicConfig(level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("train")


# ── Constants ──────────────────────────────────────────

FEATURES = [
    "current_price", "lag_7", "lag_14", "lag_30",
    "rolling_mean_7", "rolling_mean_14", "rolling_std_7",
    "month", "quarter", "day_of_week", "week_of_year",
    "season_code", "state_supply_index", "msp_ratio",
    "temperature", "rainfall", "humidity",
    "price_momentum", "price_acceleration"
]

TARGET = "modal_price_next_14d"   # predict 14-day forward price

MSP_2024 = {
    "RICE": 2183, "WHEAT": 2275, "MAIZE": 2090, "BAJRA": 2500,
    "JOWAR": 3180, "SUGARCANE": 3150, "MUSTARD": 5650,
    "GROUNDNUT": 6377, "SOYBEAN": 4600, "COTTON": 6620,
    "CHICKPEA": 5440, "LENTIL": 6000, "ONION": 0, "POTATO": 0, "TOMATO": 0
}

STATE_SUPPLY_IDX = {
    "PUNJAB": 0.85, "UTTAR PRADESH": 0.82, "MADHYA PRADESH": 0.84,
    "MAHARASHTRA": 0.86, "RAJASTHAN": 0.89, "KARNATAKA": 0.87,
    "ANDHRA PRADESH": 0.88, "BIHAR": 0.90, "WEST BENGAL": 0.87,
    "GUJARAT": 0.86, "HARYANA": 0.88, "TAMIL NADU": 0.88,
    "TELANGANA": 0.89, "ODISHA": 0.91, "KERALA": 0.93,
}

SEASON_MAP = {1: 1, 2: 1, 3: 2, 4: 2, 5: 2, 6: 3,
              7: 3, 8: 3, 9: 3, 10: 4, 11: 4, 12: 1}   # 1=Winter 2=Summer 3=Kharif 4=Rabi


# ── Data Preprocessing ────────────────────────────────

def load_data(csv_path: str) -> pd.DataFrame:
    logger.info(f"Loading data from {csv_path}")
    df = pd.read_csv(csv_path, parse_dates=["date"], low_memory=False)

    # Normalize columns
    df.columns = [c.strip().lower().replace(" ", "_") for c in df.columns]
    df["crop"]     = df["crop"].str.upper().str.strip()
    df["state"]    = df["state"].str.upper().str.strip()
    df["district"] = df["district"].str.upper().str.strip()

    # Use modal price as primary target
    for col in ["modal_price", "min_price", "max_price"]:
        if col not in df.columns:
            df[col] = np.nan

    df = df.dropna(subset=["modal_price", "date", "crop", "state"])
    df = df[df["modal_price"] > 100]  # remove bad data
    df = df.sort_values(["crop", "state", "date"]).reset_index(drop=True)

    logger.info(f"Loaded {len(df):,} rows | Crops: {df['crop'].nunique()} | States: {df['state'].nunique()}")
    return df


def generate_synthetic_data(n_rows: int = 50000) -> pd.DataFrame:
    """Generate realistic synthetic training data when real data is unavailable."""
    logger.info(f"Generating {n_rows:,} synthetic training samples...")

    crops  = list(MSP_2024.keys())
    states = list(STATE_SUPPLY_IDX.keys())
    rng    = np.random.default_rng(42)

    records = []
    start = datetime(2019, 1, 1)
    for _ in range(n_rows):
        crop   = rng.choice(crops)
        state  = rng.choice(states)
        msp    = MSP_2024[crop]
        base   = msp if msp > 0 else rng.uniform(1500, 6000)
        days_offset = int(rng.integers(0, 365 * 5))
        date   = start + pd.Timedelta(days=days_offset)
        month  = date.month

        seasonal_mult = SEASON_MAP[month] * 0.05 + 0.9
        noise  = rng.normal(1.0, 0.06)
        price  = base * seasonal_mult * noise

        records.append({
            "date":        date,
            "crop":        crop,
            "state":       state,
            "district":    "DISTRICT_X",
            "modal_price": round(price, 2),
            "min_price":   round(price * 0.93, 2),
            "max_price":   round(price * 1.07, 2),
            "temperature": rng.uniform(10, 42),
            "rainfall":    max(0, rng.normal(60, 50)),
            "humidity":    rng.uniform(30, 95),
        })

    df = pd.DataFrame(records)
    df = df.sort_values(["crop", "state", "date"]).reset_index(drop=True)
    logger.info("Synthetic data generated.")
    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    logger.info("Engineering features...")
    dfs = []

    for (crop, state), group in df.groupby(["crop", "state"]):
        g = group.sort_values("date").copy()

        # Lag features
        g["lag_7"]  = g["modal_price"].shift(7)
        g["lag_14"] = g["modal_price"].shift(14)
        g["lag_30"] = g["modal_price"].shift(30)

        # Rolling stats
        g["rolling_mean_7"]  = g["modal_price"].rolling(7, min_periods=3).mean()
        g["rolling_mean_14"] = g["modal_price"].rolling(14, min_periods=7).mean()
        g["rolling_std_7"]   = g["modal_price"].rolling(7, min_periods=3).std().fillna(0)

        # Momentum (rate of change)
        g["price_momentum"]     = g["modal_price"].pct_change(7).fillna(0)
        g["price_acceleration"] = g["price_momentum"].diff().fillna(0)

        # Target: price 14 days later
        g[TARGET] = g["modal_price"].shift(-14)

        dfs.append(g)

    result = pd.concat(dfs, ignore_index=True)

    # Calendar features
    result["month"]        = result["date"].dt.month
    result["quarter"]      = result["date"].dt.quarter
    result["day_of_week"]  = result["date"].dt.dayofweek
    result["week_of_year"] = result["date"].dt.isocalendar().week.astype(int)
    result["season_code"]  = result["month"].map(SEASON_MAP)

    # Domain features
    result["state_supply_index"] = result["state"].map(STATE_SUPPLY_IDX).fillna(0.89)
    result["msp_ratio"] = result.apply(
        lambda r: r["modal_price"] / MSP_2024[r["crop"]]
            if MSP_2024.get(r["crop"], 0) > 0 else 1.0, axis=1)

    # Current price alias
    result["current_price"] = result["modal_price"]

    # Fill weather with seasonal defaults if missing
    for col, default in [("temperature", 25.0), ("rainfall", 60.0), ("humidity", 60.0)]:
        if col not in result.columns:
            result[col] = default
        result[col] = result[col].fillna(default)

    # Drop rows with NaN targets
    result = result.dropna(subset=[TARGET] + ["lag_7", "lag_14", "lag_30"])
    logger.info(f"Feature engineering done: {len(result):,} samples")
    return result


# ── Model Training ────────────────────────────────────

def train_models(df: pd.DataFrame, output_dir: str) -> dict:
    os.makedirs(output_dir, exist_ok=True)

    X = df[FEATURES].values.astype(np.float32)
    y = df[TARGET].values.astype(np.float32)

    # Time-based split (respect temporal order)
    split_idx = int(len(X) * 0.85)
    X_train, X_test = X[:split_idx], X[split_idx:]
    y_train, y_test = y[:split_idx], y[split_idx:]
    logger.info(f"Train: {len(X_train):,} | Test: {len(X_test):,}")

    results = {}

    # ── 1. Linear Regression (Ridge) ─────────────────────
    logger.info("Training Ridge Regression...")
    lr_pipe = Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler",  StandardScaler()),
        ("model",   Ridge(alpha=1.0))
    ])
    lr_pipe.fit(X_train, y_train)
    lr_pred = lr_pipe.predict(X_test)
    lr_metrics = evaluate(y_test, lr_pred, "Ridge Regression")
    results["linear_regression"] = lr_metrics

    joblib.dump(lr_pipe, os.path.join(output_dir, "linear_regression.pkl"))
    logger.info(f"Saved linear_regression.pkl | MAE: ₹{lr_metrics['mae']:.2f}")

    # ── 2. Random Forest ─────────────────────────────────
    logger.info("Training Random Forest (n_estimators=200)...")
    rf_pipe = Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("model",   RandomForestRegressor(
            n_estimators    = 200,
            max_depth       = 15,
            min_samples_split = 10,
            min_samples_leaf  = 5,
            max_features    = "sqrt",
            n_jobs          = -1,
            random_state    = 42,
            oob_score       = True,
        ))
    ])
    rf_pipe.fit(X_train, y_train)
    rf_pred = rf_pipe.predict(X_test)
    rf_metrics = evaluate(y_test, rf_pred, "Random Forest")
    results["random_forest"] = rf_metrics

    # Feature importance
    fi = rf_pipe.named_steps["model"].feature_importances_
    importance = dict(sorted(
        zip(FEATURES, fi.tolist()), key=lambda x: x[1], reverse=True))

    joblib.dump(rf_pipe, os.path.join(output_dir, "random_forest.pkl"))
    logger.info(f"Saved random_forest.pkl | MAE: ₹{rf_metrics['mae']:.2f}")
    logger.info(f"OOB Score: {rf_pipe.named_steps['model'].oob_score_:.4f}")

    # ── 3. Gradient Boosting (XGBoost-like) ─────────────
    logger.info("Training Gradient Boosting Regressor...")
    gb_pipe = Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("model",   GradientBoostingRegressor(
            n_estimators = 300,
            max_depth    = 5,
            learning_rate= 0.05,
            subsample    = 0.8,
            random_state = 42,
        ))
    ])
    gb_pipe.fit(X_train, y_train)
    gb_pred = gb_pipe.predict(X_test)
    gb_metrics = evaluate(y_test, gb_pred, "Gradient Boosting")
    results["gradient_boosting"] = gb_metrics

    joblib.dump(gb_pipe, os.path.join(output_dir, "gradient_boosting.pkl"))
    logger.info(f"Saved gradient_boosting.pkl | MAE: ₹{gb_metrics['mae']:.2f}")

    # ── Save metadata ─────────────────────────────────────
    metadata = {
        "trained_at": datetime.now().isoformat(),
        "n_samples_train": int(len(X_train)),
        "n_samples_test": int(len(X_test)),
        "features": FEATURES,
        "target": TARGET,
        "metrics": results,
        "feature_importance": importance,
    }
    with open(os.path.join(output_dir, "model_metadata.json"), "w") as f:
        json.dump(metadata, f, indent=2)
    logger.info("Saved model_metadata.json")

    return results


def evaluate(y_true: np.ndarray, y_pred: np.ndarray, name: str) -> dict:
    mae  = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    r2   = r2_score(y_true, y_pred)
    mape = np.mean(np.abs((y_true - y_pred) / np.maximum(y_true, 1))) * 100

    logger.info(f"[{name}] MAE=₹{mae:.2f} | RMSE=₹{rmse:.2f} | R²={r2:.4f} | MAPE={mape:.2f}%")
    return {"mae": round(mae, 2), "rmse": round(rmse, 2),
            "r2": round(r2, 4), "mape": round(mape, 2)}


# ── Entry Point ────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="KrishiMandi ML Model Trainer")
    parser.add_argument("--data",   default="data/agmarknet_prices.csv",
                        help="Path to Agmarknet CSV dataset")
    parser.add_argument("--output", default="models/",
                        help="Directory to save trained models")
    parser.add_argument("--synthetic", action="store_true",
                        help="Use synthetic data (when real data unavailable)")
    args = parser.parse_args()

    logger.info("=" * 55)
    logger.info("  KrishiMandi AI — Model Training Pipeline")
    logger.info("=" * 55)

    # Load or generate data
    if args.synthetic or not os.path.exists(args.data):
        logger.warning("Real data not found — using synthetic data for demo.")
        df = generate_synthetic_data()
    else:
        df = load_data(args.data)

    # Engineer features
    df = engineer_features(df)

    # Train all models
    metrics = train_models(df, args.output)

    logger.info("=" * 55)
    logger.info("  Training Complete!")
    for model, m in metrics.items():
        logger.info(f"  {model}: MAE=₹{m['mae']} | R²={m['r2']} | MAPE={m['mape']}%")
    logger.info(f"  Models saved to: {args.output}")
    logger.info("=" * 55)


if __name__ == "__main__":
    main()
