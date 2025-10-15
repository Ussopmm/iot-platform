To start the system:
1. cd infrastructure
2. docker-compose up -d --build (you can run it via makefile as well, to run paste this value `make up`)
3. check if all containers are running with `docker ps` 
4. open http://localhost:3000 in your browser to see Grafana UI
5. to stop docker containers run `docker-compose down` or `make down`

To add data sources:
1. Open Grafana UI at http://localhost:3000
2. go to connections -> data sources -> add new data source -> select Prometheus and do not forget to set the URL to `http://prometheus:9090`
   (same for Loki and Tempo if you want to use them)

To add dashboards:
1. Open Grafana UI at http://localhost:3000
2. go to connections -> dashboards -> import dashboard
3. paste there this two ids 10122 (for kafka) and 9628 (for postgresql)


