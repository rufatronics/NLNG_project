package com.sentinelng.ml

import android.content.Context
import android.graphics.Bitmap
import com.sentinelng.data.InferenceResult
import com.sentinelng.data.ModelType
import com.sentinelng.data.SupportedLanguage
import com.sentinelng.utils.ImagePreprocessor
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(private val context: Context) {

    private var cropInterpreter: Interpreter? = null
    private var healthInterpreter: Interpreter? = null

    // ── Label maps ──────────────────────────────────────────────────────────

    private val cropLabelsEn = arrayOf(
        "Healthy Crop",
        "Cassava Mosaic Disease",
        "Maize Streak Virus",
        "Tomato Late Blight",
        "Rice Blast",
        "Soybean Rust",
        "Groundnut Rosette",
        "Yam Anthracnose",
        "Cowpea Aphid Infestation",
        "Banana Fusarium Wilt"
    )

    private val healthLabelsEn = arrayOf(
        "No Abnormality Detected",
        "Possible Malaria Symptoms",
        "Possible Typhoid Symptoms",
        "Skin Rash / Infection",
        "Eye Infection (Conjunctivitis)",
        "Wound / Laceration",
        "Signs of Dehydration"
    )

    private val cropAdviceEn = arrayOf(
        "Your crop looks healthy. Continue good agricultural practices.",
        "Remove and destroy infected plants. Use mosaic-resistant varieties next season.",
        "Remove infected plants, control leafhopper vectors with approved insecticide.",
        "Apply copper-based fungicide. Improve drainage and avoid overhead irrigation.",
        "Apply tricyclazole fungicide. Drain fields where possible.",
        "Apply fungicide with trifloxystrobin. Use resistant varieties next season.",
        "Remove infected groundnut plants. Control aphid vectors.",
        "Improve air circulation. Apply mancozeb-based fungicide.",
        "Apply approved aphicide. Introduce natural predators where possible.",
        "Remove and destroy infected plants. Disinfect farming tools."
    )

    private val healthAdviceEn = arrayOf(
        "No immediate concern detected. Maintain healthy habits.",
        "Seek medical attention immediately. Take malaria test. Avoid stagnant water near home.",
        "See a healthcare provider. Maintain oral hydration.",
        "Keep the area clean and dry. See a pharmacist or doctor if it worsens.",
        "Clean eyes with clean water. See a healthcare provider.",
        "Clean and cover the wound. Seek medical help for deep cuts.",
        "Drink clean water regularly. Seek medical help if symptoms worsen."
    )

    // Hausa translations (crop)
    private val cropLabelsHa = arrayOf(
        "Amfanin Gona Mai Lafiya", "Cutar Cassava Mosaic", "Ƙwayar Maize Streak",
        "Ruwan Tomato Late Blight", "Cutar Shinkafa Rice Blast", "Tsatsa Soybeen",
        "Cutar Gyaɗa Rosette", "Cutar Doya Anthracnose", "Kwari Cowpea Aphid",
        "Cutar Ayaba Fusarium"
    )
    private val cropAdviceHa = arrayOf(
        "Amfanin gonarka yana da lafiya. Ci gaba da ayyukan noma masu kyau.",
        "Cire da lalata shuke-shuke masu cutar. Yi amfani da iri masu juriya.",
        "Cire shuke-shuke masu cutar, sarrafa kwari masu ɗauke ta.",
        "Yi amfani da maganin fungicide na jan ƙarfe. Inganta ruwa da zubar da shi.",
        "Yi amfani da maganin tricyclazole. Fitar da ruwa inda ya yiwu.",
        "Yi amfani da maganin trifloxystrobin. Yi amfani da iri masu juriya.",
        "Cire gyaɗa masu cutar. Sarrafa kwari.",
        "Inganta zagayowar iska. Yi amfani da maganin mancozeb.",
        "Yi amfani da maganin aphicide. Gabatar da mafarauta na halitta.",
        "Cire da lalata shuke-shuke masu cutar. Tsarkake kayan aiki."
    )

    // ── Model loading ───────────────────────────────────────────────────────

    fun loadModel(modelType: ModelType) {
        val assetName = when (modelType) {
            ModelType.CROP_DOCTOR -> "crop_doctor.tflite"
            ModelType.HEALTH_SCAN -> "health_scan.tflite"
        }

        try {
            val assetFd = context.assets.openFd(assetName)
            val inputStream = FileInputStream(assetFd.fileDescriptor)
            val fileChannel = inputStream.channel
            val mappedBuffer: ByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength
            )

            val options = Interpreter.Options().apply {
                numThreads = 4
            }

            val interpreter = Interpreter(mappedBuffer, options)

            when (modelType) {
                ModelType.CROP_DOCTOR -> cropInterpreter = interpreter
                ModelType.HEALTH_SCAN -> healthInterpreter = interpreter
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Model file not present (placeholder) – will fail gracefully
        }
    }

    /**
     * Run inference on a bitmap. Returns null if the model isn't loaded.
     */
    fun runInference(
        bitmap: Bitmap,
        modelType: ModelType,
        language: SupportedLanguage = SupportedLanguage.ENGLISH
    ): InferenceResult? {
        val interpreter = when (modelType) {
            ModelType.CROP_DOCTOR -> cropInterpreter
            ModelType.HEALTH_SCAN -> healthInterpreter
        } ?: return null

        val inputBuffer = ImagePreprocessor.toByteBuffer(bitmap)
        val numClasses = when (modelType) {
            ModelType.CROP_DOCTOR -> cropLabelsEn.size
            ModelType.HEALTH_SCAN -> healthLabelsEn.size
        }
        val outputBuffer = Array(1) { FloatArray(numClasses) }

        interpreter.run(inputBuffer, outputBuffer)

        val scores = outputBuffer[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: 0
        val confidence = scores[maxIdx]

        val label = when (modelType) {
            ModelType.CROP_DOCTOR -> when (language) {
                SupportedLanguage.HAUSA -> cropLabelsHa[maxIdx]
                else -> cropLabelsEn[maxIdx]
            }
            ModelType.HEALTH_SCAN -> healthLabelsEn[maxIdx] // extend per language as needed
        }

        val advice = when (modelType) {
            ModelType.CROP_DOCTOR -> when (language) {
                SupportedLanguage.HAUSA -> cropAdviceHa[maxIdx]
                else -> cropAdviceEn[maxIdx]
            }
            ModelType.HEALTH_SCAN -> healthAdviceEn[maxIdx]
        }

        return InferenceResult(maxIdx, confidence, label, advice)
    }

    fun close() {
        cropInterpreter?.close()
        healthInterpreter?.close()
    }
}
