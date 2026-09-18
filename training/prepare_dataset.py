"""
数据准备：清洗 / 去重 / 类别均衡 / 训练-验证-测试划分
用法:
    python prepare_dataset.py --input data/raw/labeled.jsonl --output data/processed/
"""
import argparse
import json
import random
from collections import Counter
from pathlib import Path

import pandas as pd

LABELS = ["CHAT", "CONSULT", "RISK"]
VALID_RATIO = 0.1
TEST_RATIO = 0.1
SEED = 42


def load_jsonl(path: Path) -> pd.DataFrame:
    records = []
    with open(path, encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
                records.append(obj)
            except json.JSONDecodeError as e:
                print(f"跳过第 {line_no} 行（JSON 解析失败）: {e}")
    return pd.DataFrame(records)


def clean(df: pd.DataFrame) -> pd.DataFrame:
    """基本清洗：去空、截断、标签规范化"""
    df = df.copy()
    df["text"] = df["text"].astype(str).str.strip()
    # 去掉过短（信息量不足）和过长（噪声/超长粘贴）的样本
    df = df[(df["text"].str.len() >= 4) & (df["text"].str.len() <= 512)]
    # 标签规范化为大写，过滤非法标签
    df["label"] = df["label"].astype(str).str.strip().str.upper()
    df = df[df["label"].isin(LABELS)]
    return df.reset_index(drop=True)


def dedup(df: pd.DataFrame) -> pd.DataFrame:
    """按文本去重；同文本不同标签的冲突样本全部丢弃"""
    dup_mask = df.duplicated("text", keep=False)
    conflicts = df[dup_mask].groupby("text")["label"].nunique()
    conflict_texts = set(conflicts[conflicts > 1].index)
    before = len(df)
    df = df[~df["text"].isin(conflict_texts)]          # 丢弃标签冲突样本
    df = df.drop_duplicates("text", keep="first")       # 同标签重复保留一条
    print(f"去重: {before} -> {len(df)}（丢弃 {before - len(df)} 条，其中标签冲突 {len(conflict_texts)} 组）")
    return df.reset_index(drop=True)


def balance(df: pd.DataFrame, strategy: str = "downsample") -> pd.DataFrame:
    """类别均衡：对多数类下采样到少数类规模（或倍率上限）"""
    counts = Counter(df["label"])
    print(f"均衡前分布: {dict(counts)}")
    if strategy == "none":
        return df
    minority = min(counts.values())
    parts = []
    for label in LABELS:
        sub = df[df["label"] == label]
        if strategy == "downsample" and len(sub) > minority:
            sub = sub.sample(n=minority, random_state=SEED)
        parts.append(sub)
    out = pd.concat(parts).sample(frac=1.0, random_state=SEED).reset_index(drop=True)
    print(f"均衡后分布: {dict(Counter(out['label']))}")
    return out


def save_jsonl(df: pd.DataFrame, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for _, row in df.iterrows():
            f.write(json.dumps({"text": row["text"], "label": row["label"]},
                               ensure_ascii=False) + "\n")
    print(f"写入 {len(df)} 条 -> {path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="危机分类数据集准备")
    parser.add_argument("--input", required=True, help="原始 JSONL 路径")
    parser.add_argument("--output", default="data/processed", help="输出目录")
    parser.add_argument("--balance", choices=["downsample", "none"], default="downsample")
    args = parser.parse_args()

    random.seed(SEED)

    df = load_jsonl(Path(args.input))
    print(f"原始样本: {len(df)}")
    df = clean(df)
    print(f"清洗后: {len(df)}")
    df = dedup(df)
    df = balance(df, strategy=args.balance)

    # 分层划分 train/val/test（保证各集合类别比例一致，尤其 RISK 不被稀释）
    from sklearn.model_selection import train_test_split
    train_df, tmp_df = train_test_split(df, test_size=VALID_RATIO + TEST_RATIO,
                                        stratify=df["label"], random_state=SEED)
    val_df, test_df = train_test_split(tmp_df, test_size=TEST_RATIO / (VALID_RATIO + TEST_RATIO),
                                       stratify=tmp_df["label"], random_state=SEED)

    out = Path(args.output)
    save_jsonl(train_df, out / "train.jsonl")
    save_jsonl(val_df, out / "val.jsonl")
    save_jsonl(test_df, out / "test.jsonl")
    print("完成。")


if __name__ == "__main__":
    main()
