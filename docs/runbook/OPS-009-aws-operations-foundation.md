# OPS-009 DEPLOY-001 AWS 운영 기반 Runbook

## 문서 상태와 권위

- 작업 ID: `OPS-009`
- 작업 등급: 고위험
- 기능군: `DEPLOY-001` AWS 운영 기반
- 실행 주체: 사용자/운영자
- 적용 시점: 이 문서가 병합된 `main`을 확인한 뒤
- 대상 Region: `ap-northeast-2`

이 문서는 AWS Console에서 Budget, EC2 instance role, Security Group, EC2·EBS, Elastic IP와 접속 기반을 생성하고 검증한 뒤 필요하면 안전하게 정리하는 절차다. 저장소 변경만으로 AWS 리소스가 생성되지는 않는다. 실제 계정 상태와 비용은 AWS Console, 실제 변경 이력은 CloudTrail, 저장소 검증 상태는 GitHub Checks를 권위 원본으로 삼는다.

AWS 화면의 항목명이나 동작이 이 문서와 다르거나 승인된 값 이외의 유료 서비스가 필요하면 진행하지 않는다. 실제 계정 ID, ARN, 공인 IP, 이메일, key pair, Secret, Webhook URL은 저장소 문서나 이슈·PR에 기록하지 않는다.

## 범위

### 포함

- 월간 비용 Budget 두 개와 이메일 알림
- EC2가 SSM에 등록되기 위한 instance role
- 외부 공개 포트와 내부 비공개 포트를 분리한 Security Group
- Ubuntu Server 24.04 LTS, `t3.small`, 암호화된 gp3 40 GiB EC2 기반
- Elastic IP 할당·연결과 SSM 기본 접속
- 사용자 IP로만 제한하는 임시 비상 SSH
- Docker Engine과 Compose plugin 설치, 재부팅 후 자동 시작 확인
- 중지·재시작 검증과 실패 시 역순 정리

### 제외

- AWS CLI나 Infrastructure as Code 자동화
- production Compose, 배포 script, GHCR workflow
- 애플리케이션, MySQL, TLS, DuckDNS 실제 배포
- SSM Parameter Store, CloudWatch agent·알림, S3 backup·restore
- RDS, NAT Gateway, Load Balancer, Blue·Green, Spring Session JDBC
- 실제 Secret이나 계정 식별자의 저장

후속 단계의 후보 값은 다음과 같지만 이번 Runbook에서 생성하지 않는다.

- 같은 EC2에서 실행할 MySQL
- `pawcycle-commerce.duckdns.org` DuckDNS 후보
- `pawcycle-commerce-prod-backup-ap-northeast-2-<무작위값>` S3 이름 규칙
- GitHub Actions에서 공개 GHCR image를 commit SHA tag로 빌드하고 EC2에서 pull하는 방식
- 수동 단일 release 성공과 자원 검증 뒤의 Blue·Green

## 승인된 기준값

| 항목 | 승인값 | 적용 전 확인 |
| --- | --- | --- |
| Region | `ap-northeast-2` | Console 상단 Region을 단계마다 확인 |
| OS | Ubuntu Server 24.04 LTS | Canonical 게시자와 x86_64 아키텍처 확인 |
| Instance | `t3.small`, Credit specification `standard` | 선택 화면과 생성 후 유형·credit mode 확인 |
| Root EBS | gp3 40 GiB, 암호화 | 암호화 활성화와 `Delete on termination=No` 확인 |
| 고정 IP | Elastic IP | EC2와 같은 Region에서 할당·연결 |
| 기본 접속 | SSM Session Manager | 인바운드 포트 없이 Managed node와 세션 확인 |
| 비상 접속 | SSH 22, 현재 사용자 공인 IP 한 개 | 필요할 때만 `/32` 또는 `/128`, 종료 즉시 제거 |
| 비용 목표 | 실제 결제 0 USD 목표 | Gross 30 USD와 Net 1 USD Budget을 함께 감시 |

`Delete on termination=No`는 실수로 instance를 종료해도 root EBS를 보존하기 위한 OPS-009 기본값이다. 보존된 EBS와 할당된 Elastic IP에는 계속 비용이 발생할 수 있으므로 정리 단계에서 각각 별도 결정을 내린다.

### 리소스 식별 계약

