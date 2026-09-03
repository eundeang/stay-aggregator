---
name: domain-model
description: Use this skill whenever writing, modifying, or reviewing code related to the standard stay/domain model — Stay, RoomTypeOffer, Price, NightlyRate, or any supplier adapter code that maps external DTOs into these domain types. Also use when a design question comes up about what fields the domain model should have or how a supplier's data should be normalized.
---

# 도메인 모델 작업 시 규칙

1. **`docs/domain-model.md`를 먼저 읽는다.** 여기에 필드 정의와 각 결정의 근거가
   있다. 이 문서와 다르게 구현하지 않는다 — 다르게 하고 싶으면 코드를 먼저 짜지
   말고 사용자에게 왜 문서와 다르게 가야 하는지 먼저 확인받는다.

2. **"남은 열린 질문" 섹션을 확인한다.** 이 섹션에 있는 항목은 아직 확정되지 않은
   설계 판단이다. 구현하다가 이 항목에 해당하는 코드를 작성하게 되면:
    - 임의로 결정하지 말고, 사용자에게 트레이드오프를 짧게 제시하고 판단을 구한다.
    - 결정되면 `docs/domain-model.md`의 "남은 열린 질문"에서 해당 항목을 제거하고,
      본문에 결정 사항과 근거를 추가한다.

3. **문서에 없는 필드나 구조를 새로 추가해야 하면**, 코드부터 짜지 말고 먼저
   `docs/domain-model.md`를 업데이트한 뒤 그에 맞춰 구현한다. (안내 문서 원칙:
   "설계 문서에 적은 내용이 실제 코드에 반영되어야 한다" — 이는 역으로 코드가
   문서보다 앞서가서도 안 된다는 뜻으로 취급한다.)

4. **공급사 DTO → 도메인 모델 변환 로직을 작성할 때**, 어떤 원본 필드를 어떤
   도메인 필드로 매핑했는지 주석으로 짧게 남긴다 (예: `// A: nightlyRate+taxAmount
   합산 → Price.totalAmount`). 이건 나중에 README/인터뷰에서 매핑 근거를 다시
   설명할 때 근거 자료가 된다.