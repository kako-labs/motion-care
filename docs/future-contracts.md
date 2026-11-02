# 후속 기능 계약 초안

현재 구현에 연결되지 않은 초안입니다. 기능 착수 시 저장·삭제·응답자 정책을 함께 확정합니다.

```kotlin
package dev.motioncare.contracts.ports

import dev.motioncare.contracts.model.*
import kotlinx.coroutines.flow.StateFlow

// These are extension contracts, not working profile/feedback implementations.
interface ProfileReader { suspend fun people(): List<PersonProfile> }
interface ParticipantWriter { suspend fun assign(participants: TripParticipants) }
interface FeedbackWriter { suspend fun save(feedback: TripFeedback) }
interface SettingsReader { val settings: StateFlow<GuidanceSettings> }
interface EnvironmentReader { suspend fun current(): EnvironmentReading }
interface DepartureAdviceReader { suspend fun forTrip(id: TripId): Guidance? }

```
