# 운영 데이터 백업과 수동 복원

## 백업 실행

EC2 Ubuntu에서 저장소 루트로 이동한 뒤 실행한다. 실제 `.env`는 EC2의
저장소 루트에 준비되어 있어야 한다. 값은 출력하거나 문서에 기록하지 않는다.

```bash
sudo bash scripts/backup.sh
```

Docker Compose, Git, flock, gzip, sha256sum이 필요하다. 현재 backend 이미지의
tar를 재사용하므로 추가 이미지 다운로드가 없다. `/var/backups`에 충분한 공간을
확보하고 GitHub Actions 배포 및 다른 DB 쓰기 작업과 겹치지 않는 시간에 실행한다.
백업 잠금은 다른 백업만 막으며 현재 배포 workflow와 공유되지 않는다.

원래 실행 중이던 backend만 일시 중지하고 MySQL과 frontend는 유지한다.
이 동안 API는 사용할 수 없다. DB dump와 파일 압축은 순차 실행하고 gzip 레벨 1을
사용한다. backend 중지는 최대 60초를 기다린다. 강제 종료된 요청은 완료되지 않을 수
있으므로 가급적 요청이 없는 시간에 실행한다. 기존에 중지된 backend는 시작하지 않는다.

EXIT/HUP/INT/TERM trap은 원래 실행 중이던 backend를 다시 시작한다. SIGKILL,
호스트 종료, Docker 장애는 복구를 보장할 수 없으며 재시작 실패는 오류로 알린다.
실행 후 API가 정상인지 확인한다. backend 재시작 성공은 앱 readiness 검증이 아니다.
`bash -x`, `set -x`, 환경변수 출력, 비밀번호를 포함한 명령행 인수는 사용하지 않는다.
비밀번호는 MySQL 컨테이너 내부의 MYSQL_PWD 환경으로만 전달한다.
Docker/root 권한을 가진 관리자는 이 환경에 접근할 수 있다.

## 생성 파일

`/var/backups/individual-research/<UTC timestamp>/`에 다음 파일이 생성된다.

- `database.sql.gz`: MYSQL_DATABASE의 테이블 정의와 데이터. 현재 코드에 없는
  저장 프로시저·이벤트나 MySQL 시스템 계정 전체는 백업하지 않는다.
- `backend-data.tar.gz`: research-backend-data 전체. 숨김 파일·소유권·권한 포함.
- `manifest.txt`: 백업 시각, Git commit, backend 이미지 ID 등 비밀이 아닌 메타데이터.
- `SHA256SUMS`: 위 세 파일의 SHA-256 체크섬.

작업 중/실패한 세트에는 `.incomplete` 접미사가 붙는다. 체크섬 작성과 backend
재시작까지 성공해야 최종 이름으로 바뀐다. 실패한 세트는 복원용으로 사용하지 않는다.
자동 삭제는 없으므로 디스크 사용량을 확인하고 성공 백업 보관 수를 직접 관리한다.
디렉터리는 700, 새 파일은 umask 077로 보호한다. dump 자체에는 개인정보와 비밀번호
해시가 있으므로 내용을 로그로 출력하지 않는다. 압축 검증은 실제 복원 시험을 대신하지 않는다.

EC2 디스크 유실에 대비해 완성된 세트 전체를 접근이 제한된 PC 또는 비공개 외부
저장소에도 복사한다. 외부 사본도 암호화·접근 제한을 적용한다. `.env`는 이 백업에
포함되지 않으므로 별도로 안전하게 보관한다. Git에 올리지 않는다.

## 새 EC2에서 수동 복원

아래 명령은 **새 서버의 빈 볼륨**을 대상으로 한다. 기존 운영 DB에 실행하면 데이터가
덮어써질 수 있다. 백업의 manifest에 기록된 코드 버전을 준비하고 MySQL 8.4를 사용한다.
모든 명령은 저장소 루트에서 실행한다. Docker 명령은 접근 권한이 있는 계정으로 실행한다.
백업 파일 권한 때문에 필요하면 보호된 root shell에서 실행하되 tracing은 켜지 않는다.

