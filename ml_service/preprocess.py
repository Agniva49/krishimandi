"""
KrishiMandi AI — Dataset Preprocessing
=======================================
Cleans and normalizes raw Agmarknet mandi price data.

Agmarknet CSV format (downloaded from data.gov.in):
    State,District,Market,Commodity,Variety,Grade,Arrival_Date,
    Min_x0020_Price,Max_x0020_Price,Modal_x0020_Price

Usage:
    python preprocess.py --input data/raw/ --output data/agmarknet_prices.csv
"""

import os
import glob
import logging
import argparse
import numpy as np
import pandas as pd
from pathlib import Path

logging.basicConfig(level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("preprocess")

# ── Column mapping (Agmarknet raw names → normalized) ──
COLUMN_MAP = {
    "state":              "state",
    "state name":         "state",
    "district":           "district",
    "district name":      "district",
    "market":             "market",
    "commodity":          "crop",
    "commodity name":     "crop",
    "variety":            "variety",
    "grade":              "grade",
    "arrival_date":       "date",
    "arrivals_in_qtl":    "arrivals_qtl",
    "min_x0020_price":    "min_price",
    "max_x0020_price":    "max_price",
    "modal_x0020_price":  "modal_price",
    "min price":          "min_price",
    "max price":          "max_price",
    "modal price":        "modal_price",
}

# ── Crop name standardization ──
CROP_ALIASES = {
    "PADDY": "RICE", "PADDY(DUSHEN)": "RICE",
    "WHEAT(SHARBATI)": "WHEAT", "WHEAT(LOK 1)": "WHEAT",
    "POTATO(DESI)": "POTATO", "POTATO(HYBRID)": "POTATO",
    "ONION(DESI)": "ONION", "ONION(HYBRID)": "ONION",
    "TOMATO(DESI)": "TOMATO", "TOMATO(LOCAL)": "TOMATO",
    "GROUNDNUT(SHELL)": "GROUNDNUT", "SOYABEAN": "SOYBEAN",
    "MOONG(WHOLE)": "GREENGRAM", "URAD(WHOLE)": "BLACKGRAM",
    "ARHAR(TUR)": "PIGEONPEA", "GRAM(WHOLE)": "CHICKPEA",
    "LENTIL(MASUR)": "LENTIL",
}

TARGET_CROPS = {
    "RICE", "WHEAT", "POTATO", "ONION", "TOMATO", "MAIZE",
    "SOYBEAN", "COTTON", "SUGARCANE", "MUSTARD", "GROUNDNUT",
    "CHICKPEA", "LENTIL", "BAJRA", "JOWAR"
}

VALID_STATES = {
    "ANDHRA PRADESH", "BIHAR", "GUJARAT", "HARYANA", "KARNATAKA",
    "KERALA", "MADHYA PRADESH", "MAHARASHTRA", "ODISHA", "PUNJAB",
    "RAJASTHAN", "TAMIL NADU", "TELANGANA", "UTTAR PRADESH", "WEST BENGAL"
}

# ── Seasonal weather lookup (avg per state/month) ──
WEATHER_LOOKUP = {
    ("PUNJAB",         1): (10, 20, 70), ("PUNJAB",         7): (32, 120, 80),
    ("MAHARASHTRA",    6): (28, 200, 85),("MAHARASHTRA",    12):(22,  5,  55),
    ("UTTAR PRADESH",  5): (40,  15, 35),("UTTAR PRADESH",  8): (32, 180, 85),
    # Default fallback handled in code
}


def load_raw_files(input_dir: str) -> pd.DataFrame:
    """Load and concatenate all CSVs from input directory."""
    pattern = os.path.join(input_dir, "**", "*.csv")
    files   = glob.glob(pattern, recursive=True)
    logger.info(f"Found {len(files)} CSV files in {input_dir}")

    if not files:
        logger.warning("No CSV files found. Run with --demo to generate sample data.")
        return pd.DataFrame()

    dfs = []
    for f in files:
        try:
            df = pd.read_csv(f, encoding="utf-8", low_memory=False)
            df["source_file"] = Path(f).name
            dfs.append(df)
            logger.debug(f"Loaded {len(df):,} rows from {f}")
        except UnicodeDecodeError:
            try:
                df = pd.read_csv(f, encoding="latin1", low_memory=False)
                dfs.append(df)
            except Exception as e:
                logger.warning(f"Skipping {f}: {e}")

    combined = pd.concat(dfs, ignore_index=True)
    logger.info(f"Total raw rows: {len(combined):,}")
    return combined


def normalize_columns(df: pd.DataFrame) -> pd.DataFrame:
    """Rename columns to standard names."""
    df.columns = [c.strip().lower().replace("  ", " ") for c in df.columns]
    rename = {k: v for k, v in COLUMN_MAP.items() if k in df.columns}
    df = df.rename(columns=rename)
    logger.info(f"Columns after normalization: {list(df.columns)}")
    return df


def clean_prices(df: pd.DataFrame) -> pd.DataFrame:
    """Clean price columns: remove commas, convert to float, drop invalid."""
    for col in ["min_price", "max_price", "modal_price"]:
        if col in df.columns:
            df[col] = (df[col].astype(str)
                               .str.replace(",", "")
                               .str.strip()
                               .replace("", np.nan)
                               .astype(float))

    # Ensure modal_price exists
    if "modal_price" not in df.columns:
        if "min_price" in df.columns and "max_price" in df.columns:
            df["modal_price"] = (df["min_price"] + df["max_price"]) / 2
        else:
            raise ValueError("No price columns found in dataset")

    # Remove zero / negative / extreme prices
    df = df[df["modal_price"] > 100]
    df = df[df["modal_price"] < 1_000_000]

    # Outlier removal using IQR per crop
    def remove_outliers(group):
        q1, q3 = group["modal_price"].quantile([0.01, 0.99])
        return group[(group["modal_price"] >= q1) & (group["modal_price"] <= q3)]

    if "crop" in df.columns:
        df = df.groupby("crop", group_keys=False).apply(remove_outliers)

    return df


def standardize_crops(df: pd.DataFrame) -> pd.DataFrame:
    """Normalize crop names and filter to target crops."""
    df["crop"] = df["crop"].str.upper().str.strip()
    df["crop"] = df["crop"].map(lambda x: CROP_ALIASES.get(x, x))
    df = df[df["crop"].isin(TARGET_CROPS)]
    logger.info(f"After crop filter: {len(df):,} rows | Crops: {df['crop'].unique().tolist()}")
    return df


def standardize_states(df: pd.DataFrame) -> pd.DataFrame:
    """Normalize state names."""
    df["state"] = df["state"].str.upper().str.strip()
    df["state"] = df["state"].str.replace(r"\s+", " ", regex=True)

    # Common abbreviation fixes
    state_fixes = {
        "UP": "UTTAR PRADESH", "MP": "MADHYA PRADESH",
        "AP": "ANDHRA PRADESH", "WB": "WEST BENGAL",
        "TN": "TAMIL NADU",
    }
    df["state"] = df["state"].map(lambda x: state_fixes.get(x, x))
    df = df[df["state"].isin(VALID_STATES)]
    return df


def parse_dates(df: pd.DataFrame) -> pd.DataFrame:
    """Parse date column with multiple format attempts."""
    date_formats = ["%d/%m/%Y", "%Y-%m-%d", "%d-%m-%Y", "%d %b %Y", "%Y/%m/%d"]
    for fmt in date_formats:
        try:
            df["date"] = pd.to_datetime(df["date"], format=fmt, errors="coerce")
            valid = df["date"].notna().sum()
            if valid > len(df) * 0.8:
                logger.info(f"Parsed dates with format: {fmt} ({valid:,} valid)")
                break
        except Exception:
            continue

    df = df.dropna(subset=["date"])
    df = df[(df["date"] >= "2015-01-01") & (df["date"] <= pd.Timestamp.today())]
    return df


def add_weather_features(df: pd.DataFrame) -> pd.DataFrame:
    """Add monthly average weather as proxy features."""
    df["month"] = df["date"].dt.month

    def get_weather(row):
        key = (row["state"], row["month"])
        if key in WEATHER_LOOKUP:
            return pd.Series(WEATHER_LOOKUP[key], index=["temperature","rainfall","humidity"])
        # Seasonal defaults
        defaults = {
            range(3, 6):  (35.0, 20.0, 40.0),
            range(6, 10): (30.0, 150.0, 80.0),
            range(10, 12):(22.0, 30.0, 65.0),
        }
        for months, vals in defaults.items():
            if row["month"] in months:
                return pd.Series(vals, index=["temperature","rainfall","humidity"])
        return pd.Series([18.0, 10.0, 60.0], index=["temperature","rainfall","humidity"])

    weather = df.apply(get_weather, axis=1)
    df = pd.concat([df, weather], axis=1)
    return df


def aggregate_to_daily(df: pd.DataFrame) -> pd.DataFrame:
    """Aggregate multiple arrivals per day to single daily price."""
    agg_cols = {
        "modal_price": ["mean", "min", "max"],
        "temperature": "first",
        "rainfall":    "first",
        "humidity":    "first",
    }
    if "arrivals_qtl" in df.columns:
        agg_cols["arrivals_qtl"] = "sum"

    agg = df.groupby(["date", "crop", "state"]).agg(agg_cols).reset_index()
    agg.columns = ["_".join(c).strip("_") for c in agg.columns]
    agg = agg.rename(columns={"modal_price_mean": "modal_price"})
    return agg


def generate_demo_csv(output_path: str, n: int = 5000):
    """Generate a small demo CSV for testing the pipeline."""
    import random
    from datetime import timedelta

    crops  = list(TARGET_CROPS)
    states = list(VALID_STATES)
    base   = {"RICE":2100,"WHEAT":2400,"POTATO":1200,"ONION":2800,"TOMATO":3500,
               "MAIZE":1900,"SOYBEAN":4200,"COTTON":6500,"MUSTARD":5100,
               "GROUNDNUT":5800,"CHICKPEA":5400,"LENTIL":7200,
               "BAJRA":2200,"JOWAR":2900,"SUGARCANE":3200}
    rows = []
    start = pd.Timestamp("2019-01-01")
    rng = np.random.default_rng(42)

    for _ in range(n):
        crop  = rng.choice(crops)
        state = rng.choice(states)
        bp    = base.get(crop, 2000)
        d = start + pd.Timedelta(days=int(rng.integers(0, 365*5)))
        price = bp * rng.uniform(0.88, 1.12)
        rows.append({
            "State": state.title(), "District": "Sample District",
            "Market": "Sample Market", "Commodity": crop.title(),
            "Variety": "Local", "Grade": "FAQ",
            "Arrival_Date": d.strftime("%d/%m/%Y"),
            "Min_x0020_Price": round(price * 0.93, 2),
            "Max_x0020_Price": round(price * 1.07, 2),
            "Modal_x0020_Price": round(price, 2),
        })

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    pd.DataFrame(rows).to_csv(output_path, index=False)
    logger.info(f"Demo CSV saved: {output_path} ({n} rows)")


# ── Main Pipeline ──────────────────────────────────────

def preprocess(input_dir: str, output_path: str, demo: bool = False):
    logger.info("=" * 55)
    logger.info("  KrishiMandi — Data Preprocessing Pipeline")
    logger.info("=" * 55)

    if demo:
        demo_path = os.path.join(input_dir, "demo_agmarknet.csv")
        generate_demo_csv(demo_path, n=10000)

    df = load_raw_files(input_dir)
    if df.empty:
        logger.error("No data loaded. Exiting.")
        return

    df = normalize_columns(df)
    df = clean_prices(df)
    df = standardize_crops(df)
    df = standardize_states(df)
    df = parse_dates(df)
    df = add_weather_features(df)
    df = aggregate_to_daily(df)

    df = df.sort_values(["crop", "state", "date"]).reset_index(drop=True)
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    df.to_csv(output_path, index=False)

    logger.info("=" * 55)
    logger.info(f"  Output: {output_path}")
    logger.info(f"  Rows:   {len(df):,}")
    logger.info(f"  Crops:  {df['crop'].nunique()} | States: {df['state'].nunique()}")
    logger.info(f"  Date range: {df['date'].min().date()} → {df['date'].max().date()}")
    logger.info("=" * 55)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input",  default="data/raw/",    help="Raw CSV directory")
    parser.add_argument("--output", default="data/agmarknet_prices.csv", help="Output CSV")
    parser.add_argument("--demo",   action="store_true",    help="Generate demo data")
    args = parser.parse_args()

    preprocess(args.input, args.output, demo=args.demo)
