package dev.motioncare.feature.history.feedback

import dev.motioncare.core.model.FeedbackReason

/**
 * The choices a rider sees after a trip. The retired "차 안 환경" only shows when an earlier answer already holds it,
 * so editing that answer keeps it visible and lets the rider clear it.
 */
internal fun offeredReasons(selected: Set<FeedbackReason>): List<FeedbackReason> =
    FeedbackReason.entries.filter { it != FeedbackReason.CABIN_ENVIRONMENT || it in selected }

/** Felt in the cabin rather than caused by the car's motion. */
internal val FeedbackReason.isCabinFeeling: Boolean
    get() = this == FeedbackReason.FELT_HOT || this == FeedbackReason.STUFFY_AIR || this == FeedbackReason.CABIN_ENVIRONMENT

// "답답했어요" alone can read as "bored" to a child, so the stuffy choice names the air.
internal fun FeedbackReason.label(): String = when (this) {
    FeedbackReason.START_STOP -> "출발·정차"
    FeedbackReason.REPEATED_SPEED_CHANGE -> "반복 가감속"
    FeedbackReason.CURVE -> "커브"
    FeedbackReason.CABIN_ENVIRONMENT -> "차 안 환경"
    FeedbackReason.FELT_HOT -> "더웠어요"
    FeedbackReason.STUFFY_AIR -> "공기가 답답했어요"
    FeedbackReason.UNKNOWN -> "잘 모르겠어요"
}
