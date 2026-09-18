"""
LoRA 监督微调：将 GLM 底座微调为危机分类器
用法:
    python train_lora.py --train data/processed/train.jsonl \
                         --val data/processed/val.jsonl \
                         --output checkpoints/glm-crisis-lora
依赖 peft + transformers + datasets（见 requirements.txt）
"""
import argparse
import json
import math
from pathlib import Path

import torch
from datasets import Dataset
from peft import LoraConfig, get_peft_model, TaskType
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    DataCollatorForSeq2Seq,
    Trainer,
    TrainingArguments,
)

LABELS = ["CHAT", "CONSULT", "RISK"]
DEFAULT_BASE = "THUDM/glm-4-9b-chat"
PROMPT = (
    "你是心理健康平台的意图分类器。判断用户消息属于以下哪一类，只输出类别名：\n"
    "CHAT=日常闲聊；CONSULT=心理咨询求助；RISK=自伤/危机风险信号\n"
    f"消息：{{text}}\n类别："
)

MAX_LENGTH = 256


def load_dataset(path: Path) -> Dataset:
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            obj = json.loads(line)
            rows.append({"text": obj["text"], "label": obj["label"]})
    return Dataset.from_list(rows)


def build_tokenize_fn(tokenizer):
    def tokenize(example):
        prompt = PROMPT.format(text=example["text"])
        inputs = tokenizer(prompt, max_length=MAX_LENGTH, truncation=True)
        # 标签作为补全目标（LoRA 只训练补全部分）
        labels = tokenizer(example["label"], max_length=8, truncation=True)
        input_ids = inputs["input_ids"] + labels["input_ids"]
        label_ids = [-100] * len(inputs["input_ids"]) + labels["input_ids"]
        return {"input_ids": input_ids, "labels": label_ids,
                "attention_mask": [1] * len(input_ids)}

    return tokenize


def main() -> None:
    parser = argparse.ArgumentParser(description="LoRA 危机分类微调")
    parser.add_argument("--base-model", default=DEFAULT_BASE, help="底座模型")
    parser.add_argument("--train", required=True)
    parser.add_argument("--val", required=True)
    parser.add_argument("--output", default="checkpoints/glm-crisis-lora")
    parser.add_argument("--epochs", type=float, default=3.0)
    parser.add_argument("--batch-size", type=int, default=4)
    parser.add_argument("--grad-accum", type=int, default=4)
    parser.add_argument("--lr", type=float, default=2e-4)
    parser.add_argument("--lora-rank", type=int, default=16)
    parser.add_argument("--lora-alpha", type=int, default=32)
    args = parser.parse_args()

    if not torch.cuda.is_available():
        print("警告：未检测到 GPU，仅适用于小规模调试。")

    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        torch_dtype=torch.bfloat16,
        device_map="auto",
        trust_remote_code=True,
    )

    # LoRA 配置：低秩适配器，只训练不到 1% 的参数
    lora_config = LoraConfig(
        task_type=TaskType.CAUSAL_LM,
        r=args.lora_rank,
        lora_alpha=args.lora_alpha,
        lora_dropout=0.05,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    train_ds = load_dataset(Path(args.train)).map(build_tokenize_fn(tokenizer),
                                                  remove_columns=["text", "label"])
    val_ds = load_dataset(Path(args.val)).map(build_tokenize_fn(tokenizer),
                                              remove_columns=["text", "label"])

    training_args = TrainingArguments(
        output_dir=args.output,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        eval_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
        bf16=torch.cuda.is_available(),
        logging_steps=10,
        report_to=[],
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        data_collator=DataCollatorForSeq2Seq(tokenizer, padding=True),
    )
    trainer.train()

    model.save_pretrained(args.output)
    tokenizer.save_pretrained(args.output)
    print(f"LoRA 适配器已保存到 {args.output}")

    # 打印最佳验证损失对应的 epoch 供参考
    best = trainer.state.best_metric
    if best is not None:
        print(f"最佳验证损失: {best:.4f}")


if __name__ == "__main__":
    main()