1. Git 저장소와 Docker Compose를 준비하고, 별도 보관한 `.env`를 저장소 루트에
   안전하게 배치한다. 권한은 600으로 제한한다. 아래 경로의 timestamp는 실제 성공
   백업 디렉터리로 바꾼다.

   ```bash
   set -euo pipefail
   BACKUP_DIR=/var/backups/individual-research/REPLACE_WITH_TIMESTAMP
   (cd "$BACKUP_DIR" && sha256sum -c SHA256SUMS)
   gzip -t "$BACKUP_DIR/database.sql.gz" "$BACKUP_DIR/backend-data.tar.gz"
   docker compose config --quiet
   ```

2. 이름이 같은 기존 볼륨이 없는 **새 서버인지 먼저 확인**한 뒤 외부 볼륨을 생성한다.
   MySQL만 시작한다. backend는 DB와 파일 복원이 끝나기 전까지 실행하지 않는다.

   ```bash
   docker volume ls
   docker volume create research-mysql-data
   docker volume create research-backend-data
   docker compose up -d mysql
   docker compose ps mysql
   ```

   MySQL 상태가 healthy가 될 때까지 기다린다. 새 빈 볼륨에서는 Compose의 환경변수로
   DB와 사용자가 생성된다. 시스템 DB를 통째로 복원할 필요는 없다.

3. 애플리케이션 DB에 dump를 복원한다. 비밀번호는 컨테이너 내부에서만 참조한다.
   오류가 나면 중단하고 다음 단계로 넘어가지 않는다.

   ```bash
   gzip -dc "$BACKUP_DIR/database.sql.gz" |
     docker compose exec -T mysql sh -c '
       set +x
       set -eu
       export MYSQL_PWD="$MYSQL_PASSWORD"
       exec mysql --user="$MYSQL_USER" "$MYSQL_DATABASE"
     ' 2>/dev/null
   ```

4. backend 이미지만 빌드하고, 앱을 실행하지 않는 임시 컨테이너로 파일을 복원한다.
   기존 내용에 덮어쓰지 말고 빈 research-backend-data를 사용한다.

   ```bash
   docker compose build backend
   gzip -dc "$BACKUP_DIR/backend-data.tar.gz" |
     docker compose run --rm --no-deps -T --user 0:0 --entrypoint tar \
       backend -C /app/storage -xpf -
   docker compose run --rm --no-deps -T --user 0:0 --entrypoint sh backend -c \
     'chown -R "$(id -u app):$(id -g app)" /app/storage'
   ```

   DB에는 파일 절대 경로가 저장되므로 `/app/storage` 마운트 경로를 유지한다.
   새 이미지에서 UID/GID가 달라질 수 있어 app 사용자에 맞춰 소유권을 정리한다.

5. 새 EC2에 도메인 연결과 80/443 접근을 준비한다. 현재 최종 Nginx 설정은 인증서가
   없으면 시작하지 못한다. frontend를 시작하기 전에 Certbot standalone 방식 등으로
   인증서를 재발급한다. HTTP-01 standalone 발급 동안 호스트 80 포트는 비어 있어야 한다.
   도메인이 새 서버를 가리키는지 확인하고 다음 경로를 준비한다.

   ```text
   /etc/letsencrypt/live/dongguk-research.duckdns.org/fullchain.pem
   /etc/letsencrypt/live/dongguk-research.duckdns.org/privkey.pem
   /var/www/certbot/.well-known/acme-challenge/
   ```

   frontend 시작 후 갱신 방식을 webroot `/var/www/certbot`으로 구성하고 Certbot
   자동 갱신 timer 및 성공 후 Nginx reload hook을 재구성한다. standalone 갱신 설정을
   그대로 두면 실행 중인 frontend의 80 포트와 충돌한다. hook에는 저장소 경로를 명시해
   `docker compose exec -T frontend nginx -t` 성공 후 `nginx -s reload`를 실행한다.
   `certbot renew --dry-run`과 reload hook을 각각 검증한다.

6. 전체 서비스를 시작하고 실제 복구 여부를 확인한다.

   ```bash
   docker compose build frontend
   docker compose up -d
   docker compose exec -T frontend nginx -t
   docker compose ps
   ```

   HTTPS 응답, 로그인, 기존 신청서, 파일 다운로드·업로드, 문서 생성을 확인한다.
   backend는 시작 시 테이블 초기화와 일부 데이터 보정을 수행하므로 먼저 같은 코드
   버전으로 복원한다. APP_AUTH_SECRET을 변경하면 기존 로그인 토큰은 무효화될 수 있다.
   코드 외부의 data/ 또는 샘플 파일을 별도로 사용했다면 이 두 볼륨 백업에 포함되지
   않으므로 따로 이전한다.
