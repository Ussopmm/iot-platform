
test-it:
	-docker network create iot-platform-net 2>/dev/null || true
	docker-compose -f infrastructure/docker-compose.yaml up --build -d nexus
	docker-compose -f infrastructure/docker-compose.yaml run --rm avro-schemas
	docker-compose -f infrastructure/docker-compose-it.yaml up --build \
		--abort-on-container-exit \
		--exit-code-from device-collector-tests
	docker-compose -f infrastructure/docker-compose-it.yaml down

up: test-it
	docker-compose -f infrastructure/docker-compose.yaml up --build -d


down:
	docker-compose -f infrastructure/docker-compose-it.yaml down -v
	docker-compose -f infrastructure/docker-compose.yaml down

logs:
	docker-compose -f infrastructure/docker-compose.yaml logs -f

ps:
	docker-compose -f infrastructure/docker-compose.yaml ps

reset:
	docker-compose -f infrastructure/docker-compose-it.yaml down -v
	docker-compose -f infrastructure/docker-compose.yaml down -v
	docker-compose -f infrastructure/docker-compose up --build -d nexus
	docker-compose -f infrastructure/docker-compose-it up --build -d
	docker-compose -f infrastructure/docker-compose-it up --build -d device-collector-tests
	docker-compose -f infrastructure/docker-compose.yaml up --build -d