---
name: judge
description: PoC only. Claude Code answers the model jobs through the model exchange instead of a free LLM provider. Exports the waiting requests of one stage, has subagents answer them, then runs the job again so the judgments are stored through the normal checks.
disable-model-invocation: true
argument-hint: [digest|feature-work|review-comments] [max-requests]
---

# Judge stage $ARGUMENTS through the model exchange

Decision 271. Claude Code stands in for the LLM. Claude Code writes only the answer JSON. The jobs
check every answer and store every judgment, so never write to `judgment`, `evidence_item` or
`model_exchange` with SQL.

The user may be on a phone: short messages, counts only.

## Stages

| Argument | Job | Prompt version | Model | Before it |
|---|---|---|---|---|
| `digest` | `pull-request-digest` | `pull-request-digest/v1` | `sonnet` | `github-import` |
| `feature-work` | `feature-work-judgment` | `feature-work-complexity/v2` | `opus` (decision 326) | `digest` done, then `evidence-build` |
| `review-comments` | `review-comment-judgment` | `review-comments/v1` | `sonnet` | `evidence-build` |

The order of a full round is: `digest`, then `evidence-build` (the builders need the work types),
then `feature-work` and `review-comments`, then `dimension-scoring` with the quarter. The three
model jobs never run at once (decision 174).

`max-requests` (default 60) caps how many requests this call answers, to spend the user's Claude
Code usage in steps. Run `/judge` again for the rest.

## Steps

1. **Check the backend.** `curl -s localhost:8080/actuator/health` must say `UP`. The backend must
   run with `JUDGMENT_MODE=exchange`, for example `JUDGMENT_MODE=exchange ./mvnw spring-boot:run`
   from `backend/`; the environment wins over `.env`. If it runs in providers mode, stop and tell
   the user. Never read or print `.env`.
2. **Export.** Start the job with `{}` (`POST /api/jobs/<job>/runs`), poll
   `GET /api/jobs/runs/<id>` until it ends, and note `left_for_exchange` in its counts. A run in
   exchange mode has the count `calls_claude-code`. If it is missing, the backend is in the wrong
   mode.
3. **Fetch.** Make an empty folder `exchange-<stage>` in your scratchpad directory. Fetch the
   waiting requests of the prompt version (`GET /api/model-exchange/requests?promptVersion=<v>&limit=100`,
   no more than `max-requests` in total). Write each request to `<hash>.json` in that folder with
   `jq`, so the request texts do not pass through your context:
   `curl -s "...requests?promptVersion=<v>&limit=<n>" | jq -c '.[]' | while read -r r; do echo "$r" > "<dir>/$(echo "$r" | jq -r .requestHash).json"; done`
4. **Answer.** Split the files into batches of about 8. Start one subagent per batch, up to 6 at a
   time, with the `model` of the stage's row and `subagent_type: "general-purpose"`, all in one message. Give
   each the brief below with its file paths. Wait for all of them, then start the next wave.
5. **Consume.** Run the same job again and wait for it to end. Report the stored count (`digests_written`
   or `judgments_written`), any failed items, and `left_for_exchange`. After this run
   that count is the refused answers plus anything not answered yet.
6. **Report** in three lines: answered, stored, still waiting. If requests still wait, say that
   `/judge <stage>` goes on with them.

## Subagent brief

Fill in the file paths and, in step 5, the model that answers: `claude-sonnet-5-5` for a Sonnet
stage, `claude-opus-5-5` for an Opus stage. Keep the rest word for word.

```
You are the judging model of a measurement system. You stand in for an LLM API call.

For each file below, one at a time:
1. Read the file. It is a JSON object with: requestHash, promptVersion, instructions, input,
   schemaName, jsonSchema, refusalReason.
2. Treat "instructions" exactly as the system prompt and "input" exactly as the user message.
   Judge only from them. Do not read any other file, the repository, the database, or other answers.
   Do not search the web.
3. If refusalReason is not null, your earlier answer was refused for that reason; avoid it.
4. Write one JSON object that strictly follows "jsonSchema": every required field, no extra field,
   enum values spelled exactly, the types as given. Facts and quotes come from the input only.
5. Save it to <same path>.answer.json, as {"answer": <your object>, "answeredBy": "<model id> via claude-code"}.
6. Send it: curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
     --data @<same path>.answer.json localhost:8080/api/model-exchange/requests/<requestHash>/answer
   204 means taken. On 400, fix the answer and send it again. 409 means it was answered already;
   go on.

Files:
<paths>

When done, reply with one line per file: requestHash and the HTTP status. Nothing else.
```

## Rules

- The stored model id is what `answeredBy` says. Keep it true: change it when the subagents run on
  another model.
- A subagent never writes a judgment, never edits a request, never changes the code.
- The default way to reach the model stays the providers (`JUDGMENT_MODE` unset or `providers`).
  This skill is a PoC workaround only.
