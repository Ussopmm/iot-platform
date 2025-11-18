up:
	cd infrastructure
	docker compose up --build -d nexus
	docker-compose up --build -d

down:
	docker-compose -f infrastructure/docker-compose.yaml down

logs:
	docker-compose -f infrastructure/docker-compose.yaml logs -f

ps:
	docker-compose -f infrastructure/docker-compose.yaml ps

reset:
	docker-compose -f infrastructure/docker-compose.yaml down -v
	docker-compose -f infrastructure/docker-compose.yaml up --build -d