### 1. Entity Definitions
```
Pet:
- id: string (business id from source)
- name: string (pet name)
- species: string (dog/cat/other)
- breed: string (breed name)
- age_value: number (numeric age)
- age_unit: string (years|months)
- sex: string (M|F)
- size: string (small|medium|large)
- temperament_tags: array(string) (tags like calm|active)
- photos: array(string) (urls)
- location: object (city, postal, lat, lon)
- health_status: string (vaccinated|needs_care)
- availability_status: string (available|adopted|pending)
- created_at: datetime (source timestamp)

SearchFilter:
- id: string (business id)
- user_id: string (owner)
- name: string (optional)
- species: string
- breeds: array(string)
- age_min: number
- age_max: number
- age_unit_preference: string
- size: array(string)
- sex: string
- location_center: object (lat, lon, city)
- radius_km: number
- vaccination_required: boolean
- temperament_tags: array(string)
- sort_by: string
- page_size: number
- created_at: datetime
- is_active: boolean

TransformJob:
- id: string (business id)
- job_type: string (search_transform|bulk_transform)
- created_by: string (user or system)
- search_filter_id: string (nullable)
- rule_names: array(string) (transformation rule ids/names)
- priority: number
- status: string (PENDING|QUEUED|RUNNING|COMPLETED|FAILED)
- started_at: datetime
- completed_at: datetime
- result_count: number
- error_message: string
- output_location: string (where results are stored)
```

### 2. Entity workflows

Pet workflow:
1. Initial State: Pet persisted (PENDING_VERIFICATION) — automatic event from ingestion.
2. Validation: Validate data completeness and geo validity.
3. Enrichment: Enrich tags, normalize age, map region.
4. Approval: Automatic approval or manual review if suspicious.
5. Publication: Mark PUBLISHED for searches or ARCHIVED if invalid.

```mermaid
stateDiagram-v2
    [*] --> "PENDING_VERIFICATION"
    "PENDING_VERIFICATION" --> "ENRICHMENT" : ValidatePetCriterion automatic
    "ENRICHMENT" --> "AWAITING_APPROVAL" : EnrichmentProcessor automatic
    "AWAITING_APPROVAL" --> "PUBLISHED" : ManualApprovalProcessor manual
    "AWAITING_APPROVAL" --> "PUBLISHED" : AutoApproveCriterion automatic
    "PUBLISHED" --> "ARCHIVED" : ArchiveProcessor automatic
    "PUBLISHED" --> [*] : Completed
```

Pet processors and criteria
- Criteria: ValidatePetCriterion, GeoValidCriterion
- Processors: EnrichmentProcessor, TransformationProcessor, ManualApprovalProcessor, PublishProcessor

Pseudo code (very short)
```
class EnrichmentProcessor {
  process(pet) {
    pet.temperament_tags = inferTags(pet.description)
    pet.age_value = normalizeAge(pet.age_value, pet.age_unit)
    return pet
  }
}
```

SearchFilter workflow:
1. Created: User persists filter -> CREATED.
2. Validation: Check filter fields and location.
3. Active: Becomes ACTIVE and may schedule triggers/alerts.
4. Triggered: When user runs search or schedule triggers -> create TransformJob.
5. Disabled: User disables -> INACTIVE.

```mermaid
stateDiagram-v2
    [*] --> "CREATED"
    "CREATED" --> "VALIDATED" : ValidateSearchFilterCriterion automatic
    "VALIDATED" --> "ACTIVE" : ActivateProcessor automatic
    "ACTIVE" --> "TRIGGERED" : EnqueueSearchJobProcessor manual
    "TRIGGERED" --> "ACTIVE" : Completed
    "ACTIVE" --> "INACTIVE" : DisableFilterProcessor manual
```

Processors and criteria
- Criteria: ValidateSearchFilterCriterion
- Processors: ActivateProcessor, EnqueueSearchJobProcessor, NotifyOnMatchProcessor

