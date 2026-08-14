global:
  scrape_interval: 30s
  scrape_timeout: 10s
  evaluation_interval: 30s

scrape_configs:
  - job_name: pawcycle-production-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - __PAWCYCLE_METRICS_TARGET__
