global:
  scrape_interval: 30s
  scrape_timeout: 10s
  evaluation_interval: 30s

# Production alert rules and delivery (ARCH-016). Alertmanager shares only the observability bridge.
rule_files:
  - /etc/prometheus/rules/*.yml

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

scrape_configs:
  - job_name: pawcycle-production-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - __PAWCYCLE_METRICS_TARGET__
