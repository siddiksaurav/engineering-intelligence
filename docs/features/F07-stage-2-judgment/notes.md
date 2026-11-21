# F07 Stage 2 Feature work judgment and review comment judgment: notes

## Session note

The user asked the session to decide every open point and to skip the real model runs (no LLM key, 22 Nov 2026). No question brief was answered; decisions 163 to 176 are the session's and may be overruled by number.

## Differences from the plan

- Batch 1: `InvalidDigestException` became `InvalidAnswerException`, shared by the three answers; field readers moved from `PullRequestDigest` to a new `AnswerFields`. `FeatureWorkAnswer.toValue` is added by task 7, which knows `judgedOnPartialInput` and `leftOutPullRequests`. A fact may have an empty `quote` or `source` (its text is required); an empty or unknown source gets the item's source link. Comment ids in the review input and answer are `C-<databaseId>`. `ReviewCommentTypes.classify` fails naming the type when the setting misses it or gives a substantive type no tier. Java 25 on this machine is `/usr/lib/jvm/jdk-25-oracle-x64`.
- Batch 2: `judgment.CheckedAnswers` is the one ask-again helper (once more on an invalid or unreadable answer), used by `PullRequestDigester` and `FeatureWorkJudge`; the review judge uses it too. `ProviderChain.startRunRequiringAProvider` and `reportUseTo` replace the same lines of `PullRequestDigestJob`. `TextCuts` (cut with its line) and `PullRequestBody` are shared by the digest and Feature work inputs. `JudgmentJobFailedException` is the failure of the two Stage 2 jobs.
- The model's confidence is stored in `judgment.value.confidence`: `NewJudgment` and `Judgment` have no `confidence` column mapping (the column exists since V9, unused). A later task may map it.
- `leftOutPullRequests` holds external ids (`opf/openproject#24204`); the input text names them `PR-24204`. The oldest PR always stays in the input. Cuts for budget: task descriptions are halved first, then the feature description, only while a halving still shortens the text; no number constant in `judgment` (SpecNumbersTest).
- `FeatureWorkToJudge.taskNumbers` leaves out the Feature itself (a PR that links the Feature directly lists it among the tasks of the item).
- `ModelJobGuard` is in memory: it guards one backend process, which is the only way jobs run. `review-comment-judgment` (batch 3) takes it the same way: `enter(name())` and `close()` in a `finally`.
