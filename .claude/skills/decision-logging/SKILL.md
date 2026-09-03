---
name: decision-logging
description: Use this skill whenever a non-trivial design decision is made during this session — choosing between two or more valid approaches, rejecting an alternative, or resolving an open question from docs/*.md. Also use at the end of any work session where design judgment was involved, even if not explicitly asked to log it.
---

# 의사결정 자동 기록

이 프로젝트는 "과정"이 평가 대상이다 (JOURNAL.md, AI 활용 기록). 설계 판단이 있을
때마다 아래를 수행한다 — 사용자가 매번 "저널에 남겨줘"라고 요청하지 않아도 된다.

## 기록 대상

- 두 가지 이상의 유효한 접근법 중 하나를 선택한 경우
- 검토했지만 채택하지 않은 대안이 있는 경우
- `docs/*.md`의 "열린 질문"/"진행 중" 항목이 해결된 경우
- Claude(AI)가 제안한 방향을 사용자가 그대로 받아들이지 않고 수정하거나 거부한 경우

## 기록 형식

`JOURNAL.md`의 현재 `## Day N` 섹션(없으면 새로 추가) 아래 `### 의사결정`에 추가:

```
- **[결정 요약]** — [선택한 것과 이유를 1~2문장으로]
  - 검토했다 기각한 대안: [있다면]
```

세션 마지막에 AI 활용이 있었다면 `### AI 활용`에 짧게 한 줄 추가:

```
- [무엇을 물었고, 그 답을 그대로 썼는지/수정했는지/거부했는지]
```

## 원칙

- **판단의 결과만이 아니라 과정을 남긴다.** "X로 했다"보다 "X와 Y를 비교했고,
  Z 때문에 X를 택했다"가 훨씬 낫다 — 안내 문서가 "시행착오가 드러나는 기록"을
  높게 평가한다고 명시했다.
- **사용자의 판단과 AI의 제안을 구분해서 적는다.** "Claude가 제안 → 사용자가
  검토 후 채택/수정"처럼 누가 무엇을 했는지 명확히 한다. 사용자의 결정을 AI의
  결정처럼, 혹은 그 반대로 적지 않는다.
- 사소한 변경(오타 수정, 포맷팅 등)은 기록하지 않는다. 기록 대상은 "왜 이렇게
  했는지 나중에 설명할 수 있어야 하는" 판단으로 한정한다.