생성과 정리는 아래 이름·지원 tag·저장소 외부 매핑을 동일하게 사용한다. 공통 tag는 `Project=pawcycle-commerce`, `Environment=prod`, `Task=OPS-009`이며 개인정보나 계정 식별자를 값에 넣지 않는다. 저장소 외부 비공개 운영 기록에는 리소스 종류, 아래 논리 이름, Console에서 확인한 실제 식별자와 연결 관계, 생성·정리 상태를 매핑한다. 전체 resource ID·ARN을 저장소, PR, 이슈에 복사하지 않는다.

| 리소스 | 논리 이름 | Console tag와 외부 매핑 |
| --- | --- | --- |
| Gross Budget | `pawcycle-commerce-prod-gross-monthly` | Budget tag에 공통 tag 적용, 이름과 알림 상태 매핑 |
| Net Budget | `pawcycle-commerce-prod-net-monthly` | Budget tag에 공통 tag 적용, 이름과 알림 상태 매핑 |
| IAM role/profile | `pawcycle-commerce-prod-ec2-ssm` | role에 공통 tag 적용. IAM Console이 만드는 same-name instance profile을 한 쌍으로 매핑 |
| Security Group | `pawcycle-commerce-prod-web-sg` | 공통 tag 적용, VPC와 연결 instance 매핑 |
| EC2 instance | `pawcycle-commerce-prod-app-01` | `Name`과 공통 tag 적용, root EBS·EIP·Security Group·profile 연결 매핑 |
| Root EBS | `pawcycle-commerce-prod-root-ebs` | `Name`과 공통 tag 적용, instance의 root device 관계 매핑 |
| Elastic IP | `pawcycle-commerce-prod-eip` | `Name`과 공통 tag 적용, 연결 instance와 DNS 의존성 상태 매핑 |

IAM instance profile tag는 CLI/API에서 관리해야 하므로 Console-only인 OPS-009에서는 추가하지 않는다. role과 profile 이름이 다르거나 외부 매핑이 일치하지 않으면 생성·정리를 중단하며 CLI/API로 보정하지 않는다.

## 생성 전 게이트

아래 항목을 모두 충족하지 않으면 AWS 리소스를 만들지 않는다.

- [ ] 이 Runbook이 병합된 최신 `main`을 읽고 있다.
- [ ] 로그인한 AWS 계정과 `ap-northeast-2`가 의도한 대상임을 화면에서 확인했다.
- [ ] root user 일상 사용이나 장기 access key 대신 MFA가 적용된 사용자 세션 또는 임시 자격 증명을 사용한다.
- [ ] Billing Console 접근 권한과 알림을 받을 이메일을 확인했다.
- [ ] Billing Console의 Credits에서 적용 가능 서비스, 잔액 갱신 시점, 만료일을 직접 확인했다.
- [ ] 크레딧이 비용 발생 자체를 막지 않으며 Budget이 지출을 강제로 차단하지 않는다는 점을 확인했다.
- [ ] 현재 AWS Pricing과 예상 월 비용을 다시 확인했고 Gross 30 USD, Net 1 USD 기준 안에서 진행하기로 판단했다.
- [ ] 리소스 식별 계약의 이름·tag와 저장소 외부 매핑으로 같은 목적의 Budget, role/profile, Security Group, EC2, EBS, Elastic IP가 이미 존재하지 않음을 확인했다.
- [ ] 사용할 기존 VPC와 public subnet이 있고 subnet route가 Internet Gateway로 연결된다. 신규 VPC, NAT Gateway 또는 VPC endpoint가 필요하면 OPS-009를 중단한다.
- [ ] Security Group의 80·443 외 인바운드를 기본 폐쇄할 수 있다.
- [ ] SSM용 아웃바운드 HTTPS, Ubuntu OS package, `download.docker.com`, Docker Hub의 `hello-world` image pull과 향후 공개 GHCR용 인터넷 경로가 있다.
- [ ] 실패 시 이 문서의 역순 정리를 수행할 시간과 권한이 있다.
- [ ] 실제 식별자와 민감정보를 저장소에 남기지 않을 기록 위치를 준비했다.

### 즉시 중단 조건

