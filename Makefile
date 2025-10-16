up:
	docker-compose -f infrastructure/docker-compose.yaml up -d --build

down:
	docker-compose -f infrastructure/docker-compose.yaml down

logs:
	docker-compose -f infrastructure/docker-compose.yaml logs -f

ps:
	docker-compose -f infrastructure/docker-compose.yaml ps

reset:
	docker-compose -f infrastructure/docker-compose.yaml down -v
	docker-compose -f infrastructure/docker-compose.yaml up --build -d