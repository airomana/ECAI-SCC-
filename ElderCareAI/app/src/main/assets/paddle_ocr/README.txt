PaddleOCR 模型放置目录
========================

本应用使用 PaddleOCR 进行端侧菜单文字识别。请将 PP-OCR 模型文件放入此目录（与 README.txt 同级）。

一、需要放置的文件（共 7 个）

1. 检测模型（det）：
   - det.pdmodel
   - det.pdiparams

2. 识别模型（rec）：
   - rec.pdmodel
   - rec.pdiparams

3. 方向分类模型（cls）：
   - cls.pdmodel
   - cls.pdiparams

4. 识别字典（必须）：
   - ppocr_keys_v1.txt

二、模型下载

从 PaddleOCR 官方获取 PP-OCRv3 中文模型（或 PP-OCRv2）：

  https://github.com/PaddlePaddle/PaddleOCR/blob/release/2.6/doc/doc_ch/models_list.md

下载后，将以下文件重命名为上述名称并放入本目录：

- 检测：ch_PP-OCRv3_det_infer.* → det.pdmodel / det.pdiparams
- 识别：ch_PP-OCRv3_rec_infer.* → rec.pdmodel / rec.pdiparams
- 分类：ch_ppocr_mobile_v2.0_cls_infer.* → cls.pdmodel / cls.pdiparams
- 字典：ppocr_keys_v1.txt 直接放入（一般随 rec 模型包或 PaddleOCR 仓库中有）

三、说明

- 若未放置模型，菜单识别将返回空文本；首次加载模型可能需数秒。
- 正式发布建议在应用内实现模型下载，不要将大体积模型直接打进 APK。