- 계정이나 Region이 불명확하다.
- Budget 생성 또는 알림 구독을 확인할 수 없다.
- EBS 암호화, SSM instance role 또는 제한된 Security Group을 적용할 수 없다.
- `AdministratorAccess`, `AmazonS3FullAccess`, `CloudWatchFullAccess` 같은 광범위 권한이 필요하다는 안내를 받는다.
- SSH를 `0.0.0.0/0` 또는 `::/0`에 열어야만 진행할 수 있다.
- RDS, NAT Gateway, Load Balancer, 유료 모니터링 도구 등 승인되지 않은 리소스가 필요하다.
- 실제 Secret, 계정 ID, ARN 또는 공인 IP를 저장소에 기록해야 한다.

## 사용자 영향

이 단계에서는 애플리케이션을 배포하지 않으므로 사용자-facing 기능 영향은 없다. 다만 AWS 리소스를 생성하는 순간부터 EC2, EBS와 public IPv4 비용이 발생할 수 있다. Budget 알림은 비용을 차단하지 않으며 AWS 비용 데이터는 실시간이 아닐 수 있다.

## 증상

이 Runbook으로 다루는 대표 실패 신호는 Budget 알림 미수신, instance의 SSM Managed node 미등록, 의도하지 않은 inbound 규칙, 암호화·보존 설정 불일치, Docker 미기동, 재부팅 또는 stop/start 뒤 SSM·Docker 복구 실패다. 하나라도 발생하면 다음 단계를 실행하지 않고 `첫 확인 절차`와 `안전 정리와 원상 복구`를 따른다.

## 사용자 실행 순서

각 단계는 `실행 → 성공 확인 → 실패 시 중단·정리` 순으로 끝낸 뒤 다음 단계로 이동한다. 여러 단계를 한 번에 만들지 않는다.

### 1. Credits와 Budget 선행 확인

1. Billing and Cost Management의 Credits에서 사용할 수 있는 크레딧의 적용 서비스와 만료일을 확인한다.
2. 리소스 식별 계약의 이름과 공통 tag로 아래 월간 Cost budget 두 개를 만든다. Budget action이나 자동 리소스 중지는 구성하지 않는다.

| 구분 | 금액 | Credits | Refunds | 권장 알림 |
| --- | ---: | --- | --- | --- |
| Gross | 30 USD | 제외 | 제외 | Actual 50%, 80%, 100%; Forecasted 100% |
| Net | 1 USD | 포함 | 포함 | Actual 50%, 80%, 100%; Forecasted 100% |

1. 각 알림을 운영자가 확인하는 이메일에 연결하고 AWS의 구독 확인 절차가 표시되면 완료한다.
2. Budget 상세 화면에서 이름·공통 tag, 기간 Monthly, 금액, actual/forecast 알림, Credits·Refunds 포함 여부가 표와 일치하는지 다시 확인하고 외부 매핑에 기록한다.
3. 증거에는 Gross 30 USD·Net 1 USD 금액, Credits·Refunds 포함 여부, Actual 50%·80%·100%와 Forecasted 100% 알림 기준, 구독 확인, Credits 적용 서비스·만료일 검토 완료 여부를 남긴다. 이메일 주소, credit 잔액, 계정 ID는 저장소에 남기지 않는다.

성공 기준은 Budget 두 개가 올바른 비용 유형과 알림으로 표시되는 것이다. 알림을 받을 수 없거나 비용 유형이 불명확하면 Budget을 삭제하지 말고 화면을 벗어나 운영자 판단을 요청한다.

Forecasted 100% 알림은 충분한 과거 사용 정보가 쌓이기 전에는 계산·발송되지 않을 수 있다. AWS는 Budget forecast 생성에 약 5주의 사용 데이터가 필요하다고 안내하므로 초기에는 Forecasted 알림 미수신만으로 설정 실패로 판단하지 않는다. Actual 알림의 기준·구독 상태와 Billing/Budgets 화면의 실제 발생 비용을 우선 확인한다.

### 2. 최소 권한 EC2 instance role 생성

1. IAM Console에서 EC2 use case의 role을 리소스 식별 계약의 이름과 공통 tag로 만든다. Console이 자동 생성하는 same-name instance profile을 외부 매핑에 같은 쌍으로 기록한다. 이 role은 EC2 서비스가 assume하며 사람의 로그인이나 장기 access key에 사용하지 않는다.
2. 현재 단계에서는 AWS 관리형 정책 `AmazonSSMManagedInstanceCore` 하나만 연결한다.
3. role에 인라인 `s3:*`, `logs:*`, `cloudwatch:*`, `iam:*` 권한을 추가하지 않는다.
4. 생성 후 Trust relationship의 서비스가 EC2이고 연결 정책이 하나뿐이며 role과 instance profile 이름이 같은지 확인한다.

