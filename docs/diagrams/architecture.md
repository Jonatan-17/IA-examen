# Architecture

The frontend only talks to Spring Boot. Spring Boot owns the business rules and the relational data, and it is
the only component that calls n8n. n8n orchestrates the AI: embeddings and chat through OpenRouter, vector
search in Qdrant. Keys live in environment variables (`.env`), never in code or workflows.

## Components

```mermaid
flowchart TB
    user([User]) --> fe

    subgraph browser [Browser]
        fe["Frontend<br/>HTML · CSS · JavaScript<br/>served by nginx"]
    end

    subgraph app [Spring Boot API]
        api["REST controllers<br/>validation · DTOs"]
        svc["Services<br/>incidents · knowledge base · analytics<br/>AiQueryService · IndexingService"]
        client["N8nClient<br/>secret header · timeouts"]
        api --> svc --> client
    end

    db[("PostgreSQL<br/>incidents · articles · AI history")]

    subgraph ai [AI orchestration]
        n8n["n8n<br/>flow 1: indexing<br/>flow 2: RAG assistant"]
        qdrant[("Qdrant<br/>knowledge_articles<br/>1024 dims, cosine")]
        or["OpenRouter<br/>bge-m3 embeddings<br/>Claude Haiku 5.5 chat"]
        n8n --> qdrant
        n8n --> or
    end

    fe -- "/api (JSON)" --> api
    svc -- "JPA" --> db
    client -- "webhooks + X-Webhook-Secret" --> n8n
```

## RAG query (RF04 / RF05)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Frontend
    participant SB as Spring Boot
    participant DB as PostgreSQL
    participant N8 as n8n (flow 2)
    participant OR as OpenRouter
    participant QD as Qdrant

    FE->>SB: POST /api/incidents/{id}/assistance {description}
    alt blank description
        SB-->>FE: 400 (AI flow never runs)
    end
    SB->>DB: save query as PENDING
    SB->>N8: description + threshold + topK
    N8->>OR: embedding of the description
    N8->>QD: search with score_threshold
    alt no article passes the threshold
        N8-->>SB: NO_RESULTS (model not called)
    else sources found
        N8->>OR: grounded prompt with the sources (JSON answer)
        alt model says the sources do not cover it
            N8-->>SB: NO_RESULTS
        else
            N8-->>SB: ANSWERED + answer + sources
        end
    end
    opt ANSWERED
        SB->>DB: re-check each source exists, is active and verified
        SB->>DB: save answer and sources (or NO_RESULTS if none is valid)
    end
    alt n8n, Qdrant or OpenRouter fail
        SB->>DB: query ERROR + reason
    end
    SB-->>FE: status, answer, steps, sources with similarity
```

## Indexing (RF02 / RF03)

```mermaid
sequenceDiagram
    autonumber
    participant SB as Spring Boot
    participant DB as PostgreSQL
    participant N8 as n8n (flow 1)
    participant OR as OpenRouter
    participant QD as Qdrant

    SB->>DB: create, update, activate or deactivate an article
    Note over SB: after the transaction commits
    SB->>N8: article (active = active and verified)
    alt active and verified
        N8->>OR: embedding of title + problem + symptoms + cause + solution
        N8->>QD: upsert point id = article id (whole article in payload)
    else inactive or unverified
        N8->>QD: delete point
    end
    N8-->>SB: indexed, deleted, errors
    Note over SB: failures are logged, never break the CRUD.<br/>POST /api/knowledge-articles/reindex recovers.
```

## Safeguards against made-up answers

1. **Threshold** (0.60, calibrated): below it the model is never called.
2. **Prompt:** the model may answer `relevant: false` when the sources do not cover the problem.
3. **Database check:** Spring Boot drops any source that no longer exists or is inactive or unverified.
