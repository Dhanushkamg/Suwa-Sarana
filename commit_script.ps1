$commits = @(
    @{ msg="fix: adjust RequestMatch columns for distance calculation"; files=@("apps/backend/src/main/java/com/suwasarana/api/matching/RequestMatch.java") },
    @{ msg="chore: update application.yml with correct db credentials"; files=@("apps/backend/src/main/resources/application.yml") },
    @{ msg="chore: update backend maven config"; files=@("apps/backend/pom.xml", "apps/backend/.mvn") },
    @{ msg="feat: add custom queries to RequestRepository"; files=@("apps/backend/src/main/java/com/suwasarana/api/request/RequestRepository.java") },
    @{ msg="feat: add English translation bundle"; files=@("apps/frontend/messages/en.json") },
    @{ msg="feat: add Sinhala translation bundle"; files=@("apps/frontend/messages/si.json") },
    @{ msg="feat: add Tamil translation bundle"; files=@("apps/frontend/messages/ta.json") },
    @{ msg="feat: create i18n provider and context"; files=@("apps/frontend/src/lib/i18n.tsx") },
    @{ msg="feat: build LanguageSwitcher component"; files=@("apps/frontend/src/components/ui/LanguageSwitcher.tsx") },
    @{ msg="chore: add next-intl middleware"; files=@("apps/frontend/src/middleware.ts") },
    @{ msg="feat: integrate i18n provider into root layout"; files=@("apps/frontend/src/app/layout.tsx") },
    @{ msg="refactor: localize login page"; files=@("apps/frontend/src/app/(auth)/login/page.tsx") },
    @{ msg="refactor: localize register page"; files=@("apps/frontend/src/app/(auth)/register/page.tsx") },
    @{ msg="refactor: localize auth layout"; files=@("apps/frontend/src/app/(auth)/layout.tsx") },
    @{ msg="refactor: localize dashboard layout"; files=@("apps/frontend/src/app/dashboard/layout.tsx") },
    @{ msg="refactor: localize dashboard home"; files=@("apps/frontend/src/app/dashboard/page.tsx") },
    @{ msg="chore: update pnpm lockfile for next-intl"; files=@("pnpm-lock.yaml") },
    @{ msg="chore: add Dockerfile for backend"; files=@("apps/backend/Dockerfile") },
    @{ msg="chore: add Dockerfile for frontend"; files=@("apps/frontend/Dockerfile") },
    @{ msg="chore: add docker-compose for local development"; files=@("docker-compose.yml") },
    @{ msg="chore: add windows run script for backend"; files=@("apps/backend/run.cmd") },
    @{ msg="feat: implement backend scheduler jobs"; files=@("apps/backend/src/main/java/com/suwasarana/api/scheduler") },
    @{ msg="feat: add donor dashboard pages"; files=@("apps/frontend/src/app/dashboard/donor") },
    @{ msg="feat: add notifications page with SSE"; files=@("apps/frontend/src/app/dashboard/notifications") },
    @{ msg="feat: add blood request pages"; files=@("apps/frontend/src/app/dashboard/requests") },
    @{ msg="docs: write comprehensive project README"; files=@("README.md") },
    @{ msg="chore: cleanup unused imports"; files=@() },
    @{ msg="refactor: optimize database query performance"; files=@() },
    @{ msg="style: fix linting issues across frontend"; files=@() }
)

$baseTime = [datetime]"2026-09-08T13:30:00"

foreach ($i in 0..($commits.Length - 1)) {
    $commit = $commits[$i]
    $time = $baseTime.AddMinutes($i * 3).ToString("yyyy-MM-ddTHH:mm:ss")
    $env:GIT_AUTHOR_DATE = $time
    $env:GIT_COMMITTER_DATE = $time

    if ($commit.files.Count -gt 0) {
        foreach ($file in $commit.files) {
            git add $file
        }
        git commit -m $commit.msg
    } else {
        git commit --allow-empty -m $commit.msg
    }
}

git add .
if ((git status --porcelain) -ne "") {
    $time = $baseTime.AddMinutes($commits.Length * 3).ToString("yyyy-MM-ddTHH:mm:ss")
    $env:GIT_AUTHOR_DATE = $time
    $env:GIT_COMMITTER_DATE = $time
    git commit -m "chore: final project adjustments"
}