향후 권한은 해당 기능이 승인되고 실제 리소스가 생성된 뒤 별도 변경으로 추가한다.

| 향후 기능 | 허용 경계 | 금지 기본안 |
| --- | --- | --- |
| S3 backup·restore | 확정된 backup bucket과 object prefix에 필요한 `ListBucket`, `PutObject`, `GetObject`만 분리 정책으로 허용 | `s3:*`, 모든 bucket, 계정 전체 resource |
| CloudWatch Logs | 확정된 log group과 stream에 생성·쓰기만 허용 | `CloudWatchFullAccess`, 모든 log group 관리 |
| CloudWatch metric | 승인된 namespace의 `PutMetricData`만 조건으로 제한 | 임의 namespace와 관리·삭제 권한 |

EBS는 AWS 관리형 EBS 암호화 키를 사용하므로 OPS-009 instance role에 KMS 권한을 추가하지 않는다. 고객 관리형 KMS key가 필요하면 별도 승인 전까지 중단한다.

성공 기준은 EC2 신뢰 관계, `AmazonSSMManagedInstanceCore` 단일 연결, access key 부재, role의 공통 tag와 same-name profile 외부 매핑 일치다. 광범위 권한이 섞였으면 instance에 연결하지 말고 Console 정리 절차로 role/profile을 삭제한 뒤 원인을 기록한다.

### 3. Security Group 생성

Security Group을 `ap-northeast-2`의 승인된 기존 VPC에 리소스 식별 계약의 이름과 공통 tag로 만들고 VPC를 외부 매핑에 기록한다.

| 방향 | 프로토콜/포트 | Source/Destination | 상태와 이유 |
| --- | --- | --- | --- |
| Inbound | TCP 80 | `0.0.0.0/0` | HTTP 공개. IPv6를 실제 사용할 때만 `::/0` 별도 추가 |
| Inbound | TCP 443 | `0.0.0.0/0` | HTTPS 공개. IPv6를 실제 사용할 때만 `::/0` 별도 추가 |
| Inbound | TCP 22 | 규칙 없음 | 기본 폐쇄. 비상 절차에서만 현재 사용자 IP로 임시 추가 |
| Inbound | TCP 3306 | 규칙 없음 | MySQL 외부 비공개 |
| Inbound | TCP 8080, 3000 | 규칙 없음 | backend·frontend 직접 공개 금지, 향후 Nginx 내부 경로만 사용 |
| Outbound | All | `0.0.0.0/0` | 초기 OS update, SSM, Docker, 공개 GHCR 연결용 기본값. 별도 egress 설계 전까지 임의 축소하지 않음 |

IPv6를 사용하지 않으면 IPv6 규칙을 추가하지 않는다. Docker의 published port는 호스트 firewall 규칙을 우회할 수 있으므로 이후 production Compose에서도 3306, 8080, 3000을 public interface에 publish하지 않아야 한다. OPS-009에서는 production Compose를 만들지 않는다.

성공 기준은 인바운드에 80·443만 존재하고 22·3306·내부 서비스 포트가 없는 것이다. VPC가 불명확하거나 규칙이 자동으로 넓어지면 Security Group을 instance에 연결하지 않고 중단한다.

### 4. EC2와 암호화 EBS 생성

EC2 launch wizard에서 다음을 한 항목씩 확인한다.

1. Region은 `ap-northeast-2`다.
2. AMI는 Ubuntu Server 24.04 LTS, Canonical 게시자, x86_64다. AMI ID는 시점별로 바뀌므로 문서에 고정하지 않는다.
3. Instance type은 `t3.small`이고 Credit specification은 `standard`다. Console이나 계정 기본값이 `unlimited`여도 그대로 사용하지 않는다.
4. 기존 승인 VPC와 public subnet을 선택하고 auto-assign public IPv4는 비활성화한다. 신규 네트워크 생성은 중단 조건이다.
5. 2단계의 instance role과 3단계의 Security Group을 연결한다.
6. Root volume은 gp3 40 GiB, encrypted, `Delete on termination=No`다.
7. Metadata는 IMDSv2 required로 설정한다.
8. Shutdown behavior는 Stop, termination protection은 Enabled로 설정한다.
9. Detailed monitoring과 추가 유료 기능은 활성화하지 않는다.
10. instance에는 리소스 식별 계약의 `Name`과 공통 tag를 적용하고, launch wizard의 volume resource type에도 root EBS의 `Name`과 공통 tag를 적용한다. 생성 뒤 instance·root device 관계를 외부 매핑에 기록한다.
11. 비상 SSH용 key pair를 선택하거나 새로 만들되 private key는 사용자가 저장소 밖의 안전한 위치에 보관한다. TCP 22 inbound는 만들지 않는다.

