"""
模型评估：总体准确率 + 各类别 P/R/F1 + 混淆矩阵
重点指标：RISK 漏报率（FN / (FN + TP)）——危机场景下漏报代价远高于误报
用法:
    python evaluate.py --model checkpoints/glm-crisis-lora --test data/processed/test.jsonl
"""
import argparse
import json
from collections import Counter
from pathlib import Path

import matplotlib.pyplot as plt
import torch
from peft import PeftModel
from sklearn.metrics import confusion_matrix
from transformers import AutoModelForCausalLM, AutoTokenizer

LABELS = ["CHAT", "CONSULT", "RISK"]
PROMPT = (
    "你是心理健康平台的意图分类器。判断用户消息属于以下哪一类，只输出类别名：\n"
    "CHAT=日常闲聊；CONSULT=心理咨询求助；RISK=自伤/危机风险信号\n"
    f"消息：{{text}}\n类别："
)


def normalize(pred: str) -> str:
    pred = pred.strip().upper()
    for label in LABELS:
        if label in pred:
            return label
    return "CHAT"  # 无法解析时兜底为 CHAT（误报由服务端规则二次校验兜住）


def main() -> None:
    parser = argparse.ArgumentParser(description="危机分类模型评估")
    parser.add_argument("--base-model", default="THUDM/glm-4-9b-chat")
    parser.add_argument("--model", required=True, help="LoRA 适配器路径")
    parser.add_argument("--test", required=True)
    parser.add_argument("--plot", default="confusion_matrix.png")
    args = parser.parse_args()

    rows = []
    with open(Path(args.test), encoding="utf-8") as f:
        for line in f:
            rows.append(json.loads(line))
    print(f"测试样本: {len(rows)}，真实分布: {dict(Counter(r['label'] for r in rows))}")

    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    base = AutoModelForCausalLM.from_pretrained(
        args.base_model, torch_dtype=torch.bfloat16, device_map="auto",
        trust_remote_code=True,
    )
    model = PeftModel.from_pretrained(base, args.model)
    model.eval()

    y_true, y_pred = [], []
    with torch.no_grad():
        for row in rows:
            prompt = PROMPT.format(text=row["text"])
            inputs = tokenizer(prompt, return_tensors="pt", truncation=True,
                               max_length=256).to(model.device)
            output = model.generate(**inputs, max_new_tokens=8, do_sample=False)
            pred = tokenizer.decode(output[0][inputs["input_ids"].shape[1]:],
                                    skip_special_tokens=True)
            y_true.append(row["label"])
            y_pred.append(normalize(pred))

    # 混淆矩阵（行=真实，列=预测）
    cm = confusion_matrix(y_true, y_pred, labels=LABELS)
    print("\n混淆矩阵（行=真实, 列=预测, 顺序 " + "/".join(LABELS) + "）:")
    print(cm)

    # 各类别指标
    print("\n各类别指标:")
    print(f"{'类别':<8}{'精确率':<10}{'召回率':<10}{'F1':<10}")
    for i, label in enumerate(LABELS):
        tp = cm[i, i]
        fp = cm[:, i].sum() - tp
        fn = cm[i, :].sum() - tp
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        print(f"{label:<8}{precision:<10.3f}{recall:<10.3f}{f1:<10.3f}")

    accuracy = sum(t == p for t, p in zip(y_true, y_pred)) / len(y_true)
    print(f"\n总体准确率: {accuracy:.3f}")

    # 关键指标：RISK 漏报率
    risk_idx = LABELS.index("RISK")
    fn_risk = cm[risk_idx, :].sum() - cm[risk_idx, risk_idx]
    tp_risk = cm[risk_idx, risk_idx]
    miss_rate = fn_risk / (fn_risk + tp_risk) if fn_risk + tp_risk else 0.0
    print(f"RISK 漏报率: {miss_rate:.3f}（漏报 {fn_risk} / 实际 {fn_risk + tp_risk}）"
          f"{'⚠️ 偏高，需补充 RISK 语料重训' if miss_rate > 0.10 else ' ✓'}")

    # 混淆矩阵可视化
    fig, ax = plt.subplots(figsize=(6, 5))
    im = ax.imshow(cm, cmap="Blues")
    ax.set_xticks(range(len(LABELS)), LABELS)
    ax.set_yticks(range(len(LABELS)), LABELS)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    for i in range(len(LABELS)):
        for j in range(len(LABELS)):
            ax.text(j, i, cm[i, j], ha="center", va="center",
                    color="white" if cm[i, j] > cm.max() / 2 else "black")
    fig.colorbar(im)
    fig.tight_layout()
    fig.savefig(args.plot, dpi=150)
    print(f"混淆矩阵图已保存: {args.plot}")


if __name__ == "__main__":
    main()
