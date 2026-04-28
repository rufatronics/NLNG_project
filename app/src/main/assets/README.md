# Sentinel-NG Model Assets

Place the following model files in this directory before building the APK.
These binary files are NOT included in the repository.

## Required files

| File | Description | Size (approx) |
|------|-------------|---------------|
| `crop_doctor.tflite` | EfficientNet-Lite, 10 crop disease classes | ~10 MB |
| `health_scan.tflite` | MobileNetV3, 7 health condition classes | ~8 MB |
| `nigerian_nlu.ftz` | FastText intent classifier (5 languages) | ~2 MB |
| `bonsai_1.7b_q1_0.gguf` | Bonsai 1.7B GGUF English chat model | ~900 MB |
| `mt5_merged.gguf` | mT5-small GGUF Nigerian-language chat model | ~600 MB |

## Notes

- The `.tflite` and `.ftz` files will be loaded by TFLiteHelper and FastTextHelper.
- The `.gguf` files are loaded on demand by LlamaModelManager and copied to
  internal storage on first use (to allow mmap-based loading by llama.cpp).
- The app functions in degraded mode (keyword-based NLU, stub chat responses)
  when model files are absent. Only TFLite inference requires the .tflite files
  to be present; missing files will show a toast error.
- `aaptOptions { noCompress "tflite", "gguf", "ftz" }` in app/build.gradle
  ensures these files are stored uncompressed for efficient memory-mapped access.
