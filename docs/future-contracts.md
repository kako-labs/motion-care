# 후속 기능 계약 초안

현재 구현에 연결되지 않은 초안입니다. 기능 착수 시 저장·삭제·응답자 정책을 함께 확정합니다. 별도 contracts 모듈은 없으며, 각 인터페이스는 아래 주석의 모듈에 둡니다. 모델은 `core/model`을 사용합니다.

```kotlin
// data/profile: the working people API is ProfileRepository in docs/contracts.md.
interface ParticipantWriter { suspend fun assign(participants: TripParticipants) }
interface FeedbackWriter { suspend fun save(feedback: TripFeedback) }
interface SettingsReader { val settings: StateFlow<GuidanceSettings> }

// feature/history, computed from repositories
interface DepartureAdviceReader { suspend fun forTrip(id: TripId): Guidance? }
```
