# GitHub Actions Workflows

## Build Workflow

The `build.yml` workflow allows you to build the application on any branch via API call.

### Triggering the Workflow

#### Via GitHub UI
1. Go to the "Actions" tab in your GitHub repository
2. Select "Build Application" workflow
3. Click "Run workflow"
4. Enter the branch name you want to build
5. Select the build type (standard, workflow-import, or both)
6. Click "Run workflow"

#### Via GitHub API
You can trigger the workflow programmatically using the GitHub REST API:

```bash
curl -X POST \
  -H "Accept: application/vnd.github.v3+json" \
  -H "Authorization: token YOUR_GITHUB_TOKEN" \
  https://api.github.com/repos/YOUR_USERNAME/YOUR_REPO/actions/workflows/build.yml/dispatches \
  -d '{
    "ref": "main",
    "inputs": {
      "branch": "feature-branch-name",
      "build_type": "standard"
    }
  }'
```

#### Via GitHub CLI
```bash
gh workflow run build.yml \
  --field branch=feature-branch-name \
  --field build_type=standard
```

### Parameters

- **branch** (required): The branch to checkout and build
  - Default: `main`
  - Example: `feature/new-functionality`, `develop`, `release/v1.2.0`

- **build_type** (optional): Type of build to perform
  - `standard`: Builds the main application JAR
  - `workflow-import`: Builds the workflow import tool JAR
  - `both`: Builds both JARs
  - Default: `standard`

### What the Workflow Does

1. **Checkout**: Checks out the specified branch
2. **Setup**: Sets up Java 21 with Temurin distribution
3. **Cache**: Caches Gradle dependencies for faster builds
4. **Test**: Runs all unit tests
5. **Build**: Builds the requested JAR(s) based on build_type parameter
6. **Artifacts**: Uploads build artifacts and test results
7. **Summary**: Provides a build summary

### Artifacts

The workflow generates the following artifacts:

- **standard-jar-{branch}**: Main application JAR file
- **workflow-import-jar-{branch}**: Workflow import tool JAR file
- **test-results-{branch}**: JUnit test results (XML format)
- **test-reports-{branch}**: HTML test reports

Artifacts are retained for 30 days (JARs) or 7 days (test results).

### Requirements

- Repository must have Java 21 compatible code
- Gradle wrapper must be present and executable
- All dependencies must be available in Maven Central

### Security Notes

- The workflow requires appropriate GitHub token permissions
- For API calls, ensure your token has `actions:write` permission
- Consider using environment-specific tokens for production deployments
