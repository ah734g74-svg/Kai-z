package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * HyperAdaptiveIntelligence — العقل المدبر لنظام Kai-Ezz.
 * يقوم هذا النظام بتخزين "الخبرات" المكتسبة من عمليات الاختراق، تحليل الفشل،
 * وتعديل السلوك التكتيكي لضمان النجاح المستقبلي.
 */
@Serializable
data class HackingExperience(
    val targetType: String,
    val securityDetected: List<String>,
    val failedVectors: List<String>,
    val successfulVector: String?,
    val timestamp: Long
)

class HyperAdaptiveIntelligence(private val appSettings: AppSettings) {

    private val json = Json { ignoreUnknownKeys = true }

    // مفتاح تخزين الخبرات في الإعدادات
    private val KEY_HACKING_EXPERIENCES = "hyper_hacking_experiences"
    private val KEY_INTELLIGENCE_LEVEL = "hyper_intelligence_level"

    /**
     * تسجيل خبرة جديدة بعد محاولة اختراق.
     */
    fun recordExperience(experience: HackingExperience) {
        val currentExperiences = getExperiences().toMutableList()
        currentExperiences.add(experience)
        // الاحتفاظ بآخر 1000 خبرة للتعلم منها
        val limitedExperiences = currentExperiences.takeLast(1000)
        appSettings.settings.putString(KEY_HACKING_EXPERIENCES, json.encodeToString(limitedExperiences))
        
        // زيادة مستوى الذكاء مع كل خبرة جديدة
        val currentLevel = getIntelligenceLevel()
        setIntelligenceLevel(currentLevel + 1)
    }

    /**
     * الحصول على جميع الخبرات المسجلة.
     */
    fun getExperiences(): List<HackingExperience> {
        val rawJson = appSettings.settings.getString(KEY_HACKING_EXPERIENCES, "[]")
        return try {
            json.decodeFromString(rawJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * تحليل السياق الحالي وتقديم استراتيجية تكيفية.
     */
    fun suggestStrategy(targetType: String, detectedSecurity: List<String>): String {
        val relevantExperiences = getExperiences().filter { it.targetType == targetType }
        
        // إذا كان هناك فشل سابق مع نفس نوع الأمان، تجنب تلك النواقل
        val failedOnes = relevantExperiences.flatMap { it.failedVectors }.toSet()
        val successfulOnes = relevantExperiences.mapNotNull { it.successfulVector }.toSet()

        return when {
            successfulOnes.isNotEmpty() -> "Highly recommended: ${successfulOnes.first()}. This has worked before on similar targets."
            failedOnes.isNotEmpty() -> "Warning: Avoid ${failedOnes.joinToString()}. These were previously blocked by $detectedSecurity."
            else -> "New target profile detected. Initiating multi-vector polymorphic probe."
        }
    }

    fun getIntelligenceLevel(): Int = appSettings.settings.getInt(KEY_INTELLIGENCE_LEVEL, 1)

    private fun setIntelligenceLevel(level: Int) {
        appSettings.settings.putInt(KEY_INTELLIGENCE_LEVEL, level)
    }

    /**
     * تفعيل وضع "السرعة اللانهائية" برمجياً.
     */
    fun activateInfiniteSpeed() {
        appSettings.setFreeMode(FreeMode.FAST)
        // يمكن إضافة المزيد من منطق تسريع العمليات هنا
    }
}
