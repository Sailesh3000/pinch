# Placeholder for the bundled on-device AI model (Play Asset Delivery).
#
# The actual model file `qwen-pinch.litertlm` (~50 MB) is NOT committed to
# the repository because it is large and produced from a private fine-tune.
# To build a release bundle, drop the .litertlm file in this directory:
#
#   aimodel/src/main/assets/qwen-pinch.litertlm
#
# The FormatData feed along with the Companion build tooling expects this
# exact filename (ModelAssetProvider.MODEL_FILE_NAME). See
# docs/instrumentation_test_plan.md for the validation checklist.