생성 직후 다음을 확인한다.

- instance type, Credit specification `standard`, AMI 이름·게시자, Region, VPC/subnet
- attached role과 Security Group
- root EBS gp3 40 GiB, encrypted, DeleteOnTermination false
- termination protection과 IMDSv2 required
- instance·root EBS 이름과 공통 tag, 외부 매핑의 연결 관계
- 추가 public IPv4가 없고 불필요한 추가 volume이 없음

하나라도 다르면 애플리케이션을 설치하지 않는다. 잘못된 instance는 termination protection을 해제해 종료하되 보존된 EBS와 Elastic IP가 남는지 역순 정리에서 별도 확인한다.

### 5. Elastic IP 할당과 연결

1. `ap-northeast-2` EC2의 Elastic IP를 하나만 할당한다.
2. 리소스 식별 계약의 `Name`과 공통 tag를 적용한다.
3. 방금 만든 instance에 연결하고 instance·EIP 관계와 DNS 의존성 없음 상태를 외부 매핑에 기록한다.
4. instance networking 화면에서 public IPv4가 연결한 Elastic IP임을 확인한다.
5. 실제 IP는 저장소, PR, 이슈, 스크린샷 파일명에 기록하지 않는다.

AWS는 연결 여부와 관계없이 public IPv4 주소에 비용을 부과할 수 있다. Disassociate만 하면 할당은 유지되어 비용이 계속될 수 있고, Release하면 같은 주소를 다시 확보할 수 있다는 보장이 없다. DuckDNS나 TLS를 연결한 이후에는 먼저 DNS 의존성을 제거한 뒤 Release한다.

### 6. SSM Session Manager 기본 접속 검증

Elastic IP 연결 뒤 Systems Manager의 Managed nodes 등록을 기다리고 Session Manager에서 세션을 시작한다. SSM은 인바운드 포트를 요구하지 않지만 instance role, SSM Agent와 아웃바운드 HTTPS가 필요하다.

세션에서 민감정보를 출력하지 않는 아래 항목만 확인한다.

```bash
cat /etc/os-release
uname -m
systemctl list-units --type=service | grep -E 'amazon-ssm-agent|snap.amazon-ssm-agent'
```

성공 기준은 Ubuntu 24.04, x86_64, SSM Agent 실행과 Session Manager 명령 실행이다. 등록되지 않으면 SSH를 먼저 열지 말고 다음 순서로 확인한다.

1. instance role과 `AmazonSSMManagedInstanceCore`
2. instance 시간과 SSM Agent 상태
3. 아웃바운드 TCP 443과 DNS
4. `ssmmessages` 서비스 endpoint 도달 경로

이 확인으로 해결되지 않을 때만 비상 SSH 절차의 사용자 승인을 받는다.

### 7. 사용자 IP 제한 비상 SSH

비상 SSH는 SSM 장애 진단을 위한 일시적 경로이며 기본 운영 경로가 아니다.

1. 사용자가 현재 공인 IP를 별도 안전한 위치에서 확인한다.
2. Security Group에 TCP 22 source `<현재 사용자 공인 IPv4>/32` 또는 IPv6 한 주소의 `/128` 규칙 하나를 추가한다.
3. 사용자 소유 key pair와 Ubuntu 계정으로 접속한다. private key를 저장소에 복사하거나 명령 기록에 출력하지 않는다.
4. SSM role, agent, DNS와 아웃바운드 443만 진단한다.
5. 작업이 끝나면 접속 성공 여부와 무관하게 TCP 22 규칙을 즉시 삭제한다.
6. Security Group에서 22 규칙이 없어졌음을 다른 화면 진입 후 다시 확인한다.

