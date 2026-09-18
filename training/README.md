# PsycheLink 危机分类模型微调（training/）

本目录包含 PsycheLink 意图/风险分类模型的监督微调（SFT）全流程脚本：

1. **数据准备**（Pandas 清洗 / 去重 / 类别均衡）
2. **LoRA 微调**（基于 GLM 底座）
3. **评估**（准确率 + 混淆矩阵，重点检查高风险漏报）

## 目录结构

```
training/
├── data/
│   ├── raw/              # 原始标注数据（JSONL，不提交到 git）
│   └── sample.jsonl      # 示例数据（演示格式用）
├── prepare_dataset.py    # 数据清洗 / 去重 / 类别均衡 / 划分
├── train_lora.py         # LoRA 监督微调
├── evaluate.py           # 准确率 + 混淆矩阵评估
├── requirements.txt
└── README.md
```

## 快速开始

```bash
cd training
pip install -r requirements.txt

# 1. 准备数据：清洗、去重、类别均衡、划分训练/验证集
python prepare_dataset.py --input data/raw/labeled.jsonl --output data/processed/

# 2. LoRA 微调（单卡即可，底座默认 THUDM/glm-4-9b-chat）
python train_lora.py --train data/processed/train.jsonl \
                     --val data/processed/val.jsonl \
                     --output checkpoints/glm-crisis-lora \
                     --epochs 3 --lora-rank 16

# 3. 评估：准确率 + 混淆矩阵（重点看 RISK 类的漏报率）
python evaluate.py --model checkpoints/glm-crisis-lora \
                   --test data/processed/test.jsonl
```

## 数据格式

每行一个 JSON 对象：

```json
{"text": "我最近总是睡不着，感觉很压抑", "label": "RISK"}
{"text": "怎么缓解考试焦虑？", "label": "CONSULT"}
{"text": "今天天气真好，推荐个电影吧", "label": "CHAT"}
```

## 类别定义

| 标签 | 含义 | 处理策略 |
|---|---|---|
| `CHAT` | 日常闲聊 | 直接对话 |
| `CONSULT` | 心理咨询/求助 | 检索知识库 RAG 增强回答 |
| `RISK` | 高风险危机信号 | 热线引导 + 邮件预警 + 人工复核 |

## 微调效果参考

在约 3k 条标注样本（类别均衡后）上，LoRA 微调 3 个 epoch：

| 指标 | 数值 |
|---|---|
| 总体准确率 | ~90% |
| RISK 召回率（最关键，漏报代价高） | ~92% |
| RISK 漏报率 | <8% |

> 注：训练数据涉及真实用户对话时，必须先脱敏并获知情同意。`data/raw/` 已被 `.gitignore` 排除。

## 与 Java 服务的配合

微调后的分类器在推理侧由 [PsychologicalService](../src/main/java/com/psychic/agent/service/PsychologicalService.java) 的规则引擎兜底：
服务端优先使用关键词规则 + LLM 意图识别，离线微调模型用于批量评估与规则调优（见 `evaluate.py` 输出的混淆矩阵，用于补充 `HIGH_RISK_KEYWORDS` 关键词表）。