TransformJob workflow:
1. Initial State: Job created with PENDING.
2. Queued: Job queued for processing.
3. Running: Fetch matching Pet records.
4. Transforming: Apply transformation rules to each Pet.
5. Completion: SUCCESS or FAILED.
6. Notification: Store results and notify requester.

```mermaid
stateDiagram-v2
    [*] --> "PENDING"
    "PENDING" --> "QUEUED" : QueueJobProcessor automatic
    "QUEUED" --> "RUNNING" : StartJobProcessor automatic
    "RUNNING" --> "TRANSFORMING" : FetchPetsProcessor automatic
    "TRANSFORMING" --> "COMPLETED" : ApplyTransformationProcessor automatic
    "TRANSFORMING" --> "FAILED" : ErrorCriterion automatic
    "COMPLETED" --> "NOTIFIED" : NotifyUsersProcessor automatic
    "NOTIFIED" --> [*]
    "FAILED" --> [*]
```

Processors and criteria
- Criteria: HasSearchFilterCriterion, ErrorCriterion
- Processors: QueueJobProcessor, StartJobProcessor, FetchPetsProcessor, ApplyTransformationProcessor, NotifyUsersProcessor

Small pseudo code for job processor
```
class ApplyTransformationProcessor {
  process(job) {
    pets = fetchMatchingPets(job.search_filter_id)
    transformed = []
    for pet in pets {
      for rule in job.rule_names {
        pet = applyRule(rule, pet)
      }
      transformed.add(pet)
    }
    storeResults(job.id, transformed)
    job.result_count = transformed.size
    job.status = COMPLETED
  }
}
```

### 3. Pseudo code for processor classes
(see short examples above: EnrichmentProcessor, ApplyTransformationProcessor)
Also:
```
class FetchPetsProcessor {
  process(job) {
    filter = loadFilter(job.search_filter_id)
    return queryPets(filter, job.priority, job.page_size)
  }
}
class NotifyUsersProcessor {
  process(job) {
    if job.result_count>0 then notify(job.created_by, job.output_location)
  }
}
```

### 4. API Endpoints Design Rules

Rules followed: POST triggers events and returns only technicalId. GET endpoints only for retrieval by technicalId.

Endpoints

1) Create SearchFilter (triggers validation & activation)
- POST /api/searchfilters
Request:
```json
{
  "user_id":"user-123",
  "name":"Nearby Puppies",
  "species":"dog",
  "breeds":["beagle"],
  "age_min":0,
  "age_max":24,
  "age_unit_preference":"months",
  "location_center":{"lat":52.1,"lon":5.1,"city":"City"},
  "radius_km":30,
  "vaccination_required":true,
  "temperament_tags":["playful"],
  "sort_by":"distance",
  "page_size":20
}
```
Response:
```json
{ "technicalId":"job-abc-123" }
```

- GET /api/searchfilters/{technicalId}
Response:
```json
{
  "technicalId":"job-abc-123",
  "id":"sf-1",
  "user_id":"user-123",
  "name":"Nearby Puppies",
  "is_active":true,
  "...": "other fields"
}
```

2) Create TransformJob (orchestration) — runs search+transform
- POST /api/transform-jobs
Request:
```json
{
  "job_type":"search_transform",
  "created_by":"user-123",
  "search_filter_id":"sf-1",
  "rule_names":["normalize_age","map_region"],
  "priority":5
}
```
Response:
```json
{ "technicalId":"tjob-789" }
```

- GET /api/transform-jobs/{technicalId}
Response:
```json
{
  "technicalId":"tjob-789",
  "id":"tj-1",
  "status":"COMPLETED",
  "result_count":12,
  "output_location":"/results/tj-1.json"
}
```

3) Get Pet result by id (read-only)
- GET /api/pets/{id}
Response:
```json
{
  "id":"pet-42",
  "name":"Buddy",
  "species":"dog",
  "age_value":2,
  "age_unit":"years",
  "location":{"city":"City","lat":52.1,"lon":5.1},
  "availability_status":"available"
}
```

Notes:
- If you want TransformationRule as an editable entity, or saved favorites, I can expand to up to 10 entities — tell me and I will produce expanded entities, workflows, processors and endpoints.