#!/usr/bin/env bash
# Run with bash; never enable tracing because container credentials are used.
set +x
set -euo pipefail
umask 077

project_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
backup_root=/var/backups/individual-research
volume=research-backend-data
compose=(docker compose --project-directory "$project_dir" -f "$project_dir/compose.yaml")
restart_backend=0
work_dir=

fail() { printf 'Backup failed: %s\n' "$1" >&2; exit 1; }
resume_backend() {
    if [ "$restart_backend" -eq 1 ]; then
        if "${compose[@]}" start backend >/dev/null 2>&1; then
            restart_backend=0
        else
            printf 'Could not restart backend; manual intervention is required.\n' >&2
            return 1
        fi
    fi
}
cleanup() {
    result=$?
    trap - EXIT HUP INT TERM
    if ! resume_backend; then result=1; fi
    if [ "$result" -ne 0 ] && [ -n "$work_dir" ]; then
        printf 'Incomplete backup retained at: %s\n' "$work_dir" >&2
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

for tool in docker git flock gzip sha256sum; do
    command -v "$tool" >/dev/null || fail "Missing required tool: $tool"
done
mkdir -p -- "$backup_root"
chmod 700 "$backup_root"
exec 9>"$backup_root/.backup.lock"
flock -n 9 || fail 'Another backup is running.'

"${compose[@]}" config --quiet >/dev/null 2>&1 || fail 'Compose configuration is invalid.'
commit=$(git -c safe.directory="$project_dir" -C "$project_dir" rev-parse HEAD) || fail 'Cannot read Git commit.'
backend_id=$("${compose[@]}" ps -a -q backend 2>/dev/null) || fail 'Cannot find backend container.'
[ -n "$backend_id" ] || fail 'Create the backend container before backing up.'
backend_state=$(docker inspect --format '{{.State.Status}}' "$backend_id")
case "$backend_state" in
    running|exited|created) ;;
    *) fail 'Backend is not in a stable running/stopped state.' ;;
esac
backend_image=$(docker inspect --format '{{.Image}}' "$backend_id")
mounted_volume=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/storage"}}{{.Name}}{{end}}{{end}}' "$backend_id")
[ "$mounted_volume" = "$volume" ] || fail 'Unexpected backend storage volume.'
docker volume inspect "$volume" >/dev/null 2>&1 || fail 'Backend data volume does not exist.'
"${compose[@]}" exec -T mysql sh -c 'command -v mysqldump >/dev/null' >/dev/null 2>&1 || fail 'MySQL container is unavailable.'

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
final_dir="$backup_root/$timestamp"
work_dir="$final_dir.incomplete"
[ ! -e "$final_dir" ] || fail 'Backup timestamp already exists.'
mkdir -- "$work_dir"
printf 'Starting backup: %s\n' "$timestamp"
if [ "$backend_state" = running ]; then
    restart_backend=1
    "${compose[@]}" stop -t 60 backend >/dev/null 2>&1 || fail 'Could not stop backend.'
fi

# Expand credentials only inside the container, never in host command arguments.
# Suppress tool diagnostics; emit a generic error rather than risking credential logs.
if ! "${compose[@]}" exec -T mysql sh -c '
    set +x
    set -eu
    export MYSQL_PWD="$MYSQL_PASSWORD"
    exec mysqldump --user="$MYSQL_USER" --single-transaction --quick \
        --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"
' 2>/dev/null | gzip -1 >"$work_dir/database.sql.gz"; then
    fail 'Database dump or compression failed.'
fi

# Reuse the existing Ubuntu-based backend image: no new image pull or app startup.
if ! docker run --rm --network none --read-only --user 0:0 \
    --mount "type=volume,src=$volume,dst=/backup-volume,readonly" \
    --entrypoint tar "$backend_image" -C /backup-volume -cf - . \
    2>/dev/null | gzip -1 >"$work_dir/backend-data.tar.gz"; then
    fail 'Backend volume archive or compression failed.'
fi
resume_backend || fail 'Backend restart failed.'

gzip -t "$work_dir/database.sql.gz" "$work_dir/backend-data.tar.gz" || fail 'Archive integrity check failed.'
{
    printf 'backup_started_utc=%s\n' "$timestamp"
    printf 'backup_completed_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'git_commit=%s\n' "$commit"
    printf 'backend_image_id=%s\n' "$backend_image"
    printf 'backend_original_state=%s\n' "$backend_state"
    printf 'backend_volume=%s\n' "$volume"
    printf 'database_source=mysql service MYSQL_DATABASE\n'
} >"$work_dir/manifest.txt"
(
    cd -- "$work_dir"
    sha256sum database.sql.gz backend-data.tar.gz manifest.txt >SHA256SUMS
)
mv -- "$work_dir" "$final_dir"
work_dir=
printf 'Backup completed: %s\n' "$final_dir"