`0.0.0.0/0`, `::/0`, 넓은 회사·통신사 대역 또는 오래된 IP를 허용하지 않는다. 사용자 IP가 바뀌면 기존 규칙을 넓히지 말고 삭제 후 새 단일 주소로 교체한다.

### 8. Docker Engine과 Compose plugin 설치

SSM 세션에서 Docker 공식 Ubuntu 저장소를 사용한다. convenience script와 비공식 package를 사용하지 않는다.

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker.service containerd.service
sudo systemctl is-enabled docker.service containerd.service
sudo systemctl is-active docker.service containerd.service
sudo docker run --rm hello-world
sudo docker compose version
```

Docker group은 root 수준 권한을 부여하므로 이번 단계에서는 사용자를 추가하지 않고 `sudo docker`를 사용한다. production Compose, GHCR pull과 애플리케이션 container는 실행하지 않는다.

재부팅 검증은 EC2 Console의 Reboot 또는 다음 명령으로 한 번 수행한다.

```bash
sudo reboot
```

세션 종료는 정상이다. 다시 SSM에 연결한 뒤 다음을 확인한다.

```bash
sudo systemctl is-enabled docker.service containerd.service
sudo systemctl is-active docker.service containerd.service
sudo docker version
sudo docker compose version
```

모든 명령이 성공하고 재부팅 뒤 Docker와 containerd가 enabled·active면 성공이다. 실패하면 production 배포로 진행하지 않고 Docker 공식 troubleshooting 결과와 비민감 오류만 기록한다.

### 9. EC2 중지·재시작과 보존 검증

애플리케이션과 MySQL을 배포하기 전 빈 기반에서 한 번 검증한다.

1. EC2 Console에서 Stop을 요청하고 Stopped 상태를 기다린다. 강제 중지는 사용하지 않는다.
2. root EBS가 삭제되지 않고 instance에 연결된 상태임을 확인한다.
3. Elastic IP가 연결 상태이며 public IPv4 비용 가능성이 계속됨을 확인한다.
4. Start를 요청하고 Running과 status checks 통과를 기다린다.
5. 같은 Elastic IP가 연결돼 있는지 확인한다.
6. SSM 세션 재연결, Docker·containerd enabled·active를 다시 확인한다.

EBS-backed instance는 stop/start 동안 EBS 데이터를 보존하지만 instance store 데이터는 보존하지 않는다. 이 구성은 instance store를 사용하지 않는다. 이 단계가 실패하면 첫 deployment로 진행하지 않는다.

## 성공 확인표

| 단계 | 성공 조건 | 실패 시 다음 행동 |
| --- | --- | --- |
| Credits·Budget | Gross/Net 금액·비용 유형·알림 일치 | 비용 설정을 수정하거나 중단 |
| IAM | EC2 trust + SSM Core 단일 정책 | instance 연결 금지, role 정리 |
| Security Group | 80·443만 inbound, 22·3306·내부 포트 없음 | 연결 금지, 규칙 수정 또는 삭제 |
| EC2·EBS | 승인값, Credit specification standard, 암호화, DeleteOnTermination false | 설치 금지, 역순 정리 |
| Elastic IP | 같은 Region에서 instance에 연결 | SSM 진행 금지, 연결 상태 확인 |
| SSM | Managed node와 세션 성공 | 22를 열기 전에 role·agent·443 진단 |
| Docker | 설치·hello-world·Compose 확인 | production 배포 금지 |
| 재부팅 | SSM 재연결, Docker 자동 시작 | service 설정 복구 후 재검증 |
| stop/start | EBS·EIP 보존, SSM·Docker 복구 | deployment 승인 보류 |

## 첫 확인 절차

예상과 다른 상태가 보이면 변경을 더 만들기 전에 다음 순서로 확인한다.

1. 계정과 Region
2. 방금 수행한 단일 단계와 CloudTrail event
3. Budget, role, Security Group, EC2, EBS, Elastic IP의 현재 상태
4. 민감정보를 제거한 오류 메시지
5. 되돌릴 수 있는 마지막 정상 단계

## 완화 조치

- 비용 급증 의심: 신규 작업 중단, instance Stop, 불필요한 public IPv4와 EBS 상태 확인, Billing 화면과 Budget 알림 확인
- SSM 실패: role·agent·DNS·아웃바운드 443 확인, 불필요한 inbound 추가 금지
- Docker 실패: 애플리케이션 배포 금지, 공식 Ubuntu 설치 문서와 첫 오류만 확인
- Security Group 과다 공개: 넓은 규칙을 먼저 삭제하고 80·443만 재확인
- 식별자 노출 의심: 저장소 기록을 중단하고 사용자/Tech Lead에 즉시 보고

## 안전 정리와 원상 복구

실패했거나 기반을 사용하지 않기로 결정하면 의존성의 역순으로 정리한다. 실행 전 각 대상의 논리 이름, 지원 tag와 저장소 외부 매핑의 실제 식별자·연결 관계가 모두 일치하는지 확인한다. 하나라도 불일치하면 해당 리소스를 삭제하지 않고 중단한다.

1. 신규 deployment나 DNS 연결을 중단한다.
2. 임시 SSH 22 규칙을 삭제한다.
3. instance를 Stop하고 필요한 비민감 증거를 보존한다.
4. 종료가 승인되면 termination protection을 해제하고 instance를 Terminate한다.
5. 외부 매핑의 root device와 일치하는 `Delete on termination=No` root EBS가 Available로 남았는지 확인한다.
6. 복구 필요성이 없다는 사용자 판단 뒤에만 해당 EBS를 명시적으로 삭제한다. Snapshot 생성은 비용과 데이터 보존 결정을 요구하므로 별도 승인 없이 만들지 않는다.
7. 외부 매핑의 EIP를 Disassociate한 뒤 더 이상 필요 없고 DNS 의존성이 없음을 확인하고 Release한다. Release 이후 동일 주소 복구는 보장되지 않는다.
8. 이름·공통 tag·VPC가 외부 매핑과 일치하고 연결 대상이 없는 Security Group만 삭제한다.
9. IAM role 상세에서 연결 정책이 `AmazonSSMManagedInstanceCore` 하나뿐이고 예상 밖 inline policy가 없는지 확인한다. 다르면 삭제하지 않고 중단한다.
10. EC2 instance와 다른 workload가 해당 role/profile을 사용하지 않으며 role의 last accessed 정보가 정리 대상과 모순되지 않는지 확인한다. 사용 여부가 불명확하면 삭제하지 않고 중단한다.
11. role과 profile이 외부 매핑의 same-name 쌍일 때만 IAM Console의 Roles 화면에서 role을 삭제한다. Console은 연결 관리형 정책 분리, inline policy 삭제와 same-name EC2 instance profile 삭제를 함께 수행한다.
12. IAM Roles 검색과 EC2 launch wizard의 IAM instance profile 목록에서 same-name role/profile이 모두 사라졌는지 확인한다. 이름이 다르거나 profile이 남으면 CLI/API로 정리하지 말고 별도 승인을 위해 중단한다.
13. Budget은 계정을 계속 운영하면 비용 감시를 위해 유지한다. 계정 사용을 완전히 중단하기로 사용자가 결정한 경우에만 이름·공통 tag가 외부 매핑과 일치하는 두 Budget을 삭제한다.
14. Billing Console에서 남은 EBS, public IPv4와 기타 과금 리소스가 없는지 다시 확인하고 외부 매핑의 각 상태를 `유지` 또는 `삭제 확인`으로 갱신한다.

정리는 AWS 비용 데이터가 즉시 갱신되지 않을 수 있다는 점을 고려해 다음 청구 데이터 갱신 뒤 한 번 더 확인한다.

## 롤백

### 저장소 문서 롤백

OPS-009는 AWS를 자동 변경하지 않는다. 문서에 결함이 있으면 PR을 병합하지 않거나, 병합 후에는 OPS-009 문서 commit을 일반 revert PR로 되돌린다. `reset`, `rebase`, force push는 사용하지 않는다.

### 사용자 실행 롤백

실제 AWS 적용 뒤의 롤백은 `안전 정리와 원상 복구` 순서를 사용한다. EBS 삭제와 Elastic IP Release는 복구가 보장되지 않는 단계이므로 사용자 확인 전에는 실행하지 않는다.

## 보존할 증거

실행 증거는 다음 표 형식으로 사용자가 별도 운영 기록에 남긴다. 저장소에 반영할 때는 계정 ID, 전체 resource ID·ARN, 실제 공인 IP, 이메일, Secret을 제거하고 상태와 설정 여부만 남긴다.

| 시각대 | 단계 | 결과 | 비민감 확인값 | 실패와 정리 |
| --- | --- | --- | --- | --- |
| Asia/Seoul | 예: Security Group | 성공/실패/미실행 | 예: 80·443만 공개, 22 없음 | 오류 요약과 제거 여부 |

필수 증거 항목은 다음과 같다.

- Gross/Net Budget 금액, Credits·Refunds 포함 여부, 알림 구독 확인
- Credits 적용 가능 서비스와 만료일 검토 완료 여부
- 리소스별 논리 이름, 지원 tag와 저장소 외부 매핑 일치 여부
- EC2 role trust, 연결 정책 목록과 same-name instance profile 확인
- Security Group 이름·공통 tag·inbound 표와 SSH 제거 확인
- EC2 type·OS·Region·Credit specification standard, EBS 유형·크기·암호화·보존 설정
- Elastic IP 연결·해제 상태와 비용 확인
- SSM Managed node·세션 성공 여부
- Docker·Compose version 확인과 재부팅 후 active 상태
- stop/start 뒤 EBS·EIP·SSM·Docker 확인
- 실패 단계, 중단 시점, 역순 정리 완료 여부

스크린샷을 공유해야 하면 계정 메뉴, 전체 ARN·ID, IP, 이메일과 알림 수신자를 가린다. 실제 AWS 검증을 수행하지 않았다면 `미실행`으로 기록하며 통과로 바꾸지 않는다.

## 에스컬레이션

다음 상황은 사용자/Tech Lead 판단이 필요하다.

- 승인값과 다른 instance, storage, Region 또는 유료 서비스가 필요함
- 기존 VPC/public subnet이 없어 네트워크 설계가 필요함
- AWS 관리형 EBS key 대신 고객 관리형 KMS key가 필요함
- SSM Core보다 넓은 현재 권한이나 미래 S3·CloudWatch 권한이 필요함
- Elastic IP Release, EBS 삭제, Snapshot 생성처럼 비용·복구 결정이 필요함
- 실제 결제 0 USD 목표 또는 Budget 기준을 초과할 가능성이 있음
- Secret이나 계정 식별자 노출이 의심됨

## 후속 작업 게이트

아래 항목이 모두 증거로 확인되기 전에는 production 배포 구현으로 진행하지 않는다.

- OPS-009의 Budget, IAM, Security Group, EC2·EBS·EIP, SSM, Docker, stop/start 검증 완료
- 임시 SSH 규칙 부재
- production Compose와 배포·rollback script 별도 승인
- 공개 GHCR SHA tag workflow 별도 승인과 검증
- TLS·DuckDNS, Secret materialize, MySQL backup·restore 계획 별도 승인
- 단일 release 운영과 자원 사용량 검증 완료

Blue·Green은 위 단일 release가 완료되고 CPU, memory, disk와 비용 여유가 확인된 뒤 별도 작업으로만 구현한다.

## 공식 근거

- [AWS Budgets로 비용 추적](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html)
- [AWS Budgets CostTypes의 Credits·Refunds 설정](https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_budgets_CostTypes.html)
- [AWS Budgets tag 관리](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-best-practices.html#tagging-budgets)
- [Billing Console에서 Credits와 만료일 확인](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/useconsolidatedbilling-credits.html)
- [EC2용 Systems Manager instance permissions](https://docs.aws.amazon.com/systems-manager/latest/userguide/setup-instance-permissions.html)
- [Session Manager용 instance profile](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-getting-started-instance-profile.html)
- [IAM 보안과 최소 권한 모범 사례](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html)
- [IAM Console의 role과 same-name instance profile 삭제](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_manage_delete.html)
- [IAM instance profile tag의 CLI/API 관리 경계](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_tags_instance-profiles.html)
- [EC2 리소스 tag 관리](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags_Console.html)
- [EC2 Security Group 규칙 예시](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html)
- [EC2 stop/start 동작](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Stop_Start.html)
- [T3 unlimited surplus credit 비용과 standard 전환](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-unlimited-mode-concepts.html)
- [EBS root volume의 DeleteOnTermination 설정](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configure-root-volume-delete-on-termination.html)
- [Elastic IP 동작과 public IPv4 비용 주의](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/elastic-ip-addresses-eip.html)
- [Docker Engine Ubuntu 설치](https://docs.docker.com/engine/install/ubuntu/)
- [Docker Compose plugin 설치](https://docs.docker.com/compose/install/linux/)
