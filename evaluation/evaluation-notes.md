# CodeMind Retrieval & Answering Evaluation Benchmark

## 1. Objectives & Metrics
This evaluation benchmark objectively measures CodeMind's search and grounded QA capabilities across 4 key dimensions:

1. **Recall@5**:
   - *Definition*: Did the top 5 retrieved code chunks include at least one ground-truth expected file or symbol?
   - *Target*: $\ge 85\%$ across exact symbol and conceptual queries.
2. **Mean Reciprocal Rank (MRR)**:
   - *Definition*: $\frac{1}{|Q|} \sum_{i=1}^{|Q|} \frac{1}{\text{rank}_i}$, where $\text{rank}_i$ is the position of the first relevant result.
   - *Target*: $\ge 0.70$.
3. **Citation Validity**:
   - *Definition*: The percentage of citations in the LLM response that successfully resolve to real, indexed code lines in the repository.
   - *Target*: $100\%$ (enforced server-side via `CitationValidator`).
4. **Hallucination Resistance (Negative Queries)**:
   - *Definition*: When queried for features not in the codebase (e.g., Stripe webhooks, Redis cache, Kubernetes Helm charts), does the system explicitly admit insufficient evidence rather than making up imaginary code?
   - *Target*: $100\%$.

---

## 2. Benchmark Query Categories in `questions.json`

| Category | Count | Primary Evaluation Focus |
|---|:---:|---|
| **EXACT_SYMBOL** | 4 | Tests whether specific class or method identifiers are prioritized |
| **CONCEPTUAL** | 4 | Tests multi-chunk semantic retrieval of architectural concepts |
| **PATH_TRACING** | 3 | Tests end-to-end caller-to-callee tracing across controllers and services |
| **NEGATIVE_UNANSWERABLE** | 4 | Tests refusal to hallucinate non-existent features or libraries |
| **Total** | **15** | Comprehensive MVP evaluation set |

---

## 3. Evaluation Execution Procedure
To run the evaluation:
1. Index the target repository (e.g. CodeMind itself or a public sample repo) via `POST /api/v1/repositories`.
2. Wait until `GET /api/v1/repositories/{id}` reports `READY`.
3. For each query in `questions.json`:
   - Send `POST /api/v1/repositories/{id}/search` and check if `results[0..4]` contain `expectedFiles` or `expectedSymbols`.
   - Send `POST /api/v1/repositories/{id}/ask` and check:
     - For answerable queries: `citations` list contains valid files, line ranges > 0, and non-empty answers.
     - For negative queries: answer indicates insufficient evidence and `citations` is empty.
4. Record observed metrics against the target criteria.
